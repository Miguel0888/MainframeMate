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

    public ProxySettingsPanel(Component parent) {
        super("proxy", "Proxy");
        FormBuilder fb = new FormBuilder();

        fb.addInfo("<html><i>Proxy-Konfiguration für ausgehende Verbindungen. " +
                "Ob ein Proxy tatsächlich verwendet wird, steuert der Haken " +
                "\"Proxy\" je Passwort-Eintrag (Einstellungen → Passwörter).</i></html>");

        proxyModeBox = new JComboBox<>(new String[]{"WINDOWS_PAC", "MANUAL"});
        String currentMode = settings.proxyMode == null ? "WINDOWS_PAC" : settings.proxyMode;
        // Migrate old JAVA_SYSTEM setting to WINDOWS_PAC
        if ("JAVA_SYSTEM".equalsIgnoreCase(currentMode)) {
            currentMode = "WINDOWS_PAC";
        }
        proxyModeBox.setSelectedItem(currentMode);
        proxyModeBox.setToolTipText("<html>" +
                "<b>WINDOWS_PAC</b> — Automatische Proxy-Erkennung:<br>" +
                "&nbsp;&nbsp;1. PowerShell PAC/WPAD-Script<br>" +
                "&nbsp;&nbsp;2. Windows Registry (bei eingeschränkter PowerShell)<br>" +
                "&nbsp;&nbsp;3. Java-Subprozess mit WPAD-Erkennung (letzter Fallback)<br>" +
                "<b>MANUAL</b> — Fester Proxy-Host und -Port." +
                "</html>");
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

        proxyTestUrlLabel = new JLabel("Test-URL:");
        proxyTestUrlField = new JTextField(settings.proxyTestUrl == null ? ProxyDefaults.DEFAULT_TEST_URL : settings.proxyTestUrl, 30);
        proxyTestButton = new JButton("Testen");
        proxyTestButton.addActionListener(e -> {
            String testUrl = proxyTestUrlField.getText().trim();
            if (testUrl.isEmpty()) { JOptionPane.showMessageDialog(parent, "Bitte Test-URL eingeben.", "Proxy Test", JOptionPane.WARNING_MESSAGE); return; }

            String selectedMode = Objects.toString(proxyModeBox.getSelectedItem(), "WINDOWS_PAC");
            proxyTestButton.setEnabled(false);
            proxyTestButton.setText("…");

            new javax.swing.SwingWorker<String, Void>() {
                @Override protected String doInBackground() {
                    StringBuilder sb = new StringBuilder();

                    if ("MANUAL".equals(selectedMode)) {
                        String host = proxyHostField.getText().trim();
                        int port = ((Number) proxyPortSpinner.getValue()).intValue();
                        if (host.isEmpty() || port <= 0) {
                            return "MANUAL: Kein Host/Port konfiguriert.";
                        }
                        return "MANUAL: " + host + ":" + port;
                    }

                    // WINDOWS_PAC: test all three methods individually
                    // 1) PowerShell
                    ProxyResolver.ProxyResolution pacResult = ProxyResolver.testPacScript(testUrl, proxyPacScriptArea.getText());
                    String pacStr = pacResult.isDirect()
                            ? "DIRECT (" + pacResult.getReason() + ")"
                            : pacResult.getProxy().address() + " (" + pacResult.getReason() + ")";
                    sb.append("PowerShell PAC: ").append(pacStr);

                    // 2) Windows Registry
                    ProxyResolver.ProxyResolution regResult = ProxyResolver.testRegistry(testUrl);
                    String regStr = regResult.isDirect()
                            ? "DIRECT (" + regResult.getReason() + ")"
                            : regResult.getProxy().address() + " (" + regResult.getReason() + ")";
                    sb.append("\nWindows Registry: ").append(regStr);

                    // 3) Java Subprocess
                    ProxyResolver.ProxyResolution subResult = ProxyResolver.testJavaSubprocess(testUrl);
                    String subStr = subResult.isDirect()
                            ? "DIRECT (" + subResult.getReason() + ")"
                            : subResult.getProxy().address() + " (" + subResult.getReason() + ")";
                    sb.append("\nJava Subprocess: ").append(subStr);

                    return sb.toString();
                }
                @Override protected void done() {
                    proxyTestButton.setEnabled(true);
                    proxyTestButton.setText("Testen");
                    try {
                        JOptionPane.showMessageDialog(parent, get(), "Proxy Test — " + selectedMode, JOptionPane.INFORMATION_MESSAGE);
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
     *   <li><b>MANUAL</b>: Host/Port enabled, PAC script disabled</li>
     * </ul>
     */
    private void updateModeVisibility() {
        String mode = Objects.toString(proxyModeBox.getSelectedItem(), "WINDOWS_PAC");
        boolean isManual = "MANUAL".equals(mode);
        boolean isPac = "WINDOWS_PAC".equals(mode);

        // MANUAL fields — only active in MANUAL mode
        proxyHostLabel.setEnabled(isManual);
        proxyHostField.setEnabled(isManual);
        proxyPortLabel.setEnabled(isManual);
        proxyPortSpinner.setEnabled(isManual);

        // PAC/WPAD fields — only active in WINDOWS_PAC mode
        pacSectionLabel.setEnabled(isPac);
        proxyPacScriptArea.setEnabled(isPac);
        proxyPacScriptArea.setEditable(isPac);

        // Test-URL and Test-Button — active for WINDOWS_PAC (not MANUAL)
        proxyTestUrlLabel.setEnabled(isPac);
        proxyTestUrlField.setEnabled(isPac);
        proxyTestButton.setEnabled(isPac);
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

