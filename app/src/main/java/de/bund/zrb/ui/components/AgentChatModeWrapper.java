package de.bund.zrb.ui.components;

import de.zrb.bund.newApi.bot.Agent;

/**
 * Wrapper that pairs a {@link ChatMode#AGENT_PLUGIN} enum value with a specific
 * {@link Agent} instance. Used in the mode combo box to represent dynamically
 * registered agents alongside the built-in chat modes.
 *
 * <p>This is NOT a subclass of ChatMode (enums can't be subclassed in Java).
 * Instead, it delegates to {@link ChatMode#AGENT_PLUGIN} for all mode properties
 * and adds the agent reference for runtime behavior.</p>
 */
public class AgentChatModeWrapper {

    private final Agent agent;

    public AgentChatModeWrapper(Agent agent) {
        this.agent = agent;
    }

    public Agent getAgent() {
        return agent;
    }

    /**
     * Returns the underlying ChatMode enum value (always AGENT_PLUGIN).
     */
    public ChatMode getChatMode() {
        return ChatMode.AGENT_PLUGIN;
    }

    @Override
    public String toString() {
        return agent.getDisplayName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof AgentChatModeWrapper) {
            return agent.getId().equals(((AgentChatModeWrapper) o).agent.getId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return agent.getId().hashCode();
    }
}

