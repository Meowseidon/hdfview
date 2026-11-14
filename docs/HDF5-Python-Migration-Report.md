# HDFView HDF5 功能与实现调查报告（面向 Python 迁移）

> 调查范围：仅 HDF5。HDF4、NetCDF2/3/4、FITS 不在迁移范围内，报告中仅在"应删除/易误迁移"处提及。
>
> 代码基线：当前 master 工作树；HDF5 2.2.0（`pom.xml`、`repository/lib/jarhdf5-2.2.0.jar`）。
>
> 调查方法：源码逐类精读 + 调用链追踪 + 本机 Python 实测（h5py 3.16.0 / HDF5 2.0.0 / NumPy 2.5.3）。所有示例文件均来自 `hdfview/src/test/resources/uitest/` 或运行期生成。行号是调查时的快照，用于定位实现，不保证后续不漂移。

---

## 1. 执行摘要

### 1.1 现有实现规模

| 部分 | 规模 |
|---|---|
| HDF5 对象层（`object/src/main/java/hdf/object/h5/`） | 13 个类，约 17,650 行 |
| 格式无关对象抽象（`hdf/object/*.java`） | 约 7,838 行 |
| GUI 层（`hdfview/src/main/java/hdf/view/`） | 84 个类，约 53,949 行 |
| 其中 HDF5 相关视图（Tree/Table/Image/Palette/MetaData/Search/Statistics） | 约 28,600 行 |
| 对话框 | 约 12,000 行 |
| 直接调用的 HDF5 C API | 185 个不同函数，666 个调用点 |
| 测试 | object 模块 31 个、hdfview 模块 38 个 Java 测试类 |

### 1.2 一句话结论

HDFView 的 HDF5 能力可以完整拆成**对象模型层**（约 2.5 万行 Java/JNI）和**桌面交互层**（约 5.4 万行 SWT/NatTable/AWT）两部分。Python 迁移中，**数据访问层反而最容易**——h5py 3.16 可直接覆盖约 85%–90% 的浏览/读取能力和 70%–80% 的编辑/特殊类型能力；真正的工作量在：

1. GUI 的完整重建（树、表格、图像、调色板、搜索、统计、全部对话框）；
2. 复合类型、引用、无符号、字符串、Float8/16/BFloat16、long double 等**语义精确性**；
3. h5py 的少数硬缺口（新式 64 字节引用、SZIP、完整错误栈、bitfield 创建、bfloat16/FP8 写入、link attributes、已存在文件的 user block 修改），需要一层 ctypes/cffi 直调 libhdf5 的 `native_bridge`；
4. Java 侧 **HDF5 2.2.0** 与 h5py 内置 **HDF5 2.0.0** 的版本断层，必须在项目启动时做决策。

**推荐技术栈**：h5py 3.16 + hdf5plugin + NumPy + PySide6 6.11（Qt Model/View）+ pyqtgraph + Pillow + pytest/pytest-qt + Briefcase（安装包）/ PyInstaller onedir（开发包）。不采用 PyTables 作为主路径，不采用 PyQt6（GPL/商业许可），不引入 netCDF4。

---

## 2. 现有 Java 实现总览

### 2.1 模块分层

```
HDFView GUI (hdfview 模块, SWT/JFace + NatTable + AWT)
    TreeView / TableView / ImageView / PaletteView / MetaDataView
    Search / Statistics / Chart / Dialog / fileusage / i18n
                │  直接方法调用 + Display.syncExec/asyncExec
HDF5 对象模型 (object 模块, 抽象类 + h5 实现)
    HObject -> Group/Dataset/Datatype/H5Link
    FileFormat -> H5File
    Dataset -> ScalarDS/CompoundDS -> H5ScalarDS/H5CompoundDS
    Attribute -> H5Attribute -> H5ScalarAttr/H5CompoundAttr
    H5Utils / H5Plugins / H5MetaDataContainer
                │  hdf.hdf5lib.H5 静态 JNI 方法
libhdf5 2.2.0 (jarhdf5 2.2.0)
```

对象层采用"内存树 + native 句柄"双层结构：

- 每个 HDF5 对象在 Java 侧有 `HObject`（`filename/fileFormat/name/path/fullName/oid/linkTargetObjName`）和 native 侧句柄；
- `oid` 是 `H5Rcreate_object` 生成的对象引用数组，但 `equals(Object)` 仍是引用相等，`hashCode` 只取 `oid[0]`——**不能**把它当作可靠的对象身份；
- 打开文件时 `H5File.open()` 默认调用 `loadIntoMemory() -> depth_first()`，一次性递归构建整棵树并缓存所有 `HObject`，断链/未知类型用 `H5Link` 占位；
- 属性由每个对象内嵌的 `H5MetaDataContainer` 持有，首次读取时**一次性枚举并读取全部属性值**，之后永久缓存；
- 数据读取以"当前 selection 的整页"为单位，写入把整页缓冲写回。

### 2.2 对象层类结构

```
HObject (abstract)
├── Group (abstract) ── H5Group
├── Dataset (abstract)
│   ├── ScalarDS (abstract) ── H5ScalarDS
│   │                      └── H5ScalarAttr (extends ScalarDS, implements H5Attribute)
│   └── CompoundDS (abstract) ── H5CompoundDS
│                            └── H5CompoundAttr (extends CompoundDS, implements H5Attribute)
├── Datatype (abstract) ── H5Datatype ── H5ReferenceType
├── H5Link（断链/未知类型占位，不继承 Group）
└── H5MetaDataContainer（属性缓存容器本身也是 HObject）

FileFormat (abstract, extends java.io.File) ── H5File
接口：DataFormat、CompoundDataFormat、MetaDataContainer、Attribute、H5Attribute
工具：H5Utils（hyperslab 选择）、H5Plugins（filter 插件路径）
```

关键点：

- 属性复用数据集模型（`H5ScalarAttr extends ScalarDS`），因此属性可以直接用表格/图像视图打开；但 HDF5 属性**不支持部分写**，这个复用带来 rank>2 属性编辑的隐患。
- `Datatype` 中的 `CLASS_*` 常量（INTEGER=0 … COMPLEX=12）是查看器自己的编号，不是 HDF5 C 的 `H5T_*` 值。
- `H5File` 是 `FileFormat` 插件注册表的一个实现；HDF5-only 迁移只需保留 `H5File` 契约。

### 2.3 GUI 层结构

| 层 | 主要类 | 职责 |
|---|---|---|
| 主窗口 | `HDFView` | 菜单、工具栏、URL/最近文件/访问模式栏、SashForm/TabFolder、状态区 |
| 树 | `DefaultTreeView` | 文件生命周期、`SWT.VIRTUAL` Tree、右键菜单、内部剪贴板、新建对象 |
| 表格 | `DefaultBaseTableView`、`DefaultScalarDSTableView`、`DefaultCompoundDSTableView`、三个 Factory | NatTable 数据页、编辑、显示格式、导入导出、统计、折线图 |
| 图像 | `DefaultImageView`、`ImageViewFactory` | AWT `BufferedImage` 渲染，再逐像素转 SWT Image |
| 调色板 | `DefaultPaletteView` | 256 色 RGB 曲线/表格编辑 |
| 图表 | `Chart` | SWT GC 自绘直方图/折线图，无第三方图表库 |
| 元数据 | `DefaultBaseMetaDataView` + 四个子类 | Attributes / General 两页，属性增删改 |
| 搜索 | `DatasetSearchDialog/Engine/Snapshot/Overlay` | 名称/值搜索、脏缓冲 overlay、块读 |
| 统计 | `DatasetStatisticsDialog/Engine/HighlightMask` | 当前页/全数据集统计与高亮 |
| 新建 | `NewGroupDialog`、`NewDatasetDialog`、`NewImageDialog`、`NewCompoundDatasetDialog`、`NewLinkDialog`、`NewDatatypeDialog`、`NewScalarAttributeDialog` | 各类对象/属性创建 |

视图通过 `DataViewFactoryProducer` + 反射按类名实例化，支持 `lib/ext` 外部插件；这套 Java 反射插件体系不应迁移。GUI 没有事件总线，组件之间直接调用、扫描 `Display.getShells()`、可选 1 秒定时器刷新——这是 Qt 迁移时最需要主动替换的部分。

### 2.4 数据流与线程模型

```
UI → 对象模型（Dataset/Group/Datatype/Attribute）→ H5* 实现 → jarhdf5 JNI → libhdf5
```

- 读取总是"当前 selection 的整页"；写入把整页缓冲写回；属性全量读写。
- HDF5 原生访问由 `DatasetSearchEngine.NATIVE_IO_LOCK` 串行化；UI 更新通过 `Display.syncExec/asyncExec` 回主线程。
- `LoadDataThread` 只是把 `showDataContent` 丢回 UI 线程，不是后台 I/O；真正的后台线程是搜索/统计 worker，但读取仍与主线程共用同一把锁。
- 文件关闭后所有视图、搜索快照、统计 worker 必须显式失效；`HDFView.closeFile` 直接 dispose 所有视图，不做文件级保存确认，只有 TableView 销毁时提示未保存修改。

### 2.5 native API 使用面

185 个不同 `H5.*` 函数、666 个调用点，主要集中在：

- 文件/组/链接：`H5Fopen/Fcreate/Fflush/Fclose`、`H5Gopen/Gcreate/Gget_info/Gget_obj_info_full`、`H5Oopen/Oget_info/Oget_info_by_name/Oget_info_by_idx/Ocopy`、`H5Lcreate_hard/soft/external/Ldelete/Lexists/Lget_info/Lget_value/Lget_name_by_idx/Lmove`；
- 数据集：`H5Dopen/Dclose/Dget_type/Dget_space/Dget_create_plist/Dget_storage_size/Dread/Dwrite/Dread_VLStrings/Dwrite_VLStrings/DreadVL/DwriteVL/Dset_extent/Drefresh`；
- dataspace/plist：`H5Screate_simple/create/get_simple_extent_*/select_hyperslab`、`H5Pget_*/H5Pset_*`（libver bounds、chunk、deflate、fill value、external、virtual、copy object、fapl split/family、link creation order）；
- 类型：`H5Tget_class/size/order/sign/precision/offset/pad/super/array_dims/nmembers/member_*/strpad/cset/tag/ebias/fields/norm/inpad`、`H5Tis_variable_str/H5Tdetect_class/H5Tcommitted/H5Tequal/H5Tget_native_type`、`H5Tcopy/create/array_create/vlen_create/enum_create/enum_insert/complex_create/insert/pack/set_*`、`H5Topen/commit/close`；
- 属性：`H5Oget_info.num_attrs`、`H5Aopen_by_idx/open_by_name/create/copy/get_name/get_space/get_type/read/read_VLStrings/readComplex/readVL/write/write_VLStrings/writeVL/delete/rename_by_name/close`；
- 引用：`H5Rget_type/get_obj_type3/get_name_string/get_file_name/get_obj_name/get_attr_name/dereference/open_object/open_region/open_attr/get_region/destroy`；
- 其他：`H5Zget_filter_info`、`H5PLsize/get/replace/insert/remove/prepend/append`、`H5get_libversion`、`H5export_dataset`（jarhdf5 私有扩展，h5py 无等价物）。

---

## 3. HDF5 对象层功能与实现

### 3.1 文件、组、链接

**文件打开与访问模式**

- 访问标志：`READ` / `WRITE` / `CREATE` / `MULTIREAD`（SWMR 只读）。UI 打开前先检查文件可写性，不可写则强制 READ。
- `open(boolean,long)` 先按请求 flag `H5Fopen`；失败后**静默降级只读**；再失败尝试 split 布局（`-m.h5`/`-r.h5`，`H5Pset_fapl_split`）和 family 布局（`%0Nd`，`H5Pset_fapl_family`）。CREATE 是两步：`H5Fcreate(TRUNC)` 后关闭，再以 RDWR `H5Fopen`。
- libver bounds：新建默认 EARLIEST..LATEST；支持 V18/V110/V112/V114/V200/Latest；已打开文件可在对话框修改 FAPL 的 bounds，但**不会转换已有对象的格式**。必须区分 `getLibversion()`（`H5get_libversion`，链接的 native 库版本）与文件格式 bounds。
- SWMR：只实现 `H5F_ACC_RDONLY | H5F_ACC_SWMR_READ`，没有 SWMR 写、`H5Fstart_swmr_write`、`H5Oflush`；SWMR 打开失败会静默回退普通只读。

**层级加载与路径**

- 默认 `open() -> loadIntoMemory() -> depth_first()` 递归构建整棵树；每个 group 用一次 `H5Gget_obj_info_full` 批量取 name/type/fNos/token（代码注释称逐次 `H5Gget_objname_by_idx` 百万对象需 12 天）。
- `get(path)` 先规范化为 `/` 分隔，在内存树中广度优先按 `fullName` 匹配；未命中时 `H5Oopen` 懒加载单个对象/单层成员，且不会挂回 rootObject。文件未打开时 `get()` 会 `open(false)` 并保留 fid，导致之后 `open()` 提前返回、层级树永不加载——这是迁移时必须避免的状态机缺陷。
- 路径语义：path 始终以 `/` 开头结尾，root 的 path=null、name=`/`；硬链接多名字会在内存树中产生重复 HObject 节点。

**成员顺序与对象身份**

- 文件级 `indexType/indexOrder` 控制成员顺序：H5_INDEX_NAME / H5_INDEX_CRT_ORDER × H5_ITER_INC/DEC/NATIVE，默认 NAME/INC。UI 的 `Change file indexing` 修改后重载整棵树。
- 对象身份建议用 `(h5py.h5o.get_info(obj.id).fileno, .addr)`；不要按路径/名称去重，也不要用 `oid[0]` 做 hash。

**链接**

- 支持 hard / soft / external；不创建、不解析 user-defined link。
- `createLink` 对未知 `lType` 不做校验，type 初值 0 会静默创建 hard link；`currentObj` 为 H5Link/未知子类时可能链接已创建但返回 null。
- 创建/覆盖语义：先 `H5Lexists` 再 `H5Ldelete` 覆盖同名链接；禁止 hard link 到 root。
- `getLinkTargetName`：soft 返回 `H5Lget_value[0]`；external 返回 `fileName + '://' + objectPath`；hard/UD 返回 null。
- 断链：整树加载遇 `H5O_TYPE_UNKNOWN` 生成 `H5Link`（`open` 返回 0、`close` 空操作、`hasAttribute=false`、`getMetadata=null`）；懒加载 `get(path)` 对断链返回 null，`getGroup` 对单个成员失败仅 `continue`——同一文件在两种加载路径下断链可见性不一致。
- 循环检测：`depth_first` 用 oid 与祖先链逐级 `equalsOID`，发现环则停止递归但仍添加节点；h5py 无内建环检测，需要 `visited=set of (fileno, addr)` 并显式决定是否跟随软/外链。

**创建、删除、重命名、移动、复制**

- `delete` 只调 `H5Ldelete`，**不更新内存 memberList/nMembersInFile**；`renameObject` 用 `H5Lmove` 同文件移动；`H5Group.setName` 更新后代 path，但不更新磁盘上其他硬链接节点。
- UI 的 move = copy + delete，仅限同文件，对象在视图中打开时禁止移动。
- `copy` 按类型分派：dataset 用 `H5Ocopy + H5O_COPY_EXPAND_REFERENCE_FLAG`；group/datatype 用默认 copypl。目标名冲突只追加一次 `~copy`，第二次冲突会让 `H5Ocopy` 失败。
- 已知缺陷：`copyGroup` 后重新加载时用源 `FileFormat` 的 fid，跨文件复制会读错文件；group 复制中对象引用不展开；`updateReferenceDataset` 只对整文件复制的 scalar dataset 后处理，且按 8 字节读引用，与 HDF5 1.12+/2.0 的 64 字节 `H5R_ref_t` 不兼容。

**关闭、user block、插件、异常**

- `close` 遍历整棵内存树 clear，并用 `H5Fget_obj_count(ALL) + H5Fget_obj_ids` 强制关闭该 fid 下所有 Dataset/Group/Datatype/Attr；最终 fid=-1 但 rootObject 保留（陈旧树）。
- user block：对象层无 API。UI 直接按字节读文件，在 0/512/1024/2048... 扫描 HDF5 signature；写入时若新内容 ≤ 现有 block 大小则就地写并补零，否则重写整个文件。块大小必须是 512×2^n。
- filter 插件路径：`H5Plugins` 封装 `H5PLsize/get/replace/insert/remove/prepend/append`，启动时把 `<root>/plugin` 前插。
- 异常策略：`open()` 可返回 -1，`getGroup` 单成员失败 continue，`get` 返回 null，`readBytes` 返回 null，`getAttribute` 失败只 log；`H5Link.open()=0` 流入通用属性路径会触发 `H5Oget_info(0)` 失败并最终 NPE。迁移必须换成显式错误上报。

### 3.2 数据集（scalar / compound）

**分类与初始化**

