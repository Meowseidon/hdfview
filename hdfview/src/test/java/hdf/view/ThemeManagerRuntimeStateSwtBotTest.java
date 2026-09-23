package hdf.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.widgetOfType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import hdf.object.Dataset;
import hdf.object.HObject;
import hdf.view.DataView.DataView;
import hdf.view.TableView.TableView;
import hdf.view.i18n.I18n;
import hdf.view.statistics.DatasetStatisticsDialog;
import hdf.view.statistics.DatasetStatisticsEngine;

import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swtbot.nebula.nattable.finder.widgets.SWTBotNatTable;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import uitest.AbstractWindowTest;

/**
 * Real SWTBot coverage for changing the system theme while an edited Dataset
 * and its statistics dialog are already open.
 *
 * <p>The test installs a deterministic ThemeManager through the existing
 * Settings-listener constructor seam before constructing the real HDFView
 * window.  The user path remains SWTBot-driven; only the operating-system
 * preference is simulated.</p>
 */
@Tag("ui")
@Tag("integration")
public class ThemeManagerRuntimeStateSwtBotTest {
    private static final String DISPLAY_THEME_MANAGER_KEY = ThemeManager.class.getName() + ".instance";
    private static final String SOURCE_FIXTURE = "tframeselection.h5";
    private static final String DATASET_NAME = "test_dataset";
    private static final String EDIT_FIXTURE = "dark-mode-runtime-state.h5";
    private static final int EDIT_COLUMN = 1;
    private static final int EDIT_ROW = 1;

    private static final AtomicBoolean darkTheme = new AtomicBoolean(false);
    private static final AtomicReference<Listener> settingsListener = new AtomicReference<>();
    private static final AtomicReference<ThemeManager> themeManager = new AtomicReference<>();
    private static final AtomicReference<HDFView> hdfView = new AtomicReference<>();
    private static final AtomicReference<CountingViewProperties> countingProperties = new AtomicReference<>();
    private static final AtomicReference<Display> display = new AtomicReference<>();
    private static final AtomicReference<Shell> mainShell = new AtomicReference<>();

    private static Thread uiThread;
    private static SWTBot bot;
    private static final FixtureAccess fixtureAccess = new FixtureAccess();

    @BeforeAll
    static void startApplication() throws Exception
    {
        I18n.setLanguage(I18n.Language.ENGLISH);

        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<Throwable> startupFailure = new AtomicReference<>();
        uiThread = new Thread(() -> {
            Display uiDisplay = null;
            try {
                String rootDir = System.getProperty("hdfview.rootdir");
                if (rootDir == null)
                    rootDir = System.getProperty("user.dir");
                String startDir = System.getProperty("hdfview.workdir");

                /*
                 * HDFView owns the Display it creates.  Bootstrap once only to
                 * obtain that Display, replace the manager before opening the
                 * real window, and then construct the actual application.
                 */
                new HDFView(rootDir, startDir);
                uiDisplay = Display.getCurrent();
                if (uiDisplay == null)
                    throw new IllegalStateException("HDFView did not create the SWT UI Display");
                display.set(uiDisplay);

                ThemeManager.forDisplay(uiDisplay).dispose();
                darkTheme.set(false);
                ThemeManager injectedManager = new ThemeManager(
                    uiDisplay,
                    darkTheme::get,
                    true,
                    settingsListener::set,
                    listener -> settingsListener.compareAndSet(listener, null));
                themeManager.set(injectedManager);
                /* This is the existing display-scoped factory key, not a production test API. */
                uiDisplay.setData(DISPLAY_THEME_MANAGER_KEY, injectedManager);

                HDFView application = new HDFView(rootDir, startDir);
                installSaveCountingProperties(application, rootDir, startDir);
                /* Keep the runtime test independent of a previous persisted choice. */
                injectedManager.setThemeMode(ThemeManager.ThemeMode.SYSTEM);
                application.setTestState(true);
                hdfView.set(application);

                Shell openedShell = application.openMainWindow(new java.util.Vector<File>(),
                                                               1200, 800, 0, 0);
                mainShell.set(openedShell);
                Monitor primary = uiDisplay.getPrimaryMonitor();
                if (primary != null)
                    openedShell.setBounds(primary.getBounds());

                ready.countDown();
                application.runMainWindow();
            }
            catch (Throwable failure) {
                startupFailure.set(failure);
                ready.countDown();
            }
            finally {
                ThemeManager manager = themeManager.get();
                if (manager != null)
                    manager.dispose();
                if (uiDisplay != null && !uiDisplay.isDisposed())
                    uiDisplay.dispose();
            }
        }, "hdfview-theme-state-swtbot");
        uiThread.setDaemon(true);
        uiThread.start();

        assertTrue(ready.await(30, TimeUnit.SECONDS), "Timed out waiting for HDFView to open");
        Throwable failure = startupFailure.get();
        if (failure != null)
            throw new AssertionError("HDFView SWTBot application failed to start", failure);

        bot = new SWTBot();
        SWTBotPreferences.PLAYBACK_DELAY = 10;
        fixtureAccess.install(bot, mainShell.get());
        assertNotNull(themeManager.get());
        assertEquals(ThemeManager.ThemeMode.SYSTEM, themeManager.get().getThemeMode());
        assertFalse(themeManager.get().isDark(), "The deterministic test must start in Light mode");
    }

