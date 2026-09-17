package uitest;

import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.widgetOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import hdf.object.FileFormat;
import hdf.object.HObject;
import hdf.object.h5.H5File;
import hdf.object.h5.H5ScalarDS;
import hdf.view.i18n.I18n;

import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swtbot.nebula.nattable.finder.widgets.SWTBotNatTable;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTabItem;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Regression coverage for the default Dataset view hosted by HDFView. */
@Tag("ui")
@Tag("integration")
public class TestHDFViewInlineDataset extends AbstractWindowTest {
    private static final String SCALAR_FILE = "tscalarintsize.h5";
    private static final String SCALAR_DATASET = "DS08BITS";
    private static final String EDIT_FILE = "tintsize.h5";
    private static final String EDIT_DATASET = "DS08BITS";
    private static final String EDITOR_LIFECYCLE_FILE = "inline-editor-lifecycle.h5";

    @Test
    public void selectingDatasetShowsInlineDataAndDoubleClickKeepsMainShell()
    {
        File hdfFile = openFile(SCALAR_FILE, FILE_MODE.READ_ONLY);

        try {
            SWTBotTree tree = bot.tree();
            SWTBotTreeItem fileItem = tree.getTreeItem(SCALAR_FILE);
            SWTBotTreeItem firstDataset = fileItem.getNode(SCALAR_DATASET);

            firstDataset.click();

            SWTBotTabItem dataTab = waitForTab("tab.dataContent");
            assertTrue(dataTab.isActive(), "single-clicking a Dataset must select Data Content");
            assertNotNull(bot.tabItem(I18n.text("tab.objectAttributeInfo")));
            assertNotNull(bot.tabItem(I18n.text("tab.generalObjectInfo")));
            assertTrue(bot.widget(widgetOfType(NatTable.class)) instanceof NatTable,
                       "Data Content must contain the existing NatTable");

            int shellCount = bot.shells().length;
            firstDataset.doubleClick();

            assertEquals(shellCount, bot.shells().length,
                         "default Dataset double-click must not create a TableView Shell");
            assertTrue(waitForTab("tab.dataContent").isActive(),
                       "default Dataset double-click must focus the inline Data Content tab");

            SWTBotTreeItem secondDataset = fileItem.getNode("DU64BITS");
            secondDataset.click();
            assertTrue(waitForTab("tab.dataContent").isActive(),
                       "switching Dataset must select its own Data Content tab");
            SWTBotTabItem generalTab = bot.tabItem(I18n.text("tab.generalObjectInfo"));
            generalTab.activate();
            assertEquals("DU64BITS", bot.textWithLabel(I18n.text("meta.objectName")).getText(),
                         "switching Dataset must refresh General Object Info");
        }
        finally {
            closeFile(hdfFile, false);
        }
    }

    @Test
    public void switchingGroupAndDatasetDoesNotLeaveAnEditableGroupTable()
    {
        String filename = "tattr2.h5";
        File hdfFile = openFile(filename, FILE_MODE.READ_ONLY);

        try {
            SWTBotTree filetree = bot.tree();
            SWTBotTreeItem fileItem = filetree.getTreeItem(filename);
            SWTBotTreeItem group = fileItem.getNode("g2");
            group.expand();

            group.click();
            SWTBotTabItem generalTab = waitForTab("tab.generalObjectInfo");
            generalTab.activate();
            assertEquals("g2", bot.textWithLabel(I18n.text("meta.objectName")).getText(),
                         "Group selection must refresh metadata for the Group");
            assertThrows(WidgetNotFoundException.class, () -> bot.tabItem(I18n.text("tab.dataContent")),
                         "a Group must not receive an empty editable Data Content tab");

            SWTBotTreeItem dataset = group.getNode("array");
            dataset.click();
            assertTrue(waitForTab("tab.dataContent").isActive(),
                       "selecting a Dataset after a Group must create Data Content");

            generalTab = bot.tabItem(I18n.text("tab.generalObjectInfo"));
            generalTab.activate();
            assertEquals("array", bot.textWithLabel(I18n.text("meta.objectName")).getText(),
                         "Dataset metadata must not retain the previous Group");

            group.click();
            generalTab = waitForTab("tab.generalObjectInfo");
            generalTab.activate();
            assertEquals("g2", bot.textWithLabel(I18n.text("meta.objectName")).getText(),
                         "returning to a Group must restore Group metadata");
            assertThrows(WidgetNotFoundException.class, () -> bot.tabItem(I18n.text("tab.dataContent")),
                         "switching back to a Group must remove the Dataset table");
        }
        finally {
            closeFile(hdfFile, false);
        }
    }

