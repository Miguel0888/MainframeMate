package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;

public class DebugSettingsPanel extends AbstractSettingsPanel {

    private static final String[] LOG_LEVELS = {"OFF", "SEVERE", "WARNING", "INFO", "FINE", "FINER", "FINEST", "ALL"};
    private static final String[] LOG_CATEGORIES = {
            de.bund.zrb.util.AppLogger.MAIL,
            de.bund.zrb.util.AppLogger.STAR,
            de.bund.zrb.util.AppLogger.FTP,
            de.bund.zrb.util.AppLogger.NDV,
            de.bund.zrb.util.AppLogger.TOOL,
            de.bund.zrb.util.AppLogger.INDEX,
            de.bund.zrb.util.AppLogger.SEARCH,
            de.bund.zrb.util.AppLogger.RAG,
            de.bund.zrb.util.AppLogger.UI,
            de.bund.zrb.util.AppLogger.PLUGIN,
            de.bund.zrb.util.AppLogger.BROWSER
    };

    private final JComboBox<String> globalLogLevelCombo;
    private final java.util.Map<String, JComboBox<String>> categoryLevelCombos = new java.util.LinkedHashMap<>();

    public DebugSettingsPanel() {
        super("debug", "Debug");
        FormBuilder fb = new FormBuilder();

        globalLogLevelCombo = new JComboBox<>(LOG_LEVELS);
        globalLogLevelCombo.setSelectedItem(settings.logLevel != null ? settings.logLevel : "INFO");
        globalLogLevelCombo.setToolTipText("INFO = normal, FINE = Debug, FINEST = alles");
        fb.addRow("Globales Log-Level:", globalLogLevelCombo);

        fb.addSection("Kategorie-Log-Level");
        fb.addInfo("Überschreibt das globale Level für einzelne Kategorien.");

        String[] catLevelsWithDefault = {"(global)", "OFF", "SEVERE", "WARNING", "INFO", "FINE", "FINER", "FINEST", "ALL"};
        for (String cat : LOG_CATEGORIES) {
            JComboBox<String> combo = new JComboBox<>(catLevelsWithDefault);
            String current = settings.logCategoryLevels != null ? settings.logCategoryLevels.get(cat) : null;
            combo.setSelectedItem(current != null && !current.isEmpty() ? current : "(global)");
            categoryLevelCombos.put(cat, combo);
            fb.addRow(cat + ":", combo);
        }

        installPanel(fb);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.logLevel = (String) globalLogLevelCombo.getSelectedItem();
        s.logCategoryLevels.clear();
        for (java.util.Map.Entry<String, JComboBox<String>> entry : categoryLevelCombos.entrySet()) {
            String selected = (String) entry.getValue().getSelectedItem();
            if (selected != null && !"(global)".equals(selected)) {
                s.logCategoryLevels.put(entry.getKey(), selected);
            }
        }
    }

    @Override
    protected void afterApply(Settings s) {
        de.bund.zrb.util.AppLogger.applySettings();
    }
}

