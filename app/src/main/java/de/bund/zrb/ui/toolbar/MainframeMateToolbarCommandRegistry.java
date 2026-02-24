package de.bund.zrb.ui.toolbar;

import com.example.toolbarkit.command.ToolbarCommand;
import com.example.toolbarkit.command.ToolbarCommandRegistry;
import de.bund.zrb.ui.commands.config.CommandRegistryImpl;
import de.zrb.bund.api.MenuCommand;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

/**
 * Adapter that bridges the MainframeMate {@link CommandRegistryImpl} into the
 * toolbar-kit {@link ToolbarCommandRegistry} interface.
 * <p>
 * Every access delegates to {@code CommandRegistryImpl} and wraps each
 * {@link MenuCommand} in a {@link MenuCommandToolbarCommandAdapter}.
 * <p>
 * {@link #register(ToolbarCommand)} and {@link #clear()} are unsupported
 * because the canonical source of truth is {@code CommandRegistryImpl}.
 */
public class MainframeMateToolbarCommandRegistry implements ToolbarCommandRegistry {

    @Override
    public void register(ToolbarCommand command) {
        // Registration happens through CommandRegistryImpl – this adapter is read-only.
        throw new UnsupportedOperationException(
                "Use CommandRegistryImpl.register(MenuCommand) instead.");
    }

    @Override
    public Optional<ToolbarCommand> findById(String id) {
        Optional<MenuCommand> mc = CommandRegistryImpl.getById(id);
        if (mc.isPresent()) {
            return Optional.<ToolbarCommand>of(new MenuCommandToolbarCommandAdapter(mc.get()));
        }
        return Optional.empty();
    }

    @Override
    public Collection<ToolbarCommand> getAll() {
        Collection<MenuCommand> all = CommandRegistryImpl.getAll();
        Collection<ToolbarCommand> result = new ArrayList<ToolbarCommand>(all.size());
        for (MenuCommand mc : all) {
            result.add(new MenuCommandToolbarCommandAdapter(mc));
        }
        return result;
    }

    @Override
    public void clear() {
        // Clearing happens through CommandRegistryImpl – this adapter is read-only.
        throw new UnsupportedOperationException(
                "Use CommandRegistryImpl.clear() instead.");
    }
}
