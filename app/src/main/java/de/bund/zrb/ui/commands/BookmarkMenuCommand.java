package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.BrowserConnectionTab;
import de.bund.zrb.ui.FileTabImpl;
import de.bund.zrb.ui.MainFrame;
import de.bund.zrb.ui.SharePointConnectionTab;
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
        return "\u2606 Bookmark setzen";
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

        // Determine backend typSchluessel
        String backendType = "LOCAL";
        if (tab instanceof BrowserConnectionTab) {
            backendType = "BROWSER";
        } else if (tab instanceof SharePointConnectionTab) {
            backendType = "SHAREPOINT";
        } else if (tab instanceof de.bund.zrb.wiki.ui.WikiFileTab
                || tab instanceof de.bund.zrb.wiki.ui.WikiConnectionTab) {
            backendType = "WIKI";
        } else if (tab instanceof de.bund.zrb.ui.ConfluenceConnectionTab
                || tab instanceof de.bund.zrb.ui.ConfluenceReaderTab) {
            backendType = "CONFLUENCE";
        } else if (tab instanceof FileTabImpl) {
            FileTabImpl ft = (FileTabImpl) tab;
            if (ft.getResource() != null) {
                backendType = ft.getResource().getBackendType().name();
            }
        }

        if (context instanceof MainFrame) {
            MainFrame mainFrame = (MainFrame) context;
            boolean added = mainFrame.getBookmarkDrawer().toggleBookmark(path, backendType);
            if (added) {
                JOptionPane.showMessageDialog(context.getMainFrame(), "Bookmark hinzugefügt:\n" + path);
            } else {
                JOptionPane.showMessageDialog(context.getMainFrame(), "Bookmark entfernt:\n" + path);
            }
            // Refresh star buttons in tab headers
            mainFrame.getTabManager().refreshStarButtons();
        }
    }
}
