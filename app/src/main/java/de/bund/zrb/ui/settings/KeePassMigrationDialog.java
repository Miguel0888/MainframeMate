package de.bund.zrb.ui.settings;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.util.PasswordMethod;
import de.bund.zrb.util.WindowsCryptoUtil;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dialog shown when the user confirms (OK / Apply) a switch to KeePass.
 * <p>
 * Explains prerequisites, asks whether to migrate existing credentials,
 * and performs the migration if confirmed. The caller can check
 * {@link #wasConfirmed()} to decide whether to keep the KeePass selection.
 * <p>
 * The dialog is intentionally <em>not</em> shown when the combo box
 * selection changes, but only at apply-time — so the user can first
 * configure KeePass settings (paths, RPC, pairing) before migration runs.
 */
public final class KeePassMigrationDialog {

    private static final Logger LOG = Logger.getLogger(KeePassMigrationDialog.class.getName());

    private final Component parent;
    private final PasswordMethod previousMethod;
    private boolean confirmed;

    /**
     * @param parent         owner component for dialog centering
     * @param previousMethod the method the user is switching <em>from</em>
     */
    public KeePassMigrationDialog(Component parent, PasswordMethod previousMethod) {
        this.parent = parent;
        this.previousMethod = previousMethod;
    }

    /**
     * Show the dialog and (optionally) perform migration.
     *
     * @return {@code true} if the user confirmed the switch to KeePass,
     *         {@code false} if cancelled (the caller should revert the combo box)
     */
    public boolean showAndMigrate() {
        // ── Step 1: inform & ask ────────────────────────────────────
        String html = buildInfoHtml();
        JEditorPane info = new JEditorPane("text/html", html);
        info.setEditable(false);
        info.setOpaque(false);
        info.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        info.setFont(UIManager.getFont("Label.font"));

        JScrollPane scroll = new JScrollPane(info);
        scroll.setPreferredSize(new Dimension(560, 340));
        scroll.setBorder(null);

        Object[] options = {"Migrieren und wechseln", "Ohne Migration wechseln", "Abbrechen"};
        int choice = JOptionPane.showOptionDialog(
                parent,
                scroll,
                "Wechsel zu KeePass",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[2]  // default = Abbrechen
        );

        if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
            // Cancelled
            confirmed = false;
            return false;
        }

        if (choice == 1) {
            // Switch without migration
            confirmed = true;
            return true;
        }

        // ── Step 2: migrate ─────────────────────────────────────────
        confirmed = true;
        performMigration();
        return true;
    }

    public boolean wasConfirmed() {
        return confirmed;
    }

    // ── private helpers ─────────────────────────────────────────────

    private String buildInfoHtml() {
        return "<html><body style='width:520px; font-family:sans-serif;'>"
                + "<h2 style='margin-top:0;'>Wechsel zu KeePass</h2>"
                + "<p>Sie wechseln die Passwort-Verschlüsselung von <b>"
                + previousMethod.getDisplayName() + "</b> auf <b>KeePass</b>.</p>"

                + "<h3>⚠ Warum ist eine Migration notwendig?</h3>"
                + "<p>Ihre bestehenden Passwörter sind aktuell mit <b>"
                + previousMethod.getDisplayName() + "</b> verschlüsselt und in der lokalen "
                + "Konfigurationsdatei (<code>settings.json</code>) gespeichert. "
                + "KeePass verwaltet Passwörter in einer separaten <code>.kdbx</code>-Datenbank. "
                + "Ohne Migration gehen Ihre gespeicherten Zugangsdaten verloren, da MainframeMate "
                + "sie nach dem Wechsel nicht mehr entschlüsseln kann.</p>"

                + "<h3>📋 Voraussetzungen</h3>"
                + "<ul>"
                + "<li><b>KeePass 2.x</b> muss installiert und <b>gestartet</b> sein.</li>"
                + "<li>Die KeePass-Datenbank muss <b>entsperrt</b> (geöffnet) sein.</li>"
                + "<li><b>Empfehlung:</b> Richten Sie die Datenbank so ein, dass sie "
                + "<em>ohne Master-Passwort</em> funktioniert — nur an Ihr <b>Windows-Benutzerkonto</b> "
                + "gebunden (<em>Windows User Account</em> als Schlüsselquelle). "
                + "Damit wird die Datenbank beim Windows-Login automatisch entsperrt.</li>"
                + "<li>Es muss eine <b>Datenbank eingerichtet und geöffnet</b> sein "
                + "(<code>.kdbx</code>-Datei).</li>"
                + "</ul>"

                + "<h3>🔄 Was wird migriert?</h3>"
                + "<p>Alle in MainframeMate gespeicherten Passwörter (FTP, Wiki, BetaView usw.) "
                + "werden entschlüsselt und in die KeePass-Datenbank übertragen. "
                + "Anschließend werden die lokalen Einträge durch KeePass-Verweise ersetzt.</p>"

                + "<p style='color:#888; font-size:0.9em;'>Sie können diesen Vorgang auch abbrechen "
                + "und später manuell migrieren.</p>"
                + "</body></html>";
    }

    /**
     * Decrypt every credential with the <em>old</em> method and re-encrypt into KeePass.
     * Shows a progress dialog and a summary at the end.
     */
    private void performMigration() {
        Settings settings = SettingsHelper.load();
        Map<String, String> credentials = new LinkedHashMap<String, String>(settings.componentCredentials);

        // Also consider the legacy encryptedPassword field
        String legacyEncrypted = settings.encryptedPassword;
        boolean hasLegacy = legacyEncrypted != null && !legacyEncrypted.isEmpty()
                && !legacyEncrypted.startsWith("keepass:");

        int total = credentials.size() + (hasLegacy ? 1 : 0);
        if (total == 0) {
            JOptionPane.showMessageDialog(parent,
                    "Es sind keine gespeicherten Passwörter vorhanden — nichts zu migrieren.",
                    "Migration", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Decrypt with old method (settings still has old passwordMethod)
        int migrated = 0;
        int failed = 0;
        StringBuilder errors = new StringBuilder();

        // ── Migrate componentCredentials ────────────────────────────
        Map<String, String> decrypted = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : credentials.entrySet()) {
            try {
                String plain = WindowsCryptoUtil.decrypt(entry.getValue());
                decrypted.put(entry.getKey(), plain);
            } catch (Exception e) {
                failed++;
                errors.append("• ").append(entry.getKey()).append(": ").append(e.getMessage()).append("\n");
                LOG.log(Level.WARNING, "Migration failed for " + entry.getKey(), e);
            }
        }

        // Decrypt legacy password
        String legacyPlain = null;
        if (hasLegacy) {
            try {
                legacyPlain = WindowsCryptoUtil.decrypt(legacyEncrypted);
            } catch (Exception e) {
                failed++;
                errors.append("• Hauptpasswort: ").append(e.getMessage()).append("\n");
                LOG.log(Level.WARNING, "Migration failed for legacy password", e);
            }
        }

        // Now switch to KeePass method and re-encrypt
        settings.passwordMethod = PasswordMethod.KEEPASS.name();
        SettingsHelper.save(settings);

        for (Map.Entry<String, String> entry : decrypted.entrySet()) {
            try {
                String encrypted = WindowsCryptoUtil.encrypt(entry.getValue());
                settings.componentCredentials.put(entry.getKey(), encrypted);
                migrated++;
            } catch (Exception e) {
                failed++;
                errors.append("• ").append(entry.getKey()).append(" (KeePass-Speichern): ")
                        .append(e.getMessage()).append("\n");
                LOG.log(Level.WARNING, "KeePass store failed for " + entry.getKey(), e);
            }
        }

        if (legacyPlain != null) {
            try {
                String encrypted = WindowsCryptoUtil.encrypt(legacyPlain);
                settings.encryptedPassword = encrypted;
                migrated++;
            } catch (Exception e) {
                failed++;
                errors.append("• Hauptpasswort (KeePass-Speichern): ").append(e.getMessage()).append("\n");
                LOG.log(Level.WARNING, "KeePass store failed for legacy password", e);
            }
        }

        SettingsHelper.save(settings);

        // ── Summary ─────────────────────────────────────────────────
        StringBuilder msg = new StringBuilder();
        msg.append("Migration abgeschlossen.\n\n");
        msg.append("✅ Erfolgreich migriert: ").append(migrated).append("\n");
        if (failed > 0) {
            msg.append("❌ Fehlgeschlagen: ").append(failed).append("\n\n");
            msg.append("Details:\n").append(errors);
        }

        JOptionPane.showMessageDialog(parent, msg.toString(),
                "KeePass-Migration",
                failed > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
    }
}

