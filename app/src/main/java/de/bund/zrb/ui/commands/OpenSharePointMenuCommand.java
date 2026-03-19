package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.MainFrame;
import de.bund.zrb.ui.SharePointConnectionTab;
import de.bund.zrb.ui.TabbedPaneManager;
import de.bund.zrb.ui.drawer.LeftDrawer;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;
import java.util.logging.Logger;

/**
 * Opens a new SharePoint ConnectionTab.
 * Mounts selected SharePoint sites as WebDAV network paths and provides
 * a file-browser-like view with automatic caching.
 */
public class OpenSharePointMenuCommand extends ShortcutMenuCommand {

    private static final Logger LOG = Logger.getLogger(OpenSharePointMenuCommand.class.getName());

    private final JFrame parent;
    private final TabbedPaneManager tabManager;

    public OpenSharePointMenuCommand(JFrame parent, TabbedPaneManager tabManager) {
        this.parent = parent;
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "connection.sharepoint";
    }

    @Override
    public String getLabel() {
        return "SharePoint\u2026";
    }

    @Override
    public void perform() {
        SharePointConnectionTab spTab = new SharePointConnectionTab();
        spTab.setTabbedPaneManager(tabManager);

        // Wire link callback for left drawer
        if (parent instanceof MainFrame) {
            MainFrame mf = (MainFrame) parent;
            LeftDrawer leftDrawer = mf.getBookmarkDrawer();
            if (leftDrawer != null) {
                spTab.setLinksCallback(entries ->
                        SwingUtilities.invokeLater(() ->
                                leftDrawer.updateRelations("SharePoint-Dateien", entries)));

                leftDrawer.setOnRelationOpen(entry -> {
                    if ("SP_FILE".equals(entry.getType())) {
                        spTab.navigateToUrl(entry.getTargetPath());
                    }
                });
            }
        }

        tabManager.addTab(spTab);
    }
}
