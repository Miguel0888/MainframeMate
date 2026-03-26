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
    private final DefaultListModel<String> searchOrderListModel;

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

        // ── Library Search Order (for unqualified symbol resolution) ──
        fb.addSection("Bibliotheks-Suchreihenfolge");
        fb.addInfo("Beim Öffnen eines Symbols ohne explizite Bibliothek wird in dieser<br>"
                + "Reihenfolge gesucht. Die Default-Bibliothek wird immer zuerst geprüft.");

        searchOrderListModel = new DefaultListModel<String>();
        if (settings.ndvLibrarySearchOrder != null) {
            for (String lib : settings.ndvLibrarySearchOrder) {
                searchOrderListModel.addElement(lib);
            }
        }
        final JList<String> searchOrderList = new JList<String>(searchOrderListModel);
        searchOrderList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        searchOrderList.setVisibleRowCount(5);
        JScrollPane searchOrderScroll = new JScrollPane(searchOrderList);
        searchOrderScroll.setPreferredSize(new Dimension(0, 100));

        JButton soAddBtn = new JButton("➕");
        soAddBtn.addActionListener(e -> {
            String lib = JOptionPane.showInputDialog(this, "NDV-Bibliotheksname:", "Bibliothek hinzufügen",
                    JOptionPane.PLAIN_MESSAGE);
            if (lib != null && !lib.trim().isEmpty()) {
                searchOrderListModel.addElement(lib.trim().toUpperCase());
            }
        });
        JButton soRemoveBtn = new JButton("➖");
        soRemoveBtn.addActionListener(e -> {
            int sel = searchOrderList.getSelectedIndex();
            if (sel >= 0) searchOrderListModel.remove(sel);
        });
        JButton soUpBtn = new JButton("▲");
        soUpBtn.addActionListener(e -> {
            int sel = searchOrderList.getSelectedIndex();
            if (sel > 0) {
                String val = searchOrderListModel.remove(sel);
                searchOrderListModel.add(sel - 1, val);
                searchOrderList.setSelectedIndex(sel - 1);
            }
        });
        JButton soDownBtn = new JButton("▼");
        soDownBtn.addActionListener(e -> {
            int sel = searchOrderList.getSelectedIndex();
            if (sel >= 0 && sel < searchOrderListModel.size() - 1) {
                String val = searchOrderListModel.remove(sel);
                searchOrderListModel.add(sel + 1, val);
                searchOrderList.setSelectedIndex(sel + 1);
            }
        });

        JPanel soBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        soBtnPanel.add(soAddBtn);
        soBtnPanel.add(soRemoveBtn);
        soBtnPanel.add(soUpBtn);
        soBtnPanel.add(soDownBtn);

        fb.addWideGrow(searchOrderScroll);
        fb.addWide(soBtnPanel);

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

        // Save library search order
        s.ndvLibrarySearchOrder = new java.util.ArrayList<String>();
        for (int i = 0; i < searchOrderListModel.size(); i++) {
            s.ndvLibrarySearchOrder.add(searchOrderListModel.getElementAt(i));
        }

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

