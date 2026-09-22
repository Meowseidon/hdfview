/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * All rights reserved.                                                       *
 *****************************************************************************/

package hdf.view.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Block-local index for transient dirty Dataset snapshots.
 *
 * <p>Rectangular snapshots use the selection intersection iterator in
 * {@link DatasetSearchSnapshot}.  Sparse snapshots, such as a TableView with
 * one edited cell, are assigned to the bounded blocks once when this index is
 * built.  The index stores only dirty-cell references; it never materializes
 * a Dataset-wide coordinate/value map.</p>
 */
public final class DatasetSearchOverlay {
    /** Callback receives a block scalar index and a snapshot scalar index. */
    @FunctionalInterface
    public interface ValueVisitor
    {
        /** Return {@code false} to stop the traversal early. */
        boolean visit(DatasetSearchSnapshot snapshot, int blockValueIndex, int snapshotValueIndex);
    }

    private static final class SparseEntry {
        private final int cellIndex;
        private final int blockCellIndex;

        private SparseEntry(int cellIndex, int blockCellIndex)
        {
            this.cellIndex      = cellIndex;
            this.blockCellIndex = blockCellIndex;
        }
    }

    private final List<DatasetSearchSnapshot> snapshots;
    private final List<DatasetSearchEngine.Block> blocks;
    private final long[][] blockStarts;
    private final long[][] blockCounts;
    private final List<List<SparseEntry>> sparseEntries;

    /** Build one reusable overlay index for all passes over the same blocks. */
    public DatasetSearchOverlay(List<DatasetSearchSnapshot> snapshots,
                                List<DatasetSearchEngine.Block> blocks)
    {
        this.snapshots = snapshots == null
            ? Collections.emptyList() : new ArrayList<>(snapshots);
        this.blocks = blocks == null ? Collections.emptyList() : new ArrayList<>(blocks);
        this.blockStarts = new long[this.blocks.size()][];
        this.blockCounts = new long[this.blocks.size()][];
        for (int blockIndex = 0; blockIndex < this.blocks.size(); blockIndex++) {
            this.blockStarts[blockIndex] = this.blocks.get(blockIndex).getStart();
            this.blockCounts[blockIndex] = this.blocks.get(blockIndex).getCount();
        }

        this.sparseEntries = new ArrayList<>(this.snapshots.size() * this.blocks.size());
        for (int i = 0; i < this.snapshots.size() * this.blocks.size(); i++)
            this.sparseEntries.add(null);
        indexSparseSnapshots();
    }

    /** Number of values actually presented to {@code visitor}. */
    public long forEachValue(int blockIndex, int valuesPerCell, ValueVisitor visitor)
    {
        if (visitor == null || blockIndex < 0 || blockIndex >= blocks.size())
            return 0;

        long visited = 0;
        for (int snapshotIndex = 0; snapshotIndex < snapshots.size(); snapshotIndex++) {
            DatasetSearchSnapshot snapshot = snapshots.get(snapshotIndex);
            if (snapshot == null || snapshot.getValuesPerCell() != valuesPerCell)
                continue;

            if (snapshot.isSparse()) {
                List<SparseEntry> entries = sparseEntries.get(entryIndex(snapshotIndex, blockIndex));
                if (entries == null)
                    continue;

                int valueCount = snapshot.getValueCount();
                for (SparseEntry entry : entries) {
                    long snapshotValueBase = (long)entry.cellIndex * valuesPerCell;
                    long blockValueBase = (long)entry.blockCellIndex * valuesPerCell;
                    if (snapshotValueBase < 0 || blockValueBase < 0 ||
                        snapshotValueBase >= valueCount ||
                        snapshotValueBase > Integer.MAX_VALUE ||
                        blockValueBase > Integer.MAX_VALUE)
                        continue;

                    int available = (int)Math.min((long)valuesPerCell,
                                                  valueCount - snapshotValueBase);
                    for (int scalar = 0; scalar < available; scalar++) {
                        if (!visitor.visit(snapshot, (int)blockValueBase + scalar,
                                           (int)snapshotValueBase + scalar))
                            return visited;
                        visited++;
                    }
                }
            }
            else {
                boolean[] completed = {true};
                long denseVisited = snapshot.forEachDenseValueInBlock(
                    blockStarts[blockIndex], blockCounts[blockIndex], valuesPerCell,
                    (blockValueIndex, snapshotValueIndex) ->
                    {
                        boolean keepGoing = visitor.visit(snapshot, blockValueIndex, snapshotValueIndex);
                        if (!keepGoing)
                            completed[0] = false;
                        return keepGoing;
                    });
                visited += denseVisited;
                if (!completed[0])
                    return visited;
            }
        }
        return visited;
    }

    private void indexSparseSnapshots()
    {
        for (int snapshotIndex = 0; snapshotIndex < snapshots.size(); snapshotIndex++) {
            DatasetSearchSnapshot snapshot = snapshots.get(snapshotIndex);
            if (snapshot == null || !snapshot.isSparse())
                continue;

            int rank = snapshot.getCount() == null ? 0 : snapshot.getCount().length;
            int sparseCellCount = snapshot.getSparseCellCount();
            for (int cellIndex = 0; cellIndex < sparseCellCount; cellIndex++) {
                for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
                    int localCell = localCellIndex(snapshot, cellIndex, rank, blockIndex);
                    if (localCell < 0)
                        continue;
                    int index = entryIndex(snapshotIndex, blockIndex);
                    List<SparseEntry> entries = sparseEntries.get(index);
                    if (entries == null) {
                        entries = new ArrayList<>();
                        sparseEntries.set(index, entries);
                    }
                    entries.add(new SparseEntry(cellIndex, localCell));
                    break;
                }
            }
        }
    }

    private int localCellIndex(DatasetSearchSnapshot snapshot, int cellIndex, int rank,
                               int blockIndex)
    {
        long[] blockStart = blockStarts[blockIndex];
        long[] blockCount = blockCounts[blockIndex];
        if (blockStart.length != rank || blockCount.length != rank)
            return rank == 0 && blockStart.length == 0 && blockCount.length == 0 ? 0 : -1;

        long localCell = 0;
        for (int dimension = 0; dimension < rank; dimension++) {
            long coordinate = snapshot.sparseCoordinateAt(cellIndex, dimension);
            long blockEnd = blockStart[dimension] + blockCount[dimension];
            if (coordinate < blockStart[dimension] || coordinate >= blockEnd)
                return -1;
            long local = coordinate - blockStart[dimension];
            if (localCell > Integer.MAX_VALUE / Math.max(1L, blockCount[dimension]))
                return -1;
            localCell = localCell * blockCount[dimension] + local;
        }
        return localCell > Integer.MAX_VALUE ? -1 : (int)localCell;
    }

    private int entryIndex(int snapshotIndex, int blockIndex)
    {
        return snapshotIndex * blocks.size() + blockIndex;
    }
}
