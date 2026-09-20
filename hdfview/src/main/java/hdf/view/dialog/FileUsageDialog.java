package hdf.view.dialog;

import hdf.object.FileFormat;
import hdf.view.Tools;
import hdf.view.fileusage.FileUsageActionPolicy;
import hdf.view.fileusage.FileUsageException;
import hdf.view.fileusage.FileUsageInspector;
import hdf.view.fileusage.FileUsageProcess;
import hdf.view.fileusage.FileUsageScanResult;
import hdf.view.i18n.I18n;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Modeless, explicitly refreshed view of Restart Manager file usage. */
public final class FileUsageDialog {
    private final Shell parent;
    private final FileUsageInspector inspector;
    private final long currentPid;
    private final ExecutorService worker;

    private final Shell shell;
    private final Label fileLabel;
    private final Label statusLabel;
    private final Label detailsLabel;
    private final Table table;
    private final Button refreshButton;
    private final Button requestCloseButton;
    private final Button forceTerminateButton;

    private Path currentFile;
    private FileUsageScanResult lastResult;
    private FileUsageProcess selectedProcess;
    private Future<?> pendingWork;
    private long generation;
    private boolean actionRunning;
    private String actionError;

    public FileUsageDialog(Shell parent, FileUsageInspector inspector, long currentPid) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.inspector = Objects.requireNonNull(inspector, "inspector");
        this.currentPid = currentPid;
        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "hdfview-file-usage");
            thread.setDaemon(true);
            return thread;
        });

        shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
        shell.setFont(parent.getFont());
        I18n.bind(shell, "dialog.fileUsage.title");
        shell.setLayout(new GridLayout(1, false));

        fileLabel = new Label(shell, SWT.WRAP);
        fileLabel.setFont(parent.getFont());
        fileLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        I18n.bindDynamic(fileLabel, this::fileLabelText);

        statusLabel = new Label(shell, SWT.WRAP);
        statusLabel.setFont(parent.getFont());
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        I18n.bind(statusLabel, "dialog.fileUsage.status.ready");

        table = new Table(shell, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        table.setFont(parent.getFont());
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        createColumns();
        table.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                TableItem[] selection = table.getSelection();
                selectedProcess = selection.length == 0 ? null : (FileUsageProcess) selection[0].getData();
                updateActionState();
            }
        });

        detailsLabel = new Label(shell, SWT.WRAP);
        detailsLabel.setFont(parent.getFont());
        detailsLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Composite buttons = new Composite(shell, SWT.NONE);
        buttons.setLayout(new GridLayout(4, false));
        buttons.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));

        refreshButton = new Button(buttons, SWT.PUSH);
        refreshButton.setFont(parent.getFont());
        I18n.bind(refreshButton, "dialog.fileUsage.refresh");
        refreshButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                refresh();
            }
        });

        requestCloseButton = new Button(buttons, SWT.PUSH);
        requestCloseButton.setFont(parent.getFont());
        I18n.bind(requestCloseButton, "dialog.fileUsage.requestClose");
        requestCloseButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                runAction(false);
            }
        });

        forceTerminateButton = new Button(buttons, SWT.PUSH);
        forceTerminateButton.setFont(parent.getFont());
        I18n.bind(forceTerminateButton, "dialog.fileUsage.forceTerminate");
        forceTerminateButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                runAction(true);
            }
        });

        Button closeButton = new Button(buttons, SWT.PUSH);
        closeButton.setFont(parent.getFont());
        I18n.bind(closeButton, "dialog.fileUsage.close");
        closeButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                shell.close();
            }
        });

        shell.addDisposeListener(new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent event) {
                generation++;
                cancelPendingWork();
                worker.shutdownNow();
            }
        });

        shell.setSize(1050, 520);
        updateActionState();
    }

    public Shell getShell() {
        return shell;
    }

    /** Update the selected FileFormat without parsing the URL bar text. */
    public void setCurrentFile(FileFormat file) {
        if (shell.isDisposed()) {
            return;
        }
        Path nextFile = null;
        if (file != null) {
            try {
                nextFile = Path.of(file.getAbsolutePath()).toAbsolutePath().normalize();
            } catch (InvalidPathException ignored) {
                nextFile = null;
            }
        }
        if (Objects.equals(currentFile, nextFile)) {
            return;
        }

        currentFile = nextFile;
        generation++;
        actionRunning = false;
        actionError = null;
        cancelPendingWork();
        lastResult = null;
        selectedProcess = null;
        I18n.bindDynamic(fileLabel, this::fileLabelText);
        table.removeAll();
        detailsLabel.setText("");
        detailsLabel.requestLayout();
        if (currentFile == null) {
            I18n.bind(statusLabel, "dialog.fileUsage.status.noFile");
        } else {
            I18n.bind(statusLabel, "dialog.fileUsage.status.selectionChanged");
        }
        updateActionState();
        shell.layout(true, true);
    }

    public void open() {
        if (shell.isDisposed()) {
            return;
        }
        shell.open();
        shell.forceActive();
        refresh();
    }

    public void refreshLanguage() {
        if (shell.isDisposed()) {
            return;
        }
        I18n.refreshDisplay(shell.getDisplay());
        renderResult(lastResult);
        updateActionState();
        shell.layout(true, true);
    }

    public void dispose() {
        if (!shell.isDisposed()) {
            shell.dispose();
        }
    }

    private void createColumns() {
        String[] keys = {
                "dialog.fileUsage.column.pid",
                "dialog.fileUsage.column.process",
                "dialog.fileUsage.column.type",
                "dialog.fileUsage.column.window",
                "dialog.fileUsage.column.executable"
        };
        int[] widths = {90, 210, 150, 250, 340};
        for (int index = 0; index < keys.length; index++) {
            TableColumn column = new TableColumn(table, SWT.LEFT);
            I18n.bind(column, keys[index]);
            column.setWidth(widths[index]);
        }
    }

    private void refresh() {
        if (shell.isDisposed()) {
            return;
        }
        if (currentFile == null) {
            I18n.bind(statusLabel, "dialog.fileUsage.status.noFile");
            return;
        }
        cancelPendingWork();
        long requestGeneration = ++generation;
        Path file = currentFile;
        I18n.bind(statusLabel, "dialog.fileUsage.status.scanning");
        updateActionState();
        pendingWork = worker.submit(() -> {
            try {
                FileUsageScanResult result = inspector.scan(file);
                runOnUi(requestGeneration, () -> renderResult(result));
            } catch (FileUsageException error) {
                runOnUi(requestGeneration, () -> renderFailure(error));
            } catch (RuntimeException error) {
                runOnUi(requestGeneration, () -> renderFailure(error));
            }
        });
    }

    private void renderResult(FileUsageScanResult result) {
        if (shell.isDisposed() || result == null) {
            return;
        }
        lastResult = result;
        table.removeAll();
        for (FileUsageProcess process : result.processes()) {
            TableItem item = new TableItem(table, SWT.NONE);
            String processName = process.processName();
            if (process.currentHdfView()) {
                processName += " (" + I18n.text("dialog.fileUsage.current") + ")";
            }
            item.setText(0, Long.toString(process.identity().pid()));
            item.setText(1, processName);
            item.setText(2, I18n.text(process.type().messageKey()));
            item.setText(3, process.primaryWindowTitle().isBlank()
                    ? I18n.text("dialog.fileUsage.window.none")
                    : process.primaryWindowTitle());
            item.setText(4, process.executablePath() == null
                    ? I18n.text("dialog.fileUsage.executable.none")
                    : process.executablePath());
            item.setData(process);
        }
        selectedProcess = null;
        if (result.externalProcessCount() == 0) {
            boolean currentHdfView = result.processes().stream()
                    .anyMatch(FileUsageProcess::currentHdfView);
            I18n.bind(statusLabel, currentHdfView
                    ? "dialog.fileUsage.status.noExternal"
                    : "dialog.fileUsage.status.noExternalNoCurrent");
        } else {
            I18n.bind(statusLabel, "dialog.fileUsage.status.externalCount", result.externalProcessCount());
        }
        String renderedActionError = actionError;
        actionError = null;
        detailsLabel.setText("");
        detailsLabel.requestLayout();
        updateActionState();
        if (renderedActionError != null) {
            detailsLabel.setText(I18n.text("dialog.fileUsage.action.failed", renderedActionError));
            detailsLabel.requestLayout();
        }
        table.getParent().layout(true, true);
    }

    private void renderFailure(Exception error) {
        if (shell.isDisposed()) {
            return;
        }
        lastResult = null;
        table.removeAll();
        selectedProcess = null;
        String detail = errorText(error);
        I18n.bind(statusLabel, "dialog.fileUsage.status.failed", detail);
        String renderedActionError = actionError;
        actionError = null;
        detailsLabel.setText(renderedActionError == null
                ? ""
                : I18n.text("dialog.fileUsage.action.failed", renderedActionError));
        detailsLabel.requestLayout();
        updateActionState();
        table.getParent().layout(true, true);
    }

    private void updateActionState() {
        boolean alive = selectedProcess != null
                && ProcessHandle.of(selectedProcess.identity().pid()).map(ProcessHandle::isAlive).orElse(false);
        FileUsageActionPolicy.BlockReason forceReason = FileUsageActionPolicy.forceTerminateBlockReason(
                selectedProcess, currentPid, alive);
        FileUsageActionPolicy.BlockReason closeReason = FileUsageActionPolicy.requestCloseBlockReason(
                selectedProcess, currentPid, alive);
        boolean enabled = !actionRunning && currentFile != null;
        requestCloseButton.setEnabled(enabled && closeReason == FileUsageActionPolicy.BlockReason.NONE);
        forceTerminateButton.setEnabled(enabled && forceReason == FileUsageActionPolicy.BlockReason.NONE);

        if (selectedProcess != null && forceReason != FileUsageActionPolicy.BlockReason.NONE) {
            detailsLabel.setText(blockReasonText(forceReason));
        } else if (selectedProcess != null && !selectedProcess.windowTitles().isEmpty()) {
            detailsLabel.setText(I18n.text(
                    "dialog.fileUsage.windowDetails",
                    String.join(System.lineSeparator(), selectedProcess.windowTitles())));
        } else if (selectedProcess != null) {
            detailsLabel.setText(I18n.text("dialog.fileUsage.windowDetails",
                    I18n.text("dialog.fileUsage.window.none")));
        } else if (lastResult != null && actionError == null) {
            detailsLabel.setText("");
        }
        detailsLabel.requestLayout();
    }

    private String blockReasonText(FileUsageActionPolicy.BlockReason reason) {
        return switch (reason) {
            case SERVICE -> I18n.text("dialog.fileUsage.status.serviceDisabled");
            case CRITICAL_SYSTEM_PROCESS -> I18n.text("dialog.fileUsage.status.criticalDisabled");
            case CURRENT_HDFVIEW -> I18n.text("dialog.fileUsage.status.selfDisabled");
            case INVALID_PID, NOT_RUNNING -> I18n.text("dialog.fileUsage.status.invalidDisabled");
            case NO_VISIBLE_WINDOW -> I18n.text("dialog.fileUsage.status.noWindowDisabled");
            case NONE -> "";
        };
    }

    private void runAction(boolean force) {
        FileUsageProcess process = selectedProcess;
        Path file = currentFile;
        if (process == null || file == null || actionRunning) {
            return;
        }
        boolean alive = ProcessHandle.of(process.identity().pid()).map(ProcessHandle::isAlive).orElse(false);
        FileUsageActionPolicy.BlockReason reason = force
                ? FileUsageActionPolicy.forceTerminateBlockReason(process, currentPid, alive)
                : FileUsageActionPolicy.requestCloseBlockReason(process, currentPid, alive);
        if (reason != FileUsageActionPolicy.BlockReason.NONE) {
            updateActionState();
            return;
        }

        String window = process.windowTitles().isEmpty()
                ? I18n.text("dialog.fileUsage.window.none")
                : String.join(System.lineSeparator(), process.windowTitles());
        String actionKey = force
                ? "dialog.fileUsage.action.forceTerminate.title"
                : "dialog.fileUsage.action.requestClose.title";
        String confirmation = I18n.text(
                "dialog.fileUsage.action.confirm",
                process.processName(),
                process.identity().pid(),
                window,
                file.toAbsolutePath().normalize());
        if (!Tools.showConfirm(shell, I18n.text(actionKey), confirmation)) {
            return;
        }

        actionRunning = true;
        updateActionState();
        long actionGeneration = generation;
        pendingWork = worker.submit(() -> {
            try {
                if (actionGeneration != generation || !Objects.equals(file, currentFile)) {
                    return;
                }
                if (force) {
                    inspector.forceTerminate(file, process);
                } else {
                    inspector.requestClose(file, process);
                }
                runOnUi(actionGeneration, () -> {
                    actionRunning = false;
                    actionError = null;
                    refresh();
                });
            } catch (FileUsageException error) {
                runOnUi(actionGeneration, () -> {
                    actionRunning = false;
                    actionError = errorText(error);
                    refresh();
                });
            } catch (RuntimeException error) {
                runOnUi(actionGeneration, () -> {
                    actionRunning = false;
                    actionError = errorText(error);
                    refresh();
                });
            }
        });
    }

    private void cancelPendingWork() {
        if (pendingWork != null) {
            pendingWork.cancel(true);
            pendingWork = null;
        }
    }

    private void runOnUi(long requestGeneration, Runnable action) {
        if (shell.isDisposed()) {
            return;
        }
        shell.getDisplay().asyncExec(() -> {
            if (!shell.isDisposed() && requestGeneration == generation) {
                action.run();
            }
        });
    }

    private String fileLabelText() {
        String path = currentFile == null
                ? I18n.text("dialog.fileUsage.noFile")
                : currentFile.toAbsolutePath().normalize().toString();
        return I18n.text("dialog.fileUsage.file", path);
    }

    private String errorText(Exception error) {
        if (error instanceof FileUsageException fileUsageError
                && fileUsageError.messageKey() != null) {
            return I18n.text(fileUsageError.messageKey(), fileUsageError.messageArgs());
        }
        String message = error.getMessage();
        return message == null ? error.toString() : message;
    }
}
