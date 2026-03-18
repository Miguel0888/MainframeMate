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
import java.util.Arrays;
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
    private static final List<String> CATEGORIES = Arrays.asList("General", "Wiki");

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
        table.getColumnModel().getColumn(0).setPreferredWidth(70);  // Kategorie
        table.getColumnModel().getColumn(1).setPreferredWidth(120); // ID
        table.getColumnModel().getColumn(2).setPreferredWidth(130); // Anzeigename
        table.getColumnModel().getColumn(3).setPreferredWidth(120); // Benutzername
        table.getColumnModel().getColumn(4).setPreferredWidth(100); // Passwort
        table.getColumnModel().getColumn(5).setPreferredWidth(200); // URL
        table.getColumnModel().getColumn(6).setPreferredWidth(36);  // 👁
        table.getColumnModel().getColumn(6).setMaxWidth(40);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        // Eye column: renderer + click handler
        table.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
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
                if (col == 6 && row >= 0) {
                    int modelRow = table.convertRowIndexToModel(row);
                    visible[modelRow] = !visible[modelRow];
                    model.fireTableRowsUpdated(modelRow, modelRow);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(920, 380));

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
                            created.title, created.userName, created.password, created.url,
                            created.displayName, created.category,
                            created.requiresLogin, created.useProxy, created.autoIndex);
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
                            updated.title, updated.userName, updated.password,
                            updated.displayName, updated.category,
                            updated.requiresLogin, updated.useProxy, updated.autoIndex);
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
        JComboBox<String> categoryBox = new JComboBox<String>(CATEGORIES.toArray(new String[0]));
        if (existing != null) {
            categoryBox.setSelectedItem(existing.category);
        }

        JTextField idField          = new JTextField(existing != null ? existing.title : "", 25);
        JTextField displayNameField = new JTextField(existing != null ? existing.displayName : "", 25);
        JTextField userField        = new JTextField(existing != null ? existing.userName : "", 25);
        JPasswordField passField    = new JPasswordField(existing != null ? existing.password : "", 25);
        JTextField urlField         = new JTextField(existing != null ? existing.url : "", 25);

        JCheckBox loginCb    = new JCheckBox("Login erforderlich");
        JCheckBox proxyCb    = new JCheckBox("Proxy verwenden");
        JCheckBox autoIdxCb  = new JCheckBox("Auto-Index");

        if (existing != null) {
            loginCb.setSelected(existing.requiresLogin);
            proxyCb.setSelected(existing.useProxy);
            autoIdxCb.setSelected(existing.autoIndex);
        }

        // Show/hide password toggle
        JCheckBox showPass = new JCheckBox("anzeigen");
        showPass.addActionListener(e ->
                passField.setEchoChar(showPass.isSelected() ? (char) 0 : '•'));

        JPanel passPanel = new JPanel(new BorderLayout(4, 0));
        passPanel.add(passField, BorderLayout.CENTER);
        passPanel.add(showPass, BorderLayout.EAST);

        // If editing, ID is read-only (it's the KeePass title / key)
        if (existing != null) {
            idField.setEditable(false);
            idField.setBackground(UIManager.getColor("TextField.inactiveBackground"));
        }

        // URL label — will be updated based on category
        JLabel urlLabel = new JLabel("URL:");

        // Update URL requirement when category changes
        categoryBox.addActionListener(e -> {
            boolean isWiki = "Wiki".equals(categoryBox.getSelectedItem());
            urlLabel.setText(isWiki ? "URL: *" : "URL:");
        });
        // Set initial state
        if (existing != null && "Wiki".equals(existing.category)) {
            urlLabel.setText("URL: *");
        }

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        int row = 0;

        gbc.gridx = 0; gbc.gridy = row;
        panel.add(new JLabel("Kategorie:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(categoryBox, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("ID:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(idField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Anzeigename:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(displayNameField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Benutzername:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(userField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Passwort:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(passPanel, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(urlLabel, gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(urlField, gbc);

        // ── Separator + Checkboxes ──
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(new JSeparator(), gbc);
        gbc.gridwidth = 1;

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Optionen:"), gbc);
        gbc.gridx = 1;
        JPanel cbPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        cbPanel.add(loginCb);
        cbPanel.add(proxyCb);
        cbPanel.add(autoIdxCb);
        panel.add(cbPanel, gbc);

        int result = JOptionPane.showConfirmDialog(parent, panel, dialogTitle,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return null;

        String category    = (String) categoryBox.getSelectedItem();
        String id          = idField.getText().trim();
        String displayName = displayNameField.getText().trim();
        String user        = userField.getText().trim();
        String pass        = new String(passField.getPassword());
        String url         = urlField.getText().trim();

        if (id.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Die ID darf nicht leer sein.",
                    "Ungültige Eingabe", JOptionPane.WARNING_MESSAGE);
            return null;
        }

        if ("Wiki".equals(category) && url.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "Für Wiki-Einträge ist die URL ein Pflichtfeld.",
                    "Ungültige Eingabe", JOptionPane.WARNING_MESSAGE);
            return null;
        }

        return new KeePassEntry(category, id, displayName, user, pass, url,
                existing != null ? existing.uniqueID : "",
                loginCb.isSelected(), proxyCb.isSelected(), autoIdxCb.isSelected());
    }

    // ═══════════════════════════════════════════════════════════
    //  Table model
    // ═══════════════════════════════════════════════════════════

    private static class EntryTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
                "Kategorie", "ID", "Anzeigename", "Benutzername", "Passwort", "URL", ""
        };
        private final List<KeePassEntry> entries;
        private final boolean[] visible;

        EntryTableModel(List<KeePassEntry> entries, boolean[] visible) {
            this.entries = entries;
            this.visible = visible;
        }

        @Override public int getRowCount() { return entries.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            KeePassEntry e = entries.get(row);
            switch (col) {
                case 0: return e.category;
                case 1: return e.title;
                case 2: return e.displayName;
                case 3: return e.userName;
                case 4: return visible[row] ? e.password : "••••••••";
                case 5: return e.url;
                case 6: return visible[row] ? "\uD83D\uDD13" : "\uD83D\uDC41";
                default: return "";
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Parsing
    // ═══════════════════════════════════════════════════════════

    private static List<KeePassEntry> parseEntries(String raw) {
        List<KeePassEntry> entries = new ArrayList<KeePassEntry>();
        if (raw == null || raw.trim().isEmpty()) return entries;

        String cleaned = raw.trim();
        int lastNewline = cleaned.lastIndexOf('\n');
        if (lastNewline > 0) {
            String lastLine = cleaned.substring(lastNewline + 1).trim();
            if (lastLine.startsWith("OK:")) {
                cleaned = cleaned.substring(0, lastNewline).trim();
            }
        } else if (cleaned.startsWith("OK:")) {
            return entries;
        }

        String[] blocks = cleaned.split("\\n\\s*\\n");

        for (String block : blocks) {
            String title = "";
            String userName = "";
            String password = "";
            String url = "";
            String uniqueID = "";
            String mmCategory = "";
            String mmDisplayName = "";
            String mmRequiresLogin = "false";
            String mmUseProxy = "false";
            String mmAutoIndex = "false";

            for (String line : block.split("\\n")) {
                line = line.trim();
                if (line.startsWith("Title: "))              title            = line.substring("Title: ".length()).trim();
                else if (line.startsWith("UserName: "))      userName         = line.substring("UserName: ".length()).trim();
                else if (line.startsWith("Password: "))      password         = line.substring("Password: ".length()).trim();
                else if (line.startsWith("URL: "))           url              = line.substring("URL: ".length()).trim();
                else if (line.startsWith("UniqueID: "))      uniqueID         = line.substring("UniqueID: ".length()).trim();
                else if (line.startsWith("MM_Category: "))   mmCategory       = line.substring("MM_Category: ".length()).trim();
                else if (line.startsWith("MM_DisplayName: "))mmDisplayName    = line.substring("MM_DisplayName: ".length()).trim();
                else if (line.startsWith("MM_RequiresLogin: "))mmRequiresLogin= line.substring("MM_RequiresLogin: ".length()).trim();
                else if (line.startsWith("MM_UseProxy: "))   mmUseProxy       = line.substring("MM_UseProxy: ".length()).trim();
                else if (line.startsWith("MM_AutoIndex: "))  mmAutoIndex      = line.substring("MM_AutoIndex: ".length()).trim();
            }

            if (!title.isEmpty()) {
                String displayName = !mmDisplayName.isEmpty() ? mmDisplayName : title;
                String category = !mmCategory.isEmpty() ? mmCategory : "General";
                boolean reqLogin = "true".equalsIgnoreCase(mmRequiresLogin);
                boolean proxy    = "true".equalsIgnoreCase(mmUseProxy);
                boolean autoIdx  = "true".equalsIgnoreCase(mmAutoIndex);
                entries.add(new KeePassEntry(category, title, displayName, userName, password, url, uniqueID,
                        reqLogin, proxy, autoIdx));
            }
        }

        return entries;
    }

    // ═══════════════════════════════════════════════════════════
    //  Entry model
    // ═══════════════════════════════════════════════════════════

    private static class KeePassEntry {
        final String category;
        final String title;         // = ID (KeePass Title field, immutable)
        final String displayName;   // user-facing display name
        final String userName;
        final String password;
        final String url;
        final String uniqueID;
        final boolean requiresLogin;
        final boolean useProxy;
        final boolean autoIndex;

        KeePassEntry(String category, String title, String displayName,
                     String userName, String password, String url, String uniqueID,
                     boolean requiresLogin, boolean useProxy, boolean autoIndex) {
            this.category = category;
            this.title = title;
            this.displayName = displayName;
            this.userName = userName;
            this.password = password;
            this.url = url;
            this.uniqueID = uniqueID;
            this.requiresLogin = requiresLogin;
            this.useProxy = useProxy;
            this.autoIndex = autoIndex;
        }
    }
}

