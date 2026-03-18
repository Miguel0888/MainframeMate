package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class NdvSettingsPanel extends AbstractSettingsPanel {

    private final JSpinner ndvPortSpinner;
    private final JTextField ndvDefaultLibraryField;
    private final JTextField ndvLibPathField;
    private final DefaultTableModel mappingTableModel;

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

        // ── Natural Library Mappings (STEPLIB → NDV) ──
        fb.addSection("Natural-Bibliothekszuordnung (STEPLIB → NDV)");
        fb.addInfo("Ordnet JCL-STEPLIB-Namen der entsprechenden NDV-Bibliothek zu (z.B. ABAK-M → ABAK-T).<br>"
                + "Wird beim Öffnen von Natural-Programmen aus der JCL-Analyse verwendet.");

        mappingTableModel = new DefaultTableModel(new String[]{"STEPLIB (JCL)", "NDV-Bibliothek"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        };
        // Load existing mappings
        if (settings.naturalLibraryMappings != null) {
            for (Map.Entry<String, String> entry : settings.naturalLibraryMappings.entrySet()) {
                mappingTableModel.addRow(new Object[]{entry.getKey(), entry.getValue()});
            }
        }

        JTable mappingTable = new JTable(mappingTableModel);
        mappingTable.setRowHeight(24);
        mappingTable.getTableHeader().setReorderingAllowed(false);
        JScrollPane tableScroll = new JScrollPane(mappingTable);
        tableScroll.setPreferredSize(new Dimension(0, 120));

        JButton addBtn = new JButton("➕ Hinzufügen");
        addBtn.addActionListener(e -> {
            mappingTableModel.addRow(new Object[]{"", ""});
            int row = mappingTableModel.getRowCount() - 1;
            mappingTable.editCellAt(row, 0);
            mappingTable.changeSelection(row, 0, false, false);
        });

        JButton removeBtn = new JButton("➖ Entfernen");
        removeBtn.addActionListener(e -> {
            int sel = mappingTable.getSelectedRow();
            if (sel >= 0) {
                if (mappingTable.isEditing()) mappingTable.getCellEditor().stopCellEditing();
                mappingTableModel.removeRow(sel);
            }
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        btnPanel.add(addBtn);
        btnPanel.add(removeBtn);

        fb.addWideGrow(tableScroll);
        fb.addWide(btnPanel);

        installPanel(fb);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.ndvPort = ((Number) ndvPortSpinner.getValue()).intValue();
        s.ndvDefaultLibrary = ndvDefaultLibraryField.getText().trim();
        s.ndvLibPath = ndvLibPathField.getText().trim();

        // Save Natural library mappings from table
        s.naturalLibraryMappings = new LinkedHashMap<>();
        for (int i = 0; i < mappingTableModel.getRowCount(); i++) {
            String stepLib = String.valueOf(mappingTableModel.getValueAt(i, 0)).trim().toUpperCase();
            String ndvLib = String.valueOf(mappingTableModel.getValueAt(i, 1)).trim().toUpperCase();
            if (!stepLib.isEmpty() && !ndvLib.isEmpty()) {
                s.naturalLibraryMappings.put(stepLib, ndvLib);
            }
        }
    }
}

