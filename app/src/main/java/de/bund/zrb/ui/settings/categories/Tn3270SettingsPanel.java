package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.MouseFkeyBinding;
import de.bund.zrb.model.MouseFkeyBinding.Modifier;
import de.bund.zrb.model.MouseFkeyBinding.MouseAction;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings panel for TN3270 terminal configuration.
 */
public class Tn3270SettingsPanel extends AbstractSettingsPanel {

    private final JSpinner portSpinner;
    private final JCheckBox tlsCheckBox;
    private final JTextField termTypeField;
    private final JSpinner keepAliveSpinner;
    private final JCheckBox autoLoginCheckBox;
    private final JCheckBox autoCommandCheckBox;
    private final JTextField autoCommandField;
    private final JSpinner actionDelaySpinner;
    private final JSpinner fkeyOpacitySpinner;
    private final JCheckBox cosmicClockCheckBox;
    private final JSpinner cosmicClockFactorSpinner;
    private final JCheckBox germanNamesCheckBox;
    private final MouseBindingTableModel mouseBindingModel;

    public Tn3270SettingsPanel() {
        super("tn3270", "3270-Terminal");
        FormBuilder fb = new FormBuilder();

        fb.addSection("TN3270 Verbindung");

        portSpinner = new JSpinner(new SpinnerNumberModel(settings.tn3270Port, 1, 65535, 1));
        portSpinner.setToolTipText("Standard: 23");
        fb.addRow("Port:", portSpinner);

        tlsCheckBox = new JCheckBox("SSL/TLS verwenden", settings.tn3270Tls);
        tlsCheckBox.setToolTipText("Verschlüsselte Verbindung zum Host");
        fb.addWide(tlsCheckBox);

        termTypeField = new JTextField(settings.tn3270TermType != null ? settings.tn3270TermType : "IBM-3278-2", 20);
        termTypeField.setToolTipText("z.B. IBM-3278-2, IBM-3278-3, IBM-3278-4, IBM-3278-5, IBM-3279-2");
        fb.addRow("Terminal-Typ:", termTypeField);

        keepAliveSpinner = new JSpinner(new SpinnerNumberModel(settings.tn3270KeepAliveTimeout, 0, 3600, 10));
        keepAliveSpinner.setToolTipText("KeepAlive-Intervall in Sekunden (0 = deaktiviert)");
        fb.addRow("KeepAlive (Sek.):", keepAliveSpinner);

        fb.addSection("Automatisierung");

        autoLoginCheckBox = new JCheckBox("Auto-Login nach Verbindung", settings.tn3270AutoLogin);
        autoLoginCheckBox.setToolTipText("Sendet Benutzername und Passwort automatisch nach dem Verbinden");
        fb.addWide(autoLoginCheckBox);

        autoCommandCheckBox = new JCheckBox("Nach Login Befehl senden:", settings.tn3270AutoCommand);
        autoCommandCheckBox.setToolTipText("Sendet nach dem Login automatisch einen Befehl (z.B. um den Startbildschirm zu überspringen)");
        autoCommandField = new JTextField(
                settings.tn3270AutoCommandText != null ? settings.tn3270AutoCommandText : "a", 10);
        autoCommandField.setToolTipText("Befehl der nach dem Login gesendet wird (z.B. \"a\" + Enter)");
        autoCommandField.setEnabled(settings.tn3270AutoCommand);

        // Enable/disable the text field based on the checkbox
        autoCommandCheckBox.addActionListener(e -> autoCommandField.setEnabled(autoCommandCheckBox.isSelected()));
        // Also disable auto-command when auto-login is off
        autoLoginCheckBox.addActionListener(e -> {
            boolean loginOn = autoLoginCheckBox.isSelected();
            autoCommandCheckBox.setEnabled(loginOn);
            autoCommandField.setEnabled(loginOn && autoCommandCheckBox.isSelected());
        });
        autoCommandCheckBox.setEnabled(settings.tn3270AutoLogin);

        JPanel cmdPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        cmdPanel.add(autoCommandCheckBox);
        cmdPanel.add(autoCommandField);
        fb.addWide(cmdPanel);

        actionDelaySpinner = new JSpinner(new SpinnerNumberModel(settings.tn3270ActionDelayMs, 0, 10000, 100));
        actionDelaySpinner.setToolTipText("Wartezeit in Millisekunden nach jeder AID-Taste (Enter, F-Key) bei Auto-Login und Makro-Wiedergabe");
        fb.addRow("Aktions-Delay (ms):", actionDelaySpinner);

        fb.addSection("Darstellung");

        fkeyOpacitySpinner = new JSpinner(new SpinnerNumberModel(settings.tn3270FkeyOverlayOpacity, 0, 100, 5));
        fkeyOpacitySpinner.setToolTipText("Transparenz der F-Tasten-Leiste in Prozent (0 = unsichtbar, 100 = deckend)");
        fb.addRow("F-Tasten Deckkraft (%):", fkeyOpacitySpinner);

        cosmicClockCheckBox = new JCheckBox("Kosmische Uhr als Hintergrund", settings.cosmicClockEnabled);
        cosmicClockCheckBox.setToolTipText("Animierter Sternenhimmel hinter dem Terminal (deaktiviert = klassisch schwarzer Hintergrund)");

        cosmicClockFactorSpinner = new JSpinner(new SpinnerNumberModel(
                (int) settings.cosmicClockTimeFactor, 1, 10000, 10));
        cosmicClockFactorSpinner.setToolTipText("Zeitfaktor (1 = Echtzeit, 120 = 2 Min ≈ 4 Std simuliert)");
        cosmicClockFactorSpinner.setEnabled(settings.cosmicClockEnabled);

        germanNamesCheckBox = new JCheckBox("Sternbild-Namen auf Deutsch", settings.cosmicClockGermanNames);
        germanNamesCheckBox.setToolTipText("Zeigt Konstellationsnamen auf Deutsch an (z.B. 'Großer Wagen' statt 'Big Dipper')");
        germanNamesCheckBox.setEnabled(settings.cosmicClockEnabled);

        cosmicClockCheckBox.addActionListener(e -> {
                cosmicClockFactorSpinner.setEnabled(cosmicClockCheckBox.isSelected());
                germanNamesCheckBox.setEnabled(cosmicClockCheckBox.isSelected());
        });

        JPanel clockPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        clockPanel.add(cosmicClockCheckBox);
        clockPanel.add(new JLabel("Faktor:"));
        clockPanel.add(cosmicClockFactorSpinner);
        fb.addWide(clockPanel);

        fb.addWide(germanNamesCheckBox);

        // ── Mouse → F-Key bindings ──────────────────────────────
        fb.addSection("Maus-Aktionen");
        fb.addInfo("Maustasten und Scrollrad können F-Tasten auslösen. Änderungen werden sofort übernommen.");

        List<MouseFkeyBinding> bindings = settings.tn3270MouseFkeyBindings;
        if (bindings == null) bindings = MouseFkeyBinding.getDefaults();
        mouseBindingModel = new MouseBindingTableModel(new ArrayList<>(bindings));

        JTable bindingTable = new JTable(mouseBindingModel);
        bindingTable.setRowHeight(24);
        bindingTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // ComboBox editors for the enum columns
        JComboBox<MouseAction> actionCombo = new JComboBox<>(MouseAction.values());
        bindingTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(actionCombo));

