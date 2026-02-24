package de.bund.zrb.ui.components;

import de.bund.zrb.runtime.ToolRegistryImpl;
import de.bund.zrb.tools.ToolPolicy;
import de.bund.zrb.tools.ToolPolicyRepository;
import de.bund.zrb.ui.help.HelpContentProvider;
import de.bund.zrb.ui.settings.McpRegistryBrowserDialog;
import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.mcp.McpTool;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Displays the full chat tab with session tabs and controls.
 */
public class Chat extends JPanel {

    private final MainframeContext mainframeContext;
    private final ChatManager chatManager;
    private final de.bund.zrb.service.McpChatEventBridge chatEventBridge;
    private final TabbedPaneWithHelpOverlay chatTabs = new TabbedPaneWithHelpOverlay();

    private JCheckBox keepAliveCheckbox;
    private JCheckBox contextMemoryCheckbox;
    private JButton toolsMenuButton;

    public Chat(MainframeContext mainframeContext, ChatManager chatManager) {
        this(mainframeContext, chatManager, null);
    }

    public Chat(MainframeContext mainframeContext, ChatManager chatManager, de.bund.zrb.service.McpChatEventBridge chatEventBridge) {
        this.mainframeContext = mainframeContext;
        this.chatManager = chatManager;
        this.chatEventBridge = chatEventBridge;

        setLayout(new BorderLayout(8, 8));
        add(createHeader(), BorderLayout.NORTH);

        // Hilfe-Button als Overlay über der Tab-Leiste
        HelpButton helpButton = new HelpButton("Hilfe zum Chat",
                e -> HelpContentProvider.showHelpPopup(
                        (Component) e.getSource(),
                        HelpContentProvider.HelpTopic.CHAT));
        chatTabs.setHelpComponent(helpButton);

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

        // Copy chat as Markdown button
        JButton copyMdButton = new JButton("📋");
        copyMdButton.setFont(smallFont);
        copyMdButton.setFocusable(false);
        copyMdButton.setToolTipText("Chat als Markdown kopieren");
        copyMdButton.setMargin(new Insets(1, 4, 1, 4));
        copyMdButton.addActionListener(e -> copyActiveChatAsMarkdown(copyMdButton));

        // MCP Server menu button
        toolsMenuButton = new JButton("\uD83D\uDD27 Tools");
        toolsMenuButton.setFont(smallFont);
        toolsMenuButton.setFocusable(false);
        toolsMenuButton.setToolTipText("Tools verwalten");
        toolsMenuButton.addActionListener(e -> showToolsMenu());

        JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        checkboxPanel.add(copyMdButton);
        checkboxPanel.add(contextMemoryCheckbox);
        checkboxPanel.add(keepAliveCheckbox);
        checkboxPanel.add(toolsMenuButton);

        header.add(checkboxPanel, BorderLayout.EAST);
        header.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        return header;
    }

    private void copyActiveChatAsMarkdown(JButton source) {
        Component selected = chatTabs.getSelectedComponent();
        if (selected instanceof ChatSession) {
            String markdown = ((ChatSession) selected).exportAsMarkdown();
            java.awt.datatransfer.StringSelection sel =
                    new java.awt.datatransfer.StringSelection(markdown);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);

            // Visual feedback
            String orig = source.getText();
            source.setText("✅");
            Timer timer = new Timer(1500, ev -> source.setText(orig));
            timer.setRepeats(false);
            timer.start();
        }
    }


    private void showToolsMenu() {
        JPopupMenu popup = new JPopupMenu();

        ToolPolicyRepository policyRepo = new ToolPolicyRepository();
        List<ToolPolicy> policies = policyRepo.loadAll();
        Map<String, ToolPolicy> policyMap = new HashMap<>();
        for (ToolPolicy p : policies) {
            policyMap.put(p.getToolName(), p);
        }

        List<McpTool> allTools = new java.util.ArrayList<>(
                ToolRegistryImpl.getInstance().getAllTools());

        if (allTools.isEmpty()) {
            JMenuItem empty = new JMenuItem("(keine Tools registriert)");
            empty.setEnabled(false);
            popup.add(empty);
        } else {
            for (final McpTool tool : allTools) {
                String name = tool.getSpec().getName();
                ToolPolicy policy = policyMap.get(name);
                boolean enabled = policy == null || policy.isEnabled();

                JCheckBoxMenuItem item = new JCheckBoxMenuItem(name, enabled);
                item.setToolTipText(tool.getSpec().getDescription());
                item.addActionListener(e -> {
                    ToolPolicy p = policyMap.get(name);
                    if (p == null) {
                        p = new ToolPolicy(name, !enabled, false,
                                de.bund.zrb.tools.ToolAccessTypeDefaults.resolveDefault(name));
                        policyMap.put(name, p);
                    } else {
                        p.setEnabled(!p.isEnabled());
                    }
                    policyRepo.saveAll(new java.util.ArrayList<>(policyMap.values()));
                });
                popup.add(item);
            }
        }

        popup.addSeparator();

        JMenuItem manageItem = new JMenuItem("\uD83D\uDD0C MCP Registry...");
        manageItem.addActionListener(e -> McpRegistryBrowserDialog.show(this));
        popup.add(manageItem);

        popup.show(toolsMenuButton, 0, toolsMenuButton.getHeight());
    }


    private void addNewChatSession() {
        ChatSession sessionPanel = new ChatSession(mainframeContext, chatManager, keepAliveCheckbox, contextMemoryCheckbox, chatEventBridge);
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
