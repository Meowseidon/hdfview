package hdf.view.fileusage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.WINDOWS)
class WindowsFileUsageInspectorIntegrationTest {
    @Test
    void reportsCurrentProcessAndNoExternalUsage() throws Exception {
        Path file = Files.createTempFile("hdfview-file-usage-self-", ".h5");
        try (FileChannel ignored = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            FileUsageInspector inspector = FileUsageInspectorFactory.create();
            long currentPid = ProcessHandle.current().pid();
            FileUsageProcess current = awaitProcess(
                    inspector,
                    file,
                    process -> process.identity().pid() == currentPid && process.currentHdfView(),
                    Duration.ofSeconds(8));

            assertNotNull(current);
            assertTrue(current.currentHdfView());
            assertEquals(0, inspector.scan(file).externalProcessCount());
            assertEquals(
                    FileUsageActionPolicy.BlockReason.CURRENT_HDFVIEW,
                    FileUsageActionPolicy.forceTerminateBlockReason(current, currentPid, true));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void findsHelperPidValidatesStartTimeAndRemovesItAfterForceTermination() throws Exception {
        Path file = Files.createTempFile("hdfview-file-usage-", ".h5");
        Process helper = startHelper(file);
        try {
            long helperPid = helper.pid();
            FileUsageInspector inspector = FileUsageInspectorFactory.create();
            FileUsageProcess discovered = awaitProcess(
                    inspector,
                    file,
                    process -> process.identity().pid() == helperPid,
                    Duration.ofSeconds(8));
            assertNotNull(discovered);

            FileUsageProcess staleIdentity = new FileUsageProcess(
                    new ProcessIdentity(helperPid, discovered.identity().startTime100ns() + 1),
                    discovered.processName(),
                    discovered.executablePath(),
                    discovered.windowTitles(),
                    discovered.type(),
                    false,
                    discovered.restartable());
            assertThrows(FileUsageException.class, () -> inspector.forceTerminate(file, staleIdentity));
            assertTrue(helper.isAlive(), "PID/start-time mismatch must not terminate the helper");

            inspector.forceTerminate(file, discovered);
            assertTrue(helper.waitFor(5, TimeUnit.SECONDS), "force termination should end the helper");
            awaitNoProcess(inspector, file, helperPid, Duration.ofSeconds(8));
        } finally {
            if (helper.isAlive()) {
                helper.destroyForcibly();
                helper.waitFor(5, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(file);
        }
    }

    private static Process startHelper(Path file) throws IOException {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        Process process = new ProcessBuilder(
                java.toString(),
                "-cp",
                System.getProperty("java.class.path"),
                WindowsFileUsageHelper.class.getName(),
                file.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String ready = output.readLine();
        assertEquals("READY", ready, "helper failed to announce readiness");
        return process;
    }

    private static FileUsageProcess awaitProcess(
            FileUsageInspector inspector,
            Path file,
            Predicate<FileUsageProcess> predicate,
            Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        FileUsageProcess found = null;
        while (System.nanoTime() < deadline) {
            FileUsageScanResult result = inspector.scan(file);
            Optional<FileUsageProcess> candidate = result.processes().stream().filter(predicate).findFirst();
            if (candidate.isPresent()) {
                found = candidate.get();
                break;
            }
            Thread.sleep(100);
        }
        return found;
    }

    private static void awaitNoProcess(
            FileUsageInspector inspector, Path file, long pid, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            boolean present = inspector.scan(file).processes().stream()
                    .anyMatch(process -> process.identity().pid() == pid);
            if (!present) {
                return;
            }
            Thread.sleep(100);
        }
        assertTrue(false, "terminated PID remained in Restart Manager results");
    }

}
