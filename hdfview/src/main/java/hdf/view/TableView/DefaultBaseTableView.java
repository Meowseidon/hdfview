/*****************************************************************************
 * Copyright by The HDF Group.                                               *
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

package hdf.view.TableView;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.nio.ByteOrder;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicBoolean;

import hdf.object.CompoundDS;
import hdf.object.DataFormat;
import hdf.object.Dataset;
import hdf.object.Datatype;
import hdf.object.FileFormat;
import hdf.object.Group;
import hdf.object.HObject;
import hdf.object.ScalarDS;
import hdf.object.h5.H5Datatype;
import hdf.object.h5.H5ReferenceType;
import hdf.view.Chart;
import hdf.view.DataView.DataViewManager;
import hdf.view.DefaultFileFilter;
import hdf.view.HDFView;
import hdf.view.TableView.DataDisplayConverterFactory.HDFDisplayConverter;
import hdf.view.TableView.DataProviderFactory.HDFDataProvider;
import hdf.view.Tools;
import hdf.view.TreeView.TreeView;
import hdf.view.ViewProperties;
import hdf.view.ViewProperties.BITMASK_OP;
import hdf.view.dialog.InputDialog;
import hdf.view.dialog.MathConversionDialog;
import hdf.view.dialog.NewDatasetDialog;
import hdf.view.i18n.I18n;
import hdf.view.search.DatasetSearchSnapshot;
import hdf.view.statistics.DatasetStatisticsDialog;
import hdf.view.statistics.DatasetStatisticsEngine;

import hdf.hdf5lib.HDF5Constants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.command.StructuralRefreshCommand;
import org.eclipse.nebula.widgets.nattable.command.VisualRefreshCommand;
import org.eclipse.nebula.widgets.nattable.config.AbstractRegistryConfiguration;
import org.eclipse.nebula.widgets.nattable.config.AbstractUiBindingConfiguration;
import org.eclipse.nebula.widgets.nattable.config.CellConfigAttributes;
import org.eclipse.nebula.widgets.nattable.config.IConfigRegistry;
import org.eclipse.nebula.widgets.nattable.config.IEditableRule;
import org.eclipse.nebula.widgets.nattable.coordinate.Range;
import org.eclipse.nebula.widgets.nattable.data.IDataProvider;
import org.eclipse.nebula.widgets.nattable.data.validate.DataValidator;
import org.eclipse.nebula.widgets.nattable.edit.EditConfigAttributes;
import org.eclipse.nebula.widgets.nattable.edit.action.KeyEditAction;
import org.eclipse.nebula.widgets.nattable.edit.action.MouseEditAction;
import org.eclipse.nebula.widgets.nattable.edit.config.DefaultEditConfiguration;
import org.eclipse.nebula.widgets.nattable.edit.config.DialogErrorHandling;
import org.eclipse.nebula.widgets.nattable.edit.editor.ICellEditor;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.nebula.widgets.nattable.grid.layer.ColumnHeaderLayer;
import org.eclipse.nebula.widgets.nattable.grid.layer.GridLayer;
import org.eclipse.nebula.widgets.nattable.grid.layer.RowHeaderLayer;
import org.eclipse.nebula.widgets.nattable.layer.DataLayer;
import org.eclipse.nebula.widgets.nattable.layer.ILayer;
import org.eclipse.nebula.widgets.nattable.layer.IUniqueIndexLayer;
import org.eclipse.nebula.widgets.nattable.layer.LabelStack;
import org.eclipse.nebula.widgets.nattable.layer.cell.IConfigLabelAccumulator;
import org.eclipse.nebula.widgets.nattable.layer.config.DefaultColumnHeaderLayerConfiguration;
import org.eclipse.nebula.widgets.nattable.layer.config.DefaultColumnHeaderStyleConfiguration;
import org.eclipse.nebula.widgets.nattable.layer.config.DefaultRowHeaderLayerConfiguration;
import org.eclipse.nebula.widgets.nattable.layer.config.DefaultRowHeaderStyleConfiguration;
import org.eclipse.nebula.widgets.nattable.painter.cell.TextPainter;
import org.eclipse.nebula.widgets.nattable.painter.cell.decorator.BeveledBorderDecorator;
import org.eclipse.nebula.widgets.nattable.painter.cell.decorator.LineBorderDecorator;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.nebula.widgets.nattable.selection.command.SelectAllCommand;
import org.eclipse.nebula.widgets.nattable.selection.command.SelectCellCommand;
import org.eclipse.nebula.widgets.nattable.style.CellStyleAttributes;
import org.eclipse.nebula.widgets.nattable.style.DisplayMode;
import org.eclipse.nebula.widgets.nattable.style.HorizontalAlignmentEnum;
import org.eclipse.nebula.widgets.nattable.style.Style;
import org.eclipse.nebula.widgets.nattable.ui.action.IMouseAction;
import org.eclipse.nebula.widgets.nattable.ui.binding.UiBindingRegistry;
import org.eclipse.nebula.widgets.nattable.ui.matcher.CellEditorMouseEventMatcher;
import org.eclipse.nebula.widgets.nattable.ui.matcher.LetterOrDigitKeyEventMatcher;
import org.eclipse.nebula.widgets.nattable.ui.matcher.MouseEventMatcher;
import org.eclipse.nebula.widgets.nattable.ui.menu.PopupMenuAction;
import org.eclipse.nebula.widgets.nattable.ui.menu.PopupMenuBuilder;
import org.eclipse.nebula.widgets.nattable.viewport.ViewportLayer;
import org.eclipse.nebula.widgets.nattable.viewport.command.ShowCellInViewportCommand;
import org.eclipse.nebula.widgets.nattable.viewport.command.ShowRowInViewportCommand;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

/**
 * DefaultBaseTableView serves as the base class for a DataView that displays
 * HDF data in a tabular format. This class is used for internal bookkeeping and
 * as a place to store higher-level data manipulation functions, whereas its
 * subclasses are responsible for setting up the actual GUI components.
 *
 * @author jhenderson
 * @version 1.0 4/13/2018
 */
public abstract class DefaultBaseTableView implements TableView, DatasetStatisticsDialog.Host {

    private static final Logger log = LoggerFactory.getLogger(DefaultBaseTableView.class);

    private final Display display = Display.getDefault();
    /** The shell used for dialogs and, for standalone views, the top-level view shell. */
    protected final Shell shell;

    /** The Composite which owns all controls belonging to this TableView. */
    protected final Composite viewParent;

    /** Whether this TableView is mounted in an existing Composite. */
    protected final boolean isEmbedded;

    /** Whether the TableView has released its data and GUI resources. */
    private boolean viewDisposed = false;
    /** Whether active editing and pending changes have been handled. */
    private boolean cleanupPrepared = false;
    /** Listener installed on an embedded host for its final disposal. */
    private DisposeListener parentDisposeListener;
    /** The current font. */
    protected Font curFont;

    /** The main HDFView. */
    protected final DataViewManager viewer;

    /** The reference to the NAT table used. */
    protected NatTable dataTable;

    /** The data object to be displayed in the Table. */
    protected final DataFormat dataObject;

    /** The data value of the data object. */
    protected Object dataValue;

    /** The value used for fill. */
    protected Object fillValue;

    /** the valid types of tableviews. */
    protected enum ViewType {
        /** The data view is of type spreadsheet. */
        TABLE,
        /** The data view is of type image. */
        IMAGE
    }
    ;

    /** The type of view. */
    protected ViewType viewType = ViewType.TABLE;

    /** Changed to use normalized scientific notation (1 is less than coefficient is less than 10). */
    protected final DecimalFormat scientificFormat = new DecimalFormat("0.0###E0###");
    /** custom format pattern. */
    protected DecimalFormat customFormat = new DecimalFormat("###.#####");
    /** the normal format to be used for numbers. */
    protected final NumberFormat normalFormat = null;
    /** the format to be used for numbers. */
    protected NumberFormat numberFormat = normalFormat;

    /** Used for bitmask operations on data. */
    protected BitSet bitmask = null;
    /** Used for the type of bitmask operation. */
    protected BITMASK_OP bitmaskOP = BITMASK_OP.EXTRACT;

    /** Fields to keep track of which 'frame' of 3 dimensional data is being displayed. */
    private Text frameField;
    private long curDataFrame = 0;
    private long maxDataFrame = 1;

    /** The index base used for display row and column numbers of data. */
    protected int indexBase = 0;

    /** size of default data length. */
    protected int fixedDataLength = -1;

    /** default binary order. */
    protected int binaryOrder;

    /** status if file is read only. */
    protected boolean isReadOnly = false;

    /** status if the enums are to display converted. */
    protected boolean isEnumConverted = false;

    /** status if the display type is a char. */
    protected boolean isDisplayTypeChar;

    /** status if the data is transposed. */
    protected boolean isDataTransposed;

    /** reference status. */
    protected boolean isRegRef = false;
    protected boolean isObjRef = false;
    protected boolean isStdRef = false;
    /** show data as status. */
    protected boolean showAsHex = false;
    protected boolean showAsBin = false;

    /** Keep references to the selection layers for ease of access. */
    protected SelectionLayer selectionLayer;
    /** Keep references to the data layers for ease of access. */
    protected DataLayer dataLayer;

    /** reference to the data provider for the row. */
    protected IDataProvider rowHeaderDataProvider;
    /** reference to the data provider for the column. */
    protected IDataProvider columnHeaderDataProvider;

    /** reference to the data provider. */
    protected HDFDataProvider dataProvider;
    /** reference to the display converter. */
    protected HDFDisplayConverter dataDisplayConverter;

    /** Stable NatTable label used for current-page statistics highlighting. */
    private static final String STATISTICS_HIGHLIGHT_LABEL =
        "HDFVIEW_STATISTICS_HIGHLIGHT";

    /** Body-cell positions highlighted by the current statistics result. */
    private final Set<Long> statisticsHighlightCells = new HashSet<>();

    /** Current statistics dialog and its cooperative worker state. */
    private DatasetStatisticsDialog statisticsDialog;
    private AtomicBoolean statisticsCancel;
    private long statisticsGeneration;

    /** Checkbox menu item for Fixed Data Length default. */
    protected MenuItem checkFixedDataLength = null;
    /** Checkbox menu item for Custom Notation default. */
    protected MenuItem checkCustomNotation = null;
    /** Checkbox menu item for Scientific Notation default. */
    protected MenuItem checkScientificNotation = null;
    /** Checkbox menu item for hex default. */
    protected MenuItem checkHex = null;
    /** Checkbox menu item for binary default. */
    protected MenuItem checkBin = null;
    /** Checkbox menu item for enum default. */
    protected MenuItem checkEnum = null;

    /** Labeled Group to display the index base. */
    protected org.eclipse.swt.widgets.Group indexBaseGroup;

    /** Text field to display the value of the currently selected table cell. */
    protected Text cellValueField;

    /** Label to indicate the current cell location. */
    protected Label cellLabel;

    /**
     * Constructs a base TableView with no additional data properties.
     *
     * @param theView
     *            the main HDFView.
     */
    public DefaultBaseTableView(DataViewManager theView) { this(theView, null, null); }

    /**
     * Constructs a base TableView with the specified data properties.
     *
     * @param theView
     *            the main HDFView.
     *
     * @param dataPropertiesMap
     *            the properties on how to show the data. The map is used to allow
     *            applications to pass properties on how to display the data, such
     *            as: transposing data, showing data as characters, applying a
     *            bitmask, and etc. Predefined keys are listed at
     *            ViewProperties.DATA_VIEW_KEY.
     */
    @SuppressWarnings("rawtypes")
    public DefaultBaseTableView(DataViewManager theView, HashMap dataPropertiesMap)
    {
        this(theView, dataPropertiesMap, null);
    }

