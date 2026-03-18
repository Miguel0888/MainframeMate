package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.settings.FormBuilder;
import de.bund.zrb.util.CredentialStore;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings panel for Wiki site configuration.
 * Wiki sites are managed centrally via the "Passwörter" dialog
 * ({@code Einstellungen → Passwörter}).
 * This panel shows a read-only overview and prefetch settings.
 */
public class WikiSettingsPanel extends AbstractSettingsPanel {

    private final WikiSiteTableModel tableModel;
    private final JTable siteTable;
    private final JSpinner prefetchMaxItemsSpinner;
    private final JSpinner prefetchConcurrencySpinner;
    private final JSpinner prefetchCacheMaxMbSpinner;


    public WikiSettingsPanel() {
        super("wiki", "Wiki");
        FormBuilder fb = new FormBuilder();

        fb.addSection("Wiki-Sites");
        fb.addInfo("<html>Wiki-Sites werden über <b>Einstellungen → Passwörter</b> verwaltet.<br>"
                + "Dort können Sie Wiki-Einträge (Kategorie <i>Wiki</i>) hinzufügen, "
                + "bearbeiten und Zugangsdaten hinterlegen.</html>");

        // Read-only table populated from password entries (category "Wiki")
        List<WikiSiteRow> rows = loadWikiSitesFromPasswordEntries();
        tableModel = new WikiSiteTableModel(rows);
        siteTable = new JTable(tableModel);
        siteTable.setRowHeight(24);
        siteTable.getColumnModel().getColumn(0).setPreferredWidth(100);  // ID
        siteTable.getColumnModel().getColumn(1).setPreferredWidth(150);  // Name
        siteTable.getColumnModel().getColumn(2).setPreferredWidth(280);  // URL
        siteTable.getColumnModel().getColumn(3).setPreferredWidth(60);   // Login
        siteTable.getColumnModel().getColumn(3).setMaxWidth(80);
        siteTable.getColumnModel().getColumn(4).setPreferredWidth(60);   // Proxy
        siteTable.getColumnModel().getColumn(4).setMaxWidth(80);
        siteTable.getColumnModel().getColumn(5).setPreferredWidth(80);   // Auto-Index
        siteTable.getColumnModel().getColumn(5).setMaxWidth(100);
        siteTable.getColumnModel().getColumn(6).setPreferredWidth(100);  // Benutzer
        siteTable.getColumnModel().getColumn(7).setPreferredWidth(60);   // Status
        siteTable.getColumnModel().getColumn(7).setMaxWidth(80);
        siteTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scroll = new JScrollPane(siteTable);
        scroll.setPreferredSize(new Dimension(600, 180));
        fb.addWideGrow(scroll);

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton openPwdBtn = new JButton("🔑 Im Passwörter-Dialog verwalten…");
        openPwdBtn.setToolTipText("Öffnet den zentralen Passwörter-Dialog (Einstellungen → Passwörter)");
        openPwdBtn.addActionListener(e -> {
            JOptionPane.showMessageDialog(
                    WikiSettingsPanel.this,
                    "Bitte öffnen Sie den Passwörter-Dialog über:\n\n"
                            + "  Einstellungen → Passwörter\n\n"
                            + "Dort können Sie Wiki-Einträge (Kategorie \"Wiki\") anlegen,\n"
                            + "bearbeiten und Zugangsdaten hinterlegen.",
                    "Passwörter verwalten",
                    JOptionPane.INFORMATION_MESSAGE);
        });
        JButton refreshBtn = new JButton("🔄 Aktualisieren");
        refreshBtn.setToolTipText("Tabelle neu laden aus dem Passwörter-Speicher");
        refreshBtn.addActionListener(e -> refreshTable());
        buttons.add(openPwdBtn);
        buttons.add(refreshBtn);
        fb.addWide(buttons);

        // ── Prefetch settings ──
        fb.addSection("Vorabladen (Prefetch)");

        prefetchMaxItemsSpinner = new JSpinner(new SpinnerNumberModel(
                settings.wikiPrefetchMaxItems, 1, 1000, 10));
        fb.addRow("Max. Seiten vorladen:", prefetchMaxItemsSpinner);

        prefetchConcurrencySpinner = new JSpinner(new SpinnerNumberModel(
                settings.wikiPrefetchConcurrency, 1, 8, 1));
        fb.addRow("Parallele Requests:", prefetchConcurrencySpinner);

        prefetchCacheMaxMbSpinner = new JSpinner(new SpinnerNumberModel(
                settings.wikiPrefetchCacheMaxMb, 1, 500, 10));
        fb.addRow("Max. Cache-Größe (MB):", prefetchCacheMaxMbSpinner);

        installPanel(fb);
    }

