package org.example.ui;

import org.example.ftp.*;
import org.example.model.LineEndingOption;
import org.example.model.Settings;
import org.example.util.SettingsManager;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SettingsDialog {

    public static void show(Component parent, FtpManager ftpManager) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createDefaultGbc();

        // Zeichensatz-Auswahl
        JComboBox<String> encodingCombo = new JComboBox<>();
        List<String> encodings = SettingsManager.SUPPORTED_ENCODINGS;
        encodings.forEach(encodingCombo::addItem);

        Settings settings = SettingsManager.load();
        String currentEncoding = settings.encoding != null ? settings.encoding : "windows-1252";
        encodingCombo.setSelectedItem(currentEncoding);

        addEncodingSelector(panel, gbc, encodingCombo);

        // Schriftart-Auswahl
        gbc.gridwidth = 2;
        panel.add(new JLabel("Editor-Schriftart:"), gbc);
        gbc.gridy++;

        JComboBox<String> fontCombo = new JComboBox<>(new String[] {
                "Monospaced", "Consolas", "Courier New", "Menlo", "Dialog"
        });
        fontCombo.setSelectedItem(settings.editorFont);
        panel.add(fontCombo, gbc);
        gbc.gridy++;
        gbc.gridwidth = 1;

        // Schriftgröße
        gbc.gridwidth = 2;
        panel.add(new JLabel("Editor-Schriftgröße:"), gbc);
        gbc.gridy++;
        JComboBox<Integer> fontSizeCombo = new JComboBox<>(new Integer[] {
                10, 11, 12, 13, 14, 16, 18, 20, 24, 28, 32, 36, 48, 72
        });
        fontSizeCombo.setEditable(true);
        fontSizeCombo.setSelectedItem(settings.editorFontSize);
        panel.add(fontSizeCombo, gbc);
        gbc.gridy++;
        gbc.gridwidth = 1;

        // Zeilenumbruch
        panel.add(new JLabel("Zeilenumbruch des Servers:"), gbc);
        gbc.gridy++;
        JComboBox<String> lineEndingBox = LineEndingOption.createLineEndingComboBox(settings.lineEnding);
        panel.add(lineEndingBox, gbc);
        gbc.gridy++;

        // Marker-Linie (z. B. bei Spalte 80)
        gbc.gridwidth = 2;
        panel.add(new JLabel("Vertikale Markierung bei Spalte (0 = aus):"), gbc);
        gbc.gridy++;
        JSpinner marginSpinner = new JSpinner(new SpinnerNumberModel(
                Math.max(0, settings.marginColumn),  // sicherstellen, dass 0 erlaubt ist
                0, 200, 1
        ));
        panel.add(marginSpinner, gbc);
        gbc.gridy++;
        gbc.gridwidth = 1;

        // Login-Dialog unterdrücken
        JCheckBox hideLoginBox = new JCheckBox("Login-Fenster verbergen (wenn Passwort gespeichert)");
        hideLoginBox.setSelected(settings.hideLoginDialog);
        panel.add(hideLoginBox, gbc);
        gbc.gridy++;

        // Login beim Start (new Session), falls Bookmarks nicht verwendet werden
        JCheckBox autoConnectBox = new JCheckBox("Automatisch verbinden (beim Start)");
        autoConnectBox.setSelected(settings.autoConnect);
        panel.add(autoConnectBox, gbc);
        gbc.gridy++;

        // User Profile Folder
        JButton openFolderButton = new JButton("\uD83D\uDCC1");
        openFolderButton.setToolTipText("Einstellungsordner öffnen");
        openFolderButton.setMargin(new Insets(0, 5, 0, 5));
        openFolderButton.setFocusable(false);
        openFolderButton.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(SettingsManager.getSettingsFolder());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(parent, "Ordner konnte nicht geöffnet werden:\n" + ex.getMessage());
            }
        });
        panel.add(openFolderButton, gbc);
        gbc.gridy++;

        // FTP-Transferoptionen (TYPE, FORMAT, STRUCTURE, MODE)
        panel.add(new JLabel("FTP Datei-Typ (TYPE):"), gbc);
        gbc.gridy++;
        JComboBox<FtpFileType> typeBox = org.example.ui.components.ComboBoxHelper.createComboBoxWithNullOption(
                FtpFileType.class, settings.ftpFileType, "Standard"
        );
        panel.add(typeBox, gbc);
        gbc.gridy++;

        panel.add(new JLabel("FTP Text-Format (FORMAT):"), gbc);
        gbc.gridy++;
        JComboBox<FtpTextFormat> formatBox = org.example.ui.components.ComboBoxHelper.createComboBoxWithNullOption(
                FtpTextFormat.class, settings.ftpTextFormat, "Standard"
        );
        panel.add(formatBox, gbc);
        gbc.gridy++;

        panel.add(new JLabel("FTP Dateistruktur (STRUCTURE):"), gbc);
        gbc.gridy++;
        JComboBox<FtpFileStructure> structureBox = org.example.ui.components.ComboBoxHelper.createComboBoxWithNullOption(
                FtpFileStructure.class, settings.ftpFileStructure, "Automatisch"
        );
        panel.add(structureBox, gbc);
        gbc.gridy++;

        panel.add(new JLabel("FTP Übertragungsmodus (MODE):"), gbc);
        gbc.gridy++;
        JComboBox<FtpTransferMode> modeBox = org.example.ui.components.ComboBoxHelper.createComboBoxWithNullOption(
                FtpTransferMode.class, settings.ftpTransferMode, "Standard"
        );
        panel.add(modeBox, gbc);
        gbc.gridy++;

        JCheckBox hexDumpBox = new JCheckBox("Hexdump in Konsole anzeigen (Debugzwecke)");
        hexDumpBox.setSelected(settings.enableHexDump);
        panel.add(hexDumpBox, gbc);
        gbc.gridy++;

        // Farbüberschreibungen für Feldnamen
        gbc.gridwidth = 2;
        panel.add(new JLabel("Farbüberschreibungen für Feldnamen:"), gbc);
        gbc.gridy++;

        ColorOverrideTableModel colorModel = new ColorOverrideTableModel(settings.fieldColorOverrides);
        JTable colorTable = new JTable(colorModel);
        colorTable.getColumnModel().getColumn(1).setCellEditor(new ColorCellEditor());
        colorTable.getColumnModel().getColumn(1).setCellRenderer((table, value, isSelected, hasFocus, row, col) -> {
            JLabel label = new JLabel(value != null ? value.toString() : "");
            label.setOpaque(true);
            label.setBackground(parseHexColor(String.valueOf(value), Color.WHITE));
            return label;
        });
        colorTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    // open color chooser does not work cause of missing double click event
                }
                else {
                    int row = colorTable.rowAtPoint(e.getPoint());
                    int column = colorTable.columnAtPoint(e.getPoint());
                    if (column == 1) {
                        colorTable.editCellAt(row, column);
                    }
                }
            }
        });

        colorTable.setFillsViewportHeight(true);
        colorTable.setPreferredScrollableViewportSize(new Dimension(300, 100));

        JPanel colorPanel = new JPanel(new BorderLayout());
        colorPanel.add(new JScrollPane(colorTable), BorderLayout.CENTER);

        JPanel colorButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addRowButton = new JButton("➕");
        addRowButton.addActionListener(e -> {
            String key = JOptionPane.showInputDialog(parent, "Feldname eingeben:");
            if (key != null && !key.trim().isEmpty()) {
                colorModel.addEntry(key.trim().toUpperCase(), "#00AA00");
            }
        });
        JButton removeRowButton = new JButton("➖");
        removeRowButton.addActionListener(e -> {
            int selected = colorTable.getSelectedRow();
            if (selected >= 0) {
                colorModel.removeEntry(selected);
            }
        });

        colorButtons.add(addRowButton);
        colorButtons.add(removeRowButton);
        colorPanel.add(colorButtons, BorderLayout.SOUTH);

        panel.add(colorPanel, gbc);
        gbc.gridy++;


        // Dialog anzeigen
        int result = JOptionPane.showConfirmDialog(parent, panel, "Einstellungen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            settings.encoding = (String) encodingCombo.getSelectedItem();
            settings.editorFont = (String) fontCombo.getSelectedItem();
            settings.editorFontSize = Optional.ofNullable(fontSizeCombo.getEditor().getItem())
                    .map(Object::toString)
                    .map(s -> {
                        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
                    })
                    .orElse(12);
            settings.lineEnding = LineEndingOption.normalizeInput(lineEndingBox.getSelectedItem());
            settings.marginColumn = (Integer) marginSpinner.getValue();
            settings.hideLoginDialog = hideLoginBox.isSelected();
            settings.autoConnect = autoConnectBox.isSelected();
            settings.ftpFileType = org.example.ui.components.ComboBoxHelper.getSelectedEnumValue(typeBox, FtpFileType.class);
            settings.ftpTextFormat = org.example.ui.components.ComboBoxHelper.getSelectedEnumValue(formatBox, FtpTextFormat.class);
            settings.ftpFileStructure = org.example.ui.components.ComboBoxHelper.getSelectedEnumValue(structureBox, FtpFileStructure.class);
            settings.ftpTransferMode = org.example.ui.components.ComboBoxHelper.getSelectedEnumValue(modeBox, FtpTransferMode.class);
            settings.enableHexDump = hexDumpBox.isSelected();
            SettingsManager.save(settings);

            ftpManager.getClient().setControlEncoding(settings.encoding);

            JOptionPane.showMessageDialog(parent,
                    "Einstellungen wurden gespeichert.",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static GridBagConstraints createDefaultGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        return gbc;
    }

    private static void addEncodingSelector(JPanel panel, GridBagConstraints gbc, JComboBox<String> encodingCombo) {
        gbc.gridwidth = 2;
        panel.add(new JLabel("Zeichenkodierung:"), gbc);
        gbc.gridy++;
        panel.add(encodingCombo, gbc);
        gbc.gridy++;
        gbc.gridwidth = 1;
    }

    private static Color parseHexColor(String hex, Color fallback) {
        if (hex == null) return fallback;
        try {
            return Color.decode(hex);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }


    private static class ColorOverrideTableModel extends AbstractTableModel {
        private final java.util.List<String> keys;
        private final Map<String, String> colorMap;

        public ColorOverrideTableModel(Map<String, String> colorMap) {
            this.colorMap = colorMap;
            this.keys = new ArrayList<>(colorMap.keySet());
        }

        @Override
        public int getRowCount() {
            return keys.size();
        }

        @Override
        public int getColumnCount() {
            return 2; // name + farbe
        }

        @Override
        public String getColumnName(int col) {
            return col == 0 ? "Feldname" : "Farbe (Hex)";
        }

        @Override
        public Object getValueAt(int row, int col) {
            String key = keys.get(row);
            return col == 0 ? key : colorMap.get(key);
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col == 1) {
                colorMap.put(keys.get(row), value.toString());
            }
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col == 1;
        }

        public void addEntry(String name, String color) {
            colorMap.put(name, color);
            keys.add(name);
            fireTableDataChanged();
        }

        public void removeEntry(int rowIndex) {
            if (rowIndex >= 0 && rowIndex < keys.size()) {
                colorMap.remove(keys.get(rowIndex));
                keys.remove(rowIndex);
                fireTableDataChanged();
            }
        }
    }

    private static class ColorCellEditor extends AbstractCellEditor implements TableCellEditor {

        private final JButton button = new JButton();
        private String currentColorHex;

        public ColorCellEditor() {
            button.addActionListener(e -> {
                Color initialColor = parseHexColor(currentColorHex, Color.BLACK);
                Color selectedColor = JColorChooser.showDialog(button, "Farbe wählen", initialColor);
                if (selectedColor != null) {
                    currentColorHex = "#" + Integer.toHexString(selectedColor.getRGB()).substring(2).toUpperCase();
                    button.setBackground(selectedColor);
                    fireEditingStopped();
                }
            });
        }

        @Override
        public Object getCellEditorValue() {
            return currentColorHex;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
                                                     int row, int column) {
            currentColorHex = String.valueOf(value);
            button.setBackground(parseHexColor(currentColorHex, Color.BLACK));
            return button;
        }

        private Color parseHexColor(String hex, Color fallback) {
            try {
                return Color.decode(hex);
            } catch (Exception e) {
                return fallback;
            }
        }
    }


}
