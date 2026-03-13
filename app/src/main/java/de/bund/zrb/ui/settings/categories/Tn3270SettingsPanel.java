package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;

/**
 * Settings panel for TN3270 terminal configuration.
 */
public class Tn3270SettingsPanel extends AbstractSettingsPanel {

    private final JSpinner portSpinner;
    private final JCheckBox tlsCheckBox;
    private final JTextField termTypeField;
    private final JSpinner keepAliveSpinner;
    private final JCheckBox autoLoginCheckBox;
    private final JCheckBox autoCommandCheckBox;
    private final JTextField autoCommandField;
    private final JSpinner actionDelaySpinner;
    private final JSpinner fkeyOpacitySpinner;

    public Tn3270SettingsPanel() {
        super("tn3270", "3270-Terminal");
        FormBuilder fb = new FormBuilder();

        fb.addSection("TN3270 Verbindung");

        portSpinner = new JSpinner(new SpinnerNumberModel(settings.tn3270Port, 1, 65535, 1));
        portSpinner.setToolTipText("Standard: 23");
        fb.addRow("Port:", portSpinner);

        tlsCheckBox = new JCheckBox("SSL/TLS verwenden", settings.tn3270Tls);
        tlsCheckBox.setToolTipText("Verschlüsselte Verbindung zum Host");
        fb.addWide(tlsCheckBox);

        termTypeField = new JTextField(settings.tn3270TermType != null ? settings.tn3270TermType : "IBM-3278-2", 20);
        termTypeField.setToolTipText("z.B. IBM-3278-2, IBM-3278-3, IBM-3278-4, IBM-3278-5, IBM-3279-2");
        fb.addRow("Terminal-Typ:", termTypeField);

        keepAliveSpinner = new JSpinner(new SpinnerNumberModel(settings.tn3270KeepAliveTimeout, 0, 3600, 10));
        keepAliveSpinner.setToolTipText("KeepAlive-Intervall in Sekunden (0 = deaktiviert)");
        fb.addRow("KeepAlive (Sek.):", keepAliveSpinner);

        fb.addSection("Automatisierung");

        autoLoginCheckBox = new JCheckBox("Auto-Login nach Verbindung", settings.tn3270AutoLogin);
        autoLoginCheckBox.setToolTipText("Sendet Benutzername und Passwort automatisch nach dem Verbinden");
        fb.addWide(autoLoginCheckBox);

        autoCommandCheckBox = new JCheckBox("Nach Login Befehl senden:", settings.tn3270AutoCommand);
        autoCommandCheckBox.setToolTipText("Sendet nach dem Login automatisch einen Befehl (z.B. um den Startbildschirm zu überspringen)");
        autoCommandField = new JTextField(
                settings.tn3270AutoCommandText != null ? settings.tn3270AutoCommandText : "a", 10);
        autoCommandField.setToolTipText("Befehl der nach dem Login gesendet wird (z.B. \"a\" + Enter)");
        autoCommandField.setEnabled(settings.tn3270AutoCommand);

        // Enable/disable the text field based on the checkbox
        autoCommandCheckBox.addActionListener(e -> autoCommandField.setEnabled(autoCommandCheckBox.isSelected()));
        // Also disable auto-command when auto-login is off
        autoLoginCheckBox.addActionListener(e -> {
            boolean loginOn = autoLoginCheckBox.isSelected();
            autoCommandCheckBox.setEnabled(loginOn);
            autoCommandField.setEnabled(loginOn && autoCommandCheckBox.isSelected());
        });
        autoCommandCheckBox.setEnabled(settings.tn3270AutoLogin);

        JPanel cmdPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        cmdPanel.add(autoCommandCheckBox);
        cmdPanel.add(autoCommandField);
        fb.addWide(cmdPanel);

        actionDelaySpinner = new JSpinner(new SpinnerNumberModel(settings.tn3270ActionDelayMs, 0, 10000, 100));
        actionDelaySpinner.setToolTipText("Wartezeit in Millisekunden nach jeder AID-Taste (Enter, F-Key) bei Auto-Login und Makro-Wiedergabe");
        fb.addRow("Aktions-Delay (ms):", actionDelaySpinner);

        fb.addSection("Darstellung");

        fkeyOpacitySpinner = new JSpinner(new SpinnerNumberModel(settings.tn3270FkeyOverlayOpacity, 0, 100, 5));
        fkeyOpacitySpinner.setToolTipText("Transparenz der F-Tasten-Leiste in Prozent (0 = unsichtbar, 100 = deckend)");
        fb.addRow("F-Tasten Deckkraft (%):", fkeyOpacitySpinner);

        fb.addInfo("<html><i>Host und Benutzer werden aus den Server-Einstellungen übernommen.<br>"
                + "Der Port kann beim Verbinden im Dialog überschrieben werden.</i></html>");

        installPanel(fb);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.tn3270Port = ((Number) portSpinner.getValue()).intValue();
        s.tn3270Tls = tlsCheckBox.isSelected();
        s.tn3270TermType = termTypeField.getText().trim();
        s.tn3270KeepAliveTimeout = ((Number) keepAliveSpinner.getValue()).intValue();
        s.tn3270AutoLogin = autoLoginCheckBox.isSelected();
        s.tn3270AutoCommand = autoCommandCheckBox.isSelected();
        s.tn3270AutoCommandText = autoCommandField.getText();
        s.tn3270ActionDelayMs = ((Number) actionDelaySpinner.getValue()).intValue();
        s.tn3270FkeyOverlayOpacity = ((Number) fkeyOpacitySpinner.getValue()).intValue();
    }
}

