package de.bund.zrb.mermaid;

import org.graalvm.polyglot.Context;
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
 * Each call to {@link #execute(String)} creates a fresh JS context,
 * exposes a {@link JavaBridge} under the global name {@code javaBridge},
 * and returns the evaluation result wrapped in a {@link JsExecutionResult}.
 */
final class GraalJsExecutor {

    /** Maximum time in seconds to wait for a single JS evaluation. */
    private static final int TIMEOUT_SECONDS = 600;

    /**
     * Evaluates the given JavaScript source and returns the result.
     *
     * @param script JavaScript source code
     * @return execution result — either success with the stringified return value, or failure with the exception details
     */
    JsExecutionResult execute(String script) {
        Context context = null;

        try {
            context = Context.newBuilder("js")
                    .allowAllAccess(true)
                    .option("engine.WarnInterpreterOnly", "false")
                    .build();

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
                context.close();
            }
        }
    }

    /**
     * Evaluates a setup script, flushes pending microtasks (important for
     * Promise-based APIs like Mermaid 11+), and then evaluates a separate
     * result expression to read the outcome.
     * <p>
     * GraalJS processes pending microtasks at the start of each {@code eval()} call,
     * so inserting a no-op eval between setup and result reading ensures that
     * Promise {@code .then()} callbacks have fired.
     * <p>
     * The evaluation is guarded by a {@value #TIMEOUT_SECONDS}-second timeout.
     * If the script does not finish in time, the GraalJS context is closed
     * (which interrupts execution) and a failure result is returned.
     *
     * @param setupScript      JavaScript that starts async operations and stores results in globals
     * @param resultExpression JavaScript expression evaluated after microtask flush to read the final result
     * @return execution result
     */
    JsExecutionResult executeAsync(String setupScript, String resultExpression) {
        final Context context;
        try {
            System.err.println("[GraalJS] Creating context...");
            long t0 = System.currentTimeMillis();
            context = Context.newBuilder("js")
                    .allowAllAccess(true)
                    .option("engine.WarnInterpreterOnly", "false")
                    .build();
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

                        // Step 1: Run the setup script
                        System.err.println("[GraalJS] Evaluating setup script ("
                                + (setupScript.length() / 1024) + " KB)...");
                        long t2 = System.currentTimeMillis();
                        context.eval("js", setupScript);
                        System.err.println("[GraalJS] Setup script evaluated in "
                                + (System.currentTimeMillis() - t2) + " ms");

                        // Step 2: Flush pending microtasks
                        context.eval("js", "void 0");

                        // Step 3: Read the result
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
            // Force-close the context to interrupt the JS evaluation
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

        /**
         * Off-screen image used to obtain a Graphics2D context for font metrics.
         * Created once and reused (thread-confined to the GraalJS executor thread).
         */
        private static final BufferedImage MEASURE_IMAGE =
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        private static final Graphics2D MEASURE_GFX = MEASURE_IMAGE.createGraphics();

        /**
         * Batik-based BBox service for accurate SVG geometry computation.
         * Lazily initialized on first use.
         */
        private BatikBBoxService bboxService;

        @SuppressWarnings("unused") // called from JS
        @HostAccess.Export
        public void log(String message) {
            System.err.println("[JS] " + message);
        }

        /**
         * Measure the pixel width of the given text using Java's font engine.
         *
         * @param text       the text to measure
         * @param fontFamily CSS font-family string (e.g. "trebuchet ms, verdana, arial, sans-serif")
         * @param fontSize   font size in pixels (e.g. 16)
         * @return width in pixels (double)
         */
        @SuppressWarnings("unused") // called from JS
        @HostAccess.Export
        public double measureTextWidth(String text, String fontFamily, double fontSize) {
            if (text == null || text.isEmpty()) return 0;
            Font font = resolveFont(fontFamily, Font.PLAIN, (float) fontSize);
            FontMetrics fm = MEASURE_GFX.getFontMetrics(font);
            return fm.stringWidth(text);
        }

        /**
         * Measure text and return both width and height.
         *
         * @return comma-separated "width,ascent,descent,height"
         */
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

        /**
         * Compute the accurate bounding box of an SVG fragment using Apache Batik's
         * GVT (Graphics Vector Toolkit) tree.
         * <p>
         * This is the key method that replaces the heuristic JavaScript-side
         * {@code _computeElementDims()} for elements where accurate layout matters
         * (especially {@code <text>} with {@code <tspan>} children).
         * <p>
         * The fragment is wrapped in a minimal {@code <svg>} document, parsed by
         * Batik, and the resulting GVT tree's geometry bounds are returned.
         *
         * @param svgFragment well-formed SVG markup
         *                    (e.g. {@code <text x="10" y="20"><tspan dy="1.2em">Hello</tspan></text>})
         * @return comma-separated "x,y,width,height" string, or empty string if computation fails
         */
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

        /**
         * Clear the Batik BBox cache. Should be called between diagram renders
         * to prevent stale results.
         */
        @SuppressWarnings("unused") // called from JS
        @HostAccess.Export
        public void clearBBoxCache() {
            if (bboxService != null) {
                bboxService.clearCache();
            }
        }

        /**
         * Resolve a CSS font-family string to a Java Font.
         * Tries each comma-separated font name, falls back to SansSerif.
         */
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

