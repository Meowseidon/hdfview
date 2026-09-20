package uitest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import hdf.view.i18n.I18n;

import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCombo;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swtbot.nebula.nattable.finder.widgets.SWTBotNatTable;
import org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** UI coverage for the unified Dataset statistics dialog. */
@Tag("ui")
@Tag("integration")
public class TestHDFViewDatasetStatistics extends AbstractWindowTest {
    @Test
    public void dataContentToolbarOpensStatisticsAndRefreshesLanguage()
    {
        selectLanguage(I18n.Language.ENGLISH);
        File hdfFile = openFile("tscalarintsize.h5", FILE_MODE.READ_ONLY);
        SWTBotShell statisticsShell = null;
        try {
            SWTBotTreeItem dataset = bot.tree().getTreeItem(hdfFile.getName()).getNode("DS08BITS");
            dataset.click();

            SWTBotShell mainShell = new SWTBotShell(shell);
            assertEquals(ui("table.statistics"),
                         mainShell.bot().toolbarButtonWithTooltip(ui("table.statistics.tooltip")).getText());
            mainShell.bot().toolbarButtonWithTooltip(ui("table.statistics.tooltip")).click();
            statisticsShell = bot.shell(ui("statistics.title"));
            waitForValue(statisticsShell.bot().table(), 0, 1);

            switchLanguage(I18n.Language.SIMPLIFIED_CHINESE);
            SWTBotShell chineseMainShell = new SWTBotShell(shell);
            assertEquals(ui("table.statistics"),
                         chineseMainShell.bot().toolbarButtonWithTooltip(ui("table.statistics.tooltip")).getText());

            switchLanguage(I18n.Language.ENGLISH);
            SWTBotShell englishMainShell = new SWTBotShell(shell);
            assertEquals(ui("table.statistics"),
                         englishMainShell.bot().toolbarButtonWithTooltip(ui("table.statistics.tooltip")).getText());
        }
        finally {
            if (statisticsShell != null && statisticsShell.isOpen())
                statisticsShell.close();
            closeFile(hdfFile, false);
        }
    }

    @Test
    public void embeddedNatTableContextMenuReachesExistingTableStatisticsAction()
    {
        selectLanguage(I18n.Language.ENGLISH);
        File hdfFile = openFile("tscalarintsize.h5", FILE_MODE.READ_ONLY);
        SWTBotShell statisticsShell = null;
        try {
            SWTBotTreeItem dataset = bot.tree().getTreeItem(hdfFile.getName()).getNode("DS08BITS");
            dataset.click();

            SWTBotShell mainShell = new SWTBotShell(shell);
            SWTBotNatTable table = new SWTBotNatTable(
                mainShell.bot().widget(WidgetMatcherFactory.widgetOfType(NatTable.class)));
            table.contextMenu(1, 1).menu(ui("table")).menu(ui("table.showStatistics")).click();

            statisticsShell = bot.shell(ui("statistics.title"));
            waitForValue(statisticsShell.bot().table(), 0, 1);
            assertTrue(statisticsShell.isOpen(), "NatTable context menu must open Dataset statistics");
        }
        finally {
            if (statisticsShell != null && statisticsShell.isOpen())
                statisticsShell.close();
            closeFile(hdfFile, false);
        }
    }

    @Test
    public void currentAndEntireDatasetStatisticsAreLocalizedAndCancelable()
    {
        selectLanguage(I18n.Language.ENGLISH);
        File hdfFile = openFile("tscalarintsize.h5", FILE_MODE.READ_ONLY);
        SWTBotShell statisticsShell = null;
        try {
            SWTBotTreeItem dataset = bot.tree().getTreeItem(hdfFile.getName()).getNode("DS08BITS");
            dataset.click();

            SWTBotShell mainShell = new SWTBotShell(shell);
            tableMenu(mainShell).menu(ui("table.showStatistics")).click();
            statisticsShell = bot.shell(ui("statistics.title"));
            SWTBotTable table = statisticsShell.bot().table();
            waitForValue(table, 0, 1);
            assertEquals(ui("statistics.total"), table.cell(0, 0));
            assertEquals(ui("statistics.nonZero"), table.cell(2, 0));
            assertEquals(ui("statistics.positive"), table.cell(3, 0));

            SWTBotCombo scope = statisticsShell.bot().comboBox(0);
            scope.setSelection(ui("statistics.scope.entireDataset"));
            statisticsShell.bot().button(ui("statistics.calculate")).click();
            waitForValue(table, 0, 1);

            switchLanguage(I18n.Language.SIMPLIFIED_CHINESE);
            SWTBotShell chineseShell = bot.shell(ui("statistics.title"));
            assertEquals(ui("statistics.total"), chineseShell.bot().table().cell(0, 0));
            assertEquals(ui("statistics.scope.entireDataset"), chineseShell.bot().comboBox(0).getText());

            switchLanguage(I18n.Language.ENGLISH);
            SWTBotShell englishShell = bot.shell(ui("statistics.title"));
            assertEquals(ui("statistics.total"), englishShell.bot().table().cell(0, 0));
        }
        finally {
            if (statisticsShell != null && statisticsShell.isOpen())
                statisticsShell.close();
            closeFile(hdfFile, false);
        }
    }

