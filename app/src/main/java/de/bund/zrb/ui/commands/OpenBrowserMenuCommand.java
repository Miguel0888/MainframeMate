package de.bund.zrb.ui.commands;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.BrowserConnectionTab;
import de.bund.zrb.ui.MainFrame;
import de.bund.zrb.ui.TabbedPaneManager;
import de.bund.zrb.ui.drawer.LeftDrawer;
import de.bund.zrb.ui.drawer.RightDrawer;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Opens a new Browser ConnectionTab.
 * Launches a headless browser (Firefox/Chrome/Edge) and provides a web browsing tab
 * with address bar, text content display, link extraction, and outline.
 */
public class OpenBrowserMenuCommand extends ShortcutMenuCommand {

    private static final Logger LOG = Logger.getLogger(OpenBrowserMenuCommand.class.getName());

    private final JFrame parent;
    private final TabbedPaneManager tabManager;

    public OpenBrowserMenuCommand(JFrame parent, TabbedPaneManager tabManager) {
        this.parent = parent;
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "connection.browser";
    }

    @Override
    public String getLabel() {
        return "\u25CE Browser\u2026";
    }

    @Override
    public void perform() {
        Settings settings = SettingsHelper.load();
        String homePage = settings.browserHomePage;
        if (homePage == null || homePage.trim().isEmpty()) {
            homePage = "https://www.google.com";
        }

        BrowserConnectionTab browserTab = new BrowserConnectionTab(homePage);

        // Wire link navigation: clicking a relation in the left drawer navigates the browser
        if (parent instanceof MainFrame) {
            MainFrame mf = (MainFrame) parent;
            LeftDrawer leftDrawer = mf.getBookmarkDrawer();
            if (leftDrawer != null) {
                // Set link callback: update left drawer when page loads
                browserTab.setLinksCallback(entries ->
                        SwingUtilities.invokeLater(() ->
                                leftDrawer.updateRelations("Browser-Links", entries)));

                // Set relation open callback: navigate when user clicks a link
                leftDrawer.setOnRelationOpen(entry -> {
                    if ("BROWSER_LINK".equals(entry.getType())) {
                        browserTab.navigateToLink(entry.getTargetPath());
                    }
                });
            }

            // Wire outline callback: update RightDrawer when page loads
            RightDrawer rightDrawer = mf.getRightDrawer();
            if (rightDrawer != null) {
                browserTab.setOutlineCallback(outline ->
                        SwingUtilities.invokeLater(() ->
                                rightDrawer.updateWikiOutline(outline,
                                        browserTab.getCurrentTitle(),
                                        (java.util.function.Consumer<String>) null)));
            }
        }

        // Add tab immediately (shows "Browser wird gestartet…")
        tabManager.addTab(browserTab);

        // Launch browser in background
        parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                browserTab.launchBrowser();
                return null;
            }

            @Override
            protected void done() {
                parent.setCursor(Cursor.getDefaultCursor());
                try {
                    get(); // Check for exceptions
                    // Navigate to home page on EDT
                    browserTab.navigateToHomePage();
                    // Update tab title
                    tabManager.updateTitleFor(browserTab);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "[Browser] Launch failed", e);
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    JOptionPane.showMessageDialog(parent,
                            "Browser konnte nicht gestartet werden:\n" + msg,
                            "Browser-Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}

