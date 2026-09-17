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

package hdf.view.MetaDataView;

import java.io.File;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import hdf.object.Attribute;
import hdf.object.CompoundDS;
import hdf.object.Dataset;
import hdf.object.Datatype;
import hdf.object.FileFormat;
import hdf.object.Group;
import hdf.object.HObject;
import hdf.object.MetaDataContainer;
import hdf.object.ScalarDS;
import hdf.object.h5.H5ReferenceType;
import hdf.object.h5.H5ReferenceType.H5ReferenceData;
import hdf.view.DataView.DataViewManager;
import hdf.view.DefaultFileFilter;
import hdf.view.Tools;
import hdf.view.TreeView.DefaultTreeView;
import hdf.view.TreeView.TreeView;
import hdf.view.ViewProperties;
import hdf.view.dialog.InputDialog;
import hdf.view.dialog.NewScalarAttributeDialog;
import hdf.view.dialog.NewStringAttributeDialog;
import hdf.view.i18n.I18n;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/**
 * DefaultBaseMetaDataView is a default implementation of the MetaDataView which
 * is used to show data properties of an object. Data properties include
 * attributes and general object information such as the object type, data type
 * and data space.
 *
 * This base class is responsible for displaying an object's general information
 * and attributes, since these are not object-specific. Subclasses of this class
 * are responsible for displaying any extra object-specific content by
 * overriding the addObjectSpecificContent() method.
 *
 * @author Jordan T. Henderson
 * @version 1.0 4/20/2018
 */
public abstract class DefaultBaseMetaDataView implements MetaDataView {

    private static final Logger log = LoggerFactory.getLogger(DefaultBaseMetaDataView.class);

    /** The default display. */
    protected final Display display = Display.getDefault();

    /** The view manger reference. */
    protected final DataViewManager viewManager;

    private final Composite parent;

    /** The metadata container. */
    protected final TabFolder contentTabFolder;

    /** The attribute metadata pane. */
    protected final Composite attributeInfoPane;

    /** The general metadata pane. */
    protected final Composite generalObjectInfoPane;

    /** The current font. */
    protected Font curFont;

    /** The HDF data object. */
    protected HObject dataObject;

    /** The table to hold the list of attributes attached to the HDF object. */
    private Table attrTable;

    private Label attrNumberLabel;

    private List<?> attrList;

    private int numAttributes;

    /** The HDF data object is hdf5 type. */
    protected boolean isH5;
    /** The HDF data object is hdf4 type. */
    protected boolean isH4;
    /** The HDF data object is netcdf type. */
    protected boolean isN3;

    private static final String[] attrTableColumnKeys =
        {"meta.attributeName", "meta.attributeType", "meta.attributeArraySize", "meta.attributeValue"};

    private static final int ATTR_TAB_INDEX    = 0;
    private static final int GENERAL_TAB_INDEX = 1;

    /** Whether the caller supplied the long-lived host TabFolder. */
    private final boolean usesParentTabFolder;

