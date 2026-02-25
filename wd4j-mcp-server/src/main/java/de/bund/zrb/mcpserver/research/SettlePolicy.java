package de.bund.zrb.mcpserver.research;

/**
 * Determines how the system waits for a page to "settle" after an action.
 */
public enum SettlePolicy {
    /**
     * Wait for navigation events (domContentLoaded/load via ReadinessState).
     * Use for regular link clicks that cause a full page load.
     */
    NAVIGATION,

    /**
     * Wait until DOM mutations are quiet for T milliseconds.
     * Use for SPA clicks that don't trigger a full navigation.
     */
    DOM_QUIET,

    /**
     * Wait until no new network requests for T milliseconds.
     * Use for AJAX-heavy pages.
     */
    NETWORK_QUIET;

    public static SettlePolicy fromString(String s) {
        if (s == null || s.isEmpty()) return NAVIGATION;
        switch (s.toUpperCase().replace("-", "_")) {
            case "DOM_QUIET":
            case "DOM":
                return DOM_QUIET;
            case "NETWORK_QUIET":
            case "NETWORK":
                return NETWORK_QUIET;
            case "NAVIGATION":
            default:
                return NAVIGATION;
        }
    }
}

