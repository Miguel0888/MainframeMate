package org.example.ui;

import org.example.ftp.FtpFileBuffer;
import org.example.ftp.FtpManager;
import org.example.ui.FtpTab;

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

    public TabbedPaneManager() {
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

    public void openFileTab(FtpManager ftpManager, FtpFileBuffer buffer) {
        FileTab fileTab = new FileTab(ftpManager, this, buffer);
        addTab(fileTab); // handled everything
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Plugin-Management
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public Optional<FtpTab> getSelectedTab() {
        Component selected = tabbedPane.getSelectedComponent();
        return Optional.ofNullable(tabMap.get(selected));
    }

    public Optional<FileTab> getSelectedFileTab() {
        Component selected = tabbedPane.getSelectedComponent();
        FtpTab tab = tabMap.get(selected);
        if (tab instanceof FileTab) {
            return Optional.of((FileTab) tab);
        }
        return Optional.empty();
    }


}
