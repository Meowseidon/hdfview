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

package hdf.view.TableView;

import hdf.view.DataView.DataView;

/**
 *
 * The table view interface for displaying data in table form.
 *
 * @author Peter X. Cao
 * @version 2.4 9/6/2007
 */
public interface TableView extends DataView {
    /**
     * Get the table.
     *
     * @return the table
     */
    Object getTable();

    /**
     * Get the array of selected data.
     *
     * @return array of selected data
     */
    Object getSelectedData();

    /**
     * Get the array of selected column count.
     *
     * @return array of selected column count
     */
    int getSelectedColumnCount();

    /**
     * Get the array of selected row count.
     *
     * @return array of selected row count
     */
    int getSelectedRowCount();

    /**
     * Write the change of a dataset into file.
     */
    void updateValueInFile();

    /**
     * Commit an editor which is still active before the TableView controls are
     * disposed. The default is a no-op so existing TableView implementations
     * remain source-compatible.
     */
    default void commitActiveCellEditor() {}

    /**
     * refresh the data table.
     */
    void refreshDataTable();

    /**
     * Dispose the view and release its data and GUI resources.
     *
     * <p>The default implementation keeps existing third-party TableView
     * implementations source-compatible. The built-in TableView uses this
     * hook for both top-level windows and embedded views.</p>
     */
    default void disposeView() {}

    /**
     * Return whether this view has already released its resources.
     *
     * @return {@code true} when the view is no longer usable
     */
    default boolean isViewDisposed() { return false; }
}
