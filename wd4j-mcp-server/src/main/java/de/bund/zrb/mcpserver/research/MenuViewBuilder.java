package de.bund.zrb.mcpserver.research;

import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.browser.NodeRef;
import de.bund.zrb.mcpserver.browser.NodeRefRegistry;
import de.bund.zrb.type.browsingContext.WDLocator;
import de.bund.zrb.type.script.WDEvaluateResult;
import de.bund.zrb.type.script.WDRemoteReference;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builds a {@link MenuView} from the current page state in a {@link BrowserSession}.
 *
 * <p>Uses a two-phase approach (tagging bridge):
 * <ol>
 *   <li>JS script describes interactive elements and tags them with {@code data-mm-menu-id}</li>
 *   <li>CSS {@code locateNodes} resolves the tagged elements to SharedReferences</li>
 * </ol>
 *
 * <p>Also extracts a page text excerpt for the bot's overview.
 *
 * <p>After building, the MenuView and its menuItemId→SharedRef mapping are
 * stored in the {@link ResearchSession} under a new viewToken.</p>
 */
public class MenuViewBuilder {

    private static final Logger LOG = Logger.getLogger(MenuViewBuilder.class.getName());

    private final BrowserSession session;
    private final ResearchSession researchSession;

    public MenuViewBuilder(BrowserSession session, ResearchSession researchSession) {
        this.session = session;
        this.researchSession = researchSession;
    }

    /**
     * Build a MenuView for the current page, register it in the ResearchSession,
     * and return it with a fresh viewToken.
     *
     * @param maxItems    max number of menu items
     * @param excerptLen  max length of the text excerpt
     * @return MenuView with viewToken, excerpt, and menuItems
     */
    public MenuView build(int maxItems, int excerptLen) {
        LOG.fine("[MenuViewBuilder] Building view (maxItems=" + maxItems + ", excerptLen=" + excerptLen + ")");

        // Phase 1: JS describes + tags elements
        String jsOutput = runDescribeScript(maxItems, excerptLen);
        ParsedPage parsed = parseJsOutput(jsOutput);

        LOG.info("[MenuViewBuilder] Page: " + parsed.title + " | URL: " + parsed.url
                + " | Elements: " + parsed.elements.size());

        // Phase 2: CSS locate → SharedReferences
        Map<String, WDRemoteReference.SharedReference> itemRefs = new LinkedHashMap<>();
        List<MenuItem> menuItems = new ArrayList<>();

        if (!parsed.elements.isEmpty()) {
            try {
                // Invalidate old NodeRefs
                session.getNodeRefRegistry().invalidateAll();

                List<NodeRef> nodeRefs = session.locateAndRegister(
                        new WDLocator.CssLocator("[data-mm-menu-id]"),
                        parsed.elements.size());

                for (int i = 0; i < parsed.elements.size(); i++) {
                    ElementInfo el = parsed.elements.get(i);
                    String menuItemId = "m" + i;

                    MenuItem item = new MenuItem(
                            menuItemId,
                            MenuItem.inferType(el.tag),
                            el.label,
                            el.href,
                            el.actionHint
                    );
                    menuItems.add(item);

                    // Map menuItemId → SharedRef if we have one
                    if (i < nodeRefs.size()) {
                        NodeRefRegistry.Entry entry = session.getNodeRefRegistry().resolve(nodeRefs.get(i).getId());
                        itemRefs.put(menuItemId, entry.sharedRef);
                    }
                }

                LOG.fine("[MenuViewBuilder] Mapped " + itemRefs.size() + " menuItems to SharedRefs");
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[MenuViewBuilder] CSS locate failed, creating view without action refs", e);
                // Still create menu items, just without refs
                for (int i = 0; i < parsed.elements.size(); i++) {
                    ElementInfo el = parsed.elements.get(i);
                    menuItems.add(new MenuItem("m" + i, MenuItem.inferType(el.tag), el.label, el.href, el.actionHint));
                }
            }
        }

        // Cleanup markers
        cleanupMarkers();

        // Create MenuView and register in session
        // viewToken is set by ResearchSession.updateView
        MenuView view = new MenuView(
                null, // token will be set below
                parsed.url,
                parsed.title,
                parsed.excerpt,
                menuItems
        );

        // Register and get the viewToken
        String viewToken = researchSession.updateView(view, itemRefs);

        // Create final view with token
        MenuView finalView = new MenuView(viewToken, parsed.url, parsed.title, parsed.excerpt, menuItems);
        LOG.info("[MenuViewBuilder] View built: " + viewToken + " (" + menuItems.size() + " items, "
                + itemRefs.size() + " with refs)");

        return finalView;
    }

    /**
     * Wait for page to settle according to the given policy, then build.
     */
    public MenuView buildWithSettle(SettlePolicy policy, int maxItems, int excerptLen) {
        settle(policy);
        return build(maxItems, excerptLen);
    }

