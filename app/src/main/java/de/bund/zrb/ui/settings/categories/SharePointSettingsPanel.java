package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.sharepoint.SharePointLinkFetcher;
import de.bund.zrb.sharepoint.SharePointSite;
import de.bund.zrb.sharepoint.SharePointSiteStore;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings panel for SharePoint connection configuration.
 * <ol>
 *   <li>Parent-URL field – the page from which all links are extracted</li>
 *   <li>"Links abrufen" button – fetches the page and extracts links</li>
 *   <li>Checkbox table – user picks which links are SharePoint sites</li>
 *   <li>Cache concurrency spinner</li>
 * </ol>
 */
public class SharePointSettingsPanel extends AbstractSettingsPanel {

    private final JTextField parentPageUrlField;
    private final JSpinner cacheConcurrencySpinner;
    private final LinkTableModel linkTableModel;
    private final JTable linkTable;

    public SharePointSettingsPanel() {
        super("sharepoint", "SharePoint");
        FormBuilder fb = new FormBuilder();

        // ── Section 1: Parent page ──
        fb.addSection("Parent-Seite");

        parentPageUrlField = new JTextField(
                settings.sharepointParentPageUrl != null ? settings.sharepointParentPageUrl : "", 40);
        parentPageUrlField.setToolTipText(
                "URL der Seite, auf der Links zu Ihren SharePoint-Sites aufgelistet sind.");
        fb.addRow("Parent-URL:", parentPageUrlField);

        JButton fetchButton = new JButton("🔗 Links abrufen…");
        fetchButton.setToolTipText("Alle Links von der Parent-Seite laden");
        fetchButton.addActionListener(e -> fetchLinks());
        fb.addRow("", fetchButton);

        fb.addInfo("<html><i>Geben Sie die URL einer internen Seite an (z.B. Intranet/Wiki),<br>"
                + "die Links zu Ihren SharePoint-Sites enthält. "
                + "Klicken Sie dann auf <b>Links abrufen</b>,<br>"
                + "um alle dort gelisteten URLs zu erkennen.</i></html>");

        // ── Section 2: Link checklist ──
        fb.addSection("SharePoint-Sites auswählen");

        fb.addInfo("<html><i>Haken Sie die Links an, die echte SharePoint-Sites sind.<br>"
                + "Die ausgewählten Sites werden als Netzlaufwerk (WebDAV) eingebunden<br>"
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

        // ── Section 3: Caching ──
        fb.addSection("Caching");

        cacheConcurrencySpinner = new JSpinner(new SpinnerNumberModel(
                settings.sharepointCacheConcurrency, 1, 8, 1));
        cacheConcurrencySpinner.setToolTipText("Anzahl paralleler Downloads beim Caching");
        fb.addRow("Parallele Downloads:", cacheConcurrencySpinner);

        fb.addInfo("<html><i>Beim Durchsuchen einer SharePoint-Site werden Dokumente automatisch<br>"
                + "im lokalen Cache (H2 + Lucene) gespeichert und sind über<br>"
                + "<b>Überall suchen</b> mit dem Kürzel <b>SP</b> auffindbar.</i></html>");

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
                return SharePointLinkFetcher.fetchLinks(url);
            }

            @Override
            protected void done() {
                linkTable.setCursor(Cursor.getDefaultCursor());
                try {
                    List<SharePointSite> fetched = get();
                    linkTableModel.mergeLinks(fetched);
                    JOptionPane.showMessageDialog(linkTable,
                            fetched.size() + " Links gefunden.\n"
                                    + "Bitte die SharePoint-Sites anhaken und Einstellungen speichern.",
                            "Links abgerufen", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    JOptionPane.showMessageDialog(linkTable,
                            "Fehler beim Abrufen der Links:\n" + msg,
                            "SharePoint-Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.sharepointParentPageUrl = parentPageUrlField.getText().trim();
        s.sharepointCacheConcurrency = ((Number) cacheConcurrencySpinner.getValue()).intValue();
        s.sharepointSitesJson = SharePointSiteStore.toJson(linkTableModel.getSites());
    }

    // ═══════════════════════════════════════════════════════════
    //  Table model for the link checklist
    // ═══════════════════════════════════════════════════════════

    private static class LinkTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"✓", "Name", "URL"};
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
            // Build map of existing URLs → selection state
            java.util.Map<String, Boolean> existing = new java.util.LinkedHashMap<String, Boolean>();
            for (SharePointSite s : sites) {
                existing.put(s.getUrl(), s.isSelected());
            }

            // Rebuild list: keep existing entries (preserving selection), add new ones
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
            // Also keep previously-selected entries that are no longer in the fetched list
            for (java.util.Map.Entry<String, Boolean> e : existing.entrySet()) {
                if (!seen.contains(e.getKey()) && e.getValue()) {
                    // find original site object
                    for (SharePointSite orig : new ArrayList<SharePointSite>(sites)) {
                        // not found — recreate with URL as name
                    }
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
            return col == 0; // only the checkbox
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
