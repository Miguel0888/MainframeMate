package de.bund.zrb.ui.filetab;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class OriginalBarPanel extends JPanel {

    private final JLabel pathLabel = new JLabel();
    private final JCheckBox appendCheckbox = new JCheckBox("An Datei anhängen");
    private final JButton closeButton = new JButton("✖");

    public OriginalBarPanel() {
        super(new BorderLayout(6, 2));
        setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.add(new JLabel("Pfad: "));
        leftPanel.add(pathLabel);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightPanel.add(appendCheckbox);
        rightPanel.add(Box.createHorizontalStrut(10));
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
