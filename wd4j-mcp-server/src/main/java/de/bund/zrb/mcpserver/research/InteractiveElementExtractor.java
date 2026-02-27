package de.bund.zrb.mcpserver.research;

import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.type.script.WDEvaluateResult;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extracts interactive form elements from the current page via browser JS.
 * Returns elements with their NodeRef IDs so the bot can use web_type / web_click.
 * <p>
 * Runs after navigation + cookie dismissal, captures:
 * - Text inputs, search fields, password fields
 * - Textareas
 * - Buttons (submit, button type)
 * - Select dropdowns
 */
public class InteractiveElementExtractor {

    private static final Logger LOG = Logger.getLogger(InteractiveElementExtractor.class.getName());

    /**
     * Extract visible interactive elements from the current page.
     * Each element is registered in the BrowserSession's NodeRefRegistry
     * so it can be referenced by web_type / web_click.
     *
     * @return list of interactive elements with their NodeRef IDs, type, and labels
     */
    public static List<InteractiveElement> extract(BrowserSession session) {
        List<InteractiveElement> elements = new ArrayList<InteractiveElement>();
        if (session == null || session.getDriver() == null) return elements;

        try {
            String js = buildExtractionScript();
            WDEvaluateResult result = session.evaluate(js, false);

            if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                String json = ((WDEvaluateResult.WDEvaluateResultSuccess) result)
                        .getResult().asString();
                if (json != null && json.startsWith("[")) {
                    parseResult(json, elements, session);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[InteractiveElementExtractor] Extraction failed", e);
        }

        LOG.info("[InteractiveElementExtractor] Found " + elements.size() + " interactive elements");
        return elements;
    }

    private static String buildExtractionScript() {
        return "(function() {\n"
             + "  var results = [];\n"
             + "  var selector = 'input:not([type=hidden]):not([type=checkbox]):not([type=radio]),"
             + "textarea, button, [type=submit], select';\n"
             + "  var els = document.querySelectorAll(selector);\n"
             + "  for (var i = 0; i < els.length && results.length < 20; i++) {\n"
             + "    var el = els[i];\n"
             + "    if (el.offsetParent === null && el.style.display !== 'contents') continue;\n" // not visible
             + "    var tag = el.tagName.toLowerCase();\n"
             + "    var type = (el.type || '').toLowerCase();\n"
             + "    var name = el.name || '';\n"
             + "    var placeholder = el.placeholder || '';\n"
             + "    var ariaLabel = el.getAttribute('aria-label') || '';\n"
             + "    var label = '';\n"
             // Try to find associated label
             + "    if (el.id) {\n"
             + "      var lbl = document.querySelector('label[for=\"'+el.id+'\"]');\n"
             + "      if (lbl) label = lbl.innerText || '';\n"
             + "    }\n"
             + "    if (!label && el.closest('label')) label = el.closest('label').innerText || '';\n"
             + "    var text = (tag === 'button' || type === 'submit') ? (el.innerText || el.value || '') : '';\n"
             + "    var elType = tag;\n"
             + "    if (tag === 'input') elType = type || 'text';\n"
             + "    results.push({\n"
             + "      idx: i,\n"
             + "      tag: tag,\n"
             + "      elType: elType,\n"
             + "      name: name.substring(0, 50),\n"
             + "      placeholder: placeholder.substring(0, 80),\n"
             + "      ariaLabel: ariaLabel.substring(0, 80),\n"
             + "      label: (label || '').trim().substring(0, 80),\n"
             + "      text: (text || '').trim().substring(0, 80),\n"
             + "      value: (el.value || '').substring(0, 30)\n"
             + "    });\n"
             + "  }\n"
             + "  return JSON.stringify(results);\n"
             + "})();\n";
    }

    /**
     * Register each extracted element in the NodeRefRegistry using locateNodes
     * with a CSS selector.
     */
    private static void parseResult(String json, List<InteractiveElement> out, BrowserSession session) {
        // Simple manual JSON array parsing (avoids Gson dependency in wd4j module)
        // Format: [{"idx":0,"tag":"input","elType":"search","name":"p","placeholder":"Suche",...}, ...]
        try {
            // Strip outer brackets
            String inner = json.substring(1, json.length() - 1);
            if (inner.trim().isEmpty()) return;

            int depth = 0;
            int start = -1;
            for (int i = 0; i < inner.length(); i++) {
                char c = inner.charAt(i);
                if (c == '{') {
                    if (depth == 0) start = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        String objStr = inner.substring(start, i + 1);
                        InteractiveElement elem = parseElement(objStr, session);
                        if (elem != null) out.add(elem);
                        start = -1;
                    }
                }
            }
        } catch (Exception e) {
            LOG.warning("[InteractiveElementExtractor] parseResult failed: " + e.getMessage());
        }
    }

