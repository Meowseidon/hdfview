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
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swtbot.swt.finder.matchers.WithRegex;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotButton;
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

    @Test
    public void chineseCreateAndDeleteGroupAndDatasetUsesTheSameActions()
    {
        selectLanguage(I18n.Language.SIMPLIFIED_CHINESE);
        String filename    = "testi18nobjects.h5";
        String groupName   = "test_i18n_group";
        String datasetName = "test_i18n_dataset";
        File hdfFile = createFile(filename);

        try {
            SWTBotTree tree = bot.tree();
            SWTBotTreeItem fileItem = tree.getTreeItem(filename);

            fileItem.click();
            fileItem.contextMenu().contextMenu(I18n.text("tree.new"))
                .menu(I18n.text("tree.new.group")).click();

            SWTBotShell groupShell = bot.shell(I18n.text("dialog.newGroup.title"));
            groupShell.activate();
            bot.waitUntil(Conditions.shellIsActive(groupShell.getText()));
            groupShell.bot().text(0).setText(groupName);
            groupShell.bot().button(I18n.text("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(groupShell));

            fileItem = tree.getTreeItem(filename);
            assertTrue(fileItem.getNode(0).getText().equals(groupName),
                       "Chinese New Group must create the requested object");

            SWTBotTreeItem groupItem = fileItem.getNode(0);
            groupItem.click();
            groupItem.contextMenu().contextMenu(I18n.text("tree.new"))
                .menu(I18n.text("tree.new.dataset")).click();

            SWTBotShell datasetShell = bot.shell(I18n.text("dialog.newDataset.title"));
            datasetShell.activate();
            bot.waitUntil(Conditions.shellIsActive(datasetShell.getText()));
            datasetShell.bot().text(0).setText(datasetName);
            datasetShell.bot().text(2).setText("2 x 2");
            datasetShell.bot().button(I18n.text("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(datasetShell));

            fileItem = tree.getTreeItem(filename);
            groupItem = fileItem.getNode(0);
            groupItem.expand();
            SWTBotTreeItem datasetItem = groupItem.getNode(0);
            assertEquals(datasetName, datasetItem.getText(),
                         "Chinese New Dataset must create the requested object");

            datasetItem.click();
            closeDataObject(new SWTBotShell(shell));
            datasetItem.contextMenu().contextMenu(I18n.text("tree.delete")).click();
            bot.waitUntil(Conditions.waitForShell(
                WithRegex.withRegex(".*" + VERSION + " - " + I18n.text("action.delete"))));
            SWTBotShell deleteDatasetShell = bot.shell(applicationDialogTitle("action.delete"));
            deleteDatasetShell.activate();
            bot.waitUntil(Conditions.shellIsActive(deleteDatasetShell.getText()));
            deleteDatasetShell.bot().button(I18n.text("button.yes")).click();
            bot.waitUntil(Conditions.shellCloses(deleteDatasetShell));
            waitForVisibleRows(tree, 2);
            assertEquals(groupName, tree.getTreeItem(filename).getNode(0).getText(),
                         "Chinese Delete must remove the Dataset and keep its Group");

            SWTBotTreeItem groupAfterDatasetDelete = tree.getTreeItem(filename).getNode(0);
            groupAfterDatasetDelete.click();
            groupAfterDatasetDelete.contextMenu().contextMenu(I18n.text("tree.delete")).click();
            bot.waitUntil(Conditions.waitForShell(
                WithRegex.withRegex(".*" + VERSION + " - " + I18n.text("action.delete"))));
            SWTBotShell deleteGroupShell = bot.shell(applicationDialogTitle("action.delete"));
            deleteGroupShell.activate();
            bot.waitUntil(Conditions.shellIsActive(deleteGroupShell.getText()));
            deleteGroupShell.bot().button(I18n.text("button.yes")).click();
            bot.waitUntil(Conditions.shellCloses(deleteGroupShell));
            waitForVisibleRows(tree, 1);
            assertEquals(filename, tree.getTreeItem(filename).getText(),
                         "Chinese Delete must remove the Group and keep the file open");
        }
        finally {
            try {
                closeFile(hdfFile, true);
            }
            finally {
                selectLanguage(I18n.Language.ENGLISH);
            }
        }
    }

    @Test
    public void switchingLanguageRelayoutsExistingControlsWithoutChangingMainWindow()
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

            Rectangle initialWindowBounds = mainWindowBounds();
            SWTBotButton recentFiles = bot.button(ui("button.recentFiles"));
            SWTBotButton clearText   = bot.button(ui("button.clearText"));

            // Make the existing controls narrower than their preferred size.
            // A language change must restore their layout without packing the
            // top-level window.
            setControlWidth(recentFiles, 1);
            setControlWidth(clearText, 1);

            selectLanguage(I18n.Language.SIMPLIFIED_CHINESE);
            assertWindowBoundsUnchanged(initialWindowBounds);
            assertControlFitsPreferredSize(recentFiles);
            assertControlFitsPreferredSize(clearText);
            assertCoreChineseUi();
            assertSelectionPreserved(tree, dataset, selectedObject, selectedTreeItem);

            selectLanguage(I18n.Language.ENGLISH);
            assertWindowBoundsUnchanged(initialWindowBounds);
            assertControlFitsPreferredSize(recentFiles);
            assertControlFitsPreferredSize(clearText);
            assertCoreEnglishUi();
            assertSelectionPreserved(tree, dataset, selectedObject, selectedTreeItem);

            // Repeated switches must not keep growing or moving the main shell.
            for (int i = 0; i < 2; i++) {
                selectLanguage(I18n.Language.SIMPLIFIED_CHINESE);
                assertWindowBoundsUnchanged(initialWindowBounds);
                selectLanguage(I18n.Language.ENGLISH);
                assertWindowBoundsUnchanged(initialWindowBounds);
            }

            openUserOptionsAndSwitchLanguage(initialWindowBounds);
            assertWindowBoundsUnchanged(initialWindowBounds);
            assertSelectionPreserved(tree, dataset, selectedObject, selectedTreeItem);
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

    private void openUserOptionsAndSwitchLanguage(Rectangle initialWindowBounds)
    {
        bot.menu().menu(ui("menu.tools")).menu(ui("menu.tools.preferences")).click();

        SWTBotShell options = bot.shell(ui("dialog.userOptions.title"));
        options.activate();
        bot.waitUntil(Conditions.shellIsActive(options.getText()));
        Rectangle initialDialogBounds = dialogBounds(options);

        // The Preferences dialog is modeless in the HDFView test runtime, so
        // the existing main-window language action can refresh it in place.
        selectLanguageFromMainWindow(I18n.Language.SIMPLIFIED_CHINESE);
        assertEquals(ui("dialog.userOptions.title"), options.getText());
        assertControlFitsPreferredSize(options.bot().button(ui("button.cancel")));
        assertControlFitsPreferredSize(options.bot().button(ui("button.applyAndClose")));
        assertDialogPositionUnchanged(initialDialogBounds, options);

        selectLanguageFromMainWindow(I18n.Language.ENGLISH);
        assertEquals(ui("dialog.userOptions.title"), options.getText());
        assertControlFitsPreferredSize(options.bot().button(ui("button.cancel")));
        assertDialogPositionUnchanged(initialDialogBounds, options);

        options.activate();
        options.bot().button(ui("button.cancel")).click();
        bot.waitUntil(Conditions.shellCloses(options));
    }

    private void selectLanguageFromMainWindow(I18n.Language language)
    {
        SWTBotShell mainShell = new SWTBotShell(shell);
        mainShell.activate();
        bot.waitUntil(Conditions.shellIsActive(mainShell.getText()));

        if (I18n.getLanguage() == language)
            return;

        String targetKey = language == I18n.Language.ENGLISH
            ? "menu.tools.language.english"
            : "menu.tools.language.simplifiedChinese";

        mainShell.bot().menu().menu(ui("menu.tools"))
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
                return "Timed out waiting for HDFView language to become " + language;
            }
        });
    }

    private Rectangle mainWindowBounds()
    {
        final Rectangle[] bounds = new Rectangle[1];
        Display.getDefault().syncExec(() -> bounds[0] = shell.getBounds());
        return bounds[0];
    }

    private void assertWindowBoundsUnchanged(Rectangle expected)
    {
        Rectangle actual = mainWindowBounds();
        assertEquals(expected.x, actual.x, "language switching must not move the main window horizontally");
        assertEquals(expected.y, actual.y, "language switching must not move the main window vertically");
        assertEquals(expected.width, actual.width, "language switching must not resize the main window width");
        assertEquals(expected.height, actual.height, "language switching must not resize the main window height");
    }

    private void setControlWidth(SWTBotButton button, int width)
    {
        Display.getDefault().syncExec(() -> {
            Point size = button.widget.getSize();
            button.widget.setSize(width, size.y);
        });
    }

    private void assertControlFitsPreferredSize(SWTBotButton button)
    {
        final int[] sizes = new int[2];
        Display.getDefault().syncExec(() -> {
            sizes[0] = button.widget.getSize().x;
            sizes[1] = button.widget.computeSize(org.eclipse.swt.SWT.DEFAULT,
                                                 org.eclipse.swt.SWT.DEFAULT, true).x;
        });
        assertTrue(sizes[0] >= sizes[1],
                   "localized control is narrower than its preferred size: actual=" + sizes[0] +
                       ", preferred=" + sizes[1]);
    }

    private void assertDialogPositionUnchanged(Rectangle initialBounds, SWTBotShell dialog)
    {
        Rectangle actual = dialogBounds(dialog);
        assertEquals(initialBounds.x, actual.x,
                     "language switching must not move the User Options dialog horizontally");
        assertEquals(initialBounds.y, actual.y,
                     "language switching must not move the User Options dialog vertically");
    }

    private Rectangle dialogBounds(SWTBotShell dialog)
    {
        final Rectangle[] bounds = new Rectangle[1];
        Display.getDefault().syncExec(() -> bounds[0] = dialog.widget.getBounds());
        return bounds[0];
    }

    private void assertSelectionPreserved(SWTBotTree tree, SWTBotTreeItem dataset,
                                           HObject selectedObject, TreeItem selectedTreeItem)
    {
        assertTrue(waitForTab("tab.dataContent").isActive(),
                   "language switching must keep the Dataset Data Content tab active");
        assertSame(selectedObject, objectFor(dataset),
                   "language switching must not replace the selected Dataset model");
        assertSame(selectedTreeItem, selectedTreeItem(tree),
                   "language switching must not replace the Tree selection");
    }

    private void waitForVisibleRows(final SWTBotTree tree, final int rows)
    {
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test()
            {
                return tree.visibleRowCount() == rows;
            }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for " + rows + " visible TreeView rows (actual "
                    + tree.visibleRowCount() + ")";
            }
        });
    }

    private void assertCoreEnglishUi()
    {
        assertEquals(I18n.Language.ENGLISH, I18n.getLanguage());
        assertEquals(I18n.text("menu.file"), bot.menu().menu(I18n.text("menu.file")).getText());
        assertEquals(I18n.text("menu.tools"), bot.menu().menu(I18n.text("menu.tools")).getText());
        assertEquals(I18n.text("tab.dataContent"), bot.tabItem(I18n.text("tab.dataContent")).getText());
        assertEquals(I18n.text("tab.objectAttributeInfo"), bot.tabItem(I18n.text("tab.objectAttributeInfo")).getText());
        assertEquals(I18n.text("tab.generalObjectInfo"), bot.tabItem(I18n.text("tab.generalObjectInfo")).getText());
        assertEquals(I18n.text("button.recentFiles"), bot.button(I18n.text("button.recentFiles")).getText());
        assertEquals(I18n.text("button.clearText"), bot.button(I18n.text("button.clearText")).getText());
    }

    private void assertCoreChineseUi()
    {
        assertEquals(I18n.Language.SIMPLIFIED_CHINESE, I18n.getLanguage());
        assertEquals(I18n.text("menu.file"), bot.menu().menu(I18n.text("menu.file")).getText());
        assertEquals(I18n.text("menu.tools"), bot.menu().menu(I18n.text("menu.tools")).getText());
        assertEquals(I18n.text("tab.dataContent"), bot.tabItem(I18n.text("tab.dataContent")).getText());
        assertEquals(I18n.text("tab.objectAttributeInfo"), bot.tabItem(I18n.text("tab.objectAttributeInfo")).getText());
        assertEquals(I18n.text("tab.generalObjectInfo"), bot.tabItem(I18n.text("tab.generalObjectInfo")).getText());
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
