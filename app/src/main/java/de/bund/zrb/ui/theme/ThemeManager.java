package de.bund.zrb.ui.theme;

import de.bund.zrb.ui.lock.LockerStyle;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.plaf.ColorUIResource;
import java.awt.*;

/**
 * Central singleton responsible for applying the global App theme.
 * Maps a LockerStyle to an AppTheme and sets UIManager defaults for the entire Swing UI.
 *
 * Usage:
 *   ThemeManager.getInstance().applyTheme(LockerStyle.MODERN);
 *   // or from settings index:
 *   ThemeManager.getInstance().applyTheme(settings.lockStyle);
 */
public final class ThemeManager {

    private static final ThemeManager INSTANCE = new ThemeManager();

    private AppTheme currentTheme = AppTheme.CLASSIC;

    private ThemeManager() {}

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    public AppTheme getCurrentTheme() {
        return currentTheme;
    }

    /**
     * Apply the theme derived from the given LockerStyle.
     * Must be called on the EDT (or before any UI is created).
     */
    public void applyTheme(LockerStyle style) {
        applyTheme(AppTheme.fromLockerStyle(style));
    }

    /**
     * Apply the theme derived from a settings lockStyle index.
     */
    public void applyTheme(int lockStyleIndex) {
        applyTheme(AppTheme.fromLockStyleIndex(lockStyleIndex));
    }

    /**
     * Apply the given AppTheme to the entire application.
     */
    public void applyTheme(AppTheme theme) {
        if (theme == null) theme = AppTheme.CLASSIC;
        this.currentTheme = theme;

        if (theme == AppTheme.CLASSIC) {
            applyClassicTheme();
        } else {
            applyDarkTheme(theme);
        }

        refreshAllWindows();
    }

    /**
     * Classic theme: reset to system defaults – minimal overrides.
     */
    private void applyClassicTheme() {
        // Restore system L&F defaults by removing our overrides
        String[] keysToRemove = {
                "Panel.background", "Panel.foreground",
                "Label.foreground", "Label.disabledForeground",
                "Button.background", "Button.foreground", "Button.select", "Button.focus",
                "TextField.background", "TextField.foreground", "TextField.caretForeground",
                "TextField.selectionBackground", "TextField.selectionForeground",
                "TextField.inactiveForeground",
                "TextArea.background", "TextArea.foreground", "TextArea.caretForeground",
                "TextArea.selectionBackground", "TextArea.selectionForeground",
                "TextPane.background", "TextPane.foreground", "TextPane.caretForeground",
                "TextPane.selectionBackground", "TextPane.selectionForeground",
                "EditorPane.background", "EditorPane.foreground", "EditorPane.caretForeground",
                "PasswordField.background", "PasswordField.foreground", "PasswordField.caretForeground",
                "Table.background", "Table.foreground",
                "Table.selectionBackground", "Table.selectionForeground",
                "Table.gridColor", "Table.focusCellHighlightBorder",
                "TableHeader.background", "TableHeader.foreground",
                "List.background", "List.foreground",
                "List.selectionBackground", "List.selectionForeground",
                "ComboBox.background", "ComboBox.foreground",
                "ComboBox.selectionBackground", "ComboBox.selectionForeground",
                "ComboBox.buttonBackground",
                "ScrollPane.background",
                "Viewport.background", "Viewport.foreground",
                "TabbedPane.background", "TabbedPane.foreground",
                "TabbedPane.selected", "TabbedPane.selectedForeground",
                "TabbedPane.contentAreaColor",
                "MenuBar.background", "MenuBar.foreground",
                "Menu.background", "Menu.foreground",
                "Menu.selectionBackground", "Menu.selectionForeground",
                "MenuItem.background", "MenuItem.foreground",
                "MenuItem.selectionBackground", "MenuItem.selectionForeground",
                "PopupMenu.background", "PopupMenu.foreground",
                "CheckBoxMenuItem.background", "CheckBoxMenuItem.foreground",
                "CheckBoxMenuItem.selectionBackground", "CheckBoxMenuItem.selectionForeground",
                "RadioButtonMenuItem.background", "RadioButtonMenuItem.foreground",
                "ToolBar.background", "ToolBar.foreground",
                "ToolTip.background", "ToolTip.foreground",
                "OptionPane.background", "OptionPane.messageForeground",
                "SplitPane.background",
                "SplitPane.dividerFocusColor",
                "Tree.background", "Tree.foreground", "Tree.textBackground", "Tree.textForeground",
                "Tree.selectionBackground", "Tree.selectionForeground",
                "ScrollBar.background", "ScrollBar.thumb", "ScrollBar.thumbHighlight",
                "Separator.foreground", "Separator.background",
                "CheckBox.background", "CheckBox.foreground",
                "RadioButton.background", "RadioButton.foreground",
                "Spinner.background", "Spinner.foreground",
                "FileChooser.listViewBackground",
                "ProgressBar.background", "ProgressBar.foreground",
                "TitledBorder.titleColor",
        };

        // Try to get the default LookAndFeel defaults
        try {
            LookAndFeel systemLaf = UIManager.getLookAndFeel();
            UIDefaults systemDefaults = systemLaf.getDefaults();
            for (String key : keysToRemove) {
                Object systemValue = systemDefaults.get(key);
                if (systemValue != null) {
                    UIManager.put(key, systemValue);
                } else {
                    UIManager.put(key, null);
                }
            }
        } catch (Exception e) {
            // Fallback: just remove our overrides
            for (String key : keysToRemove) {
                UIManager.put(key, null);
            }
        }
    }

