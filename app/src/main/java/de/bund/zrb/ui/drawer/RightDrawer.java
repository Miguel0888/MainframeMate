package de.bund.zrb.ui.drawer;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.components.Chat;
import de.bund.zrb.ui.components.Workflow;
import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.MainframeContext;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class RightDrawer extends JPanel {

    private final ChatManager chatManager;
    private final JTabbedPane tabbedPane;
    private final MainframeContext mainframeContext;

    private JCheckBox keepAliveCheckbox;
    private JCheckBox contextMemoryCheckbox;

    public RightDrawer(MainframeContext mainFrame, ChatManager chatManager) {
        this.mainframeContext = mainFrame;
        this.chatManager = chatManager;

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        tabbedPane = new JTabbedPane();
        add(tabbedPane, BorderLayout.CENTER);

        addWorkflowTab();
        addChatTab();
    }

    private void addWorkflowTab() {
        Workflow workflowPanel = new Workflow(mainframeContext);
        tabbedPane.addTab("📋 Workflow", workflowPanel);
    }

    private void addChatTab() {
        Chat chatPanel = new Chat(mainframeContext, chatManager);
        tabbedPane.addTab("💬 Chat", chatPanel);
    }

    public void addApplicationState(Map<String, String> state) {
        if (state == null) return;
        if (keepAliveCheckbox != null && contextMemoryCheckbox != null) {
            state.put("chat.keepAlive", String.valueOf(keepAliveCheckbox.isSelected()));
            state.put("chat.rememberContext", String.valueOf(contextMemoryCheckbox.isSelected()));
        }
    }
}
