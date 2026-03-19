package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.sharepoint.SharePointLinkFetcher;
import de.bund.zrb.sharepoint.SharePointSite;
import de.bund.zrb.sharepoint.SharePointSiteStore;
import de.bund.zrb.ui.settings.FormBuilder;
import de.bund.zrb.util.CredentialStore;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Settings panel for SharePoint connection configuration.
 * <p>
 * SharePoint sites are managed as password entries with category <b>SP</b>
 * in the central password dialog ({@code Einstellungen &rarr; Passw&ouml;rter}).
 * <p>
 * This panel provides:
 * <ol>
 *   <li>A "Parent page" URL + "Links abrufen" to auto-discover SP sites</li>
 *   <li>A checkbox table to select which discovered links are SP sites</li>
 *   <li>Cache concurrency setting</li>
 * </ol>
 * Fetched links are persisted both to {@code settings.sharepointSitesJson}
 * (legacy) and to {@code settings.passwordEntries} (category "SP") so the
 * password dialog can manage them.
 */
public class SharePointSettingsPanel extends AbstractSettingsPanel {

    private static final Logger LOG = Logger.getLogger(SharePointSettingsPanel.class.getName());

    private final JTextField parentPageUrlField;
    private final JSpinner cacheConcurrencySpinner;
    private final LinkTableModel linkTableModel;
    private final JTable linkTable;

    public SharePointSettingsPanel() {
        super("sharepoint", "SharePoint");
        FormBuilder fb = new FormBuilder();

        // ── Section 1: Info ──
        fb.addSection("SharePoint-Sites");
        fb.addInfo("<html>SharePoint-Sites werden \u00fcber <b>Einstellungen \u2192 Passw\u00f6rter</b> verwaltet.<br>"
                + "Dort k\u00f6nnen Sie SP-Eintr\u00e4ge (Kategorie <i>SP</i>) hinzuf\u00fcgen,<br>"
                + "bearbeiten, Zugangsdaten hinterlegen und SSO testen.<br><br>"
                + "Alternativ k\u00f6nnen Sie unten eine <b>Parent-Seite</b> angeben,<br>"
                + "von der SharePoint-Links automatisch erkannt werden.</html>");

        // ── Section 2: Parent page ──
        fb.addSection("Parent-Seite (optional)");

        parentPageUrlField = new JTextField(
                settings.sharepointParentPageUrl != null ? settings.sharepointParentPageUrl : "", 40);
        parentPageUrlField.setToolTipText(
                "URL der Seite, auf der Links zu Ihren SharePoint-Sites aufgelistet sind.");
        fb.addRow("Parent-URL:", parentPageUrlField);

        JButton fetchButton = new JButton("\uD83D\uDD17 Links abrufen\u2026");
        fetchButton.setToolTipText("Alle Links von der Parent-Seite laden (PowerShell/SSO)");
        fetchButton.addActionListener(e -> fetchLinks());
        
        JButton browserFetchBtn = new JButton("\uD83C\uDF10 Per Browser abrufen\u2026");
        browserFetchBtn.setToolTipText("Links per echtem Browser abrufen (SSO funktioniert automatisch)");
        browserFetchBtn.addActionListener(e -> fetchLinksViaBrowser());

        JPanel fetchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        fetchPanel.add(fetchButton);
        fetchPanel.add(browserFetchBtn);
        fb.addRow("", fetchPanel);

        fb.addInfo("<html><i>Geben Sie die URL einer internen Seite an (z.B. Intranet/Wiki),<br>"
                + "die Links zu Ihren SharePoint-Sites enth\u00e4lt. "
                + "Klicken Sie dann auf <b>Links abrufen</b>,<br>"
                + "um alle dort gelisteten URLs zu erkennen.</i></html>");

        // ── Section 3: Link checklist ──
        fb.addSection("Erkannte Links");

        fb.addInfo("<html><i>Haken Sie die Links an, die echte SharePoint-Sites sind.<br>"
                + "Die ausgew\u00e4hlten Sites werden als <b>SP</b>-Eintr\u00e4ge unter Passw\u00f6rter gespeichert<br>"
                + "und sind im SharePoint-Tab wie ein lokales Dateisystem navigierbar.</i></html>");

        List<SharePointSite> existingSites = SharePointSiteStore.fromJson(settings.sharepointSitesJson);
        linkTableModel = new LinkTableModel(existingSites);
        linkTable = new JTable(linkTableModel);
        linkTable.getColumnModel().getColumn(0).setMaxWidth(30);
        linkTable.getColumnModel().getColumn(0).setMinWidth(30);
        linkTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        linkTable.getColumnModel().getColumn(2).setPreferredWidth(400);
        linkTable.setRowHeight(22);

        JScrollPane tableScroll = new JScrollPane(linkTable);
        tableScroll.setPreferredSize(new Dimension(640, 200));
        fb.addWideGrow(tableScroll);

        // ── Section 4: Caching ──
        fb.addSection("Caching");

        cacheConcurrencySpinner = new JSpinner(new SpinnerNumberModel(
                settings.sharepointCacheConcurrency, 1, 8, 1));
        cacheConcurrencySpinner.setToolTipText("Anzahl paralleler Downloads beim Caching");
        fb.addRow("Parallele Downloads:", cacheConcurrencySpinner);

        fb.addInfo("<html><i>Beim Durchsuchen einer SharePoint-Site werden Dokumente automatisch<br>"
                + "im lokalen Cache (H2 + Lucene) gespeichert und sind \u00fcber<br>"
                + "<b>\u00dcberall suchen</b> mit dem K\u00fcrzel <b>SP</b> auffindbar.</i></html>");

        installPanel(fb);
    }

