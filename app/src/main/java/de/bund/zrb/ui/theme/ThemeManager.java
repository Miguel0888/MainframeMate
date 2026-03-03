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
     * - CLASSIC:       System L&F (native Windows look), no color overrides.
     * - CLASSIC_METAL: Metal L&F, reset to Metal defaults (light theme with Metal controls).
     * - MODERN/RETRO:  Metal L&F with dark color overrides via UIManager.
     */
    public void applyTheme(AppTheme theme) {
        if (theme == null) theme = AppTheme.CLASSIC;
        this.currentTheme = theme;

        if (theme == AppTheme.CLASSIC) {
            // Native Windows look
            switchLookAndFeel(systemLafClassName);
            resetDarkOverrides();
        } else if (theme == AppTheme.CLASSIC_METAL) {
            // Metal L&F with its default light colors
            switchLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
            resetDarkOverrides();
        } else {
            // Dark themes: Metal L&F + full color override
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
     * Reset ALL UIManager keys that applyDarkTheme() overrides.
     * Restores fresh defaults from the currently installed L&F (System or Metal).
     * Called when switching to any light theme (CLASSIC or CLASSIC_METAL).
     */
    private void resetDarkOverrides() {
        // Every key that applyDarkTheme() touches must be restored here
        String[] allOverriddenKeys = {
                // Panels / Frames / Windows
                "Panel.background", "Panel.foreground",
                "window", "control", "text", "textText", "controlText",
                "infoText", "info",
                "activeCaption", "activeCaptionText",
                "inactiveCaption", "inactiveCaptionText",
                "desktop",
                "InternalFrame.activeTitleBackground", "InternalFrame.activeTitleForeground",
                "InternalFrame.inactiveTitleBackground", "InternalFrame.inactiveTitleForeground",
                "RootPane.background", "ContentPane.background",
                // Labels
                "Label.foreground", "Label.disabledForeground",
                // Buttons
                "Button.background", "Button.foreground", "Button.select", "Button.focus",
                "Button.shadow", "Button.darkShadow", "Button.light", "Button.highlight",
                "Button.border", "Button.gradient",
                "ToggleButton.background", "ToggleButton.foreground", "ToggleButton.select",
                "ToggleButton.shadow", "ToggleButton.darkShadow", "ToggleButton.light", "ToggleButton.highlight",
                "ToggleButton.border", "ToggleButton.gradient",
                // Text fields
                "TextField.background", "TextField.foreground", "TextField.caretForeground",
                "TextField.selectionBackground", "TextField.selectionForeground", "TextField.inactiveForeground",
                "TextField.border",
                "TextArea.background", "TextArea.foreground", "TextArea.caretForeground",
                "TextArea.selectionBackground", "TextArea.selectionForeground", "TextArea.inactiveForeground",
                "TextArea.border",
                "TextPane.background", "TextPane.foreground", "TextPane.caretForeground",
                "TextPane.selectionBackground", "TextPane.selectionForeground", "TextPane.inactiveForeground",
                "EditorPane.background", "EditorPane.foreground", "EditorPane.caretForeground",
                "EditorPane.selectionBackground", "EditorPane.selectionForeground", "EditorPane.inactiveForeground",
                "PasswordField.background", "PasswordField.foreground", "PasswordField.caretForeground",
                "PasswordField.selectionBackground", "PasswordField.selectionForeground", "PasswordField.inactiveForeground",
                "PasswordField.border",
                "FormattedTextField.background", "FormattedTextField.foreground", "FormattedTextField.caretForeground",
                "FormattedTextField.selectionBackground", "FormattedTextField.selectionForeground", "FormattedTextField.inactiveForeground",
                "FormattedTextField.border",
                // Tables
                "Table.background", "Table.foreground",
                "Table.selectionBackground", "Table.selectionForeground",
                "Table.gridColor", "Table.focusCellHighlightBorder",
                "TableHeader.background", "TableHeader.foreground",
                // Lists
                "List.background", "List.foreground",
                "List.selectionBackground", "List.selectionForeground",
                // ComboBox
                "ComboBox.background", "ComboBox.foreground",
                "ComboBox.selectionBackground", "ComboBox.selectionForeground",
                "ComboBox.buttonBackground", "ComboBox.buttonShadow", "ComboBox.buttonDarkShadow", "ComboBox.buttonHighlight",
                // ScrollPane / Viewport
                "ScrollPane.background",
                "Viewport.background", "Viewport.foreground",
                // TabbedPane
                "TabbedPane.background", "TabbedPane.foreground",
                "TabbedPane.selected", "TabbedPane.selectedForeground",
                "TabbedPane.contentAreaColor", "TabbedPane.tabAreaBackground",
                "TabbedPane.light", "TabbedPane.highlight",
                "TabbedPane.shadow", "TabbedPane.darkShadow",
                "TabbedPane.focus", "TabbedPane.unselectedBackground",
                // Menus
                "MenuBar.background", "MenuBar.foreground",
                "Menu.background", "Menu.foreground",
                "Menu.selectionBackground", "Menu.selectionForeground",
                "MenuItem.background", "MenuItem.foreground",
                "MenuItem.selectionBackground", "MenuItem.selectionForeground",
                "PopupMenu.background", "PopupMenu.foreground",
                "CheckBoxMenuItem.background", "CheckBoxMenuItem.foreground",
                "CheckBoxMenuItem.selectionBackground", "CheckBoxMenuItem.selectionForeground",
                "RadioButtonMenuItem.background", "RadioButtonMenuItem.foreground",
                // Toolbar
                "ToolBar.background", "ToolBar.foreground",
                // Tooltips
                "ToolTip.background", "ToolTip.foreground", "ToolTip.border",
                // OptionPane
                "OptionPane.background", "OptionPane.messageForeground",
                // SplitPane
                "SplitPane.background", "SplitPane.dividerFocusColor",
                "SplitPane.darkShadow", "SplitPane.shadow", "SplitPane.highlight",
                "SplitPaneDivider.draggingColor", "SplitPane.dividerSize",
                // Tree
                "Tree.background", "Tree.foreground", "Tree.textBackground", "Tree.textForeground",
                "Tree.selectionBackground", "Tree.selectionForeground",
                // ScrollBar
                "ScrollBar.background", "ScrollBar.thumb", "ScrollBar.thumbHighlight",
                "ScrollBar.thumbShadow", "ScrollBar.thumbDarkShadow",
                "ScrollBar.track", "ScrollBar.trackHighlight",
                "ScrollBar.darkShadow", "ScrollBar.shadow", "ScrollBar.highlight",
                // Separators
                "Separator.foreground", "Separator.background",
                // CheckBox / RadioButton
                "CheckBox.background", "CheckBox.foreground",
                "RadioButton.background", "RadioButton.foreground",
                // Spinner
                "Spinner.background", "Spinner.foreground",
                "Spinner.border", "Spinner.arrowButtonBackground", "Spinner.arrowButtonBorder",
                // FileChooser
                "FileChooser.listViewBackground",
                // ProgressBar
                "ProgressBar.background", "ProgressBar.foreground",
                // TitledBorder
                "TitledBorder.titleColor",
        };

        // Get fresh defaults from the newly installed L&F
        UIDefaults freshDefaults = UIManager.getLookAndFeel().getDefaults();
        for (String key : allOverriddenKeys) {
            Object freshValue = freshDefaults.get(key);
            UIManager.put(key, freshValue); // null is fine — removes the override
        }

        // Restore default UI delegates that we replaced with custom accent versions
        String[] uiDelegateKeys = {"ButtonUI", "ToggleButtonUI", "ScrollBarUI"};
        for (String key : uiDelegateKeys) {
            Object freshValue = freshDefaults.get(key);
            if (freshValue != null) {
                UIManager.put(key, freshValue);
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

        // --- Buttons (accent fill via custom UI) ---
        UIManager.put("ButtonUI", "de.bund.zrb.ui.theme.AccentButtonUI");
        UIManager.put("ToggleButtonUI", "de.bund.zrb.ui.theme.AccentButtonUI");
        UIManager.put("Button.background", accent);
        UIManager.put("Button.foreground", c(t.isDark ? Color.BLACK : Color.WHITE));
        UIManager.put("Button.select", c(t.accentHover));
        UIManager.put("Button.focus", c(t.accentHover));
        UIManager.put("Button.shadow", accent);
        UIManager.put("Button.darkShadow", accent);
        UIManager.put("Button.light", c(t.accentHover));
        UIManager.put("Button.highlight", c(t.accentHover));
        UIManager.put("Button.gradient", null);
        UIManager.put("ToggleButton.background", accent);
        UIManager.put("ToggleButton.foreground", c(t.isDark ? Color.BLACK : Color.WHITE));
        UIManager.put("ToggleButton.select", c(t.accentHover));
        UIManager.put("ToggleButton.shadow", accent);
        UIManager.put("ToggleButton.darkShadow", accent);
        UIManager.put("ToggleButton.light", c(t.accentHover));
        UIManager.put("ToggleButton.highlight", c(t.accentHover));
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

        // --- ScrollBar (accent fill via custom UI) ---
        UIManager.put("ScrollBarUI", "de.bund.zrb.ui.theme.AccentScrollBarUI");
        UIManager.put("ScrollBar.background", surface);
        UIManager.put("ScrollBar.thumb", accent);
        UIManager.put("ScrollBar.thumbHighlight", c(t.accentHover));
        UIManager.put("ScrollBar.thumbShadow", accent);
        UIManager.put("ScrollBar.thumbDarkShadow", accent);
        UIManager.put("ScrollBar.track", surface);
        UIManager.put("ScrollBar.trackHighlight", accent);
        UIManager.put("ScrollBar.darkShadow", accent);
        UIManager.put("ScrollBar.shadow", accent);
        UIManager.put("ScrollBar.highlight", c(t.accentHover));

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
    public void refreshAllWindows() {
        for (Window window : Window.getWindows()) {
            refreshWindow(window);
        }
    }

    /**
     * Refresh a single window to apply the current theme.
     * Call this after creating a new window/dialog if the theme was applied before the window existed.
     */
    public void refreshWindow(Window window) {
        if (window == null) return;
        SwingUtilities.updateComponentTreeUI(window);
        applyThemeToComponentTree(window);

        // JMenuBar is NOT part of getComponents() — must be handled explicitly
        if (window instanceof JFrame) {
            JMenuBar mb = ((JFrame) window).getJMenuBar();
            if (mb != null) {
                SwingUtilities.updateComponentTreeUI(mb);
                applyThemeToComponentTree(mb);
            }
        }

        applyWindowsTitleBarTheme(window);
        window.revalidate();
        window.repaint();
    }


    /**
     * Recursively apply theme colors to components that don't respect UIManager defaults.
     * This covers RSyntaxTextArea, JEditorPane (HTML preview), JToolBar, JMenuBar, etc.
     * For Classic theme this also RESETS manually set colors so the system L&F takes over again.
     */
    private void applyThemeToComponentTree(Component root) {
        if (root instanceof org.fife.ui.rsyntaxtextarea.RSyntaxTextArea) {
            applyEditorTheme((org.fife.ui.rsyntaxtextarea.RSyntaxTextArea) root);
        }
        if (root instanceof JEditorPane && !(root instanceof javax.swing.JTextPane)) {
            applyThemeToEditorPane((JEditorPane) root);
        }

        if (root instanceof JToolBar) {
            JToolBar tb = (JToolBar) root;
            if (currentTheme.isDark) {
                tb.setBackground(currentTheme.bg);
                tb.setForeground(currentTheme.text);
                tb.setOpaque(true);
                for (Component child : tb.getComponents()) {
                    if (child instanceof AbstractButton) {
                        child.setBackground(currentTheme.bg);
                        child.setForeground(currentTheme.text);
                    }
                }
            } else {
                // Reset: let L&F decide
                tb.setBackground(null);
                tb.setForeground(null);
                tb.setOpaque(false);
                for (Component child : tb.getComponents()) {
                    if (child instanceof AbstractButton) {
                        child.setBackground(null);
                        child.setForeground(null);
                    }
                }
            }
        }

        if (root instanceof JMenuBar) {
            JMenuBar mb = (JMenuBar) root;
            if (currentTheme.isDark) {
                mb.setBackground(currentTheme.bg);
                mb.setForeground(currentTheme.text);
                mb.setOpaque(true);
                mb.setBorderPainted(true);
                mb.setBorder(javax.swing.BorderFactory.createMatteBorder(
                        0, 0, 1, 0, currentTheme.border));
                for (int i = 0; i < mb.getMenuCount(); i++) {
                    JMenu menu = mb.getMenu(i);
                    if (menu != null) {
                        menu.setBackground(currentTheme.bg);
                        menu.setForeground(currentTheme.text);
                        menu.setOpaque(true);
                    }
                }
            } else {
                // Reset: let L&F decide
                mb.setBackground(null);
                mb.setForeground(null);
                mb.setOpaque(false);
                mb.setBorderPainted(false);
                mb.setBorder(null);
                for (int i = 0; i < mb.getMenuCount(); i++) {
                    JMenu menu = mb.getMenu(i);
                    if (menu != null) {
                        menu.setBackground(null);
                        menu.setForeground(null);
                        menu.setOpaque(false);
                    }
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
     *
     * For Classic theme: resets all colors to RSyntaxTextArea defaults.
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

        // Restore or adjust syntax scheme colors
        org.fife.ui.rsyntaxtextarea.SyntaxScheme scheme = area.getSyntaxScheme();
        if (scheme != null) {
            if (t.isDark) {
                // Lighten dark foreground colors that would be invisible on dark bg
                for (int i = 0; i < scheme.getStyleCount(); i++) {
                    org.fife.ui.rsyntaxtextarea.Style style = scheme.getStyle(i);
                    if (style != null && style.foreground != null) {
                        if (isTooDark(style.foreground, t.editorBg)) {
                            style.foreground = t.text;
                        }
                    }
                }
            } else {
                // Classic: restore the default syntax scheme
                // Create a temporary RSyntaxTextArea to get a fresh default scheme
                String syntaxStyle = area.getSyntaxEditingStyle();
                try {
                    org.fife.ui.rsyntaxtextarea.RSyntaxTextArea tmp = new org.fife.ui.rsyntaxtextarea.RSyntaxTextArea();
                    area.setSyntaxScheme(tmp.getSyntaxScheme());
                } catch (Exception ignored) {}
                // Re-apply the syntax style to trigger re-tokenization
                area.setSyntaxEditingStyle(syntaxStyle);
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
     * The window must be visible (native peer must exist) for this to work.
     */
    private void applyWindowsTitleBarTheme(Window window) {
        if (!(window instanceof JFrame) && !(window instanceof JDialog)) return;
        if (!window.isDisplayable() || !window.isVisible()) return;

        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("windows")) return;

        try {
            long hwnd = getWindowHandle(window);
            if (hwnd == 0) {
                System.err.println("[ThemeManager] Could not get HWND for window: " + window.getName());
                return;
            }

            com.sun.jna.platform.win32.WinDef.HWND hWnd =
                    new com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Pointer.createConstant(hwnd));

            // DWMWA_USE_IMMERSIVE_DARK_MODE = 20 (Windows 10 20H1+, Windows 11)
            com.sun.jna.Memory darkMode = new com.sun.jna.Memory(4);
            darkMode.setInt(0, currentTheme.isDark ? 1 : 0);

            com.sun.jna.platform.win32.WinNT.HRESULT result =
                    Dwmapi.INSTANCE.DwmSetWindowAttribute(hWnd, 20, darkMode, 4);

            if (result != null && result.intValue() != 0) {
                // Fallback to attribute 19 for older Windows 10 builds
                Dwmapi.INSTANCE.DwmSetWindowAttribute(hWnd, 19, darkMode, 4);
            }

            // Force Windows to redraw the title bar by toggling the window size
            Dimension size = window.getSize();
            window.setSize(size.width, size.height + 1);
            window.setSize(size.width, size.height);

        } catch (Throwable t) {
            System.err.println("[ThemeManager] Windows title bar dark mode not available: " + t.getMessage());
        }
    }

    /**
     * Get the native window handle (HWND) from a Swing Window.
     * Tries JNA Native.getWindowID first (most reliable), then reflection fallback.
     */
    private long getWindowHandle(Window window) {
        // Try JNA first — most reliable for visible windows
        try {
            long hwnd = com.sun.jna.Native.getWindowID(window);
            if (hwnd != 0) return hwnd;
        } catch (Throwable ignored) {}

        // Fallback: reflection via sun.awt peer
        try {
            if (window.isDisplayable()) {
                Object peer = getComponentPeer(window);
                if (peer != null) {
                    java.lang.reflect.Method getHWnd = peer.getClass().getMethod("getHWnd");
                    getHWnd.setAccessible(true);
                    return (Long) getHWnd.invoke(peer);
                }
            }
        } catch (Throwable ignored) {}

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

