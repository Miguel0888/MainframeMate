package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;

/**
 * Settings panel for JES Spool configuration.
 * JES uses its own FTP connection, so these settings are separate from TN3270.
 */
public class JesSpoolSettingsPanel extends AbstractSettingsPanel {

    private final JComboBox<String> jesSpoolDdNameCombo;
    private final JSpinner jesProbeConnectionsSpinner;
    private final JCheckBox jesFastBackgroundProbeCheckBox;

    public JesSpoolSettingsPanel() {
        super("jes-spool", "JES Spool");
        FormBuilder fb = new FormBuilder();

        fb.addSection("DD-Name Erkennung");

        jesSpoolDdNameCombo = new JComboBox<>(new String[]{"FAST", "PROBE", "OFF"});
        String currentMode = settings.jesSpoolDdNameMode != null ? settings.jesSpoolDdNameMode : "FAST";
        jesSpoolDdNameCombo.setSelectedItem(currentMode);
        jesSpoolDdNameCombo.setToolTipText(
                "<html><b>FAST</b> = Schnell laden (gesamter Output), DDNames aus Inhalt erkennen<br>"
                        + "<b>PROBE</b> = Einzelne Spool-Files parallel abrufen (genauere DDNames)<br>"
                        + "<b>OFF</b> = Schnell laden, alle SPOOL#n, dann im Hintergrund korrigieren</html>");
        fb.addRow("DD-Name Erkennung:", jesSpoolDdNameCombo);

        fb.addSection("Paralleles Laden");

        jesProbeConnectionsSpinner = new JSpinner(new SpinnerNumberModel(
                settings.jesProbeParallelConnections, 1, 10, 1));
        jesProbeConnectionsSpinner.setToolTipText(
                "Anzahl paralleler FTP-Verbindungen für das Abrufen einzelner Spool-Files (PROBE/Nachladen)");
        fb.addRow("Parallele Verbindungen:", jesProbeConnectionsSpinner);

        jesFastBackgroundProbeCheckBox = new JCheckBox(
                "DDNames im Hintergrund nachladen (FAST-Modus)", settings.jesFastBackgroundProbe);
        jesFastBackgroundProbeCheckBox.setToolTipText(
                "Nach dem schnellen Laden werden DDNames im Hintergrund per Einzelabruf korrigiert");
        fb.addWide(jesFastBackgroundProbeCheckBox);

        // Enable/disable background probe checkbox based on mode
        jesSpoolDdNameCombo.addActionListener(e -> {
            String mode = (String) jesSpoolDdNameCombo.getSelectedItem();
            jesFastBackgroundProbeCheckBox.setEnabled("FAST".equals(mode));
        });
        jesFastBackgroundProbeCheckBox.setEnabled("FAST".equals(currentMode));

        fb.addInfo("<html><i><b>FAST</b>: Lädt gesamten Output in einem Abruf, erkennt DDNames aus dem Inhalt.<br>"
                + "<b>PROBE</b>: Ruft jedes Spool-File einzeln ab – parallel über N Verbindungen.<br>"
                + "<b>OFF</b>: Schnell laden (alle SPOOL#n), dann im Hintergrund per Probe korrigieren.</i></html>");

        fb.addInfo("<html><i>Host und Benutzer werden aus den Server-Einstellungen übernommen.</i></html>");

        installPanel(fb);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.jesSpoolDdNameMode = (String) jesSpoolDdNameCombo.getSelectedItem();
        s.jesProbeParallelConnections = ((Number) jesProbeConnectionsSpinner.getValue()).intValue();
        s.jesFastBackgroundProbe = jesFastBackgroundProbeCheckBox.isSelected();
    }
}

