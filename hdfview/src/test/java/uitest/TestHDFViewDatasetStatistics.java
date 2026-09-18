package uitest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import hdf.view.i18n.I18n;

import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCombo;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** UI coverage for the unified Dataset statistics dialog. */
@Tag("ui")
@Tag("integration")
public class TestHDFViewDatasetStatistics extends AbstractWindowTest {
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
