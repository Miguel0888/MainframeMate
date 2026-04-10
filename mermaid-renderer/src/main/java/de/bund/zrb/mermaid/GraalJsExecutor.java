package de.bund.zrb.mermaid;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import org.graalvm.polyglot.HostAccess;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes JavaScript code snippets inside an embedded GraalJS context.
 * <p>
 * Supports two modes of operation:
 * <ol>
 *   <li><b>Cold execution</b> ({@link #execute}, {@link #executeAsync}): creates
 *       a fresh context per call — simple but slow for large scripts.</li>
 *   <li><b>Warm-context execution</b> ({@link #warmUp}, {@link #executeWarm}):
 *       evaluates a large preamble (browser shim + Mermaid bundle) once and keeps
 *       the context alive.  Subsequent calls only evaluate a short render script
 *       against the pre-initialised environment — typically 10–50× faster.</li>
 * </ol>
 * The warm context lives on a dedicated single-thread executor to satisfy
 * GraalJS's thread-confinement requirement.
 */
final class GraalJsExecutor {

    /** Maximum time in seconds to wait for a single JS evaluation. */
    private static final int TIMEOUT_SECONDS = 600;

    /**
     * Shared GraalVM engine — enables code-cache sharing across contexts.
     * Even in cold mode this avoids re-parsing the same source repeatedly.
     */
    private static final Engine SHARED_ENGINE;
    static {
        Engine e;
        try {
            e = Engine.newBuilder()
                    .option("engine.WarnInterpreterOnly", "false")
                    .build();
        } catch (Exception ex) {
            System.err.println("[GraalJS] Failed to create shared engine: " + ex);
            e = null;
        }
        SHARED_ENGINE = e;
    }

    // ── Warm-context state ──────────────────────────────────────────────────

    /** Dedicated thread for the warm context (GraalJS contexts are thread-confined). */
    private ExecutorService warmThread;
    /** The pre-initialised GraalJS context (preamble already evaluated). */
    private Context warmContext;
    /** Number of renders executed on the current warm context. */
    private int warmRenderCount;
    /** Maximum renders before recycling the warm context (prevents memory bloat). */
    private static final int MAX_WARM_RENDERS = 100;

    // ═════════════════════════════════════════════════════════════════════════
    //  Cold execution (legacy — creates a fresh context every time)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Evaluates the given JavaScript source and returns the result.
     *
     * @param script JavaScript source code
     * @return execution result
     */
    JsExecutionResult execute(String script) {
        Context context = null;
        try {
            Context.Builder builder = Context.newBuilder("js").allowAllAccess(true);
            if (SHARED_ENGINE != null) builder.engine(SHARED_ENGINE);
            else builder.option("engine.WarnInterpreterOnly", "false");
            context = builder.build();

            Value bindings = context.getBindings("js");
            bindings.putMember("javaBridge", new JavaBridge());

            Value result = context.eval("js", script);
            String output = convertResultToString(result);
            return JsExecutionResult.success(output);
        } catch (PolyglotException polyglotException) {
            return JsExecutionResult.failure(formatPolyglotError(polyglotException));
        } catch (Exception exception) {
            return JsExecutionResult.failure(exception.getClass().getName() + ": " + exception.getMessage());
        } finally {
            if (context != null) {
                try { context.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Evaluates a setup script, flushes pending microtasks, and reads the result.
     * Creates a fresh context — use {@link #executeWarm} for repeat renders.
     */
    JsExecutionResult executeAsync(String setupScript, String resultExpression) {
        final Context context;
        try {
            System.err.println("[GraalJS] Creating context...");
            long t0 = System.currentTimeMillis();
            Context.Builder builder = Context.newBuilder("js").allowAllAccess(true);
            if (SHARED_ENGINE != null) builder.engine(SHARED_ENGINE);
            else builder.option("engine.WarnInterpreterOnly", "false");
            context = builder.build();
            System.err.println("[GraalJS] Context created in " + (System.currentTimeMillis() - t0) + " ms");
        } catch (Exception e) {
            return JsExecutionResult.failure("Failed to create GraalJS context: " + e.getMessage());
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<JsExecutionResult> future = executor.submit(new Callable<JsExecutionResult>() {
                @Override
                public JsExecutionResult call() {
                    try {
                        long t1 = System.currentTimeMillis();
                        Value bindings = context.getBindings("js");
                        bindings.putMember("javaBridge", new JavaBridge());
                        System.err.println("[GraalJS] Bindings set in " + (System.currentTimeMillis() - t1) + " ms");

                        System.err.println("[GraalJS] Evaluating setup script ("
                                + (setupScript.length() / 1024) + " KB)...");
                        long t2 = System.currentTimeMillis();
                        context.eval("js", setupScript);
                        System.err.println("[GraalJS] Setup script evaluated in "
                                + (System.currentTimeMillis() - t2) + " ms");

                        context.eval("js", "void 0");

                        long t3 = System.currentTimeMillis();
                        Value result = context.eval("js", resultExpression);
                        System.err.println("[GraalJS] Result read in "
                                + (System.currentTimeMillis() - t3) + " ms");
                        String output = convertResultToString(result);
                        return JsExecutionResult.success(output);
                    } catch (PolyglotException polyglotException) {
                        return JsExecutionResult.failure(formatPolyglotError(polyglotException));
                    } catch (Exception exception) {
                        return JsExecutionResult.failure(
                                exception.getClass().getName() + ": " + exception.getMessage());
                    }
                }
            });

            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            try { context.close(true); } catch (Exception ignored) {}
            return JsExecutionResult.failure(
                    "JS evaluation timed out after " + TIMEOUT_SECONDS + " seconds");
        } catch (Exception e) {
            return JsExecutionResult.failure("Execution error: " + e.getMessage());
        } finally {
            executor.shutdownNow();
            try { context.close(); } catch (Exception ignored) {}
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Warm-context execution (preamble evaluated once, render calls reuse)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Prepare a warm context by evaluating the given preamble script once.
     * After this returns successfully, subsequent calls to {@link #executeWarm}
     * will run against this pre-initialised context.
     *
     * @param preambleScript the browser shim + Mermaid bundle + initialize call
     * @return true if the warm-up succeeded
     */
    synchronized boolean warmUp(String preambleScript) {
        disposeWarmContext();

        warmThread = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> future = warmThread.submit(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    try {
                        long t0 = System.currentTimeMillis();
                        Context.Builder builder = Context.newBuilder("js").allowAllAccess(true);
                        if (SHARED_ENGINE != null) builder.engine(SHARED_ENGINE);
                        else builder.option("engine.WarnInterpreterOnly", "false");
                        warmContext = builder.build();

                        Value bindings = warmContext.getBindings("js");
                        bindings.putMember("javaBridge", new JavaBridge());

                        System.err.println("[GraalJS] Warm-up: evaluating preamble ("
                                + (preambleScript.length() / 1024) + " KB)...");
                        warmContext.eval("js", preambleScript);
                        warmContext.eval("js", "void 0"); // flush microtasks
                        warmRenderCount = 0;

                        System.err.println("[GraalJS] Warm-up complete in "
                                + (System.currentTimeMillis() - t0) + " ms");
                        return Boolean.TRUE;
                    } catch (Exception e) {
                        System.err.println("[GraalJS] Warm-up failed: " + e);
                        if (warmContext != null) {
                            try { warmContext.close(); } catch (Exception ignored) {}
                            warmContext = null;
                        }
                        return Boolean.FALSE;
                    }
                }
            });
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("[GraalJS] Warm-up error: " + e);
            disposeWarmContext();
            return false;
        }
    }

    /**
     * Execute a short render script against the warm context.
     * The preamble is already loaded — this only evaluates the diagram-specific code.
     *
     * @param renderScript     short JS that calls mermaid.render() and stores result in globals
     * @param resultExpression JS expression to read the final SVG result
     * @return execution result, or failure if the warm context is not available
     */
    JsExecutionResult executeWarm(final String renderScript, final String resultExpression) {
        if (warmContext == null || warmThread == null || warmThread.isShutdown()) {
            return JsExecutionResult.failure("Warm context not available");
        }

        try {
            Future<JsExecutionResult> future = warmThread.submit(new Callable<JsExecutionResult>() {
                @Override
                public JsExecutionResult call() {
                    try {
                        long t0 = System.currentTimeMillis();
                        warmContext.eval("js", renderScript);
                        warmContext.eval("js", "void 0"); // flush microtasks
                        Value result = warmContext.eval("js", resultExpression);
                        String output = convertResultToString(result);
                        warmRenderCount++;
                        System.err.println("[GraalJS] Warm render #" + warmRenderCount
                                + " completed in " + (System.currentTimeMillis() - t0) + " ms");
                        return JsExecutionResult.success(output);
                    } catch (PolyglotException polyglotException) {
                        return JsExecutionResult.failure(formatPolyglotError(polyglotException));
                    } catch (Exception exception) {
                        return JsExecutionResult.failure(
                                exception.getClass().getName() + ": " + exception.getMessage());
                    }
                }
            });
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.err.println("[GraalJS] Warm render timed out — disposing context");
            disposeWarmContext();
            return JsExecutionResult.failure(
                    "JS evaluation timed out after " + TIMEOUT_SECONDS + " seconds");
        } catch (Exception e) {
            return JsExecutionResult.failure("Warm execution error: " + e.getMessage());
        }
    }

    /** @return true if a warm context is active and ready for {@link #executeWarm} calls. */
    boolean isWarm() {
        return warmContext != null && warmThread != null && !warmThread.isShutdown();
    }

    /** @return true if the warm context should be recycled (too many renders). */
    boolean needsRecycle() {
        return warmRenderCount >= MAX_WARM_RENDERS;
    }

    /** Dispose the warm context and its dedicated thread. */
    synchronized void disposeWarmContext() {
        if (warmContext != null) {
            try { warmContext.close(true); } catch (Exception ignored) {}
            warmContext = null;
        }
        if (warmThread != null) {
            warmThread.shutdownNow();
            warmThread = null;
        }
        warmRenderCount = 0;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Utilities
    // ═════════════════════════════════════════════════════════════════════════

    private String formatPolyglotError(PolyglotException polyglotException) {
        StringBuilder sb = new StringBuilder();
        sb.append(polyglotException.getMessage());

        if (polyglotException.getSourceLocation() != null) {
            sb.append("\n  at source line: ").append(polyglotException.getSourceLocation().getStartLine());
            sb.append(", column: ").append(polyglotException.getSourceLocation().getStartColumn());
        }

        sb.append("\n  JS stack trace:");
        for (PolyglotException.StackFrame frame : polyglotException.getPolyglotStackTrace()) {
            if (frame.isGuestFrame()) {
                sb.append("\n    ").append(frame.getRootName());
                if (frame.getSourceLocation() != null) {
                    sb.append(" (line ").append(frame.getSourceLocation().getStartLine()).append(")");
                }
            }
        }

        return sb.toString();
    }

    private String convertResultToString(Value result) {
        if (result == null || result.isNull()) {
            return null;
        }
        if (result.isString()) {
            return result.asString();
        }
        return result.toString();
    }

    /**
     * Bridge object exposed to JavaScript as {@code javaBridge}.
     * <p>
     * Provides services that cannot be implemented in pure JavaScript:
     * <ul>
     *   <li><b>Accurate text measurement</b> via {@code java.awt.FontMetrics}</li>
     *   <li><b>Accurate SVG bounding box computation</b> via Apache Batik's GVT tree —
     *       replaces the heuristic JS-side {@code _computeElementDims()} for complex
     *       elements (text with tspan, groups with transforms)</li>
     * </ul>
     */
    public static final class JavaBridge {

        private static final BufferedImage MEASURE_IMAGE =
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        private static final Graphics2D MEASURE_GFX = MEASURE_IMAGE.createGraphics();

        private BatikBBoxService bboxService;

        @SuppressWarnings("unused") // called from JS
        @HostAccess.Export
        public void log(String message) {
            System.err.println("[JS] " + message);
        }

        @SuppressWarnings("unused") // called from JS
        @HostAccess.Export
        public double measureTextWidth(String text, String fontFamily, double fontSize) {
            if (text == null || text.isEmpty()) return 0;
            Font font = resolveFont(fontFamily, Font.PLAIN, (float) fontSize);
            FontMetrics fm = MEASURE_GFX.getFontMetrics(font);
            return fm.stringWidth(text);
        }

        @SuppressWarnings("unused") // called from JS
        @HostAccess.Export
        public String measureTextFull(String text, String fontFamily, double fontSize) {
            if (text == null || text.isEmpty()) return "0,0,0,0";
            Font font = resolveFont(fontFamily, Font.PLAIN, (float) fontSize);
            FontMetrics fm = MEASURE_GFX.getFontMetrics(font);
            int width = fm.stringWidth(text);
            int ascent = fm.getAscent();
            int descent = fm.getDescent();
            int height = fm.getHeight();
            return width + "," + ascent + "," + descent + "," + height;
        }

        @SuppressWarnings("unused") // called from JS
        @HostAccess.Export
        public String computeSvgBBox(String svgFragment) {
            if (svgFragment == null || svgFragment.isEmpty()) return "";
            if (bboxService == null) {
                bboxService = new BatikBBoxService();
            }
            String result = bboxService.computeBBox(svgFragment);
            return result != null ? result : "";
        }

        @SuppressWarnings("unused") // called from JS
        @HostAccess.Export
        public void clearBBoxCache() {
            if (bboxService != null) {
                bboxService.clearCache();
            }
        }

        private static Font resolveFont(String fontFamily, int style, float size) {
            if (fontFamily != null && !fontFamily.isEmpty()) {
                String[] families = fontFamily.split(",");
                for (String family : families) {
                    String name = family.trim()
                            .replace("\"", "")
                            .replace("'", "");
                    if (name.equalsIgnoreCase("sans-serif")) {
                        name = Font.SANS_SERIF;
                    } else if (name.equalsIgnoreCase("serif")) {
                        name = Font.SERIF;
                    } else if (name.equalsIgnoreCase("monospace")) {
                        name = Font.MONOSPACED;
                    }
                    Font f = new Font(name, style, 1).deriveFont(size);
                    // Check if the font was actually found (Java substitutes Dialog if not)
                    if (!f.getFamily().equalsIgnoreCase("Dialog") ||
                            name.equalsIgnoreCase("Dialog")) {
                        return f;
                    }
                }
            }
            return new Font(Font.SANS_SERIF, style, 1).deriveFont(size);
        }
    }
}

