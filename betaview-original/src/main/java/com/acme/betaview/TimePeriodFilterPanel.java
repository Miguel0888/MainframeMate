package com.acme.betaview;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.CardLayout;
import java.awt.FlowLayout;

/**
 * Filter panel for the time period selection (lastsel + timeunit + lasthoursdaysvalue).
 * Also supports absolute date range (datefrom / dateto).
 */
public final class TimePeriodFilterPanel extends JPanel {

    private static final String CARD_FIXED = "FIXED";
    private static final String CARD_INDIVIDUAL = "INDIVIDUAL";
    private static final String CARD_DATERANGE = "DATERANGE";

    private final JComboBox<OptionItem> periodBox;
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);

    private final JSpinner valueSpinner;
    private final JComboBox<OptionItem> unitBox;

    private final JTextField dateFromField = new JTextField(10);
    private final JTextField dateToField = new JTextField(10);

    public TimePeriodFilterPanel() {
        super(new FlowLayout(FlowLayout.LEFT, 6, 0));

        periodBox = new JComboBox<>(new OptionItem[]{
                new OptionItem("last7days", "Letzte 7 Tage"),
                new OptionItem("today", "Heute"),
                new OptionItem("yesterday", "Gestern"),
                new OptionItem("individual", "Individuell"),
                new OptionItem("daterange", "Datumsbereich (Von – Bis)")
        });

        valueSpinner = new JSpinner(new SpinnerNumberModel(60, 1, 99999, 1));
        unitBox = new JComboBox<>(new OptionItem[]{
                new OptionItem("days", "Tage"),
                new OptionItem("hours", "Stunden"),
                new OptionItem("minutes", "Minuten")
        });

        JPanel fixedCard = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        cardPanel.add(fixedCard, CARD_FIXED);

        JPanel individualCard = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        individualCard.add(new JLabel("Wert:"));
        individualCard.add(valueSpinner);
        individualCard.add(unitBox);
        cardPanel.add(individualCard, CARD_INDIVIDUAL);

        JPanel dateRangeCard = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        dateFromField.setToolTipText("Von-Datum (TT.MM.JJJJ)");
        dateToField.setToolTipText("Bis-Datum (TT.MM.JJJJ)");
        dateRangeCard.add(new JLabel("Von:"));
        dateRangeCard.add(dateFromField);
        dateRangeCard.add(new JLabel("Bis:"));
        dateRangeCard.add(dateToField);
        cardPanel.add(dateRangeCard, CARD_DATERANGE);

        add(periodBox);
        add(cardPanel);

        periodBox.addActionListener(e -> updateCard());
        cards.show(cardPanel, CARD_FIXED);
    }

    private void updateCard() {
        String sel = selectedValue(periodBox);
        if ("individual".equals(sel)) {
            cards.show(cardPanel, CARD_INDIVIDUAL);
        } else if ("daterange".equals(sel)) {
            cards.show(cardPanel, CARD_DATERANGE);
        } else {
            cards.show(cardPanel, CARD_FIXED);
        }
    }

    /** Returns "last" for relative modes, "date" for absolute date range. */
    public String lastdate() {
        return "daterange".equals(selectedValue(periodBox)) ? "date" : "last";
    }

    public String lastsel() {
        String sel = selectedValue(periodBox);
        // For daterange, the server still expects lastsel=individual
        return "daterange".equals(sel) ? "individual" : sel;
    }

    public String timeunit() {
        return selectedValue(unitBox);
    }

    public int value() {
        return (int) valueSpinner.getValue();
    }

    public String datefrom() {
        return "daterange".equals(selectedValue(periodBox)) ? dateFromField.getText().trim() : "";
    }

    public String dateto() {
        return "daterange".equals(selectedValue(periodBox)) ? dateToField.getText().trim() : "";
    }

    public void applyInitialValues(String lastsel, String timeunit, int value) {
        setSelectedByValue(periodBox, lastsel);
        setSelectedByValue(unitBox, timeunit);
        valueSpinner.setValue(value);
        updateCard();
    }

    public void setUiEnabled(boolean enabled) {
        SwingEnablement.setEnabledRecursive(this, enabled);
    }

    private String selectedValue(JComboBox<OptionItem> box) {
        Object sel = box.getSelectedItem();
        return sel instanceof OptionItem ? ((OptionItem) sel).value() : "";
    }

    private void setSelectedByValue(JComboBox<OptionItem> box, String value) {
        if (value == null) return;
        for (int i = 0; i < box.getItemCount(); i++) {
            if (box.getItemAt(i).value().equals(value)) {
                box.setSelectedIndex(i);
                return;
            }
        }
    }
}
