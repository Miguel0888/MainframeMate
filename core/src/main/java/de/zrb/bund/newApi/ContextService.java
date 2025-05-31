package de.zrb.bund.newApi;

import com.azul.crs.client.service.EventService;
import de.zrb.bund.api.ChatManager;

public interface ContextService {
    TabService getTabService();
    ChatService getChatService();
    CommandService getCommandService();
    EventService getEventService();
}