    @Test
    public void statisticsConditionsHighlightCurrentPageAndClearWithoutChangingValues()
    {
        selectLanguage(I18n.Language.ENGLISH);
        File hdfFile = openFile("tintsize.h5", FILE_MODE.READ_ONLY);
        SWTBotShell statisticsShell = null;
        try {
            SWTBotTreeItem fileItem = bot.tree().getTreeItem(hdfFile.getName());
            SWTBotTreeItem dataset = fileItem.getNode("DS08BITS");
            dataset.click();

            SWTBotShell mainShell = new SWTBotShell(shell);
            tableMenu(mainShell).menu(ui("table.showStatistics")).click();
            statisticsShell = bot.shell(ui("statistics.title"));
            SWTBotTable resultTable = statisticsShell.bot().table();
            waitForValue(resultTable, 0, 1);

            SWTBotNatTable table = new SWTBotNatTable(
                mainShell.bot().widget(WidgetMatcherFactory.widgetOfType(NatTable.class)));
            int[] zeroCell = findCell(table, "zero");
            int[] nonZeroCell = findCell(table, "non-zero");
            int[] negativeCell = findCell(table, "negative");
            String zeroValue = table.getCellDataValueByPosition(zeroCell[0], zeroCell[1]);
            String nonZeroValue = table.getCellDataValueByPosition(nonZeroCell[0], nonZeroCell[1]);

            statisticsShell.bot().button(ui("statistics.zero")).click();
            waitForHighlight(table, zeroCell, true);
            waitForHighlight(table, nonZeroCell, false);
            assertEquals(I18n.text("statistics.activeHighlight", I18n.text("statistics.zero")),
                         statisticsShell.bot().label(
                             I18n.text("statistics.activeHighlight", I18n.text("statistics.zero"))).getText());

            statisticsShell.bot().button(ui("statistics.nonZero")).click();
            waitForHighlight(table, nonZeroCell, true);
            waitForHighlight(table, zeroCell, false);

            statisticsShell.bot().button(ui("statistics.negative")).click();
            waitForHighlight(table, negativeCell, true);

            statisticsShell.bot().button(ui("statistics.clearHighlight")).click();
            waitForHighlight(table, negativeCell, false);
            assertEquals(zeroValue, table.getCellDataValueByPosition(zeroCell[0], zeroCell[1]));
            assertEquals(nonZeroValue, table.getCellDataValueByPosition(nonZeroCell[0], nonZeroCell[1]));
            assertFalse(table.hasConfigLabel(negativeCell[0], negativeCell[1],
                                             "HDFVIEW_STATISTICS_HIGHLIGHT"));

            switchLanguage(I18n.Language.SIMPLIFIED_CHINESE);
            assertEquals(I18n.text("statistics.activeHighlight.none"),
                         statisticsShell.bot().label(I18n.text("statistics.activeHighlight.none")).getText());
            switchLanguage(I18n.Language.ENGLISH);
        }
        finally {
            if (statisticsShell != null && statisticsShell.isOpen())
                statisticsShell.close();
            closeFile(hdfFile, false);
        }
    }

