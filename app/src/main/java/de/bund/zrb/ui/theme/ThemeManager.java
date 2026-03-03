package de.bund.zrb.ui.theme;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.plaf.ColorUIResource;
import java.awt.*;

/**
 * Central manager for the application's global UI theme.
 * <p>
 * Classic      → System L&F (Windows native), no color overrides.
 * Classic Metal→ Metal L&F, no color overrides.
 * Modern/Retro → Metal L&F + dark color overrides via UIManager + custom UI delegates.
 * <p>
 * IMPORTANT DESIGN DECISIONS:
 * - AccentButtonUI is registered ONLY for "ButtonUI", NOT for "ToggleButtonUI"
 *   (ToggleButtons in toolbars need standard Metal rendering).
 * - JMenuBar must be explicitly refreshed since it is NOT part of JFrame.getComponents().
 * - Button.border is NEVER overridden (Metal defaults handle icon/text spacing).
 */
public final class ThemeManager {

    private static final ThemeManager INSTANCE = new ThemeManager();
    private final String systemLafClassName;
    private AppTheme currentTheme = AppTheme.CLASSIC;

    private ThemeManager() {
        systemLafClassName = UIManager.getSystemLookAndFeelClassName();
    }

    public static ThemeManager getInstance() { return INSTANCE; }
    public AppTheme getCurrentTheme() { return currentTheme; }

    // ── convenience entry points ────────────────────────────────────────

    public void applyTheme(int lockStyleIndex) {
        applyTheme(AppTheme.fromLockStyleIndex(lockStyleIndex));
    }

    public void applyTheme(AppTheme theme) {
        if (theme == null) theme = AppTheme.CLASSIC;
        this.currentTheme = theme;

        // 1. Switch Look & Feel
        String laf = theme.lafClassName != null ? theme.lafClassName : systemLafClassName;
        switchLookAndFeel(laf);

        // 2. Apply or reset color overrides
        if (theme.isDark) {
            applyDarkDefaults(theme);
        } else {
            resetDarkOverrides();
        }

        // 3. Refresh all open windows
        refreshAllWindows();
    }

    // ── L&F switching ───────────────────────────────────────────────────

    private void switchLookAndFeel(String lafClassName) {
        try {
            UIManager.setLookAndFeel(lafClassName);
        } catch (Exception e) {
            System.err.println("[ThemeManager] L&F switch failed: " + e.getMessage());
        }
    }

    // ── window refresh ──────────────────────────────────────────────────

    public void refreshAllWindows() {
        for (Window w : Window.getWindows()) {
            refreshWindow(w);
        }
    }

    public void refreshWindow(Window window) {
        if (window == null) return;
        SwingUtilities.updateComponentTreeUI(window);

        // JMenuBar is NOT part of getComponents() – handle explicitly
        if (window instanceof JFrame) {
            JMenuBar mb = ((JFrame) window).getJMenuBar();
            if (mb != null) {
                SwingUtilities.updateComponentTreeUI(mb);
                applyMenuBarTheme(mb);
            }
        }

        applyThemeToTree(window);
        applyWindowsTitleBar(window);
        window.revalidate();
        window.repaint();
    }

    // ── dark UIManager defaults ─────────────────────────────────────────

