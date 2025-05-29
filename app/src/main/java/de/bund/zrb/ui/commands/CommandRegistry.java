package de.bund.zrb.ui.commands;

import de.zrb.bund.api.Command;

import java.util.*;

public class CommandRegistry {

    private static final Map<String, Command> registry = new LinkedHashMap<>();

    public static void register(Command command) {
        registry.put(command.getId(), command);
    }

    public static Optional<Command> getById(String id) {
        return Optional.ofNullable(registry.get(id));
    }

    public static Collection<Command> getAll() {
        return Collections.unmodifiableCollection(registry.values());
    }

    public static void clear() {
        registry.clear();
    }
}
