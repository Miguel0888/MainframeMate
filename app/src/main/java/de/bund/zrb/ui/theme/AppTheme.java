package de.bund.zrb.ui.theme;

import de.bund.zrb.ui.lock.LockerStyle;

import java.awt.Color;

/**
 * Defines the color tokens for each application theme.
 * Each theme provides a complete set of UI colors for the entire application.
 */
public enum AppTheme {

    CLASSIC(
            "Classic",
            new Color(240, 240, 240),   // bg
            Color.WHITE,                 // surface
            new Color(51, 51, 51),       // text
            new Color(100, 100, 100),    // textSecondary
            new Color(0, 120, 215),      // accent (Windows blue)
            new Color(0, 102, 204),      // accentHover
            new Color(0, 120, 215),      // selection
            Color.WHITE,                 // selectionText
            new Color(200, 200, 200),    // border
            Color.WHITE,                 // inputBg
            new Color(51, 51, 51),       // inputText
            new Color(160, 160, 160),    // disabledText
            new Color(200, 0, 0),        // errorText
            new Color(51, 51, 51),       // caretColor
            new Color(204, 232, 255),    // tableSelectionBg
            new Color(51, 51, 51),       // tableSelectionFg
            new Color(230, 230, 230),    // tableGridColor
            Color.WHITE,                 // editorBg (RSyntaxTextArea)
            new Color(232, 242, 254),    // editorCurrentLineBg
            false                        // isDark
    ),

    MODERN(
            "Modern (Dark Orange)",
            new Color(30, 30, 30),       // bg         (#1E1E1E)
            new Color(37, 37, 38),       // surface     (#252526)
            new Color(212, 212, 212),    // text        (#D4D4D4)
            new Color(150, 150, 150),    // textSecondary
            new Color(255, 106, 0),      // accent      (#FF6A00) Orange
            new Color(255, 140, 50),     // accentHover
            new Color(255, 106, 0),      // selection
            Color.WHITE,                 // selectionText
            new Color(60, 60, 60),       // border
            new Color(45, 45, 45),       // inputBg
            new Color(220, 220, 220),    // inputText
            new Color(100, 100, 100),    // disabledText
            new Color(255, 80, 80),      // errorText
            new Color(255, 106, 0),      // caretColor  (orange)
            new Color(80, 50, 0),        // tableSelectionBg
            new Color(255, 220, 180),    // tableSelectionFg
            new Color(55, 55, 55),       // tableGridColor
            new Color(40, 40, 40),       // editorBg (dunkelgrau)
            new Color(50, 50, 50),       // editorCurrentLineBg
            true                         // isDark
    ),

    RETRO(
            "Retro (Dark Green)",
            new Color(10, 10, 10),       // bg          (#0A0A0A)
            new Color(18, 18, 18),       // surface      (#121212)
            new Color(0, 255, 65),       // text         (#00FF41) green
            new Color(0, 180, 45),       // textSecondary
            new Color(0, 255, 65),       // accent       (#00FF41) green
            new Color(50, 255, 100),     // accentHover
            new Color(0, 100, 25),       // selection
            new Color(0, 255, 65),       // selectionText
            new Color(0, 80, 20),        // border
            new Color(15, 15, 15),       // inputBg
            new Color(0, 230, 60),       // inputText
            new Color(0, 100, 25),       // disabledText
            new Color(255, 60, 60),      // errorText
            new Color(0, 255, 65),       // caretColor  (green)
            new Color(0, 60, 15),        // tableSelectionBg
            new Color(0, 255, 65),       // tableSelectionFg
            new Color(0, 50, 12),        // tableGridColor
            new Color(8, 8, 8),          // editorBg (anthrazit/fast-schwarz)
            new Color(15, 20, 15),       // editorCurrentLineBg
            true                         // isDark
    ),

    CLASSIC_METAL(
            "Classic (Metal)",
            new Color(240, 240, 240),   // bg
            Color.WHITE,                 // surface
            new Color(51, 51, 51),       // text
            new Color(100, 100, 100),    // textSecondary
            new Color(0, 120, 215),      // accent (Windows blue)
            new Color(0, 102, 204),      // accentHover
            new Color(0, 120, 215),      // selection
            Color.WHITE,                 // selectionText
            new Color(200, 200, 200),    // border
            Color.WHITE,                 // inputBg
            new Color(51, 51, 51),       // inputText
            new Color(160, 160, 160),    // disabledText
            new Color(200, 0, 0),        // errorText
            new Color(51, 51, 51),       // caretColor
            new Color(204, 232, 255),    // tableSelectionBg
            new Color(51, 51, 51),       // tableSelectionFg
            new Color(230, 230, 230),    // tableGridColor
            Color.WHITE,                 // editorBg (RSyntaxTextArea)
            new Color(232, 242, 254),    // editorCurrentLineBg
            false                        // isDark
    );

    private final String displayName;
    public final Color bg;
    public final Color surface;
    public final Color text;
    public final Color textSecondary;
    public final Color accent;
    public final Color accentHover;
    public final Color selection;
    public final Color selectionText;
    public final Color border;
    public final Color inputBg;
    public final Color inputText;
    public final Color disabledText;
    public final Color errorText;
    public final Color caretColor;
    public final Color tableSelectionBg;
    public final Color tableSelectionFg;
    public final Color tableGridColor;
    public final Color editorBg;            // RSyntaxTextArea background
    public final Color editorCurrentLineBg; // RSyntaxTextArea current line highlight
    public final boolean isDark;

    AppTheme(String displayName,
             Color bg, Color surface, Color text, Color textSecondary,
             Color accent, Color accentHover, Color selection, Color selectionText,
             Color border, Color inputBg, Color inputText, Color disabledText, Color errorText,
             Color caretColor, Color tableSelectionBg, Color tableSelectionFg,
             Color tableGridColor, Color editorBg, Color editorCurrentLineBg, boolean isDark) {
        this.displayName = displayName;
        this.bg = bg;
        this.surface = surface;
        this.text = text;
        this.textSecondary = textSecondary;
        this.accent = accent;
        this.accentHover = accentHover;
        this.selection = selection;
        this.selectionText = selectionText;
        this.border = border;
        this.inputBg = inputBg;
        this.inputText = inputText;
        this.disabledText = disabledText;
        this.errorText = errorText;
        this.caretColor = caretColor;
        this.tableSelectionBg = tableSelectionBg;
        this.tableSelectionFg = tableSelectionFg;
        this.tableGridColor = tableGridColor;
        this.editorBg = editorBg;
        this.editorCurrentLineBg = editorCurrentLineBg;
        this.isDark = isDark;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Maps a LockerStyle to the corresponding AppTheme.
     */
    public static AppTheme fromLockerStyle(LockerStyle style) {
        if (style == null) return CLASSIC;
        switch (style) {
            case MODERN:        return MODERN;
            case RETRO:         return RETRO;
            case CLASSIC_METAL: return CLASSIC_METAL;
            default:            return CLASSIC;
        }
    }

    /**
     * Maps a settings lockStyle index to the corresponding AppTheme.
     */
    public static AppTheme fromLockStyleIndex(int index) {
        LockerStyle[] styles = LockerStyle.values();
        int safeIndex = Math.max(0, Math.min(styles.length - 1, index));
        return fromLockerStyle(styles[safeIndex]);
    }
}

