package de.bund.zrb.mermaid;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

/**
 * Executes JavaScript code snippets inside an embedded GraalJS context.
 * <p>
 * Each call to {@link #execute(String)} creates a fresh JS context,
 * exposes a {@link JavaBridge} under the global name {@code javaBridge},
 * and returns the evaluation result wrapped in a {@link JsExecutionResult}.
 */
final class GraalJsExecutor {

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
     *
     * @param setupScript      JavaScript that starts async operations and stores results in globals
     * @param resultExpression JavaScript expression evaluated after microtask flush to read the final result
     * @return execution result
     */
    JsExecutionResult executeAsync(String setupScript, String resultExpression) {
        Context context = null;

        try {
            context = Context.newBuilder("js")
                    .allowAllAccess(true)
                    .option("engine.WarnInterpreterOnly", "false")
                    .build();

            Value bindings = context.getBindings("js");
            bindings.putMember("javaBridge", new JavaBridge());

            // Step 1: Run the setup script (starts async operations, chains .then() callbacks)
            context.eval("js", setupScript);

            // Step 2: Flush pending microtasks — GraalJS processes them at the start of each eval()
            context.eval("js", "void 0");

            // Step 3: Read the result after microtasks have been processed
            Value result = context.eval("js", resultExpression);
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

