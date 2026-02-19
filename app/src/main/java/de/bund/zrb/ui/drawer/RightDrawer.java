package de.bund.zrb.ui.drawer;

import de.bund.zrb.ui.components.Chat;
import de.bund.zrb.ui.components.HelpButton;
import de.bund.zrb.ui.components.TabbedPaneWithHelpOverlay;
import de.bund.zrb.ui.components.WorkflowPanel;
import de.bund.zrb.ui.help.HelpContentProvider;
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
    private final TabbedPaneWithHelpOverlay tabbedPane;
    private final MainframeContext mainframeContext;
    private final ToolRegistry toolRegistry;
    private final McpService mcpService;
    private final de.bund.zrb.service.McpChatEventBridge chatEventBridge;

    private JCheckBox keepAliveCheckbox;
    private JCheckBox contextMemoryCheckbox;

    public RightDrawer(MainframeContext mainframeContext, ChatManager chatManager,
                       ToolRegistry toolRegistry, McpService mcpService,
                       de.bund.zrb.service.McpChatEventBridge chatEventBridge) {

        this.mainframeContext = mainframeContext;
        this.chatManager = chatManager;
        this.toolRegistry = toolRegistry;
        this.mcpService = mcpService;
        this.chatEventBridge = chatEventBridge;

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        tabbedPane = new TabbedPaneWithHelpOverlay();

        // Hilfe-Button als Overlay über der Tab-Leiste
        HelpButton helpButton = new HelpButton("Hilfe zur Seitenleiste",
                e -> HelpContentProvider.showHelpPopup(
                        (Component) e.getSource(),
                        HelpContentProvider.HelpTopic.RIGHT_DRAWER));
        tabbedPane.setHelpComponent(helpButton);

        add(tabbedPane, BorderLayout.CENTER);

        addWorkflowTab();
        addChatTab();
    }

    // Keep old constructor for binary/source compatibility
    public RightDrawer(MainframeContext mainframeContext, ChatManager chatManager,
                       ToolRegistry toolRegistry, McpService mcpService) {
        this(mainframeContext, chatManager, toolRegistry, mcpService, null);
    }

    private void addWorkflowTab() {
        WorkflowRunner runner = mainframeContext.getWorkflowRunner();
        WorkflowPanel workflowPanel = new WorkflowPanel(runner, mainframeContext);
        tabbedPane.addTab("📋 Workflow", workflowPanel);
    }

    private void addChatTab() {
        // Bridge is optional; if not provided, tool events simply won't be displayed.
        Chat chatPanel = new Chat(mainframeContext, chatManager, chatEventBridge);
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
