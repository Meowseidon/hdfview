package hdf.view.fileusage;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.PointerType;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Windows Restart Manager and User32 implementation of file-usage inspection.
 *
 * <p>This class is instantiated only by {@link FileUsageInspectorFactory} on
 * Windows. The native libraries are loaded by the constructor, not by the
 * platform-neutral model or UI classes.</p>
 */
public final class WindowsFileUsageInspector implements FileUsageInspector {
    private static final int ERROR_SUCCESS = 0;
    private static final int ERROR_MORE_DATA = 234;
    private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;
    private static final int WM_CLOSE = 0x0010;
    private static final int CCH_RM_SESSION_KEY = 32;
    private static final int MAX_RM_RESULTS_RETRIES = 4;
    private static final long CLOSE_WAIT_MILLIS = 1500;

    private final RestartManagerApi restartManager;
    private final Kernel32Api kernel32;
    private final User32Api user32;

    public WindowsFileUsageInspector() {
        restartManager = Native.load("rstrtmgr", RestartManagerApi.class);
        kernel32 = Native.load("kernel32", Kernel32Api.class);
        user32 = Native.load("user32", User32Api.class);
    }

    @Override
    public FileUsageScanResult scan(Path file) throws FileUsageException {
        Path normalizedFile = normalizeFile(file);
        IntByReference sessionHandle = new IntByReference();
        boolean started = false;
        FileUsageException failure = null;
        try {
            char[] sessionKey = new char[CCH_RM_SESSION_KEY + 1];
            int status = restartManager.RmStartSession(sessionHandle, 0, sessionKey);
            if (status != ERROR_SUCCESS) {
                throw nativeFailure("RmStartSession", status);
            }
            started = true;

            try (Memory resourceName = wideString(normalizedFile.toString())) {
                Pointer[] resources = {resourceName};
                status = restartManager.RmRegisterResources(
                        sessionHandle.getValue(), 1, resources, 0, null, 0, null);
            }
            if (status != ERROR_SUCCESS) {
                throw nativeFailure("RmRegisterResources", status);
            }

            return new FileUsageScanResult(normalizedFile, getProcesses(sessionHandle.getValue()));
        } catch (FileUsageException error) {
            failure = error;
            throw error;
        } finally {
            if (started) {
                int endStatus = restartManager.RmEndSession(sessionHandle.getValue());
                if (endStatus != ERROR_SUCCESS && failure == null) {
                    throw nativeFailure("RmEndSession", endStatus);
                }
            }
        }
    }

    @Override
    public void requestClose(Path file, FileUsageProcess target) throws FileUsageException {
        FileUsageProcess current = validateTarget(file, target);
        List<WindowHandleSnapshot> windows = enumerateWindows(current.identity().pid());
        if (windows.isEmpty()) {
            throw FileUsageException.localized("dialog.fileUsage.error.noWindow");
        }

        boolean sent = false;
        for (WindowHandleSnapshot window : windows) {
            if (user32.PostMessageW(window.handle(), WM_CLOSE, Pointer.NULL, Pointer.NULL)) {
                sent = true;
            }
        }
        if (!sent) {
            throw nativeFailure("PostMessageW(WM_CLOSE)", kernel32.GetLastError());
        }
        waitForExit(current.identity().pid(), CLOSE_WAIT_MILLIS);
    }

