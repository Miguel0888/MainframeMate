package de.zrb.bund.newApi;

import de.zrb.bund.api.MenuCommand;

import java.util.Collection;
import java.util.Optional;

public interface CommandService {
    void registerCommand(String name, MenuCommand menuCommand);
    Optional<MenuCommand> getCommand(String name);
    Collection<String> getAllCommandNames();
}
