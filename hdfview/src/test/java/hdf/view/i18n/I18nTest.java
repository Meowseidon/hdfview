/****************************************************************************
 * Copyright by The HDF Group.                                               *
 * Copyright by the Board of Trustees of the University of Illinois.         *
 * All rights reserved.                                                       *
 *                                                                            *
 * This file is part of the HDF Java Products distribution.                   *
 * The full copyright notice, including terms governing use, modification,   *
 * and redistribution, is contained in the COPYING file, which can be found   *
 * at the root of the source code distribution tree,                          *
 * or in https://www.hdfgroup.org/licenses.                                   *
 * If you do not have access to either file, you may request a copy from      *
 * help@hdfgroup.org.                                                         *
 ****************************************************************************/

package hdf.view.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ResourceBundle;

import hdf.view.ViewProperties;
import hdf.view.search.DatasetSearchException;
import hdf.view.statistics.DatasetStatisticsEngine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Regression coverage for language resolution and user preference persistence. */
class I18nTest {
    private static final String PROPERTY_NAME = "hdfview.propfile";

    @AfterEach
    void restoreDefaultLanguage()
    {
        I18n.setLanguage(I18n.Language.ENGLISH);
    }

    @Test
    void resolvesEnglishAndSimplifiedChineseResources()
    {
        I18n.setLanguage(I18n.Language.ENGLISH);
        String englishTab = I18n.text("tab.dataContent");

        I18n.setLanguage(I18n.Language.SIMPLIFIED_CHINESE);
        String chineseTab = I18n.text("tab.dataContent");

        assertEquals("Data Content", englishTab);
        assertEquals("数据内容", chineseTab);
        assertNotEquals(englishTab, chineseTab);

        I18n.setLanguage(I18n.Language.ENGLISH);
        assertEquals("Changes Detected", I18n.text("message.changesDetected.title"));
        assertEquals("Save", I18n.text("message.save.title"));
        I18n.setLanguage(I18n.Language.SIMPLIFIED_CHINESE);
        assertEquals("检测到更改", I18n.text("message.changesDetected.title"));
        assertEquals("保存", I18n.text("message.save.title"));

        assertEquals(I18n.Language.SIMPLIFIED_CHINESE, I18n.Language.fromProperty("zh_CN"));
        assertEquals(I18n.Language.SIMPLIFIED_CHINESE, I18n.Language.fromProperty("zh"));
        assertEquals(I18n.Language.ENGLISH, I18n.Language.fromProperty("unsupported"));
    }

    @Test
    void resourceBundlesHaveMatchingKeysAndDatatypeDescriptionsStayBilingual()
    {
        ResourceBundle english =
            ResourceBundle.getBundle("hdf.view.i18n.messages", Locale.ENGLISH);
        ResourceBundle chinese =
            ResourceBundle.getBundle("hdf.view.i18n.messages", Locale.SIMPLIFIED_CHINESE);
        assertEquals(english.keySet(), chinese.keySet());

        I18n.setLanguage(I18n.Language.ENGLISH);
        assertEquals("64-bit floating-point", I18n.datatypeDescription("64-bit floating-point"));

        I18n.setLanguage(I18n.Language.SIMPLIFIED_CHINESE);
        assertEquals("64 位浮点数 (64-bit floating-point)",
                     I18n.datatypeDescription("64-bit floating-point"));
        assertEquals("数据集区域引用 (Dataset region reference)",
                     I18n.datatypeDescription("Dataset region reference"));
    }

