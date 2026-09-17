package uitest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swtbot.nebula.nattable.finder.widgets.SWTBotNatTable;
import org.eclipse.swtbot.swt.finder.matchers.WidgetOfType;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;

@Tag("ui")
public class TestTreeViewNewMenu extends AbstractWindowTest {
    @Test
    public void createNewHDF5Dataset()
    {
        String filename        = "testds.h5";
        String groupname       = "testgroupname";
        String datasetname     = "testdatasetname";
        String datasetdimsize  = "4 x 4";
        SWTBotShell tableShell = null;
        File hdf_file          = createFile(filename);

        try {
            SWTBotTree filetree    = bot.tree();
            SWTBotTreeItem[] items = filetree.getAllItems();

            assertTrue(filetree.visibleRowCount() == 1,
                       constructWrongValueMessage("createNewHDF5Dataset()", "filetree wrong row count", "1",
                                                  String.valueOf(filetree.visibleRowCount())));
            assertTrue(items[0].getText().compareTo(filename) == 0,
                       "createNewHDF5Dataset() filetree is missing file '" + filename + "'");

            items[0].click();
            items[0].contextMenu().contextMenu(ui("tree.new")).menu(ui("tree.new.group")).click();

            SWTBotShell groupShell = bot.shell(ui("dialog.newGroup.title"));
            groupShell.activate();
            bot.waitUntil(Conditions.shellIsActive(groupShell.getText()));

            groupShell.bot().text(0).setText(groupname);

            String val = groupShell.bot().text(0).getText();
            assertTrue(val.equals(groupname), constructWrongValueMessage("createNewHDF5Dataset()",
                                                                         "wrong group name", groupname, val));

            groupShell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(groupShell));

            assertTrue(filetree.visibleRowCount() == 2,
                       constructWrongValueMessage("createNewHDF5Dataset()", "filetree wrong row count", "2",
                                                  String.valueOf(filetree.visibleRowCount())));
            assertTrue(items[0].getText().compareTo(filename) == 0,
                       "createNewHDF5Dataset() filetree is missing file '" + filename + "'");
            assertTrue(items[0].getNode(0).getText().compareTo(groupname) == 0,
                       "createNewHDF5Dataset() filetree is missing group '" + groupname + "'");

            items[0].getNode(0).click();

            items[0].getNode(0).contextMenu().contextMenu(ui("tree.new")).menu(ui("tree.new.dataset")).click();

            SWTBotShell datasetShell = bot.shell(ui("dialog.newDataset.title"));
            datasetShell.activate();
            bot.waitUntil(Conditions.shellIsActive(datasetShell.getText()));

            datasetShell.bot().text(0).setText(datasetname);
            datasetShell.bot().text(2).setText(datasetdimsize);

            val = datasetShell.bot().text(0).getText();
            assertTrue(
                val.equals(datasetname),
                constructWrongValueMessage("createNewHDF5Dataset()", "wrong dataset name", datasetname, val));

            val = datasetShell.bot().text(2).getText();
            assertTrue(val.equals(datasetdimsize),
                       constructWrongValueMessage("createNewHDF5Dataset()", "wrong dataset dimension sizes",
                                                  datasetdimsize, val));

            datasetShell.bot().button(ui("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(datasetShell));

            items[0].getNode(0).click();
            items[0].getNode(0).contextMenu().contextMenu(ui("tree.expandAll")).click();

            assertTrue(filetree.visibleRowCount() == 3,
                       constructWrongValueMessage("createNewHDF5Dataset()", "filetree wrong row count", "3",
                                                  String.valueOf(filetree.visibleRowCount())));
            assertTrue(items[0].getText().compareTo(filename) == 0,
                       "createNewHDF5Dataset() filetree is missing file '" + filename + "'");
            assertTrue(items[0].getNode(0).getText().compareTo(groupname) == 0,
                       "createNewHDF5Dataset() filetree is missing group '" + groupname + "'");
            assertTrue(items[0].getNode(0).getNode(0).getText().compareTo(datasetname) == 0,
                       "createNewHDF5Dataset() filetree is missing dataset '" + datasetname + "'");

            items[0].getNode(0).getNode(0).click();
            items[0].getNode(0).getNode(0).contextMenu().contextMenu(ui("tree.open")).click();
            tableShell = openDataObject(datasetname);

            final SWTBotNatTable table =
                new SWTBotNatTable(tableShell.bot().widget(WidgetOfType.widgetOfType(NatTable.class)));

            for (int row = 1; row <= table.preferredRowCount() - 1; row++) {
                for (int col = 1; col <= table.preferredColumnCount() - 1; col++) {
                    final String thisVal =
                        String.valueOf(((row - 1) * (table.preferredColumnCount() - 1)) + (col));

                    // Note: setCellDataValueByPosition throws a null pointer exception in SWTBot currently,
                    // resort to manual workaround below
                    // table.setCellDataValueByPosition(row, col, val);

                    table.doubleclick(row, col);

                    Display.getDefault().syncExec(new Runnable() {
                        @Override
                        public void run()
                        {
                            table.widget.getActiveCellEditor().setEditorValue(thisVal);
                            table.widget.getActiveCellEditor().commit(SelectionLayer.MoveDirectionEnum.RIGHT,
                                                                      true, true);
                        }
                    });
                }
            }

            tableMenu(tableShell).menu(ui("table.saveChanges")).click();

            closeDataObject(tableShell);

            items[0].getNode(0).getNode(0).click();
            items[0].getNode(0).getNode(0).contextMenu().contextMenu(ui("tree.open")).click();
            tableShell = openDataObject(datasetname);

            SWTBotNatTable table2 =
                new SWTBotNatTable(tableShell.bot().widget(WidgetOfType.widgetOfType(NatTable.class)));

            for (int row = 1; row <= table2.preferredRowCount() - 1; row++) {
                for (int col = 1; col < table2.preferredColumnCount(); col++) {
                    String expected =
                        String.valueOf(((row - 1) * (table2.preferredColumnCount() - 1)) + (col));
                    val = table2.getCellDataValueByPosition(row, col);
                    assertTrue(val.equals(expected), constructWrongValueMessage("createNewHDF5Dataset()",
                                                                                "wrong data", expected, val));
                }
            }

            closeDataObject(tableShell);
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
            if (tableShell != null && tableShell.isOpen()) {
                closeDataObject(tableShell);
            }

            try {
                closeFile(hdf_file, true);
            }
            catch (Exception ex) {
            }
        }
    }
}
