package hdf.view.fileusage;

import java.nio.file.Path;

/** Provider used on non-Windows systems or when the Windows binding cannot load. */
final class UnavailableFileUsageInspector implements FileUsageInspector {
    private final String reasonKey;
    private final Object[] reasonArgs;

    UnavailableFileUsageInspector(String reasonKey, Object... reasonArgs) {
        this.reasonKey = reasonKey;
        this.reasonArgs = reasonArgs.clone();
    }

    @Override
    public FileUsageScanResult scan(Path file) throws FileUsageException {
        throw FileUsageException.localized(reasonKey, reasonArgs);
    }

    @Override
    public void requestClose(Path file, FileUsageProcess target) throws FileUsageException {
        throw FileUsageException.localized(reasonKey, reasonArgs);
    }

    @Override
    public void forceTerminate(Path file, FileUsageProcess target) throws FileUsageException {
        throw FileUsageException.localized(reasonKey, reasonArgs);
    }
}
