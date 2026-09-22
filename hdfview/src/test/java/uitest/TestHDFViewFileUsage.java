/****************************************************************************
 * Copyright by The HDF Group.                                               *
 * All rights reserved.                                                       *
 *                                                                            *
 * This file is part of the HDF Java Products distribution.                   *
 * The full copyright notice for this file is contained in the COPYING file.  *
 ****************************************************************************/

package uitest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import hdf.view.i18n.I18n;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Regression coverage for the modeless File Usage dialog's file lifecycle. */
@EnabledOnOs(OS.WINDOWS)
@Tag("ui")
@Tag("integration")
public class TestHDFViewFileUsage extends AbstractWindowTest {
    private static final String FILE_A = "tscalarintsize.h5";
    private static final String FILE_B = "tattr2.h5";

    @Test
    public void fileUsageFollowsSingleCloseAndCloseAll() {
        activateMainWindow();
        selectLanguage(I18n.Language.ENGLISH);
        File fileA = openFile(FILE_A, FILE_MODE.READ_ONLY);
        File fileB = openFile(FILE_B, FILE_MODE.READ_ONLY);

        try {
            SWTBotTree tree = bot.tree();
            tree.getTreeItem(FILE_A).click();

            SWTBotShell fileUsage = openFileUsageDialog();
            waitForUsageResult(fileUsage);
            assertFileUsageFile(fileUsage, fileA);

            SWTBotTable initialTable = fileUsage.bot().table();
            initialTable.select(0);
            assertEquals(1, initialTable.selectionCount(),
                         "the initial File Usage result must support selection");

            closeFile(fileA, false);
            assertFileUsageFile(fileUsage, fileB);
            assertEquals(0, fileUsage.bot().table().rowCount(),
                         "changing files must invalidate the old File Usage result");
            fileUsage.bot().button(ui("dialog.fileUsage.refresh")).click();
            waitForUsageResult(fileUsage);

            SWTBotTable fileBTable = fileUsage.bot().table();
            fileBTable.select(0);
            assertEquals(1, fileBTable.selectionCount(),
                         "the followed File Usage result must support selection");

            new SWTBotShell(shell).activate();
            new SWTBotShell(shell).bot().menu(ui("menu.file"))
                    .menu(ui("menu.file.closeAll")).click();
            bot.waitUntil(Conditions.treeHasRows(tree, 0));
            resetOpenFileCount();

            waitForNoFileState(fileUsage);
            assertEquals(0, fileUsage.bot().table().rowCount(),
                         "Close All must clear the old File Usage result");
            assertEquals(0, fileUsage.bot().table().selectionCount(),
                         "Close All must clear the old File Usage selection");
            assertFalse(fileUsage.bot().button(ui("dialog.fileUsage.requestClose")).isEnabled(),
                        "Request Close must be disabled without a selected HDF file");
            assertFalse(fileUsage.bot().button(ui("dialog.fileUsage.forceTerminate")).isEnabled(),
                        "Force Terminate must be disabled without a selected HDF file");
        }
        finally {
            selectLanguage(I18n.Language.ENGLISH);
            SWTBotTree tree = bot.tree();
            if (hasTreeItem(tree, FILE_A)) {
                closeFile(fileA, false);
            }
            if (hasTreeItem(tree, FILE_B))
                closeFile(fileB, false);
        }
    }