    @Override
    public void forceTerminate(Path file, FileUsageProcess target) throws FileUsageException {
        FileUsageProcess current = validateTarget(file, target);
        long pid = current.identity().pid();
        Optional<ProcessHandle> process = ProcessHandle.of(pid);
        if (process.isEmpty() || !process.get().isAlive()) {
            throw FileUsageException.localized("dialog.fileUsage.error.notRunning", pid);
        }

        boolean requested;
        try {
            requested = process.get().destroyForcibly();
        } catch (SecurityException error) {
            throw FileUsageException.localizedWithCause(
                    "dialog.fileUsage.error.forceDenied", error, exceptionDetail(error));
        }
        if (!requested) {
            throw FileUsageException.localized("dialog.fileUsage.error.forceNotAccepted", pid);
        }
        try {
            process.get().onExit().get(CLOSE_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw FileUsageException.localizedWithCause(
                    "dialog.fileUsage.error.forceInterrupted", error);
        } catch (java.util.concurrent.TimeoutException error) {
            throw FileUsageException.localizedWithCause(
                    "dialog.fileUsage.error.forceTimeout", error, pid);
        } catch (java.util.concurrent.ExecutionException error) {
            throw FileUsageException.localizedWithCause(
                    "dialog.fileUsage.error.forceFailed", error, pid,
                    exceptionDetail(error.getCause() == null ? error : error.getCause()));
        }
    }

    private FileUsageProcess validateTarget(Path file, FileUsageProcess target) throws FileUsageException {
        if (target == null || target.identity() == null || target.identity().pid() <= 0) {
            throw FileUsageException.localized("dialog.fileUsage.error.invalidPid");
        }

        FileUsageScanResult refreshed = scan(file);
        FileUsageProcess current = refreshed.processes().stream()
                .filter(process -> process.identity().equals(target.identity()))
                .findFirst()
                .orElseThrow(() -> FileUsageException.localized("dialog.fileUsage.error.stale"));

        long currentPid = ProcessHandle.current().pid();
        FileUsageActionPolicy.BlockReason reason = FileUsageActionPolicy.forceTerminateBlockReason(
                current, currentPid, isAlive(current.identity().pid()));
        if (reason != FileUsageActionPolicy.BlockReason.NONE) {
            throw FileUsageException.localized(actionBlockKey(reason));
        }

        long actualStart = queryProcessStartTime(current.identity().pid());
        if (actualStart != current.identity().startTime100ns()) {
            throw FileUsageException.localized("dialog.fileUsage.error.pidReused");
        }
        return current;
    }

    private boolean isAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    private String actionBlockKey(FileUsageActionPolicy.BlockReason reason) {
        return switch (reason) {
            case CURRENT_HDFVIEW -> "dialog.fileUsage.error.current";
            case CRITICAL_SYSTEM_PROCESS -> "dialog.fileUsage.error.critical";
            case SERVICE -> "dialog.fileUsage.error.service";
            case INVALID_PID -> "dialog.fileUsage.error.invalidPid";
            case NOT_RUNNING -> "dialog.fileUsage.error.notRunningAction";
            case NO_VISIBLE_WINDOW -> "dialog.fileUsage.error.noWindow";
            case NONE -> "";
        };
    }

    private List<FileUsageProcess> getProcesses(int session) throws FileUsageException {
        IntByReference needed = new IntByReference();
        IntByReference count = new IntByReference();
        IntByReference rebootReasons = new IntByReference();
        int status = restartManager.RmGetList(session, needed, count, null, rebootReasons);
        if (status != ERROR_SUCCESS && status != ERROR_MORE_DATA) {
            throw nativeFailure("RmGetList", status);
        }
        int capacity = needed.getValue();
        if (capacity <= 0) {
            return List.of();
        }

        for (int attempt = 0; attempt < MAX_RM_RESULTS_RETRIES; attempt++) {
            RMProcessInfo first = new RMProcessInfo();
            RMProcessInfo[] entries = (RMProcessInfo[]) first.toArray(capacity);
            IntByReference entryCount = new IntByReference(capacity);
            status = restartManager.RmGetList(
                    session, needed, entryCount, entries, rebootReasons);
            if (status == ERROR_MORE_DATA) {
                capacity = Math.max(capacity + 1, needed.getValue());
                continue;
            }
            if (status != ERROR_SUCCESS) {
                throw nativeFailure("RmGetList", status);
            }

            int resultCount = Math.min(entryCount.getValue(), entries.length);
            List<FileUsageProcess> result = new ArrayList<>(resultCount);
            for (int index = 0; index < resultCount; index++) {
                result.add(toProcess(entries[index]));
            }
            return result;
        }
        throw FileUsageException.localized("dialog.fileUsage.error.resultsLimit");
    }

    private FileUsageProcess toProcess(RMProcessInfo info) {
        long pid = Integer.toUnsignedLong(info.process.dwProcessId);
        ProcessIdentity identity = new ProcessIdentity(pid, fileTimeValue(info.process.processStartTime));
        String executablePath = queryExecutablePath(pid);
        String processName = executablePath == null ? null : fileName(executablePath);
        if (processName == null || processName.isBlank()) {
            processName = firstNonBlank(
                    nullTerminated(info.applicationName),
                    nullTerminated(info.serviceShortName),
                    "PID " + pid);
        }

        FileUsageType type = FileUsageType.fromRestartManagerValue(info.applicationType);
        boolean currentHdfView = pid == ProcessHandle.current().pid();
        return new FileUsageProcess(
                identity,
                processName,
                executablePath,
                WindowsWindowTitleMapper.visibleTitlesFor(pid, snapshotWindows(pid)),
                type,
                currentHdfView,
                info.restartable != 0);
    }

    private String queryExecutablePath(long pid) {
        Pointer handle = kernel32.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, (int) pid);
        if (handle == null) {
            return null;
        }
        try {
            char[] buffer = new char[32768];
            IntByReference length = new IntByReference(buffer.length);
            if (!kernel32.QueryFullProcessImageNameW(handle, 0, buffer, length)) {
                return null;
            }
            return new String(buffer, 0, Math.min(length.getValue(), buffer.length));
        } finally {
            kernel32.CloseHandle(handle);
        }
    }

