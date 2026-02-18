package de.bund.zrb.ui.filetab;

import de.bund.zrb.ui.filetab.event.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public class StatusBarPanel extends JPanel {

    private final JButton compareButton = new JButton("\uD83D\uDD00"); // 🔁
    private final JButton undoButton = new JButton("↶");
    private final JButton redoButton = new JButton("↷");

    private final JTextField grepField = new JTextField();
    private final JComboBox<String> sentenceComboBox = new JComboBox<>();
    private final JPanel legendWrapper = new JPanel(new BorderLayout());

    public StatusBarPanel() {
        super(new BorderLayout());



        // Button-Styling
        styleIconButton(compareButton, 18f);
        styleIconButton(undoButton, 18f);
        styleIconButton(redoButton, 18f);

        // Left: Compare, Undo, Redo
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.add(compareButton);
        leftPanel.add(Box.createHorizontalStrut(8));
        leftPanel.add(undoButton);
        leftPanel.add(Box.createHorizontalStrut(4));
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

    private void styleIconButton(JButton button, float fontSize) {
        button.setFont(button.getFont().deriveFont(Font.BOLD, fontSize));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFocusable(false);
    }

    public JButton getCompareButton() {
        return compareButton;
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

    public void setSentenceTypes(List<String> types) {
        sentenceComboBox.removeAllItems();
        sentenceComboBox.addItem(""); // Leerer Eintrag für "keine Satzart"
        for (String t : types) {
            sentenceComboBox.addItem(t);
        }
    }


    public void setSelectedSentenceType(String sentenceType) {
        ensureEmptySentenceOption();

        if (sentenceType == null || sentenceType.trim().isEmpty()) {
            sentenceComboBox.setSelectedIndex(0); // Leerer Eintrag
        } else {
            sentenceComboBox.setSelectedItem(sentenceType);
        }
    }

    private void ensureEmptySentenceOption() {
        if (sentenceComboBox.getItemCount() == 0) {
            sentenceComboBox.addItem("");
        }
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

        compareButton.addActionListener(e -> dispatcher.publish(new ShowComparePanelEvent()));

        undoButton.addActionListener(e -> dispatcher.publish(new UndoRequestedEvent()));
        redoButton.addActionListener(e -> dispatcher.publish(new RedoRequestedEvent()));
    }

}
