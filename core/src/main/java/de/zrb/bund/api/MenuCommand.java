package de.zrb.bund.api;

import java.util.Collections;
import java.util.List;

public interface MenuCommand {
    String getId();               // z. B. "file.save"
    String getLabel();            // z. B. "Speichern"
    void perform();
    default List<String> getShortcut() {
        return Collections.emptyList();
    }
    default void setShortcut(List<String> shortcut) {}
}
