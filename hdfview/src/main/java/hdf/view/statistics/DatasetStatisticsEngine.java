/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * All rights reserved.                                                       *
 *****************************************************************************/

package hdf.view.statistics;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import hdf.object.Dataset;
import hdf.object.Datatype;
import hdf.object.FileFormat;
import hdf.view.search.DatasetSearchEngine;
import hdf.view.search.DatasetSearchSnapshot;

/**
 * Bounded statistics for the numeric and boolean values displayed by HDFView.
 *
 * <p>The count comparisons intentionally mirror the NumPy comparisons used by
 * the original Python viewer. In particular, NaN is non-zero but is neither
 * positive nor negative, and positive/negative are strict comparisons. The
 * legacy min/max/mean/standard-deviation values use the existing HDFView fill
 * value convention: fill values are skipped by the legacy reduction loops but
 * remain part of the six classification counts. The first-value seed and
 * special-value handling of Tools.findMinMax() and Tools.computeStatistics()
 * are retained.</p>
 */
public final class DatasetStatisticsEngine {
    /** Keep every native read bounded to the same budget as the old viewer. */
    public static final long STATISTICS_BLOCK_ELEMENTS = 65536L;

    /** Statistics ranges exposed by the HDFView dialog. */
    public enum Scope {
        /** The currently displayed 2D page/frame. */
        CURRENT_PAGE,
        /** Every scalar value in the Dataset. */
        ENTIRE_DATASET
    }

    /** Count classifications which can also be used for table highlighting. */
    public enum Kind {
        ZERO,
        NON_ZERO,
        POSITIVE,
        NEGATIVE,
        NON_NEGATIVE
    }

    /** Cooperative cancellation exception used by worker callers. */
    public static final class StatisticsCancelled extends Exception {
        private static final long serialVersionUID = 1L;
    }

    /** Progress callback; callers decide how to marshal it to their UI thread. */
    public interface Listener {
        void onProgress(long processed, long total, String phase);
    }

    /** Immutable data captured on the UI thread before a worker starts. */
    public static final class Request {
        private final Dataset dataset;
        private final DatasetSearchSnapshot currentPage;
        private final DatasetSearchSnapshot dirtyPage;
        private final Object fillValue;

        public Request(Dataset dataset, DatasetSearchSnapshot currentPage,
                       DatasetSearchSnapshot dirtyPage, Object fillValue)
        {
            this.dataset     = dataset;
            this.currentPage = currentPage;
            this.dirtyPage   = dirtyPage;
            this.fillValue   = copyFillValue(fillValue);
        }

        public Dataset getDataset() { return dataset; }

        public DatasetSearchSnapshot getCurrentPage() { return currentPage; }

        public DatasetSearchSnapshot getDirtyPage() { return dirtyPage; }

        public Object getFillValue() { return fillValue; }
    }

    /** Complete result shared by the dialog and table highlighter. */
    public static final class Result {
        private final Scope scope;
        private final long total;
        private final long zero;
        private final long nonZero;
        private final long positive;
        private final long negative;
        private final long nonNegative;
        private final long numericCount;
        private final double minimum;
        private final double maximum;
        private final double mean;
        private final double standardDeviation;

        private Result(Scope scope, Accumulator accumulator)
        {
            this.scope             = scope;
            this.total             = accumulator.total;
            this.zero              = accumulator.zero;
            this.nonZero           = accumulator.nonZero;
            this.positive          = accumulator.positive;
            this.negative          = accumulator.negative;
            this.nonNegative       = accumulator.nonNegative;
            this.numericCount      = accumulator.numericCount;
            this.minimum           = accumulator.minimum;
            this.maximum           = accumulator.maximum;
            this.mean              = accumulator.mean;
            this.standardDeviation = accumulator.standardDeviation;
        }

        public Scope getScope() { return scope; }

        public long getTotal() { return total; }

        public long getZero() { return zero; }

        public long getNonZero() { return nonZero; }

        public long getPositive() { return positive; }

        public long getNegative() { return negative; }

        public long getNonNegative() { return nonNegative; }

        public long getNumericCount() { return numericCount; }

        public double getMinimum() { return minimum; }

        public double getMaximum() { return maximum; }

        public double getMean() { return mean; }

        public double getStandardDeviation() { return standardDeviation; }
    }

    private interface ValueConsumer {
        void accept(Object value) throws StatisticsCancelled;
    }

    private static final class Accumulator {
        private final Object fillValue;
        private long total;
        private long zero;
        private long nonZero;
        private long positive;
        private long negative;
        private long nonNegative;
        private long numericCount;
        private double sum;
        /* Tools.findMinMax() seeds both extrema from the first raw value. */
        private double minimum = Double.MAX_VALUE;
        private double maximum = -Double.MAX_VALUE;
        private boolean extremaInitialized;
        /* Tools.computeStatistics() receives a zero-initialized avgstd array. */
        private double mean;
        private double standardDeviation;
        private double variance;

