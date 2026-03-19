package de.zrb.bund.newApi.bot;

import java.util.List;

/**
 * Registry for managing available agents.
 *
 * <p>Agents are registered by plugins during initialization.
 * The chat system queries this registry to list and activate agents.</p>
 */
public interface AgentRegistry {

    /**
     * Register an agent. Replaces any previously registered agent with the same ID.
     *
     * @param agent the agent to register
     */
    void registerAgent(Agent agent);

    /**
     * Unregister an agent by its ID.
     *
     * @param agentId the agent ID to remove
     */
    void unregisterAgent(String agentId);

    /**
     * Returns the agent with the given ID, or null if not found.
     */
    Agent getAgent(String agentId);

    /**
     * Returns all registered agents.
     */
    List<Agent> getAllAgents();

    /**
     * Returns true if an agent with the given ID is registered.
     */
    boolean hasAgent(String agentId);
}

