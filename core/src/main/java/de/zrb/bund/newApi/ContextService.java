package de.zrb.bund.newApi;

public interface ContextService {
    TabService getTabService();
    ChatService getChatService();
    CommandRegistry getCommandService();
    EventService getEventService();
}