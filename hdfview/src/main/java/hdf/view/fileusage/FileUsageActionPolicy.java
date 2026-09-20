package hdf.view.fileusage;

/** Safety checks shared by the UI and the native provider before termination. */
public final class FileUsageActionPolicy {
    private FileUsageActionPolicy() {
    }

    public enum BlockReason {
        NONE,
        INVALID_PID,
        CURRENT_HDFVIEW,
        CRITICAL_SYSTEM_PROCESS,
        SERVICE,
        NOT_RUNNING,
        NO_VISIBLE_WINDOW
    }

    public static BlockReason forceTerminateBlockReason(
            FileUsageProcess process, long currentPid, boolean alive) {
        BlockReason common = commonBlockReason(process, currentPid, alive);
        return common;
    }

    public static BlockReason requestCloseBlockReason(
            FileUsageProcess process, long currentPid, boolean alive) {
        BlockReason common = commonBlockReason(process, currentPid, alive);
        if (common != BlockReason.NONE) {
            return common;
        }
        return process.windowTitles().isEmpty() ? BlockReason.NO_VISIBLE_WINDOW : BlockReason.NONE;
    }

    public static boolean canForceTerminate(FileUsageProcess process, long currentPid, boolean alive) {
        return forceTerminateBlockReason(process, currentPid, alive) == BlockReason.NONE;
    }

    public static boolean canRequestClose(FileUsageProcess process, long currentPid, boolean alive) {
        return requestCloseBlockReason(process, currentPid, alive) == BlockReason.NONE;
    }

    private static BlockReason commonBlockReason(
            FileUsageProcess process, long currentPid, boolean alive) {
        if (process == null || process.identity() == null || process.identity().pid() <= 0) {
            return BlockReason.INVALID_PID;
        }
        if (process.currentHdfView() || process.identity().pid() == currentPid) {
            return BlockReason.CURRENT_HDFVIEW;
        }
        if (process.type().isCritical()) {
            return BlockReason.CRITICAL_SYSTEM_PROCESS;
        }
        if (process.type().isService()) {
            return BlockReason.SERVICE;
        }
        return alive ? BlockReason.NONE : BlockReason.NOT_RUNNING;
    }
}
