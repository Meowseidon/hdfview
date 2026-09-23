/****************************************************************************
 * Copyright by The HDF Group.                                               *
 * All rights reserved.                                                      *
 ****************************************************************************/

package hdf.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** SWT behavior tests for the display-scoped system-theme manager. */
class ThemeManagerTest {
    private Display display;
    private ThemeManager manager;
    private org.eclipse.swt.widgets.Listener settingsListener;

    @BeforeEach
    void setUp()
    {
        display = new Display();
    }

    @AfterEach
    void tearDown()
    {
        if (manager != null)
            manager.dispose();
        if (display != null && !display.isDisposed())
            display.dispose();
    }

    @Test
    void startsWithTheDetectedLightThemeAndCachesSemanticColors()
    {
        manager = new ThemeManager(display, () -> false, false);

        assertEquals(ThemeManager.Theme.LIGHT, manager.getTheme());
        assertFalse(manager.isDark());
        assertEquals(new org.eclipse.swt.graphics.RGB(255, 255, 255),
                     manager.color(ThemeManager.ColorRole.TABLE_BODY_BACKGROUND).getRGB());
        assertNotEquals(manager.color(ThemeManager.ColorRole.TABLE_BODY_BACKGROUND),
                        manager.color(ThemeManager.ColorRole.TABLE_HEADER_BACKGROUND));
    }