    private void applyDarkDefaults(AppTheme t) {
        ColorUIResource bg       = c(t.bg);
        ColorUIResource surface  = c(t.surface);
        ColorUIResource text     = c(t.text);
        ColorUIResource accent   = c(t.accent);
        ColorUIResource selBg    = c(t.selection);
        ColorUIResource selFg    = c(t.selectionText);
        ColorUIResource border   = c(t.border);
        ColorUIResource inputBg  = c(t.inputBg);
        ColorUIResource inputTxt = c(t.inputText);
        ColorUIResource disabled = c(t.disabledText);
        ColorUIResource caret    = c(t.caretColor);
        ColorUIResource tSelBg   = c(t.tableSelectionBg);
        ColorUIResource tSelFg   = c(t.tableSelectionFg);
        ColorUIResource tGrid    = c(t.tableGridColor);

        // global
        UIManager.put("Panel.background", bg);
        UIManager.put("Panel.foreground", text);
        UIManager.put("window", bg);
        UIManager.put("control", bg);
        UIManager.put("text", inputBg);
        UIManager.put("textText", inputTxt);
        UIManager.put("controlText", text);
        UIManager.put("infoText", text);
        UIManager.put("info", surface);
        UIManager.put("desktop", bg);
        UIManager.put("RootPane.background", bg);
        UIManager.put("ContentPane.background", bg);

        // label
        UIManager.put("Label.foreground", text);
        UIManager.put("Label.disabledForeground", disabled);

        // buttons – AccentButtonUI for ButtonUI only (NOT ToggleButtonUI)
        UIManager.put("ButtonUI", "de.bund.zrb.ui.theme.AccentButtonUI");
        UIManager.put("Button.background", accent);
        UIManager.put("Button.foreground", c(Color.BLACK));
        UIManager.put("Button.select", c(t.accentHover));
        UIManager.put("Button.focus", c(t.accentHover));
        UIManager.put("Button.gradient", null);
        // ToggleButton keeps Metal defaults – just override colors
        UIManager.put("ToggleButton.background", surface);
        UIManager.put("ToggleButton.foreground", text);
        UIManager.put("ToggleButton.select", accent);
        UIManager.put("ToggleButton.gradient", null);

        // text fields
        for (String pfx : new String[]{"TextField","TextArea","TextPane","EditorPane","PasswordField","FormattedTextField"}) {
            UIManager.put(pfx + ".background", inputBg);
            UIManager.put(pfx + ".foreground", inputTxt);
            UIManager.put(pfx + ".caretForeground", caret);
            UIManager.put(pfx + ".selectionBackground", selBg);
            UIManager.put(pfx + ".selectionForeground", selFg);
            UIManager.put(pfx + ".inactiveForeground", disabled);
        }
        for (String pfx : new String[]{"TextField","TextArea","PasswordField","FormattedTextField"}) {
            UIManager.put(pfx + ".border", new LineBorder(border, 1));
        }

        // table
        UIManager.put("Table.background", surface);
        UIManager.put("Table.foreground", text);
        UIManager.put("Table.selectionBackground", tSelBg);
        UIManager.put("Table.selectionForeground", tSelFg);
        UIManager.put("Table.gridColor", tGrid);
        UIManager.put("Table.focusCellHighlightBorder", new LineBorder(accent, 1));
        UIManager.put("TableHeader.background", bg);
        UIManager.put("TableHeader.foreground", text);

        // list
        UIManager.put("List.background", surface);
        UIManager.put("List.foreground", text);
        UIManager.put("List.selectionBackground", selBg);
        UIManager.put("List.selectionForeground", selFg);

        // combo
        UIManager.put("ComboBox.background", inputBg);
        UIManager.put("ComboBox.foreground", inputTxt);
        UIManager.put("ComboBox.selectionBackground", selBg);
        UIManager.put("ComboBox.selectionForeground", selFg);
        UIManager.put("ComboBox.buttonBackground", surface);
        UIManager.put("ComboBox.buttonShadow", border);
        UIManager.put("ComboBox.buttonDarkShadow", border);
        UIManager.put("ComboBox.buttonHighlight", surface);

        // scroll
        UIManager.put("ScrollBarUI", "de.bund.zrb.ui.theme.AccentScrollBarUI");
        UIManager.put("ScrollBar.background", surface);
        UIManager.put("ScrollBar.thumb", accent);
        UIManager.put("ScrollBar.thumbHighlight", c(t.accentHover));
        UIManager.put("ScrollBar.thumbShadow", accent);
        UIManager.put("ScrollBar.track", surface);
        UIManager.put("ScrollPane.background", bg);
        UIManager.put("Viewport.background", bg);
        UIManager.put("Viewport.foreground", text);

        // tabbed pane
        UIManager.put("TabbedPane.background", bg);
        UIManager.put("TabbedPane.foreground", text);
        UIManager.put("TabbedPane.selected", surface);
        UIManager.put("TabbedPane.selectedForeground", accent);
        UIManager.put("TabbedPane.contentAreaColor", bg);
        UIManager.put("TabbedPane.tabAreaBackground", bg);
        UIManager.put("TabbedPane.light", surface);
        UIManager.put("TabbedPane.highlight", surface);
        UIManager.put("TabbedPane.shadow", border);
        UIManager.put("TabbedPane.darkShadow", border);
        UIManager.put("TabbedPane.focus", accent);
        UIManager.put("TabbedPane.unselectedBackground", bg);

        // menus
        UIManager.put("MenuBar.background", bg);
        UIManager.put("MenuBar.foreground", text);
        UIManager.put("Menu.background", bg);
        UIManager.put("Menu.foreground", text);
        UIManager.put("Menu.selectionBackground", selBg);
        UIManager.put("Menu.selectionForeground", selFg);
        UIManager.put("MenuItem.background", surface);
        UIManager.put("MenuItem.foreground", text);
        UIManager.put("MenuItem.selectionBackground", selBg);
        UIManager.put("MenuItem.selectionForeground", selFg);
        UIManager.put("PopupMenu.background", surface);
        UIManager.put("PopupMenu.foreground", text);
        UIManager.put("CheckBoxMenuItem.background", surface);
        UIManager.put("CheckBoxMenuItem.foreground", text);
        UIManager.put("CheckBoxMenuItem.selectionBackground", selBg);
        UIManager.put("CheckBoxMenuItem.selectionForeground", selFg);
        UIManager.put("RadioButtonMenuItem.background", surface);
        UIManager.put("RadioButtonMenuItem.foreground", text);

        // toolbar
        UIManager.put("ToolBar.background", bg);
        UIManager.put("ToolBar.foreground", text);

        // tooltip
        UIManager.put("ToolTip.background", surface);
        UIManager.put("ToolTip.foreground", text);
        UIManager.put("ToolTip.border", new LineBorder(border, 1));

        // option pane
        UIManager.put("OptionPane.background", bg);
        UIManager.put("OptionPane.messageForeground", text);

        // split pane
        UIManager.put("SplitPane.background", bg);
        UIManager.put("SplitPane.dividerFocusColor", accent);
        UIManager.put("SplitPane.darkShadow", border);
        UIManager.put("SplitPane.shadow", border);
        UIManager.put("SplitPane.highlight", surface);
        UIManager.put("SplitPaneDivider.draggingColor", accent);
        UIManager.put("SplitPane.dividerSize", 6);

        // tree
        UIManager.put("Tree.background", surface);
        UIManager.put("Tree.foreground", text);
        UIManager.put("Tree.textBackground", surface);
        UIManager.put("Tree.textForeground", text);
        UIManager.put("Tree.selectionBackground", selBg);
        UIManager.put("Tree.selectionForeground", selFg);

        // separator
        UIManager.put("Separator.foreground", border);
        UIManager.put("Separator.background", bg);

        // checkbox / radio
        UIManager.put("CheckBox.background", bg);
        UIManager.put("CheckBox.foreground", text);
        UIManager.put("RadioButton.background", bg);
        UIManager.put("RadioButton.foreground", text);

        // spinner
        UIManager.put("Spinner.background", inputBg);
        UIManager.put("Spinner.foreground", inputTxt);
        UIManager.put("Spinner.border", new LineBorder(border, 1));

        // progress bar
        UIManager.put("ProgressBar.background", bg);
        UIManager.put("ProgressBar.foreground", accent);

        // titled border
        UIManager.put("TitledBorder.titleColor", accent);

        // file chooser
        UIManager.put("FileChooser.listViewBackground", surface);
    }

