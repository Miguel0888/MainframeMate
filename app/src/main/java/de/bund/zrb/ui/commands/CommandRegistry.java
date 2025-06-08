package de.bund.zrb.ui.commands;

import de.zrb.bund.api.MenuCommand;

import java.util.*;

public class CommandRegistry {

    private static final Map<String, MenuCommand> registry = new LinkedHashMap<>();

    public static void register(MenuCommand menuCommand) {
        registry.put(menuCommand.getId(), menuCommand);
    }

    public static Optional<MenuCommand> getById(String id) {
        return Optional.ofNullable(registry.get(id));
    }

    public static Collection<MenuCommand> getAll() {
        return Collections.unmodifiableCollection(registry.values());
    }

    public static void clear() {
        registry.clear();
    }
}
