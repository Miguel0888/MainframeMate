package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.sharepoint.SharePointSite;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;

/**
 * Settings panel for SharePoint connection configuration.
 * <p>
 * SharePoint sites are managed as password entries with category <b>SP</b>
 * in the central password dialog ({@code Einstellungen &rarr; Passw&ouml;rter}).
 * Use the <em>🌐 SP-Links scannen</em> button there to discover sites via
 * browser.
 * <p>
 * This panel only provides the cache concurrency setting.
 */
public class SharePointSettingsPanel extends AbstractSettingsPanel {

    private final JSpinner cacheConcurrencySpinner;

    public SharePointSettingsPanel() {
        super("sharepoint", "SharePoint");
        FormBuilder fb = new FormBuilder();

        // ── Info ──
        fb.addSection("SharePoint-Sites");
        fb.addInfo("<html>SharePoint-Sites werden \u00fcber <b>Einstellungen \u2192 Passw\u00f6rter</b> verwaltet.<br>"
                + "Dort k\u00f6nnen Sie SP-Eintr\u00e4ge (Kategorie <i>SP</i>) manuell hinzuf\u00fcgen<br>"
                + "oder per <b>\uD83C\uDF10 SP-Links scannen</b> automatisch erkennen lassen.<br><br>"
                + "Die erkannten Sites sind im <b>SharePoint-Tab</b> wie ein lokales<br>"
                + "Dateisystem per WebDAV navigierbar.</html>");

        // ── Caching ──
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

    @Override
    protected void applyToSettings(Settings s) {
        s.sharepointCacheConcurrency = ((Number) cacheConcurrencySpinner.getValue()).intValue();
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
                while (id.endsWith("_")) id = id.substring(0, id.length() - 1);
            }
            return id;
        } catch (Exception e) {
            return "sp_" + url.hashCode();
        }
    }
}