        private Accumulator(Object fillValue) { this.fillValue = fillValue; }

        private void acceptClassification(Object value) throws StatisticsCancelled
        {
            double numeric = toDouble(value);
            total++;

            if (numeric == 0.0)
                zero++;
            else
                nonZero++;
            if (numeric > 0.0)
                positive++;
            if (numeric < 0.0)
                negative++;
            if (numeric >= 0.0)
                nonNegative++;

            if (!extremaInitialized) {
                minimum           = numeric;
                maximum           = numeric;
                extremaInitialized = true;
            }

            if (isFillValue(value))
                return;

            numericCount++;
            sum += numeric;
            /* findMinMax() excludes NaN and infinities from later comparisons. */
            if (isNaNINF(numeric))
                return;
            if (numeric < minimum)
                minimum = numeric;
            if (numeric > maximum)
                maximum = numeric;
        }

        private void finishMean()
        {
            if (numericCount == 0)
                mean = fillAsDouble();
            else if (numericCount > 1)
                mean = sum / numericCount;
        }

        private void acceptVariance(Object value, double mean) throws StatisticsCancelled
        {
            if (isFillValue(value))
                return;
            double numeric = toDouble(value);
            variance += (numeric - mean) * (numeric - mean);
        }

        private void finishVariance()
        {
            if (numericCount <= 1)
                standardDeviation = 0.0;
            else
                standardDeviation = Math.sqrt(variance / (numericCount - 1));
        }

        private boolean isFillValue(Object value)
        {
            Double fill = effectiveFillValue();
            return fill != null && toDouble(value) == fill.doubleValue();
        }

        private double fillAsDouble()
        {
            Double fill = effectiveFillValue();
            return fill == null ? 0.0 : fill.doubleValue();
        }

        private Double effectiveFillValue()
        {
            if (fillValue == null || !fillValue.getClass().isArray() ||
                Array.getLength(fillValue) == 0)
                return null;
            return numericOrNull(Array.get(fillValue, 0));
        }
    }

    private DatasetStatisticsEngine() {}

    /** Compute statistics for a captured current page without native I/O. */
    public static Result computeCurrentPage(Object data, Datatype datatype, Object fillValue,
                                            AtomicBoolean cancelled)
        throws StatisticsCancelled
    {
        validateDatatype(datatype, data);
        Accumulator accumulator = new Accumulator(fillValue);
        forEachValue(data, accumulator::acceptClassification, cancelled);
        accumulator.finishMean();
        forEachValue(data, value -> accumulator.acceptVariance(value, accumulator.mean), cancelled);
        accumulator.finishVariance();
        return new Result(Scope.CURRENT_PAGE, accumulator);
    }

    /**
     * Compute the requested range. Entire Dataset uses independent Dataset
     * objects and bounded subset reads, so it never replaces the TableView's
     * current buffer.
     */
    public static Result compute(Request request, Scope scope, AtomicBoolean cancelled,
                                 Listener listener)
        throws Exception
    {
        if (request == null || request.getDataset() == null || request.getCurrentPage() == null)
            throw new IllegalArgumentException("Dataset statistics request is incomplete");
        if (scope == null)
            throw new IllegalArgumentException("Statistics scope is null");

        AtomicBoolean stop = cancelled == null ? new AtomicBoolean(false) : cancelled;
        Listener callback = listener == null ? (processed, total, phase) -> {} : listener;
        if (scope == Scope.CURRENT_PAGE)
            return computeCurrentPage(request.getCurrentPage().getData(),
                                      request.getCurrentPage().getDatatype(),
                                      request.getFillValue(), stop);

        return computeEntireDataset(request, stop, callback);
    }

    /** Return the exact comparison used by count statistics and highlighting. */
    public static boolean matches(Object value, Kind kind)
    {
        if (kind == null)
            throw new IllegalArgumentException("Statistics kind is null");
        double numeric = toDouble(value);
        switch (kind) {
        case ZERO:
            return numeric == 0.0;
        case NON_ZERO:
            return numeric != 0.0;
        case POSITIVE:
            return numeric > 0.0;
        case NEGATIVE:
            return numeric < 0.0;
        case NON_NEGATIVE:
            return numeric >= 0.0;
        default:
            throw new IllegalArgumentException("Unknown statistics kind: " + kind);
        }
    }

    /** Validate the numeric/bool boundary before a long scan starts. */
    public static void validateDatatype(Datatype datatype, Object sampleData)
    {
        Datatype scalar = scalarDatatype(datatype);
        if (scalar != null && (scalar.isInteger() || scalar.isFloat()))
            return;
        if (containsBoolean(sampleData))
            return;

        String description = scalar == null ? "Unknown datatype" : scalar.getDescription();
        throw new UnsupportedOperationException(
            "Statistics supports numeric and boolean Dataset values only (" + description + ")");
    }