- 树中数据集对象构造时不读数据；首次访问类型/维度/数据时 `init()` 打开 dataset，读取 dataspace、datatype、fill value、layout/VDS/external 标志。`H5File.getDataset` 对顶层类型解一层 `H5T_ARRAY` 取 base 后，只有 `H5T_COMPOUND` 走 `H5CompoundDS`，其余（ARRAY、VLEN、STRING、REFERENCE 等）一律走 `H5ScalarDS`。

**dataspace 与维度**

- rank 0 视为标量并归一化 `dims={1}`；`H5S_NULL` 也按 rank 0→标量处理，只置 `isNULL`。h5py 中 `shape=()`（标量）、`shape=None`（NULL dataspace）、shape 含 0（空数组）必须显式区分。

**selection 与读取**

- 每个"显示页"由 `startDims + selectedStride + selectedDims` 组成一个矩形 hyperslab；默认 1D/2D 读整块，3D+ 固定其它维且 frame 维 count=1；`selectedIndex` 决定行列/帧映射。
- `H5Utils.selectHyperslab`：全选时短路为 `H5S_ALL`；否则 `H5Screate_simple(rank, selectedDims)` + `H5Sselect_hyperslab(H5S_SELECT_SET, start, stride, count, null)`。`getTotalSelectedSpacePoints` 对空选择抛异常，总点数超过 `Integer.MAX_VALUE` 抛异常。
- H5ScalarDS 一次 `H5Dread/H5Dwrite` 完成整页；变长字符串走 `H5Dread_VLStrings/H5Dwrite_VLStrings`，VLEN 走 `H5DreadVL/H5DwriteVL`。H5CompoundDS 对 compound **逐字段**各调用一次 `H5Dread/H5Dwrite`。
- `readBytes` 按文件原始 datatype 返回未转换 byte[]（元素数 × `H5Tget_size`），用于无损复制/导出；异常被吞后返回 null。h5py 高层 `dset[...]` 总是转 numpy dtype，不保证文件原始布局；opaque/bitfield/user-defined/非标准 float 的 raw-byte 路径需低层 `h5py.h5d.read` 保留。
- 表格一次读整页，3D+ 每次一帧；图像一次一帧，动画按 stride 降采样到 ≤300 像素后逐帧读取。

**写入与编辑回写**

- 表格单元格提交 → `updateValueInFile` → `Dataset.write()` → H5ScalarDS/H5CompoundDS；写入的是**当前 selection 的整个缓冲**，不是单单元格；写成功后 `clearData()`，下次读取重新加载。只读文件或 hex/bin 显示模式跳过写入。
- compound 写逐字段进行，单成员失败只记录日志并继续，可能产生部分字段已写/部分未写的文件（非原子）。
- `compoundTypeIO` 写分支存在索引 bug：部分成员未选中时写入数据会错位（源码 TODO 明示）；单成员写异常只日志继续。

**创建**

- scalar：支持 dims、maxdims（0→dims，负数→`H5S_UNLIMITED`）、chunks、gzip 级别、fill value。
- compound：支持成员名/类型/数组维度，自动 `H5Tcreate/H5Tinsert` 并按顺序计算 offset，支持 chunk/gzip。
- image：创建后写 `CLASS / IMAGE_VERSION / IMAGE_MINMAXRANGE / IMAGE_SUBCLASS / INTERLACE_MODE / PALETTE` 属性。
- **创建侧只支持 contiguous/chunked + deflate(gzip)**；compact 布局、任意 filter 参数、nbit、szip、shuffle、fletcher32、scaleoffset、blosc 都没有创建入口。这是 Java 版的能力边界，Python 侧会显著更强。

**chunk、过滤器、fill value、布局**

- chunk：元数据展示 CHUNKED 和 chunk 维度；创建用 `H5Pset_layout(H5D_CHUNKED)+H5Pset_chunk`；可扩展 dataset 自动生成 `min(dim,64)` 的 chunk；不校验 chunk 是否大于 dims 或非正数；读取不做 chunk 对齐或预取。
- 过滤器读取：`H5Pget_nfilters/H5Pget_filter`（cdValues 上限 20、name 上限 120）+ `H5Zget_filter_info`，可识别 deflate/szip/shuffle/fletcher32/nbit/scaleoffset 及任意 user-defined filter，计算压缩比，并对 SZIP 查询 encode/decode 能力。展示缺陷：SHUFFLE 的 `Nbytes` 取自无参数 filter 的 `cdValues[0]`；未用 `H5Pget_filter_by_id2`；超过 20 个参数的 filter 无法完整展示。
- fill value：scalar 若 `H5Pfill_value_defined == USER_DEFINED`，则 `H5Pget_fill_value(nativeTID)` 读取、unsigned 转换后加入图像过滤值，并作为 `Dataset.fillValue` 暴露。compound 完全忽略 fill value。`parseFillValue` 对字符串不补齐、float>8 直接写 ASCII、reference 强转 long，均不可靠。
- 布局：元数据显示 CONTIGUOUS / CHUNKED / COMPACT / VIRTUAL（含 maps）/ NONE，并显示 `H5Dget_storage_size` 与 allocation time。
- external storage：只识别 `H5Pget_external_count` 并在 layout 文本标注 EXTERNAL，**不能创建或编辑**。读取依赖 HDF5 自动打开外部文件，并通过把进程级 `System.setProperty("user.dir", 主文件父目录)` 解析相对路径，`H5File.open` 再复位——多线程/多文件不安全，Python 不应改 CWD。
- VDS：只读元数据——virtual map 数量、源文件名、源 dataset 名、源/目标空间、view、printf gap；数据读取由 HDF5 透明完成，没有 VDS 创建入口。TreeView 的 `Open Source Files` 会打开所有虚拟源文件。
- **dimension scales 完全缺失**：全仓库无任何 `H5DS` / `DIMENSION_LIST` / `REFERENCE_LIST` 调用，HDF5 dataset 的 `dimNames` 永远为 null。Python 可用 `dset.dims[i].label`、`attach_scale/detach_scale`、`h5py.h5ds` 补齐——这属于**新增功能**，不是等价迁移。

**compound 数据集细节**

- `init` 调 `H5Datatype.extractCompoundInfo` 递归摊平嵌套 compound，生成 `flatNameList/flatTypeList`（嵌套字段名用不可见字符 U+0095 连接），并计算 `memberOrders/memberDims`。
- 读取时用 `H5Tcreate + H5Tinsert + H5Tpack` 构造"只含一个字段"的 packed compound 类型，再 `H5Dread`。单成员失败填充 `"*ERROR*"`。
- 写侧拒绝 ARRAY-of-COMPOUND 与 VLEN-of-COMPOUND；VLEN 非字符串成员写拒绝；VLEN(compound) 读取后格式化成 `"{...}"` 字符串，数值结构丢失。
- 逐字段 I/O 会重复解压/过滤 chunk，宽 compound 的 I/O 放大 N 倍，是必须修复的性能问题。

### 3.3 数据类型系统

`H5Datatype.fromNative()` 把 HDF5 tid 解析为 Java 模型；`createNative()` 反向构造 native memory type，末尾 `H5Tget_native_type` 归一化；`allocateArray()` 按类型分配 Java 缓冲区。

**整数、字节序与 unsigned**

- 支持 1/2/4/8 字节 signed/unsigned 和任意 size 的自定义整数；读取保留 order/precision/offset/pad；创建时以 `H5T_NATIVE_INT*` 为基础设置 size/precision/order/sign，**从不恢复 offset/pad**，自定义精度整数会退化。
- Java 无 unsigned：对象层读取为同宽度有符号数组，UI 再扩宽 u8→short、u16→int、u32→long；u64 保持 long 位模式，显示时用 BigInteger 转无符号十进制，无法完整正确表示。写回时 `convertToUnsignedC()` 截断回原宽度。compound 的 unsigned 成员在单字段读取时转换，compound 整体 unsigned 未实现。

**浮点、long double、Float8/16/BFLOAT16**

- 支持 float32/float64、16 字节 long double、1/2 字节 Float8/Float16/BFLOAT16 的读取回退。`createNative` 对 size≤2 无条件 `H5Tcopy(H5T_NATIVE_FLOAT)`（4 字节），没有 `H5Tset_size(2)`——因此**新建 float16 数据集/compound 成员实际会变成 32 位 float**。
- 读取 Float16/BFLOAT16 依赖 `H5Tget_native_type` 或 HDF5 转成 float32；代码中没有显式 bfloat16 解码。
- long double：按字节解码为 BigDecimal；16 字节 float 不允许写回；`convertByteToBigDecimal` 丢弃了 `sig.negate()` 的结果，负 long double 的显示可能丢符号；`H5T_NATIVE_LDOUBLE` 在 Windows x64 常见为 8 字节，与 UI 标注的 128-bit 不一致。
- complex：支持 HDF5 2.x `H5T_COMPLEX`（float32/float64/long double、BE、数组、compound 成员）；VLEN complex 明确不支持；16 字节 long double/复数用 `byte[][]`/BigDecimal，精度与平台相关。

**字符串**

- 定长字符串：按文件类型 size 读成 byte[]，用平台默认字符集解码，遇第一个 `\0` 截断并去掉尾部所有 ≤0x20 字符。写入用空格填充、按 Java char 超长截断、`getBytes()` 默认字符集。`H5Datatype` 保存 `nativeStrPad/nativeStrCSET`，但 Java 转换层**不按 cset 解码**。
- 变长字符串：用 `H5Tis_variable_str` 识别；读取分配 `String[]` 并走 `H5Dread_VLStrings/H5Aread_VLStrings`；写回走 `H5Dwrite_VLStrings/H5Awrite_VLStrings`。

**compound / array / enum / vlen / opaque / bitfield**

- compound：逐成员读取 `H5Tget_nmembers/member_name/member_offset/member_type` 并递归构造；Java 侧扁平化 U+0095；ARRAY-of-COMPOUND 在 H5File 中解一层 array 后按 compound 处理，写入路径拒绝 ARRAY/VLEN of compound。
- enum：`enumMembers` 是 value 字符串到成员名的 `HashMap`；读取 `H5Tget_member_name/get_member_value` 按 1/2/4/8 字节转换；显示可切换数值/名称，写回 `convertEnumNameToValue`。缺陷：大端 enum 未做 order 转换；1/2 字节 unsigned enum 高位值显示 `**ENUM ERR**`；HashMap 导致成员顺序不稳定；`setEnumMembers` 对缺少 `=` 的条目抛 AIOOBE。
- VLEN：datatypeSize 使用 `H5T_VL_T`（hvl_t）而非基类型大小；分配 `ArrayList[]`，元素为 `ArrayList<byte[]>`，读取走 `H5DreadVL/H5AreadVL`；不支持非字符串 VLEN 写回；应用层没有 `H5Dvlen_reclaim`，内存依赖 hdf5lib/JVM GC。
- ARRAY：保存 `arrayDims` 和 `baseType`，datatypeSize 为整体字节大小；表格显示 `[a, b, ...]`。
- opaque/bitfield：opaque 保存 tag，按字节十六进制显示（空格分隔）；bitfield 按 1/2/4/8 字节处理，按字节十六进制显示（冒号分隔）；`createNative` 用 `H5T_NATIVE_B8/16/32/64`、`H5Tcreate(H5T_OPAQUE)`、`H5Tset_size/precision/tag`，没有 UI 创建入口。
- `allocateArray` 对 opaque/bitfield 的 NATIVE 分支错误地把 `CLASS_OPAQUE/CLASS_BITFIELD` 传给需要 datatype id 的 `H5Tget_size`。

**引用**

- 支持旧式 object reference（H5R_OBJECT1）、dataset region reference（H5R_DATASET_REGION1），以及 HDF5 1.12+ 的 `H5T_STD_REF`（内部可含 H5R_OBJECT2 / H5R_DATASET_REGION2 / H5R_ATTR）。
- `H5ReferenceType.init()` 用 `H5Rget_type/H5Rget_obj_type3/H5Rget_name_string/H5Rget_file_name/H5Rget_obj_name/H5Rget_attr_name` 取描述，用 `H5Rdereference/H5Ropen_object/H5Ropen_region/H5Ropen_attr` 打开目标，使用后 `H5Rdestroy`。
- 已知缺陷：`toString` 对非 `H5T_STD_REF` 的数组路径递归调用自身，存在栈溢出风险；ARRAY/compound/VLEN 中的引用把 refSize 固定为 1；`write()` 是空实现；`setData()` 只追加不先 clear；`updateReferenceDataset` 只按 8 字节 long 更新。

**命名 datatype**

- `createDatatype` 只创建内存中的 H5Datatype；`createNamedDatatype` 在 `createNative` 后 `H5Tcommit` 到文件；`fromNative` 用 `H5Tcommitted/H5Iget_name` 标记 `isNamed`；`copyDatatype` 用 `H5Ocopy`；UI 支持复用已提交类型。h5py 无匿名 dtype commit 的高层 API，需 `h5py.h5t.py_create(dtype)` 后 `commit(group.id, name)`。

**UI 新建类型的覆盖范围**

- 支持 integer/float/char/string/reference/enum/vlen integer/vlen float/vlen string；compound 成员支持 byte/short/int、unsigned、long、float/double、string、enum、float16、long double。
- **没有 opaque/bitfield/array/complex/time 的创建入口**；`CLASS_TIME` 只有常量，`H5T_TIME` 从未映射。

### 3.4 属性层

**模型与缓存**

- 接口：`Attribute`（父对象、名称、datatype、space type、rank/dims/plane、NULL/scalar 判定、取/设值、写回、字符串化）；`MetaDataContainer`（getMetadata/writeMetadata/removeMetadata/updateMetadata/hasAttribute/clear）；`H5Attribute extends Attribute`（attributeCommonIO、attributeSelection）。
- 实现：`H5ScalarAttr extends ScalarDS` 处理标量、数值数组、定长/变长字符串、VLEN、引用、枚举、ARRAY、opaque/bitfield；`H5CompoundAttr extends CompoundDS` 处理顶层 `H5T_COMPOUND`（ARRAY/VLEN of COMPOUND 未真正支持）。
- 每个 H5Group/H5ScalarDS/H5CompoundDS/H5Datatype 内嵌一个 `H5MetaDataContainer`；`H5Link` 故意没有属性。
- **属性读取是 eager 全量**：`H5File.getAttribute` 在枚举阶段对每个属性调用 `getAttributeData()`，元数据面板一打开就把该对象所有属性值读进内存。

**枚举与排序**

- 先 `H5Oget_info` 取 `num_attrs`，再逐个 `H5Aopen_by_idx(objID, '.', idxType, order, i, ...)`；支持 NAME/CRT_ORDER × INC/DEC/NATIVE，默认 NAME/INC。`num_attrs` 被强转 int，`lsize<=0` 时跳过值读取。
- 属性数量/大小没有显式上限；HDF5 在属性超过默认 compact 阈值后转 dense attribute storage（默认 max_compact=8/min_dense=6），HDFView 从不调用 `H5Pset_attr_phase_change`。`AttrNameLen=256` 常量声明但从未使用，代码没有 256 截断逻辑。

**类型处理**

- 标量/数组属性：`H5Aget_space/H5Sget_simple_extent_*` + `H5Aget_type` + `H5Tget_native_type`，读取在 `attributeCommonIO` 中按 `allocateArray` 分配，`H5Aread` 一次读整个属性；16 字节 float 转 BigDecimal、array of Float16 转 BigDecimal、reference 转 `ArrayList<byte[]>`。
- 定长字符串：显示为普通 String，尾部空白被裁掉；写入按空格填充，且只把整个缓冲区最后一个字节置 NUL，重写已有属性不能保证原始 NUL/空格字节不变。
- 变长字符串：`H5Aread_VLStrings/H5Awrite_VLStrings`，可创建空字符串。
- 数组：ArraySize 列显示 dataspace 维度（如 `2 x 3`），Value 列最多 50 项；顶层 `H5T_ARRAY` 的子维度不出现在 ArraySize 列但值会展开；rank>2 时 `attributeSelection()` 做内存切片。HDF5 `H5Aread` 本身没有部分读，HDFView 的 subset 只是内存 arraycopy；`attributeSelection` 对 ARRAY 用 `arrayDims.length` 作为拷贝元素数，是潜在错误。
- compound 属性：用 native compound tid 执行一次 `H5Aread` 到 byte[]，再由 `compoundTypeIO` 按 memberOffset 拆成 `List<数组>`；写入重新拼字节。限制：ARRAY of COMPOUND 写抛异常；VLEN of COMPOUND 读写都抛异常/占位 `*UNSUPPORTED*`；VLEN 成员占位 `*UNSUPPORTED*`。已知缺陷：写入按 `writeListIndex` 顺序累加而非真实 memberOffset（带 padding/对齐会写错位）；成员选择索引与未选成员不匹配；file datatype 的 offset 被用于解析 native buffer，当成员 native size 与 file size 不同（Float16/BFLOAT16、long double）时读出垃圾值。
- enum/unsigned 属性：enum 值读取仍是底层整数数组，显示按数值→名称映射；unsigned 读取保留 signed 位型，显示时修正；TableView 打开属性时做扩宽/缩窄。
- 引用属性：读取先读为 byte[] 再复制成 `ArrayList<byte[]>`；`H5File.getAttribute` 枚举后对所有 ref 属性做二次遍历填充 `H5ReferenceType`；写入只支持 tmpData 为单个 String 的引用（`H5Rcreate_object + H5Awrite + H5Rdestroy`），引用数组/区域引用数组没有对称写路径。
- NULL dataspace：置 `isNULL`，表中显示 NULL，查看器拒绝打开并提示 `noDataNullDataspace`；写入用 `H5Screate(H5S_NULL)` 并跳过值写入。

