package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.browser.NodeRef;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.bund.zrb.type.browsingContext.WDLocator;
import de.bund.zrb.type.script.WDEvaluateResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Takes a snapshot of the page and registers interactive elements as NodeRefs.
 * Uses a two-phase approach:
 * 1) Single JS call to describe all interactive elements and tag them with data-mm-ref
 * 2) CSS locateNodes to get SharedReferences for the tagged elements
 */
public class BrowseSnapshotTool implements McpServerTool {

    @Override
    public String name() {
        return "web_snapshot";
    }

    @Override
    public String description() {
        return "Get a compact text snapshot of the current page with interactive elements registered as NodeRefs (n1, n2, …). "
             + "Use the returned NodeRef IDs in web_click, web_type, etc. "
             + "Modes: 'interactive' (default, only clickable/input elements), 'full' (all visible text + elements).";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject mode = new JsonObject();
        mode.addProperty("type", "string");
        mode.addProperty("description", "Snapshot mode: 'interactive' (default) or 'full'");
        props.add("mode", mode);

        JsonObject selector = new JsonObject();
        selector.addProperty("type", "string");
        selector.addProperty("description", "Optional CSS selector to scope the snapshot to a subtree");
        props.add("selector", selector);

        schema.add("properties", props);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String mode = params.has("mode") ? params.get("mode").getAsString() : "interactive";
        String selector = params.has("selector") ? params.get("selector").getAsString() : null;

        try {
            // Clear previous refs
            session.getNodeRefRegistry().invalidateAll();
            int version = session.getNodeRefRegistry().getSnapshotVersion();

            // Phase 1: Single JS call to describe all interactive elements and tag them
            String scope = selector != null ? "document.querySelector('" + escapeJs(selector) + "')" : "document";
            String describeScript = buildDescribeScript(scope);
            String described = evalString(session, describeScript);

            // Parse the element descriptions
            String[] lines = described.split("\n");
            List<String[]> elementInfos = new ArrayList<String[]>();
            String title = "";
            String url = "";
            String pageText = "";

            for (String line : lines) {
                if (line.startsWith("EL|")) {
                    String[] parts = line.split("\\|", 4);
                    if (parts.length >= 4) {
                        elementInfos.add(parts);
                    }
                } else if (line.startsWith("TITLE|")) {
                    title = line.substring(6);
                } else if (line.startsWith("URL|")) {
                    url = line.substring(4);
                } else if (line.startsWith("TEXT|")) {
                    pageText = line.substring(5);
                }
            }

            // Phase 2: CSS locate to get SharedReferences for tagged elements
            List<NodeRef> refs = new ArrayList<NodeRef>();
            if (!elementInfos.isEmpty()) {
                try {
                    List<NodeRef> locateResult = session.locateAndRegister(
                            new WDLocator.CssLocator("[data-mm-ref]"), elementInfos.size());

                    // Update each NodeRef with the description from our JS
                    for (int i = 0; i < Math.min(locateResult.size(), elementInfos.size()); i++) {
                        NodeRef ref = locateResult.get(i);
                        String[] info = elementInfos.get(i);
                        String tag = info[2];
                        String desc = info[3];
                        session.getNodeRefRegistry().updateInfo(ref.getId(), tag, desc, null, desc);
                        refs.add(ref);
                    }
                } catch (Exception e) {
                    // Fallback: no NodeRefs, just text
                }
            }

            // Phase 3: Build output text
            StringBuilder sb = new StringBuilder();
            sb.append("Page: ").append(title).append("\n");
            sb.append("URL: ").append(url).append("\n");
            sb.append("Snapshot: v").append(version).append(" (").append(refs.size()).append(" elements)\n\n");

            sb.append("Interactive elements:\n");
            if (refs.isEmpty() && !elementInfos.isEmpty()) {
                // Fallback without NodeRefs
                for (String[] info : elementInfos) {
                    sb.append("  <").append(info[2]).append("> ").append(info[3]).append("\n");
                }
            } else {
                for (NodeRef ref : refs) {
                    sb.append("  ").append(ref.toCompactString()).append("\n");
                }
            }

            if ("full".equals(mode) && !pageText.isEmpty()) {
                sb.append("\nPage text:\n").append(pageText);
            }

            // Cleanup marker attributes
            cleanupMarkers(session);

            return ToolResult.text(sb.toString());
        } catch (Exception e) {
            cleanupMarkers(session);
            return ToolResult.error("Snapshot failed: " + e.getMessage());
        }
    }

    private String buildDescribeScript(String scopeExpr) {
        return "(function(){"
             + "var root=" + scopeExpr + "||document;"
             + "var r='TITLE|'+document.title+'\\n'+'URL|'+window.location.href+'\\n';"
             + "var sel='a,button,input,select,textarea,[role=button],[role=link],[role=tab],[role=menuitem],[role=checkbox],[role=radio],[onclick],[contenteditable=true]';"
             + "var nodes=root.querySelectorAll(sel);"
             + "var idx=0;"
             + "for(var i=0;i<nodes.length&&idx<150;i++){"
             + "  var n=nodes[i];"
             + "  if(!n.offsetParent&&n.tagName!=='BODY'&&n.tagName!=='HTML')continue;"
             + "  var tag=n.tagName.toLowerCase();"
             + "  var desc='';"
             + "  var al=n.getAttribute('aria-label')||'';"
             + "  var ph=n.getAttribute('placeholder')||'';"
             + "  var tt=n.getAttribute('title')||'';"
             + "  var tp=n.getAttribute('type')||'';"
             + "  var nm=n.getAttribute('name')||'';"
             + "  var hr=n.getAttribute('href')||'';"
             + "  var vl=(n.value||'').substring(0,20);"
             + "  var tx=(n.innerText||n.textContent||'').trim().substring(0,50).replace(/\\n/g,' ');"
             + "  if(tp)desc+=tag+'['+tp+']';else desc+=tag;"
             + "  if(nm)desc+='[name='+nm+']';"
             + "  if(al)desc+=' \"'+al+'\"';"
             + "  else if(ph)desc+=' \"'+ph+'\"';"
             + "  else if(tt)desc+=' \"'+tt+'\"';"
             + "  else if(tx.length>0)desc+=' \"'+tx.substring(0,40)+'\"';"
             + "  if(hr&&hr.length<80&&hr.indexOf('javascript:')<0)desc+=' ->'+hr;"
             + "  if(vl&&(tag==='input'||tag==='textarea'))desc+=' val=\"'+vl+'\"';"
             + "  n.setAttribute('data-mm-ref',''+idx);"
             + "  r+='EL|'+idx+'|'+tag+'|'+desc+'\\n';"
             + "  idx++;"
             + "}"
             + "var bodyText=(document.body?document.body.innerText:'').substring(0,3000).replace(/\\n{3,}/g,'\\n\\n');"
             + "r+='TEXT|'+bodyText;"
             + "return r;"
             + "})()";
    }

    private void cleanupMarkers(BrowserSession session) {
        try {
            session.evaluate(
                    "(function(){var els=document.querySelectorAll('[data-mm-ref]');"
                  + "for(var i=0;i<els.length;i++)els[i].removeAttribute('data-mm-ref');})()",
                    true);
        } catch (Exception ignored) {}
    }

    private String evalString(BrowserSession session, String script) {
        try {
            Object result = session.evaluate(script, true);
            if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                return ((WDEvaluateResult.WDEvaluateResultSuccess) result)
                        .getResult().asString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String escapeJs(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}

