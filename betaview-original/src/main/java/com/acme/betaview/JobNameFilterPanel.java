package com.acme.betaview;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.CardLayout;
import java.awt.FlowLayout;

public final class JobNameFilterPanel extends JPanel {

    private static final String CARD_NONE = "NONE";
    private static final String CARD_CUSTOM = "CUSTOM";

    private final JComboBox<OptionItem> box;
    private final JTextField customField = new JTextField(12);

    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);

    public JobNameFilterPanel() {
        super(new FlowLayout(FlowLayout.LEFT, 6, 0));

        box = new JComboBox<OptionItem>(new OptionItem[] {
                new OptionItem("*", "* – Keine Einschränkung"),
                new OptionItem("APABBBNK", "APABBBNK – Protokoll Einlieferungen/Kontoinfos (Prüfung Bundesbank-Dateien)"),
                new OptionItem("APABHOLE", "APABHOLE – Protokoll Auslieferungen (Prüfung Bewirtschafter-Dateien)"),
                new OptionItem("APABDAUB", "APABDAUB – Protokoll Auslagerung an HKR/ZÜV"),
                new OptionItem("CUSTOM", "Custom – Freier Jobname")
        });

        customField.setToolTipText("Freien Jobnamen eingeben, z. B. APAB…");

        cardPanel.add(new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)), CARD_NONE);
        JPanel customCard = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        customCard.add(customField);
        cardPanel.add(customCard, CARD_CUSTOM);

        add(box);
        add(cardPanel);

        box.addActionListener(e -> updateCard());
        updateCard();
    }

    private void updateCard() {
        cards.show(cardPanel, "CUSTOM".equals(selectedValue()) ? CARD_CUSTOM : CARD_NONE);
    }

    public String value() {
        String v = selectedValue();
        if ("CUSTOM".equals(v)) {
            return safe(customField.getText());
        }
        return v;
    }

    public void applyInitialValue(String raw) {
        String v = safe(raw);
        if (v.isEmpty()) {
            return;
        }

        for (int i = 0; i < box.getItemCount(); i++) {
            OptionItem it = box.getItemAt(i);
            if (it.value().equals(v)) {
                box.setSelectedIndex(i);
                return;
            }
        }

        box.setSelectedItem(box.getItemAt(box.getItemCount() - 1)); // CUSTOM
        customField.setText(v);
        updateCard();
    }

    public void setUiEnabled(boolean enabled) {
        SwingEnablement.setEnabledRecursive(this, enabled);
    }

    private String selectedValue() {
        Object selected = box.getSelectedItem();
        if (selected instanceof OptionItem) {
            return ((OptionItem) selected).value();
        }
        return "*";
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}