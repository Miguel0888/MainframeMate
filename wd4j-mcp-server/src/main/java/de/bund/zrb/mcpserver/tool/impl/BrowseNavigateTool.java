package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.command.response.WDBrowsingContextResult;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

/**
 * Navigate to a URL and return a compact page snapshot.
 * Invalidates all existing NodeRefs.
 */
public class BrowseNavigateTool implements McpServerTool {

    @Override
    public String name() {
        return "web_navigate";
    }

    @Override
    public String description() {
        return "Navigate to a URL in the browser. Returns the page title, final URL, and a compact snapshot of interactive elements. "
             + "All previous NodeRefs are invalidated after navigation.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject url = new JsonObject();
        url.addProperty("type", "string");
        url.addProperty("description", "URL to navigate to");
        props.add("url", url);

        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("url");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String url = params.get("url").getAsString();
        try {
            // Invalidate old refs
            session.getNodeRefRegistry().invalidateAll();

            WDBrowsingContextResult.NavigateResult nav = session.navigate(url);
            String finalUrl = nav.getUrl();

            // Get a compact snapshot via JS
            String snapshot = getCompactSnapshot(session);

            StringBuilder sb = new StringBuilder();
            sb.append("URL: ").append(finalUrl != null ? finalUrl : url).append("\n");
            sb.append(snapshot);

            return ToolResult.text(sb.toString());
        } catch (Exception e) {
            return ToolResult.error("Navigation failed: " + e.getMessage());
        }
    }

    static String getCompactSnapshot(BrowserSession session) {
        try {
            String script =
                "(function(){"
              + "var r='Title: '+document.title+'\\n\\n';"
              + "var nodes=document.querySelectorAll('a,button,input,select,textarea,[role=button],[role=link],[role=tab],[role=menuitem],[onclick]');"
              + "var items=[];"
              + "for(var i=0;i<Math.min(nodes.length,200);i++){"
              + "  var n=nodes[i];"
              + "  if(!n.offsetParent&&n.tagName!=='BODY')continue;"  // skip hidden
              + "  var tag=n.tagName.toLowerCase();"
              + "  var txt=(n.textContent||'').trim().substring(0,60);"
              + "  var type=n.getAttribute('type')||'';"
              + "  var name=n.getAttribute('name')||'';"
              + "  var placeholder=n.getAttribute('placeholder')||'';"
              + "  var href=n.getAttribute('href')||'';"
              + "  var ariaLabel=n.getAttribute('aria-label')||'';"
              + "  var val=n.value||'';"
              + "  var s=tag;"
              + "  if(type)s+='[type='+type+']';"
              + "  if(name)s+='[name='+name+']';"
              + "  if(ariaLabel)s+=' \"'+ariaLabel+'\"';"
              + "  else if(placeholder)s+=' \"'+placeholder+'\"';"
              + "  else if(txt&&txt.length>0)s+=' \"'+txt.substring(0,40)+'\"';"
              + "  if(href&&href.length<80)s+=' →'+href;"
              + "  if(val&&tag==='input')s+=' val=\"'+val.substring(0,20)+'\"';"
              + "  items.push(s);"
              + "}"
              + "r+='Interactive elements ('+items.length+'):\\n';"
              + "for(var j=0;j<items.length;j++){r+='  '+items[j]+'\\n';}"
              + "var textSnippet=(document.body?document.body.innerText:'').substring(0,2000);"
              + "r+='\\nPage text (truncated):\\n'+textSnippet;"
              + "return r;"
              + "})()";

            Object result = session.evaluate(script, true);
            if (result instanceof de.bund.zrb.type.script.WDEvaluateResult.WDEvaluateResultSuccess) {
                return ((de.bund.zrb.type.script.WDEvaluateResult.WDEvaluateResultSuccess) result)
                        .getResult().asString();
            }
            return "(snapshot unavailable)";
        } catch (Exception e) {
            return "(snapshot error: " + e.getMessage() + ")";
        }
    }
}

