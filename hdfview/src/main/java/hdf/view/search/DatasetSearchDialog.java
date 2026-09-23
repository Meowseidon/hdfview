/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * Copyright by the Board of Trustees of the University of Illinois.         *
 * All rights reserved.                                                       *
 *****************************************************************************/

package hdf.view.search;

import java.util.ArrayList;
import java.util.List;

import hdf.view.HDFView;
import hdf.view.ThemeManager;
import hdf.view.i18n.I18n;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/** SWT window for Dataset name and value searches. */
public final class DatasetSearchDialog {
    private final HDFView viewer;
    private final Shell shell;

    private Text queryText;
    private Combo modeCombo;
    private Button searchButton;
    private Button cancelButton;
    private Label statusLabel;
    private Label errorLabel;
    private ProgressBar progressBar;
    private Table resultTable;

    private final List<DatasetSearchResult> results = new ArrayList<>();
    private boolean searching;
    private String statusKey = "search.status.ready";
    private Object[] statusArgs = new Object[0];
    private String lastErrorPath = "";
    private String lastErrorMessage = "";
    private DatasetSearchException lastError;

    public DatasetSearchDialog(HDFView viewer, Shell parent)
    {
        this.viewer = viewer;
        shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MODELESS);
        ThemeManager themeManager = ThemeManager.forDisplay(shell.getDisplay());
        themeManager.applyTo(shell);
        createContents();
    }

    private void createContents()
    {
        shell.setLayout(new GridLayout(1, false));
        I18n.bind(shell, "dialog.datasetSearch.title");

        Composite queryArea = new Composite(shell, SWT.NONE);
        ThemeManager.forDisplay(shell.getDisplay()).bindBackground(
            queryArea, ThemeManager.ColorRole.SURFACE);
        queryArea.setLayout(new GridLayout(5, false));
        queryArea.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label queryLabel = new Label(queryArea, SWT.NONE);
        I18n.bind(queryLabel, "search.query");

        queryText = new Text(queryArea, SWT.SINGLE | SWT.BORDER);
        queryText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        I18n.bindMessage(queryText, "search.query.message");
        queryText.addListener(SWT.DefaultSelection, event -> startSearch());

        Label modeLabel = new Label(queryArea, SWT.NONE);
        I18n.bind(modeLabel, "search.mode");

        modeCombo = new Combo(queryArea, SWT.READ_ONLY | SWT.DROP_DOWN);
        modeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        I18n.bindItems(modeCombo, "search.mode.datasetName", "search.mode.dataValue");
        modeCombo.select(0);

        searchButton = new Button(queryArea, SWT.PUSH);
        I18n.bind(searchButton, "search.start");
        searchButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) { startSearch(); }
        });

        progressBar = new ProgressBar(shell, SWT.HORIZONTAL);
        progressBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        progressBar.setMinimum(0);
        progressBar.setMaximum(100);

        statusLabel = new Label(shell, SWT.WRAP);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        I18n.bindDynamic(statusLabel, () -> I18n.text(statusKey, statusArgs));

        errorLabel = new Label(shell, SWT.WRAP);
        errorLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        I18n.bindDynamic(errorLabel, () -> {
            if (lastErrorPath == null || lastErrorPath.isEmpty())
                return "";
            String message = lastError == null
                ? lastErrorMessage
                : I18n.text(lastError.messageKey(), lastError.messageArgs());
            return I18n.text("search.skipped", lastErrorPath, message);
        });

        resultTable = new Table(shell, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE | SWT.V_SCROLL |
                                           SWT.H_SCROLL);
        resultTable.setHeaderVisible(true);
        resultTable.setLinesVisible(true);
        resultTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        createResultColumn("search.column.file", 190);
        createResultColumn("search.column.dataset", 260);
        createResultColumn("search.column.value", 170);
        createResultColumn("search.column.index", 130);
        createResultColumn("search.column.matchType", 130);

        resultTable.addListener(SWT.DefaultSelection, event -> activateSelectedResult());

        Composite buttonArea = new Composite(shell, SWT.NONE);
        ThemeManager.forDisplay(shell.getDisplay()).bindBackground(
            buttonArea, ThemeManager.ColorRole.SURFACE);
        buttonArea.setLayout(new GridLayout(3, false));
        buttonArea.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));

        cancelButton = new Button(buttonArea, SWT.PUSH);
        I18n.bind(cancelButton, "search.cancel");
        cancelButton.setEnabled(false);
        cancelButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) { viewer.cancelDatasetSearch(); }
        });

        Button closeButton = new Button(buttonArea, SWT.PUSH);
        I18n.bind(closeButton, "button.close");
        closeButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) { shell.dispose(); }
        });

        shell.addListener(SWT.Close, event -> viewer.datasetSearchDialogClosed(this));
    }

    private void createResultColumn(String key, int width)
    {
        TableColumn column = new TableColumn(resultTable, SWT.NONE);
        I18n.bind(column, key);
        column.setWidth(width);
    }

    private void startSearch()
    {
        if (searching)
            return;

        clearResults();
        lastErrorPath    = "";
        lastErrorMessage = "";
        lastError        = null;
        searching        = true;
        searchButton.setEnabled(false);
        cancelButton.setEnabled(true);
        progressBar.setState(SWT.NORMAL);
        progressBar.setSelection(0);
        setStatus("search.status.running", 0L, "");

        DatasetSearchEngine.SearchMode mode = modeCombo.getSelectionIndex() == 1
            ? DatasetSearchEngine.SearchMode.DATA_VALUE
            : DatasetSearchEngine.SearchMode.DATASET_NAME;
        viewer.startDatasetSearch(this, queryText.getText(), mode);
    }

    private void clearResults()
    {
        results.clear();
        if (resultTable != null && !resultTable.isDisposed())
            resultTable.removeAll();
    }

    private void setStatus(String key, Object... args)
    {
        statusKey  = key;
        statusArgs = args == null ? new Object[0] : args.clone();
        if (statusLabel != null && !statusLabel.isDisposed())
            statusLabel.getParent().layout(true, true);
    }

    /** Called on the SWT UI thread by HDFView when a result batch is ready. */
    public void addResults(List<DatasetSearchResult> batch)
    {
        if (batch == null || batch.isEmpty() || shell.isDisposed())
            return;

        for (DatasetSearchResult result : batch) {
            if (results.size() >= DatasetSearchEngine.MAX_SHOWN_RESULTS)
                break;

            results.add(result);
            TableItem item = new TableItem(resultTable, SWT.NONE);
            item.setData(result);
            item.setText(0, result.getFilePath());
            item.setText(1, result.getDatasetPath());
            item.setText(2, result.getMatchedValue());
            item.setText(3, result.getCoordinateText());
            I18n.bindTableCell(item, 4, matchTypeKey(result.getMatchType()));
        }
    }

    /** Called on the SWT UI thread by HDFView after a search error. */
    public void addError(String path, String message)
    {
        if (shell.isDisposed())
            return;
        lastErrorPath    = path == null ? "" : path;
        lastErrorMessage = message == null ? "" : message;
        lastError        = null;
        errorLabel.getParent().layout(true, true);
    }

    /** Called on the SWT UI thread with a stable, language-independent failure. */
    public void addError(String path, DatasetSearchException failure)
    {
        if (shell.isDisposed())
            return;
        lastErrorPath    = path == null ? "" : path;
        lastErrorMessage = "";
        lastError        = failure;
        errorLabel.getParent().layout(true, true);
    }

    /** Called on the SWT UI thread by HDFView for worker progress. */
    public void updateProgress(long processed, long total, String datasetPath)
    {
        if (shell.isDisposed())
            return;

        if (total > 0 && total <= Integer.MAX_VALUE) {
            progressBar.setState(SWT.NORMAL);
            progressBar.setMaximum((int)total);
            progressBar.setSelection((int)Math.min(total, Math.max(0, processed)));
        }
        else {
            progressBar.setState(SWT.INDETERMINATE);
        }
        setStatus("search.status.running", processed, datasetPath == null ? "" : datasetPath);
    }

    /** Called on the SWT UI thread by HDFView when the worker terminates. */
    public void finish(DatasetSearchEngine.SearchSummary summary)
    {
        if (shell.isDisposed())
            return;

        searching = false;
        searchButton.setEnabled(true);
        cancelButton.setEnabled(false);
        progressBar.setState(SWT.NORMAL);
        progressBar.setSelection(progressBar.getMaximum());

        if (summary != null && summary.isCancelled()) {
            setStatus("search.status.cancelled", summary.getShownMatches());
        }
        else if (summary != null) {
            setStatus("search.status.complete", summary.getTotalMatches(),
                      summary.getShownMatches(), summary.getErrors().size());
        }
        else {
            setStatus("search.status.complete", 0L, 0, 0);
        }
    }

    private void activateSelectedResult()
    {
        if (resultTable == null || resultTable.isDisposed())
            return;
        int index = resultTable.getSelectionIndex();
        if (index < 0 || index >= results.size())
            return;
        viewer.navigateDatasetSearchResult(results.get(index));
    }

    private String matchTypeKey(DatasetSearchResult.MatchType matchType)
    {
        if (matchType == DatasetSearchResult.MatchType.DATASET_NAME)
            return "search.matchType.datasetName";
        if (matchType == DatasetSearchResult.MatchType.NUMERIC_VALUE)
            return "search.matchType.numericValue";
        if (matchType == DatasetSearchResult.MatchType.CHAR_VALUE)
            return "search.matchType.charValue";
        return "search.matchType.stringValue";
    }

    public boolean isSearching() { return searching; }

    public Shell getShell() { return shell; }

    /** Open/focus this window. */
    public void open()
    {
        if (shell.isDisposed())
            return;

        shell.setMinimumSize(780, 430);
        shell.setSize(960, 560);
        shell.open();
        shell.forceActive();
    }
}
