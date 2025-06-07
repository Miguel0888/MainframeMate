package de.bund.zrb.ui.components;

import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.MainframeContext;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Displays the full chat tab with session tabs and controls.
 */
public class Chat extends JPanel {

    private final MainframeContext mainframeContext;
    private final ChatManager chatManager;
    private final JTabbedPane chatTabs = new JTabbedPane();

    private JCheckBox keepAliveCheckbox;
    private JCheckBox contextMemoryCheckbox;

    public Chat(MainframeContext mainframeContext, ChatManager chatManager) {
        this.mainframeContext = mainframeContext;
        this.chatManager = chatManager;

        setLayout(new BorderLayout(8, 8));
        add(createHeader(), BorderLayout.NORTH);
        add(chatTabs, BorderLayout.CENTER);

        addPlusTab();          // 1. "+"-Tab hinzufügen
        addNewChatSession();   // 2. Session davor einfügen

        chatTabs.addChangeListener(e -> {
            int plusTabIndex = chatTabs.getTabCount() - 1;
            int selectedIndex = chatTabs.getSelectedIndex();

            // Wenn "+"-Tab ausgewählt wurde und mindestens ein anderer Tab offen ist
            if (selectedIndex == plusTabIndex && plusTabIndex > 0) {
                // Wähle den vorherigen Tab (index -1)
                int newIndex = plusTabIndex - 1;
                chatTabs.setSelectedIndex(newIndex);
            }
        });
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());

        // Rechtsbündige Checkbox-Gruppe
        keepAliveCheckbox = new JCheckBox("Modell behalten", true);
        contextMemoryCheckbox = new JCheckBox("Kontext merken", true);
        Font smallFont = new Font("Dialog", Font.PLAIN, 11);
        keepAliveCheckbox.setFont(smallFont);
        contextMemoryCheckbox.setFont(smallFont);

        JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        checkboxPanel.add(contextMemoryCheckbox);
        checkboxPanel.add(keepAliveCheckbox);

        header.add(checkboxPanel, BorderLayout.EAST);
        header.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        return header;
    }

    private void addNewChatSession() {
        ChatSession sessionPanel = new ChatSession(mainframeContext, chatManager, keepAliveCheckbox, contextMemoryCheckbox);
        String shortId = sessionPanel.getSessionId().toString().substring(0, 6);

        int insertIndex = Math.max(chatTabs.getTabCount() - 1, 0);
        chatTabs.insertTab(null, null, sessionPanel, null, insertIndex);
        chatTabs.setTabComponentAt(insertIndex, createTabTitle("💬 " + shortId, sessionPanel));
        chatTabs.setSelectedComponent(sessionPanel);
    }

    private void addPlusTab() {
        JPanel tabPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabPanel.setOpaque(false);

        JButton openButton = new JButton("＋");
        openButton.setMargin(new Insets(0, 0, 0, 0));
        openButton.setBorder(BorderFactory.createEmptyBorder());
        openButton.setFocusable(false);
        openButton.setContentAreaFilled(true);
        openButton.setToolTipText("Neuen Tab öffnen..");

        openButton.addActionListener(e -> {
            addNewChatSession();
        });

        tabPanel.add(openButton);

        int insertIndex = Math.max(chatTabs.getTabCount() - 1, 0);
        chatTabs.insertTab(null, null, null, null, insertIndex);
        chatTabs.setTabComponentAt(insertIndex, tabPanel);

        chatTabs.setEnabledAt(chatTabs.getTabCount() - 1, false);
    }

    private Component createTabTitle(String title, Component tabContent) {
        JPanel tabPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabPanel.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
        tabPanel.add(titleLabel);

        JButton closeButton = new JButton("×");
        closeButton.setMargin(new Insets(0, 5, 0, 5));
        closeButton.setBorder(BorderFactory.createEmptyBorder());
        closeButton.setFocusable(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setToolTipText("Tab schließen");

        closeButton.addActionListener(e -> {
            int index = chatTabs.indexOfComponent(tabContent);
            if (index >= 0 && index != chatTabs.getTabCount() - 1) {
                chatTabs.remove(index);
            }
        });

        tabPanel.add(closeButton);
        return tabPanel;
    }
}
