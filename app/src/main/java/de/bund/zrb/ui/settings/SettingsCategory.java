package de.bund.zrb.ui.settings;

import javax.swing.*;

/**
 * Contract for a single settings category in the Outlook-style settings dialog.
 * Each category provides an id, a display title, and a Swing component for the right-hand side.
 */
public interface SettingsCategory {

    /** Unique identifier (used internally for CardLayout). */
    String getId();

    /** Human-readable title shown in the left navigation list. */
    String getTitle();

    /** The Swing component that renders this category's settings. */
    JComponent getComponent();

    /**
     * Validate the current input.
     * @throws IllegalArgumentException with a user-readable message if invalid.
     */
    default void validate() throws IllegalArgumentException {
        // No validation by default.
    }

    /**
     * Apply the current values (persist / push into model).
     * Called on "Übernehmen" and "OK".
     */
    default void apply() {
        // No-op by default.
    }
}
