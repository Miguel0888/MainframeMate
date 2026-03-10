package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.settings.FormBuilder;
import de.bund.zrb.util.WindowsCryptoUtil;

import javax.swing.*;

/**
 * Settings panel for BetaView connection configuration.
 * Credentials can be shared with FTP/NDV (default) or configured separately.
 */
public class BetaViewSettingsPanel extends AbstractSettingsPanel {

    private final JTextField urlField;
    private final JCheckBox useSharedCredentialsBox;
    private final JTextField hostField;
    private final JTextField userField;
    private final JPasswordField passwordField;
    private final JButton clearPasswordButton;
    private final JTextField favoriteIdField;
    private final JTextField localeField;
    private final JTextField extensionField;
    private final JTextField formField;
    private final JSpinner daysBackSpinner;

    private final boolean[] passwordCleared = {false};

    public BetaViewSettingsPanel() {
        super("betaview", "BetaView");
        FormBuilder fb = new FormBuilder();

        fb.addSection("Verbindung");

        urlField = new JTextField(safe(settings.betaviewUrl), 30);
        urlField.setToolTipText("BetaView Base URL, z.B. https://betaview.example.com/betaview/");
        fb.addRow("Base URL:", urlField);

        fb.addSection("Zugangsdaten");

        useSharedCredentialsBox = new JCheckBox("Credentials aus Server-Einstellungen (FTP/NDV) übernehmen");
        useSharedCredentialsBox.setSelected(settings.betaviewUseSharedCredentials);
        useSharedCredentialsBox.setToolTipText(
                "Wenn aktiviert, werden Benutzername und Passwort aus den Server-Einstellungen verwendet (wie FTP/NDV).");
        fb.addWide(useSharedCredentialsBox);

        String sharedInfo = "";
        if (settings.host != null && !settings.host.isEmpty()) {
            sharedInfo = "Server-Host: " + settings.host
                    + (settings.user != null && !settings.user.isEmpty() ? ", User: " + settings.user : "");
        }
        if (!sharedInfo.isEmpty()) {
            fb.addInfo("Aktuell: " + sharedInfo);
        }

        hostField = new JTextField(safe(settings.betaviewHost), 24);
        hostField.setToolTipText("BetaView-Server Host (nur wenn eigene Credentials)");
        fb.addRow("Host:", hostField);

        userField = new JTextField(safe(settings.betaviewUser), 24);
        userField.setToolTipText("BetaView-Benutzername (nur wenn eigene Credentials)");
        fb.addRow("Benutzer:", userField);

        passwordField = new JPasswordField(24);
        if (settings.betaviewEncryptedPassword != null && !settings.betaviewEncryptedPassword.isEmpty()) {
            passwordField.setText("********");
        }
        fb.addRow("Passwort:", passwordField);

        clearPasswordButton = new JButton("Passwort löschen");
        clearPasswordButton.addActionListener(e -> {
            passwordField.setText("");
            passwordCleared[0] = true;
        });
        fb.addWide(clearPasswordButton);

        // Toggle credential fields enabled/disabled
        useSharedCredentialsBox.addActionListener(e -> updateCredentialFieldsState());
        updateCredentialFieldsState();

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

    private void updateCredentialFieldsState() {
        boolean shared = useSharedCredentialsBox.isSelected();
        hostField.setEnabled(!shared);
        userField.setEnabled(!shared);
        passwordField.setEnabled(!shared);
        clearPasswordButton.setEnabled(!shared);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.betaviewUrl = urlField.getText().trim();
        s.betaviewUseSharedCredentials = useSharedCredentialsBox.isSelected();
        s.betaviewHost = hostField.getText().trim();
        s.betaviewUser = userField.getText().trim();

        // Password handling (same logic as ServerSettingsDialog)
        char[] passChars = passwordField.getPassword();
        String passStr = new String(passChars);
        if (passwordCleared[0] && passStr.isEmpty()) {
            s.betaviewEncryptedPassword = "";
        } else if (!passStr.equals("********") && passChars.length > 0) {
            s.betaviewEncryptedPassword = WindowsCryptoUtil.encrypt(passStr);
        }
        // else: password field was not changed, keep existing

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
