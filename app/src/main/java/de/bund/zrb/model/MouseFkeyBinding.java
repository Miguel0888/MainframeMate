package de.bund.zrb.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Configurable mapping of a mouse action (with optional modifier key)
 * to a TN3270 F-key (PF1–PF24).
 * <p>
 * Stored as a list in {@link Settings#tn3270MouseFkeyBindings} and
 * serialised to JSON by Gson.
 */
public class MouseFkeyBinding {

    // ── Mouse actions ───────────────────────────────────────────
    public enum MouseAction {
        BACK("Maus Zurück"),
        FORWARD("Maus Vorwärts"),
        SCROLL_UP("Scroll hoch"),
        SCROLL_DOWN("Scroll runter");

        private final String displayName;
        MouseAction(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
        @Override public String toString() { return displayName; }
    }

    // ── Modifier keys ───────────────────────────────────────────
    public enum Modifier {
        NONE("Keine"),
        SHIFT("Shift"),
        CTRL("Strg"),
        ALT("Alt"),
        CTRL_SHIFT("Strg+Shift"),
        CTRL_ALT("Strg+Alt"),
        SHIFT_ALT("Shift+Alt");

        private final String displayName;
        Modifier(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
        @Override public String toString() { return displayName; }
    }

    // ── Fields (public for Gson) ────────────────────────────────
    public MouseAction mouseAction;
    public Modifier modifier;
    public int fkey; // 1–24

    public MouseFkeyBinding() {} // Gson needs a no-arg constructor

    public MouseFkeyBinding(MouseAction mouseAction, Modifier modifier, int fkey) {
        this.mouseAction = mouseAction;
        this.modifier = modifier;
        this.fkey = fkey;
    }

    /** Default bindings shipped with the application. */
    public static List<MouseFkeyBinding> getDefaults() {
        List<MouseFkeyBinding> list = new ArrayList<>();
        list.add(new MouseFkeyBinding(MouseAction.BACK,        Modifier.NONE,  3));  // Back → F3
        list.add(new MouseFkeyBinding(MouseAction.SCROLL_DOWN, Modifier.NONE,  8));  // Scroll ↓ → F8
        list.add(new MouseFkeyBinding(MouseAction.SCROLL_UP,   Modifier.NONE,  7));  // Scroll ↑ → F7
        list.add(new MouseFkeyBinding(MouseAction.SCROLL_DOWN, Modifier.SHIFT, 11)); // Shift+Scroll ↓ → F11
        list.add(new MouseFkeyBinding(MouseAction.SCROLL_UP,   Modifier.SHIFT, 10)); // Shift+Scroll ↑ → F10
        return list;
    }

    /**
     * Check whether the given modifier state matches this binding's modifier.
     */
    public boolean modifierMatches(boolean shift, boolean ctrl, boolean alt) {
        switch (modifier) {
            case NONE:       return !shift && !ctrl && !alt;
            case SHIFT:      return  shift && !ctrl && !alt;
            case CTRL:       return !shift &&  ctrl && !alt;
            case ALT:        return !shift && !ctrl &&  alt;
            case CTRL_SHIFT: return  shift &&  ctrl && !alt;
            case CTRL_ALT:   return !shift &&  ctrl &&  alt;
            case SHIFT_ALT:  return  shift && !ctrl &&  alt;
            default:         return false;
        }
    }
}

