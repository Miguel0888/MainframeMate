package de.bund.zrb.ui.commands;

import de.zrb.bund.api.MenuCommand;

/**
 * Spezielles Command, das einen Separator im Menü erzeugt.
 * Wird vom MenuTreeBuilder erkannt und als JSeparator gerendert.
 */
public class SeparatorMenuCommand implements MenuCommand {

    private final String id;

    public SeparatorMenuCommand(String menuId, int index) {
        this.id = menuId + ".---" + index;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getLabel() {
        return "---";
    }

    @Override
    public void perform() {
        // Separator führt keine Aktion aus
    }

    /**
     * Prüft, ob eine Command-ID einen Separator repräsentiert.
     */
    public static boolean isSeparator(String id) {
        return id != null && id.contains(".---");
    }

    /**
     * Prüft, ob ein MenuCommand ein Separator ist.
     */
    public static boolean isSeparator(MenuCommand cmd) {
        return cmd instanceof SeparatorMenuCommand ||
               (cmd != null && isSeparator(cmd.getId()));
    }
}

