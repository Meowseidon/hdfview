package hdf.view.fileusage;

/** Creates the platform provider without initializing Windows native code elsewhere. */
public final class FileUsageInspectorFactory {
    private FileUsageInspectorFactory() {
    }

    public static FileUsageInspector create() {
        if (!isWindows()) {
            return new UnavailableFileUsageInspector("dialog.fileUsage.error.unsupported");
        }
        try {
            return new WindowsFileUsageInspector();
        } catch (LinkageError | RuntimeException error) {
            String detail = error.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = error.getClass().getSimpleName();
            }
            return new UnavailableFileUsageInspector(
                    "dialog.fileUsage.error.nativeUnavailable", detail);
        }
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