**创建、更新、删除、重命名**

- 创建：`NewScalarAttributeDialog`（类型、size/order/unsigned、rank 1..32、dims）→ `createNewDatatype` + `allocateArray` 生成零值/空字符串 → `writeAttribute()`。最终 `H5File.writeAttribute`：`createNative`、`H5Screate(NULL/SCALAR)` 或 `H5Screate_simple`、`H5Acreate`（ACP L/FAPL 都是 `H5P_DEFAULT`）。从不调用 `H5Pset_attr_creation_order`；组件创建时也没开 `track_order`。
- 更新：独立 TableView 修改后 `updateValueInFile` → `Dataset.write()` → `H5ScalarAttr.write`（只读检查、setData、`writeMetadata(this)`、随后又直接 `scalarattributeCommonIO(WRITE)`，**同一值写两次**）。`H5File.writeAttribute` 对已存在属性走 `H5Aopen_by_name`，否则 `H5Acreate`，然后用 attr 的 native type 执行 `attributeCommonIO(WRITE)`。
- 删除：元数据页 Delete → `removeMetadata` 只从内存 List 移除并更新 nAttributes；真正的 `H5Adelete` 在各对象子类。删除失败只记日志。
- 重命名：`H5Arename_by_name`；表格文本更新，但 Attribute 对象自身的内存 name **不更新**，后续 `writeAttribute` 会按旧名 `H5Aopen_by_name` 失败。
- 属性显示：Object Attribute Info 页四列 Name/Type/ArraySize/Value；Type 列用 `H5Datatype.getDescription()` 文本；Value 列最多 50 项；双击属性行或右键 View/Edit 会把属性当 Dataset 在独立 TableView/ImageView 打开。`NewCompoundAttributeDialog` 实际未接线且会 NPE，全仓库无调用点；`updateAttributeValue` 是死代码。

**属性与对象交互**

- 属性可挂在 group、dataset、committed datatype；soft/external link 本身没有属性（需先解引用到目标对象）；对象复制走 `H5Ocopy` 时属性自动带上；另存为时 root group 属性通过反射调用 `H5File.copyAttributes` 显式复制（固定 `H5_INDEX_CRT_ORDER`，在未跟踪属性创建序的普通对象上会失败且异常被吞）。
- 图像/调色板/CF 语义全部由属性驱动：`CLASS / IMAGE_MINMAXRANGE / IMAGE_SUBCLASS / INTERLACE_MODE / PALETTE`，以及 CF 的 `_FillValue / valid_range / valid_min / valid_max`。

### 3.5 对象层实现要点与已知缺陷

**高优先级（阻断正确性或必须重新设计）**

1. 整页内存模型 + 2^31 点数限制：`getData()` 一次分配整个 selection，`H5Utils` 强制总点数 ≤ `Integer.MAX_VALUE`。
2. compound 逐字段 I/O 放大 N 倍，宽 compound 会重复解压/过滤 chunk。
3. compound 写回索引 bug + 非原子写：部分成员未选中时数据错位；单成员失败后继续，可能半写。
4. Float8/Float16/BFLOAT16 workaround 脆弱：新建 float16 实际变 float32；读取依赖 HDF5 2.0 转换，而 CLAUDE.md 明确 HDF5 2.0.0 有回归。
5. unsigned 是显示层补丁：u64 无法正确显示，compound unsigned 未实现。
6. 定长字符串往返不保真：平台默认字符集、裁剪尾部 ≤0x20、按 Java char 截断；cset 被忽略。
7. 属性写入语义：`H5ScalarAttr.write` 重复写；h5py `attrs[name]=` 是 delete+recreate，会改变 dtype/shape/创建序。
8. 引用语义不可移植：8 字节 vs 64 字节 H5R_ref_t；refSize 固定 1；`write()` 空实现；setData 不 clear；toString 递归。
9. dimension scales 完全缺失。
10. eager 整树加载与 eager 属性读取。
11. 异常吞掉导致空树/空数据。
12. compound 属性手工字节逻辑不可靠。

**中优先级**

- open/close 状态机与句柄语义；`get()` 留下 fid 导致层级树永不加载；`close` 强制关闭全部子对象。
- 链接可见性与目标归属；external link 解引用后的对象属于目标文件。
- user-defined link 不可创建/跟随。
- track_order 只在创建时决定；文件级 indexType 偏好无法复用。
- 复制语义：dataset 展开引用、group 不展开；`copyGroup` 跨文件读错文件。
- libver bounds 与 split/family 打开。
- 外部存储搜索目录依赖 cwd。
- filter 读取/创建能力差距；SHUFFLE Nbytes 展示错误。
- fill value 解析不可靠；scalar/NULL dataspace 混淆。
- selection 无对象层校验 + 静默 clamp。
- int32 限制贯穿 UI。
- 每次关闭 dataset 都 `H5Fflush`，高频翻页产生大量 flush。
- 属性容器缓存缺陷（updateMetadata 不刷新列表、clear 不重置 nAttributes、rename 后 name 陈旧）。
- rank>2 属性编辑风险：`attributeSelection` 换成 2D 切片，保存只写回切片，可能失败或破坏原属性。
- enum 缺陷（大端、unsigned 高位、HashMap 顺序）。
- VLEN/ARRAY-of-compound 能力不完整。
- precision/offset/pad 不保留。
- H5T_TIME/H5T_NO_CLASS 未实现。
- complex 创建/读取限制；long double 缺陷。
- 命名 datatype 与类型等价；user block 写入路径。
- split/family/SWMR 打开；可变 selection 全局状态。

**低优先级**

- 调色板 768 字节限制（HDFView 实现限制，非 HDF5 语义）。
- `AttrNameLen` 假截断。
- UI 死代码（updateAttributeValue、NewCompoundAttributeDialog、过时帮助文本）。
- H5Link 无属性；`toString` 递归；`allocateArray` opaque/bitfield bug。
- `copyAttributes` 固定 CRT_ORDER。
- 派生类型创建入口不完整。
- 属性 inspect 失败的 NPE。
- 根路径语义（root path=null vs `/`）。
- 共享 FileFormat 模板状态。
- `H5Pget_filter` 参数上限 20。

---

## 4. GUI 层 HDF5 功能

### 4.1 主窗口与文件生命周期

- 主窗口：菜单栏 → 工具栏 → 最近文件/URL/访问模式栏 → 垂直 SashForm（上部水平 SashForm：左树、右 TabFolder；下部状态文本区）→ 状态栏。命令行支持 `-root`、`-start`、`-geometry` 和若干文件/目录参数。
- 打开：`File > Open`、工具栏、`Ctrl+O`、OS 拖放；文件对话框支持多选。格式由 `H5Fis_hdf5` 判定，扩展名只用于过滤。访问模式 READ/WRITE/CREATE/MULTIREAD；WRITE 失败会**静默降级 READ**。
- 关闭：删除该文件的顶层树节点、关闭所有 DataView、清理 Tab、移出 fileList；**关闭文件本身不弹保存确认**，TableView 销毁时才提示。
- 保存：`File > Save` 只把脏 TableView 缓冲写回当前文件；`Save As` 不是字节复制，而是重建 HDF5 文件（复制已加载根成员、复制根组属性、反射调用 `updateReferenceDataset`）；`Save to` 把选中子树复制到新文件。三者语义必须显式区分：flush 脏数据集、物理克隆/repack、子树复制。
- 最近文件：最多 15 项；打开文件或在树中单击文件节点都会移动条目；地址栏支持 http/https/ftp，先下载到临时目录再打开。
- 多文件："当前文件"只表示最近点击/右键的文件；关闭、重载、保存、索引、访问模式都作用于它。

### 4.2 树视图与右键工作流

- `SWT.VIRTUAL` Tree 只在展开时创建 TreeItem；但对象模型仍是全量预加载。
- 节点类型与图标：文件根（HDF5 图标，只读用 R 变体）、Group（开/关文件夹，带属性加 A 角标）、标量数据集（图像/文本/普通）、复合数据集（表格）、命名 datatype、H5Link（question 图标）。
- 右键菜单全集：Open、Open As、Open Source Files（仅 VDS）、New（Group/Dataset/Image/Compound DS/Datatype/Link）、Cut、Copy、Paste、Delete、Export Dataset（Text/Native/Little/Big Endian）、Save to、Rename、Change file indexing、Find、Expand All、Collapse All、Close File、Reload File、Reload File As（Read-Only/SWMR/Read-Write）、Set Lib version bounds。
- 启用矩阵：只读禁编辑；根组禁 Cut/Delete/Rename/Save to；导出只对 ScalarDS；索引与库版本只对 HDF5 根组且可写；New Link 仅 HDF5；Open Source Files 仅虚拟数据集。
- 新建组：名称、父组、creation order tracked/indexed、link storage compact/dense 边界（默认 8/6）、`0 < maxCompact <= 65536`、`maxCompact >= minDense`。
- 新建数据集：共享 datatype 控件；维度用 `x` 分隔；maxdims 中 `u` 无限、`0` 同当前；contiguous/chunked、gzip、fill value；可从表格选区或图像选区创建。
- 新建图像：height/width；Indexed Colormap 或 True Color 24-bit；索引 `{h,w}`、真彩 pixel `{h,w,3}`、plane `{3,h,w}`；1 字节 char。**已知缺陷**：索引分支未把 interlace 改为 -1，实际写出 `IMAGE_TRUECOLOR + INTERLACE_MODE`，不是 `IMAGE_INDEXED + PALETTE`。
- 新建复合数据集：Rank 1..32、Current/Max Size（0=当前、-1=unlimited、<-1 拒绝）、Contiguous/Chunked、Chunk Size、GZIP 0..9、成员表 Name/Type/Size；成员只能按数量从尾部增删，不能重排或单独删除；Size 语义随类型变化（数值=array 长度，字符串=字节长度，enum=定义字符串），解析失败静默用 1；创建结果为空数据集（fill value）。
- 新建链接：Hard 从当前文件对象列表选目标；Soft 允许手工路径甚至悬空；External 选目标文件并解析对象路径；禁止链接根组。
- 新建命名数据类型：`H5Tcommit` 提交到文件。
- 复制/剪切/粘贴/删除/重命名：Copy 存入应用内部剪贴板；Cut 进入移动模式；Paste 复制/移动到当前选中组；Delete 确认后立即删除；Rename 用输入框。根组不可删除/重命名/移动；被打开的对象不可删除/移动；移动只能同文件；重名自动追加 `~copy`；跨文件只允许 HDF5→HDF5；复制 Group 时只立刻补建第一层子节点；对象引用不重映射。
- 导出数据集：Text/Native/Little/Big Endian；只对 ScalarDS；目标文件已存在需确认，已被 HDFView 打开则拒绝；底层 `H5export_dataset`（binaryOrder 99=text、1/2/3=native/little/big）。
- 查找：前缀 `*name`、后缀 `name*`、包含 `*name*`、精确匹配；只在当前选中节点之下、且已经实际创建出来的 TreeItem 中做广度优先搜索；不支持正则；未展开就搜不到。
- 索引/库版本/虚拟数据集：Change file indexing 修改后关闭并重开文件；Set Lib version bounds 只改 FAPL，不转换已有对象；Open Source Files 用 `virtual_sources()` 获取映射并逐个打开源文件。
- UI 刷新：无事件总线；选择变化直接调 `showMetaData`；新建对象调 `TreeView.addObject`；属性增删调 `updateItemIcon`；通过扫描 `Display.getShells()` 找视图；Window 菜单项由 `addDataView/removeDataView` 增删；可选 1 秒定时器刷新 TableView；无文件系统监视器，外部修改只能靠 Reload File 反映。

### 4.3 表格视图

**澄清："虚拟分页"不是滚动懒加载**

`loadData(DataFormat)` 调用 `DataFormat.getData()` 把当前选择区**一次性读入** Java 数组/List；NatTable 的 `ViewportLayer` 只虚拟化绘制，不虚拟化数据读取。所谓"分页"是：

- rank > 2 默认强制 `selectedDims[selectedIndex[2]] = 1`，第三显示维只读一帧；
- 工具栏首/上/下/末帧和帧号输入调用 `gotoFrame`：先检查脏数据并 `updateValueInFile()`，再校验帧范围、设置 `start[selectedIndex[2]]`、`clearData()`、`getData()` 重读整页、更新 provider、刷新统计高亮；
- rank > 3 的其余维在 `DataOptionDialog` 中固定为单切片（默认索引 0）；
- 行列数受 NatTable int32 限制；CompoundDS 高度 > 10000 时关闭 byte→String 的急切转换。

**Open As / DataOptionDialog**

可配置：显示为 Spreadsheet 或 Image；char 数据显示；表格/图像视图实现类；图像调色板（文件内调色板 + Gray/ReverseGray/GrayWave/Rainbow/Nature/Wave）、有效值域、无效值列表；Index Base 0/1；bitmask（逐位选择、Extract Selected Bits、掩码应用）；compound 成员选择；最多 3 个显示轴的 start/end/stride；轴顺序/转置只在行轴下标大于列轴时允许；更高维固定切片 0。OK 后设置 `isImageDisplay` 并把配置 map 传给视图。

**数据提供者、显示转换器、校验器**

- `DataProviderFactory` 按 Datatype 递归创建：`HDFDataProvider`（扁平索引、物理位置映射、脏值索引、3D 更新缓冲、updateAtomicValue）、`CompoundDataProvider`、`ArrayDataProvider`、`VlenDataProvider`、`StringDataProvider`、`NumericalDataProvider`、`EnumDataProvider`、`BitfieldDataProvider`、`RefDataProvider`、`ComplexDataProvider`。
- `DataDisplayConverterFactory` 负责 canonical 值 ↔ 显示字符串：默认十进制、科学计数、自定义 DecimalFormat、十六进制、二进制、enum 名称、bitfield/opaque 逐字节 hex、array/vlen 括号、compound 花括号、引用字节。
- `DataValidatorFactory` 在提交前校验：数值范围（含 unsigned 边界）、固定字符串长度、array/vlen 元素数量与逐元素校验、enum 底层整数、complex 两分量；失败抛 `ValidationFailedException`。
- 三个 factory 都持有 static `dataFormatReference`，同一时刻只支持一个"当前 dataset 上下文"，多视图/多数据集并发不安全——迁移时绝不能沿用。

**显示格式**

- Data Display 子菜单：Scientific、Custom、Create Custom、Hexadecimal、Binary、Enum Values；这些 CHECK 项互斥。
- Hex/Binary 只对整数、bitfield、opaque 显示；enum 转换只对整数类显示。
- **Hex/Binary 显示时禁止编辑**。
- 标量菜单还有 Fixed Data Length（固定宽度文本解析）和二进制导入/导出。

**编辑与写回**

- 编辑触发：双击单元格或直接键入字母/数字。
- 编辑只改内存 `dataValue` 与 `IDataProvider`，并置脏标志/记录 changed indices。
- `Save Changes to File`（Ctrl+U）→ `updateValueInFile` → `Dataset.write()`，按当前 start/stride/count 写回当前页；只读、无变化、或显示为 hex/binary 时跳过。
- `gotoFrame` 切帧前自动保存脏页；`prepareForCleanup` 关闭视图时提示未保存。
- 必须区分三层状态：原始 HDF5 数据、当前页 numpy 缓冲、渲染/显示转换。

**剪贴板、文本/二进制导入导出、复制为新数据集**

- 系统剪贴板：选区按行 tab 分隔、换行结尾的纯文本；粘贴按行/制表符填充到当前选区起点，超出表格部分丢弃；无转义，单元格含 tab/换行会不可逆损坏。
- 文本导出：当前表格全部行列；分隔符来自 Preferences 的 `data.delimiter`（Tab/Comma/Space/Colon/Semi-Colon）；CompoundDS 在配置分隔符后强制追加 tab；无引号转义。
- 文本导入：从选区左上角开始逐行填充到表格边界；支持配置分隔符或固定长度；只改内存，需再 Save Changes；坏值 catch 后 continue 静默跳过。
- 二进制导出：仅 ScalarDS；不写形状/头部/类型元信息；导出前对无符号数据 `convertToUnsignedC`；按 B/S/I/J/F/D 分支用 ByteBuffer 分块写。
- 二进制导入：仅 ScalarDS；按指定字节序填充当前内存缓冲；读取长度取 min(文件大小, 数据集字节数)，不足保留原值；只改内存；Java 忽略 `InputStream.read` 返回值，截断文件会静默接受。
- Copy to New Dataset：选中矩形单元格后弹 New Dataset 对话框，按 `[选中行数, 选中列数]` 创建新数据集并写入选区；无符号源先转换；新对象加入树。

