package de.bund.zrb.ui.components;

import de.zrb.bund.api.Bookmarkable;
import de.zrb.bund.newApi.ui.AppTab;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for selecting tabs to attach to the chat.
 * Groups tabs by typSchluessel (Preview, File, Connection).
 */
public class AttachTabDialog extends JDialog {

    private final List<AppTab> availableTabs;
    private final List<AppTab> selectedTabs = new ArrayList<>();
    private final JPanel tabListPanel;
    private boolean confirmed = false;

    public AttachTabDialog(Window owner, List<AppTab> availableTabs) {
        super(owner, "📎 Tab anhängen", ModalityType.APPLICATION_MODAL);
        this.availableTabs = availableTabs;

        setLayout(new BorderLayout());
        setSize(400, 350);
        setLocationRelativeTo(owner);

        // Header
        JLabel headerLabel = new JLabel("Wähle Tabs zum Anhängen aus:");
        headerLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        add(headerLabel, BorderLayout.NORTH);

        // Tab list
        tabListPanel = new JPanel();
        tabListPanel.setLayout(new BoxLayout(tabListPanel, BoxLayout.Y_AXIS));
        tabListPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        buildTabList();

        JScrollPane scrollPane = new JScrollPane(tabListPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelButton = new JButton("Abbrechen");
        cancelButton.addActionListener(e -> {
            confirmed = false;
            dispose();
        });

        JButton attachButton = new JButton("Anhängen");
        attachButton.addActionListener(e -> {
            confirmed = true;
            dispose();
        });

        buttonPanel.add(cancelButton);
        buttonPanel.add(attachButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void buildTabList() {
        tabListPanel.removeAll();

        // Group tabs by typSchluessel
        List<AppTab> previewTabs = new ArrayList<>();
        List<AppTab> fileTabs = new ArrayList<>();
        List<AppTab> otherTabs = new ArrayList<>();

        for (AppTab tab : availableTabs) {
            Bookmarkable.Type type = tab.getType();
            if (type == Bookmarkable.Type.PREVIEW) {
                previewTabs.add(tab);
            } else if (type == Bookmarkable.Type.FILE) {
                fileTabs.add(tab);
            } else if (type != Bookmarkable.Type.CONNECTION && type != Bookmarkable.Type.LOG) {
                otherTabs.add(tab);
            }
        }

        // Add sections
        if (!previewTabs.isEmpty()) {
            addSection("📄 Preview-Tabs", previewTabs);
        }
        if (!fileTabs.isEmpty()) {
            addSection("📝 Datei-Tabs", fileTabs);
        }
        if (!otherTabs.isEmpty()) {
            addSection("📋 Weitere Tabs", otherTabs);
        }

        if (previewTabs.isEmpty() && fileTabs.isEmpty() && otherTabs.isEmpty()) {
            JLabel emptyLabel = new JLabel("Keine anhängbaren Tabs geöffnet.");
            emptyLabel.setForeground(Color.GRAY);
            emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            tabListPanel.add(emptyLabel);
        }

        tabListPanel.revalidate();
        tabListPanel.repaint();
    }

    private void addSection(String title, List<AppTab> tabs) {
        JLabel sectionLabel = new JLabel(title);
        sectionLabel.setFont(sectionLabel.getFont().deriveFont(Font.BOLD, 12f));
        sectionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));
        tabListPanel.add(sectionLabel);

        for (AppTab tab : tabs) {
            JCheckBox checkBox = createTabCheckBox(tab);
            checkBox.setAlignmentX(Component.LEFT_ALIGNMENT);
            tabListPanel.add(checkBox);
        }
    }

    private JCheckBox createTabCheckBox(AppTab tab) {
        String label = tab.getTitle();
        if (label.length() > 40) {
            label = label.substring(0, 37) + "...";
        }

        JCheckBox checkBox = new JCheckBox(label);
        checkBox.setToolTipText(tab.getTooltip());

        checkBox.addActionListener(e -> {
            if (checkBox.isSelected()) {
                if (!selectedTabs.contains(tab)) {
                    selectedTabs.add(tab);
                }
            } else {
                selectedTabs.remove(tab);
            }
        });

        return checkBox;
    }

    /**
     * Show the dialog and return selected tabs.
     */
    public List<AppTab> showAndGetSelection() {
        setVisible(true);
        return confirmed ? new ArrayList<>(selectedTabs) : new ArrayList<>();
    }

    /**
     * Check if the dialog was confirmed.
     */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Get the selected tabs.
     */
    public List<AppTab> getSelectedTabs() {
        return new ArrayList<>(selectedTabs);
    }
}

