package de.bund.zrb.type.session;

/**
 * WebDriver page load strategy.
 *
 * <p>Defines when the browser considers a navigation to be "complete":
 * <ul>
 *   <li>{@link #NONE} — do not wait for any readiness state after navigation.
 *       The command returns immediately once the navigation is initiated.</li>
 *   <li>{@link #EAGER} — wait until the DOM is ready (DOMContentLoaded).</li>
 *   <li>{@link #NORMAL} — wait until the page and all sub-resources have finished
 *       loading (load event).</li>
 * </ul>
 *
 * @see <a href="https://w3c.github.io/webdriver/#dfn-page-load-strategy">
 *     W3C WebDriver — Page Load Strategy</a>
 */
public enum WDPageLoadStrategy {
    NONE("none"),
    EAGER("eager"),
    NORMAL("normal");

    private final String value;

    WDPageLoadStrategy(String value) {
        this.value = value;
    }

    /**
     * Returns the W3C-spec string value (e.g. {@code "none"}).
     */
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
