/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * All rights reserved.                                                       *
 *****************************************************************************/

package hdf.view.statistics;

import java.util.Locale;

import hdf.view.ThemeManager;
import hdf.view.i18n.I18n;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

/**
 * Modeless Dataset statistics window used by both embedded and standalone
 * TableViews.
 *
 * <p>The dialog owns only presentation state. Dataset reads, cancellation,
 * dirty-buffer capture, and current-page highlighting remain in the owning
 * TableView so there is one data and save path.</p>
 */
public final class DatasetStatisticsDialog {
    /** Callbacks into the owning TableView. */
    public interface Host {
        /** Return the current Dataset label for the dialog header. */
        String getStatisticsDatasetLabel();

        /** Return the current page/frame label, if one is available. */
        String getStatisticsPageLabel();

        /** Start a calculation on the requested range. */
        void startStatistics(DatasetStatisticsDialog dialog,
                             DatasetStatisticsEngine.Scope scope);

        /** Request cooperative cancellation of the current calculation. */
        void cancelStatistics(DatasetStatisticsDialog dialog);

        /** Highlight one classification in the current displayed page. */
        void highlightStatistics(DatasetStatisticsEngine.Kind kind);

        /** Return the classification currently highlighted in the TableView. */
        DatasetStatisticsEngine.Kind getStatisticsHighlightKind();

        /** Clear current-page statistics highlighting. */
        void clearStatisticsHighlight();
    }

    private static final String[] ROW_KEYS = {
        "statistics.total",
        "statistics.zero",
        "statistics.nonZero",
        "statistics.positive",
        "statistics.negative",
        "statistics.nonNegative",
        "statistics.minimum",
        "statistics.maximum",
        "statistics.mean",
        "statistics.standardDeviation"
    };

    private static final String[] HIGHLIGHT_KEYS = {
        "statistics.zero",
        "statistics.nonZero",
        "statistics.positive",
        "statistics.negative"
    };

    private final Shell parent;
    private final Host host;

    private Shell shell;
    private Label datasetLabel;
    private Label pageLabel;
    private Label statusLabel;
    private ProgressBar progressBar;
    private Combo scopeCombo;
    private Button calculateButton;
    private Button cancelButton;
    private Button closeButton;
    private Label activeHighlightLabel;
    private Button[] highlightButtons;
    private Table resultTable;
    private boolean running;
    private boolean hasSuccessfulResult;
    private String statusKey = "statistics.status.ready";
    private Object[] statusArgs = new Object[0];

    /** Create a modeless statistics window attached to the main HDFView shell. */
    public DatasetStatisticsDialog(Shell parent, Host host)
    {
        this.parent = parent;
        this.host   = host;
    }

    /** Open the window, creating its controls once. */
    public void open()
    {
        if (shell != null && !shell.isDisposed()) {
            shell.open();
            shell.forceActive();
            return;
        }

        shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MODELESS);
        ThemeManager themeManager = ThemeManager.forDisplay(shell.getDisplay());
        themeManager.applyTo(shell);
        I18n.bind(shell, "statistics.title");
        shell.setLayout(new GridLayout(1, false));
        shell.addListener(SWT.Close, event -> {
            if (running && host != null)
                host.cancelStatistics(this);
        });

        datasetLabel = new Label(shell, SWT.WRAP);
        datasetLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        I18n.bindDynamic(datasetLabel,
                         () -> I18n.text("statistics.dataset", safe(host.getStatisticsDatasetLabel())));

        pageLabel = new Label(shell, SWT.WRAP);
        pageLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        I18n.bindDynamic(pageLabel,
                         () -> safe(host.getStatisticsPageLabel()));

        Composite scopeRow = new Composite(shell, SWT.NONE);
        themeManager.bindBackground(scopeRow, ThemeManager.ColorRole.SURFACE);
        scopeRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        scopeRow.setLayout(new GridLayout(4, false));

        Label scopeLabel = new Label(scopeRow, SWT.NONE);
        I18n.bind(scopeLabel, "statistics.scope");

        scopeCombo = new Combo(scopeRow, SWT.READ_ONLY);
        scopeCombo.setLayoutData(new GridData(180, SWT.DEFAULT));
        I18n.bindItems(scopeCombo, "statistics.scope.currentPage", "statistics.scope.entireDataset");
        scopeCombo.select(0);

