package de.zrb.bund.newApi;

import de.zrb.bund.newApi.listener.EventListener;

public interface EventService {
    <T extends Event> void publish(T event);
    <T extends Event> void subscribe(Class<T> eventType, EventListener<T> listener);
}
