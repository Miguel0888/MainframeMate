package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;

/**
 * Settings panel for BetaView connection configuration.
 * Credentials (user/password) are shared with FTP/NDV via LoginManager.
 */
public class BetaViewSettingsPanel extends AbstractSettingsPanel {

    private final JTextField urlField;
    private final JTextField favoriteIdField;
    private final JTextField localeField;
    private final JTextField extensionField;
    private final JTextField formField;
    private final JSpinner daysBackSpinner;

    public BetaViewSettingsPanel() {
        super("betaview", "BetaView");
        FormBuilder fb = new FormBuilder();

        fb.addSection("Verbindung");

        urlField = new JTextField(safe(settings.betaviewUrl), 30);
        urlField.setToolTipText("BetaView Base URL, z.B. https://betaview.example.com/betaview/");
        fb.addRow("Base URL:", urlField);

        fb.addInfo("Benutzername und Passwort werden aus der FTP-/NDV-Anmeldung übernommen.");

        fb.addSection("Standard-Suchfilter");

        favoriteIdField = new JTextField(safe(settings.betaviewFavoriteId), 20);
        fb.addRow("Favorite ID:", favoriteIdField);

        localeField = new JTextField(safe(settings.betaviewLocale), 10);
        fb.addRow("Locale:", localeField);

        extensionField = new JTextField(safe(settings.betaviewExtension), 20);
        fb.addRow("Extension:", extensionField);

        formField = new JTextField(safe(settings.betaviewForm), 10);
        fb.addRow("Form:", formField);

        daysBackSpinner = new JSpinner(new SpinnerNumberModel(
                settings.betaviewDaysBack > 0 ? settings.betaviewDaysBack : 60,
                1, 9999, 1));
        fb.addRow("Tage zurück:", daysBackSpinner);

        installPanel(fb);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.betaviewUrl = urlField.getText().trim();
        s.betaviewFavoriteId = favoriteIdField.getText().trim();
        s.betaviewLocale = localeField.getText().trim();
        s.betaviewExtension = extensionField.getText().trim();
        s.betaviewForm = formField.getText().trim();
        s.betaviewDaysBack = ((Number) daysBackSpinner.getValue()).intValue();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}

