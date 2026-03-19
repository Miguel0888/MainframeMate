package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.MainFrame;
import de.bund.zrb.ui.SharePointConnectionTab;
import de.bund.zrb.ui.TabbedPaneManager;
import de.bund.zrb.ui.drawer.LeftDrawer;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;
import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Opens a new SharePoint ConnectionTab.
 * Launches a headless browser to discover SharePoint sites from a configured parent page,
 * then provides a file-browser-like view with automatic caching.
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

        // Wire link callback for left drawer
        if (parent instanceof MainFrame) {
            MainFrame mf = (MainFrame) parent;
            LeftDrawer leftDrawer = mf.getBookmarkDrawer();
            if (leftDrawer != null) {
                spTab.setLinksCallback(entries ->
                        SwingUtilities.invokeLater(() ->
                                leftDrawer.updateRelations("SharePoint-Seiten", entries)));

                leftDrawer.setOnRelationOpen(entry -> {
                    if ("SP_LINK".equals(entry.getType())) {
                        spTab.navigateToUrl(entry.getTargetPath());
                    }
                });
            }
        }

        // Add tab immediately
        tabManager.addTab(spTab);

        // Launch browser and load sites in background
        parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                spTab.launchBrowser();
                return null;
            }

            @Override
            protected void done() {
                parent.setCursor(Cursor.getDefaultCursor());
                try {
                    get(); // Check for exceptions
                    spTab.loadSitesInBackground();
                    tabManager.updateTitleFor(spTab);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "[SP] Launch failed", e);
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    JOptionPane.showMessageDialog(parent,
                            "SharePoint-Browser konnte nicht gestartet werden:\n" + msg,
                            "SharePoint-Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}

