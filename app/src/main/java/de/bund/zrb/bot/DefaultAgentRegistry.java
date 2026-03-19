package de.bund.zrb.bot;

import de.zrb.bund.newApi.bot.Agent;
import de.zrb.bund.newApi.bot.AgentRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Default implementation of the AgentRegistry.
 * Stores agents in memory, keyed by their unique ID.
 */
public class DefaultAgentRegistry implements AgentRegistry {

    private static final Logger LOG = Logger.getLogger(DefaultAgentRegistry.class.getName());

    private final Map<String, Agent> agents = new LinkedHashMap<>();

    @Override
    public void registerAgent(Agent agent) {
        if (agent == null || agent.getId() == null) {
            throw new IllegalArgumentException("Agent and agent ID must not be null");
        }
        agents.put(agent.getId(), agent);
        LOG.info("[AgentRegistry] Registered agent: " + agent.getId() + " (" + agent.getDisplayName() + ")");
    }

    @Override
    public void unregisterAgent(String agentId) {
        Agent removed = agents.remove(agentId);
        if (removed != null) {
            LOG.info("[AgentRegistry] Unregistered agent: " + agentId);
        }
    }

    @Override
    public Agent getAgent(String agentId) {
        return agents.get(agentId);
    }

    @Override
    public List<Agent> getAllAgents() {
        return Collections.unmodifiableList(new ArrayList<>(agents.values()));
    }

    @Override
    public boolean hasAgent(String agentId) {
        return agents.containsKey(agentId);
    }
}

