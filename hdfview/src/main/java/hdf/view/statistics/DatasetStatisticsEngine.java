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
import hdf.view.search.DatasetSearchOverlay;
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

    /** Stable presentation categories used when a statistics request fails. */
    public enum ErrorCode {
        REQUEST_INCOMPLETE("statistics.error.requestIncomplete"),
        SCOPE_UNAVAILABLE("statistics.error.scopeNull"),
        DIMENSIONS_UNAVAILABLE("statistics.error.dimensionsUnavailable"),
        SUBSET_UNAVAILABLE("statistics.error.subsetUnavailable"),
        SELECTION_RANK_CHANGED("statistics.error.selectionRankChanged"),
        DATASET_READER_UNAVAILABLE("statistics.error.datasetReaderUnavailable"),
        NO_DATA("statistics.noData"),
        UNSUPPORTED_DATATYPE("statistics.unsupported"),
        UNKNOWN_DATATYPE("statistics.error.unknownDatatype"),
        VALUE_NOT_NUMERIC_OR_BOOLEAN("statistics.error.valueNotNumericOrBoolean"),
        CALCULATION_FAILED("statistics.error.failed");

        private final String messageKey;

        ErrorCode(String messageKey) { this.messageKey = messageKey; }

        public String messageKey() { return messageKey; }
    }

    /** Language-independent contract consumed by the presentation layer. */
    public interface LocalizedFailure {
        String messageKey();

        Object[] messageArgs();
    }

    /** A state or native-read failure with a stable localized presentation. */
    public static class StatisticsException extends IllegalStateException
        implements LocalizedFailure
    {
        private static final long serialVersionUID = 1L;

        private final ErrorCode code;
        private final Object[] messageArgs;

        private StatisticsException(ErrorCode code, Throwable cause, Object[] messageArgs)
        {
            super(code.messageKey(), cause);
            this.code        = code;
            this.messageArgs = messageArgs == null ? new Object[0] : messageArgs.clone();
        }

        public static StatisticsException localized(ErrorCode code, Object... messageArgs)
        {
            return new StatisticsException(code, null, messageArgs);
        }

        public static StatisticsException localizedWithCause(ErrorCode code, Throwable cause,
                                                             Object... messageArgs)
        {
            return new StatisticsException(code, cause, messageArgs);
        }

        public ErrorCode code() { return code; }

        @Override
        public String messageKey() { return code.messageKey(); }

        @Override
        public Object[] messageArgs() { return messageArgs.clone(); }
    }

    /** Unsupported-value failure retained as an UnsupportedOperationException. */
    public static final class UnsupportedDatatypeException extends UnsupportedOperationException
        implements LocalizedFailure
    {
        private static final long serialVersionUID = 1L;

        private final ErrorCode code;
        private final Object[] messageArgs;

        private UnsupportedDatatypeException(ErrorCode code, Object... messageArgs)
        {
            super(code.messageKey());
            this.code        = code;
            this.messageArgs = messageArgs == null ? new Object[0] : messageArgs.clone();
        }

        private UnsupportedDatatypeException(ErrorCode code, Throwable cause,
                                             Object... messageArgs)
        {
            super(code.messageKey(), cause);
            this.code        = code;
            this.messageArgs = messageArgs == null ? new Object[0] : messageArgs.clone();
        }

        public static UnsupportedDatatypeException localized(ErrorCode code,
                                                             Object... messageArgs)
        {
            return new UnsupportedDatatypeException(code, messageArgs);
        }

        public static UnsupportedDatatypeException localizedWithCause(ErrorCode code,
                                                                      Throwable cause,
                                                                      Object... messageArgs)
        {
            return new UnsupportedDatatypeException(code, cause, messageArgs);
        }

        public ErrorCode code() { return code; }

        @Override
        public String messageKey() { return code.messageKey(); }

        @Override
        public Object[] messageArgs() { return messageArgs.clone(); }
    }

    /** Invalid caller input retains the old IllegalArgumentException contract. */
    public static final class RequestException extends IllegalArgumentException
        implements LocalizedFailure
    {
        private static final long serialVersionUID = 1L;

        private final ErrorCode code;

        private RequestException(ErrorCode code) { super(code.messageKey()); this.code = code; }

        public static RequestException localized(ErrorCode code)
        {
            return new RequestException(code);
        }

        @Override
        public String messageKey() { return code.messageKey(); }

        @Override
        public Object[] messageArgs() { return new Object[0]; }
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
            throw DatasetStatisticsEngine.RequestException.localized(
                ErrorCode.REQUEST_INCOMPLETE);
        if (scope == null)
            throw DatasetStatisticsEngine.RequestException.localized(ErrorCode.SCOPE_UNAVAILABLE);

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

        if (scalar == null)
            throw UnsupportedDatatypeException.localized(ErrorCode.UNKNOWN_DATATYPE);

        String description = scalar.getDescription();
        throw UnsupportedDatatypeException.localized(ErrorCode.UNSUPPORTED_DATATYPE, description);
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
                throw StatisticsException.localized(ErrorCode.DIMENSIONS_UNAVAILABLE);

            long total = DatasetSearchEngine.safeElementCount(dims);

            Accumulator accumulator = new Accumulator(request.getFillValue());
            DatasetSearchOverlay dirtyOverlay = new DatasetSearchOverlay(
                request.getDirtyPage() == null ? java.util.Collections.emptyList()
                                                : java.util.Collections.singletonList(request.getDirtyPage()),
                dims, STATISTICS_BLOCK_ELEMENTS);
            scanBlocks(scanner, dims, dirtyOverlay, datatype, cancelled, listener,
                       accumulator, total, false);
            accumulator.finishMean();

            scanBlocks(scanner, dims, dirtyOverlay, datatype, cancelled, listener,
                       accumulator, total, true);
            accumulator.finishVariance();
            return new Result(Scope.ENTIRE_DATASET, accumulator);
        }
        catch (StatisticsCancelled ex) {
            throw ex;
        }
        catch (StatisticsException ex) {
            throw ex;
        }
        catch (UnsupportedDatatypeException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw StatisticsException.localizedWithCause(ErrorCode.CALCULATION_FAILED, ex);
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

    private static void scanBlocks(Dataset scanner, long[] dims,
                                   DatasetSearchOverlay dirtyOverlay, Datatype datatype,
                                   AtomicBoolean cancelled, Listener listener,
                                   Accumulator accumulator, long total, boolean variancePass)
        throws Exception
    {
        long processed = 0;
        DatasetSearchEngine.BlockIterator blocks = DatasetSearchEngine.iterateBlocks(
            dims, STATISTICS_BLOCK_ELEMENTS, cancelled);
        while (true) {
            checkCancelled(cancelled);
            if (!blocks.hasNext())
                break;
            DatasetSearchEngine.Block block = blocks.next();
            Object data = readBlock(scanner, block);
            overlayDirtyPage(data, dirtyOverlay, block, datatype, cancelled);
            checkCancelled(cancelled);

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
            if (start == null || count == null || stride == null)
                throw StatisticsException.localized(ErrorCode.SUBSET_UNAVAILABLE);
            if (start.length != blockStart.length || count.length != blockCount.length)
                throw StatisticsException.localized(ErrorCode.SELECTION_RANK_CHANGED);

            for (int i = 0; i < blockStart.length; i++) {
                start[i]  = blockStart[i];
                count[i]  = blockCount[i];
                stride[i] = 1;
            }
            return scanner.read();
        }
    }

    /** Merge only the dirty values indexed for the current block. */
    private static void overlayDirtyPage(Object blockData, DatasetSearchOverlay dirtyOverlay,
                                         DatasetSearchEngine.Block block, Datatype datatype,
                                         AtomicBoolean cancelled)
    {
        if (blockData == null || dirtyOverlay == null || !blockData.getClass().isArray())
            return;

        /*
         * ArrayDataProvider exposes numeric array cells as a flat native buffer:
         * all scalar values for one Dataset cell are contiguous.  Keep the
         * Dataset coordinate at cell granularity, then address the scalar
         * offset within that cell separately.
         */
        int scalarValuesPerCell = valuesPerCell(datatype);
        if (scalarValuesPerCell <= 0 || scalarValuesPerCell == Integer.MAX_VALUE)
            return;

        int blockLength = Array.getLength(blockData);
        dirtyOverlay.forEachValue(block, scalarValuesPerCell,
                                   (snapshot, blockValueIndex, snapshotValueIndex) -> {
            if (cancelled != null && cancelled.get())
                return false;
            if (blockValueIndex < 0 || blockValueIndex >= blockLength)
                return true;
            try {
                Array.set(blockData, blockValueIndex,
                          valueAt(snapshot.getData(), snapshotValueIndex));
            }
            catch (IllegalArgumentException ignored) {
                // A display-only conversion with a different Java wrapper cannot
                // be installed in the native block; leave the disk value intact.
            }
            return true;
        });
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
            throw StatisticsException.localizedWithCause(
                ErrorCode.DATASET_READER_UNAVAILABLE, ex, source.getClass().getName());
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
        throw UnsupportedDatatypeException.localized(
            ErrorCode.VALUE_NOT_NUMERIC_OR_BOOLEAN, String.valueOf(value));
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
