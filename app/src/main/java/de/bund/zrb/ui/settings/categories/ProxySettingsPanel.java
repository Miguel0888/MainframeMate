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

    private final JCheckBox proxyEnabledBox;
    private final JComboBox<String> proxyModeBox;
    private final JTextField proxyHostField;
    private final JSpinner proxyPortSpinner;
    private final JCheckBox proxyNoProxyLocalBox;
    private final RSyntaxTextArea proxyPacScriptArea;
    private final JTextField proxyTestUrlField;

    public ProxySettingsPanel(Component parent) {
        super("proxy", "Proxy");
        FormBuilder fb = new FormBuilder();

        proxyEnabledBox = new JCheckBox("Proxy aktivieren");
        proxyEnabledBox.setSelected(settings.proxyEnabled);
        fb.addWide(proxyEnabledBox);

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
        fb.addWideGrow(new RTextScrollPane(proxyPacScriptArea));

        proxyTestUrlField = new JTextField(settings.proxyTestUrl == null ? ProxyDefaults.DEFAULT_TEST_URL : settings.proxyTestUrl, 30);
        JButton proxyTestButton = new JButton("Testen");
        proxyTestButton.addActionListener(e -> {
            String testUrl = proxyTestUrlField.getText().trim();
            if (testUrl.isEmpty()) { JOptionPane.showMessageDialog(parent, "Bitte Test-URL eingeben.", "Proxy Test", JOptionPane.WARNING_MESSAGE); return; }
            ProxyResolver.ProxyResolution result = ProxyResolver.testPacScript(testUrl, proxyPacScriptArea.getText());
            String msg = result.isDirect() ? "DIRECT (" + result.getReason() + ")" : result.getProxy().address() + " (" + result.getReason() + ")";
            JOptionPane.showMessageDialog(parent, msg, "Proxy Test", JOptionPane.INFORMATION_MESSAGE);
        });
        fb.addRowWithButton("Test-URL:", proxyTestUrlField, proxyTestButton);

        installPanel(fb);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.proxyEnabled = proxyEnabledBox.isSelected();
        s.proxyMode = Objects.toString(proxyModeBox.getSelectedItem(), "WINDOWS_PAC");
        s.proxyHost = proxyHostField.getText().trim();
        s.proxyPort = ((Number) proxyPortSpinner.getValue()).intValue();
        s.proxyNoProxyLocal = proxyNoProxyLocalBox.isSelected();
        s.proxyPacScript = proxyPacScriptArea.getText();
        s.proxyTestUrl = proxyTestUrlField.getText().trim();
    }
}

