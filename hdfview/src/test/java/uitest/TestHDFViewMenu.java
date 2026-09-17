package uitest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import static org.eclipse.swtbot.swt.finder.waits.Conditions.shellCloses;

import java.io.File;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotRadio;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotText;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;

@Tag("ui")
@Tag("integration")
public class TestHDFViewMenu extends AbstractWindowTest {
    @Test
    public void verifyOpenButtonEnabled()
    {
        try {
            boolean status = bot.toolbarButtonWithTooltip(ui("toolbar.open")).isEnabled();
            assertTrue(status, "verifyOpenButtonEnabled() open button not enabled ");
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
    }

    @Test
    public void verifyCloseButtonEnabled()
    {
        try {
            boolean status = bot.toolbarButtonWithTooltip(ui("toolbar.close")).isEnabled();
            assertTrue(status, "verifyCloseButtonEnabled() close button not enabled");
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
    }

    @Test
    public void verifyHelpButtonEnabled()
    {
        try {
            boolean status = bot.toolbarButtonWithTooltip(ui("toolbar.help")).isEnabled();
            assertTrue(status, "verifyHelpButtonEnabled() help button not enabled");
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
    }

    @Test
    public void verifyHDF4ButtonEnabled()
    {
        try {
            boolean status = bot.toolbarButtonWithTooltip(ui("toolbar.hdf4Library")).isEnabled();
            assertTrue(status, "verifyHDF4ButtonEnabled() HDF4 button not enabled");
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
    }

    @Test
    public void verifyHDF5ButtonEnabled()
    {
        try {
            boolean status = bot.toolbarButtonWithTooltip(ui("toolbar.hdf5Library")).isEnabled();
            assertTrue(status, "verifyHDF5ButtonEnabled() HDF5 button not enabled");
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
    }

    @Test
    public void verifyTextInLabelWhenClickingHDF4Button()
    {
        try {
            bot.toolbarButtonWithTooltip(ui("toolbar.hdf4Library")).click();

            SWTBotShell botshell = bot.shell(ui("dialog.libraryVersion.title"));
            botshell.activate();
            bot.waitUntil(Conditions.shellIsActive(ui("dialog.libraryVersion.title")));

            String val = botshell.bot().label(1).getText();
            assertTrue(val.equals(HDF4VERSION),
                       constructWrongValueMessage("verifyTextInLabelWhenClickingHDF4Button()",
                                                  "wrong label text", HDF4VERSION, val));
            botshell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(botshell));
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
    }

    @Test
    public void verifyTextInLabelWhenClickingHDF5Button()
    {
        try {
            bot.toolbarButtonWithTooltip(ui("toolbar.hdf5Library")).click();

            SWTBotShell botshell = bot.shell(ui("dialog.libraryVersion.title"));
            botshell.activate();
            bot.waitUntil(Conditions.shellIsActive(ui("dialog.libraryVersion.title")));

            String val = botshell.bot().label(1).getText();
            assertTrue(val.equals(HDF5VERSION),
                       constructWrongValueMessage("verifyTextInLabelWhenClickingHDF5Button()",
                                                  "wrong label text", HDF5VERSION, val));

            botshell.bot().label(HDF5VERSION);
            botshell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(botshell));
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
    }

    @Test
    public void verifyButtonOpen()
    {
        String filename = "testopenbutton.hdf";
        File hdfFile    = createFile(filename);

        try {
            closeFile(hdfFile, false);

            openFile(filename, FILE_MODE.READ_ONLY);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
        finally {
            try {
                closeFile(hdfFile, true);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Test
    public void verifyButtonClose()
    {
        String filename = "closebutton.hdf";
        File hdfFile    = createFile(filename);

        try {
            SWTBotTree filetree    = bot.tree();
            SWTBotTreeItem[] items = filetree.getAllItems();

            checkFileTree(filetree, "verifyButtonClose()", 1, filename);

            items[0].click();

            bot.toolbarButtonWithTooltip(ui("toolbar.close")).click();

            resetOpenFileCount();

            assertTrue(hdfFile.delete(), "verifyButtonClose() file '" + hdfFile + "' not deleted");
            assertFalse(hdfFile.exists(), "verifyButtonClose() file '" + hdfFile + "' wasn't gone");
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
        finally {
            try {
                if (hdfFile.exists())
                    closeFile(hdfFile, true);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Test
    public void verifyMenuOpen()
    {
        String filename = "testopenfile.hdf";
        File hdf_file   = createFile(filename);

        try {
            closeFile(hdf_file, false);

            SWTBotMenu fileMenuItem = bot.menu().menu(ui("menu.file"));
            fileMenuItem.menu(ui("action.open")).click();

            SWTBotShell shell = bot.shell(ui("dialog.enterFileName.title"));
            shell.activate();
            bot.waitUntil(Conditions.shellIsActive(shell.getText()));

            SWTBotText text = shell.bot().text();
            text.setText(filename);

            String val = text.getText();
            assertTrue(val.equals(filename),
                       constructWrongValueMessage("verifyMenuOpen()", "wrong file name", filename, val));

            shell.bot().button(ui("button.ok")).click();
            bot.waitUntil(shellCloses(shell));

            SWTBotTree filetree = bot.tree();
            assertTrue(filetree.visibleRowCount() == 1,
                       constructWrongValueMessage("verifyMenuOpen()", "filetree wrong row count", "1",
                                                  String.valueOf(filetree.visibleRowCount())));
            assertTrue(filetree.getAllItems()[0].getText().compareTo(filename) == 0,
                       "verifyMenuOpen() filetree is missing file '" + filename + "'");
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
        finally {
            try {
                closeFile(hdf_file, true);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Test
    public void verifyMenuOpenReadOnly()
    {
        String filename = "testopenrofile.h5";
        File hdf_file   = createFile(filename);

        try {
            closeFile(hdf_file, false);

            SWTBotMenu fileMenuItem = bot.menu().menu(ui("menu.file"));
            fileMenuItem.menu(ui("tree.openAs")).menu(ui("menu.file.openAs.readOnly")).click();

            SWTBotShell shell = bot.shell(ui("dialog.enterFileName.title"));
            shell.activate();
            bot.waitUntil(Conditions.shellIsActive(shell.getText()));

            SWTBotText text = shell.bot().text();
            text.setText(filename);

            String val = text.getText();
            assertTrue(val.equals(filename), constructWrongValueMessage("verifyMenuOpenReadOnly()",
                                                                        "wrong file name", filename, val));

            shell.bot().button(ui("button.ok")).click();
            bot.waitUntil(shellCloses(shell));

            SWTBotTree filetree    = bot.tree();
            SWTBotTreeItem[] items = filetree.getAllItems();
            assertTrue(filetree.visibleRowCount() == 1,
                       constructWrongValueMessage("verifyMenuOpenReadOnly()", "filetree wrong row count", "1",
                                                  String.valueOf(filetree.visibleRowCount())));
            assertTrue(items[0].getText().compareTo(filename) == 0,
                       "verifyMenuOpenReadOnly() filetree is missing file '" + filename + "'");

            items[0].click();

            assertFalse(items[0].contextMenu().contextMenu(ui("tree.new")).isEnabled(),
                        "verifyMenuOpenReadOnly() error: New Menu Item is enabled.");
            assertFalse(items[0].contextMenu().contextMenu(ui("tree.cut")).isEnabled(),
                        "verifyMenuOpenReadOnly() error: Cut Menu Item is enabled.");
            assertFalse(items[0].contextMenu().contextMenu(ui("tree.paste")).isEnabled(),
                        "verifyMenuOpenReadOnly() error: Paste Menu Item is enabled.");
            assertFalse(items[0].contextMenu().contextMenu(ui("tree.delete")).isEnabled(),
                        "verifyMenuOpenReadOnly() error: Delete Menu Item is enabled.");
            assertFalse(items[0].contextMenu().contextMenu(ui("tree.rename")).isEnabled(),
                        "verifyMenuOpenReadOnly() error: Rename Menu Item is enabled.");
            assertFalse(items[0].contextMenu().contextMenu(ui("tree.setLibVersionBounds")).isEnabled(),
                        "verifyMenuOpenReadOnly() error: Set Lib Version Bounds Menu Item is enabled.");
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
        finally {
            try {
                closeFile(hdf_file, true);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Test
    public void verifyMenuNewHDF4()
    {
        String filename = "testfile.hdf";
        File hdf_file   = null;

        try {
            SWTBotMenu fileMenuItem = bot.menu().menu(ui("menu.file"));
            fileMenuItem.menu(ui("menu.file.new")).menu(ui("menu.tools.convertImage.hdf4")).click();

            hdf_file = new File(workDir, filename);
            if (hdf_file.exists())
                hdf_file.delete();

            SWTBotShell shell = bot.shell(ui("dialog.enterFileName.title"));
            shell.activate();
            bot.waitUntil(Conditions.shellIsActive(shell.getText()));

            SWTBotText text = shell.bot().text();
            text.setText(filename);

            String val = text.getText();
            assertTrue(val.equals(filename),
                       constructWrongValueMessage("verifyMenuNewHDF4()", "wrong file name", filename, val));

            shell.bot().button(ui("button.ok")).click();
            bot.waitUntil(shellCloses(shell));

            SWTBotTree filetree = bot.tree();
            assertTrue(
                filetree.visibleRowCount() == 1,
                constructWrongValueMessage("verifyMenuNewHDF4()", "filetree wrong row count", "1", val));
            assertTrue(filetree.getAllItems()[0].getText().compareTo(filename) == 0,
                       "verifyMenuNewHDF4() filetree is missing file '" + filename + "'");
            assertTrue(hdf_file.exists(), "verifyMenuNewHDF4() file '" + hdf_file + "' not created");
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
        finally {
            try {
                closeFile(hdf_file, true);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Test
    public void verifyMenuNewHDF5()
    {
        String filename = "testfile.h5";
        File hdf_file   = null;

        try {
            SWTBotMenu fileMenuItem = bot.menu().menu(ui("menu.file"));
            fileMenuItem.menu(ui("menu.file.new")).menu(ui("menu.tools.convertImage.hdf5")).click();

            hdf_file = new File(workDir, filename);
            if (hdf_file.exists())
                hdf_file.delete();

            SWTBotShell shell = bot.shell(ui("dialog.enterFileName.title"));
            shell.activate();
            bot.waitUntil(Conditions.shellIsActive(shell.getText()));

            SWTBotText text = shell.bot().text();
            text.setText(filename);

            String val = text.getText();
            assertTrue(val.equals(filename),
                       constructWrongValueMessage("verifyMenuNewHDF5()", "wrong file name", filename, val));

            shell.bot().button(ui("button.ok")).click();
            bot.waitUntil(shellCloses(shell));

            SWTBotTree filetree = bot.tree();
            assertTrue(filetree.visibleRowCount() == 1,
                       constructWrongValueMessage("verifyMenuNewHDF5()", "filetree wrong row count", "1",
                                                  String.valueOf(filetree.visibleRowCount())));
            assertTrue(filetree.getAllItems()[0].getText().compareTo(filename) == 0,
                       "verifyMenuNewHDF5() filetree is missing file '" + filename + "'");
            assertTrue(hdf_file.exists(), "verifyMenuNewHDF5() file '" + hdf_file + "' not created");
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
        finally {
            try {
                closeFile(hdf_file, true);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Test
    public void verifyMenuClose()
    {
        String filename = "closefile.hdf";
        File hdfFile    = createFile(filename);

        try {
            SWTBotTree filetree = bot.tree();

            checkFileTree(filetree, "verifyMenuClose()", 1, filename);
            SWTBotTreeItem[] items = filetree.getAllItems();

            items[0].click();

            SWTBotMenu fileMenuItem = bot.menu().menu(ui("menu.file"));
            fileMenuItem.menu(ui("action.close")).click();

            resetOpenFileCount();

            assertTrue(hdfFile.delete(), "verifyMenuClose() file '" + hdfFile + "' not deleted");
            assertFalse(hdfFile.exists(), "verifyMenuClose() file '" + hdfFile + "' not gone");
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
        finally {
            try {
                if (hdfFile.exists())
                    closeFile(hdfFile, true);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Test
    public void verifyMenuCloseAll()
    {
        String filename  = "closeallfiles.hdf";
        String filename2 = "closeallfiles.h5";
        File hdf4File    = createFile(filename);
        try {
            SWTBotTree filetree    = bot.tree();
            SWTBotTreeItem[] items = filetree.getAllItems();
            assertTrue(filetree.visibleRowCount() == 1,
                       constructWrongValueMessage("verifyMenuCloseAll()", "filetree wrong row count", "1",
                                                  String.valueOf(filetree.visibleRowCount())));
            assertTrue(items[0].getText().compareTo(filename) == 0,
                       "verifyMenuCloseAll() HDF filetree is missing file '" + filename + "'");
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }

        File hdf5File = createFile(filename2);
        try {
            SWTBotTree filetree    = bot.tree();
            SWTBotTreeItem[] items = filetree.getAllItems();
            assertTrue(filetree.visibleRowCount() == 2,
                       constructWrongValueMessage("verifyMenuCloseAll()", "filetree wrong row count", "2",
                                                  String.valueOf(filetree.visibleRowCount())));
            assertTrue(items[0].getText().compareTo(filename) == 0,
                       "verifyMenuCloseAll() HDF filetree is missing file '" + filename + "'");
            assertTrue(items[1].getText().compareTo(filename2) == 0,
                       "verifyMenuCloseAll() HDF5 filetree is missing file '" + filename2 + "'");
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
            try {
                if (hdf4File.exists())
                    closeFile(hdf4File, true);
            }
            catch (Exception ex2) {
                ex2.printStackTrace();
            }
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
            try {
                if (hdf4File.exists())
                    closeFile(hdf4File, true);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        try {
            SWTBotMenu fileMenuItem = bot.menu().menu(ui("menu.file"));
            fileMenuItem.menu(ui("menu.file.closeAll")).click();

            resetOpenFileCount();

            assertTrue(hdf4File.delete(), "verifyMenuCloseAll() HDF file '" + hdf4File + "' not deleted");
            assertFalse(hdf4File.exists(), "verifyMenuCloseAll() HDF file '" + hdf4File + "' not gone");

            assertTrue(hdf5File.delete(), "verifyMenuCloseAll() HDF5 file '" + hdf5File + "' not deleted");
            assertFalse(hdf5File.exists(), "verifyMenuCloseAll() HDF5 file '" + hdf5File + "' not gone");
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
        finally {
            try {
                if (hdf4File.exists())
                    closeFile(hdf4File, true);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }

            try {
                if (hdf5File.exists())
                    closeFile(hdf5File, true);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Test
    public void verifyMenuSave()
    {
        String filename  = "testsavefile.h5";
        String groupname = "grouptestname";
        File hdf_file    = createFile(filename);

        try {
            SWTBotTree filetree    = bot.tree();
            SWTBotTreeItem[] items = filetree.getAllItems();

            items[0].click();

            SWTBotMenu groupMenuItem = items[0].contextMenu().contextMenu(ui("tree.new"));
            groupMenuItem.menu(ui("tree.new.group")).click();

            SWTBotShell botshell = bot.shell(ui("dialog.newGroup.title"));
            botshell.activate();
            bot.waitUntil(Conditions.shellIsActive(botshell.getText()));

            botshell.bot().text(0).setText(groupname);
            botshell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(botshell));

            Display.getDefault().syncExec(new Runnable() {
                @Override
                public void run()
                {
                    shell.forceActive();
                }
            });

            SWTBotMenu fileMenuItem = bot.menu(ui("menu.file"));
            fileMenuItem.menu(ui("action.save")).click();

            closeFile(hdf_file, false);

            openFile(filename, FILE_MODE.READ_ONLY);

            SWTBotTreeItem group = bot.tree().getAllItems()[0].getNode(0);
            assertTrue(group.getText().compareTo(groupname) == 0,
                       "verifyMenuSave() filetree is missing group '" + groupname + "'");
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
        finally {
            try {
                closeFile(hdf_file, true);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Test
    public void verifyMenuSaveAs()
    {
        String filename         = "testsaveasfile.h5";
        String save_to_filename = "testsaveasfile2.h5";
        String groupname        = "grouptestname";

        File hdfFile     = createFile(filename);
        File hdfSaveFile = new File(workDir, save_to_filename);
        if (hdfSaveFile.exists())
            hdfSaveFile.delete();

        try {
            SWTBotTree filetree    = bot.tree();
            SWTBotTreeItem[] items = filetree.getAllItems();

            items[0].click();

            SWTBotMenu groupMenuItem = items[0].contextMenu().contextMenu(ui("tree.new"));
            groupMenuItem.menu(ui("tree.new.group")).click();

            SWTBotShell botshell = bot.shell(ui("dialog.newGroup.title"));
            botshell.activate();
            bot.waitUntil(Conditions.shellIsActive(botshell.getText()));

            botshell.bot().text(0).setText(groupname);
            botshell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(botshell));

            Display.getDefault().syncExec(new Runnable() {
                @Override
                public void run()
                {
                    shell.forceActive();
                }
            });

            SWTBotMenu fileMenuItem = bot.menu().menu(ui("menu.file"));
            fileMenuItem.menu(ui("menu.file.saveAs")).click();

            SWTBotShell shell = bot.shell(ui("dialog.enterFileName.title"));
            shell.activate();
            bot.waitUntil(Conditions.shellIsActive(shell.getText()));

            SWTBotText text = shell.bot().text();
            text.setText(save_to_filename);

            String val = text.getText();
            assertTrue(
                val.equals(save_to_filename),
                constructWrongValueMessage("verifyMenuSaveAs()", "wrong file name", save_to_filename, val));

            shell.bot().button(ui("button.ok")).click();
            bot.waitUntil(shellCloses(shell));

            refreshOpenFileCount();

            closeFile(hdfSaveFile, false);

            openFile(save_to_filename, FILE_MODE.READ_ONLY);

            SWTBotTreeItem group = bot.tree().getAllItems()[0].getNode(0);
            assertTrue(group.getText().compareTo(groupname) == 0,
                       "verifyMenuSaveAs() filetree is missing group '" + groupname + "'");
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
        finally {
            try {
                closeFile(hdfFile, true);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }

            try {
                if (hdfSaveFile.exists())
                    closeFile(hdfSaveFile, true);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Test
    public void verifyMenuWindowCloseAll()
    {
        String filename = "hdf5_test.h5";
        File hdfFile    = null;

        try {
            hdfFile = openFile(filename, FILE_MODE.READ_ONLY);

            SWTBotTree filetree    = bot.tree();
            SWTBotTreeItem[] items = filetree.getAllItems();

            assertTrue(filetree.visibleRowCount() == 6,
                       constructWrongValueMessage("verifyMenuWindowCloseAll()", "filetree wrong row count",
                                                  "6", String.valueOf(filetree.visibleRowCount())));
            assertTrue(bot.shells().length == 1, "verifyMenuWindowCloseAll() too many shells open");

            filetree.expandNode(filename, true);

            /*
             * Ordinary Datasets now reuse the main window's Data Content tab.
             * Keep each open operation in this test so it also proves that
             * Close All does not mistake embedded views for child shells.
             */
            openInlineDataset(items[0].getNode(2).getNode(1));
            openInlineDataset(items[0].getNode(2).getNode(2));
            openInlineDataset(items[0].getNode(2).getNode(4));
            openInlineDataset(items[0].getNode(2).getNode(5));
            openInlineDataset(items[0].getNode(2).getNode(6));

            /* Image views remain specialized child windows. */
            openSpecializedView(items[0].getNode(4).getNode(1));
            openSpecializedView(items[0].getNode(4).getNode(5));

            // Make sure all shells have time to open and the bot has time to
            // stabilize before checking how many shells are open
            bot.sleep(1000);

            assertTrue(bot.shells().length == 3,
                       constructWrongValueMessage("verifyMenuWindowCloseAll()", "too many or missing shells",
                                                  "3", String.valueOf(bot.shells().length)));

            SWTBotShell mainShell = new SWTBotShell(shell);
            mainShell.activate();
            bot.waitUntil(Conditions.shellIsActive(mainShell.getText()));

            bot.menu().menu(ui("menu.window")).menu(ui("menu.file.closeAll")).click();

            assertTrue(bot.shells().length == 1,
                       constructWrongValueMessage("verifyMenuWindowCloseAll()", "too many or missing shells",
                                                  "1", String.valueOf(bot.shells().length)));
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
        finally {
            try {
                closeFile(hdfFile, false);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void openInlineDataset(SWTBotTreeItem dataset)
    {
        dataset.click();
        dataset.contextMenu().contextMenu(ui("tree.open")).click();

        final String dataContentTab = ui("tab.dataContent");
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test()
            {
                try {
                    return bot.tabItem(dataContentTab).isActive();
                }
                catch (WidgetNotFoundException ex) {
                    return false;
                }
            }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for inline Data Content tab '" + dataContentTab + "'";
            }
        });

        assertTrue(bot.shells().length == 1,
                   "ordinary Dataset Open must not create a child TableView Shell");
    }

    private void openSpecializedView(SWTBotTreeItem dataObject)
    {
        String objectName = dataObject.getText();
        dataObject.click();
        dataObject.contextMenu().contextMenu(ui("tree.open")).click();
        openDataObject(objectName);
    }

    @Test
    public void verifyTextInLabelWhenClickingHDF4Help()
    {
        try {
            // Test that the Help->HDF4 Library Version MenuItem works correctly
            SWTBotMenu fileMenuItem = bot.menu().menu(ui("menu.help"));
            fileMenuItem.menu(ui("menu.help.hdf4Library")).click();

            SWTBotShell botshell = bot.shell(ui("dialog.libraryVersion.title"));
            botshell.activate();
            bot.waitUntil(Conditions.shellIsActive(ui("dialog.libraryVersion.title")));

            String val = botshell.bot().label(1).getText();
            assertTrue(val.equals(HDF4VERSION),
                       constructWrongValueMessage("verifyTextInLabelWhenClickingHDF4Help()",
                                                  "wrong label text", HDF4VERSION, val));
            botshell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(botshell));
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
    }

    @Test
    public void verifyTextInLabelWhenClickingHDF5Help()
    {
        try {
            // Test that the Help->HDF5 Library Version MenuItem works correctly
            SWTBotMenu fileMenuItem = bot.menu().menu(ui("menu.help"));
            fileMenuItem.menu(ui("menu.help.hdf5Library")).click();

            SWTBotShell botshell = bot.shell(ui("dialog.libraryVersion.title"));
            botshell.activate();
            bot.waitUntil(Conditions.shellIsActive(ui("dialog.libraryVersion.title")));

            String val = botshell.bot().label(1).getText();
            assertTrue(val.equals(HDF5VERSION),
                       constructWrongValueMessage("verifyTextInLabelWhenClickingHDF5Help()",
                                                  "wrong label text", HDF5VERSION, val));

            botshell.bot().label(HDF5VERSION);
            botshell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(botshell));
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
    }

    @Test
    public void verifyTextInLabelWhenClickingJavaHelp()
    {
        try {
            SWTBotMenu fileMenuItem = bot.menu().menu(ui("menu.help"));
            fileMenuItem.menu(ui("menu.help.javaVersion")).click();

            SWTBotShell botshell = bot.shell(ui("dialog.javaVersion.title"));
            botshell.activate();
            bot.waitUntil(Conditions.shellIsActive(ui("dialog.javaVersion.title")));

            //("Compiled at jdk 1.7.*\\sRunning at.*");
            botshell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(botshell));
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
    }

    @Test
    public void verifyTextInLabelWhenClickingAboutHelp()
    {
        try {
            SWTBotMenu fileMenuItem = bot.menu().menu(ui("menu.help"));
            fileMenuItem.menu(ui("menu.help.about")).click();

            SWTBotShell botshell = bot.shell(ui("dialog.about.title"));
            botshell.activate();
            bot.waitUntil(Conditions.shellIsActive(ui("dialog.about.title")));

            //("HDF Viewer, Version " + VERSION + "\\sFor.*\\s\\sCopyright.*2006 The HDF Group.\\sAll rights
            // reserved.");
            botshell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(botshell));
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
    }

    @Test
    public void verifyTextInLabelWhenClickingSupportedFileFormatsHelp()
    {
        try {
            SWTBotMenu fileMenuItem = bot.menu().menu(ui("menu.help"));
            fileMenuItem.menu(ui("menu.help.supportedFileFormats")).click();

            SWTBotShell botshell = bot.shell(ui("dialog.supportedFileFormats.title"));
            botshell.activate();
            bot.waitUntil(Conditions.shellIsActive(ui("dialog.supportedFileFormats.title")));
            //("\\sSupported File Formats: \\s.*Fits\\s.*HDF5\\s.*NetCDF\\s.*HDF4\\s\\s");
            botshell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(botshell));
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
    }

    @Test
    public void verifyRegisterFileFormatTools()
    {
        try {
            SWTBotMenu toolsMenuItem = bot.menu().menu(ui("menu.tools"));
            SWTBotMenu helpMenuItem  = bot.menu().menu(ui("menu.help"));

            toolsMenuItem.menu(ui("menu.tools.unregister")).click();

            SWTBotShell botshell = bot.shell(ui("dialog.unregister.title"));
            botshell.activate();
            bot.waitUntil(Conditions.shellIsActive(ui("dialog.unregister.title")));

            botshell.bot().comboBox().setSelection("FITS");
            botshell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(botshell));

            helpMenuItem.menu(ui("menu.help.supportedFileFormats")).click();

            botshell = bot.shell(ui("dialog.supportedFileFormats.title"));
            botshell.activate();
            bot.waitUntil(Conditions.shellIsActive(ui("dialog.supportedFileFormats.title")));
            //("\\sSupported File Formats: \\s.*HDF5\\s.*NetCDF\\s.*HDF4\\s\\s"));
            botshell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(botshell));

            toolsMenuItem.menu(ui("menu.tools.register")).click();

            botshell = bot.shell(ui("dialog.register.title"));
            botshell.activate();
            bot.waitUntil(Conditions.shellIsActive(ui("dialog.register.title")));

            botshell.bot().text().setText("FITS:hdf.object.fits.FitsFile:fits");
            botshell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(botshell));

            helpMenuItem.menu(ui("menu.help.supportedFileFormats")).click();

            botshell = bot.shell(ui("dialog.supportedFileFormats.title"));
            botshell.activate();
            bot.waitUntil(Conditions.shellIsActive(ui("dialog.supportedFileFormats.title")));
            //("\\sSupported File Formats: \\s.*Fits\\s.*HDF5\\s.*NetCDF\\s.*HDF4\\s\\s");
            botshell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(botshell));
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
    }

    @Test
    public void verifyUnregisterFileFormatTools()
    {
        try {
            SWTBotMenu toolsMenuItem = bot.menu().menu(ui("menu.tools"));
            SWTBotMenu helpMenuItem  = bot.menu().menu(ui("menu.help"));

            toolsMenuItem.menu(ui("menu.tools.unregister")).click();

            SWTBotShell botshell = bot.shell(ui("dialog.unregister.title"));
            botshell.activate();
            bot.waitUntil(Conditions.shellIsActive(ui("dialog.unregister.title")));

            botshell.bot().comboBox().setSelection("FITS");
            botshell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(botshell));

            helpMenuItem.menu(ui("menu.help.supportedFileFormats")).click();

            botshell = bot.shell(ui("dialog.supportedFileFormats.title"));
            botshell.activate();
            bot.waitUntil(Conditions.shellIsActive(ui("dialog.supportedFileFormats.title")));
            //("\\sSupported File Formats: \\s.*HDF5\\s.*NetCDF\\s.*HDF4\\s\\s");
            botshell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(botshell));
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
    }

    @Test
    public void verifyUserOptionsDialog()
    {
        try {
            SWTBotMenu fileMenuItem = bot.menu().menu(ui("menu.tools"));
            fileMenuItem.menu(ui("menu.tools.preferences")).click();

            SWTBotShell botshell = bot.shell(ui("dialog.userOptions.title"));
            botshell.activate();
            bot.waitUntil(Conditions.shellIsActive(ui("dialog.userOptions.title")));

            SWTBotRadio rwButton = botshell.bot().radio(ui("options.readWrite"));
            assertTrue(rwButton.isEnabled());

            // botshell.bot().button(ui("button.restoreDefaults")).click();

            botshell.bot().button(ui("button.cancel")).click();
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
    }
}
