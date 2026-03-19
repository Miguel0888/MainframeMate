package de.zrb.bund.newApi.bot;

/**
 * A session scoped to a single agent activation within a chat conversation.
 *
 * <p>The session holds agent-specific state (e.g. visited URLs for a research agent,
 * modified files for a code agent) and provides callbacks for the chat to delegate
 * agent-specific behavior.</p>
 */
public interface AgentSession {

    /**
     * Returns the agent that owns this session.
     */
    Agent getAgent();

    /**
     * Called when a tool execution has completed, to generate the follow-up
     * message that will be sent to the LLM.
     *
     * @param toolResultJson the JSON string of the tool result
     * @param originalUserRequest the user's original request text
     * @return the follow-up text to send as the next user message, or null to use the default
     */
    String buildFollowUpMessage(String toolResultJson, String originalUserRequest);

    /**
     * Called to format tool results before they are added to the chat history.
     * Agents can transform JSON tool results into more readable formats
     * (e.g. extracting links as plain text for small models).
     *
     * @param toolResultJson the raw JSON tool result
     * @return the formatted message to add to history, or null to use the default JSON format
     */
    String formatToolResult(String toolResultJson);

    /**
     * Called when the agent session is being deactivated or the chat is closing.
     * Release session-specific resources.
     */
    default void close() {
        // default: no-op
    }
}

