package de.bund.zrb.ui.commands;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.mcpserver.browser.BrowserLauncher;
import de.bund.zrb.model.Settings;
import de.bund.zrb.sharepoint.NetworkDriveMapper;
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
    private static final List<String> CATEGORIES = Arrays.asList("Mainframe", "Wiki", "SP", "Confluence");

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
        return "\u2731 Passw\u00f6rter\u2026";
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
        table.getColumnModel().getColumn(0).setPreferredWidth(70);  // Netzwerk
        table.getColumnModel().getColumn(1).setPreferredWidth(70);  // Kategorie
        table.getColumnModel().getColumn(2).setPreferredWidth(120); // ID
        table.getColumnModel().getColumn(3).setPreferredWidth(130); // Anzeigename
        table.getColumnModel().getColumn(4).setPreferredWidth(120); // Benutzername
        table.getColumnModel().getColumn(5).setPreferredWidth(100); // Passwort
        table.getColumnModel().getColumn(6).setPreferredWidth(180); // URL
        table.getColumnModel().getColumn(7).setPreferredWidth(46);  // Login
        table.getColumnModel().getColumn(7).setMaxWidth(54);
        table.getColumnModel().getColumn(8).setPreferredWidth(46);  // Proxy
        table.getColumnModel().getColumn(8).setMaxWidth(54);
        table.getColumnModel().getColumn(9).setPreferredWidth(56);  // Auto-Idx
        table.getColumnModel().getColumn(9).setMaxWidth(64);
        table.getColumnModel().getColumn(10).setPreferredWidth(72);  // PW-Speicher
        table.getColumnModel().getColumn(10).setMaxWidth(80);
        table.getColumnModel().getColumn(11).setPreferredWidth(54); // Session
        table.getColumnModel().getColumn(11).setMaxWidth(62);
        table.getColumnModel().getColumn(12).setPreferredWidth(36); // 👁
        table.getColumnModel().getColumn(12).setMaxWidth(40);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        // Eye column (index 12): renderer + click handler
        table.getColumnModel().getColumn(12).setCellRenderer(new DefaultTableCellRenderer() {
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
                if (col == 12 && row >= 0) {
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

        JButton netDriveBtn = new JButton("\uD83D\uDDB3 Netzlaufwerk");
        netDriveBtn.setToolTipText("Ausgewählten SP-Eintrag als Windows-Netzlaufwerk (WebDAV) verbinden");
        netDriveBtn.addActionListener(e -> mapSelectedAsNetworkDrive(table, entries));

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
        buttonPanel.add(netDriveBtn);

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

        // Network zone dropdown
        JComboBox<String> networkZoneBox = new JComboBox<String>(new String[]{
                "\uD83C\uDF10 Extern", "\uD83C\uDFE2 Intern"});
        if (existing != null && "INTERN".equals(existing.networkZone)) {
            networkZoneBox.setSelectedIndex(1);
        }

        JTextField idField          = new JTextField(existing != null ? existing.title : "", 25);
        JTextField displayNameField = new JTextField(existing != null ? existing.displayName : "", 25);
        JTextField userField        = new JTextField(existing != null ? existing.userName : "", 25);
        JPasswordField passField    = new JPasswordField(existing != null ? existing.password : "", 25);
        JTextField urlField         = new JTextField(existing != null ? existing.url : "", 25);

        // Certificate alias (Confluence)
        final JTextField certAliasField = new JTextField(
                existing != null ? existing.certAlias : "", 25);
        certAliasField.setEditable(false);
        certAliasField.setBackground(UIManager.getColor("TextField.inactiveBackground"));
        JButton certChooseBtn = new JButton("🔒 Zertifikat…");
        certChooseBtn.setToolTipText("Windows-Zertifikat für mTLS-Authentifizierung auswählen");
        certChooseBtn.addActionListener(e -> {
            String chosen = de.bund.zrb.confluence.CertificateChooser.showChooserDialog(
                    parent, certAliasField.getText().trim());
            if (chosen != null) {
                certAliasField.setText(chosen);
            }
        });
        JPanel certPanel = new JPanel(new BorderLayout(4, 0));
        certPanel.add(certAliasField, BorderLayout.CENTER);
        certPanel.add(certChooseBtn, BorderLayout.EAST);

        // Checkbox to enable/disable certificate usage (default: checked)
        final JCheckBox useCertCb = new JCheckBox("Zertifikat verwenden", true);
        useCertCb.setToolTipText("mTLS-Zertifikat für die Verbindung verwenden");
        useCertCb.addActionListener(e -> {
            boolean use = useCertCb.isSelected();
            certAliasField.setEnabled(use);
            certChooseBtn.setEnabled(use);
        });

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
        } else {
            // Defaults for new entries
            loginCb.setSelected(true);
            savePwCb.setSelected(true);
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

        // Update URL requirement and cert visibility when category changes
        Runnable updateCategoryUi = new Runnable() {
            @Override
            public void run() {
                String cat = (String) categoryBox.getSelectedItem();
                boolean urlRequired = "Wiki".equals(cat) || "SP".equals(cat) || "Confluence".equals(cat);
                urlLabel.setText(urlRequired ? "URL: *" : "URL:");
                boolean showCert = "Confluence".equals(cat);
                useCertCb.setVisible(showCert);
                certPanel.setVisible(showCert);
            }
        };
        categoryBox.addActionListener(e -> updateCategoryUi.run());

        // Set initial state
        if (existing != null && ("Wiki".equals(existing.category) || "SP".equals(existing.category)
                || "Confluence".equals(existing.category))) {
            urlLabel.setText("URL: *");
        }
        // Initial cert visibility and checkbox state
        boolean showCertInitial = existing != null && "Confluence".equals(existing.category);
        useCertCb.setVisible(showCertInitial);
        certPanel.setVisible(showCertInitial);
        // If editing an existing entry without a cert, uncheck the checkbox
        if (existing != null && (existing.certAlias == null || existing.certAlias.trim().isEmpty())) {
            useCertCb.setSelected(false);
            certAliasField.setEnabled(false);
            certChooseBtn.setEnabled(false);
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
        panel.add(new JLabel("Netzwerk:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(networkZoneBox, gbc);

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

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(useCertCb, gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(certPanel, gbc);

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

        // Confluence connection test button
        JButton confluenceTestBtn = new JButton("🔗 Verbindung testen");
        confluenceTestBtn.setToolTipText("REST-Verbindung mit Zertifikat + Basic Auth testen");
        confluenceTestBtn.setVisible("Confluence".equals(categoryBox.getSelectedItem()));

        categoryBox.addActionListener(e -> {
            String cat = (String) categoryBox.getSelectedItem();
            ssoTestBtn.setVisible("SP".equals(cat));
            confluenceTestBtn.setVisible("Confluence".equals(cat));
            updateCategoryUi.run();
        });
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

        confluenceTestBtn.addActionListener(e -> {
            String testUrl = urlField.getText().trim();
            String testAlias = useCertCb.isSelected() ? certAliasField.getText().trim() : "";
            String testUser = loginCb.isSelected() ? userField.getText().trim() : "";
            String testPass = loginCb.isSelected() ? new String(passField.getPassword()) : "";
            if (testUrl.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                        "Bitte eine URL eingeben.",
                        "Confluence-Test", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (loginCb.isSelected() && testUser.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                        "Bitte einen Benutzernamen eingeben oder \u201eLogin erforderlich\u201c deaktivieren.",
                        "Confluence-Test", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (useCertCb.isSelected() && testAlias.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                        "Bitte ein Zertifikat ausw\u00e4hlen oder \u201eZertifikat verwenden\u201c deaktivieren.",
                        "Confluence-Test", JOptionPane.WARNING_MESSAGE);
                return;
            }
            dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            new SwingWorker<de.bund.zrb.confluence.ConfluenceRestClient.TestResult, Void>() {
                @Override
                protected de.bund.zrb.confluence.ConfluenceRestClient.TestResult doInBackground() {
                    de.bund.zrb.confluence.ConfluenceConnectionConfig cfg =
                            new de.bund.zrb.confluence.ConfluenceConnectionConfig(
                                    testUrl, testUser, testPass, testAlias, 15000, 15000);
                    de.bund.zrb.confluence.ConfluenceRestClient cl =
                            new de.bund.zrb.confluence.ConfluenceRestClient(cfg);
                    return cl.testConnection();
                }
                @Override
                protected void done() {
                    dialog.setCursor(Cursor.getDefaultCursor());
                    try {
                        de.bund.zrb.confluence.ConfluenceRestClient.TestResult result = get();
                        if (result.isSuccess()) {
                            JOptionPane.showMessageDialog(dialog,
                                    "✓ Verbindung erfolgreich! (HTTP " + result.getStatusCode() + ")\n\n"
                                            + "URL: " + testUrl + "\n"
                                            + "Zertifikat: " + testAlias,
                                    "Confluence-Test", JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            String detail = result.getErrorDetail() != null
                                    ? result.getErrorDetail()
                                    : "HTTP " + result.getStatusCode();
                            JOptionPane.showMessageDialog(dialog,
                                    "✗ Verbindung fehlgeschlagen\n\n"
                                            + detail + "\n\n"
                                            + "Prüfen Sie URL, Zertifikat, Proxy und Zugangsdaten.",
                                    "Confluence-Test", JOptionPane.WARNING_MESSAGE);
                        }
                    } catch (Exception ex) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        JOptionPane.showMessageDialog(dialog,
                                "Fehler beim Verbindungsaufbau:\n\n"
                                        + cause.getClass().getSimpleName() + ": " + cause.getMessage(),
                                "Confluence-Test", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(e -> {
            // ── Validate BEFORE closing the dialog ──
            String valCat = (String) categoryBox.getSelectedItem();
            String valId = idField.getText().trim();
            String valUrl = urlField.getText().trim();

            if (valId.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Die ID darf nicht leer sein.",
                        "Ungültige Eingabe", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (("Wiki".equals(valCat) || "SP".equals(valCat) || "Confluence".equals(valCat))
                    && valUrl.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                        "Für " + valCat + "-Einträge ist die URL ein Pflichtfeld.",
                        "Ungültige Eingabe", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Confluence: cert hint (not blocking) if checkbox is checked but no cert chosen
            if ("Confluence".equals(valCat) && useCertCb.isSelected()
                    && certAliasField.getText().trim().isEmpty()) {
                int choice = JOptionPane.showOptionDialog(dialog,
                "Sie haben \u201eZertifikat verwenden\u201c aktiviert,\n"
                                + "aber kein Zertifikat ausgew\u00e4hlt.\n\n"
                                + "Ohne mTLS-Zertifikat kann die Verbindung zu\n"
                                + "Confluence fehlschlagen, wenn der Server eines erfordert.\n\n"
                                + "M\u00f6chten Sie trotzdem ohne Zertifikat speichern?",
                        "Hinweis: Kein Zertifikat",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.INFORMATION_MESSAGE,
                        null,
                        new String[]{"Ohne Zertifikat speichern", "Zur\u00fcck zum Bearbeiten"},
                        "Zur\u00fcck zum Bearbeiten");
                if (choice != 0) {
                    return; // dialog stays open — user can add cert
                }
                // User explicitly chose to continue without cert
            }

            // Warning when disabling "PW speichern" on an existing entry
            // that previously had a saved password
            if (existing != null && existing.savePassword && !savePwCb.isSelected()) {
                int choice = JOptionPane.showOptionDialog(dialog,
                        "Sie haben \u201ePW speichern\u201c deaktiviert.\n\n"
                                + "Das gespeicherte Passwort wird aus dem Keystore\n"
                                + "bzw. der Einstellungsdatei gel\u00f6scht.\n\n"
                                + "M\u00f6chten Sie fortfahren?",
                        "Passwort l\u00f6schen",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        new String[]{"Passwort l\u00f6schen", "Zur\u00fcck"},
                        "Zur\u00fcck");
                if (choice != 0) {
                    return; // dialog stays open
                }
            }

            confirmed[0] = true;
            dialog.dispose();
        });
        JButton cancelBtn = new JButton("Abbrechen");
        cancelBtn.addActionListener(e -> dialog.dispose());

        JPanel buttonBar = new JPanel(new BorderLayout());
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leftButtons.add(ssoTestBtn);
        leftButtons.add(confluenceTestBtn);
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

        // All validation already done inside OK handler — just build the entry
        String category    = (String) categoryBox.getSelectedItem();
        String id          = idField.getText().trim();
        String displayName = displayNameField.getText().trim();
        String user        = userField.getText().trim();
        String pass        = new String(passField.getPassword());
        String url         = urlField.getText().trim();
        String certAlias   = useCertCb.isSelected() ? certAliasField.getText().trim() : "";
        String networkZone = networkZoneBox.getSelectedIndex() == 1 ? "INTERN" : "EXTERN";

        return new KeePassEntry(category, id, displayName, user, pass, url,
                existing != null ? existing.uniqueID : "",
                certAlias, networkZone,
                loginCb.isSelected(), proxyCb.isSelected(), autoIdxCb.isSelected(),
                savePwCb.isSelected(), sessionCb.isSelected());
    }

    // ═══════════════════════════════════════════════════════════
    //  Network drive mapping for SP entries
    // ═══════════════════════════════════════════════════════════

    /**
     * Map one or more selected SP entries as Windows network drives (WebDAV).
     * <p>
     * If exactly one row is selected, that entry is mapped directly.
     * If no row is selected, all SP entries are offered in a checklist dialog.
     */
    private void mapSelectedAsNetworkDrive(final JTable table, final List<KeePassEntry> entries) {
        // Gather candidate(s)
        int selRow = table.getSelectedRow();
        if (selRow >= 0) {
            int modelRow = table.convertRowIndexToModel(selRow);
            KeePassEntry entry = entries.get(modelRow);
            if (!"SP".equalsIgnoreCase(entry.category)) {
                JOptionPane.showMessageDialog(parent,
                        "Netzlaufwerk-Zuordnung ist nur für SP-Einträge möglich.\n"
                                + "Der ausgewählte Eintrag hat die Kategorie \"" + entry.category + "\".",
                        "Kein SP-Eintrag", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (entry.url == null || entry.url.trim().isEmpty()) {
                JOptionPane.showMessageDialog(parent,
                        "Der SP-Eintrag \"" + entry.title + "\" hat keine URL.",
                        "Keine URL", JOptionPane.WARNING_MESSAGE);
                return;
            }
            mapSingleDrive(entry);
        } else {
            // No row selected → show multi-select dialog for all SP entries
            mapMultipleDrives(entries);
        }
    }

    /**
     * Map a single SP entry as a network drive with progress feedback.
     */
    private void mapSingleDrive(final KeePassEntry entry) {
        parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<NetworkDriveMapper.MappingResult, Void>() {
            @Override
            protected NetworkDriveMapper.MappingResult doInBackground() {
                return NetworkDriveMapper.mapDrive(entry.url, entry.displayName);
            }

            @Override
            protected void done() {
                parent.setCursor(Cursor.getDefaultCursor());
                try {
                    NetworkDriveMapper.MappingResult result = get();
                    if (result.success) {
                        JOptionPane.showMessageDialog(parent,
                                "\"" + entry.displayName + "\" wurde als Netzlaufwerk "
                                        + result.driveLetter + " verbunden.\n\n"
                                        + "UNC-Pfad: " + result.uncPath,
                                "Netzlaufwerk verbunden", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(parent,
                                "Netzlaufwerk konnte nicht verbunden werden:\n" + result.message,
                                "Fehler", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "MapDrive failed", ex);
                    JOptionPane.showMessageDialog(parent,
                            "Fehler beim Verbinden:\n" + ex.getMessage(),
                            "Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Show a dialog listing all SP entries with checkboxes, then map selected ones.
     */
    private void mapMultipleDrives(final List<KeePassEntry> allEntries) {
        // Collect SP entries with URLs
        final List<KeePassEntry> spEntries = new ArrayList<KeePassEntry>();
        for (KeePassEntry e : allEntries) {
            if ("SP".equalsIgnoreCase(e.category) && e.url != null && !e.url.trim().isEmpty()) {
                spEntries.add(e);
            }
        }

        if (spEntries.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "Es sind keine SP-Einträge mit URLs vorhanden.",
                    "Keine SP-Einträge", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Build checklist
        final JCheckBox[] checkBoxes = new JCheckBox[spEntries.size()];
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

        // Check which are already mapped
        final java.util.Map<String, String> mapped = NetworkDriveMapper.listMappedDrives();
        java.util.Set<String> mappedUnc = new java.util.HashSet<String>();
        for (String unc : mapped.values()) {
            mappedUnc.add(unc.toLowerCase().replace("/", "\\"));
        }

        for (int i = 0; i < spEntries.size(); i++) {
            KeePassEntry se = spEntries.get(i);
            String unc = SharePointPathUtil.toUncPath(se.url);
            boolean alreadyMapped = mappedUnc.contains(unc.toLowerCase().replace("/", "\\"));

            String label = se.displayName;
            if (label == null || label.isEmpty()) label = se.title;
            if (alreadyMapped) {
                label += "  ✓ (bereits verbunden)";
            }

            checkBoxes[i] = new JCheckBox(label, !alreadyMapped);
            checkBoxes[i].setEnabled(!alreadyMapped);
            checkBoxes[i].setToolTipText(se.url);
            listPanel.add(checkBoxes[i]);
        }

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setPreferredSize(new Dimension(500, Math.min(350, 30 + spEntries.size() * 28)));
        scroll.setBorder(BorderFactory.createTitledBorder("SP-Sites als Netzlaufwerk verbinden"));

        int result = JOptionPane.showConfirmDialog(parent, scroll,
                "Netzlaufwerke einrichten", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        // Collect selected
        final List<KeePassEntry> toMap = new ArrayList<KeePassEntry>();
        for (int i = 0; i < checkBoxes.length; i++) {
            if (checkBoxes[i].isSelected() && checkBoxes[i].isEnabled()) {
                toMap.add(spEntries.get(i));
            }
        }
        if (toMap.isEmpty()) return;

        // Map in background
        parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<List<NetworkDriveMapper.MappingResult>, Void>() {
            @Override
            protected List<NetworkDriveMapper.MappingResult> doInBackground() {
                List<NetworkDriveMapper.MappingResult> results = new ArrayList<NetworkDriveMapper.MappingResult>();
                for (KeePassEntry entry : toMap) {
                    results.add(NetworkDriveMapper.mapDrive(entry.url, entry.displayName));
                }
                return results;
            }

            @Override
            protected void done() {
                parent.setCursor(Cursor.getDefaultCursor());
                try {
                    List<NetworkDriveMapper.MappingResult> results = get();
                    StringBuilder sb = new StringBuilder();
                    int ok = 0, fail = 0;
                    for (NetworkDriveMapper.MappingResult r : results) {
                        if (r.success) {
                            sb.append("✓ ").append(r.driveLetter).append("  →  ").append(r.url).append("\n");
                            ok++;
                        } else {
                            sb.append("✗ ").append(r.url).append(": ").append(r.message).append("\n");
                            fail++;
                        }
                    }
                    String title = ok + " verbunden" + (fail > 0 ? ", " + fail + " fehlgeschlagen" : "");
                    JOptionPane.showMessageDialog(parent, sb.toString(), title,
                            fail > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "MapMultipleDrives failed", ex);
                    JOptionPane.showMessageDialog(parent,
                            "Fehler beim Verbinden:\n" + ex.getMessage(),
                            "Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
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
        // ── 1. Build input dialog with browser selection + URL ──
        Settings currentSettings = SettingsHelper.load();

        JComboBox<String> browserBox = new JComboBox<String>(new String[]{"Firefox", "Chrome", "Edge"});
        if (currentSettings.browserType != null) {
            browserBox.setSelectedItem(currentSettings.browserType);
        }
        JTextField urlField = new JTextField(30);

        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;

        g.gridx = 0; g.gridy = 0;
        inputPanel.add(new JLabel("Browser:"), g);
        g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
        inputPanel.add(browserBox, g);

        g.gridx = 0; g.gridy = 1; g.fill = GridBagConstraints.NONE; g.weightx = 0;
        inputPanel.add(new JLabel("URL:"), g);
        g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
        inputPanel.add(urlField, g);

        g.gridx = 0; g.gridy = 2; g.gridwidth = 2;
        inputPanel.add(new JLabel("<html><i>Seite mit Links zu SharePoint-Sites<br>"
                + "(z.\u00a0B. Intranet-\u00dcbersicht)</i></html>"), g);

        int result = JOptionPane.showConfirmDialog(parent, inputPanel,
                "SharePoint-Links scannen", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String url = urlField.getText().trim();
        if (url.isEmpty()) return;

        // Auto-prepend https:// if missing
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        // ── 2. Persist browser choice so BrowserService uses it ──
        String chosenBrowser = (String) browserBox.getSelectedItem();
        if (chosenBrowser != null && !chosenBrowser.equals(currentSettings.browserType)) {
            currentSettings.browserType = chosenBrowser;
            SettingsHelper.save(currentSettings);
        }

        // ── 3. Get BrowserService (reads browserType from Settings) ──
        BrowserService browserService = null;
        if (parent instanceof MainframeContext) {
            // Close existing session if browser type changed
            BrowserService bs = ((MainframeContext) parent).getBrowserService();
            if (bs != null && bs.isSessionActive()
                    && !chosenBrowser.equalsIgnoreCase(bs.getBrowserType())) {
                bs.closeSession();
            }
            browserService = bs;
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

                // Browser is no longer needed — close the session
                try {
                    bs.closeSession();
                } catch (Exception ignore) { }

                try {
                    List<SharePointSite> allLinks = get();

                    if (allLinks.isEmpty()) {
                        JOptionPane.showMessageDialog(parent,
                                "Keine Links auf der Seite gefunden.\n\n"
                                        + "M\u00f6glicherweise ben\u00f6tigt die Seite l\u00e4nger zum Laden,\n"
                                        + "oder sie enth\u00e4lt keine Hyperlinks.",
                                "Keine Links", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    // ── 4. Show selection dialog with regex filter ──
                    int saved = showSpLinkSelectionDialog(allLinks, entries, model, targetUrl);
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
     * Show a dialog where the user can select which discovered links
     * should be saved as SP password entries.
     * <p>
     * The dialog includes a dynamic URL-filter panel (editable dropdowns
     * per path segment). Default filter: {@code <DOMAIN>/sites/[^/]+}
     *
     * @param allLinks   all links found on the page (unfiltered)
     * @param entries    current password entries (for duplicate detection)
     * @param model      the table model to refresh after saving
     * @param scannedUrl the URL that was scanned (used for default domain)
     * @return number of entries actually created
     */
    private int showSpLinkSelectionDialog(final List<SharePointSite> allLinks,
                                          final List<KeePassEntry> entries,
                                          final EntryTableModel model,
                                          String scannedUrl) {
        // ── Existing-URL lookup for "vorhanden" status ──
        final java.util.Set<String> existingUrls = new java.util.HashSet<String>();
        for (KeePassEntry e : entries) {
            if ("SP".equals(e.category) && e.url != null) {
                existingUrls.add(e.url);
            }
        }

        // ── Build filter panel ──
        List<String> allUrls = new ArrayList<String>();
        for (SharePointSite site : allLinks) {
            if (site.getUrl() != null) allUrls.add(site.getUrl());
        }
        final de.bund.zrb.ui.components.SpUrlFilterPanel filterPanel =
                new de.bund.zrb.ui.components.SpUrlFilterPanel(allUrls, scannedUrl);

        // ── Filtered list (rebuilt on every filter change) ──
        final List<SharePointSite> filtered = new ArrayList<SharePointSite>();

        // Selection state keyed by URL – survives filter changes
        final java.util.Map<String, Boolean> selectionMap = new java.util.LinkedHashMap<String, Boolean>();

        // Apply initial filter
        applyFilter(filterPanel, allLinks, filtered, existingUrls, selectionMap);

        // ── Table model over the filtered list ──
        final javax.swing.table.AbstractTableModel tModel = new javax.swing.table.AbstractTableModel() {
            final String[] cols = {"\u2713", "Name", "URL", "Status"};
            @Override public int getRowCount() { return filtered.size(); }
            @Override public int getColumnCount() { return cols.length; }
            @Override public String getColumnName(int c) { return cols[c]; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
            @Override public boolean isCellEditable(int r, int c) { return c == 0; }

            @Override
            public Object getValueAt(int r, int c) {
                SharePointSite s = filtered.get(r);
                switch (c) {
                    case 0:
                        Boolean sel = selectionMap.get(s.getUrl());
                        return sel != null ? sel : Boolean.FALSE;
                    case 1: return s.getName();
                    case 2: return s.getUrl();
                    case 3: return existingUrls.contains(s.getUrl()) ? "vorhanden" : "neu";
                    default: return "";
                }
            }

            @Override
            public void setValueAt(Object v, int r, int c) {
                if (c == 0 && v instanceof Boolean) {
                    selectionMap.put(filtered.get(r).getUrl(), (Boolean) v);
                    fireTableCellUpdated(r, c);
                }
            }
        };

        final JTable table = new JTable(tModel);
        table.getColumnModel().getColumn(0).setMaxWidth(30);
        table.getColumnModel().getColumn(0).setMinWidth(30);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(350);
        table.getColumnModel().getColumn(3).setPreferredWidth(70);
        table.setRowHeight(22);

        // ── React to filter changes ──
        filterPanel.addChangeListener(new Runnable() {
            @Override
            public void run() {
                applyFilter(filterPanel, allLinks, filtered, existingUrls, selectionMap);
                tModel.fireTableDataChanged();
            }
        });

        // ── Assemble resizable dialog ──
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(800, Math.min(400, 60 + filtered.size() * 22)));

        JPanel contentPanel = new JPanel(new BorderLayout(0, 6));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        contentPanel.add(filterPanel, BorderLayout.NORTH);
        contentPanel.add(scroll, BorderLayout.CENTER);

        // OK / Cancel buttons
        final int[] dialogResult = {JOptionPane.CANCEL_OPTION};
        final JDialog dlg = new JDialog(
                (parent instanceof Frame) ? (Frame) parent : null,
                "SharePoint-Links importieren (" + allLinks.size() + " gesamt)",
                true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Abbrechen");
        okBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                dialogResult[0] = JOptionPane.OK_OPTION;
                dlg.dispose();
            }
        });
        cancelBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                dlg.dispose();
            }
        });
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);

        dlg.getContentPane().setLayout(new BorderLayout());
        dlg.getContentPane().add(contentPanel, BorderLayout.CENTER);
        dlg.getContentPane().add(btnPanel, BorderLayout.SOUTH);
        dlg.setResizable(true);
        dlg.pack();

        // Limit to screen bounds, position centred
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(
                dlg.getGraphicsConfiguration());
        int maxW = screen.width - screenInsets.left - screenInsets.right;
        int maxH = screen.height - screenInsets.top - screenInsets.bottom;
        if (dlg.getWidth() > maxW) dlg.setSize(maxW, dlg.getHeight());
        if (dlg.getHeight() > maxH) dlg.setSize(dlg.getWidth(), maxH);
        dlg.setMinimumSize(new Dimension(500, 300));
        dlg.setLocationRelativeTo(parent);

        // Enter = OK, Escape = Cancel
        dlg.getRootPane().setDefaultButton(okBtn);
        dlg.getRootPane().registerKeyboardAction(
                new ActionListener() {
                    @Override public void actionPerformed(ActionEvent e) { dlg.dispose(); }
                },
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        dlg.setVisible(true);

        if (dialogResult[0] != JOptionPane.OK_OPTION) return 0;

        // Persist filter segments for next time
        filterPanel.saveSegments();

        // ── Save selected links as SP password entries ──
        int created = 0;
        for (SharePointSite site : filtered) {
            Boolean sel = selectionMap.get(site.getUrl());
            if (sel == null || !sel) continue;
            if (existingUrls.contains(site.getUrl())) continue;

            String id = de.bund.zrb.ui.settings.categories.SharePointSettingsPanel.toSiteId(site);
            String displayName = site.getName();
            if (displayName == null || displayName.isEmpty()) {
                displayName = SharePointPathUtil.extractDisplayName(site.getUrl());
            }
            if (displayName != null) displayName = displayName.trim();
            String siteUrl = site.getUrl() != null ? site.getUrl().trim() : "";

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
                KeePassEntry entry = new KeePassEntry("SP", id, displayName, "", "", siteUrl, "",
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

    /**
     * Apply the current regex filter to rebuild the filtered list.
     */
    private static void applyFilter(de.bund.zrb.ui.components.SpUrlFilterPanel filterPanel,
                                     List<SharePointSite> allLinks,
                                     List<SharePointSite> filtered,
                                     java.util.Set<String> existingUrls,
                                     java.util.Map<String, Boolean> selectionMap) {
        filtered.clear();
        java.util.regex.Pattern pattern = filterPanel.compileFilter();
        for (SharePointSite site : allLinks) {
            String url = site.getUrl();
            if (url == null) continue;
            if (pattern != null && pattern.matcher(url).matches()) {
                filtered.add(site);
                // Pre-select new entries that are not yet in the selection map
                if (!selectionMap.containsKey(url)) {
                    selectionMap.put(url, !existingUrls.contains(url));
                }
            }
        }
        filterPanel.setMatchCount(filtered.size(), allLinks.size());
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
                // Defaults: login=false, proxy=true, autoIndex=false, zone=EXTERN
                KeePassEntry entry = new KeePassEntry("Wiki", id, displayName, "", "", url, "",
                        "", "EXTERN",
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
                "Netzwerk", "Kategorie", "ID", "Anzeigename", "Benutzername", "Passwort", "URL",
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
            // Columns 7–11 are boolean checkboxes
            return (col >= 7 && col <= 11) ? Boolean.class : Object.class;
        }

        @Override
        public Object getValueAt(int row, int col) {
            KeePassEntry e = entries.get(row);
            switch (col) {
                case 0: return "INTERN".equals(e.networkZone) ? "\uD83C\uDFE2 Intern" : "\uD83C\uDF10 Extern";
                case 1: return e.category;
                case 2: return e.title;
                case 3: return e.displayName;
                case 4: return e.userName;
                case 5: return visible[row] ? e.password : "••••••••";
                case 6: return e.url;
                case 7: return e.requiresLogin;
                case 8: return e.useProxy;
                case 9: return e.autoIndex;
                case 10: return e.savePassword;
                case 11: return e.sessionCache;
                case 12: return visible[row] ? "\uD83D\uDD13" : "\uD83D\uDC41";
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
                    meta.certAlias != null ? meta.certAlias : "",
                    meta.networkZone != null ? meta.networkZone : "EXTERN",
                    meta.requiresLogin, meta.useProxy, meta.autoIndex,
                    meta.savePassword, meta.sessionCache));
        }
        return entries;
    }

    private static List<KeePassEntry> loadFromKeePass() {
        String raw = CredentialStore.listKeePassEntries();
        List<KeePassEntry> entries = parseKeePassOutput(raw);

        // Merge savePassword/sessionCache flags from existing settings, because
        // older KeePass entries may not yet have MM_SavePassword/MM_SessionCache fields.
        // Without this, opening the dialog would reset the flags to false.
        Settings settings = SettingsHelper.load();
        Map<String, Settings.PasswordEntryMeta> existingMeta = new HashMap<String, Settings.PasswordEntryMeta>();
        for (Settings.PasswordEntryMeta meta : settings.passwordEntries) {
            existingMeta.put(meta.id, meta);
        }
        List<KeePassEntry> merged = new ArrayList<KeePassEntry>(entries.size());
        for (KeePassEntry e : entries) {
            Settings.PasswordEntryMeta prev = existingMeta.get(e.title);
            if (prev != null && !e.savePassword && prev.savePassword) {
                // KeePass entry doesn't have MM_SavePassword yet — use value from settings
                e = new KeePassEntry(e.category, e.title, e.displayName, e.userName,
                        e.password, e.url, e.uniqueID, e.certAlias, e.networkZone,
                        e.requiresLogin, e.useProxy, e.autoIndex,
                        prev.savePassword, e.sessionCache);
            }
            if (prev != null && !e.sessionCache && prev.sessionCache) {
                // KeePass entry doesn't have MM_SessionCache yet — use value from settings
                e = new KeePassEntry(e.category, e.title, e.displayName, e.userName,
                        e.password, e.url, e.uniqueID, e.certAlias, e.networkZone,
                        e.requiresLogin, e.useProxy, e.autoIndex,
                        e.savePassword, prev.sessionCache);
            }
            merged.add(e);
        }

        // Sync metadata into settings.passwordEntries so other components
        // (parseWikiSites, WikiSettingsPanel, etc.) can discover entries
        // even if they were created before this sync mechanism existed.
        syncMetadataToSettings(merged);

        return merged;
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
            meta.certAlias = e.certAlias;
            meta.networkZone = e.networkZone;
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
        meta.certAlias = entry.certAlias;
        meta.networkZone = entry.networkZone;
        meta.requiresLogin = entry.requiresLogin;
        meta.useProxy = entry.useProxy;
        meta.autoIndex = entry.autoIndex;
        meta.savePassword = entry.savePassword;
        meta.sessionCache = entry.sessionCache;
        settings.passwordEntries.add(meta);
        SettingsHelper.save(settings);

        if (entry.savePassword) {
            // Persist credentials to disk (encrypted in componentCredentials)
            CredentialStore.store(PWD_PREFIX + entry.title, entry.userName, entry.password);
        } else {
            // savePassword is OFF — persist only the username (with empty password)
            // so other components can still resolve the username for dialog prefill.
            // The actual password is NEVER persisted to disk.
            CredentialStore.store(PWD_PREFIX + entry.title, entry.userName, "");

            if (entry.sessionCache && entry.password != null && !entry.password.isEmpty()) {
                // Store in RAM-only session cache (lost on app exit)
                CredentialStore.storeInSession(PWD_PREFIX + entry.title, entry.userName, entry.password);
            }
        }
    }

    private static void saveToKeePass(KeePassEntry entry) {
        if (entry.savePassword) {
            // Persist credentials (user+password) to KeePass
            CredentialStore.updateKeePassEntry(
                    entry.title, entry.userName, entry.password, entry.url,
                    entry.displayName, entry.category,
                    entry.requiresLogin, entry.useProxy, entry.autoIndex,
                    entry.certAlias, entry.savePassword, entry.sessionCache);
        } else {
            // Save metadata to KeePass but with empty password
            CredentialStore.updateKeePassEntry(
                    entry.title, entry.userName, "", entry.url,
                    entry.displayName, entry.category,
                    entry.requiresLogin, entry.useProxy, entry.autoIndex,
                    entry.certAlias, entry.savePassword, entry.sessionCache);

            if (entry.sessionCache && entry.password != null && !entry.password.isEmpty()) {
                // Store in RAM-only session cache (lost on app exit)
                CredentialStore.storeInSession(PWD_PREFIX + entry.title, entry.userName, entry.password);
            }
        }

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
        meta.certAlias = entry.certAlias;
        meta.networkZone = entry.networkZone;
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
            String mmCertAlias = "";
            String mmNetworkZone = "EXTERN";

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
                else if (line.startsWith("MM_CertAlias: "))       mmCertAlias = line.substring(14).trim();
                else if (line.startsWith("MM_NetworkZone: "))     mmNetworkZone = line.substring(16).trim();
            }

            if (!title.isEmpty()) {
                entries.add(new KeePassEntry(
                        !mmCat.isEmpty() ? mmCat : "Mainframe",
                        title,
                        !mmDisp.isEmpty() ? mmDisp : title,
                        userName, password, url, uniqueID,
                        mmCertAlias,
                        mmNetworkZone,
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
        final String certAlias;     // Windows certificate alias (Confluence)
        final String networkZone;   // "INTERN" or "EXTERN"
        final boolean requiresLogin;
        final boolean useProxy;
        final boolean autoIndex;
        final boolean savePassword;
        final boolean sessionCache;

        KeePassEntry(String category, String title, String displayName,
                     String userName, String password, String url, String uniqueID,
                     boolean requiresLogin, boolean useProxy, boolean autoIndex,
                     boolean savePassword, boolean sessionCache) {
            this(category, title, displayName, userName, password, url, uniqueID, "",
                    "EXTERN", requiresLogin, useProxy, autoIndex, savePassword, sessionCache);
        }

        KeePassEntry(String category, String title, String displayName,
                     String userName, String password, String url, String uniqueID,
                     String certAlias,
                     boolean requiresLogin, boolean useProxy, boolean autoIndex,
                     boolean savePassword, boolean sessionCache) {
            this(category, title, displayName, userName, password, url, uniqueID, certAlias,
                    "EXTERN", requiresLogin, useProxy, autoIndex, savePassword, sessionCache);
        }

        KeePassEntry(String category, String title, String displayName,
                     String userName, String password, String url, String uniqueID,
                     String certAlias, String networkZone,
                     boolean requiresLogin, boolean useProxy, boolean autoIndex,
                     boolean savePassword, boolean sessionCache) {
            this.category = category;
            this.title = title;
            this.displayName = displayName;
            this.userName = userName;
            this.password = password;
            this.url = url;
            this.uniqueID = uniqueID;
            this.certAlias = certAlias != null ? certAlias : "";
            this.networkZone = networkZone != null ? networkZone : "EXTERN";
            this.requiresLogin = requiresLogin;
            this.useProxy = useProxy;
            this.autoIndex = autoIndex;
            this.savePassword = savePassword;
            this.sessionCache = sessionCache;
        }
    }
}

