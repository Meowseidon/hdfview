package hdf.view.fileusage;

/**
 * The Windows identity of a process. A PID by itself is not stable because
 * Windows can reuse it after a process exits.
 *
 * @param pid Windows process identifier
 * @param startTime100ns Windows FILETIME process start time in 100 ns ticks
 */
public record ProcessIdentity(long pid, long startTime100ns) {
}
