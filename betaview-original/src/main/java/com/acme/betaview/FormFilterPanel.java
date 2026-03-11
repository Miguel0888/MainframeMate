package com.acme.betaview;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.CardLayout;
import java.awt.FlowLayout;

public final class FormFilterPanel extends JPanel {

    private static final String CARD_NONE = "NONE";
    private static final String CARD_CUSTOM = "CUSTOM";

    private final JComboBox<OptionItem> baseFormBox;
    private final JTextField customField;

    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);

    public FormFilterPanel() {
        super(new FlowLayout(FlowLayout.LEFT, 6, 0));

        baseFormBox = new JComboBox<OptionItem>(new OptionItem[] {
                new OptionItem("*", "* – Keine Einschränkung"),
                new OptionItem("APZC", "APZC – Produktion (alle Produktionsdaten)"),
                new OptionItem("APZF", "APZF – Produktion (Format)"),
                new OptionItem("ATZC", "ATZC – Test (Bewirtschafter-Test/Entwicklung)"),
                new OptionItem("ATAB", "ATAB – Schulung"),
                new OptionItem("APZD", "APZD – F13Z/F15Z Buchungsdaten (Produktion)"),
                new OptionItem("ATZD", "ATZD – F13Z/F15Z Buchungsdaten (Test)"),
                new OptionItem("APAB", "APAB – ZABAK (Basis)"),
                new OptionItem("CUSTOM", "Custom – Freier Form-Wert")
        });

        customField = new JTextField(12);
        customField.setToolTipText("Freier Form-Wert eingeben");

        // Card: nothing (for * and known codes)
        cardPanel.add(new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)), CARD_NONE);

        // Card: custom free-text field
        JPanel customCard = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        customCard.add(customField);
        cardPanel.add(customCard, CARD_CUSTOM);

        add(baseFormBox);
        add(cardPanel);

        baseFormBox.addActionListener(e -> updateCard());
        updateCard();
    }

    private void updateCard() {
        String sel = selectedValue(baseFormBox);
        cards.show(cardPanel, "CUSTOM".equals(sel) ? CARD_CUSTOM : CARD_NONE);
    }

    public String value() {
        String base = selectedValue(baseFormBox);

        if ("CUSTOM".equals(base)) {
            String custom = safe(customField.getText());
            return custom.isEmpty() ? "*" : custom;
        }
        return base;
    }

    public void applyInitialValue(String raw) {
        String v = safe(raw);
        if (v.isEmpty() || "*".equals(v)) {
            setSelectedByValue(baseFormBox, "*");
            updateCard();
            return;
        }

        // Try match known base codes exactly
        String[] knownBases = {"APZC", "APZF", "ATZC", "ATAB", "APZD", "ATZD", "APAB"};
        for (String base : knownBases) {
            if (v.equals(base)) {
                setSelectedByValue(baseFormBox, base);
                updateCard();
                return;
            }
        }

        // Fallback: custom
        setSelectedByValue(baseFormBox, "CUSTOM");
        customField.setText(v);
        updateCard();
    }

    public void setUiEnabled(boolean enabled) {
        SwingEnablement.setEnabledRecursive(this, enabled);
    }

    private String selectedValue(JComboBox<OptionItem> box) {
        Object selected = box.getSelectedItem();
        if (selected instanceof OptionItem) {
            return ((OptionItem) selected).value();
        }
        return "*";
    }

    private void setSelectedByValue(JComboBox<OptionItem> box, String value) {
        for (int i = 0; i < box.getItemCount(); i++) {
            OptionItem it = box.getItemAt(i);
            if (it.value().equals(value)) {
                box.setSelectedIndex(i);
                return;
            }
        }
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}