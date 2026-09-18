/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * All rights reserved.                                                       *
 *****************************************************************************/

package hdf.view.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import hdf.object.Dataset;
import hdf.object.Datatype;
import hdf.object.FileFormat;
import hdf.object.Group;
import hdf.object.HObject;
import hdf.object.h5.H5File;
import hdf.view.search.DatasetSearchEngine;
import hdf.view.search.DatasetSearchSnapshot;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Regression coverage for the old hdfviewer statistics semantics. */
@Tag("integration")
class DatasetStatisticsEngineTest {
    @Test
    void countsMatchPythonNumpyDefinitions() throws Exception
    {
        try (OpenedFile opened = openFixture("tscalarintsize.h5")) {
            Datatype datatype = firstNumeric(opened.file).getDatatype();
            DatasetStatisticsEngine.Result result = DatasetStatisticsEngine.computeCurrentPage(
                new double[] {-2.0, 0.0, 3.0, Double.NaN, Double.POSITIVE_INFINITY,
                             Double.NEGATIVE_INFINITY},
                datatype, null, new AtomicBoolean());

            assertEquals(6, result.getTotal());
            assertEquals(1, result.getZero());
            assertEquals(5, result.getNonZero());
            assertEquals(2, result.getPositive());
            assertEquals(2, result.getNegative());
            assertEquals(3, result.getNonNegative());
            assertEquals(Double.NEGATIVE_INFINITY, result.getMinimum());
            assertEquals(Double.POSITIVE_INFINITY, result.getMaximum());
            assertTrue(Double.isNaN(result.getMean()), "NaN remains part of the legacy mean reduction");
            assertTrue(Double.isNaN(result.getStandardDeviation()),
                       "NaN remains part of the legacy standard-deviation reduction");
        }
    }

    @Test
    void booleanValuesUseZeroAndPositiveComparisons() throws Exception
    {
        try (OpenedFile opened = openFixture("tscalarintsize.h5")) {
            Datatype datatype = firstNumeric(opened.file).getDatatype();
            DatasetStatisticsEngine.Result result = DatasetStatisticsEngine.computeCurrentPage(
                new boolean[] {false, true, false}, datatype, null, new AtomicBoolean());

            assertEquals(3, result.getTotal());
            assertEquals(2, result.getZero());
            assertEquals(1, result.getNonZero());
            assertEquals(1, result.getPositive());
            assertEquals(0, result.getNegative());
            assertEquals(3, result.getNonNegative());
        }
    }

    @Test
    void fillValueIsExcludedOnlyFromLegacyReductions() throws Exception
    {
        try (OpenedFile opened = openFixture("tscalarintsize.h5")) {
            Datatype datatype = firstNumeric(opened.file).getDatatype();
            DatasetStatisticsEngine.Result result = DatasetStatisticsEngine.computeCurrentPage(
                new int[] {0, 2, 4}, datatype, new int[] {0}, new AtomicBoolean());

            assertEquals(3, result.getTotal());
            assertEquals(1, result.getZero(), "fill values still participate in classification counts");
            assertEquals(2, result.getNonZero());
            assertEquals(2.0, result.getMinimum());
            assertEquals(4.0, result.getMaximum());
            assertEquals(3.0, result.getMean());
            assertEquals(Math.sqrt(2.0), result.getStandardDeviation());
        }
    }

    @Test
    void currentPageUsesTheCapturedUnsavedTableBuffer() throws Exception
    {
        try (OpenedFile opened = openFixture("tscalarintsize.h5")) {
            Dataset dataset = firstNumeric(opened.file);
            DatasetSearchSnapshot dirtyPage = new DatasetSearchSnapshot(
                opened.file.getFilePath(), dataset.getFullName(), new int[] {0, 0, 9},
                new long[] {0}, new long[] {3}, new long[] {1}, new long[] {3},
                dataset.getDatatype());
            DatasetStatisticsEngine.Request request = new DatasetStatisticsEngine.Request(
                dataset, dirtyPage, dirtyPage, null);

            DatasetStatisticsEngine.Result result = DatasetStatisticsEngine.compute(
                request, DatasetStatisticsEngine.Scope.CURRENT_PAGE, new AtomicBoolean(), null);

            assertEquals(3, result.getTotal());
            assertEquals(2, result.getZero());
            assertEquals(1, result.getPositive());
        }
    }

    @Test
    void unsupportedStringDatatypeIsReported() throws Exception
    {
        try (OpenedFile opened = openFixture("tstr.h5")) {
            Dataset text = firstText(opened.file);
            assertThrows(UnsupportedOperationException.class,
                         () -> DatasetStatisticsEngine.computeCurrentPage(
                             new String[] {"text"}, text.getDatatype(), null, new AtomicBoolean()));
            assertThrows(UnsupportedOperationException.class,
                         () -> DatasetStatisticsEngine.matches("text", DatasetStatisticsEngine.Kind.ZERO));
        }
    }