    private static Result computeEntireDataset(Request request, AtomicBoolean cancelled,
                                               Listener listener)
        throws Exception
    {
        Dataset source = request.getDataset();
        Dataset scanner = null;
        try {
            scanner = newFreshDataset(source);
            long[] dims;
            Datatype datatype;
            synchronized (DatasetSearchEngine.NATIVE_IO_LOCK) {
                scanner.init();
                dims = copy(scanner.getDims());
                datatype = scanner.getDatatype();
            }
            validateDatatype(datatype, request.getCurrentPage().getData());
            if (dims == null)
                throw new IllegalStateException("Dataset dimensions are unavailable");

            List<DatasetSearchEngine.Block> blocks =
                DatasetSearchEngine.buildBlocks(dims, STATISTICS_BLOCK_ELEMENTS);
            long total = 0;
            for (DatasetSearchEngine.Block block : blocks)
                total = safeAdd(total, block.getElementCount());

            Accumulator accumulator = new Accumulator(request.getFillValue());
            scanBlocks(scanner, blocks, request.getDirtyPage(), datatype, cancelled, listener,
                       accumulator, total, false);
            accumulator.finishMean();

            scanBlocks(scanner, blocks, request.getDirtyPage(), datatype, cancelled, listener,
                       accumulator, total, true);
            accumulator.finishVariance();
            return new Result(Scope.ENTIRE_DATASET, accumulator);
        }
        finally {
            if (scanner != null) {
                try {
                    scanner.clear();
                }
                catch (RuntimeException ignored) {
                    // The native read result has already been released or closed.
                }
            }
        }
    }

    private static void scanBlocks(Dataset scanner, List<DatasetSearchEngine.Block> blocks,
                                   DatasetSearchSnapshot dirtyPage, Datatype datatype,
                                   AtomicBoolean cancelled, Listener listener,
                                   Accumulator accumulator, long total, boolean variancePass)
        throws Exception
    {
        long processed = 0;
        for (DatasetSearchEngine.Block block : blocks) {
            checkCancelled(cancelled);
            Object data = readBlock(scanner, block);
            overlayDirtyPage(data, block, dirtyPage, datatype);

            if (variancePass) {
                forEachValue(data, value -> accumulator.acceptVariance(value, accumulator.mean), cancelled);
            }
            else {
                forEachValue(data, accumulator::acceptClassification, cancelled);
            }

            processed = safeAdd(processed, block.getElementCount());
            listener.onProgress(processed, total, variancePass ? "variance" : "values");
        }
    }

    private static Object readBlock(Dataset scanner, DatasetSearchEngine.Block block) throws Exception
    {
        synchronized (DatasetSearchEngine.NATIVE_IO_LOCK) {
            long[] start  = scanner.getStartDims();
            long[] count  = scanner.getSelectedDims();
            long[] stride = scanner.getStride();
            long[] blockStart = block.getStart();
            long[] blockCount = block.getCount();
            if (start == null || count == null || stride == null ||
                start.length != blockStart.length || count.length != blockCount.length)
                throw new IllegalStateException("Dataset subset selection is unavailable");

            for (int i = 0; i < blockStart.length; i++) {
                start[i]  = blockStart[i];
                count[i]  = blockCount[i];
                stride[i] = 1;
            }
            return scanner.read();
        }
    }

    /** Merge the current dirty page into only the blocks it overlaps. */
    private static void overlayDirtyPage(Object blockData, DatasetSearchEngine.Block block,
                                         DatasetSearchSnapshot dirtyPage, Datatype datatype)
    {
        if (blockData == null || dirtyPage == null || !blockData.getClass().isArray())
            return;

        /* Current TableView scalar buffers have one Java value per Dataset cell. */
        if (valuesPerCell(datatype) != 1)
            return;

        Object dirtyData = dirtyPage.getData();
        int dirtyLength = valueLength(dirtyData);
        long[] blockStart = block.getStart();
        long[] blockCount = block.getCount();
        for (int i = 0; i < dirtyLength; i++) {
            long[] coordinate = dirtyPage.coordinateForValue(i, 1);
            int localIndex = localIndex(coordinate, blockStart, blockCount);
            if (localIndex < 0 || localIndex >= Array.getLength(blockData))
                continue;
            try {
                Array.set(blockData, localIndex, valueAt(dirtyData, i));
            }
            catch (IllegalArgumentException ignored) {
                // A display-only conversion with a different Java wrapper cannot
                // be installed in the native block; leave the disk value intact.
            }
        }
    }

