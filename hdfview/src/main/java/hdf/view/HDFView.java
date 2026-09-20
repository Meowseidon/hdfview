/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * Copyright by the Board of Trustees of the University of Illinois.         *
 * All rights reserved.                                                      *
 *                                                                           *
 * This file is part of the HDF Java Products distribution.                  *
 * The full copyright notice, including terms governing use, modification,   *
 * and redistribution, is contained in the COPYING file, which can be found  *
 * at the root of the source code distribution tree,                         *
 * or in https://www.hdfgroup.org/licenses.                                  *
 * If you do not have access to either file, you may request a copy from     *
 * help@hdfgroup.org.                                                        *
 ****************************************************************************/

package hdf.view;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import hdf.HDFVersions;
import hdf.object.Attribute;
import hdf.object.CompoundDS;
import hdf.object.DataFormat;
import hdf.object.Dataset;
import hdf.object.FileFormat;
import hdf.object.HObject;
import hdf.object.ScalarDS;
import hdf.view.DataView.DataView;
import hdf.view.DataView.DataViewFactory;
import hdf.view.DataView.DataViewFactoryProducer;
import hdf.view.DataView.DataViewManager;
import hdf.view.HelpView.HelpView;
import hdf.view.MetaDataView.MetaDataView;
import hdf.view.TableView.TableView;
import hdf.view.TableView.TableViewFactory;
import hdf.view.TreeView.DefaultTreeView;
import hdf.view.TreeView.TreeView;
import hdf.view.ViewProperties.DataViewType;
import hdf.view.dialog.FileUsageDialog;
import hdf.view.dialog.ImageConversionDialog;
import hdf.view.dialog.InputDialog;
import hdf.view.dialog.UserOptionsDialog;
import hdf.view.dialog.UserOptionsGeneralPage;
import hdf.view.dialog.UserOptionsHDFPage;
import hdf.view.dialog.UserOptionsNode;
import hdf.view.dialog.UserOptionsViewModulesPage;
import hdf.view.fileusage.FileUsageInspectorFactory;
import hdf.view.i18n.I18n;
import hdf.view.search.DatasetSearchDialog;
import hdf.view.search.DatasetSearchEngine;
import hdf.view.search.DatasetSearchResult;
import hdf.view.search.DatasetSearchSnapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

/**
 * HDFView is the main class of this HDF visual tool. It is used to layout the graphical components of the
 * hdfview. The major GUI components of the HDFView include Menubar, Toolbar, TreeView, ContentView, and
 * MessageArea.
 *
 * The HDFView is designed in such a way that it does not have direct access to the HDF library. All the HDF
 * library access is done through HDF objects. Therefore, the HDFView package depends on the object package
 * but not the library package. The source code of the view package (hdf.view) should be compiled with the
 * library package (hdf.hdflib and hdf.hdf5lib).
 *
 * @author Jordan T. Henderson
 * @version 2.4 2015
 */
public class HDFView implements DataViewManager {
    private static final Logger log = LoggerFactory.getLogger(HDFView.class);

    private static Display display;
    private static Shell mainWindow;
    private static ThemeManager themeManager;

    /** Determines whether HDFView is being executed for GUI testing. */
    private boolean isTesting = false;

    /** The directory where HDFView is installed. */
    private String rootDir;

    /** The initial directory where HDFView looks for files. */
    private String startDir;

    /** The current working directory. */
    private String currentDir;

    /** The current working file. */
    private String currentFile = null;

    /** The view properties. */
    private ViewProperties props;

    /** A list of tree view implementations. */
    private static List<String> treeViews;

    /** A list of image view implementations. */
    private static List<String> imageViews;

    /** A list of tree table implementations. */
    private static List<?> tableViews;

    /** A list of metadata view implementations. */
    private static List<?> metaDataViews;

    /** A list of palette view implementations. */
    private static List<?> paletteViews;

    /** A list of help view implementations. */
    private static List<?> helpViews;

    /** The list of GUI components related to NetCDF3. */
    private final List<MenuItem> n3GUIs = new ArrayList<>();

    /** The list of GUI components related to HDF4. */
    private final List<MenuItem> h4GUIs = new ArrayList<>();

    /** The list of GUI components related to HDF5. */
    private final List<MenuItem> h5GUIs = new ArrayList<>();

    /* The list of GUI components related to editing */
    // private final List<?>            editGUIs;

    /** GUI component: the TreeView. */
    private TreeView treeView = null;

    private static final String JAVA_VERSION    = HDFVersions.getPropertyVersionJava();
    private static final String HDF4_VERSION    = HDFVersions.getPropertyVersionHDF4();
    private static final String HDF5_VERSION    = HDFVersions.getPropertyVersionHDF5();
    private static final String HDFVIEW_VERSION = HDFVersions.getPropertyVersionView();
    private static final String HDFVIEW_USERSGUIDE_URL =
        "https://support.hdfgroup.org/documentation/hdfview/HDFView+3.x+User%27s+Guide";
    private static final String JAVA_COMPILER = "jdk " + JAVA_VERSION;
    /** GUI component: The toolbar for open, close, help and hdf4 and hdf5 library information. */
    private ToolBar toolBar;

    /** GUI component: The text area for showing status messages. */
    private Text status;

    /** GUI component: The area for object view. */
    private ScrolledComposite treeArea;

    /** GUI component: The area for quick general view. */
    private ScrolledComposite generalArea;

    /** Long-lived tab host for the right side of the main window. */
    private TabFolder rightTabFolder;

    /** Stable ScrolledComposite content which owns rightTabFolder. */
    private Composite rightTabContent;

    /** The optional inline Data Content tab for the selected ordinary Dataset. */
    private TabItem dataContentTab;

    /** The one inline default TableView currently hosted by the main window. */
    private TableView inlineTableView;

    /** Object represented by the currently populated right-side tabs. */
    private HObject displayedMetadataObject;

    /** GUI component: To add and display URLs. */
    private Combo urlBar;

    private Button recentFilesButton;
    private Button clearTextButton;
    private Button fileUsageButton;
    private FileUsageDialog fileUsageDialog;

    /** Inline selector showing and changing the access mode of the selected file. */
    private Combo accessModeSelector;
    private boolean updatingAccessModeSelector;

    private static final int ACCESS_MODE_READ_ONLY_INDEX  = 0;
    private static final int ACCESS_MODE_READ_WRITE_INDEX = 1;
    private static final String ACCESS_MODE_SELECTOR_ID   = "accessModeSelector";

    /** File represented by the inline access-mode selector. */
    private FileFormat accessModeFile;

    /** GUI component: A list of current data windows. */
    private Menu windowMenu;

    /** Language radio items in the Tools menu. */
    private MenuItem englishLanguageItem;
    private MenuItem simplifiedChineseLanguageItem;

    /** The modeless Dataset content search window, if it is open. */
    private DatasetSearchDialog datasetSearchDialog;

    /** Current worker and cooperative cancellation flag for Dataset search. */
    private Thread datasetSearchThread;
    private AtomicBoolean datasetSearchCancel;
    private long datasetSearchGeneration;

    /* GUI component: File menu on the menubar */
    // private final Menu               fileMenu;

    /** The font to be used for display text on all Controls. */
    private Font currentFont;

    private UserOptionsDialog userOptionDialog;

    /** State of refresh. */
    public boolean viewerState = false;

