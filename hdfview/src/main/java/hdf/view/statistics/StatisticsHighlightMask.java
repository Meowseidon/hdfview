/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * All rights reserved.                                                       *
 *****************************************************************************/

package hdf.view.statistics;

import java.util.HashSet;
import java.util.Set;

/**
 * The current-page membership mask used by the statistics highlight layer.
 *
 * <p>The mask deliberately knows nothing about NatTable.  A full rebuild is
 * used when the displayed page changes, while an editor update can read and
 * replace one cell without making the caller walk the rest of the page.</p>
 */
public final class StatisticsHighlightMask
{
    @FunctionalInterface
    public interface CellValueReader
    {
        Object read(int column, int row);
    }

    private final Set<Long> cells = new HashSet<>();

    public void clear()
    {
        cells.clear();
    }

    public void add(int column, int row)
    {
        cells.add(cellKey(column, row));
    }

    public boolean contains(int column, int row)
    {
        return cells.contains(cellKey(column, row));
    }

    public int size()
    {
        return cells.size();
    }

    /**
     * Replace one cell's membership using an already-read value.
     *
     * @return whether the membership changed
     */
    public boolean updateCell(DatasetStatisticsEngine.Kind kind, int column, int row,
                              Object value)
    {
        long key = cellKey(column, row);
        boolean matches = kind != null && value != null &&
                          DatasetStatisticsEngine.matches(value, kind);
        if (matches)
            return cells.add(key);
        return cells.remove(key);
    }

    /**
     * Read exactly the requested cell and replace its membership.
     *
     * <p>This is the common single-cell update path.  Keeping the value read
     * here makes the one-read contract directly testable without constructing
     * a SWT/NatTable view.</p>
     */
    public boolean updateCell(DatasetStatisticsEngine.Kind kind, int column, int row,
                              CellValueReader reader)
    {
        if (kind == null)
            return updateCell(null, column, row, null);
        return updateCell(kind, column, row, reader.read(column, row));
    }

    /** Rebuild the mask for every cell in the displayed page. */
    public void refreshAll(DatasetStatisticsEngine.Kind kind, int rows, int columns,
                           CellValueReader reader)
    {
        clear();
        if (kind == null)
            return;

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++)
                updateCell(kind, column, row, reader.read(column, row));
        }
    }

    private static long cellKey(int column, int row)
    {
        return (((long)row) << 32) ^ (column & 0xffffffffL);
    }
}