    @Test
    void restoresLanguageFromTheExistingViewPropertiesFile() throws Exception
    {
        String originalPropertyFile = System.getProperty(PROPERTY_NAME);
        Path propertyFile = Files.createTempFile("hdfview-i18n-", ".properties");

        try {
            System.setProperty(PROPERTY_NAME, propertyFile.toString());

            ViewProperties firstStart = new ViewProperties(propertyFile.getParent().toString(),
                                                            propertyFile.getParent().toString());
            firstStart.load();
            firstStart.setValue(ViewProperties.LANGUAGE_PROPERTY,
                                I18n.Language.SIMPLIFIED_CHINESE.getPropertyValue());
            firstStart.save();

            ViewProperties restarted = new ViewProperties(propertyFile.getParent().toString(),
                                                          propertyFile.getParent().toString());
            restarted.load();
            I18n.initialize(restarted);

            assertEquals(I18n.Language.SIMPLIFIED_CHINESE, I18n.getLanguage());
            assertEquals(I18n.Language.SIMPLIFIED_CHINESE.getPropertyValue(),
                         restarted.getString(ViewProperties.LANGUAGE_PROPERTY));
            assertEquals("数据内容", I18n.text("tab.dataContent"));
        }
        finally {
            if (originalPropertyFile == null)
                System.clearProperty(PROPERTY_NAME);
            else
                System.setProperty(PROPERTY_NAME, originalPropertyFile);
            Files.deleteIfExists(propertyFile);
        }
    }

    @Test
    void newSearchAndStatisticsFailuresUseLocalizedKeysInsteadOfEnglishMessages()
    {
        I18n.setLanguage(I18n.Language.SIMPLIFIED_CHINESE);

        DatasetSearchException searchFailure = DatasetSearchException.localizedWithCause(
            DatasetSearchException.Code.BLOCK_READ_NO_DATA,
            new IllegalStateException("Dataset block read returned no data"));
        assertEquals("数据集块读取未返回数据。",
                     I18n.text(searchFailure.messageKey(), searchFailure.messageArgs()));
        assertFalse(I18n.text(searchFailure.messageKey(), searchFailure.messageArgs())
                        .contains("Dataset block read returned no data"));

        DatasetSearchException genericSearchFailure = DatasetSearchException.from(
            new IllegalStateException("Dataset subset selection is unavailable"));
        assertEquals("无法查找数据集。",
                     I18n.text(genericSearchFailure.messageKey(), genericSearchFailure.messageArgs()));
        assertFalse(I18n.text(genericSearchFailure.messageKey(), genericSearchFailure.messageArgs())
                        .contains("Dataset subset selection is unavailable"));

        DatasetStatisticsEngine.UnsupportedDatatypeException unsupported =
            assertThrows(DatasetStatisticsEngine.UnsupportedDatatypeException.class,
                         () -> DatasetStatisticsEngine.validateDatatype(null,
                                                                        new String[] {"text"}));
        assertEquals("数据集数据类型不可用。",
                     I18n.text(unsupported.messageKey(), unsupported.messageArgs()));
        assertFalse(I18n.text(unsupported.messageKey(), unsupported.messageArgs())
                        .contains("Dataset"));

        DatasetStatisticsEngine.UnsupportedDatatypeException valueFailure =
            assertThrows(DatasetStatisticsEngine.UnsupportedDatatypeException.class,
                         () -> DatasetStatisticsEngine.matches("text",
                                                               DatasetStatisticsEngine.Kind.ZERO));
        assertEquals("统计值不是数值或布尔值：text",
                     I18n.text(valueFailure.messageKey(), valueFailure.messageArgs()));

        DatasetStatisticsEngine.StatisticsException statisticsFailure =
            DatasetStatisticsEngine.StatisticsException.localizedWithCause(
                DatasetStatisticsEngine.ErrorCode.SUBSET_UNAVAILABLE,
                new IllegalStateException("Dataset subset selection is unavailable"));
        assertEquals("数据集子集选择不可用。",
                     I18n.text(statisticsFailure.messageKey(), statisticsFailure.messageArgs()));
        assertFalse(I18n.text(statisticsFailure.messageKey(), statisticsFailure.messageArgs())
                        .contains("Dataset subset selection is unavailable"));
        assertEquals("当前没有可用的数据。", I18n.text("statistics.noData"));
    }
}
