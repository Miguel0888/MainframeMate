package example.mermaid;

/**
 * Immutable result of a JavaScript execution attempt via GraalJS.
 * Either successful (with output) or failed (with error message).
 */
public final class JavaScriptExecutionResult {

    private final boolean successful;
    private final String output;
    private final String errorMessage;

    private JavaScriptExecutionResult(boolean successful, String output, String errorMessage) {
        this.successful = successful;
        this.output = output;
        this.errorMessage = errorMessage;
    }

    public static JavaScriptExecutionResult success(String output) {
        return new JavaScriptExecutionResult(true, output, null);
    }

    public static JavaScriptExecutionResult failure(String errorMessage) {
        return new JavaScriptExecutionResult(false, null, errorMessage);
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

