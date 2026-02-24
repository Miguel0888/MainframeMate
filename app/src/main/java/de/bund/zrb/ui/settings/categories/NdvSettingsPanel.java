package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;
import java.awt.*;

public class NdvSettingsPanel extends AbstractSettingsPanel {

    private final JSpinner ndvPortSpinner;
    private final JTextField ndvDefaultLibraryField;
    private final JTextField ndvLibPathField;

    public NdvSettingsPanel() {
        super("ndv", "NDV-Verbindung");
        FormBuilder fb = new FormBuilder();

        ndvPortSpinner = new JSpinner(new SpinnerNumberModel(settings.ndvPort, 1, 65535, 1));
        ndvPortSpinner.setToolTipText("Standard: 8011");
        fb.addRow("NDV Port:", ndvPortSpinner);

        ndvDefaultLibraryField = new JTextField(settings.ndvDefaultLibrary != null ? settings.ndvDefaultLibrary : "", 20);
        ndvDefaultLibraryField.setToolTipText("z.B. ABAK-T");
        fb.addRow("Default-Bibliothek:", ndvDefaultLibraryField);

        fb.addSection("NDV-Bibliotheken (JARs)");

        String defaultLibDir = de.bund.zrb.ndv.NdvLibLoader.getLibDir().getAbsolutePath();
        ndvLibPathField = new JTextField(settings.ndvLibPath != null && !settings.ndvLibPath.isEmpty() ? settings.ndvLibPath : "", 24);
        ndvLibPathField.setToolTipText("Standard: " + defaultLibDir);
        JButton openLibFolderButton = new JButton("📂 Öffnen");
        openLibFolderButton.addActionListener(e -> {
            String customPath = ndvLibPathField.getText().trim();
            java.io.File libDir = !customPath.isEmpty() ? new java.io.File(customPath) : de.bund.zrb.ndv.NdvLibLoader.getLibDir();
            if (!libDir.exists()) libDir.mkdirs();
            try { Desktop.getDesktop().open(libDir); }
            catch (Exception ex) { JOptionPane.showMessageDialog(null, "Fehler: " + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE); }
        });
        fb.addRowWithButton("Pfad zu NDV-JARs:", ndvLibPathField, openLibFolderButton);

        boolean available = de.bund.zrb.ndv.NdvLibLoader.isAvailable();
        JLabel statusLabel = new JLabel(available ? "✅ NDV-Bibliotheken gefunden" : "⚠ Nicht gefunden in: " + defaultLibDir);
        statusLabel.setForeground(available ? new Color(0, 128, 0) : new Color(200, 100, 0));
        fb.addWide(statusLabel);

        fb.addInfo("Benötigte JARs: ndvserveraccess_*.jar, auxiliary_*.jar (NaturalONE)");

        installPanel(fb);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.ndvPort = ((Number) ndvPortSpinner.getValue()).intValue();
        s.ndvDefaultLibrary = ndvDefaultLibraryField.getText().trim();
        s.ndvLibPath = ndvLibPathField.getText().trim();
    }
}