    @Test
    public void positiveConditionHighlightsFloatingDatasetValues()
    {
        selectLanguage(I18n.Language.ENGLISH);
        File hdfFile = openFile("tfloat8.h5", FILE_MODE.READ_ONLY);
        SWTBotShell statisticsShell = null;
        try {
            SWTBotTreeItem dataset = bot.tree().getTreeItem(hdfFile.getName())
                                      .getNode("DS8BITSE4M3_ALLVALS");
            dataset.click();
            SWTBotShell mainShell = new SWTBotShell(shell);
            tableMenu(mainShell).menu(ui("table.showStatistics")).click();
            statisticsShell = bot.shell(ui("statistics.title"));
            waitForValue(statisticsShell.bot().table(), 0, 1);

            SWTBotNatTable table = new SWTBotNatTable(
                mainShell.bot().widget(WidgetMatcherFactory.widgetOfType(NatTable.class)));
            int[] positiveCell = findCell(table, "positive");
            int[] negativeCell = findCell(table, "negative");
            statisticsShell.bot().button(ui("statistics.positive")).click();
            waitForHighlight(table, positiveCell, true);
            waitForHighlight(table, negativeCell, false);
        }
        finally {
            if (statisticsShell != null && statisticsShell.isOpen())
                statisticsShell.close();
            closeFile(hdfFile, false);
        }
    }

    @Test
    public void switchingDatasetClearsThePreviousPageHighlight()
    {
        selectLanguage(I18n.Language.ENGLISH);
        File hdfFile = openFile("tintsize.h5", FILE_MODE.READ_ONLY);
        SWTBotShell statisticsShell = null;
        try {
            SWTBotTreeItem fileItem = bot.tree().getTreeItem(hdfFile.getName());
            fileItem.getNode("DS08BITS").click();
            SWTBotShell mainShell = new SWTBotShell(shell);
            tableMenu(mainShell).menu(ui("table.showStatistics")).click();
            statisticsShell = bot.shell(ui("statistics.title"));
            waitForValue(statisticsShell.bot().table(), 0, 1);

            SWTBotNatTable firstTable = new SWTBotNatTable(
                mainShell.bot().widget(WidgetMatcherFactory.widgetOfType(NatTable.class)));
            int[] negativeCell = findCell(firstTable, "negative");
            statisticsShell.bot().button(ui("statistics.negative")).click();
            waitForHighlight(firstTable, negativeCell, true);

            statisticsShell.close();
            statisticsShell = null;
            fileItem.getNode("DU08BITS").click();

            final SWTBotNatTable[] switchedTable = new SWTBotNatTable[1];
            bot.waitUntil(new DefaultCondition() {
                @Override
                public boolean test()
                {
                    try {
                        switchedTable[0] = new SWTBotNatTable(
                            mainShell.bot().widget(WidgetMatcherFactory.widgetOfType(NatTable.class)));
                        return !hasStatisticsHighlight(switchedTable[0]);
                    }
                    catch (WidgetNotFoundException ex) {
                        return false;
                    }
                }

                @Override
                public String getFailureMessage()
                {
                    return "Timed out waiting for the Dataset switch to clear the previous highlight";
                }
            });

            mainShell.bot().toolbarButtonWithTooltip(ui("table.statistics.tooltip")).click();
            statisticsShell = bot.shell(ui("statistics.title"));
            waitForValue(statisticsShell.bot().table(), 0, 1);
            assertTrue(statisticsShell.bot().label(0).getText().contains("DU08BITS"),
                       "Statistics toolbar must target the current Dataset after switching");
        }
        finally {
            if (statisticsShell != null && statisticsShell.isOpen())
                statisticsShell.close();
            closeFile(hdfFile, false);
        }
    }

    @Test
    public void activeHighlightIsRecomputedAfterRankThreeFrameChange()
    {
        selectLanguage(I18n.Language.ENGLISH);
        File hdfFile = openFile("tframeselection.h5", FILE_MODE.READ_ONLY);
        SWTBotShell statisticsShell = null;
        try {
            SWTBotTreeItem dataset = bot.tree().getTreeItem(hdfFile.getName()).getNode("test_dataset");
            dataset.click();
            SWTBotShell mainShell = new SWTBotShell(shell);
            tableMenu(mainShell).menu(ui("table.showStatistics")).click();
            statisticsShell = bot.shell(ui("statistics.title"));
            waitForValue(statisticsShell.bot().table(), 0, 1);

            SWTBotNatTable table = new SWTBotNatTable(
                mainShell.bot().widget(WidgetMatcherFactory.widgetOfType(NatTable.class)));
            int[] firstPageCell = findCell(table, "positive");
            String firstPageValue = table.getCellDataValueByPosition(firstPageCell[0], firstPageCell[1]);
            statisticsShell.bot().button(ui("statistics.positive")).click();
            waitForHighlight(table, firstPageCell, true);

            mainShell.activate();
            mainShell.bot().toolbarButtonWithTooltip(ui("table.nextFrame")).click();
            bot.waitUntil(new DefaultCondition() {
                @Override
                public boolean test()
                {
                    return !firstPageValue.equals(
                        table.getCellDataValueByPosition(firstPageCell[0], firstPageCell[1]));
                }

                @Override
                public String getFailureMessage()
                {
                    return "Timed out waiting for the inline Dataset frame to change";
                }
            });

            int[] secondPageCell = findCell(table, "positive");
            waitForHighlight(table, secondPageCell, true);
        }
        finally {
            if (statisticsShell != null && statisticsShell.isOpen())
                statisticsShell.close();
            closeFile(hdfFile, false);
        }
    }