**数学转换**

- 对选中数据执行 clip、abs、线性 `a + b*x`、幂 `x^a`、exp、ln、log_a、sin、cos、tan；按 Java 数组类型做范围检查后原地修改传入数组。
- 只读直接返回；CompoundDS 选中多于 1 列报错；无选区报错；成功后 arraycopy 回 `dataValue`，置脏标志，调用 `System.gc()`（迁移时删除）。

**折线图与统计入口**

- 折线图：选择按行/按列、可选 X 轴列/行、最多 10 条线、丢弃 NaN/INF；标题包含数据集路径与 by row/by column；`Chart.LINEPLOT` 自绘，默认只有 8 种颜色，第 9/10 条复用最后颜色。
- 统计按钮/菜单：ScalarDS 打开统一统计窗口；CompoundDS 走 legacy 单列 min/max/mean/sample-std 信息框。

**数据类型覆盖与限制**

- 覆盖有符号/无符号整数（含 uint64→BigInteger）、float/double（含 float16/BFLOAT16 兼容路径）、char、固定/变长字符串、enum、bitfield、opaque、object/region/standard reference、complex、compound（嵌套、array-of-compound、vlen-of-compound）。
- 限制：table 行/列 32 位；整页急切加载；enum 在搜索/统一统计中因 `CLASS_ENUM != CLASS_INTEGER` 被判不支持；uint64 显示/搜索走 BigInteger/BigDecimal；CompoundDS 的 unsigned 转换在表格显示层缺失（显示负值）；显示格式切换与编辑互斥。

### 4.4 图像、调色板、图表

**图像识别**

- HDF5 ScalarDS 只有带字符串属性 `CLASS="IMAGE"` 时才被视为图像。`hasAttribute()` 读取 CLASS 并设置 `isImage/isImageDisplay`；`init()` 在 `isImage && rank>=3` 时读取 `IMAGE_SUBCLASS`，设置 `isTrueColor/interlace`。真彩要求 `IMAGE_SUBCLASS="IMAGE_TRUECOLOR"` 且 rank>=3，仅支持 3 个颜色分量；其余（rank 2 索引图、rank>2 非真彩帧序列）走索引色路径。
- 顺序依赖缺陷：`hasAttribute` 通常由树图标触发；如果先 init 后读属性，`interlace=-1`、`isTrueColor=false`。Python 必须显式、幂等地读取 `CLASS/IMAGE_SUBCLASS/INTERLACE_MODE/PALETTE`，对 bytes 做 `strip().upper()`，禁止依赖 UI 副作用。
- 默认路由：`isImage()` → 独立 ImageView；否则内联表格。双击组展开；NULL Dataset 提示无数据。

**索引色渲染、调色板与无效值**

- 索引图先转成 256 级 8 位索引，再查 256 项调色板生成 RGB。
- 缩放公式 `y = (x - min) * 255 / (max - min)`；1 字节数据且范围 [0,255] 时直接使用原字节；小于 min、大于 max、等于 fill value、NaN、Inf 的像素写 0，并标记为完全透明（ARGB alpha=0）——这是"越界置 0 + 透明"，不是 clip。
- 数据范围优先 `IMAGE_MINMAXRANGE`，其次 CF 的 `valid_range/valid_min/valid_max`，否则转换时现算 min/max；fill value 自动加入 `filteredImageValues`；无 PALETTE 属性时用 Gray 并提示。
- 内置调色板固定 256 项：Gray、Reverse Gray、GrayWave、Rainbow、Nature、Wave。Open As 与调色板视图还可选用户导入的 `.lut` 文件与文件内多个调色板。
- PALETTE 属性是对象引用，可为一维数组；每个被引用数据集按 768 字节 RGB 三元组读取，不足 768 返回 null，超过 768 忽略，转换为 `byte[3][256]`；调色板名称取被引用对象的 HDF5 路径。

**真彩色、交织、帧、方向**

- `INTERLACE_PIXEL`：`[height][width][3]`；`INTERLACE_PLANE`：`[3][height][width]`；每个分量按统一范围线性转 8 位，打包为不透明 ARGB。`INTERLACE_LINE` 常量存在但未实现。真彩不参与调色板、直方图和帧切换；即使 rank>3 也不显示帧控件。
- 非真彩 rank>2 按帧序列显示：帧轴由 `selectedIndex[2]` 指定，默认 `{rank-3, rank-2, rank-1}`；工具栏首/上/下/末帧 + 帧号；读取时 `selectedDims[frame]=1`；帧号遵循 Index Base。
- 图像原点（UpperLeft 默认、LowerLeft、UpperRight、LowerRight）是全局用户偏好，不是 HDF5 属性；渲染后通过水平/垂直翻转或旋转 180° 实现，并显示 `(0,0)`/`(1,1)` 角标；用户还可额外 Flip Horizontal/Vertical、Rotate CW/CCW 90°；旋转计数非 0 时禁用直方图、Show Values 等菜单。
- 已知缺口：`getSelectedData` 只补偿 flip，不处理 rotation；旋转后选区/统计映射不完整。

**交互、动画、导出**

- 缩放通过菜单/工具栏按钮，范围 1/8..8 倍；>1 步长 1，<1 步长 0.125；图像放在 ScrolledComposite 中；左键拖动平移，单击把点击点移到视图中心；Shift+左键拖出矩形选区（红框）；Show Values 后悬停显示 x/y/value（真彩 RGB）；滚轮只刷新读数不缩放。
- 动画仅 `is3D`（非真彩 3D 索引图）可用；模态窗口 300×300，速度 2/4/6/8/10 fps，默认 2 fps；播放前一次性读取全部帧；若空间最大边超过 300，对两个空间轴做整数步长抽样降分辨率；帧缓存在内存循环显示。
- 导出：Image > Save As 支持 JPEG/PNG/GIF/BMP（TIFF 被注释）；导出当前渲染图（已应用调色板、亮度/对比度、翻转、轮廓），不包含 zoom，也不裁剪选区；JPEG/BMP 把 ARGB 转 RGB，透明区域以黑色填充。
- 选区操作：Histogram 要求非空选区，统计选区内 8 位 imageByteData 的 256 bin 频次，横轴用原始数据范围；Statistics 对选区（无选区则整图）计算 min/max/mean/std，排除 fill value，真彩按 3 分量展开；Write Selection 在可写且选区非空时把选区原始数据复制成新数据集。
- Value Range：真彩禁用，min==max 禁用；上半部 256-bin 原始数据分布柱状图，下方上下界文本 + 10 档滑块；Apply 按新范围重映射，Cancel 恢复。
- Brightness/Contrast：滑块 -100..100，Apply/OK 作用于渲染图，Cancel 恢复；自动增益模式下改名为 Bias/Gain。
- Contour：3/5/7/9 级，对渲染图灰度 `(R+G+B)/3` 求等值边界并涂循环颜色；重复应用累积；只改显示。
- 自动增益（Auto Gain）：仅用户选项启用、索引图无附加调色板、本地文件、整型数据时生效；用直方图/最小最大计算 gain/bias，把原始整数映射到类型满量程；之后亮度/对比度变为 Bias/Gain；float 数据不走该路径。

**调色板查看与编辑**

- 模态对话框：三条 R/G/B 曲线、Red/Green/Blue 单选、鼠标拖拽改通道、256 行数值表、Preview/OK/Cancel、文件内调色板与内置/导入调色板下拉。
- **所有修改只在当前 ImageView 内存中生效，不写回 HDF5 PALETTE 属性**；OK 只调用 setPalette/setImage，关闭窗口即丢失。已知缺陷：`mouseDown` 中 `dragX0/dragY0` 被注释掉，拖拽直线锚点实际是 `(0,0)`——迁移时不要复制，应实现"按下点—当前点"插值。
- LUT 导入/导出：Import Palette 只把绝对路径加入全局调色板列表，不立即应用；Export Palette 把当前 imagePalette 写成 256 行 `index R G B` 文本。`Tools.readPalette` 支持 2..256 项、四列 `value R G B`；颜色最大值 ≤1 视为 0..1 浮点并线性放大到 0..255；缺失索引用相邻项线性插值；索引 0 固定黑。

**新建图像与图片转 HDF5**

- 新建图像：注意索引属性写入缺陷；Python 必须显式写 `CLASS=IMAGE`、`IMAGE_VERSION=1.2`、`IMAGE_MINMAXRANGE`、`IMAGE_SUBCLASS=IMAGE_INDEXED` 与 PALETTE 引用（可创建全灰 palette 数据集），或 `IMAGE_TRUECOLOR + INTERLACE_MODE`；数据类型用 uint8。
- 图片转 HDF5：只选源图片和目标 `.h5`，`ImageIO.read` 后按 ARGB 提取 R/G/B、丢弃 alpha；创建 `[height][width][3]` 的 8 位无符号数据集，名称为源文件名（含扩展名），写 `IMAGE_TRUECOLOR + INTERLACE_PIXEL`；成功后自动在树中打开目标文件。

**图表**

- `Chart` 只有两种图：HISTOGRAM（竖直柱状，可改柱色）和 LINEPLOT（多折线，最多 10 条序列，默认 8 种颜色，第 9/10 条复用最后颜色；图例仅在 lineLabels 存在时绘制）。
- 两者都用 SWT GC 手工绘制坐标轴、固定 10 个 x/y 刻度、字符串标签、图例框、线段/矩形；无抗锯齿、无鼠标缩放/平移、无 tooltip、无导出、无坐标轴标题。没有面积图、散点图、饼图、3D 图、堆叠图。

### 4.5 元数据视图

- 固定两个顶层标签：Attributes（索引 0）与 General（索引 1）；子类只向 General 页追加专属内容。宿主是 TabFolder 时按 `TAB_ROLE_KEY` 复用 TabItem、仅清空并重建页面；父容器记住上次标签索引。
- Attributes 页：Name/Type/Array Size/Value 四列；HDF5 额外显示属性创建顺序状态（not tracked / tracked / tracked+indexed）与总数；NULL 属性显示 NULL，标量显示 Scalar，其余显示 `d1 x d2 ...`；值最多显示前 50 个元素；compound 字段属性名显示为 `name (field)`；双击行把属性作为 Dataset 在 TableView 打开；右键 Rename/View-Edit/Delete。
- 增删改：Add 只读时禁用；HDF5 打开 `NewScalarAttributeDialog`；Delete 仅 HDF5 且非只读，删除前确认；Rename 仅 HDF5，底層 `H5Arename_by_name`；成功后只更新表格行、计数和树图标，不整体刷新文件树。
- General 页：所有对象显示 Object Name、Object Path（null→`/`）、Object Type、Object Reference（OID[0]）；Root group 额外显示文件大小 KB、文件名、父目录、文件类型+对象计数、HDF5 Library Version Bounds、Show User Block 按钮；对象计数遍历 `depthFirstMemberList`，任何非 Group 对象都计入 datasetCount（包括 datatype/link）。
- 类型专属：Dataset 显示 Rank/Dimensions/Max Dimensions、datatype 描述、Storage Layout、Compression、Filters、Storage、Fill Value、Show Data with Options；CompoundDS 额外成员表；Datatype 只读多行 Text 显示全量描述；Group 显示 Link Creation Order 状态、成员计数、成员表；Link 显示 Link Target Info 文本框，回车/失焦可修改目标（禁止指向 `/`，覆盖同名链接）。

### 4.6 搜索与统计

**Dataset 搜索**

- 范围是当前打开的所有文件；对每个 root 做 `depthFirstMemberList`，仅保留 Dataset，按 `filePath+fullPath` 去重；不搜 Group/Datatype/Attribute，不跟随外部链接新开文件。
- 两种模式：Dataset Name（对 basename 做 Locale.ROOT 小写后的子串匹配）；Data Value（仅 integer/float/string/char，array 递归取 base）。数值：整数查询用 BigInteger 精确比较；浮点查询尽量用 BigDecimal.compareTo，否则 Double.compare；非法数值查询静默返回。字符串/字符：小写子串匹配。
- 引擎按 65536 cell 分块：`BlockIterator` 克隆 shape 后反复把最大维度 ceil 减半直到元素数 ≤ 上限，按行主序产生块；每块改写 scanner 的 start/count/stride 后 read()；原生 I/O 由 `NATIVE_IO_LOCK` 串行化；每个 Dataset 用反射新建 fresh Dataset，避免覆盖 TableView 缓冲；每块检查取消；OOM/异常按 Dataset 隔离；结果最多展示 5000 行，按 100 条批量回调，totalMatches 继续全量计数。
- 脏 TableView 缓冲 overlay：搜索启动时收集内联 TableView 与所有带 TableView data 的 Shell，先提交活动单元格编辑器，再 `getSearchSnapshot`：有变更索引→稀疏快照（排序去重 cell、映射全 Dataset 坐标、按 cell 打包标量）；否则稠密快照（整页数据+start/count/stride/dims/datatype）。引擎按 canonical path 匹配快照，在块读取后只覆盖相交脏值；快照不写回、不清理 dirty flag。
- 结果身份：只存 filePath/datasetPath/matchedValue/zero-based coordinate/MatchType，不持有 TreeItem/显示文本；激活时按 canonical 路径重解析文件与 Dataset，校验后 selectObject→showInlineDataContent→navigateToIndex。不可解析显示 staleResult。搜索没有持久化高亮层，唯一视觉反馈是选中并滚动到单元格。

**Dataset 统计**

- 无模式窗口；后台线程计算，进度/结果/取消/错误经 `Display.asyncExec` 回 UI；关闭窗口或切换对象 cancel；generation 防旧结果落地。
- 范围 Current Page（对当前 dataValue 快照计算，无原生 I/O）或 Entire Dataset（fresh Dataset 分块读全量，块 65536 cell，两遍扫描）。
- 统一窗口只有 10 行：Total、Zero、Non-Zero、Positive、Negative、Non-Negative、Minimum、Maximum、Mean、Standard Deviation；**没有 median/quantile/percentile/mode**。
- 语义：total 遍历所有标量（含 fill/NaN/Inf/bool）；zero = numeric==0，nonZero = !=0（NaN 计入 nonZero）；positive/negative/nonNegative 严格 >0/<0/>=0（NaN 三者都不计）；numericCount 排除 fill；mean=sum/numericCount（n>1），仅 1 个非 fill 值为 0、0 个为 fill（无 fill 则 0）；stddev=`sqrt(Σ(x-mean)^2/(n-1))`（n<=1 为 0），方差累加不排除 NaN/Inf；min/max 用第一个原始值播种，之后跳过 fill/NaN/Inf；布尔 true=1/false=0；显示格式 `%.12g`。
- 高亮遮罩：仅当前显示页支持 Zero/Non-Zero/Positive/Negative 四类（没有 Non-Negative 按钮），Clear 清除；用 `HashSet<Long>(row<<32 ^ column)` 记录；整页刷新遍历 dataLayer 重建；单元格编辑通过 DataUpdateEvent 只更新该格；切页/refreshDataTable 重建；关闭/清理清空；颜色来自 ThemeManager。
- 限制：CompoundDS 不走统一引擎，菜单转 legacy 单列统计（必须整列选中）；字符串/compound/ref/enum 抛 UnsupportedDatatypeException；整数据集两遍 I/O，第二遍进度从 0 重新开始；array dtype 每块内存放大。

### 4.7 新建对象与属性对话框

- NewScalarAttributeDialog：名称、datatype（Use Committed Type、Class、Size、Byte Ordering、Unsigned）、Dataspace Rank 1..32（默认 2）+ Current Size（默认 `1 x 1`）；**没有初值输入框**，创建后属性值全 0/空；校验名称非空、size token 数 ≥ rank、每维 Long 且 >0；创建 H5CompoundAttr 或 H5ScalarAttr，allocateArray 分配缓冲，writeAttribute 写出。Help 文本仍描述 value 输入，是过期文案；Parent Object 标签实际未填值。
- NewStringAttributeDialog：HDF4/NetCDF 专用，HDF5 不使用，迁移直接删除。
- NewCompoundAttributeDialog：名义上的 HDF5 compound 属性对话框，实际**未接线且会 NPE**：open() 从不调用 createDatatypeWidget()，createAttribute() 收集的 mNames/mDatatypes/mOrders 从未使用，随后 createNewDatatype(null) 解引用未创建的控件；全仓库无调用点。迁移时必须删除或重写。
- NewCompoundDatasetDialog：成员不支持重排/单独删除；模板导入按 Dataset 名称匹配、重名取第一个且只解析 GZIP；chunk 校验比较元素数而非字节数，HDF5 的 4 GiB chunk 上限未完整检查；创建结果为空数据集；默认 chunk 仅在 extendable 且 chunks=null 时按每维 min(dims,64) 生成。

