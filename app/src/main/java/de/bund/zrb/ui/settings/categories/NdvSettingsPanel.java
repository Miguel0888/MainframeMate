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

    // Natural block highlight color buttons
    private final JButton colorSubroutineBtn;
    private final JButton colorDefineDataBtn;
    private final JButton colorOnErrorBtn;
    private Color colorSubroutine;
    private Color colorDefineData;
    private Color colorOnError;

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

        // ── Natural Block Highlight Colors ──
        fb.addSection("Natural-Block-Farben");
        fb.addInfo("Hintergrundfarben für strukturelle Blöcke im Natural-Editor.<br>"
                + "Klicken Sie auf die Farbfläche, um eine neue Farbe zu wählen.");

        colorSubroutine = hexToColor(settings.naturalColorSubroutine, new Color(255, 230, 230));
        colorSubroutineBtn = createColorButton(colorSubroutine);
        colorSubroutineBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Farbe: DEFINE SUBROUTINE", colorSubroutine);
            if (c != null) { colorSubroutine = c; applyColorToButton(colorSubroutineBtn, c); }
        });
        fb.addRow("DEFINE SUBROUTINE:", colorSubroutineBtn);

        colorDefineData = hexToColor(settings.naturalColorDefineData, new Color(230, 240, 255));
        colorDefineDataBtn = createColorButton(colorDefineData);
        colorDefineDataBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Farbe: DEFINE DATA", colorDefineData);
            if (c != null) { colorDefineData = c; applyColorToButton(colorDefineDataBtn, c); }
        });
        fb.addRow("DEFINE DATA:", colorDefineDataBtn);

        colorOnError = hexToColor(settings.naturalColorOnError, new Color(255, 248, 220));
        colorOnErrorBtn = createColorButton(colorOnError);
        colorOnErrorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Farbe: ON ERROR", colorOnError);
            if (c != null) { colorOnError = c; applyColorToButton(colorOnErrorBtn, c); }
        });
        fb.addRow("ON ERROR:", colorOnErrorBtn);

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

        // Save Natural block colors
        s.naturalColorSubroutine = colorToHex(colorSubroutine);
        s.naturalColorDefineData = colorToHex(colorDefineData);
        s.naturalColorOnError    = colorToHex(colorOnError);
    }

    // ── Color helper methods ──

    private static JButton createColorButton(Color color) {
        JButton btn = new JButton();
        btn.setPreferredSize(new Dimension(80, 24));
        applyColorToButton(btn, color);
        return btn;
    }

    private static void applyColorToButton(JButton btn, Color color) {
        btn.setBackground(color);
        btn.setOpaque(true);
        btn.setBorderPainted(true);
        // Show hex code as text for clarity
        btn.setText(colorToHex(color));
        btn.setForeground(brightness(color) < 128 ? Color.WHITE : Color.BLACK);
    }

    private static String colorToHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    static Color hexToColor(String hex, Color fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        try {
            return Color.decode(hex);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int brightness(Color c) {
        return (c.getRed() * 299 + c.getGreen() * 587 + c.getBlue() * 114) / 1000;
    }
}

