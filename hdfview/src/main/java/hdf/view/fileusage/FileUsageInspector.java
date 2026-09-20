package hdf.view.fileusage;

import java.nio.file.Path;

/**
 * Inspects the applications and services that Windows reports as using a file.
 *
 * <p>The interface deliberately describes resource usage rather than read or
 * write locks. Restart Manager does not expose that distinction.</p>
 */
public interface FileUsageInspector {
    FileUsageScanResult scan(Path file) throws FileUsageException;

    void requestClose(Path file, FileUsageProcess target) throws FileUsageException;

    void forceTerminate(Path file, FileUsageProcess target) throws FileUsageException;
}