    @Test
    public void editingInlineDatasetAndSavingChangesTheFile()
    {
        selectLanguage(I18n.Language.SIMPLIFIED_CHINESE);
        File hdfFile = openFile(EDIT_FILE, FILE_MODE.READ_WRITE);
        String originalValue = null;
        String newValue = null;

        try {
            SWTBotTreeItem dataset = bot.tree().getTreeItem(EDIT_FILE).getNode(EDIT_DATASET);
            dataset.click();
            waitForTab("tab.dataContent");

            SWTBotNatTable table = new SWTBotNatTable(bot.widget(widgetOfType(NatTable.class)));
            table.click(1, 1);
            originalValue = table.getCellDataValueByPosition(1, 1);
            newValue = originalValue.equals("123") ? "-123" : "123";

            final String valueToWrite = newValue;
            Display.getDefault().syncExec(() -> {
                table.doubleclick(1, 1);
                table.widget.getActiveCellEditor().setEditorValue(valueToWrite);
                table.widget.getActiveCellEditor().commit(SelectionLayer.MoveDirectionEnum.RIGHT,
                                                           true, true);
            });

            assertEquals(newValue, table.getCellDataValueByPosition(1, 1),
                         "inline editing must update the existing TableView data provider");

            bot.menu().menu(I18n.text("menu.file")).menu(I18n.text("menu.file.save")).click();
        }
        finally {
            closeFile(hdfFile, false);
            selectLanguage(I18n.Language.ENGLISH);
        }

        assertNotNull(originalValue, "the Dataset cell was not read before editing");
        assertNotNull(newValue, "the Dataset cell was not assigned before saving");
        String valueReadFromFile = readFirstDatasetValue(hdfFile, EDIT_DATASET);
        assertNotEquals(originalValue, valueReadFromFile,
                        "saving the inline TableView must change the on-disk Dataset value");
        assertEquals(newValue, valueReadFromFile,
                     "the value reread from disk must equal the value saved through HDFView");
    }

    @Test
    public void switchingDatasetWhileEditorIsActiveUsesExistingSaveConfirmation()
    {
        Path source = new File(workDir, EDIT_FILE).toPath();
        Path workingCopy = new File(workDir, EDITOR_LIFECYCLE_FILE).toPath();
        File hdfFile = null;
        String originalValue = null;
        String newValue = null;

        try {
            Files.copy(source, workingCopy, StandardCopyOption.REPLACE_EXISTING);
            hdfFile = openFile(EDITOR_LIFECYCLE_FILE, FILE_MODE.READ_WRITE);

            SWTBotTreeItem fileItem = bot.tree().getTreeItem(EDITOR_LIFECYCLE_FILE);
            SWTBotTreeItem datasetA = fileItem.getNode(EDIT_DATASET);
            SWTBotTreeItem datasetB = fileItem.getNode("DS16BITS");
            datasetA.click();
            waitForTab("tab.dataContent");

            SWTBotNatTable table = new SWTBotNatTable(bot.widget(widgetOfType(NatTable.class)));
            table.click(1, 1);
            originalValue = table.getCellDataValueByPosition(1, 1);
            newValue = originalValue.equals("123") ? "-123" : "123";

            final String valueToWrite = newValue;
            Display.getDefault().syncExec(() -> {
                table.doubleclick(1, 1);
                assertNotNull(table.widget.getActiveCellEditor(),
                              "double-click must leave an active NatTable cell editor");
                table.widget.getActiveCellEditor().setEditorValue(valueToWrite);
                assertNotNull(table.widget.getActiveCellEditor(),
                              "the editor must still be active before the object switch");
            });

            /*
             * This is deliberately the user path under investigation: no ENTER,
             * TAB, or direct editor commit occurs before selecting another object.
             */
            datasetB.click();

            SWTBotShell saveShell = waitForChangesDialog();
            saveShell.activate();
            saveShell.bot().button(I18n.text("button.yes")).click();
            waitForShellToClose(saveShell);

            assertTrue(waitForTab("tab.dataContent").isActive(),
                       "switching to Dataset B must finish after the existing save confirmation");
            closeFile(hdfFile, false);
            hdfFile = null;

            String valueReadFromFile = readFirstDatasetValue(workingCopy.toFile(), EDIT_DATASET);
            assertEquals(newValue, valueReadFromFile,
                         "an active editor value must survive an inline Dataset switch and save");
        }
        catch (Exception ex) {
            throw new AssertionError("Unable to exercise an active inline Dataset editor", ex);
        }
        finally {
            if (hdfFile != null)
                closeFile(hdfFile, false);
            try {
                Files.deleteIfExists(workingCopy);
            }
            catch (Exception ex) {
                throw new AssertionError("Unable to remove the temporary editor lifecycle fixture", ex);
            }
        }
    }

