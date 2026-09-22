package hdf.view.fileusage;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUsageI18nTest {
    @Test
    void englishAndSimplifiedChineseContainTheFileUsageSurface() {
        ResourceBundle english = ResourceBundle.getBundle(
                "hdf.view.i18n.messages", Locale.ENGLISH, NO_DEFAULT_LOCALE);
        ResourceBundle chinese = ResourceBundle.getBundle(
                "hdf.view.i18n.messages", Locale.SIMPLIFIED_CHINESE, NO_DEFAULT_LOCALE);

        assertEquals("File Usage...", english.getString("button.fileUsage"));
        assertEquals("占用…", chinese.getString("button.fileUsage"));
        assertTrue(english.getString("dialog.fileUsage.action.confirm").contains("corrupt"));
        assertTrue(chinese.getString("dialog.fileUsage.action.confirm").contains("损坏"));
        assertTrue(english.getString("dialog.fileUsage.status.noExternal").contains("No external"));
        assertTrue(chinese.getString("dialog.fileUsage.status.noExternal").contains("未发现外部占用"));
        assertTrue(chinese.getString("dialog.fileUsage.status.noExternal")
                           .contains("Windows 重启管理器（Restart Manager）"));
        assertTrue(english.getString("dialog.fileUsage.status.externalCount")
                           .contains("external usage item(s)"));
        assertTrue(chinese.getString("dialog.fileUsage.status.externalCount")
                           .contains("外部占用项"));
        assertTrue(english.getString("dialog.fileUsage.error.native").contains("Windows error"));
        assertTrue(chinese.getString("dialog.fileUsage.error.native").contains("Windows 错误"));
    }

    private static final ResourceBundle.Control NO_DEFAULT_LOCALE = new ResourceBundle.Control() {
        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) {
            return null;
        }
    };
}
