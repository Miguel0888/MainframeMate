package de.bund.zrb.ui;

import de.bund.zrb.archive.ui.CacheConnectionTab;
import de.bund.zrb.ui.components.HelpButton;
import de.bund.zrb.ui.components.TabbedPaneWithHelpOverlay;
import de.bund.zrb.ui.drawer.LeftDrawer;
import de.bund.zrb.ui.drawer.RightDrawer;
import de.bund.zrb.ui.help.HelpContentProvider;
import de.bund.zrb.ui.jes.JobDetailTab;
import de.bund.zrb.ui.preview.SplitPreviewTab;
import de.bund.zrb.util.AppLogger;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.api.Bookmarkable;
import de.zrb.bund.newApi.ui.FileTab;
import de.zrb.bund.newApi.ui.FtpTab;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TabbedPaneManager {

    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final TabbedPaneWithHelpOverlay tabbedPaneWrapper;
    private final Map<Component, FtpTab> tabMap = new HashMap<>();
    private final MainframeContext mainframeContext;

    public TabbedPaneManager(MainframeContext mainFrame) {
        this.mainframeContext = mainFrame;
        this.tabbedPaneWrapper = new TabbedPaneWithHelpOverlay(tabbedPane);

        // Hilfe-Button als Overlay über der Tab-Leiste
        HelpButton helpButton = new HelpButton("Hilfe zu Datei-Tabs",
                e -> HelpContentProvider.showHelpPopup(
                        (Component) e.getSource(),
                        HelpContentProvider.HelpTopic.MAIN_TABS));
        tabbedPaneWrapper.setHelpComponent(helpButton);

        // Tab change listener for JCL outline updates + focus management
        tabbedPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                updateJclOutlineForSelectedTab();
                // Give the newly selected tab a chance to claim keyboard focus
                Component selected = tabbedPane.getSelectedComponent();
                if (selected != null) {
                    FtpTab tab = tabMap.get(selected);
                    if (tab != null) {
                        // Use invokeLater so the tab is fully visible when focus is requested
                        SwingUtilities.invokeLater(() -> tab.focusSearchField());
                    }
                }
            }
        });

        tabbedPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            private void maybeShowPopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;

                int tabIndex = tabbedPane.indexAtLocation(e.getX(), e.getY());
                if (tabIndex < 0) return;

                Component tabComponent = tabbedPane.getComponentAt(tabIndex);
                FtpTab tab = tabMap.get(tabComponent);
                if (tab == null) return;

                JPopupMenu menu = tab.createContextMenu(() -> closeTab(tabIndex));
                if (menu != null) {
                    menu.show(tabbedPane, e.getX(), e.getY());
                }
            }
        });
    }

    public void addTab(FtpTab tab) {
        tabbedPane.addTab(tab.getTitle(), tab.getComponent());
        int index = tabbedPane.indexOfComponent(tab.getComponent());

        addClosableTabComponent(index, tab);
        tabMap.put(tab.getComponent(), tab);
        tabbedPane.setSelectedComponent(tab.getComponent());
    }

    public void updateTitleFor(FtpTab tab) {
        Component comp = tab.getComponent();
        int index = tabbedPane.indexOfComponent(comp);
        if (index >= 0) {
            tabbedPane.setTitleAt(index, tab.getTitle());

            // Optional: Wenn ein benutzerdefiniertes Tab-Panel (mit Label + Close-Button) verwendet wird:
            Component tabComponent = tabbedPane.getTabComponentAt(index);
            if (tabComponent instanceof JPanel) {
                JPanel panel = (JPanel) tabComponent;
                for (Component c : panel.getComponents()) {
                    if (c instanceof JLabel) {
                        JLabel label = (JLabel) c;
                        label.setText(tab.getTitle());
                        break;
                    }
                }
            }
        }
    }

    public void updateTooltipFor(FtpTab tab) {
        Component comp = tab.getComponent();
        int index = tabbedPane.indexOfComponent(comp);
        if (index >= 0) {
            tabbedPane.setToolTipTextAt(index, tab.getTooltip());
        }
    }

    private static final String STAR_EMPTY = "☆";
    private static final String STAR_FILLED = "★";

    private void addClosableTabComponent(int index, FtpTab tab) {
        JPanel tabPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabPanel.setOpaque(false);

        // Favorite star button for all tabs that have a path
        if (tab instanceof Bookmarkable) {
            JButton starButton = createStarButton(tab);
            tabPanel.add(starButton);
        }

        JLabel titleLabel = new JLabel(tab.getTitle());
        JButton closeButton = new JButton("×");
        closeButton.setMargin(new Insets(0, 5, 0, 5));
        closeButton.setBorder(BorderFactory.createEmptyBorder());
        closeButton.setFocusable(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setToolTipText("Tab schließen");
        closeButton.addActionListener(e -> closeTab(tabbedPane.indexOfComponent(tab.getComponent())));

        tabPanel.add(titleLabel);
        tabPanel.add(closeButton);
        tabbedPane.setTabComponentAt(index, tabPanel);
    }

    /**
     * Determine the backend typSchluessel string for a tab.
     */
    private String getBackendTypeForTab(FtpTab tab) {
        if (tab instanceof FileTabImpl) {
            VirtualResource res = ((FileTabImpl) tab).getResource();
            return res != null ? res.getBackendType().name() : "LOCAL";
        }
        if (tab instanceof ConnectionTabImpl) return "FTP";
        if (tab instanceof MvsConnectionTab) return "FTP";
        if (tab instanceof NdvConnectionTab) return "NDV";
        if (tab instanceof LocalConnectionTabImpl) return "LOCAL";
        if (tab instanceof de.bund.zrb.ui.mail.MailConnectionTab) return "MAIL";
        if (tab instanceof de.bund.zrb.ui.mail.MailPreviewTab) return "MAIL";
        if (tab instanceof CacheConnectionTab) return "CACHE";
        if (tab instanceof de.bund.zrb.wiki.ui.WikiConnectionTab) return "WIKI";
        if (tab instanceof de.bund.zrb.wiki.ui.WikiFileTab) return "WIKI";
        if (tab instanceof de.bund.zrb.ui.terminal.TerminalConnectionTab) return "TN3270";
        return "LOCAL";
    }

    /**
     * Determine the resource kind string for a tab.
     */
    private String getResourceKindForTab(FtpTab tab) {
        if (tab instanceof FileTabImpl) return "FILE";
        return "DIRECTORY";
    }

    /**
     * Get the current path from any tab.
     */
    private String getPathForTab(FtpTab tab) {
        if (tab instanceof Bookmarkable) {
            return ((Bookmarkable) tab).getPath();
        }
        return null;
    }

    private JButton createStarButton(FtpTab tab) {
        String rawPath = getPathForTab(tab);
        String backendType = getBackendTypeForTab(tab);

        LeftDrawer drawer = getBookmarkDrawer();
        boolean isBookmarked = drawer != null && rawPath != null && drawer.isBookmarked(rawPath, backendType);
        AppLogger.get(AppLogger.STAR).fine("createStarButton: rawPath=" + rawPath + " backend=" + backendType + " isBookmarked=" + isBookmarked);

        JButton starButton = new JButton(isBookmarked ? STAR_FILLED : STAR_EMPTY);
        starButton.setMargin(new Insets(0, 0, 0, 2));
        starButton.setBorder(BorderFactory.createEmptyBorder());
        starButton.setFocusable(false);
        starButton.setContentAreaFilled(false);
        starButton.setToolTipText(isBookmarked ? "Lesezeichen entfernen" : "Als Lesezeichen merken");
        starButton.setForeground(isBookmarked ? new Color(255, 200, 0) : Color.GRAY);
        starButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        starButton.addActionListener(e -> {
            String path = getPathForTab(tab);
            String backend = getBackendTypeForTab(tab);
            String kind = getResourceKindForTab(tab);
            LeftDrawer d = getBookmarkDrawer();
            if (d != null && path != null && !path.isEmpty()) {
                // For NDV FILE bookmarks, pass NDV metadata so we can reopen directly
                NdvResourceState ndvState = null;
                if ("NDV".equals(backend) && "FILE".equals(kind) && tab instanceof FileTabImpl) {
                    VirtualResource res = ((FileTabImpl) tab).getResource();
                    if (res != null) ndvState = res.getNdvState();
                }

                // For TN3270 bookmarks, capture macro recording and ask for a name
                String macroSteps = null;
                if ("TN3270".equals(backend) && tab instanceof de.bund.zrb.ui.terminal.TerminalConnectionTab) {
                    de.bund.zrb.ui.terminal.TerminalConnectionTab termTab =
                            (de.bund.zrb.ui.terminal.TerminalConnectionTab) tab;
                    macroSteps = termTab.getMacroStepsJson();
                    // Check if already bookmarked — if so, just remove
                    if (d.isBookmarked(path, backend)) {
                        d.toggleBookmark(path, backend, kind, null, null);
                        starButton.setText(STAR_EMPTY);
                        starButton.setForeground(Color.GRAY);
                        starButton.setToolTipText("Als Lesezeichen merken");
                        return;
                    }
                    // Prompt for bookmark name
                    String defaultName = "🖥️ 3270 → " + path;
                    String name = (String) javax.swing.JOptionPane.showInputDialog(
                            tabbedPane, "Name für das Terminal-Lesezeichen:",
                            "3270 Lesezeichen", javax.swing.JOptionPane.PLAIN_MESSAGE,
                            null, null, defaultName);
                    if (name == null || name.trim().isEmpty()) return; // cancelled

                    // Create bookmark directly with custom label
                    String prefixedPath = de.bund.zrb.model.BookmarkEntry.buildPath(backend, path);
                    de.bund.zrb.model.BookmarkEntry entry =
                            new de.bund.zrb.model.BookmarkEntry(name.trim(), prefixedPath, false);
                    entry.resourceKind = "CONNECTION";
                    entry.tn3270MacroSteps = macroSteps;
                    de.bund.zrb.helper.BookmarkHelper.addBookmarkToFolder("Allgemein", entry);
                    d.refreshBookmarks();
                    starButton.setText(STAR_FILLED);
                    starButton.setForeground(new Color(255, 200, 0));
                    starButton.setToolTipText("Lesezeichen entfernen");
                    return;
                }

                boolean added = d.toggleBookmark(path, backend, kind, ndvState);
                starButton.setText(added ? STAR_FILLED : STAR_EMPTY);
                starButton.setForeground(added ? new Color(255, 200, 0) : Color.GRAY);
                starButton.setToolTipText(added ? "Lesezeichen entfernen" : "Als Lesezeichen merken");
            }
        });

        return starButton;
    }

    public LeftDrawer getBookmarkDrawer() {
        if (mainframeContext instanceof MainFrame) {
            return ((MainFrame) mainframeContext).getBookmarkDrawer();
        }
        return null;
    }

    public void closeTab(int index) {
        Component comp = tabbedPane.getComponentAt(index);
        FtpTab tab = tabMap.remove(comp);
        if (tab != null) tab.onClose();
        tabbedPane.remove(index);
    }

    /**
     * Close all tabs that are instances of the given class.
     * Each tab's onClose() is called before removal.
     */
    public void closeTabsOfType(Class<?> tabClass) {
        // Iterate backwards to avoid index shift issues
        for (int i = tabbedPane.getTabCount() - 1; i >= 0; i--) {
            Component comp = tabbedPane.getComponentAt(i);
            FtpTab tab = tabMap.get(comp);
            if (tab != null && tabClass.isInstance(tab)) {
                tabMap.remove(comp);
                tab.onClose();
                tabbedPane.remove(i);
            }
        }
    }

    /**
     * Refresh all star (favorite) buttons in tab headers to match current bookmark state.
     */
    public void refreshStarButtons() {
        LeftDrawer drawer = getBookmarkDrawer();
        if (drawer == null) return;

        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            Component tabComp = tabbedPane.getTabComponentAt(i);
            if (!(tabComp instanceof JPanel)) continue;

            Component contentComp = tabbedPane.getComponentAt(i);
            FtpTab tab = tabMap.get(contentComp);
            if (tab == null) continue;

            String rawPath = getPathForTab(tab);
            boolean isBookmarked;
            if (rawPath == null || rawPath.isEmpty()) {
                isBookmarked = false;
            } else {
                String backendType = getBackendTypeForTab(tab);
                isBookmarked = drawer.isBookmarked(rawPath, backendType);
            }

            JPanel panel = (JPanel) tabComp;
            for (Component c : panel.getComponents()) {
                if (c instanceof JButton) {
                    JButton btn = (JButton) c;
                    String text = btn.getText();
                    if (STAR_EMPTY.equals(text) || STAR_FILLED.equals(text)) {
                        btn.setText(isBookmarked ? STAR_FILLED : STAR_EMPTY);
                        btn.setForeground(isBookmarked ? new Color(255, 200, 0) : Color.GRAY);
                        btn.setToolTipText(isBookmarked ? "Lesezeichen entfernen" : "Als Lesezeichen merken");
                        break;
                    }
                }
            }
        }
    }

    /**
     * Refresh the star button for a specific tab (e.g. after directory navigation).
     */
    public void refreshStarForTab(FtpTab tab) {
        if (tab == null) return;
        LeftDrawer drawer = getBookmarkDrawer();
        if (drawer == null) return;

        Component comp = tab.getComponent();
        int index = tabbedPane.indexOfComponent(comp);
        if (index < 0) return;

        Component tabComp = tabbedPane.getTabComponentAt(index);
        if (!(tabComp instanceof JPanel)) return;

        String rawPath = getPathForTab(tab);
        if (rawPath == null || rawPath.isEmpty()) return;

        String backendType = getBackendTypeForTab(tab);
        boolean isBookmarked = drawer.isBookmarked(rawPath, backendType);
        AppLogger.get(AppLogger.STAR).fine("refreshStarForTab: rawPath=" + rawPath + " backend=" + backendType + " isBookmarked=" + isBookmarked);

        JPanel panel = (JPanel) tabComp;
        for (Component c : panel.getComponents()) {
            if (c instanceof JButton) {
                JButton btn = (JButton) c;
                String text = btn.getText();
                if (STAR_EMPTY.equals(text) || STAR_FILLED.equals(text)) {
                    btn.setText(isBookmarked ? STAR_FILLED : STAR_EMPTY);
                    btn.setForeground(isBookmarked ? new Color(255, 200, 0) : Color.GRAY);
                    btn.setToolTipText(isBookmarked ? "Lesezeichen entfernen" : "Als Lesezeichen merken");
                    break;
                }
            }
        }
    }

    public void saveSelectedComponent() {
        Component comp = tabbedPane.getSelectedComponent();
        FtpTab tab = tabMap.get(comp);
        if (tab != null) tab.saveIfApplicable();
    }

    public void saveAndCloseSelectedComponent() {
        Component comp = tabbedPane.getSelectedComponent();
        if (comp == null) return;

        FtpTab tab = tabMap.get(comp);
        if (tab != null) {
            tab.saveIfApplicable(); // erst speichern
            int index = tabbedPane.indexOfComponent(comp);
            if (index >= 0) {
                closeTab(index); // dann schließen
            }
        }
    }

    public void closeSelectedComponent() {
        Component comp = tabbedPane.getSelectedComponent();
        if (comp == null) return;
        int index = tabbedPane.indexOfComponent(comp);
        if (index >= 0) {
            closeTab(index);
        }
    }

    public JComponent getComponent() {
        return tabbedPaneWrapper;
    }

    public Component getSelectedComponent() {
        return tabbedPane.getSelectedComponent();
    }

    /**
     * Öffnet einen neuen FileTab basierend auf VirtualResource.
     */
    public FileTab openFileTab(VirtualResource resource, String content, String sentenceType, String searchPattern, Boolean toCompare) {
        de.bund.zrb.ui.FileTabImpl fileTabImpl = new de.bund.zrb.ui.FileTabImpl(this, resource, content, sentenceType, searchPattern, toCompare);
        addTab(fileTabImpl);
        focusTabByAdapter(fileTabImpl);
        return fileTabImpl;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Plugin-Management
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public Optional<FtpTab> getSelectedTab() {
        Component selected = tabbedPane.getSelectedComponent();
        return Optional.ofNullable(tabMap.get(selected));
    }

    public Optional<Bookmarkable> getSelectedFileTab() {
        Component selected = tabbedPane.getSelectedComponent();
        FtpTab tab = tabMap.get(selected);
        if (tab instanceof FileTabImpl) {
            return Optional.of((FileTabImpl) tab);
        }
        return Optional.empty();
    }


    public java.util.List<Bookmarkable> getAllTabs() {
        java.util.List<Bookmarkable> result = new java.util.ArrayList<>();
        for (FtpTab tab : tabMap.values()) {
            if (tab instanceof Bookmarkable) {
                result.add((Bookmarkable) tab);
            }
        }
        return result;
    }

    public java.util.List<FtpTab> getAllOpenTabs() {
        return new java.util.ArrayList<>(tabMap.values());
    }

    public void focusTabByAdapter(Bookmarkable tab) {
        if (!(tab instanceof FtpTab)) return;

        Component comp = ((FtpTab) tab).getComponent();
        int index = tabbedPane.indexOfComponent(comp);
        if (index >= 0) {
            tabbedPane.setSelectedIndex(index);
        }
    }

    public MainframeContext getMainframeContext() {
        return mainframeContext;
    }

    public void replaceTab(FtpTab oldTab, FtpTab newTab) {
        Component oldComponent = oldTab.getComponent();
        int index = tabbedPane.indexOfComponent(oldComponent);
        if (index >= 0) {
            tabbedPane.setComponentAt(index, newTab.getComponent());
            tabMap.remove(oldComponent);
            tabMap.put(newTab.getComponent(), newTab);
            updateTitleFor(newTab);
            updateTooltipFor(newTab);
        }
    }

    /**
     * Get the parent frame for dialogs.
     */
    public Frame getParentFrame() {
        if (mainframeContext instanceof Frame) {
            return (Frame) mainframeContext;
        }
        return (Frame) SwingUtilities.getWindowAncestor(tabbedPane);
    }

    /**
     * Update JCL outline panel when a tab is selected.
     * Detects if the selected tab contains JCL content and updates the outline.
     */
    /**
     * Public entry point to refresh the outline panel for the currently active tab.
     * Called when the sentence type dropdown changes.
     */
    public void refreshOutlineForActiveTab() {
        updateJclOutlineForSelectedTab();
    }

    private void updateJclOutlineForSelectedTab() {
        // Get RightDrawer from MainFrame
        if (!(mainframeContext instanceof MainFrame)) {
            return;
        }

        MainFrame mainFrame = (MainFrame) mainframeContext;
        RightDrawer rightDrawer = mainFrame.getRightDrawer();
        if (rightDrawer == null) {
            return;
        }

        Component selected = tabbedPane.getSelectedComponent();
        if (selected == null) {
            rightDrawer.clearJclOutline();
            return;
        }

        FtpTab tab = tabMap.get(selected);
        if (tab == null) {
            rightDrawer.clearJclOutline();
            rightDrawer.restoreCodeOutline();
            return;
        }

        // WikiFileTab: show wiki outline in RightDrawer + relations in LeftDrawer
        if (tab instanceof de.bund.zrb.wiki.ui.WikiFileTab) {
            de.bund.zrb.wiki.ui.WikiFileTab wikiTab = (de.bund.zrb.wiki.ui.WikiFileTab) tab;
            de.bund.zrb.wiki.domain.OutlineNode outline = wikiTab.getOutline();
            if (outline != null) {
                rightDrawer.updateWikiOutline(outline, wikiTab.getPageTitle(), wikiTab);
            } else {
                rightDrawer.restoreCodeOutline();
                rightDrawer.clearJclOutline();
            }
            updateRelationsForWikiTab(mainFrame, wikiTab);
            return;
        }

        // Restore code outline for non-wiki tabs
        rightDrawer.restoreCodeOutline();

        // Update relations for non-wiki tabs
        updateRelationsForNonWikiTab(mainFrame, tab);

        // Get content, source name, and sentence type from the tab
        String content = null;
        String sourceName = null;
        String sentenceType = null;

        if (tab instanceof FileTabImpl) {
            FileTabImpl fileTab = (FileTabImpl) tab;
            content = fileTab.getContent();
            sourceName = fileTab.getPath();
            sentenceType = fileTab.getModel().getSentenceType();
        } else if (tab instanceof JobDetailTab) {
            JobDetailTab jesTab = (JobDetailTab) tab;
            content = jesTab.getContent();
            sourceName = jesTab.getPath();
            sentenceType = jesTab.getEffectiveLanguageHint();
            // Wire outline refresh so language dropdown changes update the outline
            jesTab.setOutlineRefreshCallback(this::refreshOutlineForActiveTab);
        } else if (tab instanceof SplitPreviewTab) {
            SplitPreviewTab previewTab = (SplitPreviewTab) tab;
            content = previewTab.getContent();
            sourceName = previewTab.getPath();
        }

        if (content == null || content.isEmpty()) {
            rightDrawer.clearJclOutline();
            return;
        }

        // Determine whether to show outline based on the sentence type from dropdown
        boolean showOutline = false;
        if (sentenceType != null && !sentenceType.isEmpty()) {
            String upper = sentenceType.toUpperCase();
            showOutline = upper.contains("JCL") || upper.contains("COBOL") || upper.contains("NATURAL");
        } else {
            // Fallback: auto-detect from content if no sentence type is set in the dropdown
            showOutline = isJclContent(content) || isCobolContent(content) || isNaturalContent(content);
        }

        if (showOutline) {
            rightDrawer.updateJclOutline(content, sourceName, sentenceType);

            // Set up line navigator to jump to line in editor
            rightDrawer.getOutlinePanel().setLineNavigator(lineNumber -> {
                navigateToLine(tab, lineNumber);
            });
        } else {
            rightDrawer.clearJclOutline();
        }
    }

    /**
     * Check if content looks like JCL.
     */
    private boolean isJclContent(String content) {
        if (content == null || content.length() < 3) return false;

        String[] lines = content.split("\\r?\\n", 20);
        int jclLineCount = 0;

        for (String line : lines) {
            if (line.startsWith("//")) {
                jclLineCount++;
            }
        }

        // Consider it JCL if at least 2 lines start with //
        return jclLineCount >= 2;
    }

    /**
     * Check if content looks like COBOL.
     */
    private boolean isCobolContent(String content) {
        if (content == null || content.length() < 10) return false;

        String[] lines = content.split("\\r?\\n", 30);
        int cobolHits = 0;
        for (String line : lines) {
            String upper = line.toUpperCase();
            if (upper.contains("IDENTIFICATION DIVISION")
                    || upper.contains("PROCEDURE DIVISION")
                    || upper.contains("DATA DIVISION")
                    || upper.contains("ENVIRONMENT DIVISION")
                    || upper.contains("WORKING-STORAGE SECTION")
                    || upper.contains("PROGRAM-ID")) {
                cobolHits++;
            }
        }
        return cobolHits >= 1;
    }

    /**
     * Check if content looks like Natural (Software AG).
     */
    private boolean isNaturalContent(String content) {
        if (content == null || content.length() < 10) return false;

        String[] lines = content.split("\\r?\\n", 40);
        int naturalHits = 0;
        for (String line : lines) {
            String trimmed = line.trim().toUpperCase();
            if (trimmed.startsWith("DEFINE DATA")
                    || trimmed.startsWith("END-DEFINE")
                    || trimmed.startsWith("DEFINE SUBROUTINE")
                    || trimmed.startsWith("CALLNAT ")
                    || trimmed.startsWith("END-SUBROUTINE")
                    || trimmed.startsWith("LOCAL USING")
                    || trimmed.startsWith("PARAMETER USING")
                    || trimmed.startsWith("DECIDE ON")
                    || trimmed.startsWith("DECIDE FOR")
                    || trimmed.startsWith("INPUT USING MAP")
                    || trimmed.startsWith("FETCH RETURN")) {
                naturalHits++;
            }
        }
        return naturalHits >= 2;
    }

    /**
     * Navigate to a specific line in the tab's editor.
     */
    private void navigateToLine(FtpTab tab, int lineNumber) {
        try {
            if (tab instanceof FileTabImpl) {
                FileTabImpl fileTab = (FileTabImpl) tab;
                org.fife.ui.rsyntaxtextarea.RSyntaxTextArea textArea = fileTab.getRawPane();
                if (textArea != null) {
                    int offset = textArea.getLineStartOffset(lineNumber - 1);
                    textArea.setCaretPosition(offset);
                    textArea.requestFocusInWindow();
                }
            } else if (tab instanceof JobDetailTab) {
                JobDetailTab jesTab = (JobDetailTab) tab;
                org.fife.ui.rsyntaxtextarea.RSyntaxTextArea textArea = jesTab.getContentArea();
                if (textArea != null) {
                    int offset = textArea.getLineStartOffset(lineNumber - 1);
                    textArea.setCaretPosition(offset);
                    textArea.requestFocusInWindow();
                }
            } else if (tab instanceof SplitPreviewTab) {
                SplitPreviewTab previewTab = (SplitPreviewTab) tab;
                org.fife.ui.rsyntaxtextarea.RSyntaxTextArea textArea = previewTab.getRawPane();
                if (textArea != null) {
                    int offset = textArea.getLineStartOffset(lineNumber - 1);
                    textArea.setCaretPosition(offset);
                    textArea.requestFocusInWindow();
                }
            }
        } catch (Exception e) {
            System.err.println("Could not navigate to line " + lineNumber + ": " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Relations (LeftDrawer) support
    // ═══════════════════════════════════════════════════════════

    private void updateRelationsForWikiTab(MainFrame mainFrame, de.bund.zrb.wiki.ui.WikiFileTab wikiTab) {
        LeftDrawer leftDrawer = mainFrame.getBookmarkDrawer();
        de.bund.zrb.service.RelationsService relationsService = mainFrame.getRelationsService();
        if (leftDrawer == null || relationsService == null) return;

        String tabPath = wikiTab.getPath(); // wiki://siteId/pageTitle

        // Check cache first
        java.util.List<LeftDrawer.RelationEntry> cached = relationsService.getCached(tabPath);
        if (cached != null) {
            leftDrawer.updateRelations("Wiki-Links", cached);
            return;
        }

        // Show loading and resolve in background
        leftDrawer.showRelationsLoading();

        de.bund.zrb.wiki.domain.WikiSiteId siteId =
                new de.bund.zrb.wiki.domain.WikiSiteId(wikiTab.getSiteId());

        relationsService.resolveWikiLinks(siteId, wikiTab.getPageTitle(), tabPath,
                new de.bund.zrb.service.RelationsService.RelationsCallback() {
                    @Override
                    public void onRelationsResolved(java.util.List<LeftDrawer.RelationEntry> entries) {
                        leftDrawer.updateRelations("Wiki-Links", entries);
                    }
                });
    }

    private void updateRelationsForNonWikiTab(MainFrame mainFrame, de.zrb.bund.newApi.ui.FtpTab tab) {
        LeftDrawer leftDrawer = mainFrame.getBookmarkDrawer();
        if (leftDrawer == null) return;

        // Check if it's a program source (JCL/COBOL/Natural) → show placeholder
        String sentenceType = null;
        if (tab instanceof FileTabImpl) {
            sentenceType = ((FileTabImpl) tab).getModel().getSentenceType();
        }

        if (sentenceType != null) {
            String upper = sentenceType.toUpperCase();
            if (upper.contains("JCL") || upper.contains("COBOL") || upper.contains("NATURAL")) {
                leftDrawer.showRelationsPlaceholder("Dependencies werden in einer zukünftigen Version unterstützt.");
                return;
            }
        }

        leftDrawer.clearRelations();
    }

    /**
     * Open a wiki page from a relation entry as a new WikiFileTab.
     * Uses the existing WikiContentService if a WikiConnectionTab is open,
     * otherwise falls back to a fresh service from settings.
     */
    public void openWikiRelationAsTab(String siteId, String pageTitle) {
        // Try to find an open WikiConnectionTab to get its service + callback
        for (java.util.Map.Entry<Component, de.zrb.bund.newApi.ui.FtpTab> entry : tabMap.entrySet()) {
            if (entry.getValue() instanceof de.bund.zrb.wiki.ui.WikiConnectionTab) {
                de.bund.zrb.wiki.ui.WikiConnectionTab wikiConn =
                        (de.bund.zrb.wiki.ui.WikiConnectionTab) entry.getValue();
                // Trigger the connection tab's open mechanism
                wikiConn.openPageExternally(siteId, pageTitle);
                return;
            }
        }
        // If no wiki connection tab is open, we can't resolve the page
        javax.swing.JOptionPane.showMessageDialog(tabbedPane,
                "Bitte öffnen Sie zuerst einen Wiki-Tab unter Verbindung → Wiki.",
                "Kein Wiki verbunden", javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }
}
