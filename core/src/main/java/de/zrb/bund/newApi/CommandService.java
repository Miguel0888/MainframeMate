package de.zrb.bund.newApi;

import de.zrb.bund.api.Command;

import java.util.Collection;
import java.util.Optional;

public interface CommandService {
    void registerCommand(String name, Command command);
    Optional<Command> getCommand(String name);
    Collection<String> getAllCommandNames();
}
