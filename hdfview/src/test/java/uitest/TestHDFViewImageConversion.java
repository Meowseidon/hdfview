package uitest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;

import hdf.view.i18n.I18n;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTabItem;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;

@Tag("ui")
@Tag("integration")
@Tag("visual") // Requires real display - pixel-level image verification
public class TestHDFViewImageConversion extends AbstractWindowTest {
    private static String JPGFILE   = "apollo17_earth.jpg";
    private static String HDF4IMAGE = JPGFILE + ".hdf";
    private static String HDF5IMAGE = JPGFILE + ".h5";

    @Test
    @Disabled("HDF4 image conversion has known bug in native library - to be fixed later")
    public void convertImageToHDF4()
    {
        File hdf_file = new File(workDir, HDF4IMAGE);

        try {
            bot.menu().menu(ui("menu.tools")).menu(ui("menu.tools.convertImage")).menu(ui("menu.tools.convertImage.hdf4")).click();

            SWTBotShell convertshell = bot.shell(ui("dialog.convertImage.hdf4.title"));
            convertshell.activate();
            bot.waitUntil(Conditions.shellIsActive(convertshell.getText()));

            convertshell.bot().text(0).setText(workDir + File.separator + JPGFILE);

            String val = convertshell.bot().text(0).getText();
            assertTrue(val.equals(workDir + File.separator + JPGFILE),
                       constructWrongValueMessage("convertImageToHDF4()", "wrong source file",
                                                  workDir + File.separator + JPGFILE, val));

            convertshell.bot().text(1).setText(workDir + File.separator + HDF4IMAGE);

            val = convertshell.bot().text(1).getText();
            assertTrue(val.equals(workDir + File.separator + HDF4IMAGE),
                       constructWrongValueMessage("convertImageToHDF4()", "wrong dest file",
                                                  workDir + File.separator + HDF4IMAGE, val));

            convertshell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(convertshell));

            SWTBotTree filetree    = bot.tree();
            SWTBotTreeItem[] items = filetree.getAllItems();

            assertTrue(items[0].getText().compareTo(HDF4IMAGE) == 0,
                       "convertImageToHDF4() filetree is missing file '" + HDF4IMAGE + "'");
            assertTrue(items[0].getNode(0).getText().compareTo(JPGFILE) == 0,
                       "convertImageToHDF4() filetree is missing image '" + JPGFILE + "'");

            items[0].getNode(0).click();

            // Test metadata

            SWTBotTabItem tabItem = bot.tabItem(I18n.text("tab.generalObjectInfo"));
            tabItem.activate();

            val = bot.textWithLabel(ui("meta.objectName")).getText();
            assertTrue(val.equals(JPGFILE),
                       constructWrongValueMessage("convertImageToHDF4()", "wrong image name", JPGFILE,
                                                  val)); // Test dataset name

            val = bot.textInGroup(ui("meta.datasetDataspaceDatatype"), 0).getText();
            assertTrue(val.equals("2"), constructWrongValueMessage("convertImageToHDF4()", "wrong image rank",
                                                                   "2", val)); // Test rank

            val = bot.textInGroup(ui("meta.datasetDataspaceDatatype"), 1).getText();
            assertTrue(val.equals("533 x 533"),
                       constructWrongValueMessage("convertImageToHDF4()", "wrong image dimension sizes",
                                                  "533 x 533", val)); // Test dimension sizes

            // Test sample pixels
            items[0].getNode(0).contextMenu().contextMenu(ui("tree.openAs")).click();

            SWTBotShell openAsShell = bot.shell(ui("dialog.dataOption.title", JPGFILE, "/"));
            openAsShell.bot().radio(ui("common.image")).click();
            openAsShell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(openAsShell));

            SWTBotShell imageShell = openStandaloneDataObject(JPGFILE);

            testSamplePixel(325, 53, "x=325,   y=53,   value=(152, 106, 91)");
            testSamplePixel(430, 357, "x=430,   y=357,   value=(83, 80, 107)");
            testSamplePixel(197, 239, "x=197,   y=239,   value=(206, 177, 159)");

            bot.activeShell().bot().menu().menu(ui("image.menu")).menu(ui("action.close")).click();
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
    public void convertImageToHDF5()
    {
        File hdf_file = new File(workDir, HDF5IMAGE);

        try {
            bot.menu().menu(ui("menu.tools")).menu(ui("menu.tools.convertImage")).menu(ui("menu.tools.convertImage.hdf5")).click();

            SWTBotShell convertshell = bot.shell(ui("dialog.convertImage.hdf5.title"));
            convertshell.activate();
            bot.waitUntil(Conditions.shellIsActive(convertshell.getText()));

            convertshell.bot().text(0).setText(workDir + File.separator + JPGFILE);

            String val = convertshell.bot().text(0).getText();
            assertTrue(val.equals(workDir + File.separator + JPGFILE),
                       constructWrongValueMessage("convertImageToHDF5()", "wrong source file",
                                                  workDir + File.separator + JPGFILE, val));

            convertshell.bot().text(1).setText(workDir + File.separator + HDF5IMAGE);

            val = convertshell.bot().text(1).getText();
            assertTrue(val.equals(workDir + File.separator + HDF5IMAGE),
                       constructWrongValueMessage("convertImageToHDF5()", "wrong dest file",
                                                  workDir + File.separator + HDF5IMAGE, val));

            convertshell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(convertshell));

            SWTBotTree filetree    = bot.tree();
            SWTBotTreeItem[] items = filetree.getAllItems();

            assertTrue(items[0].getText().compareTo(HDF5IMAGE) == 0,
                       "convertImageToHDF5() filetree is missing file '" + HDF5IMAGE + "'");
            assertTrue(items[0].getNode(0).getText().compareTo(JPGFILE) == 0,
                       "convertImageToHDF5() filetree is missing image '" + JPGFILE + "'");

            // Test metadata
            items[0].getNode(0).click();

            SWTBotTabItem tabItem = bot.tabItem(I18n.text("tab.generalObjectInfo"));
            tabItem.activate();

            val = bot.textWithLabel(ui("meta.objectName")).getText();
            assertTrue(val.equals(JPGFILE),
                       constructWrongValueMessage("convertImageToHDF5()", "wrong image name", JPGFILE,
                                                  val)); // Test dataset name

            val = bot.textInGroup(ui("meta.datasetDataspaceDatatype"), 0).getText();
            assertTrue(val.equals("3"), constructWrongValueMessage("convertImageToHDF5()", "wrong image rank",
                                                                   "3", val)); // Test rank

            val = bot.textInGroup(ui("meta.datasetDataspaceDatatype"), 1).getText();
            assertTrue(val.equals("533 x 533 x 3"),
                       constructWrongValueMessage("convertImageToHDF5()", "wrong image dimension sizes",
                                                  "533 x 533 x 3", val)); // Test dimension sizes

            // Test sample pixels
            items[0].getNode(0).contextMenu().contextMenu(ui("tree.openAs")).click();

            SWTBotShell openAsShell = bot.shell(ui("dialog.dataOption.title", JPGFILE, "/"));
            openAsShell.bot().radio(ui("common.image")).click();
            openAsShell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(openAsShell));

            SWTBotShell imageShell = openStandaloneDataObject(JPGFILE);

            testSamplePixel(325, 53, "x=325,   y=53,   value=(152, 106, 91)");
            testSamplePixel(430, 357, "x=430,   y=357,   value=(83, 80, 107)");
            testSamplePixel(197, 239, "x=197,   y=239,   value=(206, 177, 159)");

            bot.activeShell().bot().menu().menu(ui("image.menu")).menu(ui("action.close")).click();
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
}
