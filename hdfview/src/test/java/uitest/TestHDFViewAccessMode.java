/****************************************************************************
 * Copyright by The HDF Group.                                               *
 * All rights reserved.                                                       *
 *                                                                            *
 * This file is part of the HDF Java Products distribution.                   *
 * The full copyright notice for this file is contained in the COPYING file.  *
 ****************************************************************************/

package uitest;

import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.allOf;
import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.withId;
import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.widgetOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import hdf.object.HObject;
import hdf.view.i18n.I18n;

import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCombo;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Regression coverage for the inline file access-mode selector. */
@Tag("ui")
@Tag("integration")
public class TestHDFViewAccessMode extends AbstractWindowTest {
    private static final String READ_ONLY_FILE  = "tscalarintsize.h5";
    private static final String READ_WRITE_FILE = "tattr2.h5";
    private static final int READ_ONLY_INDEX  = 0;
    private static final int READ_WRITE_INDEX = 1;

    @Test
    public void readOnlyFileCanBeReopenedReadWriteWithoutDuplicateTreeNode()
    {
        selectLanguage(I18n.Language.ENGLISH);
        File hdfFile = openFile(READ_ONLY_FILE, FILE_MODE.READ_ONLY);

        try {
            SWTBotTree tree = bot.tree();
            SWTBotTreeItem fileItem = tree.getTreeItem(READ_ONLY_FILE);
            fileItem.click();

            assertAccessMode("fileAccessMode.readOnly");
            assertFalse(fileItem.contextMenu().contextMenu(ui("tree.new")).isEnabled(),
                        "read-only files must keep modification actions disabled");
            hideTreeContextMenu(tree);

            accessModeSelector().setSelection(READ_WRITE_INDEX);
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
            assertAccessMode("fileAccessMode.readWrite");

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
    public void readWriteFileCanBeReopenedReadOnlyWithoutDuplicateTreeNode()
    {
        selectLanguage(I18n.Language.ENGLISH);
        File hdfFile = openFile(READ_WRITE_FILE, FILE_MODE.READ_WRITE);

        try {
            SWTBotTree tree = bot.tree();
            SWTBotTreeItem fileItem = tree.getTreeItem(READ_WRITE_FILE);
            fileItem.click();

            assertAccessMode("fileAccessMode.readWrite");
            assertTrue(fileItem.contextMenu().contextMenu(ui("tree.new")).isEnabled(),
                       "read/write files must keep modification actions enabled");
            hideTreeContextMenu(tree);

            accessModeSelector().setSelection(READ_ONLY_INDEX);
            bot.waitUntil(new DefaultCondition() {
                @Override
                public boolean test()
                {
                    return isFileReadOnly(bot.tree(), READ_WRITE_FILE);
                }

                @Override
                public String getFailureMessage()
                {
                    return "Timed out waiting for the file to reopen as read-only";
                }
            });

            assertEquals(1, topLevelFileCount(tree),
                         "reopening a file must replace its node rather than duplicate it");
            assertAccessMode("fileAccessMode.readOnly");

            fileItem = tree.getTreeItem(READ_WRITE_FILE);
            assertFalse(fileItem.contextMenu().contextMenu(ui("tree.new")).isEnabled(),
                        "read-only reopen must disable modification actions");
            hideTreeContextMenu(tree);
        }
        finally {
            closeFile(hdfFile, false);
        }
    }

    @Test
    public void swmrReadIsShownAsASeparateNonSelectableMode()
    {
        selectLanguage(I18n.Language.ENGLISH);
        File hdfFile = openFile(READ_ONLY_FILE, FILE_MODE.MULTI_READ_ONLY);

        try {
            SWTBotTree tree = bot.tree();
            tree.getTreeItem(READ_ONLY_FILE).click();

            SWTBotCombo selector = accessModeSelector();
            bot.waitUntil(new DefaultCondition() {
                @Override
                public boolean test()
                {
                    return selector.getText().equals(ui("fileAccessMode.swmrRead"));
                }

                @Override
                public String getFailureMessage()
                {
                    return "Timed out waiting for the SWMR access-mode selector";
                }
            });
            assertEquals(1, selector.itemCount(), "SWMR must not expose ordinary modes");
            assertFalse(selector.isEnabled(), "SWMR mode must not be changed by the ordinary selector");
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
            assertAccessMode("fileAccessMode.readOnly");

            tree.getTreeItem(READ_WRITE_FILE).click();
            assertAccessMode("fileAccessMode.readWrite");

            SWTBotTreeItem readWriteRoot = tree.getTreeItem(READ_WRITE_FILE);
            SWTBotTreeItem group = readWriteRoot.getNode("g2");
            group.expand();
            group.click();
            assertAccessMode("fileAccessMode.readWrite");

            SWTBotTreeItem dataset = group.getNode("array");
            dataset.click();
            assertAccessMode("fileAccessMode.readWrite");

            selectLanguage(I18n.Language.SIMPLIFIED_CHINESE);
            assertAccessMode("fileAccessMode.readWrite");

            tree.getTreeItem(READ_ONLY_FILE).click();
            assertAccessMode("fileAccessMode.readOnly");

            selectLanguage(I18n.Language.ENGLISH);
            assertAccessMode("fileAccessMode.readOnly");

            bot.menu().menu(ui("menu.file")).menu(ui("menu.file.closeAll")).click();
            bot.waitUntil(Conditions.treeHasRows(tree, 0));
            resetOpenFileCount();
            SWTBotCombo selector = accessModeSelector();
            assertEquals(ui("fileAccessMode.noFile"), selector.getText(),
                         "closing all files must clear the access-mode selector");
            assertFalse(selector.isEnabled(),
                        "the access-mode selector must be disabled after Close All");
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

    private void assertAccessMode(String modeKey)
    {
        String mode = ui(modeKey);
        SWTBotCombo selector = accessModeSelector();
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test()
            {
                try {
                    return selector.getText().equals(mode);
                }
                catch (Exception ex) {
                    return false;
                }
            }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for access-mode selector " + mode;
            }
        });

        assertEquals(mode, selector.getText());
        assertTrue(selector.isEnabled(), "normal read-only/read-write modes must be selectable");
        assertEquals(2, selector.itemCount(), "normal access mode selector must have two choices");
        String[] items = selector.items();
        assertEquals(ui("fileAccessMode.readOnly"), items[READ_ONLY_INDEX]);
        assertEquals(ui("fileAccessMode.readWrite"), items[READ_WRITE_INDEX]);
    }

    private SWTBotCombo accessModeSelector()
    {
        return new SWTBotCombo(bot.widget(allOf(widgetOfType(Combo.class),
                                                withId(I18n.WIDGET_KEY, "accessModeSelector"))));
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

    private boolean isFileReadOnly(SWTBotTree tree, String filename)
    {
        try {
            SWTBotTreeItem item = tree.getTreeItem(filename);
            final boolean[] readOnly = new boolean[] {false};
            Display.getDefault().syncExec(() -> {
                Object data = item.widget.getData();
                if (data instanceof HObject && ((HObject)data).getFileFormat() != null)
                    readOnly[0] = ((HObject)data).getFileFormat().isReadOnly();
            });
            return readOnly[0];
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
