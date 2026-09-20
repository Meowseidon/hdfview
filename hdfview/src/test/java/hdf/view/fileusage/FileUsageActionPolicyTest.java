package hdf.view.fileusage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUsageActionPolicyTest {
    private static final long CURRENT_PID = 2000;

    @Test
    void currentHdfViewCannotBeForceTerminated() {
        FileUsageProcess process = process(CURRENT_PID, FileUsageType.MAIN_WINDOW, true, true);

        assertEquals(
                FileUsageActionPolicy.BlockReason.CURRENT_HDFVIEW,
                FileUsageActionPolicy.forceTerminateBlockReason(process, CURRENT_PID, true));
        assertFalse(FileUsageActionPolicy.canForceTerminate(process, CURRENT_PID, true));
    }

    @Test
    void servicesAndCriticalProcessesCannotBeTerminated() {
        FileUsageProcess service = process(2001, FileUsageType.SERVICE, false, false);
        FileUsageProcess critical = process(2002, FileUsageType.CRITICAL, false, false);

        assertEquals(
                FileUsageActionPolicy.BlockReason.SERVICE,
                FileUsageActionPolicy.forceTerminateBlockReason(service, CURRENT_PID, true));
        assertEquals(
                FileUsageActionPolicy.BlockReason.CRITICAL_SYSTEM_PROCESS,
                FileUsageActionPolicy.forceTerminateBlockReason(critical, CURRENT_PID, true));
        assertFalse(FileUsageActionPolicy.canForceTerminate(service, CURRENT_PID, true));
        assertFalse(FileUsageActionPolicy.canForceTerminate(critical, CURRENT_PID, true));
    }

    @Test
    void invalidOrExitedProcessesAreBlocked() {
        FileUsageProcess invalid = process(0, FileUsageType.MAIN_WINDOW, false, false);
        FileUsageProcess exited = process(2003, FileUsageType.MAIN_WINDOW, false, false);

        assertEquals(
                FileUsageActionPolicy.BlockReason.INVALID_PID,
                FileUsageActionPolicy.forceTerminateBlockReason(invalid, CURRENT_PID, true));
        assertEquals(
                FileUsageActionPolicy.BlockReason.NOT_RUNNING,
                FileUsageActionPolicy.forceTerminateBlockReason(exited, CURRENT_PID, false));
    }

    @Test
    void requestCloseRequiresVisibleTitledWindow() {
        FileUsageProcess process = process(2004, FileUsageType.MAIN_WINDOW, false, false);

        assertEquals(
                FileUsageActionPolicy.BlockReason.NO_VISIBLE_WINDOW,
                FileUsageActionPolicy.requestCloseBlockReason(process, CURRENT_PID, true));

        FileUsageProcess withWindow = new FileUsageProcess(
                process.identity(), process.processName(), process.executablePath(),
                List.of("HDFView - test.h5"), process.type(), false, true);
        assertTrue(FileUsageActionPolicy.canRequestClose(withWindow, CURRENT_PID, true));
    }

    private static FileUsageProcess process(
            long pid, FileUsageType type, boolean current, boolean visibleWindow) {
        return new FileUsageProcess(
                new ProcessIdentity(pid, 1234),
                "test-process.exe",
                "C:\\test\\test-process.exe",
                visibleWindow ? List.of("test") : List.of(),
                type,
                current,
                true);
    }
}
