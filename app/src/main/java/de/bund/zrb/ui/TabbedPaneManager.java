package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.ftp.FtpManager;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.api.Bookmarkable;
import de.zrb.bund.newApi.ui.FileTab;
import de.zrb.bund.newApi.ui.FtpTab;

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
        return tabbedPane;
    }

    public Component getSelectedComponent() {
        return tabbedPane.getSelectedComponent();
    }

    /**
     * Öffnet einen neuen FileTab, der eine neue Datei anlegt. Der Inhalt wird bis zum Speichern nur im Editor gehalten.
     */
    public FileTab openFileTab(FtpManager ftpManager, String content, String sentenceType) {
        FileTabImpl fileTabImpl = new FileTabImpl(this, ftpManager, content, sentenceType);
        addTab(fileTabImpl); // handled everything
        return fileTabImpl;
    }

    /**
     * Öffnet einen neuen FileTab, der eine bestehende Datei anzeigt.
     *
     * @param ftpManager der FtpManager, der die Verbindung verwaltet
     * @param buffer     der FtpFileBuffer, der die Datei repräsentiert
     */
    public FtpTab openFileTab(FtpManager ftpManager, FtpFileBuffer buffer, String sentenceType, boolean toCompare) {
        if( !toCompare) {
            FileTabImpl fileTabImpl = new FileTabImpl(this, ftpManager, buffer, sentenceType);
            addTab(fileTabImpl); // handled everything
            updateTooltipFor(fileTabImpl);
            return fileTabImpl;
        } else {
            ComparableFileTabImpl fileTabImpl = new ComparableFileTabImpl(this, ftpManager, buffer, sentenceType);
            addTab(fileTabImpl); // handled everything
            updateTooltipFor(fileTabImpl);
            return fileTabImpl;
        }

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
}