    @Test
    void lightPaletteKeepsSystemAndLegacyLightColors()
    {
        manager = new ThemeManager(display, () -> false, false);

        assertEquals(display.getSystemColor(SWT.COLOR_WIDGET_FOREGROUND).getRGB(),
                     manager.color(ThemeManager.ColorRole.FOREGROUND).getRGB());
        assertEquals(display.getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW).getRGB(),
                     manager.color(ThemeManager.ColorRole.SECONDARY_SURFACE).getRGB());
        assertEquals(new RGB(255, 255, 240),
                     manager.color(ThemeManager.ColorRole.INPUT_BACKGROUND).getRGB());
        assertEquals(display.getSystemColor(SWT.COLOR_GRAY).getRGB(),
                     manager.color(ThemeManager.ColorRole.READ_ONLY_BACKGROUND).getRGB());
        assertEquals(display.getSystemColor(SWT.COLOR_YELLOW).getRGB(),
                     manager.color(ThemeManager.ColorRole.STATISTICS_HIGHLIGHT_BACKGROUND).getRGB());
    }

    @Test
    void startsWithTheDetectedDarkTheme()
    {
        manager = new ThemeManager(display, () -> true, false);

        assertEquals(ThemeManager.Theme.DARK, manager.getTheme());
        assertTrue(manager.isDark());
        assertEquals(new org.eclipse.swt.graphics.RGB(30, 31, 34),
                     manager.color(ThemeManager.ColorRole.TABLE_BODY_BACKGROUND).getRGB());
    }

    @Test
    void displayScopedManagerUsesSwtSystemDetection()
    {
        manager = ThemeManager.forDisplay(display);

        assertEquals(Display.isSystemDarkTheme(), manager.isDark());
        assertEquals(ThemeManager.ThemeMode.SYSTEM, manager.getThemeMode());
        assertSame(manager, ThemeManager.forDisplay(display));
    }

    @Test
    void missingAndUnknownThemePropertiesFallBackToSystem()
    {
        assertEquals(ThemeManager.ThemeMode.SYSTEM,
                     ThemeManager.ThemeMode.fromProperty(null));
        assertEquals(ThemeManager.ThemeMode.SYSTEM,
                     ThemeManager.ThemeMode.fromProperty(""));
        assertEquals(ThemeManager.ThemeMode.SYSTEM,
                     ThemeManager.ThemeMode.fromProperty("sepia"));
        assertEquals(ThemeManager.ThemeMode.LIGHT,
                     ThemeManager.ThemeMode.fromProperty(" LIGHT "));
        assertEquals(ThemeManager.ThemeMode.DARK,
                     ThemeManager.ThemeMode.fromProperty("DaRk"));
    }

    @Test
    void forcedThemeModesIgnoreSettingsAndKeepTheirPalette()
    {
        AtomicBoolean dark = new AtomicBoolean(false);
        manager = listeningManager(dark::get);

        manager.setThemeMode(ThemeManager.ThemeMode.LIGHT);
        assertEquals(ThemeManager.ThemeMode.LIGHT, manager.getThemeMode());
        assertEquals(ThemeManager.Theme.LIGHT, manager.getTheme());

        manager.setThemeMode(ThemeManager.ThemeMode.DARK);
        Color forcedDarkColor = manager.color(ThemeManager.ColorRole.SURFACE);
        assertEquals(ThemeManager.ThemeMode.DARK, manager.getThemeMode());
        assertEquals(ThemeManager.Theme.DARK, manager.getTheme());

        dark.set(false);
        fireSettingsEvent();

        assertEquals(ThemeManager.ThemeMode.DARK, manager.getThemeMode());
        assertEquals(ThemeManager.Theme.DARK, manager.getTheme());
        assertFalse(forcedDarkColor.isDisposed(),
                    "SWT.Settings must not replace a forced palette");
    }

    @Test
    void switchingBackToSystemResumesSettingsThemeChanges()
    {
        AtomicBoolean dark = new AtomicBoolean(false);
        manager = listeningManager(dark::get);

        manager.setThemeMode(ThemeManager.ThemeMode.DARK);
        dark.set(true);
        fireSettingsEvent();
        assertEquals(ThemeManager.Theme.DARK, manager.getTheme());

        manager.setThemeMode(ThemeManager.ThemeMode.SYSTEM);
        assertEquals(ThemeManager.ThemeMode.SYSTEM, manager.getThemeMode());
        assertEquals(ThemeManager.Theme.DARK, manager.getTheme());

        dark.set(false);
        fireSettingsEvent();
        assertEquals(ThemeManager.Theme.LIGHT, manager.getTheme());
        assertEquals(ThemeManager.ThemeMode.SYSTEM, manager.getThemeMode());
    }

    @Test
    void applyToOnlyChangesShellNativeThemePreference()
    {
        manager = new ThemeManager(display, () -> true, false);
        Shell shell = new Shell(display);
        Composite page = new Composite(shell, SWT.NONE);
        Color customBackground = new Color(display, new RGB(17, 19, 23));
        Color customForeground = new Color(display, new RGB(231, 137, 61));
        page.setBackground(customBackground);
        page.setForeground(customForeground);

        manager.applyTo(shell);

        assertEquals(customBackground.getRGB(), page.getBackground().getRGB());
        assertEquals(customForeground.getRGB(), page.getForeground().getRGB());

        shell.dispose();
        customBackground.dispose();
        customForeground.dispose();
    }

    @Test
    void doesNotThemeOrdinaryControlsWhenTheirShellIsShown()
    {
        manager = new ThemeManager(display, () -> true, true);
        Shell shell = new Shell(display);
        Composite page = new Composite(shell, SWT.NONE);
        Label label = new Label(page, SWT.NONE);
        Text input = new Text(page, SWT.BORDER);
        Button button = new Button(page, SWT.PUSH);
        Color customBackground = new Color(display, new RGB(13, 27, 41));
        Color customForeground = new Color(display, new RGB(221, 161, 79));
        page.setBackground(customBackground);
        label.setForeground(customForeground);
        input.setBackground(customBackground);
        button.setForeground(customForeground);

        shell.open();

        assertEquals(customBackground.getRGB(), page.getBackground().getRGB());
        assertEquals(customForeground.getRGB(), label.getForeground().getRGB());
        assertEquals(customBackground.getRGB(), input.getBackground().getRGB());
        assertEquals(customForeground.getRGB(), button.getForeground().getRGB());

        shell.dispose();
        customBackground.dispose();
        customForeground.dispose();
    }

    @Test
    void settingsEventChangesLightDarkLightWithoutReplacingShells()
    {
        AtomicBoolean dark = new AtomicBoolean(false);
        manager = listeningManager(dark::get);
        Shell shell = new Shell(display);
        shell.setText("stable shell");
        shell.setSize(320, 240);
        ThemeManager.Registration binding = manager.bindBackground(
            shell, ThemeManager.ColorRole.SURFACE);
        Color lightColor = manager.color(ThemeManager.ColorRole.SURFACE);

        dark.set(true);
        fireSettingsEvent();
        assertEquals(ThemeManager.Theme.DARK, manager.getTheme());
        assertTrue(lightColor.isDisposed());
        assertSame(shell, display.getShells()[0]);
        assertEquals("stable shell", shell.getText());
        assertEquals(manager.color(ThemeManager.ColorRole.SURFACE).getRGB(), shell.getBackground().getRGB());

        Color darkColor = manager.color(ThemeManager.ColorRole.SURFACE);
        dark.set(false);
        fireSettingsEvent();
        assertEquals(ThemeManager.Theme.LIGHT, manager.getTheme());
        assertTrue(darkColor.isDisposed());
        assertSame(shell, display.getShells()[0]);
        assertEquals(320, shell.getSize().x);
        assertEquals(240, shell.getSize().y);
        assertEquals(manager.color(ThemeManager.ColorRole.SURFACE).getRGB(), shell.getBackground().getRGB());

        binding.dispose();
        shell.dispose();
    }

    @Test
    void disposesEveryReplacedPaletteAndTheFinalPalette()
    {
        AtomicBoolean dark = new AtomicBoolean(false);
        manager = listeningManager(dark::get);
        List<Color> colors = new ArrayList<>();
        colors.add(manager.color(ThemeManager.ColorRole.WINDOW_BACKGROUND));

        for (int i = 0; i < 4; i++) {
            dark.set(!dark.get());
            fireSettingsEvent();
            colors.add(manager.color(ThemeManager.ColorRole.WINDOW_BACKGROUND));
            assertTrue(colors.get(i).isDisposed(), "previous palette must be disposed");
        }

        manager.dispose();
        assertTrue(colors.get(colors.size() - 1).isDisposed());
    }

    @Test
    void keepsStatisticsHighlightReadableInBothThemes()
    {
        for (boolean dark : new boolean[] {false, true}) {
            if (manager != null)
                manager.dispose();
            manager = new ThemeManager(display, () -> dark, false);
            Color background = manager.color(ThemeManager.ColorRole.STATISTICS_HIGHLIGHT_BACKGROUND);
            Color foreground = manager.color(ThemeManager.ColorRole.STATISTICS_HIGHLIGHT_FOREGROUND);
            int distance = Math.abs(background.getRGB().red - foreground.getRGB().red)
                + Math.abs(background.getRGB().green - foreground.getRGB().green)
                + Math.abs(background.getRGB().blue - foreground.getRGB().blue);
            assertTrue(distance > 150, "statistics highlight must have visible contrast");
        }
    }

    private ThemeManager listeningManager(BooleanSupplier detector)
    {
        return new ThemeManager(display, detector, true,
                                listener -> settingsListener = listener,
                                listener -> {
                                    if (settingsListener == listener)
                                        settingsListener = null;
                                });
    }

    /** SWT 3.134 has no public Display.notifyListeners; dispatch the registered public listener seam. */
    private void fireSettingsEvent()
    {
        assertTrue(settingsListener != null, "ThemeManager did not register an SWT.Settings listener");
        settingsListener.handleEvent(new Event());
    }
}