    /**
     * Constructs a base TableView using either a new top-level Shell or the
     * supplied Composite as its control parent.
     *
     * @param theView            the main HDFView
     * @param dataPropertiesMap  the properties on how to show the data
     * @param parent             an existing Composite for an embedded view, or null
     *                           for the historical standalone window
     */
    @SuppressWarnings("rawtypes")
    protected DefaultBaseTableView(DataViewManager theView, HashMap dataPropertiesMap, Composite parent)
    {
        isEmbedded = parent != null;
        viewParent = isEmbedded ? parent : new Shell(display, SWT.SHELL_TRIM);
        shell      = viewParent.getShell();

        if (!isEmbedded)
            viewParent.setData(this);

        viewParent.setLayout(new GridLayout(1, true));

        if (!isEmbedded) {
            viewParent.addListener(SWT.Close, event -> commitActiveCellEditor());
        }

        /*
         * When the table is closed, make sure to prompt the user about saving their
         * changes, then do any pending cleanup work.
         */
        parentDisposeListener = new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent e)
            {
                cleanupView();
            }
        };
        viewParent.addDisposeListener(parentDisposeListener);

        /* Grab the current font to be used for all GUI components */
        try {
            curFont =
                new Font(display, ViewProperties.getFontType(), ViewProperties.getFontSize(), SWT.NORMAL);
        }
        catch (Exception ex) {
            curFont = null;
        }

        viewer = theView;

        /* Retrieve any display properties passed in via the HashMap parameter */
        HObject hObject = null;

        if (ViewProperties.isIndexBase1())
            indexBase = 1;

        if (dataPropertiesMap != null) {
            hObject = (HObject)dataPropertiesMap.get(ViewProperties.DATA_VIEW_KEY.OBJECT);

            bitmask   = (BitSet)dataPropertiesMap.get(ViewProperties.DATA_VIEW_KEY.BITMASK);
            bitmaskOP = (BITMASK_OP)dataPropertiesMap.get(ViewProperties.DATA_VIEW_KEY.BITMASKOP);

            Boolean b = (Boolean)dataPropertiesMap.get(ViewProperties.DATA_VIEW_KEY.CHAR);
            if (b != null)
                isDisplayTypeChar = b.booleanValue();

            b = (Boolean)dataPropertiesMap.get(ViewProperties.DATA_VIEW_KEY.TRANSPOSED);
            if (b != null)
                isDataTransposed = b.booleanValue();

            b = (Boolean)dataPropertiesMap.get(ViewProperties.DATA_VIEW_KEY.INDEXBASE1);
            if (b != null) {
                if (b.booleanValue())
                    indexBase = 1;
                else
                    indexBase = 0;
            }
        }

        if (hObject == null)
            hObject = viewer.getTreeView().getCurrentObject();

        /* Only edit objects which actually contain editable data */
        if ((hObject == null) || !(hObject instanceof DataFormat)) {
            log.debug("data object is null or not an instanceof DataFormat");
            dataObject = null;
            closeViewControl();
            return;
        }

        dataObject = (DataFormat)hObject;
        if (((HObject)dataObject).getFileFormat() == null) {
            log.debug("DataFormat object cannot access FileFormat");
            closeViewControl();
            return;
        }

        isReadOnly = ((HObject)dataObject).getFileFormat().isReadOnly();

        if (((HObject)dataObject)
                .getFileFormat()
                .isThisType(FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF4)) &&
            (dataObject instanceof CompoundDS)) {
            /* Cannot edit HDF4 VData */
            isReadOnly = true;
        }

        /* Disable edit feature for SZIP compression when encode is not enabled */
        if (!isReadOnly) {
            String compression = dataObject.getCompression();
            if ((compression != null) && compression.startsWith("SZIP")) {
                if (!compression.endsWith("ENCODE_ENABLED"))
                    isReadOnly = true;
            }
        }

        log.trace("dataObject({}) isReadOnly={}", dataObject, isReadOnly);

        long[] dims = dataObject.getDims();
        long tsize  = 1;

        if (dims == null) {
            log.debug("data object has null dimensions");
            viewer.showError(I18n.text("message.dataObjectNullDimensions", ((HObject)dataObject).getName()));
            closeViewControl();
            Tools.showError(display.getActiveShell(), I18n.text("action.error"),
                            I18n.text("message.cannotOpenDataObjectNullDimensions",
                                      ((HObject)dataObject).getName()));
            return;
        }

        for (int i = 0; i < dims.length; i++)
            tsize *= dims[i];

        log.trace("Data object Size={} Height={} Width={}", tsize, dataObject.getHeight(),
                  dataObject.getWidth());

        if (dataObject.getHeight() <= 0 || dataObject.getWidth() <= 0 || tsize <= 0) {
            log.debug("data object has dimension of size 0");
            viewer.showError(I18n.text("message.dataObjectZeroDimensions", ((HObject)dataObject).getName()));
            closeViewControl();
            Tools.showError(display.getActiveShell(), I18n.text("action.error"),
                            I18n.text("message.cannotOpenDataObjectZeroDimensions",
                                      ((HObject)dataObject).getName()));
            return;
        }

        /*
         * Determine whether the data is to be displayed as characters and whether or
         * not enum data is to be converted.
         */
        Datatype dtype = dataObject.getDatatype();

        log.trace("Data object getDatatypeClass()={}", dtype.getDatatypeClass());
        isDisplayTypeChar = (isDisplayTypeChar && (dtype.getDatatypeSize() == 1 ||
                                                   (dtype.isArray() && dtype.getDatatypeBase().isChar())));

        isEnumConverted = ViewProperties.isConvertEnum();

        log.trace("Data object isDisplayTypeChar={} isEnumConverted={}", isDisplayTypeChar, isEnumConverted);

        if (dtype.isRef()) {
            if (((HObject)dataObject)
                    .getFileFormat()
                    .isThisType(FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF5))) {
                isStdRef = ((H5Datatype)dtype).isStdRef();
                isRegRef = ((H5Datatype)dtype).isRegRef();
                isObjRef = ((H5Datatype)dtype).isRefObj();
            }
        }

        // Setup subset information
        int spaceType       = dataObject.getSpaceType();
        int rank            = dataObject.getRank();
        int[] selectedIndex = dataObject.getSelectedIndex();
        long[] count        = dataObject.getSelectedDims();
        long[] stride       = dataObject.getStride();
        long[] start        = dataObject.getStartDims();
        int n               = Math.min(3, rank);

        if (rank > 2) {
            curDataFrame = start[selectedIndex[2]] + indexBase;
            maxDataFrame = (indexBase == 1) ? dims[selectedIndex[2]] : dims[selectedIndex[2]] - 1;
        }

        /* Create the toolbar area that contains useful shortcuts */
        ToolBar toolBar = createToolbar(viewParent);
        if (!isEmbedded) {
            toolBar.setSize(shell.getSize().x, 30);
            toolBar.setLocation(0, 0);
        }

        /*
         * Create the group that contains the text fields for displaying the value and
         * location of the current cell, as well as the index base.
         */
        indexBaseGroup = new org.eclipse.swt.widgets.Group(viewParent, SWT.SHADOW_ETCHED_OUT);
        indexBaseGroup.setFont(curFont);
        I18n.bind(indexBaseGroup, "table.indexBased", indexBase);
        indexBaseGroup.setLayout(new GridLayout(1, true));
        indexBaseGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        SashForm content = new SashForm(indexBaseGroup, SWT.VERTICAL);
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        content.setSashWidth(10);

        SashForm cellValueComposite = new SashForm(content, SWT.HORIZONTAL);
        cellValueComposite.setSashWidth(8);

        cellLabel = new Label(cellValueComposite, SWT.RIGHT | SWT.BORDER);
        cellLabel.setAlignment(SWT.CENTER);
        cellLabel.setFont(curFont);

        final ScrolledComposite cellValueFieldScroller =
            new ScrolledComposite(cellValueComposite, SWT.V_SCROLL | SWT.H_SCROLL);
        cellValueFieldScroller.setLayout(new FillLayout());

        cellValueField = new Text(cellValueFieldScroller, SWT.MULTI | SWT.BORDER | SWT.WRAP);
        cellValueField.setEditable(false);
        cellValueField.setBackground(new Color(display, 255, 255, 240));
        cellValueField.setEnabled(false);
        cellValueField.setFont(curFont);

        cellValueFieldScroller.setContent(cellValueField);
        cellValueFieldScroller.setExpandHorizontal(true);
        cellValueFieldScroller.setExpandVertical(true);
        cellValueFieldScroller.setMinSize(cellValueField.computeSize(SWT.DEFAULT, SWT.DEFAULT));

        cellValueComposite.setWeights(new int[] {1, 5});

        /* Make sure that the Dataset's data value is accessible for conditionally adding GUI components */
        try {
            loadData(dataObject);
            if (isStdRef) {
                if (dataObject.getRank() > 2)
                    ((H5ReferenceType)dtype)
                        .setRefSize((int)dataObject.getWidth() * (int)dataObject.getWidth());
                ((H5ReferenceType)dtype).setData(dataValue);
            }
        }
        catch (Exception ex) {
            log.debug("loadData(): data not loaded: ", ex);
            viewer.showError(I18n.text("message.unableLoadTableData"));
            closeViewControl();
            Tools.showError(display.getActiveShell(), I18n.text("action.open"),
                            I18n.text("message.tableDataLoadFailed") + "\n\n" + ex.getMessage());
            return;
        }

        /* Create the standalone menu bar or the embedded TableView popup menu. */
        Menu viewMenu = createMenuBar(shell);
        if (isEmbedded)
            viewParent.setMenu(viewMenu);
        else
            shell.setMenuBar(viewMenu);

        /*
         * Set the default selection on the "Show Hexadecimal/Show Binary", etc. MenuItems.
         * This step must be done after the menu bar has actually been created.
         */
        if (dataObject.getDatatype().isBitField() || dataObject.getDatatype().isOpaque()) {
            showAsHex = true;
            checkHex.setSelection(true);
            checkScientificNotation.setSelection(false);
            checkCustomNotation.setSelection(false);
            checkBin.setSelection(false);
            showAsBin    = false;
            numberFormat = normalFormat;
        }

        /*
         * Set the default selection on the "Show Enum", etc. MenuItems.
         * This step must be done after the menu bar has actually been created.
         */
        if (dataObject.getDatatype().isEnum()) {
            checkEnum.setSelection(isEnumConverted);
            checkScientificNotation.setSelection(false);
            checkCustomNotation.setSelection(false);
            checkBin.setSelection(false);
            checkHex.setSelection(false);
            showAsBin    = false;
            showAsHex    = false;
            numberFormat = normalFormat;
        }

        /* Create the actual NatTable */
        log.debug("table creation {}", ((HObject)dataObject).getName());
        try {
            dataTable = createTable(content, dataObject);
            if (dataTable == null) {
                log.debug("table creation for object {} failed", ((HObject)dataObject).getName());
                viewer.showError(I18n.text("message.tableCreationFailed", ((HObject)dataObject).getName()));
                closeViewControl();
                Tools.showError(display.getActiveShell(), I18n.text("action.open"),
                                I18n.text("message.tableCreationObjectFailed"));
                return;
            }
        }
        catch (UnsupportedOperationException ex) {
            log.debug("Subclass does not implement createTable()");
            closeViewControl();
            return;
        }

        /*
         * Set the default data display conversion settings.
         */
        updateDataConversionSettings();

        dataTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        /*
         * Set the Shell's title using the object path and name
         */
        FileFormat objectFileFormat = ((HObject)dataObject).getFileFormat();
        if (!isEmbedded) {
            if (objectFileFormat != null) {
                I18n.bind(shell, "table.objectTitle", hObject.getName(), hObject.getPath(),
                          objectFileFormat.getName(), objectFileFormat.getParent());
            }
            else {
                I18n.bind(shell, "table.objectNameTitle", hObject.getName());
            }
        }

        /*
         * Append subsetting information and show this as a status message in the
         * HDFView main window
         */
        StringBuilder dimsText = new StringBuilder(String.valueOf(selectedIndex[0]));
        for (int i = 1; i < n; i++) {
            dimsText.append("x").append(selectedIndex[i]);
        }
        StringBuilder startText = new StringBuilder(String.valueOf(start[selectedIndex[0]]));
        for (int i = 1; i < n; i++) {
            startText.append("x").append(start[selectedIndex[i]]);
        }
        StringBuilder countText = new StringBuilder(String.valueOf(count[selectedIndex[0]]));
        for (int i = 1; i < n; i++) {
            countText.append("x").append(count[selectedIndex[i]]);
        }
        StringBuilder strideText = new StringBuilder(String.valueOf(stride[selectedIndex[0]]));
        for (int i = 1; i < n; i++) {
            strideText.append("x").append(stride[selectedIndex[i]]);
        }
        String subsetStatus = I18n.text("table.subsetStatus", dimsText, startText, countText, strideText);

        if (log.isTraceEnabled())
            log.trace("subset={}", subsetStatus);

        viewer.showStatus(subsetStatus);

        indexBaseGroup.pack();

        content.setWeights(new int[] {1, 12});

        if (!isEmbedded) {
            shell.pack();

            int width  = 700 + (ViewProperties.getFontSize() - 12) * 15;
            int height = 500 + (ViewProperties.getFontSize() - 12) * 10;
            shell.setSize(width, height);
        }
        else {
            viewParent.layout(true, true);
        }
    }

    /**
     * Dispose this TableView in either standalone or embedded mode.
     *
     * <p>For embedded views this disposes only the TableView-owned child controls;
     * the host Composite, HDFView window, and TabFolder remain alive so the page
     * can be rebound to another Dataset.</p>
     */
    @Override
    public void disposeView()
    {
        if (viewDisposed)
            return;

        if (isEmbedded)
            disposeEmbeddedControls();
        else if (viewParent.isDisposed())
            cleanupView();
        else {
            prepareForCleanup();
            viewParent.dispose();
        }
    }

    @Override
    public boolean isViewDisposed() { return viewDisposed || viewParent.isDisposed(); }

    /** Dispose only the TableView-owned controls during constructor failure. */
    private void closeViewControl()
    {
        if (!viewParent.isDisposed())
            viewParent.dispose();
    }

    /**
     * Commit the editor before its control can be destroyed.
     *
     * <p>NatTable normally commits on focus loss. This hook covers lifecycle
     * paths where the parent Shell/Composite is closed while the editor still
     * owns focus, such as application exit.</p>
     */
    @Override
    public void commitActiveCellEditor()
    {
        try {
            if (dataTable == null) {
                log.debug("commitActiveCellEditor(): No active cell editor");
                return;
            }

            ICellEditor activeCellEditor = dataTable.getActiveCellEditor();
            if (activeCellEditor == null) {
                log.debug("commitActiveCellEditor(): No active cell editor");
                return;
            }

            log.debug("commitActiveCellEditor(): Active cell editor detected - committing before disposal");
            activeCellEditor.commit(SelectionLayer.MoveDirectionEnum.NONE, true, true);
            log.debug("commitActiveCellEditor(): Cell editor committed successfully");
        }
        catch (Exception ex) {
            log.warn("commitActiveCellEditor(): Failed to commit active editor", ex);
        }
    }

    /** Prepare active editing and pending changes exactly once. */
    private void prepareForCleanup()
    {
        if (cleanupPrepared)
            return;

        cleanupPrepared = true;
        commitActiveCellEditor();

        if (dataProvider != null && dataProvider.getIsValueChanged() && !isReadOnly && dataObject != null) {
            if (Tools.showConfirm(shell, I18n.text("message.changesDetected.title"),
                                  I18n.text("message.changesDetected.text", ((HObject)dataObject).getName())))
                updateValueInFile();
            else
                dataObject.clearData();
        }
    }

    /** Dispose embedded TableView controls while retaining their host Composite. */
    private void disposeEmbeddedControls()
    {
        if (viewParent.isDisposed()) {
            cleanupView();
            return;
        }

        prepareForCleanup();

        Menu viewMenu = viewParent.getMenu();
        if (viewMenu != null && !viewMenu.isDisposed())
            viewMenu.dispose();

        for (Control child : viewParent.getChildren()) {
            if (!child.isDisposed())
                child.dispose();
        }

        cleanupView();
    }

    /** Perform the common close/dispose cleanup exactly once. */
    private void cleanupView()
    {
        if (viewDisposed)
            return;

        stopStatistics();

        // This is also a fallback for callers which dispose the parent directly
        // instead of going through disposeView() or a Shell close event.
        prepareForCleanup();

        dataValue = null;
        dataTable = null;

        if (curFont != null && !curFont.isDisposed())
            curFont.dispose();

        if (parentDisposeListener != null && !viewParent.isDisposed()) {
            viewParent.removeDisposeListener(parentDisposeListener);
            parentDisposeListener = null;
        }

        viewDisposed = true;

        if (!isEmbedded && viewer != null)
            viewer.removeDataView(DefaultBaseTableView.this);
    }

    /** Stop a worker before the Dataset or its TableView controls disappear. */
    private void stopStatistics()
    {
        statisticsGeneration++;
        if (statisticsCancel != null)
            statisticsCancel.set(true);
        statisticsCancel = null;

        DatasetStatisticsDialog dialog = statisticsDialog;
        statisticsDialog = null;
        if (dialog != null)
            dialog.dispose();
    }

    /**
     * Creates the toolbar for the Shell.
     *
     * @param parent - the containing composite.
     *
     * @return the new toolbar
     */
    private ToolBar createToolbar(final Composite parent)
    {
        ToolBar toolbar = new ToolBar(parent, SWT.HORIZONTAL | SWT.RIGHT | SWT.BORDER);
        toolbar.setFont(curFont);
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        // Chart button
        ToolItem item = new ToolItem(toolbar, SWT.PUSH);
        item.setImage(ViewProperties.getChartIcon());
        I18n.bindToolTip(item, "table.linePlot");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                showLineplot();
            }
        });

        if (dataObject.getRank() > 2) {
            new ToolItem(toolbar, SWT.SEPARATOR).setWidth(20);

            // First frame button
            item = new ToolItem(toolbar, SWT.PUSH);
            item.setImage(ViewProperties.getFirstIcon());
            I18n.bindToolTip(item, "table.firstFrame");
            item.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    firstFrame();
                }
            });

            // Previous frame button
            item = new ToolItem(toolbar, SWT.PUSH);
            item.setImage(ViewProperties.getPreviousIcon());
            I18n.bindToolTip(item, "table.previousFrame");
            item.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    previousFrame();
                }
            });

            ToolItem separator = new ToolItem(toolbar, SWT.SEPARATOR);

            frameField = new Text(toolbar, SWT.SINGLE | SWT.BORDER | SWT.CENTER);
            frameField.setFont(curFont);
            frameField.setText(String.valueOf(curDataFrame));
            frameField.addTraverseListener(new TraverseListener() {
                @Override
                public void keyTraversed(TraverseEvent e)
                {
                    if (e.detail == SWT.TRAVERSE_RETURN) {
                        try {
                            int frame = 0;

                            try {
                                frame = Integer.parseInt(frameField.getText().trim()) - indexBase;
                            }
                            catch (Exception ex) {
                                frame = -1;
                            }

                            gotoFrame(frame);
                        }
                        catch (Exception ex) {
                            log.debug("Frame change failure: ", ex);
                        }
                    }
                }
            });

            frameField.pack();

            separator.setWidth(frameField.getSize().x + 30);
            separator.setControl(frameField);

            separator = new ToolItem(toolbar, SWT.SEPARATOR);

            Text maxFrameText = new Text(toolbar, SWT.SINGLE | SWT.BORDER | SWT.CENTER);
            maxFrameText.setFont(curFont);
            maxFrameText.setText(String.valueOf(maxDataFrame));
            maxFrameText.setEditable(false);
            maxFrameText.setEnabled(false);

            maxFrameText.pack();

            separator.setWidth(maxFrameText.getSize().x + 30);
            separator.setControl(maxFrameText);

            new ToolItem(toolbar, SWT.SEPARATOR).setWidth(10);

            // Next frame button
            item = new ToolItem(toolbar, SWT.PUSH);
            item.setImage(ViewProperties.getNextIcon());
            I18n.bindToolTip(item, "table.nextFrame");
            item.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    nextFrame();
                }
            });

            // Last frame button
            item = new ToolItem(toolbar, SWT.PUSH);
            item.setImage(ViewProperties.getLastIcon());
            I18n.bindToolTip(item, "table.lastFrame");
            item.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    lastFrame();
                }
            });
        }

        return toolbar;
    }

    /**
     * Creates the menubar for the Shell.
     *
     * @param theShell
     *    the reference to the display shell
     *
     * @return the newly created menu
     */
    protected Menu createMenuBar(final Shell theShell)
    {
        Menu menuBar       = new Menu(theShell, isEmbedded ? SWT.POP_UP : SWT.BAR);
        boolean isEditable = !isReadOnly;

        MenuItem tableMenuItem = new MenuItem(menuBar, SWT.CASCADE);
        I18n.bind(tableMenuItem, "table");

        Menu tableMenu = new Menu(theShell, SWT.DROP_DOWN);
        tableMenuItem.setMenu(tableMenu);

        MenuItem item = new MenuItem(tableMenu, SWT.PUSH);
        I18n.bind(item, "table.selectAll");
        item.setAccelerator(SWT.CTRL | 'A');
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                try {
                    dataTable.doCommand(new SelectAllCommand());
                }
                catch (Exception ex) {
                    theShell.getDisplay().beep();
                    Tools.showError(theShell, I18n.text("action.select"), ex.getMessage());
                }
            }
        });

        item = new MenuItem(tableMenu, SWT.PUSH);
        I18n.bind(item, "table.copy");
        item.setAccelerator(SWT.CTRL | 'C');
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                copyData();
            }
        });

        item = new MenuItem(tableMenu, SWT.PUSH);
        I18n.bind(item, "table.paste");
        item.setAccelerator(SWT.CTRL | 'V');
        item.setEnabled(isEditable);
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                pasteData();
            }
        });

        new MenuItem(tableMenu, SWT.SEPARATOR);

        item = new MenuItem(tableMenu, SWT.PUSH);
        I18n.bind(item, "table.copyToNewDataset");
        item.setEnabled(isEditable && (dataObject instanceof ScalarDS));
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if ((selectionLayer.getSelectedColumnPositions().length <= 0) ||
                    (selectionLayer.getSelectedRowCount() <= 0)) {
                    Tools.showInformation(theShell, I18n.text("action.copy"),
                                           I18n.text("table.selectCellsToWrite"));
                    return;
                }

                TreeView treeView = viewer.getTreeView();
                Group pGroup = (Group)(treeView.findTreeItem((HObject)dataObject).getParentItem().getData());
                HObject root = ((HObject)dataObject).getFileFormat().getRootObject();

                if (root == null)
                    return;

                ArrayList<HObject> list =
                    new ArrayList<>(((HObject)dataObject).getFileFormat().getNumberOfMembers() + 5);
                Iterator<HObject> it = ((Group)root).depthFirstMemberList().iterator();

                while (it.hasNext())
                    list.add(it.next());
                list.add(root);

                NewDatasetDialog dialog =
                    new NewDatasetDialog(theShell, pGroup, list, DefaultBaseTableView.this);
                dialog.open();

                HObject obj = dialog.getObject();
                if (obj != null) {
                    Group pgroup = dialog.getParentGroup();
                    try {
                        treeView.addObject(obj, pgroup);
                    }
                    catch (Exception ex) {
                        log.debug("Write selection to dataset:", ex);
                    }
                }

                list.clear();
            }
        });

        item = new MenuItem(tableMenu, SWT.PUSH);
        I18n.bind(item, "table.saveChanges");
        item.setAccelerator(SWT.CTRL | 'U');
        item.setEnabled(isEditable);
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                try {
                    updateValueInFile();
                }
                catch (Exception ex) {
                    theShell.getDisplay().beep();
                    Tools.showError(theShell, I18n.text("action.save"), ex.getMessage());
                }
            }
        });

        new MenuItem(tableMenu, SWT.SEPARATOR);

        item = new MenuItem(tableMenu, SWT.PUSH);
        I18n.bind(item, "table.showLineplot");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                showLineplot();
            }
        });

        item = new MenuItem(tableMenu, SWT.PUSH);
        I18n.bind(item, "table.showStatistics");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                showStatistics(theShell);
            }
        });

        new MenuItem(tableMenu, SWT.SEPARATOR);

        item = new MenuItem(tableMenu, SWT.PUSH);
        I18n.bind(item, "table.mathConversion");
        item.setEnabled(isEditable);
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                try {
                    mathConversion();
                }
                catch (Exception ex) {
                    shell.getDisplay().beep();
                    Tools.showError(theShell, I18n.text("action.convert"), ex.getMessage());
                }
            }
        });

        new MenuItem(tableMenu, SWT.SEPARATOR);

        item = new MenuItem(tableMenu, SWT.PUSH);
        I18n.bind(item, "table.close");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (isEmbedded) {
                    disposeView();
                    if (viewer instanceof HDFView)
                        ((HDFView)viewer).inlineTableViewClosed(DefaultBaseTableView.this);
                }
                else
                    theShell.dispose();
            }
        });

        // Set up MenuItems for refreshing the TableView *
        item = new MenuItem(tableMenu, SWT.PUSH);
        I18n.bind(item, "table.startTimer");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                viewer.executeTimer(true);
            }
        });

        item = new MenuItem(tableMenu, SWT.PUSH);
        I18n.bind(item, "table.stopTimer");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                viewer.executeTimer(false);
            }
        });

        // Set up MenuItems for Importing/Exporting Data from the TableView *
        MenuItem importExportMenuItem = new MenuItem(menuBar, SWT.CASCADE);
        I18n.bind(importExportMenuItem, "table.importExport");

        Menu importExportMenu = new Menu(theShell, SWT.DROP_DOWN);
        importExportMenuItem.setMenu(importExportMenu);

        item = new MenuItem(importExportMenu, SWT.CASCADE);
        I18n.bind(item, "table.exportDataTo");

        Menu exportMenu = new Menu(item);
        item.setMenu(exportMenu);

        item = new MenuItem(exportMenu, SWT.PUSH);
        I18n.bind(item, "table.textFile");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                try {
                    saveAsText();
                }
                catch (Exception ex) {
                    theShell.getDisplay().beep();
                    Tools.showError(theShell, I18n.text("action.save"), ex.getMessage());
                }
            }
        });

        item = new MenuItem(importExportMenu, SWT.CASCADE);
        I18n.bind(item, "table.importDataFrom");

        Menu importMenu = new Menu(item);
        item.setMenu(importMenu);

        item = new MenuItem(importMenu, SWT.PUSH);
        I18n.bind(item, "table.textFile");
        item.setEnabled(!isReadOnly);
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                String currentDir = ((HObject)dataObject).getFileFormat().getParent();

                String filename = null;
                if (((HDFView)viewer).getTestState()) {
                filename = currentDir + File.separator +
                               new InputDialog(theShell, I18n.text("dialog.enterFileName.title"), "").open();
                }
                else {
                    FileDialog fChooser = new FileDialog(theShell, SWT.OPEN);
                    fChooser.setFilterPath(currentDir);

                    DefaultFileFilter filter = DefaultFileFilter.getFileFilterText();
                    fChooser.setFilterExtensions(new String[] {"*", filter.getExtensions()});
                    fChooser.setFilterNames(new String[] {I18n.text("fileChooser.allFiles"),
                                                          filter.getDescription()});
                    fChooser.setFilterIndex(1);

                    filename = fChooser.open();
                }

                if (filename == null)
                    return;

                File chosenFile = new File(filename);
                if (!chosenFile.exists()) {
                    Tools.showError(theShell, I18n.text("table.importTextTitle"),
                                    I18n.text("message.fileDoesNotExist", filename));
                    return;
                }

                if (!Tools.showConfirm(theShell, I18n.text("table.importTextTitle"),
                                       I18n.text("table.pasteConfirm")))
                    return;

                importTextData(chosenFile.getAbsolutePath());
            }
        });

        return menuBar;
    }

    /**
     * Open the unified Dataset statistics view while retaining the historical
     * CompoundDS one-column dialog path.
     */
    private void showStatistics(Shell theShell)
    {
        try {
            if (dataObject instanceof CompoundDS) {
                showLegacyStatistics(theShell);
                return;
            }

            if (!(dataObject instanceof Dataset) || dataValue == null) {
                Tools.showError(theShell, I18n.text("action.statistics"),
                                I18n.text("statistics.noData"));
                return;
            }

            if (statisticsDialog == null || !statisticsDialog.isOpen())
                statisticsDialog = new DatasetStatisticsDialog(theShell, this);
            statisticsDialog.open();
            startStatistics(statisticsDialog, DatasetStatisticsEngine.Scope.CURRENT_PAGE);
        }
        catch (Exception ex) {
            theShell.getDisplay().beep();
            Tools.showError(theShell, I18n.text("action.statistics"), ex.getMessage());
        }
    }

    /** Preserve the existing min/max/mean/sample-standard-deviation behavior. */
    private void showLegacyStatistics(Shell theShell)
    {
        Object theData = getSelectedData();
        int cols = selectionLayer.getFullySelectedColumnPositions().length;
        if (cols != 1) {
            Tools.showError(theShell, I18n.text("action.statistics"),
                            I18n.text("message.statisticsOneColumn"));
            return;
        }
        if (theData == null)
            theData = dataValue;

        double[] minmax = new double[2];
        double[] stat   = new double[2];
        Tools.findMinMax(theData, minmax, fillValue);
        if (Tools.computeStatistics(theData, stat, fillValue) > 0) {
            String stats = I18n.text("message.statistics", minmax[0], minmax[1], stat[0], stat[1]);
            Tools.showInformation(theShell, I18n.text("action.statistics"), stats);
        }
    }

    /**
     * Loads the data buffer of an object.
     *
     * @param theDataObject the object that has the buffer for the data.
     *
     * @throws Exception if a failure occurred
     */
    protected void loadData(DataFormat theDataObject) throws Exception
    {
        if (!theDataObject.isInited()) {
            try {
                theDataObject.init();
            }
            catch (Exception ex) {
                dataValue = null;
                log.debug("loadData(): ", ex);
                throw ex;
            }
        }

        // use lazy convert for large number of strings
        if (theDataObject.getHeight() > 10000 && theDataObject instanceof CompoundDS) {
            ((CompoundDS)theDataObject).setConvertByteToString(false);
        }

        // Make sure entire dataset is not loaded when looking at 3D
        // datasets using the default display mode (double clicking the
        // data object)
        if (theDataObject.getRank() > 2)
            theDataObject.getSelectedDims()[theDataObject.getSelectedIndex()[2]] = 1;

        // NatTable uses 32-bit int for row/column indices
        long displayRows = theDataObject.getHeight();
        long displayCols = theDataObject.getWidth();
        if (displayRows > Integer.MAX_VALUE) {
            throw new Exception(I18n.text("message.tableTooManyRows", displayRows, Integer.MAX_VALUE));
        }
        if (displayCols > Integer.MAX_VALUE) {
            throw new Exception(I18n.text("message.tableTooManyColumns", displayCols, Integer.MAX_VALUE));
        }
        if (displayRows > Integer.MAX_VALUE / 20) {
            log.warn("loadData(): row count {} approaches NatTable 32-bit limit; "
                         + "display may not render correctly. Consider selecting a smaller subset.",
                     displayRows);
        }
        if (displayCols > Integer.MAX_VALUE / 80) {
            log.warn("loadData(): column count {} approaches NatTable 32-bit limit; "
                         + "display may not render correctly. Consider selecting a smaller subset.",
                     displayCols);
        }

        dataValue = null;
        try {
            log.trace("loadData(): call getData()");
            dataValue = theDataObject.getData();
        }
        catch (Exception ex) {
            dataValue = null;
            log.debug("loadData(): ", ex);
            throw ex;
        }
    }

    /**
     * Create a data table for a data object.
     *
     * @param parent        the parent object this table will be associated with.
     * @param theDataObject the data object this table will be associated with.
     *
     * @return the newly created data table
     */
    protected abstract NatTable createTable(Composite parent, DataFormat theDataObject);

    /**
     * Show the object reference data.
     *
     * @param ref
     *            the identifer for the object reference.
     */
    protected abstract void showObjRefData(byte[] ref);

    /**
     * Show the region reference data.
     *
     * @param reg
     *            the identifier for the region reference.
     */
    protected abstract void showRegRefData(byte[] reg);

    /**
     * Show the standard reference data.
     *
     * @param reg
     *            the identifier for the standard reference.
     */
    protected abstract void showStdRefData(byte[] reg);

    /**
     * Get the data editing rule for the object.
     *
     * @param theDataObject the data object
     *
     * @return the rule
     */
    protected abstract IEditableRule getDataEditingRule(DataFormat theDataObject);

    /**
     * Update the display converters.
     */
    protected void updateDataConversionSettings()
    {
        if (dataDisplayConverter != null) {
            dataDisplayConverter.setShowAsHex(showAsHex);
            dataDisplayConverter.setShowAsBin(showAsBin);
            dataDisplayConverter.setNumberFormat(numberFormat);
            dataDisplayConverter.setConvertEnum(isEnumConverted);
        }
    }

    /**
     * Update dataset's value in file. The changes will go to the file.
     */
    @Override
    public void updateValueInFile()
    {
        log.debug("updateValueInFile(): ENTRY");

        // Commit any active cell editor before saving. This is the same lifecycle
        // operation used before a view or its parent is disposed.
        commitActiveCellEditor();

        if (isReadOnly || !dataProvider.getIsValueChanged() || showAsBin || showAsHex) {
            log.debug(
                "updateValueInFile(): file not updated; read-only or unchanged data or displayed as hex or binary");
            return;
        }

        log.debug("updateValueInFile(): Calling dataObject.write()");
        try {
            dataObject.write();
            log.debug("updateValueInFile(): dataObject.write() completed successfully");
        }
        catch (Exception ex) {
            shell.getDisplay().beep();
            Tools.showError(shell, I18n.text("action.update"), ex.getMessage());
            log.debug("updateValueInFile(): ", ex);
            return;
        }

        dataProvider.setIsValueChanged(false);
        log.debug("updateValueInFile(): EXIT - value changed flag cleared");
    }

    @Override
    public HObject getDataObject()
    {
        return (HObject)dataObject;
    }

    /**
     * Capture the existing dirty TableView buffer for content search.  The
     * caller commits only the active editor before asking for this snapshot;
     * this method never writes the Dataset to disk or clears its dirty flag.
     */
    @Override
    public DatasetSearchSnapshot getSearchSnapshot()
    {
        if (dataObject == null || dataValue == null || dataProvider == null ||
            !dataProvider.getIsValueChanged() || !(dataObject instanceof Dataset))
            return null;

        HObject object = (HObject)dataObject;
        FileFormat file = object.getFileFormat();
        return new DatasetSearchSnapshot(file == null ? null : file.getFilePath(), object.getFullName(),
                                         dataValue, dataObject.getStartDims(),
                                         dataObject.getSelectedDims(), dataObject.getStride(),
                                         dataObject.getDims(), dataObject.getDatatype());
    }

    @Override
    public String getStatisticsDatasetLabel()
    {
        return dataObject == null ? "" : ((HObject)dataObject).getFullName();
    }

    @Override
    public String getStatisticsPageLabel()
    {
        if (dataObject == null)
            return "";

        int[] selectedIndex = dataObject.getSelectedIndex();
        long[] start = dataObject.getStartDims();
        if (dataObject.getRank() < 3 || selectedIndex == null || start == null)
            return I18n.text("statistics.currentPage");

        StringBuilder indices = new StringBuilder();
        for (int i = 2; i < selectedIndex.length; i++) {
            if (indices.length() > 0)
                indices.append(", ");
            int dimension = selectedIndex[i];
            indices.append(dimension < start.length ? start[dimension] : 0);
        }
        return I18n.text("statistics.page", indices.toString());
    }

    @Override
    public void startStatistics(DatasetStatisticsDialog dialog, DatasetStatisticsEngine.Scope scope)
    {
        if (dialog == null || dataObject == null || !(dataObject instanceof Dataset))
            return;

        if (statisticsCancel != null)
            statisticsCancel.set(true);
        final long generation = ++statisticsGeneration;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        statisticsCancel = cancelled;
        statisticsDialog = dialog;
        dialog.showRunning(scope);

        final DatasetStatisticsEngine.Request request;
        try {
            request = captureStatisticsRequest();
        }
        catch (Exception ex) {
            dialog.showError(statisticsError(ex));
            return;
        }

        Thread worker = new Thread(() -> {
            try {
                DatasetStatisticsEngine.Result result =
                    DatasetStatisticsEngine.compute(request, scope, cancelled,
                        (processed, total, phase) -> display.asyncExec(() -> {
                            if (isStatisticsActive(dialog, generation))
                                dialog.updateProgress(processed, total);
                        }));
                display.asyncExec(() -> {
                    if (isStatisticsActive(dialog, generation))
                        dialog.showResult(result);
                });
            }
            catch (DatasetStatisticsEngine.StatisticsCancelled ex) {
                display.asyncExec(() -> {
                    if (isStatisticsActive(dialog, generation))
                        dialog.showCancelled();
                });
            }
            catch (Exception ex) {
                display.asyncExec(() -> {
                    if (isStatisticsActive(dialog, generation))
                        dialog.showError(statisticsError(ex));
                });
            }
        }, "hdfview-dataset-statistics");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void cancelStatistics(DatasetStatisticsDialog dialog)
    {
        if (dialog == statisticsDialog && statisticsCancel != null)
            statisticsCancel.set(true);
    }

    @Override
    public void highlightStatistics(DatasetStatisticsEngine.Kind kind)
    {
        if (dataTable == null || dataLayer == null || dataProvider == null)
            return;

        commitActiveCellEditor();
        statisticsHighlightCells.clear();
        try {
            int rows = dataProvider.getRowCount();
            int columns = dataProvider.getColumnCount();
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    Object value = dataLayer.getDataValueByPosition(column, row);
                    if (value != null && DatasetStatisticsEngine.matches(value, kind))
                        statisticsHighlightCells.add(cellKey(column, row));
                }
            }
        }
        catch (RuntimeException ex) {
            log.debug("highlightStatistics(): unsupported table value", ex);
            statisticsHighlightCells.clear();
        }
        dataTable.doCommand(new VisualRefreshCommand());
    }

    @Override
    public void clearStatisticsHighlight()
    {
        statisticsHighlightCells.clear();
        if (dataTable != null && !dataTable.isDisposed())
            dataTable.doCommand(new VisualRefreshCommand());
    }

    private DatasetStatisticsEngine.Request captureStatisticsRequest()
    {
        commitActiveCellEditor();
        if (!(dataObject instanceof Dataset) || dataValue == null)
            throw new IllegalStateException(I18n.text("statistics.noData"));

        HObject object = (HObject)dataObject;
        FileFormat file = object.getFileFormat();
        DatasetSearchSnapshot currentPage = new DatasetSearchSnapshot(
            file == null ? null : file.getFilePath(), object.getFullName(), dataValue,
            dataObject.getStartDims(), dataObject.getSelectedDims(), dataObject.getStride(),
            dataObject.getDims(), dataObject.getDatatype());
        DatasetSearchSnapshot dirtyPage = getSearchSnapshot();
        return new DatasetStatisticsEngine.Request((Dataset)dataObject, currentPage, dirtyPage, fillValue);
    }

    private boolean isStatisticsActive(DatasetStatisticsDialog dialog, long generation)
    {
        return generation == statisticsGeneration && dialog == statisticsDialog &&
               dialog.isOpen() && !display.isDisposed();
    }

    private String statisticsError(Exception ex)
    {
        String message = ex == null ? "" : ex.getMessage();
        if (ex instanceof UnsupportedOperationException)
            return I18n.text("statistics.unsupported", message == null ? "" : message);
        return message == null || message.length() == 0 ? ex.getClass().getSimpleName() : message;
    }

    private static long cellKey(int column, int row)
    {
        return (((long)row) << 32) ^ (column & 0xffffffffL);
    }

    @Override
    public Object getTable()
    {
        return dataTable;
    }

    @Override
    public int getSelectedRowCount()
    {
        return selectionLayer.getSelectedRowCount();
    }

    @Override
    public int getSelectedColumnCount()
    {
        return selectionLayer.getSelectedColumnPositions().length;
    }

    /**
     * Get the selection layer.
     *
     * @return the selection layer
     */
    public SelectionLayer getSelectionLayer() { return selectionLayer; }

    /**
     * Get the data layer.
     *
     * @return the data layer
     */
    public DataLayer getDataLayer() { return dataLayer; }

    /**
     * Add current-page statistics highlighting without replacing NatTable's
     * existing data-layer label accumulator.
     */
    protected final void configureStatisticsHighlighting()
    {
        if (dataLayer == null)
            return;

        final IConfigLabelAccumulator previous = dataLayer.getConfigLabelAccumulator();
        dataLayer.setConfigLabelAccumulator(new IConfigLabelAccumulator() {
            @Override
            public void accumulateConfigLabels(LabelStack configLabels,
                                                int columnPosition, int rowPosition)
            {
                if (previous != null)
                    previous.accumulateConfigLabels(configLabels, columnPosition, rowPosition);
                if (statisticsHighlightCells.contains(cellKey(columnPosition, rowPosition)))
                    configLabels.addLabelOnTop(STATISTICS_HIGHLIGHT_LABEL);
            }
        });
    }

    /**
     * refresh the data table.
     */
    @Override
    public void refreshDataTable()
    {
        log.trace("refreshDataTable()");

        clearStatisticsHighlight();

        shell.setCursor(display.getSystemCursor(SWT.CURSOR_WAIT));
        dataValue = dataObject.refreshData();
        shell.setCursor(null);

        long[] dims = dataObject.getDims();
        log.trace("refreshDataTable() dims:{}", dims);
        dataProvider.updateDataBuffer(dataValue);
        ((RowHeaderDataProvider)rowHeaderDataProvider).updateRows(dataObject);
        log.trace("refreshDataTable(): rows={} : cols={}", dataProvider.getRowCount(),
                  dataProvider.getColumnCount());

        dataTable.doCommand(new StructuralRefreshCommand());
        final ViewportLayer viewportLayer = new ViewportLayer(selectionLayer);
        dataTable.doCommand(new ShowRowInViewportCommand(dataProvider.getRowCount() - 1));
        log.trace("refreshDataTable() finish");
    }

    /**
     * Navigate the existing NatTable to a full zero-based Dataset coordinate.
     * The Dataset selection arrays are reused so multidimensional navigation
     * follows the same frame/slice model as the normal TableView controls.
     */
    @Override
    public void navigateToIndex(long[] coordinate)
    {
        if (dataObject == null || dataTable == null || selectionLayer == null)
            return;

        clearStatisticsHighlight();

        int rank = dataObject.getRank();
        long[] dims = dataObject.getDims();
        if (dims == null)
            return;

        if (dataProvider != null && dataProvider.getIsValueChanged()) {
            commitActiveCellEditor();
            if (!Tools.showConfirm(shell, I18n.text("message.changesDetected.title"),
                                   I18n.text("message.changesDetected.text",
                                             ((HObject)dataObject).getName())))
                return;
            updateValueInFile();
        }

        if (coordinate == null || coordinate.length == 0) {
            if (rank == 0)
                selectAndRevealCell(1, 1);
            return;
        }
        if (coordinate.length != rank) {
            Tools.showError(shell, I18n.text("action.select"),
                            I18n.text("search.invalidCoordinate"));
            return;
        }
        for (int i = 0; i < rank; i++) {
            if (coordinate[i] < 0 || coordinate[i] >= dims[i]) {
                Tools.showError(shell, I18n.text("action.select"),
                                I18n.text("search.invalidCoordinate"));
                return;
            }
        }

        long[] start  = dataObject.getStartDims();
        long[] count  = dataObject.getSelectedDims();
        long[] stride = dataObject.getStride();
        int[] selectedIndex = dataObject.getSelectedIndex();
        if (start == null || count == null || stride == null || selectedIndex == null)
            return;

        for (int i = 0; i < rank; i++) {
            start[i]  = coordinate[i];
            count[i]  = 1;
            stride[i] = 1;
        }

        int rowDimension = rank > 1 ? selectedIndex[0] : 0;
        int columnDimension = rank > 1 ? selectedIndex[1] : -1;
        int frameDimension = rank > 2 ? selectedIndex[2] : -1;

        if (rank == 1) {
            start[0] = 0;
            count[0] = dims[0];
        }
        else if (rank == 2) {
            start[rowDimension] = 0;
            count[rowDimension] = dims[rowDimension];
            start[columnDimension] = 0;
            count[columnDimension] = dims[columnDimension];
        }
        else {
            start[rowDimension] = 0;
            count[rowDimension] = dims[rowDimension];
            start[columnDimension] = 0;
            count[columnDimension] = dims[columnDimension];
            start[frameDimension] = coordinate[frameDimension];
            count[frameDimension] = 1;
            curDataFrame = coordinate[frameDimension] + indexBase;
            if (frameField != null && !frameField.isDisposed())
                frameField.setText(String.valueOf(curDataFrame));
        }

        dataObject.clearData();
        try {
            dataValue = dataObject.getData();
            if (!(dataObject instanceof CompoundDS))
                dataObject.convertFromUnsignedC();
            dataValue = dataObject.getData();
            dataProvider.updateDataBuffer(dataValue);
            dataTable.doCommand(new VisualRefreshCommand());

            int row = rank == 1 ? (int)coordinate[0] + 1
                                : (int)(coordinate[rowDimension] - start[rowDimension]) + 1;
            int column = rank == 1 ? 1
                                   : (int)(coordinate[columnDimension] - start[columnDimension]) + 1;
            selectAndRevealCell(column, row);
        }
        catch (Exception ex) {
            shell.getDisplay().beep();
            Tools.showError(shell, I18n.text("message.errorLoadingData"), ex.getMessage());
            log.debug("navigateToIndex(): unable to load Dataset coordinate", ex);
        }
    }

    private void selectAndRevealCell(int column, int row)
    {
        dataTable.doCommand(new SelectCellCommand(selectionLayer, column, row, false, false));
        dataTable.doCommand(new ShowCellInViewportCommand(column, row));
    }

    // Flip to previous 'frame' of Table data
    private void previousFrame()
    {
        // Only valid operation if data object has 3 or more dimensions
        if (dataObject.getRank() < 3)
            return;

        long[] start        = dataObject.getStartDims();
        int[] selectedIndex = dataObject.getSelectedIndex();
        long curFrame       = start[selectedIndex[2]];

        if (curFrame == 0)
            return; // Current frame is the first frame

        gotoFrame(curFrame - 1);
    }

    // Flip to next 'frame' of Table data
    private void nextFrame()
    {
        // Only valid operation if data object has 3 or more dimensions
        if (dataObject.getRank() < 3)
            return;

        long[] start        = dataObject.getStartDims();
        int[] selectedIndex = dataObject.getSelectedIndex();
        long[] dims         = dataObject.getDims();
        long curFrame       = start[selectedIndex[2]];

        if (curFrame == dims[selectedIndex[2]] - 1)
            return; // Current frame is the last frame

        gotoFrame(curFrame + 1);
    }

    // Flip to the first 'frame' of Table data
    private void firstFrame()
    {
        // Only valid operation if data object has 3 or more dimensions
        if (dataObject.getRank() < 3)
            return;

        long[] start        = dataObject.getStartDims();
        int[] selectedIndex = dataObject.getSelectedIndex();
        long curFrame       = start[selectedIndex[2]];

        if (curFrame == 0)
            return; // Current frame is the first frame

        gotoFrame(0);
    }

    // Flip to the last 'frame' of Table data
    private void lastFrame()
    {
        // Only valid operation if data object has 3 or more dimensions
        if (dataObject.getRank() < 3)
            return;

        long[] start        = dataObject.getStartDims();
        int[] selectedIndex = dataObject.getSelectedIndex();
        long[] dims         = dataObject.getDims();
        long curFrame       = start[selectedIndex[2]];

        if (curFrame == dims[selectedIndex[2]] - 1)
            return; // Current page is the last page

        gotoFrame(dims[selectedIndex[2]] - 1);
    }

    // Flip to the specified 'frame' of Table data
    private void gotoFrame(long idx)
    {
        // Only valid operation if data object has 3 or more dimensions
        if (dataObject.getRank() < 3 || idx == (curDataFrame - indexBase))
            return;

        clearStatisticsHighlight();

        // Make sure to save any changes to this frame of data before changing frames
        if (dataProvider.getIsValueChanged())
            updateValueInFile();

        long[] start        = dataObject.getStartDims();
        int[] selectedIndex = dataObject.getSelectedIndex();
        long[] dims         = dataObject.getDims();

        // Do a bit of frame index validation
        if ((idx < 0) || (idx >= dims[selectedIndex[2]])) {
            shell.getDisplay().beep();
            Tools.showError(shell, I18n.text("action.select"),
                            I18n.text("message.frameRange", indexBase,
                                      dims[selectedIndex[2]] - 1 + indexBase));
            return;
        }

        start[selectedIndex[2]] = idx;
        curDataFrame            = idx + indexBase;
        frameField.setText(String.valueOf(curDataFrame));

        dataObject.clearData();

        shell.setCursor(display.getSystemCursor(SWT.CURSOR_WAIT));

        try {
            dataValue = dataObject.getData();

            /*
             * TODO(HDFView) [2025-12]: Implement unsigned-to-signed conversion for table view display of
             * CompoundDS. Currently compound datasets skip unsigned conversion, causing negative values to
             * appear for unsigned data. Display layer should handle conversion even if data layer doesn't
             * (see Dataset.java line 738). May need display-only conversion without modifying underlying
             * data. Related: Dataset.java line 738 for data-layer conversion logic.
             */
            if (!(dataObject instanceof CompoundDS))
                dataObject.convertFromUnsignedC();

            dataValue = dataObject.getData();
        }
        catch (Exception ex) {
            shell.getDisplay().beep();
            Tools.showError(shell, I18n.text("message.errorLoadingData"),
                            I18n.text("message.datasetGetData") + ": " + ex.getMessage());
            log.debug("gotoFrame(): ", ex);
            dataValue = null;
        }
        finally {
            shell.setCursor(null);
        }

        dataProvider.updateDataBuffer(dataValue);

        dataTable.doCommand(new VisualRefreshCommand());
    }

    /**
     * Copy data from the spreadsheet to the system clipboard.
     */
    private void copyData()
    {
        StringBuilder sb = new StringBuilder();

        Rectangle selection = selectionLayer.getLastSelectedRegion();
        if (selection == null) {
            Tools.showError(shell, I18n.text("action.copy"), I18n.text("table.noDataToCopy"));
            return;
        }

        int r0 = selectionLayer.getLastSelectedRegion().y; // starting row
        int c0 = selectionLayer.getLastSelectedRegion().x; // starting column

        if ((r0 < 0) || (c0 < 0))
            return;

        int nr = selectionLayer.getSelectedRowCount();
        int nc = selectionLayer.getSelectedColumnPositions().length;
        int r1 = r0 + nr; // finish row
        int c1 = c0 + nc; // finishing column

        try {
            for (int i = r0; i < r1; i++) {
                sb.append(selectionLayer.getDataValueByPosition(c0, i).toString());
                for (int j = c0 + 1; j < c1; j++)
                    sb.append("\t").append(selectionLayer.getDataValueByPosition(j, i).toString());
                sb.append("\n");
            }
        }
        catch (java.lang.OutOfMemoryError err) {
            shell.getDisplay().beep();
            Tools.showError(
                shell, I18n.text("action.copy"), I18n.text("message.copyClipboardFailed"));
            return;
        }

        Clipboard cb             = Toolkit.getDefaultToolkit().getSystemClipboard();
        StringSelection contents = new StringSelection(sb.toString());
        cb.setContents(contents, null);
    }

    /**
     * Paste data from the system clipboard to the spreadsheet.
     */
    private void pasteData()
    {
        if (!Tools.showConfirm(shell, I18n.text("message.clipboardData.title"),
                               I18n.text("table.pasteConfirm")))
            return;

        int cols = selectionLayer.getPreferredColumnCount();
        int rows = selectionLayer.getPreferredRowCount();
        int r0   = 0;
        int c0   = 0;

        Rectangle selection = selectionLayer.getLastSelectedRegion();
        if (selection != null) {
            r0 = selection.y;
            c0 = selection.x;
        }

        if (c0 < 0)
            c0 = 0;
        if (r0 < 0)
            r0 = 0;
        int r = r0;
        int c = c0;

        Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        String line  = "";
        try {
            String s = (String)cb.getData(DataFlavor.stringFlavor);

            StringTokenizer st = new StringTokenizer(s, "\n");
            // read line by line
            while (st.hasMoreTokens() && (r < rows)) {
                line = st.nextToken();

                if (fixedDataLength < 1) {
                    // separate by delimiter
                    StringTokenizer lt = new StringTokenizer(line, "\t");
                    while (lt.hasMoreTokens() && (c < cols)) {
                        try {
                            dataProvider.setDataValue(c, r, lt.nextToken());
                        }
                        catch (Exception ex) {
                            continue;
                        }
                        c++;
                    }
                    r = r + 1;
                    c = c0;
                }
                else {
                    // the data has fixed length
                    int n = line.length();
                    String theVal;
                    for (int i = 0; i < n; i = i + fixedDataLength) {
                        try {
                            theVal = line.substring(i, i + fixedDataLength);
                            dataProvider.setDataValue(c, r, theVal);
                        }
                        catch (Exception ex) {
                            continue;
                        }
                        c++;
                    }
                }
            }
        }
        catch (Exception ex) {
            shell.getDisplay().beep();
            Tools.showError(shell, I18n.text("action.paste"), ex.getMessage());
        }
    }

    /**
     * Save data as text.
     *
     * @throws Exception
     *             if a failure occurred
     */
    protected void saveAsText() throws Exception
    {
        String currentDir = ((HObject)dataObject).getFileFormat().getParent();

        String filename = null;
        if (((HDFView)viewer).getTestState()) {
            filename = currentDir + File.separator +
                       new InputDialog(shell, I18n.text("dialog.enterFileName.title"), "").open();
        }
        else {
            FileDialog fChooser = new FileDialog(shell, SWT.SAVE);
            fChooser.setFilterPath(currentDir);

            DefaultFileFilter filter = DefaultFileFilter.getFileFilterText();
            fChooser.setFilterExtensions(new String[] {"*", filter.getExtensions()});
            fChooser.setFilterNames(new String[] {I18n.text("fileChooser.allFiles"), filter.getDescription()});
            fChooser.setFilterIndex(1);
            fChooser.setText(I18n.text("table.saveCurrentText", ((HObject)dataObject).getName()));

            filename = fChooser.open();
        }
        if (filename == null)
            return;

        File chosenFile = new File(filename);
        String fname    = chosenFile.getAbsolutePath();

        log.trace("saveAsText: file={}", fname);

        // Check if the file is in use and prompt for overwrite
        if (chosenFile.exists()) {
            List<?> fileList = viewer.getTreeView().getCurrentFiles();
            if (fileList != null) {
                FileFormat theFile   = null;
                Iterator<?> iterator = fileList.iterator();
                while (iterator.hasNext()) {
                    theFile = (FileFormat)iterator.next();
                    if (theFile.getFilePath().equals(fname)) {
                        shell.getDisplay().beep();
                        Tools.showError(shell, I18n.text("action.save"),
                                        I18n.text("message.saveFileInUse", fname));
                        return;
                    }
                }
            }

            if (!Tools.showConfirm(shell, I18n.text("action.save"),
                                   I18n.text("message.fileExists")))
                return;
        }

        PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(chosenFile)));

        String delName   = ViewProperties.getDataDelimiter();
        String delimiter = "";

        // delimiter must include a tab to be consistent with copy/paste for
        // compound fields
        if (dataObject instanceof CompoundDS)
            delimiter = "\t";

        if (delName.equalsIgnoreCase(ViewProperties.DELIMITER_TAB))
            delimiter = "\t";
        else if (delName.equalsIgnoreCase(ViewProperties.DELIMITER_SPACE))
            delimiter = " " + delimiter;
        else if (delName.equalsIgnoreCase(ViewProperties.DELIMITER_COMMA))
            delimiter = "," + delimiter;
        else if (delName.equalsIgnoreCase(ViewProperties.DELIMITER_COLON))
            delimiter = ":" + delimiter;
        else if (delName.equalsIgnoreCase(ViewProperties.DELIMITER_SEMI_COLON))
            delimiter = ";" + delimiter;

        int cols = selectionLayer.getPreferredColumnCount();
        int rows = selectionLayer.getPreferredRowCount();

        for (int i = 0; i < rows; i++) {
            out.print(selectionLayer.getDataValueByPosition(0, i));
            for (int j = 1; j < cols; j++) {
                out.print(delimiter);
                out.print(selectionLayer.getDataValueByPosition(j, i));
            }
            out.println();
        }

        out.flush();
        out.close();

        viewer.showStatus(I18n.text("table.dataSavedTo", fname));
    }

    // Save data as text (from TextView).
    // private void saveAsTextTextView() throws Exception {
    // FileDialog fChooser = new FileDialog(shell, SWT.SAVE);
    // fChooser.setText("Save Current Data To Text File --- " + dataset.getName());
    // fChooser.setFilterPath(dataset.getFileFormat().getParent());
    //
    // DefaultFileFilter filter = DefaultFileFilter.getFileFilterText();
    // fChooser.setFilterExtensions(new String[] {"*", filter.getExtensions()});
    // fChooser.setFilterNames(new String[] {"All Files", filter.getDescription()});
    // fChooser.setFilterIndex(1);
    //
    // // fchooser.changeToParentDirectory();
    // fChooser.setFileName(dataset.getName() + ".txt");
    // fChooser.setOverwrite(true);
    //
    // String filename = fChooser.open();
    //
    //  (filename == null) return;
    //
    // File chosenFile = new File(filename);
    //
    // // check if the file is in use
    // String fname = chosenFile.getAbsolutePath();
    // List<FileFormat> fileList = viewer.getTreeView().getCurrentFiles();
    //  (fileList != null) {
    // FileFormat theFile = null;
    // Iterator<FileFormat> iterator = fileList.iterator();
    // while (iterator.hasNext()) {
    // theFile = iterator.next();
    //  (theFile.getFilePath().equals(fname)) {
    // Tools.showError(shell, "Save", "Unable to save data to file \"" + fname
    // + "\". \nThe file is being used.");
    // return;
    // }
    // }
    // }
    //
    // PrintWriter out = new PrintWriter(new BufferedWriter(new
    // FileWriter(chosenFile)));
    //
    // int rows = text.length;
    //  (int i = 0; i < rows; i++) {
    // out.print(text[i].trim());
    // out.println();
    // out.println();
    // }
    //
    // out.flush();
    // out.close();
    //
    // viewer.showStatus("Data saved to: " + fname);
    //
    // try {
    // RandomAccessFile rf = new RandomAccessFile(chosenFile, "r");
    // long size = rf.length();
    // rf.close();
    // viewer.showStatus("File size (bytes): " + size);
    // }
    // catch (Exception ex) {
    // log.debug("raf file size:", ex);
    // }
    // }

    // print the table (from TextView)
    // private void print() {
    // // StreamPrintServiceFactory[] spsf = StreamPrintServiceFactory
    // // .lookupStreamPrintServiceFactories(null, null);
    // //  (int i = 0; i < spsf.length; i++) {
    // // System.out.println(spsf[i]);
    // // }
    // // DocFlavor[] docFlavors = spsf[0].getSupportedDocFlavors();
    // //  (int i = 0; i < docFlavors.length; i++) {
    // // System.out.println(docFlavors[i]);
    // // }
    //
    // // TODO(HDFView) [2025-12]: Implement Windows-specific URL handling for printing.
    // // Commented-out code suggests incomplete Windows URL support for print functionality.
    // // May need platform-specific file:// URL conversion or Windows print service integration.
    // // Low priority - printing feature appears to be disabled/incomplete across all platforms.
    // // Get a text DocFlavor
    // InputStream is = null;
    // try {
    // is = new BufferedInputStream(new java.io.FileInputStream(
    // "e:\\temp\\t.html"));
    // }
    // catch (Exception ex) {
    // log.debug("Get a text DocFlavor:", ex);
    // }
    // DocFlavor flavor = DocFlavor.STRING.TEXT_HTML;
    //
    // // Get all available print services
    // PrintService[] services = PrintServiceLookup.lookupPrintServices(null,
    // null);
    //
    // // Print it
    // try {
    // // Print this job on the first print server
    // DocPrintJob job = services[0].createPrintJob();
    // Doc doc = new SimpleDoc(is, flavor, null);
    //
    // job.print(doc, null);
    // }
    // catch (Exception ex) {
    // log.debug("print(): failure: ", ex);
    // }
    // }

    /**
     * Save data as binary.
     *
     * @throws Exception
     *             if a failure occurred
     */
    protected void saveAsBinary() throws Exception
    {
        String currentDir = ((HObject)dataObject).getFileFormat().getParent();

        String filename = null;
        if (((HDFView)viewer).getTestState()) {
            filename = currentDir + File.separator +
                       new InputDialog(shell, I18n.text("dialog.enterFileName.title"), "").open();
        }
        else {
            FileDialog fChooser = new FileDialog(shell, SWT.SAVE);
            fChooser.setFilterPath(currentDir);

            DefaultFileFilter filter = DefaultFileFilter.getFileFilterBinary();
            fChooser.setFilterExtensions(new String[] {"*", filter.getExtensions()});
            fChooser.setFilterNames(new String[] {I18n.text("fileChooser.allFiles"), filter.getDescription()});
            fChooser.setFilterIndex(1);
            fChooser.setText(I18n.text("table.saveCurrentBinary", ((HObject)dataObject).getName()));

            filename = fChooser.open();
        }
        if (filename == null)
            return;

        File chosenFile = new File(filename);
        String fname    = chosenFile.getAbsolutePath();

        log.trace("saveAsBinary: file={}", fname);

        // Check if the file is in use and prompt for overwrite
        if (chosenFile.exists()) {
            List<?> fileList = viewer.getTreeView().getCurrentFiles();
            if (fileList != null) {
                FileFormat theFile   = null;
                Iterator<?> iterator = fileList.iterator();
                while (iterator.hasNext()) {
                    theFile = (FileFormat)iterator.next();
                    if (theFile.getFilePath().equals(fname)) {
                        shell.getDisplay().beep();
                        Tools.showError(shell, I18n.text("action.save"),
                                        I18n.text("message.saveFileInUse", fname));
                        return;
                    }
                }
            }

            if (!Tools.showConfirm(shell, I18n.text("action.save"), I18n.text("message.fileExists")))
                return;
        }

        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(chosenFile))) {
            if (dataObject instanceof ScalarDS) {
                ((ScalarDS)dataObject).convertToUnsignedC();
                Object data  = dataObject.getData();
                ByteOrder bo = ByteOrder.nativeOrder();

                if (binaryOrder == 1)
                    bo = ByteOrder.nativeOrder();
                else if (binaryOrder == 2)
                    bo = ByteOrder.LITTLE_ENDIAN;
                else if (binaryOrder == 3)
                    bo = ByteOrder.BIG_ENDIAN;

                Tools.saveAsBinary(out, data, bo);

                viewer.showStatus(I18n.text("table.dataSavedTo", fname));
            }
            else
                viewer.showError(I18n.text("message.dataNotSavedScalar"));
        }
    }

    /**
     * Import data values from text file.
     *
     * @param fname
     *            the file to import text from
     */
    protected void importTextData(String fname)
    {
        int cols = selectionLayer.getPreferredColumnCount();
        int rows = selectionLayer.getPreferredRowCount();
        int r0;
        int c0;

        Rectangle lastSelection = selectionLayer.getLastSelectedRegion();
        if (lastSelection != null) {
            r0 = lastSelection.y;
            c0 = lastSelection.x;

            if (c0 < 0)
                c0 = 0;
            if (r0 < 0)
                r0 = 0;
        }
        else {
            r0 = 0;
            c0 = 0;
        }

        // Start at the first column for compound datasets
        if (dataObject instanceof CompoundDS)
            c0 = 0;

        String importLine          = null;
        StringTokenizer tokenizer1 = null;
        try (BufferedReader in = new BufferedReader(new FileReader(fname))) {
            try {
                importLine = in.readLine();
            }
            catch (FileNotFoundException ex) {
                log.debug("import data values from text file {}:", fname, ex);
                return;
            }
            catch (IOException ex) {
                log.debug("read text file {}:", fname, ex);
                return;
            }

            String delName   = ViewProperties.getDataDelimiter();
            String delimiter = "";

            if (delName.equalsIgnoreCase(ViewProperties.DELIMITER_TAB))
                delimiter = "\t";
            else if (delName.equalsIgnoreCase(ViewProperties.DELIMITER_SPACE))
                delimiter = " " + delimiter;
            else if (delName.equalsIgnoreCase(ViewProperties.DELIMITER_COMMA))
                delimiter = ",";
            else if (delName.equalsIgnoreCase(ViewProperties.DELIMITER_COLON))
                delimiter = ":";
            else if (delName.equalsIgnoreCase(ViewProperties.DELIMITER_SEMI_COLON))
                delimiter = ";";
            String token = null;
            int r        = r0;
            int c        = c0;
            while ((importLine != null) && (r < rows)) {
                if (fixedDataLength > 0) {
                    // the data has fixed length
                    int n = importLine.length();
                    String theVal;
                    for (int i = 0; i < n; i = i + fixedDataLength) {
                        try {
                            theVal = importLine.substring(i, i + fixedDataLength);
                            dataProvider.setDataValue(c, r, theVal);
                        }
                        catch (Exception ex) {
                            continue;
                        }
                        c++;
                    }
                }
                else {
                    try {
                        tokenizer1 = new StringTokenizer(importLine, delimiter);
                        while (tokenizer1.hasMoreTokens() && (c < cols)) {
                            token                      = tokenizer1.nextToken();
                            StringTokenizer tokenizer2 = new StringTokenizer(token);
                            if (tokenizer2.hasMoreTokens()) {
                                while (tokenizer2.hasMoreTokens() && (c < cols)) {
                                    dataProvider.setDataValue(c, r, tokenizer2.nextToken());
                                    c++;
                                }
                            }
                            else
                                c++;
                        }
                    }
                    catch (Exception ex) {
                        Tools.showError(shell, I18n.text("action.import"), ex.getMessage());
                        return;
                    }
                }

                try {
                    importLine = in.readLine();
                }
                catch (IOException ex) {
                    log.debug("read text file {}:", fname, ex);
                    importLine = null;
                }

                // Start at the first column for compound datasets
                if (dataObject instanceof CompoundDS)
                    c = 0;
                else
                    c = c0;

                r++;
            } // ((line != null) && (r < rows))
        }
        catch (IOException ex) {
            log.debug("import text file {}:", fname, ex);
        }
    }

    /**
     * Import data values from binary file.
     */
    protected void importBinaryData()
    {
        String currentDir = ((HObject)dataObject).getFileFormat().getParent();

        String filename = null;
        if (((HDFView)viewer).getTestState()) {
            filename = currentDir + File.separator +
                       new InputDialog(shell, I18n.text("dialog.enterFileName.title"), "").open();
        }
        else {
            FileDialog fChooser = new FileDialog(shell, SWT.OPEN);
            fChooser.setFilterPath(currentDir);

            DefaultFileFilter filter = DefaultFileFilter.getFileFilterBinary();
            fChooser.setFilterExtensions(new String[] {"*", filter.getExtensions()});
            fChooser.setFilterNames(new String[] {I18n.text("fileChooser.allFiles"), filter.getDescription()});
            fChooser.setFilterIndex(1);

            filename = fChooser.open();
        }

        if (filename == null)
            return;

        File chosenFile = new File(filename);
        if (!chosenFile.exists()) {
            Tools.showError(shell, I18n.text("table.importBinaryTitle"),
                            I18n.text("message.fileDoesNotExist", chosenFile.getName()));
            return;
        }

        if (!Tools.showConfirm(shell, I18n.text("table.importBinaryTitle"),
                               I18n.text("table.pasteConfirm")))
            return;

        ByteOrder bo = ByteOrder.nativeOrder();
        if (binaryOrder == 1)
            bo = ByteOrder.nativeOrder();
        else if (binaryOrder == 2)
            bo = ByteOrder.LITTLE_ENDIAN;
        else if (binaryOrder == 3)
            bo = ByteOrder.BIG_ENDIAN;

        try {
            if (Tools.getBinaryDataFromFile(dataValue, chosenFile.getAbsolutePath(), bo))
                dataProvider.setIsValueChanged(true);

            dataTable.doCommand(new StructuralRefreshCommand());
        }
        catch (Exception ex) {
            log.debug("importBinaryData():", ex);
        }
        catch (OutOfMemoryError e) {
            log.debug("importBinaryData(): Out of memory");
        }
    }

    /**
     * Convert selected data based on predefined math functions.
     */
    private void mathConversion() throws Exception
    {
        if (isReadOnly) {
            log.debug("mathConversion(): can't convert read-only data");
            return;
        }

        int cols = selectionLayer.getSelectedColumnPositions().length;
        if ((dataObject instanceof CompoundDS) && (cols > 1)) {
            shell.getDisplay().beep();
            Tools.showError(shell, I18n.text("action.convert"), I18n.text("message.mathOneColumn"));
            log.debug("mathConversion(): more than one column selected for CompoundDS");
            return;
        }

        Object theData = getSelectedData();
        if (theData == null) {
            shell.getDisplay().beep();
            Tools.showError(shell, I18n.text("action.convert"), I18n.text("table.noDataSelected"));
            log.debug("mathConversion(): no data selected");
            return;
        }

        MathConversionDialog dialog = new MathConversionDialog(shell, theData);
        dialog.open();

        if (dialog.isConverted()) {
            if (dataObject instanceof CompoundDS) {
                Object colData = null;
                try {
                    colData =
                        ((List<?>)dataObject.getData()).get(selectionLayer.getSelectedColumnPositions()[0]);
                }
                catch (Exception ex) {
                    log.debug("mathConversion(): ", ex);
                }

                if (colData != null) {
                    int size = Array.getLength(theData);
                    System.arraycopy(theData, 0, colData, 0, size);
                }
            }
            else {
                int rows = selectionLayer.getSelectedRowCount();

                // Since NatTable returns the selected row positions as a Set<Range>, convert
                // this to
                // an Integer[]
                Set<Range> rowPositions     = selectionLayer.getSelectedRowPositions();
                Set<Integer> selectedRowPos = new LinkedHashSet<>();
                Iterator<Range> i1          = rowPositions.iterator();
                while (i1.hasNext())
                    selectedRowPos.addAll(i1.next().getMembers());

                int r0 = selectedRowPos.toArray(new Integer[0])[0];
                int c0 = selectionLayer.getSelectedColumnPositions()[0];

                int w      = dataTable.getPreferredColumnCount() - 1;
                int idxSrc = 0;
                int idxDst = 0;

                for (int i = 0; i < rows; i++) {
                    idxDst = (r0 + i) * w + c0;
                    System.arraycopy(theData, idxSrc, dataValue, idxDst, cols);
                    idxSrc += cols;
                }
            }

            System.gc();

            dataProvider.setIsValueChanged(true);
        }
    }

    private void showLineplot()
    {
        // Since NatTable returns the selected row positions as a Set<Range>, convert
        // this to
        // an Integer[]
        Set<Range> rowPositions     = selectionLayer.getSelectedRowPositions();
        Set<Integer> selectedRowPos = new LinkedHashSet<>();
        Iterator<Range> i1          = rowPositions.iterator();
        while (i1.hasNext()) {
            selectedRowPos.addAll(i1.next().getMembers());
        }

        Integer[] rows = selectedRowPos.toArray(new Integer[0]);
        int[] cols     = selectionLayer.getSelectedColumnPositions();

        if ((rows == null) || (cols == null) || (rows.length <= 0) || (cols.length <= 0)) {
            shell.getDisplay().beep();
            Tools.showError(shell, I18n.text("action.select"), I18n.text("table.selectRowsColumns"));
            return;
        }

        int nrow = dataTable.getPreferredRowCount() - 1;
        int ncol = dataTable.getPreferredColumnCount() - 1;

        log.trace("DefaultTableView showLineplot: {} - {}", nrow, ncol);
        LinePlotOption lpo = new LinePlotOption(shell, SWT.NONE, nrow, ncol);
        lpo.open();

        int plotType = lpo.getPlotBy();
        if (plotType == LinePlotOption.NO_PLOT)
            return;

        boolean isRowPlot = (plotType == LinePlotOption.ROW_PLOT);
        int xIndex        = lpo.getXindex();

        // figure out to plot data by row or by column
        // Plot data by rows if all columns are selected and part of
        // rows are selected, otherwise plot data by column
        double[][] data = null;
        int nLines      = 0;
        final String chartObjectPath = ((HObject)dataObject).getPath() + ((HObject)dataObject).getName();
        final String chartPlotTypeKey;
        String title = I18n.text("chart.linePlot") + " - " + chartObjectPath;
        String[] lineLabels = null;
        double[] yRange     = {Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        double[] xData      = null;

        if (isRowPlot) {
            chartPlotTypeKey = "chart.byRow";
            title += " - " + I18n.text(chartPlotTypeKey);
            nLines = rows.length;
            if (nLines > 10) {
                shell.getDisplay().beep();
                nLines = 10;
                Tools.showWarning(shell, I18n.text("action.select"),
                                  I18n.text("message.moreThanTenRows"));
            }
            lineLabels = new String[nLines];
            data       = new double[nLines][cols.length];

            double value = 0.0;
            for (int i = 0; i < nLines; i++) {
                lineLabels[i] = String.valueOf(rows[i] + indexBase);
                for (int j = 0; j < cols.length; j++) {
                    data[i][j] = 0;
                    try {
                        value = Double.parseDouble(
                            selectionLayer.getDataValueByPosition(cols[j], rows[i]).toString());
                        data[i][j] = value;
                        yRange[0]  = Math.min(yRange[0], value);
                        yRange[1]  = Math.max(yRange[1], value);
                    }
                    catch (NumberFormatException ex) {
                        log.debug("rows[{}]:", i, ex);
                    }
                }
            }

            if (xIndex >= 0) {
                xData = new double[cols.length];
                for (int j = 0; j < cols.length; j++) {
                    xData[j] = 0;
                    try {
                        value = Double.parseDouble(
                            selectionLayer.getDataValueByPosition(cols[j], xIndex).toString());
                        xData[j] = value;
                    }
                    catch (NumberFormatException ex) {
                        log.debug("xIndex of {}:", xIndex, ex);
                    }
                }
            }
        }
        else {
            chartPlotTypeKey = "chart.byColumn";
            title += " - " + I18n.text(chartPlotTypeKey);
            nLines = cols.length;
            if (nLines > 10) {
                shell.getDisplay().beep();
                nLines = 10;
                Tools.showWarning(shell, I18n.text("action.select"),
                                  I18n.text("message.moreThanTenColumns"));
            }
            lineLabels   = new String[nLines];
            data         = new double[nLines][rows.length];
            double value = 0.0;
            for (int j = 0; j < nLines; j++) {
                lineLabels[j] = columnHeaderDataProvider.getDataValue(cols[j] + indexBase, 0).toString();
                for (int i = 0; i < rows.length; i++) {
                    data[j][i] = 0;
                    try {
                        value = Double.parseDouble(
                            selectionLayer.getDataValueByPosition(cols[j], rows[i]).toString());
                        data[j][i] = value;
                        yRange[0]  = Math.min(yRange[0], value);
                        yRange[1]  = Math.max(yRange[1], value);
                    }
                    catch (NumberFormatException ex) {
                        log.debug("cols[{}]:", j, ex);
                    }
                }
            }

            if (xIndex >= 0) {
                xData = new double[rows.length];
                for (int j = 0; j < rows.length; j++) {
                    xData[j] = 0;
                    try {
                        value = Double.parseDouble(
                            selectionLayer.getDataValueByPosition(xIndex, rows[j]).toString());
                        xData[j] = value;
                    }
                    catch (NumberFormatException ex) {
                        log.debug("xIndex of {}:", xIndex, ex);
                    }
                }
            }
        }

        int n = removeInvalidPlotData(data, xData, yRange);
        if (n < data[0].length) {
            double[][] dataNew = new double[data.length][n];
            for (int i = 0; i < data.length; i++)
                System.arraycopy(data[i], 0, dataNew[i], 0, n);

            data = dataNew;

            if (xData != null) {
                double[] xDataNew = new double[n];
                System.arraycopy(xData, 0, xDataNew, 0, n);
                xData = xDataNew;
            }
        }

        // allow to draw a flat line: all values are the same
        if (yRange[0] == yRange[1]) {
            yRange[1] += 1;
            yRange[0] -= 1;
        }
        else if (yRange[0] > yRange[1]) {
            shell.getDisplay().beep();
            Tools.showError(shell, I18n.text("action.select"),
                            I18n.text("message.linePlotRange", yRange[0], yRange[1]));
            return;
        }
        if (xData == null) { // use array index and length for x data range
            xData    = new double[2];
            xData[0] = indexBase;                              // 1- or zero-based
            xData[1] = data[0].length + (double)indexBase - 1; // maximum index
        }

        Chart cv = new Chart(shell, title, Chart.LINEPLOT, data, xData, yRange);
        cv.setWindowTitleSupplier(
            () -> I18n.text("chart.linePlot") + " - " + chartObjectPath + " - " +
                  I18n.text(chartPlotTypeKey));
        cv.setLineLabels(lineLabels);

        String cname = dataValue.getClass().getName();
        char dname   = cname.charAt(cname.lastIndexOf('[') + 1);
        if ((dname == 'B') || (dname == 'S') || (dname == 'I') || (dname == 'J'))
            cv.setTypeToInteger();

        cv.open();
    }

    /**
     * Remove values of NaN, INF from the array.
     *
     * @param data
     *            the data array
     * @param xData
     *            the x-axis data points
     * @param yRange
     *            the range of data values
     *
     * @return number of data points in the plot data if successful; otherwise,
     *         returns false.
     */
    private int removeInvalidPlotData(double[][] data, double[] xData, double[] yRange)
    {
        int idx            = 0;
        boolean hasInvalid = false;

        if (data == null || yRange == null)
            return -1;

        yRange[0] = Double.POSITIVE_INFINITY;
        yRange[1] = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < data[0].length; i++) {
            hasInvalid = false;

            for (int j = 0; j < data.length; j++) {
                hasInvalid = Tools.isNaNINF(data[j][i]);
                if (xData != null)
                    hasInvalid = hasInvalid || Tools.isNaNINF(xData[i]);

                if (hasInvalid)
                    break;
                else {
                    data[j][idx] = data[j][i];
                    if (xData != null)
                        xData[idx] = xData[i];
                    yRange[0] = Math.min(yRange[0], data[j][idx]);
                    yRange[1] = Math.max(yRange[1], data[j][idx]);
                }
            }

            if (!hasInvalid)
                idx++;
        }

        return idx;
    }

    /**
     * An implementation of a GridLayer with support for column grouping and with
     * editing triggered by a double click instead of a single click.
     */
    protected class EditingGridLayer extends GridLayer {
        /**
         * Create the Grid Layer with editing triggered by a
         *  double click instead of a single click.
         *
         * @param bodyLayer
         *        the body layer
         * @param columnHeaderLayer
         *        the Column Header layer
         * @param rowHeaderLayer
         *        the Row Header layer
         * @param cornerLayer
         *        the Corner Layer
         */
        public EditingGridLayer(ILayer bodyLayer, ILayer columnHeaderLayer, ILayer rowHeaderLayer,
                                ILayer cornerLayer)
        {
            super(bodyLayer, columnHeaderLayer, rowHeaderLayer, cornerLayer, false);

            // Left-align cells, change font for rendering cell text
            // and add cell data display converter for displaying as
            // Hexadecimal, Binary, etc.
            this.addConfiguration(new AbstractRegistryConfiguration() {
                @Override
                public void configureRegistry(IConfigRegistry configRegistry)
                {
                    Style cellStyle = new Style();

                    cellStyle.setAttributeValue(CellStyleAttributes.HORIZONTAL_ALIGNMENT,
                                                HorizontalAlignmentEnum.LEFT);
                    cellStyle.setAttributeValue(CellStyleAttributes.BACKGROUND_COLOR,
                                                Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));

                    if (curFont != null)
                        cellStyle.setAttributeValue(CellStyleAttributes.FONT, curFont);
                    else
                        cellStyle.setAttributeValue(CellStyleAttributes.FONT,
                                                    Display.getDefault().getSystemFont());

                    configRegistry.registerConfigAttribute(CellConfigAttributes.CELL_STYLE, cellStyle,
                                                           DisplayMode.NORMAL, GridRegion.BODY);

                    configRegistry.registerConfigAttribute(CellConfigAttributes.CELL_STYLE, cellStyle,
                                                           DisplayMode.SELECT, GridRegion.BODY);

                    Style statisticsStyle = new Style();
                    statisticsStyle.setAttributeValue(CellStyleAttributes.HORIZONTAL_ALIGNMENT,
                                                       HorizontalAlignmentEnum.LEFT);
                    statisticsStyle.setAttributeValue(CellStyleAttributes.BACKGROUND_COLOR,
                                                       Display.getCurrent().getSystemColor(SWT.COLOR_YELLOW));
                    statisticsStyle.setAttributeValue(CellStyleAttributes.FOREGROUND_COLOR,
                                                       Display.getCurrent().getSystemColor(SWT.COLOR_BLACK));
                    if (curFont != null)
                        statisticsStyle.setAttributeValue(CellStyleAttributes.FONT, curFont);
                    configRegistry.registerConfigAttribute(CellConfigAttributes.CELL_STYLE,
                                                           statisticsStyle, DisplayMode.NORMAL,
                                                           STATISTICS_HIGHLIGHT_LABEL);
                    configRegistry.registerConfigAttribute(CellConfigAttributes.CELL_STYLE,
                                                           statisticsStyle, DisplayMode.SELECT,
                                                           STATISTICS_HIGHLIGHT_LABEL);

                    // Add data display conversion capability
                    try {
                        dataDisplayConverter =
                            DataDisplayConverterFactory.getDataDisplayConverter(dataObject);

                        configRegistry.registerConfigAttribute(CellConfigAttributes.DISPLAY_CONVERTER,
                                                               dataDisplayConverter, DisplayMode.NORMAL,
                                                               GridRegion.BODY);
                    }
                    catch (Exception ex) {
                        log.debug("EditingGridLayer: failed to retrieve a DataDisplayConverter: ", ex);
                        dataDisplayConverter = null;
                    }
                }
            });

            if (isStdRef || isRegRef || isObjRef) {
                // Show data pointed to by reference on double click
                this.addConfiguration(new AbstractUiBindingConfiguration() {
                    @Override
                    public void configureUiBindings(UiBindingRegistry uiBindingRegistry)
                    {
                        uiBindingRegistry.registerDoubleClickBinding(
                            new MouseEventMatcher(), new IMouseAction() {
                                @Override
                                public void run(NatTable table, MouseEvent event)
                                {
                                    if (!(isStdRef || isRegRef || isObjRef))
                                        return;

                                    viewType = ViewType.TABLE;

                                    Object theData = null;
                                    try {
                                        theData = ((Dataset)getDataObject()).getData();
                                    }
                                    catch (Exception ex) {
                                        log.debug("show reference data: ", ex);
                                        theData = null;
                                        Tools.showError(shell, I18n.text("action.select"), ex.getMessage());
                                    }

                                    if (theData == null) {
                                        shell.getDisplay().beep();
                                        Tools.showError(shell, I18n.text("action.select"),
                                                        I18n.text("table.noDataSelected"));
                                        return;
                                    }

                                    // Since NatTable returns the selected row positions as a Set<Range>,
                                    // convert this to an Integer[]
                                    Set<Range> rowPositions     = selectionLayer.getSelectedRowPositions();
                                    Set<Integer> selectedRowPos = new LinkedHashSet<>();
                                    Iterator<Range> i1          = rowPositions.iterator();
                                    while (i1.hasNext()) {
                                        selectedRowPos.addAll(i1.next().getMembers());
                                    }

                                    Integer[] selectedRows = selectedRowPos.toArray(new Integer[0]);
                                    if (selectedRows == null || selectedRows.length <= 0) {
                                        log.debug("show reference data: no data selected");
                        Tools.showError(shell, I18n.text("action.select"),
                                        I18n.text("table.noDataSelected"));
                                        return;
                                    }
                                    int len = Array.getLength(selectedRows);
                                    for (int i = 0; i < len; i++) {
                                        byte[] rElements = null;
                                        if (theData instanceof ArrayList)
                                            rElements = (byte[])((ArrayList)theData).get(selectedRows[i]);
                                        else
                                            rElements = (byte[])theData;

                                        if (isStdRef)
                                            showStdRefData(rElements);
                                        else if (isRegRef)
                                            showRegRefData(rElements);
                                        else if (isObjRef)
                                            showObjRefData(rElements);
                                    }
                                }
                            });
                    }
                });
            }
            else {
                // Add default bindings for editing
                this.addConfiguration(new DefaultEditConfiguration());

                // Register cell editing rules with the table and add
                // data validation
                this.addConfiguration(new AbstractRegistryConfiguration() {
                    @Override
                    public void configureRegistry(IConfigRegistry configRegistry)
                    {
                        IEditableRule editingRule = getDataEditingRule(dataObject);
                        if (editingRule != null) {
                            // Register cell editing rules with table
                            configRegistry.registerConfigAttribute(EditConfigAttributes.CELL_EDITABLE_RULE,
                                                                   editingRule, DisplayMode.EDIT);
                        }

                        // Add data validator and validation error handler
                        DataValidator validator = null;
                        try {
                            validator = DataValidatorFactory.getDataValidator(dataObject);
                        }
                        catch (Exception ex) {
                            log.debug(
                                "EditingGridLayer: no DataValidator retrieved, data editing will be disabled");
                        }

                        if (validator != null) {
                            configRegistry.registerConfigAttribute(EditConfigAttributes.DATA_VALIDATOR,
                                                                   validator, DisplayMode.EDIT,
                                                                   GridRegion.BODY);
                        }

                        configRegistry.registerConfigAttribute(EditConfigAttributes.VALIDATION_ERROR_HANDLER,
                                                               new DialogErrorHandling(), DisplayMode.EDIT,
                                                               GridRegion.BODY);
                    }
                });

                // Change cell editing to be on double click rather than single click
                // and allow editing of cells by pressing keys as well
                this.addConfiguration(new AbstractUiBindingConfiguration() {
                    @Override
                    public void configureUiBindings(UiBindingRegistry uiBindingRegistry)
                    {
                        uiBindingRegistry.registerFirstKeyBinding(new LetterOrDigitKeyEventMatcher(),
                                                                  new KeyEditAction());
                        uiBindingRegistry.registerFirstDoubleClickBinding(new CellEditorMouseEventMatcher(),
                                                                          new MouseEditAction());
                    }
                });
            }
        }
    }

    /**
     * An implementation of the table's Row Header which adapts to the current font.
     */
    protected class RowHeader extends RowHeaderLayer {
        /**
         * Create the RowHeader which adapts to the current font.
         *
         * @param baseLayer
         *        the base layer
         * @param verticalLayerDependency
         *        the vertical layer dependency
         * @param selectionLayer
         *        the selection layer
         */
        public RowHeader(IUniqueIndexLayer baseLayer, ILayer verticalLayerDependency,
                         SelectionLayer selectionLayer)
        {
            super(baseLayer, verticalLayerDependency, selectionLayer);

            this.addConfiguration(new DefaultRowHeaderLayerConfiguration() {
                @Override
                public void addRowHeaderStyleConfig()
                {
                    this.addConfiguration(new DefaultRowHeaderStyleConfiguration() {
                        {
                            this.cellPainter = new LineBorderDecorator(new TextPainter(false, true, 2, true));
                            this.bgColor =
                                Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW);
                            this.font = (curFont == null) ? Display.getDefault().getSystemFont() : curFont;
                        }
                    });
                }
            });
        }
    }

    /**
     * Custom Row Header data provider to set row indices based on Index Base for
     * both Scalar Datasets and Compound Datasets.
     */
    protected class RowHeaderDataProvider implements IDataProvider {
        private int rank;
        private int spaceType;
        private long[] dims;
        private long[] startArray;
        private long[] strideArray;
        private int[] selectedIndex;

        /** the start value. */
        protected int start;
        /** the stride value. */
        protected int stride;

        private int nrows;

        /**
         * Create the Row Header data provider to set row indices based on Index Base for
         *  both Scalar Datasets and Compound Datasets.
         *
         * @param theDataObject
         *        the data object
         */
        public RowHeaderDataProvider(DataFormat theDataObject)
        {
            this.spaceType     = theDataObject.getSpaceType();
            this.rank          = theDataObject.getRank();
            this.dims          = theDataObject.getSelectedDims();
            this.startArray    = theDataObject.getStartDims();
            this.strideArray   = theDataObject.getStride();
            this.selectedIndex = theDataObject.getSelectedIndex();

            if (rank > 1)
                this.nrows = (int)theDataObject.getHeight();
            else
                this.nrows = (int)dims[0];

            start  = (int)startArray[selectedIndex[0]];
            stride = (int)strideArray[selectedIndex[0]];
        }

        /**
         * Update the Row Header data provider to set row indices based on Index Base for
         *  both Scalar Datasets and Compound Datasets.
         *
         * @param theDataObject
         *        the data object
         */
        public void updateRows(DataFormat theDataObject)
        {
            this.rank          = theDataObject.getRank();
            this.dims          = theDataObject.getSelectedDims();
            this.selectedIndex = theDataObject.getSelectedIndex();

            if (rank > 1)
                this.nrows = (int)theDataObject.getHeight();
            else
                this.nrows = (int)dims[0];
        }

        @Override
        public int getColumnCount()
        {
            return 1;
        }

        @Override
        public int getRowCount()
        {
            return nrows;
        }

        @Override
        public Object getDataValue(int columnIndex, int rowIndex)
        {
            return String.valueOf(start + indexBase + (rowIndex * stride));
        }

        @Override
        public void setDataValue(int columnIndex, int rowIndex, Object newValue)
        {
            // Intentional
        }
    }

    /**
     * An implementation of the table's Column Header which adapts to the current
     * font.
     */
    protected class ColumnHeader extends ColumnHeaderLayer {
        /**
         * Create the ColumnHeader which adapts to the current font.
         *
         * @param baseLayer
         *        the base layer
         * @param horizontalLayerDependency
         *        the horizontal layer dependency
         * @param selectionLayer
         *        the selection layer
         */
        public ColumnHeader(IUniqueIndexLayer baseLayer, ILayer horizontalLayerDependency,
                            SelectionLayer selectionLayer)
        {
            super(baseLayer, horizontalLayerDependency, selectionLayer);

            this.addConfiguration(new DefaultColumnHeaderLayerConfiguration() {
                @Override
                public void addColumnHeaderStyleConfig()
                {
                    this.addConfiguration(new DefaultColumnHeaderStyleConfiguration() {
                        {
                            this.cellPainter =
                                new BeveledBorderDecorator(new TextPainter(false, true, 2, true));
                            this.bgColor = Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW);
                            this.font    = (curFont == null) ? Display.getDefault().getSystemFont() : curFont;
                        }
                    });
                }
            });
        }
    }

    /** Context-menu for dealing with region and object references. */
    protected class RefContextMenu extends AbstractUiBindingConfiguration {
        private final Menu contextMenu;

        /**
         * Create the Context-menu for dealing with region and object references.
         *
         * @param table
         *        the NatTable object
         */
        public RefContextMenu(NatTable table) { this.contextMenu = createMenu(table).build(); }

        private void showRefTable()
        {
            log.trace("show reference data: Show data as {}", viewType);

            Object theData = getSelectedData();
            if (theData == null) {
                shell.getDisplay().beep();
                Tools.showError(shell, I18n.text("action.select"), I18n.text("table.noDataSelected"));
                return;
            }
            if (!(theData instanceof byte[]) && !(theData instanceof ArrayList)) {
                shell.getDisplay().beep();
                Tools.showError(shell, I18n.text("action.select"), I18n.text("message.notReference"));
                return;
            }
            log.trace("show reference data: Data is {}", theData);

            // Since NatTable returns the selected row positions as a Set<Range>, convert
            // this to an Integer[]
            Set<Range> rowPositions     = selectionLayer.getSelectedRowPositions();
            Set<Integer> selectedRowPos = new LinkedHashSet<>();
            Iterator<Range> i1          = rowPositions.iterator();
            while (i1.hasNext())
                selectedRowPos.addAll(i1.next().getMembers());

            Integer[] selectedRows = selectedRowPos.toArray(new Integer[0]);
            int[] selectedCols     = selectionLayer.getSelectedColumnPositions();
            if (selectedRows == null || selectedRows.length <= 0) {
                shell.getDisplay().beep();
                Tools.showError(shell, I18n.text("action.select"), I18n.text("table.noDataSelected"));
                log.trace("show reference data: Show data as {}: selectedRows is empty", viewType);
                return;
            }

            int len = Array.getLength(selectedRows) * Array.getLength(selectedCols);
            log.trace("show reference data: Show data as {}: len={}", viewType, len);
            if (len > 1) {
                shell.getDisplay().beep();
                Tools.showError(shell, I18n.text("action.select"), I18n.text("message.referenceOneCell"));
                log.trace("show reference data: Show data as {}: Too much data", viewType);
                return;
            }

            for (int i = 0; i < len; i++) {
                byte[] rElements = null;
                if (theData instanceof ArrayList)
                    rElements = (byte[])((ArrayList)theData).get(i);
                else
                    rElements = (byte[])theData;

                if (rElements.length == HDF5Constants.H5R_DSET_REG_REF_BUF_SIZE) {
                    showRegRefData(rElements);
                }
                else if (rElements.length == HDF5Constants.H5R_OBJ_REF_BUF_SIZE) {
                    showObjRefData(rElements);
                }
                else {
                    showStdRefData(rElements);
                }
            }
        }

        private PopupMenuBuilder createMenu(NatTable table)
        {
            Menu menu = new Menu(table);

            MenuItem item = new MenuItem(menu, SWT.PUSH);
            I18n.bind(item, "table.showAsTable");
            item.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    viewType = ViewType.TABLE;
                    showRefTable();
                }
            });

            item = new MenuItem(menu, SWT.PUSH);
            I18n.bind(item, "table.showAsImage");
            item.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    viewType = ViewType.IMAGE;
                    showRefTable();
                }
            });

            return new PopupMenuBuilder(table, menu);
        }

        @Override
        public void configureUiBindings(UiBindingRegistry uiBindingRegistry)
        {
            uiBindingRegistry.registerMouseDownBinding(
                new MouseEventMatcher(SWT.NONE, GridRegion.BODY, MouseEventMatcher.RIGHT_BUTTON),
                new PopupMenuAction(this.contextMenu));
        }
    }

    private class LinePlotOption extends Dialog {
        private Shell linePlotOptionShell;

        private Button rowButton;
        private Button colButton;

        private Combo rowBox;
        private Combo colBox;

        public static final int NO_PLOT     = -1;
        public static final int ROW_PLOT    = 0;
        public static final int COLUMN_PLOT = 1;

        private int nrow;
        private int ncol;

        private int idxXAxis = -1;
        private int plotType = -1;

        LinePlotOption(Shell parent, int style, int nrow, int ncol)
        {
            super(parent, style);

            this.nrow = nrow;
            this.ncol = ncol;
        }

        public void open()
        {
            Shell parent        = getParent();
            linePlotOptionShell = new Shell(parent, SWT.SHELL_TRIM | SWT.APPLICATION_MODAL);
            linePlotOptionShell.setFont(curFont);
            I18n.bind(linePlotOptionShell, "dialog.linePlot.title", ((HObject)dataObject).getName());
            linePlotOptionShell.setImages(ViewProperties.getHdfIcons());
            linePlotOptionShell.setLayout(new GridLayout(1, true));

            Label label = new Label(linePlotOptionShell, SWT.RIGHT);
            label.setFont(curFont);
            I18n.bind(label, "dialog.linePlot.options");

            Composite content = new Composite(linePlotOptionShell, SWT.BORDER);
            content.setLayout(new GridLayout(3, false));
            content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

            label = new Label(content, SWT.RIGHT);
            label.setFont(curFont);
            I18n.bind(label, "table.lineSeriesIn");

            colButton = new Button(content, SWT.RADIO);
            colButton.setFont(curFont);
            I18n.bind(colButton, "common.column");
            colButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
            colButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    colBox.setEnabled(true);
                    rowBox.setEnabled(false);
                }
            });

            rowButton = new Button(content, SWT.RADIO);
            rowButton.setFont(curFont);
            I18n.bind(rowButton, "common.row");
            rowButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
            rowButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    rowBox.setEnabled(true);
                    colBox.setEnabled(false);
                }
            });

            label = new Label(content, SWT.RIGHT);
            label.setFont(curFont);
            I18n.bind(label, "table.abscissa");

            long[] startArray   = dataObject.getStartDims();
            long[] strideArray  = dataObject.getStride();
            int[] selectedIndex = dataObject.getSelectedIndex();
            int start           = (int)startArray[selectedIndex[0]];
            int stride          = (int)strideArray[selectedIndex[0]];

            colBox = new Combo(content, SWT.SINGLE | SWT.READ_ONLY);
            colBox.setFont(curFont);
            GridData colBoxData     = new GridData(SWT.FILL, SWT.FILL, true, false);
            colBoxData.minimumWidth = 100;
            colBox.setLayoutData(colBoxData);

            colBox.add(I18n.text("common.arrayIndex"));

            for (int i = 0; i < ncol; i++)
                colBox.add("");

            String[] colItemKeys = new String[colBox.getItemCount()];
            Object[][] colItemArgs = new Object[colBox.getItemCount()][];
            colItemKeys[0] = "common.arrayIndex";
            for (int i = 1; i < colItemKeys.length; i++) {
                colItemKeys[i] = "table.column";
                colItemArgs[i] = new Object[] {columnHeaderDataProvider.getDataValue(i - 1, 0)};
            }
            I18n.bindItems(colBox, colItemKeys, colItemArgs);

            rowBox = new Combo(content, SWT.SINGLE | SWT.READ_ONLY);
            rowBox.setFont(curFont);
            GridData rowBoxData     = new GridData(SWT.FILL, SWT.FILL, true, false);
            rowBoxData.minimumWidth = 100;
            rowBox.setLayoutData(rowBoxData);

            rowBox.add(I18n.text("common.arrayIndex"));

            for (int i = 0; i < nrow; i++)
                rowBox.add("");

            String[] rowItemKeys = new String[rowBox.getItemCount()];
            Object[][] rowItemArgs = new Object[rowBox.getItemCount()][];
            rowItemKeys[0] = "common.arrayIndex";
            for (int i = 1; i < rowItemKeys.length; i++) {
                rowItemKeys[i] = "table.row";
                rowItemArgs[i] = new Object[] {start + indexBase + (i - 1) * stride};
            }
            I18n.bindItems(rowBox, rowItemKeys, rowItemArgs);

            // Create Ok/Cancel button region
            Composite buttonComposite = new Composite(linePlotOptionShell, SWT.NONE);
            buttonComposite.setLayout(new GridLayout(2, true));
            buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));

            Button okButton = new Button(buttonComposite, SWT.PUSH);
            okButton.setFont(curFont);
            I18n.bind(okButton, "button.ok");
            okButton.setLayoutData(new GridData(SWT.END, SWT.FILL, true, false));
            okButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    if (colButton.getSelection()) {
                        idxXAxis = colBox.getSelectionIndex() - 1;
                        plotType = COLUMN_PLOT;
                    }
                    else {
                        idxXAxis = rowBox.getSelectionIndex() - 1;
                        plotType = ROW_PLOT;
                    }

                    linePlotOptionShell.dispose();
                }
            });

            Button cancelButton = new Button(buttonComposite, SWT.PUSH);
            cancelButton.setFont(curFont);
            I18n.bind(cancelButton, "button.cancel");
            cancelButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.FILL, true, false));
            cancelButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    plotType = NO_PLOT;
                    linePlotOptionShell.dispose();
                }
            });

            colButton.setSelection(true);
            rowButton.setSelection(false);

            colBox.select(0);
            rowBox.select(0);

            colBox.setEnabled(colButton.getSelection());
            rowBox.setEnabled(rowButton.getSelection());

            linePlotOptionShell.pack();

            linePlotOptionShell.setMinimumSize(linePlotOptionShell.computeSize(SWT.DEFAULT, SWT.DEFAULT));

            Rectangle parentBounds = parent.getBounds();
            Point shellSize        = linePlotOptionShell.getSize();
            linePlotOptionShell.setLocation((parentBounds.x + (parentBounds.width / 2)) - (shellSize.x / 2),
                                            (parentBounds.y + (parentBounds.height / 2)) - (shellSize.y / 2));

            linePlotOptionShell.open();

            Display pdisplay = parent.getDisplay();
            while (!linePlotOptionShell.isDisposed())
                if (!pdisplay.readAndDispatch())
                    pdisplay.sleep();
        }

        int getXindex() { return idxXAxis; }

        int getPlotBy() { return plotType; }
    }
}
