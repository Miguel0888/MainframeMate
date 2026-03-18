package de.bund.zrb.ui.commands;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.util.CredentialStore;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
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
    private static final List<String> CATEGORIES = Arrays.asList("Mainframe", "Wiki");

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
        return "🔑 Passwörter…";
    }

    @Override
    public void perform() {
        // Load entries in background (credential decryption may be slow)
        parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<List<KeePassEntry>, Void>() {
            @Override
            protected List<KeePassEntry> doInBackground() {
                return loadEntries();
            }

            @Override
            protected void done() {
                parent.setCursor(Cursor.getDefaultCursor());
                try {
                    List<KeePassEntry> entries = get();
                    showEntriesDialog(entries);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    LOG.log(Level.WARNING, "LoadEntries failed", cause);
                    JOptionPane.showMessageDialog(parent,
                            "Fehler beim Laden der Passwort-Einträge:\n" + cause.getMessage(),
                            "Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ═══════════════════════════════════════════════════════════
    //  Dialog
    // ═══════════════════════════════════════════════════════════

    private void showEntriesDialog(final List<KeePassEntry> entries) {
        final boolean[] visible = new boolean[1024]; // per-row password visibility

        final EntryTableModel model = new EntryTableModel(entries, visible);

        final JTable table = new JTable(model);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(70);  // Kategorie
        table.getColumnModel().getColumn(1).setPreferredWidth(120); // ID
        table.getColumnModel().getColumn(2).setPreferredWidth(130); // Anzeigename
        table.getColumnModel().getColumn(3).setPreferredWidth(120); // Benutzername
        table.getColumnModel().getColumn(4).setPreferredWidth(100); // Passwort
        table.getColumnModel().getColumn(5).setPreferredWidth(180); // URL
        table.getColumnModel().getColumn(6).setPreferredWidth(46);  // Login
        table.getColumnModel().getColumn(6).setMaxWidth(54);
        table.getColumnModel().getColumn(7).setPreferredWidth(46);  // Proxy
        table.getColumnModel().getColumn(7).setMaxWidth(54);
        table.getColumnModel().getColumn(8).setPreferredWidth(56);  // Auto-Idx
        table.getColumnModel().getColumn(8).setMaxWidth(64);
        table.getColumnModel().getColumn(9).setPreferredWidth(72);  // PW-Speicher
        table.getColumnModel().getColumn(9).setMaxWidth(80);
        table.getColumnModel().getColumn(10).setPreferredWidth(54); // Session
        table.getColumnModel().getColumn(10).setMaxWidth(62);
        table.getColumnModel().getColumn(11).setPreferredWidth(36); // 👁
        table.getColumnModel().getColumn(11).setMaxWidth(40);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        // Eye column (index 11): renderer + click handler
        table.getColumnModel().getColumn(11).setCellRenderer(new DefaultTableCellRenderer() {
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
                if (col == 11 && row >= 0) {
                    int modelRow = table.convertRowIndexToModel(row);
                    visible[modelRow] = !visible[modelRow];
                    model.fireTableRowsUpdated(modelRow, modelRow);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(1200, 380));

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
                    saveEntry(created);
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
                    saveEntry(updated);
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
                deleteEntry(entry.title);
                entries.remove(modelRow);
                model.fireTableDataChanged();
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "RemoveEntry failed", ex);
                JOptionPane.showMessageDialog(parent,
                        "Eintrag konnte nicht gelöscht werden:\n" + ex.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton defaultsBtn = new JButton("📦 Standard-Wikis");
        defaultsBtn.setToolTipText("Wikipedia (DE + EN) als Standard-Wiki-Einträge in KeePass anlegen");
        defaultsBtn.addActionListener(e -> {
            int created = loadWikiDefaults(entries);
            if (created > 0) {
                model.fireTableDataChanged();
                JOptionPane.showMessageDialog(parent,
                        created + " Standard-Wiki-Eintrag/-Einträge in KeePass angelegt.",
                        "Standard-Wikis", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(parent,
                        "Alle Standard-Wikis existieren bereits.",
                        "Standard-Wikis", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttonPanel.add(showAll);
        buttonPanel.add(Box.createHorizontalStrut(10));
        buttonPanel.add(copyBtn);
        buttonPanel.add(addBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(Box.createHorizontalStrut(16));
        buttonPanel.add(defaultsBtn);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 8));
        mainPanel.add(new JLabel("Passwörter  (" + entries.size() + " Einträge)"),
                BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        JDialog dialog = new JDialog(parent, "Passwörter verwalten", true);
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
        JCheckBox savePwCb   = new JCheckBox("PW speichern");
        JCheckBox sessionCb  = new JCheckBox("Session-Cache");

        if (existing != null) {
            loginCb.setSelected(existing.requiresLogin);
            proxyCb.setSelected(existing.useProxy);
            autoIdxCb.setSelected(existing.autoIndex);
            savePwCb.setSelected(existing.savePassword);
            sessionCb.setSelected(existing.sessionCache);
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
        cbPanel.add(savePwCb);
        cbPanel.add(sessionCb);
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
                loginCb.isSelected(), proxyCb.isSelected(), autoIdxCb.isSelected(),
                savePwCb.isSelected(), sessionCb.isSelected());
    }

    // ═══════════════════════════════════════════════════════════
    //  Default Wiki entries
    // ═══════════════════════════════════════════════════════════

    /** Standard wiki definitions: {id, displayName, apiUrl} */
    private static final String[][] WIKI_DEFAULTS = {
            {"wikipedia_de", "Wikipedia (DE)", "https://de.wikipedia.org/w/"},
            {"wikipedia_en", "Wikipedia (EN)", "https://en.wikipedia.org/w/"},
    };

    /**
     * Create default wiki entries in KeePass (skipping those that already exist).
     *
     * @param entries the live list of entries displayed in the table (will be appended)
     * @return number of entries actually created
     */
    private int loadWikiDefaults(List<KeePassEntry> entries) {
        int created = 0;
        for (String[] def : WIKI_DEFAULTS) {
            String id = def[0];
            String displayName = def[1];
            String url = def[2];

            // Skip if already present
            boolean exists = false;
            for (KeePassEntry e : entries) {
                if (id.equals(e.title)) {
                    exists = true;
                    break;
                }
            }
            if (exists) continue;

            try {
                // Original defaults: login=false, proxy=false, autoIndex=false
                KeePassEntry entry = new KeePassEntry("Wiki", id, displayName, "", "", url, "",
                        false, false, false, false, false);
                saveEntry(entry);
                entries.add(entry);
                created++;
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Failed to create default wiki entry: " + id, ex);
                JOptionPane.showMessageDialog(parent,
                        "Standard-Wiki \"" + displayName + "\" konnte nicht angelegt werden:\n"
                                + ex.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        }
        return created;
    }

    // ═══════════════════════════════════════════════════════════
    //  Table model
    // ═══════════════════════════════════════════════════════════

    private static class EntryTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
                "Kategorie", "ID", "Anzeigename", "Benutzername", "Passwort", "URL",
                "Login", "Proxy", "Auto-Idx", "PW-Speicher", "Session", ""
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
        public Class<?> getColumnClass(int col) {
            // Columns 6–10 are boolean checkboxes
            return (col >= 6 && col <= 10) ? Boolean.class : Object.class;
        }

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
                case 6: return e.requiresLogin;
                case 7: return e.useProxy;
                case 8: return e.autoIndex;
                case 9: return e.savePassword;
                case 10: return e.sessionCache;
                case 11: return visible[row] ? "\uD83D\uDD13" : "\uD83D\uDC41";
                default: return "";
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Settings-based persistence
    // ═══════════════════════════════════════════════════════════

    /** Credential-store key prefix for password entries. */
    private static final String PWD_PREFIX = "pwd:";

    /**
     * Load all password entries from {@code settings.passwordEntries} and
     * decrypt their credentials from {@code componentCredentials}.
     */
    private static List<KeePassEntry> loadEntries() {
        Settings settings = SettingsHelper.load();
        List<KeePassEntry> entries = new ArrayList<KeePassEntry>();
        for (Settings.PasswordEntryMeta meta : settings.passwordEntries) {
            String[] cred = CredentialStore.resolveIncludingEmpty(PWD_PREFIX + meta.id);
            entries.add(new KeePassEntry(
                    meta.category != null ? meta.category : "Mainframe",
                    meta.id,
                    meta.displayName != null && !meta.displayName.isEmpty() ? meta.displayName : meta.id,
                    cred[0], cred[1],
                    meta.url != null ? meta.url : "",
                    "",
                    meta.requiresLogin, meta.useProxy, meta.autoIndex,
                    meta.savePassword, meta.sessionCache));
        }
        return entries;
    }

    /**
     * Save (create or update) a password entry in Settings + CredentialStore.
     */
    private static void saveEntry(KeePassEntry entry) {
        Settings settings = SettingsHelper.load();

        // Remove existing metadata with the same id
        Iterator<Settings.PasswordEntryMeta> it = settings.passwordEntries.iterator();
        while (it.hasNext()) {
            if (entry.title.equals(it.next().id)) {
                it.remove();
                break;
            }
        }

        // Add new metadata
        Settings.PasswordEntryMeta meta = new Settings.PasswordEntryMeta();
        meta.id = entry.title;
        meta.category = entry.category;
        meta.displayName = entry.displayName;
        meta.url = entry.url;
        meta.requiresLogin = entry.requiresLogin;
        meta.useProxy = entry.useProxy;
        meta.autoIndex = entry.autoIndex;
        meta.savePassword = entry.savePassword;
        meta.sessionCache = entry.sessionCache;
        settings.passwordEntries.add(meta);
        SettingsHelper.save(settings);

        // Store encrypted credentials (user|password) via the configured method
        CredentialStore.store(PWD_PREFIX + entry.title, entry.userName, entry.password);
    }

    /**
     * Delete a password entry from Settings + CredentialStore.
     */
    private static void deleteEntry(String id) {
        Settings settings = SettingsHelper.load();
        Iterator<Settings.PasswordEntryMeta> it = settings.passwordEntries.iterator();
        while (it.hasNext()) {
            if (id.equals(it.next().id)) {
                it.remove();
                break;
            }
        }
        SettingsHelper.save(settings);
        CredentialStore.remove(PWD_PREFIX + id);
    }

    // ═══════════════════════════════════════════════════════════
    //  Entry model
    // ═══════════════════════════════════════════════════════════

    private static class KeePassEntry {
        final String category;
        final String title;         // = ID (unique key)
        final String displayName;   // user-facing display name
        final String userName;
        final String password;
        final String url;
        final String uniqueID;
        final boolean requiresLogin;
        final boolean useProxy;
        final boolean autoIndex;
        final boolean savePassword;
        final boolean sessionCache;

        KeePassEntry(String category, String title, String displayName,
                     String userName, String password, String url, String uniqueID,
                     boolean requiresLogin, boolean useProxy, boolean autoIndex,
                     boolean savePassword, boolean sessionCache) {
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
            this.savePassword = savePassword;
            this.sessionCache = sessionCache;
        }
    }
}

