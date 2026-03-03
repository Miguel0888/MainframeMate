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
    private final String systemLafClassName;

    private ThemeManager() {
        // Remember the system L&F so we can restore it for Classic theme
        systemLafClassName = UIManager.getSystemLookAndFeelClassName();
    }

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
     * For dark themes, switches to Metal L&F so UIManager defaults are fully honored
     * (Windows L&F uses native rendering and ignores many color overrides).
     */
    public void applyTheme(AppTheme theme) {
        if (theme == null) theme = AppTheme.CLASSIC;
        this.currentTheme = theme;

        if (theme == AppTheme.CLASSIC) {
            switchLookAndFeel(systemLafClassName);
            applyClassicTheme();
        } else {
            // Metal L&F respects all UIManager.put() color overrides
            switchLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
            applyDarkTheme(theme);
        }

        refreshAllWindows();
    }

    /**
     * Switch the global Look & Feel. Fails silently if not available.
     */
    private void switchLookAndFeel(String lafClassName) {
        try {
            String currentLaf = UIManager.getLookAndFeel().getClass().getName();
            if (!currentLaf.equals(lafClassName)) {
                UIManager.setLookAndFeel(lafClassName);
            }
        } catch (Exception e) {
            System.err.println("[ThemeManager] Could not switch L&F to " + lafClassName + ": " + e.getMessage());
        }
    }

    /**
     * Classic theme: the system L&F has already been restored by switchLookAndFeel().
     * We just need to remove any explicit overrides we set (borders, gradient nulls, etc.)
     * that would persist across L&F changes.
     */
    private void applyClassicTheme() {
        // Remove our explicit overrides – the system L&F provides correct defaults
        String[] keysToRemove = {
                // Borders we set explicitly
                "Button.border", "Button.gradient",
                "ToggleButton.border", "ToggleButton.gradient",
                "TextField.border", "TextArea.border",
                "PasswordField.border", "FormattedTextField.border",
                "Spinner.border", "Spinner.arrowButtonBackground", "Spinner.arrowButtonBorder",
                "ToolTip.border",
                // SplitPane extras
                "SplitPane.darkShadow", "SplitPane.shadow", "SplitPane.highlight",
                "SplitPaneDivider.draggingColor", "SplitPane.dividerSize",
                // Button extras
                "Button.shadow", "Button.darkShadow", "Button.light", "Button.highlight",
                "ToggleButton.shadow", "ToggleButton.darkShadow", "ToggleButton.light", "ToggleButton.highlight",
                // ComboBox extras
                "ComboBox.buttonShadow", "ComboBox.buttonDarkShadow", "ComboBox.buttonHighlight",
                // ScrollBar extras
                "ScrollBar.thumbShadow", "ScrollBar.thumbDarkShadow", "ScrollBar.track",
                "ScrollBar.trackHighlight", "ScrollBar.darkShadow", "ScrollBar.shadow", "ScrollBar.highlight",
                // TabbedPane extras
                "TabbedPane.tabAreaBackground", "TabbedPane.light", "TabbedPane.highlight",
                "TabbedPane.shadow", "TabbedPane.darkShadow", "TabbedPane.focus",
                "TabbedPane.unselectedBackground",
                // FormattedTextField
                "FormattedTextField.background", "FormattedTextField.foreground",
                "FormattedTextField.caretForeground", "FormattedTextField.selectionBackground",
                "FormattedTextField.selectionForeground", "FormattedTextField.inactiveForeground",
                // ToggleButton
                "ToggleButton.background", "ToggleButton.foreground", "ToggleButton.select",
        };

        for (String key : keysToRemove) {
            UIManager.put(key, null);
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

        // --- Panels / Frames / Windows ---
        UIManager.put("Panel.background", bg);
        UIManager.put("Panel.foreground", text);
        UIManager.put("window", bg);
        UIManager.put("control", bg);
        UIManager.put("text", inputBg);
        UIManager.put("textText", inputText);
        UIManager.put("controlText", text);
        UIManager.put("infoText", text);
        UIManager.put("info", surface);
        UIManager.put("activeCaption", bg);
        UIManager.put("activeCaptionText", text);
        UIManager.put("inactiveCaption", bg);
        UIManager.put("inactiveCaptionText", disabled);
        UIManager.put("desktop", bg);
        UIManager.put("InternalFrame.activeTitleBackground", bg);
        UIManager.put("InternalFrame.activeTitleForeground", text);
        UIManager.put("InternalFrame.inactiveTitleBackground", bg);
        UIManager.put("InternalFrame.inactiveTitleForeground", disabled);
        UIManager.put("RootPane.background", bg);
        UIManager.put("ContentPane.background", bg);

        // --- Labels ---
        UIManager.put("Label.foreground", text);
        UIManager.put("Label.disabledForeground", disabled);

        // --- Buttons ---
        UIManager.put("Button.background", surface);
        UIManager.put("Button.foreground", text);
        UIManager.put("Button.select", accent);
        UIManager.put("Button.focus", accent);
        UIManager.put("Button.shadow", border);
        UIManager.put("Button.darkShadow", border);
        UIManager.put("Button.light", surface);
        UIManager.put("Button.highlight", surface);
        UIManager.put("Button.border", new LineBorder(border, 1));
        UIManager.put("Button.gradient", null); // disable Metal gradient
        UIManager.put("ToggleButton.background", surface);
        UIManager.put("ToggleButton.foreground", text);
        UIManager.put("ToggleButton.select", accent);
        UIManager.put("ToggleButton.shadow", border);
        UIManager.put("ToggleButton.darkShadow", border);
        UIManager.put("ToggleButton.light", surface);
        UIManager.put("ToggleButton.highlight", surface);
        UIManager.put("ToggleButton.border", new LineBorder(border, 1));
        UIManager.put("ToggleButton.gradient", null);

        // --- Text Fields ---
        setTextComponent("TextField", inputBg, inputText, caret, selBg, selFg, disabled);
        setTextComponent("TextArea", inputBg, inputText, caret, selBg, selFg, disabled);
        setTextComponent("TextPane", inputBg, inputText, caret, selBg, selFg, disabled);
        setTextComponent("EditorPane", inputBg, inputText, caret, selBg, selFg, disabled);
        setTextComponent("PasswordField", inputBg, inputText, caret, selBg, selFg, disabled);
        setTextComponent("FormattedTextField", inputBg, inputText, caret, selBg, selFg, disabled);
        // Borders for text fields
        UIManager.put("TextField.border", new LineBorder(border, 1));
        UIManager.put("TextArea.border", new LineBorder(border, 1));
        UIManager.put("PasswordField.border", new LineBorder(border, 1));
        UIManager.put("FormattedTextField.border", new LineBorder(border, 1));

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
        UIManager.put("ComboBox.buttonShadow", border);
        UIManager.put("ComboBox.buttonDarkShadow", border);
        UIManager.put("ComboBox.buttonHighlight", surface);

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
        UIManager.put("TabbedPane.tabAreaBackground", bg);
        UIManager.put("TabbedPane.light", surface);
        UIManager.put("TabbedPane.highlight", surface);
        UIManager.put("TabbedPane.shadow", border);
        UIManager.put("TabbedPane.darkShadow", border);
        UIManager.put("TabbedPane.focus", accent);
        UIManager.put("TabbedPane.unselectedBackground", bg);

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
        UIManager.put("ToolTip.border", new LineBorder(border, 1));

        // --- OptionPane ---
        UIManager.put("OptionPane.background", bg);
        UIManager.put("OptionPane.messageForeground", text);

        // --- SplitPane ---
        UIManager.put("SplitPane.background", bg);
        UIManager.put("SplitPane.dividerFocusColor", accent);
        UIManager.put("SplitPane.darkShadow", border);
        UIManager.put("SplitPane.shadow", border);
        UIManager.put("SplitPane.highlight", surface);
        UIManager.put("SplitPaneDivider.draggingColor", accent);
        UIManager.put("SplitPane.dividerSize", 6);

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
        UIManager.put("ScrollBar.thumbShadow", border);
        UIManager.put("ScrollBar.thumbDarkShadow", bg);
        UIManager.put("ScrollBar.track", bg);
        UIManager.put("ScrollBar.trackHighlight", surface);
        UIManager.put("ScrollBar.darkShadow", bg);
        UIManager.put("ScrollBar.shadow", bg);
        UIManager.put("ScrollBar.highlight", surface);

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
        UIManager.put("Spinner.border", new LineBorder(border, 1));
        UIManager.put("Spinner.arrowButtonBackground", surface);
        UIManager.put("Spinner.arrowButtonBorder", new LineBorder(border, 1));

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
     * Also applies theme to RSyntaxTextArea instances (which don't use UIManager defaults),
     * and forces dark background on JToolBar/JMenuBar.
     */
    private void refreshAllWindows() {
        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
            applyThemeToComponentTree(window);

            // Apply Windows dark title bar if available
            applyWindowsTitleBarTheme(window);

            window.revalidate();
            window.repaint();
        }
    }

    /**
     * Recursively apply theme colors to components that don't respect UIManager defaults.
     * This covers RSyntaxTextArea, JEditorPane (HTML preview), JToolBar, JMenuBar, etc.
     */
    private void applyThemeToComponentTree(Component root) {
        if (root instanceof org.fife.ui.rsyntaxtextarea.RSyntaxTextArea) {
            applyEditorTheme((org.fife.ui.rsyntaxtextarea.RSyntaxTextArea) root);
        }
        if (root instanceof JEditorPane && !(root instanceof javax.swing.JTextPane)) {
            applyThemeToEditorPane((JEditorPane) root);
        }
        // JToolBar often ignores UIManager defaults — force colors directly
        if (root instanceof JToolBar) {
            JToolBar tb = (JToolBar) root;
            tb.setBackground(currentTheme.bg);
            tb.setForeground(currentTheme.text);
            tb.setOpaque(true);
            // Also style buttons inside the toolbar
            for (Component child : tb.getComponents()) {
                if (child instanceof AbstractButton) {
                    child.setBackground(currentTheme.bg);
                    child.setForeground(currentTheme.text);
                }
            }
        }
        // JMenuBar often ignores UIManager defaults — force colors directly
        if (root instanceof JMenuBar) {
            JMenuBar mb = (JMenuBar) root;
            mb.setBackground(currentTheme.bg);
            mb.setForeground(currentTheme.text);
            mb.setOpaque(true);
            mb.setBorderPainted(currentTheme.isDark);
            if (currentTheme.isDark) {
                mb.setBorder(javax.swing.BorderFactory.createMatteBorder(
                        0, 0, 1, 0, currentTheme.border));
            }
            for (int i = 0; i < mb.getMenuCount(); i++) {
                JMenu menu = mb.getMenu(i);
                if (menu != null) {
                    menu.setBackground(currentTheme.bg);
                    menu.setForeground(currentTheme.text);
                    menu.setOpaque(true);
                }
            }
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

    // ==================== Windows Dark Title Bar ====================

    /**
     * On Windows 10 (build 17763+) / Windows 11, uses DwmSetWindowAttribute
     * to enable/disable the dark title bar for the given window.
     * This is the official Microsoft API for dark mode title bars.
     * Falls back gracefully on non-Windows or older Windows versions.
     */
    private void applyWindowsTitleBarTheme(Window window) {
        if (!(window instanceof JFrame) && !(window instanceof JDialog)) return;

        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("windows")) return;

        try {
            // DWMWA_USE_IMMERSIVE_DARK_MODE = 20 (Windows 10 20H1+, Windows 11)
            // Older Windows 10 builds use undocumented attribute 19
            long hwnd = getWindowHandle(window);
            if (hwnd == 0) return;

            com.sun.jna.platform.win32.WinDef.HWND hWnd =
                    new com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Pointer.createConstant(hwnd));

            // Try attribute 20 first (Windows 10 20H1+), fallback to 19
            com.sun.jna.Memory darkMode = new com.sun.jna.Memory(4);
            darkMode.setInt(0, currentTheme.isDark ? 1 : 0);

            com.sun.jna.platform.win32.WinNT.HRESULT result =
                    Dwmapi.INSTANCE.DwmSetWindowAttribute(hWnd, 20, darkMode, 4);

            if (result != null && result.intValue() != 0) {
                // Fallback to attribute 19 for older Windows 10 builds
                Dwmapi.INSTANCE.DwmSetWindowAttribute(hWnd, 19, darkMode, 4);
            }

            // Force Windows to redraw the title bar
            // Toggle visibility to trigger a non-client area repaint
            if (window.isVisible()) {
                Dimension size = window.getSize();
                window.setSize(size.width, size.height + 1);
                window.setSize(size.width, size.height);
            }
        } catch (Throwable t) {
            // Not critical — fall back silently
            System.err.println("[ThemeManager] Windows title bar dark mode not available: " + t.getMessage());
        }
    }

    /**
     * Get the native window handle (HWND) from a Swing Window.
     * Uses the sun.awt.windows.WComponentPeer internal API.
     */
    private long getWindowHandle(Window window) {
        try {
            // Access the peer's HWND via reflection (works on Oracle/OpenJDK on Windows)
            if (window.isDisplayable()) {
                // Java 8+: window peer has getHWnd()
                Object peer = getComponentPeer(window);
                if (peer != null) {
                    java.lang.reflect.Method getHWnd = peer.getClass().getMethod("getHWnd");
                    getHWnd.setAccessible(true);
                    return (Long) getHWnd.invoke(peer);
                }
            }
        } catch (Throwable t) {
            // Fallback: try Native.getWindowID from JNA
            try {
                return com.sun.jna.Native.getWindowID(window);
            } catch (Throwable t2) {
                // not available
            }
        }
        return 0;
    }

    @SuppressWarnings("deprecation")
    private Object getComponentPeer(Window window) {
        try {
            // In Java 8, getPeer() is public but deprecated
            java.lang.reflect.Method getPeer = Component.class.getDeclaredMethod("getPeer");
            getPeer.setAccessible(true);
            return getPeer.invoke(window);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * JNA interface for the Windows DWM (Desktop Window Manager) API.
     */
    private interface Dwmapi extends com.sun.jna.Library {
        Dwmapi INSTANCE = com.sun.jna.Native.load("dwmapi", Dwmapi.class);

        com.sun.jna.platform.win32.WinNT.HRESULT DwmSetWindowAttribute(
                com.sun.jna.platform.win32.WinDef.HWND hwnd,
                int dwAttribute,
                com.sun.jna.Pointer pvAttribute,
                int cbAttribute
        );
    }
}

