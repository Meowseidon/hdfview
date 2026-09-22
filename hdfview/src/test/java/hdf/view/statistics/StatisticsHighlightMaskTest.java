/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * All rights reserved.                                                       *
 *****************************************************************************/

package hdf.view.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** Deterministic regressions for the statistics highlight update boundary. */
class StatisticsHighlightMaskTest
{
    @Test
    void changingOneCellRemovesOrAddsOnlyThatCell()
    {
        StatisticsHighlightMask mask = new StatisticsHighlightMask();
        mask.updateCell(DatasetStatisticsEngine.Kind.POSITIVE, 1, 2, 4.0);
        mask.updateCell(DatasetStatisticsEngine.Kind.POSITIVE, 4, 5, 8.0);

        mask.updateCell(DatasetStatisticsEngine.Kind.POSITIVE, 1, 2, -3.0);
        assertFalse(mask.contains(1, 2), "a highlighted cell must be removed when it no longer matches");
        assertTrue(mask.contains(4, 5), "an unrelated highlighted cell must remain unchanged");

        mask.updateCell(DatasetStatisticsEngine.Kind.POSITIVE, 1, 2, 6.0);
        assertTrue(mask.contains(1, 2), "a newly matching cell must be added");
        assertTrue(mask.contains(4, 5), "adding a cell must not alter another membership");
        assertEquals(2, mask.size());
    }

    @Test
    void ordinarySingleCellUpdateReadsNoOtherPageCells()
    {
        StatisticsHighlightMask mask = new StatisticsHighlightMask();
        AtomicInteger reads = new AtomicInteger();

        mask.updateCell(DatasetStatisticsEngine.Kind.NON_ZERO, 37, 19, (column, row) -> {
            assertEquals(37, column);
            assertEquals(19, row);
            reads.incrementAndGet();
            return 1.0;
        });

        assertEquals(1, reads.get(),
                     "the common update path must read only the event cell");
        assertTrue(mask.contains(37, 19));
    }

    @Test
    void editorNoEventFallbackCanReplaceTheCapturedCell()
    {
        StatisticsHighlightMask mask = new StatisticsHighlightMask();
        mask.updateCell(DatasetStatisticsEngine.Kind.POSITIVE, 2, 3, 9.0);

        mask.updateCell(DatasetStatisticsEngine.Kind.POSITIVE, 2, 3,
                        (column, row) -> -1.0);

        assertFalse(mask.contains(2, 3),
                    "the no-event editor fallback must still update membership");
    }

    @Test
    void fullRefreshStillRecomputesEveryCellForAFrameOrPageChange()
    {
        StatisticsHighlightMask mask = new StatisticsHighlightMask();
        AtomicInteger reads = new AtomicInteger();

        mask.refreshAll(DatasetStatisticsEngine.Kind.ZERO, 3, 4, (column, row) -> {
            reads.incrementAndGet();
            return column == row ? 0.0 : 1.0;
        });
        assertEquals(12, reads.get());
        assertEquals(3, mask.size());

        reads.set(0);
        mask.refreshAll(DatasetStatisticsEngine.Kind.ZERO, 3, 4, (column, row) -> {
            reads.incrementAndGet();
            return column == 0 ? 0.0 : 1.0;
        });
        assertEquals(12, reads.get(),
                     "a frame/page change must retain the explicit full rebuild path");
        assertEquals(3, mask.size());
        assertTrue(mask.contains(0, 0));
        assertTrue(mask.contains(0, 1));
        assertTrue(mask.contains(0, 2));
        assertFalse(mask.contains(1, 1));
    }

    @Test
    void scalarAndBooleanComparisonUsesTheStatisticsEngineRules()
    {
        StatisticsHighlightMask mask = new StatisticsHighlightMask();
        mask.updateCell(DatasetStatisticsEngine.Kind.ZERO, 0, 0, false);
        mask.updateCell(DatasetStatisticsEngine.Kind.POSITIVE, 1, 0, true);
        mask.updateCell(DatasetStatisticsEngine.Kind.NEGATIVE, 2, 0, -1);

        assertTrue(mask.contains(0, 0));
        assertTrue(mask.contains(1, 0));
        assertTrue(mask.contains(2, 0));
    }
}
