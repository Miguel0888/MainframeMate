package de.bund.zrb.mcpserver.research;

import java.util.*;
import java.util.logging.Logger;

/**
 * Builds a {@link MenuView} from captured HTML using Jsoup (server-side).
 *
 * <p><b>No browser JS injection.</b> Instead, the HTML body is obtained from
 * {@link NetworkIngestionPipeline}'s cache (the last navigation response)
 * and parsed with {@link HtmlLinkExtractor} to extract links, title, and excerpt.
 *
 * <p>The bot navigates exclusively via URLs (address bar). The only browser-side
 * interaction is cookie-banner dismissal via CSS selectors.
 */
public class MenuViewBuilder {

    private static final Logger LOG = Logger.getLogger(MenuViewBuilder.class.getName());

    private final ResearchSession researchSession;
    private final NetworkIngestionPipeline pipeline;

    // Direct HTML override (bypasses pipeline)
    private String htmlOverride;
    private String urlOverride;

    public MenuViewBuilder(ResearchSession researchSession, NetworkIngestionPipeline pipeline) {
        this.researchSession = researchSession;
        this.pipeline = pipeline;
    }

    /**
     * Set HTML content directly, bypassing the NetworkIngestionPipeline.
     * Used when HTML is fetched via script.evaluate("document.documentElement.outerHTML").
     */
    public void setHtmlOverride(String html, String url) {
        this.htmlOverride = html;
        this.urlOverride = url;
    }

    /**
     * Build a MenuView from the last captured HTML body.
     * Uses Jsoup for server-side link extraction – zero JS injection.
     *
     * @param maxItems    max number of menu items (links)
     * @param excerptLen  max length of the text excerpt
     * @return MenuView with viewToken, excerpt, and link-based menuItems
     */
    public MenuView build(int maxItems, int excerptLen) {
        // Priority 1: direct HTML override (from DOM fetch)
        String html = htmlOverride;
        String pageUrl = urlOverride;

        // Priority 2: pipeline cache (legacy path)
        if (html == null && pipeline != null) {
            html = pipeline.getLastNavigationHtml();
            pageUrl = pipeline.getLastNavigationUrl();
        }

        if (pageUrl == null) pageUrl = "";

        if (html == null || html.isEmpty()) {
            LOG.warning("[MenuViewBuilder] No cached HTML body available – returning empty view");
            String token = researchSession.generateNextViewToken();
            MenuView emptyView = new MenuView(token, pageUrl, "", "No HTML body captured.", Collections.<MenuItem>emptyList());
            researchSession.setCurrentView(emptyView);
            return emptyView;
        }

        LOG.fine("[MenuViewBuilder] Parsing HTML with Jsoup (" + html.length() + " chars, url=" + pageUrl + ")");

        // Parse with Jsoup
        HtmlLinkExtractor.ParsedPageResult parsed = HtmlLinkExtractor.parse(html, pageUrl, maxItems, excerptLen);

        LOG.info("[MenuViewBuilder] Page: " + parsed.title + " | URL: " + parsed.url
                + " | Links: " + parsed.links.size());

        // Convert links to MenuItems – no "external" hint, all links are navigable via research_choose
        List<MenuItem> menuItems = new ArrayList<>();
        for (int i = 0; i < parsed.links.size(); i++) {
            HtmlLinkExtractor.LinkInfo link = parsed.links.get(i);
            String menuItemId = "m" + i;
            menuItems.add(new MenuItem(menuItemId, MenuItem.Type.LINK, link.label, link.href, ""));
        }

        // Generate token first, then create view with token embedded, then store it
        String viewToken = researchSession.generateNextViewToken();
        MenuView finalView = new MenuView(viewToken, parsed.url, parsed.title, parsed.excerpt, menuItems);
        researchSession.setCurrentView(finalView);

        LOG.info("[MenuViewBuilder] View built: " + viewToken + " (" + menuItems.size() + " links)");

        return finalView;
    }

    /**
     * Build with settle – wait for the pipeline to cache the HTML body, then build.
     * No JS-based settle needed since we parse the HTTP response body directly.
     * Uses active polling instead of blind sleep to minimize wait time.
     */
    public MenuView buildWithSettle(SettlePolicy policy, int maxItems, int excerptLen) {
        // Wait for the pipeline to cache the navigation HTML body.
        // The pipeline fetches bodies asynchronously via getData() with retry logic,
        // so we need to actively wait until the HTML is available.
        if (pipeline != null) {
            int maxWaitMs = (policy == SettlePolicy.NETWORK_QUIET || policy == SettlePolicy.DOM_QUIET)
                    ? 5000 : 3000;
            int polled = 0;
            while (polled < maxWaitMs) {
                if (pipeline.getLastNavigationHtml() != null) {
                    LOG.fine("[MenuViewBuilder] HTML body available after " + polled + "ms");
                    break;
                }
                sleep(100);
                polled += 100;
            }
            if (pipeline.getLastNavigationHtml() == null) {
                LOG.warning("[MenuViewBuilder] HTML body not available after " + maxWaitMs + "ms");
            }
        } else {
            // No pipeline – just wait a bit
            sleep(300);
        }
        return build(maxItems, excerptLen);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
