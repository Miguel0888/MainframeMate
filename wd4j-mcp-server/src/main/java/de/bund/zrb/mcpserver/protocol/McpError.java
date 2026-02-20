package de.bund.zrb.mcpserver.protocol;

/**
 * JSON-RPC 2.0 error codes used in MCP.
 */
public final class McpError {

    private McpError() {}

    // Standard JSON-RPC codes
    public static final int PARSE_ERROR      = -32700;
    public static final int INVALID_REQUEST  = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS   = -32602;
    public static final int INTERNAL_ERROR   = -32603;
}