    @AfterAll
    static void stopApplication() throws Exception
    {
        Display uiDisplay = display.get();
        Shell openedShell = mainShell.get();
        if (uiDisplay != null && !uiDisplay.isDisposed() && openedShell != null && !openedShell.isDisposed()) {
            uiDisplay.syncExec(() -> {
                if (!openedShell.isDisposed())
                    openedShell.close();
            });
        }

        if (uiThread != null)
            uiThread.join(15_000);
        if (uiThread != null && uiThread.isAlive())
            throw new AssertionError("HDFView SWTBot UI thread did not terminate");
    }

    @Test
    void forcedLightDarkSystemPreservesDatasetEditFrameHighlightAndOpenDialogs() throws Exception
    {
        Path fixturePath = fixturePath();
        Files.copy(fixturePath.resolveSibling(SOURCE_FIXTURE), fixturePath,
                   StandardCopyOption.REPLACE_EXISTING);

        File hdfFile = null;
        SWTBotShell statisticsShell = null;
        SWTBotShell applicationShell = new SWTBotShell(mainShell.get());
        SWTBotNatTable dataTable = null;
        try {
            if (I18n.getLanguage() != I18n.Language.ENGLISH)
                selectLanguage(I18n.Language.ENGLISH);
            selectLanguage(I18n.Language.SIMPLIFIED_CHINESE);
            assertEquals(I18n.Language.SIMPLIFIED_CHINESE.getPropertyValue(),
                         persistedProperty(ViewProperties.LANGUAGE_PROPERTY));
            selectLanguage(I18n.Language.ENGLISH);
            assertEquals(I18n.Language.ENGLISH.getPropertyValue(),
                         persistedProperty(ViewProperties.LANGUAGE_PROPERTY));

            selectThemeMode(ThemeManager.ThemeMode.LIGHT);
            assertEquals("light", persistedTheme());

            hdfFile = fixtureAccess.openReadWrite(EDIT_FIXTURE);
            SWTBotTreeItem datasetItem = bot.tree().getTreeItem(hdfFile.getName()).getNode(DATASET_NAME);
            datasetItem.click();

            dataTable = waitForDataContent(applicationShell);
            assertRankThreeDataset();
            assertEmbeddedTableChrome(dataTable);

            String firstFrame = currentFrame();
            applicationShell.activate();
            applicationShell.bot().toolbarButtonWithTooltip(text("table.nextFrame")).click();
            bot.waitUntil(new DefaultCondition() {
                @Override
                public boolean test() { return !firstFrame.equals(currentFrame()); }

                @Override
                public String getFailureMessage()
                {
                    return "Timed out waiting for the rank-three Dataset to leave its default frame";
                }
            });

            String oldValue = dataTable.getCellDataValueByPosition(EDIT_COLUMN, EDIT_ROW);
            String newValue = oldValue.trim().equals("123") ? "124" : "123";
            editCell(dataTable, newValue);
            assertEquals(newValue, dataTable.getCellDataValueByPosition(EDIT_COLUMN, EDIT_ROW));
            assertDirty();

            fixtureAccess.tableMenuFor(applicationShell).menu(text("table.showStatistics")).click();
            statisticsShell = bot.shell(text("statistics.title"));
            waitForStatistics(statisticsShell.bot().table());
            statisticsShell.bot().button(text("statistics.nonZero")).click();

            State before = captureState(dataTable, EDIT_COLUMN, EDIT_ROW);
            Shell[] shellsBefore = openShells();
            Shell statisticsWidget = statisticsShell.widget;
            assertStatisticsChrome(statisticsWidget);
            assertSame(mainShell.get(), applicationShell.widget, "The test must use the existing main Shell");
            assertSame(statisticsWidget, statisticsShell.widget);
            assertEquals(DatasetStatisticsEngine.Kind.NON_ZERO, before.highlightKind);
            assertTrue(before.dirty, "The edited Dataset must still be dirty before theme refresh");

            selectThemeMode(ThemeManager.ThemeMode.DARK);
            assertEquals("dark", persistedTheme());
            State dark = captureState(dataTable, EDIT_COLUMN, EDIT_ROW);
            assertEquals(ThemeManager.ThemeMode.DARK, themeManager.get().getThemeMode());
            assertStateUnchanged(before, dark, "Forced Light -> Forced Dark");
            assertShellsUnchanged(shellsBefore, statisticsWidget, "Forced Light -> Forced Dark");
            assertEmbeddedTableChrome(dataTable);
            assertStatisticsChrome(statisticsWidget);

            selectThemeMode(ThemeManager.ThemeMode.SYSTEM);
            assertEquals("system", persistedTheme());
            State system = captureState(dataTable, EDIT_COLUMN, EDIT_ROW);
            assertEquals(ThemeManager.ThemeMode.SYSTEM, themeManager.get().getThemeMode());
            assertStateUnchanged(before, system, "Forced Dark -> System");
            assertShellsUnchanged(shellsBefore, statisticsWidget, "Forced Dark -> System");
            assertEmbeddedTableChrome(dataTable);
            assertStatisticsChrome(statisticsWidget);

            refreshSystemTheme(true);
            State systemDark = captureState(dataTable, EDIT_COLUMN, EDIT_ROW);
            assertStateUnchanged(before, systemDark, "System Light -> System Dark");
            assertShellsUnchanged(shellsBefore, statisticsWidget, "System Light -> System Dark");
            assertEmbeddedTableChrome(dataTable);
            assertStatisticsChrome(statisticsWidget);

            refreshSystemTheme(false);
            State systemLight = captureState(dataTable, EDIT_COLUMN, EDIT_ROW);
            assertStateUnchanged(before, systemLight, "System Dark -> System Light");
            assertShellsUnchanged(shellsBefore, statisticsWidget, "System Dark -> System Light");
            assertEmbeddedTableChrome(dataTable);
            assertStatisticsChrome(statisticsWidget);
        }
        finally {
            if (statisticsShell != null && statisticsShell.isOpen())
                statisticsShell.close();
            closeFixtureWithoutSaving(hdfFile);
            Files.deleteIfExists(fixturePath);
        }
    }

