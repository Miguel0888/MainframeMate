package de.bund.zrb.ui.drawer;

import de.bund.zrb.ui.components.Chat;
import de.bund.zrb.ui.components.WorkflowPanel;
import de.bund.zrb.workflow.WorkflowRunnerImpl;
import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.McpService;
import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.workflow.WorkflowRunner;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class RightDrawer extends JPanel {

    private final ChatManager chatManager;
    private final JTabbedPane tabbedPane;
    private final MainframeContext mainframeContext;
    private final ToolRegistry toolRegistry;
    private final McpService mcpService;

    private JCheckBox keepAliveCheckbox;
    private JCheckBox contextMemoryCheckbox;

    public RightDrawer(MainframeContext mainframeContext, ChatManager chatManager,
                       ToolRegistry toolRegistry, McpService mcpService) {

        this.mainframeContext = mainframeContext;
        this.chatManager = chatManager;
        this.toolRegistry = toolRegistry;
        this.mcpService = mcpService;

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        tabbedPane = new JTabbedPane();
        add(tabbedPane, BorderLayout.CENTER);

        addWorkflowTab();
        addChatTab();
    }

    private void addWorkflowTab() {
        WorkflowRunner runner = mainframeContext.getWorkflowRunner();
        WorkflowPanel workflowPanel = new WorkflowPanel(runner, toolRegistry);
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
