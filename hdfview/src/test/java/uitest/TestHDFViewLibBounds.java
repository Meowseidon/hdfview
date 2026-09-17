package uitest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import static org.eclipse.swtbot.swt.finder.waits.Conditions.shellCloses;

import java.io.File;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTabItem;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;

@Tag("ui")
@Tag("integration")
public class TestHDFViewLibBounds extends AbstractWindowTest {
    @Test
    public void testLibVersion()
    {
        String testFilename = "test_libversion.h5";
        File hdfFile        = createFile(testFilename);

        try {
            closeFile(hdfFile, false);
            hdfFile = openFile(testFilename, FILE_MODE.READ_WRITE);

            SWTBotTree filetree = bot.tree();
            checkFileTree(filetree, "testLibVersion()", 1, testFilename);

            SWTBotTabItem tabItem = openMetadataTab(filetree, testFilename, "/", "tab.generalObjectInfo");
            tabItem.activate();

            String val = bot.textWithLabel(ui("label.libraryVersionBounds")).getText();
            assertTrue(
                val.equals("V18 and V200"),
                constructWrongValueMessage("testLibVersion()", "wrong lib bounds", "V18 and V200", val));

            SWTBotTreeItem[] items = filetree.getAllItems();
            items[0].click();
            items[0].contextMenu().contextMenu(ui("tree.setLibVersionBounds")).click();

            SWTBotShell libVersionShell = bot.shell(ui("dialog.libVersion.title"));
            libVersionShell.activate();

            libVersionShell.bot().comboBox(0).setSelection("V110");

            libVersionShell.bot().button(ui("button.ok")).click();
            bot.waitUntil(shellCloses(libVersionShell));

            val = bot.textWithLabel(ui("label.libraryVersionBounds")).getText();
            assertTrue(
                val.equals("V110 and V200"),
                constructWrongValueMessage("testLibVersion()", "wrong lib bounds", "V110 and V200", val));

            items[0].contextMenu().contextMenu(ui("tree.setLibVersionBounds")).click();

            libVersionShell = bot.shell(ui("dialog.libVersion.title"));
            libVersionShell.activate();

            libVersionShell.bot().comboBox(1).setSelection("V110");

            libVersionShell.bot().button(ui("button.ok")).click();
            bot.waitUntil(shellCloses(libVersionShell));

            val = bot.textWithLabel(ui("label.libraryVersionBounds")).getText();
            assertTrue(
                val.equals("V110 and V110"),
                constructWrongValueMessage("testLibVersion()", "wrong lib bounds", "V110 and V110", val));

            items[0].contextMenu().contextMenu(ui("tree.setLibVersionBounds")).click();

            libVersionShell = bot.shell(ui("dialog.libVersion.title"));
            libVersionShell.activate();

            libVersionShell.bot().comboBox(0).setSelection("Latest");

            libVersionShell.bot().button(ui("button.ok")).click();
            SWTBotShell libVersionErrorShell = bot.shells()[2];
            libVersionErrorShell.activate();
            libVersionErrorShell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(libVersionErrorShell));

            libVersionShell.bot().button(ui("button.cancel")).click();
            bot.waitUntil(shellCloses(libVersionShell));
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
}
