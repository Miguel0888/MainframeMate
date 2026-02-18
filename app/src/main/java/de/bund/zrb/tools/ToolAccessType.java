package de.bund.zrb.tools;

/** Access class for tools. */
public enum ToolAccessType {
    READ,
    WRITE;

    public boolean isWrite() {
        return this == WRITE;
    }
}
