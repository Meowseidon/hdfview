/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * Copyright by the Board of Trustees of the University of Illinois.         *
 * All rights reserved.                                                       *
 *****************************************************************************/

package hdf.view.search;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import hdf.object.Datatype;

/**
 * Short-lived snapshot of the currently displayed dirty TableView buffer.
 *
 * <p>This is not a second application cache: it is copied once when a search
 * starts, then discarded with the search.  It lets content search retain the
 * old hdfviewer behavior of seeing an active Dataset's unsaved buffer without
 * saving, clearing, or replacing that buffer.</p>
 */
public final class DatasetSearchSnapshot {
    private final String filePath;
    private final String datasetPath;
    private final Object data;
    private final long[] start;
    private final long[] count;
    private final long[] stride;
    private final long[] datasetDims;
    private final Datatype datatype;

    public DatasetSearchSnapshot(String filePath, String datasetPath, Object data,
                                 long[] start, long[] count, long[] stride,
                                 long[] datasetDims, Datatype datatype)
    {
        this.filePath    = filePath == null ? "" : filePath;
        this.datasetPath = datasetPath == null ? "" : datasetPath;
        this.data        = copyValue(data);
        this.start       = copy(start);
        this.count       = copy(count);
        this.stride      = copy(stride);
        this.datasetDims = copy(datasetDims);
        this.datatype    = datatype;
    }

    public String getFilePath() { return filePath; }

    public String getDatasetPath() { return datasetPath; }

    public Object getData() { return data; }

    public long[] getStart() { return copy(start); }

    public long[] getCount() { return copy(count); }

    public long[] getStride() { return copy(stride); }

    public long[] getDatasetDims() { return copy(datasetDims); }

    public Datatype getDatatype() { return datatype; }

    /** Return the number of Dataset cells represented by this view buffer. */
    public long getElementCount()
    {
        if (count == null || count.length == 0)
            return 1;

        long result = 1;
        for (long value : count) {
            if (value <= 0 || Long.MAX_VALUE / value < result)
                return Long.MAX_VALUE;
            result *= value;
        }
        return result;
    }

    /**
     * Map a flat value position in the displayed buffer to the full Dataset
     * coordinate.  Array datatypes can expose several scalar values for one
     * Dataset cell; those values intentionally share the cell coordinate.
     */
    public long[] coordinateForValue(int valueIndex, int valuesPerCell)
    {
        if (count == null || count.length == 0)
            return new long[0];

        long cellIndex = Math.max(0, valueIndex) / Math.max(1, valuesPerCell);
        long[] coordinate = new long[count.length];
        for (int i = count.length - 1; i >= 0; i--) {
            long dimension = count[i];
            if (dimension <= 0)
                return new long[0];
            coordinate[i] = cellIndex % dimension;
            cellIndex /= dimension;
            long base = start != null && i < start.length ? start[i] : 0;
            long step = stride != null && i < stride.length ? stride[i] : 1;
            coordinate[i] = base + coordinate[i] * step;
        }
        return coordinate;
    }

    /** Match by stable file and Dataset identities, not displayed text. */
    public boolean matches(String candidateFilePath, String candidateDatasetPath)
    {
        return sameFilePath(filePath, candidateFilePath) && datasetPath.equals(candidateDatasetPath);
    }

    private static boolean sameFilePath(String first, String second)
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

    private static long[] copy(long[] values) { return values == null ? null : values.clone(); }

    /** Copy primitive/object arrays and the ArrayList form used by compounds. */
    private static Object copyValue(Object value)
    {
        if (value == null)
            return null;
        if (value instanceof List<?>) {
            List<Object> copy = new ArrayList<>(((List<?>)value).size());
            for (Object item : (List<?>)value)
                copy.add(copyValue(item));
            return copy;
        }
        if (!value.getClass().isArray())
            return value;

        int length = Array.getLength(value);
        Object copy = Array.newInstance(value.getClass().getComponentType(), length);
        if (value.getClass().getComponentType().isPrimitive()) {
            System.arraycopy(value, 0, copy, 0, length);
            return copy;
        }
        for (int i = 0; i < length; i++)
            Array.set(copy, i, copyValue(Array.get(value, i)));
        return copy;
    }
}