    @Test
    public void fileUsageRowsAndActionsFollowLanguageWithoutSecondDisplayRefresh() {
        activateMainWindow();
        selectLanguage(I18n.Language.ENGLISH);
        File hdfFile = openFile(FILE_A, FILE_MODE.READ_ONLY);
        Label[] refreshProbe = new Label[1];
        AtomicInteger displayRefreshes = new AtomicInteger();

        try {
            bot.tree().getTreeItem(FILE_A).click();
            SWTBotShell fileUsage = openFileUsageDialog();
            waitForUsageResult(fileUsage);

            Display.getDefault().syncExec(() -> {
                refreshProbe[0] = new Label(fileUsage.widget, SWT.NONE);
                refreshProbe[0].setVisible(false);
                I18n.bindDynamic(refreshProbe[0], () -> {
                    displayRefreshes.incrementAndGet();
                    return "probe";
                });
                displayRefreshes.set(0);
            });

            int englishCurrentRow = currentHdfViewRow(fileUsage);
            assertTrue(fileUsage.bot().table().getTableItem(englishCurrentRow).getText(1)
                           .contains(I18n.text("dialog.fileUsage.current")));
            fileUsage.bot().table().select(englishCurrentRow);
            assertFalse(fileUsage.bot().button(I18n.text("dialog.fileUsage.requestClose")).isEnabled());
            assertFalse(fileUsage.bot().button(I18n.text("dialog.fileUsage.forceTerminate")).isEnabled());

            activateMainWindow();
            selectLanguage(I18n.Language.SIMPLIFIED_CHINESE);
            assertEquals(1, displayRefreshes.get(),
                         "one language change must refresh the Display only once");
            assertEquals(I18n.text("dialog.fileUsage.requestClose"),
                         fileUsage.bot().button(I18n.text("dialog.fileUsage.requestClose")).getText());
            assertEquals(I18n.text("dialog.fileUsage.forceTerminate"),
                         fileUsage.bot().button(I18n.text("dialog.fileUsage.forceTerminate")).getText());
            int chineseCurrentRow = currentHdfViewRow(fileUsage);
            assertTrue(fileUsage.bot().table().getTableItem(chineseCurrentRow).getText(1)
                           .contains(I18n.text("dialog.fileUsage.current")));
            fileUsage.bot().table().select(chineseCurrentRow);
            assertFalse(fileUsage.bot().button(I18n.text("dialog.fileUsage.requestClose")).isEnabled());
            assertFalse(fileUsage.bot().button(I18n.text("dialog.fileUsage.forceTerminate")).isEnabled());

            displayRefreshes.set(0);
            activateMainWindow();
            selectLanguage(I18n.Language.ENGLISH);
            assertEquals(1, displayRefreshes.get(),
                         "switching back must also refresh the Display only once");
            int restoredCurrentRow = currentHdfViewRow(fileUsage);
            assertTrue(fileUsage.bot().table().getTableItem(restoredCurrentRow).getText(1)
                           .contains(I18n.text("dialog.fileUsage.current")));
        }
        finally {
            if (refreshProbe[0] != null && !refreshProbe[0].isDisposed())
                refreshProbe[0].dispose();
            selectLanguage(I18n.Language.ENGLISH);
            if (hasTreeItem(bot.tree(), FILE_A))
                closeFile(hdfFile, false);
        }
    }

    private SWTBotShell openFileUsageDialog() {
        SWTBotButton button = bot.button(ui("button.fileUsage"));
        assertTrue(button.isEnabled(), "File Usage must be enabled for an open file");
        button.click();
        String title = ui("dialog.fileUsage.title");
        bot.waitUntil(Conditions.shellIsActive(title));
        return bot.shell(title);
    }

    private void waitForUsageResult(SWTBotShell fileUsage) {
        SWTBotTable table = fileUsage.bot().table();
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() {
                return table.rowCount() > 0;
            }

            @Override
            public String getFailureMessage() {
                return "Timed out waiting for the File Usage scan result";
            }
        });
    }

    private void waitForNoFileState(SWTBotShell fileUsage) {
        String expectedStatus = ui("dialog.fileUsage.status.noFile");
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() {
                return fileUsage.bot().label(0).getText().equals(
                                   ui("dialog.fileUsage.file", ui("dialog.fileUsage.noFile")))
                        && fileUsage.bot().label(1).getText().equals(expectedStatus)
                        && fileUsage.bot().table().rowCount() == 0
                        && fileUsage.bot().table().selectionCount() == 0;
            }

            @Override
            public String getFailureMessage() {
                return "Timed out waiting for File Usage to enter the no-file state";
            }
        });
    }

    private void assertFileUsageFile(SWTBotShell fileUsage, File file) {
        String expectedPath = Path.of(file.getAbsolutePath()).toAbsolutePath().normalize().toString();
        assertEquals(ui("dialog.fileUsage.file", expectedPath), fileUsage.bot().label(0).getText());
    }

    private int currentHdfViewRow(SWTBotShell fileUsage) {
        String marker = I18n.text("dialog.fileUsage.current");
        SWTBotTable table = fileUsage.bot().table();
        for (int row = 0; row < table.rowCount(); row++) {
            if (table.getTableItem(row).getText(1).contains(marker))
                return row;
        }
        throw new AssertionError("File Usage result did not contain the current HDFView process");
    }

    private boolean hasTreeItem(SWTBotTree tree, String filename) {
        for (var item : tree.getAllItems()) {
            if (filename.equals(item.getText()))
                return true;
        }
        return false;
    }

    private void selectLanguage(I18n.Language language) {
        if (I18n.getLanguage() == language)
            return;

        String targetKey = language == I18n.Language.ENGLISH
                ? "menu.tools.language.english"
                : "menu.tools.language.simplifiedChinese";

        bot.menu().menu(ui("menu.tools"))
                .menu(ui("menu.tools.language"))
                .menu(ui(targetKey)).click();

        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() {
                return I18n.getLanguage() == language;
            }

            @Override
            public String getFailureMessage() {
                return "Timed out waiting for language " + language;
            }
        });
    }

    private void activateMainWindow() {
        SWTBotShell mainShell = new SWTBotShell(shell);
        mainShell.activate();
        bot = mainShell.bot();
    }
}
