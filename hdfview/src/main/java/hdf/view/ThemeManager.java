/****************************************************************************
 * Copyright by The HDF Group.                                               *
 * All rights reserved.                                                      *
 *                                                                           *
 * This file is part of the HDF Java Products distribution.                  *
 * The full copyright notice, including terms governing use, modification,   *
 * and redistribution, is contained in the COPYING file, which can be found  *
 * at the root of the source code distribution tree,                         *
 * or in https://www.hdfgroup.org/licenses.                                  *
 ****************************************************************************/

package hdf.view;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

/**
 * Owns HDFView's semantic colors and follows the operating-system theme.
 *
 * <p>SWT reports a system-theme change through {@link SWT#Settings}.  The
 * manager deliberately does not poll the Windows registry or keep a second
 * user preference.  It updates the native SWT dark-theme preference for the
 * display and existing shells, then replaces one cached color palette and
 * notifies the controls which use custom colors.</p>
 */
public final class ThemeManager {
    /** Semantic themes supported by the viewer. */
    public enum Theme {
        /** The operating system is using a light theme. */
        LIGHT,
        /** The operating system is using a dark theme. */
        DARK
    }

    /** Semantic colors used by HDFView's custom-painted or explicitly-colored controls. */
    public enum ColorRole {
        WINDOW_BACKGROUND,
        SURFACE,
        SECONDARY_SURFACE,
        FOREGROUND,
        DISABLED_FOREGROUND,
        INPUT_BACKGROUND,
        INPUT_FOREGROUND,
        READ_ONLY_BACKGROUND,
        TABLE_BODY_BACKGROUND,
        TABLE_BODY_FOREGROUND,
        TABLE_HEADER_BACKGROUND,
        TABLE_HEADER_FOREGROUND,
        SELECTION_BACKGROUND,
        SELECTION_FOREGROUND,
        STATISTICS_HIGHLIGHT_BACKGROUND,
        STATISTICS_HIGHLIGHT_FOREGROUND,
        SEPARATOR
    }

    private static final String DISPLAY_DATA_KEY = ThemeManager.class.getName() + ".instance";

    private final Display display;
    private final BooleanSupplier systemThemeDetector;
    private final List<Consumer<ThemeManager>> listeners = new ArrayList<>();
    private final Listener settingsListener = event -> refreshFromSystem();
    private Runnable removeSettingsListener = () -> {};

    private EnumMap<ColorRole, Color> colors;
    private Theme theme;
    private boolean disposed;
    private boolean refreshing;

    /**
     * Return the display-scoped manager, creating it from SWT's public system
     * theme detection API when needed.
     *
     * @param display the SWT display which owns the controls
     * @return the manager associated with {@code display}
     */
    public static ThemeManager forDisplay(Display display)
    {
        Objects.requireNonNull(display, "display");
        Object existing = display.getData(DISPLAY_DATA_KEY);
        if (existing instanceof ThemeManager manager && !manager.disposed)
            return manager;

        ThemeManager manager = new ThemeManager(display, Display::isSystemDarkTheme, true);
        display.setData(DISPLAY_DATA_KEY, manager);
        return manager;
    }

    /**
     * Constructor used by the display-scoped factory and package-level SWT
     * tests.  The detector is injectable so a test can simulate a Settings
     * event without changing the machine-wide Windows preference.
     */
    ThemeManager(Display display, BooleanSupplier systemThemeDetector, boolean listenToSettings)
    {
        this(display, systemThemeDetector, listenToSettings,
             listener -> display.addListener(SWT.Settings, listener),
             listener -> display.removeListener(SWT.Settings, listener));
    }

    /**
     * Package-level constructor with an injectable Settings registration seam
     * for deterministic tests.  Production callers use the constructor above,
     * which registers directly with SWT's public Display API.
     */
    ThemeManager(Display display, BooleanSupplier systemThemeDetector, boolean listenToSettings,
                 Consumer<Listener> addSettingsListener, Consumer<Listener> removeSettingsListener)
    {
        this.display = Objects.requireNonNull(display, "display");
        this.systemThemeDetector = Objects.requireNonNull(systemThemeDetector, "systemThemeDetector");
        Objects.requireNonNull(addSettingsListener, "addSettingsListener");
        Objects.requireNonNull(removeSettingsListener, "removeSettingsListener");
        this.theme = systemThemeDetector.getAsBoolean() ? Theme.DARK : Theme.LIGHT;
        this.colors = createPalette(theme);

        applyNativeThemePreference(theme);
        if (listenToSettings) {
            addSettingsListener.accept(settingsListener);
            this.removeSettingsListener = () -> removeSettingsListener.accept(settingsListener);
        }
        display.disposeExec(this::dispose);
    }

