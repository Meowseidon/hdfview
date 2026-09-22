/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * Copyright by the Board of Trustees of the University of Illinois.         *
 * All rights reserved.                                                       *
 *****************************************************************************/

package hdf.view.search;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import hdf.object.Dataset;
import hdf.object.Datatype;
import hdf.object.FileFormat;
import hdf.object.Group;
import hdf.object.HObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background-friendly search engine for Dataset names and Dataset values.
 *
 * <p>The engine deliberately lives in the GUI module.  It uses the existing
 * Object API subset contract ({@code start}, {@code stride}, and
 * {@code selected}) and does not add another data cache or alter the object
 * module.  Each scan creates a fresh Dataset object attached to the already
 * open FileFormat.  This is important: reading a block must not overwrite the
 * buffer belonging to the Dataset currently shown in a TableView.</p>
 */
public final class DatasetSearchEngine {
    private static final Logger log = LoggerFactory.getLogger(DatasetSearchEngine.class);

    /** Bound the maximum number of values handed to one native read call. */
    public static final long SEARCH_BLOCK_ELEMENTS = 65536L;

    /** Bound the number of result rows retained by the result window. */
    public static final int MAX_SHOWN_RESULTS = 5000;

    private static final int RESULT_BATCH_SIZE = 100;

    /**
     * Native HDF operations used by a search are serialized through this lock.
     * HDFView never starts a second search concurrently, and close/navigation
     * paths use the same lock for the short native operation that invalidates a
     * search target.
     */
    public static final Object NATIVE_IO_LOCK = new Object();

    /** Search modes exposed by the Dataset search window. */
    public enum SearchMode {
        /** Search Dataset names case-insensitively by substring. */
        DATASET_NAME,
        /** Search numeric values exactly and string/char values by substring. */
        DATA_VALUE
    }

    /** Listener called by the worker thread; SWT callers must marshal updates. */
    public interface Listener {
        default void onResults(List<DatasetSearchResult> results) {}

        default void onProgress(long processedElements, long totalElements, String datasetPath) {}

        default void onError(String datasetPath, String message, Throwable error) {}
    }

    /** Immutable search summary returned after the scan stops or completes. */
    public static final class SearchSummary {
        private final long totalMatches;
        private final int shownMatches;
        private final long totalElements;
        private final long processedElements;
        private final List<String> errors;
        private final boolean cancelled;

        private SearchSummary(long totalMatches, int shownMatches, long totalElements,
                              long processedElements, List<String> errors, boolean cancelled)
        {
            this.totalMatches     = totalMatches;
            this.shownMatches     = shownMatches;
            this.totalElements    = totalElements;
            this.processedElements = processedElements;
            this.errors           = Collections.unmodifiableList(new ArrayList<>(errors));
            this.cancelled        = cancelled;
        }

        public long getTotalMatches() { return totalMatches; }

        public int getShownMatches() { return shownMatches; }

        public long getTotalElements() { return totalElements; }

        public long getProcessedElements() { return processedElements; }

        public List<String> getErrors() { return errors; }

        public boolean isCancelled() { return cancelled; }
    }

    /** A bounded rectangular selection passed to one Dataset.read() call. */
    public static final class Block {
        private final long[] start;
        private final long[] count;

        private Block(long[] start, long[] count)
        {
            this.start = start.clone();
            this.count = count.clone();
        }

        public long[] getStart() { return start.clone(); }

        public long[] getCount() { return count.clone(); }

        public long getElementCount()
        {
            long result = 1;
            for (long value : count)
                result = Math.multiplyExact(result, value);
            return result;
        }
    }

    /**
     * Lazy row-major iterator over bounded Dataset blocks.
     *
     * <p>The iterator keeps only the Dataset shape, the chosen tile shape, and
     * the current tile origin.  Calling {@link #hasNext()} never constructs a
     * block; one {@link Block} is created only by the corresponding
     * {@link #next()} call.  This is intentionally a small custom iterator so
     * a cancellation flag can stop block generation before the next block is
     * materialized.</p>
     */
    public static final class BlockIterator implements Iterator<Block> {
        private final long[] dims;
        private final long[] tile;
        private final long[] start;
        private final AtomicBoolean cancelled;
        private boolean hasNext;
        private long generatedBlockCount;

