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
     */
    private void refreshAllWindows() {
        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
            window.revalidate();
            window.repaint();
        }
    }

    /**
     * Convert Color to ColorUIResource (required for UIManager defaults).
     */
    private static ColorUIResource c(Color color) {
        return new ColorUIResource(color);
    }
}

