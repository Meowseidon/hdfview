/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * All rights reserved.                                                       *
 *****************************************************************************/

package hdf.view.search;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Array;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import hdf.object.Dataset;
import hdf.object.Datatype;
import hdf.object.FileFormat;
import hdf.object.Group;
import hdf.object.HObject;
import hdf.object.h5.H5File;

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
    void dirtyTableBufferIsSearchedWithoutWritingItToDisk() throws Exception
    {
        try (OpenedFile opened = openFixture("tscalarintsize.h5")) {
            Dataset dataset = firstDataset(opened.file, true, false);
            assertNotNull(dataset);
            dataset.init();

            List<DatasetSearchResult> results = new ArrayList<>();
            DatasetSearchSnapshot dirty = new DatasetSearchSnapshot(
                opened.file.getFilePath(), dataset.getFullName(), new byte[] {42},
                new long[] {0}, new long[] {1}, new long[] {1}, dataset.getDims(), dataset.getDatatype());
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
