package de.zrb.bund.newApi.listener;

import de.zrb.bund.newApi.Event;

public interface EventListener<T extends Event> {
    void onEvent(T event);
}
