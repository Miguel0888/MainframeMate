package de.bund.zrb.ui.components;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.MainframeContext;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * Displays the full chat tab with header and session area.
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

        addNewChatSession();
    }

    private JPanel createHeader() {
        JButton newTabButton = new JButton("+");
        newTabButton.setFocusable(false);
        newTabButton.setMargin(new Insets(0, 5, 0, 5));
        newTabButton.setToolTipText("Neue Session starten");
        newTabButton.addActionListener(e -> addNewChatSession());

        Settings settings = SettingsHelper.load();
        Map<String, String> state = settings.applicationState;

        keepAliveCheckbox = new JCheckBox("Modell behalten", Boolean.parseBoolean(state.getOrDefault("chat.keepAlive", "true")));
        contextMemoryCheckbox = new JCheckBox("Kontext merken", Boolean.parseBoolean(state.getOrDefault("chat.rememberContext", "true")));

        for (JCheckBox box : new JCheckBox[]{keepAliveCheckbox, contextMemoryCheckbox}) {
            box.setFont(new Font("Dialog", Font.PLAIN, 11));
            box.setHorizontalTextPosition(SwingConstants.LEFT);
            box.setMargin(new Insets(0, 0, 0, 0));
            box.setFocusable(false);
        }

        JPanel checkboxPanel = new JPanel();
        checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.X_AXIS));
        checkboxPanel.setOpaque(false);
        checkboxPanel.add(newTabButton);
        checkboxPanel.add(Box.createHorizontalStrut(12));
        checkboxPanel.add(contextMemoryCheckbox);
        checkboxPanel.add(Box.createHorizontalStrut(8));
        checkboxPanel.add(keepAliveCheckbox);

        JPanel headerLine = new JPanel(new BorderLayout());
        headerLine.add(checkboxPanel, BorderLayout.EAST);
        headerLine.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        return headerLine;
    }

    private void addNewChatSession() {
        ChatSession sessionPanel = new ChatSession(mainframeContext, chatManager, keepAliveCheckbox, contextMemoryCheckbox);
        String shortId = sessionPanel.getSessionId().toString().substring(0, 6);

        chatTabs.addTab(null, sessionPanel);
        int index = chatTabs.indexOfComponent(sessionPanel);
        chatTabs.setTabComponentAt(index, createTabTitle("💬 " + shortId, sessionPanel));
        chatTabs.setSelectedComponent(sessionPanel);
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
            if (index >= 0) {
                chatTabs.remove(index);
            }
        });

        tabPanel.add(closeButton);
        return tabPanel;
    }
}
