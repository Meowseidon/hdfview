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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ResourceBundle;

import hdf.view.ViewProperties;

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
}