    /** @return the SWT display owned by this manager. */
    public Display getDisplay() { return display; }

    /** @return the current system theme. */
    public Theme getTheme() { return theme; }

    /** @return whether the current system theme is dark. */
    public boolean isDark() { return theme == Theme.DARK; }

    /**
     * Return a cached semantic color.  The returned Color is owned by this
     * manager and must not be disposed by callers.
     *
     * @param role semantic color role
     * @return the cached color for the current theme
     */
    public Color color(ColorRole role)
    {
        if (disposed)
            throw new IllegalStateException("ThemeManager is disposed");
        return colors.get(Objects.requireNonNull(role, "role"));
    }

    /**
     * Re-read SWT's public system-theme value.  This is called by the SWT
     * Settings listener and is also useful to deterministic SWT tests.
     */
    public void refreshFromSystem()
    {
        if (disposed || display.isDisposed() || refreshing)
            return;

        refreshing = true;
        try {
            Theme nextTheme = systemThemeDetector.getAsBoolean() ? Theme.DARK : Theme.LIGHT;
            applyNativeThemePreference(nextTheme);
            if (nextTheme == theme)
                return;

            EnumMap<ColorRole, Color> previousColors = colors;
            colors = createPalette(nextTheme);
            theme = nextTheme;
            try {
                for (Consumer<ThemeManager> listener : new ArrayList<>(listeners))
                    listener.accept(this);
            }
            finally {
                disposePalette(previousColors);
            }
        }
        finally {
            refreshing = false;
        }
    }

    /**
     * Register a listener which is called after the semantic palette has been
     * replaced.  The listener must only touch controls on this display thread.
     *
     * @param listener callback for a theme change
     * @return a registration which removes the callback
     */
    public Registration addListener(Consumer<ThemeManager> listener)
    {
        if (disposed)
            throw new IllegalStateException("ThemeManager is disposed");
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return new Registration(this, listener);
    }

    /** Apply the native SWT preference to an existing shell. */
    public void applyTo(Shell shell)
    {
        Objects.requireNonNull(shell, "shell");
        if (shell.isDisposed())
            return;
        if (shell.getDisplay() != display)
            throw new IllegalArgumentException("Shell belongs to a different Display");

        shell.setDarkThemePreferred(isDark());
    }

    /** Bind a control's explicit background and foreground to semantic roles. */
    public Registration bind(Control control, ColorRole backgroundRole, ColorRole foregroundRole)
    {
        Objects.requireNonNull(control, "control");
        if (backgroundRole == null && foregroundRole == null)
            throw new IllegalArgumentException("At least one color role is required");
        if (control.isDisposed())
            throw new IllegalStateException("Control is disposed");

        applyColors(control, backgroundRole, foregroundRole);
        Registration registration = addListener(manager -> {
            if (!control.isDisposed())
                manager.applyColors(control, backgroundRole, foregroundRole);
        });
        control.addDisposeListener(event -> registration.dispose());
        return registration;
    }

    /** Bind only a control's explicit background to a semantic role. */
    public Registration bindBackground(Control control, ColorRole role)
    {
        return bind(control, role, null);
    }

    /** Bind only a control's explicit foreground to a semantic role. */
    public Registration bindForeground(Control control, ColorRole role)
    {
        return bind(control, null, role);
    }

    /** Dispose listeners and all cached custom colors. */
    public void dispose()
    {
        if (disposed)
            return;

        disposed = true;
        if (!display.isDisposed()) {
            removeSettingsListener.run();
            if (display.getData(DISPLAY_DATA_KEY) == this)
                display.setData(DISPLAY_DATA_KEY, null);
        }
        removeSettingsListener = () -> {};
        listeners.clear();
        disposePalette(colors);
        colors = new EnumMap<>(ColorRole.class);
    }

    private void applyColors(Control control, ColorRole backgroundRole, ColorRole foregroundRole)
    {
        if (backgroundRole != null)
            control.setBackground(color(backgroundRole));
        if (foregroundRole != null)
            control.setForeground(color(foregroundRole));
    }

    private void applyNativeThemePreference(Theme selectedTheme)
    {
        if (display.isDisposed())
            return;

        boolean dark = selectedTheme == Theme.DARK;
        display.setDarkThemePreferred(dark);
        for (Shell shell : display.getShells()) {
            if (!shell.isDisposed()) {
                shell.setDarkThemePreferred(dark);
            }
        }
    }