    private void refreshTable() {
        tableModel.setRows(loadWikiSitesFromPasswordEntries());
    }

    /**
     * Load wiki site descriptors from {@code settings.passwordEntries} (category "Wiki").
     * Falls back to legacy {@code settings.wikiSites} if no password entries exist.
     */
    private List<WikiSiteRow> loadWikiSitesFromPasswordEntries() {
        Settings s = SettingsHelper.load();
        List<WikiSiteRow> rows = new ArrayList<WikiSiteRow>();

        for (Settings.PasswordEntryMeta meta : s.passwordEntries) {
            if (!"Wiki".equals(meta.category)) continue;

            String user = "";
            boolean hasCreds = false;
            try {
                String[] cred = CredentialStore.resolveIncludingEmpty("pwd:" + meta.id);
                if (cred != null && !cred[0].isEmpty()) {
                    user = cred[0];
                    hasCreds = true;
                }
            } catch (Exception ignore) {
                // decryption failed
            }

            rows.add(new WikiSiteRow(
                    meta.id,
                    meta.displayName != null ? meta.displayName : meta.id,
                    meta.url != null ? meta.url : "",
                    meta.requiresLogin,
                    meta.useProxy,
                    meta.autoIndex,
                    user,
                    hasCreds));
        }

        // Fallback: if no Wiki password entries exist, show legacy wikiSites entries
        if (rows.isEmpty() && s.wikiSites != null) {
            for (String entry : s.wikiSites) {
                String[] parts = entry.split("\\|", 6);
                if (parts.length >= 3) {
                    String id = parts[0].trim();
                    String name = parts[1].trim();
                    String url = parts[2].trim();
                    boolean login = parts.length >= 4 && "true".equalsIgnoreCase(parts[3].trim());
                    boolean proxy = parts.length >= 5 && "true".equalsIgnoreCase(parts[4].trim());
                    boolean autoIdx = parts.length >= 6 && "true".equalsIgnoreCase(parts[5].trim());
                    rows.add(new WikiSiteRow(id, name, url, login, proxy, autoIdx, "", false));
                }
            }
        }

        return rows;
    }

    @Override
    protected void applyToSettings(Settings s) {
        // Sync wikiSites from passwordEntries for backward compatibility
        // (OpenWebMenuCommand.parseWikiSites reads from settings.wikiSites)
        List<String> serialized = new ArrayList<String>();
        for (Settings.PasswordEntryMeta meta : s.passwordEntries) {
            if ("Wiki".equals(meta.category)) {
                serialized.add(meta.id + "|"
                        + (meta.displayName != null ? meta.displayName : meta.id) + "|"
                        + (meta.url != null ? meta.url : "") + "|"
                        + meta.requiresLogin + "|"
                        + meta.useProxy + "|"
                        + meta.autoIndex);
            }
        }
        if (!serialized.isEmpty()) {
            s.wikiSites = serialized;
        }
        // else keep existing wikiSites (legacy data)

        s.wikiPrefetchMaxItems = (Integer) prefetchMaxItemsSpinner.getValue();
        s.wikiPrefetchConcurrency = (Integer) prefetchConcurrencySpinner.getValue();
        s.wikiPrefetchCacheMaxMb = (Integer) prefetchCacheMaxMbSpinner.getValue();

        // Sync IndexSource entries for auto-indexed wiki sites
        syncWikiIndexSources(s);
    }