    /**
     * Apply a dark theme (MODERN or RETRO) using UIManager defaults.
     */
    private void applyDarkTheme(AppTheme t) {
        ColorUIResource bg = c(t.bg);
        ColorUIResource surface = c(t.surface);
        ColorUIResource text = c(t.text);
        ColorUIResource textSec = c(t.textSecondary);
        ColorUIResource accent = c(t.accent);
        ColorUIResource selBg = c(t.selection);
        ColorUIResource selFg = c(t.selectionText);
        ColorUIResource border = c(t.border);
        ColorUIResource inputBg = c(t.inputBg);
        ColorUIResource inputText = c(t.inputText);
        ColorUIResource disabled = c(t.disabledText);
        ColorUIResource caret = c(t.caretColor);
        ColorUIResource tblSelBg = c(t.tableSelectionBg);
        ColorUIResource tblSelFg = c(t.tableSelectionFg);
        ColorUIResource tblGrid = c(t.tableGridColor);

        // --- Panels / Frames ---
        UIManager.put("Panel.background", bg);
        UIManager.put("Panel.foreground", text);

        // --- Labels ---
        UIManager.put("Label.foreground", text);
        UIManager.put("Label.disabledForeground", disabled);

        // --- Buttons ---
        UIManager.put("Button.background", surface);
        UIManager.put("Button.foreground", text);
        UIManager.put("Button.select", accent);
        UIManager.put("Button.focus", accent);

        // --- Text Fields ---
        setTextComponent("TextField", inputBg, inputText, caret, selBg, selFg, disabled);
        setTextComponent("TextArea", inputBg, inputText, caret, selBg, selFg, disabled);
        setTextComponent("TextPane", inputBg, inputText, caret, selBg, selFg, disabled);
        setTextComponent("EditorPane", inputBg, inputText, caret, selBg, selFg, disabled);
        setTextComponent("PasswordField", inputBg, inputText, caret, selBg, selFg, disabled);

        // --- Tables ---
        UIManager.put("Table.background", surface);
        UIManager.put("Table.foreground", text);
        UIManager.put("Table.selectionBackground", tblSelBg);
        UIManager.put("Table.selectionForeground", tblSelFg);
        UIManager.put("Table.gridColor", tblGrid);
        UIManager.put("Table.focusCellHighlightBorder", new LineBorder(accent, 1));
        UIManager.put("TableHeader.background", bg);
        UIManager.put("TableHeader.foreground", text);

        // --- Lists ---
        UIManager.put("List.background", surface);
        UIManager.put("List.foreground", text);
        UIManager.put("List.selectionBackground", selBg);
        UIManager.put("List.selectionForeground", selFg);

        // --- ComboBox ---
        UIManager.put("ComboBox.background", inputBg);
        UIManager.put("ComboBox.foreground", inputText);
        UIManager.put("ComboBox.selectionBackground", selBg);
        UIManager.put("ComboBox.selectionForeground", selFg);
        UIManager.put("ComboBox.buttonBackground", surface);

        // --- ScrollPane / Viewport ---
        UIManager.put("ScrollPane.background", bg);
        UIManager.put("Viewport.background", bg);
        UIManager.put("Viewport.foreground", text);

        // --- TabbedPane ---
        UIManager.put("TabbedPane.background", bg);
        UIManager.put("TabbedPane.foreground", text);
        UIManager.put("TabbedPane.selected", surface);
        UIManager.put("TabbedPane.selectedForeground", accent);
        UIManager.put("TabbedPane.contentAreaColor", bg);

        // --- Menus ---
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

        // --- Toolbar ---
        UIManager.put("ToolBar.background", bg);
        UIManager.put("ToolBar.foreground", text);

        // --- Tooltips ---
        UIManager.put("ToolTip.background", surface);
        UIManager.put("ToolTip.foreground", text);

        // --- OptionPane ---
        UIManager.put("OptionPane.background", bg);
        UIManager.put("OptionPane.messageForeground", text);

        // --- SplitPane ---
        UIManager.put("SplitPane.background", bg);
        UIManager.put("SplitPane.dividerFocusColor", accent);

        // --- Tree ---
        UIManager.put("Tree.background", surface);
        UIManager.put("Tree.foreground", text);
        UIManager.put("Tree.textBackground", surface);
        UIManager.put("Tree.textForeground", text);
        UIManager.put("Tree.selectionBackground", selBg);
        UIManager.put("Tree.selectionForeground", selFg);

        // --- ScrollBar ---
        UIManager.put("ScrollBar.background", bg);
        UIManager.put("ScrollBar.thumb", border);
        UIManager.put("ScrollBar.thumbHighlight", accent);

        // --- Separators ---
        UIManager.put("Separator.foreground", border);
        UIManager.put("Separator.background", bg);

        // --- CheckBox / RadioButton ---
        UIManager.put("CheckBox.background", bg);
        UIManager.put("CheckBox.foreground", text);
        UIManager.put("RadioButton.background", bg);
        UIManager.put("RadioButton.foreground", text);

        // --- Spinner ---
        UIManager.put("Spinner.background", inputBg);
        UIManager.put("Spinner.foreground", inputText);

        // --- FileChooser ---
        UIManager.put("FileChooser.listViewBackground", surface);

        // --- ProgressBar ---
        UIManager.put("ProgressBar.background", bg);
        UIManager.put("ProgressBar.foreground", accent);

        // --- TitledBorder ---
        UIManager.put("TitledBorder.titleColor", accent);
    }