### 4.8 导入导出与外围

- 表格文本/二进制导入导出、剪贴板、Copy to New Dataset：见 4.3。
- 树右键 Export Dataset：见 4.2；`H5export_dataset` 无 h5py 等价物，文本排版由原生代码决定，迁移前必须用样本逐字节比对。
- Save to：把选中对象（组递归）`H5Ocopy` 到新建文件根组，不复制源根组属性、不做引用重写，成功后打开新文件。
- Save As：语义是"重打包"，不是字节复制，会丢失 User Block、文件空间策略、superblock 版本、共享消息、未加载对象；归档场景应改用 `shutil.copy2` 或 `h5repack`。
- User Block：根组 General 页 Show User Block 按 Text/Binary/Octal/Hexadecimal/Decimal 显示偏移之前的块；文本模式可编辑并 Save；块大小不增大时直接 RandomAccessFile 原位写入，增大时提示重写整个文件。Java 同大小分支未先关闭文件，存在元数据损坏风险，不要复制。
- libver：新建文件使用 Preferences 的默认 early/late；Set Lib version bounds 修改当前文件 FAPL 并刷新元数据；已存在文件的展示值不代表既有对象编码。
- H5PL 插件路径：启动时把 `<root>/plugin` 前插。
- 版本显示：`HDFVersions` 从 classpath 的 versions.properties 读取 HDFVIEW/HDF5/HDF4/Java 版本（构建时资源过滤）；Help > HDF5 Library Version 显示构建版本而非运行时库版本；About 显示 HDFView 版本与 OS。
- 文件占用检测：工具栏 File Usage 打开无模式对话框；后台单线程扫描当前文件，显示 PID/进程/Restart Manager 类型/可见窗口标题/可执行路径、外部占用计数；Refresh 手动刷新；Request Close 向可见顶层窗口 `PostMessageW(WM_CLOSE)` 并等待 1.5 秒；Force Terminate 强制终止并等待；禁止对当前 HDFView、Windows 服务、关键系统进程操作；操作前重新扫描并校验 PID+启动时间防复用；非 Windows 显示不支持。
- i18n：基于 ResourceBundle + MessageFormat，语言 en/zh_CN，禁止 JVM 默认 Locale 回退；控件 data 中保存 key/args，refreshDisplay 遍历 Shell/菜单/表格/Tab/ToolBar 重新应用；英文/中文资源各 1156 个 key。
- 配置：`ViewProperties` 继承 JFace PreferenceStore，属性文件 `.hdfview<VERSION>`（user.home 优先、user.dir 次之、`-Dhdfview.propfile` 覆盖）；包含分隔符、图像原点、HDF5 index type/order、默认 early/late libver、enum 转换、regref 显示、读模式、扩展名、字体、模块、最近文件、调色板、H5PL 插件路径。大多数偏好改内存、主窗口关闭时统一写文件；语言/主题切换立即写文件。
- 文件过滤：默认扩展名含 hdf,h4,hdf4,h5,hdf5,he2,he5；HDF5 专用过滤器为 h5,hdf5；HE5 是 HDF5 容器。
- 远程文件：URL 下载到系统临时目录，文件名把斜杠替换为 `@`；Java 不校验缓存新鲜度。
- User Guide：用系统默认浏览器打开本地/在线 HTML；HelpView 是类加载式插件，Python 无等价物，简化为 `webbrowser.open`。

---

## 5. HDF5 功能清单（按 HDF5 概念汇总）

### 5.1 文件与组织

| 功能 | Java 支持程度 | 说明 |
|---|---|---|
| 打开/创建/关闭 | 完整 | r / r+ / w；CREATE 两步；关闭时强制关闭该 fid 下全部子对象 |
| 访问模式 | 完整 | READ / WRITE / CREATE / MULTIREAD(SWMR 读)；WRITE 失败静默降级 READ |
| SWMR | 只读 | 无 SWMR 写 |
| libver bounds | 完整 | Earliest/V18/V110/V112/V114/V200/Latest；已打开文件改 bounds 不转换格式 |
| user block | 读/改/不能创建 | 按 signature 扫描；大小 512×2^n；增大需整文件重写 |
| 超级块版本 | 不显示 | 代码无 H5Fget_info；可新增 |
| 文件锁 | 依赖 HDF5 默认 | 无 H5Pset_file_locking；Windows 文件占用检测是 Restart Manager，不区分读写锁 |
| split/family | 仅打开回退 | 无创建入口 |
| 文件镜像/ROS3/网络文件 | 无 | — |
| 文件级属性 | Group/Dataset/Datatype 属性 | link 本身无属性；soft/external link 需解引用 |
| 组成员枚举/顺序 | 完整 | NAME/CRT_ORDER × INC/DEC/NATIVE |
| 路径查找 | 完整但有状态机缺陷 | 内存树 + 懒加载 |
| 对象身份 | oid 引用 | equals 是引用相等；hashCode 只用 oid[0] |

### 5.2 数据集与存储

| 功能 | Java 支持程度 | 说明 |
|---|---|---|
| scalar / compound 数据集 | 完整 | compound 逐字段读写 |
| dataspace / rank / dims / maxdims | 完整 | scalar=rank0；NULL dataspace 混淆 |
| hyperslab 选择 | 完整（规则矩形） | start/stride/count；无点选择/负步长 |
| 整页读写 | 完整 | 一次分配整个 selection；2^31 点数限制 |
| chunking | 读/创建 | 创建只支持 chunked+contiguous |
| 压缩/过滤器 | 读完整/创建仅 gzip | 识别 deflate/szip/shuffle/fletcher32/nbit/scaleoffset/用户 filter；创建只有 `H5Pset_deflate` |
| fill value | scalar 读/创建 | compound 忽略；parseFillValue 不可靠 |
| 存储布局 | 读完整/创建部分 | 识别 contiguous/chunked/compact/virtual；创建仅 chunked/contiguous |
| external storage | 只读 | 仅 `H5Pget_external_count`；不能创建/编辑 |
| virtual dataset | 只读 | 解析 virtual map；Open Source Files；不能创建 |
| dimension scales | **无** | 无 H5DS/DIMENSION_LIST/REFERENCE_LIST |
| 数据转换 | 完整 | H5Tget_native_type + H5Dread 自动转换 |
| raw bytes 读取 | 完整 | `readBytes` 返回文件原始布局 |
| 数据集复制 | 完整 | `H5Ocopy + EXPAND_REFERENCE`；group 不展开引用 |
| 删除 | unlink 语义 | 不回收空间；需 Save As 压缩 |
| 数据集扩展 | 支持 maxdims/UNLIMITED | 创建时指定 |
| 数据导入导出 | 完整 | 表格文本/二进制；树 H5export_dataset |
| 大数据分页 | 无真正流式 | 整页驻留 + 视口绘制 |

### 5.3 数据类型

| 类型 | 读 | 写/创建 | 备注 |
|---|---|---|---|
| signed/unsigned 整数 1/2/4/8 字节 | 完整 | 完整 | unsigned 靠显示层扩宽；u64 用 BigInteger 兜底 |
| 自定义 size/precision/offset/pad | 读 | 部分 | 创建不恢复 offset/pad |
| float16/32/64 | 读 | 创建 float16 实际变 float32 | HDF5 2.0 有回归 |
| long double (16 字节) | 读 | 不允许写回 | BigDecimal；负号有缺陷；平台相关 |
| Float8 E4M3/E5M2 | 读（位展开） | 无原生创建 | 无原生 HDF5 类型 |
| BFLOAT16 | 读（转 float32） | 创建走 float32 | 无原生解码 |
| complex f32/f64/long double | 读 | 部分 | H5T_COMPLEX；VLEN complex 不支持 |
| fixed-length string | 完整 | 完整 | 平台默认字符集；裁剪尾部 ≤0x20；不按 cset |
| variable-length string | 完整 | 完整 | VLStrings API |
| compound | 完整（逐字段） | 完整 | 嵌套摊平；ARRAY/VLEN of compound 写拒绝 |
| enum | 完整 | 完整 | 大端/unsigned 高位/顺序有缺陷 |
| VLEN 基本类型 | 完整 | 非字符串写拒绝 | ArrayList<byte[]>；无 vlen_reclaim |
| ARRAY | 完整 | 创建入口有限 | 顶层 ARRAY 解一层后按 base 处理 |
| opaque | 读/hex 显示 | 程序化创建 | tag；无 UI 创建入口 |
| bitfield | 读/hex 显示 | 程序化创建 | H5T_NATIVE_B* |
| object reference（旧式） | 完整 | 部分 | 显示/导航；写入有限 |
| region reference（旧式） | 完整 | 只读 | block/point 描述 |
| 新式 H5T_STD_REF（64 字节） | 完整 | 无 | H5R_OBJECT2/DATASET_REGION2/ATTR |
| named/committed datatype | 完整 | 完整 | H5Tcommit；UI 可复用 |
| time / no-class | 无 | 无 | 常量存在但无映射 |
| 类型等价/转换 | 完整 | 完整 | H5Tequal/H5Tget_native_type |

### 5.4 属性

| 功能 | Java 支持程度 | 说明 |
|---|---|---|
| 枚举/读取 | 完整 | eager 全量读取并缓存 |
| scalar / 数组 / rank>2 | 完整 | rank>2 用内存切片 |
| fixed/vlen string | 完整 | 裁剪/填充语义有损 |
| compound attribute | 完整 | 手工字节拼接；padding/对齐有缺陷 |
| enum/unsigned/reference attribute | 完整 | 引用写入只支持单 String |
| NULL dataspace | 支持显示/创建 | 查看器拒绝打开 |
| 创建/更新/删除/重命名 | 完整 | 更新原地；创建不开启 attr creation order |
| 大属性 | 支持 | 不启用 dense storage 参数；h5py 需要 track_order |
| 复制 | 完整 | H5Acopy 固定 CRT_ORDER |
| 图像/调色板属性 | 完整 | CLASS/IMAGE_*/PALETTE/CF |

### 5.5 引用与链接

| 功能 | Java 支持程度 | 说明 |
|---|---|---|
| hard link | 完整 | 创建/删除/重命名/移动 |
| soft link | 完整 | 可悬空 |
| external link | 完整 | `fileName + '://' + objectPath` |
| user-defined link | 无 | 不创建不解析 |
| 断链占位 | 完整 | H5Link |
| 环检测 | 完整 | oid 祖先比较 |
| 旧式 object reference | 完整 | 显示/导航 |
| 旧式 region reference | 完整 | block/point 描述 |
| 新式 64 字节引用 | 完整 | H5R_OBJECT2/DATASET_REGION2/ATTR |
| 引用写入 | 部分 | 属性引用只支持单 String；数据集引用不写 |
| 引用重映射 | 仅 Save As 部分 | 按 8 字节 long 的旧算法 |

### 5.6 图像、调色板、CF 约定

| 功能 | Java 支持程度 | 说明 |
|---|---|---|
| CLASS=IMAGE 识别 | 完整 | 依赖属性；有顺序依赖缺陷 |
| 索引图 | 完整 | 256 级 + 调色板；越界/NaN/fill → 0 且透明 |
| 真彩色 | 完整 | RGB24；pixel/plane；line 未实现 |
| 多帧/动画 | 完整 | 非真彩 rank>2；≤300px 降采样 |
| 调色板读取 | 完整 | PALETTE 对象引用；≤768 字节 |
| 调色板编辑 | 仅内存 | 不写回 HDF5；拖拽锚点有缺陷 |
| LUT 导入/导出 | 完整 | 2..256 项；浮点 0..1 自动放大 |
| 图像导出 | 完整 | JPEG/PNG/GIF/BMP |
| 新建图像 | 完整 | 索引分支有属性写入 bug |
| 图片转 HDF5 | 完整 | RGB24 (h,w,3)，丢弃 alpha |
| CF 约定 | 部分 | `_FillValue/valid_range/valid_min/valid_max` |
| 直方图/统计/值域/亮度对比度/轮廓 | 完整 | 均作用于渲染图 |

### 5.7 过滤器与插件

| 功能 | Java 支持程度 | 说明 |
|---|---|---|
| deflate/gzip | 读/创建 | 创建级别 1-9 |
| shuffle/fletcher32 | 读/元数据 | 不能创建 |
| szip | 读/元数据 | 不能创建；测试环境常不可用 |
| nbit/scaleoffset | 读/元数据 | 不能创建 |
| blosc/lzf/bzip2/用户 filter | 读/元数据 | 依赖插件；仓库 plugin 目录含 blosc/blosc2/bshuf/bz2/lz4/lzf/zstd/zfp |
| H5PL 路径管理 | 完整 | size/get/replace/insert/remove/prepend/append |
| 压缩比计算 | 完整 | 有 SHUFFLE Nbytes 展示缺陷 |
| filter 参数展示 | 20 参数上限 | 超过无法完整展示 |

### 5.8 版本、访问、错误

| 功能 | Java 支持程度 | 说明 |
|---|---|---|
| HDF5 库版本 | 内部有 `H5get_libversion` | UI 显示的是构建期 versions.properties（2.2.0） |
| 文件 libver bounds | 完整 | 创建/修改 FAPL |
| 超级块版本 | 不显示 | 可新增 |
| 错误信息 | 仅 message | HDF5LibraryException 有 major/minor，但 UI 只显示 message |
| 文件占用检测 | Windows 完整 | Restart Manager；非 HDF5 |
| 配置持久化 | 完整 | `.hdfview<VERSION>` |
| i18n | 完整 | en / zh_CN，1156 key |
| 视图插件扩展 | Java 反射 | lib/ext；不应迁移 |

### 5.9 Java 版未实现/只读的能力（迁移边界）

- SWMR 写、`H5Oflush`；
- dimension scales（H5DS）；
- VDS 创建；
- external storage 创建/编辑；
- compact 布局创建；
- 任意 filter 创建（shuffle/fletcher32/szip/nbit/scaleoffset/blosc）；
- user-defined link 创建/解析；
- link attributes；
- HDF5 文件锁控制；
- 超级块版本显示；
- time 类型；
- 新式 64 字节引用的写入（读支持完整）；
- 超过 2^31 点的单页数据；
- 真正的表格滚动懒加载；
- 图像调色板写回；
- 引用重映射（除 Save As 的部分旧逻辑）。

---

## 6. Python 侧能力映射与缺口

### 6.1 技术选型总表

| 领域 | 推荐 | 理由 | 不推荐 |
|---|---|---|---|
| HDF5 访问 | h5py 3.16 | 高层 File/Group/Dataset/Attribute + 低层 h5*；NumPy 零拷贝切片；覆盖约 85–90% 浏览 | PyTables（通用 HDF5 对象 UnImplemented；第二套运行时）；netCDF4（非通用 HDF5 API） |
| 过滤器 | hdf5plugin 7.1 | Blosc/Blosc2/Zstd/ZFP/SZ/SZ3/bitshuffle/LZ4/BZip2 等；import 即注册 | 手工逐个加载插件 |
| 低层逃生舱 | ctypes（cffi 可选） | 加载 h5py 自带 hdf5.dll，访问 h5py 未包装的 H5R/H5T/H5E/H5P 函数 | Cython（第一版非必需） |
| UI | PySide6 6.11 | 官方 Qt for Python，LGPLv3；QMainWindow/QTreeView/QTableView/QImage/QSettings/QTranslator 齐全 | PyQt6（GPL 或商业许可）；Tkinter（树/表/图像能力不足） |
| 树/表 | Qt Model/View | QAbstractItemModel 懒加载；QAbstractTableModel 虚拟化；QStyledItemDelegate 自定义绘制/编辑 | QTableWidget/QStandardItemModel（大数据不可用） |
| 图表 | pyqtgraph | 交互折线/直方图/ImageItem/ROI；与 PySide6 同事件循环 | matplotlib 作主交互（慢）；QtCharts（定制受限） |
| 图像 | QImage + Pillow | QImage 显示；Pillow 导入导出/格式转换 | 纯 Pillow 做交互（性能/交互不足） |
| 数组 | NumPy 2.5 | structured dtype、uint64、float16、complex64/128 | 纯 Python list（性能/内存） |
| 打包 | Briefcase（安装包）/ PyInstaller onedir（开发包） | Qt 插件处理稳；LGPL 动态链接合规 | PyInstaller onefile（与 LGPL 冲突） |
| 测试 | pytest + pytest-qt | 参数化/标记/fixtures；qtbot 测模型/信号/控件 | 仅 unittest（生态弱） |

### 6.2 功能覆盖矩阵

支持程度：`full` = h5py 高层/标准低级 API 可直接完成；`partial` = 需低层 API、绕行或仅只读/仅写；`none` = h5py 无路径。