    // ══════════════════════════════════════════════════════════════════
    // Internal: Settle strategies
    // ══════════════════════════════════════════════════════════════════

    private void settle(SettlePolicy policy) {
        switch (policy) {
            case NAVIGATION:
                settleNavigation();
                break;
            case DOM_QUIET:
                settleDomQuiet();
                break;
            case NETWORK_QUIET:
                settleNetworkQuiet();
                break;
        }
    }

    private void settleNavigation() {
        // The actual navigation wait is handled by BrowserSession.navigate()
        // with ReadinessState.INTERACTIVE. We add a minimal stabilization delay
        // for late-loading scripts/styles.
        sleep(300);
    }

    private void settleDomQuiet() {
        // Wait until DOM mutations are quiet for ~500ms
        String script =
                "(function(){"
              + "return new Promise(function(resolve){"
              + "  var timer;"
              + "  var obs=new MutationObserver(function(){"
              + "    clearTimeout(timer);"
              + "    timer=setTimeout(function(){obs.disconnect();resolve('quiet');},500);"
              + "  });"
              + "  obs.observe(document.body||document.documentElement,{childList:true,subtree:true,attributes:true});"
              + "  timer=setTimeout(function(){obs.disconnect();resolve('timeout');},5000);"
              + "});"
              + "})()";
        try {
            session.evaluate(script, true);
        } catch (Exception e) {
            LOG.fine("[MenuViewBuilder] DOM_QUIET settle failed: " + e.getMessage());
        }
        // Additional small delay after quiet
        sleep(300);
    }

