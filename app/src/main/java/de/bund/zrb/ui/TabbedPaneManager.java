package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.ftp.FtpManager;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.api.TabAdapter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TabbedPaneManager {

    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final Map<Component, FtpTab> tabMap = new HashMap<>();
    private final MainframeContext mainframeContext;

    public TabbedPaneManager(MainframeContext mainFrame) {
        this.mainframeContext = mainFrame;
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
        }

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

    public JComponent getComponent() {
        return tabbedPane;
    }

    public Component getSelectedComponent() {
        return tabbedPane.getSelectedComponent();
    }

    /**
     * Öffnet einen neuen FileTab, der eine neue Datei anlegt. Der Inhalt wird bis zum Speichern nur im Editor gehalten.
     */
    public void openFileTab(String content, String sentenceType) {
        FileTab fileTab = new FileTab(this, content, sentenceType);
        addTab(fileTab); // handled everything
    }

    /**
     * Öffnet einen neuen FileTab, der eine bestehende Datei anzeigt.
     *
     * @param ftpManager der FtpManager, der die Verbindung verwaltet
     * @param buffer     der FtpFileBuffer, der die Datei repräsentiert
     */
    public void openFileTab(FtpManager ftpManager, FtpFileBuffer buffer) {
        FileTab fileTab = new FileTab(this, ftpManager, buffer);
        addTab(fileTab); // handled everything
        updateTooltipFor(fileTab);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Plugin-Management
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public Optional<FtpTab> getSelectedTab() {
        Component selected = tabbedPane.getSelectedComponent();
        return Optional.ofNullable(tabMap.get(selected));
    }

    public Optional<TabAdapter> getSelectedFileTab() {
        Component selected = tabbedPane.getSelectedComponent();
        FtpTab tab = tabMap.get(selected);
        if (tab instanceof FileTab) {
            return Optional.of((FileTab) tab);
        }
        return Optional.empty();
    }


    public java.util.List<TabAdapter> getAllTabs() {
        java.util.List<TabAdapter> result = new java.util.ArrayList<>();
        for (FtpTab tab : tabMap.values()) {
            if (tab instanceof TabAdapter) {
                result.add((TabAdapter) tab);
            }
        }
        return result;
    }

    public void focusTabByAdapter(TabAdapter tab) {
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
}
