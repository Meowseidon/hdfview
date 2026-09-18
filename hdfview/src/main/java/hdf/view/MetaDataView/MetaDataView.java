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

import hdf.object.Attribute;
import hdf.object.HObject;
import hdf.view.DataView.DataView;

/**
 *
 * The metadata view interface for displaying metadata information.
 *
 * @author Peter X. Cao
 * @version 2.4 9/6/2007
 */
public interface MetaDataView extends DataView {
    /** Data key used to identify a reusable top-level metadata tab. */
    String TAB_ROLE_KEY = "hdfview.metadata.tabRole";

    /** Stable role for the Object Attribute Info tab. */
    String TAB_ROLE_ATTRIBUTE_INFO = "objectAttributeInfo";

    /** Stable role for the General Object Info tab. */
    String TAB_ROLE_GENERAL_INFO = "generalObjectInfo";

    /** Stable role for the optional inline Data Content tab. */
    String TAB_ROLE_DATA_CONTENT = "dataContent";

    /**
     * Add an attribute to a data object.
     *
     * @param obj  the attribute to add
     *
     * @return the Attribute object
     */
    Attribute addAttribute(HObject obj);

    /**
     * Delete an attribute from a data object.
     *
     * @param obj  the attribute to delte
     *
     * @return the Attribute object
     */
    Attribute deleteAttribute(HObject obj);
}
