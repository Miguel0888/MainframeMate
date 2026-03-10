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
    }
}

