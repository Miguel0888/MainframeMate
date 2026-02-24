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
 *
 * The JS descriptions (text, aria-label, href, placeholder etc.) are used directly
 * in the output – the CSS locate phase only registers handles for click/type actions.
 */
public class BrowseSnapshotTool implements McpServerTool {

    @Override
    public String name() {
        return "web_snapshot";
    }

    @Override
    public String description() {
        return "Get interactive elements on the current page as NodeRefs (n1, n2, …) for clicking/typing. "
             + "Use the returned NodeRef IDs with web_click, web_type, etc. "
             + "To READ the page text content, use web_read_page instead. "
             + "Modes: 'interactive' (default), 'full' (includes visible page text).";
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

            // Parse the JS output
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

            // Phase 2: CSS locate to get SharedReferences for tagged elements (for click/type actions)
            List<NodeRef> registeredRefs = new ArrayList<NodeRef>();
            if (!elementInfos.isEmpty()) {
                try {
                    registeredRefs = session.locateAndRegister(
                            new WDLocator.CssLocator("[data-mm-ref]"), elementInfos.size());
                } catch (Exception e) {
                    // Fallback: no NodeRefs for actions, but descriptions still shown
                }
            }

            // Phase 3: Build output using JS descriptions (always reliable) + NodeRef IDs (for actions)
            StringBuilder sb = new StringBuilder();
            sb.append("Page: ").append(title).append("\n");
            sb.append("URL: ").append(url).append("\n");

            int refCount = Math.max(registeredRefs.size(), elementInfos.size());
            sb.append("Snapshot: v").append(version).append(" (").append(refCount).append(" elements)\n\n");

            sb.append("Interactive elements:\n");
            for (int i = 0; i < elementInfos.size(); i++) {
                String[] info = elementInfos.get(i);
                String desc = info[3]; // full description from JS (tag[type][name] "label" ->href val="...")

                if (i < registeredRefs.size()) {
                    // Has a NodeRef → bot can click/type it
                    NodeRef ref = registeredRefs.get(i);
                    sb.append("  [").append(ref.getId()).append("] ").append(desc).append("\n");
                    // Also update the registry so the ref has a meaningful name for error messages
                    session.getNodeRefRegistry().updateInfo(ref.getId(), info[2], desc, null, desc);
                } else {
                    // No NodeRef (locate returned fewer), show description without ref
                    sb.append("  ").append(desc).append("\n");
                }
            }

            // If full mode, add page text
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
             + "var allNodes=[];"
             // Collect from main document
             + "var mainNodes=root.querySelectorAll(sel);"
             + "for(var m=0;m<mainNodes.length;m++)allNodes.push(mainNodes[m]);"
             // Also collect from iframes (same-origin only)
             + "try{var iframes=root.querySelectorAll('iframe');"
             + "for(var f=0;f<iframes.length;f++){"
             + "  try{var fd=iframes[f].contentDocument;"
             + "  if(fd){var fn=fd.querySelectorAll(sel);for(var fi=0;fi<fn.length;fi++)allNodes.push(fn[fi]);}}"
             + "  catch(e){}"
             + "}}catch(e){}"
             + "var idx=0;"
             + "for(var i=0;i<allNodes.length&&idx<150;i++){"
             + "  var n=allNodes[i];"
             + "  try{if(!n.offsetParent&&n.tagName!=='BODY'&&n.tagName!=='HTML')continue;}catch(e){continue;}"
             + "  var tag=n.tagName.toLowerCase();"
             + "  var desc='';"
             + "  var al=n.getAttribute('aria-label')||'';"
             + "  var ph=n.getAttribute('placeholder')||'';"
             + "  var tt=n.getAttribute('title')||'';"
             + "  var tp=n.getAttribute('type')||'';"
             + "  var nm=n.getAttribute('name')||'';"
             + "  var hr=n.getAttribute('href')||'';"
             + "  var vl='';try{vl=(n.value||'').substring(0,20);}catch(e){}"
             + "  var tx='';try{tx=(n.innerText||n.textContent||'').trim().substring(0,50).replace(/\\n/g,' ');}catch(e){}"
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
             + "var bodyText='';try{bodyText=(document.body?document.body.innerText:'').substring(0,3000).replace(/\\n{3,}/g,'\\n\\n');}catch(e){}"
             + "r+='TEXT|'+bodyText;"
             + "return r;"
             + "})()";
    }

    private void cleanupMarkers(BrowserSession session) {
        try {
            session.evaluate(
                    "(function(){"
                  + "var els=document.querySelectorAll('[data-mm-ref]');"
                  + "for(var i=0;i<els.length;i++)els[i].removeAttribute('data-mm-ref');"
                  + "try{var ifs=document.querySelectorAll('iframe');"
                  + "for(var f=0;f<ifs.length;f++){"
                  + "  try{var fd=ifs[f].contentDocument;if(fd){"
                  + "    var fe=fd.querySelectorAll('[data-mm-ref]');"
                  + "    for(var j=0;j<fe.length;j++)fe[j].removeAttribute('data-mm-ref');"
                  + "  }}catch(e){}"
                  + "}}catch(e){}"
                  + "})()",
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

