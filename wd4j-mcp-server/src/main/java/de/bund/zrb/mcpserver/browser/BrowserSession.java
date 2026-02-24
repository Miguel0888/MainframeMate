package de.bund.zrb.mcpserver.browser;

import de.bund.zrb.WDWebSocketImpl;
import de.bund.zrb.WebDriver;
import de.bund.zrb.command.response.WDBrowsingContextResult;
import de.bund.zrb.manager.*;
import de.bund.zrb.type.browsingContext.WDBrowsingContext;
import de.bund.zrb.type.browsingContext.WDLocator;
import de.bund.zrb.type.script.*;

import java.net.URI;
import java.util.ArrayList;
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
    private Process browserProcess;
    private final NodeRefRegistry nodeRefRegistry = new NodeRefRegistry();
    private final List<String> consoleLogs = new ArrayList<String>();

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

    // ── NodeRef & Snapshot ──────────────────────────────────────────

    public NodeRefRegistry getNodeRefRegistry() {
        return nodeRefRegistry;
    }

    public List<String> getConsoleLogs() {
        return new ArrayList<String>(consoleLogs);
    }

    public void clearConsoleLogs() {
        consoleLogs.clear();
    }

    public void addConsoleLog(String log) {
        if (consoleLogs.size() > 1000) {
            consoleLogs.remove(0);
        }
        consoleLogs.add(log);
    }

    /**
     * Locate nodes using WD4J locateNodes and register them as NodeRefs.
     */
    public List<NodeRef> locateAndRegister(WDLocator locator, int maxCount) {
        String ctx = resolveContext(null);
        WDBrowsingContextResult.LocateNodesResult result =
                driver.browsingContext().locateNodes(ctx, locator, maxCount);

        List<NodeRef> refs = new ArrayList<NodeRef>();
        if (result.getNodes() != null) {
            for (WDRemoteValue.NodeRemoteValue node : result.getNodes()) {
                WDRemoteReference.SharedReference sharedRef = node.getSharedIdReference();
                String tag = "?";
                String text = "";
                if (node.getValue() != null) {
                    if (node.getValue().getLocalName() != null) tag = node.getValue().getLocalName();
                    if (node.getValue().getNodeValue() != null) text = node.getValue().getNodeValue();
                }
                // Get accessible info via JS later if needed
                NodeRef ref = nodeRefRegistry.register(tag, text, null, null, true, sharedRef);
                refs.add(ref);
            }
        }
        return refs;
    }

    /**
     * Click a node by its NodeRef ID.
     */
    public void clickNodeRef(String nodeRefId) {
        NodeRefRegistry.Entry entry = nodeRefRegistry.resolve(nodeRefId);
        WDTarget target = new WDTarget.ContextTarget(new WDBrowsingContext(resolveContext(null)));
        List<WDLocalValue> args = Collections.<WDLocalValue>singletonList(entry.sharedRef);
        driver.script().callFunction(
                "function(el) { el.scrollIntoView({block:'center'}); el.click(); }",
                true, target, args);
    }

    /**
     * Type text into a node by its NodeRef ID.
     */
    public void typeNodeRef(String nodeRefId, String text, boolean clearFirst) {
        NodeRefRegistry.Entry entry = nodeRefRegistry.resolve(nodeRefId);
        WDTarget target = new WDTarget.ContextTarget(new WDBrowsingContext(resolveContext(null)));
        List<WDLocalValue> args = new ArrayList<WDLocalValue>();
        args.add(entry.sharedRef);
        args.add(new WDPrimitiveProtocolValue.StringValue(text));
        args.add(new WDPrimitiveProtocolValue.BooleanValue(clearFirst));
        driver.script().callFunction(
                "function(el,text,clear){el.focus();if(clear){el.value='';}el.value+=text;"
              + "el.dispatchEvent(new Event('input',{bubbles:true}));"
              + "el.dispatchEvent(new Event('change',{bubbles:true}));}",
                true, target, args);
    }

    /**
     * Hover over a node by its NodeRef ID.
     */
    public void hoverNodeRef(String nodeRefId) {
        NodeRefRegistry.Entry entry = nodeRefRegistry.resolve(nodeRefId);
        WDTarget target = new WDTarget.ContextTarget(new WDBrowsingContext(resolveContext(null)));
        List<WDLocalValue> args = Collections.<WDLocalValue>singletonList(entry.sharedRef);
        driver.script().callFunction(
                "function(el){el.scrollIntoView({block:'center'});"
              + "el.dispatchEvent(new MouseEvent('mouseover',{bubbles:true}));"
              + "el.dispatchEvent(new MouseEvent('mouseenter',{bubbles:true}));}",
                true, target, args);
    }

    /**
     * Select an option in a <select> by its NodeRef ID.
     */
    public void selectOptionNodeRef(String nodeRefId, String value, String label, Integer index) {
        NodeRefRegistry.Entry entry = nodeRefRegistry.resolve(nodeRefId);
        WDTarget target = new WDTarget.ContextTarget(new WDBrowsingContext(resolveContext(null)));
        String criteria;
        if (value != null) {
            criteria = "'value','" + value.replace("'", "\\'") + "'";
        } else if (label != null) {
            criteria = "'label','" + label.replace("'", "\\'") + "'";
        } else if (index != null) {
            criteria = "'index'," + index;
        } else {
            throw new IllegalArgumentException("Provide value, label, or index for select");
        }
        List<WDLocalValue> args = Collections.<WDLocalValue>singletonList(entry.sharedRef);
        driver.script().callFunction(
                "function(el){var m=" + criteria + ";"
              + "var opts=el.options;for(var i=0;i<opts.length;i++){"
              + "if((m==='value'&&opts[i].value===arguments[1])"
              + "||(m==='label'&&opts[i].text===arguments[1])"
              + "||(m==='index'&&i===arguments[1])){el.selectedIndex=i;break;}}"
              + "el.dispatchEvent(new Event('change',{bubbles:true}));}",
                true, target, args);
    }

    // ── Internal ────────────────────────────────────────────────────

    private String resolveContext(String ctxId) {
        String ctx = ctxId != null ? ctxId : this.contextId;
        if (ctx == null || ctx.isEmpty()) {
            throw new IllegalStateException("No active browsing context. The browser may not have started correctly. Check the Websearch plugin settings.");
        }
        return ctx;
    }
}

