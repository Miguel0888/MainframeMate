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
    private final JTextField proxyHostField;
    private final JSpinner proxyPortSpinner;
    private final JCheckBox proxyNoProxyLocalBox;
    private final RSyntaxTextArea proxyPacScriptArea;
    private final JTextField proxyTestUrlField;
    private final JButton proxyTestButton;
    private final RTextScrollPane pacScrollPane;

    public ProxySettingsPanel(Component parent) {
        super("proxy", "Proxy");
        FormBuilder fb = new FormBuilder();

        fb.addInfo("<html><i>Proxy-Konfiguration für ausgehende Verbindungen. " +
                "Ob ein Proxy tatsächlich verwendet wird, steuert der Haken " +
                "\"Proxy\" je Passwort-Eintrag (Einstellungen → Passwörter).</i></html>");

        proxyModeBox = new JComboBox<>(new String[]{"WINDOWS_PAC", "MANUAL"});
        proxyModeBox.setSelectedItem(settings.proxyMode == null ? "WINDOWS_PAC" : settings.proxyMode);
        fb.addRow("Proxy-Modus:", proxyModeBox);

        proxyHostField = new JTextField(settings.proxyHost == null ? "" : settings.proxyHost, 24);
        fb.addRow("Proxy Host:", proxyHostField);

        proxyPortSpinner = new JSpinner(new SpinnerNumberModel(settings.proxyPort, 0, 65535, 1));
        fb.addRow("Proxy Port:", proxyPortSpinner);

        proxyNoProxyLocalBox = new JCheckBox("Lokale Ziele niemals über Proxy");
        proxyNoProxyLocalBox.setSelected(settings.proxyNoProxyLocal);
        fb.addWide(proxyNoProxyLocalBox);

        fb.addSection("PAC/WPAD Script");

        proxyPacScriptArea = new RSyntaxTextArea(12, 60);
        proxyPacScriptArea.setSyntaxEditingStyle("text/powershell");
        proxyPacScriptArea.setCodeFoldingEnabled(true);
        proxyPacScriptArea.setText(settings.proxyPacScript == null ? ProxyDefaults.DEFAULT_PAC_SCRIPT : settings.proxyPacScript);
        pacScrollPane = new RTextScrollPane(proxyPacScriptArea);
        fb.addWideGrow(pacScrollPane);

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
        fb.addRowWithButton("Test-URL:", proxyTestUrlField, proxyTestButton);

        installPanel(fb);
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

