/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * All rights reserved.                                                       *
 *****************************************************************************/

package hdf.view.search;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Array;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import hdf.object.Dataset;
import hdf.object.Datatype;
import hdf.object.FileFormat;
import hdf.object.Group;
import hdf.object.HObject;
import hdf.object.h5.H5File;
import hdf.view.TableView.DataProviderFactory;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Focused regression coverage for bounded Dataset content search. */
@Tag("integration")
class DatasetSearchEngineTest {
    @Test
    void largeShapesAreAlwaysSplitIntoBoundedReads()
    {
        List<DatasetSearchEngine.Block> blocks =
            DatasetSearchEngine.buildBlocks(new long[] {1000, 1000, 8}, 1024);

        assertTrue(blocks.size() > 1, "a large Dataset must not become one whole-array read");
        for (DatasetSearchEngine.Block block : blocks)
            assertTrue(block.getElementCount() <= 1024,
                       "every native read block must stay within the configured bound");
    }

    @Test
    void dirtyOverlayIndexesChangedCellsAcrossDifferentTwoDimensionalBlocks()
    {
        long[] dims = {64, 64};
        int[] page = new int[(int)(dims[0] * dims[1])];
        int firstCell = 1 * (int)dims[1] + 1;
        int lastCell = 60 * (int)dims[1] + 60;
        DatasetSearchSnapshot dirty = DatasetSearchSnapshot.fromChangedValues(
            "file.h5", "/dataset", page, new int[] {firstCell, lastCell},
            new long[] {0, 0}, dims, new long[] {1, 1}, dims, null);
        List<DatasetSearchEngine.Block> blocks = DatasetSearchEngine.buildBlocks(dims, 256);
        DatasetSearchOverlay overlay = new DatasetSearchOverlay(
            Collections.singletonList(dirty), blocks);

        Set<Integer> blocksWithDirtyValues = new HashSet<>();
        int[] visitedValues = {0};
        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
            int currentBlockIndex = blockIndex;
            overlay.forEachValue(blockIndex, 1, (snapshot, blockValueIndex, snapshotValueIndex) -> {
                blocksWithDirtyValues.add(currentBlockIndex);
                visitedValues[0]++;
                return true;
            });
        }

