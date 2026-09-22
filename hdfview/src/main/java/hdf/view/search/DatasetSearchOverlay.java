/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * All rights reserved.                                                       *
 *****************************************************************************/

package hdf.view.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Block-local index for transient dirty Dataset snapshots.
 *
 * <p>Rectangular snapshots use the selection intersection iterator in
 * {@link DatasetSearchSnapshot}.  Sparse snapshots, such as a TableView with
 * one edited cell, can either be assigned to a compatibility block index or
 * intersected with one streamed block at a time.  Neither mode materializes
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

    private static final class BlockKey {
        private final long[] start;
        private final int hash;

        private BlockKey(long[] start)
        {
            this.start = start.clone();
            this.hash = Arrays.hashCode(this.start);
        }

        @Override
        public int hashCode() { return hash; }

        @Override
        public boolean equals(Object other)
        {
            return other instanceof BlockKey &&
                   Arrays.equals(start, ((BlockKey)other).start);
        }
    }

    private final List<DatasetSearchSnapshot> snapshots;
    private final List<DatasetSearchEngine.Block> blocks;
    private final long[][] blockStarts;
    private final long[][] blockCounts;
    private final List<List<SparseEntry>> sparseEntries;
    private final List<Map<BlockKey, List<SparseEntry>>> streamedSparseEntries;

    /**
     * Build an overlay for a streamed block sequence.  This constructor does
     * not retain or enumerate any Dataset blocks; sparse entries are checked
     * against the block supplied to {@link #forEachValue(DatasetSearchEngine.Block,
     * int, ValueVisitor)} as it is consumed.
     */
    public DatasetSearchOverlay(List<DatasetSearchSnapshot> snapshots)
    {
        this.snapshots = snapshots == null
            ? Collections.emptyList() : new ArrayList<>(snapshots);
        this.blocks = null;
        this.blockStarts = new long[0][];
        this.blockCounts = new long[0][];
        this.sparseEntries = Collections.emptyList();
        this.streamedSparseEntries = null;
    }

    /**
     * Build an overlay index for a streamed block sequence.  The index groups
     * sparse dirty cells by the tile start that contains them, so consuming a
     * block performs one map lookup rather than scanning every dirty cell.
     * Only explicitly changed cells are retained in this index.
     */
    public DatasetSearchOverlay(List<DatasetSearchSnapshot> snapshots,
                                long[] datasetDims, long maxElements)
    {
        this.snapshots = snapshots == null
            ? Collections.emptyList() : new ArrayList<>(snapshots);
        this.blocks = null;
        this.blockStarts = new long[0][];
        this.blockCounts = new long[0][];
        this.sparseEntries = Collections.emptyList();
        this.streamedSparseEntries = new ArrayList<>(this.snapshots.size());

        long[] dims = datasetDims == null ? null : datasetDims.clone();
        long[] tile = DatasetSearchEngine.blockTile(dims, maxElements);
        indexStreamedSparseSnapshots(dims, tile);
    }

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
        this.streamedSparseEntries = null;
        indexSparseSnapshots();
    }

    /** Number of values actually presented to {@code visitor}. */
    public long forEachValue(int blockIndex, int valuesPerCell, ValueVisitor visitor)
    {
        if (visitor == null || blocks == null || blockIndex < 0 || blockIndex >= blocks.size())
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

    /** Number of values actually presented for one streamed block. */
    public long forEachValue(DatasetSearchEngine.Block block, int valuesPerCell,
                             ValueVisitor visitor)
    {
        if (block == null || visitor == null)
            return 0;

        long[] blockStart = block.getStart();
        long[] blockCount = block.getCount();
        BlockKey blockKey = null;
        long visited = 0;
        for (int snapshotIndex = 0; snapshotIndex < snapshots.size(); snapshotIndex++) {
            DatasetSearchSnapshot snapshot = snapshots.get(snapshotIndex);
            if (snapshot == null || snapshot.getValuesPerCell() != valuesPerCell)
                continue;

            if (snapshot.isSparse()) {
                List<SparseEntry> entries = null;
                if (streamedSparseEntries != null) {
                    Map<BlockKey, List<SparseEntry>> byBlock =
                        streamedSparseEntries.get(snapshotIndex);
                    if (byBlock.isEmpty())
                        continue;
                    if (blockKey == null)
                        blockKey = new BlockKey(blockStart);
                    entries = byBlock.get(blockKey);
                    if (entries == null)
                        continue;
                }

                boolean[] completed = {true};
                visited += visitSparseEntries(snapshot, entries, blockStart, blockCount,
                                              valuesPerCell, visitor, completed);
                if (!completed[0])
                    return visited;
            }
            else {
                boolean[] completed = {true};
                long denseVisited = snapshot.forEachDenseValueInBlock(
                    blockStart, blockCount, valuesPerCell,
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

    private long visitSparseEntries(DatasetSearchSnapshot snapshot,
                                    List<SparseEntry> indexedEntries,
                                    long[] blockStart, long[] blockCount,
                                    int valuesPerCell, ValueVisitor visitor,
                                    boolean[] completed)
    {
        int rank = snapshot.getCount() == null ? 0 : snapshot.getCount().length;
        int sparseCellCount = indexedEntries == null
            ? snapshot.getSparseCellCount() : indexedEntries.size();
        int valueCount = snapshot.getValueCount();
        long visited = 0;
        for (int entryIndex = 0; entryIndex < sparseCellCount; entryIndex++) {
            int cellIndex = indexedEntries == null
                ? entryIndex : indexedEntries.get(entryIndex).cellIndex;
            int blockCellIndex = indexedEntries == null
                ? localCellIndex(snapshot, cellIndex, rank, blockStart, blockCount)
                : indexedEntries.get(entryIndex).blockCellIndex;
            if (blockCellIndex < 0)
                continue;

            long snapshotValueBase = (long)cellIndex * valuesPerCell;
            long blockValueBase = (long)blockCellIndex * valuesPerCell;
            if (snapshotValueBase < 0 || blockValueBase < 0 ||
                snapshotValueBase >= valueCount ||
                snapshotValueBase > Integer.MAX_VALUE ||
                blockValueBase > Integer.MAX_VALUE)
                continue;

            int available = (int)Math.min((long)valuesPerCell,
                                          valueCount - snapshotValueBase);
            for (int scalar = 0; scalar < available; scalar++) {
                if (!visitor.visit(snapshot, (int)blockValueBase + scalar,
                                   (int)snapshotValueBase + scalar)) {
                    completed[0] = false;
                    return visited;
                }
                visited++;
            }
        }
        return visited;
    }

    private void indexStreamedSparseSnapshots(long[] datasetDims, long[] tile)
    {
        for (DatasetSearchSnapshot snapshot : snapshots) {
            Map<BlockKey, List<SparseEntry>> byBlock = new HashMap<>();
            streamedSparseEntries.add(byBlock);
            if (snapshot == null || !snapshot.isSparse() || datasetDims == null || tile == null)
                continue;

            int rank = snapshot.getCount() == null ? 0 : snapshot.getCount().length;
            if (datasetDims.length != rank || tile.length != rank)
                continue;

            int sparseCellCount = snapshot.getSparseCellCount();
            for (int cellIndex = 0; cellIndex < sparseCellCount; cellIndex++) {
                long[] blockStart = new long[rank];
                boolean valid = true;
                for (int dimension = 0; dimension < rank; dimension++) {
                    long coordinate = snapshot.sparseCoordinateAt(cellIndex, dimension);
                    if (coordinate < 0 || coordinate >= datasetDims[dimension] ||
                        tile[dimension] <= 0) {
                        valid = false;
                        break;
                    }
                    blockStart[dimension] = (coordinate / tile[dimension]) * tile[dimension];
                }
                if (!valid)
                    continue;

                long[] blockCount = new long[rank];
                for (int dimension = 0; dimension < rank; dimension++)
                    blockCount[dimension] = Math.min(tile[dimension],
                                                     datasetDims[dimension] - blockStart[dimension]);
                int localCell = localCellIndex(snapshot, cellIndex, rank,
                                               blockStart, blockCount);
                if (localCell < 0)
                    continue;

                BlockKey key = new BlockKey(blockStart);
                List<SparseEntry> entries = byBlock.get(key);
                if (entries == null) {
                    entries = new ArrayList<>();
                    byBlock.put(key, entries);
                }
                entries.add(new SparseEntry(cellIndex, localCell));
            }
        }
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
        return localCellIndex(snapshot, cellIndex, rank, blockStart, blockCount);
    }

    private int localCellIndex(DatasetSearchSnapshot snapshot, int cellIndex, int rank,
                               long[] blockStart, long[] blockCount)
    {
        if (blockStart.length != rank || blockCount.length != rank)
            return rank == 0 && blockStart.length == 0 && blockCount.length == 0 ? 0 : -1;

        long localCell = 0;
        for (int dimension = 0; dimension < rank; dimension++) {
            long coordinate = snapshot.sparseCoordinateAt(cellIndex, dimension);
            long blockEnd = blockStart[dimension] > Long.MAX_VALUE - blockCount[dimension]
                ? Long.MAX_VALUE : blockStart[dimension] + blockCount[dimension];
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