    /** Timer for refresh functions. */
    private final Runnable timer = new Runnable() {
        public void run()
        {
            // The default Dataset TableView is hosted by the main window rather
            // than represented by a top-level Shell.
            if (inlineTableView != null && !inlineTableView.isViewDisposed()) {
                HObject obj = inlineTableView.getDataObject();
                if (obj != null && obj.getFileFormat() != null &&
                    obj.getFileFormat().isThisType(FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF5))) {
                    inlineTableView.refreshDataTable();
                }
            }

            // refresh each table displaying data
            Shell[] shellList = display.getShells();
            if (shellList != null) {
                for (int i = 0; i < shellList.length; i++) {
                    if (!shellList[i].equals(mainWindow)) {
                        DataView view = (DataView)shellList[i].getData();
                        if ((view != null) && (view instanceof TableView)) {
                            HObject obj = view.getDataObject();
                            if (obj == null || obj.getFileFormat() == null || !(obj instanceof DataFormat))
                                continue;

                            FileFormat file = obj.getFileFormat();
                            if (file.isThisType(FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF5)))
                                ((TableView)view).refreshDataTable();
                        }
                    }
                }
            }
            log.trace("viewerState = {}", viewerState);
            if (viewerState)
                display.timerExec(ViewProperties.getTimerRefresh(), timer);
            else
                display.timerExec(-1, timer);
        }
    };

    /**
     * Constructs HDFView with a given root directory, where the HDFView is installed, and opens the given
     * files in the viewer.
     *
     * @param root      the directory where the HDFView is installed.
     * @param startPath the starting directory for file searches
     */
    public HDFView(String root, String startPath)
    {
        log.debug("Root is {}", root);

        if (display == null || display.isDisposed())
            display = new Display();
        themeManager = ThemeManager.forDisplay(display);

        rootDir  = root;
        startDir = startPath;

        // editGUIs = new Vector<Object>();

        props = new ViewProperties(rootDir, startDir);
        try {
            props.load();
        }
        catch (Exception ex) {
            log.debug("Failed to load View Properties from {}", rootDir);
        }
        ensureDefaultViewModules();

        I18n.initialize(props);

        ViewProperties.loadIcons();

        String workDir = System.getProperty("hdfview.workdir");
        if (workDir != null)
            currentDir = workDir;
        else
            currentDir = ViewProperties.getWorkDir();

        if (currentDir == null)
            currentDir = System.getProperty("user.dir");

        log.info("Current directory is {}", currentDir);

        try {
            currentFont =
                new Font(display, ViewProperties.getFontType(), ViewProperties.getFontSize(), SWT.NORMAL);
        }
        catch (Exception ex) {
            currentFont = null;
        }

        if (FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF5) != null)
            initPluginPaths();

        treeViews     = ViewProperties.getTreeViewList();
        metaDataViews = ViewProperties.getMetaDataViewList();
        tableViews    = ViewProperties.getTableViewList();
        imageViews    = ViewProperties.getImageViewList();
        paletteViews  = ViewProperties.getPaletteViewList();
        helpViews     = ViewProperties.getHelpViewList();

        log.debug("Constructor exit");
    }

    /**
     * Keep a usable built-in view selection when a new or unreadable user
     * properties file prevents ViewProperties.load() from finishing.  The
     * normal properties path already installs these entries; this only
     * restores the invariant required by DataViewFactoryProducer.
     */
    private void ensureDefaultViewModules()
    {
        List<List<String>> moduleLists = Arrays.asList(ViewProperties.getTreeViewList(),
                                                        ViewProperties.getMetaDataViewList(),
                                                        ViewProperties.getTableViewList(),
                                                        ViewProperties.getImageViewList(),
                                                        ViewProperties.getPaletteViewList());
        for (List<String> moduleList : moduleLists) {
            if (moduleList != null && moduleList.isEmpty())
                moduleList.add(ViewProperties.DEFAULT_MODULE_TEXT);
        }
    }

    /**
     * Establishes the HDF5 filter plugin search paths for this HDFView session.
     *
     * The search list is the
     * bundled install directory followed by whatever paths the HDF5 library already knows
     * (its compiled-in defaults plus any HDF5_PLUGIN_PATH entries). Users can
     * further adjust the list through the User Options dialog.
     *
     * The bundled directory must be added at runtime because a jpackage installation is
     * relocatable: filter plugins ship at <root>/plugin (e.g.
     * <install>/lib/app/plugin on Linux, where hdfview.root resolves to
     * $APPDIR), but the HDF5 library only supports absolute, build-time plugin
     * paths, so its defaults never point at the actual install location.
     */
    private void initPluginPaths()
    {
        // Start from the paths the HDF5 library already has registered.
        ViewProperties.loadPluginPaths();

        if (rootDir == null)
            return;

        File pluginDir = new File(rootDir, "plugin");
        if (!pluginDir.isDirectory()) {
            log.debug("No bundled plugin directory at {}", pluginDir.getAbsolutePath());
            return;
        }

        String pluginPath = pluginDir.getAbsolutePath();
        for (String existing : ViewProperties.getPluginPaths()) {
            if (pluginPath.equals(existing)) {
                log.debug("Bundled plugin path {} already present", pluginPath);
                return;
            }
        }

        // Prepend so the bundled filters take precedence over system defaults.
        ViewProperties.prependPluginPath(pluginPath);
        log.debug("Added bundled plugin path {}", pluginPath);
    }

    /**
     * Creates HDFView with a given size, and opens the given files in the viewer.
     *
     * @param flist
     *            a list of files to open.
     * @param width
     *            the width of the app in pixels
     * @param height
     *            the height of the app in pixels
     * @param x
     *            the coord x of the app in pixels
     * @param y
     *            the coord y of the app in pixels
     *
     * @return
     *            the newly-created HDFView Shell
     */
    public Shell openMainWindow(List<File> flist, int width, int height, int x, int y)
    {
        log.debug("openMainWindow enter current directory is {}", currentDir);

        // Initialize all GUI components
        mainWindow = createMainWindow();

        try {
            Font font    = null;
            String fType = ViewProperties.getFontType();
            int fSize    = ViewProperties.getFontSize();

            try {
                font = new Font(display, fType, fSize, SWT.NORMAL);
            }
            catch (Exception ex) {
                log.debug("Failed to load font");
                font = null;
            }

            if (font != null)
                updateFont(font);
        }
        catch (Exception ex) {
            log.debug("Failed to load Font properties");
        }

        // Make sure all GUI components are in place before
        // opening any files
        mainWindow.pack();

        int nfiles   = flist.size();
        File theFile = null;
        for (int i = 0; i < nfiles; i++) {
            theFile = flist.get(i);

            if (theFile.isFile()) {
                currentDir  = theFile.getParentFile().getAbsolutePath();
                currentFile = theFile.getAbsolutePath();

                try {
                    int accessMode = FileFormat.WRITE;
                    if (ViewProperties.isReadOnly())
                        accessMode = FileFormat.READ;
                    else if (ViewProperties.isReadSWMR())
                        accessMode = FileFormat.READ | FileFormat.MULTIREAD;
                    treeView.openFile(currentFile, accessMode);

                    try {
                        urlBar.remove(currentFile);
                    }
                    catch (Exception ex) {
                    }

                    // first entry is always the workdir
                    urlBar.add(currentFile, 1);
                    urlBar.select(1);
                }
                catch (Exception ex) {
                    showError(ex.toString());
                }
            }
            else {
                currentDir = theFile.getAbsolutePath();
            }

            log.info("CurrentDir is {}", currentDir);
        }

        if (FileFormat.getFileFormat(FileFormat.FILE_TYPE_NC3) == null)
            setEnabled(n3GUIs, false);

        if (FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF4) == null)
            setEnabled(h4GUIs, false);

        if (FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF5) == null)
            setEnabled(h5GUIs, false);

        // Set size of main window
        // float inset = 0.17f; // for UG only.
        float inset  = 0.04f;
        Point winDim = new Point(width, height);

        // If given height and width are too small, adjust accordingly
        if (height <= 300)
            winDim.y = (int)((1 - 2 * inset) * mainWindow.getSize().y);

        if (width <= 300)
            winDim.x = (int)(0.9 * mainWindow.getSize().y);

        mainWindow.setLocation(x, y);
        mainWindow.setSize(winDim.x + 200, winDim.y);

        // Display the window
        mainWindow.open();
        log.debug("openMainWindow exit");
        return mainWindow;
    }

    /** switch processing to the main application window. */
    public void runMainWindow()
    {
        log.debug("runMainWindow enter");

        while (!mainWindow.isDisposed()) {
            // ===================================================
            // Wrap each event dispatch in an exception handler
            // so that if any event causes an exception it does
            // not break the main UI loop
            // ===================================================
            try {
                if (!display.readAndDispatch())
                    display.sleep();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (!isTesting)
            display.dispose();
        log.debug("runMainWindow exit");
    }

    /**
     * Creates and lays out GUI components.
     *
     * <pre>
     * ||=========||=============================||
     * ||         ||                             ||
     * ||         ||                             ||
     * || TreeView||       ContentPane           ||
     * ||         ||                             ||
     * ||=========||=============================||
     * ||            Message Area                ||
     * ||========================================||
     * </pre>
     *
     * @return the shell object of the main window
     */
    private Shell createMainWindow()
    {
        // Create a new display window
        final Shell shell = new Shell(display);
        themeManager.applyTo(shell);
        shell.setImages(ViewProperties.getHdfIcons());
        shell.setFont(currentFont);
        I18n.bind(shell, "window.title", HDFVIEW_VERSION);
        shell.setLayout(new GridLayout(5, false));
        shell.addListener(SWT.Close, event -> {
            if (inlineTableView != null && !inlineTableView.isViewDisposed())
                inlineTableView.commitActiveCellEditor();
        });
        shell.addDisposeListener(new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent e)
            {
                ViewProperties.setRecentFiles(new ArrayList<>(Arrays.asList(urlBar.getItems())));

                try {
                    props.save();
                }
                catch (Exception ex) {
                }

                cancelDatasetSearch();
                if (fileUsageDialog != null)
                    fileUsageDialog.dispose();

                synchronized (DatasetSearchEngine.NATIVE_IO_LOCK) {
                    disposeInlineDataView();
                    closeAllWindows();

                    // Close all open files
                    try {
                        List<FileFormat> filelist = treeView.getCurrentFiles();

                        if ((filelist != null) && !filelist.isEmpty()) {
                            Object[] files = filelist.toArray();

                            for (int i = 0; i < files.length; i++) {
                                try {
                                    treeView.closeFile((FileFormat)files[i]);
                                }
                                catch (Exception ex) {
                                    continue;
                                }
                            }
                        }
                    }
                    catch (Exception ex) {
                    }
                }

                if (currentFont != null)
                    currentFont.dispose();
            }
        });

        createMenuBar(shell);
        createToolbar(shell);
        createUrlToolbar(shell);
        createContentArea(shell);

        log.info("Main Window created");

        return shell;
    }

    private void createMenuBar(final Shell shell)
    {
        Menu menu = new Menu(shell, SWT.BAR);
        shell.setMenuBar(menu);

        MenuItem menuItem = new MenuItem(menu, SWT.CASCADE);
        I18n.bind(menuItem, "menu.file");

        Menu fileMenu = new Menu(menuItem);
        menuItem.setMenu(fileMenu);

        MenuItem item = new MenuItem(fileMenu, SWT.PUSH);
        I18n.bind(item, "menu.file.open");
        item.setAccelerator(SWT.MOD1 + 'O');
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                openLocalFile(null, -1);
            }
        });

        item = new MenuItem(fileMenu, SWT.CASCADE);
        I18n.bind(item, "menu.file.openAs");

        Menu openAsMenu = new Menu(item);
        item.setMenu(openAsMenu);

        item = new MenuItem(openAsMenu, SWT.PUSH);
        I18n.bind(item, "menu.file.openAs.readOnly");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                openLocalFile(null, FileFormat.READ);
            }
        });

        item = new MenuItem(openAsMenu, SWT.PUSH);
        I18n.bind(item, "menu.file.openAs.swmr");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                openLocalFile(null, FileFormat.READ | FileFormat.MULTIREAD);
            }
        });

        item = new MenuItem(openAsMenu, SWT.PUSH);
        I18n.bind(item, "menu.file.openAs.readWrite");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                openLocalFile(null, FileFormat.WRITE);
            }
        });

        new MenuItem(fileMenu, SWT.SEPARATOR);

        MenuItem fileNewMenu = new MenuItem(fileMenu, SWT.CASCADE);
        I18n.bind(fileNewMenu, "menu.file.new");

        Menu newMenu = new Menu(fileNewMenu);
        fileNewMenu.setMenu(newMenu);

        item = new MenuItem(newMenu, SWT.PUSH);
        I18n.bind(item, "menu.file.new.hdf4");
        h4GUIs.add(item);
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (currentDir != null)
                    currentDir += File.separator;
                else
                    currentDir = "";

                String filename = null;

                if (!isTesting) {
                    FileDialog fChooser = new FileDialog(shell, SWT.SAVE);
                    fChooser.setFileName(Tools.checkNewFile(currentDir, ".hdf").getName());

                    DefaultFileFilter filter = DefaultFileFilter.getFileFilterHDF4();
                    fChooser.setFilterExtensions(new String[] {filter.getExtensions()});
                    fChooser.setFilterNames(new String[] {filter.getDescription()});
                    fChooser.setFilterIndex(0);

                    filename = fChooser.open();
                }
                else {
                    // Prepend test file directory to filename
                    filename = currentDir.concat(new InputDialog(mainWindow,
                                                                  I18n.text("dialog.enterFileName.title"),
                                                                  "").open());
                }

                if (filename == null)
                    return;

                try {
                    log.trace("HDFView create hdf4 file");
                    FileFormat theFile = Tools.createNewFile(filename, currentDir, FileFormat.FILE_TYPE_HDF4,
                                                             getTreeView().getCurrentFiles());

                    if (theFile == null)
                        return;

                    currentDir = theFile.getParent();
                }
                catch (Exception ex) {
                    Tools.showError(mainWindow, I18n.text("action.create"), ex.getMessage());
                    return;
                }

                try {
                    treeView.openFile(filename, FileFormat.WRITE);
                    currentFile = filename;

                    try {
                        urlBar.remove(filename);
                    }
                    catch (Exception ex) {
                        log.debug("unable to remove {} from urlBar", filename);
                    }

                    // first entry is always the workdir
                    urlBar.add(filename, 1);
                    urlBar.select(1);
                }
                catch (Exception ex) {
                    display.beep();
                    Tools.showError(mainWindow, I18n.text("action.create"), ex.getMessage() + "\n" + filename);
                }
            }
        });

        item = new MenuItem(newMenu, SWT.PUSH);
        I18n.bind(item, "menu.file.new.hdf5");
        h5GUIs.add(item);
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (currentDir != null)
                    currentDir += File.separator;
                else
                    currentDir = "";

                String filename = null;

                if (!isTesting) {
                    FileDialog fChooser = new FileDialog(shell, SWT.SAVE);
                    fChooser.setFileName(Tools.checkNewFile(currentDir, ".h5").getName());

                    DefaultFileFilter filter = DefaultFileFilter.getFileFilterHDF5();
                    fChooser.setFilterExtensions(new String[] {filter.getExtensions()});
                    fChooser.setFilterNames(new String[] {filter.getDescription()});
                    fChooser.setFilterIndex(0);

                    filename = fChooser.open();
                }
                else {
                    // Prepend test file directory to filename
                    filename = currentDir.concat(new InputDialog(mainWindow,
                                                                  I18n.text("dialog.enterFileName.title"),
                                                                  "").open());
                }

                if (filename == null)
                    return;

                try {
                    log.trace("HDFView create hdf5 file");
                    FileFormat theFile = Tools.createNewFile(filename, currentDir, FileFormat.FILE_TYPE_HDF5,
                                                             getTreeView().getCurrentFiles());

                    if (theFile == null)
                        return;

                    currentDir = theFile.getParent();
                }
                catch (Exception ex) {
                    Tools.showError(mainWindow, I18n.text("action.create"), ex.getMessage());
                    return;
                }

                try {
                    treeView.openFile(filename, FileFormat.WRITE);
                    currentFile = filename;

                    try {
                        urlBar.remove(filename);
                    }
                    catch (Exception ex) {
                        log.debug("unable to remove {} from urlBar", filename);
                    }

                    // first entry is always the workdir
                    urlBar.add(filename, 1);
                    urlBar.select(1);
                }
                catch (Exception ex) {
                    display.beep();
                    Tools.showError(mainWindow, I18n.text("action.create"), ex.getMessage() + "\n" + filename);
                }
            }
        });

        new MenuItem(fileMenu, SWT.SEPARATOR);

        item = new MenuItem(fileMenu, SWT.PUSH);
        I18n.bind(item, "menu.file.close");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                closeFile(treeView.getSelectedFile());
            }
        });

        item = new MenuItem(fileMenu, SWT.PUSH);
        I18n.bind(item, "menu.file.closeAll");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                cancelDatasetSearch();
                closeAllWindows(true);

                List<FileFormat> files = treeView.getCurrentFiles();
                while (!files.isEmpty()) {
                    try {
                        synchronized (DatasetSearchEngine.NATIVE_IO_LOCK) {
                            treeView.closeFile(files.get(0));
                        }
                    }
                    catch (Exception ex) {
                        log.trace("unable to close {} in treeView", files.get(0));
                    }
                }

                currentFile = null;

                /* Keep the right-side tab host alive for the next file. */
                disposeInlineDataView();
                clearRightTabs();
                displayedMetadataObject = null;
                updateAccessModeStatus(null);
                syncFileUsageSelectionFromTree();
                layoutRightTabs();

                urlBar.setText("");
            }
        });

        new MenuItem(fileMenu, SWT.SEPARATOR);

        item = new MenuItem(fileMenu, SWT.PUSH);
        I18n.bind(item, "menu.file.save");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (treeView.getCurrentFiles().isEmpty()) {
                    Tools.showError(mainWindow, I18n.text("message.save.title"),
                                    I18n.text("message.noFilesOpen"));
                    return;
                }

                if (treeView.getSelectedFile() == null) {
                    Tools.showError(mainWindow, I18n.text("message.save.title"),
                                    I18n.text("message.noFilesSelected"));
                    return;
                }

                // Save what has been changed in memory into file
                writeDataToFile(treeView.getSelectedFile());
            }
        });

        item = new MenuItem(fileMenu, SWT.PUSH);
        I18n.bind(item, "menu.file.saveAs");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (treeView.getCurrentFiles().isEmpty()) {
                    Tools.showError(mainWindow, I18n.text("message.save.title"),
                                    I18n.text("message.noFilesOpen"));
                    return;
                }

                if (treeView.getSelectedFile() == null) {
                    Tools.showError(mainWindow, I18n.text("message.save.title"),
                                    I18n.text("message.noFilesSelected"));
                    return;
                }

                try {
                    treeView.saveFile(treeView.getSelectedFile());
                }
                catch (Exception ex) {
                    display.beep();
                    Tools.showError(mainWindow, I18n.text("action.save"), ex.getMessage());
                }
            }
        });

        new MenuItem(fileMenu, SWT.SEPARATOR);

        item = new MenuItem(fileMenu, SWT.PUSH);
        I18n.bind(item, "menu.file.exit");
        item.setAccelerator(SWT.MOD1 + 'Q');
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                mainWindow.dispose();
            }
        });

        menuItem = new MenuItem(menu, SWT.CASCADE);
        I18n.bind(menuItem, "menu.window");

        windowMenu = new Menu(menuItem);
        menuItem.setMenu(windowMenu);

        item = new MenuItem(windowMenu, SWT.PUSH);
        I18n.bind(item, "menu.window.cascade");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                cascadeWindows();
            }
        });

        item = new MenuItem(windowMenu, SWT.PUSH);
        I18n.bind(item, "menu.window.tile");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                tileWindows();
            }
        });

        new MenuItem(windowMenu, SWT.SEPARATOR);

        item = new MenuItem(windowMenu, SWT.PUSH);
        I18n.bind(item, "menu.window.closeAll");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                closeAllWindows();
            }
        });

        new MenuItem(windowMenu, SWT.SEPARATOR);

        menuItem = new MenuItem(menu, SWT.CASCADE);
        I18n.bind(menuItem, "menu.tools");

        Menu toolsMenu = new Menu(menuItem);
        menuItem.setMenu(toolsMenu);

        MenuItem convertMenuItem = new MenuItem(toolsMenu, SWT.CASCADE);
        I18n.bind(convertMenuItem, "menu.tools.convertImage");

        Menu convertMenu = new Menu(convertMenuItem);
        convertMenuItem.setMenu(convertMenu);

        item = new MenuItem(convertMenu, SWT.PUSH);
        I18n.bind(item, "menu.tools.convertImage.hdf4");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                convertFile(Tools.FILE_TYPE_IMAGE, FileFormat.FILE_TYPE_HDF4);
            }
        });
        h4GUIs.add(item);

        item = new MenuItem(convertMenu, SWT.PUSH);
        I18n.bind(item, "menu.tools.convertImage.hdf5");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                convertFile(Tools.FILE_TYPE_IMAGE, FileFormat.FILE_TYPE_HDF5);
            }
        });
        h5GUIs.add(item);

        new MenuItem(toolsMenu, SWT.SEPARATOR);

        MenuItem languageMenuItem = new MenuItem(toolsMenu, SWT.CASCADE);
        I18n.bind(languageMenuItem, "menu.tools.language");

        Menu languageMenu = new Menu(languageMenuItem);
        languageMenuItem.setMenu(languageMenu);

        englishLanguageItem = new MenuItem(languageMenu, SWT.RADIO);
        I18n.bind(englishLanguageItem, "menu.tools.language.english");
        englishLanguageItem.setSelection(I18n.getLanguage() == I18n.Language.ENGLISH);
        englishLanguageItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                changeLanguage(I18n.Language.ENGLISH);
            }
        });

        simplifiedChineseLanguageItem = new MenuItem(languageMenu, SWT.RADIO);
        I18n.bind(simplifiedChineseLanguageItem, "menu.tools.language.simplifiedChinese");
        simplifiedChineseLanguageItem.setSelection(I18n.getLanguage() == I18n.Language.SIMPLIFIED_CHINESE);
        simplifiedChineseLanguageItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                changeLanguage(I18n.Language.SIMPLIFIED_CHINESE);
            }
        });

        item = new MenuItem(toolsMenu, SWT.PUSH);
        I18n.bind(item, "menu.tools.searchDatasetContent");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                openDatasetSearchDialog();
            }
        });

        item = new MenuItem(toolsMenu, SWT.PUSH);
        I18n.bind(item, "menu.tools.preferences");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                openUserOptionsDialog(shell);
            }
        });

        new MenuItem(toolsMenu, SWT.SEPARATOR);

        item = new MenuItem(toolsMenu, SWT.PUSH);
        I18n.bind(item, "menu.tools.register");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                registerFileFormat();
            }
        });

        item = new MenuItem(toolsMenu, SWT.PUSH);
        I18n.bind(item, "menu.tools.unregister");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                unregisterFileFormat();
            }
        });

        menuItem = new MenuItem(menu, SWT.CASCADE);
        I18n.bind(menuItem, "menu.help");

        Menu helpMenu = new Menu(menuItem);
        menuItem.setMenu(helpMenu);

        item = new MenuItem(helpMenu, SWT.PUSH);
        I18n.bind(item, "menu.help.usersGuide");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                String usersGuideURL = ViewProperties.getUsersGuide();
                log.trace("usersGuideURL: {}", usersGuideURL);
                URL urlObject;
                try {
                    if (usersGuideURL != null)
                        urlObject = new URI(usersGuideURL).toURL();
                    else
                        urlObject = new URI(HDFVIEW_USERSGUIDE_URL).toURL();
                    org.eclipse.swt.program.Program.launch(urlObject.toString());
                }
                catch (Exception ex) {
                    log.debug("Could not instantiate Browser: {}", ex);
                }
            }
        });

        if ((helpViews != null) && !helpViews.isEmpty()) {
            int n = helpViews.size();
            for (int i = 0; i < n; i++) {
                HelpView theView = (HelpView)helpViews.get(i);
                item             = new MenuItem(helpMenu, SWT.PUSH);
                item.setText(theView.getLabel());
                // item.setActionCommand(theView.getActionCommand());
            }
        }

        new MenuItem(helpMenu, SWT.SEPARATOR);

        item = new MenuItem(helpMenu, SWT.PUSH);
        I18n.bind(item, "menu.help.hdf4Library");
        h4GUIs.add(item);
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                new LibraryVersionDialog(shell, FileFormat.FILE_TYPE_HDF4).open();
            }
        });

        item = new MenuItem(helpMenu, SWT.PUSH);
        I18n.bind(item, "menu.help.hdf5Library");
        h5GUIs.add(item);
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                new LibraryVersionDialog(shell, FileFormat.FILE_TYPE_HDF5).open();
            }
        });

        item = new MenuItem(helpMenu, SWT.PUSH);
        I18n.bind(item, "menu.help.javaVersion");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                new JavaVersionDialog(mainWindow).open();
            }
        });

        new MenuItem(helpMenu, SWT.SEPARATOR);

        item = new MenuItem(helpMenu, SWT.PUSH);
        I18n.bind(item, "menu.help.supportedFileFormats");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                new SupportedFileFormatsDialog(mainWindow).open();
            }
        });

        new MenuItem(helpMenu, SWT.SEPARATOR);

        item = new MenuItem(helpMenu, SWT.PUSH);
        I18n.bind(item, "menu.help.about");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                new AboutDialog(mainWindow).open();
            }
        });

        setEnabled(Arrays.asList(windowMenu.getItems()), false);

        // Set up handlers for system menu items (e.g., About and Preferences on macOS)
        setupSystemMenuHandlers(shell);

        log.info("Menubar created");
    }

    /**
     * Sets up handlers for system menu items (SWT.ID_ABOUT and SWT.ID_PREFERENCES).
     * On platforms that provide a system menu (e.g., macOS application menu), this connects
     * the platform-specific menu items to the application's dialogs. On other platforms,
     * the system menu will be null and these handlers are safely ignored.
     *
     * @param shell the main shell to attach menu handlers to
     */
    private void setupSystemMenuHandlers(final Shell shell)
    {
        Menu systemMenu = shell.getDisplay().getSystemMenu();

        if (systemMenu != null) {
            MenuItem[] items = systemMenu.getItems();

            for (MenuItem item : items) {
                int id = item.getID();

                if (id == SWT.ID_ABOUT) {
                    item.addListener(SWT.Selection, event -> {
                        log.debug("System About menu triggered");
                        new AboutDialog(shell).open();
                    });
                }
                else if (id == SWT.ID_PREFERENCES) {
                    item.addListener(SWT.Selection, event -> {
                        log.debug("System Preferences menu triggered");
                        openUserOptionsDialog(shell);
                    });
                }
            }

            log.info("System menu handlers configured");
        }
    }

    /**
     * Apply a language selection to the current HDFView session and persist it
     * through the existing user preference store.
     *
     * <p>The widget tree is refreshed in place. No file, TreeItem, Dataset, or
     * DataView is recreated as part of a language change.</p>
     *
     * @param language the selected UI language
     */
    private void changeLanguage(I18n.Language language)
    {
        I18n.setLanguage(language);
        props.setValue(ViewProperties.LANGUAGE_PROPERTY, language.getPropertyValue());

        try {
            props.save();
        }
        catch (Exception ex) {
            log.warn("Unable to persist HDFView language preference", ex);
            showError(I18n.text("status.errorSavingLanguage", ex.getMessage()));
        }

        I18n.refreshDisplay(display);
        if (userOptionDialog != null && userOptionDialog.getShell() != null &&
            !userOptionDialog.getShell().isDisposed())
            userOptionDialog.refreshLanguage();
        if (fileUsageDialog != null && !fileUsageDialog.getShell().isDisposed())
            fileUsageDialog.refreshLanguage();

        if (englishLanguageItem != null && !englishLanguageItem.isDisposed())
            englishLanguageItem.setSelection(language == I18n.Language.ENGLISH);
        if (simplifiedChineseLanguageItem != null && !simplifiedChineseLanguageItem.isDisposed())
            simplifiedChineseLanguageItem.setSelection(language == I18n.Language.SIMPLIFIED_CHINESE);

        String languageLabelKey = language == I18n.Language.ENGLISH
            ? "menu.tools.language.english"
            : "menu.tools.language.simplifiedChinese";
        showStatus(I18n.text("status.languageChanged", I18n.text(languageLabelKey)));
    }

    /** Open the global Dataset name/value search window. */
    public void openDatasetSearchDialog()
    {
        if (datasetSearchDialog != null && datasetSearchDialog.getShell() != null &&
            !datasetSearchDialog.getShell().isDisposed()) {
            datasetSearchDialog.getShell().forceActive();
            return;
        }

        datasetSearchDialog = new DatasetSearchDialog(this, mainWindow);
        datasetSearchDialog.open();
    }

    /**
     * Start one Dataset search on a worker thread.  All callbacks are marshalled
     * back to the SWT display before they touch the search window.
     */
    public void startDatasetSearch(final DatasetSearchDialog dialog, final String query,
                                   final DatasetSearchEngine.SearchMode mode)
    {
        if (dialog == null || dialog.getShell() == null || dialog.getShell().isDisposed())
            return;

        cancelDatasetSearch();
        final long generation = ++datasetSearchGeneration;
        final AtomicBoolean cancellation = new AtomicBoolean(false);
        datasetSearchCancel = cancellation;

        final List<FileFormat> files = treeView == null
            ? new ArrayList<>() : new ArrayList<>(treeView.getCurrentFiles());
        final List<DatasetSearchSnapshot> dirtySnapshots = captureDirtySearchSnapshots();
        if (files.isEmpty()) {
            dialog.addError(I18n.text("common.file"), I18n.text("message.noFilesOpen"));
            dialog.finish(null);
            return;
        }

        DatasetSearchEngine.Listener listener = new DatasetSearchEngine.Listener() {
            @Override
            public void onResults(List<DatasetSearchResult> results)
            {
                postDatasetSearchUpdate(generation, dialog, () -> dialog.addResults(results));
            }

            @Override
            public void onProgress(long processedElements, long totalElements, String datasetPath)
            {
                postDatasetSearchUpdate(generation, dialog,
                                        () -> dialog.updateProgress(processedElements, totalElements,
                                                                     datasetPath));
            }

            @Override
            public void onError(String datasetPath, String message, Throwable error)
            {
                String displayMessage = error instanceof UnsupportedOperationException
                    ? I18n.text("search.unsupportedDatatype", message) : message;
                postDatasetSearchUpdate(generation, dialog,
                                        () -> dialog.addError(datasetPath, displayMessage));
            }
        };

        DatasetSearchEngine engine = new DatasetSearchEngine();
        datasetSearchThread = new Thread(() -> {
            DatasetSearchEngine.SearchSummary summary;
            try {
                summary = engine.search(files, query, mode, cancellation, listener, dirtySnapshots);
            }
            catch (Throwable error) {
                log.warn("Dataset content search failed", error);
                final String message = error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage();
                postDatasetSearchUpdate(generation, dialog,
                                        () -> dialog.addError(I18n.text("common.file"), message));
                summary = null;
            }

            final DatasetSearchEngine.SearchSummary completed = summary;
            postDatasetSearchUpdate(generation, dialog, () -> dialog.finish(completed));
        }, "HDFView-DatasetSearch");
        datasetSearchThread.setDaemon(true);
        datasetSearchThread.start();
    }

    /**
     * Capture only the already-open TableView buffers that are dirty.  The
     * active editor is committed into the existing buffer, but no save is
     * performed and the user keeps the normal dirty/save-confirmation state.
     */
    private List<DatasetSearchSnapshot> captureDirtySearchSnapshots()
    {
        List<DatasetSearchSnapshot> snapshots = new ArrayList<>();
        List<TableView> views = new ArrayList<>();
        if (inlineTableView != null && !inlineTableView.isViewDisposed())
            views.add(inlineTableView);

        if (display != null && !display.isDisposed()) {
            for (Shell shell : display.getShells()) {
                Object data = shell.getData();
                if (data instanceof TableView && !((TableView)data).isViewDisposed())
                    views.add((TableView)data);
            }
        }

        for (TableView view : views) {
            try {
                view.commitActiveCellEditor();
                DatasetSearchSnapshot snapshot = view.getSearchSnapshot();
                if (snapshot != null)
                    snapshots.add(snapshot);
            }
            catch (RuntimeException ex) {
                log.debug("Unable to capture dirty Dataset search buffer", ex);
            }
        }
        return snapshots;
    }

    /** Cancel the active search without destroying already displayed results. */
    public void cancelDatasetSearch()
    {
        if (datasetSearchCancel != null)
            datasetSearchCancel.set(true);
    }

    /** Called when the modeless search window is closed by the user. */
    public void datasetSearchDialogClosed(DatasetSearchDialog dialog)
    {
        if (datasetSearchDialog == dialog)
            datasetSearchDialog = null;
        cancelDatasetSearch();
    }

    private void postDatasetSearchUpdate(long generation, DatasetSearchDialog dialog, Runnable update)
    {
        if (display == null || display.isDisposed())
            return;

        display.asyncExec(() -> {
            if (generation != datasetSearchGeneration || datasetSearchDialog != dialog ||
                dialog.getShell() == null || dialog.getShell().isDisposed())
                return;
            update.run();
        });
    }

    /**
     * Activate a result by its stable file path, Dataset path, and coordinate.
     * No TreeItem label or result-table text is parsed to locate the object.
     */
    public void navigateDatasetSearchResult(DatasetSearchResult result)
    {
        if (result == null || treeView == null)
            return;

        FileFormat openFile = null;
        for (FileFormat file : treeView.getCurrentFiles()) {
            if (sameFilePath(file == null ? null : file.getFilePath(), result.getFilePath())) {
                openFile = file;
                break;
            }
        }

        if (openFile == null) {
            Tools.showError(mainWindow, I18n.text("dialog.datasetSearch.title"),
                            I18n.text("search.staleResult"));
            return;
        }

        HObject object;
        try {
            synchronized (DatasetSearchEngine.NATIVE_IO_LOCK) {
                object = openFile.get(result.getDatasetPath());
            }
        }
        catch (Exception ex) {
            log.debug("Unable to resolve Dataset search result {}", result.getDatasetPath(), ex);
            Tools.showError(mainWindow, I18n.text("dialog.datasetSearch.title"),
                            I18n.text("search.staleResult"));
            return;
        }

        if (!(object instanceof Dataset)) {
            Tools.showError(mainWindow, I18n.text("dialog.datasetSearch.title"),
                            I18n.text("search.staleResult"));
            return;
        }

        try {
            if (!treeView.selectObject(object)) {
                Tools.showError(mainWindow, I18n.text("dialog.datasetSearch.title"),
                                I18n.text("search.staleResult"));
                return;
            }

            TableView tableView = showInlineDataContent(object);
            if (tableView != null)
                tableView.navigateToIndex(result.getCoordinate());
            else
                /* Images and other advanced Dataset views keep their existing
                 * dedicated-window behavior. */
                treeView.showDataContent(object);
        }
        catch (Exception ex) {
            log.debug("Unable to navigate to Dataset search result", ex);
            Tools.showError(mainWindow, I18n.text("dialog.datasetSearch.title"), ex.getMessage());
        }
    }

    private boolean sameFilePath(String first, String second)
    {
        if (first == null || second == null)
            return false;
        try {
            return new File(first).getCanonicalFile().equals(new File(second).getCanonicalFile());
        }
        catch (Exception ex) {
            return first.equalsIgnoreCase(second);
        }
    }

    /**
     * Opens the Preferences dialog. Extracted to a separate method for reuse
     * by both the Tools menu and the system Preferences menu item.
     *
     * @param parentShell the parent shell for the dialog
     */
    private void openUserOptionsDialog(Shell parentShell)
    {
        // Create the preference manager
        PreferenceManager mgr = new PreferenceManager();

        // Create the preference nodes with semantic names
        UserOptionsNode generalNode = new UserOptionsNode("general", new UserOptionsGeneralPage());
        UserOptionsNode hdfNode     = new UserOptionsNode("hdf", new UserOptionsHDFPage());
        UserOptionsNode modulesNode = new UserOptionsNode("modules", new UserOptionsViewModulesPage());

        // Add the nodes
        mgr.addToRoot(generalNode);
        mgr.addToRoot(hdfNode);
        mgr.addToRoot(modulesNode);

        // Create the preferences dialog using the provided parent shell
        userOptionDialog = new UserOptionsDialog(parentShell, mgr, rootDir);

        // Set the preference store
        userOptionDialog.setPreferenceStore(props);
        userOptionDialog.create();

        // Open the dialog
        userOptionDialog.open();

        // TODO(HDFView) [2025-01]: TECHNICAL DEBT - Fix work directory change detection
        // Problem: isWorkDirChanged() not properly exposed by UserOptionsDialog
        // Current behavior: Unconditionally overwrites currentDir after dialog closes
        // Desired behavior: Only update if user actually changed the working directory
        // Solution: Expose isWorkDirChanged() as public getter in UserOptionsDialog
        // Impact: Unnecessary directory updates may confuse users or cause unexpected behavior
        // Note: This issue was preserved during macOS menu refactoring (2025-01-13)
        currentDir = ViewProperties.getWorkDir();

        // if (userOptionDialog.isFontChanged()) {
        Font font = null;

        try {
            font = new Font(parentShell.getDisplay(), ViewProperties.getFontType(),
                            ViewProperties.getFontSize(), SWT.NORMAL);
        }
        catch (Exception ex) {
            // Log the exception to aid debugging - font creation failures should be investigated
            log.warn("Failed to create font with type='{}' size={}: {}", ViewProperties.getFontType(),
                     ViewProperties.getFontSize(), ex.getMessage());
            log.debug("Font creation exception details", ex);
            font = null;
        }

        log.trace("update fonts");
        updateFont(font);
    }

    private void createToolbar(final Shell shell)
    {
        toolBar = new ToolBar(shell, SWT.HORIZONTAL | SWT.RIGHT);
        toolBar.setFont(Display.getCurrent().getSystemFont());
        toolBar.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 5, 1));

        ToolItem openItem = new ToolItem(toolBar, SWT.PUSH);
        I18n.bindToolTip(openItem, "toolbar.open");
        openItem.setImage(ViewProperties.getFileopenIcon());
        openItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                openLocalFile(null, -1);
            }
        });

        new ToolItem(toolBar, SWT.SEPARATOR).setWidth(4);

        ToolItem closeItem = new ToolItem(toolBar, SWT.PUSH);
        closeItem.setImage(ViewProperties.getFilecloseIcon());
        I18n.bindToolTip(closeItem, "toolbar.close");
        closeItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                closeFile(treeView.getSelectedFile());
            }
        });

        new ToolItem(toolBar, SWT.SEPARATOR).setWidth(20);

        ToolItem helpItem = new ToolItem(toolBar, SWT.PUSH);
        helpItem.setImage(ViewProperties.getHelpIcon());
        I18n.bindToolTip(helpItem, "toolbar.help");
        helpItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                String ugPath = ViewProperties.getUsersGuide();

                if (ugPath == null || !ugPath.startsWith("http://")) {
                    String sep   = File.separator;
                    File tmpFile = new File(ugPath);

                    if (!(tmpFile.exists())) {
                        ugPath  = rootDir + sep + "UsersGuide" + sep + "index.html";
                        tmpFile = new File(ugPath);

                        if (!(tmpFile.exists()))
                            ugPath = HDFVIEW_USERSGUIDE_URL;

                        ViewProperties.setUsersGuide(ugPath);
                    }
                }

                try {
                    org.eclipse.swt.program.Program.launch(ugPath);
                }
                catch (Exception ex) {
                    Tools.showError(shell, I18n.text("action.help"), ex.getMessage());
                }
            }
        });

        new ToolItem(toolBar, SWT.SEPARATOR).setWidth(4);

        ToolItem hdf4Item = new ToolItem(toolBar, SWT.PUSH);
        hdf4Item.setImage(ViewProperties.getH4Icon());
        I18n.bindToolTip(hdf4Item, "toolbar.hdf4Library");
        hdf4Item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                new LibraryVersionDialog(shell, FileFormat.FILE_TYPE_HDF4).open();
            }
        });

        if (FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF4) == null)
            hdf4Item.setEnabled(false);

        new ToolItem(toolBar, SWT.SEPARATOR).setWidth(4);

        ToolItem hdf5Item = new ToolItem(toolBar, SWT.PUSH);
        hdf5Item.setImage(ViewProperties.getH5Icon());
        I18n.bindToolTip(hdf5Item, "toolbar.hdf5Library");
        hdf5Item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                new LibraryVersionDialog(shell, FileFormat.FILE_TYPE_HDF5).open();
            }
        });

        if (FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF5) == null)
            hdf5Item.setEnabled(false);

        // Make the toolbar as wide as the window and as
        // tall as the buttons
        toolBar.setSize(shell.getClientArea().width, openItem.getBounds().height);
        toolBar.setLocation(0, 0);

        log.info("Toolbar created");
    }

    private void createUrlToolbar(final Shell shell)
    {
        // Recent Files button
        recentFilesButton = new Button(shell, SWT.PUSH);
        recentFilesButton.setFont(currentFont);
        I18n.bind(recentFilesButton, "button.recentFiles");
        I18n.bindToolTip(recentFilesButton, "tooltip.recentFiles");
        recentFilesButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
        recentFilesButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                urlBar.setListVisible(true);
            }
        });

        // Recent files combo box
        urlBar = new Combo(shell, SWT.BORDER | SWT.SINGLE);
        urlBar.setFont(currentFont);
        /*
         * The first URL-combo entry is the working directory.  A freshly
         * created or unavailable user-properties file can leave the in-memory
         * recent-file list empty, but the open-file paths below still insert a
         * file at index 1.  Restore that invariant here instead of allowing a
         * normal Open/Open As action to fail with an SWT index error.
         */
        ArrayList<String> recentFiles = new ArrayList<>(ViewProperties.getMRF());
        if (recentFiles.isEmpty()) {
            String workDir = currentDir;
            if (workDir == null || workDir.isEmpty())
                workDir = System.getProperty("user.dir");
            if (workDir != null && !workDir.isEmpty())
                recentFiles.add(workDir);
            ViewProperties.setRecentFiles(recentFiles);
        }
        urlBar.setItems(recentFiles.toArray(new String[0]));
        urlBar.setVisibleItemCount(ViewProperties.MAX_RECENT_FILES);
        urlBar.deselectAll();
        urlBar.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        urlBar.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (e.keyCode == SWT.CR) {
                    String filename = urlBar.getText();
                    if (filename == null || filename.length() < 1 || filename.equals(currentFile))
                        return;

                    if (!(filename.startsWith("http://") || filename.startsWith("https://") ||
                          filename.startsWith("ftp://"))) {
                        openLocalFile(filename, -1);
                    }
                    else {
                        String remoteFile = openRemoteFile(filename);

                        if (remoteFile != null)
                            openLocalFile(remoteFile, -1);
                    }
                }
            }
        });
        urlBar.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                String filename = urlBar.getText();
                if (filename == null || filename.length() < 1 || filename.equals(currentFile)) {
                    return;
                }

                if (!(filename.startsWith("http://") || filename.startsWith("https://") ||
                      filename.startsWith("ftp://"))) {
                    openLocalFile(filename, -1);
                }
                else {
                    String remoteFile = openRemoteFile(filename);

                    if (remoteFile != null)
                        openLocalFile(remoteFile, -1);
                }
            }
        });

        createAccessModeSelector(shell);

        fileUsageButton = new Button(shell, SWT.PUSH);
        fileUsageButton.setFont(currentFont);
        I18n.bind(fileUsageButton, "button.fileUsage");
        I18n.bindToolTip(fileUsageButton, "tooltip.fileUsage");
        fileUsageButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
        fileUsageButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                FileFormat file = treeView == null ? null : treeView.getSelectedFile();
                if (file == null)
                    file = accessModeFile;
                openFileUsageDialog(file);
            }
        });
        updateFileUsageButton();

        clearTextButton = new Button(shell, SWT.PUSH);
        I18n.bindToolTip(clearTextButton, "tooltip.clearText");
        clearTextButton.setFont(currentFont);
        I18n.bind(clearTextButton, "button.clearText");
        clearTextButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
        clearTextButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                urlBar.setText("");
                urlBar.deselectAll();
            }
        });

        log.info("URL Toolbar created");
    }

    /** Create the access-mode selector inline with the URL toolbar. */
    private void createAccessModeSelector(final Shell shell)
    {
        accessModeSelector = new Combo(shell, SWT.DROP_DOWN | SWT.READ_ONLY);
        accessModeSelector.setFont(currentFont);
        accessModeSelector.setVisibleItemCount(2);
        accessModeSelector.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
        accessModeSelector.setData(I18n.WIDGET_KEY, ACCESS_MODE_SELECTOR_ID);
        I18n.bindToolTip(accessModeSelector, "tooltip.accessModeSelector");
        accessModeSelector.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (!updatingAccessModeSelector)
                    changeSelectedFileAccessMode();
            }
        });

        updateAccessModeStatus(null);
    }

    /** Update the inline access-mode selector for a newly opened file. */
    public void fileOpened(FileFormat file)
    {
        updateAccessModeStatus(file);
        syncFileUsageSelection(file);
    }

    /** Update the inline access-mode selector without rebuilding any views. */
    private void updateAccessModeStatus(FileFormat file)
    {
        accessModeFile = file;
        refreshAccessModeSelector();
        updateFileUsageButton();
    }

    private void updateFileUsageButton()
    {
        if (fileUsageButton != null && !fileUsageButton.isDisposed())
            fileUsageButton.setEnabled(accessModeFile != null);
    }

    private void syncFileUsageSelection(FileFormat file)
    {
        if (fileUsageDialog != null && !fileUsageDialog.getShell().isDisposed())
            fileUsageDialog.setCurrentFile(file);
    }

    /** Keep the modeless File Usage dialog aligned with the TreeView selection. */
    private void syncFileUsageSelectionFromTree()
    {
        syncFileUsageSelection(fileUsageSelectionFromTree());
    }

    /**
     * Resolve the File Usage file after a tree node is removed. The TreeView
     * clears its selected-file field while disposing the selected root; when
     * exactly one file remains, that file is an unambiguous replacement.
     */
    private FileFormat fileUsageSelectionFromTree()
    {
        if (treeView == null)
            return null;

        FileFormat selected = treeView.getSelectedFile();
        if (selected != null)
            return selected;

        List<FileFormat> openFiles = treeView.getCurrentFiles();
        return openFiles.size() == 1 ? openFiles.get(0) : null;
    }

    private void openFileUsageDialog(FileFormat file)
    {
        if (file == null)
            return;
        if (fileUsageDialog == null || fileUsageDialog.getShell().isDisposed()) {
            fileUsageDialog = new FileUsageDialog(
                    mainWindow, FileUsageInspectorFactory.create(), ProcessHandle.current().pid());
        }
        fileUsageDialog.setCurrentFile(file);
        fileUsageDialog.open();
    }

    /** Refresh selector items and selection without using translated text for behavior. */
    private void refreshAccessModeSelector()
    {
        if (accessModeSelector == null || accessModeSelector.isDisposed())
            return;

        boolean hasFile = accessModeFile != null;
        int accessMode = hasFile ? getFileAccessMode(accessModeFile) : -1;
        boolean swmr = isSwmrAccessMode(accessMode);
        String[] itemKeys;
        if (!hasFile)
            itemKeys = new String[] {"fileAccessMode.noFile"};
        else if (swmr)
            itemKeys = new String[] {"fileAccessMode.swmrRead"};
        else
            itemKeys = new String[] {"fileAccessMode.readOnly", "fileAccessMode.readWrite"};

        updatingAccessModeSelector = true;
        try {
            I18n.bindItems(accessModeSelector, itemKeys);
            if (!hasFile || swmr) {
                accessModeSelector.select(0);
                accessModeSelector.setEnabled(false);
            }
            else {
                accessModeSelector.select(accessModeFile.isReadOnly()
                                              ? ACCESS_MODE_READ_ONLY_INDEX
                                              : ACCESS_MODE_READ_WRITE_INDEX);
                accessModeSelector.setEnabled(true);
            }
        }
        finally {
            updatingAccessModeSelector = false;
        }
        accessModeSelector.requestLayout();
    }

    /** Return the effective access flags remembered by the active TreeView. */
    private int getFileAccessMode(FileFormat file)
    {
        if (file == null)
            return -1;

        if (treeView != null)
            return treeView.getFileAccessMode(file);

        return file.isReadOnly() ? FileFormat.READ : FileFormat.WRITE;
    }

    private boolean isSwmrAccessMode(int accessMode)
    {
        return accessMode >= 0 && (accessMode & FileFormat.MULTIREAD) == FileFormat.MULTIREAD;
    }

    /** Reopen the selected file through the existing TreeView path. */
    private void changeSelectedFileAccessMode()
    {
        FileFormat file = accessModeFile;
        if (file == null) {
            refreshAccessModeSelector();
            return;
        }

        int currentAccessMode = getFileAccessMode(file);
        if (isSwmrAccessMode(currentAccessMode)) {
            refreshAccessModeSelector();
            return;
        }

        int selectedIndex = accessModeSelector.getSelectionIndex();
        int currentIndex = file.isReadOnly()
            ? ACCESS_MODE_READ_ONLY_INDEX
            : ACCESS_MODE_READ_WRITE_INDEX;
        if (selectedIndex < 0 || selectedIndex == currentIndex) {
            refreshAccessModeSelector();
            return;
        }

        int requestedAccessMode = selectedIndex == ACCESS_MODE_READ_WRITE_INDEX
            ? FileFormat.WRITE
            : FileFormat.READ;
        String filename = file.getAbsolutePath();
        String requestedMode = requestedAccessMode == FileFormat.WRITE
            ? I18n.text("fileAccessMode.readWrite")
            : I18n.text("fileAccessMode.readOnly");

        accessModeSelector.setEnabled(false);
        try {
            cancelDatasetSearch();
            FileFormat reopened = treeView.reopenFile(file, requestedAccessMode);
            if (reopened == null)
                throw new java.io.IOException(I18n.text("message.reopenFileFailed", filename));

            updateAccessModeStatus(reopened);
            syncFileUsageSelectionFromTree();
        }
        catch (Exception ex) {
            /* reopenFile restores the original FileFormat when possible. Refresh
             * from the TreeView so a failed switch never leaves a stale selector. */
            updateAccessModeStatus(treeView.getSelectedFile());
            syncFileUsageSelectionFromTree();
            display.beep();
            String detail = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            Tools.showError(mainWindow, I18n.text("action.changeAccessMode"),
                            I18n.text("message.changeAccessModeFailed", filename, requestedMode, detail));
        }
    }

    private void createContentArea(final Shell shell)
    {
        SashForm content = new SashForm(shell, SWT.VERTICAL);
        content.setSashWidth(10);
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 5, 1));

        // Add Data content area and Status Area to main window
        Composite container = new Composite(content, SWT.NONE);
        container.setLayout(new FillLayout());

        Composite statusArea = new Composite(content, SWT.NONE);
        themeManager.bindBackground(statusArea, ThemeManager.ColorRole.SECONDARY_SURFACE);
        statusArea.setLayout(new FillLayout(SWT.HORIZONTAL));

        final SashForm contentArea = new SashForm(container, SWT.HORIZONTAL);
        contentArea.setSashWidth(10);

        // Add TreeView and DataView to content area pane
        treeArea = new ScrolledComposite(contentArea, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
        themeManager.bindBackground(treeArea, ThemeManager.ColorRole.WINDOW_BACKGROUND);
        treeArea.setExpandHorizontal(true);
        treeArea.setExpandVertical(true);

        generalArea = new ScrolledComposite(contentArea, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
        generalArea.setExpandHorizontal(true);
        generalArea.setExpandVertical(true);
        themeManager.bindBackground(generalArea, ThemeManager.ColorRole.SECONDARY_SURFACE);
        generalArea.setMinHeight(contentArea.getSize().y - 2);

        /*
         * Keep the right-side tab host alive for the lifetime of the main
         * window. Selection changes replace only the tab controls, which avoids
         * the old dispose/setContent cycle and its visible blank state.
         */
        rightTabContent = new Composite(generalArea, SWT.NONE);
        themeManager.bindBackground(rightTabContent, ThemeManager.ColorRole.SURFACE);
        rightTabContent.setLayout(new FillLayout());
        rightTabFolder = new TabFolder(rightTabContent, SWT.NONE);
        generalArea.setContent(rightTabContent);
        generalArea.setMinSize(rightTabContent.computeSize(SWT.DEFAULT, SWT.DEFAULT));

        // Create status area for displaying messages and metadata
        status = new Text(statusArea, SWT.V_SCROLL | SWT.MULTI | SWT.BORDER);
        themeManager.bind(status, ThemeManager.ColorRole.SECONDARY_SURFACE,
                          ThemeManager.ColorRole.FOREGROUND);
        status.setEditable(false);
        status.setFont(currentFont);

        contentArea.addListener(SWT.Resize, new Listener() {
            @Override
            public void handleEvent(Event arg0)
            {
                generalArea.setMinHeight(contentArea.getSize().y - 2);
            }
        });

        // Add drag and drop support for opening files
        DropTarget target               = new DropTarget(treeArea, DND.DROP_COPY);
        final FileTransfer fileTransfer = FileTransfer.getInstance();
        target.setTransfer(new Transfer[] {fileTransfer});
        target.addDropListener(new DropTargetListener() {
            @Override
            public void dragEnter(DropTargetEvent e)
            {
                e.detail = DND.DROP_COPY;
            }
            @Override
            public void dragOver(DropTargetEvent e)
            {
                // Intentional
            }
            @Override
            public void dragOperationChanged(DropTargetEvent e)
            {
                // Intentional
            }
            @Override
            public void dragLeave(DropTargetEvent e)
            {
                // Intentional
            }
            @Override
            public void dropAccept(DropTargetEvent e)
            {
                // Intentional
            }
            @Override
            public void drop(DropTargetEvent e)
            {
                if (fileTransfer.isSupportedType(e.currentDataType)) {
                    String[] files = (String[])e.data;
                    for (int i = 0; i < files.length; i++)
                        openLocalFile(files[i], -1);
                }
            }
        });

        showStatus(I18n.text("status.root", rootDir));
        showStatus(I18n.text("status.userProperty", ViewProperties.getPropertyFile()));

        content.setWeights(new int[] {9, 1});
        contentArea.setWeights(new int[] {1, 3});

        DataViewFactory treeViewFactory = null;
        try {
            treeViewFactory = DataViewFactoryProducer.getFactory(DataViewType.TREEVIEW);
        }
        catch (Exception ex) {
            log.debug("createContentArea(): error occurred while instantiating TreeView factory class", ex);
            this.showError(I18n.text("message.treeViewFactoryFailed"));
            return;
        }

        if (treeViewFactory == null) {
            log.debug("createContentArea(): TreeView factory is null");
            return;
        }

        try {
            treeView = treeViewFactory.getTreeView(treeArea, this);

            if (treeView == null) {
                log.debug("createContentArea(): error occurred while instantiating TreeView class");
                this.showError(I18n.text("message.treeViewClassFailed"));
                return;
            }
        }
        catch (ClassNotFoundException ex) {
            log.debug("createContentArea(): no suitable TreeView class found");
            this.showError(I18n.text("message.treeViewUnavailable"));
            return;
        }

        treeArea.setContent(treeView.getTree());

        log.info("Content Area created");
    }

    /**
     * Get a list of treeview implementations.
     *
     * @return a list of treeview implementations.
     */
    public static final List<String> getListOfTreeViews() { return treeViews; }

    /**
     * Get a list of imageview implementations.
     *
     * @return a list of imageview implementations.
     */
    public static final List<String> getListOfImageViews() { return imageViews; }

    /**
     * Get a list of tableview implementations.
     *
     * @return a list of tableview implementations.
     */
    public static final List<?> getListOfTableViews() { return tableViews; }

    /**
     * Get a list of metaDataview implementations.
     *
     * @return a list of metaDataview implementations.
     */
    public static final List<?> getListOfMetaDataViews() { return metaDataViews; }

    /**
     * Get a list of paletteview implementations.
     *
     * @return a list of paletteview implementations.
     */
    public static final List<?> getListOfPaletteViews() { return paletteViews; }

    @Override
    public TreeView getTreeView()
    {
        return treeView;
    }

    /**
     * Get the combobox associated with a URL entry.
     *
     * @return the combobox associated with a URL entry.
     */
    public Combo getUrlBar() { return urlBar; }

    /**
     * Start stop a timer.
     *
     * @param toggleTimer
     *            -- true: start timer, false stop timer.
     */
    @Override
    public final void executeTimer(boolean toggleTimer)
    {
        showStatus(I18n.text("status.timerToggled", toggleTimer));
        viewerState = toggleTimer;
        if (viewerState)
            display.timerExec(ViewProperties.getTimerRefresh(), timer);
        else
            display.timerExec(-1, timer);
    }

    /**
     * Display feedback message.
     *
     * @param msg
     *            the message to display.
     */
    @Override
    public void showStatus(String msg)
    {
        if (status == null) {
            log.debug("showStatus(): status area is null");
            return;
        }

        status.append(msg);
        status.append("\n");
    }

    /**
     * Display error message.
     *
     * @param errMsg the error message to display
     */
    @Override
    public void showError(String errMsg)
    {
        if (status == null) {
            log.debug("showError(): status area is null");
            return;
        }

        status.append(" *** ");
        status.append(errMsg);
        if (log.isDebugEnabled())
            status.append(I18n.text("status.seeLogForDetails"));
        status.append(" *** ");
        status.append("\n");
    }

    /**
     * Display the metadata view for an object.
     *
     * @param obj the object containing the metadata to show
     */
    public void showMetaData(final HObject obj)
    {
        FileFormat selectedFile = obj == null ? null : obj.getFileFormat();
        updateAccessModeStatus(selectedFile);
        syncFileUsageSelection(selectedFile);

        if (rightTabFolder == null || rightTabFolder.isDisposed())
            return;

        /* A repeated notification for the same object does not rebuild the view. */
        if (obj != null && sameObject(displayedMetadataObject, obj) && rightTabFolder.getItemCount() > 0) {
            if (dataContentTab != null && !dataContentTab.isDisposed())
                rightTabFolder.setSelection(dataContentTab);
            return;
        }

        rightTabFolder.setRedraw(false);
        try {
            if (obj == null || !isInlineTableDataset(obj))
                disposeInlineDataView();
            else
                disposeInlineTableViewForSelection();
            displayedMetadataObject = obj;

            if (obj == null)
            {
                clearRightTabs();
                return;
            }

            DataViewFactory metaDataViewFactory = null;
            try {
                metaDataViewFactory = DataViewFactoryProducer.getFactory(DataViewType.METADATA);
            }
            catch (Exception ex) {
                log.debug("showMetaData(): error occurred while instantiating MetaDataView factory class", ex);
                this.showError(I18n.text("message.metadataViewFactoryFailed"));
                return;
            }

            if (metaDataViewFactory == null) {
                log.debug("showMetaData(): MetaDataView factory is null");
                return;
            }

            MetaDataView theView;
            try {
                /* The metadata factory appends its two panes to this shared host. */
                theView = metaDataViewFactory.getMetaDataView(rightTabFolder, this, obj);

                if (theView == null) {
                    log.debug("showMetaData(): error occurred while instantiating MetaDataView class");
                    this.showError(I18n.text("message.metadataViewClassFailed"));
                    return;
                }
            }
            catch (ClassNotFoundException ex) {
                log.debug("showMetaData(): no suitable MetaDataView class found");
                this.showError(I18n.text("message.metadataViewUnavailable"));
                return;
            }

            /* Ordinary table datasets get a third sibling tab at index zero. */
            if (isInlineTableDataset(obj))
                createInlineDataContent(obj);

            layoutRightTabs();

            if (dataContentTab != null && !dataContentTab.isDisposed())
                rightTabFolder.setSelection(dataContentTab);
            else if (rightTabFolder.getItemCount() > 0)
                rightTabFolder.setSelection(0);
        }
        finally {
            rightTabFolder.setRedraw(true);
            layoutRightTabs();
        }
    }

    /**
     * Show or focus the default table view for an ordinary Dataset in the main
     * window. This is the entry point used by the default TreeView double-click
     * path; advanced Open As paths continue to use their standalone views.
     *
     * @param obj the Dataset to display
     *
     * @return the embedded TableView, or null when the object is not a normal
     *         table dataset or the view could not be created
     */
    public TableView showInlineDataContent(HObject obj)
    {
        if (!isInlineTableDataset(obj))
            return null;

        if (!sameObject(displayedMetadataObject, obj)) {
            showMetaData(obj);
            return inlineTableView;
        }

        if (inlineTableView == null || inlineTableView.isViewDisposed()) {
            rightTabFolder.setRedraw(false);
            try {
                /* A Table popup's Close action disposes its root Composite first. */
                if (inlineTableView != null || dataContentTab != null)
                    disposeInlineDataView();
                createInlineDataContent(obj);
                layoutRightTabs();
            }
            finally {
                rightTabFolder.setRedraw(true);
                layoutRightTabs();
            }
        }

        if (dataContentTab != null && !dataContentTab.isDisposed()) {
            rightTabFolder.setSelection(dataContentTab);
            rightTabFolder.setFocus();
        }

        return inlineTableView;
    }

    /** Return whether the object can use the built-in editable TableView. */
    private boolean isInlineTableDataset(HObject obj)
    {
        /* Attributes use Dataset implementations for their storage, but retain
         * the historical standalone editor workflow. */
        if (obj instanceof Attribute || !(obj instanceof Dataset))
            return false;

        Dataset dataset = (Dataset)obj;
        if (dataset.isNULL() || (!(dataset instanceof ScalarDS) && !(dataset instanceof CompoundDS)))
            return false;

        if (dataset instanceof ScalarDS) {
            try {
                if (!dataset.isInited())
                    dataset.init();
                return !((ScalarDS)dataset).isImage();
            }
            catch (Exception ex) {
                log.debug("isInlineTableDataset(): unable to inspect Dataset {}", dataset.getName(), ex);
                return false;
            }
        }

        return true;
    }

    /** Create or reuse the Data Content tab and mount the normal TableView into it. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void createInlineDataContent(HObject obj)
    {
        if (inlineTableView != null && !inlineTableView.isViewDisposed())
            return;

        Composite dataParent = null;
        if (dataContentTab != null && !dataContentTab.isDisposed()) {
            Control existingControl = dataContentTab.getControl();
            if (existingControl instanceof Composite && !existingControl.isDisposed())
                dataParent = (Composite)existingControl;
        }

        if (dataParent == null) {
            dataParent = new Composite(rightTabFolder, SWT.NONE);
            dataContentTab = new TabItem(rightTabFolder, SWT.NONE, 0);
            I18n.bind(dataContentTab, "tab.dataContent");
            dataContentTab.setData(MetaDataView.TAB_ROLE_KEY, MetaDataView.TAB_ROLE_DATA_CONTENT);
            dataContentTab.setControl(dataParent);
        }

        try {
            DataViewFactory tableViewFactory = DataViewFactoryProducer.getFactory(DataViewType.TABLE);
            if (!(tableViewFactory instanceof TableViewFactory)) {
                log.debug("createInlineDataContent(): TableView factory does not support embedding");
                showError(I18n.text("message.inlineTableFactoryUnavailable"));
            }
            else {
                HashMap<ViewProperties.DATA_VIEW_KEY, Serializable> map = new HashMap<>(8);
                map.put(ViewProperties.DATA_VIEW_KEY.OBJECT, obj);
                map.put(ViewProperties.DATA_VIEW_KEY.VIEW_NAME, null);
                map.put(ViewProperties.DATA_VIEW_KEY.CHAR, Boolean.FALSE);
                map.put(ViewProperties.DATA_VIEW_KEY.TRANSPOSED, Boolean.FALSE);
                map.put(ViewProperties.DATA_VIEW_KEY.INDEXBASE1, ViewProperties.isIndexBase1());
                map.put(ViewProperties.DATA_VIEW_KEY.BITMASK, null);

                inlineTableView = ((TableViewFactory)tableViewFactory).getTableView(this, map, dataParent);
                if (inlineTableView == null || inlineTableView.isViewDisposed()) {
                    log.debug("createInlineDataContent(): TableView factory returned no usable view");
                    showError(I18n.text("message.inlineTableCreationFailed"));
                }
            }
        }
        catch (Exception ex) {
            log.debug("createInlineDataContent(): no suitable TableView class found", ex);
            showError(I18n.text("message.inlineTableUnavailable", obj.getName()));
        }

        if (inlineTableView == null || inlineTableView.isViewDisposed()) {
            if (!dataParent.isDisposed())
                dataParent.dispose();
            if (!dataContentTab.isDisposed())
                dataContentTab.dispose();
            dataContentTab = null;
            inlineTableView = null;
        }
    }

    /** Dispose only the current inline TableView while retaining its Data Content page. */
    private void disposeInlineTableViewForSelection()
    {
        TableView view = inlineTableView;
        inlineTableView = null;

        if (view != null && !view.isViewDisposed())
            view.disposeView();
    }

    /** Dispose the current inline TableView and its Data Content page. */
    private void disposeInlineDataView()
    {
        disposeInlineTableViewForSelection();

        if (dataContentTab != null && !dataContentTab.isDisposed()) {
            Control control = dataContentTab.getControl();
            if (control != null && !control.isDisposed())
                control.dispose();
            dataContentTab.dispose();
        }
        dataContentTab = null;
    }

    /**
     * Clear the embedded Data Content tab after its TableView close action has
     * disposed the TableView-owned controls.
     *
     * <p>This is intentionally separate from the Dataset-selection cleanup:
     * selection changes dispose only the TableView-owned controls and retain
     * the page Composite, while this callback handles a user closing the
     * embedded view from its Table menu and removes the whole Data Content page.
     * </p>
     *
     * @param view the embedded TableView that was closed
     */
    public void inlineTableViewClosed(TableView view)
    {
        if (inlineTableView != view)
            return;

        inlineTableView = null;
        if (dataContentTab != null && !dataContentTab.isDisposed()) {
            Control control = dataContentTab.getControl();
            if (control != null && !control.isDisposed())
                control.dispose();
            dataContentTab.dispose();
        }
        dataContentTab = null;

        if (rightTabFolder != null && !rightTabFolder.isDisposed()) {
            layoutRightTabs();
            if (rightTabFolder.getItemCount() > 0)
                rightTabFolder.setSelection(0);
        }
    }

    /** Remove the current tab controls while retaining the host TabFolder. */
    private void clearRightTabs()
    {
        if (rightTabFolder == null || rightTabFolder.isDisposed())
            return;

        for (TabItem item : rightTabFolder.getItems()) {
            Control control = item.getControl();
            if (control != null && !control.isDisposed())
                control.dispose();
            item.dispose();
        }
        dataContentTab = null;
    }

    /** Keep the ScrolledComposite content size in sync with the persistent tabs. */
    private void layoutRightTabs()
    {
        if (rightTabFolder == null || rightTabFolder.isDisposed())
            return;

        rightTabFolder.layout(true, true);
        rightTabContent.layout(true, true);
        generalArea.setMinSize(rightTabContent.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    }

    /** Compare HDF objects without confusing objects from different files. */
    private boolean sameObject(HObject first, HObject second)
    {
        if (first == second)
            return true;
        if (first == null || second == null)
            return false;

        FileFormat firstFile  = first.getFileFormat();
        FileFormat secondFile = second.getFileFormat();
        return first.equals(second) && firstFile != null && firstFile.equals(secondFile);
    }

    /**
     * close the file currently selected in the application.
     *
     * @param theFile the file selected or specified
     */
    public void closeFile(FileFormat theFile)
    {
        if (theFile == null) {
            display.beep();
            Tools.showError(mainWindow, I18n.text("action.close"), I18n.text("message.noFileToClose"));
            return;
        }

        cancelDatasetSearch();

        synchronized (DatasetSearchEngine.NATIVE_IO_LOCK) {

        boolean wasAccessModeFile = accessModeFile != null && accessModeFile.equals(theFile);

        if (inlineTableView != null) {
            HObject inlineObject = inlineTableView.getDataObject();
            if (inlineObject != null && theFile.equals(inlineObject.getFileFormat()))
                disposeInlineDataView();
        }

        // Close all the data windows of this file
        Shell[] views = display.getShells();
        if (views != null) {
            for (int i = 0; i < views.length; i++) {
                Object shellData = views[i].getData();

                if (!(shellData instanceof DataView))
                    continue;

                if ((DataView)shellData != null) {
                    HObject obj = ((DataView)shellData).getDataObject();

                    if (obj == null || obj.getFileFormat() == null)
                        continue;

                    if (obj.getFileFormat().equals(theFile)) {
                        views[i].dispose();
                        views[i] = null;
                    }
                }
            }
        }

        int index = urlBar.getSelectionIndex();
        if (index >= 0) {
            String fName = urlBar.getItem(urlBar.getSelectionIndex());
            if (theFile.getFilePath().equals(fName)) {
                currentFile = null;
                urlBar.setText("");
            }
        }

        try {
            synchronized (DatasetSearchEngine.NATIVE_IO_LOCK) {
                treeView.closeFile(theFile);
            }
        }
        catch (Exception ex) {
            // Intentional
        }

        if (displayedMetadataObject != null && theFile.equals(displayedMetadataObject.getFileFormat())) {
            clearRightTabs();
            displayedMetadataObject = null;
        }

        if (wasAccessModeFile)
            updateAccessModeStatus(null);
        syncFileUsageSelectionFromTree();

        System.gc();
        }
    }

    /**
     * Write the change of data to the given file.
     *
     * @param theFile
     *           The file to be updated.
     */
    public void writeDataToFile(FileFormat theFile)
    {
        try {
            if (inlineTableView != null && !inlineTableView.isViewDisposed()) {
                HObject obj = inlineTableView.getDataObject();
                if (obj != null && theFile.equals(obj.getFileFormat()))
                    inlineTableView.updateValueInFile();
            }

            Shell[] openShells = display.getShells();

            if (openShells != null) {
                for (int i = 0; i < openShells.length; i++) {
                    Object shellData = openShells[i].getData();
                    if (!(shellData instanceof DataView))
                        continue;

                    DataView theView = (DataView)shellData;

                    if (theView instanceof TableView) {
                        TableView tableView = (TableView)theView;
                        FileFormat file     = tableView.getDataObject().getFileFormat();
                        if (file.equals(theFile))
                            tableView.updateValueInFile();
                    }
                }
            }
        }
        catch (Exception ex) {
            display.beep();
            Tools.showError(mainWindow, I18n.text("action.save"), ex.getMessage());
        }
    }

    @Override
    public void addDataView(DataView dataView)
    {
        if (dataView == null || dataView instanceof MetaDataView)
            return;

        // Check if the data content is already displayed
        Shell[] shellList = display.getShells();
        if (shellList != null) {
            for (int i = 0; i < shellList.length; i++) {
                if (dataView.equals(shellList[i].getData()) && shellList[i].isVisible()) {
                    showWindow(shellList[i]);
                    return;
                }
            }
        }

        // First window being added
        if (shellList != null && shellList.length == 2)
            setEnabled(Arrays.asList(windowMenu.getItems()), true);

        HObject obj = dataView.getDataObject();
        String fullPath =
            ((obj.getPath() == null) ? "" : obj.getPath()) + ((obj.getName() == null) ? "" : obj.getName());

        MenuItem item = new MenuItem(windowMenu, SWT.PUSH);
        item.setText(fullPath);
        item.setData(dataView);
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                Object selectedView = ((MenuItem)e.widget).getData();
                if (!(selectedView instanceof DataView))
                    return;

                Shell[] sList = display.getShells();

                for (int i = 0; i < sList.length; i++) {
                    DataView view = (DataView)sList[i].getData();

                    if (view != null && view.equals(selectedView))
                        showWindow(sList[i]);
                }
            }
        });

        mainWindow.setCursor(null);
    }

    @Override
    public void removeDataView(DataView dataView)
    {
        if (mainWindow.isDisposed())
            return;

        HObject obj = dataView.getDataObject();
        if (obj == null)
            return;

        MenuItem[] items = windowMenu.getItems();
        for (int i = 0; i < items.length; i++) {
            if (items[i].getData() == dataView)
                items[i].dispose();
        }

        // Last window being closed
        if (display.getShells().length == 2)
            for (MenuItem item : windowMenu.getItems())
                item.setEnabled(false);
    }

    @Override
    public DataView getDataView(HObject dataObject)
    {
        if (inlineTableView != null && !inlineTableView.isViewDisposed()) {
            HObject inlineObject = inlineTableView.getDataObject();
            if (inlineObject != null && sameObject(inlineObject, dataObject))
                return inlineTableView;
        }

        Shell[] openShells             = display.getShells();
        DataView view                  = null;
        HObject currentObj             = null;
        FileFormat currentDataViewFile = null;

        for (int i = 0; i < openShells.length; i++) {
            Object shellData = openShells[i].getData();
            if (!(shellData instanceof DataView))
                continue;

            view = (DataView)shellData;

            if (view != null) {
                currentObj = view.getDataObject();
                if (currentObj == null)
                    continue;

                currentDataViewFile = currentObj.getFileFormat();

                if (currentObj.equals(dataObject) && currentDataViewFile.equals(dataObject.getFileFormat()))
                    return view;
            }
        }

        return null;
    }

    /**
     * Set the testing state that determines if HDFView
     * is being executed for GUI testing.
     *
     * @param testing
     *           Provides SWTBot native dialog compatibility
     *           workarounds if set to true.
     */
    public void setTestState(boolean testing) { isTesting = testing; }

    /**
     * Get the testing state that determines if HDFView
     * is being executed for GUI testing.
     *
     * @return true if HDFView is being executed for GUI testing.
     */
    public boolean getTestState() { return isTesting; }

    /**
     * Set default UI fonts.
     *
     * @param font - the font to update
     */
    private void updateFont(Font font)
    {
        if (currentFont != null)
            currentFont.dispose();

        log.trace("updateFont():");
        currentFont = font;

        mainWindow.setFont(font);
        recentFilesButton.setFont(font);
        recentFilesButton.requestLayout();
        urlBar.setFont(font);
        urlBar.requestLayout();
        clearTextButton.setFont(font);
        clearTextButton.requestLayout();
        if (fileUsageButton != null) {
            fileUsageButton.setFont(font);
            fileUsageButton.requestLayout();
        }
        accessModeSelector.setFont(font);
        accessModeSelector.requestLayout();
        status.setFont(font);

        // On certain platforms the url_bar items don't update their size after
        // a font change. Removing and replacing them fixes this.
        for (String item : urlBar.getItems()) {
            urlBar.remove(item);
            urlBar.add(item);
        }

        treeArea.setFont(font);
        treeArea.requestLayout();
        for (Control control : treeArea.getChildren()) {
            control.setFont(font);
            control.requestLayout();
        }

        generalArea.setFont(font);
        generalArea.requestLayout();
        for (Control control : generalArea.getChildren()) {
            control.setFont(font);
            control.requestLayout();
        }

        if (treeView.getSelectedFile() != null)
            urlBar.select(0);

        if (treeView instanceof DefaultTreeView)
            ((DefaultTreeView)treeView).updateFont(font);

        Shell[] shellList = display.getShells();
        if (shellList != null) {
            for (int i = 0; i < shellList.length; i++) {
                shellList[i].setFont(font);
                shellList[i].requestLayout();
            }
        }

        mainWindow.requestLayout();
    }

    /**
     * Bring window to the front.
     *
     * @param shell - the shell of the window to show.
     */
    private void showWindow(final Shell shell)
    {
        shell.getDisplay().asyncExec(new Runnable() {
            @Override
            public void run()
            {
                shell.forceActive();
            }
        });
    }

    /**
     * Cascade all windows.
     */
    private void cascadeWindows()
    {
        Shell[] sList = display.getShells();

        // Return if main window (shell) is the only open shell
        if (sList.length <= 1)
            return;

        Shell shell = null;

        Rectangle bounds = Display.getCurrent().getPrimaryMonitor().getClientArea();
        int w            = Math.max(50, bounds.width - 100);
        int h            = Math.max(50, bounds.height - 100);

        int x = bounds.x;
        int y = bounds.y;

        for (int i = 0; i < sList.length; i++) {
            shell = sList[i];
            shell.setBounds(x, y, w, h);
            shell.setActive();
            x += 20;
            y += 20;
        }
    }

    /**
     * Tile all windows.
     */
    private void tileWindows()
    {
        Shell[] sList = display.getShells();

        // Return if main window (shell) is the only open shell
        if (sList.length <= 1)
            return;

        int x       = 0;
        int y       = 0;
        int idx     = 0;
        Shell shell = null;

        int n    = sList.length;
        int cols = (int)Math.sqrt(n);
        int rows = (int)Math.ceil((double)n / (double)cols);

        Rectangle bounds = Display.getCurrent().getPrimaryMonitor().getClientArea();
        int w            = bounds.width / cols;
        int h            = bounds.height / rows;

        y = bounds.y;
        for (int i = 0; i < rows; i++) {
            x = bounds.x;

            for (int j = 0; j < cols; j++) {
                idx = i * cols + j;
                if (idx >= n)
                    return;

                shell = sList[idx];
                shell.setBounds(x, y, w, h);
                shell.setActive();
                x += w;
            }

            y += h;
        }
    }

    /**
     * Closes all windows.
     */
    private void closeAllWindows()
    {
        closeAllWindows(false);
    }

    /** Close child windows, optionally keeping the modeless File Usage dialog. */
    private void closeAllWindows(boolean keepFileUsageDialog)
    {
        Shell[] sList = display.getShells();

        for (int i = 0; i < sList.length; i++) {
            if (sList[i].equals(mainWindow))
                continue;
            if (keepFileUsageDialog && fileUsageDialog != null && !fileUsageDialog.getShell().isDisposed()
                    && sList[i].equals(fileUsageDialog.getShell()))
                continue;
            sList[i].dispose();
        }
    }

    /* Enable and disable GUI components */
    private static void setEnabled(List<MenuItem> list, boolean b)
    {
        Iterator<MenuItem> it = list.iterator();

        while (it.hasNext())
            it.next().setEnabled(b);
    }

    /**
     * Open local file.
     *
     * @param filename     - the name of the file
     * @param fileAccessID - the file permissions
     */
    private void openLocalFile(String filename, int fileAccessID)
    {
        log.trace("openLocalFile {},{}", filename, fileAccessID);

        /*
         * If given a specific access mode, use it without changing it. If not given a
         * specific access mode, check the current status of the "is read only" property
         * to determine how to open the file. This is to allow one time overrides of the
         * default file access mode when opening a file.
         */
        int accessMode = fileAccessID;
        if (accessMode < 0) {
            if (ViewProperties.isReadOnly())
                accessMode = FileFormat.READ;
            else if (ViewProperties.isReadSWMR())
                accessMode = FileFormat.READ | FileFormat.MULTIREAD;
            else
                accessMode = FileFormat.WRITE;
        }

        String[] selectedFilenames = null;
        File[] chosenFiles         = null;

        if (filename != null) {
            File file = new File(filename);
            if (!file.exists()) {
                Tools.showError(mainWindow, I18n.text("action.open"),
                                I18n.text("message.fileDoesNotExist", filename));
                return;
            }

            if (file.isDirectory()) {
                currentDir = filename;
                openLocalFile(null, -1);
            }
            else {
                currentFile = filename;

                try {
                    treeView.openFile(filename, accessMode);
                }
                catch (Exception ex) {
                    try {
                        treeView.openFile(filename, FileFormat.READ);
                    }
                    catch (Exception ex2) {
                        display.beep();
                        urlBar.deselectAll();
                        Tools.showError(mainWindow, I18n.text("action.open"),
                                        I18n.text("message.openFileFailedWithDetails", filename, ex2));
                        currentFile = null;
                    }
                }
            }

            try {
                urlBar.remove(filename);
            }
            catch (Exception ex) {
                log.trace("unable to remove {} from urlBar", filename);
            }

            // first entry is always the workdir
            urlBar.add(filename, 1);
            urlBar.select(1);
        }
        else {
            if (!isTesting) {
                log.trace("openLocalFile filename is null");
                FileDialog fChooser = new FileDialog(mainWindow, SWT.OPEN | SWT.MULTI);
                String modeStr      = I18n.text("fileChooser.mode.readWrite");
                boolean isSWMRFile  = (FileFormat.MULTIREAD == (accessMode & FileFormat.MULTIREAD));
                if (isSWMRFile)
                    modeStr = I18n.text("fileChooser.mode.swmrReadOnly");
                else if (accessMode == FileFormat.READ)
                    modeStr = I18n.text("fileChooser.mode.readOnly");
                fChooser.setText(I18n.text("fileChooser.openTitle", mainWindow.getText(), modeStr));
                fChooser.setFilterPath(currentDir);

                DefaultFileFilter filter = DefaultFileFilter.getFileFilter();
                fChooser.setFilterExtensions(new String[] {"*", filter.getExtensions()});
                fChooser.setFilterNames(new String[] {I18n.text("fileChooser.allFiles"), filter.getDescription()});
                fChooser.setFilterIndex(1);

                fChooser.open();

                selectedFilenames = fChooser.getFileNames();
                if (selectedFilenames.length <= 0)
                    return;

                chosenFiles = new File[selectedFilenames.length];
                for (int i = 0; i < chosenFiles.length; i++) {
                    log.trace("openLocalFile selectedFilenames[{}]: {}", i, selectedFilenames[i]);
                    chosenFiles[i] =
                        new File(fChooser.getFilterPath() + File.separator + selectedFilenames[i]);

                    if (!chosenFiles[i].exists()) {
                        Tools.showError(mainWindow, I18n.text("action.open"),
                                        I18n.text("message.fileDoesNotExist", chosenFiles[i].getName()));
                        continue;
                    }

                    if (chosenFiles[i].isDirectory())
                        currentDir = chosenFiles[i].getPath();
                    else
                        currentDir = chosenFiles[i].getParent();

                    try {
                        urlBar.remove(chosenFiles[i].getAbsolutePath());
                    }
                    catch (Exception ex) {
                        log.trace("unable to remove {} from urlBar", chosenFiles[i].getAbsolutePath());
                    }

                    // first entry is always the workdir
                    urlBar.add(chosenFiles[i].getAbsolutePath(), 1);
                    urlBar.select(1);

                    log.trace("openLocalFile treeView.openFile(accessMode={} chosenFiles[{}]: {}", accessMode,
                              i, chosenFiles[i].getAbsolutePath());
                    try {
                        treeView.openFile(chosenFiles[i].getAbsolutePath(), accessMode + FileFormat.OPEN_NEW);
                    }
                    catch (Exception ex) {
                        try {
                            treeView.openFile(chosenFiles[i].getAbsolutePath(), FileFormat.READ);
                        }
                        catch (Exception ex2) {
                            display.beep();
                            urlBar.deselectAll();
                            Tools.showError(mainWindow, I18n.text("action.open"),
                                            I18n.text("message.openFileFailedWithDetails",
                                                      selectedFilenames[i], ex2));
                            currentFile = null;
                        }
                    }
                }

                currentFile = chosenFiles[0].getAbsolutePath();
            }
            else {
                // Prepend test file directory to filename
                String fName =
                    currentDir + File.separator + new InputDialog(mainWindow,
                                                                  I18n.text("dialog.enterFileName.title"),
                                                                  "").open();

                File chosenFile = new File(fName);

                if (!chosenFile.exists()) {
                    Tools.showError(mainWindow, I18n.text("action.open"),
                                    I18n.text("message.fileDoesNotExist", chosenFile.getName()));
                    return;
                }

                if (chosenFile.isDirectory())
                    currentDir = chosenFile.getPath();
                else
                    currentDir = chosenFile.getParent();

                try {
                    urlBar.remove(chosenFile.getAbsolutePath());
                }
                catch (Exception ex) {
                    log.trace("unable to remove {} from urlBar", chosenFile.getAbsolutePath());
                }

                // first entry is always the workdir
                urlBar.add(chosenFile.getAbsolutePath(), 1);
                urlBar.select(1);

                log.trace("openLocalFile treeView.openFile(chosenFile[{}]: {}", chosenFile.getAbsolutePath(),
                          accessMode + FileFormat.OPEN_NEW);
                try {
                    treeView.openFile(chosenFile.getAbsolutePath(), accessMode + FileFormat.OPEN_NEW);
                }
                catch (Exception ex) {
                    try {
                        treeView.openFile(chosenFile.getAbsolutePath(), FileFormat.READ);
                    }
                    catch (Exception ex2) {
                        display.beep();
                        urlBar.deselectAll();
                        Tools.showError(mainWindow, I18n.text("action.open"),
                                        I18n.text("message.openFileFailedWithDetails", chosenFile, ex2));
                        currentFile = null;
                    }
                }

                currentFile = chosenFile.getAbsolutePath();
            }
        }
    }

    /**
     * Load remote file and save it to local temporary directory.
     *
     * @param urlStr - the URL
     *
     * @return the localized file name
     */
    private String openRemoteFile(String urlStr)
    {
        if (urlStr == null)
            return null;

        String localFile = null;

        if (urlStr.startsWith("http://"))
            localFile = urlStr.substring(7);
        else if (urlStr.startsWith("https://"))
            localFile = urlStr.substring(8);
        else if (urlStr.startsWith("ftp://"))
            localFile = urlStr.substring(6);
        else
            return null;

        localFile = localFile.replace('/', '@');
        localFile = localFile.replace('\\', '@');

        // Search the local file cache
        String tmpDir = System.getProperty("java.io.tmpdir");

        File tmpFile = new File(tmpDir);
        if (!tmpFile.canWrite())
            tmpDir = System.getProperty("user.home");

        localFile = tmpDir + File.separator + localFile;

        tmpFile = new File(localFile);
        if (tmpFile.exists())
            return localFile;

        URL url = null;

        try {
            url = new URL(urlStr);
        }
        catch (Exception ex) {
            url = null;
            display.beep();
            Tools.showError(mainWindow, I18n.text("action.open"), ex.getMessage());
            return null;
        }

        try (BufferedInputStream in = new BufferedInputStream(url.openStream())) {
            try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(tmpFile))) {
                mainWindow.setCursor(display.getSystemCursor(SWT.CURSOR_WAIT));
                byte[] buff = new byte[512]; // set default buffer size to 512
                int n       = 0;
                while ((n = in.read(buff)) > 0)
                    out.write(buff, 0, n);
            }
            catch (Exception ex) {
                log.debug("Remote file: ", ex);
                throw ex;
            }
        }
        catch (Exception ex) {
            display.beep();
            Tools.showError(mainWindow, I18n.text("action.open"), ex.getMessage());
            // Want to call setCursor always
            localFile = null;
        }

        mainWindow.setCursor(null);

        return localFile;
    }

    private void convertFile(String typeFrom, String typeTo)
    {
        ImageConversionDialog dialog =
            new ImageConversionDialog(mainWindow, typeFrom, typeTo, currentDir, treeView.getCurrentFiles());
        dialog.open();

        if (dialog.isFileConverted()) {
            String filename = dialog.getConvertedFile();
            File theFile    = new File(filename);

            if (!theFile.exists())
                return;

            currentDir  = theFile.getParentFile().getAbsolutePath();
            currentFile = theFile.getAbsolutePath();

            try {
                treeView.openFile(filename, FileFormat.WRITE);

                try {
                    urlBar.remove(filename);
                }
                catch (Exception ex) {
                    log.trace("unable to remove {} from urlBar", filename);
                }

                // first entry is always the workdir
                urlBar.add(filename, 1);
                urlBar.select(1);
            }
            catch (Exception ex) {
                showError(ex.toString());
            }
        }
    }

    private void registerFileFormat()
    {
        String msg = I18n.text("hdfview.register.instructions");

        // TODO(HDFView) [2025-12]: Add custom HDFLarge branded icon to file format registration dialog.
        // Currently uses default system dialog icon. Could improve branding with HDF-themed icon.
        // Low priority - cosmetic enhancement. Related: InputDialog.java:43 for small icon support.
        InputDialog dialog = new InputDialog(mainWindow, I18n.text("dialog.register.title"), msg,
                                             SWT.ICON_INFORMATION);

        String str = dialog.open();

        if ((str == null) || (str.length() < 1))
            return;

        int idx1 = str.indexOf(':');
        int idx2 = str.lastIndexOf(':');

        if ((idx1 < 0) || (idx2 <= idx1)) {
            Tools.showError(mainWindow, I18n.text("dialog.register.title"),
                            I18n.text("hdfview.register.invalidFormat", str));
            return;
        }

        String key       = str.substring(0, idx1);
        String className = str.substring(idx1 + 1, idx2);
        String extension = str.substring(idx2 + 1);

        // Check if the file format has been registered or the key is taken.
        String theKey            = null;
        String theClassName      = null;
        Enumeration<?> localEnum = FileFormat.getFileFormatKeys();
        while (localEnum.hasMoreElements()) {
            theKey = (String)localEnum.nextElement();
            if (theKey.endsWith(key)) {
                Tools.showError(mainWindow, I18n.text("dialog.register.title"),
                                I18n.text("hdfview.register.keyTaken", key));
                return;
            }

            theClassName = FileFormat.getFileFormat(theKey).getClass().getName();
            if (theClassName.endsWith(className)) {
                Tools.showError(mainWindow, I18n.text("dialog.register.title"),
                                I18n.text("hdfview.register.alreadyRegistered", className));
                return;
            }
        }

        // Enables use of JHDF5 in JNLP (Web Start) applications, the system
        // class loader with reflection first.
        Class<?> theClass = null;
        try {
            theClass = Class.forName(className);
        }
        catch (Exception ex) {
            try {
                theClass = ViewProperties.loadExtClass().loadClass(className);
            }
            catch (Exception ex2) {
                theClass = null;
            }
        }

        if (theClass == null)
            return;

        try {
            Object theObject = theClass.newInstance();
            if (theObject instanceof FileFormat)
                FileFormat.addFileFormat(key, (FileFormat)theObject);
        }
        catch (Exception ex) {
            Tools.showError(mainWindow, I18n.text("dialog.register.title"),
                            I18n.text("hdfview.register.failed", str) + "\n\n" + ex);
            return;
        }

        if ((extension != null) && (extension.length() > 0)) {
            extension  = extension.trim();
            String ext = ViewProperties.getFileExtension();
            ext += ", " + extension;
            ViewProperties.setFileExtension(ext);
        }
    }

    private void unregisterFileFormat()
    {
        Enumeration<?> keys       = FileFormat.getFileFormatKeys();
        ArrayList<Object> keyList = new ArrayList<>();

        while (keys.hasMoreElements())
            keyList.add(keys.nextElement());

        String theKey = new UnregisterFileFormatDialog(mainWindow, SWT.NONE, keyList).open();

        if (theKey == null)
            return;

        FileFormat.removeFileFormat(theKey);
    }

    private class LibraryVersionDialog extends Dialog {
        private String message;

        LibraryVersionDialog(Shell parent, String libType)
        {
            super(parent, SWT.APPLICATION_MODAL | SWT.DIALOG_TRIM);

            if (libType.equals(FileFormat.FILE_TYPE_HDF4))
                setMessage(I18n.text("hdfview.libraryVersion", "HDF", HDF4_VERSION));
            else if (libType.equals(FileFormat.FILE_TYPE_HDF5))
                setMessage(I18n.text("hdfview.libraryVersion", "HDF5", HDF5_VERSION));
        }

        public void setMessage(String message) { this.message = message; }

        public void open()
        {
            Shell dialog = new Shell(getParent(), getStyle());
            dialog.setFont(currentFont);
            I18n.bind(dialog, "dialog.libraryVersion.title");

            createContents(dialog);

            dialog.pack();

            Point computedSize = dialog.computeSize(SWT.DEFAULT, SWT.DEFAULT);
            dialog.setSize(computedSize.x + 50, computedSize.y + 50);

            // Center the window relative to the main HDFView window
            Point winCenter = new Point(mainWindow.getBounds().x + (mainWindow.getBounds().width / 2),
                                        mainWindow.getBounds().y + (mainWindow.getBounds().height / 2));

            dialog.setLocation(winCenter.x - (dialog.getSize().x / 2),
                               winCenter.y - (dialog.getSize().y / 2));

            dialog.open();

            Display parDisplay = getParent().getDisplay();
            while (!dialog.isDisposed()) {
                if (!parDisplay.readAndDispatch())
                    parDisplay.sleep();
            }
        }

        private void createContents(final Shell shell)
        {
            shell.setLayout(new GridLayout(2, false));

            Image hdfImage = ViewProperties.getHDFViewIcon();

            Label imageLabel = new Label(shell, SWT.CENTER);
            imageLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            imageLabel.setImage(hdfImage);

            Label versionLabel = new Label(shell, SWT.CENTER);
            versionLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
            versionLabel.setFont(currentFont);
            versionLabel.setText(message);

            // Draw HDF Icon and Version string
            Composite buttonComposite = new Composite(shell, SWT.NONE);
            buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
            RowLayout buttonLayout = new RowLayout();
            buttonLayout.center    = true;
            buttonLayout.justify   = true;
            buttonLayout.type      = SWT.HORIZONTAL;
            buttonComposite.setLayout(buttonLayout);

            Button okButton = new Button(buttonComposite, SWT.PUSH);
            okButton.setFont(currentFont);
            I18n.bind(okButton, "button.ok");
            shell.setDefaultButton(okButton);
            okButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    shell.dispose();
                }
            });
        }
    }

    private class JavaVersionDialog extends Dialog {
        JavaVersionDialog(Shell parent) { super(parent, SWT.APPLICATION_MODAL | SWT.DIALOG_TRIM); }

        public void open()
        {
            final Shell dialog = new Shell(getParent(), getStyle());
            dialog.setFont(currentFont);
            I18n.bind(dialog, "dialog.javaVersion.title");
            dialog.setLayout(new GridLayout(2, false));

            Image hdfImage = ViewProperties.getHDFViewIcon();

            Label imageLabel = new Label(dialog, SWT.CENTER);
            imageLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            imageLabel.setImage(hdfImage);

            Label versionLabel = new Label(dialog, SWT.CENTER);
            versionLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
            versionLabel.setFont(currentFont);
            versionLabel.setText(I18n.text("hdfview.javaVersionInfo", JAVA_COMPILER,
                                           System.getProperty("java.version")));

            Composite buttonComposite = new Composite(dialog, SWT.NONE);
            buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
            RowLayout buttonLayout = new RowLayout();
            buttonLayout.center    = true;
            buttonLayout.justify   = true;
            buttonLayout.type      = SWT.HORIZONTAL;
            buttonComposite.setLayout(buttonLayout);

            Button okButton = new Button(buttonComposite, SWT.PUSH);
            okButton.setFont(currentFont);
            I18n.bind(okButton, "button.ok");
            dialog.setDefaultButton(okButton);
            okButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    dialog.dispose();
                }
            });

            dialog.pack();

            Point computedSize = dialog.computeSize(SWT.DEFAULT, SWT.DEFAULT);
            dialog.setSize(computedSize.x + 50, computedSize.y + 50);

            // Center the window relative to the main HDFView window
            Point winCenter = new Point(mainWindow.getBounds().x + (mainWindow.getBounds().width / 2),
                                        mainWindow.getBounds().y + (mainWindow.getBounds().height / 2));

            dialog.setLocation(winCenter.x - (dialog.getSize().x / 2),
                               winCenter.y - (dialog.getSize().y / 2));

            dialog.open();

            Display openDisplay = getParent().getDisplay();
            while (!dialog.isDisposed()) {
                if (!openDisplay.readAndDispatch())
                    openDisplay.sleep();
            }
        }
    }

    private class SupportedFileFormatsDialog extends Dialog {
        SupportedFileFormatsDialog(Shell parent) { super(parent, SWT.APPLICATION_MODAL | SWT.DIALOG_TRIM); }

        public void open()
        {
            final Shell dialog = new Shell(getParent(), getStyle());
            dialog.setFont(currentFont);
            I18n.bind(dialog, "dialog.supportedFileFormats.title");
            dialog.setLayout(new GridLayout(2, false));

            Image hdfImage = ViewProperties.getHDFViewIcon();

            Label imageLabel = new Label(dialog, SWT.CENTER);
            imageLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            imageLabel.setImage(hdfImage);

            Enumeration<?> formatKeys = FileFormat.getFileFormatKeys();

            StringBuilder formats = new StringBuilder("\n")
                .append(I18n.text("hdfview.supportedFormats"))
                .append("\n");
            while (formatKeys.hasMoreElements())
                formats.append("    ").append(formatKeys.nextElement()).append("\n");
            formats.append("\n");

            Label formatsLabel = new Label(dialog, SWT.LEFT);
            formatsLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
            formatsLabel.setFont(currentFont);
            formatsLabel.setText(formats.toString());

            Composite buttonComposite = new Composite(dialog, SWT.NONE);
            buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
            RowLayout buttonLayout = new RowLayout();
            buttonLayout.center    = true;
            buttonLayout.justify   = true;
            buttonLayout.type      = SWT.HORIZONTAL;
            buttonComposite.setLayout(buttonLayout);

            Button okButton = new Button(buttonComposite, SWT.PUSH);
            okButton.setFont(currentFont);
            I18n.bind(okButton, "button.ok");
            dialog.setDefaultButton(okButton);
            okButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    dialog.dispose();
                }
            });

            dialog.pack();

            Point computedSize = dialog.computeSize(SWT.DEFAULT, SWT.DEFAULT);
            dialog.setSize(computedSize.x + 50, computedSize.y + 50);

            // Center the window relative to the main HDFView window
            Point winCenter = new Point(mainWindow.getBounds().x + (mainWindow.getBounds().width / 2),
                                        mainWindow.getBounds().y + (mainWindow.getBounds().height / 2));

            dialog.setLocation(winCenter.x - (dialog.getSize().x / 2),
                               winCenter.y - (dialog.getSize().y / 2));

            dialog.open();

            Display openDisplay = getParent().getDisplay();
            while (!dialog.isDisposed()) {
                if (!openDisplay.readAndDispatch())
                    openDisplay.sleep();
            }
        }
    }

    private class AboutDialog extends Dialog {
        AboutDialog(Shell parent) { super(parent, SWT.APPLICATION_MODAL | SWT.DIALOG_TRIM); }

        public void open()
        {
            final Shell dialog = new Shell(getParent(), getStyle());
            dialog.setFont(currentFont);
            I18n.bind(dialog, "dialog.about.title");
            dialog.setLayout(new GridLayout(2, false));

            Image hdfImage = ViewProperties.getHDFViewIcon();

            Label imageLabel = new Label(dialog, SWT.CENTER);
            imageLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            imageLabel.setImage(hdfImage);

            Label aboutLabel = new Label(dialog, SWT.LEFT);
            aboutLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
            aboutLabel.setFont(currentFont);
            aboutLabel.setText(I18n.text("hdfview.about", ViewProperties.VERSION,
                                        System.getProperty("os.name")));

            Composite buttonComposite = new Composite(dialog, SWT.NONE);
            buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
            RowLayout buttonLayout = new RowLayout();
            buttonLayout.center    = true;
            buttonLayout.justify   = true;
            buttonLayout.type      = SWT.HORIZONTAL;
            buttonComposite.setLayout(buttonLayout);

            Button okButton = new Button(buttonComposite, SWT.PUSH);
            okButton.setFont(currentFont);
            I18n.bind(okButton, "button.ok");
            dialog.setDefaultButton(okButton);
            okButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    dialog.dispose();
                }
            });

            dialog.pack();

            Point computedSize = dialog.computeSize(SWT.DEFAULT, SWT.DEFAULT);
            dialog.setSize(computedSize.x + 50, computedSize.y + 50);

            // Center the window relative to the main HDFView window
            Point winCenter = new Point(mainWindow.getBounds().x + (mainWindow.getBounds().width / 2),
                                        mainWindow.getBounds().y + (mainWindow.getBounds().height / 2));

            dialog.setLocation(winCenter.x - (dialog.getSize().x / 2),
                               winCenter.y - (dialog.getSize().y / 2));

            dialog.open();

            Display openDisplay = getParent().getDisplay();
            while (!dialog.isDisposed()) {
                if (!openDisplay.readAndDispatch())
                    openDisplay.sleep();
            }
        }
    }

    private class UnregisterFileFormatDialog extends Dialog {
        private List<Object> keyList;
        private String formatChoice = null;

        UnregisterFileFormatDialog(Shell parent, int style, List<Object> keyList)
        {
            super(parent, style);

            this.keyList = keyList;
        }

        public String open()
        {
            Shell parent      = getParent();
            final Shell shell = new Shell(parent, SWT.APPLICATION_MODAL | SWT.DIALOG_TRIM);
            shell.setFont(currentFont);
            I18n.bind(shell, "dialog.unregister.title");
            shell.setLayout(new GridLayout(2, false));

            Image hdfImage = ViewProperties.getHDFViewIcon();

            Label imageLabel = new Label(shell, SWT.CENTER);
            imageLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            imageLabel.setImage(hdfImage);

            final Combo formatChoiceCombo = new Combo(shell, SWT.SINGLE | SWT.DROP_DOWN | SWT.READ_ONLY);
            formatChoiceCombo.setFont(currentFont);
            formatChoiceCombo.setItems(keyList.toArray(new String[0]));
            formatChoiceCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
            formatChoiceCombo.select(0);
            formatChoiceCombo.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    formatChoice = formatChoiceCombo.getItem(formatChoiceCombo.getSelectionIndex());
                }
            });

            Composite buttonComposite = new Composite(shell, SWT.NONE);
            buttonComposite.setLayout(new GridLayout(2, true));
            buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));

            Button okButton = new Button(buttonComposite, SWT.PUSH);
            okButton.setFont(currentFont);
            I18n.bind(okButton, "button.ok");
            okButton.setLayoutData(new GridData(SWT.END, SWT.FILL, true, false));
            okButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    shell.dispose();
                }
            });

            Button cancelButton = new Button(buttonComposite, SWT.PUSH);
            cancelButton.setFont(currentFont);
            I18n.bind(cancelButton, "button.cancel");
            cancelButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.FILL, true, false));
            cancelButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    shell.dispose();
                }
            });

            shell.pack();

            Point computedSize = shell.computeSize(SWT.DEFAULT, SWT.DEFAULT);
            shell.setSize(computedSize.x + 50, computedSize.y + 50);

            Rectangle parentBounds = parent.getBounds();
            Point shellSize        = shell.getSize();
            shell.setLocation((parentBounds.x + (parentBounds.width / 2)) - (shellSize.x / 2),
                              (parentBounds.y + (parentBounds.height / 2)) - (shellSize.y / 2));

            shell.open();

            Display openDisplay = parent.getDisplay();
            while (!shell.isDisposed()) {
                if (!openDisplay.readAndDispatch())
                    openDisplay.sleep();
            }

            return formatChoice;
        }
    }

    /**
     * The starting point of this application.
     *
     * <pre>
     * Usage: java(w)
     *        -Dhdf.hdf5lib.H5.hdf5lib="your HDF5 library path"
     *        -Dhdf.hdflib.HDFLibrary.hdflib="your HDF4 library path"
     *        -root "the directory where the HDFView is installed"
     *        -start "the directory HDFView searches for files"
     *        -geometry or -g "the preferred window size as WIDTHxHEIGHT+XOFF+YOFF"
     *        -java.version "show the version of jave used to build the HDFView and exit"
     *        [filename] "the file to open"
     * </pre>
     *
     * @param args  the command line arguments
     */
    public static void main(String[] args)
    {
        if (display == null || display.isDisposed())
            display = new Display();

        String rootDir = System.getProperty("hdfview.root");
        if (rootDir == null)
            rootDir = System.getProperty("user.dir");
        String startDir = System.getProperty("user.dir");
        log.trace("main: rootDir = {}  startDir = {}", rootDir, startDir);

        File tmpFile           = null;
        Monitor primaryMonitor = display.getPrimaryMonitor();
        Point margin = new Point(primaryMonitor.getBounds().width, primaryMonitor.getBounds().height);

        int argsLen = args.length;
        int marginW = margin.x / 2;
        int marginH = margin.y;
        int geomX   = 0;
        int geomY   = 0;

        for (int i = 0; i < args.length; i++) {
            if ("-root".equalsIgnoreCase(args[i])) {
                argsLen--;
                try {
                    argsLen--;
                    tmpFile = new File(args[++i]);

                    if (tmpFile.isDirectory())
                        rootDir = tmpFile.getPath();
                    else if (tmpFile.isFile())
                        rootDir = tmpFile.getParent();
                }
                catch (Exception ex) {
                }
            }
            else if ("-start".equalsIgnoreCase(args[i])) {
                argsLen--;
                try {
                    argsLen--;
                    tmpFile = new File(args[++i]);

                    if (tmpFile.isDirectory())
                        startDir = tmpFile.getPath();
                    else if (tmpFile.isFile())
                        startDir = tmpFile.getParent();
                }
                catch (Exception ex) {
                }
            }
            else if ("-g".equalsIgnoreCase(args[i]) || "-geometry".equalsIgnoreCase(args[i])) {
                argsLen--;
                // -geometry WIDTHxHEIGHT+XOFF+YOFF
                try {
                    String geom = args[++i];
                    argsLen--;

                    int idx  = 0;
                    int idx2 = geom.lastIndexOf('-');
                    int idx3 = geom.lastIndexOf('+');

                    idx = Math.max(idx2, idx3);
                    if (idx > 0) {
                        geomY = Integer.parseInt(geom.substring(idx + 1));

                        if (idx == idx2)
                            geomY = -geomY;

                        geom = geom.substring(0, idx);
                        idx2 = geom.lastIndexOf('-');
                        idx3 = geom.lastIndexOf('+');
                        idx  = Math.max(idx2, idx3);

                        if (idx > 0) {
                            geomX = Integer.parseInt(geom.substring(idx + 1));

                            if (idx == idx2)
                                geomX = -geomX;

                            geom = geom.substring(0, idx);
                        }
                    }

                    idx = geom.indexOf('x');

                    if (idx > 0) {
                        marginW = Integer.parseInt(geom.substring(0, idx));
                        marginH = Integer.parseInt(geom.substring(idx + 1));
                    }
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            else if ("-java.version".equalsIgnoreCase(args[i])) {
                /* Set icon to ViewProperties.getLargeHdfIcon() */
                Tools.showInformation(mainWindow, I18n.text("dialog.javaVersion.title"),
                                      I18n.text("hdfview.javaVersionInfo", JAVA_COMPILER,
                                                System.getProperty("java.version")));
                System.exit(0);
            }
        }

        ArrayList<File> fList = new ArrayList<>();

        if (argsLen >= 0) {
            for (int i = args.length - argsLen; i < args.length; i++) {
                tmpFile = new File(args[i]);
                if (!tmpFile.isAbsolute())
                    tmpFile = new File(startDir, args[i]);
                log.trace("main: filelist - file = {} ", tmpFile.getAbsolutePath());
                log.trace("main: filelist - add file = {} exists={} isFile={} isDir={}", tmpFile,
                          tmpFile.exists(), tmpFile.isFile(), tmpFile.isDirectory());
                if (tmpFile.exists() && (tmpFile.isFile() || tmpFile.isDirectory())) {
                    log.trace("main: flist - add file = {}", tmpFile.getAbsolutePath());
                    fList.add(new File(tmpFile.getAbsolutePath()));
                }
            }
        }

        final ArrayList<File> theFileList = fList;
        final String theRootDir           = rootDir;
        final String theStartDir          = startDir;
        final int theX                    = geomX;
        final int theY                    = geomY;
        final int theW                    = marginW;
        final int theH                    = marginH;

        display.syncExec(new Runnable() {
            @Override
            public void run()
            {
                HDFView app = new HDFView(theRootDir, theStartDir);

                // TODO(HDFView) [2025-12]: Investigate better solution for native dialog compatibility
                // issues. Current workaround may not be optimal for cross-platform native file/directory
                // dialogs. Consider SWT dialog improvements or alternative dialog libraries for better
                // platform integration. Medium priority - affects user experience on certain platforms.
                app.setTestState(false);

                app.openMainWindow(theFileList, theW, theH, theX, theY);
                app.runMainWindow();
            }
        });
    }
}
