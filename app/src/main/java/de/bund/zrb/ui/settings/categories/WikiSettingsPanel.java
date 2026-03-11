package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings panel for Wiki site configuration.
 * Users can add, edit, remove wiki sites (each with id, name, API URL, login flag).
 */
public class WikiSettingsPanel extends AbstractSettingsPanel {

    private final WikiSiteTableModel tableModel;
    private final JTable siteTable;
    private final JSpinner prefetchMaxItemsSpinner;
    private final JSpinner prefetchConcurrencySpinner;
    private final JSpinner prefetchCacheMaxMbSpinner;

    /** Credentials edited via the dialog – kept separately so they survive settings reload in apply(). */
    private final Map<String, String> pendingCredentials = new HashMap<String, String>();

    public WikiSettingsPanel() {
        super("wiki", "Wiki");
        FormBuilder fb = new FormBuilder();

        fb.addSection("Wiki-Sites");
        fb.addInfo("Konfigurieren Sie hier Ihre MediaWiki-Instanzen. "
                + "Die API-URL endet typischerweise auf /w/ oder /api.php.");

        // Parse existing sites from settings
        List<WikiSiteRow> rows = parseSites(settings.wikiSites);
        tableModel = new WikiSiteTableModel(rows);
        siteTable = new JTable(tableModel);
        siteTable.setRowHeight(24);
        siteTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        siteTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        siteTable.getColumnModel().getColumn(2).setPreferredWidth(300);
        siteTable.getColumnModel().getColumn(3).setPreferredWidth(60);
        siteTable.getColumnModel().getColumn(3).setMaxWidth(80);
        siteTable.getColumnModel().getColumn(4).setPreferredWidth(60);
        siteTable.getColumnModel().getColumn(4).setMaxWidth(80);
        siteTable.getColumnModel().getColumn(5).setPreferredWidth(80);
        siteTable.getColumnModel().getColumn(5).setMaxWidth(100);

        JScrollPane scroll = new JScrollPane(siteTable);
        scroll.setPreferredSize(new Dimension(600, 180));
        fb.addWideGrow(scroll);

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton addBtn = new JButton("➕ Hinzufügen");
        addBtn.addActionListener(e -> addSite());
        JButton removeBtn = new JButton("➖ Entfernen");
        removeBtn.addActionListener(e -> removeSite());
        JButton credBtn = new JButton("🔑 Zugangsdaten…");
        credBtn.setToolTipText("Benutzername und Passwort für das ausgewählte Wiki setzen");
        credBtn.addActionListener(e -> editCredentials());
        JButton defaultBtn = new JButton("📦 Standard-Wikis laden");
        defaultBtn.setToolTipText("Wikipedia (DE + EN) als Standard hinzufügen");
        defaultBtn.addActionListener(e -> loadDefaults());
        buttons.add(addBtn);
        buttons.add(removeBtn);
        buttons.add(credBtn);
        buttons.add(Box.createHorizontalStrut(16));
        buttons.add(defaultBtn);
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

    private void addSite() {
        tableModel.addRow(new WikiSiteRow("new_wiki", "Neues Wiki", "https://example.com/w/", false, false, true));
        int newRow = tableModel.getRowCount() - 1;
        siteTable.setRowSelectionInterval(newRow, newRow);
        siteTable.scrollRectToVisible(siteTable.getCellRect(newRow, 0, true));
    }

    private void removeSite() {
        int row = siteTable.getSelectedRow();
        if (row >= 0) {
            tableModel.removeRow(row);
        }
    }

    private void loadDefaults() {
        List<WikiSiteRow> defaults = new ArrayList<WikiSiteRow>();
        defaults.add(new WikiSiteRow("wikipedia_de", "Wikipedia (DE)", "https://de.wikipedia.org/w/", false, false, false));
        defaults.add(new WikiSiteRow("wikipedia_en", "Wikipedia (EN)", "https://en.wikipedia.org/w/", false, false, false));

        for (WikiSiteRow def : defaults) {
            boolean exists = false;
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                if (tableModel.getRow(i).id.equals(def.id)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                tableModel.addRow(def);
            }
        }
    }

    private void editCredentials() {
        int row = siteTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(siteTable, "Bitte zuerst ein Wiki auswählen.",
                    "Kein Wiki ausgewählt", JOptionPane.WARNING_MESSAGE);
            return;
        }
        WikiSiteRow site = tableModel.getRow(row);

        // Load existing credentials (decrypt)
        String existingUser = "";
        String existingPass = "";
        String existingEncrypted = settings.wikiCredentials.get(site.id);
        // Also check pendingCredentials (may have been set in this session)
        if ((existingEncrypted == null || existingEncrypted.isEmpty()) && pendingCredentials.containsKey(site.id)) {
            existingEncrypted = pendingCredentials.get(site.id);
        }
        if (existingEncrypted != null && !existingEncrypted.isEmpty()) {
            try {
                String decrypted = de.bund.zrb.util.WindowsCryptoUtil.decrypt(existingEncrypted);
                int sep = decrypted.indexOf('|');
                if (sep >= 0) {
                    existingUser = decrypted.substring(0, sep);
                    existingPass = decrypted.substring(sep + 1);
                }
            } catch (Exception ignore) {
                // corrupted credential, start fresh
            }
        }

        JTextField userField = new JTextField(existingUser, 20);
        JPasswordField passField = new JPasswordField(existingPass, 20);
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Wiki:"), gbc);
        gbc.gridx = 1;
        panel.add(new JLabel(site.displayName + " (" + site.id + ")"), gbc);

        // Status indicator
        boolean hasExistingCreds = !existingUser.isEmpty() && !existingPass.isEmpty();
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        JLabel statusHint = new JLabel(hasExistingCreds
                ? "✅ Zugangsdaten sind gespeichert."
                : "⚠️ Noch keine Zugangsdaten hinterlegt.");
        statusHint.setFont(statusHint.getFont().deriveFont(Font.ITALIC, 11f));
        panel.add(statusHint, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Benutzername:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(userField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Passwort:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(passField, gbc);

        int result = JOptionPane.showConfirmDialog(siteTable, panel,
                "Wiki-Zugangsdaten: " + site.displayName,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;

        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());

        if (user.isEmpty() && pass.isEmpty()) {
            // Clear credentials
            settings.wikiCredentials.remove(site.id);
            pendingCredentials.remove(site.id);
        } else {
            // Encrypt and store
            String encrypted = de.bund.zrb.util.WindowsCryptoUtil.encrypt(user + "|" + pass);
            settings.wikiCredentials.put(site.id, encrypted);
            pendingCredentials.put(site.id, encrypted);

            // Also persist immediately so credentials survive settings reload
            Settings diskSettings = de.bund.zrb.helper.SettingsHelper.load();
            diskSettings.wikiCredentials.put(site.id, encrypted);
            de.bund.zrb.helper.SettingsHelper.save(diskSettings);
        }

        // Also enable requiresLogin if credentials are set
        if (!user.isEmpty()) {
            site.requiresLogin = true;
            tableModel.fireTableRowsUpdated(row, row);
        }
    }

    @Override
    protected void applyToSettings(Settings s) {
        // Stop editing to capture any pending cell edits
        if (siteTable.isEditing()) {
            siteTable.getCellEditor().stopCellEditing();
        }

        List<String> serialized = new ArrayList<String>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            WikiSiteRow row = tableModel.getRow(i);
            serialized.add(row.id + "|" + row.displayName + "|" + row.apiUrl + "|" + row.requiresLogin + "|" + row.useProxy + "|" + row.autoIndex);
        }
        s.wikiSites = serialized;

        // Credentials: merge pending edits into the freshly loaded settings
        // (apply() reloads settings from disk, which already has the immediately-saved creds,
        //  but pendingCredentials is the authoritative source for this session)
        s.wikiCredentials.putAll(pendingCredentials);

        s.wikiPrefetchMaxItems = (Integer) prefetchMaxItemsSpinner.getValue();
        s.wikiPrefetchConcurrency = (Integer) prefetchConcurrencySpinner.getValue();
        s.wikiPrefetchCacheMaxMb = (Integer) prefetchCacheMaxMbSpinner.getValue();

        // Sync IndexSource entries for auto-indexed wiki sites
        syncWikiIndexSources();
    }

    /**
     * Create/remove IndexSource entries for each wiki site based on autoIndex flag.
     */
    private void syncWikiIndexSources() {
        try {
            de.bund.zrb.indexing.service.IndexingService indexingService =
                    de.bund.zrb.indexing.service.IndexingService.getInstance();
            java.util.Set<String> existingWikiSourceIds = new java.util.HashSet<>();
            for (de.bund.zrb.indexing.model.IndexSource src : indexingService.getAllSources()) {
                if (src.getSourceType() == de.bund.zrb.indexing.model.SourceType.WIKI) {
                    existingWikiSourceIds.add(src.getConnectionHost());
                }
            }

            for (int i = 0; i < tableModel.getRowCount(); i++) {
                WikiSiteRow row = tableModel.getRow(i);
                if (row.autoIndex && !existingWikiSourceIds.contains(row.id)) {
                    // Create new IndexSource for this wiki
                    de.bund.zrb.indexing.model.IndexSource source = new de.bund.zrb.indexing.model.IndexSource();
                    source.setName("Wiki: " + row.displayName);
                    source.setSourceType(de.bund.zrb.indexing.model.SourceType.WIKI);
                    source.setEnabled(true);
                    source.setConnectionHost(row.id); // siteId
                    source.setMaxCrawlDepth(0); // only search results, no crawling by default
                    source.setMaxUrlsPerSession(100);
                    source.setScheduleMode(de.bund.zrb.indexing.model.ScheduleMode.MANUAL);
                    source.setFulltextEnabled(true);
                    source.setEmbeddingEnabled(false);
                    indexingService.saveSource(source);
                } else if (!row.autoIndex && existingWikiSourceIds.contains(row.id)) {
                    // Remove IndexSource for this wiki
                    for (de.bund.zrb.indexing.model.IndexSource src : indexingService.getAllSources()) {
                        if (src.getSourceType() == de.bund.zrb.indexing.model.SourceType.WIKI
                                && row.id.equals(src.getConnectionHost())) {
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
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    private List<WikiSiteRow> parseSites(List<String> raw) {
        List<WikiSiteRow> rows = new ArrayList<WikiSiteRow>();
        if (raw == null) return rows;
        for (String entry : raw) {
            String[] parts = entry.split("\\|", 6);
            if (parts.length >= 3) {
                String id = parts[0].trim();
                String name = parts[1].trim();
                String url = parts[2].trim();
                boolean login = parts.length >= 4 && "true".equalsIgnoreCase(parts[3].trim());
                boolean proxy = parts.length >= 5 && "true".equalsIgnoreCase(parts[4].trim());
                boolean autoIdx = parts.length >= 6 && "true".equalsIgnoreCase(parts[5].trim());
                rows.add(new WikiSiteRow(id, name, url, login, proxy, autoIdx));
            }
        }
        return rows;
    }

    // ═══════════════════════════════════════════════════════════
    //  Table Model
    // ═══════════════════════════════════════════════════════════

    private static final class WikiSiteRow {
        String id;
        String displayName;
        String apiUrl;
        boolean requiresLogin;
        boolean useProxy;
        boolean autoIndex;

        WikiSiteRow(String id, String displayName, String apiUrl, boolean requiresLogin) {
            this(id, displayName, apiUrl, requiresLogin, false, false);
        }

        WikiSiteRow(String id, String displayName, String apiUrl, boolean requiresLogin, boolean useProxy) {
            this(id, displayName, apiUrl, requiresLogin, useProxy, false);
        }

        WikiSiteRow(String id, String displayName, String apiUrl, boolean requiresLogin, boolean useProxy, boolean autoIndex) {
            this.id = id;
            this.displayName = displayName;
            this.apiUrl = apiUrl;
            this.requiresLogin = requiresLogin;
            this.useProxy = useProxy;
            this.autoIndex = autoIndex;
        }
    }

    private static final class WikiSiteTableModel extends AbstractTableModel {
        private final List<WikiSiteRow> rows;
        private static final String[] COLUMNS = {"ID", "Name", "API-URL", "Login", "Proxy", "Auto-Index"};

        WikiSiteTableModel(List<WikiSiteRow> rows) {
            this.rows = new ArrayList<WikiSiteRow>(rows);
        }

        WikiSiteRow getRow(int row) { return rows.get(row); }

        void addRow(WikiSiteRow row) {
            rows.add(row);
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        void removeRow(int row) {
            rows.remove(row);
            fireTableRowsDeleted(row, row);
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Class<?> getColumnClass(int col) {
            return (col >= 3 && col <= 5) ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int col) { return true; }

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
                default: return "";
            }
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            WikiSiteRow r = rows.get(row);
            switch (col) {
                case 0: r.id = String.valueOf(value); break;
                case 1: r.displayName = String.valueOf(value); break;
                case 2: r.apiUrl = String.valueOf(value); break;
                case 3: r.requiresLogin = Boolean.TRUE.equals(value); break;
                case 4: r.useProxy = Boolean.TRUE.equals(value); break;
                case 5: r.autoIndex = Boolean.TRUE.equals(value); break;
            }
            fireTableCellUpdated(row, col);
        }
    }
}