    private EnumMap<ColorRole, Color> createPalette(Theme selectedTheme)
    {
        EnumMap<ColorRole, Color> palette = new EnumMap<>(ColorRole.class);
        try {
            for (ColorRole role : ColorRole.values())
                palette.put(role, new Color(display, rgb(selectedTheme, role)));
        }
        catch (RuntimeException ex) {
            disposePalette(palette);
            throw ex;
        }
        return palette;
    }

    private RGB rgb(Theme selectedTheme, ColorRole role)
    {
        boolean dark = selectedTheme == Theme.DARK;
        switch (role) {
        case WINDOW_BACKGROUND:
            return dark ? new RGB(30, 31, 34) : systemRGB(SWT.COLOR_WHITE);
        case SURFACE:
            return dark ? new RGB(43, 45, 48) : new RGB(255, 255, 255);
        case SECONDARY_SURFACE:
            return dark ? new RGB(54, 57, 62) : systemRGB(SWT.COLOR_WIDGET_LIGHT_SHADOW);
        case FOREGROUND:
            return dark ? new RGB(232, 234, 237) : systemRGB(SWT.COLOR_WIDGET_FOREGROUND);
        case DISABLED_FOREGROUND:
            return dark ? new RGB(145, 147, 153) : systemRGB(SWT.COLOR_WIDGET_DISABLED_FOREGROUND);
        case INPUT_BACKGROUND:
            return dark ? new RGB(36, 38, 42) : new RGB(255, 255, 240);
        case INPUT_FOREGROUND:
            return dark ? new RGB(232, 234, 237) : systemRGB(SWT.COLOR_WIDGET_FOREGROUND);
        case READ_ONLY_BACKGROUND:
            return dark ? new RGB(54, 57, 62) : systemRGB(SWT.COLOR_GRAY);
        case TABLE_BODY_BACKGROUND:
            return dark ? new RGB(30, 31, 34) : systemRGB(SWT.COLOR_WHITE);
        case TABLE_BODY_FOREGROUND:
            return dark ? new RGB(232, 234, 237) : systemRGB(SWT.COLOR_LIST_FOREGROUND);
        case TABLE_HEADER_BACKGROUND:
            return dark ? new RGB(58, 61, 68) : systemRGB(SWT.COLOR_WIDGET_NORMAL_SHADOW);
        case TABLE_HEADER_FOREGROUND:
            return dark ? new RGB(245, 246, 248) : systemRGB(SWT.COLOR_WIDGET_FOREGROUND);
        case SELECTION_BACKGROUND:
            return dark ? new RGB(60, 110, 170) : systemRGB(SWT.COLOR_LIST_SELECTION);
        case SELECTION_FOREGROUND:
            return dark ? new RGB(255, 255, 255) : systemRGB(SWT.COLOR_LIST_SELECTION_TEXT);
        case STATISTICS_HIGHLIGHT_BACKGROUND:
            return dark ? new RGB(115, 87, 0) : systemRGB(SWT.COLOR_YELLOW);
        case STATISTICS_HIGHLIGHT_FOREGROUND:
            return dark ? new RGB(255, 244, 179) : systemRGB(SWT.COLOR_BLACK);
        case SEPARATOR:
            return dark ? new RGB(93, 96, 103) : systemRGB(SWT.COLOR_WIDGET_LIGHT_SHADOW);
        default:
            throw new IllegalArgumentException("Unknown color role: " + role);
        }
    }

    private RGB systemRGB(int systemColorId)
    {
        return display.getSystemColor(systemColorId).getRGB();
    }

    private void disposePalette(EnumMap<ColorRole, Color> palette)
    {
        if (palette == null)
            return;
        for (Color color : palette.values()) {
            if (color != null && !color.isDisposed())
                color.dispose();
        }
        palette.clear();
    }

    private void removeListener(Consumer<ThemeManager> listener)
    {
        listeners.remove(listener);
    }

    /** Handle used to detach a control or table listener. */
    public static final class Registration implements AutoCloseable {
        private ThemeManager manager;
        private Consumer<ThemeManager> listener;

        private Registration(ThemeManager manager, Consumer<ThemeManager> listener)
        {
            this.manager = manager;
            this.listener = listener;
        }

        /** Detach this registration. */
        public void dispose()
        {
            if (manager == null)
                return;
            manager.removeListener(listener);
            manager = null;
            listener = null;
        }

        @Override
        public void close() { dispose(); }
    }
}
