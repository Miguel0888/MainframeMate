package de.bund.zrb.confluence;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dialog for browsing and selecting a client certificate alias
 * from the Windows {@code Windows-MY} certificate store.
 * <p>
 * Shows a table with Alias, Subject, Issuer, Valid Until, and SHA-1 fingerprint.
 * Only certificates that are currently valid (not expired, not yet valid) are
 * shown by default, with a toggle to show all.
 */
public final class CertificateChooser {

    private static final Logger LOG = Logger.getLogger(CertificateChooser.class.getName());

    /** Certificate metadata row. */
    public static final class CertInfo {
        public final String alias;
        public final String subject;
        public final String issuer;
        public final Date validUntil;
        public final String sha1;
        public final boolean valid;

        CertInfo(String alias, String subject, String issuer, Date validUntil, String sha1, boolean valid) {
            this.alias = alias;
            this.subject = subject;
            this.issuer = issuer;
            this.validUntil = validUntil;
            this.sha1 = sha1;
            this.valid = valid;
        }
    }

    private CertificateChooser() { /* utility */ }

    /**
     * Load all certificates from the Windows-MY store.
     */
    public static List<CertInfo> loadCertificates() {
        List<CertInfo> result = new ArrayList<CertInfo>();
        try {
            KeyStore ks = KeyStore.getInstance("Windows-MY");
            ks.load(null, null);
            Enumeration<String> aliases = ks.aliases();
            Date now = new Date();

            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate cert = ks.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    X509Certificate x = (X509Certificate) cert;
                    byte[] digest = MessageDigest.getInstance("SHA-1").digest(x.getEncoded());
                    StringBuilder sha1 = new StringBuilder();
                    for (int i = 0; i < digest.length; i++) {
                        if (i > 0) sha1.append(':');
                        sha1.append(String.format("%02X", digest[i]));
                    }
                    boolean valid = now.after(x.getNotBefore()) && now.before(x.getNotAfter());
                    result.add(new CertInfo(
                            alias,
                            x.getSubjectX500Principal().getName(),
                            x.getIssuerX500Principal().getName(),
                            x.getNotAfter(),
                            sha1.toString(),
                            valid));
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[CertChooser] Fehler beim Lesen des Zertifikatsspeichers", e);
        }
        return result;
    }

    /**
     * Show a modal dialog to select a certificate alias.
     *
     * @param parent       parent component for centering
     * @param currentAlias pre-selected alias (may be {@code null})
     * @return the selected alias, or {@code null} if cancelled
     */
    public static String showChooserDialog(Component parent, String currentAlias) {
        List<CertInfo> allCerts = loadCertificates();

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String[] columns = {"Alias", "Subject", "Gültig bis", "Status"};

        final DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        final List<CertInfo> validCerts = new ArrayList<CertInfo>();
        for (CertInfo c : allCerts) {
            if (c.valid) validCerts.add(c);
        }

        // Initially show only valid certs
        final boolean[] showAll = {false};
        final List<CertInfo> displayedCerts = new ArrayList<CertInfo>(validCerts);

        for (CertInfo c : displayedCerts) {
            model.addRow(new Object[]{
                    c.alias,
                    simplifySubject(c.subject),
                    fmt.format(c.validUntil),
                    c.valid ? "✅ Gültig" : "❌ Abgelaufen"
            });
        }

        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(1).setPreferredWidth(350);
        table.getColumnModel().getColumn(2).setPreferredWidth(140);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);

        // Pre-select current alias
        if (currentAlias != null && !currentAlias.isEmpty()) {
            for (int i = 0; i < displayedCerts.size(); i++) {
                if (currentAlias.equals(displayedCerts.get(i).alias)) {
                    table.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(780, 300));

        JCheckBox showAllCb = new JCheckBox("Auch abgelaufene Zertifikate anzeigen");
        showAllCb.addActionListener(e -> {
            showAll[0] = showAllCb.isSelected();
            model.setRowCount(0);
            displayedCerts.clear();
            List<CertInfo> source = showAll[0] ? allCerts : validCerts;
            displayedCerts.addAll(source);
            for (CertInfo c : displayedCerts) {
                model.addRow(new Object[]{
                        c.alias,
                        simplifySubject(c.subject),
                        fmt.format(c.validUntil),
                        c.valid ? "✅ Gültig" : "❌ Abgelaufen"
                });
            }
            // Re-select current alias
            if (currentAlias != null) {
                for (int i = 0; i < displayedCerts.size(); i++) {
                    if (currentAlias.equals(displayedCerts.get(i).alias)) {
                        table.setRowSelectionInterval(i, i);
                        break;
                    }
                }
            }
        });

        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.add(new JLabel("Zertifikat aus dem Windows-Zertifikatsspeicher auswählen:"), BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(showAllCb, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(parent, panel,
                "Zertifikat auswählen", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            int row = table.getSelectedRow();
            if (row >= 0 && row < displayedCerts.size()) {
                return displayedCerts.get(row).alias;
            }
        }
        return null;
    }

    /**
     * Extract the CN= part from a DN subject string for display.
     */
    private static String simplifySubject(String dn) {
        if (dn == null) return "";
        // Try to extract CN=...
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.toUpperCase().startsWith("CN=")) {
                return trimmed.substring(3).trim();
            }
        }
        // Fallback: truncate
        return dn.length() > 60 ? dn.substring(0, 60) + "…" : dn;
    }
}