| 能力项 | Java 当前能力 | h5py | 推荐实现 | 风险 |
|---|---|---|---|---|
| 文件打开/访问模式/libver/SWMR 读 | r/rw/rs(SWMR 读)；libver Earliest..Latest；family/split 回退；无 SWMR 写 | full | `h5py.File(mode, libver=('v18','v200'), swmr=True, locking='best-effort')`；`f.id.get_access_plist().get_libver_bounds()` | 低 |
| SWMR 写 | 无 | full | `File.start_swmr_write()` + `dset.refresh()`/`flush()` | 中（无 Java 对照） |
| 树遍历/链接/悬空链接 | 全树/懒加载；软/外链/悬空链；Open Source Files | full（`visititems` 跳过悬空链） | `keys()` + `get(getlink=True)`；递归 + `h5o.get_info().addr` 去重；`QAbstractItemModel` 懒加载 | 中 |
| 属性读取 | 全类型；name/type/rank/dims/value；索引/创建序 | full（新式引用属性除外） | `obj.attrs` 按需读取；`h5py.h5a.iterate(index_type=INDEX_CRT_ORDER)`；64 字节 ref 走 native_bridge | 中 |
| 属性写/删/改名/大属性 | 支持；copyAttributes 按 CRT_ORDER；未显式开启 attr creation order | full（大属性需 `track_order=True`） | `h5py.h5a.create/write/rename/delete`；`track_order=True`；决定是否保留 256 截断 | 低-中 |
| 定长/变长字符串与 UTF-8 | 平台默认字符集（Java 21=UTF-8）；定长截断/补零 | full | `string_dtype(encoding='utf-8')`；`.asstr()`；显示层显式 decode；按字节判断容量 | 低 |
| enum | 名称/数值互转，`enum.conversion` 可配 | full | `h5py.check_enum_dtype`；维护 enum 字典；`QComboBox` delegate | 低 |
| bitfield | 显示 hex/bin；不能创建 | partial（可读写为 uint，不能创建） | `h5py.h5t.STD_B8/16/32/64`；创建用 ctypes `H5Tcreate(H5T_BITFIELD)+H5Tset_precision` | 中 |
| opaque | 创建/hex 显示/tag | partial（既有文件可能 `no appropriate function for conversion path`） | `np.dtype('Vn')`；`TypeOpaqueID.set_tag(b'...')`；低层 `h5d.read(mtype=文件类型)` | 中 |
| array 子类型 | 显示元素列表；可创建 ARRAY | full | NumPy subarray dtype | 低 |
| compound 整记录/字段 | 分字段列读写；嵌套两层；ARRAY-of-COMPOUND/VLEN(COMPOUND) 写拒绝 | full（整记录）/partial（部分写） | structured dtype；`dset[field]`；嵌套/vlen 用整记录 RMW 或低层 subset dtype | 中 |
| vlen 基本类型/字符串/vlen(compound) | 支持；VLEN(COMPOUND) 写拒绝；compound 内非字符串 vlen 写拒绝 | full（vlen compound 可写，优于 Java） | `vlen_dtype`；整记录 RMW；禁止手工 vlen compound mem type | 低-中 |
| complex | H5T_COMPLEX f32/f64/long double；BE；compound 成员；VLEN complex 明确不支持 | partial（f32/f64 native；f16/long double 需绕行） | `COMPLEX_IEEE_F32LE/F64LE` + `libver='v200'`；long double 原始字节解析；可选 ml_dtypes | 中-高 |
| float16/bfloat16/float8 | 读转 float；float8 位展开；写 float16 走 FLOAT32 拷贝 | partial（f16 full；bf16/fp8 读转换、写需 ctypes） | `IEEE_F16LE`；ctypes `H5T_FLOAT_BFLOAT16LE_g`/`H5T_FLOAT_F8E4M3_g`；ml_dtypes 显示 | 高 |
| uint64/无符号大整数 | Java 有符号 long 用 BigInteger 兜底；hex/bin | full（NumPy uint64） | 禁止转 int64；Python int/BigInt 格式化；统计保持 uint64 | 中 |
| 旧式对象/区域引用 | 显示/导航；区域 block/point 描述 | full | `Reference/RegionReference`；`dset.ref/regionref`；`f[ref]`；`H5Sget_select_*` 重建描述 | 低 |
| 新式引用/H5R_ATTR | 完整支持 | none | native_bridge：`H5Rget_type/name/obj_type3/open_object/open_region/open_attr/destroy`；写 `H5Rcreate_*`+`H5T_STD_REF_g` | 高 |
| hyperslab/选择 | start/stride/count 规则矩形；标量强制 H5S_ALL | full（含 fancy/mask/select_elements） | NumPy 切片；`select_hyperslab(start,count,stride,block)`；大点集分块 | 低 |
| chunk/压缩/filter 读 | deflate/shuffle/fletcher32/nbit/scaleoffset/szip/用户 filter 元数据 | partial | `dset.chunks/compression/compression_opts/shuffle/fletcher32/scaleoffset`；`h5z.filter_avail`；`h5pl` | 中 |
| filter 创建 | 仅 chunked+gzip | full（gzip/lzf/shuffle/fletcher32/scaleoffset） | `create_dataset(compression=..., shuffle=..., fletcher32=...)`；`PropDCID.set_filter(FILTER_NBIT)`；hdf5plugin | 低-中 |
| SZIP | 自带 2.2 库可读；SWT 测试因环境不可用而禁用 | none（stock wheel） | 自建 h5py 链接 HDF5 2.2+libaec；或独立进程直调带 SZIP 的 libhdf5；禁止同进程混两套 HDF5 | 高 |
| 布局 chunked/contiguous/compact | 识别三种；创建仅 chunked/contiguous | full | `PropDCID.set_layout(COMPACT)`；`chunks=None` | 低 |
| external storage | 仅显示 count；读取受 cwd 影响 | full（读写） | `create_dataset(external=[(name,offset,size)])`；`dset.external`；绝对路径或临时 chdir | 低-中 |
| VDS | 读取/解析 virtual map/Open Source Files；无创建 | full | `VirtualLayout/VirtualSource/create_virtual_dataset/virtual_sources` | 低-中 |
| user block | 读/显示/原位或整文件重写；不能创建 | partial | `File.userblock_size`；创建时 `userblock_size=512`；已有文件重写 + 原子替换 | 中 |
| dimension scales | Java 无 H5DS 调用 | full（超集） | `dset.make_scale/attach_scale`；`dset.dims[i].label`；`h5py.h5ds.*` | 低（范围） |
| 图像/调色板 | 索引/真彩/interlace/多 palette/bitmask/帧动画 | full（数据层） | `QImage.Format_Indexed8`+`setColorTable` 或 `Format_RGB888`；Pillow 导入导出；QTimer 帧动画；pyqtgraph ImageItem | 中 |
| 表格/编辑/rank>2 | NatTable 虚拟化；compound 列头；frame 导航；hex/bin/enum 格式化 | full（数据层） | `QTableView`+`QAbstractTableModel` 页缓存 + dirty overlay；`QStyledItemDelegate`；自定义 `QHeaderView` | 中 |
| 搜索/统计 | 64K 元素块、5000 结果上限；scope/kind/ddof=1/NaN/Inf/fill 排除 | full | NumPy 分块 + `np.isclose/count_nonzero/mean/std(ddof=1)`；worker+`threading.Event`；结果 dataclass | 中 |
| 导入导出/Save As | `H5export_dataset`；文本/二进制 native/LE/BE；`H5Ocopy` expand refs | full（需自实现 export） | `astype+byteswap.tobytes`/`tofile`；csv；`h5py.h5o.copy` + ctypes `H5Pset_copy_object(EXPAND_REFERENCE)` | 中 |
| 对象创建/复制/移动/删除/重命名 | 全支持；复制是 H5Ocopy 非硬链接；跨文件 move 不支持 | full | `create_group/create_dataset`；`Group.move`；`h5o.copy`；`del` | 低-中 |
| 元数据/superblock/token | layout/filter/fill/VDS/external；无 token；不显示 superblock | full（superblock）/partial（token） | `create_plist/ObjInfo`；`get_create_plist().get_version()`；ctypes `H5Oget_info3` | 低-中 |
| H5PL 插件管理 | H5PLsize/get/replace/insert/remove/prepend/append | full | `h5py.h5pl.*`；`HDF5_PLUGIN_PATH`；启动能力报告 | 中 |
| 错误栈 | major/minor/minorText | partial | ctypes `H5Eset_auto2`+`H5Ewalk2` 采集 `H5E_error2_t`；异常类型映射 | 中 |
| 文件占用检测 | Windows Restart Manager | none（非 HDF5） | ctypes `RmStartSession/RmRegisterResources/RmGetList` 或 psutil；仅 Windows | 中 |
| 巨大 group/索引顺序/配置/模块扩展 | max.members；INDEX_NAME/CRT_ORDER；lib/ext 反射插件 | partial | `keys()` 懒加载；`H5Aopen_by_idx/H5Lget_name_by_idx`；新配置 schema；放弃 Java 插件 | 中 |

### 6.3 必须直调低层 libhdf5 或绕行的缺口

1. **新式 64 字节引用（H5R_OBJECT2 / H5R_DATASET_REGION2 / H5R_ATTR / H5T_STD_REF）**。本地实测：h5py 3.16 打开 `trefer_obj.h5`/`trefer_attr.h5` 直接抛 `TypeError: Unknown reference type`；`h5py.h5r.create` 只能生成旧式引用；Java `H5ReferenceType` 已完整支持。绕行：native_bridge 用 `H5Dread/H5Aread` 取 `H5R_ref_t`（64 字节），再 `H5Rget_type/H5Rget_file_name/H5Rget_obj_name/H5Rget_attr_name/H5Rget_obj_type3/H5Ropen_object/H5Ropen_region/H5Ropen_attr/H5Rdestroy`；区域描述用 `H5Sget_select_type/H5Sget_select_hyper_blocklist/H5Sget_select_elem_pointlist` 重建；写入用 `H5Rcreate_object/region/attr + H5T_STD_REF_g`。风险高，Phase 1 必须完成。
2. **SZIP（FILTER_SZIP=4）**。本地实测：stock h5py wheel `h5Zfilter_avail(4)=False`；而 HDFView 自带 `hdf5.dll` 的探针报告为 True。注意 Java SWT 测试本身也因"SZIP filter not available in test environment"禁用该用例，说明运行环境必须复核。绕行：源码构建 h5py 链接 HDF5 2.2.0 + libaec，生成自有 wheel；否则在独立子进程用带 SZIP 的 libhdf5 转换/导出；禁止同进程加载两套 HDF5 运行时。风险高，Phase 0 决策。
3. **完整 HDF5 错误栈**。h5py 3.x 无 `h5py.h5e`，异常只带一条顶层消息；Java `HDF5LibraryException` 有 major/minor/minorText。绕行：ctypes `H5Eset_auto2` 设置回调 + `H5Ewalk2` 收集 desc/func_name/maj_num/min_num/file/line，UI 错误码与详情分层显示。风险中，Phase 1。
4. **bitfield 创建**。`h5py.h5t.create` 只接受 COMPOUND/OPAQUE；现有 bitfield 可读为 uint。绕行：ctypes `H5Tcreate(H5T_BITFIELD)+H5Tset_precision+H5Tcommit2`；读写用 `H5Dread/H5Dwrite` 显式 mtype。风险中，Phase 5。
5. **opaque 既有文件的转换路径缺失**。`tbitnopaque.h5` 高层报 `OSError: no appropriate function for conversion path`，低层 `h5d.read(mtype=文件类型)` 可读。绕行：低层读取 + 原始字节 hex 显示；写入用 `np.dtype('Vn')` 与 `TypeOpaqueID.set_tag`。风险中，Phase 1/3。
6. **bfloat16/FP8 写入**。NumPy 无原生 dtype；读取已被 HDF5 转换为 float32/float16；ctypes 可访问 `H5T_FLOAT_BFLOAT16LE_g`、`H5T_FLOAT_F8E4M3_g` 且本地创建 bfloat16 dataset 成功。绕行：native_bridge 用预定义全局类型 + `H5Dcreate2/H5Dwrite`；显示可用 ml_dtypes。风险高，Phase 0 决定是否要求写。
7. **16 字节 long double / float128**。Windows `np.longdouble` 为 8 字节，h5py 创建 `IEEE_F128` 报 `Insufficient precision`；Java 按 32 字节 byte[][] 保留原始位。绕行：原始字节读取 + Python 手工解码/hex 显示；必要时 ctypes 使用 `H5T_NATIVE_LDOUBLE`。风险高，Phase 1/3。
8. **长双精度 complex / complex f16**。Java 支持 float32/64/long double complex、BE、数组、compound 成员，VLEN complex 明确不支持；h5py 仅 native f32/f64 complex 可用。绕行：long double 按原始字节；complex f16 无 NumPy 映射，按整数/字节显示或明确不支持。风险中-高，Phase 3。
9. **compound 嵌套字段 / vlen string 单字段写**。本地实测：`d['a'][1:3] = ...` **无异常但静默不写**；整字段 `d['a'] = ...` 可以写；vlen string 单字段赋值 TypeError；手工 vlen compound mem type 曾 segfault；嵌套字段链式赋值不落盘。绕行：顶层字段用 `dset[field]`；嵌套/vlen 用"读整条记录→改→整条写回"（RMW）或低层 `H5Dwrite(mtype=subset compound dtype)`；明确禁止手工 vlen compound mem type。风险高（静默丢写），Phase 1/5。
10. **link attributes**。h5py 无 API。绕行：若必须支持，用 ctypes 低层 `H5A*` 按 link 路径打开/读写；否则明确不支持并在 UI 标注。风险中，Phase 0 决策。
11. **已存在文件的 user block 修改**。h5py 仅创建时接受 `userblock_size`，无 set_userblock。绕行：读原文件→跳过旧 block→按 512×2^n 写新 block→复制剩余内容→临时文件原子替换；大文件耗时与磁盘翻倍。风险中，Phase 5。
12. **H5O token / 对象地址**。h5py `ObjInfo` 无 token；Java 元数据也不展示 token。绕行：ctypes `H5Oget_info3(H5O_info2_t)` 取 token；若 UI 不需要可放弃。风险低-中，Phase 1（元数据）。
13. **`H5export_dataset` 无等价物**。Java `H5File.exportDataset()` 直接用 H5export_dataset；h5py 无此函数。绕行：NumPy 分块 `astype + byteswap + tobytes` 实现 native/LE/BE 三种二进制，文本用 csv/格式化；这不是低层直调，但必须自实现并做端序测试。风险中，Phase 1/3。
14. **group link phase change / attr creation order 等创建属性**。Java 建组可设 CRT_ORDER_TRACKED/INDEXED 与 max compact/min dense；h5py 高层仅 `track_order=True`。绕行：ctypes `H5Pset_link_phase_change`/`H5Pset_attr_creation_order`。风险中，Phase 5。
15. **Windows 文件占用检测**。`hdf.view.fileusage` 使用 Restart Manager；非 HDF5。绕行：ctypes `RmStartSession/RmRegisterResources/RmGetList`；或只提示"文件被占用"。风险中，Phase 5/6。

### 6.4 h5py 行为差异与陷阱（本地实测/文档确认）

| 主题 | HDFView/Java | h5py | 迁移对策 |
|---|---|---|---|
| 对象模型 | 打开即构建整棵 HObject 树 | 无内存树；每次按路径新开 ID | UI 层自建懒加载模型，不复制 eager 树 |
| 路径查找 | `get()` 返回 null | `f.get(name)` 返回 None；`f[name]` 抛 KeyError | 区分"不存在"与"断链" |
| 断链 | H5Link 占位、可显示 | `get(name,getlink=True)` 返回 SoftLink/ExternalLink；`visititems` 跳过 | 用 `keys()` + `get(getlink=True)` 递归 |
| 外部链接 | HObject 记录包含它的文件 | 解引用后对象属于目标文件（`f['ext'].file.filename` 是 ext.h5） | 额外包装 containment；写/删前确认目标文件 |
| 创建顺序 | 文件级 indexType/indexOrder | 只有创建时 `track_order=True` 才按创建序；`list(g)` 默认字母序 | 需要创建序时创建时开启；或用低层 iterate |
| 属性赋值 | `writeAttribute` 原地更新 | `attrs[name]=` 是 delete+recreate，会改变 dtype/形状/创建序（本地实测：`|S8` → object） | 用 `attrs.modify` 或低层 `h5a.open(...).write(...)` |
| 属性顺序 | NAME/CRT_ORDER × INC/DEC/NATIVE | 高层顺序固定；2×3 组合用 `h5a.iterate` | 低层 API 复刻 |
| 复合字段写 | 逐字段 H5Dwrite | `dset['a'][slice]=...` 静默不写（本地实测） | 整字段赋值或整记录 RMW/低层 mtype |
| 新式引用 | 完整读写 | `trefer_obj.h5` 打开失败 TypeError（本地实测） | native_bridge |
| SZIP | 自带 2.2 库可读 | stock wheel 不可用（本地实测 `filter_avail(4)=False`） | 自建 wheel/独立进程 |
| 数据类型 | 自定义 Java 映射 | NumPy dtype 直接映射 | 用 `TypeInfo` 统一显示/编辑/统计 |
| 字符串 | 平台默认字符集、裁剪尾部空白 | fixed 返回 `np.bytes_`，vlen 返回 str；写入默认 vlen | 显式 `string_dtype`、按字节长度、保留原始 bytes |
| unsigned | 显示层扩宽 | NumPy uint8/16/32/64 | 删除双缓冲；统计/格式化显式 uint64 |
| 错误 | message + major/minor | OSError/KeyError/ValueError/TypeError，仅顶层消息 | native_bridge 采集 H5E 栈 |
| 线程 | 全局 NATIVE_IO_LOCK | 全局 phil 锁，多线程 I/O 串行 | 单一 H5Session worker 线程 |
| 文件关闭 | 强制关闭全部子对象 | `f.close()` 后所有子 ID 失效 | `with h5py.File(...)`；丢弃所有对象引用 |

