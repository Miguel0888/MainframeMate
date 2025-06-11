package de.bund.zrb.ui.filetab;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class OriginalBarPanel extends JPanel {

    private final JLabel pathLabel = new JLabel();
    private final JCheckBox appendCheckbox = new JCheckBox("Anhängen");
    private final JButton closeButton = new JButton("\u274C"); // ❌

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

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.add(new JLabel("Pfad: "));
        leftPanel.add(pathLabel);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightPanel.add(appendCheckbox);
        rightPanel.add(closeButton);

        add(leftPanel, BorderLayout.WEST);
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
}