    private void settleNetworkQuiet() {
        // Wait until no new network activity for ~500ms using PerformanceObserver
        String script =
                "(function(){"
              + "return new Promise(function(resolve){"
              + "  var timer;"
              + "  function reset(){clearTimeout(timer);timer=setTimeout(function(){resolve('quiet');},500);}"
              + "  try{"
              + "    var obs=new PerformanceObserver(function(list){"
              + "      if(list.getEntries().length>0)reset();"
              + "    });"
              + "    obs.observe({type:'resource',buffered:false});"
              + "    reset();"
              + "    setTimeout(function(){try{obs.disconnect();}catch(e){}resolve('timeout');},8000);"
              + "  }catch(e){resolve('unsupported');}"
              + "});"
              + "})()";
        try {
            session.evaluate(script, true);
        } catch (Exception e) {
            LOG.fine("[MenuViewBuilder] NETWORK_QUIET settle failed: " + e.getMessage());
        }
        sleep(300);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Internal: JS script
    // ══════════════════════════════════════════════════════════════════

    private String runDescribeScript(int maxItems, int excerptLen) {
        String script = buildDescribeScript(maxItems, excerptLen);
        try {
            Object result = session.evaluate(script, true);
            if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                String s = ((WDEvaluateResult.WDEvaluateResultSuccess) result).getResult().asString();
                if (s != null && !s.startsWith("[Object:")) return s;
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[MenuViewBuilder] Describe script failed", e);
        }
        return "";
    }

    private String buildDescribeScript(int maxItems, int excerptLen) {
        return "(function(){"
             + "var r='TITLE|'+document.title+'\\n'+'URL|'+window.location.href+'\\n';"
             // Interactive elements selector
             + "var sel='a[href],button,input,select,textarea,[role=button],[role=link],[role=tab],"
             + "[role=menuitem],[role=checkbox],[role=radio],[onclick],[contenteditable=true]';"
             + "var nodes=document.querySelectorAll(sel);"
             // Also try iframes (same-origin)
             + "var allNodes=[];"
             + "for(var m=0;m<nodes.length;m++)allNodes.push(nodes[m]);"
             + "try{var ifs=document.querySelectorAll('iframe');"
             + "for(var f=0;f<ifs.length;f++){"
             + "  try{var fd=ifs[f].contentDocument;"
             + "  if(fd){var fn=fd.querySelectorAll(sel);for(var fi=0;fi<fn.length;fi++)allNodes.push(fn[fi]);}}"
             + "  catch(e){}"
             + "}}catch(e){}"
             // Iterate and tag
             + "var idx=0;"
             + "for(var i=0;i<allNodes.length&&idx<" + maxItems + ";i++){"
             + "  var n=allNodes[i];"
             // Visibility check
             + "  try{var cs=window.getComputedStyle(n);"
             + "  if(cs.display==='none'||cs.visibility==='hidden')continue;"
             + "  if(n.offsetWidth===0&&n.offsetHeight===0)continue;}catch(e){continue;}"
             // Describe
             + "  var tag=n.tagName.toLowerCase();"
             + "  var al=n.getAttribute('aria-label')||'';"
             + "  var ph=n.getAttribute('placeholder')||'';"
             + "  var tt=n.getAttribute('title')||'';"
             + "  var tp=n.getAttribute('type')||'';"
             + "  var nm=n.getAttribute('name')||'';"
             + "  var hr='';"
             + "  try{if(tag==='a'&&n.href){hr=n.href;}"
             + "  else{var raw=n.getAttribute('href')||'';"
             + "  if(raw){try{hr=new URL(raw,location.href).href;}catch(e){hr=raw;}}}"
             + "  }catch(e){}"
             + "  var tx='';try{tx=(n.innerText||n.textContent||'').trim().substring(0,60).replace(/\\n/g,' ');}catch(e){}"
             // Build label: prefer aria-label > placeholder > title > text
             + "  var label=al||ph||tt||tx||nm||'';"
             + "  if(label.length>80)label=label.substring(0,80)+'…';"
             // Build desc line
             + "  var desc=tp?tag+'['+tp+']':tag;"
             + "  if(nm)desc+='[name='+nm+']';"
             + "  desc+=' \"'+label+'\"';"
             + "  if(hr&&hr.indexOf('javascript:')<0)desc+=' ->'+hr.substring(0,200);"
             // Action hint
             + "  var hint='';"
             + "  if(n.getAttribute('target')==='_blank')hint='new window';"
             + "  else if(hr&&(hr.endsWith('.pdf')||hr.endsWith('.zip')||hr.endsWith('.exe')))hint='download';"
             + "  else if(tp==='password')hint='password field';"
             // Tag element
             + "  n.setAttribute('data-mm-menu-id',''+idx);"
             + "  r+='EL|'+idx+'|'+tag+'|'+label+'|'+hr+'|'+hint+'\\n';"
             + "  idx++;"
             + "}"
             // Excerpt: try article/main first, fall back to body
             + "var excEl=document.querySelector('article')||document.querySelector('[role=main]')"
             + "||document.querySelector('main')||document.querySelector('#content')"
             + "||document.querySelector('.content')||document.body;"
             + "var exc='';if(excEl){"
             + "  var cl=excEl.cloneNode(true);"
             + "  var rm=cl.querySelectorAll('script,style,nav,footer,header,noscript,[aria-hidden=true],.ad,.ads');"
             + "  for(var ri=0;ri<rm.length;ri++){try{rm[ri].parentNode.removeChild(rm[ri]);}catch(e){}}"
             + "  exc=(cl.innerText||cl.textContent||'').replace(/\\t/g,' ').replace(/ {3,}/g,' ')"
             + "    .replace(/\\n{3,}/g,'\\n\\n').trim().substring(0," + excerptLen + ");"
             + "}"
             + "r+='EXCERPT|'+exc;"
             + "return r;"
             + "})()";
    }

    // ══════════════════════════════════════════════════════════════════
    // Internal: Parsing JS output
    // ══════════════════════════════════════════════════════════════════

    private ParsedPage parseJsOutput(String jsOutput) {
        ParsedPage page = new ParsedPage();
        if (jsOutput == null || jsOutput.isEmpty()) return page;

        String[] lines = jsOutput.split("\n");
        for (String line : lines) {
            if (line.startsWith("TITLE|")) {
                page.title = line.substring(6);
            } else if (line.startsWith("URL|")) {
                page.url = line.substring(4);
            } else if (line.startsWith("EXCERPT|")) {
                page.excerpt = line.substring(8);
            } else if (line.startsWith("EL|")) {
                // Format: EL|idx|tag|label|href|hint
                String[] parts = line.split("\\|", 7);
                if (parts.length >= 4) {
                    ElementInfo el = new ElementInfo();
                    el.index = parts[1];
                    el.tag = parts[2];
                    el.label = parts.length > 3 ? parts[3] : "";
                    el.href = parts.length > 4 ? parts[4] : "";
                    el.actionHint = parts.length > 5 ? parts[5] : "";
                    page.elements.add(el);
                }
            }
        }
        return page;
    }

    private void cleanupMarkers() {
        try {
            session.evaluate(
                    "(function(){"
                  + "var els=document.querySelectorAll('[data-mm-menu-id]');"
                  + "for(var i=0;i<els.length;i++)els[i].removeAttribute('data-mm-menu-id');"
                  + "try{var ifs=document.querySelectorAll('iframe');"
                  + "for(var f=0;f<ifs.length;f++){"
                  + "  try{var fd=ifs[f].contentDocument;if(fd){"
                  + "    var fe=fd.querySelectorAll('[data-mm-menu-id]');"
                  + "    for(var j=0;j<fe.length;j++)fe[j].removeAttribute('data-mm-menu-id');"
                  + "  }}catch(e){}"
                  + "}}catch(e){}"
                  + "})()",
                    true);
        } catch (Exception ignored) {}
    }

    // ── Internal data classes ───────────────────────────────────────

    private static class ParsedPage {
        String title = "";
        String url = "";
        String excerpt = "";
        List<ElementInfo> elements = new ArrayList<>();
    }

    private static class ElementInfo {
        String index;
        String tag;
        String label;
        String href;
        String actionHint;
    }
}