        assertNotNull(dirty);
        assertTrue(dirty.isSparse(), "a changed-cell capture must not retain a dense page");
        assertEquals(2, Array.getLength(dirty.getData()));
        assertTrue(blocks.size() > 1, "the regression needs multiple bounded blocks");
        assertEquals(2, visitedValues[0],
                     "only changed values should be presented to the block overlay");
        assertEquals(Set.of(0, blocks.size() - 1), blocksWithDirtyValues,
                     "the two edited cells must land in different blocks");
        assertTrue(visitedValues[0] < blocks.size() * page.length,
                   "overlay work must not scale as block count times the old page size");
    }

    @Test
    void denseOverlayIntersectsStridedSelectionsWithoutCoordinateAllocationsPerValue()
    {
        long[] dims = {8, 8};
        DatasetSearchSnapshot dirty = new DatasetSearchSnapshot(
            "file.h5", "/dataset", new int[] {11, 12, 21, 22, 31, 32},
            new long[] {0, 1}, new long[] {3, 2}, new long[] {2, 3}, dims, null);
        List<DatasetSearchEngine.Block> blocks = DatasetSearchEngine.buildBlocks(dims, 8);
        DatasetSearchOverlay overlay = new DatasetSearchOverlay(
            Collections.singletonList(dirty), blocks);
        List<Integer> snapshotValueIndices = new ArrayList<>();

        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
            overlay.forEachValue(blockIndex, 1, (snapshot, blockValueIndex, snapshotValueIndex) -> {
                snapshotValueIndices.add(snapshotValueIndex);
                return true;
            });
        }

        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5), snapshotValueIndices,
                     "stride coordinates must retain row-major dirty-buffer order");
    }

    @Test
    void laterDirtySnapshotWinsWhenTwoSnapshotsOverlap()
    {
        List<DatasetSearchSnapshot> snapshots = Arrays.asList(
            new DatasetSearchSnapshot("file.h5", "/dataset", new int[] {11},
                                      new long[] {1}, new long[] {1}, new long[] {1},
                                      new long[] {4}, null),
            new DatasetSearchSnapshot("file.h5", "/dataset", new int[] {22},
                                      new long[] {1}, new long[] {1}, new long[] {1},
                                      new long[] {4}, null));
        List<DatasetSearchEngine.Block> blocks = DatasetSearchEngine.buildBlocks(
            new long[] {4}, 4);
        DatasetSearchOverlay overlay = new DatasetSearchOverlay(snapshots, blocks);
        int[] blockData = {0, 0, 0, 0};

        overlay.forEachValue(0, 1, (snapshot, blockValueIndex, snapshotValueIndex) -> {
            blockData[blockValueIndex] = ((int[])snapshot.getData())[snapshotValueIndex];
            return true;
        });

        assertArrayEquals(new int[] {0, 22, 0, 0}, blockData,
                          "later overlapping dirty snapshots must retain overwrite order");
    }

    @Test
    void tableProviderReportsChangedScalarPositionsForSparseSnapshots() throws Exception
    {
        try (OpenedFile opened = openFixture("tattr2.h5")) {
            Dataset dataset = findDataset(opened.file, "/g2/integer");
            assertNotNull(dataset);
            dataset.init();
            Object data = dataset.getData();
            DataProviderFactory.HDFDataProvider provider =
                DataProviderFactory.getDataProvider(dataset, data, false);

            provider.setDataValue(0, 0, "-777");

            assertTrue(provider.getIsValueChanged());
            assertArrayEquals(new int[] {0}, provider.getChangedValueIndices());
            DatasetSearchSnapshot sparse = DatasetSearchSnapshot.fromChangedValues(
                opened.file.getFilePath(), dataset.getFullName(), data,
                provider.getChangedValueIndices(), dataset.getStartDims(),
                dataset.getSelectedDims(), dataset.getStride(), dataset.getDims(),
                dataset.getDatatype());
            assertTrue(sparse != null && sparse.isSparse());
            assertEquals(1, Array.getLength(sparse.getData()));

            provider.setIsValueChanged(false);
            assertFalse(provider.getIsValueChanged());
            assertEquals(0, provider.getChangedValueIndices().length);
        }
    }

    @Test
    void numericNameAndNoResultSearchUseStablePathsAndCoordinates() throws Exception
    {
        try (OpenedFile opened = openFixture("tscalarintsize.h5")) {
            Dataset numeric = firstDataset(opened.file, true, false);
            assertNotNull(numeric, "the numeric fixture must contain a Dataset");
            numeric.init();
            Object data = numeric.getData();
            assertNotNull(data);
            Object first = Array.getLength(data) == 0 ? null : Array.get(data, 0);
            assertNotNull(first);

            DatasetSearchEngine engine = new DatasetSearchEngine();
            List<DatasetSearchResult> nameResults = new ArrayList<>();
            engine.search(Collections.singletonList(opened.file), numeric.getName(),
                          DatasetSearchEngine.SearchMode.DATASET_NAME, new AtomicBoolean(),
                          collecting(nameResults));
            assertTrue(nameResults.stream().anyMatch(result ->
                           result.getDatasetPath().equals(numeric.getFullName()) &&
                           result.getMatchType() == DatasetSearchResult.MatchType.DATASET_NAME));

            List<DatasetSearchResult> multipleDatasetResults = new ArrayList<>();
            engine.search(Collections.singletonList(opened.file), "DS",
                          DatasetSearchEngine.SearchMode.DATASET_NAME, new AtomicBoolean(),
                          collecting(multipleDatasetResults));
            assertTrue(multipleDatasetResults.size() > 1,
                       "a global name search must return matches from multiple Datasets");

            List<DatasetSearchResult> valueResults = new ArrayList<>();
            String query = DatasetSearchEngine.scalarToText(first);
            engine.search(Collections.singletonList(opened.file), query,
                          DatasetSearchEngine.SearchMode.DATA_VALUE, new AtomicBoolean(),
                          collecting(valueResults));
            assertTrue(valueResults.stream().anyMatch(result ->
                           result.getDatasetPath().equals(numeric.getFullName()) &&
                           result.getCoordinate().length == numeric.getRank()),
                       "numeric matches must retain the full Dataset coordinate");

            List<DatasetSearchResult> noResults = new ArrayList<>();
            DatasetSearchEngine.SearchSummary summary = engine.search(
                Collections.singletonList(opened.file), "__not_a_real_hdf_value__",
                DatasetSearchEngine.SearchMode.DATA_VALUE, new AtomicBoolean(), collecting(noResults));
            assertTrue(noResults.isEmpty());
            assertFalse(summary.isCancelled());
        }
    }

    @Test
    void stringOrCharacterValuesAndCancellationAreHandled() throws Exception
    {
        try (OpenedFile opened = openFixture("tstr.h5")) {
            Dataset textDataset = firstDataset(opened.file, false, true);
            assertNotNull(textDataset, "the string fixture must contain a string/character Dataset");
            textDataset.init();
            Object data = textDataset.getData();
            assertNotNull(data);
            String value = firstTextValue(data, textDataset.getDatatype());
            assertNotNull(value);
            assertFalse(value.isEmpty());

            List<DatasetSearchResult> results = new ArrayList<>();
            new DatasetSearchEngine().search(Collections.singletonList(opened.file),
                                             value.substring(0, 1),
                                             DatasetSearchEngine.SearchMode.DATA_VALUE,
                                             new AtomicBoolean(), collecting(results));
            assertTrue(results.stream().anyMatch(result ->
                           result.getDatasetPath().equals(textDataset.getFullName())));

            AtomicBoolean cancellation = new AtomicBoolean(true);
            DatasetSearchEngine.SearchSummary cancelled = new DatasetSearchEngine().search(
                Collections.singletonList(opened.file), value,
                DatasetSearchEngine.SearchMode.DATA_VALUE, cancellation, new DatasetSearchEngine.Listener() {});
            assertTrue(cancelled.isCancelled(), "a cancelled search must report cancellation");
        }
    }

    @Test
    void multidimensionalResultsKeepTheFullCoordinate() throws Exception
    {
        try (OpenedFile opened = openFixture("tframeselection.h5")) {
            Dataset multidimensional = firstDataset(opened.file, true, false, 3);
            assertNotNull(multidimensional, "the frame fixture must contain a multidimensional Dataset");
            multidimensional.init();
            Object data = multidimensional.getData();
            long[] dims = multidimensional.getDims();
            int linear = Math.min(3, Array.getLength(data) - 1);
            Object value = Array.get(data, linear);
            long[] expected = linearCoordinate(dims, linear);

            List<DatasetSearchResult> results = new ArrayList<>();
            new DatasetSearchEngine().search(Collections.singletonList(opened.file),
                                             DatasetSearchEngine.scalarToText(value),
                                             DatasetSearchEngine.SearchMode.DATA_VALUE,
                                             new AtomicBoolean(), collecting(results));
            assertTrue(results.stream().anyMatch(result ->
                           result.getDatasetPath().equals(multidimensional.getFullName()) &&
                           java.util.Arrays.equals(expected, result.getCoordinate())),
                       "a value match must navigate by its full multidimensional coordinate");
        }
    }

    @Test
    void floatingPointValuesMatchTheirDisplayedDecimalRepresentation() throws Exception
    {
        try (OpenedFile opened = openFixture("tfloat16.h5")) {
            Dataset floatingPoint = firstDataset(opened.file, true, false);
            assertNotNull(floatingPoint, "the floating-point fixture must contain a Dataset");
            floatingPoint.init();
            long[] dims = floatingPoint.getDims();
            assertNotNull(dims);

            long[] count = new long[dims.length];
            long[] stride = new long[dims.length];
            java.util.Arrays.fill(count, 1L);
            java.util.Arrays.fill(stride, 1L);

            List<DatasetSearchResult> results = new ArrayList<>();
            DatasetSearchSnapshot dirty = new DatasetSearchSnapshot(
                opened.file.getFilePath(), floatingPoint.getFullName(), new float[] {0.1f},
                new long[dims.length], count, stride, dims, floatingPoint.getDatatype());
            new DatasetSearchEngine().search(Collections.singletonList(opened.file), "0.1",
                                             DatasetSearchEngine.SearchMode.DATA_VALUE,
                                             new AtomicBoolean(), collecting(results),
                                             Collections.singletonList(dirty));

            assertTrue(results.stream().anyMatch(result ->
                           result.getDatasetPath().equals(floatingPoint.getFullName()) &&
                           result.getMatchedValue().equals("0.1")),
                       "Float values must match the decimal representation users see");
        }
    }

    @Test
    void dirtyPagesOverlayTheWholeDatasetAndPreserveTheDiskAndBuffers() throws Exception
    {
        try (OpenedFile opened = openFixture("tframeselection.h5")) {
            Dataset dataset = firstDataset(opened.file, true, false, 3);
            assertNotNull(dataset, "the frame fixture must contain a rank-three numeric Dataset");
            dataset.init();
            long[] dims = dataset.getDims();
            assertArrayEquals(new long[] {5, 5, 5}, dims);

            int[] diskValues = readFullIntDataset(dataset);
            int pageSize = (int)(dims[1] * dims[2]);
            int[] pageZero = Arrays.copyOfRange(diskValues, 0, pageSize);
            int[] pageOne = Arrays.copyOfRange(diskValues, pageSize, pageSize * 2);
            int originalPageZeroValue = pageZero[0];
            int originalPageOneValue = pageOne[0];

            int dirtyPageZeroValue = unusedValue(diskValues, 123456789);
            int dirtyPageOneValue = unusedValue(diskValues, 246813579);
            pageZero[0] = dirtyPageZeroValue;
            pageOne[0] = dirtyPageOneValue;

            int diskOnlyIndex = -1;
            for (int i = pageSize * 2; i < diskValues.length; i++) {
                if (!contains(pageZero, diskValues[i]) && !contains(pageOne, diskValues[i])) {
                    diskOnlyIndex = i;
                    break;
                }
            }
            assertTrue(diskOnlyIndex >= 0,
                       "the fixture must provide a value outside both dirty pages");

            List<DatasetSearchSnapshot> dirtySnapshots = Arrays.asList(
                new DatasetSearchSnapshot(opened.file.getFilePath(), dataset.getFullName(), pageZero,
                                          new long[] {0, 0, 0}, new long[] {1, 5, 5},
                                          new long[] {1, 1, 1}, dims, dataset.getDatatype()),
                new DatasetSearchSnapshot(opened.file.getFilePath(), dataset.getFullName(), pageOne,
                                          new long[] {1, 0, 0}, new long[] {1, 5, 5},
                                          new long[] {1, 1, 1}, dims, dataset.getDatatype()));

            String datasetPath = dataset.getFullName();
            long[] diskOnlyCoordinate = linearCoordinate(dims, diskOnlyIndex);
            List<DatasetSearchResult> diskResults = searchValues(
                opened.file, DatasetSearchEngine.scalarToText(diskValues[diskOnlyIndex]), dirtySnapshots);
            assertTrue(hasMatchAt(diskResults, datasetPath, diskOnlyCoordinate,
                                  DatasetSearchEngine.scalarToText(diskValues[diskOnlyIndex])),
                       "a dirty page must not hide matching disk content on another page");

            List<DatasetSearchResult> firstDirtyResults = searchValues(
                opened.file, DatasetSearchEngine.scalarToText(dirtyPageZeroValue), dirtySnapshots);
            assertTrue(hasMatchAt(firstDirtyResults, datasetPath, new long[] {0, 0, 0},
                                  DatasetSearchEngine.scalarToText(dirtyPageZeroValue)),
                       "the first dirty page's new value must be searchable");

            List<DatasetSearchResult> secondDirtyResults = searchValues(
                opened.file, DatasetSearchEngine.scalarToText(dirtyPageOneValue), dirtySnapshots);
            assertTrue(hasMatchAt(secondDirtyResults, datasetPath, new long[] {1, 0, 0},
                                  DatasetSearchEngine.scalarToText(dirtyPageOneValue)),
                       "a second dirty snapshot for the same Dataset must be merged");

            List<DatasetSearchResult> replacedValueResults = searchValues(
                opened.file, DatasetSearchEngine.scalarToText(originalPageZeroValue), dirtySnapshots);
            assertFalse(hasMatchAt(replacedValueResults, datasetPath, new long[] {0, 0, 0},
                                    DatasetSearchEngine.scalarToText(originalPageZeroValue)),
                        "the replaced disk value must not survive the dirty overlay");

            assertTrue(pageZero[0] == dirtyPageZeroValue && pageOne[0] == dirtyPageOneValue,
                       "search must not mutate the captured dirty buffers");

            int[] afterSearch = readFullIntDataset(dataset);
            assertTrue(afterSearch[0] == originalPageZeroValue &&
                       afterSearch[pageSize] == originalPageOneValue,
                       "search must not write dirty values back to the Dataset");
        }
    }

    @Test
    void dirtyTableBufferIsSearchedWithoutWritingItToDisk() throws Exception
    {
        try (OpenedFile opened = openFixture("tscalarintsize.h5")) {
            Dataset dataset = firstDataset(opened.file, true, false);
            assertNotNull(dataset);
            dataset.init();

            List<DatasetSearchResult> results = new ArrayList<>();
            byte[] dirtyData = ((byte[])dataset.getData()).clone();
            dirtyData[0] = 42;
            DatasetSearchSnapshot dirty = DatasetSearchSnapshot.fromChangedValues(
                opened.file.getFilePath(), dataset.getFullName(), dirtyData, new int[] {0},
                dataset.getStartDims(), dataset.getSelectedDims(), dataset.getStride(),
                dataset.getDims(), dataset.getDatatype());
            assertTrue(dirty != null && dirty.isSparse());
            new DatasetSearchEngine().search(Collections.singletonList(opened.file), "42",
                                             DatasetSearchEngine.SearchMode.DATA_VALUE,
                                             new AtomicBoolean(), collecting(results),
                                             Collections.singletonList(dirty));

            assertTrue(results.stream().anyMatch(result ->
                           result.getDatasetPath().equals(dataset.getFullName()) &&
                           result.getMatchedValue().equals("42")),
                       "the search must use the current dirty TableView buffer for that Dataset");
        }
    }

    private static DatasetSearchEngine.Listener collecting(List<DatasetSearchResult> destination)
    {
        return new DatasetSearchEngine.Listener() {
            @Override
            public void onResults(List<DatasetSearchResult> results) { destination.addAll(results); }
        };
    }

    private static List<DatasetSearchResult> searchValues(FileFormat file, String query,
                                                           List<DatasetSearchSnapshot> dirtySnapshots)
    {
        List<DatasetSearchResult> results = new ArrayList<>();
        new DatasetSearchEngine().search(Collections.singletonList(file), query,
                                         DatasetSearchEngine.SearchMode.DATA_VALUE,
                                         new AtomicBoolean(), collecting(results), dirtySnapshots);
        return results;
    }

    private static boolean hasMatchAt(List<DatasetSearchResult> results, String datasetPath,
                                      long[] coordinate, String matchedValue)
    {
        return results.stream().anyMatch(result ->
            result.getDatasetPath().equals(datasetPath) &&
            Arrays.equals(coordinate, result.getCoordinate()) &&
            result.getMatchedValue().equals(matchedValue));
    }

    private static int[] readFullIntDataset(Dataset dataset) throws Exception
    {
        long[] dims = dataset.getDims();
        long[] start = dataset.getStartDims();
        long[] count = dataset.getSelectedDims();
        long[] stride = dataset.getStride();
        for (int i = 0; i < dims.length; i++) {
            start[i] = 0;
            count[i] = dims[i];
            stride[i] = 1;
        }
        dataset.clearData();
        Object data = dataset.getData();
        assertTrue(data instanceof int[], "the frame fixture must use a Java int buffer");
        return (int[])data;
    }

    private static int unusedValue(int[] values, int candidate)
    {
        while (contains(values, candidate))
            candidate++;
        return candidate;
    }

    private static boolean contains(int[] values, int candidate)
    {
        for (int value : values) {
            if (value == candidate)
                return true;
        }
        return false;
    }

    private static Dataset firstDataset(FileFormat file, boolean numeric, boolean text, int minimumRank)
        throws Exception
    {
        Group root = (Group)file.getRootObject();
        for (HObject object : root.depthFirstMemberList()) {
            if (!(object instanceof Dataset))
                continue;
            Dataset dataset = (Dataset)object;
            dataset.init();
            Datatype datatype = scalarDatatype(dataset.getDatatype());
            boolean matches = (numeric && datatype != null && (datatype.isInteger() || datatype.isFloat())) ||
                              (text && datatype != null && (datatype.isString() || datatype.isChar()));
            if (matches && dataset.getRank() >= minimumRank)
                return dataset;
        }
        return null;
    }

    private static Dataset findDataset(FileFormat file, String fullName) throws Exception
    {
        Group root = (Group)file.getRootObject();
        for (HObject object : root.depthFirstMemberList()) {
            if (object instanceof Dataset && fullName.equals(object.getFullName()))
                return (Dataset)object;
        }
        return null;
    }

    private static Datatype scalarDatatype(Datatype datatype)
    {
        Datatype current = datatype;
        while (current != null && current.isArray() && current.getDatatypeBase() != null)
            current = current.getDatatypeBase();
        return current;
    }

    private static Dataset firstDataset(FileFormat file, boolean numeric, boolean text)
        throws Exception
    {
        return firstDataset(file, numeric, text, 0);
    }

    private static String firstTextValue(Object data, Datatype datatype)
    {
        datatype = scalarDatatype(datatype);
        int length = data.getClass().isArray() ? Array.getLength(data) : 1;
        for (int i = 0; i < length; i++) {
            Object value = data.getClass().isArray() ? Array.get(data, i) : data;
            String text = datatype.isChar() && value instanceof Number
                ? String.valueOf((char)(((Number)value).intValue() & 0xffff))
                : DatasetSearchEngine.scalarToText(value);
            if (!text.isEmpty())
                return text;
        }
        return null;
    }

    private static long[] linearCoordinate(long[] dims, int linear)
    {
        long[] coordinate = new long[dims.length];
        long remaining = linear;
        for (int i = dims.length - 1; i >= 0; i--) {
            coordinate[i] = remaining % dims[i];
            remaining /= dims[i];
        }
        return coordinate;
    }

    private static OpenedFile openFixture(String name) throws Exception
    {
        Path path;
        try {
            path = Paths.get(DatasetSearchEngineTest.class.getClassLoader()
                                 .getResource("uitest/" + name).toURI());
        }
        catch (URISyntaxException ex) {
            throw new AssertionError("Unable to resolve test fixture " + name, ex);
        }

        H5File file = new H5File(path.toString(), FileFormat.READ);
        file.open();
        return new OpenedFile(file);
    }

    private static final class OpenedFile implements AutoCloseable {
        private final H5File file;

        private OpenedFile(H5File file) { this.file = file; }

        @Override
        public void close() throws Exception { file.close(); }
    }
}
