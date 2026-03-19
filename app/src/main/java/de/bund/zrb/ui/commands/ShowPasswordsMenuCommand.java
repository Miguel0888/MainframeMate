package de.bund.zrb.ui.commands;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.sharepoint.SharePointLinkFetcher;
import de.bund.zrb.sharepoint.SharePointPathUtil;
import de.bund.zrb.sharepoint.SharePointSite;
import de.bund.zrb.util.CredentialStore;
import de.bund.zrb.util.PasswordMethod;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.api.ShortcutMenuCommand;
import de.zrb.bund.newApi.browser.BrowserService;

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
 * Menu command under <em>Einstellungen → Passwörter</em>.
 * <p>
 * Central CRUD for password entries. Metadata is stored in
 * {@code settings.passwordEntries}, credentials encrypted in
 * {@code settings.componentCredentials} using the configured method.
 * <p>
 * On first open, existing KeePass entries are migrated automatically.
 */
public class ShowPasswordsMenuCommand extends ShortcutMenuCommand {

    private static final Logger LOG = Logger.getLogger(ShowPasswordsMenuCommand.class.getName());
    private static final List<String> CATEGORIES = Arrays.asList("Mainframe", "Wiki", "SP");

    private final JFrame parent;

    public ShowPasswordsMenuCommand(JFrame parent) {
        this.parent = parent;
    }

