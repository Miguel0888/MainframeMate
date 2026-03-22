package de.bund.zrb.mermaid;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Diagnostic test to isolate WHERE GraalJS hangs when evaluating the Mermaid bundle.
 */
class GraalJsHangDiagnostic {

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void step1_contextCreation() {
        System.err.println("[Diag] Step 1: Creating GraalJS context...");
        long t0 = System.currentTimeMillis();
        Context ctx = Context.newBuilder("js")
                .allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        System.err.println("[Diag] Context created in " + (System.currentTimeMillis() - t0) + " ms");
        ctx.eval("js", "'hello'");
        System.err.println("[Diag] Basic eval OK in " + (System.currentTimeMillis() - t0) + " ms");
        ctx.close();
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void step2_shimOnly() {
        String shim = MermaidRenderer.loadResource("/mermaid/browser-shim.js");
        assertNotNull(shim);
        System.err.println("[Diag] Step 2: Evaluating browser shim (" + shim.length() + " chars)...");
        long t0 = System.currentTimeMillis();
        Context ctx = Context.newBuilder("js")
                .allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        ctx.eval("js", shim);
        System.err.println("[Diag] Shim evaluated in " + (System.currentTimeMillis() - t0) + " ms");
        ctx.close();
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void step3_shimAndBundle() {
        String shim = MermaidRenderer.loadResource("/mermaid/browser-shim.js");
        String bundle = MermaidRenderer.loadResource("/mermaid/mermaid.min.js");
        assertNotNull(shim);
        assertNotNull(bundle);
        System.err.println("[Diag] Step 3: Evaluating shim + bundle (" 
                + (shim.length() + bundle.length()) / 1024 + " KB)...");
        long t0 = System.currentTimeMillis();
        Context ctx = Context.newBuilder("js")
                .allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        ctx.eval("js", shim + "\n" + bundle);
        System.err.println("[Diag] Shim + bundle evaluated in " + (System.currentTimeMillis() - t0) + " ms");
        
        Value mermaid = ctx.eval("js", "window.mermaid");
        System.err.println("[Diag] window.mermaid = " + mermaid);
        assertFalse(mermaid.isNull(), "window.mermaid should be set");
        ctx.close();
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS) 
    void step4_fullRender() {
        System.err.println("[Diag] Step 4: Full render test...");
        long t0 = System.currentTimeMillis();
        MermaidRenderer renderer = MermaidRenderer.getInstance();
        assertTrue(renderer.isAvailable(), "Renderer should be available");
        System.err.println("[Diag] Renderer ready in " + (System.currentTimeMillis() - t0) + " ms");
        
        String svg = renderer.renderToSvg("graph TD; A-->B;");
        System.err.println("[Diag] Render completed in " + (System.currentTimeMillis() - t0) + " ms");
        System.err.println("[Diag] SVG length: " + (svg != null ? svg.length() : "null"));
        assertNotNull(svg);
    }
}

