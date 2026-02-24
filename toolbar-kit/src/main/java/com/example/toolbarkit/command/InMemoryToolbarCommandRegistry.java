package com.example.toolbarkit.command;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Store commands in-memory in registration order.
 */
public class InMemoryToolbarCommandRegistry implements ToolbarCommandRegistry {

    private final Map<String, ToolbarCommand> commands = new LinkedHashMap<String, ToolbarCommand>();

    @Override
    public void register(ToolbarCommand command) {
        if (command == null || command.getId() == null) {
            throw new IllegalArgumentException("command/id must not be null");
        }
        commands.put(command.getId(), command);
    }

    @Override
    public Optional<ToolbarCommand> findById(String id) {
        return Optional.ofNullable(commands.get(id));
    }

    @Override
    public Collection<ToolbarCommand> getAll() {
        return Collections.unmodifiableCollection(commands.values());
    }

    @Override
    public void clear() {
        commands.clear();
    }
}