        private BlockIterator(long[] dims, long maxElements, AtomicBoolean cancelled)
        {
            this.cancelled = cancelled;
            if (dims == null) {
                this.dims = null;
                this.tile = null;
                this.start = null;
                this.hasNext = false;
                return;
            }

            this.dims = dims.clone();
            if (this.dims.length == 0) {
                this.tile = new long[0];
                this.start = new long[0];
                this.hasNext = true;
                return;
            }
            if (maxElements <= 0)
                throw new IllegalArgumentException("maxElements must be positive");

            for (long dim : this.dims) {
                if (dim < 0)
                    throw new IllegalArgumentException("Dataset dimensions cannot be negative");
            }

            boolean empty = false;
            for (long dim : this.dims) {
                if (dim == 0) {
                    empty = true;
                    break;
                }
            }

            this.tile = chooseTile(this.dims, maxElements);
            this.start = new long[this.dims.length];
            this.hasNext = !empty;
        }

        @Override
        public boolean hasNext()
        {
            if (cancelled != null && cancelled.get()) {
                hasNext = false;
                return false;
            }
            return hasNext;
        }

        @Override
        public Block next()
        {
            if (!hasNext())
                throw new NoSuchElementException();

            long[] count = new long[dims.length];
            for (int i = 0; i < dims.length; i++)
                count[i] = Math.min(tile[i], dims[i] - start[i]);

            Block block = new Block(start, count);
            if (generatedBlockCount < Long.MAX_VALUE)
                generatedBlockCount++;
            advance();
            return block;
        }

        /** Number of blocks whose objects have actually been created. */
        public long getGeneratedBlockCount() { return generatedBlockCount; }

        private void advance()
        {
            if (dims.length == 0) {
                hasNext = false;
                return;
            }

            for (int dimension = dims.length - 1; dimension >= 0; dimension--) {
                long limit = dims[dimension];
                long step = tile[dimension];
                if (start[dimension] < limit - step) {
                    start[dimension] += step;
                    return;
                }
                start[dimension] = 0;
            }
            hasNext = false;
        }
    }

    private static final class DatasetTarget {
        private final FileFormat file;
        private final Dataset dataset;

        private DatasetTarget(FileFormat file, Dataset dataset)
        {
            this.file    = file;
            this.dataset = dataset;
        }
    }

    private static final class RunState {
        private final Listener listener;
        private final List<DatasetSearchResult> batch = new ArrayList<>(RESULT_BATCH_SIZE);
        private final List<String> errors = new ArrayList<>();
        private long totalMatches;
        private int shownMatches;
        private long totalElements;
        private long processedElements;

        private RunState(Listener listener) { this.listener = listener; }

        private void emit(DatasetSearchResult result)
        {
            totalMatches++;
            if (shownMatches >= MAX_SHOWN_RESULTS)
                return;

            shownMatches++;
            batch.add(result);
            if (batch.size() >= RESULT_BATCH_SIZE)
                flush();
        }

        private void flush()
        {
            if (batch.isEmpty())
                return;

            List<DatasetSearchResult> copy = new ArrayList<>(batch);
            batch.clear();
            try {
                listener.onResults(copy);
            }
            catch (RuntimeException ex) {
                log.debug("Dataset search result listener failed", ex);
            }
        }

        private void progress(String datasetPath)
        {
            try {
                listener.onProgress(processedElements, totalElements, datasetPath);
            }
            catch (RuntimeException ex) {
                log.debug("Dataset search progress listener failed", ex);
            }
        }

        private void error(String datasetPath, DatasetSearchException failure)
        {
            String message = failure.technicalDetail();
            Object[] messageArgs = failure.messageArgs();
            if (message.isEmpty() && messageArgs.length > 0)
                message = String.valueOf(messageArgs[0]);
            if (message.isEmpty())
                message = failure.messageKey();

            String text = datasetPath + ": " + message;
            errors.add(text);
            log.warn("Dataset search failed for {} ({})", datasetPath, failure.messageKey(), failure);
            try {
                listener.onError(datasetPath, message, failure);
            }
            catch (RuntimeException ex) {
                log.debug("Dataset search error listener failed", ex);
            }
        }
    }

