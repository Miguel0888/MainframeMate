package de.bund.zrb.ui.filetab;

import de.bund.zrb.ui.filetab.event.RegexFilterChangedEvent;
import de.bund.zrb.ui.filetab.event.SentenceTypeChangedEvent;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class StatusBarPanel extends JPanel {

    private final JButton undoButton = new JButton("↶");
    private final JButton redoButton = new JButton("↷");
    private final JTextField grepField = new JTextField();
    private final JComboBox<String> sentenceComboBox = new JComboBox<>();
    private final JPanel legendWrapper = new JPanel(new BorderLayout());

    public StatusBarPanel() {
        super(new BorderLayout());

        // Left: Undo/Redo
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftPanel.add(undoButton);
        leftPanel.add(redoButton);

        // Center: Regex
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(new JLabel("🔎 ", JLabel.RIGHT), BorderLayout.WEST);
        centerPanel.add(grepField, BorderLayout.CENTER);

        // Right: Legende + Satzart
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.X_AXIS));
        rightPanel.add(legendWrapper);
        rightPanel.add(Box.createHorizontalStrut(10));
        rightPanel.add(sentenceComboBox);

        add(leftPanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
    }

    public JButton getUndoButton() {
        return undoButton;
    }

    public JButton getRedoButton() {
        return redoButton;
    }

    public JTextField getGrepField() {
        return grepField;
    }

    public JComboBox<String> getSentenceComboBox() {
        return sentenceComboBox;
    }

    public JPanel getLegendWrapper() {
        return legendWrapper;
    }

    public void setSentenceTypes(java.util.List<String> types) {
        sentenceComboBox.removeAllItems();
        for (String t : types) {
            sentenceComboBox.addItem(t);
        }
    }

    public void setSelectedSentenceType(String sentenceType) {
        sentenceComboBox.setSelectedItem(sentenceType);
    }

    public void onSentenceTypeChanged(Consumer<String> callback) {
        sentenceComboBox.addActionListener(e -> {
            Object selected = sentenceComboBox.getSelectedItem();
            if (selected != null) {
                callback.accept(selected.toString());
            }
        });
    }

    public void onRegexChanged(Runnable callback) {
        grepField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { callback.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { callback.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { callback.run(); }
        });
    }

    public void bindEvents(FileTabEventDispatcher dispatcher) {
        onSentenceTypeChanged(type -> dispatcher.publish(new SentenceTypeChangedEvent(type)));
        onRegexChanged(() -> dispatcher.publish(new RegexFilterChangedEvent(getGrepField().getText())));
    }

}
