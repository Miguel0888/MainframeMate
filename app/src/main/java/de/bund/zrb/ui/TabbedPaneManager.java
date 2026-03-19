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
        if (tab instanceof FtpConnectionTabImpl) return "FTP";
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

        // WikiConnectionTab: show outline + relations for the currently previewed page
        if (tab instanceof de.bund.zrb.wiki.ui.WikiConnectionTab) {
            de.bund.zrb.wiki.ui.WikiConnectionTab wikiConnTab =
                    (de.bund.zrb.wiki.ui.WikiConnectionTab) tab;
            de.bund.zrb.wiki.domain.OutlineNode outline = wikiConnTab.getCurrentOutline();
            if (outline != null) {
                java.util.function.Consumer<String> scroller = anchor -> wikiConnTab.scrollToAnchor(anchor);
                rightDrawer.updateWikiOutline(outline, wikiConnTab.getCurrentPageTitle(), scroller);
            } else {
                rightDrawer.restoreCodeOutline();
                rightDrawer.clearJclOutline();
            }
            updateRelationsForWikiPreview(mainFrame, wikiConnTab);
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

            // Auto-switch to Outline tab so the user can see the outline
            rightDrawer.showOutlineTab();
        } else {
            rightDrawer.clearJclOutline();
        }
    }

    /**
     * Check if content looks like JCL.
     */
    private boolean isJclContent(String content) {
        if (content == null || content.length() < 3) return false;

        String[] lines = content.split("\\r?\\n", 80);
        int jclLineCount = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//")) {
                jclLineCount++;
            } else {
                // JES spool output may prepend line numbers, e.g. "   1 //JOBNAME JOB ..."
                String stripped = trimmed.replaceFirst("^\\d+\\s+", "");
                if (stripped.startsWith("//")) {
                    jclLineCount++;
                }
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

    /**
     * Navigate to a specific line in the given tab's editor (public facade).
     *
     * @param tab        the tab to navigate in
     * @param lineNumber 1-based line number
     */
    public void navigateToLineInTab(FtpTab tab, int lineNumber) {
        navigateToLine(tab, lineNumber);
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

    /**
     * Update the LeftDrawer relations panel for a WikiConnectionTab preview.
     * Works like {@link #updateRelationsForWikiTab} but reads siteId/pageTitle
     * from the previewed page instead of from a WikiFileTab.
     */
    private void updateRelationsForWikiPreview(MainFrame mainFrame,
                                               de.bund.zrb.wiki.ui.WikiConnectionTab wikiConnTab) {
        LeftDrawer leftDrawer = mainFrame.getBookmarkDrawer();
        de.bund.zrb.service.RelationsService relationsService = mainFrame.getRelationsService();
        if (leftDrawer == null || relationsService == null) return;

        de.bund.zrb.wiki.domain.WikiSiteId siteId = wikiConnTab.getCurrentSiteId();
        String pageTitle = wikiConnTab.getCurrentPageTitle();
        if (siteId == null || pageTitle == null || pageTitle.isEmpty()) {
            leftDrawer.showRelationsPlaceholder("Keine Vorschau geladen.");
            return;
        }

        String cachePath = "wiki://" + siteId.value() + "/" + pageTitle;

        // Check cache first
        java.util.List<LeftDrawer.RelationEntry> cached = relationsService.getCached(cachePath);
        if (cached != null) {
            leftDrawer.updateRelations("Wiki-Links", cached);
            return;
        }

        // Show loading and resolve in background
        leftDrawer.showRelationsLoading();

        relationsService.resolveWikiLinks(siteId, pageTitle, cachePath,
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

        // Check if it's a Natural source → analyze dependencies
        String content = null;
        String sourceName = null;
        String sentenceType = null;

        if (tab instanceof FileTabImpl) {
            FileTabImpl fileTab = (FileTabImpl) tab;
            content = fileTab.getContent();
            sourceName = fileTab.getPath();
            sentenceType = fileTab.getModel().getSentenceType();
        } else if (tab instanceof de.bund.zrb.ui.jes.JobDetailTab) {
            de.bund.zrb.ui.jes.JobDetailTab jesTab = (de.bund.zrb.ui.jes.JobDetailTab) tab;
            content = jesTab.getContent();
            sourceName = jesTab.getPath();
            sentenceType = jesTab.getEffectiveLanguageHint();
        }

        final de.bund.zrb.service.NaturalAnalysisService analysisService =
                de.bund.zrb.service.NaturalAnalysisService.getInstance();

        // For Natural sources, show real dependencies (active + passive XRefs) + call hierarchy
        if (content != null && analysisService.isNaturalSource(content, sentenceType)) {
            leftDrawer.showRelationsLoading();
            leftDrawer.showCallHierarchyLoading();
            final String src = content;
            final String name = sourceName;
            final String lib = extractLibrary(tab);
            new javax.swing.SwingWorker<de.bund.zrb.service.NaturalDependencyService.DependencyResult, Void>() {
                @Override
                protected de.bund.zrb.service.NaturalDependencyService.DependencyResult doInBackground() {
                    return analysisService.analyzeDependencies(src, name);
                }

                @Override
                protected void done() {
                    try {
                        de.bund.zrb.service.NaturalDependencyService.DependencyResult result = get();
                        showFullDependenciesInLeftDrawer(leftDrawer, result, lib, name);
                        // Also populate call hierarchy from graph (if available)
                        populateCallHierarchy(leftDrawer, lib, name);
                    } catch (Exception ex) {
                        leftDrawer.showRelationsPlaceholder("Fehler bei Abhängigkeitsanalyse: " + ex.getMessage());
                        leftDrawer.clearCallHierarchy();
                    }
                }
            }.execute();
            return;
        }

        // For JCL sources, show real dependencies (PGM, PROC, INCLUDE, JCLLIB, DSN) + call hierarchy
        final de.bund.zrb.service.JclDependencyService jclDependencyService =
                de.bund.zrb.service.JclDependencyService.getInstance();
        if (content != null && jclDependencyService.isJclSource(content, sentenceType)) {
            leftDrawer.showRelationsLoading();
            leftDrawer.showCallHierarchyLoading();
            final String jclContent = content;
            final String jclName = sourceName;
            new javax.swing.SwingWorker<Object[], Void>() {
                @Override
                protected Object[] doInBackground() {
                    de.bund.zrb.service.JclDependencyService.JclDependencyResult deps =
                            jclDependencyService.analyze(jclContent, jclName);
                    java.util.List<de.bund.zrb.service.JclDependencyService.JclCallNode> hierarchy =
                            jclDependencyService.buildCallHierarchy(jclContent, jclName);
                    return new Object[]{deps, hierarchy};
                }

                @Override
                @SuppressWarnings("unchecked")
                protected void done() {
                    try {
                        Object[] results = get();
                        de.bund.zrb.service.JclDependencyService.JclDependencyResult depsResult =
                                (de.bund.zrb.service.JclDependencyService.JclDependencyResult) results[0];
                        java.util.List<de.bund.zrb.service.JclDependencyService.JclCallNode> hierarchy =
                                (java.util.List<de.bund.zrb.service.JclDependencyService.JclCallNode>) results[1];
                        showJclDependenciesInLeftDrawer(leftDrawer, depsResult);
                        showJclCallHierarchy(leftDrawer, hierarchy, jclName);
                    } catch (Exception ex) {
                        leftDrawer.showRelationsPlaceholder("Fehler bei JCL-Abhängigkeitsanalyse: " + ex.getMessage());
                        leftDrawer.clearCallHierarchy();
                    }
                }
            }.execute();
            return;
        }

        // For COBOL, show placeholder (future)
        if (sentenceType != null) {
            String upper = sentenceType.toUpperCase();
            if (upper.contains("COBOL")) {
                leftDrawer.showRelationsPlaceholder("COBOL-Dependencies werden in einer zukünftigen Version unterstützt.");
                return;
            }
        }

        leftDrawer.clearRelations();
    }

    /** @deprecated Use {@link de.bund.zrb.service.NaturalAnalysisService} instead. Kept for backward compatibility. */
    private de.bund.zrb.service.NaturalDependencyService naturalDependencyService;

    private de.bund.zrb.service.NaturalDependencyService getNaturalDependencyService() {
        if (naturalDependencyService == null) {
            naturalDependencyService = new de.bund.zrb.service.NaturalDependencyService();
        }
        return naturalDependencyService;
    }

    /** Dependency graph per library — now delegated to NaturalAnalysisService. */
    private final java.util.Map<String, de.bund.zrb.service.NaturalDependencyGraph> dependencyGraphs =
            new java.util.concurrent.ConcurrentHashMap<String, de.bund.zrb.service.NaturalDependencyGraph>();

    /**
     * Get or create a dependency graph for a library.
     * Delegates to {@link de.bund.zrb.service.NaturalAnalysisService}.
     */
    public de.bund.zrb.service.NaturalDependencyGraph getDependencyGraph(String library) {
        return de.bund.zrb.service.NaturalAnalysisService.getInstance().getGraph(library);
    }

    /**
     * Register an externally-built dependency graph.
     * Delegates to {@link de.bund.zrb.service.NaturalAnalysisService}.
     */
    public void registerDependencyGraph(String library, de.bund.zrb.service.NaturalDependencyGraph graph) {
        de.bund.zrb.service.NaturalAnalysisService.getInstance().registerGraph(library, graph);
    }

    /**
     * Remove the dependency graph for a library.
     * Delegates to {@link de.bund.zrb.service.NaturalAnalysisService}.
     */
    public void removeDependencyGraph(String library) {
        de.bund.zrb.service.NaturalAnalysisService.getInstance().removeGraph(library);
    }

    /**
     * Build (or rebuild) a dependency graph for a library by scanning all known sources.
     * Delegates to {@link de.bund.zrb.service.NaturalAnalysisService}.
     *
     * @param library     library name
     * @param sources     map of objectName → sourceCode for all objects in the library
     * @return the built graph
     */
    public de.bund.zrb.service.NaturalDependencyGraph buildDependencyGraph(
            String library, java.util.Map<String, String> sources) {
        return de.bund.zrb.service.NaturalAnalysisService.getInstance().buildGraph(library, sources);
    }

    /**
     * Show both active and passive XRefs in the LeftDrawer.
     * Active XRefs come from the direct analysis; passive XRefs come from the library graph (if available).
     */
    private void showFullDependenciesInLeftDrawer(LeftDrawer leftDrawer,
                                                   de.bund.zrb.service.NaturalDependencyService.DependencyResult result,
                                                   String library, String sourceName) {
        java.util.Map<String, java.util.List<LeftDrawer.RelationEntry>> sections =
                new java.util.LinkedHashMap<String, java.util.List<LeftDrawer.RelationEntry>>();
        int totalCount = 0;

        final de.bund.zrb.service.NaturalAnalysisService analysisService =
                de.bund.zrb.service.NaturalAnalysisService.getInstance();

        // ── Active XRefs (what this program calls) ──
        if (!result.isEmpty()) {
            for (java.util.Map.Entry<de.bund.zrb.service.NaturalDependencyService.DependencyKind,
                    java.util.List<de.bund.zrb.service.NaturalDependencyService.Dependency>> group
                    : result.getGrouped().entrySet()) {

                de.bund.zrb.service.NaturalDependencyService.DependencyKind kind = group.getKey();
                java.util.List<LeftDrawer.RelationEntry> entries = new java.util.ArrayList<LeftDrawer.RelationEntry>();

                for (de.bund.zrb.service.NaturalDependencyService.Dependency dep : group.getValue()) {
                    String targetPath = buildDependencyTargetPath(dep, library);
                    String depType = "DEPENDENCY_" + kind.getCode();
                    entries.add(new LeftDrawer.RelationEntry(
                            dep.getDisplayText(), targetPath, depType));
                }

                sections.put("➡ " + kind.getDisplayLabel(), entries);
                totalCount += entries.size();
            }
        }

        // ── Passive XRefs (who calls this program) from graph ──
        if (library != null) {
            String objName = analysisService.extractObjectName(sourceName);
            if (objName != null) {
                java.util.Map<de.bund.zrb.service.NaturalDependencyService.DependencyKind,
                        java.util.List<de.bund.zrb.service.NaturalDependencyGraph.CallerInfo>> callerGroups =
                        analysisService.getPassiveXRefsGrouped(library, objName);

                if (!callerGroups.isEmpty()) {
                    for (java.util.Map.Entry<de.bund.zrb.service.NaturalDependencyService.DependencyKind,
                            java.util.List<de.bund.zrb.service.NaturalDependencyGraph.CallerInfo>> cgroup
                            : callerGroups.entrySet()) {

                        de.bund.zrb.service.NaturalDependencyService.DependencyKind kind = cgroup.getKey();
                        java.util.List<LeftDrawer.RelationEntry> entries = new java.util.ArrayList<LeftDrawer.RelationEntry>();

                        for (de.bund.zrb.service.NaturalDependencyGraph.CallerInfo caller : cgroup.getValue()) {
                            String targetPath = (library != null && !library.isEmpty())
                                    ? "ndv://" + library + "/" + caller.getCallerName()
                                    : null;
                            entries.add(new LeftDrawer.RelationEntry(
                                    caller.getDisplayText(), targetPath, "CALLER_" + kind.getCode()));
                        }

                        sections.put("⬅ Aufgerufen von (" + kind.getCode() + ")", entries);
                        totalCount += entries.size();
                    }
                }
            }
        }

        if (totalCount == 0) {
            leftDrawer.showRelationsPlaceholder("Keine Abhängigkeiten gefunden.");
        } else {
            leftDrawer.updateRelationsGrouped("Abhängigkeiten", sections, totalCount);
        }
    }

    /**
     * Show JCL dependencies (PGM, PROC, INCLUDE, JCLLIB, DSN, Natural) in the LeftDrawer.
     * Natural program entries get a special type prefix (JCL_NAT_) for visual highlighting.
     */
    private void showJclDependenciesInLeftDrawer(LeftDrawer leftDrawer,
                                                  de.bund.zrb.service.JclDependencyService.JclDependencyResult result) {
        if (result.isEmpty()) {
            leftDrawer.showRelationsPlaceholder("Keine JCL-Abhängigkeiten gefunden.");
            return;
        }

        // Build lookup for known system functions (IDCAMS, IEFBR14, …)
        java.util.Map<String, de.bund.zrb.model.SystemFunctionEntry> sysFuncLookup =
                de.bund.zrb.helper.SystemFunctionSettingsHelper.buildLookup();

        java.util.Map<String, java.util.List<LeftDrawer.RelationEntry>> sections =
                new java.util.LinkedHashMap<String, java.util.List<LeftDrawer.RelationEntry>>();
        int totalCount = 0;

        for (java.util.Map.Entry<de.bund.zrb.service.JclDependencyService.JclDependencyKind,
                java.util.List<de.bund.zrb.service.JclDependencyService.JclDependency>> group
                : result.getGrouped().entrySet()) {

            de.bund.zrb.service.JclDependencyService.JclDependencyKind kind = group.getKey();
            java.util.List<LeftDrawer.RelationEntry> entries = new java.util.ArrayList<LeftDrawer.RelationEntry>();

            for (de.bund.zrb.service.JclDependencyService.JclDependency dep : group.getValue()) {
                String depType;
                String targetPath = null;
                if (kind == de.bund.zrb.service.JclDependencyService.JclDependencyKind.NATURAL_PROGRAM) {
                    // Natural program → special type for highlighting + navigation
                    depType = "JCL_NAT_" + dep.getNaturalLibrary();
                    // targetPath encodes library;program for double-click NDV navigation
                    targetPath = "nat-jcl://" + dep.getNaturalLibrary() + "/" + dep.getNaturalProgram();
                } else if (kind == de.bund.zrb.service.JclDependencyService.JclDependencyKind.PROGRAM
                        && sysFuncLookup.containsKey(dep.getTargetName().toUpperCase())) {
                    // Known system function → link to Wikipedia article
                    de.bund.zrb.model.SystemFunctionEntry sysFunc =
                            sysFuncLookup.get(dep.getTargetName().toUpperCase());
                    depType = "JCL_SYSFUNC";
                    // Encode wiki search term in targetPath: sysfunc://NAME
                    targetPath = "sysfunc://" + sysFunc.getName();
                } else {
                    depType = "JCL_DEP_" + kind.getCode();
                }
                entries.add(new LeftDrawer.RelationEntry(
                        dep.getDisplayText(), targetPath, depType, dep.getLineNumber()));
            }

            sections.put(kind.getDisplayLabel(), entries);
            totalCount += entries.size();
        }

        leftDrawer.updateRelationsGrouped("JCL-Abhängigkeiten", sections, totalCount);
    }

    /**
     * Show JCL call hierarchy (JOB → EXEC steps → DD) in the LeftDrawer call hierarchy panel.
     */
    private void showJclCallHierarchy(LeftDrawer leftDrawer,
                                       java.util.List<de.bund.zrb.service.JclDependencyService.JclCallNode> roots,
                                       String sourceName) {
        if (roots == null || roots.isEmpty()) {
            leftDrawer.showCallHierarchyPlaceholder("Keine JCL-Ausführungshierarchie gefunden.");
            return;
        }

        // Convert roots to CallHierarchyData and show as callees
        java.util.List<LeftDrawer.CallHierarchyData> children =
                new java.util.ArrayList<LeftDrawer.CallHierarchyData>();
        for (de.bund.zrb.service.JclDependencyService.JclCallNode root : roots) {
            children.add(convertJclCallNode(root));
        }

        LeftDrawer.CallHierarchyData calleesRoot = new LeftDrawer.CallHierarchyData(
                "JCL Ausführung", null, false, children);

        leftDrawer.updateCallHierarchy(calleesRoot, null, sourceName);
    }

    /**
     * Recursively convert a JclCallNode to LeftDrawer.CallHierarchyData.
     * Natural program nodes get a targetPath of "nat-jcl://LIB/PROG" for navigation.
     * Known system functions get a targetPath of "sysfunc://PGM" for Wikipedia linking.
     */
    private LeftDrawer.CallHierarchyData convertJclCallNode(
            de.bund.zrb.service.JclDependencyService.JclCallNode node) {

        java.util.List<LeftDrawer.CallHierarchyData> children =
                new java.util.ArrayList<LeftDrawer.CallHierarchyData>();
        for (de.bund.zrb.service.JclDependencyService.JclCallNode child : node.getChildren()) {
            children.add(convertJclCallNode(child));
        }

        String targetPath = null;
        String natRef = node.getNaturalRef();
        if (natRef != null && natRef.contains(";")) {
            String[] parts = natRef.split(";", 2);
            targetPath = "nat-jcl://" + parts[0] + "/" + parts[1];
        }

        // Check if this is a known system function (from display text: "▶ STEP → PGM=IDCAMS")
        if (targetPath == null) {
            String display = node.getDisplayText();
            if (display != null && display.contains("PGM=")) {
                int pgmIdx = display.indexOf("PGM=");
                String afterPgm = display.substring(pgmIdx + 4).trim();
                // Extract program name (up to space, bracket, or end)
                int end = afterPgm.length();
                for (int i = 0; i < afterPgm.length(); i++) {
                    char c = afterPgm.charAt(i);
                    if (c == ' ' || c == ',' || c == ')' || c == '[') {
                        end = i;
                        break;
                    }
                }
                String pgm = afterPgm.substring(0, end).toUpperCase();
                java.util.Map<String, de.bund.zrb.model.SystemFunctionEntry> lookup =
                        de.bund.zrb.helper.SystemFunctionSettingsHelper.buildLookup();
                if (lookup.containsKey(pgm)) {
                    targetPath = "sysfunc://" + pgm;
                }
            }
        }

        return new LeftDrawer.CallHierarchyData(
                node.getDisplayText(),
                targetPath,
                false, // not recursive
                children
        );
    }

    /**
     * Extract the object name from a path — delegates to NaturalAnalysisService.
     */
    private String extractObjectName(String path) {
        return de.bund.zrb.service.NaturalAnalysisService.getInstance().extractObjectName(path);
    }

    /**
     * Populate the Call Hierarchy (bottom split) from the library's dependency graph.
     * Shows both callees (what this calls, recursive) and callers (who calls this, recursive).
     */
    private void populateCallHierarchy(LeftDrawer leftDrawer, String library, String sourceName) {
        if (library == null) {
            leftDrawer.showCallHierarchyPlaceholder("Bibliothek unbekannt — kein Call-Graph.");
            return;
        }

        final de.bund.zrb.service.NaturalAnalysisService analysisService =
                de.bund.zrb.service.NaturalAnalysisService.getInstance();

        de.bund.zrb.service.NaturalDependencyGraph graph = analysisService.getGraph(library);

        if (graph == null || !graph.isBuilt()) {
            leftDrawer.showCallHierarchyPlaceholder(
                    "Graph wird beim Öffnen der Bibliothek erstellt.\n" +
                    "Öffnen Sie den NDV-Browser und navigieren Sie zur Bibliothek.");
            return;
        }

        String objName = analysisService.extractObjectName(sourceName);
        if (objName == null) {
            leftDrawer.clearCallHierarchy();
            return;
        }

        // Build callee hierarchy (what this calls, max depth 5)
        de.bund.zrb.service.NaturalDependencyGraph.CallHierarchyNode calleesNode =
                analysisService.getCallHierarchy(library, objName, true, 5);
        LeftDrawer.CallHierarchyData calleesData = convertHierarchyNode(calleesNode, library);

        // Build caller hierarchy (who calls this, max depth 5)
        de.bund.zrb.service.NaturalDependencyGraph.CallHierarchyNode callersNode =
                analysisService.getCallHierarchy(library, objName, false, 5);
        LeftDrawer.CallHierarchyData callersData = convertHierarchyNode(callersNode, library);

        leftDrawer.updateCallHierarchy(calleesData, callersData, objName);
    }

    /**
     * Convert a NaturalDependencyGraph.CallHierarchyNode to a LeftDrawer.CallHierarchyData (UI model).
     */
    private LeftDrawer.CallHierarchyData convertHierarchyNode(
            de.bund.zrb.service.NaturalDependencyGraph.CallHierarchyNode node, String library) {

        java.util.List<LeftDrawer.CallHierarchyData> children =
                new java.util.ArrayList<LeftDrawer.CallHierarchyData>();
        for (de.bund.zrb.service.NaturalDependencyGraph.CallHierarchyNode child : node.getChildren()) {
            children.add(convertHierarchyNode(child, library));
        }

        String targetPath = (library != null && !library.isEmpty())
                ? "ndv://" + library + "/" + node.getObjectName()
                : null;

        return new LeftDrawer.CallHierarchyData(
                node.getDisplayText(),
                targetPath,
                node.isRecursive(),
                children
        );
    }

    /**
     * Determine if the source content is Natural — delegates to NaturalAnalysisService.
     */
    private boolean isNaturalSource(String content, String sentenceType) {
        return de.bund.zrb.service.NaturalAnalysisService.getInstance().isNaturalSource(content, sentenceType);
    }

    /**
     * Extract the library name from a tab's path (for NDV paths like "LIBNAME/OBJNAME.ext").
     */
    private String extractLibrary(de.zrb.bund.newApi.ui.FtpTab tab) {
        String path = tab.getPath();
        if (path == null) return null;
        // NDV paths: "LIBNAME/OBJNAME.NSP"
        int slash = path.indexOf('/');
        if (slash > 0 && slash < path.length() - 1) {
            return path.substring(0, slash);
        }
        return null;
    }

    /**
     * Convert dependency analysis result into LeftDrawer grouped relations.
     */
    private void showDependenciesInLeftDrawer(LeftDrawer leftDrawer,
                                              de.bund.zrb.service.NaturalDependencyService.DependencyResult result,
                                              String library) {
        if (result.isEmpty()) {
            leftDrawer.showRelationsPlaceholder("Keine Abhängigkeiten gefunden.");
            return;
        }

        java.util.Map<String, java.util.List<LeftDrawer.RelationEntry>> sections =
                new java.util.LinkedHashMap<String, java.util.List<LeftDrawer.RelationEntry>>();

        for (java.util.Map.Entry<de.bund.zrb.service.NaturalDependencyService.DependencyKind,
                java.util.List<de.bund.zrb.service.NaturalDependencyService.Dependency>> group
                : result.getGrouped().entrySet()) {

            de.bund.zrb.service.NaturalDependencyService.DependencyKind kind = group.getKey();
            java.util.List<LeftDrawer.RelationEntry> entries = new java.util.ArrayList<LeftDrawer.RelationEntry>();

            for (de.bund.zrb.service.NaturalDependencyService.Dependency dep : group.getValue()) {
                // Build target path for navigation (ndv://LIBRARY/OBJECT if library known)
                String targetPath = buildDependencyTargetPath(dep, library);
                String depType = "DEPENDENCY_" + kind.getCode();
                entries.add(new LeftDrawer.RelationEntry(
                        dep.getDisplayText(), targetPath, depType));
            }

            sections.put(kind.getDisplayLabel(), entries);
        }

        leftDrawer.updateRelationsGrouped("Abhängigkeiten", sections, result.getTotalCount());
    }

    /**
     * Build a navigable target path for a dependency.
     * For CALLNAT/FETCH/INCLUDE/USING: ndv://LIBRARY/TARGETNAME  (if library is known)
     * For DB_ACCESS/VIEW: no navigation target (null)
     */
    private String buildDependencyTargetPath(
            de.bund.zrb.service.NaturalDependencyService.Dependency dep, String library) {
        switch (dep.getKind()) {
            case CALLNAT:
            case FETCH:
            case CALL:
            case PERFORM:
            case INCLUDE:
            case USING:
            case INPUT_MAP:
                if (library != null && !library.isEmpty()) {
                    return "ndv://" + library + "/" + dep.getTargetName();
                }
                return null;
            default:
                return null;
        }
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

    /**
     * Search for a term in an already open WikiConnectionTab.
     * Switches to the tab and triggers the search.
     *
     * @param siteId     preferred site (e.g. "wikipedia_de") — used to select the wiki dropdown if possible
     * @param searchTerm the term to search for
     * @return true if a WikiConnectionTab was found and the search was started
     */
    public boolean searchInWikiConnectionTab(String siteId, String searchTerm) {
        for (java.util.Map.Entry<Component, de.zrb.bund.newApi.ui.FtpTab> entry : tabMap.entrySet()) {
            if (entry.getValue() instanceof de.bund.zrb.wiki.ui.WikiConnectionTab) {
                de.bund.zrb.wiki.ui.WikiConnectionTab wikiConn =
                        (de.bund.zrb.wiki.ui.WikiConnectionTab) entry.getValue();
                // Switch to the wiki tab
                int idx = tabbedPane.indexOfComponent(entry.getKey());
                if (idx >= 0) {
                    tabbedPane.setSelectedIndex(idx);
                }
                // Select the matching wiki site if possible
                if (siteId != null && !siteId.isEmpty()) {
                    wikiConn.selectSiteById(siteId);
                }
                // Trigger search
                wikiConn.searchFor(searchTerm);
                return true;
            }
        }
        return false;
    }

    /**
     * Open an NDV dependency target by finding an open NdvConnectionTab
     * and navigating to the specified library/object.
     *
     * @param library    target library name
     * @param objectName target object name (nullable, if null just navigate to library)
     */
    public void openNdvDependencyTarget(String library, String objectName) {
        // Find an open NdvConnectionTab
        for (java.util.Map.Entry<Component, de.zrb.bund.newApi.ui.FtpTab> entry : tabMap.entrySet()) {
            if (entry.getValue() instanceof NdvConnectionTab) {
                NdvConnectionTab ndvTab = (NdvConnectionTab) entry.getValue();
                // Switch to this tab
                int idx = tabbedPane.indexOfComponent(entry.getKey());
                if (idx >= 0) {
                    tabbedPane.setSelectedIndex(idx);
                }
                // Navigate to the library and optionally open the object
                if (objectName != null && !objectName.isEmpty()) {
                    ndvTab.navigateToLibraryAndOpen(library, objectName);
                } else {
                    ndvTab.navigateToLibrary(library);
                }
                return;
            }
        }
        // No NdvConnectionTab found
        javax.swing.JOptionPane.showMessageDialog(tabbedPane,
                "Bitte öffnen Sie zuerst eine NDV-Verbindung unter Verbindung → NDV.",
                "Keine NDV-Verbindung", javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }
}
