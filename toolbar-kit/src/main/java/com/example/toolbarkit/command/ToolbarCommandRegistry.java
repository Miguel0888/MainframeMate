package com.example.toolbarkit.command;

import java.util.Collection;
import java.util.Optional;

/**
 * Provide access to registered commands.
 */
public interface ToolbarCommandRegistry {

    void register(ToolbarCommand command);

    Optional<ToolbarCommand> findById(String id);

    Collection<ToolbarCommand> getAll();

    void clear();
}
