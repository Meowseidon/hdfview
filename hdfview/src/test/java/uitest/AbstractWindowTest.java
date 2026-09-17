/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * All rights reserved.                                                      *
 *                                                                           *
 * This file is part of the HDF Java Products distribution.                  *
 * The full copyright notice, including terms governing use, modification,   *
 * and redistribution, is contained in the COPYING file, which can be found  *
 * at the root of the source code distribution tree,                         *
 * or in https://www.hdfgroup.org/licenses.                                  *
 * If you do not have access to either file, you may request a copy from     *
 * help@hdfgroup.org.                                                        *
 ****************************************************************************/

package uitest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.widgetOfType;

import java.io.File;
import java.lang.reflect.Array;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import hdf.HDFVersions;
import hdf.object.Attribute;
import hdf.object.CompoundDS;
import hdf.object.Dataset;
import hdf.object.HObject;
import hdf.object.ScalarDS;
import hdf.view.DataView.DataView;
import hdf.view.HDFView;
import hdf.view.i18n.I18n;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swtbot.nebula.nattable.finder.widgets.Position;
import org.eclipse.swtbot.nebula.nattable.finder.widgets.SWTBotNatTable;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.widgets.AbstractSWTBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCanvas;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotRootMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTabItem;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotText;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;

import uitest.AbstractWindowTest.DataRetrieverFactory.TableDataRetriever;
import uitest.AbstractWindowTest.FILE_MODE;

public abstract class AbstractWindowTest {
    private static final Logger log = LoggerFactory.getLogger(AbstractWindowTest.class);

    protected static String HDF5VERSION = "HDF5 " + HDFVersions.getPropertyVersionHDF5();
    protected static String HDF4VERSION = "HDF " + HDFVersions.getPropertyVersionHDF4();
    // the version of the HDFViewer
    protected static String VERSION = HDFVersions.getPropertyVersionView();

    protected static String workDir = System.getProperty("hdfview.workdir");

    protected static SWTBot bot;

    protected static Thread uiThread;

    protected static Shell shell;

    private static final CyclicBarrier swtBarrier = new CyclicBarrier(2);

    private static int TEST_DELAY = 10;

    private static int open_files = 0;

    protected static Rectangle monitorBounds;

    protected TestInfo testInfo;

    protected static enum FILE_MODE { READ_ONLY, READ_WRITE, MULTI_READ_ONLY }

    /** Resolve test-facing UI text through the same resources as the app. */
    protected static String ui(String key, Object... args)
    {
        return I18n.text(key, args);
    }

    /** Resolve a standard HDFView child-window title used by SWTBot lookups. */
    protected static String applicationDialogTitle(String key)
    {
        return ui("window.title", VERSION) + " - " + ui(key);
    }

