package de.bund.zrb.ui.commands;

import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.api.Bookmarkable;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;

public class BookmarkMenuCommand extends ShortcutMenuCommand {

    private final MainframeContext context;

    public BookmarkMenuCommand(MainframeContext context) {
        this.context = context;
    }

    @Override
    public String getId() {
        return "edit.bookmark";
    }

    @Override
    public String getLabel() {
        return "Bookmark setzen";
    }

    @Override
    public void perform() {
        Bookmarkable tab = context.getSelectedTab().orElse(null);
        if (tab == null) {
            JOptionPane.showMessageDialog(context.getMainFrame(), "Kein aktiver Tab.");
            return;
        }

        String path = tab.getPath();
        if (path == null || path.trim().isEmpty()) {
            JOptionPane.showMessageDialog(context.getMainFrame(), "Kein gültiger Pfad im aktuellen Tab.");
            return;
        }

        String label = JOptionPane.showInputDialog(context.getMainFrame(),
                "Bezeichnung für Bookmark:", tab.getTitle());

        if (label != null && !label.trim().isEmpty()) {
            context.getBookmarkManager().addBookmark(label.trim(), path);
            context.refresh();
            JOptionPane.showMessageDialog(context.getMainFrame(), "Bookmark hinzugefügt:\n" + label);
        }
    }
}
