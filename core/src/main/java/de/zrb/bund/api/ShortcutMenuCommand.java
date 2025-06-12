package de.zrb.bund.api;

import java.util.ArrayList;
import java.util.List;

public abstract class ShortcutMenuCommand implements MenuCommand {

    private final List<String> shortcut = new ArrayList<>();

    @Override
    public List<String> getShortcut() {
        return new ArrayList<>(shortcut);
    }

    @Override
    public void setShortcut(List<String> newShortcut) {
        shortcut.clear();
        if (newShortcut != null) {
            shortcut.addAll(newShortcut);
        }
    }
}