    /**
     * Fetch links from the configured parent page and merge them
     * into the existing checklist (preserving selection state).
     */
    private void fetchLinks() {
        String url = parentPageUrlField.getText().trim();
        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(linkTable, "Bitte zuerst eine Parent-URL eingeben.",
                    "SharePoint", JOptionPane.WARNING_MESSAGE);
            return;
        }

        linkTable.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new SwingWorker<List<SharePointSite>, Void>() {
            @Override
            protected List<SharePointSite> doInBackground() throws Exception {
                // Try SSO first (no explicit credentials)
                return SharePointLinkFetcher.fetchLinks(url, "", "");
            }

            @Override
            protected void done() {
                linkTable.setCursor(Cursor.getDefaultCursor());
                try {
                    List<SharePointSite> fetched = get();
                    linkTableModel.mergeLinks(fetched);
                    JOptionPane.showMessageDialog(linkTable,
                            fetched.size() + " Links gefunden.\n"
                                    + "Bitte die SharePoint-Sites anhaken und Einstellungen speichern.\n\n"
                                    + "Die ausgew\u00e4hlten Sites werden als SP-Eintr\u00e4ge\n"
                                    + "unter Einstellungen \u2192 Passw\u00f6rter gespeichert.",
                            "Links abgerufen", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    JOptionPane.showMessageDialog(linkTable,
                            "Fehler beim Abrufen der Links:\n" + msg
                                    + "\n\nSie k\u00f6nnen SharePoint-Sites auch manuell\n"
                                    + "unter Einstellungen \u2192 Passw\u00f6rter (Kategorie SP) anlegen.",
                            "SharePoint-Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Fetch links using the real browser (WebDriver BiDi).
     * The browser inherits Windows SSO, so authentication works automatically.
     */
    private void fetchLinksViaBrowser() {
        String url = parentPageUrlField.getText().trim();
        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(linkTable, "Bitte zuerst eine Parent-URL eingeben.",
                    "SharePoint", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Find BrowserService via the settings dialog's parent hierarchy
        de.zrb.bund.newApi.browser.BrowserService browserService = findBrowserService();
        if (browserService == null) {
            JOptionPane.showMessageDialog(linkTable,
                    "Browser-Service ist nicht verf\u00fcgbar.\n\n"
                            + "Bitte konfigurieren Sie den Browser unter\n"
                            + "Einstellungen \u2192 Plugin-Einstellungen \u2192 Websearch.",
                    "Browser nicht verf\u00fcgbar", JOptionPane.ERROR_MESSAGE);
            return;
        }

        linkTable.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        final de.zrb.bund.newApi.browser.BrowserService bs = browserService;
        final String targetUrl = url;

        new SwingWorker<List<SharePointSite>, Void>() {
            @Override
            protected List<SharePointSite> doInBackground() throws Exception {
                return SharePointLinkFetcher.fetchLinksViaBrowser(bs, targetUrl);
            }

            @Override
            protected void done() {
                linkTable.setCursor(Cursor.getDefaultCursor());
                try {
                    List<SharePointSite> fetched = get();
                    linkTableModel.mergeLinks(fetched);
                    JOptionPane.showMessageDialog(linkTable,
                            fetched.size() + " Links per Browser gefunden.\n"
                                    + "Bitte die SharePoint-Sites anhaken und Einstellungen speichern.\n\n"
                                    + "Die ausgew\u00e4hlten Sites werden als SP-Eintr\u00e4ge\n"
                                    + "unter Einstellungen \u2192 Passw\u00f6rter gespeichert.",
                            "Links abgerufen (Browser)", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    JOptionPane.showMessageDialog(linkTable,
                            "Fehler beim Abrufen per Browser:\n" + msg,
                            "Browser-Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Try to locate the BrowserService by traversing the parent component hierarchy
     * to find a MainframeContext (typically MainFrame).
     */
    private de.zrb.bund.newApi.browser.BrowserService findBrowserService() {
        // Walk up the component tree to find the JFrame that implements MainframeContext
        Container c = linkTable;
        while (c != null) {
            if (c instanceof de.zrb.bund.api.MainframeContext) {
                return ((de.zrb.bund.api.MainframeContext) c).getBrowserService();
            }
            c = c.getParent();
            // Also check Window owner
            if (c == null && linkTable != null) {
                Window w = SwingUtilities.getWindowAncestor(linkTable);
                if (w instanceof JDialog) {
                    Window owner = ((JDialog) w).getOwner();
                    if (owner instanceof de.zrb.bund.api.MainframeContext) {
                        return ((de.zrb.bund.api.MainframeContext) owner).getBrowserService();
                    }
                }
                break;
            }
        }
        return null;
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.sharepointParentPageUrl = parentPageUrlField.getText().trim();
        s.sharepointCacheConcurrency = ((Number) cacheConcurrencySpinner.getValue()).intValue();

        List<SharePointSite> sites = linkTableModel.getSites();
        s.sharepointSitesJson = SharePointSiteStore.toJson(sites);

        // ── Sync selected sites into passwordEntries (category "SP") ──
        syncSitesToPasswordEntries(s, sites);
    }

    /**
     * Create / update SP password entries for each selected site.
     * Non-selected sites are removed from password entries.
     */
    private void syncSitesToPasswordEntries(Settings s, List<SharePointSite> sites) {
        // Collect IDs of SP entries coming from this sync
        java.util.Set<String> syncedIds = new java.util.HashSet<String>();

        for (SharePointSite site : sites) {
            String id = toSiteId(site);
            if (site.isSelected()) {
                syncedIds.add(id);

                // Check if the entry already exists
                boolean exists = false;
                for (Settings.PasswordEntryMeta meta : s.passwordEntries) {
                    if (id.equals(meta.id) && "SP".equals(meta.category)) {
                        // Update URL / displayName if changed
                        meta.url = site.getUrl();
                        if (meta.displayName == null || meta.displayName.isEmpty()) {
                            meta.displayName = site.getName();
                        }
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    Settings.PasswordEntryMeta meta = new Settings.PasswordEntryMeta();
                    meta.id = id;
                    meta.category = "SP";
                    meta.displayName = site.getName();
                    meta.url = site.getUrl();
                    meta.requiresLogin = false;  // SSO by default
                    meta.useProxy = false;
                    meta.autoIndex = false;
                    meta.savePassword = false;
                    meta.sessionCache = false;
                    s.passwordEntries.add(meta);
                    LOG.info("[SP-Settings] Created SP password entry: " + id + " -> " + site.getUrl());
                }
            }
        }

        // Remove SP entries that were de-selected in the table
        // (only those that match a known site from this table — leave manually added entries alone)
        java.util.Set<String> knownTableIds = new java.util.HashSet<String>();
        for (SharePointSite site : sites) {
            knownTableIds.add(toSiteId(site));
        }
        Iterator<Settings.PasswordEntryMeta> it = s.passwordEntries.iterator();
        while (it.hasNext()) {
            Settings.PasswordEntryMeta meta = it.next();
            if ("SP".equals(meta.category)
                    && knownTableIds.contains(meta.id)
                    && !syncedIds.contains(meta.id)) {
                LOG.info("[SP-Settings] Removing de-selected SP entry: " + meta.id);
                it.remove();
            }
        }
    }

    /**
     * Derive a stable ID from a SharePoint site URL.
     * Uses the host + path slug, e.g. "sp_myorg_sharepoint_com_sites_TeamSite".
     */
    public static String toSiteId(SharePointSite site) {
        String url = site.getUrl();
        if (url == null || url.isEmpty()) return "sp_unknown";
        try {
            java.net.URL parsed = new java.net.URL(url);
            String id = "sp_" + parsed.getHost().replace('.', '_');
            String path = parsed.getPath();
            if (path != null && !path.isEmpty() && !"/".equals(path)) {
                id += path.replace('/', '_').replace(' ', '_').replace("%20", "_");
                // Remove trailing underscore
                while (id.endsWith("_")) id = id.substring(0, id.length() - 1);
            }
            return id;
        } catch (Exception e) {
            return "sp_" + url.hashCode();
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    // ═══════════════════════════════════════════════════════════
    //  Table model for the link checklist
    // ═══════════════════════════════════════════════════════════

    private static class LinkTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"\u2713", "Name", "URL"};
        private final List<SharePointSite> sites;

        LinkTableModel(List<SharePointSite> initial) {
            this.sites = new ArrayList<SharePointSite>(initial != null ? initial : new ArrayList<SharePointSite>());
        }

        List<SharePointSite> getSites() {
            return sites;
        }

        /**
         * Merge newly fetched links into the existing list.
         * Preserves the selection state of links that were already present.
         */
        void mergeLinks(List<SharePointSite> fetched) {
            java.util.Map<String, Boolean> existing = new java.util.LinkedHashMap<String, Boolean>();
            for (SharePointSite s : sites) {
                existing.put(s.getUrl(), s.isSelected());
            }

            sites.clear();
            java.util.Set<String> seen = new java.util.HashSet<String>();
            for (SharePointSite f : fetched) {
                Boolean wasSelected = existing.get(f.getUrl());
                if (wasSelected != null) {
                    f.setSelected(wasSelected);
                }
                sites.add(f);
                seen.add(f.getUrl());
            }
            // Keep previously-selected entries that are no longer in the fetched list
            for (java.util.Map.Entry<String, Boolean> e : existing.entrySet()) {
                if (!seen.contains(e.getKey()) && e.getValue()) {
                    sites.add(new SharePointSite(e.getKey(), e.getKey(), true));
                }
            }

            fireTableDataChanged();
        }

        @Override public int getRowCount() { return sites.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Class<?> getColumnClass(int col) {
            return col == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col == 0;
        }

        @Override
        public Object getValueAt(int row, int col) {
            SharePointSite s = sites.get(row);
            switch (col) {
                case 0: return s.isSelected();
                case 1: return s.getName();
                case 2: return s.getUrl();
                default: return "";
            }
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col == 0 && value instanceof Boolean) {
                sites.get(row).setSelected((Boolean) value);
                fireTableCellUpdated(row, col);
            }
        }
    }
}
