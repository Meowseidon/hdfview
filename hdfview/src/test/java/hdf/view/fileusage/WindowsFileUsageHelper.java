package hdf.view.fileusage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Small independent process used by the Windows Restart Manager integration test. */
public final class WindowsFileUsageHelper {
    private WindowsFileUsageHelper() {
    }

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[0]);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            FileLock lock = null;
            try {
                lock = channel.tryLock();
            } catch (java.nio.channels.OverlappingFileLockException ignored) {
                // The handle is still useful for Restart Manager even if a lock cannot be acquired.
            }
            try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in))) {
                System.out.println("READY");
                System.out.flush();
                input.readLine();
            } finally {
                if (lock != null && lock.isValid()) {
                    lock.release();
                }
            }
        }
    }
}
