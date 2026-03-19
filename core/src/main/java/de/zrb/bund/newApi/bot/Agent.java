package de.zrb.bund.newApi.bot;

import de.zrb.bund.newApi.mcp.McpTool;

import java.util.List;

/**
 * A specialized agent that can be registered and used within the chat system.
 *
 * <p>Agents extend the chat with domain-specific capabilities (e.g. web research,
 * code analysis, refactoring). They provide their own system prompts, tools,
 * and follow-up strategies without modifying the chat core.</p>
 *
 * <p>Agents are discovered via plugins implementing
 * {@link de.zrb.bund.api.MainframeMatePlugin#getAgents()}.</p>
 */
public interface Agent {

    /**
     * Unique identifier for this agent (e.g. "recherche", "code-agent").
     * Used for registration and lookup.
     */
    String getId();

    /**
     * Human-readable display name shown in the UI (e.g. "Recherche", "Code Agent").
     */
    String getDisplayName();

    /**
     * Short description / tooltip text explaining what this agent does.
     */
    String getDescription();

    /**
     * The system prompt fragment contributed by this agent.
     * This is appended to or replaces the base system prompt when the agent is active.
     */
    String getSystemPrompt();

    /**
     * Returns the tools that this agent provides / requires.
     * These tools are registered in the ToolRegistry when the agent is activated.
     */
    List<McpTool> getTools();

    /**
     * Creates a new session for this agent, scoped to a chat conversation.
     *
     * @param context the agent context providing access to core services
     * @return a new agent session
     */
    AgentSession createSession(AgentContext context);

    /**
     * Whether this agent operates autonomously (like AGENT/RECHERCHE modes),
     * meaning it should automatically follow up on tool results.
     */
    boolean isAutonomous();

    /**
     * Called when the agent is being shut down (e.g. application exit).
     * Release any held resources.
     */
    default void shutdown() {
        // default: no-op
    }
}

