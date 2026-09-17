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

import java.lang.reflect.Array;

import hdf.object.CompoundDS;
import hdf.object.Dataset;
import hdf.object.Datatype;
import hdf.object.HObject;
import hdf.object.ScalarDS;
import hdf.view.DataView.DataViewManager;
import hdf.view.Tools;
import hdf.view.i18n.I18n;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/**
 * The metadata view interface for displaying dataset metadata information.
 */
public class DefaultDatasetMetaDataView extends DefaultLinkMetaDataView implements MetaDataView {

    private static final Logger log = LoggerFactory.getLogger(DefaultDatasetMetaDataView.class);

    /**
     * The metadata view interface for displaying dataset metadata information.
     *
     * @param parentComposite the parent visual object
     * @param viewer          the viewer to use
     * @param theObj          the object to display the metadata info
     */
    public DefaultDatasetMetaDataView(Composite parentComposite, DataViewManager viewer, HObject theObj)
    {
        super(parentComposite, viewer, theObj);
    }

    @Override
    protected void addObjectSpecificContent()
    {

        super.addObjectSpecificContent();

        Label label;
        Text text;

        Dataset d = (Dataset)dataObject;
        if (!d.isInited()) {
            d.init();
        }

        org.eclipse.swt.widgets.Group datasetInfoGroup =
            new org.eclipse.swt.widgets.Group(generalObjectInfoPane, SWT.NONE);
        datasetInfoGroup.setFont(curFont);
        I18n.bind(datasetInfoGroup, "meta.datasetDataspaceDatatype");
        datasetInfoGroup.setLayout(new GridLayout(2, false));
        datasetInfoGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));

        /* Dataset Rank section */
        label = new Label(datasetInfoGroup, SWT.LEFT);
        label.setFont(curFont);
        I18n.bind(label, "label.noDimensions");

        text = new Text(datasetInfoGroup, SWT.SINGLE | SWT.BORDER);
        text.setEditable(false);
        text.setFont(curFont);
        if (d.isNULL())
            I18n.bind(text, "common.nullUpper");
        else if (d.isScalar())
            I18n.bind(text, "common.scalar");
        else
            I18n.bind(text, "meta.rank", d.getRank());
        text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        if (!d.isScalar() && !d.isNULL()) {
            /* Dataset dimension size section */
            label = new Label(datasetInfoGroup, SWT.LEFT);
            label.setFont(curFont);
            I18n.bind(label, "label.dimensionSize");

            // Set Dimension Size. Keep the raw dimensions so localized markers
            // can be regenerated when the user changes language.
            final long[] dims      = d.getDims();
            final long[] maxDims   = d.getMaxDims();
            final String[] dimNames = d.getDimNames();

            text = new Text(datasetInfoGroup, SWT.SINGLE | SWT.BORDER);
            text.setEditable(false);
            text.setFont(curFont);
            I18n.bindDynamic(text, () -> formatDimensions(dims, maxDims, dimNames, false));
            text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

            label = new Label(datasetInfoGroup, SWT.LEFT);
            label.setFont(curFont);
            I18n.bind(label, "label.maxDimensionSize");

            text = new Text(datasetInfoGroup, SWT.SINGLE | SWT.BORDER);
            text.setEditable(false);
            text.setFont(curFont);
            I18n.bindDynamic(text, () -> formatDimensions(dims, maxDims, dimNames, true));
            text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        }

        /* Dataset datatype section */
        label = new Label(datasetInfoGroup, SWT.LEFT);
        label.setFont(curFont);
        I18n.bind(label, "label.dataType");

        Datatype t  = d.getDatatype();
        String type = (t == null) ? null : t.getDescription();
        if (d instanceof CompoundDS && type != null) {
            if (isH4) {
                type = I18n.text("common.vdata");
            }
            else {
                /*
                 * For Compounds, Arrays of Compounds, Vlens of Compounds, etc. we want to show
                 * the fully-qualified type, minus the compound members, since we already show
                 * the Compound datatype's members in a table.
                 */
                int bracketIndex     = type.indexOf('{');
                int lastBracketIndex = type.lastIndexOf('}');
                if (bracketIndex >= 0 && lastBracketIndex >= 0) {
                    type = type.replace(type.substring(bracketIndex, lastBracketIndex + 1), "");
                }
            }
        }

        text = new Text(datasetInfoGroup, SWT.SINGLE | SWT.BORDER);
        text.setEditable(false);
        text.setFont(curFont);
        if (isH4 && d instanceof CompoundDS)
            I18n.bind(text, "common.vdata");
        else if (type == null)
            I18n.bind(text, "common.null");
        else
            I18n.bindDatatypeDescription(text, type);
        text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        /* Add a dummy label to take up some vertical space between sections */
        label = new Label(generalObjectInfoPane, SWT.LEFT);
        label.setText("");
        label.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));

        /*
         * Dataset storage layout, compression, filters, storage type, fill value, etc.
         * section
         */
        org.eclipse.swt.widgets.Group datasetLayoutGroup =
            new org.eclipse.swt.widgets.Group(generalObjectInfoPane, SWT.NONE);
        datasetLayoutGroup.setFont(curFont);
        I18n.bind(datasetLayoutGroup, "meta.miscDatasetInfo");
        datasetLayoutGroup.setLayout(new GridLayout(2, false));
        datasetLayoutGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));

        /* Dataset Storage Layout section */
        label = new Label(datasetLayoutGroup, SWT.LEFT);
        label.setFont(curFont);
        I18n.bind(label, "label.storageLayout");

        label = new Label(datasetLayoutGroup, SWT.RIGHT);
        label.setFont(curFont);
        final String storageLayout = d.getStorageLayout();
        I18n.bindDynamic(label, () -> storageLayout == null
                                         ? I18n.text("common.unknown").toUpperCase()
                                         : storageLayout);

        /* Dataset Compression section */
        label = new Label(datasetLayoutGroup, SWT.LEFT);
        label.setFont(curFont);
        I18n.bind(label, "label.compression");

        label = new Label(datasetLayoutGroup, SWT.RIGHT);
        label.setFont(curFont);
        final String compression = d.getCompression();
        I18n.bindDynamic(label, () -> compression == null
                                         ? I18n.text("common.unknown").toUpperCase()
                                         : compression);

        /* Dataset filters section */
        label = new Label(datasetLayoutGroup, SWT.LEFT);
        label.setFont(curFont);
        I18n.bind(label, "label.filters");

        label = new Label(datasetLayoutGroup, SWT.RIGHT);
        label.setFont(curFont);
        final String filters = d.getFilters();
        I18n.bindDynamic(label, () -> filters == null
                                         ? I18n.text("common.unknown").toUpperCase()
                                         : filters);

        /* Dataset extra storage information section */
        label = new Label(datasetLayoutGroup, SWT.LEFT);
        label.setFont(curFont);
        I18n.bind(label, "label.storage");

        label = new Label(datasetLayoutGroup, SWT.RIGHT);
        label.setFont(curFont);
        final String storage = d.getStorage();
        I18n.bindDynamic(label, () -> storage == null
                                         ? I18n.text("common.unknown").toUpperCase()
                                         : storage);

        /* Dataset fill value info section */
        label = new Label(datasetLayoutGroup, SWT.LEFT);
        label.setFont(curFont);
        I18n.bind(label, "label.fillValue");

        Object fillValue     = null;
        String fillValueInfo = I18n.text("common.none");
        if (d instanceof ScalarDS)
            fillValue = ((ScalarDS)d).getFillValue();
        if (fillValue != null) {
            if (fillValue.getClass().isArray()) {
                int len       = Array.getLength(fillValue);
                fillValueInfo = Array.get(fillValue, 0).toString();
                for (int i = 1; i < len; i++) {
                    fillValueInfo += ", ";
                    fillValueInfo += Array.get(fillValue, i).toString();
                }
            }
            else
                fillValueInfo = fillValue.toString();
        }

        label = new Label(datasetLayoutGroup, SWT.RIGHT);
        label.setFont(curFont);
        final Object storedFillValue = fillValue;
        I18n.bindDynamic(label, () -> formatFillValue(storedFillValue));

        /* Button to open Data Option dialog */
        Button showDataOptionButton = new Button(datasetInfoGroup, SWT.PUSH);
        showDataOptionButton.setLayoutData(new GridData(SWT.CENTER, SWT.FILL, true, false, 2, 1));
        I18n.bind(showDataOptionButton, "meta.showDataWithOptions");
        showDataOptionButton.setEnabled(!d.isNULL());
        showDataOptionButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                try {
                    viewManager.getTreeView().setDefaultDisplayMode(false);
                    viewManager.getTreeView().showDataContent(dataObject);
                }
                catch (Exception ex) {
                    display.beep();
                    Tools.showError(display.getShells()[0], I18n.text("action.select"), ex.getMessage());
                }
            }
        });

        /*
         * If this is a Compound Dataset, add a table which displays all of the members
         * in the Compound Datatype.
         */
        if (d instanceof CompoundDS) {
            log.trace("addObjectSpecificContent(): add member table for Compound Datatype Dataset");

            CompoundDS compound = (CompoundDS)d;

            int n = compound.getMemberCount();
            log.trace("addObjectSpecificContent(): number of compound members={}", n);

            // Add a dummy label to take up some vertical space between sections
            label = new Label(generalObjectInfoPane, SWT.LEFT);
            label.setText("");
            label.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));

            org.eclipse.swt.widgets.Group compoundMembersGroup =
                new org.eclipse.swt.widgets.Group(generalObjectInfoPane, SWT.NONE);
            compoundMembersGroup.setFont(curFont);
            I18n.bind(compoundMembersGroup, "meta.compoundDatasetMembers");
            compoundMembersGroup.setLayout(new FillLayout(SWT.VERTICAL));
            compoundMembersGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));

            Table memberTable = new Table(compoundMembersGroup, SWT.BORDER);
            memberTable.setLinesVisible(true);
            memberTable.setHeaderVisible(true);
            memberTable.setFont(curFont);

            final int columnCount = 3;
            for (int i = 0; i < columnCount; i++) {
                TableColumn column = new TableColumn(memberTable, SWT.NONE);
                if (i == 0)
                    I18n.bind(column, "meta.name");
                else if (i == 1)
                    I18n.bind(column, "meta.type");
                else
                    I18n.bind(column, "meta.arraySize");
                column.setMoveable(false);
            }

            if (n > 0) {
                String[][] rowData   = new String[n][3];
                final String[] names = compound.getMemberNames();
                Datatype[] types     = compound.getMemberTypes();
                int[] orders         = compound.getMemberOrders();

                for (int i = 0; i < n; i++) {
                    rowData[i][0] = new String(names[i]);

                    if (rowData[i][0].contains(CompoundDS.SEPARATOR)) {
                        rowData[i][0] = rowData[i][0].replaceAll(CompoundDS.SEPARATOR, "->");
                    }

                    int[] mDims = compound.getMemberDims(i);
                    if (mDims == null) {
                        rowData[i][2] = String.valueOf(orders[i]);

                        if (isH4 && types[i].isString()) {
                            rowData[i][2] = String.valueOf(types[i].getDatatypeSize());
                        }
                    }
                    else {
                        String mStr = String.valueOf(mDims[0]);
                        int m       = mDims.length;
                        for (int j = 1; j < m; j++) {
                            mStr += " x " + mDims[j];
                        }
                        rowData[i][2] = mStr;
                    }
                    rowData[i][1] = (types[i] == null) ? I18n.text("common.null") : types[i].getDescription();
                }

                for (int i = 0; i < rowData.length; i++) {
                    TableItem item = new TableItem(memberTable, SWT.NONE);
                    item.setFont(curFont);
                    item.setText(0, rowData[i][0]);
                    item.setText(1, rowData[i][1]);
                    item.setText(2, rowData[i][2]);
                    if (types[i] != null)
                        I18n.bindDatatypeDescription(item, 1, rowData[i][1]);
                }

                for (int i = 0; i < columnCount; i++) {
                    memberTable.getColumn(i).pack();
                }

                // set cell height for large fonts
                // int cellRowHeight = Math.max(16,
                // table.getFontMetrics(table.getFont()).getHeight());
                // table.setRowHeight(cellRowHeight);
            } //  (n > 0)

            // Prevent conflict from equal vertical grabbing
            datasetLayoutGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
        }
    }

    /** Format dataset dimensions while resolving the unlimited marker at refresh time. */
    private String formatDimensions(long[] dims, long[] maxDims, String[] dimNames, boolean maximum)
    {
        if (dims == null || (maximum && maxDims == null))
            return I18n.text("common.null");

        boolean hasDimNames = !maximum && dimNames != null && dimNames.length == dims.length;
        long[] values       = maximum ? maxDims : dims;
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0)
                value.append(" x ");

            if (maximum && values[i] < 0)
                value.append(I18n.text("common.unlimited"));
            else
                value.append(values[i]);

            if (hasDimNames)
                value.append(" (").append(dimNames[i]).append(")");
        }
        return value.toString();
    }

    /** Format a fill value while keeping the localized empty-value marker live. */
    private String formatFillValue(Object fillValue)
    {
        if (fillValue == null)
            return I18n.text("common.none");

        if (!fillValue.getClass().isArray())
            return fillValue.toString();

        int length = Array.getLength(fillValue);
        if (length == 0)
            return "";

        StringBuilder value = new StringBuilder(String.valueOf(Array.get(fillValue, 0)));
        for (int i = 1; i < length; i++)
            value.append(", ").append(Array.get(fillValue, i));
        return value.toString();
    }
}