    private static int localIndex(long[] coordinate, long[] start, long[] count)
    {
        if (coordinate == null || start == null || count == null ||
            coordinate.length != start.length || coordinate.length != count.length)
            return -1;
        long index = 0;
        for (int i = 0; i < coordinate.length; i++) {
            if (coordinate[i] < start[i] || coordinate[i] >= start[i] + count[i])
                return -1;
            long local = coordinate[i] - start[i];
            if (index > Integer.MAX_VALUE / Math.max(1L, count[i]))
                return -1;
            index = index * count[i] + local;
        }
        return index > Integer.MAX_VALUE ? -1 : (int)index;
    }

    private static void forEachValue(Object data, ValueConsumer consumer, AtomicBoolean cancelled)
        throws StatisticsCancelled
    {
        if (data == null)
            return;
        if (data instanceof List<?>) {
            for (Object value : (List<?>)data)
                forEachValue(value, consumer, cancelled);
            return;
        }
        if (!data.getClass().isArray()) {
            checkCancelled(cancelled);
            consumer.accept(data);
            return;
        }

        int length = Array.getLength(data);
        for (int i = 0; i < length; i++) {
            if ((i & 1023) == 0)
                checkCancelled(cancelled);
            forEachValue(Array.get(data, i), consumer, cancelled);
        }
    }

    private static int valueLength(Object data)
    {
        if (data == null)
            return 0;
        if (data instanceof List<?>)
            return ((List<?>)data).size();
        return data.getClass().isArray() ? Array.getLength(data) : 1;
    }

    private static Object valueAt(Object data, int index)
    {
        if (data == null)
            return null;
        if (data instanceof List<?>)
            return ((List<?>)data).get(index);
        return data.getClass().isArray() ? Array.get(data, index) : (index == 0 ? data : null);
    }

    private static Dataset newFreshDataset(Dataset source) throws Exception
    {
        try {
            @SuppressWarnings("unchecked")
            Constructor<? extends Dataset> constructor =
                (Constructor<? extends Dataset>)source.getClass().getConstructor(
                    FileFormat.class, String.class, String.class);
            return constructor.newInstance(source.getFileFormat(), source.getName(), source.getPath());
        }
        catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Dataset type cannot create an independent statistics object: " +
                                            source.getClass().getName(), ex);
        }
    }

    private static boolean containsBoolean(Object data)
    {
        if (data == null)
            return false;
        if (data instanceof Boolean)
            return true;
        if (data instanceof List<?>) {
            for (Object value : (List<?>)data) {
                if (containsBoolean(value))
                    return true;
            }
            return false;
        }
        if (!data.getClass().isArray())
            return false;
        for (int i = 0; i < Array.getLength(data); i++) {
            if (containsBoolean(Array.get(data, i)))
                return true;
        }
        return false;
    }

    private static double toDouble(Object value)
    {
        if (value instanceof Boolean)
            return ((Boolean)value) ? 1.0 : 0.0;
        if (value instanceof Number)
            return ((Number)value).doubleValue();
        throw new UnsupportedOperationException("Statistics value is not numeric or boolean: " +
                                                String.valueOf(value));
    }

    private static Double numericOrNull(Object value)
    {
        if (value == null)
            return null;
        if (value.getClass().isArray())
            return Array.getLength(value) == 0 ? null : numericOrNull(Array.get(value, 0));
        if (value instanceof Boolean)
            return ((Boolean)value) ? 1.0 : 0.0;
        if (value instanceof Number)
            return ((Number)value).doubleValue();
        return null;
    }

    /** Keep the legacy floating-point reduction filter in sync with Tools.isNaNINF(). */
    private static boolean isNaNINF(double value)
    {
        return Double.isNaN(value) || value == Float.NEGATIVE_INFINITY ||
               value == Float.POSITIVE_INFINITY || value == Double.NEGATIVE_INFINITY ||
               value == Double.POSITIVE_INFINITY;
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

    private static Datatype scalarDatatype(Datatype datatype)
    {
        Datatype current = datatype;
        while (current != null && current.isArray() && current.getDatatypeBase() != null)
            current = current.getDatatypeBase();
        return current;
    }

    private static void checkCancelled(AtomicBoolean cancelled) throws StatisticsCancelled
    {
        if (cancelled != null && cancelled.get())
            throw new StatisticsCancelled();
    }

    private static long[] copy(long[] value) { return value == null ? null : value.clone(); }

    private static long safeAdd(long first, long second)
    {
        if (second < 0 || Long.MAX_VALUE - first < second)
            return Long.MAX_VALUE;
        return first + second;
    }

    private static Object copyFillValue(Object value)
    {
        if (value == null || !value.getClass().isArray())
            return value;
        int length = Array.getLength(value);
        Object copy = Array.newInstance(value.getClass().getComponentType(), length);
        System.arraycopy(value, 0, copy, 0, length);
        return copy;
    }
}
