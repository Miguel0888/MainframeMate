package de.bund.zrb.mcpserver.browser;

import de.bund.zrb.api.WDWebSocket;
import de.bund.zrb.WDWebSocketImpl;
import de.bund.zrb.WebDriver;
import de.bund.zrb.chrome.ChromeBidiWebSocketImpl;
import de.bund.zrb.command.response.WDBrowsingContextResult;
import de.bund.zrb.manager.*;
import de.bund.zrb.type.browsingContext.WDBrowsingContext;
import de.bund.zrb.type.browsingContext.WDLocator;
import de.bund.zrb.type.browsingContext.WDReadinessState;
import de.bund.zrb.type.script.*;

import de.bund.zrb.type.log.WDLogEntry;
import de.bund.zrb.type.session.WDSubscriptionRequest;
import de.bund.zrb.event.WDLogEvent;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Holds the state of a single browser session: WebSocket connection,
 * WebDriver instance, and the active browsing context (tab).
 */
public class BrowserSession {

    private static final Logger LOG = Logger.getLogger(BrowserSession.class.getName());

    private WDWebSocket webSocket;
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

        // Subscribe to browser console logs via BiDi
        subscribeToConsoleLogs();

        if (createContext) {
            WDBrowsingContextResult.CreateResult ctx = driver.browsingContext().create();
            this.contextId = ctx.getContext();
        }
    }

    /**
     * Connect to Chrome via its CDP WebSocket URL, using the BiDi mapper adapter.
     * Chrome does not support native BiDi - instead, a JavaScript mapper is injected
     * into a hidden tab that translates BiDi ↔ CDP.
     */
    public void connectChrome(String cdpWebSocketUrl, boolean createContext)
            throws Exception {
        LOG.info("[BrowserSession] Connecting to Chrome via BiDi mapper: " + cdpWebSocketUrl);
        ChromeBidiWebSocketImpl chromeBidiWs = new ChromeBidiWebSocketImpl(cdpWebSocketUrl, false);
        this.webSocket = chromeBidiWs;
        this.driver = new WebDriver(chromeBidiWs);

        // Chrome mapper handles session internally; use reconnect with a placeholder session ID
        driver.reconnect("chrome-bidi-mapper");

        // Subscribe to browser console logs via BiDi
        subscribeToConsoleLogs();

        if (createContext) {
            WDBrowsingContextResult.CreateResult ctx = driver.browsingContext().create();
            this.contextId = ctx.getContext();
        }
    }

    /**
     * Subscribe to BiDi log.entryAdded events and pipe them to consoleLogs.
     */
    private void subscribeToConsoleLogs() {
        try {
            WDSubscriptionRequest logSubscription = new WDSubscriptionRequest(
                    Collections.singletonList("log.entryAdded"));
            Consumer<WDLogEvent.EntryAdded> logListener = event -> {
                if (event != null && event.getParams() != null) {
                    WDLogEntry entry = event.getParams();
                    String level = "info";
                    String text = "(no text)";
                    if (entry instanceof WDLogEntry.BaseWDLogEntry) {
                        WDLogEntry.BaseWDLogEntry base = (WDLogEntry.BaseWDLogEntry) entry;
                        level = base.getLevel() != null ? base.getLevel().value() : "info";
                        text = base.getText() != null ? base.getText() : "(no text)";
                    }
                    String logLine = "[" + level.toUpperCase() + "] " + text;
                    addConsoleLog(logLine);
                    LOG.fine("[BrowserLog] " + logLine);
                }
            };
            driver.addEventListener(logSubscription, logListener);
            LOG.info("[BrowserSession] Subscribed to log.entryAdded via BiDi");
        } catch (Exception e) {
            LOG.warning("[BrowserSession] Failed to subscribe to log.entryAdded: " + e.getMessage());
        }
    }

    /**
     * Launch a browser process, parse its BiDi WebSocket URL, then connect.
     */
    public void launchAndConnect(String browserPath, List<String> args, boolean headless, long timeoutMs)
            throws Exception {
        launchAndConnect(browserPath, args, headless, timeoutMs, 0);
    }

    /**
     * Launch a browser process with a specific debug port, parse its WebSocket URL, then connect.
     *
     * @param debugPort  the debugging port to use; 0 = auto-select a free port
     */
    public void launchAndConnect(String browserPath, List<String> args, boolean headless, long timeoutMs, int debugPort)
            throws Exception {
        BrowserLauncher.LaunchResult launchResult = BrowserLauncher.launchWithProcess(browserPath, args, headless, timeoutMs, debugPort);
        this.browserProcess = launchResult.process;

        if (launchResult.browserType == BrowserLauncher.BrowserType.CHROME) {
            // Chrome: wsUrl is the CDP webSocketDebuggerUrl, route through BiDi mapper
            connectChrome(launchResult.wsUrl, true);
        } else {
            // Firefox: wsUrl is the native BiDi endpoint
            connect(launchResult.wsUrl, launchResult.browserType.bidiName(), true);
        }
    }

    // ── Navigation ──────────────────────────────────────────────────

    public WDBrowsingContextResult.NavigateResult navigate(String url) {
        return navigate(url, null);
    }

    public WDBrowsingContextResult.NavigateResult navigate(String url, String ctxId) {
        String ctx = resolveContext(ctxId);
        // Use INTERACTIVE readiness state to avoid timeouts on heavy pages
        // (e.g. news sites with many ads/trackers that never reach "complete").
        return driver.browsingContext().navigate(url, ctx, WDReadinessState.INTERACTIVE);
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
            closeWebSocket();
            destroyBrowserProcess();
        } finally {
            driver = null;
            contextId = null;
        }
    }

    /**
     * Forcefully kill the browser process and reset all session state.
     * After calling this, the next getSession() call in WebSearchBrowserManager
     * will detect isConnected()==false and re-launch the browser.
     */
    public void killBrowserProcess() {
        System.err.println("[BrowserSession] Killing browser process due to timeout/hang");
        try {
            closeWebSocket();
        } finally {
            destroyBrowserProcess();
            driver = null;
            contextId = null;
            nodeRefRegistry.invalidateAll();
        }
    }

    // ── Internal process lifecycle ──────────────────────────────────

    /**
     * Close the WebSocket connection, ignoring errors.
     */
    private void closeWebSocket() {
        if (webSocket != null) {
            try { webSocket.close(); } catch (Exception ignored) {}
            webSocket = null;
        }
    }

    /**
     * Destroy only the browser process that THIS session started.
     * Never kills other browser instances. This is the single place
     * where the process is terminated — all callers delegate here.
     */
    private void destroyBrowserProcess() {
        if (browserProcess == null) return;
        if (!browserProcess.isAlive()) {
            browserProcess = null;
            return;
        }
        long pid = getPid(browserProcess);
        System.err.println("[BrowserSession] Destroying browser process (pid=" + pid + ")");
        browserProcess.destroyForcibly();
        try {
            browserProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        browserProcess = null;
    }

    /**
     * Best-effort PID extraction for logging. Returns -1 on Java 8.
     */
    private static long getPid(Process proc) {
        try {
            // Java 9+: Process.pid() via reflection (project targets Java 8)
            java.lang.reflect.Method pidMethod = proc.getClass().getMethod("pid");
            return (Long) pidMethod.invoke(proc);
        } catch (Exception e) {
            return -1;
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

    /**
     * Returns the browser {@link Process} that was started by {@link #launchAndConnect},
     * or {@code null} if the session was created via {@link #connect} (external browser).
     * Used for targeted cleanup – only this process should be killed, never all Firefox instances.
     */
    public Process getBrowserProcess() {
        return browserProcess;
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
     * Locate elements by visible text content via JS, then register as NodeRefs.
     * Workaround for Firefox not supporting innerText locator in BiDi yet.
     */
    public List<NodeRef> locateByTextAndRegister(String searchText, int maxCount) {
        String ctx = resolveContext(null);
        String escaped = searchText.replace("\\", "\\\\").replace("'", "\\'");

        // Step 1: Find matching elements via JS and tag them with a unique data attribute
        String markerId = "mm-locate-" + System.currentTimeMillis();
        String findScript =
                "(function(){"
              + "var q='" + escaped + "'.toLowerCase();"
              + "var all=document.querySelectorAll('*');"
              + "var found=0;"
              + "for(var i=0;i<all.length&&found<" + maxCount + ";i++){"
              + "  var el=all[i];"
              + "  if(el.children.length>3)continue;"  // skip containers, prefer leaf nodes
              + "  var t=(el.innerText||el.textContent||'').trim();"
              + "  if(t.toLowerCase().indexOf(q)>=0){"
              + "    el.setAttribute('data-mm-locate','" + markerId + "');"
              + "    found++;"
              + "  }"
              + "}"
              + "return found;"
              + "})()";

        try {
            WDEvaluateResult evalResult = evaluate(findScript, true, ctx);
            int count = 0;
            if (evalResult instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                try {
                    count = Integer.parseInt(
                            ((WDEvaluateResult.WDEvaluateResultSuccess) evalResult).getResult().asString());
                } catch (NumberFormatException ignored) {}
            }

            if (count == 0) {
                return new ArrayList<NodeRef>();
            }

            // Step 2: Use CSS locator to find the tagged elements (gets us SharedReferences)
            WDLocator cssLocator = new WDLocator.CssLocator("[data-mm-locate='" + markerId + "']");
            List<NodeRef> refs = locateAndRegister(cssLocator, maxCount);

            // Step 3: Clean up the marker attributes
            String cleanupScript =
                    "(function(){"
                  + "var els=document.querySelectorAll('[data-mm-locate=\"" + markerId + "\"]');"
                  + "for(var i=0;i<els.length;i++)els[i].removeAttribute('data-mm-locate');"
                  + "})()";
            evaluate(cleanupScript, true, ctx);

            // Step 4: Enrich NodeRefs with text/tag info via JS
            enrichNodeRefsViaJs(refs, ctx);

            return refs;
        } catch (Exception e) {
            // Cleanup on error
            try {
                evaluate("(function(){var els=document.querySelectorAll('[data-mm-locate]');"
                       + "for(var i=0;i<els.length;i++)els[i].removeAttribute('data-mm-locate');})()", true, ctx);
            } catch (Exception ignored) {}
            throw new RuntimeException("Text locate failed: " + e.getMessage(), e);
        }
    }

    /**
     * Locate elements by accessible name/role via JS, then register as NodeRefs.
     * Workaround for browsers that don't fully support accessibility locator.
     */
    public List<NodeRef> locateByAriaAndRegister(String name, String role, int maxCount) {
        // Try the native accessibility locator first
        try {
            WDLocator.AccessibilityLocator.Value val = new WDLocator.AccessibilityLocator.Value(name, role);
            WDLocator locator = new WDLocator.AccessibilityLocator(val);
            List<NodeRef> refs = locateAndRegister(locator, maxCount);
            if (!refs.isEmpty()) {
                enrichNodeRefsViaJs(refs, resolveContext(null));
                return refs;
            }
        } catch (Exception ignored) {
            // Browser doesn't support it, fall back to JS
        }

        // Fallback: search by aria-label, role attribute, and button/link text
        String escaped = name.replace("\\", "\\\\").replace("'", "\\'");
        String markerId = "mm-aria-" + System.currentTimeMillis();
        String roleFilter = role != null ? "&&(el.getAttribute('role')=='" + role.replace("'", "\\'") + "'||el.tagName.toLowerCase()=='" + role.replace("'", "\\'") + "')" : "";

        String findScript =
                "(function(){"
              + "var q='" + escaped + "'.toLowerCase();"
              + "var all=document.querySelectorAll('*');"
              + "var found=0;"
              + "for(var i=0;i<all.length&&found<" + maxCount + ";i++){"
              + "  var el=all[i];"
              + "  var al=(el.getAttribute('aria-label')||'').toLowerCase();"
              + "  var txt=(el.textContent||'').trim().toLowerCase();"
              + "  var title=(el.getAttribute('title')||'').toLowerCase();"
              + "  if((al.indexOf(q)>=0||txt.indexOf(q)>=0||title.indexOf(q)>=0)" + roleFilter + "){"
              + "    el.setAttribute('data-mm-locate','" + markerId + "');"
              + "    found++;"
              + "  }"
              + "}"
              + "return found;"
              + "})()";

        String ctx = resolveContext(null);
        try {
            evaluate(findScript, true, ctx);
            WDLocator cssLocator = new WDLocator.CssLocator("[data-mm-locate='" + markerId + "']");
            List<NodeRef> refs = locateAndRegister(cssLocator, maxCount);
            evaluate("(function(){var els=document.querySelectorAll('[data-mm-locate=\"" + markerId + "\"]');"
                   + "for(var i=0;i<els.length;i++)els[i].removeAttribute('data-mm-locate');})()", true, ctx);
            enrichNodeRefsViaJs(refs, ctx);
            return refs;
        } catch (Exception e) {
            try {
                evaluate("(function(){var els=document.querySelectorAll('[data-mm-locate]');"
                       + "for(var i=0;i<els.length;i++)els[i].removeAttribute('data-mm-locate');})()", true, ctx);
            } catch (Exception ignored) {}
            throw new RuntimeException("ARIA locate failed: " + e.getMessage(), e);
        }
    }

    /**
     * Enrich NodeRefs with visible text, tag name, and aria-label via JS calls.
     * Convenience method using the default context.
     */
    public void enrichNodeRefsViaJs(List<NodeRef> refs) {
        enrichNodeRefsViaJs(refs, resolveContext(null));
    }

    /**
     * Enrich NodeRefs with visible text, tag name, and aria-label via a single JS call per ref.
     * Replaces NodeRef objects in the list with enriched versions.
     */
    public void enrichNodeRefsViaJs(List<NodeRef> refs, String ctx) {
        if (refs.isEmpty()) return;
        for (int ri = 0; ri < refs.size(); ri++) {
            NodeRef ref = refs.get(ri);
            try {
                NodeRefRegistry.Entry entry = nodeRefRegistry.resolve(ref.getId());
                WDTarget target = new WDTarget.ContextTarget(new WDBrowsingContext(ctx));
                List<WDLocalValue> args = Collections.<WDLocalValue>singletonList(entry.sharedRef);
                WDEvaluateResult result = driver.script().callFunction(
                        "function(el){"
                      + "var tag=el.tagName.toLowerCase();"
                      + "var al=el.getAttribute('aria-label')||'';"
                      + "var ph=el.getAttribute('placeholder')||'';"
                      + "var tt=el.getAttribute('title')||'';"
                      + "var tp=el.getAttribute('type')||'';"
                      + "var nm=el.getAttribute('name')||'';"
                      + "var hr='';"
                      + "try{var rawHr=el.getAttribute('href')||'';"
                      + "if(tag==='a'&&el.href){hr=el.href;}"
                      + "else if(rawHr&&rawHr.length>0){try{hr=new URL(rawHr,window.location.href).href;}catch(ue){hr=rawHr;}}"
                      + "}catch(e){}"
                      + "var vl=(el.value||'').substring(0,20);"
                      + "var tx=(el.innerText||el.textContent||'').trim().substring(0,50).replace(/\\n/g,' ');"
                      + "var d=tp?tag+'['+tp+']':tag;"
                      + "if(nm)d+='[name='+nm+']';"
                      + "if(al)d+=' \"'+al+'\"';"
                      + "else if(ph)d+=' \"'+ph+'\"';"
                      + "else if(tt)d+=' \"'+tt+'\"';"
                      + "else if(tx.length>0)d+=' \"'+tx.substring(0,40)+'\"';"
                      + "if(hr&&hr.indexOf('javascript:')<0)d+=' ->'+(hr.length>200?hr.substring(0,200):hr);"
                      + "if(vl&&(tag==='input'||tag==='textarea'))d+=' val=\"'+vl+'\"';"
                      + "return d;"
                      + ";}",
                        true, target, args);
                if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                    String desc = ((WDEvaluateResult.WDEvaluateResultSuccess) result).getResult().asString();
                    if (desc != null && !desc.isEmpty() && !desc.startsWith("[Object:")) {
                        String tag = desc.contains("[") ? desc.substring(0, desc.indexOf('[')) : desc.split(" ")[0];
                        nodeRefRegistry.updateInfo(ref.getId(), tag, desc, null, desc);
                        // Replace in list so caller sees the updated version
                        refs.set(ri, nodeRefRegistry.resolve(ref.getId()).nodeRef);
                    }
                }
            } catch (Exception ignored) {
                // Skip enrichment for this ref
            }
        }
    }

    // ── NodeRef-based Actions ────────────────────────────────────────

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
     * Select an option in a <select> by its NodeRef ID.
     */
    public void selectOptionNodeRef(String nodeRefId, String value, String label, Integer index) {
        NodeRefRegistry.Entry entry = nodeRefRegistry.resolve(nodeRefId);
        WDTarget target = new WDTarget.ContextTarget(new WDBrowsingContext(resolveContext(null)));

        String jsValue;
        if (value != null) {
            jsValue = "'" + value.replace("'", "\\'") + "'";
        } else if (label != null) {
            jsValue = "'" + label.replace("'", "\\'") + "'";
        } else if (index != null) {
            jsValue = String.valueOf(index);
        } else {
            throw new IllegalArgumentException("Provide value, label, or index for select");
        }

        String mode = value != null ? "value" : label != null ? "label" : "index";

        List<WDLocalValue> args = new ArrayList<WDLocalValue>();
        args.add(entry.sharedRef);
        args.add(new WDPrimitiveProtocolValue.StringValue(mode));
        args.add(new WDPrimitiveProtocolValue.StringValue(jsValue));

        driver.script().callFunction(
                "function(el,mode,val){"
              + "var opts=el.options;"
              + "for(var i=0;i<opts.length;i++){"
              + "  if((mode==='value'&&opts[i].value===val)"
              + "  ||(mode==='label'&&opts[i].text===val)"
              + "  ||(mode==='index'&&i===parseInt(val))){"
              + "    el.selectedIndex=i;break;"
              + "  }"
              + "}"
              + "el.dispatchEvent(new Event('change',{bubbles:true}));"
              + "}",
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