    @Test
    void cancellationIsCheckedBetweenCurrentPageBlocks() throws Exception
    {
        try (OpenedFile opened = openFixture("tscalarintsize.h5")) {
            AtomicBoolean cancelled = new AtomicBoolean(true);
            Datatype datatype = firstNumeric(opened.file).getDatatype();
            assertThrows(DatasetStatisticsEngine.StatisticsCancelled.class,
                         () -> DatasetStatisticsEngine.computeCurrentPage(
                             new int[(int)DatasetStatisticsEngine.STATISTICS_BLOCK_ELEMENTS + 1],
                             datatype, null, cancelled));
        }
    }

    @Test
    void cancellationStopsAnEntireDatasetScanBetweenNativeBlocks() throws Exception
    {
        try (OpenedFile opened = openFixture("tframeselection.h5")) {
            Dataset dataset = firstNumeric(opened.file, 3);
            dataset.init();
            long[] dims = dataset.getDims();
            DatasetSearchSnapshot current = new DatasetSearchSnapshot(
                opened.file.getFilePath(), dataset.getFullName(), dataset.getData(),
                dataset.getStartDims(), dataset.getSelectedDims(), dataset.getStride(), dims,
                dataset.getDatatype());
            DatasetStatisticsEngine.Request request = new DatasetStatisticsEngine.Request(
                dataset, current, null, dataset.getFillValue());
            AtomicBoolean cancelled = new AtomicBoolean();

            assertThrows(DatasetStatisticsEngine.StatisticsCancelled.class,
                         () -> DatasetStatisticsEngine.compute(
                             request, DatasetStatisticsEngine.Scope.ENTIRE_DATASET, cancelled,
                             (processed, total, phase) -> cancelled.set(true)));
        }
    }

    @Test
    void boundedBlocksKeepEntireDatasetReadsBelowTheLegacyBudget()
    {
        for (DatasetSearchEngine.Block block : DatasetSearchEngine.buildBlocks(
                 new long[] {1000, 1000, 8}, DatasetStatisticsEngine.STATISTICS_BLOCK_ELEMENTS))
            assertTrue(block.getElementCount() <= DatasetStatisticsEngine.STATISTICS_BLOCK_ELEMENTS);
    }

    @Test
    void currentPageAndEntireDatasetKeepRankThreeFramesSeparate() throws Exception
    {
        try (OpenedFile opened = openFixture("tframeselection.h5")) {
            Dataset dataset = firstNumeric(opened.file, 3);
            assertTrue(dataset != null, "the frame fixture must contain a rank-three numeric Dataset");
            dataset.init();
            long[] dims = dataset.getDims();
            int[] selectedIndex = dataset.getSelectedIndex();
            long[] start = dataset.getStartDims();
            long[] count = dataset.getSelectedDims();
            long[] stride = dataset.getStride();
            assertTrue(selectedIndex != null && selectedIndex.length >= 3);

            int frameDimension = selectedIndex[2];
            if (dims[frameDimension] > 1)
                start[frameDimension] = 1;
            count[frameDimension] = 1;
            Arrays.fill(stride, 1L);
            dataset.clearData();
            Object page = dataset.getData();

            DatasetSearchSnapshot current = new DatasetSearchSnapshot(
                opened.file.getFilePath(), dataset.getFullName(), page, start, count, stride,
                dims, dataset.getDatatype());
            DatasetStatisticsEngine.Request request = new DatasetStatisticsEngine.Request(
                dataset, current, null, dataset.getFillValue());
            DatasetStatisticsEngine.Result currentResult = DatasetStatisticsEngine.compute(
                request, DatasetStatisticsEngine.Scope.CURRENT_PAGE, new AtomicBoolean(), null);
            DatasetStatisticsEngine.Result entireResult = DatasetStatisticsEngine.compute(
                request, DatasetStatisticsEngine.Scope.ENTIRE_DATASET, new AtomicBoolean(), null);

            assertEquals(product(count), currentResult.getTotal());
            assertEquals(product(dims), entireResult.getTotal());
            assertTrue(entireResult.getTotal() >= currentResult.getTotal());
        }
    }

    private static Dataset firstNumeric(FileFormat file) throws Exception
    {
        return firstNumeric(file, 0);
    }

    private static Dataset firstNumeric(FileFormat file, int minimumRank) throws Exception
    {
        Group root = (Group)file.getRootObject();
        for (HObject object : root.depthFirstMemberList()) {
            if (!(object instanceof Dataset))
                continue;
            Dataset dataset = (Dataset)object;
            dataset.init();
            Datatype datatype = scalarDatatype(dataset.getDatatype());
            if (datatype != null && (datatype.isInteger() || datatype.isFloat()) &&
                dataset.getRank() >= minimumRank)
                return dataset;
        }
        return null;
    }

    private static Dataset firstText(FileFormat file) throws Exception
    {
        Group root = (Group)file.getRootObject();
        for (HObject object : root.depthFirstMemberList()) {
            if (!(object instanceof Dataset))
                continue;
            Dataset dataset = (Dataset)object;
            dataset.init();
            Datatype datatype = scalarDatatype(dataset.getDatatype());
            if (datatype != null && (datatype.isString() || datatype.isChar()))
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

    private static long product(long[] values)
    {
        long result = 1;
        for (long value : values)
            result *= value;
        return result;
    }

    private static OpenedFile openFixture(String name) throws Exception
    {
        Path path;
        try {
            path = Paths.get(DatasetStatisticsEngineTest.class.getClassLoader()
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
