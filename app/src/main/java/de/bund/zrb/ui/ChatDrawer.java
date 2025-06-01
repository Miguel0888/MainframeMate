package de.bund.zrb.ui;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.components.ChatSession;
import de.zrb.bund.api.ChatManager;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.UUID;

public class ChatDrawer extends JPanel {

    private final ChatManager chatManager;
    private final JTabbedPane tabbedPane;

    private JCheckBox keepAliveCheckbox;
    private JCheckBox contextMemoryCheckbox;

    public ChatDrawer(ChatManager chatManager) {
        this.chatManager = chatManager;

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(createHeader(), BorderLayout.NORTH);

        tabbedPane = new JTabbedPane();
        add(tabbedPane, BorderLayout.CENTER);

        addNewChatSession();
    }

    private JPanel createHeader() {
        JLabel titleLabel = new JLabel("💬 Chat");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));

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
        headerLine.add(titleLabel, BorderLayout.WEST);
        headerLine.add(checkboxPanel, BorderLayout.EAST);
        headerLine.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        return headerLine;
    }

    private void addNewChatSession() {
        ChatSession sessionPanel = new ChatSession(chatManager, keepAliveCheckbox, contextMemoryCheckbox);
        String shortId = sessionPanel.getSessionId().toString().substring(0, 6);

        tabbedPane.addTab(null, sessionPanel);
        int index = tabbedPane.indexOfComponent(sessionPanel);
        tabbedPane.setTabComponentAt(index, createTabTitle("💬 " + shortId, sessionPanel));
        tabbedPane.setSelectedComponent(sessionPanel);
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
            int index = tabbedPane.indexOfComponent(tabContent);
            if (index >= 0) {
                tabbedPane.remove(index);
            }
        });

        tabPanel.add(closeButton);
        return tabPanel;
    }

    public void addApplicationState(Map<String, String> state) {
        if (state == null) return;
        state.put("chat.keepAlive", String.valueOf(keepAliveCheckbox.isSelected()));
        state.put("chat.rememberContext", String.valueOf(contextMemoryCheckbox.isSelected()));
    }
}
