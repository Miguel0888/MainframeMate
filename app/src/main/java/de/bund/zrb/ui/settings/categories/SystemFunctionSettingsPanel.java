package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.helper.SystemFunctionSettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.model.SystemFunctionEntry;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings panel for managing system function markers (IDCAMS, IEFBR14, SORT …).
 * <p>
 * When a system function appears in JCL (EXEC PGM=...), it is linked to its
 * Wikipedia article. The user can add, edit, or remove entries.
 */
public class SystemFunctionSettingsPanel extends AbstractSettingsPanel {

    private List<SystemFunctionEntry> entries;
    private FunctionTableModel tableModel;
    private JTable table;

    public SystemFunctionSettingsPanel() {
        super("sysfunc", "Systemfunktionen");
        entries = SystemFunctionSettingsHelper.load();
        if (entries.isEmpty()) {
            entries = SystemFunctionSettingsHelper.getDefaults();
            SystemFunctionSettingsHelper.save(entries);
        }
        buildUi();
    }

    private void buildUi() {
        setLayout(new BorderLayout());

        // Info label
        JLabel infoLabel = new JLabel(
                "<html><b>Systemfunktionen (z/OS Utilities)</b><br>"
                        + "<small>Programme die hier eingetragen sind, werden im JCL-Quelltext als Hyperlink "
                        + "dargestellt. Ein Doppelklick öffnet den zugehörigen Wikipedia-Artikel.</small></html>");
        infoLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(infoLabel, BorderLayout.NORTH);

        // Table
        tableModel = new FunctionTableModel();
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setPreferredScrollableViewportSize(new Dimension(700, 300));
        table.getColumnModel().getColumn(0).setPreferredWidth(100);  // Name
        table.getColumnModel().getColumn(1).setPreferredWidth(200);  // Wiki (DE)
        table.getColumnModel().getColumn(2).setPreferredWidth(200);  // Wiki (EN)
        table.getColumnModel().getColumn(3).setPreferredWidth(300);  // Beschreibung

        add(new JScrollPane(table), BorderLayout.CENTER);

        // Buttons
        JButton addBtn = new JButton("➕ Hinzufügen");
        addBtn.addActionListener(e -> addEntry());

        JButton editBtn = new JButton("✏ Bearbeiten");
        editBtn.addActionListener(e -> editEntry());

        JButton removeBtn = new JButton("❌ Entfernen");
        removeBtn.addActionListener(e -> removeEntry());

        JButton defaultsBtn = new JButton("🔄 Defaults laden");
        defaultsBtn.setToolTipText("Fehlende Standard-Systemfunktionen ergänzen");
        defaultsBtn.addActionListener(e -> loadDefaults());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        buttonPanel.add(addBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(removeBtn);
        buttonPanel.add(Box.createHorizontalStrut(16));
        buttonPanel.add(defaultsBtn);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void addEntry() {
        SystemFunctionEntry newEntry = new SystemFunctionEntry("", "", "", "");
        if (showEditorDialog(newEntry, "Neue Systemfunktion")) {
            // Check for duplicates
            for (SystemFunctionEntry existing : entries) {
                if (existing.getName().equalsIgnoreCase(newEntry.getName())) {
                    JOptionPane.showMessageDialog(this,
                            "Ein Eintrag mit dem Namen \"" + newEntry.getName() + "\" existiert bereits.",
                            "Duplikat", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            entries.add(newEntry);
            tableModel.fireTableDataChanged();
            SystemFunctionSettingsHelper.save(entries);
        }
    }

    private void editEntry() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        SystemFunctionEntry entry = entries.get(row);
        String oldName = entry.getName();
        if (showEditorDialog(entry, "Systemfunktion bearbeiten")) {
            // Check for name collision with different entry
            if (!entry.getName().equalsIgnoreCase(oldName)) {
                for (SystemFunctionEntry existing : entries) {
                    if (existing != entry && existing.getName().equalsIgnoreCase(entry.getName())) {
                        JOptionPane.showMessageDialog(this,
                                "Ein Eintrag mit dem Namen \"" + entry.getName() + "\" existiert bereits.",
                                "Duplikat", JOptionPane.WARNING_MESSAGE);
                        entry.setName(oldName); // revert
                        return;
                    }
                }
            }
            tableModel.fireTableDataChanged();
            SystemFunctionSettingsHelper.save(entries);
        }
    }

    private void removeEntry() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        SystemFunctionEntry entry = entries.get(row);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Systemfunktion \"" + entry.getName() + "\" wirklich entfernen?",
                "Entfernen", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            entries.remove(row);
            tableModel.fireTableDataChanged();
            SystemFunctionSettingsHelper.save(entries);
        }
    }

    private void loadDefaults() {
        List<SystemFunctionEntry> defaults = SystemFunctionSettingsHelper.getDefaults();
        Map<String, SystemFunctionEntry> existing = new LinkedHashMap<String, SystemFunctionEntry>();
        for (SystemFunctionEntry e : entries) {
            existing.put(e.getName().toUpperCase(), e);
        }
        int added = 0;
        for (SystemFunctionEntry def : defaults) {
            if (!existing.containsKey(def.getName().toUpperCase())) {
                entries.add(def);
                added++;
            }
        }
        if (added > 0) {
            tableModel.fireTableDataChanged();
            SystemFunctionSettingsHelper.save(entries);
            JOptionPane.showMessageDialog(this,
                    added + " Standard-Systemfunktion(en) hinzugefügt.",
                    "Defaults", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Alle Standard-Systemfunktionen sind bereits vorhanden.",
                    "Defaults", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private boolean showEditorDialog(SystemFunctionEntry entry, String title) {
        JTextField nameField = new JTextField(entry.getName(), 20);
        JTextField wikiDeField = new JTextField(entry.getWikiTitleDe(), 30);
        JTextField wikiEnField = new JTextField(entry.getWikiTitleEn(), 30);
        JTextField descField = new JTextField(entry.getDescription(), 40);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Programmname (PGM):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(nameField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Wikipedia (DE):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(wikiDeField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Wikipedia (EN):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(wikiEnField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Beschreibung:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(descField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel hint = new JLabel(
                "<html><small><i>Wikipedia-Titel = Artikelname in der URL, z.B. \"Access_Method_Services\".<br>"
                        + "Leer lassen = Programmname wird als Suchbegriff verwendet.</i></small></html>");
        hint.setForeground(Color.GRAY);
        panel.add(hint, gbc);

        int result = JOptionPane.showConfirmDialog(this, panel, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return false;

        String name = nameField.getText().trim().toUpperCase();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Der Programmname darf nicht leer sein.",
                    "Fehler", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        entry.setName(name);
        entry.setWikiTitleDe(wikiDeField.getText().trim());
        entry.setWikiTitleEn(wikiEnField.getText().trim());
        entry.setDescription(descField.getText().trim());
        return true;
    }

    @Override
    protected void applyToSettings(Settings s) {
        // System functions are stored in their own JSON file, not in settings.json.
        // Save is done immediately on add/edit/remove.
    }

    // ═══════════════════════════════════════════════════════════
    //  Table model
    // ═══════════════════════════════════════════════════════════

    private class FunctionTableModel extends AbstractTableModel {
        private final String[] COLUMNS = {"Programmname", "Wiki (DE)", "Wiki (EN)", "Beschreibung"};

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            SystemFunctionEntry e = entries.get(rowIndex);
            switch (columnIndex) {
                case 0: return e.getName();
                case 1: return e.getWikiTitleDe();
                case 2: return e.getWikiTitleEn();
                case 3: return e.getDescription();
                default: return "";
            }
        }
    }
}