    @Test
    public void switchingDatasetToGroupSavesAnActiveEditorBeforeDisposal()
    {
        Path workingCopy = copyFixture("tattr2.h5", "inline-editor-group.h5");
        File hdfFile = null;
        String newValue = null;

        try {
            selectLanguage(I18n.Language.ENGLISH);
            hdfFile = openFile(workingCopy.getFileName().toString(), FILE_MODE.READ_WRITE);

            SWTBotTreeItem fileItem = bot.tree().getTreeItem(workingCopy.getFileName().toString());
            SWTBotTreeItem group = fileItem.getNode("g2");
            group.expand();
            SWTBotTreeItem dataset = group.getNode("integer");
            dataset.click();
            waitForTab("tab.dataContent");

            SWTBotNatTable table = new SWTBotNatTable(bot.widget(widgetOfType(NatTable.class)));
            newValue = editFirstCellWithoutCommit(table);

            /* Select the Group directly while the editor is still active. */
            group.click();
            acceptChangesDialog();

            SWTBotTabItem generalTab = bot.tabItem(I18n.text("tab.generalObjectInfo"));
            generalTab.activate();
            assertEquals("g2", bot.textWithLabel(I18n.text("meta.objectName")).getText(),
                         "switching from a Dataset to its Group must complete after saving the edit");

            closeFile(hdfFile, false);
            hdfFile = null;

            assertEquals(newValue, readFirstDatasetValue(workingCopy.toFile(), "g2/integer"),
                         "the active editor value must be saved before switching to a Group");
        }
        catch (Exception ex) {
            throw new AssertionError("Unable to exercise Dataset-to-Group editor disposal", ex);
        }
        finally {
            if (hdfFile != null)
                closeFile(hdfFile, false);
            deleteFixture(workingCopy);
        }
    }

    @Test
    public void closingFileWhileEditorIsActiveUsesExistingSaveConfirmation()
    {
        Path workingCopy = copyFixture(EDIT_FILE, "inline-editor-close.h5");
        File hdfFile = null;
        String newValue = null;

        try {
            selectLanguage(I18n.Language.ENGLISH);
            hdfFile = openFile(workingCopy.getFileName().toString(), FILE_MODE.READ_WRITE);

            SWTBotTreeItem fileItem = bot.tree().getTreeItem(workingCopy.getFileName().toString());
            SWTBotTreeItem dataset = fileItem.getNode(EDIT_DATASET);
            dataset.click();
            waitForTab("tab.dataContent");

            SWTBotNatTable table = new SWTBotNatTable(bot.widget(widgetOfType(NatTable.class)));
            newValue = editFirstCellWithoutCommit(table);

            bot.menu().menu(I18n.text("menu.file")).menu(I18n.text("menu.file.close")).click();
            acceptChangesDialog();
            waitForTreeRows(0);
            resetOpenFileCount();
            hdfFile = null;

            assertEquals(newValue, readFirstDatasetValue(workingCopy.toFile(), EDIT_DATASET),
                         "closing a file must save an active inline editor before the file is closed");
        }
        catch (Exception ex) {
            throw new AssertionError("Unable to exercise file close with an active inline editor", ex);
        }
        finally {
            if (hdfFile != null)
                closeFile(hdfFile, false);
            deleteFixture(workingCopy);
        }
    }

