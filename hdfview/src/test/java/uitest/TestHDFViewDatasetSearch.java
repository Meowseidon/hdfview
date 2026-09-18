/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * Copyright by the Board of Trustees of the University of Illinois.         *
 * All rights reserved.                                                       *
 *****************************************************************************/

package uitest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import hdf.view.i18n.I18n;

import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotText;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTableItem;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTabItem;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** UI regression coverage for the global Dataset content search window. */
@Tag("ui")
@Tag("integration")
public class TestHDFViewDatasetSearch extends AbstractWindowTest {
    private static final String FILE_NAME = "tscalarintsize.h5";

    @Test
    public void datasetNameSearchNavigatesToTheExistingInlineDataPageAndSwitchesLanguage()
    {
        selectLanguage(I18n.Language.ENGLISH);
        File hdfFile = openFile(FILE_NAME, FILE_MODE.READ_ONLY);
        SWTBotShell searchShell = null;

        try {
            bot.menu().menu(ui("menu.tools"))
                .menu(ui("menu.tools.searchDatasetContent")).click();
            searchShell = bot.shell(ui("dialog.datasetSearch.title"));
            searchShell.activate();
            bot.waitUntil(Conditions.shellIsActive(searchShell.getText()));

            SWTBotText query = searchShell.bot().text();
            query.setText("DS08BITS");
            searchShell.bot().button(ui("search.start")).click();

            SWTBotTable resultTable = searchShell.bot().table();
            bot.waitUntil(new DefaultCondition() {
                @Override
                public boolean test() { return resultTable.rowCount() > 0; }

                @Override
                public String getFailureMessage()
                {
                    return "Dataset name search did not produce a result row";
                }
            });

            SWTBotTableItem firstResult = resultTable.getTableItem(0);
            assertEquals("/DS08BITS", firstResult.getText(1),
                         "search results must retain the full Dataset path");
            assertEquals("DS08BITS", firstResult.getText(2),
                         "name matches must retain the matched Dataset name");

            int shellCount = bot.shells().length;
            firstResult.doubleClick();
            assertEquals(shellCount, bot.shells().length,
                         "activating a default search result must not create a standalone TableView Shell");
            SWTBotTabItem dataTab = bot.tabItem(ui("tab.dataContent"));
            assertTrue(dataTab.isActive(),
                       "activating a result must focus the main window Data Content tab");

            selectLanguage(I18n.Language.SIMPLIFIED_CHINESE);
            assertEquals(ui("dialog.datasetSearch.title"), searchShell.getText(),
                         "an existing search window must refresh its title in Chinese");
            assertTrue(searchShell.bot().button(ui("search.cancel")).isEnabled() ||
                           searchShell.bot().button(ui("search.start")).isEnabled(),
                       "the existing search controls must remain usable after language switching");

            selectLanguage(I18n.Language.ENGLISH);
        }
        finally {
            if (searchShell != null && searchShell.isOpen())
                searchShell.close();
            selectLanguage(I18n.Language.ENGLISH);
            closeFile(hdfFile, false);
        }
    }

    @Test
    public void closedFileResultIsReportedAsStale()
    {
        selectLanguage(I18n.Language.ENGLISH);
        File hdfFile = openFile(FILE_NAME, FILE_MODE.READ_ONLY);
        SWTBotShell searchShell = null;
        boolean fileClosed = false;

        try {
            bot.menu().menu(ui("menu.tools"))
                .menu(ui("menu.tools.searchDatasetContent")).click();
            searchShell = bot.shell(ui("dialog.datasetSearch.title"));
            searchShell.activate();
            bot.waitUntil(Conditions.shellIsActive(searchShell.getText()));

            SWTBotText query = searchShell.bot().text();
            query.setText("DS08BITS");
            searchShell.bot().button(ui("search.start")).click();

            SWTBotTable resultTable = searchShell.bot().table();
            bot.waitUntil(new DefaultCondition() {
                @Override
                public boolean test() { return resultTable.rowCount() > 0; }

                @Override
                public String getFailureMessage()
                {
                    return "Dataset name search did not produce a result row";
                }
            });

            closeFile(hdfFile, false);
            fileClosed = true;

            resultTable.getTableItem(0).doubleClick();
            SWTBotShell errorShell = bot.activeShell();
            assertEquals(applicationDialogTitle("dialog.datasetSearch.title"), errorShell.getText(),
                         "a stale result must use the search dialog title");
            assertTrue(errorShell.bot().label(ui("search.staleResult")).isVisible(),
                       "a result from a closed file must be rejected explicitly");
            errorShell.bot().button(ui("button.ok")).click();
        }
        finally {
            if (searchShell != null && searchShell.isOpen())
                searchShell.close();
            selectLanguage(I18n.Language.ENGLISH);
            if (!fileClosed)
                closeFile(hdfFile, false);
        }
    }

    private void selectLanguage(I18n.Language language)
    {
        if (I18n.getLanguage() == language)
            return;

        String key = language == I18n.Language.ENGLISH
            ? "menu.tools.language.english"
            : "menu.tools.language.simplifiedChinese";
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
