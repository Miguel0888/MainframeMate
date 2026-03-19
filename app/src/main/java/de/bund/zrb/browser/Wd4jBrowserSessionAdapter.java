package de.bund.zrb.browser;

import de.zrb.bund.newApi.browser.BrowserException;
import de.zrb.bund.newApi.browser.BrowserSession;
import de.zrb.bund.newApi.browser.NavigationResult;
import de.zrb.bund.newApi.browser.TabInfo;

import de.bund.zrb.type.script.WDEvaluateResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Adapter that wraps the wd4j-specific BrowserSession behind the core Browser API.
 * This is the bridge between the abstract BrowserSession interface and the concrete wd4j implementation.
 */
public class Wd4jBrowserSessionAdapter implements BrowserSession {

    private static final Logger LOG = Logger.getLogger(Wd4jBrowserSessionAdapter.class.getName());

    private final de.bund.zrb.mcpserver.browser.BrowserSession delegate;

    public Wd4jBrowserSessionAdapter(de.bund.zrb.mcpserver.browser.BrowserSession delegate) {
        this.delegate = delegate;
    }

    @Override
    public NavigationResult navigate(String url) throws BrowserException {
        try {
            de.bund.zrb.command.response.WDBrowsingContextResult.NavigateResult result = delegate.navigate(url);
            return new NavigationResult(
                    result.getUrl(),
                    result.getNavigation()
            );
        } catch (Exception e) {
            throw new BrowserException("Navigation to '" + url + "' failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void navigateBack() throws BrowserException {
        try {
            delegate.evaluate("window.history.back()", false);
        } catch (Exception e) {
            throw new BrowserException("Navigate back failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void navigateForward() throws BrowserException {
        try {
            delegate.evaluate("window.history.forward()", false);
        } catch (Exception e) {
            throw new BrowserException("Navigate forward failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getCurrentUrl() {
        try {
            WDEvaluateResult result = delegate.evaluate("window.location.href", true);
            if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                return ((WDEvaluateResult.WDEvaluateResultSuccess) result).getResult().asString();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getPageContent() throws BrowserException {
        try {
            WDEvaluateResult result = delegate.evaluate(
                    "document.body ? document.body.innerText : document.documentElement.textContent", true);
            if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                return ((WDEvaluateResult.WDEvaluateResultSuccess) result).getResult().asString();
            }
            return "";
        } catch (Exception e) {
            throw new BrowserException("Failed to read page content: " + e.getMessage(), e);
        }
    }

    @Override
    public String getDomSnapshot() throws BrowserException {
        try {
            WDEvaluateResult result = delegate.evaluate("document.documentElement.outerHTML", true);
            if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                return ((WDEvaluateResult.WDEvaluateResultSuccess) result).getResult().asString();
            }
            return "";
        } catch (Exception e) {
            throw new BrowserException("Failed to capture DOM snapshot: " + e.getMessage(), e);
        }
    }

    @Override
    public String captureScreenshot() throws BrowserException {
        try {
            return delegate.captureScreenshot();
        } catch (Exception e) {
            throw new BrowserException("Screenshot failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void click(String cssSelector) throws BrowserException {
        try {
            delegate.clickElement(cssSelector, null);
        } catch (Exception e) {
            throw new BrowserException("Click on '" + cssSelector + "' failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void clickByRef(String nodeRefId) throws BrowserException {
        try {
            delegate.clickNodeRef(nodeRefId);
        } catch (Exception e) {
            throw new BrowserException("Click on ref '" + nodeRefId + "' failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void type(String cssSelector, String text, boolean clearFirst) throws BrowserException {
        try {
            delegate.typeIntoElement(cssSelector, text, clearFirst, null);
        } catch (Exception e) {
            throw new BrowserException("Type into '" + cssSelector + "' failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void typeByRef(String nodeRefId, String text, boolean clearFirst) throws BrowserException {
        try {
            delegate.typeNodeRef(nodeRefId, text, clearFirst);
        } catch (Exception e) {
            throw new BrowserException("Type into ref '" + nodeRefId + "' failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void select(String cssSelector, String value, String label, Integer index) throws BrowserException {
        try {
            // First register the node, then use selectOptionNodeRef
            String refId = delegate.registerNodeRef(cssSelector);
            if (refId == null) {
                throw new BrowserException("No element found for selector: " + cssSelector);
            }
            delegate.selectOptionNodeRef(refId, value, label, index);
        } catch (BrowserException e) {
            throw e;
        } catch (Exception e) {
            throw new BrowserException("Select on '" + cssSelector + "' failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void scroll(String cssSelector, int deltaX, int deltaY) throws BrowserException {
        try {
            if (cssSelector != null && !cssSelector.isEmpty()) {
                // Scroll element into view
                delegate.evaluate(
                        "document.querySelector('" + cssSelector.replace("'", "\\'")
                      + "').scrollIntoView({behavior:'smooth'})", true);
            } else {
                delegate.evaluate("window.scrollBy(" + deltaX + "," + deltaY + ")", true);
            }
        } catch (Exception e) {
            throw new BrowserException("Scroll failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String executeScript(String script, boolean awaitPromise) throws BrowserException {
        try {
            WDEvaluateResult result = delegate.evaluate(script, awaitPromise);
            if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                return ((WDEvaluateResult.WDEvaluateResultSuccess) result).getResult().asString();
            }
            if (result instanceof WDEvaluateResult.WDEvaluateResultError) {
                String msg = ((WDEvaluateResult.WDEvaluateResultError) result).getExceptionDetails().getText();
                throw new BrowserException("Script error: " + msg);
            }
            return null;
        } catch (BrowserException e) {
            throw e;
        } catch (Exception e) {
            throw new BrowserException("Script execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getActiveTabId() {
        return delegate.getContextId();
    }

    @Override
    public List<TabInfo> listTabs() throws BrowserException {
        try {
            de.bund.zrb.command.response.WDBrowsingContextResult.GetTreeResult tree =
                    delegate.getDriver().browsingContext().getTree();
            List<TabInfo> tabs = new ArrayList<>();
            if (tree.getContexts() != null) {
                for (de.bund.zrb.type.browsingContext.WDInfo ctx : tree.getContexts()) {
                    tabs.add(new TabInfo(
                            ctx.getContext().value(),
                            ctx.getUrl(),
                            "" // title not readily available from getTree
                    ));
                }
            }
            return tabs;
        } catch (Exception e) {
            throw new BrowserException("Failed to list tabs: " + e.getMessage(), e);
        }
    }

    @Override
    public void activateTab(String tabId) throws BrowserException {
        delegate.setContextId(tabId);
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public void close() {
        delegate.close();
    }

    /**
     * Returns the underlying wd4j BrowserSession.
     * For internal use by browser-core tools only – not exposed via the API interface.
     */
    public de.bund.zrb.mcpserver.browser.BrowserSession getDelegate() {
        return delegate;
    }
}