    // ── reset overrides (for light themes) ──────────────────────────────

    /** All UIManager keys we override in dark mode – must be restored for light themes. */
    private static final String[] DARK_KEYS = {
            "Panel.background", "Panel.foreground",
            "window", "control", "text", "textText", "controlText", "infoText", "info", "desktop",
            "RootPane.background", "ContentPane.background",
            "Label.foreground", "Label.disabledForeground",
            "ButtonUI",
            "Button.background", "Button.foreground", "Button.select", "Button.focus", "Button.gradient",
            "ToggleButton.background", "ToggleButton.foreground", "ToggleButton.select", "ToggleButton.gradient",
            "TextField.background", "TextField.foreground", "TextField.caretForeground",
            "TextField.selectionBackground", "TextField.selectionForeground", "TextField.inactiveForeground", "TextField.border",
            "TextArea.background", "TextArea.foreground", "TextArea.caretForeground",
            "TextArea.selectionBackground", "TextArea.selectionForeground", "TextArea.inactiveForeground", "TextArea.border",
            "TextPane.background", "TextPane.foreground", "TextPane.caretForeground",
            "TextPane.selectionBackground", "TextPane.selectionForeground", "TextPane.inactiveForeground",
            "EditorPane.background", "EditorPane.foreground", "EditorPane.caretForeground",
            "EditorPane.selectionBackground", "EditorPane.selectionForeground", "EditorPane.inactiveForeground",
            "PasswordField.background", "PasswordField.foreground", "PasswordField.caretForeground",
            "PasswordField.selectionBackground", "PasswordField.selectionForeground", "PasswordField.inactiveForeground", "PasswordField.border",
            "FormattedTextField.background", "FormattedTextField.foreground", "FormattedTextField.caretForeground",
            "FormattedTextField.selectionBackground", "FormattedTextField.selectionForeground", "FormattedTextField.inactiveForeground", "FormattedTextField.border",
            "Table.background", "Table.foreground", "Table.selectionBackground", "Table.selectionForeground",
            "Table.gridColor", "Table.focusCellHighlightBorder", "TableHeader.background", "TableHeader.foreground",
            "List.background", "List.foreground", "List.selectionBackground", "List.selectionForeground",
            "ComboBox.background", "ComboBox.foreground", "ComboBox.selectionBackground", "ComboBox.selectionForeground",
            "ComboBox.buttonBackground", "ComboBox.buttonShadow", "ComboBox.buttonDarkShadow", "ComboBox.buttonHighlight",
            "ScrollBarUI",
            "ScrollBar.background", "ScrollBar.thumb", "ScrollBar.thumbHighlight", "ScrollBar.thumbShadow", "ScrollBar.track",
            "ScrollPane.background", "Viewport.background", "Viewport.foreground",
            "TabbedPane.background", "TabbedPane.foreground", "TabbedPane.selected", "TabbedPane.selectedForeground",
            "TabbedPane.contentAreaColor", "TabbedPane.tabAreaBackground",
            "TabbedPane.light", "TabbedPane.highlight", "TabbedPane.shadow", "TabbedPane.darkShadow",
            "TabbedPane.focus", "TabbedPane.unselectedBackground",
            "MenuBar.background", "MenuBar.foreground",
            "Menu.background", "Menu.foreground", "Menu.selectionBackground", "Menu.selectionForeground",
            "MenuItem.background", "MenuItem.foreground", "MenuItem.selectionBackground", "MenuItem.selectionForeground",
            "PopupMenu.background", "PopupMenu.foreground",
            "CheckBoxMenuItem.background", "CheckBoxMenuItem.foreground",
            "CheckBoxMenuItem.selectionBackground", "CheckBoxMenuItem.selectionForeground",
            "RadioButtonMenuItem.background", "RadioButtonMenuItem.foreground",
            "ToolBar.background", "ToolBar.foreground",
            "ToolTip.background", "ToolTip.foreground", "ToolTip.border",
            "OptionPane.background", "OptionPane.messageForeground",
            "SplitPane.background", "SplitPane.dividerFocusColor", "SplitPane.darkShadow", "SplitPane.shadow",
            "SplitPane.highlight", "SplitPaneDivider.draggingColor", "SplitPane.dividerSize",
            "Tree.background", "Tree.foreground", "Tree.textBackground", "Tree.textForeground",
            "Tree.selectionBackground", "Tree.selectionForeground",
            "Separator.foreground", "Separator.background",
            "CheckBox.background", "CheckBox.foreground",
            "RadioButton.background", "RadioButton.foreground",
            "Spinner.background", "Spinner.foreground", "Spinner.border",
            "ProgressBar.background", "ProgressBar.foreground",
            "TitledBorder.titleColor",
            "FileChooser.listViewBackground",
    };

