package de.bund.zrb.mcpserver.research;

import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.type.script.WDEvaluateResult;

import java.util.Arrays;
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
 * The approach is best-effort: if no banner is found, nothing happens.
 * If clicking fails, it's silently ignored.
 */
public class CookieBannerDismisser {

    private static final Logger LOG = Logger.getLogger(CookieBannerDismisser.class.getName());

    /**
     * CSS selectors for common cookie consent "Accept All" buttons.
     * Ordered by specificity – most common frameworks first.
     */
    private static final List<String> CONSENT_SELECTORS = Arrays.asList(
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
    );

    /**
     * Try to dismiss a cookie banner by clicking the first matching accept button.
     * Waits briefly for the banner to appear, then tries each selector.
     *
     * @param session the browser session
     * @return true if a button was clicked, false otherwise
     */
    public static boolean tryDismiss(BrowserSession session) {
        if (session == null || session.getDriver() == null) {
            return false;
        }

        try {
            // Build a JS snippet that tries each selector and clicks the first visible match
            StringBuilder js = new StringBuilder();
            js.append("(function() {\n");
            js.append("  var selectors = [\n");
            for (int i = 0; i < CONSENT_SELECTORS.size(); i++) {
                js.append("    '").append(escapeJs(CONSENT_SELECTORS.get(i))).append("'");
                if (i < CONSENT_SELECTORS.size() - 1) js.append(",");
                js.append("\n");
            }
            js.append("  ];\n");
            js.append("  for (var i = 0; i < selectors.length; i++) {\n");
            js.append("    try {\n");
            js.append("      var el = document.querySelector(selectors[i]);\n");
            js.append("      if (el && el.offsetParent !== null) {\n");
            js.append("        el.click();\n");
            js.append("        return 'clicked:' + selectors[i];\n");
            js.append("      }\n");
            js.append("    } catch(e) {}\n");
            js.append("  }\n");
            // Fallback: look for buttons with accept-related text
            js.append("  var buttons = document.querySelectorAll('button, a[role=button], [class*=btn]');\n");
            js.append("  for (var j = 0; j < buttons.length && j < 50; j++) {\n");
            js.append("    var txt = (buttons[j].innerText || '').toLowerCase().trim();\n");
            js.append("    if ((txt.indexOf('accept') >= 0 || txt.indexOf('akzeptier') >= 0 || txt.indexOf('agree') >= 0 || txt.indexOf('alle annehmen') >= 0 || txt.indexOf('zustimmen') >= 0) ");
            js.append("        && buttons[j].offsetParent !== null) {\n");
            js.append("      buttons[j].click();\n");
            js.append("      return 'clicked-text:' + txt;\n");
            js.append("    }\n");
            js.append("  }\n");
            js.append("  return 'none';\n");
            js.append("})();\n");

            WDEvaluateResult result = session.evaluate(js.toString(), false);

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

