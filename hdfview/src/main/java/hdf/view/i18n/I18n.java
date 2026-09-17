/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * Copyright by the Board of Trustees of the University of Illinois.         *
 * All rights reserved.                                                      *
 *                                                                           *
 * This file is part of the HDF Java Products distribution.                  *
 * The full copyright notice, including terms governing use, modification,   *
 * and redistribution, is contained in the COPYING file, which can be found  *
 * at the root of the source code distribution tree,                         *
 * or in https://www.hdfgroup.org/licenses.                                  *
 * If you do not have access to either file, you may request a copy from     *
 * help@hdfgroup.org.                                                        *
 ****************************************************************************/

package hdf.view.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import hdf.view.ViewProperties;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Widget;

/**
 * Small, SWT-oriented internationalization service used by HDFView.
 *
 * <p>The service deliberately keeps language state separate from application
 * behavior. Controls are associated with stable resource keys through widget
 * data, and a language change reapplies those keys to the existing widget
 * hierarchy. This makes switching in an open window possible without
 * rebuilding the HDF object tree or data views.</p>
 */
public final class I18n {
    /** The ResourceBundle base name. */
    private static final String BUNDLE_BASE_NAME = "hdf.view.i18n.messages";

    /** Widget data key containing the stable resource key. */
    public static final String WIDGET_KEY = I18n.class.getName() + ".key";

    /** Widget data key containing optional MessageFormat arguments. */
    private static final String WIDGET_ARGS = I18n.class.getName() + ".args";

    /** Widget data key containing a tooltip resource key. */
    private static final String TOOLTIP_KEY = I18n.class.getName() + ".tooltip";

    /** Widget data key containing optional tooltip MessageFormat arguments. */
    private static final String TOOLTIP_ARGS = I18n.class.getName() + ".tooltip.args";

    /** Do not let the host JVM's default locale replace the requested UI language. */
    private static final ResourceBundle.Control NO_DEFAULT_LOCALE_CONTROL =
        new ResourceBundle.Control() {
            @Override
            public Locale getFallbackLocale(String baseName, Locale locale)
            {
                return null;
            }
        };

    private static final Object[] NO_ARGS = new Object[0];

    private static Language currentLanguage = Language.ENGLISH;
    private static ResourceBundle bundle = loadBundle(currentLanguage);

    private I18n() {}

    /** Supported HDFView UI languages and their persisted identifiers. */
    public enum Language {
        /** English UI. */
        ENGLISH("en", Locale.ENGLISH),
        /** Simplified Chinese UI. */
        SIMPLIFIED_CHINESE("zh_CN", Locale.SIMPLIFIED_CHINESE);

        private final String propertyValue;
        private final Locale locale;

        Language(String propertyValue, Locale locale)
        {
            this.propertyValue = propertyValue;
            this.locale        = locale;
        }

        /** @return the stable value stored in the user properties file. */
        public String getPropertyValue() { return propertyValue; }

        /** @return the Locale used for loading this language's resources. */
        public Locale getLocale() { return locale; }

        /**
         * Convert a persisted value to a supported language.
         *
         * @param value a persisted language value
         * @return the matching language, or English for missing/unknown values
         */
        public static Language fromProperty(String value)
        {
            if (value != null) {
                for (Language language : values()) {
                    if (language.propertyValue.equalsIgnoreCase(value.trim()))
                        return language;
                }
                if ("zh".equalsIgnoreCase(value.trim()) || "中文".equals(value.trim()))
                    return SIMPLIFIED_CHINESE;
            }
            return ENGLISH;
        }
    }

    /**
     * Initialize the current language from the existing HDFView preferences.
     *
     * @param properties the application's existing user preference store
     */
    public static synchronized void initialize(ViewProperties properties)
    {
        String value = properties == null ? null : properties.getString(ViewProperties.LANGUAGE_PROPERTY);
        setLanguage(Language.fromProperty(value));
    }

    /**
     * Select a language for the current process. Persistence is intentionally
     * handled by the caller's existing {@link ViewProperties} instance.
     *
     * @param language the new language
     */
    public static synchronized void setLanguage(Language language)
    {
        Language selected = language == null ? Language.ENGLISH : language;
        currentLanguage = selected;
        bundle         = loadBundle(selected);
    }

    /** @return the language currently used for newly-created and bound controls. */
    public static synchronized Language getLanguage() { return currentLanguage; }

    /**
     * Resolve a resource key, falling back to English and finally the key
     * itself when a resource entry is missing.
     *
     * @param key resource key
     * @param args optional MessageFormat arguments
     * @return translated text
     */
    public static synchronized String text(String key, Object... args)
    {
        if (key == null)
            return "";

        String value = null;
        try {
            value = bundle.getString(key);
        }
        catch (MissingResourceException ex) {
            try {
                value = loadBundle(Language.ENGLISH).getString(key);
            }
            catch (MissingResourceException ignored) {
                value = key;
            }
        }

        if (args == null || args.length == 0)
            return value;

        return new MessageFormat(value, currentLanguage.locale).format(args);
    }

