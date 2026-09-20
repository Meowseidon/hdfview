package hdf.view.fileusage;

import java.nio.file.Path;
import java.util.List;

/** A successful, point-in-time Restart Manager query. */
public final class FileUsageScanResult {
    private final Path file;
    private final List<FileUsageProcess> processes;

    public FileUsageScanResult(Path file, List<FileUsageProcess> processes) {
        this.file = file;
        this.processes = processes == null ? List.of() : List.copyOf(processes);
    }

    public Path file() {
        return file;
    }

    public List<FileUsageProcess> processes() {
        return processes;
    }

    public long externalProcessCount() {
        return processes.stream().filter(process -> !process.currentHdfView()).count();
    }
}
