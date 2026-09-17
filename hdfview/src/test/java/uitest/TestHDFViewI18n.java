/****************************************************************************
 * Copyright by The HDF Group.                                               *
 * Copyright by the Board of Trustees of the University of Illinois.         *
 * All rights reserved.                                                       *
 *                                                                            *
 * This file is part of the HDF Java Products distribution.                   *
 * The full copyright notice, including terms governing use, modification,   *
 * and redistribution, is contained in the COPYING file, which can be found   *
 * at the root of the source code distribution tree,                          *
 * or in https://www.hdfgroup.org/licenses.                                   *
 * If you do not have access to either file, you may request a copy from      *
 * help@hdfgroup.org.                                                         *
 *                                                                            *
 ****************************************************************************/

package uitest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import hdf.object.HObject;
import hdf.view.i18n.I18n;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTabItem;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Regression coverage for runtime language switching in the core HDFView UI. */
@Tag("ui")
@Tag("integration")
public class TestHDFViewI18n extends AbstractWindowTest {
    private static final String FILE_NAME = "tscalarintsize.h5";
    private static final String DATASET_NAME = "DS08BITS";

    @Test
    public void switchingCoreLanguagePreservesOpenDatasetAndUsesLanguageForNewDialogs()
    {
        selectLanguage(I18n.Language.ENGLISH);
        File hdfFile = openFile(FILE_NAME, FILE_MODE.READ_ONLY);

        SWTBotTreeItem dataset = null;
        HObject selectedObject = null;
        TreeItem selectedTreeItem = null;

        try {
            SWTBotTree tree = bot.tree();
            dataset = tree.getTreeItem(FILE_NAME).getNode(DATASET_NAME);
            dataset.click();
            selectedObject = objectFor(dataset);
            selectedTreeItem = selectedTreeItem(tree);

            assertCoreEnglishUi();
            assertTrue(waitForTab("tab.dataContent").isActive(),
                       "an English Dataset selection must activate Data Content");
            assertTrue(dataset.contextMenu().contextMenu(I18n.text("tree.open")).isEnabled(),
                       "the TreeView context menu must expose the translated Open action");
            hideTreeContextMenu(tree);

            selectLanguage(I18n.Language.SIMPLIFIED_CHINESE);
            assertCoreChineseUi();
            assertTrue(waitForTab("tab.dataContent").isActive(),
                       "switching language must keep the Dataset Data Content tab active");
            assertSame(selectedObject, objectFor(dataset),
                       "switching language must not replace the selected Dataset model");
            assertSame(selectedTreeItem, selectedTreeItem(tree),
                       "switching language must not replace the Tree selection");
            assertNotNull(tree.getTreeItem(FILE_NAME),
                          "switching language must keep the open file in the TreeView");
            assertTrue(dataset.contextMenu().contextMenu(I18n.text("tree.open")).isEnabled(),
                       "the TreeView context menu must refresh to Simplified Chinese");
            hideTreeContextMenu(tree);

            openJavaVersionDialogAndClose(I18n.Language.SIMPLIFIED_CHINESE);

            selectLanguage(I18n.Language.ENGLISH);
            assertCoreEnglishUi();
            assertTrue(waitForTab("tab.dataContent").isActive(),
                       "switching back to English must keep Data Content active");
            assertSame(selectedObject, objectFor(dataset),
                       "switching back must not replace the selected Dataset model");
            assertSame(selectedTreeItem, selectedTreeItem(tree),
                       "switching back must not replace the Tree selection");
        }
        finally {
            try {
                selectLanguage(I18n.Language.ENGLISH);
            }
            catch (Exception ex) {
                // Preserve the primary test failure while leaving normal runs in English.
            }
            closeFile(hdfFile, false);
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

    private void assertCoreEnglishUi()
    {
        assertEquals(I18n.Language.ENGLISH, I18n.getLanguage());
        assertEquals(I18n.text("menu.file"), bot.menu().menu(I18n.text("menu.file")).getText());
        assertEquals(I18n.text("menu.tools"), bot.menu().menu(I18n.text("menu.tools")).getText());
        assertEquals("Data Content", bot.tabItem(I18n.text("tab.dataContent")).getText());
        assertEquals("Object Attribute Info", bot.tabItem(I18n.text("tab.objectAttributeInfo")).getText());
        assertEquals("General Object Info", bot.tabItem(I18n.text("tab.generalObjectInfo")).getText());
        assertEquals(I18n.text("button.recentFiles"), bot.button(I18n.text("button.recentFiles")).getText());
        assertEquals(I18n.text("button.clearText"), bot.button(I18n.text("button.clearText")).getText());
    }

    private void assertCoreChineseUi()
    {
        assertEquals(I18n.Language.SIMPLIFIED_CHINESE, I18n.getLanguage());
        assertEquals(I18n.text("menu.file"), bot.menu().menu(I18n.text("menu.file")).getText());
        assertEquals(I18n.text("menu.tools"), bot.menu().menu(I18n.text("menu.tools")).getText());
        assertEquals("数据内容", bot.tabItem(I18n.text("tab.dataContent")).getText());
        assertEquals("对象属性信息", bot.tabItem(I18n.text("tab.objectAttributeInfo")).getText());
        assertEquals("对象常规信息", bot.tabItem(I18n.text("tab.generalObjectInfo")).getText());
        assertEquals(I18n.text("button.recentFiles"), bot.button(I18n.text("button.recentFiles")).getText());
        assertEquals(I18n.text("button.clearText"), bot.button(I18n.text("button.clearText")).getText());
    }

    private void openJavaVersionDialogAndClose(I18n.Language language)
    {
        bot.menu().menu(I18n.text("menu.help"))
            .menu(I18n.text("menu.help.javaVersion"))
            .click();

        String title = I18n.text("dialog.javaVersion.title");
        SWTBotShell versionDialog = bot.shell(title);
        versionDialog.activate();
        bot.waitUntil(Conditions.shellIsActive(title));
        assertNotNull(versionDialog.bot().button(I18n.text("button.ok")),
                      "new HDFView dialogs must use the selected language for OK");
        versionDialog.bot().button(I18n.text("button.ok")).click();
        bot.waitUntil(Conditions.shellCloses(versionDialog));
        assertEquals(language, I18n.getLanguage());
    }

    private TreeItem selectedTreeItem(SWTBotTree tree)
    {
        final TreeItem[] selected = new TreeItem[1];
        Display.getDefault().syncExec(() -> {
            TreeItem[] items = tree.widget.getSelection();
            assertEquals(1, items.length, "HDFView must keep one selected Tree item");
            selected[0] = items[0];
        });
        return selected[0];
    }

    private HObject objectFor(SWTBotTreeItem item)
    {
        final HObject[] object = new HObject[1];
        Display.getDefault().syncExec(() -> object[0] = (HObject)item.widget.getData());
        return object[0];
    }

    private void hideTreeContextMenu(SWTBotTree tree)
    {
        Display.getDefault().syncExec(() -> {
            if (tree.widget.getMenu() != null)
                tree.widget.getMenu().setVisible(false);
        });
    }

    private SWTBotTabItem waitForTab(String tabKey)
    {
        String tabName = I18n.text(tabKey);
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test()
            {
                try {
                    bot.tabItem(tabName);
                    return true;
                }
                catch (org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException ex) {
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
}