        calculateButton = new Button(scopeRow, SWT.PUSH);
        I18n.bind(calculateButton, "statistics.calculate");
        calculateButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event)
            {
                if (host != null)
                    host.startStatistics(DatasetStatisticsDialog.this, getSelectedScope());
            }
        });

        cancelButton = new Button(scopeRow, SWT.PUSH);
        I18n.bind(cancelButton, "statistics.cancel");
        cancelButton.setEnabled(false);
        cancelButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event)
            {
                if (host != null)
                    host.cancelStatistics(DatasetStatisticsDialog.this);
            }
        });

        resultTable = new Table(shell, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
        resultTable.setHeaderVisible(true);
        resultTable.setLinesVisible(true);
        resultTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        TableColumn nameColumn = new TableColumn(resultTable, SWT.LEFT);
        I18n.bind(nameColumn, "statistics.column.name");
        nameColumn.setWidth(220);
        TableColumn valueColumn = new TableColumn(resultTable, SWT.RIGHT);
        I18n.bind(valueColumn, "statistics.column.value");
        valueColumn.setWidth(180);
        for (String key : ROW_KEYS) {
            TableItem item = new TableItem(resultTable, SWT.NONE);
            I18n.bindTableCell(item, 0, key);
            item.setText(1, "");
        }

        statusLabel = new Label(shell, SWT.WRAP);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        I18n.bindDynamic(statusLabel, this::statusText);

        progressBar = new ProgressBar(shell, SWT.NONE);
        progressBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        progressBar.setMinimum(0);
        progressBar.setMaximum(100);

        Group highlightGroup = new Group(shell, SWT.NONE);
        themeManager.bind(highlightGroup, ThemeManager.ColorRole.SECONDARY_SURFACE,
                          ThemeManager.ColorRole.FOREGROUND);
        I18n.bind(highlightGroup, "statistics.highlightGroup");
        highlightGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        highlightGroup.setLayout(new GridLayout(5, false));

        Label hint = new Label(highlightGroup, SWT.WRAP);
        I18n.bind(hint, "statistics.highlightHint");
        hint.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 5, 1));

        activeHighlightLabel = new Label(highlightGroup, SWT.WRAP);
        activeHighlightLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 5, 1));
        I18n.bindDynamic(activeHighlightLabel, this::activeHighlightText);

        highlightButtons = new Button[HIGHLIGHT_KEYS.length];
        for (int i = 0; i < HIGHLIGHT_KEYS.length; i++) {
            Button button = new Button(highlightGroup, SWT.PUSH);
            highlightButtons[i] = button;
            I18n.bind(button, HIGHLIGHT_KEYS[i]);
            button.setEnabled(false);
            final DatasetStatisticsEngine.Kind kind = highlightKind(i);
            button.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent event)
                {
                    if (host != null)
                        host.highlightStatistics(kind);
                    refreshHighlightState();
                }
            });
        }

        Button clearHighlightButton = new Button(highlightGroup, SWT.PUSH);
        I18n.bind(clearHighlightButton, "statistics.clearHighlight");
        clearHighlightButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event)
            {
                if (host != null)
                    host.clearStatisticsHighlight();
                refreshHighlightState();
            }
        });

        Composite buttonRow = new Composite(shell, SWT.NONE);
        themeManager.bindBackground(buttonRow, ThemeManager.ColorRole.SURFACE);
        buttonRow.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
        buttonRow.setLayout(new GridLayout(1, false));
        closeButton = new Button(buttonRow, SWT.PUSH);
        I18n.bind(closeButton, "button.close");
        closeButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event)
            {
                close();
            }
        });

        shell.setSize(new Point(560, 600));
        shell.open();
        shell.layout(true, true);
        refreshHighlightState();
    }

    /** @return whether the window currently exists and is open. */
    public boolean isOpen() { return shell != null && !shell.isDisposed(); }

    /** @return whether a calculation is currently running. */
    public boolean isRunning() { return running; }

    /** Return the stable scope selected by index, not its translated label. */
    public DatasetStatisticsEngine.Scope getSelectedScope()
    {
        return scopeCombo != null && scopeCombo.getSelectionIndex() == 1
            ? DatasetStatisticsEngine.Scope.ENTIRE_DATASET
            : DatasetStatisticsEngine.Scope.CURRENT_PAGE;
    }

    /** Show the busy state before a worker starts. */
    public void showRunning(DatasetStatisticsEngine.Scope scope)
    {
        if (!isOpen())
            open();
        if (scopeCombo != null)
            scopeCombo.select(scope == DatasetStatisticsEngine.Scope.ENTIRE_DATASET ? 1 : 0);
        running = true;
        setHighlightButtonsEnabled(false);
        setStatus("statistics.status.running");
        progressBar.setSelection(0);
        calculateButton.setEnabled(false);
        cancelButton.setEnabled(true);
        closeButton.setEnabled(false);
    }

    /** Update progress from the UI thread. */
    public void updateProgress(long processed, long total)
    {
        if (!isOpen())
            return;
        int percentage = total <= 0 ? 0 : (int)Math.min(100L, Math.max(0L, processed * 100L / total));
        progressBar.setSelection(percentage);
        setStatus("statistics.status.running", percentage);
    }

    /** Display a completed result. */
    public void showResult(DatasetStatisticsEngine.Result result)
    {
        if (!isOpen())
            return;
        running = false;
        hasSuccessfulResult = true;
        setHighlightButtonsEnabled(true);
        long[] values = {
            result.getTotal(), result.getZero(), result.getNonZero(), result.getPositive(),
            result.getNegative(), result.getNonNegative()
        };
        String[] reductions = {
            format(result.getMinimum()), format(result.getMaximum()), format(result.getMean()),
            format(result.getStandardDeviation())
        };
        for (int i = 0; i < values.length; i++)
            resultTable.getItem(i).setText(1, String.valueOf(values[i]));
        for (int i = 0; i < reductions.length; i++)
            resultTable.getItem(values.length + i).setText(1, reductions[i]);
        progressBar.setSelection(100);
        setStatus("statistics.status.complete");
        calculateButton.setEnabled(true);
        cancelButton.setEnabled(false);
        closeButton.setEnabled(true);
        refreshHighlightState();
        shell.layout(true, true);
    }

    /** Display a cooperative cancellation result. */
    public void showCancelled()
    {
        if (!isOpen())
            return;
        running = false;
        setHighlightButtonsEnabled(hasSuccessfulResult);
        setStatus("statistics.status.cancelled");
        calculateButton.setEnabled(true);
        cancelButton.setEnabled(false);
        closeButton.setEnabled(true);
    }

    /** Display an error without pretending the calculation completed. */
    public void showError(String message)
    {
        if (!isOpen())
            return;
        running = false;
        hasSuccessfulResult = false;
        setHighlightButtonsEnabled(false);
        setStatus("statistics.status.error", message == null ? "" : message);
        calculateButton.setEnabled(true);
        cancelButton.setEnabled(false);
        closeButton.setEnabled(true);
    }

    /** Close the window and cancel a still-running worker. */
    public void close()
    {
        if (running && host != null)
            host.cancelStatistics(this);
        running = false;
        if (shell != null && !shell.isDisposed())
            shell.dispose();
    }

    /** Dispose during TableView cleanup without creating a second save path. */
    public void dispose() { close(); }

    private void setStatus(String key, Object... args)
    {
        statusKey  = key;
        statusArgs = args == null ? new Object[0] : args.clone();
        if (statusLabel != null && !statusLabel.isDisposed())
            statusLabel.setText(statusText());
    }

    private void setHighlightButtonsEnabled(boolean enabled)
    {
        if (highlightButtons == null)
            return;
        for (Button button : highlightButtons) {
            if (button != null && !button.isDisposed())
                button.setEnabled(enabled);
        }
    }

    private String statusText()
    {
        String base = I18n.text(statusKey, statusArgs);
        if ("statistics.status.running".equals(statusKey) && statusArgs.length == 1)
            return base + " " + String.valueOf(statusArgs[0]) + "%";
        return base;
    }

    /** Refresh the visible indication without changing the owning TableView. */
    public void refreshHighlightState()
    {
        if (activeHighlightLabel != null && !activeHighlightLabel.isDisposed())
            activeHighlightLabel.setText(activeHighlightText());
    }

    private String activeHighlightText()
    {
        DatasetStatisticsEngine.Kind kind = host == null ? null : host.getStatisticsHighlightKind();
        if (kind == null)
            return I18n.text("statistics.activeHighlight.none");
        return I18n.text("statistics.activeHighlight", I18n.text(highlightKey(kind)));
    }

    private static DatasetStatisticsEngine.Kind highlightKind(int index)
    {
        switch (index) {
        case 0: return DatasetStatisticsEngine.Kind.ZERO;
        case 1: return DatasetStatisticsEngine.Kind.NON_ZERO;
        case 2: return DatasetStatisticsEngine.Kind.POSITIVE;
        case 3: return DatasetStatisticsEngine.Kind.NEGATIVE;
        default: throw new IllegalArgumentException("Unknown highlight index " + index);
        }
    }

    private static String highlightKey(DatasetStatisticsEngine.Kind kind)
    {
        switch (kind) {
        case ZERO: return "statistics.zero";
        case NON_ZERO: return "statistics.nonZero";
        case POSITIVE: return "statistics.positive";
        case NEGATIVE: return "statistics.negative";
        default: return "statistics.activeHighlight.none";
        }
    }

    private static String format(double value)
    {
        if (Double.isNaN(value))
            return "NaN";
        if (Double.isInfinite(value))
            return value > 0 ? "+Inf" : "-Inf";
        return String.format(Locale.ROOT, "%.12g", value);
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
