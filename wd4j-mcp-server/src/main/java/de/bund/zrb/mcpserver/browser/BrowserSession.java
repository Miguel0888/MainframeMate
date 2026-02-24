package de.bund.zrb.mcpserver.browser;

import de.bund.zrb.WDWebSocketImpl;
import de.bund.zrb.WebDriver;
import de.bund.zrb.command.response.WDBrowsingContextResult;
import de.bund.zrb.manager.*;
import de.bund.zrb.type.browsingContext.WDBrowsingContext;
import de.bund.zrb.type.browsingContext.WDLocator;
import de.bund.zrb.type.script.*;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Holds the state of a single browser session: WebSocket connection,
 * WebDriver instance, and the active browsing context (tab).
 */
public class BrowserSession {

    private WDWebSocketImpl webSocket;
    private WebDriver driver;
    private String contextId;
    private String sessionId;
    private Process browserProcess; // non-null if we launched the browser ourselves

    // ── Connection ──────────────────────────────────────────────────

    /**
     * Connect to an already-running browser via its BiDi WebSocket URL.
     */
    public void connect(String bidiWebSocketUrl, String browserName, boolean createContext)
            throws ExecutionException, InterruptedException {
        URI uri = URI.create(bidiWebSocketUrl);
        this.webSocket = new WDWebSocketImpl(uri, 30_000.0);
        this.driver = new WebDriver(webSocket);

        driver.connect(browserName);

        if (createContext) {
            WDBrowsingContextResult.CreateResult ctx = driver.browsingContext().create();
            this.contextId = ctx.getContext();
        }
    }

    /**
     * Launch a browser process, parse its BiDi WebSocket URL, then connect.
     */
    public void launchAndConnect(String browserPath, List<String> args, boolean headless, long timeoutMs)
            throws Exception {
        BrowserLauncher.LaunchResult launchResult = BrowserLauncher.launchWithProcess(browserPath, args, headless, timeoutMs);
        this.browserProcess = launchResult.process;
        connect(launchResult.wsUrl, "firefox", true);
    }

    // ── Navigation ──────────────────────────────────────────────────

    public WDBrowsingContextResult.NavigateResult navigate(String url) {
        return navigate(url, null);
    }

    public WDBrowsingContextResult.NavigateResult navigate(String url, String ctxId) {
        String ctx = resolveContext(ctxId);
        return driver.browsingContext().navigate(url, ctx);
    }

    // ── Screenshot ──────────────────────────────────────────────────

    public String captureScreenshot() {
        return captureScreenshot(null);
    }

    public String captureScreenshot(String ctxId) {
        String ctx = resolveContext(ctxId);
        WDBrowsingContextResult.CaptureScreenshotResult result = driver.browsingContext().captureScreenshot(ctx);
        return result.getData();
    }

    // ── Script evaluation ───────────────────────────────────────────

    public WDEvaluateResult evaluate(String script, boolean awaitPromise) {
        return evaluate(script, awaitPromise, null);
    }

    public WDEvaluateResult evaluate(String script, boolean awaitPromise, String ctxId) {
        String ctx = resolveContext(ctxId);
        WDTarget target = new WDTarget.ContextTarget(new WDBrowsingContext(ctx));
        return driver.script().evaluate(script, target, awaitPromise);
    }

    // ── Node location ───────────────────────────────────────────────

    public WDBrowsingContextResult.LocateNodesResult locateNodes(String cssSelector, int maxCount) {
        return locateNodes(cssSelector, maxCount, null);
    }

    public WDBrowsingContextResult.LocateNodesResult locateNodes(String cssSelector, int maxCount, String ctxId) {
        String ctx = resolveContext(ctxId);
        WDLocator locator = new WDLocator.CssLocator(cssSelector);
        return driver.browsingContext().locateNodes(ctx, locator, maxCount);
    }

    // ── Click via JS ────────────────────────────────────────────────

    public void clickElement(String cssSelector, String ctxId) {
        String ctx = resolveContext(ctxId);
        // Locate the element
        WDBrowsingContextResult.LocateNodesResult nodes = locateNodes(cssSelector, 1, ctx);
        if (nodes.getNodes() == null || nodes.getNodes().isEmpty()) {
            throw new RuntimeException("No element found for selector: " + cssSelector);
        }
        WDRemoteValue.NodeRemoteValue node = nodes.getNodes().get(0);
        WDRemoteReference.SharedReference sharedRef = node.getSharedIdReference();

        // Click via callFunction with the element as argument
        WDTarget target = new WDTarget.ContextTarget(new WDBrowsingContext(ctx));
        List<WDLocalValue> args = Collections.<WDLocalValue>singletonList(sharedRef);
        driver.script().callFunction(
                "function(el) { el.click(); }",
                true,
                target,
                args
        );
    }

    // ── Type into element ───────────────────────────────────────────

    public void typeIntoElement(String cssSelector, String text, boolean clearFirst, String ctxId) {
        String ctx = resolveContext(ctxId);

        // Locate the element
        WDBrowsingContextResult.LocateNodesResult nodes = locateNodes(cssSelector, 1, ctx);
        if (nodes.getNodes() == null || nodes.getNodes().isEmpty()) {
            throw new RuntimeException("No element found for selector: " + cssSelector);
        }
        WDRemoteValue.NodeRemoteValue node = nodes.getNodes().get(0);
        WDRemoteReference.SharedReference sharedRef = node.getSharedIdReference();
        WDTarget target = new WDTarget.ContextTarget(new WDBrowsingContext(ctx));

        // Focus + optional clear + set value via JS
        StringBuilder js = new StringBuilder();
        js.append("function(el, text, clear) { ");
        js.append("  el.focus(); ");
        js.append("  if (clear) { el.value = ''; } ");
        js.append("  el.value += text; ");
        js.append("  el.dispatchEvent(new Event('input', {bubbles: true})); ");
        js.append("  el.dispatchEvent(new Event('change', {bubbles: true})); ");
        js.append("}");

        List<WDLocalValue> args = new java.util.ArrayList<WDLocalValue>();
        args.add(sharedRef);
        args.add(new WDPrimitiveProtocolValue.StringValue(text));
        args.add(new WDPrimitiveProtocolValue.BooleanValue(clearFirst));

        driver.script().callFunction(js.toString(), true, target, args);
    }

    // ── Close ───────────────────────────────────────────────────────

    public void close() {
        try {
            if (contextId != null && driver != null) {
                try {
                    driver.browsingContext().close(contextId);
                } catch (Exception e) {
                    System.err.println("[MCP] Error closing context: " + e.getMessage());
                }
            }
            if (webSocket != null) {
                webSocket.close();
            }
            if (browserProcess != null) {
                browserProcess.destroyForcibly();
                browserProcess = null;
            }
        } finally {
            driver = null;
            webSocket = null;
            contextId = null;
        }
    }

    // ── State queries ───────────────────────────────────────────────

    public boolean isConnected() {
        return driver != null && driver.isConnected();
    }

    public String getContextId() {
        return contextId;
    }

    public void setContextId(String contextId) {
        this.contextId = contextId;
    }

    public WebDriver getDriver() {
        return driver;
    }

    // ── Internal ────────────────────────────────────────────────────

    private String resolveContext(String ctxId) {
        String ctx = ctxId != null ? ctxId : this.contextId;
        if (ctx == null || ctx.isEmpty()) {
            throw new IllegalStateException("No active browsing context. Call browser_open or browser_launch first.");
        }
        return ctx;
    }
}

