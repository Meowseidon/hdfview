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

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** SWT behavior tests for the display-scoped system-theme manager. */
class ThemeManagerTest {
    private Display display;
    private ThemeManager manager;

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
        assertSame(manager, ThemeManager.forDisplay(display));
    }

    @Test
    void systemRefreshChangesLightDarkLightWithoutReplacingShells()
    {
        AtomicBoolean dark = new AtomicBoolean(false);
        manager = new ThemeManager(display, dark::get, true);
        Shell shell = new Shell(display);
        shell.setText("stable shell");
        shell.setSize(320, 240);
        ThemeManager.Registration binding = manager.bindBackground(
            shell, ThemeManager.ColorRole.SURFACE);
        Color lightColor = manager.color(ThemeManager.ColorRole.SURFACE);

        dark.set(true);
        manager.refreshFromSystem();
        assertEquals(ThemeManager.Theme.DARK, manager.getTheme());
        assertTrue(lightColor.isDisposed());
        assertSame(shell, display.getShells()[0]);
        assertEquals("stable shell", shell.getText());
        assertEquals(manager.color(ThemeManager.ColorRole.SURFACE).getRGB(), shell.getBackground().getRGB());

        Color darkColor = manager.color(ThemeManager.ColorRole.SURFACE);
        dark.set(false);
        manager.refreshFromSystem();
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
    void themesOrdinaryPageControlsAndRefreshesThemWithoutReplacingTheShell()
    {
        AtomicBoolean dark = new AtomicBoolean(false);
        manager = new ThemeManager(display, dark::get, false);
        Shell shell = new Shell(display);
        Composite page = new Composite(shell, SWT.NONE);
        Label label = new Label(page, SWT.NONE);
        Text input = new Text(page, SWT.BORDER);
        Tree tree = new Tree(page, SWT.BORDER);

        manager.applyTo(shell);

        assertEquals(manager.color(ThemeManager.ColorRole.SURFACE).getRGB(), page.getBackground().getRGB());
        assertEquals(manager.color(ThemeManager.ColorRole.FOREGROUND).getRGB(), label.getForeground().getRGB());
        assertEquals(manager.color(ThemeManager.ColorRole.INPUT_BACKGROUND).getRGB(), input.getBackground().getRGB());
        assertEquals(manager.color(ThemeManager.ColorRole.TABLE_BODY_BACKGROUND).getRGB(),
                     tree.getBackground().getRGB());

        dark.set(true);
        manager.refreshFromSystem();

        assertEquals(ThemeManager.Theme.DARK, manager.getTheme());
        assertEquals(manager.color(ThemeManager.ColorRole.SURFACE).getRGB(), page.getBackground().getRGB());
        assertEquals(manager.color(ThemeManager.ColorRole.INPUT_BACKGROUND).getRGB(), input.getBackground().getRGB());
        assertEquals(manager.color(ThemeManager.ColorRole.TABLE_BODY_BACKGROUND).getRGB(),
                     tree.getBackground().getRGB());

        shell.dispose();
    }

    @Test
    void themesControlsWhenTheirShellIsShown()
    {
        manager = new ThemeManager(display, () -> true, true);
        Shell shell = new Shell(display);
        Composite page = new Composite(shell, SWT.NONE);
        Label label = new Label(page, SWT.NONE);
        Text input = new Text(page, SWT.BORDER);

        shell.open();

        assertEquals(manager.color(ThemeManager.ColorRole.SURFACE).getRGB(), page.getBackground().getRGB());
        assertEquals(manager.color(ThemeManager.ColorRole.FOREGROUND).getRGB(), label.getForeground().getRGB());
        assertEquals(manager.color(ThemeManager.ColorRole.INPUT_BACKGROUND).getRGB(), input.getBackground().getRGB());

        shell.dispose();
    }

    @Test
    void disposesEveryReplacedPaletteAndTheFinalPalette()
    {
        AtomicBoolean dark = new AtomicBoolean(false);
        manager = new ThemeManager(display, dark::get, true);
        List<Color> colors = new ArrayList<>();
        colors.add(manager.color(ThemeManager.ColorRole.WINDOW_BACKGROUND));

        for (int i = 0; i < 4; i++) {
            dark.set(!dark.get());
            manager.refreshFromSystem();
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
}
