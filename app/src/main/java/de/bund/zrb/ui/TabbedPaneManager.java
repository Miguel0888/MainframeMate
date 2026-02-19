package de.bund.zrb.ui;

import de.bund.zrb.ui.components.HelpButton;
import de.bund.zrb.ui.components.TabbedPaneWithHelpOverlay;
import de.bund.zrb.ui.drawer.RightDrawer;
import de.bund.zrb.ui.help.HelpContentProvider;
import de.bund.zrb.ui.preview.SplitPreviewTab;
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

        // Tab change listener for JCL outline updates
        tabbedPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                updateJclOutlineForSelectedTab();
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

    private void addClosableTabComponent(int index, FtpTab tab) {
        JPanel tabPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabPanel.setOpaque(false);

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

    public void closeTab(int index) {
        Component comp = tabbedPane.getComponentAt(index);
        FtpTab tab = tabMap.remove(comp);
        if (tab != null) tab.onClose();
        tabbedPane.remove(index);
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
            return;
        }

        // Get content and source name
        String content = null;
        String sourceName = null;

        if (tab instanceof FileTabImpl) {
            FileTabImpl fileTab = (FileTabImpl) tab;
            content = fileTab.getContent();
            sourceName = fileTab.getPath();
        } else if (tab instanceof SplitPreviewTab) {
            SplitPreviewTab previewTab = (SplitPreviewTab) tab;
            content = previewTab.getContent();
            sourceName = previewTab.getPath();
        }

        if (content == null || content.isEmpty()) {
            rightDrawer.clearJclOutline();
            return;
        }

        // Check if content looks like JCL or COBOL
        if (isJclContent(content) || isCobolContent(content)) {
            rightDrawer.updateJclOutline(content, sourceName);

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
}