    private static InteractiveElement parseElement(String objStr, BrowserSession session) {
        try {
            String tag = extractJsonField(objStr, "tag");
            String elType = extractJsonField(objStr, "elType");
            String name = extractJsonField(objStr, "name");
            String placeholder = extractJsonField(objStr, "placeholder");
            String ariaLabel = extractJsonField(objStr, "ariaLabel");
            String label = extractJsonField(objStr, "label");
            String text = extractJsonField(objStr, "text");
            String idxStr = extractJsonField(objStr, "idx");
            int idx = idxStr != null ? Integer.parseInt(idxStr) : -1;

            // Build a CSS selector to locate this element in the browser DOM
            String selector = buildSelector(tag, elType, name, placeholder, idx);

            // Register in NodeRefRegistry
            String nodeRefId = null;
            try {
                nodeRefId = session.registerNodeRef(selector);
            } catch (Exception e) {
                LOG.fine("[InteractiveElementExtractor] Could not register NodeRef for: " + selector);
            }

            if (nodeRefId == null) return null;

            // Build descriptive label for the bot
            String description = buildDescription(elType, name, placeholder, ariaLabel, label, text);

            InteractiveElement elem = new InteractiveElement();
            elem.nodeRefId = nodeRefId;
            elem.type = elType != null ? elType : tag;
            elem.description = description;
            elem.isInput = "input".equals(tag) || "textarea".equals(tag) || "select".equals(tag);
            return elem;
        } catch (Exception e) {
            return null;
        }
    }

    private static String buildSelector(String tag, String type, String name, String placeholder, int idx) {
        StringBuilder sel = new StringBuilder();
        sel.append(tag != null ? tag : "*");
        if (type != null && !type.equals(tag) && !"text".equals(type)) {
            sel.append("[type='").append(type).append("']");
        }
        if (name != null && !name.isEmpty()) {
            sel.append("[name='").append(escCss(name)).append("']");
        } else if (placeholder != null && !placeholder.isEmpty()) {
            sel.append("[placeholder='").append(escCss(placeholder)).append("']");
        }
        return sel.toString();
    }

    private static String buildDescription(String type, String name, String placeholder,
                                            String ariaLabel, String label, String text) {
        // For buttons: use button text
        if ("button".equals(type) || "submit".equals(type)) {
            if (text != null && !text.isEmpty()) return text;
            if (ariaLabel != null && !ariaLabel.isEmpty()) return ariaLabel;
            if (label != null && !label.isEmpty()) return label;
            return "Button";
        }
        // For inputs: use label, placeholder, ariaLabel, or name
        if (label != null && !label.isEmpty()) return label;
        if (placeholder != null && !placeholder.isEmpty()) return placeholder;
        if (ariaLabel != null && !ariaLabel.isEmpty()) return ariaLabel;
        if (name != null && !name.isEmpty()) return name;
        return type != null ? type : "Eingabe";
    }

    /** Simple JSON field extraction (avoids Gson). */
    private static String extractJsonField(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        // Skip whitespace
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;
        char c = json.charAt(start);
        if (c == '"') {
            // String value
            int end = json.indexOf('"', start + 1);
            if (end < 0) return null;
            return json.substring(start + 1, end);
        } else if (c == '-' || Character.isDigit(c)) {
            // Number
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-' || json.charAt(end) == '.')) end++;
            return json.substring(start, end);
        }
        return null;
    }

    private static String escCss(String s) {
        if (s == null) return "";
        return s.replace("'", "\\'").replace("\\", "\\\\");
    }

    // ── Data class ──

    public static class InteractiveElement {
        public String nodeRefId;
        public String type;
        public String description;
        public boolean isInput;

        @Override
        public String toString() {
            return (isInput ? "📝" : "🔘") + " " + nodeRefId + " [" + type + "] " + description;
        }
    }
}
