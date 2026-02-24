package de.bund.zrb.ui.toolbar;

import com.example.toolbarkit.command.ToolbarCommand;
import de.zrb.bund.api.MenuCommand;

import java.util.Collections;
import java.util.List;

/**
 * Adapter that wraps a MainframeMate {@link MenuCommand} as a toolbar-kit {@link ToolbarCommand}.
 */
public class MenuCommandToolbarCommandAdapter implements ToolbarCommand {

    private final MenuCommand delegate;

    public MenuCommandToolbarCommandAdapter(MenuCommand delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public String getLabel() {
        return delegate.getLabel();
    }

    @Override
    public void perform() {
        delegate.perform();
    }

    @Override
    public List<String> getShortcuts() {
        List<String> sc = delegate.getShortcut();
        return sc != null ? sc : Collections.<String>emptyList();
    }

    @Override
    public void setShortcuts(List<String> shortcuts) {
        delegate.setShortcut(shortcuts != null ? shortcuts : Collections.<String>emptyList());
    }
}