    /**
     * Create/remove IndexSource entries for each wiki site based on autoIndex flag.
     */
    private void syncWikiIndexSources(Settings s) {
        try {
            de.bund.zrb.indexing.service.IndexingService indexingService =
                    de.bund.zrb.indexing.service.IndexingService.getInstance();
            java.util.Set<String> existingWikiSourceIds = new java.util.HashSet<String>();
            for (de.bund.zrb.indexing.model.IndexSource src : indexingService.getAllSources()) {
                if (src.getSourceType() == de.bund.zrb.indexing.model.SourceType.WIKI) {
                    existingWikiSourceIds.add(src.getConnectionHost());
                }
            }

            for (Settings.PasswordEntryMeta meta : s.passwordEntries) {
                if (!"Wiki".equals(meta.category)) continue;

                if (meta.autoIndex && !existingWikiSourceIds.contains(meta.id)) {
                    // Create new IndexSource for this wiki
                    de.bund.zrb.indexing.model.IndexSource source = new de.bund.zrb.indexing.model.IndexSource();
                    source.setName("Wiki: " + (meta.displayName != null ? meta.displayName : meta.id));
                    source.setSourceType(de.bund.zrb.indexing.model.SourceType.WIKI);
                    source.setEnabled(true);
                    source.setConnectionHost(meta.id);
                    source.setMaxCrawlDepth(0);
                    source.setMaxUrlsPerSession(100);
                    source.setScheduleMode(de.bund.zrb.indexing.model.ScheduleMode.MANUAL);
                    source.setFulltextEnabled(true);
                    source.setEmbeddingEnabled(false);
                    indexingService.saveSource(source);
                } else if (!meta.autoIndex && existingWikiSourceIds.contains(meta.id)) {
                    // Remove IndexSource for this wiki
                    for (de.bund.zrb.indexing.model.IndexSource src : indexingService.getAllSources()) {
                        if (src.getSourceType() == de.bund.zrb.indexing.model.SourceType.WIKI
                                && meta.id.equals(src.getConnectionHost())) {
                            indexingService.removeSource(src.getSourceId());
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Best-effort: don't fail settings save if indexing sync fails
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Table Model (read-only)
    // ═══════════════════════════════════════════════════════════

    private static final class WikiSiteRow {
        final String id;
        final String displayName;
        final String apiUrl;
        final boolean requiresLogin;
        final boolean useProxy;
        final boolean autoIndex;
        final String user;
        final boolean hasCreds;

        WikiSiteRow(String id, String displayName, String apiUrl,
                    boolean requiresLogin, boolean useProxy, boolean autoIndex,
                    String user, boolean hasCreds) {
            this.id = id;
            this.displayName = displayName;
            this.apiUrl = apiUrl;
            this.requiresLogin = requiresLogin;
            this.useProxy = useProxy;
            this.autoIndex = autoIndex;
            this.user = user;
            this.hasCreds = hasCreds;
        }
    }

    private static final class WikiSiteTableModel extends AbstractTableModel {
        private List<WikiSiteRow> rows;
        private static final String[] COLUMNS = {
                "ID", "Name", "API-URL", "Login", "Proxy", "Auto-Index", "Benutzer", "Status"
        };

        WikiSiteTableModel(List<WikiSiteRow> rows) {
            this.rows = new ArrayList<WikiSiteRow>(rows);
        }

        void setRows(List<WikiSiteRow> rows) {
            this.rows = new ArrayList<WikiSiteRow>(rows);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Class<?> getColumnClass(int col) {
            return (col >= 3 && col <= 5) ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int col) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            WikiSiteRow r = rows.get(row);
            switch (col) {
                case 0: return r.id;
                case 1: return r.displayName;
                case 2: return r.apiUrl;
                case 3: return r.requiresLogin;
                case 4: return r.useProxy;
                case 5: return r.autoIndex;
                case 6: return r.user;
                case 7: return r.hasCreds ? "✅" : "⚠️";
                default: return "";
            }
        }
    }
}