    private static SWTBotNatTable waitForDataContent(SWTBotShell applicationShell)
    {
        final AtomicReference<SWTBotNatTable> table = new AtomicReference<>();
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test()
            {
                try {
                    table.set(new SWTBotNatTable(
                        applicationShell.bot().widget(widgetOfType(NatTable.class))));
                    return true;
                }
                catch (RuntimeException ex) {
                    return false;
                }
            }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for Data Content to display a NatTable";
            }
        });
        return table.get();
    }

    private static void assertRankThreeDataset()
    {
        final int[] rank = new int[1];
        display.get().syncExec(() -> {
            HObject selected = hdfView.get().getTreeView().getCurrentObject();
            assertNotNull(selected, "The Dataset must remain selected in the Tree");
            assertTrue(selected instanceof Dataset, "The selected object must be a Dataset");
            rank[0] = ((Dataset)selected).getRank();
        });
        assertTrue(rank[0] > 2, "The existing multi-frame Dataset fixture must be rank > 2");
    }

    /** Verify the application-owned Data Content chrome, not NatTable's data palette. */
    private static void assertEmbeddedTableChrome(SWTBotNatTable table)
    {
        display.get().syncExec(() -> {
            assertNotNull(table, "Data Content NatTable must still exist");
            Control tableControl = table.widget;
            TabFolder tabFolder = null;
            for (Control parent = tableControl.getParent(); parent != null; parent = parent.getParent()) {
                if (parent instanceof TabFolder) {
                    tabFolder = (TabFolder)parent;
                    break;
                }
            }
            assertNotNull(tabFolder, "Data Content must remain inside the main TabFolder");
            int selectionIndex = tabFolder.getSelectionIndex();
            assertTrue(selectionIndex >= 0, "The Data Content tab must remain selected");
            TabItem selectedTab = tabFolder.getItem(selectionIndex);
            Control dataPage = selectedTab.getControl();
            assertNotNull(dataPage, "The selected Data Content tab must own a page Composite");

            ThemeManager manager = themeManager.get();
            RGB surface = manager.color(ThemeManager.ColorRole.SURFACE).getRGB();
            assertEquals(surface, dataPage.getBackground().getRGB(),
                         "Data Content page chrome must follow the active surface palette");

            Group indexGroup = findGroupWithPrefix(dataPage, "table.index");
            assertNotNull(indexGroup, "The embedded table must retain its index/value Group");
            assertEquals(manager.color(ThemeManager.ColorRole.SECONDARY_SURFACE).getRGB(),
                         indexGroup.getBackground().getRGB(),
                         "The embedded table index/value chrome must follow the active secondary surface");

            ToolBar toolbar = findDescendant(dataPage, ToolBar.class);
            assertNotNull(toolbar, "The embedded table must retain its application toolbar");
            assertEquals(manager.color(ThemeManager.ColorRole.SECONDARY_SURFACE).getRGB(),
                         toolbar.getBackground().getRGB(),
                         "The embedded table toolbar must follow the active secondary surface");

            SashForm[] tableSashes = findDescendants(dataPage, SashForm.class);
            assertTrue(tableSashes.length >= 2,
                       "The embedded table must retain both application-owned SashForms");
            for (SashForm sash : tableSashes)
                assertEquals(surface, sash.getBackground().getRGB(),
                             "Embedded table SashForm chrome must follow the active surface palette");

            ScrolledComposite valueScroller = findDescendant(dataPage, ScrolledComposite.class);
            assertNotNull(valueScroller, "The embedded table must retain its value ScrolledComposite");
            assertEquals(surface, valueScroller.getBackground().getRGB(),
                         "The cell-value chrome must follow the active surface palette");
        });
    }

    private static void assertStatisticsChrome(Shell statisticsShell)
    {
        display.get().syncExec(() -> {
            Group highlightGroup = findGroupWithPrefix(statisticsShell, "statistics.highlightGroup");
            assertNotNull(highlightGroup, "Statistics dialog must retain its highlight Group");
            assertEquals(themeManager.get().color(ThemeManager.ColorRole.SECONDARY_SURFACE).getRGB(),
                         highlightGroup.getBackground().getRGB(),
                         "Statistics highlight chrome must follow the active secondary surface");
        });
    }

    private static Group findGroupWithPrefix(Control root, String keyPrefix)
    {
        if (root instanceof Group) {
            String key = I18n.getKey(root);
            if (key != null && key.startsWith(keyPrefix))
                return (Group)root;
        }
        return findDescendant(root, Group.class, keyPrefix);
    }

    private static <T extends Control> T findDescendant(Control root, Class<T> type)
    {
        return findDescendant(root, type, null);
    }

    private static <T extends Control> T findDescendant(Control root, Class<T> type, String keyPrefix)
    {
        if (root == null || root.isDisposed())
            return null;
        if (type.isInstance(root) && (keyPrefix == null || hasKeyPrefix(root, keyPrefix)))
            return type.cast(root);
        if (!(root instanceof Composite))
            return null;
        for (Control child : ((Composite)root).getChildren()) {
            T match = findDescendant(child, type, keyPrefix);
            if (match != null)
                return match;
        }
        return null;
    }

    private static boolean hasKeyPrefix(Control control, String keyPrefix)
    {
        String key = I18n.getKey(control);
        return key != null && key.startsWith(keyPrefix);
    }

    private static <T extends Control> T[] findDescendants(Control root, Class<T> type)
    {
        java.util.List<T> matches = new java.util.ArrayList<>();
        collectDescendants(root, type, matches);
        @SuppressWarnings("unchecked")
        T[] result = (T[])java.lang.reflect.Array.newInstance(type, matches.size());
        return matches.toArray(result);
    }

    private static <T extends Control> void collectDescendants(Control root, Class<T> type,
                                                                java.util.List<T> matches)
    {
        if (root == null || root.isDisposed())
            return;
        if (type.isInstance(root))
            matches.add(type.cast(root));
        if (root instanceof Composite) {
            for (Control child : ((Composite)root).getChildren())
                collectDescendants(child, type, matches);
        }
    }

    private static void editCell(SWTBotNatTable table, String newValue)
    {
        display.get().syncExec(() -> {
            table.doubleclick(EDIT_COLUMN, EDIT_ROW);
            table.widget.getActiveCellEditor().setEditorValue(newValue);
            table.widget.getActiveCellEditor().commit(
                SelectionLayer.MoveDirectionEnum.NONE, true, true);
        });
    }

    private static void assertDirty()
    {
        final boolean[] dirty = new boolean[1];
        display.get().syncExec(() -> {
            HObject selected = hdfView.get().getTreeView().getCurrentObject();
            DataView view = hdfView.get().getDataView(selected);
            assertTrue(view instanceof TableView, "Data Content must be backed by the current TableView");
            dirty[0] = ((TableView)view).getSearchSnapshot() != null;
        });
        assertTrue(dirty[0], "Editing a cell must create an in-memory dirty state");
    }

    private static void waitForStatistics(SWTBotTable resultTable)
    {
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test()
            {
                try {
                    return !resultTable.cell(0, 1).trim().isEmpty();
                }
                catch (RuntimeException ex) {
                    return false;
                }
            }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for Dataset statistics to finish calculating";
            }
        });
    }

    private static State captureState(SWTBotNatTable table, int column, int row)
    {
        String[] cellValue = new String[1];
        display.get().syncExec(() -> cellValue[0] = table.getCellDataValueByPosition(column, row));

        AtomicReference<State> state = new AtomicReference<>();
        display.get().syncExec(() -> {
            HObject selectedObject = hdfView.get().getTreeView().getCurrentObject();
            assertNotNull(selectedObject, "Tree selection must remain present");
            DataView dataView = hdfView.get().getDataView(selectedObject);
            assertTrue(dataView instanceof TableView, "Data Content must remain a TableView");
            TableView tableView = (TableView)dataView;
            HObject dataObject = tableView.getDataObject();
            assertTrue(dataObject instanceof Dataset, "Data Content must remain a numeric Dataset");
            Dataset dataset = (Dataset)dataObject;
            DatasetStatisticsEngine.Kind highlightKind =
                ((DatasetStatisticsDialog.Host)tableView).getStatisticsHighlightKind();
            state.set(new State(
                selectedObject,
                dataObject,
                tableView,
                selectedObject.getFullName(),
                dataObject.getFullName(),
                frameOf(dataset),
                cellValue[0],
                tableView.getSearchSnapshot() != null,
                highlightKind));
        });
        return state.get();
    }

    private static String currentFrame()
    {
        AtomicReference<String> frame = new AtomicReference<>();
        display.get().syncExec(() -> {
            HObject selectedObject = hdfView.get().getTreeView().getCurrentObject();
            DataView dataView = hdfView.get().getDataView(selectedObject);
            assertTrue(dataView instanceof TableView, "Current Data Content must be a TableView");
            frame.set(frameOf((Dataset)((TableView)dataView).getDataObject()));
        });
        return frame.get();
    }

    private static String frameOf(Dataset dataset)
    {
        return "start=" + Arrays.toString(dataset.getStartDims()) +
               ";selected=" + Arrays.toString(dataset.getSelectedDims()) +
               ";stride=" + Arrays.toString(dataset.getStride()) +
               ";index=" + Arrays.toString(dataset.getSelectedIndex());
    }

    private static void refreshSystemTheme(boolean dark)
    {
        display.get().syncExec(() -> {
            darkTheme.set(dark);
            Listener listener = settingsListener.get();
            assertNotNull(listener, "ThemeManager must register its SWT.Settings listener");
            listener.handleEvent(new Event());
        });
        assertEquals(dark, themeManager.get().isDark(),
                     "ThemeManager must refresh through its Settings listener");
    }

    private static void selectThemeMode(ThemeManager.ThemeMode mode)
    {
        SWTBotShell applicationShell = new SWTBotShell(mainShell.get());
        applicationShell.activate();

        String themeKey = "menu.tools.theme." + mode.getPropertyValue();
        SWTBotMenu themeMenu = applicationShell.bot().menu().menu(text("menu.tools"))
            .menu(text("menu.tools.theme"));
        SWTBotMenu selectedItem = themeMenu.menu(text(themeKey));
        countingProperties.get().resetSaveCount();
        selectedItem.click();

        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test()
            {
                return themeManager.get().getThemeMode() == mode;
            }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for HDFView theme mode to become " + mode;
            }
        });
        assertTrue(selectedItem.isChecked(), "The selected theme radio item must remain checked");
        assertEquals(1, countingProperties.get().getSaveCount(),
                     "One theme radio selection must persist exactly once, not also run the deselected item");
    }

    private static void selectLanguage(I18n.Language language)
    {
        SWTBotShell applicationShell = new SWTBotShell(mainShell.get());
        applicationShell.activate();

        String languageKey = language == I18n.Language.ENGLISH
            ? "menu.tools.language.english"
            : "menu.tools.language.simplifiedChinese";
        SWTBotMenu languageMenu = applicationShell.bot().menu().menu(text("menu.tools"))
            .menu(text("menu.tools.language"));
        SWTBotMenu selectedItem = languageMenu.menu(text(languageKey));
        countingProperties.get().resetSaveCount();
        selectedItem.click();

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
        assertTrue(selectedItem.isChecked(), "The selected language radio item must remain checked");
        assertEquals(1, countingProperties.get().getSaveCount(),
                     "One language radio selection must persist exactly once, not also run the deselected item");
    }

    private static String persistedTheme() throws Exception
    {
        return persistedProperty(ViewProperties.THEME_PROPERTY);
    }

    private static String persistedProperty(String propertyName) throws Exception
    {
        String propertyFile = ViewProperties.getPropertyFile();
        assertNotNull(propertyFile, "HDFView must have a user property file");

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(Path.of(propertyFile))) {
            properties.load(input);
        }
        return properties.getProperty(propertyName);
    }

    private static void installSaveCountingProperties(HDFView application,
                                                       String rootDir,
                                                       String startDir) throws Exception
    {
        CountingViewProperties properties = new CountingViewProperties(rootDir, startDir);
        properties.load();

        Field propertiesField = HDFView.class.getDeclaredField("props");
        propertiesField.setAccessible(true);
        propertiesField.set(application, properties);
        countingProperties.set(properties);
    }

    private static Shell[] openShells()
    {
        AtomicReference<Shell[]> shells = new AtomicReference<>();
        display.get().syncExec(() -> shells.set(display.get().getShells().clone()));
        return shells.get();
    }

    private static void assertShellsUnchanged(Shell[] before, Shell statisticsShell, String transition)
    {
        Shell[] after = openShells();
        assertEquals(before.length, after.length, transition + " must not create or dispose a Shell");
        for (int i = 0; i < before.length; i++)
            assertSame(before[i], after[i], transition + " must preserve Shell identity at index " + i);
        assertFalse(mainShell.get().isDisposed(), transition + " must preserve the main Shell");
        assertFalse(statisticsShell.isDisposed(), transition + " must preserve the statistics dialog Shell");
    }

    private static void assertStateUnchanged(State expected, State actual, String transition)
    {
        assertSame(expected.treeSelection, actual.treeSelection,
                   transition + " must preserve the Tree selected object");
        assertSame(expected.dataObject, actual.dataObject,
                   transition + " must not reconstruct the current Dataset");
        assertSame(expected.tableView, actual.tableView,
                   transition + " must not recreate Data Content");
        assertEquals(expected.treePath, actual.treePath,
                     transition + " must preserve the Tree selected path");
        assertEquals(expected.datasetPath, actual.datasetPath,
                     transition + " must preserve the Data Content Dataset");
        assertEquals(expected.frame, actual.frame,
                     transition + " must preserve the current frame");
        assertEquals(expected.cellValue, actual.cellValue,
                     transition + " must preserve the in-memory edited cell value");
        assertTrue(actual.dirty, transition + " must preserve the unsaved dirty edit");
        assertEquals(expected.highlightKind, actual.highlightKind,
                     transition + " must preserve the statistics highlight rule");
    }

    private static void closeFixtureWithoutSaving(File hdfFile)
    {
        if (hdfFile == null || bot == null || mainShell.get() == null || mainShell.get().isDisposed())
            return;

        try {
            SWTBotShell applicationShell = new SWTBotShell(mainShell.get());
            applicationShell.activate();
            applicationShell.bot().menu().menu(text("menu.file")).menu(text("menu.file.close")).click();

            bot.waitUntil(new DefaultCondition() {
                @Override
                public boolean test()
                {
                    return findChangesDialog() != null || bot.tree().rowCount() == 0;
                }

                @Override
                public String getFailureMessage()
                {
                    return "Timed out waiting for the edited fixture close confirmation";
                }
            });

            SWTBotShell changes = findChangesDialog();
            if (changes != null && changes.isOpen())
                changes.bot().button(text("button.no")).click();
            bot.waitUntil(new DefaultCondition() {
                @Override
                public boolean test() { return bot.tree().rowCount() == 0; }

                @Override
                public String getFailureMessage()
                {
                    return "Timed out waiting for the edited fixture to close without saving";
                }
            });
        }
        finally {
            fixtureAccess.resetCount();
        }
    }

    private static SWTBotShell findChangesDialog()
    {
        String title = text("message.changesDetected.title");
        AtomicReference<Shell> found = new AtomicReference<>();
        Display uiDisplay = display.get();
        if (uiDisplay == null || uiDisplay.isDisposed())
            return null;
        uiDisplay.syncExec(() -> {
            for (Shell candidate : uiDisplay.getShells()) {
                if (!candidate.isDisposed() && candidate.getText().contains(title)) {
                    found.set(candidate);
                    break;
                }
            }
        });
        return found.get() == null ? null : new SWTBotShell(found.get());
    }

    private static Path fixturePath()
    {
        String workDir = System.getProperty("hdfview.workdir");
        if (workDir == null)
            workDir = System.getProperty("user.dir");
        return Path.of(workDir, EDIT_FIXTURE);
    }

    private static String text(String key)
    {
        return I18n.text(key);
    }

    private static final class FixtureAccess extends AbstractWindowTest {
        void install(SWTBot newBot, Shell newShell)
        {
            bot = newBot;
            shell = newShell;
        }

        File openReadWrite(String name)
        {
            return openFile(name, FILE_MODE.READ_WRITE);
        }

        SWTBotMenu tableMenuFor(SWTBotShell tableShell)
        {
            return tableMenu(tableShell);
        }

        void resetCount()
        {
            resetOpenFileCount();
        }
    }

    private static final class CountingViewProperties extends ViewProperties {
        private final AtomicInteger saveCount = new AtomicInteger();

        CountingViewProperties(String rootDir, String startDir)
        {
            super(rootDir, startDir);
        }

        @Override
        public void save() throws IOException
        {
            saveCount.incrementAndGet();
            super.save();
        }

        void resetSaveCount()
        {
            saveCount.set(0);
        }

        int getSaveCount()
        {
            return saveCount.get();
        }
    }

    private static final class State {
        private final HObject treeSelection;
        private final HObject dataObject;
        private final TableView tableView;
        private final String treePath;
        private final String datasetPath;
        private final String frame;
        private final String cellValue;
        private final boolean dirty;
        private final DatasetStatisticsEngine.Kind highlightKind;

        State(HObject treeSelection, HObject dataObject, TableView tableView,
              String treePath, String datasetPath, String frame, String cellValue,
              boolean dirty, DatasetStatisticsEngine.Kind highlightKind)
        {
            this.treeSelection = treeSelection;
            this.dataObject = dataObject;
            this.tableView = tableView;
            this.treePath = treePath;
            this.datasetPath = datasetPath;
            this.frame = frame;
            this.cellValue = cellValue;
            this.dirty = dirty;
            this.highlightKind = highlightKind;
        }
    }
}