    @BeforeEach
    public final void setupSWTBot(TestInfo testInfo) throws InterruptedException, BrokenBarrierException
    {
        this.testInfo = testInfo;
        // synchronize with the thread opening the shell
        swtBarrier.await();
        bot = new SWTBot();

        SWTBotPreferences.PLAYBACK_DELAY = TEST_DELAY;
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run()
            {
                shell.forceActive();
            }
        });
    }

    @AfterEach
    public void closeShell() throws InterruptedException
    {
        // close the shell
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run()
            {
                shell.close();
            }
        });
    }

    public void checkOpenFiles()
    {
        if (open_files > 0) {
            String failMsg =
                "Test " + testInfo.getDisplayName() + " still had " + open_files + " files open!";

            open_files = 0;

            fail(failMsg);
        }

        open_files = 0;
    }

    @BeforeAll
    public static void setupApp()
    {
        clearRemovePropertyFile();
        I18n.setLanguage(I18n.Language.ENGLISH);

        if (uiThread == null) {
            uiThread = new Thread(new Runnable() {
                @Override
                public void run()
                {
                    try {
                        Vector<File> fList = new Vector<>();
                        String rootDir     = System.getProperty("hdfview.rootdir");
                        if (rootDir == null)
                            rootDir = System.getProperty("user.dir");
                        String startDir = System.getProperty("hdfview.workdir");

                        int W = 800, H = 600, X = 0, Y = 0;

                        while (true) {
                            // open and layout the shell
                            HDFView window = new HDFView(rootDir, startDir);

                            // Set the testing state to handle the problem with testing
                            // of native dialogs
                            window.setTestState(true);

                            shell = window.openMainWindow(fList, W, H, X, Y);

                            // Force the HDFView window to open fullscreen on the active
                            // monitor so that certain tests don't encounter weird issues
                            // due to the window being too small
                            Monitor[] monitors    = shell.getDisplay().getMonitors();
                            Monitor activeMonitor = null;

                            Rectangle r = shell.getBounds();
                            for (int i = 0; i < monitors.length; i++) {
                                if (monitors[i].getBounds().intersects(r)) {
                                    activeMonitor = monitors[i];
                                }
                            }

                            monitorBounds = activeMonitor.getBounds();

                            shell.setBounds(monitorBounds);

                            // wait for the test setup
                            swtBarrier.await();

                            // run the event loop
                            window.runMainWindow();
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }

                    Display.getDefault().syncExec(new Runnable() {
                        @Override
                        public void run()
                        {
                            shell.getDisplay().dispose();
                        }
                    });
                }
            });
            uiThread.setDaemon(true);
            uiThread.start();
        }
    }

    private static void clearRemovePropertyFile()
    {
        // the local property file name
        // look for the property file at the use home directory
        String fn = ".hdfview" + VERSION;

        File prop_file = new File(workDir, fn);
        if (prop_file.exists()) {
            prop_file.delete();
        }
    }

    protected File openFile(String name, FILE_MODE openMode)
    {
        SWTBotShell fileNameShell = null;
        File hdf_file             = new File(workDir, name);
        log.trace("openFile workDir is {}, name is {}", workDir, name);

        try {
            SWTBotMenu fileMenuItem   = bot.menu().menu(I18n.text("menu.file"));
            SWTBotMenu openasMenuItem = fileMenuItem.menu(I18n.text("menu.file.openAs"));
            if (openMode == FILE_MODE.MULTI_READ_ONLY)
                openasMenuItem.menu(I18n.text("menu.file.openAs.swmr")).click();
            else if (openMode == FILE_MODE.READ_ONLY)
                openasMenuItem.menu(I18n.text("menu.file.openAs.readOnly")).click();
            else
                openasMenuItem.menu(I18n.text("menu.file.openAs.readWrite")).click();

            fileNameShell = bot.shell(ui("dialog.enterFileName.title"));
            fileNameShell.activate();
            bot.waitUntil(Conditions.shellIsActive(fileNameShell.getText()));

            SWTBotText text = fileNameShell.bot().text();
            text.setText(hdf_file.getName());

            String val = text.getText();
            assertTrue(val.equals(hdf_file.getName()), "openFile() wrong file name: expected '" +
                                                           hdf_file.getName() + "' but was '" + val + "'");

            fileNameShell.bot().button(I18n.text("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(fileNameShell));

            SWTBotTree filetree = bot.tree();
            bot.waitUntil(Conditions.treeHasRows(filetree, open_files + 1));

            /*
             * TODO: difference here between rowCount() and visibleRowCount(). Can't use
             * checkFileTree().
             */
            assertTrue(filetree.rowCount() == open_files + 1,
                       "openFile() filetree wrong row count: expected '" + String.valueOf(open_files) +
                           "' but was '" + filetree.rowCount() + "'");
            assertTrue(filetree.getAllItems()[open_files].getText().compareTo(hdf_file.getName()) == 0,
                       "openFile() filetree is missing file '" + hdf_file.getName() + "'");

            /*
             * Increment the open_files value last, in case an error occurs when opening a
             * file.
             */
            open_files++;
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
        }
        finally {
            if (fileNameShell != null && fileNameShell.isOpen())
                fileNameShell.close();
        }
        log.trace("openFile  {}, open_files={}", name, open_files);

        return hdf_file;
    }

    protected File createFile(String name)
    {
        boolean hdf4Type = (name.lastIndexOf(".hdf") >= 0);
        boolean hdf5Type = (name.lastIndexOf(".h5") >= 0);

        File hdfFile = new File(workDir, name);
        log.trace("createFile workDir is {}, name is {}", workDir, name);
        if (hdfFile.exists())
            hdfFile.delete();

        try {
            SWTBotMenu fileMenuItem    = bot.menu().menu(I18n.text("menu.file"));
            SWTBotMenu fileNewMenuItem = fileMenuItem.menu(I18n.text("menu.file.new"));
            if (hdf4Type)
                fileNewMenuItem.menu(I18n.text("menu.file.new.hdf4")).click();
            else if (hdf5Type)
                fileNewMenuItem.menu(I18n.text("menu.file.new.hdf5")).click();
            else
                throw new IllegalArgumentException("unknown file type");

            SWTBotShell shell = bot.shell(ui("dialog.enterFileName.title"));
            shell.activate();
            bot.waitUntil(Conditions.shellIsActive(shell.getText()));

            SWTBotText text = shell.bot().text();
            text.setText(name);

            String val = text.getText();
            assertTrue(val.equals(name),
                       "createFile() wrong file name: expected '" + name + "' but was '" + val + "'");

            shell.bot().button(I18n.text("button.ok")).click();
            bot.waitUntil(Conditions.shellCloses(shell));

            assertTrue(hdfFile.exists(), "createFile() File '" + hdfFile + "' not created");
            open_files++;
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
        }
        log.trace("createFile  {}, open_files={}", name, open_files);

        return hdfFile;
    }

    protected void closeFile(File hdfFile, boolean deleteFile)
    {
        try {
            log.trace("closeFile {}, open_files={}", hdfFile.getName(), open_files);

            /* Always resolve the tree from the stable main window. */
            SWTBotShell mainShell = new SWTBotShell(shell);
            mainShell.activate();
            bot.waitUntil(Conditions.shellIsActive(mainShell.getText()));

            SWTBotTree filetree = mainShell.bot().tree();
            filetree.select(hdfFile.getName());
            filetree.getTreeItem(hdfFile.getName()).click();

            SWTBotMenu fileMenuItem = mainShell.bot().menu().menu(I18n.text("menu.file"));
            fileMenuItem.menu(I18n.text("menu.file.close")).click();

            if (deleteFile) {
                if (hdfFile.exists()) {
                    assertTrue(hdfFile.delete(), "closeFile() File '" + hdfFile + "' not deleted");
                    assertFalse(hdfFile.exists(), "closeFile() File '" + hdfFile + "' not gone");
                }
            }
            log.trace("closeFile after open_files={}", open_files);

            if (open_files > 0) {
                assertTrue(filetree.rowCount() == open_files - 1,
                           constructWrongValueMessage("closeFile()", "filetree wrong row count",
                                                      String.valueOf(open_files - 1),
                                                      String.valueOf(filetree.rowCount())));
                open_files--;
            }

            bot.waitUntil(Conditions.treeHasRows(filetree, open_files));
            log.trace("closeFile after open_files={}", open_files);
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

    protected void checkFileTree(SWTBotTree tree, String funcName, int expectedRowCount, String filename)
        throws IllegalArgumentException, AssertionError
    {
        if (tree == null)
            throw new IllegalArgumentException("SWTBotTree parameter is null");
        if (filename == null)
            throw new IllegalArgumentException("file name parameter is null");
        bot.sleep(500);

        String expectedRowCountStr = String.valueOf(expectedRowCount);
        int visibleRowCount        = tree.visibleRowCount();
        assertTrue(visibleRowCount == expectedRowCount,
                   constructWrongValueMessage(funcName, "filetree wrong row count", expectedRowCountStr,
                                              String.valueOf(visibleRowCount)));

        String curFilename = tree.getAllItems()[0].getText();
        assertTrue(curFilename.compareTo(filename) == 0,
                   constructWrongValueMessage(funcName, "filetree is missing file", filename, curFilename));
    }

    protected void testSamplePixel(final int theX, final int theY, String requiredValue)
    {
        try {
            SWTBotShell botshell           = bot.activeShell();
            SWTBot thisbot                 = botshell.bot();
            final SWTBotCanvas imageCanvas = thisbot.canvas(1);

            // Make sure Show Values is selected
        SWTBotMenu imageMenuItem      = thisbot.menu().menu(ui("image.menu"));
        SWTBotMenu showValuesMenuItem = imageMenuItem.menu(ui("image.showValues"));
            if (!showValuesMenuItem.isChecked()) {
                showValuesMenuItem.click();
            }

            Display.getDefault().syncExec(new Runnable() {
                @Override
                public void run()
                {
                    imageCanvas.widget.notifyListeners(SWT.MouseMove, new Event() {
                        {
                            x = theX;
                            y = theY;
                        }
                    });
                }
            });

            String val = thisbot.text().getText();
            assertTrue(
                val.equals(requiredValue),
                constructWrongValueMessage("testSamplePixel()", "wrong pixel value", requiredValue, val));
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

    protected SWTBotTable openAttributeTable(SWTBotTree tree, String filename, String objectName)
    {
        SWTBotTreeItem fileItem = tree.getTreeItem(filename);

        SWTBotTreeItem foundObject = locateItemByPath(fileItem, objectName);
        foundObject.click();

        SWTBotTabItem tabItem = bot.tabItem(I18n.text("tab.objectAttributeInfo"));
        tabItem.activate();

        return new SWTBotTable(bot.widget(widgetOfType(Table.class)));
    }

    protected SWTBotTabItem openMetadataTab(SWTBotTree tree, String filename, String objectName,
                                            String tabKey)
    {
        SWTBotTreeItem fileItem = tree.getTreeItem(filename);

        SWTBotTreeItem foundObject = locateItemByPath(fileItem, objectName);
        foundObject.click();

        return bot.tabItem(I18n.text(tabKey));
    }

    protected SWTBotShell openAttributeObject(SWTBotTable attrTable, String objectName, int rowIndex)
    {
        attrTable.doubleClick(rowIndex, 0);

        return openStandaloneDataObject(objectName);
    }

    protected SWTBotShell openAttributeContext(SWTBotTable attrTable, String objectName, int rowIndex)
    {
        attrTable.click(rowIndex, 0);
        attrTable.contextMenu().contextMenu(ui("meta.viewEditAttribute")).click();

        return openStandaloneDataObject(objectName);
    }

    protected SWTBotShell openTreeviewObject(SWTBotTree tree, String filename, String objectName)
    {
        SWTBotTreeItem fileItem = tree.getTreeItem(filename);

        SWTBotTreeItem foundObject = locateItemByPath(fileItem, objectName);
        foundObject.click();
        foundObject.contextMenu().contextMenu(I18n.text("tree.open")).click();

        final HObject[] selectedObject = new HObject[1];
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run()
            {
                selectedObject[0] = (HObject)foundObject.widget.getData();
            }
        });
        HObject object = selectedObject[0];
        if (isInlineDataset(object))
            return openInlineDataObject(objectName);
        return openStandaloneDataObject(objectName);
    }

    protected SWTBotShell openDataObject(String objectName)
    {
        /* TreeView starts data loading on a worker thread. Wait for either the
         * embedded table or a specialized/attribute window before locating it. */
        bot.waitUntil(new org.eclipse.swtbot.swt.finder.waits.DefaultCondition() {
            @Override
            public boolean test()
            {
                return hasInlineTable() || hasStandaloneDataObject(objectName);
            }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for data object " + objectName;
            }
        });

        /* Ordinary Datasets now live in the main window's Data Content tab. */
        if (hasInlineTable()) {
            SWTBotShell mainShell = new SWTBotShell(shell);
            mainShell.activate();
            bot.waitUntil(Conditions.shellIsActive(mainShell.getText()));
            return mainShell;
        }

        return openStandaloneDataObject(objectName);
    }

    private SWTBotShell openInlineDataObject(String objectName)
    {
        bot.waitUntil(new org.eclipse.swtbot.swt.finder.waits.DefaultCondition() {
            @Override
            public boolean test()
            {
                return hasInlineTable();
            }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for inline data object " + objectName;
            }
        });

        SWTBotShell mainShell = new SWTBotShell(shell);
        mainShell.activate();
        bot.waitUntil(Conditions.shellIsActive(mainShell.getText()));
        return mainShell;
    }

    private boolean hasStandaloneDataObject(String objectName)
    {
        String strippedObjectName = objectName;
        int slashLoc              = objectName.lastIndexOf('/');
        if (slashLoc >= 0)
            strippedObjectName = objectName.substring(slashLoc + 1);

        final String titleFragment = strippedObjectName;
        final boolean[] found      = new boolean[] {false};
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run()
            {
                for (Shell candidate : shell.getDisplay().getShells()) {
                    if (candidate != shell && !candidate.isDisposed() &&
                        isStandaloneDataShell(candidate, titleFragment)) {
                        found[0] = true;
                        return;
                    }
                }
            }
        });
        return found[0];
    }

    protected SWTBotShell openStandaloneDataObject(String objectName)
    {
        String strippedObjectName = objectName;
        int slashLoc              = objectName.lastIndexOf('/');
        if (slashLoc >= 0) {
            strippedObjectName = objectName.substring(slashLoc + 1);
        }
        final String targetObjectName = strippedObjectName;

        final Shell[] dataShell = new Shell[1];
        bot.waitUntil(new org.eclipse.swtbot.swt.finder.waits.DefaultCondition() {
            @Override
            public boolean test()
            {
                Display.getDefault().syncExec(new Runnable() {
                    @Override
                    public void run()
                    {
                        for (Shell candidate : shell.getDisplay().getShells()) {
                            if (candidate != shell && !candidate.isDisposed() &&
                                isStandaloneDataShell(candidate, targetObjectName)) {
                                dataShell[0] = candidate;
                                return;
                            }
                        }
                    }
                });
                return dataShell[0] != null && !dataShell[0].isDisposed();
            }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for data object " + objectName;
            }
        });

        final SWTBotShell botShell = new SWTBotShell(dataShell[0]);

        botShell.activate();
        bot.waitUntil(Conditions.shellIsActive(botShell.getText()));

        /*
         * Due to testing issues where the values can't be retrieved from non-visible
         * table columns, we ensure that the table Shell is always maximized.
         */
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run()
            {
                botShell.widget.setMaximized(true);
            }
        });

        return botShell;
    }

    /** Match a standalone view by its stable DataView object before using its localized title. */
    private boolean isStandaloneDataShell(Shell candidate, String objectName)
    {
        Object shellData = candidate.getData();
        if (shellData instanceof DataView) {
            HObject displayedObject = ((DataView)shellData).getDataObject();
            if (displayedObject != null && objectName.equals(displayedObject.getName()))
                return true;
        }

        /* Keep a title fallback for third-party views which do not expose shell data. */
        return candidate.getText().contains(objectName);
    }

    private boolean isInlineDataset(HObject object)
    {
        if (object == null || object instanceof Attribute || !(object instanceof Dataset))
            return false;

        Dataset dataset = (Dataset)object;
        if (dataset.isNULL() || (!(dataset instanceof ScalarDS) && !(dataset instanceof CompoundDS)))
            return false;

        if (dataset instanceof ScalarDS) {
            try {
                if (!dataset.isInited())
                    dataset.init();
                return !((ScalarDS)dataset).isImage();
            }
            catch (Exception ex) {
                return false;
            }
        }
        return true;
    }

    private boolean hasInlineTable()
    {
        final boolean[] found = new boolean[] {false};
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run()
            {
                found[0] = findNatTable(shell) != null;
            }
        });
        return found[0];
    }

    private static NatTable findNatTable(Composite root)
    {
        if (root == null || root.isDisposed())
            return null;

        for (Control child : root.getChildren()) {
            if (child.isDisposed())
                continue;

            if (child instanceof NatTable)
                return (NatTable)child;
            if (child instanceof Composite) {
                NatTable table = findNatTable((Composite)child);
                if (table != null)
                    return table;
            }
        }
        return null;
    }

    /** Return the localized Table menu for either a standalone or inline view. */
    protected SWTBotMenu tableMenu(SWTBotShell theShell)
    {
        if (theShell == null || !theShell.isOpen())
            throw new WidgetNotFoundException("TableView shell is not open");

        if (theShell.widget != shell)
            return theShell.bot().menu().menu(ui("table"));

        final Menu[] popupMenu = new Menu[] {null};
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run()
            {
                NatTable table = findNatTable(shell);
                Control current = table;
                while (current != null && popupMenu[0] == null) {
                    if (!current.isDisposed())
                        popupMenu[0] = current.getMenu();
                    current = current.getParent();
                }
            }
        });

        if (popupMenu[0] == null || popupMenu[0].isDisposed())
            throw new WidgetNotFoundException("Inline TableView menu is not available");

        return new SWTBotRootMenu(popupMenu[0]).menu(ui("table"));
    }

    private SWTBotTreeItem locateItemByPath(SWTBotTreeItem startNode, String objPath)
    {
        StringTokenizer st  = new StringTokenizer(objPath, "/");
        SWTBotTreeItem node = startNode;

        while (st.hasMoreTokens()) {
            String nextToken = st.nextToken();
            node             = node.getNode(nextToken);
            if (node.getItems().length > 0)
                node.expand();
        }

        return node;
    }

    /*
     * A factory class to return concrete instances of TableDataRetriever classes,
     * which will retrieve the data value at the specified row and column position
     * in the given Table object.
     */
    public static class DataRetrieverFactory {

        public static TableDataRetriever getTableDataRetriever(AbstractSWTBot<?> tableObject, String funcName,
                                                               boolean noRegex)
        {
            if (tableObject == null)
                throw new IllegalArgumentException("AbstractSWTBot parameter is null");
            if (funcName == null)
                throw new IllegalArgumentException("function name parameter is null");

            if (tableObject instanceof SWTBotNatTable)
                return new NatTableDataRetriever((SWTBotNatTable)tableObject, funcName, noRegex);
            else
                return new SWTTableDataRetriever((SWTBotTable)tableObject, funcName, noRegex);
        }

        public static class TableDataRetriever {

            protected final StringBuilder sb;

            protected final String funcName;

            protected final boolean noRegex;

            public TableDataRetriever(String funcName, boolean noRegex)
            {
                this.funcName = funcName;
                this.noRegex  = noRegex;

                this.sb = new StringBuilder();
            }

            /*
             * Utility function to offset the table row position for extra header info.
             */
            public void setContainerHeaderOffset(int containerRowHeaderOffset, int containerColHeaderOffset)
            {
                throw new UnsupportedOperationException(
                    "subclasses must implement setContainerHeaderOffset()");
            }

            public void setPagingActive(boolean pagingActive)
            {
                throw new UnsupportedOperationException("subclasses must implement setPagingActive()");
            }

            /*
             * Utility function to compare a given table position against an expected value.
             */
            public void testTableLocation(int rowIndex, int colIndex, String expectedValRegex)
            {
                throw new UnsupportedOperationException("subclasses must implement testTableLocation()");
            }

            /*
             * Utility function wrapper around testTableLocations() for testing an entire
             * table.
             */
            public void testAllTableLocations(String[][] expectedValRegexArray)
            {
                testTableLocations(0, 0, expectedValRegexArray);
            }

            public void testTableLocations(int rowOffset, int colOffset, String[][] expectedValRegexArray)
            {
                int arrLen = Array.getLength(expectedValRegexArray);
                for (int i = 0; i < arrLen; i++) {
                    String[] nestedArray = (String[])Array.get(expectedValRegexArray, i);
                    int nestedLen        = Array.getLength(nestedArray);

                    for (int j = 0; j < nestedLen; j++)
                        testTableLocation(rowOffset + i, colOffset + j, (String)Array.get(nestedArray, j));
                }
            }
        }

        private static class NatTableDataRetriever extends TableDataRetriever {

            private final SWTBotNatTable table;
            private int containerRowHeaderOffset = 0;
            private int containerColHeaderOffset = 0;
            boolean pagingActive                 = false;
            Position lastVisibleCellPos =
                new Position(1 + containerRowHeaderOffset, 1 + containerColHeaderOffset);

            NatTableDataRetriever(SWTBotNatTable tableObj, String funcName, boolean noRegex)
            {
                super(funcName, noRegex);

                this.table = tableObj;
                log.trace("lastVisibleCellPos: row is {}, col is {}", lastVisibleCellPos.row,
                          lastVisibleCellPos.column);
            }

            @Override
            public void testTableLocation(int rowIndex, int colIndex, String expectedValRegex)
            {
                if (expectedValRegex == null)
                    throw new IllegalArgumentException("expected value string parameter is null");

                int textboxIndex = 0;
                if (pagingActive)
                    textboxIndex = 2;

                // TODO: temporary workaround until the solution below works.
                log.trace("rowIndex is {}, colIndex is {}", rowIndex, colIndex);
                lastVisibleCellPos = table.scrollViewport(lastVisibleCellPos, rowIndex, colIndex);
                log.trace("lastVisibleCellPos: row is {}, col is {}", lastVisibleCellPos.row,
                          lastVisibleCellPos.column);
                table.click(lastVisibleCellPos.row, lastVisibleCellPos.column);
                bot.sleep(50);
                final Shell[] tableParentShell = new Shell[1];
                Display.getDefault().syncExec(new Runnable() {
                    @Override
                    public void run()
                    {
                        tableParentShell[0] = table.widget.getShell();
                    }
                });
                SWTBotShell tableShell = new SWTBotShell(tableParentShell[0]);
                String val = tableShell.bot().text(textboxIndex).getText();

                // Disabled until Data conversion can be figured out
                // String val = table.getCellDataValueByPosition(rowIndex, colIndex);

                sb.setLength(0);
                sb.append("wrong value at table index ")
                    .append("(")
                    .append(rowIndex)
                    .append(", ")
                    .append(colIndex)
                    .append(")");
                String errMsg = constructWrongValueMessage(funcName, sb.toString(), expectedValRegex, val);
                if (noRegex)
                    expectedValRegex = "\\Q" + expectedValRegex + "\\E";
                assertTrue(val.matches(expectedValRegex), errMsg);
            }

            @Override
            public void setPagingActive(boolean pagingActive)
            {
                this.pagingActive = pagingActive;
            }

            @Override
            public void setContainerHeaderOffset(int containerRowHeaderOffset, int containerColHeaderOffset)
            {
                this.containerRowHeaderOffset = containerRowHeaderOffset;
                this.containerColHeaderOffset = containerColHeaderOffset;
                lastVisibleCellPos = new Position(1 + containerRowHeaderOffset, 1 + containerColHeaderOffset);
            }
        }

        private static class SWTTableDataRetriever extends TableDataRetriever {

            private final SWTBotTable table;

            SWTTableDataRetriever(SWTBotTable tableObj, String funcName, boolean noRegex)
            {
                super(funcName, noRegex);

                this.table = tableObj;
            }

            @Override
            public void testTableLocation(int rowIndex, int colIndex, String expectedValRegex)
            {
                if (expectedValRegex == null)
                    throw new IllegalArgumentException("expected value string parameter is null");

                table.click(rowIndex, colIndex);
                String val = table.cell(rowIndex, colIndex);

                sb.setLength(0);
                sb.append("wrong value at table index ")
                    .append("(")
                    .append(rowIndex)
                    .append(", ")
                    .append(colIndex)
                    .append(")");
                String errMsg = constructWrongValueMessage(funcName, sb.toString(), expectedValRegex, val);
                if (noRegex)
                    expectedValRegex = "\\Q" + expectedValRegex + "\\E";
                assertTrue(val.matches(expectedValRegex), errMsg);
            }
        }
    }

    protected SWTBotNatTable getNatTable(SWTBotShell theShell)
    {
        return new SWTBotNatTable(theShell.bot().widget(widgetOfType(NatTable.class)));
    }

    protected final void closeShell(SWTBotShell theShell)
    {
        if (theShell == null || !theShell.isOpen())
            return;

        if (theShell.widget == shell) {
            if (!hasInlineTable())
                return;

            try {
                tableMenu(theShell).menu(ui("table.close")).click();
                bot.waitUntil(new org.eclipse.swtbot.swt.finder.waits.DefaultCondition() {
                    @Override
                    public boolean test()
                    {
                        return !hasInlineTable();
                    }

                    @Override
                    public String getFailureMessage()
                    {
                        return "Timed out waiting for the inline TableView to close";
                    }
                });
            }
            catch (WidgetNotFoundException ex) {
                // The inline view may already have been replaced or disposed.
            }
            return;
        }

        SWTBotMenu closeButton = null;
        try {
            closeButton = theShell.bot().menu(ui("table.close"));
        }
        catch (WidgetNotFoundException ex) {
            closeButton = null;
        }

        if (closeButton != null) {
            closeButton.click();
            bot.waitUntil(Conditions.shellCloses(theShell));
        }
    }

    /** Close a TableView regardless of whether it is embedded or standalone. */
    protected final void closeDataObject(SWTBotShell dataShell)
    {
        if (dataShell == null || !dataShell.isOpen())
            return;

        if (dataShell.widget == shell) {
            closeShell(dataShell);
            return;
        }

        tableMenu(dataShell).menu(ui("action.close")).click();
        bot.waitUntil(Conditions.shellCloses(dataShell));
    }

    /*
     * Only useful when testing certain Menu items which open files in a different
     * manner than the openFile() method.
     */
    protected final void refreshOpenFileCount() { open_files = bot.tree().getAllItems().length; }

    /*
     * Only useful when testing certain Menu items which close files in a different
     * manner than the closeFile() method.
     */
    protected final void resetOpenFileCount() { open_files = 0; }

    /**
     * Resume the UI test harness after a test deliberately closes the main
     * window. The application creates the next main window and waits at this
     * barrier before returning to its event loop.
     */
    protected final void releaseUiBarrierAfterMainWindowExit()
        throws InterruptedException, BrokenBarrierException
    {
        swtBarrier.await();
    }

    protected static String constructWrongValueMessage(String methodName, String message, String expected,
                                                       String actual)
    {
        StringBuilder builder = new StringBuilder(methodName);
        builder.append(" " + message + ": expected '" + expected + "' but was '" + actual + "'");

        if (expected.equals(actual))
            builder.append(" - possible regex mismatch due to non-escaped characters \\^${}[]()*+?|<>-&");

        return builder.toString();
    }
}
