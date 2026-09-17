package uitest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.widgetOfType;

import java.io.File;

import hdf.view.ViewProperties;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swtbot.nebula.nattable.finder.widgets.SWTBotNatTable;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTabItem;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;

import uitest.AbstractWindowTest.DataRetrieverFactory.TableDataRetriever;

@Tag("ui")
@Tag("integration")
public class TestHDFViewRefs extends AbstractWindowTest {
    @Test
    public void openTAttributeRegionReference()
    {
        String[][] expectedAttrData = {
            {"Attribute1", "Dataset region reference", "4",
             "/Dataset2 REGION_TYPE BLOCK { (2,2)-(7,7) }, /Dataset2 REGION_TYPE POINT { (6,9) (2,2) (8,4) (1,6) (2,8) (3,2) (0,4) (9,0) (7,1) (3,3) }, NULL, NULL"}};
        String[][] expectedTrueData = {
            {"66,69,72,75,78,81,96,99,102,105,108,111,126,129,132,135,138,141,156,159,162,165,168,171,186,189,192,195,198,201,216,219,222,225,228,231"},
            {"207,66,252,48,84,96,12,14,213,99"},
            {"NULL"},
            {"NULL"}};
        String[][] expectedData = {
            {"/Dataset2 REGION_TYPE BLOCK { (2,2)-(7,7) }"},
            {"/Dataset2 REGION_TYPE POINT { (6,9) (2,2) (8,4) (1,6) (2,8) (3,2) (0,4) (9,0) (7,1) (3,3) }"},
            {"NULL"},
            {"NULL"}};
        SWTBotShell tableShell = null;
        String filename        = "tattrreg.h5";
        String datasetName     = "/Dataset1";
        File hdfFile           = openFile(filename, FILE_MODE.READ_ONLY);

        try {
            SWTBotTree filetree = bot.tree();

            checkFileTree(filetree, "openTAttributeRegionReference()", 3, filename);

            // Open dataset 'Dataset1' Attribute Table
            SWTBotTable attrTable = openAttributeTable(filetree, filename, datasetName);

            TableDataRetriever retriever = DataRetrieverFactory.getTableDataRetriever(
                attrTable, "openTAttributeRegionReference()", true);

            retriever.testAllTableLocations(expectedAttrData);

            tableShell = openAttributeObject(attrTable, "Attribute1", 0);
            final SWTBotNatTable dataTable =
                new SWTBotNatTable(tableShell.bot().widget(widgetOfType(NatTable.class)));

            retriever = DataRetrieverFactory.getTableDataRetriever(dataTable,
                                                                   "openTAttributeRegionReference()", true);

            boolean displayValues = ViewProperties.showRegRefValues();
            if (displayValues)
                retriever.testAllTableLocations(expectedTrueData);
            else
                retriever.testAllTableLocations(expectedData);
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
                closeFile(hdfFile, false);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Test
    public void openTDataRegionReference()
    {
        String[][] expectedTrueData = {
            {"66,69,72,75,78,81,96,99,102,105,108,111,126,129,132,135,138,141,156,159,162,165,168,171,186,189,192,195,198,201,216,219,222,225,228,231"},
            {"207,66,252,48,84,96,12,14,213,99"},
            {"NULL"},
            {"NULL"}};
        String[][] expectedTableData = {
            {"66", "69", "72", "75", "78", "81"},       {"96", "99", "102", "105", "108", "111"},
            {"126", "129", "132", "135", "138", "141"}, {"156", "159", "162", "165", "168", "171"},
            {"186", "189", "192", "195", "198", "201"}, {"216", "219", "222", "225", "228", "231"}};
        //        String[][] expectedTableData = { {
        //        "66,69,72,75,78,81,96,99,102,105,108,111,126,129,132,135,138,141,156,159,162,165,168,171,186,189,192,195,198,201,216,219,222,225,228,231"
        //        } };
        String[][] expectedData = {
            {"/Dataset2 REGION_TYPE BLOCK { (2,2)-(7,7) }"},
            {"/Dataset2 REGION_TYPE POINT { (6,9) (2,2) (8,4) (1,6) (2,8) (3,2) (0,4) (9,0) (7,1) (3,3) }"},
            {"NULL"},
            {"NULL"}};
        SWTBotShell tableShell     = null;
        SWTBotShell tableShellData = null;
        String filename            = "tdatareg.h5";
        String datasetName         = "Dataset1";
        File hdfFile               = openFile(filename, FILE_MODE.READ_ONLY);

        try {
            SWTBotTree filetree = bot.tree();

            checkFileTree(filetree, "openTDataRegionReference()", 3, filename);

            // Test metadata
            SWTBotTabItem tabItem = openMetadataTab(filetree, filename, datasetName, "tab.generalObjectInfo");
            tabItem.activate();

            String val = bot.textWithLabel(ui("meta.objectName")).getText();
            assertTrue(val.equals(datasetName),
                       constructWrongValueMessage("openTDataRegionReference()", "wrong name", datasetName,
                                                  val)); // Test dataset name

            val = bot.textInGroup(ui("meta.datasetDataspaceDatatype"), 0).getText();
            assertTrue(val.equals("1"), constructWrongValueMessage("openTDataRegionReference()", "wrong rank",
                                                                   "1", val)); // Test rank

            val = bot.textInGroup(ui("meta.datasetDataspaceDatatype"), 3).getText();
            assertTrue(val.equals("Dataset region reference"),
                       constructWrongValueMessage("openTDataRegionReference()", "wrong data type",
                                                  "Dataset region reference", val)); // Test data type

            // Open dataset
            tableShell                     = openTreeviewObject(filetree, filename, datasetName);
            final SWTBotNatTable dataTable = getNatTable(tableShell);

            TableDataRetriever retriever =
                DataRetrieverFactory.getTableDataRetriever(dataTable, "openTDataRegionReference()", true);

            boolean displayValues = ViewProperties.showRegRefValues();
            if (displayValues)
                retriever.testAllTableLocations(expectedTrueData);
            else
                retriever.testAllTableLocations(expectedData);
            // dataTable.doubleclick(1, 1);
            // tableShellData = openDataObject("Dataset2");

            // final SWTBotNatTable table2 = getNatTable(tableShellData);
            // TableDataRetriever retriever2 =
            //     DataRetrieverFactory.getTableDataRetriever(table2, "openTDataRegionReference()", true);

            // retriever2.testAllTableLocations(expectedTableData);
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
            if (tableShellData != null && tableShellData.isOpen()) {
                closeDataObject(tableShellData);
            }
            if (tableShell != null && tableShell.isOpen()) {
                tableShell.activate();
                bot.waitUntil(Conditions.shellIsActive(tableShell.getText()));
                closeDataObject(tableShell);
            }

            try {
                closeFile(hdfFile, false);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Test
    public void openTAttributeReference()
    {
        String[][] expectedData     = {{"trefer_attr.h5/Group1/Dataset1/Attr1 H5R_ATTR"},
                                       {"trefer_attr.h5/Group1/Dataset2/Attr1 H5R_ATTR"},
                                       {"trefer_attr.h5/Group1/Attr2 H5R_ATTR"},
                                       {"trefer_attr.h5/Group1/Datatype1/Attr3 H5R_ATTR"}};
        String[][] expectedAttrData = {{"0"}, {"3"}, {"6"}, {"9"}};
        SWTBotShell tableShell      = null;
        SWTBotShell tableAttrShell  = null;
        String filename             = "trefer_attr.h5";
        String datasetName          = "Dataset3";
        String attributeName        = "Attr1";
        File hdfFile                = openFile(filename, FILE_MODE.READ_ONLY);

        try {
            SWTBotTree filetree = bot.tree();

            checkFileTree(filetree, "openTAttributeReference()", 3, filename);

            // Test metadata
            SWTBotTabItem tabItem = openMetadataTab(filetree, filename, datasetName, "tab.generalObjectInfo");
            tabItem.activate();

            String val = bot.textWithLabel(ui("meta.objectName")).getText();
            assertTrue(val.equals(datasetName),
                       constructWrongValueMessage("openTAttributeReference()", "wrong name", datasetName,
                                                  val)); // Test dataset name

            val = bot.textInGroup(ui("meta.datasetDataspaceDatatype"), 0).getText();
            assertTrue(val.equals("1"), constructWrongValueMessage("openTAttributeReference()", "wrong rank",
                                                                   "1", val)); // Test rank

            val = bot.textInGroup(ui("meta.datasetDataspaceDatatype"), 3).getText();
            assertTrue(val.equals("Reference"),
                       constructWrongValueMessage("openTAttributeReference()", "wrong data type", "Reference",
                                                  val)); // Test data type

            // Open dataset 'Dataset3' Table
            tableShell                     = openTreeviewObject(filetree, filename, datasetName);
            final SWTBotNatTable dataTable = getNatTable(tableShell);

            TableDataRetriever retriever =
                DataRetrieverFactory.getTableDataRetriever(dataTable, "openTAttributeReference()", true);

            retriever.testAllTableLocations(expectedData);

            // Open attribute 'Attr1' Table
            dataTable.doubleclick(1, 1);
            if (tableShell != null && tableShell.isOpen()) {
                closeDataObject(tableShell);
            }

            try {
                tableAttrShell = openStandaloneDataObject(attributeName);

                SWTBotNatTable attributeTable = getNatTable(tableAttrShell);

                retriever = DataRetrieverFactory.getTableDataRetriever(attributeTable,
                                                                       "openTAttributeReference()", true);

                retriever.testAllTableLocations(expectedAttrData);
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
                if (tableAttrShell != null && tableAttrShell.isOpen()) {
                    closeDataObject(tableAttrShell);
                }
            }
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
                closeFile(hdfFile, false);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Test
    public void openTObjectReference()
    {
        String[][] expectedData    = {{"trefer_obj.h5/Group1/Dataset1 H5O_TYPE_OBJ_REF"},
                                      {"trefer_obj.h5/Group1/Dataset2 H5O_TYPE_OBJ_REF"},
                                      {"trefer_obj.h5/Group1 H5O_TYPE_OBJ_REF"},
                                      {"trefer_obj.h5/Group1/Datatype1 H5O_TYPE_OBJ_REF"}};
        String[][] expectedObjData = {{"0"}, {"3"}, {"6"}, {"9"}};
        SWTBotShell tableShell     = null;
        SWTBotShell tableObjShell  = null;
        String filename            = "trefer_obj.h5";
        String datasetName         = "Dataset3";
        String objectName          = "Dataset1";
        File hdfFile               = openFile(filename, FILE_MODE.READ_ONLY);

        try {
            SWTBotTree filetree = bot.tree();

            checkFileTree(filetree, "openTObjectReference()", 3, filename);

            // Test metadata
            SWTBotTabItem tabItem = openMetadataTab(filetree, filename, datasetName, "tab.generalObjectInfo");
            tabItem.activate();

            String val = bot.textWithLabel(ui("meta.objectName")).getText();
            assertTrue(val.equals(datasetName),
                       constructWrongValueMessage("openTObjectReference()", "wrong name", datasetName,
                                                  val)); // Test dataset name

            val = bot.textInGroup(ui("meta.datasetDataspaceDatatype"), 0).getText();
            assertTrue(val.equals("1"), constructWrongValueMessage("openTObjectReference()", "wrong rank",
                                                                   "1", val)); // Test rank

            val = bot.textInGroup(ui("meta.datasetDataspaceDatatype"), 3).getText();
            assertTrue(val.equals("Reference"),
                       constructWrongValueMessage("openTObjectReference()", "wrong data type", "Reference",
                                                  val)); // Test data type

            // Open dataset 'Dataset3' Table
            tableShell                     = openTreeviewObject(filetree, filename, datasetName);
            final SWTBotNatTable dataTable = getNatTable(tableShell);

            TableDataRetriever retriever =
                DataRetrieverFactory.getTableDataRetriever(dataTable, "openTObjectReference()", true);

            retriever.testAllTableLocations(expectedData);

            // Open object 'Dataset1' Table
            dataTable.doubleclick(1, 1);
            if (tableShell != null && tableShell.isOpen()) {
                closeDataObject(tableShell);
            }

            try {
                tableObjShell = openStandaloneDataObject(objectName);

                SWTBotNatTable objectTable = getNatTable(tableObjShell);

                retriever =
                    DataRetrieverFactory.getTableDataRetriever(objectTable, "openTObjectReference()", true);

                retriever.testAllTableLocations(expectedObjData);
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
                if (tableObjShell != null && tableObjShell.isOpen()) {
                    closeDataObject(tableObjShell);
                }
            }
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
                closeFile(hdfFile, false);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