    /**
     * The metadata view interface for displaying metadata information.
     *
     * @param parentComposite the parent visual object
     * @param viewer          the viewer to use
     * @param theObj          the object to display the metadata info
     */
    public DefaultBaseMetaDataView(Composite parentComposite, DataViewManager viewer, HObject theObj)
    {
        this.parent      = parentComposite;
        this.viewManager = viewer;
        this.dataObject  = theObj;
        usesParentTabFolder = parentComposite instanceof TabFolder;

        numAttributes = 0;

        isH5 = dataObject.getFileFormat().isThisType(FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF5));
        isH4 = dataObject.getFileFormat().isThisType(FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF4));
        isN3 = dataObject.getFileFormat().isThisType(FileFormat.getFileFormat(FileFormat.FILE_TYPE_NC3));

        try {
            curFont =
                new Font(display, ViewProperties.getFontType(), ViewProperties.getFontSize(), SWT.NORMAL);
        }
        catch (Exception ex) {
            curFont = null;
        }

        /* Get the metadata information before adding GUI components */
        try {
            attrList = ((MetaDataContainer)dataObject).getMetadata();
            if (attrList != null)
                numAttributes = attrList.size();
        }
        catch (Exception ex) {
            attrList = null;
            log.debug("Error retrieving metadata of object '" + dataObject.getName() + "':", ex);
        }
        for (int i = 0; i < numAttributes; i++) {
            Attribute attr = (Attribute)attrList.get(i);
            Datatype atype = attr.getAttributeDatatype();
            if (isH5 && atype.isRef()) {
                H5ReferenceType rtype = (H5ReferenceType)atype;
                try {
                    List<H5ReferenceData> refdata = (List)rtype.getData();
                    for (int r = 0; r < (int)rtype.getRefSize(); r++) {
                        H5ReferenceData rf = refdata.get(r);
                        log.trace("constructor: refdata {}", rf.refArray);
                    }
                }
                catch (Exception ex) {
                    log.trace("Error retrieving H5ReferenceData of object ", ex);
                }
            }
        }

        log.trace("dataObject={} isN3={} isH4={} isH5={} numAttributes={}", dataObject, isN3, isH4, isH5,
                  numAttributes);

        if (usesParentTabFolder) {
            contentTabFolder = (TabFolder)parent;
        }
        else {
            contentTabFolder = new TabFolder(parent, SWT.NONE);
            contentTabFolder.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    switch (contentTabFolder.getSelectionIndex()) {
                    case ATTR_TAB_INDEX:
                        parent.setData("MetaDataView.LastTabIndex", ATTR_TAB_INDEX);
                        break;
                    case GENERAL_TAB_INDEX:
                    default:
                        parent.setData("MetaDataView.LastTabIndex", GENERAL_TAB_INDEX);
                        break;
                    }
                }
            });
        }

        attributeInfoPane = createAttributeInfoPane(contentTabFolder, dataObject);
        if (attributeInfoPane != null) {
            TabItem attributeInfoItem = usesParentTabFolder
                ? new TabItem(contentTabFolder, SWT.NONE)
                : new TabItem(contentTabFolder, SWT.NONE, ATTR_TAB_INDEX);
            I18n.bind(attributeInfoItem, "tab.objectAttributeInfo");
            attributeInfoItem.setControl(attributeInfoPane);
        }

        generalObjectInfoPane = createGeneralObjectInfoPane(contentTabFolder, dataObject);
        if (generalObjectInfoPane != null) {
            TabItem generalInfoItem = usesParentTabFolder
                ? new TabItem(contentTabFolder, SWT.NONE)
                : new TabItem(contentTabFolder, SWT.NONE, GENERAL_TAB_INDEX);
            I18n.bind(generalInfoItem, "tab.generalObjectInfo");
            generalInfoItem.setControl(generalObjectInfoPane);
        }

        /* Add any extra information depending on the object type */
        try {
            addObjectSpecificContent();
        }
        catch (UnsupportedOperationException ex) {
        }

        if (!usesParentTabFolder && parent instanceof ScrolledComposite)
            ((ScrolledComposite)parent).setContent(contentTabFolder);

        /*
         * If the MetaDataView.LastTabIndex key data exists in the parent
         * composite, retrieve its value to determine which remembered
         * tab to select.
         */
        if (!usesParentTabFolder) {
            Object lastTabObject = parent.getData("MetaDataView.LastTabIndex");
            if (lastTabObject != null) {
                contentTabFolder.setSelection((int)lastTabObject);
            }
        }
    }

    /**
     * Additional metadata to display.
     */
    protected abstract void addObjectSpecificContent();

    private Composite createAttributeInfoPane(Composite aparent, final HObject adataObject)
    {
        if (aparent == null || adataObject == null)
            return null;

        org.eclipse.swt.widgets.Group attributeInfoGroup = null;

        attributeInfoGroup = new org.eclipse.swt.widgets.Group(aparent, SWT.NONE);
        attributeInfoGroup.setFont(curFont);
        attributeInfoGroup.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW));
        attributeInfoGroup.setLayout(new GridLayout(3, false));
        attributeInfoGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        if (isH5) {
            String creationOrderKey = "meta.creationOrderNotTracked";
            long ocplID                  = -1;
            long objid                   = -1;
            int creationOrder            = 0;
            try {
                objid = adataObject.open();
                if (objid >= 0) {
                    if (adataObject instanceof Group) {
                        ocplID = H5.H5Gget_create_plist(objid);
                    }
                    else if (adataObject instanceof Dataset) {
                        ocplID = H5.H5Dget_create_plist(objid);
                    }
                    if (ocplID >= 0) {
                        creationOrder = H5.H5Pget_attr_creation_order(ocplID);
                        log.trace("createAttributeInfoPane(): creationOrder={}", creationOrder);
                        if ((creationOrder & HDF5Constants.H5P_CRT_ORDER_TRACKED) > 0) {
                            creationOrderKey = "meta.creationOrderTracked";
                            if ((creationOrder & HDF5Constants.H5P_CRT_ORDER_INDEXED) > 0)
                                creationOrderKey = "meta.creationOrderTrackedIndexed";
                        }
                    }
                }
            }
            finally {
                H5.H5Pclose(ocplID);
                adataObject.close(objid);
            }

            /* Creation order section */
            Label label;
            label = new Label(attributeInfoGroup, SWT.LEFT);
            label.setFont(curFont);
            I18n.bind(label, "meta.attributeCreationOrder");
            label.setLayoutData(new GridData(SWT.BEGINNING, SWT.FILL, false, false));

            Text text;
            text = new Text(attributeInfoGroup, SWT.SINGLE | SWT.BORDER);
            text.setEditable(false);
            text.setFont(curFont);
            I18n.bind(text, creationOrderKey);
            text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
        }

        log.trace("createAttributeInfoPane(): numAttributes={}", numAttributes);

        attrNumberLabel = new Label(attributeInfoGroup, SWT.RIGHT);
        attrNumberLabel.setFont(curFont);
        I18n.bind(attrNumberLabel, "meta.numberAttributes", 0);
        attrNumberLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.FILL, false, false));

        Button addButton = new Button(attributeInfoGroup, SWT.PUSH);
        addButton.setFont(curFont);
        I18n.bind(addButton, "meta.addAttribute");
        addButton.setEnabled(!(adataObject.getFileFormat().isReadOnly()));
        addButton.setLayoutData(new GridData(SWT.END, SWT.FILL, true, false));
        addButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                addAttribute(adataObject);
            }
        });

        /* Deleting attributes is not supported by HDF4 */
        Button delButton = new Button(attributeInfoGroup, SWT.PUSH);
        delButton.setFont(curFont);
        I18n.bind(delButton, "meta.deleteAttribute");
        delButton.setEnabled(isH5 && !(adataObject.getFileFormat().isReadOnly()));
        delButton.setLayoutData(new GridData(SWT.END, SWT.FILL, false, false));
        delButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                deleteAttribute(adataObject);
            }
        });

        attrTable =
            new Table(attributeInfoGroup, SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        attrTable.setLinesVisible(true);
        attrTable.setHeaderVisible(true);
        attrTable.setFont(curFont);
        attrTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));

        Menu attrPopupMenu = createAttributePopupMenu(attrTable);
        attrTable.setMenu(attrPopupMenu);

        /*
         * Add a double-click listener for editing attribute values in a separate
         * TableView
         */
        attrTable.addListener(SWT.MouseDoubleClick, new Listener() {
            @Override
            public void handleEvent(Event arg0)
            {
                int selectionIndex = attrTable.getSelectionIndex();
                if (selectionIndex < 0) {
                    Tools.showError(Display.getDefault().getShells()[0], I18n.text("action.select"),
                                    I18n.text("meta.noAttributeSelected"));
                    return;
                }

                final TableItem item = attrTable.getItem(selectionIndex);

                viewManager.getTreeView().setDefaultDisplayMode(true);

                try {
                    Display.getDefault().syncExec(new Runnable() {
                        @Override
                        public void run()
                        {
                            try {
                                HObject selectedObject = (HObject)item.getData();
                                if ((selectedObject instanceof Dataset) &&
                                    !((Dataset)selectedObject).isNULL()) {
                                    viewManager.getTreeView().showDataContent(selectedObject);
                                }
                                else {
                                    Tools.showInformation(
                                        Display.getDefault().getShells()[0], I18n.text("action.open"),
                                        I18n.text("meta.noDataNullDataspace"));
                                }
                            }
                            catch (Exception ex) {
                                log.debug("Attribute showDataContent failure: ", ex);
                            }
                        }
                    });
                }
                catch (Exception e) {
                    log.debug("Attribute showDataContent loading manually interrupted");
                }
            }
        });

        /*
         * Add a right-click listener for showing a menu that has options for renaming
         * an attribute, editing an attribute, or deleting an attribute
         */
        attrTable.addListener(SWT.MenuDetect, new Listener() {
            @Override
            public void handleEvent(Event arg0)
            {
                int index = attrTable.getSelectionIndex();
                if (index < 0)
                    return;

                attrTable.getMenu().setVisible(true);
            }
        });

        for (int i = 0; i < attrTableColumnKeys.length; i++) {
            TableColumn column = new TableColumn(attrTable, SWT.NONE);
            I18n.bind(column, attrTableColumnKeys[i]);
            column.setMoveable(false);

            /*
             * Make sure all columns show even when the object in question has no attributes
             */
            if (i == attrTableColumnKeys.length - 1)
                column.setWidth(200);
            else
                column.setWidth(50);
        }

        if (attrList != null) {
            I18n.bind(attrNumberLabel, "meta.numberAttributes", numAttributes);

            Attribute attr = null;
            for (int i = 0; i < numAttributes; i++) {
                attr = (Attribute)attrList.get(i);

                log.trace("createAttributeInfoPane(): attr[{}] is {} of type {}", i, attr.getAttributeName(),
                          attr.getAttributeDatatype().getDescription());

                addAttributeTableItem(attrTable, attr);
            }
        }

        for (int i = 0; i < attrTableColumnKeys.length; i++) {
            attrTable.getColumn(i).pack();
        }

        // Prevent attributes with many values, such as array types, from making
        // the window too wide
        attrTable.getColumn(3).setWidth(200);

        return attributeInfoGroup;
    }

    private Composite createGeneralObjectInfoPane(Composite goparent, final HObject godataObject)
    {
        if (goparent == null || godataObject == null)
            return null;

        FileFormat theFile = godataObject.getFileFormat();
        boolean isRoot     = ((godataObject instanceof Group) && ((Group)godataObject).isRoot());
        String objTypeKey   = "common.unknown";
        Label label;
        Text text;

        /* Add an SWT Group to encompass all of the GUI components */
        org.eclipse.swt.widgets.Group generalInfoGroup =
            new org.eclipse.swt.widgets.Group(goparent, SWT.NONE);
        generalInfoGroup.setFont(curFont);
        generalInfoGroup.setLayout(new GridLayout(2, false));
        generalInfoGroup.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW));

        /* Object name section */
        label = new Label(generalInfoGroup, SWT.LEFT);
        label.setFont(curFont);
        I18n.bind(label, "meta.objectName");

        text = new Text(generalInfoGroup, SWT.SINGLE | SWT.BORDER);
        text.setEditable(false);
        text.setFont(curFont);
        text.setText(godataObject.getName());
        text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        /* Object Path section */
        label = new Label(generalInfoGroup, SWT.LEFT);
        label.setFont(curFont);
        I18n.bind(label, "meta.objectPath");

        text = new Text(generalInfoGroup, SWT.SINGLE | SWT.BORDER);
        text.setEditable(false);
        text.setFont(curFont);
        text.setText(
            godataObject.getPath() == null
                ? "/"
                : godataObject.getPath()); /* TODO(HDFView) [2025-12]: Remove null path workaround once Object
                                            * Library returns "/" for root objects. Currently path can be null
                                            * for some objects, requiring fallback to "/" in metadata display.
                                            * Once getPath() guaranteed non-null, simplify to:
                                            * text.setText(godataObject.getPath()); */
        text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        /* Object Type section */
        label = new Label(generalInfoGroup, SWT.LEFT);
        label.setFont(curFont);
        I18n.bind(label, "meta.objectType");

        if (isH5) {
            if (godataObject instanceof Group) {
                objTypeKey = "common.hdf5Group";
            }
            else if (godataObject instanceof ScalarDS) {
                objTypeKey = "common.hdf5Dataset";
            }
            else if (godataObject instanceof CompoundDS) {
                objTypeKey = "common.hdf5Dataset";
            }
            else if (godataObject instanceof Datatype) {
                objTypeKey = "common.hdf5NamedDatatype";
            }
            else {
                log.debug("createGeneralObjectInfoPane(): unknown HDF5 dataObject");
            }
        }
        else if (isH4) {
            if (godataObject instanceof Group) {
                objTypeKey = "common.hdf4Group";
            }
            else if (godataObject instanceof ScalarDS) {
                ScalarDS ds = (ScalarDS)godataObject;
                if (ds.isImage()) {
                    objTypeKey = "common.hdf4RasterImage";
                }
                else {
                    objTypeKey = "common.hdf4Sds";
                }
            }
            else if (godataObject instanceof CompoundDS) {
                objTypeKey = "common.hdf4Vdata";
            }
            else {
                log.debug("createGeneralObjectInfoPane(): unknown HDF4 dataObject");
            }
        }
        else if (isN3) {
            if (godataObject instanceof Group) {
                objTypeKey = "common.netcdf3Group";
            }
            else if (godataObject instanceof ScalarDS) {
                objTypeKey = "common.netcdf3Dataset";
            }
            else {
                log.debug("createGeneralObjectInfoPane(): unknown netCDF3 dataObject");
            }
        }
        else {
            if (godataObject instanceof Group) {
                objTypeKey = "common.group";
            }
            else if (godataObject instanceof ScalarDS) {
                objTypeKey = "common.dataset";
            }
            else if (godataObject instanceof CompoundDS) {
                objTypeKey = "common.dataset";
            }
            else {
                log.debug("createGeneralObjectInfoPane(): unknown dataObject");
            }
        }

        text = new Text(generalInfoGroup, SWT.SINGLE | SWT.BORDER);
        text.setEditable(false);
        text.setFont(curFont);
        I18n.bind(text, objTypeKey);
        text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        /* Object ID section */

        // bug #926 to remove the OID, put it back on Nov. 20, 2008, --PC
        String oidStr = null;
        long[] oID    = godataObject.getOID();
        if (oID != null) {
            oidStr = String.valueOf(oID[0]);
            if (isH4)
                oidStr += ", " + oID[1];

            if (isH5) {
                label = new Label(generalInfoGroup, SWT.LEFT);
                label.setFont(curFont);
                I18n.bind(label, "meta.objectReference");
            }
            else {
                label = new Label(generalInfoGroup, SWT.LEFT);
                label.setFont(curFont);
                I18n.bind(label, "meta.tagReference");
            }

            text = new Text(generalInfoGroup, SWT.SINGLE | SWT.BORDER);
            text.setEditable(false);
            text.setFont(curFont);
            text.setText(oidStr);
            text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        }

        /*
         * If this is the root group, add some special extra info, such as the Library
         * Version bounds set for the file.
         */
        if (isRoot) {
            /* Get the file's size */
            long fileSize = 0;
            try {
                fileSize = (new File(godataObject.getFile())).length();
            }
            catch (Exception ex) {
                fileSize = -1;
            }
            fileSize /= 1024;

            /* Retrieve the number of subgroups and datasets in the root group */
            HObject root         = theFile.getRootObject();
            HObject theObj       = null;
            Iterator<HObject> it = ((Group)root).depthFirstMemberList().iterator();
            int groupCount       = 0;
            int datasetCount     = 0;

            while (it.hasNext()) {
                theObj = it.next();

                if (theObj instanceof Group)
                    groupCount++;
                else
                    datasetCount++;
            }

            /* File name section */
            label = new Label(generalInfoGroup, SWT.LEFT);
            label.setFont(curFont);
            I18n.bind(label, "meta.fileName");

            text = new Text(generalInfoGroup, SWT.SINGLE | SWT.BORDER);
            text.setEditable(false);
            text.setFont(curFont);
            text.setText(godataObject.getFileFormat().getName());
            text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

            /* File Path section */
            label = new Label(generalInfoGroup, SWT.LEFT);
            label.setFont(curFont);
            I18n.bind(label, "meta.filePath");

            text = new Text(generalInfoGroup, SWT.SINGLE | SWT.BORDER);
            text.setEditable(false);
            text.setFont(curFont);
            text.setText((new File(godataObject.getFile())).getParent());
            text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

            label = new Label(generalInfoGroup, SWT.LEFT);
            label.setFont(curFont);
            I18n.bind(label, "meta.fileType");

            final String fileTypeKey;
            if (isH5)
                fileTypeKey = "meta.fileTypeInfo.hdf5";
            else if (isH4)
                fileTypeKey = "meta.fileTypeInfo.hdf4";
            else if (isN3)
                fileTypeKey = "meta.fileTypeInfo.netcdf3";
            else
                fileTypeKey = "meta.fileTypeInfo.other";

            text = new Text(generalInfoGroup, SWT.SINGLE | SWT.BORDER);
            text.setEditable(false);
            text.setFont(curFont);
            final long objectFileSize = fileSize;
            final int objectGroupCount = groupCount;
            final int objectDatasetCount = datasetCount;
            I18n.bindDynamic(text, () -> I18n.text(
                fileTypeKey,
                I18n.text("meta.objectCountInfo", objectFileSize, objectGroupCount, objectDatasetCount)));
            text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

            if (isH5) {
                log.trace("createGeneralObjectInfoPane(): get Library Version bounds info");
                String libversion = "";
                try {
                    libversion = godataObject.getFileFormat().getLibBoundsDescription();
                }
                catch (Exception ex) {
                    log.debug("Get Library Bounds Description failure: ", ex);
                }

                if (libversion.length() > 0) {
                    label = new Label(generalInfoGroup, SWT.LEFT);
                    label.setFont(curFont);
                    I18n.bind(label, "meta.libraryVersionBounds");

                    text = new Text(generalInfoGroup, SWT.SINGLE | SWT.BORDER);
                    text.setEditable(false);
                    text.setFont(curFont);
                    text.setText(libversion);
                    text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
                }

                Button userBlockButton = new Button(generalInfoGroup, SWT.PUSH);
                I18n.bind(userBlockButton, "meta.showUserBlock");
                userBlockButton.addSelectionListener(new SelectionAdapter() {
                    @Override
                    public void widgetSelected(SelectionEvent e)
                    {
                        new UserBlockDialog(display.getShells()[0], SWT.NONE, godataObject).open();
                    }
                });
            }
        }

        /* Add a dummy label to take up some vertical space between sections */
        label = new Label(generalInfoGroup, SWT.LEFT);
        label.setText("");
        label.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));

        return generalInfoGroup;
    }

    @Override
    public HObject getDataObject()
    {
        return dataObject;
    }

    private Menu createAttributePopupMenu(final Table table)
    {
        final Menu menu = new Menu(table);
        MenuItem item;

        item = new MenuItem(menu, SWT.PUSH);
        I18n.bind(item, "meta.renameAttribute");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                int selectionIndex = table.getSelectionIndex();
                if (selectionIndex < 0) {
                    Tools.showError(Display.getDefault().getShells()[0], I18n.text("action.select"),
                                    I18n.text("meta.noAttributeSelected"));
                    return;
                }

                HObject itemObj = (HObject)table.getItem(selectionIndex).getData();
                String result =
                    new InputDialog(Display.getDefault().getShells()[0],
                                    Display.getDefault().getShells()[0].getText() + " - "
                                        + I18n.text("meta.renameAttribute"),
                                    I18n.text("meta.newAttributeName"), itemObj.getName())
                        .open();

                if ((result == null) || ((result = result.trim()) == null) || (result.length() < 1)) {
                    return;
                }

                Attribute attr = (Attribute)attrTable.getItem(selectionIndex).getData();
                renameAttribute(attr, result);
            }
        });

        item = new MenuItem(menu, SWT.PUSH);
        I18n.bind(item, "meta.viewEditAttribute");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                int selectionIndex = attrTable.getSelectionIndex();
                if (selectionIndex < 0) {
                    Tools.showError(Display.getDefault().getShells()[0], I18n.text("action.select"),
                                    I18n.text("meta.noAttributeSelected"));
                    return;
                }

                final TableItem item = attrTable.getItem(selectionIndex);

                viewManager.getTreeView().setDefaultDisplayMode(true);

                try {
                    Display.getDefault().syncExec(new Runnable() {
                        @Override
                        public void run()
                        {
                            try {
                                HObject selectedObject = (HObject)item.getData();
                                if ((selectedObject instanceof Dataset) &&
                                    !((Dataset)selectedObject).isNULL()) {
                                    viewManager.getTreeView().showDataContent(selectedObject);
                                }
                                else {
                                    Tools.showInformation(
                                        Display.getDefault().getShells()[0], I18n.text("action.open"),
                                        I18n.text("meta.noDataNullDataspace"));
                                }
                            }
                            catch (Exception ex) {
                                log.debug("Attribute showDataContent failure: ", ex);
                            }
                        }
                    });
                }
                catch (Exception ex) {
                    log.debug("Attribute showDataContent loading manually interrupted");
                }
            }
        });

        item = new MenuItem(menu, SWT.PUSH);
        I18n.bind(item, "meta.deleteAttribute");
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                int selectionIndex = attrTable.getSelectionIndex();
                if (selectionIndex < 0) {
                    Tools.showError(Display.getDefault().getShells()[0], I18n.text("action.select"),
                                    I18n.text("meta.noAttributeSelected"));
                    return;
                }

                deleteAttribute(dataObject);
            }
        });

        menu.addMenuListener(new MenuAdapter() {
            @Override
            public void menuShown(MenuEvent e)
            {
                /* 'Rename Attribute' MenuItem */
                menu.getItem(0).setEnabled(!dataObject.getFileFormat().isReadOnly() && !isH4);

                /* 'Delete Attribute' MenuItem */
                menu.getItem(2).setEnabled(!dataObject.getFileFormat().isReadOnly() && !isH4);
            }
        });

        return menu;
    }

    @Override
    public Attribute addAttribute(HObject obj)
    {
        if (obj == null)
            return null;

        HObject root   = obj.getFileFormat().getRootObject();
        Attribute attr = null;
        if (isH5) {
            NewScalarAttributeDialog dialog = new NewScalarAttributeDialog(
                display.getShells()[0], obj, ((Group)root).breadthFirstMemberList());
            dialog.open();
            attr = dialog.getAttribute();
        }
        else {
            NewStringAttributeDialog dialog = new NewStringAttributeDialog(
                display.getShells()[0], obj, ((Group)root).breadthFirstMemberList());
            dialog.open();
            attr = dialog.getAttribute();
        }

        if (attr == null) {
            log.debug("addAttribute(): attr is null");
            return null;
        }

        addAttributeTableItem(attrTable, attr);

        numAttributes++;
        I18n.bind(attrNumberLabel, "meta.numberAttributes", numAttributes);

        if (viewManager.getTreeView() instanceof DefaultTreeView)
            ((DefaultTreeView)viewManager.getTreeView()).updateItemIcon(obj);

        return attr;
    }

    @Override
    public Attribute deleteAttribute(HObject obj)
    {
        if (obj == null)
            return null;

        int idx = attrTable.getSelectionIndex();
        if (idx < 0) {
            log.debug("deleteAttribute(): no attribute is selected");
            Tools.showError(display.getShells()[0], I18n.text("action.delete"),
                            I18n.text("message.attributeNoneSelected"));
            return null;
        }

        int answer = SWT.NO;
        if (Tools.showConfirm(display.getShells()[0], I18n.text("action.delete"),
                              I18n.text("message.deleteAttributeConfirm")))
            answer = SWT.YES;
        if (answer == SWT.NO) {
            log.trace("deleteAttribute(): attribute deletion cancelled");
            return null;
        }

        if (attrList == null) {
            log.debug("deleteAttribute(): Attribute list was null; can't delete an attribute from it");
            return null;
        }

        Attribute attr = (Attribute)attrList.get(idx);

        log.trace("deleteAttribute(): Attribute selected for deletion: {}", attr.getAttributeName());

        try {
            ((MetaDataContainer)obj).removeMetadata(attr);
        }
        catch (Exception ex) {
            log.debug("deleteAttribute(): attribute deletion failed for object '{}': ", obj.getName(), ex);
        }

        attrTable.remove(idx);
        numAttributes--;

        I18n.bind(attrNumberLabel, "meta.numberAttributes", numAttributes);

        if (viewManager.getTreeView() instanceof DefaultTreeView)
            ((DefaultTreeView)viewManager.getTreeView()).updateItemIcon(obj);

        return attr;
    }

    private void renameAttribute(Attribute attr, String newName)
    {
        if ((attr == null) || (newName == null) || (newName = newName.trim()) == null ||
            (newName.length() < 1)) {
            log.debug("renameAttribute(): Attribute is null or Attribute's new name is null");
            return;
        }

        String attrName = attr.getAttributeName();

        log.trace("renameAttribute(): oldName={} newName={}", attrName, newName);

        if (isH5) {
            try {
                dataObject.getFileFormat().renameAttribute(dataObject, attrName, newName);
            }
            catch (Exception ex) {
                log.debug("renameAttribute(): renaming failure:", ex);
                Tools.showError(display.getShells()[0], I18n.text("action.rename"), ex.getMessage());
            }

            /* Update the attribute table */
            int selectionIndex = attrTable.getSelectionIndex();
            if (selectionIndex < 0) {
                Tools.showError(Display.getDefault().getShells()[0], I18n.text("action.delete"),
                                I18n.text("meta.noAttributeSelected"));
                return;
            }

            attrTable.getItem(selectionIndex).setText(0, newName);
        }
        else {
            log.debug("renameAttribute(): renaming attributes is only allowed for HDF5 files");
        }

        if (dataObject instanceof MetaDataContainer) {
            try {
                ((MetaDataContainer)dataObject).updateMetadata(attr);
            }
            catch (Exception ex) {
                log.debug("renameAttribute(): updateMetadata() failure:", ex);
                Tools.showError(display.getShells()[0], I18n.text("action.rename"), ex.getMessage());
            }
        }
    }

    /**
     * Update an attribute's value. Currently can only update a single data point.
     *
     * @param attr
     *            the selected attribute.
     * @param newValue
     *            the string of the new value.
     */
    private void updateAttributeValue(Attribute attr, String newValue)
    {
        if ((attr == null) || (newValue == null) || (newValue = newValue.trim()) == null ||
            (newValue.length() < 1)) {
            log.debug("updateAttributeValue(): Attribute is null or Attribute's new value is null");
            return;
        }

        String attrName = attr.getAttributeName();
        Object data;

        log.trace("updateAttributeValue(): changing value of attribute '{}'", attrName);

        try {
            data = attr.getAttributeData();
        }
        catch (Exception ex) {
            log.debug("updateAttributeValue(): getData() failure:", ex);
            return;
        }

        if (data == null) {
            log.debug("updateAttributeValue(): attribute's data was null");
            return;
        }

        int arrayLength    = Array.getLength(data);
        StringTokenizer st = new StringTokenizer(newValue, ",");
        if (st.countTokens() < arrayLength) {
            log.debug("updateAttributeValue(): More data values needed: {}", newValue);
            Tools.showError(display.getShells()[0], I18n.text("action.update"),
                            I18n.text("message.moreDataNeeded", newValue));
            return;
        }

        char cNT     = ' ';
        String cName = data.getClass().getName();
        int cIndex   = cName.lastIndexOf('[');
        if (cIndex >= 0) {
            cNT = cName.charAt(cIndex + 1);
        }
        boolean isUnsigned = attr.getAttributeDatatype().isUnsigned();

        log.trace("updateAttributeValue(): array_length={} cName={} NT={} isUnsigned={}", arrayLength, cName,
                  cNT, isUnsigned);

        double d        = 0;
        String theToken = null;
        long max        = 0;
        long min        = 0;
        for (int i = 0; i < arrayLength; i++) {
            max = min = 0;
            theToken  = st.nextToken().trim();
            try {
                if (!(Array.get(data, i) instanceof String)) {
                    d = Double.parseDouble(theToken);
                }
            }
            catch (NumberFormatException ex) {
                log.debug("updateAttributeValue(): NumberFormatException: ", ex);
                Tools.showError(display.getShells()[0], I18n.text("action.update"), ex.getMessage());
                return;
            }

            if (isUnsigned && (d < 0)) {
                log.debug("updateAttributeValue(): Negative value for unsigned integer: {}", theToken);
                Tools.showError(display.getShells()[0], I18n.text("action.update"),
                                I18n.text("message.negativeUnsigned", theToken));
                return;
            }

            switch (cNT) {
            case 'B': {
                if (isUnsigned) {
                    min = 0;
                    max = 255;
                }
                else {
                    min = Byte.MIN_VALUE;
                    max = Byte.MAX_VALUE;
                }

                if ((d > max) || (d < min)) {
                    Tools.showError(display.getShells()[0], I18n.text("action.update"),
                                    I18n.text("table.dataOutOfRange", min, max, theToken));
                }
                else {
                    Array.setByte(data, i, (byte)d);
                }
                break;
            }
            case 'S': {
                if (isUnsigned) {
                    min = 0;
                    max = 65535;
                }
                else {
                    min = Short.MIN_VALUE;
                    max = Short.MAX_VALUE;
                }

                if ((d > max) || (d < min)) {
                    Tools.showError(display.getShells()[0], I18n.text("action.update"),
                                    I18n.text("table.dataOutOfRange", min, max, theToken));
                }
                else {
                    Array.setShort(data, i, (short)d);
                }
                break;
            }
            case 'I': {
                if (isUnsigned) {
                    min = 0;
                    max = 4294967295L;
                }
                else {
                    min = Integer.MIN_VALUE;
                    max = Integer.MAX_VALUE;
                }

                if ((d > max) || (d < min)) {
                    Tools.showError(display.getShells()[0], I18n.text("action.update"),
                                    I18n.text("table.dataOutOfRange", min, max, theToken));
                }
                else {
                    Array.setInt(data, i, (int)d);
                }
                break;
            }
            case 'J':
                long lvalue = 0;
                if (isUnsigned) {
                    if (theToken != null) {
                        String theValue = theToken;
                        BigInteger maxJ = new BigInteger("18446744073709551615");
                        BigInteger big  = new BigInteger(theValue);
                        if ((big.compareTo(maxJ) > 0) || (big.compareTo(BigInteger.ZERO) < 0)) {
                            Tools.showError(display.getShells()[0], I18n.text("action.update"),
                                            I18n.text("table.dataOutOfRange", min, max, theToken));
                        }
                        lvalue = big.longValue();
                        log.trace("updateAttributeValue(): big.longValue={}", lvalue);
                        Array.setLong(data, i, lvalue);
                    }
                    else
                        Array.set(data, i, theToken);
                }
                else {
                    min = Long.MIN_VALUE;
                    max = Long.MAX_VALUE;
                    if ((d > max) || (d < min)) {
                        Tools.showError(display.getShells()[0], I18n.text("action.update"),
                                        I18n.text("table.dataOutOfRange", min, max, theToken));
                    }
                    lvalue = (long)d;
                    log.trace("updateAttributeValue(): longValue={}", lvalue);
                    Array.setLong(data, i, lvalue);
                }
                break;
            case 'F':
                Array.setFloat(data, i, (float)d);
                break;
            case 'D':
                Array.setDouble(data, i, d);
                break;
            default:
                Array.set(data, i, theToken);
                break;
            }
        }

        try {
            dataObject.getFileFormat().writeAttribute(dataObject, attr, true);
        }
        catch (Exception ex) {
            log.debug("updateAttributeValue(): writeAttribute failure: ", ex);
            Tools.showError(display.getShells()[0], I18n.text("action.update"), ex.getMessage());
            return;
        }

        /* Update the attribute table */
        int selectionIndex = attrTable.getSelectionIndex();
        if (selectionIndex < 0) {
            Tools.showError(Display.getDefault().getShells()[0], I18n.text("action.update"),
                            I18n.text("meta.noAttributeSelected"));
            return;
        }

        attrTable.getItem(selectionIndex).setText(3, attr.toAttributeString(", "));

        if (dataObject instanceof MetaDataContainer) {
            try {
                ((MetaDataContainer)dataObject).updateMetadata(attr);
            }
            catch (Exception ex) {
                log.debug("updateAttributeValue(): updateMetadata() failure:", ex);
                Tools.showError(display.getShells()[0], I18n.text("action.update"), ex.getMessage());
            }
        }
    }

    private void addAttributeTableItem(Table table, Attribute attr)
    {
        if (table == null || attr == null) {
            log.debug("addAttributeTableItem(): table or attribute is null");
            return;
        }

        String attrName        = attr.getAttributeName();
        String attrType        = attr.getAttributeDatatype() == null ? null : attr.getAttributeDatatype().getDescription();
        StringBuilder attrSize = new StringBuilder();
        String attrValue       = attr.toAttributeString(", ", 50);
        String[] rowData       = new String[attrTableColumnKeys.length];

        if (attrName == null)
            attrName = I18n.text("common.null");
        if (attrValue == null)
            attrValue = I18n.text("common.null");

        TableItem item = new TableItem(attrTable, SWT.NONE);
        item.setFont(curFont);
        item.setData(attr);

        if (attr.getProperty("field") != null) {
            rowData[0] = I18n.text("meta.attributeField", attrName, attr.getProperty("field"));
        }
        else {
            rowData[0] = attrName;
        }

        if (attr.isAttributeNULL()) {
            attrSize.append(I18n.text("common.nullUpper"));
        }
        else if (attr.isAttributeScalar()) {
            attrSize.append(I18n.text("common.scalar"));
        }
        else {
            long[] dims = attr.getAttributeDims();
            attrSize.append(String.valueOf(dims[0]));
            for (int j = 1; j < dims.length; j++) {
                attrSize.append(" x ").append(dims[j]);
            }
        }

        rowData[1] = attrType == null ? I18n.text("common.null") : attrType;
        rowData[2] = attrSize.toString();
        if (attr.isAttributeNULL())
            rowData[3] = I18n.text("common.nullUpper");
        else
            rowData[3] = attrValue;

        item.setText(rowData);
        if (attrType != null)
            I18n.bindDatatypeDescription(item, 1, attrType);
        else
            I18n.bindTableCell(item, 1, "common.null");
        if (attr.isAttributeNULL()) {
            I18n.bindTableCell(item, 2, "common.nullUpper");
            I18n.bindTableCell(item, 3, "common.nullUpper");
        }
        else if (attr.isAttributeScalar()) {
            I18n.bindTableCell(item, 2, "common.scalar");
        }
    }

    /**
     * Updates the current font.
     *
     * @param font the new font
     */
    public void updateFont(Font font)
    {
        if (curFont != null)
            curFont.dispose();

        log.trace("updateFont():");
        curFont = font;

        attributeInfoPane.setFont(font);
        attributeInfoPane.pack();
        attributeInfoPane.requestLayout();

        generalObjectInfoPane.setFont(font);
        generalObjectInfoPane.pack();
        generalObjectInfoPane.requestLayout();
    }

    private class UserBlockDialog extends Dialog {
        private Shell shell;

        private final HObject obj;

        private byte[] userBlock;

        private final String[] displayChoices = {"Text", "Binary", "Octal", "Hexadecimal", "Decimal"};

        private Button jamButton;
        private Text userBlockArea;

        UserBlockDialog(Shell parent, int style, HObject obj)
        {
            super(parent, style);

            this.obj = obj;

            userBlock = Tools.getHDF5UserBlock(obj.getFile());
        }

        public void open()
        {
            Shell openParent = getParent();
            shell            = new Shell(openParent, SWT.DIALOG_TRIM | SWT.RESIZE);
            shell.setFont(curFont);
            I18n.bind(shell, "dialog.userBlock.title", obj);
            shell.setLayout(new GridLayout(5, false));

            Label label = new Label(shell, SWT.RIGHT);
            label.setFont(curFont);
            I18n.bind(label, "meta.displayAs");

            Combo userBlockDisplayChoice = new Combo(shell, SWT.SINGLE | SWT.READ_ONLY);
            userBlockDisplayChoice.setFont(curFont);
            I18n.bindItems(userBlockDisplayChoice, "common.text", "common.binary", "common.octal",
                           "common.hexadecimal", "common.decimal");
            userBlockDisplayChoice.select(0);
            userBlockDisplayChoice.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    Combo source = (Combo)e.widget;
                    int type     = 0;

                    int selectionIndex = source.getSelectionIndex();

                    jamButton.setEnabled(false);
                    userBlockArea.setEditable(false);

                    if (selectionIndex == 0) {
                        type = 0;
                        jamButton.setEnabled(true);
                        userBlockArea.setEditable(true);
                    }
                    else if (selectionIndex == 1) {
                        type = 2;
                    }
                    else if (selectionIndex == 2) {
                        type = 8;
                    }
                    else if (selectionIndex == 3) {
                        type = 16;
                    }
                    else if (selectionIndex == 4) {
                        type = 10;
                    }

                    showUserBlockAs(type);
                }
            });

            Label dummyLabel = new Label(shell, SWT.RIGHT);
            dummyLabel.setFont(curFont);
            dummyLabel.setText("");
            dummyLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

            Label sizeLabel = new Label(shell, SWT.RIGHT);
            sizeLabel.setFont(curFont);
            I18n.bind(sizeLabel, "meta.headerSize", 0);

            jamButton = new Button(shell, SWT.PUSH);
            jamButton.setFont(curFont);
            I18n.bind(jamButton, "meta.saveUserBlock");
            jamButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
            jamButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    writeUserBlock();
                }
            });

            ScrolledComposite userBlockScroller = new ScrolledComposite(shell, SWT.V_SCROLL | SWT.BORDER);
            userBlockScroller.setExpandHorizontal(true);
            userBlockScroller.setExpandVertical(true);
            userBlockScroller.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 5, 1));

            userBlockArea = new Text(userBlockScroller, SWT.MULTI | SWT.WRAP);
            userBlockArea.setEditable(true);
            userBlockArea.setFont(curFont);
            userBlockScroller.setContent(userBlockArea);

            Button closeButton = new Button(shell, SWT.CENTER);
            closeButton.setFont(curFont);
            I18n.bind(closeButton, "button.close");
            closeButton.setLayoutData(new GridData(SWT.CENTER, SWT.FILL, true, false, 5, 1));
            closeButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    shell.dispose();
                }
            });

            if (userBlock != null) {
                int headSize = showUserBlockAs(0);
                I18n.bind(sizeLabel, "meta.headerSize", headSize);
            }
            else {
                userBlockDisplayChoice.setEnabled(false);
            }

            shell.pack();

            Rectangle parentBounds = openParent.getBounds();

            Point shellSize = new Point((int)(0.5 * parentBounds.width), (int)(0.5 * parentBounds.height));
            shell.setSize(shellSize);

            shell.setLocation((parentBounds.x + (parentBounds.width / 2)) - (shellSize.x / 2),
                              (parentBounds.y + (parentBounds.height / 2)) - (shellSize.y / 2));

            shell.open();

            Display openDisplay = openParent.getDisplay();
            while (!shell.isDisposed()) {
                if (!openDisplay.readAndDispatch())
                    openDisplay.sleep();
            }
        }

        private int showUserBlockAs(int radix)
        {
            if (userBlock == null)
                return 0;

            int headerSize = 0;

            String userBlockInfo = null;
            if ((radix == 2) || (radix == 8) || (radix == 16) || (radix == 10)) {
                StringBuilder sb = new StringBuilder();
                for (headerSize = 0; headerSize < userBlock.length; headerSize++) {
                    int intValue = userBlock[headerSize];
                    if (intValue < 0) {
                        intValue += 256;
                    }
                    else if (intValue == 0) {
                        break; // null end
                    }

                    sb.append(Integer.toString(intValue, radix)).append(" ");
                }
                userBlockInfo = sb.toString();
            }
            else {
                userBlockInfo = new String(userBlock).trim();
                if (userBlockInfo != null) {
                    headerSize = userBlockInfo.length();
                }
            }

            userBlockArea.setText(userBlockInfo);

            return headerSize;
        }

        private void writeUserBlock()
        {
            if (!obj.getFileFormat().isThisType(FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF5))) {
                return;
            }

            int blkSize0 = 0;
            if (userBlock != null) {
                blkSize0 = userBlock.length;
                // The super block space is allocated by offset 0, 512, 1024, 2048, etc
                if (blkSize0 > 0) {
                    int offset = 512;
                    while (offset < blkSize0) {
                        offset *= 2;
                    }
                    blkSize0 = offset;
                }
            }

            int blkSize1        = 0;
            String userBlockStr = userBlockArea.getText();
            if (userBlockStr == null) {
                if (blkSize0 <= 0) {
                    return; // nothing to write
                }
                else {
                    userBlockStr = " "; // want to wipe out old userblock content
                }
            }
            byte[] buf = null;
            buf        = userBlockStr.getBytes();

            blkSize1 = buf.length;
            if (blkSize1 <= blkSize0) {
                java.io.RandomAccessFile raf = null;
                try {
                    raf = new java.io.RandomAccessFile(obj.getFile(), "rw");
                }
                catch (Exception ex) {
                    Tools.showError(shell, I18n.text("action.save"),
                                    I18n.text("message.cannotOpenOutput", obj.getFile()));
                    return;
                }

                try {
                    raf.seek(0);
                    raf.write(buf, 0, buf.length);
                    raf.seek(buf.length);
                    if (blkSize0 > buf.length) {
                        byte[] padBuf = new byte[blkSize0 - buf.length];
                        raf.write(padBuf, 0, padBuf.length);
                    }
                }
                catch (Exception ex) {
                    log.debug("raf write:", ex);
                }
                try {
                    raf.close();
                }
                catch (Exception ex) {
                    log.debug("raf close:", ex);
                }

                Tools.showInformation(shell, I18n.text("action.save"),
                                      I18n.text("message.userBlockSaved"));
            }
            else {
                // must rewrite the whole file
                MessageDialog confirm = new MessageDialog(
                    shell, I18n.text("action.save"), null,
                    I18n.text("message.userBlockNeedsRewrite", blkSize1, blkSize0),
                    MessageDialog.QUESTION_WITH_CANCEL,
                    new String[] {I18n.text("button.yes"), I18n.text("button.no"), I18n.text("button.cancel")}, 0);
                int op = confirm.open();

                if (op == 2)
                    return;

                String fin = obj.getFile();

                String fout = fin + "~copy.h5";
                if (fin.endsWith(".h5")) {
                    fout = fin.substring(0, fin.length() - 3) + "~copy.h5";
                }
                else if (fin.endsWith(".hdf5")) {
                    fout = fin.substring(0, fin.length() - 5) + "~copy.h5";
                }

                File outFile = null;

                if (op == 1) {
                    FileDialog fChooser = new FileDialog(shell, SWT.SAVE);
                    fChooser.setFileName(fout);

                    DefaultFileFilter filter = DefaultFileFilter.getFileFilterHDF5();
                    fChooser.setFilterExtensions(new String[] {"*", filter.getExtensions()});
                    fChooser.setFilterNames(new String[] {I18n.text("common.allFiles"), filter.getDescription()});
                    fChooser.setFilterIndex(1);

                    if (fChooser.open() == null)
                        return;

                    File chosenFile = new File(fChooser.getFileName());

                    outFile = chosenFile;
                    fout    = outFile.getAbsolutePath();
                }
                else {
                    outFile = new File(fout);
                }

                if (!outFile.exists()) {
                    try {
                        if (!outFile.createNewFile())
                            log.debug("Error creating file {}", fout);
                    }
                    catch (Exception ex) {
                        Tools.showError(shell, I18n.text("action.save"),
                                        I18n.text("message.userBlockWriteFailed"));
                        return;
                    }
                }

                // close the file
                TreeView view = viewManager.getTreeView();

                try {
                    view.closeFile(view.getSelectedFile());
                }
                catch (Exception ex) {
                    log.debug("Error closing file {}", fin);
                }

                if (Tools.setHDF5UserBlock(fin, fout, buf)) {
                    if (op == 1) {
                        fin = fout; // open the new file
                    }
                    else {
                        File oldFile   = new File(fin);
                        boolean status = oldFile.delete();
                        if (status) {
                            if (!outFile.renameTo(oldFile))
                                log.debug("Error renaming file {}", fout);
                        }
                        else {
                            Tools.showError(
                                shell, I18n.text("action.save"),
                                I18n.text("message.replaceFileFailed"));
                            outFile.delete();
                        }
                    }
                }
                else {
                    Tools.showError(shell, I18n.text("action.save"),
                                    I18n.text("message.userBlockWriteFailed"));
                    outFile.delete();
                }

                // reopen the file
                shell.dispose();

                try {
                    int accessMode = FileFormat.WRITE;
                    if (ViewProperties.isReadOnly())
                        accessMode = FileFormat.READ;
                    else if (ViewProperties.isReadSWMR())
                        accessMode = FileFormat.READ | FileFormat.MULTIREAD;
                    view.openFile(fin, accessMode);
                }
                catch (Exception ex) {
                    log.debug("Error opening file {}", fin);
                }
            }
        }
    }
}
