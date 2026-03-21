package de.bund.zrb.mermaid;

/**
 * Immutable result of a JavaScript execution attempt via GraalJS.
 * Either successful (with output) or failed (with error message).
 */
public final class JsExecutionResult {

    private final boolean successful;
    private final String output;
    private final String errorMessage;

    private JsExecutionResult(boolean successful, String output, String errorMessage) {
        this.successful = successful;
        this.output = output;
        this.errorMessage = errorMessage;
    }

    public static JsExecutionResult success(String output) {
        return new JsExecutionResult(true, output, null);
    }

    public static JsExecutionResult failure(String errorMessage) {
        return new JsExecutionResult(false, null, errorMessage);
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getOutput() {
        return output;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        if (successful) {
            return "SUCCESS: " + output;
        }
        return "FAILURE: " + errorMessage;
    }
}

