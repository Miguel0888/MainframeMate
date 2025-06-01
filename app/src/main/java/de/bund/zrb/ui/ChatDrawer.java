package de.bund.zrb.ui;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.runtime.ToolRegistryImpl;
import de.bund.zrb.ui.components.ChatSessionPanel;
import de.bund.zrb.ui.components.UiMessage;
import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.ChatStreamListener;
import de.zrb.bund.newApi.mcp.McpTool;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class ChatDrawer extends JPanel {

    private final JTabbedPane tabbedPane;
    private final Map<UUID, ChatSessionPanel> sessions = new HashMap<>();
    private final ChatManager chatManager;

    private final JCheckBox keepAliveCheckbox;
    private final JCheckBox contextMemoryCheckbox;

    public ChatDrawer(ChatManager chatManager) {
        this.chatManager = chatManager;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel titleLabel = new JLabel("💬 Chat");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));

        Settings settings = SettingsHelper.load();
        Map<String, String> state = settings.applicationState;

        keepAliveCheckbox = new JCheckBox("Modell behalten", Boolean.parseBoolean(state.getOrDefault("chat.keepAlive", "true")));
        contextMemoryCheckbox = new JCheckBox("Kontext merken", Boolean.parseBoolean(state.getOrDefault("chat.rememberContext", "true")));

        for (JCheckBox box : Arrays.asList(keepAliveCheckbox, contextMemoryCheckbox)) {
            box.setFont(new Font("Dialog", Font.PLAIN, 11));
            box.setHorizontalTextPosition(SwingConstants.LEFT);
            box.setMargin(new Insets(0, 0, 0, 0));
            box.setFocusable(false);
        }

        JButton newTabButton = new JButton("+");
        newTabButton.setFocusable(false);
        newTabButton.setMargin(new Insets(0, 8, 0, 8));
        newTabButton.addActionListener(e -> openNewSession());

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.add(titleLabel, BorderLayout.WEST);

        JPanel optionsPanel = new JPanel();
        optionsPanel.setOpaque(false);
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.X_AXIS));
        optionsPanel.add(contextMemoryCheckbox);
        optionsPanel.add(Box.createHorizontalStrut(8));
        optionsPanel.add(keepAliveCheckbox);
        optionsPanel.add(Box.createHorizontalStrut(8));
        optionsPanel.add(newTabButton);

        titlePanel.add(optionsPanel, BorderLayout.EAST);
        titlePanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        add(titlePanel, BorderLayout.NORTH);

        tabbedPane = new JTabbedPane();
        add(tabbedPane, BorderLayout.CENTER);

        openNewSession(); // Starte mit einem Tab
    }

    private void openNewSession() {
        ChatSessionPanel panel = new ChatSessionPanel(chatManager, this);
        UUID sessionId = panel.getSessionId();
        String shortId = sessionId.toString().substring(0, 8);
        sessions.put(sessionId, panel);

        tabbedPane.addTab(shortId, panel);
        int index = tabbedPane.indexOfComponent(panel);
        tabbedPane.setTabComponentAt(index, createClosableTab(shortId, panel));
        tabbedPane.setSelectedComponent(panel);
    }

    private JPanel createClosableTab(String title, ChatSessionPanel panel) {
        JPanel tabPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabPanel.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        JButton closeButton = new JButton("×");
        closeButton.setMargin(new Insets(0, 5, 0, 5));
        closeButton.setBorder(BorderFactory.createEmptyBorder());
        closeButton.setFocusable(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setToolTipText("Tab schließen");
        closeButton.addActionListener(e -> closeTab(panel.getSessionId()));

        tabPanel.add(titleLabel);
        tabPanel.add(closeButton);
        return tabPanel;
    }

    private void closeTab(UUID sessionId) {
        ChatSessionPanel panel = sessions.remove(sessionId);
        if (panel != null) {
            chatManager.closeSession(sessionId);
            tabbedPane.remove(panel);
        }
    }

    public boolean isKeepAliveEnabled() {
        return keepAliveCheckbox.isSelected();
    }

    public boolean isContextMemoryEnabled() {
        return contextMemoryCheckbox.isSelected();
    }

    public void addApplicationState(Map<String, String> state) {
        if (state == null) return;

        state.put("chat.keepAlive", String.valueOf(keepAliveCheckbox.isSelected()));
        state.put("chat.rememberContext", String.valueOf(contextMemoryCheckbox.isSelected()));
    }
}