    /**
     * Bind a widget's visible text to a stable resource key and apply it now.
     *
     * @param widget the SWT widget
     * @param key resource key
     * @param args optional MessageFormat arguments
     */
    public static void bind(Widget widget, String key, Object... args)
    {
        if (widget == null || widget.isDisposed())
            return;

        widget.setData(WIDGET_KEY, key);
        widget.setData(WIDGET_ARGS, args == null ? NO_ARGS : args.clone());
        apply(widget);
    }

    /**
     * Bind a ToolItem tooltip to a stable resource key.
     *
     * @param widget the SWT widget
     * @param key resource key
     * @param args optional MessageFormat arguments
     */
    public static void bindToolTip(Widget widget, String key, Object... args)
    {
        if (widget == null || widget.isDisposed())
            return;

        widget.setData(TOOLTIP_KEY, key);
        widget.setData(TOOLTIP_ARGS, args == null ? NO_ARGS : args.clone());
        apply(widget);
    }

    /**
     * Return the stable resource key attached to a widget.
     *
     * @param widget an SWT widget
     * @return the resource key, or null when the widget is not bound
     */
    public static String getKey(Widget widget)
    {
        if (widget == null || widget.isDisposed())
            return null;

        Object key = widget.getData(WIDGET_KEY);
        return key instanceof String ? (String)key : null;
    }

    /**
     * Refresh bound text and menus below an existing SWT widget.
     *
     * @param widget the root of the widget hierarchy to refresh
     */
    public static void refresh(Widget widget)
    {
        if (widget == null || widget.isDisposed())
            return;

        apply(widget);

        if (widget instanceof Menu) {
            for (MenuItem item : ((Menu)widget).getItems())
                refresh(item);
            return;
        }

        if (widget instanceof MenuItem) {
            Menu submenu = ((MenuItem)widget).getMenu();
            if (submenu != null)
                refresh(submenu);
            return;
        }

        if (widget instanceof TabFolder) {
            for (TabItem item : ((TabFolder)widget).getItems()) {
                refresh(item);
                Control control = item.getControl();
                if (control != null)
                    refresh(control);
            }
        }

        if (widget instanceof ToolBar) {
            for (ToolItem item : ((ToolBar)widget).getItems())
                refresh(item);
        }

        if (widget instanceof Shell) {
            Menu menuBar = ((Shell)widget).getMenuBar();
            if (menuBar != null)
                refresh(menuBar);
        }

        if (widget instanceof Control) {
            Menu contextMenu = ((Control)widget).getMenu();
            if (contextMenu != null)
                refresh(contextMenu);
        }

        if (widget instanceof Composite) {
            for (Control child : ((Composite)widget).getChildren())
                refresh(child);
        }
    }

    /**
     * Refresh all Shells belonging to an HDFView Display. This includes the
     * main window, HDFView-owned dialogs, standalone TableView windows, and
     * context menus attached to their controls.
     *
     * @param display the SWT Display
     */
    public static void refreshDisplay(org.eclipse.swt.widgets.Display display)
    {
        if (display == null || display.isDisposed())
            return;

        for (Shell shell : display.getShells())
            refresh(shell);
    }

    private static void apply(Widget widget)
    {
        String key = getDataString(widget, WIDGET_KEY);
        if (key != null) {
            String value = text(key, getDataArgs(widget, WIDGET_ARGS));

            if (widget instanceof MenuItem)
                ((MenuItem)widget).setText(value);
            else if (widget instanceof TabItem)
                ((TabItem)widget).setText(value);
            else if (widget instanceof Button)
                ((Button)widget).setText(value);
            else if (widget instanceof Label)
                ((Label)widget).setText(value);
            else if (widget instanceof Group)
                ((Group)widget).setText(value);
            else if (widget instanceof Shell)
                ((Shell)widget).setText(value);
            else if (widget instanceof Text)
                ((Text)widget).setText(value);
        }

        String tooltipKey = getDataString(widget, TOOLTIP_KEY);
        if (tooltipKey != null && widget instanceof ToolItem)
            ((ToolItem)widget).setToolTipText(text(tooltipKey, getDataArgs(widget, TOOLTIP_ARGS)));
    }

    private static String getDataString(Widget widget, String key)
    {
        Object value = widget.getData(key);
        return value instanceof String ? (String)value : null;
    }

    private static Object[] getDataArgs(Widget widget, String key)
    {
        Object value = widget.getData(key);
        return value instanceof Object[] ? (Object[])value : NO_ARGS;
    }

    private static ResourceBundle loadBundle(Language language)
    {
        return ResourceBundle.getBundle(BUNDLE_BASE_NAME, language.locale, I18n.class.getClassLoader(),
                                        NO_DEFAULT_LOCALE_CONTROL);
    }
}
