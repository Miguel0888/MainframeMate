package de.bund.zrb.ui.commands;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.util.CredentialStore;
import de.bund.zrb.util.KeePassNotAvailableException;
import de.bund.zrb.util.PasswordMethod;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Menu command under <em>Hilfe → Passwörter (KeePass)</em>.
 * <p>
 * If the password method is set to {@link PasswordMethod#KEEPASS},
 * lists all entries from the configured KeePass database via KPScript.
 * Otherwise shows a hint that KeePass must be configured first.
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
                            + "die Passwort-Methode <b>KeePass</b> und geben Sie den Pfad zu "
                            + "<code>KPScript.exe</code> sowie zur <code>.kdbx</code>-Datenbank an.</p>"
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
        List<KeePassEntry> entries = parseEntries(rawOutput);

        if (entries.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "Keine Einträge in der KeePass-Datenbank gefunden.",
                    "KeePass – Passwörter", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Build table model
        String[] columns = {"Titel", "Benutzername", "URL", "Passwort"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        for (KeePassEntry entry : entries) {
            model.addRow(new Object[]{entry.title, entry.userName, entry.url, "••••••••"});
        }

        JTable table = new JTable(model);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setPreferredWidth(180);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(250);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(750, 350));

        // Toggle password visibility
        JCheckBox showPasswords = new JCheckBox("Passwörter anzeigen");
        showPasswords.addActionListener(e -> {
            boolean show = showPasswords.isSelected();
            for (int row = 0; row < entries.size(); row++) {
                model.setValueAt(show ? entries.get(row).password : "••••••••", row, 3);
            }
        });

        // Copy password button
        JButton copyBtn = new JButton("📋 Passwort kopieren");
        copyBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(parent, "Bitte zuerst einen Eintrag auswählen.",
                        "Kein Eintrag", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int modelRow = table.convertRowIndexToModel(row);
            String pw = entries.get(modelRow).password;
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(pw), null);
            JOptionPane.showMessageDialog(parent,
                    "Passwort für \"" + entries.get(modelRow).title + "\" in die Zwischenablage kopiert.",
                    "Kopiert", JOptionPane.INFORMATION_MESSAGE);
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonPanel.add(showPasswords);
        buttonPanel.add(copyBtn);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 8));
        Settings s = SettingsHelper.load();
        mainPanel.add(new JLabel("KeePass-Datenbank: " + s.keepassDatabasePath + "  (" + entries.size() + " Einträge)"),
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
    //  Parsing
    // ═══════════════════════════════════════════════════════════

    /**
     * Parses the raw KPScript {@code -c:ListEntries} output.
     * <p>
     * Expected format per entry block (separated by blank lines):
     * <pre>
     * Title: MyEntry
     * UserName: myuser
     * Password: secret
     * URL: https://example.com
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

            for (String line : block.split("\\n")) {
                line = line.trim();
                if (line.startsWith("Title: "))    title    = line.substring("Title: ".length()).trim();
                else if (line.startsWith("UserName: ")) userName = line.substring("UserName: ".length()).trim();
                else if (line.startsWith("Password: ")) password = line.substring("Password: ".length()).trim();
                else if (line.startsWith("URL: "))      url      = line.substring("URL: ".length()).trim();
            }

            // Only add entries with a title (skip header/empty blocks)
            if (!title.isEmpty()) {
                entries.add(new KeePassEntry(title, userName, password, url));
            }
        }

        return entries;
    }

    private static class KeePassEntry {
        final String title;
        final String userName;
        final String password;
        final String url;

        KeePassEntry(String title, String userName, String password, String url) {
            this.title = title;
            this.userName = userName;
            this.password = password;
            this.url = url;
        }
    }
}

