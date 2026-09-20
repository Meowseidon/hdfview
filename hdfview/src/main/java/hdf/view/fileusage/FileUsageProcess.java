package hdf.view.fileusage;

import java.util.List;

/** One application or service returned by Restart Manager. */
public final class FileUsageProcess {
    private final ProcessIdentity identity;
    private final String processName;
    private final String executablePath;
    private final List<String> windowTitles;
    private final FileUsageType type;
    private final boolean currentHdfView;
    private final boolean restartable;

    public FileUsageProcess(
            ProcessIdentity identity,
            String processName,
            String executablePath,
            List<String> windowTitles,
            FileUsageType type,
            boolean currentHdfView,
            boolean restartable) {
        this.identity = identity;
        this.processName = processName == null || processName.isBlank() ? "" : processName;
        this.executablePath = executablePath == null || executablePath.isBlank() ? null : executablePath;
        this.windowTitles = windowTitles == null ? List.of() : List.copyOf(windowTitles);
        this.type = type == null ? FileUsageType.UNKNOWN : type;
        this.currentHdfView = currentHdfView;
        this.restartable = restartable;
    }

    public ProcessIdentity identity() {
        return identity;
    }

    public String processName() {
        return processName;
    }

    public String executablePath() {
        return executablePath;
    }

    public List<String> windowTitles() {
        return windowTitles;
    }

    public String primaryWindowTitle() {
        return windowTitles.isEmpty() ? "" : windowTitles.get(0);
    }

    public FileUsageType type() {
        return type;
    }

    public boolean currentHdfView() {
        return currentHdfView;
    }

    public boolean restartable() {
        return restartable;
    }
}