    @Test
    public void exitingMainWindowWhileEditorIsActiveSavesBeforeFileShutdown()
    {
        Path workingCopy = copyFixture(EDIT_FILE, "inline-editor-exit.h5");
        File hdfFile = null;
        String newValue = null;
        boolean uiBarrierReleased = false;
        CountDownLatch closeComplete = new CountDownLatch(1);

        try {
            hdfFile = openFile(workingCopy.getFileName().toString(), FILE_MODE.READ_WRITE);

            SWTBotTreeItem fileItem = bot.tree().getTreeItem(workingCopy.getFileName().toString());
            SWTBotTreeItem dataset = fileItem.getNode(EDIT_DATASET);
            dataset.click();
            waitForTab("tab.dataContent");

            SWTBotNatTable table = new SWTBotNatTable(bot.widget(widgetOfType(NatTable.class)));
            newValue = editFirstCellWithoutCommit(table);

            Shell closingMainShell = shell;
            String mainWindowTitle = I18n.text("window.title", VERSION);
            String changesTitle = I18n.text("message.changesDetected.title");
            AtomicReference<Shell> changesDialog = new AtomicReference<>();
            CountDownLatch changesDialogShown = new CountDownLatch(1);
            CountDownLatch replacementMainWindowShown = new CountDownLatch(1);
            Listener[] showListener = new Listener[1];
            showListener[0] = (Event event) -> {
                if (!(event.widget instanceof Shell))
                    return;

                Shell shownShell = (Shell)event.widget;
                if (shownShell.isDisposed())
                    return;

                String title = shownShell.getText();
                if (title.contains(changesTitle)) {
                    changesDialog.set(shownShell);
                    Display.getDefault().removeFilter(SWT.Show, showListener[0]);
                    changesDialogShown.countDown();
                }
                else if (shownShell != closingMainShell && title.contains(mainWindowTitle)) {
                    Display.getDefault().removeFilter(SWT.Show, showListener[0]);
                    replacementMainWindowShown.countDown();
                }
            };

            Display.getDefault().syncExec(
                () -> Display.getDefault().addFilter(SWT.Show, showListener[0]));
            Display.getDefault().asyncExec(() -> {
                try {
                    closingMainShell.close();
                }
                finally {
                    closeComplete.countDown();
                }
            });

            boolean prompted = changesDialogShown.await(5, TimeUnit.SECONDS);
            if (!prompted) {
                boolean replacementShown = replacementMainWindowShown.getCount() == 0;
                boolean closed = closeComplete.await(5, TimeUnit.SECONDS);
                if (closed) {
                    resetOpenFileCount();
                    hdfFile = null;
                    releaseUiBarrierAfterMainWindowExit();
                    uiBarrierReleased = true;
                }
                assertTrue(replacementShown,
                           "closing HDFView with an active inline editor must show the existing changes confirmation");
                assertTrue(closed, "the main HDFView window did not finish closing");
                throw new AssertionError("main-window close silently discarded an active inline edit");
            }

            SWTBotShell saveShell = new SWTBotShell(changesDialog.get());
            saveShell.activate();
            SWTBotButton yesButton = saveShell.bot().button(I18n.text("button.yes"));
            Display.getDefault().asyncExec(
                () -> yesButton.widget.notifyListeners(SWT.Selection, new Event()));

            assertTrue(closeComplete.await(5, TimeUnit.SECONDS),
                       "the main HDFView window did not finish closing after saving changes");
            resetOpenFileCount();
            hdfFile = null;
            releaseUiBarrierAfterMainWindowExit();
            uiBarrierReleased = true;

            assertEquals(newValue, readFirstDatasetValue(workingCopy.toFile(), EDIT_DATASET),
                         "exiting HDFView must save an active inline editor before closing the file");
        }
        catch (Exception ex) {
            throw new AssertionError("Unable to exercise main-window exit with an active inline editor", ex);
        }
        finally {
            if (closeComplete.getCount() == 0 && !uiBarrierReleased) {
                try {
                    releaseUiBarrierAfterMainWindowExit();
                    uiBarrierReleased = true;
                }
                catch (Exception ex) {
                    // Preserve the original test failure while allowing the
                    // normal harness teardown to make a best effort.
                }
            }
            if (hdfFile != null && !uiBarrierReleased)
                closeFile(hdfFile, false);
            deleteFixture(workingCopy);
        }
    }