    private void resetDarkOverrides() {
        UIDefaults fresh = UIManager.getLookAndFeel().getDefaults();
        for (String key : DARK_KEYS) {
            UIManager.put(key, fresh.get(key)); // null is fine – removes override
        }
    }

    // ── component-tree helpers ──────────────────────────────────────────

    private void applyMenuBarTheme(JMenuBar mb) {
        if (currentTheme.isDark) {
            mb.setBackground(currentTheme.bg);
            mb.setForeground(currentTheme.text);
            mb.setOpaque(true);
            mb.setBorderPainted(true);
            mb.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, currentTheme.border));
            for (int i = 0; i < mb.getMenuCount(); i++) {
                JMenu m = mb.getMenu(i);
                if (m != null) { m.setBackground(currentTheme.bg); m.setForeground(currentTheme.text); m.setOpaque(true); }
            }
        } else {
            mb.setBackground(null);
            mb.setForeground(null);
            mb.setOpaque(false);
            mb.setBorderPainted(false);
            mb.setBorder(null);
            for (int i = 0; i < mb.getMenuCount(); i++) {
                JMenu m = mb.getMenu(i);
                if (m != null) { m.setBackground(null); m.setForeground(null); m.setOpaque(false); }
            }
        }
    }

    /**
     * Walk the component tree and apply theme to components that ignore UIManager defaults:
     * RSyntaxTextArea, JEditorPane, JToolBar.
     */
    private void applyThemeToTree(Component root) {
        if (root instanceof org.fife.ui.rsyntaxtextarea.RSyntaxTextArea) {
            applyEditorTheme((org.fife.ui.rsyntaxtextarea.RSyntaxTextArea) root);
        }
        if (root instanceof JEditorPane && !(root instanceof JTextPane)) {
            applyHtmlPaneTheme((JEditorPane) root);
        }
        if (root instanceof JToolBar) {
            applyToolbarTheme((JToolBar) root);
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                applyThemeToTree(child);
            }
        }
    }

    private void applyToolbarTheme(JToolBar tb) {
        if (currentTheme.isDark) {
            tb.setBackground(currentTheme.bg);
            tb.setForeground(currentTheme.text);
            tb.setOpaque(true);
        } else {
            tb.setBackground(null);
            tb.setForeground(null);
            tb.setOpaque(false);
        }
    }

    /** Apply theme to RSyntaxTextArea (has its own color scheme, ignores UIManager). */
    public void applyEditorTheme(org.fife.ui.rsyntaxtextarea.RSyntaxTextArea area) {
        AppTheme t = currentTheme;
        area.setBackground(t.editorBg);
        area.setForeground(t.text);
        area.setCaretColor(t.caretColor);
        area.setSelectionColor(t.selection);
        area.setSelectedTextColor(t.selectionText);
        area.setCurrentLineHighlightColor(t.editorCurrentLineBg);
        area.setFadeCurrentLineHighlight(false);
        area.setMarginLineColor(t.isDark ? t.border : Color.RED);

        // gutter
        Component p = area.getParent();
        if (p != null) p = p.getParent();
        if (p instanceof org.fife.ui.rtextarea.RTextScrollPane) {
            org.fife.ui.rtextarea.Gutter g = ((org.fife.ui.rtextarea.RTextScrollPane) p).getGutter();
            if (g != null) {
                g.setBackground(t.isDark ? t.bg : new Color(245, 245, 245));
                g.setLineNumberColor(t.isDark ? t.textSecondary : new Color(120, 120, 120));
                g.setBorderColor(t.isDark ? t.border : new Color(220, 220, 220));
            }
        }

        // syntax scheme
        org.fife.ui.rsyntaxtextarea.SyntaxScheme scheme = area.getSyntaxScheme();
        if (scheme != null && t.isDark) {
            adjustDarkSyntaxScheme(scheme, t);
            area.setSyntaxScheme(scheme);
        }
    }

    private void adjustDarkSyntaxScheme(org.fife.ui.rsyntaxtextarea.SyntaxScheme scheme, AppTheme t) {
        for (int i = 0; i < scheme.getStyleCount(); i++) {
            org.fife.ui.rsyntaxtextarea.Style s = scheme.getStyle(i);
            if (s != null && s.foreground != null) {
                // keep bright colors, darken any that would be invisible on dark bg
                float[] hsb = Color.RGBtoHSB(s.foreground.getRed(), s.foreground.getGreen(), s.foreground.getBlue(), null);
                if (hsb[2] < 0.5f) {
                    s.foreground = Color.getHSBColor(hsb[0], hsb[1], Math.min(1f, hsb[2] + 0.4f));
                }
            }
        }
    }

    private void applyHtmlPaneTheme(JEditorPane pane) {
        if (currentTheme.isDark) {
            pane.setBackground(currentTheme.bg);
            pane.setForeground(currentTheme.text);
        }
    }

    // ── Windows dark title bar via JNA ──────────────────────────────────

    private void applyWindowsTitleBar(Window window) {
        if (!(window instanceof JFrame)) return;
        try {
            if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
            long hwndLong = com.sun.jna.Native.getWindowID(window);
            if (hwndLong == 0) return;
            com.sun.jna.platform.win32.WinDef.HWND hwnd =
                    new com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Pointer.createConstant(hwndLong));
            com.sun.jna.Memory val = new com.sun.jna.Memory(4);
            val.setInt(0, currentTheme.isDark ? 1 : 0);
            com.sun.jna.Function dwm = com.sun.jna.Function.getFunction("dwmapi", "DwmSetWindowAttribute");
            dwm.invoke(int.class, new Object[]{hwnd, 20 /* DWMWA_USE_IMMERSIVE_DARK_MODE */, val, 4});
        } catch (Exception | UnsatisfiedLinkError e) {
            // JNA not available or not on Windows – ignore
        }
    }

    // ── util ────────────────────────────────────────────────────────────

    private static ColorUIResource c(Color color) {
        return new ColorUIResource(color);
    }
}