    /**
     * Search every Dataset reachable from the supplied open files.
     *
     * @param files       snapshot of the currently open files
     * @param query       user query; an empty query produces an empty summary
     * @param mode        name or value mode
     * @param cancelled   cooperative cancellation flag
     * @param listener    worker-thread callbacks; may be null
     * @return summary of all matches, displayed matches, errors, and progress
     */
    public SearchSummary search(List<FileFormat> files, String query, SearchMode mode,
                                AtomicBoolean cancelled, Listener listener)
    {
        return search(files, query, mode, cancelled, listener, Collections.emptyList());
    }

    /**
     * Search with transient dirty-buffer snapshots captured by existing
     * TableViews.  Matching snapshots overlay only their selected coordinates
     * on the bounded on-disk reads; they are never written back by the search.
     */
    public SearchSummary search(List<FileFormat> files, String query, SearchMode mode,
                                AtomicBoolean cancelled, Listener listener,
                                List<DatasetSearchSnapshot> dirtySnapshots)
    {
        Listener callback = listener == null ? new Listener() {} : listener;
        AtomicBoolean stop = cancelled == null ? new AtomicBoolean(false) : cancelled;
        RunState state = new RunState(callback);

        String phrase = query == null ? "" : query.trim();
        if (phrase.isEmpty() || mode == null)
            return finish(state, stop);

        List<DatasetTarget> targets = collectDatasets(files, state, stop);
        if (mode == SearchMode.DATASET_NAME) {
            searchNames(targets, phrase, state, stop);
        }
        else {
            searchValues(targets, phrase, state, stop, dirtySnapshots);
        }

        state.flush();
        return finish(state, stop);
    }

    private SearchSummary finish(RunState state, AtomicBoolean cancelled)
    {
        state.flush();
        return new SearchSummary(state.totalMatches, state.shownMatches, state.totalElements,
                                 state.processedElements, state.errors, cancelled.get());
    }

    private List<DatasetTarget> collectDatasets(List<FileFormat> files, RunState state,
                                                AtomicBoolean cancelled)
    {
        if (files == null || files.isEmpty())
            return Collections.emptyList();

        List<DatasetTarget> targets = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (FileFormat file : files) {
            if (cancelled.get() || file == null)
                break;

            try {
                synchronized (NATIVE_IO_LOCK) {
                    HObject root = file.getRootObject();
                    if (!(root instanceof Group))
                        continue;

                    for (HObject object : ((Group)root).depthFirstMemberList()) {
                        if (cancelled.get())
                            break;
                        if (!(object instanceof Dataset))
                            continue;

                        Dataset dataset = (Dataset)object;
                        String key = safeFilePath(file) + "\n" + fullPath(dataset);
                        if (seen.add(key))
                            targets.add(new DatasetTarget(file, dataset));
                    }
                }
            }
            catch (Exception ex) {
                state.error(safeFilePath(file), DatasetSearchException.localizedWithCause(
                    DatasetSearchException.Code.DATASET_ENUMERATION, ex));
            }
        }

        return targets;
    }

    private void searchNames(List<DatasetTarget> targets, String phrase, RunState state,
                             AtomicBoolean cancelled)
    {
        String folded = phrase.toLowerCase(Locale.ROOT);
        state.totalElements = targets.size();
        for (DatasetTarget target : targets) {
            if (cancelled.get())
                break;

            String name = target.dataset.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).contains(folded)) {
                state.emit(new DatasetSearchResult(safeFilePath(target.file), fullPath(target.dataset), name,
                                                    new long[0], DatasetSearchResult.MatchType.DATASET_NAME));
            }

