package uitest;

import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.widgetOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Array;

import hdf.object.FileFormat;
import hdf.object.HObject;
import hdf.object.h5.H5File;
import hdf.object.h5.H5ScalarDS;
import hdf.view.i18n.I18n;

import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swtbot.nebula.nattable.finder.widgets.SWTBotNatTable;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
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
            assertEquals("DU64BITS", bot.textWithLabel("Name: ").getText(),
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
            assertEquals("g2", bot.textWithLabel("Name: ").getText(),
                         "Group selection must refresh metadata for the Group");
            assertThrows(WidgetNotFoundException.class, () -> bot.tabItem(I18n.text("tab.dataContent")),
                         "a Group must not receive an empty editable Data Content tab");

            SWTBotTreeItem dataset = group.getNode("array");
            dataset.click();
            assertTrue(waitForTab("tab.dataContent").isActive(),
                       "selecting a Dataset after a Group must create Data Content");

            generalTab = bot.tabItem(I18n.text("tab.generalObjectInfo"));
            generalTab.activate();
            assertEquals("array", bot.textWithLabel("Name: ").getText(),
                         "Dataset metadata must not retain the previous Group");

            group.click();
            generalTab = waitForTab("tab.generalObjectInfo");
            generalTab.activate();
            assertEquals("g2", bot.textWithLabel("Name: ").getText(),
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
        }

        assertNotNull(originalValue, "the Dataset cell was not read before editing");
        assertNotNull(newValue, "the Dataset cell was not assigned before saving");
        String valueReadFromFile = readFirstDatasetValue(hdfFile, EDIT_DATASET);
        assertNotEquals(originalValue, valueReadFromFile,
                        "saving the inline TableView must change the on-disk Dataset value");
        assertEquals(newValue, valueReadFromFile,
                     "the value reread from disk must equal the value saved through HDFView");
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