---

## 7. 推荐 Python 架构

### 7.1 分层

```
UI 层        MainWindow / TreeView / TableView / ImageView / MetaDataView / PaletteView
             SearchDialog / StatisticsDialog / ChartView / 各类 Dialog
                 │ Qt signal/slot，只传 domain 对象，不传 hid
领域层       Node / FileHandle / TypeInfo / Selection / DataPage / DirtyOverlay /
             Command / SearchQuery / StatisticsResult / PaletteModel / ErrorInfo
                 │ 纯 Python，可单测，不 import h5py
h5 访问层    H5Session(单线程) / FileDAO / GroupDAO / DatasetDAO / AttributeDAO /
             DatatypeDAO / ReferenceDAO / FilterService / UserBlockService /
             ExportService / native_bridge(ctypes)
                 │
HDF5         h5py 3.16 (+hdf5plugin) + 自有 HDF5 2.2 运行时/插件
```

### 7.2 模块划分

```
src/hdf5view/
  h5io/        session.py file.py group.py dataset.py datatype.py attribute.py
               reference.py filters.py userblock.py export.py errors.py native_bridge.py
  domain/      node.py file_handle.py type_info.py selection.py data_page.py
               dirty_overlay.py commands.py search.py statistics.py palette.py
  ui/          main_window.py tree/ table/ image/ metadata/ palette/ search/
               statistics/ chart/ dialogs/ models/ delegates/
  services/    settings.py theme.py i18n.py plugins.py fileusage.py recent_files.py
  cli/         h5ls.py h5dump.py h5search.py h5stats.py h5export.py
  tests/       unit/ integration/ ui/ corpus/ benchmarks/
```

关键设计决策：

- **单一 H5Session 线程**：所有 h5py/ctypes 调用串行执行；h5py 的全局 phil 锁使并行 I/O 无收益，且可避免句柄/回调跨线程崩溃。ctypes 调用与 h5py 共用同一把 `RLock`。
- **Selection dataclass**：把 Java 的 `startDims/selectedDims/selectedStride/selectedIndex` 复刻为不可变对象，一个 Selection 同时服务表格分页、图像切片、搜索/统计块读取，避免三套切片逻辑。
- **TypeInfo**：统一表示 INTEGER/FLOAT/STRING/COMPOUND/ARRAY/VLEN/ENUM/OPAQUE/BITFIELD/REF_OLD/REF_NEW/REF_ATTR/COMPLEX/BFLOAT16/FP8；提供 `to_numpy_dtype`、`format_value`、`parse_value`、`is_editable`；所有显示/编辑/搜索/统计基于它，禁止到处 `isinstance`。
- **DataPage + DirtyOverlay**：页缓存只持有当前页 NumPy 数组；编辑写 overlay，提交时按类型走 slice 写、整记录 RMW 或 ctypes 单成员写；写后读回校验。
- **Document**：每个打开文件一个对象，记录 mode/libver/swmr/plugin 状态、脏状态、视图注册表；所有视图通过 signal 订阅 `fileOpened/fileClosed/selectionChanged/objectCreated/objectDeleted/attributesChanged/dataDirty`。
- **native_bridge**：只封装 h5py 缺失的 C API（新式引用、bfloat16/FP8 写、SZIP 检测、link attributes、H5O token、完整错误栈、`H5Pset_link_phase_change`、`H5Pset_copy_object`）；函数签名用 `ctypes.Structure` 显式定义，每个函数附最小 corpus 测试与崩溃隔离开关。
- **filters**：默认 `import hdf5plugin`；支持 `HDF5_PLUGIN_PATH` 与 `h5py.h5pl.append`；启动时生成 `filter_avail` 能力报告，未知 filter 给可操作错误而非崩溃。

### 7.3 线程与数据流

```
UI → Command → H5Session.submit() → worker QThread → h5py/ctypes
   ← signal ← 结果 ←──────────────────────────────┘
```

- 表格：`HDFPageCache` 持有当前页 NumPy 数组与 DirtyOverlay；`data()` 只读缓存；`setData()` 写 overlay 并派发提交命令；`canFetchMore/fetchMore` 或 HDFView 式分页。
- 图像：只读当前帧/切片；`QImage` 由 NumPy 构造；大图先降采样/自动对比度；帧动画用 QTimer。
- 搜索/统计：worker 线程分块读取，`threading.Event` 取消，结果按批 emit；不冻结 UI。
- 错误：native_bridge 采集 H5E 栈 → `ErrorInfo(major, minor, message, trace)` → UI 详情对话框；程序分支只按异常类型，不解析文本。

---

## 8. 分阶段迁移计划

### Phase 0（1–2 周）范围冻结与风险验证

- 建立可重复 corpus：收集 `hdfview/src/test/resources/uitest/` 全部语料（`tcomplex.h5`、`tcomplex_be.h5`、`tcompound_complex*.h5`、`tldouble.h5`、`tfloat8.h5`、`tfloat16*.h5`、`tbfloat16*.h5`、`tfilters.h5`、`trefer_obj.h5`、`trefer_attr.h5`、`tdatareg.h5`、`tattrreg.h5`、`tbitnopaque.h5`、`tframeselection.h5`、`tintsize.h5`、`DS64BITS.*`、`h5ex_d_bzip2.h5`、`UTF8Strings.h5`、`unicode.h5`、`9055-deep.h5`、`HV-26-plan.h5`），以及 `object/src/test/java/object/h5ex_d_extern.*`，并补 VDS/SWMR/userblock/compact/dimension scales 生成脚本。
- 支持矩阵决策：SZIP、新式引用、link attributes、bfloat16/FP8 写、long double、complex f16、dimension scales、VDS 创建、SWMR 写。
- Spike：h5py 3.16 全 corpus 读取；native_bridge 新式引用/错误栈；Qt 百万行表格原型；PyInstaller/Briefcase 冒烟。
- 输出明确"不支持清单"与 CI 骨架（pytest markers、xvfb、benchmark）。
- 退出标准：每个高风险项有"实现方案/明确放弃"结论；h5py 对 corpus 的读取无崩溃（崩溃项有隔离方案）；native_bridge 原型能解码 `trefer_obj.h5` 与 `trefer_attr.h5`；打包产物能打开至少一个 corpus 文件。

### Phase 1（2–3 周）h5 访问层 + native_bridge + CLI

- 交付 `h5io` 全部模块、`native_bridge`、CLI（`h5ls/h5dump/h5search/h5stats/h5export`）、pytest core 套件、corpus manifest。
- 验收：全部 corpus 可读；与 `h5dump -p -d`/`h5ls -r` 的元数据/值逐项对比；悬空链接、未知 filter、损坏文件给结构化错误而非崩溃；1e8 元素搜索/统计可分块、可取消；连续打开/关闭 10000 次无句柄泄漏。

### Phase 2（2–3 周）只读 UI 壳

- 交付 `QMainWindow` + `QDockWidget`/MDI 多文件、文件打开/关闭/最近文件、`QTreeView` 懒加载树模型、元数据视图、只读属性表、openAs（r/rs/rw）、libver 选择、主题/i18n 骨架、错误详情对话框。
- 验收：打开 10 万节点树不卡顿；UI 线程 0 次 h5py 调用（可用钩子断言）；访问模式与 libver 文案/行为对齐现有测试；属性/元数据与 Java 输出逐项一致。

### Phase 3（3–4 周）表格、搜索、统计（只读）

- 交付 `QAbstractTableModel` 页缓存、标量/compound 表格、compound 多级列头、hex/bin/enum/bitmask 格式化、引用单元格导航、rank>2 帧导航、dirty overlay 基础设施；搜索对话框（名称/值、64K 块、5000 结果上限、进度/取消/stale）；统计对话框（CURRENT_PAGE/ENTIRE_DATASET × ZERO/NON_ZERO/POSITIVE/NEGATIVE/NON_NEGATIVE、ddof=1、fill 排除、NaN/Inf、页/全量切换、高亮）。
- 验收：常见数据集显示/编辑与 Java 等价；搜索/统计数值与 Java/NumPy 参考实现一致；取消延迟 <100ms；1e8 元素扫描内存有界；编辑后统计高亮自动重算。

### Phase 4（2–3 周）图像、调色板、图表

- 交付索引图/真彩色、interlace、自动对比度/增益、bitmask、翻转/旋转/缩放、帧动画、像素值读取、PNG/JPG/BMP 导出；PALETTE 属性读取（旧式 ref；新式走 native_bridge）；Gray/Reverse Gray/GrayWave/Rainbow/Nature/Wave 预设与 RGB 曲线编辑（修正 Java 已知缺陷，优先整体替换）；pyqtgraph 折线/直方图，matplotlib 导出。
- 验收：样例图像像素读数与 Java 一致；大图不 OOM；调色板预览与写回可往返；图表数据来自表格选中行列且与 Java Chart 输出一致。

### Phase 5（3–4 周）编辑与写路径

- 交付属性增删改（含 compound/vlen/enum/引用；大属性 track_order=True）；单元格编辑；创建组/数据集/图像/compound/命名类型/链接；复制/粘贴/移动/删除/重命名（复制用 `H5Ocopy`+expand refs；剪切/粘贴是同文件 `H5Lmove`；跨文件 move 明确拒绝）；user block 写；Save vs Save As 语义；SWMR 写；所有写操作先写临时文件再原子替换。
- 验收：写后重新打开逐项校验；同文件/跨文件、组/数据集、剪切/复制场景等价；根组不可复制/移动；只读模式写操作被阻止并提示；崩溃不产生半写文件；新式引用/特殊类型按 Phase 0 决策执行。

### Phase 6（2 周）打包、验收与收尾

- 交付 PyInstaller onedir 开发包 + Briefcase MSI/DMG/deb/rpm；HDF5/插件 DLL 清单；文件关联与命令行参数（`-root/-start/-g/-geometry`，未知参数按文件路径或明确报错）；签名/公证；性能/可访问性/崩溃报告；用户文档、功能对照表、遗留缺口说明；LGPL/第三方许可证声明。
- 验收：目标平台安装/卸载/启动通过；安装包内跑 corpus 回归；文件关联与启动参数符合决策；遗留缺口在 About/文档中可见。

---

## 9. 测试策略

### 9.1 测试分层

- **unit（无 UI、无真实文件）**：domain 纯逻辑（Selection、TypeInfo、格式化、统计累加器、搜索查询、命令撤销）；`@pytest.mark.unit`。
- **integration（h5py/ctypes + 真实 corpus）**：h5io 全部路径、过滤器、引用、user block、导入导出；`@pytest.mark.integration`；每个测试用 `tmp_path` 拷贝语料，避免写坏。
- **ui（pytest-qt）**：模型与委托可无视图测；窗口/对话框需 `qtbot`；`@pytest.mark.ui`；CI 用 xvfb 或 Windows 桌面。
- **benchmark**：pytest-benchmark 守住分页、搜索、统计、树加载、图像帧率。
- **golden**：用 `h5dump -p -d`/`h5ls -r` 生成参考输出；Java 侧可保留一个只读 harness 输出 CSV/文本作为对照。
- **corpus 管理**：`tests/corpus/manifest.yaml` 记录文件来源、预期类型、过滤器、引用类型、是否仅 Java 可读；生成脚本可重建 VDS/SWMR/userblock/compact/dimension scales 文件。
- **崩溃隔离**：native_bridge 高风险用例放子进程，父进程断言退出码与输出，避免 segfault 拖垮整套测试。

### 9.2 对照现有测试

| 现有测试类别 | 证据 | Python 方案 | 验收要点 |
|---|---|---|---|
| access mode | `TestHDFViewAccessMode` | `tests/integration/test_access_mode.py` + `tests/ui/test_access_mode.py` | r/rw/rs 行为一致；SWMR 打开失败显式报错；重开不重复树节点 |
| attributes | `TestHDFViewAttributes`、`TestHDFViewTAttr2` | 参数化 attrs 读写/删除/改名；compound/vlen/ref 属性；track_order 大属性 | 名称/类型/rank/dims/value 一致；创建顺序可枚举；只读保护 |
| complex | `TestHDFViewComplex`、`TestComplexDatatype` | `test_complex.py` 覆盖 f32/f64/long double、BE、数组、compound 成员；VLEN complex 断言不支持 | 显示 a+bi；long double 原始位不丢；写回字节一致 |
| cut/copy/paste | `TestHDFViewCutCopyPaste` | `test_object_ops.py`：`h5o.copy` + expand refs；`Group.move`；根组拒绝 | 复制是新对象非硬链；跨文件复制引用展开行为一致；剪切后源消失 |
| frame selection | `TestHDFViewDatasetFrameSelection` | `test_frame_selection.py`：Selection 计算 + pytest-qt 按钮/输入框 | first/prev/next/last/enter 行为一致；维度选择与 stride 正确 |
| search | `DatasetSearchEngineTest`、`TestHDFViewDatasetSearch` | `test_search.py`：名称/值、dirty overlay、进度回调、关闭文件 | 结果集合/顺序一致；取消及时；复杂/引用/compound 标注 unsupported |
| statistics | `DatasetStatisticsEngineTest`、`StatisticsHighlightMaskTest`、`TestHDFViewDatasetStatistics` | `test_statistics.py`：NumPy 参考实现 + UI 高亮 | total/zero/nonZero/positive/negative/nonNegative/min/max/sum/mean/std 一致；高亮不改变值 |
| Float16 | `TestHDFViewFloat16`、`TestBFloat16Read`、`TestBFloat16DatatypeSize` | `test_float16.py`：IEEE_F16LE/BE、bfloat16 读、float8 位展开、写路径 | 值/字节 size 一致；bfloat16 2 vs 4 字节不混；float8 两种格式正确 |
| inline dataset | `TestHDFViewInlineDataset` | `test_inline_dataset.py` + pytest-qt 模态 mock | 编辑生命周期与保存次数一致；切换数据集先保存；关闭文件确认 |
| int conversions | `TestHDFViewIntConversions` | `test_int_conversions.py`：8/16/32/64 有符号/无符号、hex/bin、uint64 BigInt | 显示与写回不溢出；uint64 最大值为 18446744073709551615 |
| lib bounds | `TestHDFViewLibBounds` | `test_lib_bounds.py`：新建文件 libver 元组 + UI 文案 | 文案/边界一致；已打开文件改 bounds 不转换格式 |
| links | `TestHDFViewLinks` | `test_links.py`：`get(getlink=True)`、悬空链、外链目标未打开/已打开 | 树节点类型/目标名一致；悬空不阻断整树 |
| refs | `TestHDFViewRefs`、`TestHDFViewTAttr2` | 旧式用 h5py；新式/属性引用用 native_bridge；子进程崩溃隔离 | 对象名/区域 block/point/属性名一致；旧式可写、新式按决策 |
| VLDatatypes | `TestTreeViewNewVLDatatypes` | `test_vlen.py`：vlen string/数值/vlen compound；整记录 RMW | 创建/读回一致；vlen compound 写成功（优于 Java） |
| tree export | `TestTreeViewExport` | `test_export.py`：NumPy 端序导出、csv 导入、树导出 | 字节级/文本逐行一致；只读模式导出报错行为对齐已知问题 |
| filters | `TestTreeViewFilters` | `test_filters.py`：filter_avail 能力报告、gzip/shuffle/fletcher32/nbit/scaleoffset/blosc/zstd/bzip2；SZIP 按决策 skip 或自建 wheel | 元数据字符串一致；未知 filter 给可操作错误；SZIP 状态在 CI 中可见 |
| H5Table | `TestH5Table` | `benchmarks/test_table_throughput.py` | 读写吞吐不低于 Java 基线的约定比例；内存不随行数线性增长 |
| memory leak | `TestH5MemoryLeak` | `test_memory_leak.py`：tracemalloc + RSS + h5py 对象计数 | 句柄/内存回到基线；10000 次后仍可打开新文件 |

---

## 10. 风险登记与待确认问题

### 10.1 主要风险

