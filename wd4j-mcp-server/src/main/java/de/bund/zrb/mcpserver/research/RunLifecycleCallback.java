package de.bund.zrb.mcpserver.research;

/**
 * Callback interface for Data Lake run lifecycle events.
 * Implementations connect the MCP server (wd4j-mcp-server) to the archive
 * service (app module) without a direct dependency.
 * <p>
 * Registered by the WebSearchPlugin at startup.
 */
public interface RunLifecycleCallback {

    /**
     * Start a new research run.
     *
     * @param mode             "RESEARCH" or "AGENT"
     * @param domainPolicyJson JSON representation of domain policy
     * @return the generated runId
     */
    String startRun(String mode, String domainPolicyJson);

    /**
     * End a research run.
     *
     * @param runId the run to end
     */
    void endRun(String runId);
}
