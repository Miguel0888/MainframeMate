package de.bund.zrb.ui.commands;

import de.bund.zrb.archive.store.CacheRepository;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.TabbedPaneManager;
import de.bund.zrb.wiki.domain.*;
import de.bund.zrb.wiki.infrastructure.JwbfWikiContentService;
import de.bund.zrb.wiki.port.WikiContentService;
import de.bund.zrb.wiki.service.WikiPrefetchService;
import de.bund.zrb.wiki.ui.WikiConnectionTab;
import de.bund.zrb.wiki.ui.WikiFileTab;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens a Wiki connection tab for browsing MediaWiki sites.
 */
public class OpenWebMenuCommand extends ShortcutMenuCommand {

    private final TabbedPaneManager tabManager;

    public OpenWebMenuCommand(TabbedPaneManager tabManager) {
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "connection.wiki";
    }

    @Override
    public String getLabel() {
        return "Wiki\u2026";
    }

    @Override
    public void perform() {
        List<WikiSiteDescriptor> sites = parseWikiSites();
        if (sites.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "Keine Wiki-Sites konfiguriert.\n"
                            + "Bitte unter Einstellungen → Wiki mindestens eine Site hinzufügen.",
                    "Keine Wiki-Sites", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Settings settings = SettingsHelper.load();
        WikiContentService service = new JwbfWikiContentService(sites);
        WikiConnectionTab tab = new WikiConnectionTab(service);

        // Wire up credentials callback: resolves encrypted credentials from settings
        // (loads settings fresh each time to pick up credentials set after tab was opened)
        tab.setCredentialsCallback(siteId -> {
            Settings currentSettings = SettingsHelper.load();
            String encrypted = currentSettings.wikiCredentials.get(siteId.value());
            if (encrypted == null || encrypted.isEmpty()) return null;
            try {
                String decrypted = de.bund.zrb.util.WindowsCryptoUtil.decrypt(encrypted);
                int sep = decrypted.indexOf('|');
                if (sep >= 0) {
                    String user = decrypted.substring(0, sep);
                    String pass = decrypted.substring(sep + 1);
                    if (!user.isEmpty()) {
                        return new WikiCredentials(user, pass.toCharArray());
                    }
                }
            } catch (Exception e) {
                // decryption failed – treat as anonymous
            }
            return null;
        });

        // Wire up open callback: creates WikiFileTab when user double-clicks/enters a result
        tab.setOpenCallback((siteId, pageTitle, htmlContent, outline, images) -> {
            WikiFileTab fileTab = new WikiFileTab(siteId, pageTitle, htmlContent, outline, images);
            wireFileTabLinks(fileTab, service, sites);
            tabManager.addTab(fileTab);
        });

        // Wire up prefetch: cache search results in the background
        try {
            CacheRepository cacheRepo = CacheRepository.getInstance();
            WikiPrefetchService prefetch = new WikiPrefetchService(
                    service, cacheRepo,
                    settings.wikiPrefetchCacheMaxMb,
                    settings.wikiPrefetchMaxItems,
                    settings.wikiPrefetchConcurrency);
            tab.setPrefetchCallback(prefetch);
        } catch (Exception e) {
            // CacheRepository not available — prefetch disabled, no problem
        }

        tabManager.addTab(tab);

        // Register wiki service with RelationsService for link resolution
        if (tabManager.getMainframeContext() instanceof de.bund.zrb.ui.MainFrame) {
            de.bund.zrb.ui.MainFrame mf = (de.bund.zrb.ui.MainFrame) tabManager.getMainframeContext();
            de.bund.zrb.service.RelationsService rs = mf.getRelationsService();
            if (rs != null) {
                rs.setWikiService(service);
            }
        }
    }

    /**
     * Wire the link callback on a WikiFileTab so that clicking a link opens a new tab.
     */
    private void wireFileTabLinks(WikiFileTab fileTab, WikiContentService service,
                                  List<WikiSiteDescriptor> sites) {
        fileTab.setLinkCallback((siteId, pageTitle) -> {
            WikiSiteId wsId = new WikiSiteId(siteId);
            new SwingWorker<WikiPageView, Void>() {
                @Override
                protected WikiPageView doInBackground() throws Exception {
                    return service.loadPage(wsId, pageTitle, resolveCredentials(wsId));
                }

                @Override
                protected void done() {
                    try {
                        WikiPageView view = get();
                        WikiFileTab newTab = new WikiFileTab(siteId, view.title(),
                                view.cleanedHtml(), view.outline(), view.images());
                        wireFileTabLinks(newTab, service, sites);
                        tabManager.addTab(newTab);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(null,
                                "Fehler beim Laden der Wiki-Seite: " + ex.getMessage(),
                                "Wiki-Fehler", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });
    }

    private List<WikiSiteDescriptor> parseWikiSites() {
        Settings settings = SettingsHelper.load();
        List<WikiSiteDescriptor> result = new ArrayList<WikiSiteDescriptor>();

        if (settings.wikiSites == null) return result;

        for (String entry : settings.wikiSites) {
            String[] parts = entry.split("\\|", 4);
            if (parts.length >= 3) {
                String id = parts[0].trim();
                String name = parts[1].trim();
                String url = parts[2].trim();
                boolean login = parts.length >= 4 && "true".equalsIgnoreCase(parts[3].trim());
                result.add(new WikiSiteDescriptor(new WikiSiteId(id), name, url, login));
            }
        }
        return result;
    }

    private WikiCredentials resolveCredentials(WikiSiteId siteId) {
        Settings settings = SettingsHelper.load();
        String encrypted = settings.wikiCredentials.get(siteId.value());
        if (encrypted == null || encrypted.isEmpty()) return WikiCredentials.anonymous();
        try {
            String decrypted = de.bund.zrb.util.WindowsCryptoUtil.decrypt(encrypted);
            int sep = decrypted.indexOf('|');
            if (sep >= 0) {
                String user = decrypted.substring(0, sep);
                String pass = decrypted.substring(sep + 1);
                if (!user.isEmpty()) {
                    return new WikiCredentials(user, pass.toCharArray());
                }
            }
        } catch (Exception e) {
            // decryption failed
        }
        return WikiCredentials.anonymous();
    }
}