1. **HDF5 2.2.0 与 h5py 内置 2.0.0 的版本断层（高）**。根 `pom.xml` 与 `repository/lib/jarhdf5-2.2.0.jar` 固定 2.2.0；h5py wheel 绑定 2.0.0。可能影响新格式、complex/Float8/Float16、插件 ABI、V200 行为。缓解：Phase 0 决定源码构建 h5py（链接 2.2.0 + libaec）或锁 2.0 并建立差异清单；CI 双运行时矩阵。
2. **SZIP 证据冲突（高）**。本地探针称 HDFView `hdf5.dll` 的 `H5Zfilter_avail(4)=1`，但 Java SWT 测试明确因"SZIP filter not available in test environment"禁用。必须在目标机器复核运行库路径；若必须支持，只能自建 wheel 或独立进程。
3. **新式引用/属性引用（高）**。h5py 完全不可读；native_bridge 需正确管理 64 字节 H5R_ref_t、H5Rdestroy、区域选择重建；任何生命周期错误都可能崩溃或悬空。
4. **特殊数值类型写路径（高）**。bfloat16/FP8/long double/complex f16 无 NumPy 原生 dtype；ctypes 写入依赖 HDF5 2.x 符号，升级即需回归。
5. **h5py 静默丢写与 segfault（高）**。`dset['field'][slice]=...` 静默不写（已实测）、vlen string 单字段 TypeError、手工 vlen compound mem type 曾 segfault。必须在代码层禁用这些 API，改用整记录 RMW/低层 subset dtype，并加"写后读回校验"测试。
6. **ctypes 内存/句柄/线程安全（高）**。必须与 h5py 共用 RLock、只在 worker 线程调用、每个封装函数配 corpus、必要时子进程隔离；不能与第二个 HDF5 运行时同进程混用。
7. **性能与 UI 响应（中）**。h5py phil 锁串行化 I/O；`data()` 内 I/O 会卡死；大图/大表必须分页、降采样、缓存；1e8 搜索/统计需可取消与进度。
8. **UI 行为对齐（中）**。NatTable 的 compound 多级列头、单元格编辑器、排序/过滤、PaletteView 单点选色已知缺陷、inline dataset 的保存/确认语义、Save vs Save As、只读保护，都需要逐项对照测试。
9. **SWTBot → pytest-qt 迁移（中）**。现有 UI 测试依赖固定文案与控件索引；Python 侧需稳定 objectName/role，避免文案断言；CI display 限制相同。
10. **打包与许可（中）**。PySide6 LGPL 动态链接、HDF5/插件 DLL 收集、`HDF5_PLUGIN_PATH`、签名/公证；onefile 与 LGPL 冲突。
11. **插件 ABI（中）**。hdf5plugin 与 HDFView 的 2.2 插件 DLL 必须匹配同一 HDF5 运行时；不能同时加载两套。
12. **扩展框架缺失（中）**。Java `lib/ext` 视图工厂无法迁移；若用户依赖第三方视图模块，需要新的 Python 插件 API 或明确废弃。
13. **范围膨胀（中）**。dimension scales、VDS 创建、SWMR 写、superblock 展示、link attributes 是 Python 可做的"超集"，但 Java 版没有或未验证；必须按 Phase 0 决策控制范围。
14. **配置/国际化/主题迁移（低-中）**。`.hdfview<version>` 配置文件、字体、语言、主题、高 DPI/可访问性；不迁移"配置随版本改名"行为。

### 10.2 待确认问题

1. 目标平台与架构：仅 Windows x64，还是 Linux/macOS/ARM 也要支持？决定 HDF5 构建、float128 行为、打包/签名。
2. SZIP 是否必须支持？是自建 h5py（HDF5 2.2 + libaec）还是允许独立进程回退？
3. 新式 64 字节引用与 H5R_ATTR 是否必须读写？若必须，native_bridge 必须是 Phase 1 交付物。
4. 是否要求 bfloat16/FP8/FP6/FP4 原生写入？complex f16、long double complex 是否需要显示/编辑？
5. 是否必须与 Java 侧 HDF5 2.2.0 完全兼容（签名插件、ROS3、2.2 新格式）？是否接受源码构建 h5py？
6. 是否保留 Java HDFView 作为并行工具/回退？验收标准是功能清单、像素级 UI 对照，还是用户任务完成度？
7. 最大文件/数据集规模、内存上限、性能目标（打开、翻页、搜索、统计、图像帧率）？
8. dimension scales 是否新增显示/附着编辑？VDS/external/compact 是否新增创建入口？
9. 是否需要 link attributes 显示/编辑？是否需要 H5O token？
10. 是否需要 Windows 文件占用检测与强制关闭进程？
11. i18n 覆盖哪些语言？亮/暗主题是否要求完全等价？是否有高 DPI/可访问性要求？
12. 分发格式（MSI/DMG/DEB/RPM/AppImage）、代码签名/公证/自动更新要求？PySide6 LGPL 动态链接与许可证声明是否可接受？
13. 是否允许 PyQt6（GPL/商业许可）？默认建议 PySide6。
14. 是否需要 PyTables 作为可选依赖（仅 OPSI）？默认排除。
15. 是否需要 CLI/批处理接口、文件关联、与现有 HDFView 启动参数兼容？未知参数按文件路径还是明确报错？
16. 是否需要第三方视图扩展插件 API（替代 Java lib/ext）？
17. 是否保留 Java 的既有怪癖：属性名 256 字符截断、`enum.conversion` 默认关闭、删除不回收空间、SWMR 打开静默回退、`-java.version` GUI 弹窗？建议仅保留数据语义，不复刻缺陷。

---

## 附录 A：Java → Python API 映射（精选）

| 领域 | Java 实现 | h5py / Python |
|---|---|---|
| 打开文件 | `H5File.open(boolean,long)`；只读回退 | `h5py.File(path, mode)`；try `'r+'` except OSError 再 `'r'` |
| 创建文件 | `createFile` 两步 | `File(path,'w'/'w-'/'a')` |
| libver bounds | `setNewLibBounds`、`H5Pset_libver_bounds` | `File(..., libver='earliest'/'latest'/(v110,v112))`；`f.libver` |
| split/family | `H5Pset_fapl_split/family` | `driver='family'` 部分支持；split 需低层/ctypes |
| 根组/整树 | `loadIntoMemory`/`depth_first` | `f['/']`、递归 `keys()`；UI 懒加载 |
| 路径查找 | `H5File.get` | `f[path]` / `f.get(path)` |
| 成员顺序 | indexType/indexOrder | `list(g)`；`h5py.h5a.iterate`/`links.iterate` |
| 对象身份 | `H5Rcreate_object` oid | `h5py.h5o.get_info(obj.id).fileno/.addr` |
| hard/soft/external link | `H5Lcreate_*`；`createLink` | `f[name]=h5py.SoftLink/ExternalLink`；低层 `g.id.links.create_*` |
| link 目标 | `getLinkTargetName` | `get(name,getlink=True)`；`SoftLink.path`、`ExternalLink.filename/.path` |
| 断链 | `H5Link` 占位 | `keys()` 包含；`get` None；`[]` KeyError |
| 环检测 | `depth_first` 比较祖先 oid | 自写 `visited=set((fileno,addr))` |
| 建组 | `H5Group.create` | `create_group(name, track_order=...)` |
| 建数据集 | `H5ScalarDS.create`/`H5CompoundDS.create` | `create_dataset(shape=,dtype=,chunks=,maxshape=,compression=,fillvalue=)` |
| 建命名类型 | `createNamedDatatype`（H5Tcommit） | `h5py.h5t.py_create(dtype)` + `TypeID.commit(group.id,name)` |
| 删除/改名/移动 | `H5Ldelete`/`H5Lmove` | `del f[name]`；`f.move(src,dst)` |
| 复制 | `H5Ocopy` | `f.copy`/`obj.copy(..., expand_refs=, expand_external=, expand_soft=)` |
| 关闭 | `H5File.close` 强制关全部子对象 | `with h5py.File(...)` / `f.close()` |
| dataspace | dims/maxDims；`H5S_UNLIMITED=-1` | `shape`、`maxshape`；scalar=()，NULL=None |
| hyperslab 读取 | `H5Utils.selectHyperslab` | `dset[tuple(slice(...))]`；`read_direct` |
| 整页缓冲 | `Dataset.getData` | NumPy 数组 + 应用层 LRU/分块 |
| raw bytes | `readBytes` | `h5py.h5d.read` + 文件类型 id |
| 写入 | `H5Dwrite` | `dset[sel]=arr`；structured array 整块写 |
| chunk | `H5Pset_chunk`；`getChunkSize` | `chunks=`、`iter_chunks()`、`rdcc_nbytes/nslots/w0` |
| 过滤器读取 | `H5Pget_nfilters/filter` | `dset.compression/compression_opts/shuffle/fletcher32/scaleoffset`；低层 dcpl |
| 过滤器创建 | 仅 `H5Pset_deflate` | `compression='gzip'` 等高层参数 |
| fill value | `H5Pfill_value_defined/get/set` | `dset.fillvalue`；低层 `dcpl.fill_value_defined()` |
| 布局 | `H5Pget_layout`、`H5Dget_storage_size` | 低层 `dcpl.get_layout()`、`dset.id.get_storage_size()` |
| external storage | 只读 `H5Pget_external_count` | 读取透明；低层 `dcpl.get_external_count/get_external/set_external` |
| VDS | 只读 `H5Pget_virtual_*` | `dset.is_virtual`、`virtual_sources()`；低层 `dcpl.set_virtual` |
| dimension scales | 无 | `dset.dims[i].label`、`.keys()/.values()/.items()`、`attach_scale/detach_scale`；`h5py.h5ds` |
| 定长字符串 | `byteToString/stringToByte` | `S<n>` dtype；`.asstr(encoding=)` |
| 变长字符串 | `H5Dread_VLStrings` | `h5py.string_dtype(length=None)`；object ndarray |
| compound 读写 | 逐字段 `H5Dread/H5Dwrite` | structured dtype、`dset.fields([...])`、`h5py.vlen_dtype` |
| enum | HashMap + `H5Tget_member_value` | `h5py.enum_dtype`；`dtype.metadata['enum']` |
| vlen | `H5DreadVL` | `h5py.vlen_dtype`；object ndarray |
| array | `arrayDims/baseType` | `np.dtype((base, dims))` |
| opaque/bitfield | `H5Tget_tag`、`H5T_NATIVE_B*` | `h5py.opaque_dtype`/void；低层 `TypeOpaqueID.get_tag` |
| reference | `H5ReferenceType.init`、`H5Rget_*` | `ref_dtype/regionref_dtype`；`f[ref]`；`h5py.h5r.*` |
| 命名类型复制 | `copyDatatype`（H5Ocopy） | `h5py.h5o.copy` |
| 类型等价 | `H5Tequal`、`H5Tget_native_type` | `h5py.h5t` 低层比较 |
| unsigned 显示 | `convertFromUnsignedC/ToUnsignedC` | NumPy uint dtype + Python int 格式化 |
| 属性枚举 | `H5Aopen_by_idx` | `obj.attrs`；`h5py.h5a.iterate/open` |
| 属性读取 | `H5Aread/VLStrings/Complex/VL` | `attrs[name]` |
| 属性创建/更新 | `H5Acreate`/`H5Aopen_by_name` | `attrs.create`/`attrs.modify`/`attrs[name]=` |
| 属性删除/改名 | `H5Adelete`；`H5Arename_by_name` | `del attrs[name]`；`h5py.h5a.rename` |
| 属性 NULL | `H5Screate(H5S_NULL)` | `h5py.Empty(dtype)` |
| 图像/CF/PALETTE | `createImageAttributes`、`checkCFconvention`、`readPalette` | 手工读写 attrs + 引用解引用 |
| filter 插件 | `H5PL*` | `h5py.h5pl.*`；`HDF5_PLUGIN_PATH` |
| user block | `Tools.get/setHDF5UserBlock` | `userblock_size`；`open(path,'r+b')` |
| 导出 | `H5export_dataset` | 无等价物；numpy/text 自实现 |
| 错误处理 | 大量 catch 后返回 null/-1 | OSError/KeyError/ValueError/TypeError；显式上报 |

---

## 附录 B：现有测试与语料索引

**对象层测试（`object/src/test/java/object/`）**：`H5TestFile`、`H5BugFixTest`、`TestComplexDatatype`、`TestBFloat16Read`、`TestBFloat16DatatypeSize`、`H5ScalarDSTest`、`H5FileTest`、`H5DatatypeTest`、`H5GroupTest`、`H5CompoundDSTest`、`AttributeTest`、`CompoundDSTest`、`DatasetTest`、`DatatypeTest`、`FileFormatTest`、`GroupTest`、`ScalarDSTest`、`HObjectTest`、`DataFormatTest`、`HDF5Assertions`、`TestDataManager`、`TestFileExtension`，以及 `misc/` 下的 `TestH5File`、`TestH5Object`、`TestH5Table`、`TestH5MemoryLeak`、`DebugHDF`。

**GUI 测试（`hdfview/src/test/java/uitest/`）**：`TestHDFViewAccessMode`、`TestHDFViewLibBounds`、`TestTreeViewFiles`、`TestTreeViewFilters`、`TestTreeViewExport`、`TestTreeViewNewVLDatatypes`、`TestTreeViewNewMenu`、`TestHDFViewComplex`、`TestHDFViewFloat16`、`TestHDFViewIntConversions`、`TestHDFViewLinks`、`TestHDFViewRefs`、`TestHDFViewTAttr2`、`TestHDFViewAttributes`、`TestHDFViewCutCopyPaste`、`TestHDFViewImageConversion`、`TestHDFViewMenu`、`TestHDFViewInlineDataset`、`TestHDFViewDatasetSearch`、`TestHDFViewDatasetStatistics`、`TestHDFViewDatasetFrameSelection`、`TestHDFViewFileUsage`、`TestBoatload`，以及单元测试 `DatasetSearchEngineTest`、`DatasetStatisticsEngineTest`、`StatisticsHighlightMaskTest`、`I18nTest`、`ThemeManagerTest` 等。

**HDF5 语料（`hdfview/src/test/resources/uitest/`）**：`hdf5_test.h5`、`tarray4.h5`、`tattr2.h5`、`tattrintsize.h5`、`tattrreg.h5`、`tbfloat16.h5`、`tbfloat16_be.h5`、`tbitnopaque.h5`、`tcmpdattrintsize.h5`、`tcmpdints.h5`、`tcmpdintsize.h5`、`tcomplex.h5`、`tcomplex_be.h5`、`tcompound_complex.h5`、`tcompound_complex2.h5`、`tdatareg.h5`、`tfilters.h5`、`tfloat16.h5`、`tfloat16_be.h5`、`tfloat8.h5`、`tframeselection.h5`、`tintsattrs.h5`、`tintsize.h5`、`tldouble.h5`、`tordergr.h5`、`trefer_attr.h5`、`trefer_obj.h5`、`tscalarattrintsize.h5`、`tscalarintsize.h5`、`tscalarstring.h5`、`tstr.h5`、`DS64BITS.ctxt/ttxt/xtxt`、`h5ex_d_bzip2.h5`、`apollo17_earth.jpg`，以及 `HDF5UITests/BugFixTests/` 下的 `UTF8Strings.h5`、`unicode.h5`、`9055-deep.h5`、`HV-26-plan.h5` 等。

---

## 附录 C：调查来源

- 源码：`object/src/main/java/hdf/object/`（抽象层）、`object/src/main/java/hdf/object/h5/`（HDF5 实现）、`hdfview/src/main/java/hdf/view/`（GUI）、`hdfview/src/main/resources/hdf/view/i18n/messages*.properties`。
- 构建：`pom.xml`（HDF5 2.2.0）、`object/pom.xml`、`hdfview/pom.xml`、`repository/lib/jarhdf5-2.2.0.jar`、`build.properties.example`。
- 测试：`object/src/test/`、`hdfview/src/test/`、`hdfview/src/test/resources/uitest/`。
- 文档：`docs/UsersGuide/ug04treeview.html`、`ug05spreadsheet.html`、`ug06imageview.html`、`docs/Testing-Guide.md`、`README.md`、`CLAUDE.md`。
- Python 实测环境：h5py 3.16.0、HDF5 2.0.0、NumPy 2.5.3；关键实测结论：`trefer_obj.h5`/`trefer_attr.h5` 打开抛 `TypeError: Unknown reference type`；`d['field'][slice]=...` 静默不写；`attrs[name]=` 将 `|S8` 改为 object dtype；stock wheel `H5Zfilter_avail(FILTER_SZIP)=False`；`hasattr(h5py,'h5ds')=True`。

---

> **最终建议**：先做 Phase 0/1（corpus + h5 访问层 + CLI），用 headless 结果证明数据语义与 Java 一致，再投入 GUI 重建。HDF5 数据访问不是迁移的主要风险；**GUI 交互、特殊类型语义、引用/复合数据、以及 h5py 的少数硬缺口**才是。所有"Java 版能读但 h5py 默认读不了"的文件（新式引用、SZIP）必须在 Phase 0 用真实语料逐一验证，并在产品中给出明确的支持矩阵与错误提示。
