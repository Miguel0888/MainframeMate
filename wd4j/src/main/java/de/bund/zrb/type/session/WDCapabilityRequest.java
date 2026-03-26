package de.bund.zrb.type.session;

/**
 * The session.CapabilityRequest type represents a specific set of requested capabilities.
 *
 * WebDriver BiDi defines additional WebDriver capabilities. The following tables enumerates the capabilities each implementation must support for WebDriver BiDi.
 *
 * <table>
 *   <tr>
 *     <th>Capability</th>
 *     <th>WebSocket URL</th>
 *   </tr>
 *   <tr>
 *     <td>Key</td>
 *     <td>"webSocketUrl"</td>
 *   </tr>
 *   <tr>
 *     <td>Value type</td>
 *     <td>boolean</td>
 *   </tr>
 *   <tr>
 *     <td>Description</td>
 *     <td>Defines the current session’s support for bidirectional connection.</td>
 *   </tr>
 * </table>
 */
public class WDCapabilityRequest {
    private final Boolean acceptInsecureCerts; // Optional
    private final String browserName; // Optional
    private final String browserVersion; // Optional
    private final String platformName; // Optional
    private final WDProxyConfiguration proxy; // Optional
    private final WDUserPromptHandler unhandledPromptBehavior; // Optional
    private final WDPageLoadStrategy pageLoadStrategy; // Optional

    public WDCapabilityRequest() {
        this(null, null, null, null, null, null, null);
    }

    /**
     * Legacy constructor (without pageLoadStrategy) — kept for backward compatibility.
     */
    public WDCapabilityRequest(Boolean acceptInsecureCerts, String browserName, String browserVersion,
                               String platformName, WDProxyConfiguration proxy, WDUserPromptHandler unhandledPromptBehavior) {
        this(acceptInsecureCerts, browserName, browserVersion, platformName, proxy, unhandledPromptBehavior, null);
    }

    /**
     * Full constructor including pageLoadStrategy.
     *
     * @param pageLoadStrategy the page load strategy; {@code null} means the browser default ("normal")
     */
    public WDCapabilityRequest(Boolean acceptInsecureCerts, String browserName, String browserVersion,
                               String platformName, WDProxyConfiguration proxy,
                               WDUserPromptHandler unhandledPromptBehavior,
                               WDPageLoadStrategy pageLoadStrategy) {
        this.acceptInsecureCerts = acceptInsecureCerts;
        this.browserName = browserName;
        this.browserVersion = browserVersion;
        this.platformName = platformName;
        this.proxy = proxy;
        this.unhandledPromptBehavior = unhandledPromptBehavior;
        this.pageLoadStrategy = pageLoadStrategy;
    }

    public Boolean getAcceptInsecureCerts() {
        return acceptInsecureCerts;
    }

    public String getBrowserName() {
        return browserName;
    }

    public String getBrowserVersion() {
        return browserVersion;
    }

    public String getPlatformName() {
        return platformName;
    }

    public WDProxyConfiguration getProxy() {
        return proxy;
    }

    public WDUserPromptHandler getUnhandledPromptBehavior() {
        return unhandledPromptBehavior;
    }

    public WDPageLoadStrategy getPageLoadStrategy() {
        return pageLoadStrategy;
    }
}