    @Test
    public void enterCommittedEditIsSavedOnceWhenSwitchingDataset()
    {
        Path workingCopy = copyFixture(EDIT_FILE, "inline-editor-enter.h5");
        File hdfFile = null;
        String newValue = null;

        try {
            selectLanguage(I18n.Language.ENGLISH);
            hdfFile = openFile(workingCopy.getFileName().toString(), FILE_MODE.READ_WRITE);

            SWTBotTreeItem fileItem = bot.tree().getTreeItem(workingCopy.getFileName().toString());
            SWTBotTreeItem datasetA = fileItem.getNode(EDIT_DATASET);
            SWTBotTreeItem datasetB = fileItem.getNode("DS16BITS");
            datasetA.click();
            waitForTab("tab.dataContent");

            SWTBotNatTable table = new SWTBotNatTable(bot.widget(widgetOfType(NatTable.class)));
            newValue = editFirstCellAndPressEnter(table);
            assertEquals(newValue, table.getCellDataValueByPosition(1, 1),
                         "an Enter-committed edit must remain in the TableView data provider");
            assertFalse(hasActiveCellEditor(table),
                        "Enter must close the active editor before a later object switch");

            bot.menu().menu(I18n.text("menu.file")).menu(I18n.text("menu.file.save")).click();
            datasetB.click();

            SWTBotShell unexpectedConfirmation = findChangesDialog();
            if (unexpectedConfirmation != null) {
                acceptChangesDialog(unexpectedConfirmation);
                throw new AssertionError("an Enter-committed and explicitly saved edit was processed again");
            }

            SWTBotTabItem generalTab = bot.tabItem(I18n.text("tab.generalObjectInfo"));
            generalTab.activate();
            assertEquals("DS16BITS", bot.textWithLabel(I18n.text("meta.objectName")).getText(),
                         "switching after an Enter commit must still select Dataset B");

            closeFile(hdfFile, false);
            hdfFile = null;

            assertEquals(newValue, readFirstDatasetValue(workingCopy.toFile(), EDIT_DATASET),
                         "an Enter-committed edit must be persisted by the normal Save action");
        }
        catch (Exception ex) {
            throw new AssertionError("Unable to exercise an Enter-committed inline editor", ex);
        }
        finally {
            SWTBotShell pendingChanges = findChangesDialog();
            if (pendingChanges != null)
                acceptChangesDialog(pendingChanges);
            if (hdfFile != null)
                closeFile(hdfFile, false);
            deleteFixture(workingCopy);
        }
    }