    private long queryProcessStartTime(long pid) throws FileUsageException {
        Pointer handle = kernel32.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, (int) pid);
        if (handle == null) {
            throw nativeFailure("OpenProcess", kernel32.GetLastError());
        }
        try {
            FILETIME creation = new FILETIME();
            FILETIME exit = new FILETIME();
            FILETIME kernel = new FILETIME();
            FILETIME user = new FILETIME();
            if (!kernel32.GetProcessTimes(handle, creation, exit, kernel, user)) {
                throw nativeFailure("GetProcessTimes", kernel32.GetLastError());
            }
            return fileTimeValue(creation);
        } finally {
            kernel32.CloseHandle(handle);
        }
    }

    private List<WindowsWindowTitleMapper.WindowSnapshot> snapshotWindows(long pid) {
        List<WindowsWindowTitleMapper.WindowSnapshot> snapshots = new ArrayList<>();
        enumerateWindows(pid, snapshot -> snapshots.add(
                new WindowsWindowTitleMapper.WindowSnapshot(pid, true, snapshot.title())));
        return snapshots;
    }

    private List<WindowHandleSnapshot> enumerateWindows(long pid) {
        List<WindowHandleSnapshot> windows = new ArrayList<>();
        enumerateWindows(pid, windows::add);
        return windows;
    }

    private void enumerateWindows(long pid, WindowConsumer consumer) {
        user32.EnumWindows((handle, data) -> {
            IntByReference windowPid = new IntByReference();
            user32.GetWindowThreadProcessId(handle, windowPid);
            long actualPid = Integer.toUnsignedLong(windowPid.getValue());
            if (actualPid != pid || !user32.IsWindowVisible(handle)) {
                return true;
            }
            int length = user32.GetWindowTextLengthW(handle);
            if (length <= 0) {
                return true;
            }
            char[] titleBuffer = new char[length + 1];
            int copied = user32.GetWindowTextW(handle, titleBuffer, titleBuffer.length);
            String title = new String(titleBuffer, 0, Math.max(0, Math.min(copied, length))).trim();
            if (!title.isBlank()) {
                consumer.accept(new WindowHandleSnapshot(handle, title));
            }
            return true;
        }, Pointer.NULL);
    }

    private void waitForExit(long pid, long waitMillis) throws FileUsageException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);
        while (isAlive(pid) && System.nanoTime() < deadline) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw FileUsageException.localizedWithCause(
                        "dialog.fileUsage.error.requestCloseInterrupted", error);
            }
        }
    }

    private static Path normalizeFile(Path file) throws FileUsageException {
        if (file == null) {
            throw FileUsageException.localized("dialog.fileUsage.error.noFile");
        }
        try {
            Path absolute = file.toAbsolutePath().normalize();
            if (!Files.exists(absolute)) {
                throw new IOException("File does not exist");
            }
            return absolute.toRealPath().normalize();
        } catch (IOException error) {
            throw FileUsageException.localizedWithCause(
                    "dialog.fileUsage.error.normalize", error, exceptionDetail(error));
        }
    }

    private static Memory wideString(String value) {
        Memory memory = new Memory((long) (value.length() + 1) * Native.WCHAR_SIZE);
        memory.setWideString(0, value);
        return memory;
    }

    private static long fileTimeValue(FILETIME time) {
        return (Integer.toUnsignedLong(time.highDateTime) << 32)
                | Integer.toUnsignedLong(time.lowDateTime);
    }

    private static String nullTerminated(char[] value) {
        if (value == null) {
            return "";
        }
        int length = 0;
        while (length < value.length && value[length] != '\0') {
            length++;
        }
        return new String(value, 0, length).trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String fileName(String path) {
        int separator = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/'));
        return separator < 0 ? path : path.substring(separator + 1);
    }

    private static FileUsageException nativeFailure(String operation, int status) {
        return FileUsageException.localized(
                "dialog.fileUsage.error.native", operation, Integer.toUnsignedLong(status));
    }

    private static String exceptionDetail(Throwable error) {
        if (error == null) {
            return "";
        }
        String detail = error.getMessage();
        return detail == null || detail.isBlank() ? error.getClass().getSimpleName() : detail;
    }

    @FunctionalInterface
    private interface WindowConsumer {
        void accept(WindowHandleSnapshot window);
    }

    private record WindowHandleSnapshot(HWND handle, String title) {
    }

    private interface RestartManagerApi extends StdCallLibrary {
        int RmStartSession(IntByReference sessionHandle, int sessionFlags, char[] sessionKey);

        int RmRegisterResources(
                int sessionHandle,
                int fileCount,
                Pointer[] fileNames,
                int applicationCount,
                RMUniqueProcess[] applications,
                int serviceCount,
                Pointer[] services);

        int RmGetList(
                int sessionHandle,
                IntByReference procInfoNeeded,
                IntByReference procInfo,
                RMProcessInfo[] affectedApplicationInfo,
                IntByReference rebootReasons);

        int RmEndSession(int sessionHandle);
    }

    private interface Kernel32Api extends StdCallLibrary {
        Pointer OpenProcess(int desiredAccess, boolean inheritHandle, int processId);

        boolean CloseHandle(Pointer handle);

        boolean GetProcessTimes(
                Pointer process,
                FILETIME creationTime,
                FILETIME exitTime,
                FILETIME kernelTime,
                FILETIME userTime);

        boolean QueryFullProcessImageNameW(
                Pointer process,
                int flags,
                char[] executablePath,
                IntByReference pathLength);

        int GetLastError();
    }

    private interface User32Api extends StdCallLibrary {
        boolean EnumWindows(WndEnumProc callback, Pointer data);

        int GetWindowThreadProcessId(HWND window, IntByReference processId);

        boolean IsWindowVisible(HWND window);

        int GetWindowTextLengthW(HWND window);

        int GetWindowTextW(HWND window, char[] text, int textLength);

        boolean PostMessageW(HWND window, int message, Pointer wParam, Pointer lParam);
    }

    private interface WndEnumProc extends StdCallLibrary.StdCallCallback {
        boolean callback(HWND window, Pointer data);
    }

    public static final class HWND extends PointerType {
        public HWND() {
        }
    }

    public static final class FILETIME extends Structure {
        public int lowDateTime;
        public int highDateTime;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("lowDateTime", "highDateTime");
        }
    }

    public static final class RMUniqueProcess extends Structure {
        public int dwProcessId;
        public FILETIME processStartTime;

        public RMUniqueProcess() {
            processStartTime = new FILETIME();
        }

        @Override
        protected List<String> getFieldOrder() {
            return List.of("dwProcessId", "processStartTime");
        }
    }

    public static final class RMProcessInfo extends Structure {
        public RMUniqueProcess process = new RMUniqueProcess();
        public char[] applicationName = new char[256];
        public int applicationType;
        public int appStatus;
        public int tssessionId;
        public int restartable;
        public char[] serviceShortName = new char[64];

        @Override
        protected List<String> getFieldOrder() {
            return List.of(
                    "process",
                    "applicationName",
                    "applicationType",
                    "appStatus",
                    "tssessionId",
                    "restartable",
                    "serviceShortName");
        }
    }
}