    /**
     * Helper to set all properties for a text component type.
     */
    private void setTextComponent(String prefix,
                                  ColorUIResource bg, ColorUIResource fg,
                                  ColorUIResource caret,
                                  ColorUIResource selBg, ColorUIResource selFg,
                                  ColorUIResource disabled) {
        UIManager.put(prefix + ".background", bg);
        UIManager.put(prefix + ".foreground", fg);
        UIManager.put(prefix + ".caretForeground", caret);
        UIManager.put(prefix + ".selectionBackground", selBg);
        UIManager.put(prefix + ".selectionForeground", selFg);
        UIManager.put(prefix + ".inactiveForeground", disabled);
    }

    /**
     * Refresh all open windows to reflect the new theme.
     * Also applies theme to RSyntaxTextArea instances (which don't use UIManager defaults).
     */
    private void refreshAllWindows() {
        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
            applyThemeToComponentTree(window);
            window.revalidate();
            window.repaint();
        }
    }

    /**
     * Recursively apply theme colors to components that don't respect UIManager defaults.
     * This covers RSyntaxTextArea, JEditorPane (HTML preview), etc.
     */
    private void applyThemeToComponentTree(Component root) {
        if (root instanceof org.fife.ui.rsyntaxtextarea.RSyntaxTextArea) {
            applyEditorTheme((org.fife.ui.rsyntaxtextarea.RSyntaxTextArea) root);
        }
        if (root instanceof JEditorPane && !(root instanceof javax.swing.JTextPane)) {
            applyThemeToEditorPane((JEditorPane) root);
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                applyThemeToComponentTree(child);
            }
        }
    }

    /**
     * Apply theme to RSyntaxTextArea – background, foreground, caret, selection, current line.
     * RSyntaxTextArea uses its own color scheme and ignores UIManager text component defaults.
     * Can be called directly when creating new editor instances.
     */
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

        // RSyntaxTextArea gutter (line numbers)
        Component parent = area.getParent();
        if (parent != null) {
            parent = parent.getParent(); // RTextScrollPane
        }
        if (parent instanceof org.fife.ui.rtextarea.RTextScrollPane) {
            org.fife.ui.rtextarea.RTextScrollPane rsp = (org.fife.ui.rtextarea.RTextScrollPane) parent;
            org.fife.ui.rtextarea.Gutter gutter = rsp.getGutter();
            if (gutter != null) {
                gutter.setBackground(t.isDark ? t.bg : new Color(245, 245, 245));
                gutter.setLineNumberColor(t.isDark ? t.textSecondary : new Color(120, 120, 120));
                gutter.setBorderColor(t.isDark ? t.border : new Color(220, 220, 220));
            }
        }

        // Update syntax scheme colors for dark themes
        if (t.isDark) {
            org.fife.ui.rsyntaxtextarea.SyntaxScheme scheme = area.getSyntaxScheme();
            if (scheme != null) {
                // Make all token types use the theme's text color as baseline
                for (int i = 0; i < scheme.getStyleCount(); i++) {
                    org.fife.ui.rsyntaxtextarea.Style style = scheme.getStyle(i);
                    if (style != null && style.foreground != null) {
                        // Lighten dark foreground colors that would be invisible on dark bg
                        if (isTooDark(style.foreground, t.editorBg)) {
                            style.foreground = t.text;
                        }
                    }
                }
            }
        }
    }

    /**
     * Apply theme to JEditorPane (HTML preview pane).
     */
    private void applyThemeToEditorPane(JEditorPane pane) {
        AppTheme t = currentTheme;
        if (t.isDark) {
            pane.setBackground(t.surface);
            pane.setForeground(t.text);
            pane.setCaretColor(t.caretColor);
            // Update the HTML stylesheet for dark mode
            if (pane.getEditorKit() instanceof javax.swing.text.html.HTMLEditorKit) {
                javax.swing.text.html.HTMLEditorKit kit = (javax.swing.text.html.HTMLEditorKit) pane.getEditorKit();
                javax.swing.text.html.StyleSheet ss = kit.getStyleSheet();
                String bgHex = colorToHex(t.surface);
                String fgHex = colorToHex(t.text);
                String accentHex = colorToHex(t.accent);
                String borderHex = colorToHex(t.border);
                String codeBgHex = colorToHex(t.editorBg);
                ss.addRule("body { background-color: " + bgHex + "; color: " + fgHex + "; }");
                ss.addRule("a { color: " + accentHex + "; }");
                ss.addRule("pre { background-color: " + codeBgHex + "; color: " + fgHex + "; }");
                ss.addRule("code { background-color: " + codeBgHex + "; color: " + fgHex + "; }");
                ss.addRule("th { background-color: " + colorToHex(t.bg) + "; color: " + fgHex + "; }");
                ss.addRule("th, td { border-color: " + borderHex + "; }");
                ss.addRule("blockquote { border-left-color: " + borderHex + "; color: " + colorToHex(t.textSecondary) + "; }");
                ss.addRule("h1, h2, h3, h4, h5, h6 { color: " + accentHex + "; }");
            }
        } else {
            // Reset to light defaults
            pane.setBackground(Color.WHITE);
            pane.setForeground(new Color(51, 51, 51));
            if (pane.getEditorKit() instanceof javax.swing.text.html.HTMLEditorKit) {
                javax.swing.text.html.HTMLEditorKit kit = (javax.swing.text.html.HTMLEditorKit) pane.getEditorKit();
                javax.swing.text.html.StyleSheet ss = kit.getStyleSheet();
                ss.addRule("body { background-color: #ffffff; color: #333333; }");
                ss.addRule("a { color: #0078d7; }");
                ss.addRule("pre { background-color: #f5f5f5; color: #333333; }");
                ss.addRule("code { background-color: #f5f5f5; color: #333333; }");
                ss.addRule("th { background-color: #f0f0f0; color: #333333; }");
                ss.addRule("th, td { border-color: #ddd; }");
                ss.addRule("blockquote { border-left-color: #ddd; color: #666; }");
                ss.addRule("h1, h2, h3, h4, h5, h6 { color: #333333; }");
            }
        }
    }

    /**
     * Check if a foreground color would be too dark (invisible) on the given background.
     */
    private boolean isTooDark(Color fg, Color bg) {
        double fgLum = luminance(fg);
        double bgLum = luminance(bg);
        // Contrast ratio: if below 2:1, it's too hard to read
        double lighter = Math.max(fgLum, bgLum);
        double darker = Math.min(fgLum, bgLum);
        double ratio = (lighter + 0.05) / (darker + 0.05);
        return ratio < 2.0;
    }

    private double luminance(Color c) {
        double r = c.getRed() / 255.0;
        double g = c.getGreen() / 255.0;
        double b = c.getBlue() / 255.0;
        r = r <= 0.03928 ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = g <= 0.03928 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = b <= 0.03928 ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static String colorToHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /**
     * Convert Color to ColorUIResource (required for UIManager defaults).
     */
    private static ColorUIResource c(Color color) {
        return new ColorUIResource(color);
    }
}

