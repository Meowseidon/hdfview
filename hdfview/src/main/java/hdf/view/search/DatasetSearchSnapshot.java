/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * Copyright by the Board of Trustees of the University of Illinois.         *
 * All rights reserved.                                                       *
 *****************************************************************************/

package hdf.view.search;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
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
    /**
     * Full Dataset coordinates for a sparse snapshot, flattened by cell.  A
     * null value means that {@link #data} is a normal rectangular selection.
     * Sparse snapshots are used for TableView edits so a one-cell edit does
     * not retain or scan the rest of the displayed page.
     */
    private final long[] sparseCoordinates;

    public DatasetSearchSnapshot(String filePath, String datasetPath, Object data,
                                 long[] start, long[] count, long[] stride,
                                 long[] datasetDims, Datatype datatype)
    {
        this(filePath, datasetPath, data, start, count, stride, datasetDims, datatype,
             null, true);
    }

    private DatasetSearchSnapshot(String filePath, String datasetPath, Object data,
                                  long[] start, long[] count, long[] stride,
                                  long[] datasetDims, Datatype datatype,
                                  long[] sparseCoordinates, boolean copyData)
    {
        this.filePath    = filePath == null ? "" : filePath;
        this.datasetPath = datasetPath == null ? "" : datasetPath;
        this.data        = copyData ? copyValue(data) : data;
        this.start       = copy(start);
        this.count       = copy(count);
        this.stride      = copy(stride);
        this.datasetDims = copy(datasetDims);
        this.datatype    = datatype;
        this.sparseCoordinates = copy(sparseCoordinates);
    }

    /**
     * Build a snapshot containing only the changed Dataset cells in a TableView
     * buffer.  {@code changedValueIndices} are flat scalar indices in the
     * displayed buffer; array datatype members in the same cell are packed
     * together so the existing array overlay semantics remain unchanged.
     *
     * <p>The copied sparse values are indexed by their full Dataset
     * coordinates.  The original selection metadata is retained for
     * diagnostics and for the coordinate mapping used by dense snapshots.</p>
     */
    public static DatasetSearchSnapshot fromChangedValues(
        String filePath, String datasetPath, Object sourceData,
        int[] changedValueIndices, long[] start, long[] count, long[] stride,
        long[] datasetDims, Datatype datatype)
    {
        if (sourceData == null || changedValueIndices == null || changedValueIndices.length == 0)
            return null;

        int valuesPerCell = valuesPerCell(datatype);
        if (valuesPerCell <= 0 || valuesPerCell == Integer.MAX_VALUE)
            return null;

        int sourceLength = valueLength(sourceData);
        if (sourceLength <= 0)
            return null;

        long selectedCells = elementCount(count);
        if (selectedCells <= 0 || selectedCells == Long.MAX_VALUE)
            return null;

        int[] sorted = changedValueIndices.clone();
        Arrays.sort(sorted);
        List<Long> localCells = new ArrayList<>();
        long previousCell = -1;
        for (int valueIndex : sorted) {
            if (valueIndex < 0 || valueIndex >= sourceLength)
                continue;
            long cell = valueIndex / (long)valuesPerCell;
            if (cell >= selectedCells || cell == previousCell)
                continue;
            previousCell = cell;
            localCells.add(cell);
        }
        if (localCells.isEmpty())
            return null;

        int rank = count == null ? 0 : count.length;
        long[] sparseCoordinates = new long[Math.multiplyExact(localCells.size(), rank)];
        List<Long> validCells = new ArrayList<>(localCells.size());
        int validCoordinateOffset = 0;
        for (Long localCell : localCells) {
            long[] coordinate = coordinateForCell(localCell.longValue(), start, count, stride,
                                                  datasetDims);
            if (coordinate == null)
                continue;
            System.arraycopy(coordinate, 0, sparseCoordinates, validCoordinateOffset, rank);
            validCoordinateOffset += rank;
            validCells.add(localCell);
        }
        if (validCells.isEmpty())
            return null;
        if (validCoordinateOffset != sparseCoordinates.length)
            sparseCoordinates = Arrays.copyOf(sparseCoordinates, validCoordinateOffset);

        Object packed = copyCells(sourceData, validCells, valuesPerCell);
        if (packed == null)
            return null;

        return new DatasetSearchSnapshot(filePath, datasetPath, packed, start, count, stride,
                                         datasetDims, datatype, sparseCoordinates, false);
    }

    public String getFilePath() { return filePath; }

    public String getDatasetPath() { return datasetPath; }

    public Object getData() { return data; }

    public long[] getStart() { return copy(start); }

    public long[] getCount() { return copy(count); }

    public long[] getStride() { return copy(stride); }

    public long[] getDatasetDims() { return copy(datasetDims); }

    public Datatype getDatatype() { return datatype; }

    /** Return whether this snapshot contains only explicitly changed cells. */
    public boolean isSparse() { return sparseCoordinates != null; }

    /** Return the number of Dataset cells represented by this view buffer. */
    public long getElementCount()
    {
        if (sparseCoordinates != null) {
            int rank = count == null ? 0 : count.length;
            return rank == 0 ? 1 : sparseCoordinates.length / rank;
        }

        return elementCount(count);
    }

    private static long elementCount(long[] values)
    {
        if (values == null || values.length == 0)
            return 1;

        long result = 1;
        for (long value : values) {
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
        if (sparseCoordinates != null) {
            int cellIndex = Math.max(0, valueIndex) / Math.max(1, valuesPerCell);
            int rank = count == null ? 0 : count.length;
            long[] coordinate = new long[rank];
            int offset = cellIndex * rank;
            if (offset < 0 || offset + rank > sparseCoordinates.length)
                return new long[0];
            System.arraycopy(sparseCoordinates, offset, coordinate, 0, rank);
            return coordinate;
        }

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

    /** Package-private overlay accessors avoid copying the sparse index. */
    int getValuesPerCell() { return valuesPerCell(datatype); }

    int getValueCount() { return valueLength(data); }

    int getSparseCellCount()
    {
        if (sparseCoordinates == null)
            return 0;
        int rank = count == null ? 0 : count.length;
        return rank == 0 ? 1 : sparseCoordinates.length / rank;
    }

    long sparseCoordinateAt(int cellIndex, int dimension)
    {
        int rank = count == null ? 0 : count.length;
        if (sparseCoordinates == null || cellIndex < 0 || cellIndex >= getSparseCellCount() ||
            dimension < 0 || dimension >= rank)
            return Long.MIN_VALUE;
        return sparseCoordinates[cellIndex * rank + dimension];
    }

    /** Iterate only the values in a dense selection that intersect one block. */
    long forEachDenseValueInBlock(long[] blockStart, long[] blockCount, int valuesPerCell,
                                  OverlayValueVisitor visitor)
    {
        if (visitor == null || sparseCoordinates != null)
            return 0;

        int rank = count == null ? 0 : count.length;
        if (rank == 0) {
            if (blockStart == null || blockCount == null || blockStart.length != 0 ||
                blockCount.length != 0)
                return 0;
            int valueCount = Math.min(Math.max(1, valuesPerCell), getValueCount());
            long visited = 0;
            for (int valueIndex = 0; valueIndex < valueCount; valueIndex++) {
                if (!visitor.visit(valueIndex, valueIndex))
                    return visited;
                visited++;
            }
            return visited;
        }

        if (blockStart == null || blockCount == null || start == null || stride == null ||
            count == null || blockStart.length != rank || blockCount.length != rank ||
            start.length != rank || stride.length != rank)
            return 0;

        long[] firstPosition = new long[rank];
        long[] lastPosition = new long[rank];
        long[] snapshotFlatStride = flatStrides(count);
        long[] blockFlatStride = flatStrides(blockCount);
        if (snapshotFlatStride == null || blockFlatStride == null)
            return 0;

        for (int dimension = 0; dimension < rank; dimension++) {
            long selectedCount = count[dimension];
            long selectedStart = start[dimension];
            long selectedStride = stride[dimension];
            long currentBlockStart = blockStart[dimension];
            long currentBlockCount = blockCount[dimension];
            if (selectedCount <= 0 || selectedStart < 0 || selectedStride <= 0 ||
                currentBlockStart < 0 || currentBlockCount <= 0)
                return 0;

            long datasetLimit = Long.MAX_VALUE;
            if (datasetDims != null) {
                if (datasetDims.length != rank || datasetDims[dimension] <= 0)
                    return 0;
                datasetLimit = datasetDims[dimension] - 1;
            }
            if (selectedStart > datasetLimit)
                return 0;

            long blockEnd = safeAdd(currentBlockStart, currentBlockCount);
            if (blockEnd == Long.MAX_VALUE && currentBlockStart != Long.MAX_VALUE)
                return 0;
            long upperCoordinate = Math.min(datasetLimit, blockEnd - 1);
            if (upperCoordinate < selectedStart)
                return 0;

            long minimumPosition = ceilDiv(Math.max(0, currentBlockStart - selectedStart), selectedStride);
            long maximumPosition = (upperCoordinate - selectedStart) / selectedStride;
            minimumPosition = Math.max(0, minimumPosition);
            maximumPosition = Math.min(selectedCount - 1, maximumPosition);
            if (minimumPosition > maximumPosition)
                return 0;

            firstPosition[dimension] = minimumPosition;
            lastPosition[dimension] = maximumPosition;
        }

        long snapshotCellIndex = 0;
        long blockCellIndex = 0;
        long[] incrementSnapshot = new long[rank];
        long[] incrementBlock = new long[rank];
        for (int dimension = 0; dimension < rank; dimension++) {
            snapshotCellIndex = safeMultiplyAdd(snapshotCellIndex, firstPosition[dimension],
                                                snapshotFlatStride[dimension]);
            long coordinate = safeAdd(start[dimension],
                                      safeMultiply(firstPosition[dimension], stride[dimension]));
            if (coordinate == Long.MAX_VALUE && start[dimension] != Long.MAX_VALUE)
                return 0;
            long local = coordinate - blockStart[dimension];
            blockCellIndex = safeMultiplyAdd(blockCellIndex, local, blockFlatStride[dimension]);
            incrementSnapshot[dimension] = snapshotFlatStride[dimension];
            incrementBlock[dimension] = safeMultiply(stride[dimension], blockFlatStride[dimension]);
        }

        int valueCount = getValueCount();
        int safeValuesPerCell = Math.max(1, valuesPerCell);
        long visited = 0;
        long[] position = firstPosition.clone();
        while (true) {
            long snapshotValueBase = safeMultiply(snapshotCellIndex, safeValuesPerCell);
            long blockValueBase = safeMultiply(blockCellIndex, safeValuesPerCell);
            if (snapshotValueBase >= 0 && snapshotValueBase < valueCount &&
                blockValueBase >= 0 && snapshotValueBase <= Integer.MAX_VALUE &&
                blockValueBase <= Integer.MAX_VALUE) {
                int available = (int)Math.min((long)safeValuesPerCell,
                                              valueCount - snapshotValueBase);
                for (int scalar = 0; scalar < available; scalar++) {
                    if (!visitor.visit((int)blockValueBase + scalar,
                                       (int)snapshotValueBase + scalar))
                        return visited;
                    visited++;
                }
            }

            int dimension = rank - 1;
            while (dimension >= 0) {
                if (position[dimension] < lastPosition[dimension]) {
                    position[dimension]++;
                    snapshotCellIndex += incrementSnapshot[dimension];
                    blockCellIndex += incrementBlock[dimension];
                    break;
                }

                snapshotCellIndex -= (lastPosition[dimension] - firstPosition[dimension]) *
                                     incrementSnapshot[dimension];
                blockCellIndex -= (lastPosition[dimension] - firstPosition[dimension]) *
                                  incrementBlock[dimension];
                position[dimension] = firstPosition[dimension];
                dimension--;
            }
            if (dimension < 0)
                break;
        }
        return visited;
    }

    @FunctionalInterface
    interface OverlayValueVisitor
    {
        boolean visit(int blockValueIndex, int snapshotValueIndex);
    }

    private static long[] flatStrides(long[] dimensions)
    {
        if (dimensions == null)
            return null;
        long[] result = new long[dimensions.length];
        long strideValue = 1;
        for (int dimension = dimensions.length - 1; dimension >= 0; dimension--) {
            if (dimensions[dimension] <= 0 || strideValue > Long.MAX_VALUE / dimensions[dimension])
                return null;
            result[dimension] = strideValue;
            strideValue *= dimensions[dimension];
        }
        return result;
    }

    private static long ceilDiv(long numerator, long denominator)
    {
        if (numerator <= 0)
            return 0;
        long quotient = numerator / denominator;
        return numerator % denominator == 0 ? quotient : quotient + 1;
    }

    private static long safeMultiply(long first, long second)
    {
        if (first < 0 || second < 0 || first == 0 || second == 0)
            return first == 0 || second == 0 ? 0 : Long.MAX_VALUE;
        if (first > Long.MAX_VALUE / second)
            return Long.MAX_VALUE;
        return first * second;
    }

    private static long safeMultiplyAdd(long first, long value, long factor)
    {
        long product = safeMultiply(value, factor);
        return safeAdd(first, product);
    }

    private static long safeAdd(long first, long second)
    {
        if (first < 0 || second < 0 || Long.MAX_VALUE - first < second)
            return Long.MAX_VALUE;
        return first + second;
    }

    private static long[] coordinateForCell(long cellIndex, long[] start, long[] count,
                                            long[] stride, long[] datasetDims)
    {
        int rank = count == null ? 0 : count.length;
        if (rank == 0)
            return new long[0];
        if (start == null || stride == null || start.length != rank || stride.length != rank ||
            (datasetDims != null && datasetDims.length != rank))
            return null;

        long[] coordinate = new long[rank];
        long remaining = cellIndex;
        for (int dimension = rank - 1; dimension >= 0; dimension--) {
            long dimensionCount = count[dimension];
            if (dimensionCount <= 0)
                return null;
            long position = remaining % dimensionCount;
            remaining /= dimensionCount;
            if (start[dimension] < 0 || stride[dimension] <= 0 ||
                position > (Long.MAX_VALUE - start[dimension]) / stride[dimension])
                return null;
            coordinate[dimension] = start[dimension] + position * stride[dimension];
            if (datasetDims != null &&
                (datasetDims[dimension] <= 0 || coordinate[dimension] >= datasetDims[dimension]))
                return null;
        }
        return coordinate;
    }

    private static Object copyCells(Object sourceData, List<Long> localCells, int valuesPerCell)
    {
        long packedLength = Math.multiplyExact((long)localCells.size(), valuesPerCell);
        if (packedLength > Integer.MAX_VALUE)
            return null;

        if (sourceData instanceof List<?>) {
            List<Object> result = new ArrayList<>((int)packedLength);
            for (Long cell : localCells) {
                for (int scalar = 0; scalar < valuesPerCell; scalar++) {
                    long sourceIndex = cell.longValue() * valuesPerCell + scalar;
                    result.add(copyValue(valueAt(sourceData, sourceIndex)));
                }
            }
            return result;
        }
        if (sourceData != null && sourceData.getClass().isArray()) {
            Object result = Array.newInstance(sourceData.getClass().getComponentType(), (int)packedLength);
            for (int cellIndex = 0; cellIndex < localCells.size(); cellIndex++) {
                long cell = localCells.get(cellIndex).longValue();
                for (int scalar = 0; scalar < valuesPerCell; scalar++) {
                    long sourceIndex = cell * valuesPerCell + scalar;
                    Array.set(result, cellIndex * valuesPerCell + scalar,
                              copyValue(valueAt(sourceData, sourceIndex)));
                }
            }
            return result;
        }
        return localCells.size() == 1 ? copyValue(sourceData) : null;
    }

    private static int valueLength(Object value)
    {
        if (value == null)
            return 0;
        if (value instanceof List<?>)
            return ((List<?>)value).size();
        return value.getClass().isArray() ? Array.getLength(value) : 1;
    }

    private static Object valueAt(Object value, long index)
    {
        if (value instanceof List<?>)
            return index >= 0 && index < ((List<?>)value).size()
                ? ((List<?>)value).get((int)index) : null;
        if (value != null && value.getClass().isArray() && index >= 0 && index < Array.getLength(value))
            return Array.get(value, (int)index);
        return index == 0 ? value : null;
    }

    private static int valuesPerCell(Datatype datatype)
    {
        int result = 1;
        Datatype current = datatype;
        while (current != null && current.isArray() && current.getDatatypeBase() != null) {
            long[] dims = current.getArrayDims();
            if (dims != null) {
                for (long dim : dims) {
                    if (dim <= 0 || result > Integer.MAX_VALUE / dim)
                        return Integer.MAX_VALUE;
                    result *= (int)dim;
                }
            }
            current = current.getDatatypeBase();
        }
        return Math.max(1, result);
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