    private void selectLanguage(I18n.Language language)
    {
        if (I18n.getLanguage() == language)
            return;

        String targetKey = language == I18n.Language.ENGLISH
            ? "menu.tools.language.english"
            : "menu.tools.language.simplifiedChinese";

        bot.menu().menu(I18n.text("menu.tools"))
            .menu(I18n.text("menu.tools.language"))
            .menu(I18n.text(targetKey))
            .click();

        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test()
            {
                return I18n.getLanguage() == language;
            }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for HDFView language to become " + language;
            }
        });
    }

    private SWTBotTabItem waitForTab(final String tabKey)
    {
        final String tabName = I18n.text(tabKey);
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test()
            {
                try {
                    bot.tabItem(tabName);
                    return true;
                }
                catch (WidgetNotFoundException ex) {
                    return false;
                }
            }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for HDFView tab '" + tabName + "'";
            }
        });

        return bot.tabItem(tabName);
    }

    private Path copyFixture(String sourceName, String workingName)
    {
        Path source = new File(workDir, sourceName).toPath();
        Path target = new File(workDir, workingName).toPath();
        try {
            return Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (Exception ex) {
            throw new AssertionError("Unable to create temporary HDF5 fixture " + workingName, ex);
        }
    }

    private void deleteFixture(Path fixture)
    {
        try {
            Files.deleteIfExists(fixture);
        }
        catch (Exception ex) {
            throw new AssertionError("Unable to remove temporary HDF5 fixture " + fixture, ex);
        }
    }

    private String editFirstCellWithoutCommit(SWTBotNatTable table)
    {
        table.click(1, 1);
        String originalValue = table.getCellDataValueByPosition(1, 1);
        String newValue = originalValue.equals("123") ? "-123" : "123";

        final String valueToWrite = newValue;
        Display.getDefault().syncExec(() -> {
            table.doubleclick(1, 1);
            assertNotNull(table.widget.getActiveCellEditor(),
                          "double-click must leave an active NatTable cell editor");
            table.widget.getActiveCellEditor().setEditorValue(valueToWrite);
            assertNotNull(table.widget.getActiveCellEditor(),
                          "the editor must still be active before the object switch");
        });

        return newValue;
    }

    private String editFirstCellAndPressEnter(SWTBotNatTable table)
    {
        table.click(1, 1);
        String originalValue = table.getCellDataValueByPosition(1, 1);
        String newValue = originalValue.equals("0") ? "1" : "0";
        final Text[] editorControl = new Text[1];

        final String valueToWrite = newValue;
        Display.getDefault().syncExec(() -> {
            table.doubleclick(1, 1);
            assertNotNull(table.widget.getActiveCellEditor(),
                          "double-click must leave an active NatTable cell editor");
            assertTrue(table.widget.getActiveCellEditor().getEditorControl() instanceof Text,
                       "the integer Dataset editor must be a Text control");
            editorControl[0] = (Text)table.widget.getActiveCellEditor().getEditorControl();
            editorControl[0].setText(valueToWrite);
            editorControl[0].forceFocus();
            Event enter = new Event();
            enter.type = SWT.KeyDown;
            enter.keyCode = SWT.CR;
            enter.character = '\r';
            enter.stateMask = 0;
            editorControl[0].notifyListeners(SWT.KeyDown, enter);
        });

        assertFalse(hasActiveCellEditor(table),
                    "the actual SWT Enter key event must close the NatTable cell editor");

        return newValue;
    }

    private boolean hasActiveCellEditor(final SWTBotNatTable table)
    {
        final boolean[] active = new boolean[1];
        Display.getDefault().syncExec(() -> active[0] = table.widget.getActiveCellEditor() != null);
        return active[0];
    }

    private SWTBotShell findChangesDialog()
    {
        String title = I18n.text("message.changesDetected.title");
        for (SWTBotShell candidate : bot.shells()) {
            if (candidate.getText().contains(title))
                return candidate;
        }
        return null;
    }

    private void acceptChangesDialog()
    {
        SWTBotShell saveShell = waitForChangesDialog();
        acceptChangesDialog(saveShell);
    }

    private void acceptChangesDialog(SWTBotShell saveShell)
    {
        saveShell.activate();
        SWTBotButton yesButton = saveShell.bot().button(I18n.text("button.yes"));
        Display.getDefault().asyncExec(
            () -> yesButton.widget.notifyListeners(SWT.Selection, new Event()));
        waitForShellToClose(saveShell);
    }

    private void waitForTreeRows(final int expectedRows)
    {
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() { return bot.tree().rowCount() == expectedRows; }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for the HDFView tree to contain " + expectedRows + " rows";
            }
        });
    }

    private SWTBotShell waitForChangesDialog()
    {
        final SWTBotShell[] dialog = new SWTBotShell[1];
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test()
            {
                String title = I18n.text("message.changesDetected.title");
                for (SWTBotShell candidate : bot.shells()) {
                    if (candidate.getText().contains(title)) {
                        dialog[0] = candidate;
                        return true;
                    }
                }
                return false;
            }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for the inline Dataset changes confirmation dialog";
            }
        });
        return dialog[0];
    }

    private void waitForShellToClose(final SWTBotShell dialog)
    {
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() { return !dialog.isOpen(); }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for the changes confirmation dialog to close";
            }
        });
    }

    private String readFirstDatasetValue(File hdfFile, String datasetName)
    {
        H5File verificationFile = new H5File(hdfFile.getAbsolutePath(), FileFormat.READ);

        try {
            verificationFile.open();
            HObject object = verificationFile.get("/" + datasetName);
            assertTrue(object instanceof H5ScalarDS, "verification object must be an HDF5 scalar Dataset");

            Object data = ((H5ScalarDS)object).getData();
            assertNotNull(data, "verification read returned no Dataset data");
            return data.getClass().isArray() ? String.valueOf(Array.get(data, 0)) : String.valueOf(data);
        }
        catch (Exception ex) {
            throw new AssertionError("Unable to reread the saved Dataset value", ex);
        }
        finally {
            try {
                verificationFile.close();
            }
            catch (Exception ex) {
                // Preserve the primary test result if native cleanup reports an error.
            }
        }
    }
}
