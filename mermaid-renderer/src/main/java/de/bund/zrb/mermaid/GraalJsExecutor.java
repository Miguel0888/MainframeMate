package de.bund.zrb.mermaid;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

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
    private static final int TIMEOUT_SECONDS = 120;

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
     */
    public static final class JavaBridge {

        @SuppressWarnings("unused") // called from JS
        public void log(String message) {
            // silent in production — override for debug
        }
    }
}

