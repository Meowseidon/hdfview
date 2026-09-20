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

import hdf.view.i18n.I18n;

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
