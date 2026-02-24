package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.settings.FormBuilder;
import de.bund.zrb.ui.settings.SettingsCategory;

import javax.swing.*;

/**
 * Base class for all settings category panels.
 * Each panel loads settings fresh from the repository on creation
 * and writes back on apply().
 */
public abstract class AbstractSettingsPanel extends JPanel implements SettingsCategory {

    protected final String id;
    protected final String title;
    protected Settings settings;

    protected AbstractSettingsPanel(String id, String title) {
        this.id = id;
        this.title = title;
        this.settings = SettingsHelper.load();
        setLayout(new java.awt.BorderLayout());
    }

    /** Subclasses call this after building their FormBuilder to install the panel. */
    protected void installPanel(FormBuilder fb) {
        add(fb.getPanel(), java.awt.BorderLayout.CENTER);
    }

    @Override public String getId() { return id; }
    @Override public String getTitle() { return title; }
    @Override public JComponent getComponent() { return this; }

    /**
     * Reload settings fresh from disk, apply values, save back, and trigger side-effects.
     * Subclasses override applyToSettings() to write their UI fields into the Settings object.
     */
    @Override
    public void apply() {
        settings = SettingsHelper.load(); // fresh load to avoid overwriting other categories
        applyToSettings(settings);
        SettingsHelper.save(settings);
        afterApply(settings);
    }

    /** Write the UI field values into the given Settings object. */
    protected abstract void applyToSettings(Settings s);

    /** Called after save – override for side-effects like applying log levels. */
    protected void afterApply(Settings s) {
        // default: no side-effects
    }
}