    @Test
    public void activeHighlightRefreshesAfterAnEditedCellIsCommitted() throws Exception
    {
        selectLanguage(I18n.Language.ENGLISH);
        Path fixture = new File(workDir, "statistics-highlight-edit.h5").toPath();
        File source = new File(workDir, "tintsize.h5");
        File hdfFile = null;
        SWTBotShell statisticsShell = null;
        try {
            Files.copy(source.toPath(), fixture, StandardCopyOption.REPLACE_EXISTING);
            hdfFile = openFile(fixture.getFileName().toString(), FILE_MODE.READ_WRITE);

            SWTBotTreeItem dataset = bot.tree().getTreeItem(hdfFile.getName()).getNode("DS08BITS");
            dataset.click();
            SWTBotShell mainShell = new SWTBotShell(shell);
            tableMenu(mainShell).menu(ui("table.showStatistics")).click();
            statisticsShell = bot.shell(ui("statistics.title"));
            waitForValue(statisticsShell.bot().table(), 0, 1);

            SWTBotNatTable table = new SWTBotNatTable(
                mainShell.bot().widget(WidgetMatcherFactory.widgetOfType(NatTable.class)));
            int[] negativeCell = findCell(table, "negative");
            statisticsShell.bot().button(ui("statistics.negative")).click();
            waitForHighlight(table, negativeCell, true);

            Display.getDefault().syncExec(() -> {
                table.doubleclick(negativeCell[0], negativeCell[1]);
                table.widget.getActiveCellEditor().setEditorValue("0");
                table.widget.getActiveCellEditor().commit(
                    org.eclipse.nebula.widgets.nattable.selection.SelectionLayer.MoveDirectionEnum.NONE,
                    true, true);
            });

            assertEquals("0", table.getCellDataValueByPosition(negativeCell[0], negativeCell[1]));
            waitForHighlight(table, negativeCell, false);
            int[] remainingNegativeCell = findCell(table, "negative");
            waitForHighlight(table, remainingNegativeCell, true);

            statisticsShell.close();
            statisticsShell = null;
            mainShell.activate();
            tableMenu(mainShell).menu(ui("table.saveChanges")).click();
        }
        finally {
            if (statisticsShell != null && statisticsShell.isOpen())
                statisticsShell.close();
            if (hdfFile != null)
                closeEditedFixtureWithoutSaving(hdfFile);
            Files.deleteIfExists(fixture);
        }
    }

    private int[] findCell(SWTBotNatTable table, String condition)
    {
        for (int row = 1; row < table.rowCount(); row++) {
            for (int column = 1; column < table.columnCount(); column++) {
                String text = table.getCellDataValueByPosition(column, row);
                try {
                    double value = Double.parseDouble(text.trim());
                    boolean match;
                    switch (condition) {
                    case "zero": match = value == 0.0; break;
                    case "non-zero": match = value != 0.0; break;
                    case "positive": match = value > 0.0; break;
                    case "negative": match = value < 0.0; break;
                    default: throw new IllegalArgumentException("Unknown condition " + condition);
                    }
                    if (match)
                        return new int[] {column, row};
                }
                catch (NumberFormatException ex) {
                    // Ignore row/column values which are not numeric cells.
                }
            }
        }
        throw new AssertionError("No " + condition + " cell was found in the current Dataset page");
    }

