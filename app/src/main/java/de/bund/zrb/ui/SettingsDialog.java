package de.bund.zrb.ui;

import de.bund.zrb.ftp.*;
import de.bund.zrb.model.*;
import de.bund.zrb.ui.components.ComboBoxHelper;
import de.bund.zrb.util.SettingsManager;

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

    private static JButton removeRowButton;
    private static JButton addRowButton;
    private static JPanel colorButtons;
    private static ColorOverrideTableModel colorModel;
    private static JCheckBox hexDumpBox;
    private static JComboBox<FtpTransferMode> modeBox;
    private static JComboBox<FtpFileStructure> structureBox;
    private static JComboBox<FtpTextFormat> formatBox;
    private static JComboBox<FtpFileType> typeBox;
    private static JButton openFolderButton;
    private static JCheckBox autoConnectBox;
    private static JCheckBox hideLoginBox;
    private static JSpinner marginSpinner;
    private static JComboBox<String> paddingBox;
    private static JComboBox<String> endMarkerBox;
    private static JCheckBox stripFinalNewlineBox;
    private static JComboBox<String> lineEndingBox;
    private static JComboBox<Integer> fontSizeCombo;
    private static JComboBox<String> fontCombo;
    private static JComboBox<String> encodingCombo;
    private static Settings settings;
    private static JTable colorTable;
    private static JTextField ollamaUrlField;
    private static JTextField ollamaModelField;
    private static JTextField ollamaKeepAliveField;

    private static JComboBox<AiProvider> providerCombo;

    public static void show(Component parent, FtpManager ftpManager) {
        JTabbedPane tabs = new JTabbedPane();

        JPanel generalContent = new JPanel(new GridBagLayout());
        JScrollPane generalPanel = new JScrollPane(generalContent);
        JPanel colorContent = new JPanel(new BorderLayout());
        JScrollPane colorPanel = new JScrollPane(colorContent);
        JPanel transformContent = new JPanel(new GridBagLayout());
        JScrollPane transformPanel = new JScrollPane(transformContent);
        JPanel connectContent = new JPanel(new GridBagLayout());
        JScrollPane connectPanel = new JScrollPane(connectContent);
        JPanel aiContent = new JPanel(new GridBagLayout());
        JScrollPane aiPanel = new JScrollPane(aiContent);

        tabs.addTab("Allgemein", generalPanel);
        tabs.addTab("Farbzuordnung", colorPanel);
        tabs.addTab("Datenumwandlung", transformPanel);
        tabs.addTab("FTP-Verbindung", connectPanel);
        tabs.add("KI", aiPanel);

        settings = SettingsManager.load();

        createGeneralContent(generalContent, parent);
        createTransformContent(transformContent);
        createConnectContent(connectContent);
        createColorContent(colorContent, parent);
        createAiContent(aiContent);

        showAndApply(parent, ftpManager, tabs);
    }

    private static void createGeneralContent(JPanel generalContent, Component parent) {
        GridBagConstraints gbcGeneral = createDefaultGbc();

        // Schriftart-Auswahl
        gbcGeneral.gridwidth = 2;
        generalContent.add(new JLabel("Editor-Schriftart:"), gbcGeneral);
        gbcGeneral.gridy++;

        fontCombo = new JComboBox<>(new String[] {
                "Monospaced", "Consolas", "Courier New", "Menlo", "Dialog"
        });
        fontCombo.setSelectedItem(settings.editorFont);
        generalContent.add(fontCombo, gbcGeneral);
        gbcGeneral.gridy++;
        gbcGeneral.gridwidth = 1;

        // Schriftgröße
        gbcGeneral.gridwidth = 2;
        generalContent.add(new JLabel("Editor-Schriftgröße:"), gbcGeneral);
        gbcGeneral.gridy++;
        fontSizeCombo = new JComboBox<>(new Integer[] {
                10, 11, 12, 13, 14, 16, 18, 20, 24, 28, 32, 36, 48, 72
        });
        fontSizeCombo.setEditable(true);
        fontSizeCombo.setSelectedItem(settings.editorFontSize);
        generalContent.add(fontSizeCombo, gbcGeneral);
        gbcGeneral.gridy++;
        gbcGeneral.gridwidth = 1;

        // Marker-Linie (z. B. bei Spalte 80)
        gbcGeneral.gridwidth = 2;
        generalContent.add(new JLabel("Vertikale Markierung bei Spalte (0 = aus):"), gbcGeneral);
        gbcGeneral.gridy++;
        marginSpinner = new JSpinner(new SpinnerNumberModel(
                Math.max(0, settings.marginColumn),  // sicherstellen, dass 0 erlaubt ist
                0, 200, 1
        ));
        generalContent.add(marginSpinner, gbcGeneral);
        gbcGeneral.gridy++;
        gbcGeneral.gridwidth = 1;

        // Login-Dialog unterdrücken
        hideLoginBox = new JCheckBox("Login-Fenster verbergen (wenn Passwort gespeichert)");
        hideLoginBox.setSelected(settings.hideLoginDialog);
        generalContent.add(hideLoginBox, gbcGeneral);
        gbcGeneral.gridy++;

        // Login beim Start (new Session), falls Bookmarks nicht verwendet werden
        autoConnectBox = new JCheckBox("Automatisch verbinden (beim Start)");
        autoConnectBox.setSelected(settings.autoConnect);
        generalContent.add(autoConnectBox, gbcGeneral);
        gbcGeneral.gridy++;

        // User Profile Folder
        openFolderButton = new JButton("\uD83D\uDCC1");
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
        generalContent.add(openFolderButton, gbcGeneral);
        gbcGeneral.gridy++;
    }

    private static void createTransformContent(JPanel expertContent) {
        GridBagConstraints gbcTransform = createDefaultGbc();

        // Zeichensatz-Auswahl
        encodingCombo = new JComboBox<>();
        List<String> encodings = SettingsManager.SUPPORTED_ENCODINGS;
        encodings.forEach(encodingCombo::addItem);
        String currentEncoding = settings.encoding != null ? settings.encoding : "windows-1252";
        encodingCombo.setSelectedItem(currentEncoding);
        addEncodingSelector(expertContent, gbcTransform, encodingCombo);

        // Zeilenumbruch
        expertContent.add(new JLabel("Zeilenumbruch des Servers:"), gbcTransform);
        gbcTransform.gridy++;
        lineEndingBox = LineEndingOption.createLineEndingComboBox(settings.lineEnding);
        expertContent.add(lineEndingBox, gbcTransform);
        gbcTransform.gridy++;

        stripFinalNewlineBox = new JCheckBox("Letzten Zeilenumbruch ausblenden (falls vorhanden)");
        stripFinalNewlineBox.setSelected(settings.removeFinalNewline);
        expertContent.add(stripFinalNewlineBox, gbcTransform);
        gbcTransform.gridy++;

        // Dateiende
        expertContent.add(new JLabel("Datei-Ende-Kennung (z. B. FF02, leer = aus):"), gbcTransform);
        gbcTransform.gridy++;
        endMarkerBox = FileEndingOption.createEndMarkerComboBox(settings.fileEndMarker);
        expertContent.add(endMarkerBox, gbcTransform);
        gbcTransform.gridy++;

        // Padding
        expertContent.add(new JLabel("Padding Byte (z. B. 00, leer = aus):"), gbcTransform);
        gbcTransform.gridy++;
        paddingBox = PaddingOption.createPaddingComboBox(settings.padding);
        expertContent.add(paddingBox, gbcTransform);
        gbcTransform.gridy++;
    }

    private static void createConnectContent(JPanel expertContent) {
        GridBagConstraints gbcConnect = createDefaultGbc();

        // FTP-Transferoptionen (TYPE, FORMAT, STRUCTURE, MODE)
        expertContent.add(new JLabel("FTP Datei-Typ (TYPE):"), gbcConnect);
        gbcConnect.gridy++;
        typeBox = ComboBoxHelper.createComboBoxWithNullOption(
                FtpFileType.class, settings.ftpFileType, "Standard"
        );
        expertContent.add(typeBox, gbcConnect);
        gbcConnect.gridy++;

        expertContent.add(new JLabel("FTP Text-Format (FORMAT):"), gbcConnect);
        gbcConnect.gridy++;
        formatBox = ComboBoxHelper.createComboBoxWithNullOption(
                FtpTextFormat.class, settings.ftpTextFormat, "Standard"
        );
        expertContent.add(formatBox, gbcConnect);
        gbcConnect.gridy++;

        expertContent.add(new JLabel("FTP Dateistruktur (STRUCTURE):"), gbcConnect);
        gbcConnect.gridy++;
        structureBox = ComboBoxHelper.createComboBoxWithNullOption(
                FtpFileStructure.class, settings.ftpFileStructure, "Automatisch"
        );
        expertContent.add(structureBox, gbcConnect);
        gbcConnect.gridy++;

        expertContent.add(new JLabel("FTP Übertragungsmodus (MODE):"), gbcConnect);
        gbcConnect.gridy++;
        modeBox = ComboBoxHelper.createComboBoxWithNullOption(
                FtpTransferMode.class, settings.ftpTransferMode, "Standard"
        );
        expertContent.add(modeBox, gbcConnect);
        gbcConnect.gridy++;

        hexDumpBox = new JCheckBox("Hexdump in Konsole anzeigen (Debugzwecke)");
        hexDumpBox.setSelected(settings.enableHexDump);
        expertContent.add(hexDumpBox, gbcConnect);
        gbcConnect.gridy++;
    }

    private static void createColorContent(JPanel colorContent, Component parent) {
        // Farbüberschreibungen für Feldnamen
        colorContent.add(new JLabel("Farbüberschreibungen für Feldnamen:"), BorderLayout.NORTH);

        colorModel = new ColorOverrideTableModel(settings.fieldColorOverrides);
        colorTable = new JTable(colorModel);
//        colorTable.setPreferredScrollableViewportSize(new Dimension(400, 150));
//        colorTable.setRowHeight(22);
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

        colorButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addRowButton = new JButton("➕");
        addRowButton.addActionListener(e -> {
            String key = JOptionPane.showInputDialog(parent, "Feldname eingeben:");
            if (key != null && !key.trim().isEmpty()) {
                colorModel.addEntry(key.trim().toUpperCase(), "#00AA00");
            }
        });
        removeRowButton = new JButton("➖");
        removeRowButton.addActionListener(e -> {
            int selected = colorTable.getSelectedRow();
            if (selected >= 0) {
                colorModel.removeEntry(selected);
            }
        });

        colorButtons.add(addRowButton);
        colorButtons.add(removeRowButton);

        JPanel innerColorPanel = new JPanel(new BorderLayout());
        innerColorPanel.add(new JScrollPane(colorTable), BorderLayout.CENTER);
        innerColorPanel.add(colorButtons, BorderLayout.SOUTH);

        colorContent.add(innerColorPanel, BorderLayout.CENTER);
        colorContent.add(colorButtons, BorderLayout.SOUTH);
    }

    private static void createAiContent(JPanel aiContent) {
        GridBagConstraints gbc = createDefaultGbc();

        providerCombo = new JComboBox<>(AiProvider.values());
        aiContent.add(new JLabel("KI-Provider:"), gbc);
        gbc.gridy++;
        aiContent.add(providerCombo, gbc);
        gbc.gridy++;

        JPanel providerOptionsPanel = new JPanel(new CardLayout());
        aiContent.add(providerOptionsPanel, gbc);
        gbc.gridy++;

        // Panels für Provider
        JPanel disabledPanel = new JPanel();
        JPanel ollamaPanel = new JPanel(new GridBagLayout());
        JPanel localAiPanel = new JPanel(new GridBagLayout());

        providerOptionsPanel.add(disabledPanel, AiProvider.DISABLED.name());
        providerOptionsPanel.add(ollamaPanel, AiProvider.OLLAMA.name());
        providerOptionsPanel.add(localAiPanel, AiProvider.LOCAL_AI.name());

        // OLLAMA-Felder
        GridBagConstraints gbcOllama = createDefaultGbc();
        ollamaPanel.add(new JLabel("OLLAMA URL:"), gbcOllama);
        gbcOllama.gridy++;
        ollamaUrlField = new JTextField(30);
        ollamaPanel.add(ollamaUrlField, gbcOllama);
        gbcOllama.gridy++;
        ollamaPanel.add(new JLabel("Modellname:"), gbcOllama);
        gbcOllama.gridy++;
        ollamaModelField = new JTextField(20);
        ollamaPanel.add(ollamaModelField, gbcOllama);
        gbcOllama.gridy++;
        ollamaPanel.add(new JLabel("Modell beibehalten für (z. B. 30m, 0, -1):"), gbcOllama);
        gbcOllama.gridy++;
        ollamaKeepAliveField = new JTextField(20);
        ollamaPanel.add(ollamaKeepAliveField, gbcOllama);
        gbcOllama.gridy++;


        // LOCAL_AI-Felder (optional, hier nur ein Hinweistext)
        GridBagConstraints gbcLocal = createDefaultGbc();
        localAiPanel.add(new JLabel("Konfiguration für LocalAI folgt."), gbcLocal);

        // Initiale Werte aus Settings
        String providerName = settings.aiConfig.getOrDefault("provider", "DISABLED");
        AiProvider selectedProvider = AiProvider.valueOf(providerName);
        providerCombo.setSelectedItem(selectedProvider);

        ollamaUrlField.setText(settings.aiConfig.getOrDefault("ollama.url", "http://localhost:11434/api/generate"));
        ollamaModelField.setText(settings.aiConfig.getOrDefault("ollama.model", "custom-modell"));
        ollamaKeepAliveField.setText(settings.aiConfig.getOrDefault("ollama.keepalive", "10m"));

        // Umschalten je nach Provider
        providerCombo.addActionListener(e -> {
            AiProvider selected = (AiProvider) providerCombo.getSelectedItem();
            CardLayout cl = (CardLayout) providerOptionsPanel.getLayout();
            cl.show(providerOptionsPanel, selected.name());
        });

        ((CardLayout) providerOptionsPanel.getLayout()).show(providerOptionsPanel, selectedProvider.name());
    }


    private static void showAndApply(Component parent, FtpManager ftpManager, JTabbedPane tabs) {
        // Dialog formatieren
        JPanel container = new JPanel(new BorderLayout());
        container.add(tabs, BorderLayout.CENTER);

        // Bildschirmhöhe holen
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int height = (int) (screenSize.height * 0.8);  // 80% der Bildschirmhöhe
        int width = 600;  // feste Breite

        container.setPreferredSize(new Dimension(width, height));

        // Dialog anzeigen
        int result = JOptionPane.showConfirmDialog(parent, container, "Einstellungen",
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
            settings.removeFinalNewline = stripFinalNewlineBox.isSelected();
            settings.fileEndMarker = FileEndingOption.normalizeInput(endMarkerBox.getSelectedItem());
            settings.padding = PaddingOption.normalizeInput(paddingBox.getSelectedItem());
            settings.marginColumn = (Integer) marginSpinner.getValue();
            settings.hideLoginDialog = hideLoginBox.isSelected();
            settings.autoConnect = autoConnectBox.isSelected();
            settings.ftpFileType = ComboBoxHelper.getSelectedEnumValue(typeBox, FtpFileType.class);
            settings.ftpTextFormat = ComboBoxHelper.getSelectedEnumValue(formatBox, FtpTextFormat.class);
            settings.ftpFileStructure = ComboBoxHelper.getSelectedEnumValue(structureBox, FtpFileStructure.class);
            settings.ftpTransferMode = ComboBoxHelper.getSelectedEnumValue(modeBox, FtpTransferMode.class);
            settings.enableHexDump = hexDumpBox.isSelected();
            settings.aiConfig.put("provider", providerCombo.getSelectedItem().toString());
            settings.aiConfig.put("ollama.url", ollamaUrlField.getText().trim());
            settings.aiConfig.put("ollama.model", ollamaModelField.getText().trim());
            settings.aiConfig.put("ollama.keepalive", ollamaKeepAliveField.getText().trim());

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
