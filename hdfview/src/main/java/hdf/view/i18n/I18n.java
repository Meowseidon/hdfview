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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Supplier;

import hdf.view.ViewProperties;

import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
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

    /** Widget data key containing a Text placeholder resource key. */
    private static final String MESSAGE_KEY = I18n.class.getName() + ".message";

    /** Widget data key containing resource keys for Combo/List items. */
    private static final String ITEM_KEYS = I18n.class.getName() + ".itemKeys";

    /** Widget data key containing optional MessageFormat arguments for Combo/List items. */
    private static final String ITEM_ARGS = I18n.class.getName() + ".itemArgs";

    /** Widget data key containing a raw datatype description. */
    private static final String DATATYPE_DESCRIPTION = I18n.class.getName() + ".datatypeDescription";

    /** Widget data key containing raw datatype descriptions for table cells. */
    private static final String DATATYPE_DESCRIPTION_CELLS = I18n.class.getName() + ".datatypeDescriptionCells";

    /** Widget data key containing a language-sensitive dynamic text supplier. */
    private static final String DYNAMIC_TEXT = I18n.class.getName() + ".dynamicText";

    /** Widget data key containing resource-backed values for table cells. */
    private static final String TABLE_CELL_BINDINGS = I18n.class.getName() + ".tableCellBindings";

    private static final Pattern BIT_INTEGER_PATTERN =
        Pattern.compile("^(\\d+)-bit (unsigned )?integer$");
    private static final Pattern NATIVE_INTEGER_PATTERN =
        Pattern.compile("^native (unsigned )?integer$");
    private static final Pattern BIT_FLOAT_PATTERN = Pattern.compile("^(\\d+)-bit floating-point$");
    private static final Pattern BIT_CHARACTER_PATTERN =
        Pattern.compile("^(\\d+)-bit (unsigned )?character$");
    private static final Pattern BITFIELD_PATTERN = Pattern.compile("^(\\d+)-bit bitfield$");
    private static final Pattern OPAQUE_BYTE_PATTERN = Pattern.compile("^(\\d+)-byte [Oo]paque$");
    private static final Pattern OPAQUE_BIT_PATTERN = Pattern.compile("^(\\d+)-bit [Oo]paque$");
    private static final Pattern BIT_ENUM_PATTERN = Pattern.compile("^(\\d+)-bit enum$");
    private static final Pattern ARRAY_PATTERN = Pattern.compile("^Array(?: (\\[[^]]*\\]))?(?: of (.*))?$");
    private static final Pattern VLEN_PATTERN = Pattern.compile("^Variable-length(?: of (.*))?$");
    private static final Pattern COMPLEX_PATTERN = Pattern.compile("^(?:native )?Complex(?: of (.*))?$");

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

    private static final class CellBinding {
        private final String key;
        private final Object[] args;

        private CellBinding(String key, Object[] args)
        {
            this.key  = key;
            this.args = args;
        }
    }

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
     * Bind a Text placeholder to a resource key without replacing the current
     * editable value.
     *
     * @param widget the SWT Text control
     * @param key the placeholder resource key
     */
    public static void bindMessage(Text widget, String key)
    {
        if (widget == null || widget.isDisposed())
            return;

        widget.setData(MESSAGE_KEY, key);
        apply(widget);
    }

    /**
     * Bind a read-only text or label whose value contains localized fragments
     * and runtime data. The supplier is evaluated again during every language
     * refresh, so values such as "Unlimited" do not remain in the old
     * language after a switch.
     *
     * @param widget the Text, Label, or Shell control
     * @param supplier supplies the complete display value
     */
    public static void bindDynamic(Widget widget, Supplier<String> supplier)
    {
        if (widget == null || widget.isDisposed() || supplier == null)
            return;

        widget.setData(DYNAMIC_TEXT, supplier);
        apply(widget);
    }

    /**
     * Bind a dynamically supplied datatype description. The raw description is
     * retained so an already-open metadata control can be translated again
     * after a runtime language switch.
     *
     * @param widget the read-only Text control
     * @param description the description supplied by the HDF object library
     */
    public static void bindDatatypeDescription(Text widget, String description)
    {
        if (widget == null || widget.isDisposed())
            return;

        widget.setData(DATATYPE_DESCRIPTION, description);
        apply(widget);
    }

    /**
     * Bind one table cell containing a datatype description. Table rows are
     * not SWT composites, so their raw values are kept explicitly for refresh.
     *
     * @param widget the table item
     * @param column the zero-based table column
     * @param description the description supplied by the HDF object library
     */
    public static void bindDatatypeDescription(TableItem widget, int column, String description)
    {
        if (widget == null || widget.isDisposed() || column < 0)
            return;

        @SuppressWarnings("unchecked")
        Map<Integer, String> descriptions = (Map<Integer, String>)widget.getData(DATATYPE_DESCRIPTION_CELLS);
        if (descriptions == null) {
            descriptions = new HashMap<>();
            widget.setData(DATATYPE_DESCRIPTION_CELLS, descriptions);
        }
        descriptions.put(column, description);
        apply(widget);
    }

    /**
     * Bind a resource-backed value in a table cell so it follows language
     * changes without rebuilding the table.
     *
     * @param widget the table item
     * @param column the zero-based table column
     * @param key the resource key
     * @param args optional MessageFormat arguments
     */
    public static void bindTableCell(TableItem widget, int column, String key, Object... args)
    {
        if (widget == null || widget.isDisposed() || column < 0 || key == null)
            return;

        @SuppressWarnings("unchecked")
        Map<Integer, CellBinding> bindings = (Map<Integer, CellBinding>)widget.getData(TABLE_CELL_BINDINGS);
        if (bindings == null) {
            bindings = new HashMap<>();
            widget.setData(TABLE_CELL_BINDINGS, bindings);
        }
        bindings.put(column, new CellBinding(key, args == null ? NO_ARGS : args.clone()));
        apply(widget);
    }

    /**
     * Translate the short datatype descriptions produced by the HDF object
     * library. English remains byte-for-byte unchanged; Chinese uses a
     * resource-backed Chinese description followed by the original technical
     * wording so it remains searchable in HDF documentation.
     *
     * @param description an object-library datatype description
     * @return the description in the current UI language
     */
    public static synchronized String datatypeDescription(String description)
    {
        if (description == null || description.length() == 0)
            return text("common.unknown");
        if (currentLanguage == Language.ENGLISH)
            return description;

        String value = description.trim();
        String core = value;
        String namedPath = null;
        int namedSeparator = value.indexOf("->");
        if (namedSeparator >= 0) {
            core      = value.substring(0, namedSeparator).trim();
            namedPath = value.substring(namedSeparator + 2).trim();
        }

        String localized = localizeDatatypeCore(core);
        if (localized == null)
            localized = text("datatype.description.technical", value);
        else if (namedPath != null && namedPath.length() > 0)
            localized += text("datatype.description.named", namedPath);

        return text("datatype.description.bilingual", localized, value);
    }

    /** Return the Chinese core of a known object-library description. */
    private static String localizeDatatypeCore(String description)
    {
        Matcher matcher = BIT_INTEGER_PATTERN.matcher(description);
        if (matcher.matches()) {
            String key = matcher.group(2) == null
                ? "datatype.description.bitInteger"
                : "datatype.description.bitUnsignedInteger";
            return text(key, matcher.group(1));
        }

        matcher = NATIVE_INTEGER_PATTERN.matcher(description);
        if (matcher.matches()) {
            return text(matcher.group(1) == null
                            ? "datatype.description.nativeInteger"
                            : "datatype.description.nativeUnsignedInteger");
        }

        matcher = BIT_FLOAT_PATTERN.matcher(description);
        if (matcher.matches())
            return text("datatype.description.bitFloat", matcher.group(1));
        if ("native floating-point".equals(description))
            return text("datatype.description.nativeFloat");

        matcher = BIT_CHARACTER_PATTERN.matcher(description);
        if (matcher.matches()) {
            String key = matcher.group(2) == null
                ? "datatype.description.bitCharacter"
                : "datatype.description.bitUnsignedCharacter";
            return text(key, matcher.group(1));
        }

        if (description.startsWith("String, length = ")) {
            String length = description.substring("String, length = ".length());
            int comma = length.indexOf(",");
            if (comma >= 0)
                length = length.substring(0, comma).trim();
            if ("variable".equalsIgnoreCase(length))
                length = text("datatype.description.variable");
            return text("datatype.description.string", length);
        }
        if ("String".equals(description))
            return text("datatype.description.stringType");

        if ("native bitfield".equals(description))
            return text("datatype.description.nativeBitfield");
        matcher = BITFIELD_PATTERN.matcher(description);
        if (matcher.matches())
            return text("datatype.description.bitBitfield", matcher.group(1));

        if ("native Opaque".equals(description) || "native opaque".equals(description))
            return text("datatype.description.nativeOpaque");
        matcher = OPAQUE_BYTE_PATTERN.matcher(description);
        if (matcher.matches())
            return text("datatype.description.byteOpaque", matcher.group(1));
        matcher = OPAQUE_BIT_PATTERN.matcher(description);
        if (matcher.matches())
            return text("datatype.description.bitOpaque", matcher.group(1));

        if ("Reference".equals(description))
            return text("datatype.description.reference");
        if ("Dataset region reference".equals(description))
            return text("datatype.description.datasetRegionReference");
        if ("Object reference".equals(description))
            return text("datatype.description.objectReference");

        if ("native enum".equals(description))
            return text("datatype.description.nativeEnum");
        matcher = BIT_ENUM_PATTERN.matcher(description);
        if (matcher.matches())
            return text("datatype.description.bitEnum", matcher.group(1));

        matcher = VLEN_PATTERN.matcher(description);
        if (matcher.matches()) {
            String localized = text("datatype.description.variableLength");
            if (matcher.group(1) != null)
                localized += text("datatype.description.of", localizeNestedDatatype(matcher.group(1)));
            return localized;
        }

        matcher = ARRAY_PATTERN.matcher(description);
        if (matcher.matches()) {
            String localized = text("datatype.description.array", matcher.group(1) == null ? "" : matcher.group(1));
            if (matcher.group(2) != null)
                localized += text("datatype.description.of", localizeNestedDatatype(matcher.group(2)));
            return localized;
        }

        matcher = COMPLEX_PATTERN.matcher(description);
        if (matcher.matches()) {
            String localized = text("datatype.description.complex");
            if (matcher.group(1) != null)
                localized += text("datatype.description.of", localizeNestedDatatype(matcher.group(1)));
            return localized;
        }

        if (description.startsWith("Compound"))
            return text("datatype.description.compound");
        if ("Unknown".equals(description) || "Unknown data type.".equals(description))
            return text("datatype.description.unknown");

        return null;
    }

    /** Nested descriptions do not repeat the complete English parenthesis. */
    private static String localizeNestedDatatype(String description)
    {
        String localized = localizeDatatypeCore(description.trim());
        return localized == null ? description : localized;
    }

    /**
     * Bind the items of a Combo, CCombo, or List to resource keys.
     *
     * <p>The selected index is preserved when a language refresh replaces the
     * visible item labels. Callers must use the selected index or another stable
     * model value for behavior; translated labels are presentation only.</p>
     *
     * @param widget the SWT item-list widget
     * @param keys resource keys in item order
     */
    public static void bindItems(Widget widget, String... keys)
    {
        bindItems(widget, keys, null);
    }

    /**
     * Bind Combo/List items to resource keys with per-item arguments. A null key
     * deliberately keeps a provider- or data-supplied item unchanged.
     *
     * @param widget the Combo, CCombo, or List
     * @param keys resource keys in item order
     * @param args optional MessageFormat arguments in item order
     */
    public static void bindItems(Widget widget, String[] keys, Object[][] args)
    {
        if (widget == null || widget.isDisposed())
            return;

        widget.setData(ITEM_KEYS, keys == null ? new String[0] : keys.clone());
        if (args == null) {
            widget.setData(ITEM_ARGS, null);
        }
        else {
            Object[][] copy = new Object[args.length][];
            for (int i = 0; i < args.length; i++)
                copy[i] = args[i] == null ? NO_ARGS : args[i].clone();
            widget.setData(ITEM_ARGS, copy);
        }
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

        refreshWidget(widget);
        relayout(widget);
    }

    /** Refresh a widget hierarchy without repeatedly laying out every child. */
    private static void refreshWidget(Widget widget)
    {
        if (widget == null || widget.isDisposed())
            return;

        apply(widget);

        if (widget instanceof Menu) {
            for (MenuItem item : ((Menu)widget).getItems())
                refreshWidget(item);
            return;
        }

        if (widget instanceof MenuItem) {
            Menu submenu = ((MenuItem)widget).getMenu();
            if (submenu != null)
                refreshWidget(submenu);
            return;
        }

        if (widget instanceof TabFolder) {
            for (TabItem item : ((TabFolder)widget).getItems()) {
                refreshWidget(item);
                Control control = item.getControl();
                if (control != null)
                    refreshWidget(control);
            }
        }

        if (widget instanceof ToolBar) {
            for (ToolItem item : ((ToolBar)widget).getItems())
                refreshWidget(item);
        }

        if (widget instanceof Table) {
            for (TableColumn column : ((Table)widget).getColumns())
                refreshWidget(column);
            for (TableItem item : ((Table)widget).getItems())
                refreshWidget(item);
        }

        if (widget instanceof Shell) {
            Menu menuBar = ((Shell)widget).getMenuBar();
            if (menuBar != null)
                refreshWidget(menuBar);
        }

        if (widget instanceof Control) {
            Menu contextMenu = ((Control)widget).getMenu();
            if (contextMenu != null)
                refreshWidget(contextMenu);
        }

        if (widget instanceof Composite) {
            for (Control child : ((Composite)widget).getChildren())
                refreshWidget(child);
        }
    }

    /**
     * Recompute layouts for the existing widget hierarchy without changing a
     * top-level Shell's size or position. SWT text changes do not invalidate
     * every parent layout synchronously, so the nearest owning Shell is laid
     * out after a refresh. The recursive layout call also reaches nested
     * composites, tab folders, toolbars, and table columns.
     */
    private static void relayout(Widget widget)
    {
        Shell shell = null;
        if (widget instanceof Shell)
            shell = (Shell)widget;
        else if (widget instanceof Control)
            shell = ((Control)widget).getShell();
        else if (widget instanceof TabItem)
            shell = ((TabItem)widget).getParent().getShell();
        else if (widget instanceof TableColumn)
            shell = ((TableColumn)widget).getParent().getShell();
        else if (widget instanceof TableItem)
            shell = ((TableItem)widget).getParent().getShell();

        if (shell != null && !shell.isDisposed()) {
            shell.layout(true, true);
            shell.update();
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

        Shell[] shells = display.getShells();
        for (Shell shell : shells) {
            if (shell == null || shell.isDisposed())
                continue;

            shell.setRedraw(false);
            try {
                refreshWidget(shell);
            }
            finally {
                shell.layout(true, true);
                shell.setRedraw(true);
                shell.update();
            }
        }
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
            else if (widget instanceof StyledText)
                ((StyledText)widget).setText(value);
            else if (widget instanceof TableColumn)
                ((TableColumn)widget).setText(value);
        }

        Object rawDatatypeDescription = widget.getData(DATATYPE_DESCRIPTION);
        if (rawDatatypeDescription instanceof String && widget instanceof Text)
            ((Text)widget).setText(datatypeDescription((String)rawDatatypeDescription));

        Object dynamicText = widget.getData(DYNAMIC_TEXT);
        if (dynamicText instanceof Supplier &&
            (widget instanceof Text || widget instanceof Label || widget instanceof Shell)) {
            try {
                String value = ((Supplier<?>)dynamicText).get().toString();
                if (widget instanceof Text)
                    ((Text)widget).setText(value);
                else if (widget instanceof Label)
                    ((Label)widget).setText(value);
                else
                    ((Shell)widget).setText(value);
            }
            catch (RuntimeException ex) {
                // Leave a runtime data value untouched when its provider fails.
            }
        }

        Object rawDatatypeCells = widget.getData(DATATYPE_DESCRIPTION_CELLS);
        if (rawDatatypeCells instanceof Map && widget instanceof TableItem) {
            @SuppressWarnings("unchecked")
            Map<Integer, String> descriptions = (Map<Integer, String>)rawDatatypeCells;
            for (Map.Entry<Integer, String> entry : descriptions.entrySet())
                ((TableItem)widget).setText(entry.getKey(), datatypeDescription(entry.getValue()));
        }

        Object rawCellBindings = widget.getData(TABLE_CELL_BINDINGS);
        if (rawCellBindings instanceof Map && widget instanceof TableItem) {
            @SuppressWarnings("unchecked")
            Map<Integer, CellBinding> bindings = (Map<Integer, CellBinding>)rawCellBindings;
            for (Map.Entry<Integer, CellBinding> entry : bindings.entrySet()) {
                CellBinding binding = entry.getValue();
                ((TableItem)widget).setText(entry.getKey(), text(binding.key, binding.args));
            }
        }

        String messageKey = getDataString(widget, MESSAGE_KEY);
        if (messageKey != null && widget instanceof Text)
            ((Text)widget).setMessage(text(messageKey));

        String tooltipKey = getDataString(widget, TOOLTIP_KEY);
        if (tooltipKey != null) {
            String tooltip = text(tooltipKey, getDataArgs(widget, TOOLTIP_ARGS));
            if (widget instanceof ToolItem)
                ((ToolItem)widget).setToolTipText(tooltip);
            else if (widget instanceof Control)
                ((Control)widget).setToolTipText(tooltip);
        }

        String[] itemKeys = getDataStringArray(widget, ITEM_KEYS);
        if (itemKeys != null) {
            Object[][] itemArgs = getDataObjectArray(widget, ITEM_ARGS);
            String[] values = new String[itemKeys.length];
            for (int i = 0; i < itemKeys.length; i++) {
                if (itemKeys[i] == null)
                    values[i] = getExistingItem(widget, i);
                else {
                    Object[] args = itemArgs != null && i < itemArgs.length && itemArgs[i] != null
                        ? itemArgs[i]
                        : NO_ARGS;
                    values[i] = text(itemKeys[i], args);
                }
            }

            if (widget instanceof Combo) {
                Combo combo = (Combo)widget;
                int selection = combo.getSelectionIndex();
                combo.setItems(values);
                if (selection >= 0 && selection < values.length)
                    combo.select(selection);
            }
            else if (widget instanceof CCombo) {
                CCombo combo = (CCombo)widget;
                int selection = combo.getSelectionIndex();
                combo.setItems(values);
                if (selection >= 0 && selection < values.length)
                    combo.select(selection);
            }
            else if (widget instanceof List) {
                List list = (List)widget;
                int[] selection = list.getSelectionIndices();
                list.setItems(values);
                if (selection.length > 0)
                    list.select(selection);
            }
        }
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

    private static String[] getDataStringArray(Widget widget, String key)
    {
        Object value = widget.getData(key);
        return value instanceof String[] ? (String[])value : null;
    }

    private static Object[][] getDataObjectArray(Widget widget, String key)
    {
        Object value = widget.getData(key);
        return value instanceof Object[][] ? (Object[][])value : null;
    }

    /** Keep provider- or data-supplied item labels unchanged in a mixed list. */
    private static String getExistingItem(Widget widget, int index)
    {
        if (widget instanceof Combo) {
            String[] items = ((Combo)widget).getItems();
            return index < items.length ? items[index] : "";
        }
        if (widget instanceof CCombo) {
            String[] items = ((CCombo)widget).getItems();
            return index < items.length ? items[index] : "";
        }
        if (widget instanceof List) {
            String[] items = ((List)widget).getItems();
            return index < items.length ? items[index] : "";
        }
        return "";
    }

    private static ResourceBundle loadBundle(Language language)
    {
        return ResourceBundle.getBundle(BUNDLE_BASE_NAME, language.locale, I18n.class.getClassLoader(),
                                        NO_DEFAULT_LOCALE_CONTROL);
    }
}
