package de.zrb.bund.newApi;

import de.zrb.bund.api.ChatManager;

public interface ContextService {
    TabService getTabService();
    ChatService getChatService();
    CommandService getCommandService();
    EventService getEventService();
}