    @Override
    public String getId() {
        return "settings.passwords";
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
                deleteEntry(entry);
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

        JButton spScanBtn = new JButton("\uD83C\uDF10 SP-Links scannen");
        spScanBtn.setToolTipText("SharePoint-Links per Browser von einer Webseite abrufen und als SP-Eintr\u00e4ge speichern");
        spScanBtn.addActionListener(e -> scanSharePointLinks(entries, model));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttonPanel.add(showAll);
        buttonPanel.add(Box.createHorizontalStrut(10));
        buttonPanel.add(copyBtn);
        buttonPanel.add(addBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(Box.createHorizontalStrut(16));
        buttonPanel.add(defaultsBtn);
        buttonPanel.add(spScanBtn);

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
            String cat = (String) categoryBox.getSelectedItem();
            boolean urlRequired = "Wiki".equals(cat) || "SP".equals(cat);
            urlLabel.setText(urlRequired ? "URL: *" : "URL:");
        });
        // Set initial state
        if (existing != null && ("Wiki".equals(existing.category) || "SP".equals(existing.category))) {
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

        // ── Custom dialog with SSO test button in the bottom button bar ──
        final JDialog dialog = new JDialog(parent, dialogTitle, true);
        final boolean[] confirmed = {false};

        // SSO test button (only visible for SP entries)
        JButton ssoTestBtn = new JButton("\uD83D\uDD11 SSO testen\u2026");
        ssoTestBtn.setToolTipText("Windows Integrated Auth (Kerberos/NTLM) f\u00fcr diese SharePoint-Site testen");
        ssoTestBtn.setVisible("SP".equals(categoryBox.getSelectedItem()));
        categoryBox.addActionListener(e ->
                ssoTestBtn.setVisible("SP".equals(categoryBox.getSelectedItem())));
        ssoTestBtn.addActionListener(e -> {
            String testUrl = urlField.getText().trim();
            if (testUrl.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Bitte zuerst eine URL eingeben.",
                        "SSO-Test", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String uncPath = de.bund.zrb.sharepoint.SharePointPathUtil.toUncPath(testUrl);
            dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() {
                    if (new java.io.File(uncPath).exists()) return true;
                    return de.bund.zrb.sharepoint.SharePointAuthenticator.netUseSso(uncPath);
                }
                @Override
                protected void done() {
                    dialog.setCursor(Cursor.getDefaultCursor());
                    try {
                        if (get()) {
                            JOptionPane.showMessageDialog(dialog,
                                    "\u2713 Windows SSO (Kerberos/NTLM) erfolgreich!\n\n"
                                            + "UNC-Pfad: " + uncPath,
                                    "SSO-Test", JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(dialog,
                                    "\u2717 Windows SSO nicht verf\u00fcgbar.\n\n"
                                            + "M\u00f6gliche Ursachen:\n"
                                            + "\u2022 SharePoint nicht in Intranet-Zone\n"
                                            + "\u2022 Kerberos-Ticket abgelaufen\n"
                                            + "\u2022 WebClient-Dienst nicht gestartet",
                                    "SSO-Test", JOptionPane.WARNING_MESSAGE);
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(dialog,
                                "Fehler: " + ex.getMessage(),
                                "SSO-Test", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(e -> { confirmed[0] = true; dialog.dispose(); });
        JButton cancelBtn = new JButton("Abbrechen");
        cancelBtn.addActionListener(e -> dialog.dispose());

        JPanel buttonBar = new JPanel(new BorderLayout());
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leftButtons.add(ssoTestBtn);
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightButtons.add(okBtn);
        rightButtons.add(cancelBtn);
        buttonBar.add(leftButtons, BorderLayout.WEST);
        buttonBar.add(rightButtons, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(panel, BorderLayout.CENTER);
        content.add(buttonBar, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(okBtn);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);

        if (!confirmed[0]) return null;

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

        if (("Wiki".equals(category) || "SP".equals(category)) && url.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "F\u00fcr " + category + "-Eintr\u00e4ge ist die URL ein Pflichtfeld.",
                    "Ungültige Eingabe", JOptionPane.WARNING_MESSAGE);
            return null;
        }

        return new KeePassEntry(category, id, displayName, user, pass, url,
                existing != null ? existing.uniqueID : "",
                loginCb.isSelected(), proxyCb.isSelected(), autoIdxCb.isSelected(),
                savePwCb.isSelected(), sessionCb.isSelected());
    }

    // ═══════════════════════════════════════════════════════════
    //  SharePoint link scanning via browser
    // ═══════════════════════════════════════════════════════════

    /**
     * Scan a web page for SharePoint links using the real browser.
     * The browser inherits the Windows SSO session, so authentication
     * against corporate portals works without explicit credentials.
     */
    private void scanSharePointLinks(final List<KeePassEntry> entries, final EntryTableModel model) {
        // ── 1. Ask for the URL ──
        String url = JOptionPane.showInputDialog(parent,
                "Geben Sie die URL der Seite ein, die SharePoint-Links enth\u00e4lt\n"
                        + "(z.\u00a0B. Intranet-\u00dcbersichtsseite mit Links zu SP-Sites):",
                "SharePoint-Links scannen", JOptionPane.PLAIN_MESSAGE);
        if (url == null || url.trim().isEmpty()) return;

        // Auto-prepend https:// if missing
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        // ── 2. Get BrowserService ──
        BrowserService browserService = null;
        if (parent instanceof MainframeContext) {
            browserService = ((MainframeContext) parent).getBrowserService();
        }
        if (browserService == null) {
            JOptionPane.showMessageDialog(parent,
                    "Browser-Service ist nicht verf\u00fcgbar.\n\n"
                            + "Bitte konfigurieren Sie den Browser unter\n"
                            + "Einstellungen \u2192 Browser.",
                    "Browser nicht verf\u00fcgbar", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final BrowserService bs = browserService;
        final String targetUrl = url.trim();

        // ── 3. Fetch in background ──
        parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new SwingWorker<List<SharePointSite>, Void>() {
            @Override
            protected List<SharePointSite> doInBackground() throws Exception {
                return SharePointLinkFetcher.fetchLinksViaBrowser(bs, targetUrl);
            }

            @Override
            protected void done() {
                parent.setCursor(Cursor.getDefaultCursor());
                try {
                    List<SharePointSite> allLinks = get();
                    List<SharePointSite> spLinks = SharePointLinkFetcher.filterSharePointLinks(allLinks);

                    if (spLinks.isEmpty() && allLinks.isEmpty()) {
                        JOptionPane.showMessageDialog(parent,
                                "Keine Links auf der Seite gefunden.\n\n"
                                        + "M\u00f6glicherweise ben\u00f6tigt die Seite l\u00e4nger zum Laden,\n"
                                        + "oder sie enth\u00e4lt keine Hyperlinks.",
                                "Keine Links", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    if (spLinks.isEmpty()) {
                        JOptionPane.showMessageDialog(parent,
                                allLinks.size() + " Links gefunden, aber keine davon\n"
                                        + "sehen nach SharePoint-Sites aus.\n\n"
                                        + "SharePoint-Links enthalten typischerweise\n"
                                        + "'/sites/', '/teams/' oder 'sharepoint.com' in der URL.\n\n"
                                        + "Tipp: Pr\u00fcfen Sie, ob die richtige Seite geladen wurde.",
                                "Keine SP-Links", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    // ── 4. Show selection dialog ──
                    int saved = showSpLinkSelectionDialog(spLinks, entries, model);
                    if (saved > 0) {
                        JOptionPane.showMessageDialog(parent,
                                saved + " SharePoint-Eintrag/-Eintr\u00e4ge unter Passw\u00f6rter gespeichert.",
                                "SP-Links gespeichert", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    JOptionPane.showMessageDialog(parent,
                            "Fehler beim Scannen der Seite:\n" + msg,
                            "Browser-Fehler", JOptionPane.ERROR_MESSAGE);
                    LOG.log(Level.WARNING, "[SP-Scan] Failed", ex);
                }
            }
        }.execute();
    }

    /**
     * Show a dialog where the user can select which discovered SP links
     * should be saved as password entries.
     *
     * @return number of entries actually created
     */
    private int showSpLinkSelectionDialog(List<SharePointSite> spLinks,
                                          List<KeePassEntry> entries, EntryTableModel model) {
        // Mark already-existing entries
        java.util.Set<String> existingUrls = new java.util.HashSet<String>();
        for (KeePassEntry e : entries) {
            if ("SP".equals(e.category) && e.url != null) {
                existingUrls.add(e.url);
            }
        }

        // Pre-select those that are new
        final boolean[] selected = new boolean[spLinks.size()];
        for (int i = 0; i < spLinks.size(); i++) {
            selected[i] = !existingUrls.contains(spLinks.get(i).getUrl());
        }

        // Build table
        final List<SharePointSite> linkList = spLinks;
        javax.swing.table.AbstractTableModel tModel = new javax.swing.table.AbstractTableModel() {
            final String[] cols = {"\u2713", "Name", "URL", "Status"};
            @Override public int getRowCount() { return linkList.size(); }
            @Override public int getColumnCount() { return cols.length; }
            @Override public String getColumnName(int c) { return cols[c]; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
            @Override public boolean isCellEditable(int r, int c) { return c == 0; }

            @Override
            public Object getValueAt(int r, int c) {
                SharePointSite s = linkList.get(r);
                switch (c) {
                    case 0: return selected[r];
                    case 1: return s.getName();
                    case 2: return s.getUrl();
                    case 3: return existingUrls.contains(s.getUrl()) ? "vorhanden" : "neu";
                    default: return "";
                }
            }

            @Override
            public void setValueAt(Object v, int r, int c) {
                if (c == 0 && v instanceof Boolean) {
                    selected[r] = (Boolean) v;
                    fireTableCellUpdated(r, c);
                }
            }
        };

        JTable table = new JTable(tModel);
        table.getColumnModel().getColumn(0).setMaxWidth(30);
        table.getColumnModel().getColumn(0).setMinWidth(30);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(350);
        table.getColumnModel().getColumn(3).setPreferredWidth(70);
        table.setRowHeight(22);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(700, Math.min(400, 60 + spLinks.size() * 22)));

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("<html><b>" + spLinks.size() + " SharePoint-Links erkannt.</b><br>"
                + "W\u00e4hlen Sie die Sites aus, die als SP-Passworteintrag gespeichert werden sollen:</html>"),
                BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(parent, panel,
                "SharePoint-Links speichern", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return 0;

        // ── Save selected links as SP password entries ──
        int created = 0;
        for (int i = 0; i < linkList.size(); i++) {
            if (!selected[i]) continue;
            SharePointSite site = linkList.get(i);

            // Skip already existing
            if (existingUrls.contains(site.getUrl())) continue;

            String id = de.bund.zrb.ui.settings.categories.SharePointSettingsPanel.toSiteId(site);
            String displayName = site.getName();
            if (displayName == null || displayName.isEmpty()) {
                displayName = SharePointPathUtil.extractDisplayName(site.getUrl());
            }

            // Check if entry with this ID already exists
            boolean idExists = false;
            for (KeePassEntry e : entries) {
                if (id.equals(e.title)) {
                    idExists = true;
                    break;
                }
            }
            if (idExists) continue;

            try {
                KeePassEntry entry = new KeePassEntry("SP", id, displayName, "", "", site.getUrl(), "",
                        false, false, false, false, false);
                saveEntry(entry);
                entries.add(entry);
                created++;
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "[SP-Scan] Failed to save SP entry: " + id, ex);
            }
        }

        if (created > 0) {
            model.fireTableDataChanged();
        }
        return created;
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
                // Defaults: login=false, proxy=true, autoIndex=false
                KeePassEntry entry = new KeePassEntry("Wiki", id, displayName, "", "", url, "",
                        false, true, false, false, false);
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
    //  Dual persistence: KeePass or Settings
    // ═══════════════════════════════════════════════════════════

    /** Credential-store key prefix for password entries. */
    private static final String PWD_PREFIX = "pwd:";

    /** Is the user's password method set to KeePass? */
    private static boolean isKeePass() {
        try {
            return PasswordMethod.valueOf(SettingsHelper.load().passwordMethod) == PasswordMethod.KEEPASS;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Load ────────────────────────────────────────────────────

    private static List<KeePassEntry> loadEntries() {
        return isKeePass() ? loadFromKeePass() : loadFromSettings();
    }

    private static List<KeePassEntry> loadFromSettings() {
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

    private static List<KeePassEntry> loadFromKeePass() {
        String raw = CredentialStore.listKeePassEntries();
        List<KeePassEntry> entries = parseKeePassOutput(raw);

        // Sync metadata into settings.passwordEntries so other components
        // (parseWikiSites, WikiSettingsPanel, etc.) can discover entries
        // even if they were created before this sync mechanism existed.
        syncMetadataToSettings(entries);

        return entries;
    }

    /**
     * Write metadata (no credentials) for the given entries into
     * {@code settings.passwordEntries}, replacing any stale entries.
     */
    private static void syncMetadataToSettings(List<KeePassEntry> entries) {
        Settings settings = SettingsHelper.load();
        // Build set of known IDs from KeePass
        java.util.Set<String> keePassIds = new java.util.HashSet<String>();
        for (KeePassEntry e : entries) {
            keePassIds.add(e.title);
        }
        // Remove stale entries that no longer exist in KeePass
        Iterator<Settings.PasswordEntryMeta> it = settings.passwordEntries.iterator();
        while (it.hasNext()) {
            Settings.PasswordEntryMeta existing = it.next();
            if (keePassIds.contains(existing.id)) {
                it.remove(); // will be re-added below with fresh data
            }
        }
        // Add fresh metadata
        for (KeePassEntry e : entries) {
            Settings.PasswordEntryMeta meta = new Settings.PasswordEntryMeta();
            meta.id = e.title;
            meta.category = e.category;
            meta.displayName = e.displayName;
            meta.url = e.url;
            meta.requiresLogin = e.requiresLogin;
            meta.useProxy = e.useProxy;
            meta.autoIndex = e.autoIndex;
            meta.savePassword = e.savePassword;
            meta.sessionCache = e.sessionCache;
            settings.passwordEntries.add(meta);
        }
        SettingsHelper.save(settings);
    }

    // ── Save ────────────────────────────────────────────────────

    private static void saveEntry(KeePassEntry entry) {
        if (isKeePass()) {
            saveToKeePass(entry);
        } else {
            saveToSettings(entry);
        }
    }

    private static void saveToSettings(KeePassEntry entry) {
        Settings settings = SettingsHelper.load();

        Iterator<Settings.PasswordEntryMeta> it = settings.passwordEntries.iterator();
        while (it.hasNext()) {
            if (entry.title.equals(it.next().id)) {
                it.remove();
                break;
            }
        }

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

        CredentialStore.store(PWD_PREFIX + entry.title, entry.userName, entry.password);
    }

    private static void saveToKeePass(KeePassEntry entry) {
        // Try update first (works for new entries too — addLogin fallback inside)
        CredentialStore.updateKeePassEntry(
                entry.title, entry.userName, entry.password, entry.url,
                entry.displayName, entry.category,
                entry.requiresLogin, entry.useProxy, entry.autoIndex);

        // Also persist metadata (without credentials) to settings.passwordEntries
        // so that other components (e.g. parseWikiSites) can discover entries.
        Settings settings = SettingsHelper.load();
        Iterator<Settings.PasswordEntryMeta> it = settings.passwordEntries.iterator();
        while (it.hasNext()) {
            if (entry.title.equals(it.next().id)) {
                it.remove();
                break;
            }
        }
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
    }

    // ── Delete ──────────────────────────────────────────────────

    private static void deleteEntry(KeePassEntry entry) {
        if (isKeePass()) {
            CredentialStore.removeKeePassEntry(entry.title, entry.uniqueID);
        }
        // Always remove metadata from settings.passwordEntries
        Settings settings = SettingsHelper.load();
        Iterator<Settings.PasswordEntryMeta> it = settings.passwordEntries.iterator();
        while (it.hasNext()) {
            if (entry.title.equals(it.next().id)) {
                it.remove();
                break;
            }
        }
        SettingsHelper.save(settings);
        if (!isKeePass()) {
            CredentialStore.remove(PWD_PREFIX + entry.title);
        }
    }

    // ── KeePass output parser ───────────────────────────────────

    private static List<KeePassEntry> parseKeePassOutput(String raw) {
        List<KeePassEntry> entries = new ArrayList<KeePassEntry>();
        if (raw == null || raw.trim().isEmpty()) return entries;

        String cleaned = raw.trim();
        int lastNl = cleaned.lastIndexOf('\n');
        if (lastNl > 0 && cleaned.substring(lastNl + 1).trim().startsWith("OK:")) {
            cleaned = cleaned.substring(0, lastNl).trim();
        } else if (cleaned.startsWith("OK:")) {
            return entries;
        }

        for (String block : cleaned.split("\\n\\s*\\n")) {
            String title = "", userName = "", password = "", url = "", uniqueID = "";
            String mmCat = "", mmDisp = "";
            String mmLogin = "false", mmProxy = "false", mmIdx = "false";
            String mmSavePw = "false", mmSession = "false";

            for (String line : block.split("\\n")) {
                line = line.trim();
                if      (line.startsWith("Title: "))              title     = line.substring(7).trim();
                else if (line.startsWith("UserName: "))           userName  = line.substring(10).trim();
                else if (line.startsWith("Password: "))           password  = line.substring(10).trim();
                else if (line.startsWith("URL: "))                url       = line.substring(5).trim();
                else if (line.startsWith("UniqueID: "))           uniqueID  = line.substring(10).trim();
                else if (line.startsWith("MM_Category: "))        mmCat     = line.substring(13).trim();
                else if (line.startsWith("MM_DisplayName: "))     mmDisp    = line.substring(16).trim();
                else if (line.startsWith("MM_RequiresLogin: "))   mmLogin   = line.substring(18).trim();
                else if (line.startsWith("MM_UseProxy: "))        mmProxy   = line.substring(13).trim();
                else if (line.startsWith("MM_AutoIndex: "))       mmIdx     = line.substring(14).trim();
                else if (line.startsWith("MM_SavePassword: "))    mmSavePw  = line.substring(17).trim();
                else if (line.startsWith("MM_SessionCache: "))    mmSession = line.substring(17).trim();
            }

            if (!title.isEmpty()) {
                entries.add(new KeePassEntry(
                        !mmCat.isEmpty() ? mmCat : "Mainframe",
                        title,
                        !mmDisp.isEmpty() ? mmDisp : title,
                        userName, password, url, uniqueID,
                        "true".equalsIgnoreCase(mmLogin),
                        "true".equalsIgnoreCase(mmProxy),
                        "true".equalsIgnoreCase(mmIdx),
                        "true".equalsIgnoreCase(mmSavePw),
                        "true".equalsIgnoreCase(mmSession)));
            }
        }
        return entries;
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