            state.processedElements++;
            state.progress(fullPath(target.dataset));
        }
    }

    private void searchValues(List<DatasetTarget> targets, String phrase, RunState state,
                              AtomicBoolean cancelled, List<DatasetSearchSnapshot> dirtySnapshots)
    {
        for (DatasetTarget target : targets) {
            if (cancelled.get())
                break;

            List<DatasetSearchSnapshot> snapshots = findSnapshots(dirtySnapshots, target);

            Dataset scanner = null;
            try {
                scanner = newFreshDataset(target.dataset);
                long[] dims;
                Datatype rawDatatype;
                synchronized (NATIVE_IO_LOCK) {
                    scanner.init();
                    dims     = copy(scanner.getDims());
                    rawDatatype = scanner.getDatatype();
                }

                Datatype datatype = scalarDatatype(rawDatatype);

                if (dims == null)
                    throw DatasetSearchException.localized(
                        DatasetSearchException.Code.DIMENSIONS_UNAVAILABLE);

                if (!isSupportedValueType(datatype)) {
                    if (datatype == null) {
                        state.error(fullPath(target.dataset), DatasetSearchException.localized(
                            DatasetSearchException.Code.UNKNOWN_DATATYPE));
                    }
                    else {
                        state.error(fullPath(target.dataset), DatasetSearchException.localized(
                            DatasetSearchException.Code.UNSUPPORTED_DATATYPE,
                            datatype.getDescription()));
                    }
                    continue;
                }

                state.totalElements = safeAdd(state.totalElements, safeElementCount(dims));
                DatasetSearchOverlay dirtyOverlay = new DatasetSearchOverlay(
                    snapshots, dims, SEARCH_BLOCK_ELEMENTS);
                BlockIterator blocks = iterateBlocks(dims, SEARCH_BLOCK_ELEMENTS, cancelled);
                while (!cancelled.get() && blocks.hasNext()) {
                    Block block = blocks.next();
                    Object data = readBlock(scanner, block);
                    if (data == null)
                        throw DatasetSearchException.localized(
                            DatasetSearchException.Code.BLOCK_READ_NO_DATA);

                    data = overlayDirtySnapshots(data, rawDatatype, dirtyOverlay, block, cancelled);
                    if (cancelled.get())
                        break;
                    long blockElements = block.getElementCount();
                    processBlock(data, block, datatype, rawDatatype, phrase, target, state);
                    state.processedElements = safeAdd(state.processedElements, blockElements);
                    state.progress(fullPath(target.dataset));
                }
            }
            catch (OutOfMemoryError error) {
                state.error(fullPath(target.dataset), DatasetSearchException.localizedWithCause(
                    DatasetSearchException.Code.BLOCK_READ_FAILED, error));
            }
            catch (DatasetSearchException error) {
                state.error(fullPath(target.dataset), error);
            }
            catch (Exception ex) {
                state.error(fullPath(target.dataset), DatasetSearchException.localizedWithCause(
                    DatasetSearchException.Code.SEARCH_FAILED, ex));
            }
            finally {
                if (scanner != null) {
                    try {
                        scanner.clear();
                    }
                    catch (RuntimeException ex) {
                        log.debug("Unable to clear Dataset search object", ex);
                    }
                }
            }
        }
    }

    private List<DatasetSearchSnapshot> findSnapshots(List<DatasetSearchSnapshot> snapshots,
                                                      DatasetTarget target)
    {
        if (snapshots == null || snapshots.isEmpty())
            return Collections.emptyList();

        String filePath = safeFilePath(target.file);
        String datasetPath = fullPath(target.dataset);
        List<DatasetSearchSnapshot> matches = new ArrayList<>();
        for (DatasetSearchSnapshot snapshot : snapshots) {
            if (snapshot != null && snapshot.matches(filePath, datasetPath))
                matches.add(snapshot);
        }
        return matches.isEmpty() ? Collections.emptyList() : matches;
    }

    /**
     * Overlay only the dirty values indexed for one bounded scan block.  The
     * returned object may be a replacement for a scalar block; array blocks
     * are boxed only if at least one dirty value actually intersects them.
     */
    private static Object overlayDirtySnapshots(Object blockData, Datatype rawDatatype,
                                                DatasetSearchOverlay overlay, Block block,
                                                AtomicBoolean cancelled)
    {
        if (overlay == null)
            return blockData;

        int blockValuesPerCell = valuesPerCell(rawDatatype);
        if (blockValuesPerCell <= 0 || blockValuesPerCell == Integer.MAX_VALUE)
            return blockData;

        Object[] overlayData = {null};
        Object[] result = {blockData};
        int blockLength = valueLength(blockData);
        overlay.forEachValue(block, blockValuesPerCell, (snapshot, blockValueIndex,
                                                         snapshotValueIndex) -> {
            if (cancelled != null && cancelled.get())
                return false;
            if (blockValueIndex < 0 || blockValueIndex >= blockLength)
                return true;

            Object value = valueAt(snapshot.getData(), snapshotValueIndex);
            if (blockData != null && blockData.getClass().isArray()) {
                if (overlayData[0] == null)
                    overlayData[0] = objectArray(blockData);
                ((Object[])overlayData[0])[blockValueIndex] = value;
            }
            else if (blockValueIndex == 0) {
                result[0] = value;
            }
            return true;
        });
        return overlayData[0] == null ? result[0] : overlayData[0];
    }

    /** Copy primitive read arrays to boxed values so dirty display wrappers can overlay them. */
    private static Object[] objectArray(Object data)
    {
        if (data instanceof Object[])
            return (Object[])data;

        int length = Array.getLength(data);
        Object[] result = new Object[length];
        for (int i = 0; i < length; i++)
            result[i] = Array.get(data, i);
        return result;
    }

    private Object readBlock(Dataset scanner, Block block) throws Exception
    {
        synchronized (NATIVE_IO_LOCK) {
            long[] start  = scanner.getStartDims();
            long[] count  = scanner.getSelectedDims();
            long[] stride = scanner.getStride();
            long[] blockStart = block.start;
            long[] blockCount = block.count;

            if (start == null || count == null || stride == null)
                throw DatasetSearchException.localized(
                    DatasetSearchException.Code.SUBSET_UNAVAILABLE);
            if (start.length != blockStart.length || count.length != blockCount.length)
                throw DatasetSearchException.localized(
                    DatasetSearchException.Code.SELECTION_RANK_CHANGED);

            for (int i = 0; i < blockStart.length; i++) {
                start[i]  = blockStart[i];
                count[i]  = blockCount[i];
                stride[i] = 1;
            }

            /* read() returns the block without installing it in the Dataset buffer. */
            return scanner.read();
        }
    }

    private void processBlock(Object data, Block block, Datatype datatype, Datatype rawDatatype,
                              String phrase, DatasetTarget target, RunState state)
    {
        int valuesPerCell = valuesPerCell(rawDatatype);
        if (datatype.isInteger() || datatype.isFloat()) {
            NumericQuery numeric = NumericQuery.parse(phrase, datatype.isInteger());
            if (!numeric.valid)
                return;

            int length = valueLength(data);
            for (int i = 0; i < length; i++) {
                Object value = valueAt(data, i);
                if (numeric.matches(value)) {
                    state.emit(new DatasetSearchResult(safeFilePath(target.file), fullPath(target.dataset),
                                                        scalarToText(value),
                                                        coordinate(block, i / valuesPerCell),
                                                        DatasetSearchResult.MatchType.NUMERIC_VALUE));
                }
            }
            return;
        }

        String folded = phrase.toLowerCase(Locale.ROOT);
        int length = valueLength(data);
        for (int i = 0; i < length; i++) {
            Object value = valueAt(data, i);
            String text = datatype.isChar() ? charScalarToText(value) : scalarToText(value);
            if (text.toLowerCase(Locale.ROOT).contains(folded)) {
                state.emit(new DatasetSearchResult(safeFilePath(target.file), fullPath(target.dataset), text,
                                                   coordinate(block, i / valuesPerCell),
                                                   datatype.isChar()
                                                       ? DatasetSearchResult.MatchType.CHAR_VALUE
                                                       : DatasetSearchResult.MatchType.STRING_VALUE));
            }
        }
    }

    private static String charScalarToText(Object value)
    {
        if (value instanceof Character)
            return String.valueOf(value);
        if (value instanceof Number)
            return String.valueOf((char)(((Number)value).intValue() & 0xffff));
        return scalarToText(value);
    }

    private static boolean isSupportedValueType(Datatype datatype)
    {
        return datatype != null && (datatype.isInteger() || datatype.isFloat() || datatype.isString() ||
                                     datatype.isChar());
    }

    /**
     * HDF5 array datatypes expose their scalar base datatype separately from
     * the Dataset shape.  The Object API returns the flattened primitive array
     * for such a Dataset, so content search must classify it by that base type
     * instead of incorrectly reporting it as an unsupported complex type.
     */
    private static Datatype scalarDatatype(Datatype datatype)
    {
        Datatype current = datatype;
        while (current != null && current.isArray() && current.getDatatypeBase() != null)
            current = current.getDatatypeBase();
        return current;
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

    private static Dataset newFreshDataset(Dataset source) throws Exception
    {
        if (source == null)
            throw new IllegalArgumentException("Dataset is null");

        try {
            @SuppressWarnings("unchecked")
            Constructor<? extends Dataset> constructor =
                (Constructor<? extends Dataset>)source.getClass().getConstructor(
                    FileFormat.class, String.class, String.class);
            return constructor.newInstance(source.getFileFormat(), source.getName(), source.getPath());
        }
        catch (ReflectiveOperationException ex) {
            throw DatasetSearchException.localizedWithCause(
                DatasetSearchException.Code.DATASET_READER_UNAVAILABLE, ex,
                source.getClass().getName());
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
            return index >= 0 && index < ((List<?>)data).size() ? ((List<?>)data).get(index) : null;
        if (data.getClass().isArray())
            return Array.get(data, index);
        return index == 0 ? data : null;
    }

    private static long[] coordinate(Block block, int linearIndex)
    {
        if (block.count.length == 0)
            return new long[0];

        long[] coordinate = new long[block.count.length];
        long remaining = linearIndex;
        for (int i = block.count.length - 1; i >= 0; i--) {
            long size = block.count[i];
            coordinate[i] = block.start[i] + (remaining % size);
            remaining /= size;
        }
        return coordinate;
    }

    /**
     * Build bounded row-major blocks for a Dataset shape.  This method is public
     * so a regression test can prove that a large Dataset is not represented by
     * one whole-array read.
     */
    public static List<Block> buildBlocks(long[] dims, long maxElements)
    {
        if (dims == null)
            return Collections.emptyList();
        if (dims.length == 0)
            return Collections.singletonList(new Block(new long[0], new long[0]));

        List<Block> blocks = new ArrayList<>();
        BlockIterator iterator = iterateBlocks(dims, maxElements);
        while (iterator.hasNext())
            blocks.add(iterator.next());
        return blocks.isEmpty() ? Collections.emptyList() : blocks;
    }

    /**
     * Create a lazy row-major block iterator.  The optional cancellation flag
     * is checked before each block is generated and is also observed by
     * {@link BlockIterator#hasNext()}.
     */
    public static BlockIterator iterateBlocks(long[] dims, long maxElements,
                                              AtomicBoolean cancelled)
    {
        return new BlockIterator(dims, maxElements, cancelled);
    }

    /** Create a lazy row-major block iterator without cancellation. */
    public static BlockIterator iterateBlocks(long[] dims, long maxElements)
    {
        return iterateBlocks(dims, maxElements, null);
    }

    /** Package-private tile metadata shared by the streaming dirty overlay. */
    static long[] blockTile(long[] dims, long maxElements)
    {
        if (dims == null)
            return null;
        if (dims.length == 0)
            return new long[0];
        if (maxElements <= 0)
            throw new IllegalArgumentException("maxElements must be positive");
        for (long dim : dims) {
            if (dim < 0)
                throw new IllegalArgumentException("Dataset dimensions cannot be negative");
        }
        return chooseTile(dims, maxElements);
    }

    /**
     * Return a shape's element count without iterating its blocks.  Overflow
     * saturates at {@link Long#MAX_VALUE}; a zero dimension yields zero.
     */
    public static long safeElementCount(long[] dims)
    {
        if (dims == null)
            return 0;

        for (long dim : dims) {
            if (dim < 0)
                throw new IllegalArgumentException("Dataset dimensions cannot be negative");
            if (dim == 0)
                return 0;
        }

        long result = 1;
        for (long dim : dims) {
            if (result > Long.MAX_VALUE / dim)
                return Long.MAX_VALUE;
            result *= dim;
        }
        return result;
    }

    private static long[] chooseTile(long[] dims, long maxElements)
    {
        long[] tile = dims.clone();
        while (productExceeds(tile, maxElements)) {
            int split = 0;
            for (int i = 1; i < tile.length; i++) {
                if (tile[i] > tile[split])
                    split = i;
            }
            if (tile[split] <= 1)
                break;
            tile[split] = Math.max(1, tile[split] / 2 + tile[split] % 2);
        }
        return tile;
    }

    private static boolean productExceeds(long[] values, long limit)
    {
        long product = 1;
        for (long value : values) {
            if (value <= 0)
                return false;
            if (product > limit / value)
                return true;
            product *= value;
        }
        return product > limit;
    }

    private static String fullPath(HObject object)
    {
        if (object == null)
            return "";
        String fullName = object.getFullName();
        if (fullName != null && !fullName.isEmpty())
            return fullName;
        String path = object.getPath() == null ? "" : object.getPath();
        String name = object.getName() == null ? "" : object.getName();
        String result = path + name;
        return result.isEmpty() ? "/" : result;
    }

    private static String safeFilePath(FileFormat file)
    {
        return file == null || file.getFilePath() == null ? "" : file.getFilePath();
    }

    private static long[] copy(long[] values) { return values == null ? null : values.clone(); }

    private static long safeAdd(long first, long second)
    {
        if (second < 0 || Long.MAX_VALUE - first < second)
            return Long.MAX_VALUE;
        return first + second;
    }

    /** Convert one Object API scalar into the text shown in a result row. */
    public static String scalarToText(Object value)
    {
        if (value == null)
            return "null";
        if (value instanceof byte[])
            return new String((byte[])value, StandardCharsets.UTF_8);
        if (value instanceof char[])
            return new String((char[])value);
        if (value instanceof Character)
            return String.valueOf(value);
        if (value instanceof Byte)
            return String.valueOf(((Byte)value).byteValue());
        if (value instanceof Short)
            return String.valueOf(((Short)value).shortValue());
        if (value instanceof Integer)
            return String.valueOf(((Integer)value).intValue());
        if (value instanceof Long)
            return String.valueOf(((Long)value).longValue());
        return String.valueOf(value);
    }

    private static final class NumericQuery {
        private final boolean integer;
        private final boolean valid;
        private final BigInteger integerValue;
        private final BigDecimal decimalValue;
        private final double doubleValue;

        private NumericQuery(boolean integer, boolean valid, BigInteger integerValue,
                             BigDecimal decimalValue, double doubleValue)
        {
            this.integer      = integer;
            this.valid        = valid;
            this.integerValue = integerValue;
            this.decimalValue = decimalValue;
            this.doubleValue  = doubleValue;
        }

        private static NumericQuery parse(String text, boolean integer)
        {
            try {
                if (integer)
                    return new NumericQuery(true, true, new BigInteger(text.trim()), null, 0.0);

                double doubleValue = Double.parseDouble(text.trim());
                BigDecimal decimal = null;
                if (Double.isFinite(doubleValue))
                    decimal = new BigDecimal(text.trim());
                return new NumericQuery(false, true, null, decimal, doubleValue);
            }
            catch (NumberFormatException ex) {
                return new NumericQuery(integer, false, null, null, 0.0);
            }
        }

        private boolean matches(Object value)
        {
            if (!(value instanceof Number) && !(value instanceof BigInteger) &&
                !(value instanceof BigDecimal))
                return false;

            if (integer) {
                if (value instanceof BigInteger)
                    return integerValue.equals(value);
                if (value instanceof BigDecimal)
                    try {
                        return integerValue.equals(((BigDecimal)value).toBigIntegerExact());
                    }
                    catch (ArithmeticException ex) {
                        return false;
                    }
                return integerValue.equals(BigInteger.valueOf(((Number)value).longValue()));
            }

            if (decimalValue != null) {
                BigDecimal actualDecimal = decimalValue(value);
                if (actualDecimal != null)
                    return decimalValue.compareTo(actualDecimal) == 0;
            }

            double actual = ((Number)value).doubleValue();
            return Double.compare(actual, doubleValue) == 0;
        }

        private static BigDecimal decimalValue(Object value)
        {
            if (value instanceof BigDecimal)
                return (BigDecimal)value;
            if (value instanceof BigInteger)
                return new BigDecimal((BigInteger)value);
            if (!(value instanceof Number))
                return null;

            /*
             * Use Number.toString() for finite floating-point values.  This
             * preserves the value users see and type into the search box;
             * converting Float 0.1f to double first would compare against
             * 0.10000000149011612 instead of the displayed 0.1.
             */
            try {
                return new BigDecimal(value.toString());
            }
            catch (NumberFormatException ex) {
                return null;
            }
        }
    }
}
