package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class MailSettingsPanel extends AbstractSettingsPanel {

    private final JTextField mailPathField;
    private final JTextField mailContainerClassesField;
    private final JList<String> mailWhitelistJList;

    // Sync settings
    private final JCheckBox cbSyncEnabled;
    private final JCheckBox cbSyncMails;
    private final JCheckBox cbSyncCalendar;
    private final JCheckBox cbSyncContacts;
    private final JCheckBox cbSyncTasks;
    private final JCheckBox cbSyncNotes;
    private final JCheckBox cbSuppressStderr;
    private final JSpinner cooldownSpinner;

    // Notification settings
    private final JCheckBox cbNotifyEnabled;
    private final JTextField defaultColorField;
    private final SenderColorTableModel senderColorModel;

    public MailSettingsPanel() {
        super("mails", "Mails");
        FormBuilder fb = new FormBuilder();

        // ── Pfad ──
        String defaultPath = de.bund.zrb.ui.commands.ConnectMailMenuCommand.getDefaultOutlookPath();
        String currentValue = settings.mailStorePath;
        if (currentValue == null || currentValue.trim().isEmpty()) currentValue = defaultPath;
        mailPathField = new JTextField(currentValue, 30);
        JButton browseButton = new JButton("📂");
        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(mailPathField.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                mailPathField.setText(chooser.getSelectedFile().getAbsolutePath());
        });
        fb.addRowWithButton("Mail-Speicherort:", mailPathField, browseButton);

        String ccValue = settings.mailContainerClasses;
        if (ccValue == null || ccValue.trim().isEmpty())
            ccValue = de.bund.zrb.mail.model.MailboxCategory.getMailContainerClassesAsString();
        mailContainerClassesField = new JTextField(ccValue, 30);
        mailContainerClassesField.setToolTipText("Standard: IPF.Note,IPF.Imap");
        fb.addRow("ContainerClasses:", mailContainerClassesField);

        // ── Sync-Einstellungen ──
        fb.addSection("Hintergrund-Sync (Indizierung)");
        fb.addInfo("Welche Kategorien sollen automatisch indiziert und bei Änderungen aktualisiert werden?");

        cbSyncEnabled = new JCheckBox("Mail-Sync aktivieren");
        cbSyncEnabled.setSelected(settings.mailSyncEnabled);
        cbSyncEnabled.addActionListener(e -> updateSyncCheckboxStates());
        fb.addWide(cbSyncEnabled);

        cbSyncMails = new JCheckBox("📧 E-Mails (IPF.Note, IPF.Imap)");
        cbSyncMails.setSelected(settings.mailSyncMails);
        fb.addWide(cbSyncMails);

        cbSyncCalendar = new JCheckBox("📅 Kalender (IPF.Appointment)");
        cbSyncCalendar.setSelected(settings.mailSyncCalendar);
        fb.addWide(cbSyncCalendar);

        cbSyncContacts = new JCheckBox("👥 Kontakte (IPF.Contact)");
        cbSyncContacts.setSelected(settings.mailSyncContacts);
        fb.addWide(cbSyncContacts);

        cbSyncTasks = new JCheckBox("✅ Aufgaben (IPF.Task)");
        cbSyncTasks.setSelected(settings.mailSyncTasks);
        fb.addWide(cbSyncTasks);

        cbSyncNotes = new JCheckBox("📝 Notizen (IPF.StickyNote)");
        cbSyncNotes.setSelected(settings.mailSyncNotes);
        fb.addWide(cbSyncNotes);

        fb.addGap(4);
        cbSuppressStderr = new JCheckBox("java-libpst Konsolenmeldungen unterdrücken");
        cbSuppressStderr.setSelected(settings.mailSyncSuppressStderr);
        cbSuppressStderr.setToolTipText("Unterdrückt 'Unknown message type' und 'Can't get children' Meldungen auf stderr");
        fb.addWide(cbSuppressStderr);

        fb.addGap(4);
        cooldownSpinner = new JSpinner(new SpinnerNumberModel(settings.mailSyncCooldownSeconds, 5, 3600, 5));
        cooldownSpinner.setToolTipText("Wartezeit in Sekunden nach einem Sync-Lauf, bevor der nächste gestartet wird (Totzeit/Cooldown)");
        fb.addRow("Totzeit (s):", cooldownSpinner);

        updateSyncCheckboxStates();

        // ── Benachrichtigung (Laufschrift) ──
        fb.addSection("Benachrichtigung (Laufschrift)");
        fb.addInfo("Zeigt neue Mails als scrollende Laufschrift in der Menüleiste an.");

        cbNotifyEnabled = new JCheckBox("Laufschrift-Benachrichtigung aktivieren");
        cbNotifyEnabled.setSelected(settings.mailNotifyEnabled);
        fb.addWide(cbNotifyEnabled);

        String defCol = settings.mailNotifyDefaultColor;
        if (defCol == null || defCol.isEmpty()) defCol = "#CC0000";
        defaultColorField = new JTextField(defCol, 10);
        JButton colorPickerBtn = new JButton("🎨");
        colorPickerBtn.setToolTipText("Farbe wählen");
        colorPickerBtn.addActionListener(e -> {
            Color initial = parseHexColor(defaultColorField.getText());
            if (initial == null) initial = Color.RED;
            Color chosen = JColorChooser.showDialog(null, "Standard-Benachrichtigungsfarbe", initial);
            if (chosen != null) defaultColorField.setText(toHex(chosen));
        });
        fb.addRowWithButton("Standardfarbe:", defaultColorField, colorPickerBtn);

        fb.addGap(4);
        fb.addInfo("Farb-Overrides pro Absender (Teilstring-Match, Groß/Klein egal):");

        senderColorModel = new SenderColorTableModel(settings.mailNotifySenderColors);
        JTable senderColorTable = new JTable(senderColorModel);
        senderColorTable.setRowHeight(22);
        senderColorTable.getColumnModel().getColumn(1).setCellRenderer(new ColorCellRenderer());
        senderColorTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        senderColorTable.getColumnModel().getColumn(1).setMaxWidth(120);
        JScrollPane senderScroll = new JScrollPane(senderColorTable);
        senderScroll.setPreferredSize(new Dimension(0, 100));
        fb.addWide(senderScroll);

        JButton addSenderBtn = new JButton("➕ Hinzufügen");
        addSenderBtn.addActionListener(e -> {
            String addr = JOptionPane.showInputDialog(null,
                    "Absender-Adresse oder Teilstring:", "Absender-Farbe", JOptionPane.PLAIN_MESSAGE);
            if (addr == null || addr.trim().isEmpty()) return;
            Color chosen = JColorChooser.showDialog(null, "Farbe für " + addr.trim(), Color.BLUE);
            if (chosen == null) return;
            senderColorModel.addEntry(addr.trim(), toHex(chosen));
        });
        JButton removeSenderBtn = new JButton("➖ Entfernen");
        removeSenderBtn.addActionListener(e -> {
            int row = senderColorTable.getSelectedRow();
            if (row >= 0) senderColorModel.removeEntry(row);
        });
        fb.addButtons(addSenderBtn, removeSenderBtn);

        // ── HTML-Whitelist ──
        fb.addSection("HTML-Whitelist");
        fb.addInfo("Absender, deren Mails immer in HTML geöffnet werden.");

        DefaultListModel<String> whitelistModel = new DefaultListModel<>();
        if (settings.mailHtmlWhitelistedSenders != null)
            for (String sender : settings.mailHtmlWhitelistedSenders) whitelistModel.addElement(sender);
        mailWhitelistJList = new JList<>(whitelistModel);
        mailWhitelistJList.setVisibleRowCount(5);
        fb.addWide(new JScrollPane(mailWhitelistJList));

        JButton wlRemoveButton = new JButton("➖ Entfernen");
        wlRemoveButton.addActionListener(e -> { int idx = mailWhitelistJList.getSelectedIndex(); if (idx >= 0) whitelistModel.removeElementAt(idx); });
        JButton wlClearButton = new JButton("🗑 Alle entfernen");
        wlClearButton.addActionListener(e -> whitelistModel.clear());
        fb.addButtons(wlRemoveButton, wlClearButton);

        installPanel(fb);
    }

    private void updateSyncCheckboxStates() {
        boolean enabled = cbSyncEnabled.isSelected();
        cbSyncMails.setEnabled(enabled);
        cbSyncCalendar.setEnabled(enabled);
        cbSyncContacts.setEnabled(enabled);
        cbSyncTasks.setEnabled(enabled);
        cbSyncNotes.setEnabled(enabled);
        cbSuppressStderr.setEnabled(enabled);
        cooldownSpinner.setEnabled(enabled);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.mailStorePath = mailPathField.getText().trim();
        s.mailContainerClasses = mailContainerClassesField.getText().trim();
        de.bund.zrb.mail.model.MailboxCategory.setMailContainerClasses(s.mailContainerClasses);

        s.mailSyncEnabled = cbSyncEnabled.isSelected();
        s.mailSyncMails = cbSyncMails.isSelected();
        s.mailSyncCalendar = cbSyncCalendar.isSelected();
        s.mailSyncContacts = cbSyncContacts.isSelected();
        s.mailSyncTasks = cbSyncTasks.isSelected();
        s.mailSyncNotes = cbSyncNotes.isSelected();
        s.mailSyncSuppressStderr = cbSuppressStderr.isSelected();
        s.mailSyncCooldownSeconds = ((Number) cooldownSpinner.getValue()).intValue();

        s.mailNotifyEnabled = cbNotifyEnabled.isSelected();
        s.mailNotifyDefaultColor = defaultColorField.getText().trim();
        s.mailNotifySenderColors = senderColorModel.toMap();

        s.mailHtmlWhitelistedSenders = new java.util.HashSet<>();
        DefaultListModel<String> wlModel = (DefaultListModel<String>) mailWhitelistJList.getModel();
        for (int i = 0; i < wlModel.size(); i++) s.mailHtmlWhitelistedSenders.add(wlModel.getElementAt(i));
    }

    // ═══════════════════════════════════════════════════════════════
    //  Sender colour table model
    // ═══════════════════════════════════════════════════════════════

    private static class SenderColorTableModel extends AbstractTableModel {
        private final java.util.List<String> addresses = new ArrayList<String>();
        private final java.util.List<String> colors = new ArrayList<String>();

        SenderColorTableModel(Map<String, String> initial) {
            if (initial != null) {
                for (Map.Entry<String, String> e : initial.entrySet()) {
                    addresses.add(e.getKey());
                    colors.add(e.getValue());
                }
            }
        }

        @Override public int getRowCount() { return addresses.size(); }
        @Override public int getColumnCount() { return 2; }
        @Override public String getColumnName(int col) { return col == 0 ? "Absender" : "Farbe"; }

        @Override
        public Object getValueAt(int row, int col) {
            return col == 0 ? addresses.get(row) : colors.get(row);
        }

        @Override
        public boolean isCellEditable(int row, int col) { return col == 0; }

        @Override
        public void setValueAt(Object val, int row, int col) {
            if (col == 0) addresses.set(row, String.valueOf(val));
            fireTableCellUpdated(row, col);
        }

        void addEntry(String addr, String hex) {
            addresses.add(addr);
            colors.add(hex);
            fireTableRowsInserted(addresses.size() - 1, addresses.size() - 1);
        }

        void removeEntry(int row) {
            addresses.remove(row);
            colors.remove(row);
            fireTableRowsDeleted(row, row);
        }

        Map<String, String> toMap() {
            Map<String, String> m = new LinkedHashMap<String, String>();
            for (int i = 0; i < addresses.size(); i++) {
                m.put(addresses.get(i), colors.get(i));
            }
            return m;
        }
    }

    /** Renderer that shows the hex colour as a coloured block + text. */
    private static class ColorCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            String hex = value != null ? value.toString() : "";
            Color c = parseHexColor(hex);
            if (c != null) {
                lbl.setForeground(c);
                lbl.setText("██ " + hex);
            }
            return lbl;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Colour helpers
    // ═══════════════════════════════════════════════════════════════

    private static Color parseHexColor(String hex) {
        if (hex == null || hex.isEmpty()) return null;
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            return new Color(Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}
