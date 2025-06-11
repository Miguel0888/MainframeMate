package de.bund.zrb.ui.filetab;

import java.util.*;

public class FileTabEventDispatcher {

    public interface Listener<T> {
        void onEvent(T value);
    }

    private final Map<Class<?>, List<Listener<?>>> listeners = new HashMap<>();

    public <T> void subscribe(Class<T> eventType, Listener<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    }

    public <T> void publish(T event) {
        List<Listener<?>> list = listeners.get(event.getClass());
        if (list == null) return;

        for (Listener<?> rawListener : list) {
            @SuppressWarnings("unchecked")
            Listener<T> listener = (Listener<T>) rawListener;
            listener.onEvent(event);
        }
    }
}
