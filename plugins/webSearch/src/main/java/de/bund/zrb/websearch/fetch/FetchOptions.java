package de.bund.zrb.websearch.fetch;

/**
 * Options for content fetching.
 */
public class FetchOptions {

    private final int timeoutMs;
    private final int maxTextLength;
    private final String cssSelector;
    private final boolean includeLinks;

    public static final FetchOptions DEFAULT = new FetchOptions(15000, 8000, null, true);

    public FetchOptions(int timeoutMs, int maxTextLength, String cssSelector, boolean includeLinks) {
        this.timeoutMs = timeoutMs;
        this.maxTextLength = maxTextLength;
        this.cssSelector = cssSelector;
        this.includeLinks = includeLinks;
    }

    public int getTimeoutMs() { return timeoutMs; }
    public int getMaxTextLength() { return maxTextLength; }
    public String getCssSelector() { return cssSelector; }
    public boolean isIncludeLinks() { return includeLinks; }

    public FetchOptions withTimeout(int timeoutMs) {
        return new FetchOptions(timeoutMs, this.maxTextLength, this.cssSelector, this.includeLinks);
    }

    public FetchOptions withMaxTextLength(int maxTextLength) {
        return new FetchOptions(this.timeoutMs, maxTextLength, this.cssSelector, this.includeLinks);
    }

    public FetchOptions withSelector(String cssSelector) {
        return new FetchOptions(this.timeoutMs, this.maxTextLength, cssSelector, this.includeLinks);
    }
}
