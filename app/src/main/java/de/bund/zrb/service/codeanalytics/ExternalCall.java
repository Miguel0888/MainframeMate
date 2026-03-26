package de.bund.zrb.service.codeanalytics;

/**
 * A single external call reference extracted from source code.
 * Represents a call to an object outside the current file.
 */
public class ExternalCall {
    private final String targetName;
    private final String callType;   // e.g. "CALLNAT", "CALL", "FETCH", "EXEC PGM", "EXEC PROC"
    private final int lineNumber;
    private final String rawText;

    public ExternalCall(String targetName, String callType, int lineNumber, String rawText) {
        this.targetName = targetName;
        this.callType = callType;
        this.lineNumber = lineNumber;
        this.rawText = rawText;
    }

    public String getTargetName() { return targetName; }
    public String getCallType() { return callType; }
    public int getLineNumber() { return lineNumber; }
    public String getRawText() { return rawText; }

    public String getDisplayText() {
        return callType + " " + targetName + "  [Zeile " + lineNumber + "]";
    }

    @Override
    public String toString() {
        return getDisplayText();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExternalCall)) return false;
        ExternalCall that = (ExternalCall) o;
        return targetName.equalsIgnoreCase(that.targetName) && callType.equals(that.callType);
    }

    @Override
    public int hashCode() {
        return 31 * targetName.toUpperCase().hashCode() + callType.hashCode();
    }
}

