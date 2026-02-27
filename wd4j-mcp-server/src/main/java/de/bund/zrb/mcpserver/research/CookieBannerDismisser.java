package de.bund.zrb.mcpserver.research;

import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.support.ScriptHelper;
import de.bund.zrb.type.script.WDEvaluateResult;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Attempts to dismiss cookie consent banners by clicking common "Accept" buttons.
 * <p>
 * Uses {@code script.evaluate} with CSS selectors to find and click consent buttons.
 * This runs after navigation and before the DOM snapshot so the snapshot captures
 * the page content without the cookie overlay.
 * <p>
 * Selectors and dismiss script are configurable via {@link #setSelectors(List)} and
 * {@link #setDismissScript(String)}. Defaults are loaded from the classpath resource
 * {@code scripts/cookie-banner-dismiss.js}.
 */
public class CookieBannerDismisser {

    private static final Logger LOG = Logger.getLogger(CookieBannerDismisser.class.getName());

    /** JS template loaded from classpath (contains %SELECTORS_JSON% placeholder). */
    private static final String DEFAULT_SCRIPT_TEMPLATE = ScriptHelper.loadScript("scripts/cookie-banner-dismiss.js");

    /**
     * Default CSS selectors for common cookie consent "Accept All" buttons.
     * Ordered by specificity – most common frameworks first.
     */
    public static final List<String> DEFAULT_SELECTORS = Collections.unmodifiableList(Arrays.asList(
            // OneTrust (very common)
            "#onetrust-accept-btn-handler",
            // Cookiebot
            "#CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll",
            // Quantcast / CMP
            ".qc-cmp2-summary-buttons button[mode='primary']",
            // Usercentrics
            "#uc-btn-accept-banner",
            // Generic patterns (class/id containing 'consent', 'cookie', 'accept')
            "[class*='cookie'] button[class*='accept']",
            "[class*='cookie'] button[class*='Accept']",
            "[class*='consent'] button[class*='accept']",
            "[class*='consent'] button[class*='Accept']",
            "[id*='cookie'] button[id*='accept']",
            "[id*='consent'] button[id*='accept']",
            // Common button text patterns via data attributes
            "button[data-action='accept']",
            "button[data-action='accept-all']",
            "button[data-action='acceptAll']",
            // German sites
            "button[class*='akzeptieren']",
            "button[class*='Akzeptieren']",
            "[class*='cookie'] .accept",
            "[class*='cookie'] .agree",
            // Generic fallback: large visible buttons in cookie/consent containers
            "[class*='cookie-banner'] button:first-of-type",
            "[class*='consent-banner'] button:first-of-type",
            "[id*='cookie-banner'] button:first-of-type",
            "[id*='consent-banner'] button:first-of-type"
    ));

    // ── Configurable state (singleton-style for plugin settings) ──

    private static volatile List<String> customSelectors = null;
    private static volatile String customDismissScript = null;

    /**
     * Override the default CSS selectors.
     * Pass {@code null} to revert to defaults.
     */
    public static void setSelectors(List<String> selectors) {
        customSelectors = selectors;
    }

    /**
     * Override the default dismiss JS template.
     * Must contain {@code %SELECTORS_JSON%} placeholder.
     * Pass {@code null} to revert to default template.
     */
    public static void setDismissScript(String script) {
        customDismissScript = script;
    }

    /** Get the currently active selectors (custom or default). */
    public static List<String> getActiveSelectors() {
        return customSelectors != null ? customSelectors : DEFAULT_SELECTORS;
    }

    /** Get the currently active script template (custom or default). */
    public static String getActiveScriptTemplate() {
        return customDismissScript != null ? customDismissScript : DEFAULT_SCRIPT_TEMPLATE;
    }

    /**
     * Try to dismiss a cookie banner by clicking the first matching accept button.
     * Uses the configured (or default) selectors and script.
     *
     * @param session the browser session
     * @return true if a button was clicked, false otherwise
     */
    public static boolean tryDismiss(BrowserSession session) {
        if (session == null || session.getDriver() == null) {
            return false;
        }

        try {
            List<String> selectors = getActiveSelectors();
            String template = getActiveScriptTemplate();

            // Build selectors JSON array
            StringBuilder jsonArr = new StringBuilder("[");
            for (int i = 0; i < selectors.size(); i++) {
                if (i > 0) jsonArr.append(",");
                jsonArr.append("'").append(escapeJs(selectors.get(i))).append("'");
            }
            jsonArr.append("]");

            // Inject selectors into template
            String js = template.replace("%SELECTORS_JSON%", jsonArr.toString());

            WDEvaluateResult result = session.evaluate(js, false);

            if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                String value = ((WDEvaluateResult.WDEvaluateResultSuccess) result)
                        .getResult().asString();
                if (value != null && value.startsWith("clicked")) {
                    LOG.info("[CookieBanner] Dismissed: " + value);
                    // Wait a bit for the banner to animate away
                    try { Thread.sleep(800); } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    return true;
                } else {
                    LOG.fine("[CookieBanner] No banner found");
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "[CookieBanner] Dismiss attempt failed", e);
        }

        return false;
    }

    private static String escapeJs(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}