        JComboBox<Modifier> modCombo = new JComboBox<>(Modifier.values());
        bindingTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(modCombo));

        JComboBox<String> fkeyCombo = new JComboBox<>();
        for (int i = 1; i <= 24; i++) fkeyCombo.addItem("F" + i);
        bindingTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(fkeyCombo));

        // Renderer for the F-key column (show "F3" instead of "3")
        bindingTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                if (v instanceof Integer) setText("F" + v);
                return this;
            }
        });

        JScrollPane tableScroll = new JScrollPane(bindingTable);
        tableScroll.setPreferredSize(new Dimension(0, 140));
        fb.addWide(tableScroll);

        JButton addBtn = new JButton("Hinzufügen");
        addBtn.addActionListener(e -> {
            mouseBindingModel.addRow(new MouseFkeyBinding(MouseAction.BACK, Modifier.NONE, 3));
            int row = mouseBindingModel.getRowCount() - 1;
            bindingTable.setRowSelectionInterval(row, row);
        });
        JButton removeBtn = new JButton("Entfernen");
        removeBtn.addActionListener(e -> {
            int row = bindingTable.getSelectedRow();
            if (row >= 0) mouseBindingModel.removeRow(row);
        });
        JButton resetBtn = new JButton("Zurücksetzen");
        resetBtn.addActionListener(e -> mouseBindingModel.resetToDefaults());
        fb.addButtons(addBtn, removeBtn, resetBtn);

        fb.addInfo("<html><i>Host und Benutzer werden aus den Server-Einstellungen übernommen.<br>"
                + "Der Port kann beim Verbinden im Dialog überschrieben werden.</i></html>");

        installPanel(fb);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.tn3270Port = ((Number) portSpinner.getValue()).intValue();
        s.tn3270Tls = tlsCheckBox.isSelected();
        s.tn3270TermType = termTypeField.getText().trim();
        s.tn3270KeepAliveTimeout = ((Number) keepAliveSpinner.getValue()).intValue();
        s.tn3270AutoLogin = autoLoginCheckBox.isSelected();
        s.tn3270AutoCommand = autoCommandCheckBox.isSelected();
        s.tn3270AutoCommandText = autoCommandField.getText();
        s.tn3270ActionDelayMs = ((Number) actionDelaySpinner.getValue()).intValue();
        s.tn3270FkeyOverlayOpacity = ((Number) fkeyOpacitySpinner.getValue()).intValue();
        s.cosmicClockEnabled = cosmicClockCheckBox.isSelected();
        s.cosmicClockTimeFactor = ((Number) cosmicClockFactorSpinner.getValue()).doubleValue();
        s.cosmicClockGermanNames = germanNamesCheckBox.isSelected();
        s.tn3270MouseFkeyBindings = mouseBindingModel.getBindings();
    }

    // ── Table model for mouse bindings ──────────────────────────

    private static class MouseBindingTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Maus-Aktion", "Modifier", "F-Taste"};
        private final List<MouseFkeyBinding> data;

        MouseBindingTableModel(List<MouseFkeyBinding> data) {
            this.data = data;
        }

        List<MouseFkeyBinding> getBindings() {
            return new ArrayList<>(data);
        }

        void addRow(MouseFkeyBinding b) {
            data.add(b);
            fireTableRowsInserted(data.size() - 1, data.size() - 1);
        }

        void removeRow(int row) {
            data.remove(row);
            fireTableRowsDeleted(row, row);
        }

        void resetToDefaults() {
            data.clear();
            data.addAll(MouseFkeyBinding.getDefaults());
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Class<?> getColumnClass(int col) {
            switch (col) {
                case 0: return MouseAction.class;
                case 1: return Modifier.class;
                case 2: return Integer.class;
                default: return Object.class;
            }
        }

        @Override public boolean isCellEditable(int row, int col) { return true; }

        @Override
        public Object getValueAt(int row, int col) {
            MouseFkeyBinding b = data.get(row);
            switch (col) {
                case 0: return b.mouseAction;
                case 1: return b.modifier;
                case 2: return b.fkey;
                default: return null;
            }
        }

        @Override
        public void setValueAt(Object val, int row, int col) {
            MouseFkeyBinding b = data.get(row);
            switch (col) {
                case 0:
                    b.mouseAction = (val instanceof MouseAction) ? (MouseAction) val
                            : MouseAction.valueOf(val.toString());
                    break;
                case 1:
                    b.modifier = (val instanceof Modifier) ? (Modifier) val
                            : Modifier.valueOf(val.toString());
                    break;
                case 2:
                    if (val instanceof Integer) {
                        b.fkey = (Integer) val;
                    } else {
                        // "F3" → 3
                        String s = val.toString().replaceAll("[^0-9]", "");
                        if (!s.isEmpty()) b.fkey = Integer.parseInt(s);
                    }
                    break;
            }
            fireTableCellUpdated(row, col);
        }
    }
}
