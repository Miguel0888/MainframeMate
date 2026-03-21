package example.mermaid;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

/**
 * Executes JavaScript code snippets inside an embedded GraalJS context.
 * <p>
 * Each call to {@link #execute(String)} creates a fresh JS context,
 * exposes a {@link JavaBridge} under the global name {@code javaBridge},
 * and returns the evaluation result wrapped in a {@link JavaScriptExecutionResult}.
 */
public final class GraalJsExecutor {

    /**
     * Evaluates the given JavaScript source and returns the result.
     *
     * @param script JavaScript source code
     * @return execution result — either success with the stringified return value, or failure with the exception details
     */
    public JavaScriptExecutionResult execute(String script) {
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
            return JavaScriptExecutionResult.success(output);
        } catch (PolyglotException polyglotException) {
            StringBuilder sb = new StringBuilder();
            sb.append(polyglotException.getMessage());

            if (polyglotException.getSourceLocation() != null) {
                sb.append("\n  at source line: ").append(polyglotException.getSourceLocation().getStartLine());
                sb.append(", column: ").append(polyglotException.getSourceLocation().getStartColumn());
                sb.append("\n  characters: ").append(polyglotException.getSourceLocation().getCharIndex())
                  .append("-").append(polyglotException.getSourceLocation().getCharEndIndex());
            }

            // Append JS stack frames
            sb.append("\n  JS stack trace:");
            for (PolyglotException.StackFrame frame : polyglotException.getPolyglotStackTrace()) {
                if (frame.isGuestFrame()) {
                    sb.append("\n    ").append(frame.getRootName());
                    if (frame.getSourceLocation() != null) {
                        sb.append(" (line ").append(frame.getSourceLocation().getStartLine()).append(")");
                    }
                }
            }

            return JavaScriptExecutionResult.failure(sb.toString());
        } catch (Exception exception) {
            return JavaScriptExecutionResult.failure(exception.getClass().getName() + ": " + exception.getMessage());
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    private String convertResultToString(Value result) {
        if (result == null) {
            return null;
        }

        if (result.isNull()) {
            return null;
        }

        if (result.isString()) {
            return result.asString();
        }

        return result.toString();
    }

    /**
     * Simple bridge object exposed to JavaScript as {@code javaBridge}.
     * Allows JS code to call back into Java for logging.
     */
    public static final class JavaBridge {

        public void log(String message) {
            System.out.println("[JS → Java] " + message);
        }
    }
}

