package de.bund.zrb.ui.commands;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.util.CredentialStore;
import de.bund.zrb.util.KeePassNotAvailableException;
import de.bund.zrb.util.PasswordMethod;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Menu command under <em>Hilfe → Passwörter (KeePass)</em>.
 * <p>
 * Full CRUD support for KeePass entries (via RPC or PowerShell).
 */
public class ShowPasswordsMenuCommand extends ShortcutMenuCommand {

    private static final Logger LOG = Logger.getLogger(ShowPasswordsMenuCommand.class.getName());

    private final JFrame parent;

    public ShowPasswordsMenuCommand(JFrame parent) {
        this.parent = parent;
    }

    @Override
    public String getId() {
        return "help.passwords";
    }

    @Override
    public String getLabel() {
        return "🔑 Passwörter (KeePass)…";
    }

    @Override
    public void perform() {
        Settings settings = SettingsHelper.load();
        PasswordMethod method;
        try {
            method = PasswordMethod.valueOf(settings.passwordMethod);
        } catch (Exception e) {
            method = PasswordMethod.WINDOWS_DPAPI;
        }

        if (method != PasswordMethod.KEEPASS) {
            JOptionPane.showMessageDialog(parent,
                    "<html><body style='width:380px'>"
                            + "<h3>KeePass ist nicht konfiguriert</h3>"
                            + "<p>Diese Funktion zeigt alle Passwörter aus einer KeePass-Datenbank an.</p>"
                            + "<p>Bitte konfigurieren Sie zuerst unter<br>"
                            + "<b>Einstellungen → Allgemein → Sicherheit</b><br>"
                            + "die Passwort-Methode <b>KeePass</b> und geben Sie das "
                            + "KeePass-Installationsverzeichnis sowie den Pfad zur <code>.kdbx</code>-Datenbank an.</p>"
                            + "</body></html>",
                    "KeePass nicht aktiv",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Load entries in background to avoid blocking the EDT
        parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return CredentialStore.listKeePassEntries();
            }

            @Override
            protected void done() {
                parent.setCursor(Cursor.getDefaultCursor());
                try {
                    String rawOutput = get();
                    showEntriesDialog(rawOutput);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    LOG.log(Level.WARNING, "KeePass ListEntries failed", cause);
                    String msg = cause instanceof KeePassNotAvailableException
                            ? cause.getMessage()
                            : "Fehler beim Lesen der KeePass-Datenbank:\n" + cause.getMessage();
                    JOptionPane.showMessageDialog(parent, msg,
                            "KeePass-Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ═══════════════════════════════════════════════════════════
    //  Dialog
    // ═══════════════════════════════════════════════════════════

    private void showEntriesDialog(String rawOutput) {
        final List<KeePassEntry> entries = parseEntries(rawOutput);
        final boolean[] visible = new boolean[1024]; // per-row password visibility

        final EntryTableModel model = new EntryTableModel(entries, visible);

        final JTable table = new JTable(model);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(180); // Titel
        table.getColumnModel().getColumn(1).setPreferredWidth(140); // Benutzername
        table.getColumnModel().getColumn(2).setPreferredWidth(120); // Passwort
        table.getColumnModel().getColumn(3).setPreferredWidth(250); // URL
        table.getColumnModel().getColumn(4).setPreferredWidth(36);  // 👁
        table.getColumnModel().getColumn(4).setMaxWidth(40);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        // Eye column: renderer + click handler
        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            {
                setHorizontalAlignment(SwingConstants.CENTER);
            }
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                int modelRow = t.convertRowIndexToModel(r);
                setText(visible[modelRow] ? "\uD83D\uDD13" : "\uD83D\uDC41");
                setToolTipText("Passwort anzeigen / verbergen");
                return this;
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                int row = table.rowAtPoint(e.getPoint());
                if (col == 4 && row >= 0) {
                    int modelRow = table.convertRowIndexToModel(row);
                    visible[modelRow] = !visible[modelRow];
                    model.fireTableRowsUpdated(modelRow, modelRow);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(800, 380));

        // ── Bottom button bar ──
        JCheckBox showAll = new JCheckBox("Alle Passwörter anzeigen");
        showAll.addActionListener(e -> {
            boolean show = showAll.isSelected();
            for (int i = 0; i < entries.size(); i++) visible[i] = show;
            model.fireTableDataChanged();
        });

        JButton copyBtn = new JButton("📋 Kopieren");
        copyBtn.setToolTipText("Passwort des ausgewählten Eintrags in die Zwischenablage kopieren");
        copyBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(parent, "Bitte zuerst einen Eintrag auswählen.",
                        "Kein Eintrag", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int modelRow = table.convertRowIndexToModel(row);
            String pw = entries.get(modelRow).password;
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(pw), null);
            JOptionPane.showMessageDialog(parent,
                    "Passwort für \"" + entries.get(modelRow).title + "\" in die Zwischenablage kopiert.",
                    "Kopiert", JOptionPane.INFORMATION_MESSAGE);
        });

        JButton addBtn = new JButton("➕ Neu");
        addBtn.setToolTipText("Neuen KeePass-Eintrag anlegen");
        addBtn.addActionListener(e -> {
            KeePassEntry created = showEntryEditor(null, "Neuen Eintrag anlegen");
            if (created != null) {
                try {
                    CredentialStore.addKeePassEntry(
                            created.title, created.userName, created.password, created.url);
                    entries.add(created);
                    model.fireTableDataChanged();
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "AddEntry failed", ex);
                    JOptionPane.showMessageDialog(parent,
                            "Eintrag konnte nicht angelegt werden:\n" + ex.getMessage(),
                            "Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JButton editBtn = new JButton("✏️ Bearbeiten");
        editBtn.setToolTipText("Ausgewählten KeePass-Eintrag bearbeiten");
        editBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(parent, "Bitte zuerst einen Eintrag auswählen.",
                        "Kein Eintrag", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int modelRow = table.convertRowIndexToModel(row);
            KeePassEntry existing = entries.get(modelRow);
            KeePassEntry updated = showEntryEditor(existing, "Eintrag bearbeiten");
            if (updated != null) {
                try {
                    CredentialStore.updateKeePassEntry(
                            updated.title, updated.userName, updated.password);
                    entries.set(modelRow, updated);
                    model.fireTableDataChanged();
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "UpdateEntry failed", ex);
                    JOptionPane.showMessageDialog(parent,
                            "Eintrag konnte nicht aktualisiert werden:\n" + ex.getMessage(),
                            "Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JButton deleteBtn = new JButton("🗑 Löschen");
        deleteBtn.setToolTipText("Ausgewählten KeePass-Eintrag löschen");
        deleteBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(parent, "Bitte zuerst einen Eintrag auswählen.",
                        "Kein Eintrag", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int modelRow = table.convertRowIndexToModel(row);
            KeePassEntry entry = entries.get(modelRow);
            int confirm = JOptionPane.showConfirmDialog(parent,
                    "Eintrag \"" + entry.title + "\" wirklich löschen?\n"
                            + "Diese Aktion kann nicht rückgängig gemacht werden.",
                    "Eintrag löschen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;

            try {
                CredentialStore.removeKeePassEntry(entry.title, entry.uniqueID);
                entries.remove(modelRow);
                model.fireTableDataChanged();
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "RemoveEntry failed", ex);
                JOptionPane.showMessageDialog(parent,
                        "Eintrag konnte nicht gelöscht werden:\n" + ex.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttonPanel.add(showAll);
        buttonPanel.add(Box.createHorizontalStrut(10));
        buttonPanel.add(copyBtn);
        buttonPanel.add(addBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(deleteBtn);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 8));
        mainPanel.add(new JLabel("KeePass  (" + entries.size() + " Einträge)"),
                BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        JDialog dialog = new JDialog(parent, "KeePass – Passwörter", true);
        dialog.setContentPane(mainPanel);
        dialog.getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    // ═══════════════════════════════════════════════════════════
    //  Entry editor dialog (for Add / Edit)
    // ═══════════════════════════════════════════════════════════

    /**
     * Show a modal dialog to create/edit a KeePass entry.
     *
     * @param existing the entry to edit, or {@code null} for a new entry
     * @param dialogTitle the title for the dialog
     * @return the new/updated entry, or {@code null} if the user cancelled
     */
    private KeePassEntry showEntryEditor(KeePassEntry existing, String dialogTitle) {
        JTextField titleField = new JTextField(existing != null ? existing.title : "", 25);
        JTextField userField  = new JTextField(existing != null ? existing.userName : "", 25);
        JPasswordField passField = new JPasswordField(existing != null ? existing.password : "", 25);
        JTextField urlField   = new JTextField(existing != null ? existing.url : "", 25);

        // Show/hide password toggle
        JCheckBox showPass = new JCheckBox("anzeigen");
        showPass.addActionListener(e -> {
            passField.setEchoChar(showPass.isSelected() ? (char) 0 : '•');
        });

        JPanel passPanel = new JPanel(new BorderLayout(4, 0));
        passPanel.add(passField, BorderLayout.CENTER);
        passPanel.add(showPass, BorderLayout.EAST);

        // If editing, title is read-only (it's the key)
        if (existing != null) {
            titleField.setEditable(false);
            titleField.setBackground(UIManager.getColor("TextField.inactiveBackground"));
        }

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Titel:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(titleField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Benutzername:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(userField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Passwort:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(passPanel, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("URL:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(urlField, gbc);

        int result = JOptionPane.showConfirmDialog(parent, panel, dialogTitle,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return null;

        String title = titleField.getText().trim();
        String user  = userField.getText().trim();
        String pass  = new String(passField.getPassword());
        String url   = urlField.getText().trim();

        if (title.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Der Titel darf nicht leer sein.",
                    "Ungültige Eingabe", JOptionPane.WARNING_MESSAGE);
            return null;
        }

        return new KeePassEntry(title, user, pass, url,
                existing != null ? existing.uniqueID : "");
    }

    // ═══════════════════════════════════════════════════════════
    //  Table model
    // ═══════════════════════════════════════════════════════════

    private static class EntryTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Titel", "Benutzername", "Passwort", "URL", ""};
        private final List<KeePassEntry> entries;
        private final boolean[] visible;

        EntryTableModel(List<KeePassEntry> entries, boolean[] visible) {
            this.entries = entries;
            this.visible = visible;
        }

        @Override
        public int getRowCount() { return entries.size(); }

        @Override
        public int getColumnCount() { return COLUMNS.length; }

        @Override
        public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public boolean isCellEditable(int row, int col) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            KeePassEntry e = entries.get(row);
            switch (col) {
                case 0: return e.title;
                case 1: return e.userName;
                case 2: return visible[row] ? e.password : "••••••••";
                case 3: return e.url;
                case 4: return visible[row] ? "\uD83D\uDD13" : "\uD83D\uDC41";
                default: return "";
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Parsing
    // ═══════════════════════════════════════════════════════════

    /**
     * Parses the PowerShell/KeePassLib entry listing output.
     * <p>
     * Expected format per entry block (separated by blank lines):
     * <pre>
     * Title: MyEntry
     * UserName: myuser
     * Password: secret
     * URL: https://example.com
     * UniqueID: abcdef123456
     * ...
     * </pre>
     * The last line "OK: Operation completed successfully." is stripped.
     */
    private static List<KeePassEntry> parseEntries(String raw) {
        List<KeePassEntry> entries = new ArrayList<KeePassEntry>();
        if (raw == null || raw.trim().isEmpty()) return entries;

        // Remove trailing status line
        String cleaned = raw.trim();
        int lastNewline = cleaned.lastIndexOf('\n');
        if (lastNewline > 0) {
            String lastLine = cleaned.substring(lastNewline + 1).trim();
            if (lastLine.startsWith("OK:")) {
                cleaned = cleaned.substring(0, lastNewline).trim();
            }
        } else if (cleaned.startsWith("OK:")) {
            return entries; // empty database
        }

        // Split on blank lines (entry separator)
        String[] blocks = cleaned.split("\\n\\s*\\n");

        for (String block : blocks) {
            String title = "";
            String userName = "";
            String password = "";
            String url = "";
            String uniqueID = "";

            for (String line : block.split("\\n")) {
                line = line.trim();
                if (line.startsWith("Title: "))       title    = line.substring("Title: ".length()).trim();
                else if (line.startsWith("UserName: "))  userName = line.substring("UserName: ".length()).trim();
                else if (line.startsWith("Password: "))  password = line.substring("Password: ".length()).trim();
                else if (line.startsWith("URL: "))       url      = line.substring("URL: ".length()).trim();
                else if (line.startsWith("UniqueID: "))  uniqueID = line.substring("UniqueID: ".length()).trim();
            }

            // Only add entries with a title (skip header/empty blocks)
            if (!title.isEmpty()) {
                entries.add(new KeePassEntry(title, userName, password, url, uniqueID));
            }
        }

        return entries;
    }

    private static class KeePassEntry {
        final String title;
        final String userName;
        final String password;
        final String url;
        final String uniqueID;

        KeePassEntry(String title, String userName, String password, String url, String uniqueID) {
            this.title = title;
            this.userName = userName;
            this.password = password;
            this.url = url;
            this.uniqueID = uniqueID;
        }
    }
}

