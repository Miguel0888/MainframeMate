package example.mermaid;

/**
 * Three-stage probe for Mermaid rendering feasibility inside GraalJS:
 * <ol>
 *     <li>Basic JS execution</li>
 *     <li>Pseudo browser globals (window, document, navigator)</li>
 *     <li>Loading the real mermaid.min.js bundle</li>
 * </ol>
 */
public final class MermaidRenderingProbe {

    private final GraalJsExecutor graalJsExecutor;

    public MermaidRenderingProbe(GraalJsExecutor graalJsExecutor) {
        this.graalJsExecutor = graalJsExecutor;
    }

    /**
     * Stage 1 — verify that GraalJS can execute trivial JavaScript.
     */
    public JavaScriptExecutionResult runBasicJavaScriptProbe() {
        String script =
                "var message = 'GraalJS works';" +
                "javaBridge.log(message);" +
                "message;";

        return graalJsExecutor.execute(script);
    }

    /**
     * Stage 2 — install minimal browser-like globals and verify they survive.
     */
    public JavaScriptExecutionResult runPseudoMermaidEnvironmentProbe() {
        String script =
                "var window = {};" +
                "var document = {" +
                "  createElement: function(name) {" +
                "    return {" +
                "      tagName: name," +
                "      innerHTML: ''," +
                "      setAttribute: function(key, value) {}" +
                "    };" +
                "  }" +
                "};" +
                "var navigator = { userAgent: 'GraalJS Spike' };" +
                "'Pseudo browser globals installed';";

        return graalJsExecutor.execute(script);
    }

    /**
     * Stage 3 — load the real Mermaid JS bundle and see what breaks.
     *
     * @param mermaidSource the full content of mermaid.min.js
     */
    public JavaScriptExecutionResult runMermaidLoadProbe(String mermaidSource) {
        // Build a script that first installs browser shims, then evaluates the mermaid bundle
        String script =
                buildBrowserShim() +
                "\n" +
                mermaidSource +
                "\n'Loaded Mermaid source';";

        return graalJsExecutor.execute(script);
    }

    /**
     * Constructs a minimal set of browser globals that libraries typically probe for.
     */
    private String buildBrowserShim() {
        return "var self = this;\n" +
               "var window = self;\n" +
               "var globalThis = self;\n" +
               "var navigator = { userAgent: 'GraalJS Spike' };\n" +
               "var document = {\n" +
               "  createElement: function(name) {\n" +
               "    var el = {\n" +
               "      tagName: name,\n" +
               "      innerHTML: '',\n" +
               "      textContent: '',\n" +
               "      style: {},\n" +
               "      childNodes: [],\n" +
               "      setAttribute: function(key, value) {},\n" +
               "      getAttribute: function(key) { return null; },\n" +
               "      appendChild: function(child) { this.childNodes.push(child); return child; },\n" +
               "      removeChild: function(child) { return child; },\n" +
               "      cloneNode: function(deep) { return document.createElement(name); },\n" +
               "      addEventListener: function(evt, fn) {},\n" +
               "      removeEventListener: function(evt, fn) {},\n" +
               "      insertBefore: function(newNode, refNode) { return newNode; },\n" +
               "      querySelectorAll: function(sel) { return []; },\n" +
               "      querySelector: function(sel) { return null; },\n" +
               "      getBoundingClientRect: function() { return {x:0,y:0,width:0,height:0,top:0,right:0,bottom:0,left:0}; }\n" +
               "    };\n" +
               "    return el;\n" +
               "  },\n" +
               "  createElementNS: function(ns, name) { return document.createElement(name); },\n" +
               "  createTextNode: function(text) { return { textContent: text, nodeType: 3 }; },\n" +
               "  querySelector: function(selector) { return null; },\n" +
               "  querySelectorAll: function(selector) { return []; },\n" +
               "  getElementById: function(id) { return null; },\n" +
               "  getElementsByTagName: function(name) { return []; },\n" +
               "  body: null,\n" +
               "  documentElement: { style: {} },\n" +
               "  head: null\n" +
               "};\n" +
               "document.body = document.createElement('body');\n" +
               "document.head = document.createElement('head');\n" +
               "var console = {\n" +
               "  log: function() { javaBridge.log(Array.prototype.slice.call(arguments).join(' ')); },\n" +
               "  warn: function() { javaBridge.log('WARN: ' + Array.prototype.slice.call(arguments).join(' ')); },\n" +
               "  error: function() { javaBridge.log('ERROR: ' + Array.prototype.slice.call(arguments).join(' ')); },\n" +
               "  info: function() { javaBridge.log('INFO: ' + Array.prototype.slice.call(arguments).join(' ')); }\n" +
               "};\n" +
               "var setTimeout = function(fn, delay) { fn(); return 0; };\n" +
               "var clearTimeout = function(id) {};\n" +
               "var setInterval = function(fn, delay) { return 0; };\n" +
               "var clearInterval = function(id) {};\n" +
               "var requestAnimationFrame = function(fn) { fn(0); return 0; };\n" +
               "var cancelAnimationFrame = function(id) {};\n" +
               "var URL = { createObjectURL: function(blob) { return ''; }, revokeObjectURL: function(url) {} };\n" +
               "var Blob = function(parts, options) { this.parts = parts; };\n" +
               "var XMLHttpRequest = function() {};\n" +
               "XMLHttpRequest.prototype.open = function() {};\n" +
               "XMLHttpRequest.prototype.send = function() {};\n";
    }
}

