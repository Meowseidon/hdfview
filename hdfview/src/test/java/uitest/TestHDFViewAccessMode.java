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

import hdf.object.HObject;
import hdf.view.i18n.I18n;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Regression coverage for the persistent file access-mode controls. */
@Tag("ui")
@Tag("integration")
public class TestHDFViewAccessMode extends AbstractWindowTest {
    private static final String READ_ONLY_FILE  = "tscalarintsize.h5";
    private static final String READ_WRITE_FILE = "tattr2.h5";

    @Test
    public void readOnlyFileCanBeReopenedReadWriteWithoutDuplicateTreeNode()
    {
        selectLanguage(I18n.Language.ENGLISH);
        File hdfFile = openFile(READ_ONLY_FILE, FILE_MODE.READ_ONLY);

        try {
            SWTBotTree tree = bot.tree();
            SWTBotTreeItem fileItem = tree.getTreeItem(READ_ONLY_FILE);
            fileItem.click();

            assertAccessMode("fileAccessMode.readOnly", true);
            assertFalse(fileItem.contextMenu().contextMenu(ui("tree.new")).isEnabled(),
                        "read-only files must keep modification actions disabled");
            hideTreeContextMenu(tree);

            bot.button(ui("button.reopenReadWrite")).click();
            bot.waitUntil(new DefaultCondition() {
                @Override
                public boolean test()
                {
                    return isFileWritable(bot.tree(), READ_ONLY_FILE);
                }

                @Override
                public String getFailureMessage()
                {
                    return "Timed out waiting for the file to reopen as read/write";
                }
            });

            assertEquals(1, topLevelFileCount(tree),
                         "reopening a file must replace its node rather than duplicate it");
            assertAccessMode("fileAccessMode.readWrite", false);

            fileItem = tree.getTreeItem(READ_ONLY_FILE);
            fileItem.click();
            assertTrue(fileItem.contextMenu().contextMenu(ui("tree.new")).isEnabled(),
                       "read/write reopen must restore modification actions");
            hideTreeContextMenu(tree);
        }
        finally {
            closeFile(hdfFile, false);
        }
    }

    @Test
    public void accessModeFollowsSelectedFileLanguageAndCloseAll()
    {
        selectLanguage(I18n.Language.ENGLISH);
        File readOnlyFile  = openFile(READ_ONLY_FILE, FILE_MODE.READ_ONLY);
        File readWriteFile = openFile(READ_WRITE_FILE, FILE_MODE.READ_WRITE);

        try {
            SWTBotTree tree = bot.tree();

            tree.getTreeItem(READ_ONLY_FILE).click();
            assertAccessMode("fileAccessMode.readOnly", true);
            assertEquals(ui("fileAccessMode.file", READ_ONLY_FILE),
                         bot.label(ui("fileAccessMode.file", READ_ONLY_FILE)).getText(),
                         "the status bar must identify the selected read-only file");

            tree.getTreeItem(READ_WRITE_FILE).click();
            assertAccessMode("fileAccessMode.readWrite", false);
            assertEquals(ui("fileAccessMode.file", READ_WRITE_FILE),
                         bot.label(ui("fileAccessMode.file", READ_WRITE_FILE)).getText(),
                         "the status bar must follow the selected read/write file");

            selectLanguage(I18n.Language.SIMPLIFIED_CHINESE);
            assertAccessMode("fileAccessMode.readWrite", false);
            assertEquals(ui("button.reopenReadWrite"),
                         bot.button(ui("button.reopenReadWrite")).getText(),
                         "the access-mode action must refresh with the selected language");

            tree.getTreeItem(READ_ONLY_FILE).click();
            assertAccessMode("fileAccessMode.readOnly", true);
            assertEquals(ui("fileAccessMode.file", READ_ONLY_FILE),
                         bot.label(ui("fileAccessMode.file", READ_ONLY_FILE)).getText(),
                         "the Chinese status bar must still follow file selection");

            bot.menu().menu(ui("menu.file")).menu(ui("menu.file.closeAll")).click();
            bot.waitUntil(Conditions.treeHasRows(tree, 0));
            resetOpenFileCount();
            assertTrue(bot.label(ui("fileAccessMode.noFile")) != null,
                       "closing all files must clear the persistent access-mode status");
            assertFalse(bot.button(ui("button.reopenReadWrite")).isEnabled(),
                        "reopen must be disabled after Close All");
        }
        finally {
            selectLanguage(I18n.Language.ENGLISH);
            if (topLevelFileCount(bot.tree()) > 0) {
                closeFile(readOnlyFile, false);
                if (topLevelFileCount(bot.tree()) > 0)
                    closeFile(readWriteFile, false);
            }
        }
    }

    private void assertAccessMode(String modeKey, boolean reopenEnabled)
    {
        String mode = ui(modeKey);
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test()
            {
                try {
                    return bot.label(mode) != null;
                }
                catch (Exception ex) {
                    return false;
                }
            }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for access-mode label " + mode;
            }
        });

        assertEquals(mode, bot.label(mode).getText());
        assertEquals(reopenEnabled, bot.button(ui("button.reopenReadWrite")).isEnabled(),
                     "reopen button state must match the selected file access mode");
    }

    private boolean isFileWritable(SWTBotTree tree, String filename)
    {
        try {
            SWTBotTreeItem item = tree.getTreeItem(filename);
            final boolean[] writable = new boolean[] {false};
            Display.getDefault().syncExec(() -> {
                Object data = item.widget.getData();
                if (data instanceof HObject && ((HObject)data).getFileFormat() != null)
                    writable[0] = !((HObject)data).getFileFormat().isReadOnly();
            });
            return writable[0];
        }
        catch (Exception ex) {
            return false;
        }
    }

    private int topLevelFileCount(SWTBotTree tree)
    {
        final int[] count = new int[] {0};
        Display.getDefault().syncExec(() -> {
            if (tree != null && tree.widget != null && !tree.widget.isDisposed())
                count[0] = tree.widget.getItemCount();
        });
        return count[0];
    }

    private void hideTreeContextMenu(SWTBotTree tree)
    {
        Display.getDefault().syncExec(() -> {
            Menu menu = tree.widget.getMenu();
            if (menu != null && !menu.isDisposed())
                menu.setVisible(false);
        });
    }

    private void selectLanguage(I18n.Language language)
    {
        if (I18n.getLanguage() == language)
            return;

        String targetKey = language == I18n.Language.ENGLISH
            ? "menu.tools.language.english"
            : "menu.tools.language.simplifiedChinese";

        bot.menu().menu(ui("menu.tools"))
            .menu(ui("menu.tools.language"))
            .menu(ui(targetKey))
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
                return "Timed out waiting for language " + language;
            }
        });
    }
}