    private void waitForHighlight(final SWTBotNatTable table, final int[] cell, final boolean expected)
    {
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test()
            {
                return table.hasConfigLabel(cell[0], cell[1], "HDFVIEW_STATISTICS_HIGHLIGHT") == expected;
            }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for statistics highlight state to become " + expected;
            }
        });
    }

    private boolean hasStatisticsHighlight(SWTBotNatTable table)
    {
        for (int row = 1; row < table.rowCount(); row++) {
            for (int column = 1; column < table.columnCount(); column++) {
                if (table.hasConfigLabel(column, row, "HDFVIEW_STATISTICS_HIGHLIGHT"))
                    return true;
            }
        }
        return false;
    }

    private void closeEditedFixtureWithoutSaving(File hdfFile)
    {
        SWTBotShell alreadyPending = findChangesDialog();
        if (alreadyPending != null) {
            alreadyPending.activate();
            alreadyPending.bot().button(I18n.text("button.no")).click();
            waitForClosedEditedFixture();
            resetOpenFileCount();
            return;
        }

        SWTBotShell mainShell = new SWTBotShell(shell);
        mainShell.activate();
        mainShell.bot().tree().getTreeItem(hdfFile.getName()).click();
        SWTBotShell pendingAfterSelection = waitForOptionalChangesDialog(mainShell);
        if (pendingAfterSelection != null) {
            pendingAfterSelection.activate();
            pendingAfterSelection.bot().button(I18n.text("button.no")).click();
            waitForMainShell(mainShell);
        }
        mainShell.bot().menu().menu(ui("menu.file")).menu(ui("menu.file.close")).click();

        final SWTBotShell[] pendingChanges = new SWTBotShell[1];
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test()
            {
                pendingChanges[0] = findChangesDialog();
                return pendingChanges[0] != null || bot.tree().rowCount() == 0;
            }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for the edited Dataset close confirmation";
            }
        });

        if (pendingChanges[0] != null) {
            pendingChanges[0].activate();
            pendingChanges[0].bot().button(I18n.text("button.no")).click();
        }

        waitForClosedEditedFixture();
        resetOpenFileCount();
    }

    private SWTBotShell waitForOptionalChangesDialog(final SWTBotShell mainShell)
    {
        final SWTBotShell[] pendingChanges = new SWTBotShell[1];
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test()
            {
                pendingChanges[0] = findChangesDialog();
                if (pendingChanges[0] != null)
                    return true;
                try {
                    return mainShell.getText().equals(bot.activeShell().getText());
                }
                catch (WidgetNotFoundException ex) {
                    return false;
                }
            }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for the edited Dataset selection to settle";
            }
        });
        return pendingChanges[0];
    }

    private void waitForMainShell(final SWTBotShell mainShell)
    {
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test()
            {
                try {
                    return mainShell.getText().equals(bot.activeShell().getText());
                }
                catch (WidgetNotFoundException ex) {
                    return false;
                }
            }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for the HDFView main window after discarding the edit";
            }
        });
    }

    private void waitForClosedEditedFixture()
    {
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() { return bot.tree().rowCount() == 0; }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for the edited Dataset to close";
            }
        });
    }

    private SWTBotShell findChangesDialog()
    {
        String title = I18n.text("message.changesDetected.title");
        try {
            SWTBotShell active = bot.activeShell();
            if (active.getText().contains(title))
                return active;
        }
        catch (WidgetNotFoundException ex) {
            // Fall back to enumerating all shells below.
        }

        final Shell[] found = new Shell[1];
        Display.getDefault().syncExec(() -> {
            for (Shell candidate : Display.getDefault().getShells()) {
                if (!candidate.isDisposed() && candidate.getText().contains(title)) {
                    found[0] = candidate;
                    break;
                }
            }
        });
        return found[0] == null ? null : new SWTBotShell(found[0]);
    }

    private void waitForValue(final SWTBotTable table, final int row, final int column)
    {
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test()
            {
                try {
                    return table.cell(row, column) != null && !table.cell(row, column).isEmpty();
                }
                catch (WidgetNotFoundException ex) {
                    return false;
                }
            }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for Dataset statistics result";
            }
        });
    }

    private void selectLanguage(I18n.Language language)
    {
        if (I18n.getLanguage() == language)
            return;
        switchLanguage(language);
    }

    private void switchLanguage(I18n.Language language)
    {
        String key = language == I18n.Language.ENGLISH
            ? "menu.tools.language.english"
            : "menu.tools.language.simplifiedChinese";
        new org.eclipse.swtbot.swt.finder.widgets.SWTBotShell(shell).activate();
        bot.menu().menu(ui("menu.tools"))
            .menu(ui("menu.tools.language"))
            .menu(ui(key)).click();
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() { return I18n.getLanguage() == language; }

            @Override
            public String getFailureMessage()
            {
                return "Timed out waiting for HDFView language to become " + language;
            }
        });
    }
}
