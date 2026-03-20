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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Opens a Wiki connection tab for browsing MediaWiki sites.
 */
public class OpenWebMenuCommand extends ShortcutMenuCommand {

    private static final Logger LOG = Logger.getLogger(OpenWebMenuCommand.class.getName());
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
        return "\u00B6 Wiki\u2026";
    }

    @Override
    public void perform() {
        List<WikiSiteDescriptor> sites = parseWikiSites();
        if (sites.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "Keine Wiki-Sites konfiguriert.\n"
                            + "Bitte unter Einstellungen → Passwörter mindestens einen\n"
                            + "Eintrag mit Kategorie \"Wiki\" und gültiger URL anlegen.",
                    "Keine Wiki-Sites", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Settings settings = SettingsHelper.load();
        JwbfWikiContentService jwbfService = new JwbfWikiContentService(sites);

        // Wire up proxy resolver so Wiki HTTP requests use the configured proxy (PAC script / manual)
        jwbfService.setProxyResolver(url -> {
            Settings s = SettingsHelper.load();
            de.bund.zrb.net.ProxyResolver.ProxyResolution res = de.bund.zrb.net.ProxyResolver.resolveForUrl(url, s);
            return res.getProxy();
        });

        WikiContentService service = jwbfService;
        WikiConnectionTab tab = new WikiConnectionTab(service);

        // Wire wiki service into the indexing scanner so wiki pages can be indexed
        de.bund.zrb.indexing.service.IndexingService indexingService =
                de.bund.zrb.indexing.service.IndexingService.getInstance();
        indexingService.getWikiScanner().setWikiService(service);
        indexingService.getWikiScanner().setCredentialsResolver(siteId -> {
            return resolveCredentials(siteId);
        });

        // Wiki search provider is registered automatically as singleton in SearchService
        // (WikiSearchProvider reads passwordEntries on each search call)

        // Wire up credentials callback: resolves encrypted credentials from componentCredentials
        // (loads settings fresh each time to pick up credentials set after tab was opened)
        tab.setCredentialsCallback(siteId -> {
            String siteKey = siteId.value();
            String credKey = "pwd:" + siteKey;
            LOG.fine("[Wiki] CredentialsCallback for site '" + siteKey + "' credKey='" + credKey + "'");
            try {
                String[] cred = de.bund.zrb.util.CredentialStore.resolveIncludingEmpty(credKey);
                if (cred != null && !cred[0].isEmpty() && !cred[1].isEmpty()) {
                    LOG.fine("[Wiki] Resolved credentials: user='" + cred[0] + "' for site '" + siteKey + "'");
                    return new WikiCredentials(cred[0], cred[1].toCharArray());
                }
                // Username present but password empty → savePassword is off → return null
                // so the dialog prompt is shown for password entry
                if (cred != null && !cred[0].isEmpty()) {
                    LOG.fine("[Wiki] Username found but password empty for site '" + siteKey
                            + "' (savePassword=off) → will prompt for password");
                }
                LOG.fine("[Wiki] No (complete) credentials stored for site '" + siteKey + "'");
            } catch (de.bund.zrb.util.JnaBlockedException e) {
                throw e; // must not be swallowed — user needs to switch password method
            } catch (de.bund.zrb.util.PowerShellBlockedException e) {
                throw e; // must not be swallowed — user needs to switch password method
            } catch (de.bund.zrb.util.KeePassNotAvailableException e) {
                throw e; // must not be swallowed — user needs to check KeePass config
            } catch (Exception e) {
                LOG.warning("[Wiki] Failed to resolve credentials for site '" + siteKey + "': " + e.getMessage());
            }
            return null;
        });

        // Wire up save callback: when user enters credentials via the login prompt,
        // encrypt and persist them to componentCredentials via CredentialStore —
        // but only if the entry has savePassword enabled. Otherwise use session cache.
        tab.setCredentialsSaveCallback((siteId, username, password) -> {
            try {
                String entryKey = siteId.value();
                Settings.PasswordEntryMeta meta = findPasswordEntryMeta(entryKey);
                if (meta != null && !meta.savePassword) {
                    // savePassword is OFF — do NOT persist to disk
                    if (meta.sessionCache) {
                        de.bund.zrb.util.CredentialStore.storeInSession(
                                "pwd:" + entryKey, username, password);
                        LOG.info("[Wiki] Credentials stored in session cache for site '"
                                + entryKey + "' user='" + username + "'");
                    } else {
                        LOG.info("[Wiki] Credentials NOT stored (savePassword=off, sessionCache=off) for site '"
                                + entryKey + "'");
                    }
                } else {
                    // savePassword is ON or no matching entry → persist normally
                    de.bund.zrb.util.CredentialStore.store(
                            "pwd:" + entryKey, username, password);
                    LOG.info("[Wiki] Credentials saved for site '" + entryKey + "' user='" + username + "'");
                }
            } catch (de.bund.zrb.util.JnaBlockedException e) {
                throw e; // must not be swallowed — user needs to switch password method
            } catch (de.bund.zrb.util.PowerShellBlockedException e) {
                throw e; // must not be swallowed — user needs to switch password method
            } catch (de.bund.zrb.util.KeePassNotAvailableException e) {
                throw e; // must not be swallowed — user needs to check KeePass config
            } catch (Exception e) {
                LOG.warning("[Wiki] Failed to save credentials for site '" + siteId.value() + "': " + e.getMessage());
            }
        });

        // Wire up open callback: creates WikiFileTab when user double-clicks/enters a result
        tab.setOpenCallback((siteId, pageTitle, htmlContent, htmlWithImages, outline, images) -> {
            WikiFileTab fileTab = new WikiFileTab(siteId, pageTitle, htmlContent, htmlWithImages, outline, images);
            wireFileTabLinks(fileTab, service, sites);
            tabManager.addTab(fileTab);
        });

        // Wire up outline callback: updates RightDrawer when preview changes
        if (tabManager.getMainframeContext() instanceof de.bund.zrb.ui.MainFrame) {
            de.bund.zrb.ui.MainFrame mf = (de.bund.zrb.ui.MainFrame) tabManager.getMainframeContext();
            de.bund.zrb.ui.drawer.RightDrawer rightDrawer = mf.getRightDrawer();
            if (rightDrawer != null) {
                tab.setOutlineCallback((outlineNode, title) -> {
                    java.util.function.Consumer<String> scroller = anchor -> tab.scrollToAnchor(anchor);
                    rightDrawer.updateWikiOutline(outlineNode, title, scroller);
                });
            }

            // Wire up dependency callback: updates LeftDrawer when preview changes
            tab.setDependencyCallback((siteId, pageTitle) -> {
                de.bund.zrb.ui.drawer.LeftDrawer leftDrawer = mf.getBookmarkDrawer();
                de.bund.zrb.service.RelationsService rs = mf.getRelationsService();
                if (leftDrawer == null || rs == null) return;

                String cachePath = "wiki://" + siteId.value() + "/" + pageTitle;
                java.util.List<de.bund.zrb.ui.drawer.LeftDrawer.RelationEntry> cached =
                        rs.getCached(cachePath);
                if (cached != null) {
                    leftDrawer.updateRelations("Wiki-Links", cached);
                    return;
                }

                leftDrawer.showRelationsLoading();
                rs.resolveWikiLinks(siteId, pageTitle, cachePath,
                        entries -> leftDrawer.updateRelations("Wiki-Links", entries));
            });
        }

        // Wire up index callback: indexes a single wiki page into Lucene on demand
        tab.setIndexCallback((siteId, pageTitle, html) -> {
            try {
                String docId = "wiki://" + siteId.value() + "/" + pageTitle;
                de.bund.zrb.rag.service.RagService rag = de.bund.zrb.rag.service.RagService.getInstance();
                // Remove old version if already indexed (re-index)
                if (rag.isIndexed(docId)) {
                    rag.removeDocument(docId);
                }
                String text = stripHtmlForIndex(html);
                if (text.isEmpty()) return 0;
                de.bund.zrb.ingestion.model.document.DocumentMetadata meta =
                        de.bund.zrb.ingestion.model.document.DocumentMetadata.builder()
                                .sourceName(pageTitle)
                                .mimeType("text/html")
                                .attribute("sourcePath", docId)
                                .attribute("wikiSite", siteId.value())
                                .build();
                de.bund.zrb.ingestion.model.document.Document doc =
                        de.bund.zrb.ingestion.model.document.Document.builder()
                                .metadata(meta)
                                .paragraph(text)
                                .build();
                rag.indexDocument(docId, pageTitle, doc, false);
                LOG.info("[Wiki] Indexed on demand: " + pageTitle + " (" + text.length() + " chars)");
                de.bund.zrb.rag.service.RagService.IndexedDocument indexed = rag.getIndexedDocument(docId);
                return indexed != null ? indexed.chunkCount : 1;
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[Wiki] Index on demand failed: " + pageTitle, e);
                return -1;
            }
        });

        // Wire up prefetch: cache search results in the background
        try {
            CacheRepository cacheRepo = CacheRepository.getInstance();
            WikiPrefetchService prefetch = new WikiPrefetchService(
                    service, cacheRepo,
                    settings.wikiPrefetchCacheMaxMb,
                    settings.wikiPrefetchMaxItems,
                    settings.wikiPrefetchConcurrency);
            prefetch.setCredentialsResolver(siteId -> resolveCredentials(siteId));

            // Auto-index pages into Lucene when site has autoIndex=true
            final List<WikiSiteDescriptor> allSites = sites;
            prefetch.setAutoIndexCallback((siteId, pageView) -> {
                // Check if this site has autoIndex enabled
                boolean autoIdx = false;
                for (WikiSiteDescriptor s : allSites) {
                    if (s.id().equals(siteId)) {
                        autoIdx = s.autoIndex();
                        break;
                    }
                }
                if (!autoIdx) return;

                try {
                    String docId = "wiki://" + siteId.value() + "/" + pageView.title();
                    de.bund.zrb.rag.service.RagService rag = de.bund.zrb.rag.service.RagService.getInstance();
                    if (rag.isIndexed(docId)) return;

                    // Build a Document from the page HTML
                    String text = stripHtmlForIndex(pageView.cleanedHtml());
                    if (text.isEmpty()) return;

                    de.bund.zrb.ingestion.model.document.DocumentMetadata meta =
                            de.bund.zrb.ingestion.model.document.DocumentMetadata.builder()
                                    .sourceName(pageView.title())
                                    .mimeType("text/html")
                                    .attribute("sourcePath", docId)
                                    .attribute("wikiSite", siteId.value())
                                    .build();
                    de.bund.zrb.ingestion.model.document.Document doc =
                            de.bund.zrb.ingestion.model.document.Document.builder()
                                    .metadata(meta)
                                    .paragraph(text)
                                    .build();
                    rag.indexDocument(docId, pageView.title(), doc, false);
                    LOG.info("[Wiki] Auto-indexed: " + pageView.title() + " (" + text.length() + " chars)");
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[Wiki] Auto-index failed for: " + pageView.title(), e);
                }
            });

            tab.setPrefetchCallback(prefetch);
        } catch (Exception e) {
            // CacheRepository not available — prefetch disabled, no problem
        }

        tabManager.addTab(tab);

        // Restore persisted wiki checkbox selection from applicationState
        tab.restoreApplicationState(settings.applicationState);

        // Auto-save checkbox state on every toggle
        tab.setStateSaveCallback(() -> {
            Settings s = SettingsHelper.load();
            tab.addApplicationState(s.applicationState);
            SettingsHelper.save(s);
        });

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
                                view.cleanedHtml(), view.htmlWithImages(), view.outline(), view.images());
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

    /**
     * Build wiki site descriptors from the central {@code passwordEntries} (category "Wiki").
     * No longer reads from the legacy {@code settings.wikiSites} list.
     */
    private List<WikiSiteDescriptor> parseWikiSites() {
        Settings settings = SettingsHelper.load();
        List<WikiSiteDescriptor> result = new ArrayList<WikiSiteDescriptor>();

        for (Settings.PasswordEntryMeta meta : settings.passwordEntries) {
            if (!"Wiki".equals(meta.category)) continue;
            String name = (meta.displayName != null && !meta.displayName.isEmpty())
                    ? meta.displayName : meta.id;
            String url = meta.url != null ? meta.url : "";
            if (url.isEmpty()) continue; // Wiki entries without URL are useless
            result.add(new WikiSiteDescriptor(
                    new WikiSiteId(meta.id), name, url,
                    meta.requiresLogin, meta.useProxy, meta.autoIndex));
        }
        return result;
    }

    private WikiCredentials resolveCredentials(WikiSiteId siteId) {
        try {
            // Resolve from central password store (pwd:<id>)
            String[] cred = de.bund.zrb.util.CredentialStore.resolveIncludingEmpty(
                    "pwd:" + siteId.value());
            if (cred != null && !cred[0].isEmpty() && !cred[1].isEmpty()) {
                return new WikiCredentials(cred[0], cred[1].toCharArray());
            }
            // Username present but password empty → savePassword is off, no session cache
        } catch (de.bund.zrb.util.JnaBlockedException e) {
            throw e; // must not be swallowed — user needs to switch password method
        } catch (de.bund.zrb.util.PowerShellBlockedException e) {
            throw e; // must not be swallowed — user needs to switch password method
        } catch (de.bund.zrb.util.KeePassNotAvailableException e) {
            throw e; // must not be swallowed — user needs to check KeePass config
        } catch (Exception e) {
            // decryption failed
        }
        return WikiCredentials.anonymous();
    }

    /**
     * Simple HTML→text conversion for indexing: strip tags, decode entities, collapse whitespace.
     */
    private static String stripHtmlForIndex(String html) {
        if (html == null || html.isEmpty()) return "";
        String text = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", "");
        text = text.replaceAll("(?i)<(br|p|div|h[1-6]|li|tr)[^>]*>", "\n");
        text = text.replaceAll("<[^>]+>", "");
        text = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                   .replace("&quot;", "\"").replace("&nbsp;", " ").replace("&#39;", "'");
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }

    /**
     * Find the PasswordEntryMeta for a given entry ID from settings.
     *
     * @return the matching meta, or {@code null} if not found
     */
    private static Settings.PasswordEntryMeta findPasswordEntryMeta(String entryId) {
        if (entryId == null) return null;
        Settings settings = SettingsHelper.load();
        for (Settings.PasswordEntryMeta meta : settings.passwordEntries) {
            if (entryId.equals(meta.id)) {
                return meta;
            }
        }
        return null;
    }
}
