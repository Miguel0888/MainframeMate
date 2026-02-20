package de.bund.zrb.ui.filetab;

import de.bund.zrb.history.HistoryEntry;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public class OriginalBarPanel extends JPanel {

    private final JLabel pathLabel = new JLabel();
    private final JCheckBox appendCheckbox = new JCheckBox("Anhängen");
    private final JButton closeButton = new JButton("\u274C"); // ❌
    private final JComboBox<HistoryEntry> historyCombo = new JComboBox<>();
    private Consumer<HistoryEntry> historySelectionListener;

    public OriginalBarPanel() {
        super(new BorderLayout(6, 2));
        setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        // Style Close-Button
        closeButton.setToolTipText("Vergleich schließen");
        closeButton.setForeground(Color.RED);
        closeButton.setFont(closeButton.getFont().deriveFont(Font.BOLD, 18f));
        closeButton.setPreferredSize(new Dimension(28, 28));
        closeButton.setMargin(new Insets(0, 0, 0, 0));
        closeButton.setFocusPainted(true);
        closeButton.setBorderPainted(true);
        closeButton.setContentAreaFilled(true);

        // History combo styling
        historyCombo.setToolTipText("Ältere Version zum Vergleich auswählen");
        historyCombo.setVisible(false); // hidden until populated
        historyCombo.setMaximumRowCount(20);
        historyCombo.addActionListener(e -> {
            if (historySelectionListener != null && historyCombo.getSelectedItem() instanceof HistoryEntry) {
                historySelectionListener.accept((HistoryEntry) historyCombo.getSelectedItem());
            }
        });

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.add(new JLabel("Pfad: "));
        leftPanel.add(pathLabel);

        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        centerPanel.add(new JLabel("Version:"));
        centerPanel.add(historyCombo);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightPanel.add(appendCheckbox);
        rightPanel.add(closeButton);

        add(leftPanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
    }

    public void setPathText(String path) {
        pathLabel.setText(path);
    }

    public void setAppendSelected(boolean selected) {
        appendCheckbox.setSelected(selected);
    }

    public boolean isAppendSelected() {
        return appendCheckbox.isSelected();
    }

    public void onAppendChanged(Consumer<Boolean> listener) {
        appendCheckbox.addActionListener(e -> listener.accept(appendCheckbox.isSelected()));
    }

    public void setCloseAction(Runnable action) {
        closeButton.addActionListener(e -> action.run());
    }

    public JButton getCloseButton() {
        return closeButton;
    }

    /**
     * Populate the history version dropdown.
     */
    public void setHistoryVersions(List<HistoryEntry> versions) {
        historyCombo.removeAllItems();
        if (versions == null || versions.isEmpty()) {
            historyCombo.setVisible(false);
            return;
        }
        for (HistoryEntry entry : versions) {
            historyCombo.addItem(entry);
        }
        historyCombo.setVisible(true);
    }

    /**
     * Set a listener that fires when user picks a different history version.
     */
    public void onHistoryVersionSelected(Consumer<HistoryEntry> listener) {
        this.historySelectionListener = listener;
    }

    public JComboBox<HistoryEntry> getHistoryCombo() {
        return historyCombo;
    }
}
