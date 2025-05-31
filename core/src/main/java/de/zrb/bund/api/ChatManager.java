package de.zrb.bund.api;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public interface ChatManager {

    /**
     * Startet eine neue Konversationssitzung.
     *
     * @return eine eindeutige Session-Id
     */
    UUID newSession();

    /**
     * Antwortet auf eine Benutzereingabe in einer bestimmten Sitzung.
     *
     * @param sessionId  eindeutige Sitzungs-ID
     * @param useContext
     * @param prompt     Benutzereingabe
     * @param listener   Callback für Streaming-Ereignisse
     * @param keepAlive  ob das Modell aktiv gehalten werden soll
     * @return
     * @throws IOException bei Transportfehlern
     */
    boolean streamAnswer(UUID sessionId, boolean useContext, String prompt, ChatStreamListener listener, boolean keepAlive) throws IOException;

    /**
     * Gibt die komplette Nachrichten-Historie für eine Session zurück.
     *
     * @param sessionId eindeutige Sitzungs-ID
     * @return Liste aller bisherigen Nachrichten (inkl. Rolleninfo)
     */
    List<String> getHistory(UUID sessionId);

    /**
     * Löscht die Historie einer bestimmten Sitzung.
     *
     * @param sessionId eindeutige Sitzungs-ID
     */
    void clearHistory(UUID sessionId);

    void addUserMessage(UUID sessionId, String message);
    void addBotMessage(UUID sessionId, String message);

    void cancel(UUID sessionId);

    void onDispose();
}
