package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.net.ProxyDefaults;
import de.bund.zrb.net.ProxyResolver;
import de.bund.zrb.ui.settings.FormBuilder;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class ProxySettingsPanel extends AbstractSettingsPanel {

    private final JComboBox<String> proxyModeBox;
    private final JLabel proxyHostLabel;
    private final JTextField proxyHostField;
    private final JLabel proxyPortLabel;
    private final JSpinner proxyPortSpinner;
    private final JCheckBox proxyNoProxyLocalBox;
    private final RSyntaxTextArea proxyPacScriptArea;
    private final RTextScrollPane pacScrollPane;
    private final JLabel pacSectionLabel;
    private final JTextField proxyTestUrlField;
    private final JLabel proxyTestUrlLabel;
    private final JButton proxyTestButton;
    private final JButton resetScriptButton;

    public ProxySettingsPanel(Component parent) {
        super("proxy", "Proxy");
        FormBuilder fb = new FormBuilder();

        fb.addInfo("<html><i>Proxy-Konfiguration für ausgehende Verbindungen. " +
                "Ob ein Proxy tatsächlich verwendet wird, steuert der Haken " +
                "\"Proxy\" je Passwort-Eintrag (Einstellungen → Passwörter).</i></html>");

        proxyModeBox = new JComboBox<>(new String[]{"WINDOWS_PAC", "MANUAL"});
        proxyModeBox.setSelectedItem(settings.proxyMode == null ? "WINDOWS_PAC" : settings.proxyMode);
        fb.addRow("Proxy-Modus:", proxyModeBox);

        // ── MANUAL-only: Host / Port ──
        proxyHostLabel = new JLabel("Proxy Host:");
        proxyHostField = new JTextField(settings.proxyHost == null ? "" : settings.proxyHost, 24);
        fb.addRow(proxyHostLabel, proxyHostField);

        proxyPortLabel = new JLabel("Proxy Port:");
        proxyPortSpinner = new JSpinner(new SpinnerNumberModel(settings.proxyPort, 0, 65535, 1));
        fb.addRow(proxyPortLabel, proxyPortSpinner);

        proxyNoProxyLocalBox = new JCheckBox("Lokale Ziele niemals über Proxy");
        proxyNoProxyLocalBox.setSelected(settings.proxyNoProxyLocal);
        fb.addWide(proxyNoProxyLocalBox);

        // ── PAC/WPAD-only: Script + Test ──
        fb.addSeparator();
        pacSectionLabel = new JLabel("PAC / WPAD Script");
        pacSectionLabel.setFont(pacSectionLabel.getFont().deriveFont(Font.BOLD, pacSectionLabel.getFont().getSize2D() + 1f));
        fb.addWide(pacSectionLabel);

        proxyPacScriptArea = new RSyntaxTextArea(12, 60);
        proxyPacScriptArea.setSyntaxEditingStyle("text/powershell");
        proxyPacScriptArea.setCodeFoldingEnabled(true);
        proxyPacScriptArea.setText(settings.proxyPacScript == null ? ProxyDefaults.DEFAULT_PAC_SCRIPT : settings.proxyPacScript);
        pacScrollPane = new RTextScrollPane(proxyPacScriptArea);
        fb.addWideGrow(pacScrollPane);

        resetScriptButton = new JButton("Standard-Script laden");
        resetScriptButton.setToolTipText("Setzt das PAC/WPAD-Script auf die Werkseinstellung zurück");
        resetScriptButton.addActionListener(e -> {
            int answer = JOptionPane.showConfirmDialog(parent,
                    "Das aktuelle Script wird durch das Standard-Script ersetzt.\nFortfahren?",
                    "Standard-Script laden", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer == JOptionPane.OK_OPTION) {
                proxyPacScriptArea.setText(ProxyDefaults.DEFAULT_PAC_SCRIPT);
            }
        });
        fb.addWide(resetScriptButton);

        proxyTestUrlLabel = new JLabel("Test-URL:");
        proxyTestUrlField = new JTextField(settings.proxyTestUrl == null ? ProxyDefaults.DEFAULT_TEST_URL : settings.proxyTestUrl, 30);
        proxyTestButton = new JButton("Testen");
        proxyTestButton.addActionListener(e -> {
            String testUrl = proxyTestUrlField.getText().trim();
            if (testUrl.isEmpty()) { JOptionPane.showMessageDialog(parent, "Bitte Test-URL eingeben.", "Proxy Test", JOptionPane.WARNING_MESSAGE); return; }

            proxyTestButton.setEnabled(false);
            proxyTestButton.setText("…");
            new javax.swing.SwingWorker<ProxyResolver.ProxyResolution, Void>() {
                @Override protected ProxyResolver.ProxyResolution doInBackground() {
                    return ProxyResolver.testPacScript(testUrl, proxyPacScriptArea.getText());
                }
                @Override protected void done() {
                    proxyTestButton.setEnabled(true);
                    proxyTestButton.setText("Testen");
                    try {
                        ProxyResolver.ProxyResolution result = get();
                        String detail = result.isDirect()
                                ? "DIRECT (" + result.getReason() + ")"
                                : result.getProxy().address() + " (" + result.getReason() + ")";
                        JOptionPane.showMessageDialog(parent, detail, "Proxy Test", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(parent, "Fehler: " + ex.getMessage(), "Proxy Test", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });
        // Build test row manually so we keep the label reference
        JPanel testRowRight = new JPanel(new BorderLayout(4, 0));
        testRowRight.add(proxyTestUrlField, BorderLayout.CENTER);
        testRowRight.add(proxyTestButton, BorderLayout.EAST);
        fb.addRow(proxyTestUrlLabel, testRowRight);

        // Wire mode switch
        proxyModeBox.addActionListener(e -> updateModeVisibility());
        updateModeVisibility();

        installPanel(fb);
    }

    /**
     * Enables/disables fields depending on the selected proxy mode.
     * <ul>
     *   <li><b>WINDOWS_PAC</b>: Host/Port disabled, PAC script + Test enabled</li>
     *   <li><b>MANUAL</b>: Host/Port enabled, PAC script + Test disabled</li>
     * </ul>
     */
    private void updateModeVisibility() {
        boolean isPac = "WINDOWS_PAC".equals(proxyModeBox.getSelectedItem());

        // MANUAL fields
        proxyHostLabel.setEnabled(!isPac);
        proxyHostField.setEnabled(!isPac);
        proxyPortLabel.setEnabled(!isPac);
        proxyPortSpinner.setEnabled(!isPac);

        // PAC/WPAD fields
        pacSectionLabel.setEnabled(isPac);
        proxyPacScriptArea.setEnabled(isPac);
        proxyPacScriptArea.setEditable(isPac);
        proxyTestUrlLabel.setEnabled(isPac);
        proxyTestUrlField.setEnabled(isPac);
        proxyTestButton.setEnabled(isPac);

        resetScriptButton.setEnabled(isPac);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.proxyMode = Objects.toString(proxyModeBox.getSelectedItem(), "WINDOWS_PAC");
        s.proxyHost = proxyHostField.getText().trim();
        s.proxyPort = ((Number) proxyPortSpinner.getValue()).intValue();
        s.proxyNoProxyLocal = proxyNoProxyLocalBox.isSelected();
        s.proxyPacScript = proxyPacScriptArea.getText();
        s.proxyTestUrl = proxyTestUrlField.getText().trim();
    }
}
