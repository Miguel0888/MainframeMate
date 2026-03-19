package de.bund.zrb.ui.commands;

import de.bund.zrb.confluence.ConfluenceConnectionConfig;
import de.bund.zrb.confluence.ConfluenceRestClient;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.ConfluenceConnectionTab;
import de.bund.zrb.ui.TabbedPaneManager;
import de.bund.zrb.util.CredentialStore;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Opens a Confluence connection tab.
 * <p>
 * Reads Confluence entries (category "Confluence") from the password entries,
 * builds a {@link ConfluenceRestClient} with mTLS + Basic Auth, and opens
 * a browsing tab.
 */
public class OpenConfluenceMenuCommand extends ShortcutMenuCommand {

    private static final Logger LOG = Logger.getLogger(OpenConfluenceMenuCommand.class.getName());
    private final TabbedPaneManager tabManager;

    public OpenConfluenceMenuCommand(TabbedPaneManager tabManager) {
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "connection.confluence";
    }

    @Override
    public String getLabel() {
        return "Confluence\u2026";
    }

    @Override
    public void perform() {
        // ── 1. Find Confluence entries ──
        List<ConfluenceEntry> confluenceEntries = findConfluenceEntries();

        if (confluenceEntries.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "Keine Confluence-Eintr\u00e4ge konfiguriert.\n\n"
                            + "Bitte unter Einstellungen \u2192 Passw\u00f6rter mindestens einen\n"
                            + "Eintrag mit Kategorie \"Confluence\", URL und Zertifikat anlegen.",
                    "Keine Confluence-Konfiguration", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ── 2. If multiple entries, let user choose ──
        ConfluenceEntry selected;
        if (confluenceEntries.size() == 1) {
            selected = confluenceEntries.get(0);
        } else {
            String[] names = new String[confluenceEntries.size()];
            for (int i = 0; i < confluenceEntries.size(); i++) {
                names[i] = confluenceEntries.get(i).displayName
                        + " (" + confluenceEntries.get(i).url + ")";
            }
            String choice = (String) JOptionPane.showInputDialog(null,
                    "Welche Confluence-Instanz soll ge\u00f6ffnet werden?",
                    "Confluence ausw\u00e4hlen",
                    JOptionPane.QUESTION_MESSAGE, null, names, names[0]);
            if (choice == null) return;
            int idx = 0;
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(choice)) { idx = i; break; }
            }
            selected = confluenceEntries.get(idx);
        }

        // ── 3. Resolve credentials ──
        String user = selected.userName;
        String pass = selected.password;
        if ((user == null || user.isEmpty()) && (pass == null || pass.isEmpty())) {
            try {
                String[] creds = CredentialStore.resolveIncludingEmpty("pwd:" + selected.id);
                if (creds != null) {
                    user = creds[0];
                    pass = creds[1];
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[Confluence] Credentials aufl\u00f6sen fehlgeschlagen", e);
            }
        }

        if (user == null || user.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "Kein Benutzername f\u00fcr den Confluence-Eintrag hinterlegt.\n"
                            + "Bitte unter Einstellungen \u2192 Passw\u00f6rter vervollst\u00e4ndigen.",
                    "Fehlende Zugangsdaten", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ── 4. Build client + open tab ──
        try {
            ConfluenceConnectionConfig config = new ConfluenceConnectionConfig(
                    selected.url, user, pass, selected.certAlias, 15000, 30000);
            ConfluenceRestClient client = new ConfluenceRestClient(config);

            ConfluenceConnectionTab tab = new ConfluenceConnectionTab(client, selected.url);
            tabManager.addTab(tab);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Confluence] Tab \u00f6ffnen fehlgeschlagen", e);
            // Build readable error chain
            StringBuilder detail = new StringBuilder();
            Throwable current = e;
            int depth = 0;
            while (current != null && depth < 5) {
                if (depth > 0) detail.append("\n  \u2190 ");
                detail.append(current.getClass().getSimpleName());
                if (current.getMessage() != null) {
                    detail.append(": ").append(current.getMessage());
                }
                current = current.getCause();
                depth++;
            }
            JOptionPane.showMessageDialog(null,
                    "Confluence-Verbindung fehlgeschlagen:\n\n" + detail.toString(),
                    "Verbindungsfehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Read all password entries with category "Confluence" from settings.
     */
    private List<ConfluenceEntry> findConfluenceEntries() {
        Settings settings = SettingsHelper.load();
        List<ConfluenceEntry> result = new ArrayList<ConfluenceEntry>();
        for (Settings.PasswordEntryMeta meta : settings.passwordEntries) {
            if ("Confluence".equalsIgnoreCase(meta.category)) {
                String user = "";
                String pass = "";
                try {
                    String[] cred = CredentialStore.resolveIncludingEmpty("pwd:" + meta.id);
                    if (cred != null) {
                        user = cred[0];
                        pass = cred[1];
                    }
                } catch (Exception ignore) { }
                result.add(new ConfluenceEntry(
                        meta.id,
                        meta.displayName != null ? meta.displayName : meta.id,
                        meta.url != null ? meta.url : "",
                        meta.certAlias != null ? meta.certAlias : "",
                        user, pass));
            }
        }
        return result;
    }

    private static final class ConfluenceEntry {
        final String id;
        final String displayName;
        final String url;
        final String certAlias;
        final String userName;
        final String password;

        ConfluenceEntry(String id, String displayName, String url,
                        String certAlias, String userName, String password) {
            this.id = id;
            this.displayName = displayName;
            this.url = url;
            this.certAlias = certAlias;
            this.userName = userName;
            this.password = password;
        }
    }
}
