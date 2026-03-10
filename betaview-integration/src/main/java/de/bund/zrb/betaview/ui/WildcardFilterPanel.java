package de.bund.zrb.betaview.ui;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.CardLayout;
import java.awt.FlowLayout;

/**
 * Generalised filter panel for fields that default to {@code *}.
 * <p>
 * Dropdown contains:
 * <ul>
 *   <li>{@code *} â€“ Keine EinschrÃ¤nkung  (no text field shown)</li>
 *   <li>zero or more fixed known values   (no text field shown)</li>
 *   <li>Custom â€“ Freier Wert             (free-text field shown)</li>
 * </ul>
 * Switching the dropdown dynamically shows / hides the text field via {@link CardLayout}.
 */
public final class WildcardFilterPanel extends JPanel {

    private static final String CARD_NONE = "NONE";
    private static final String CARD_CUSTOM = "CUSTOM_CARD";
    private static final String CUSTOM_VALUE = "__CUSTOM__";

    private final JComboBox<OptionItem> box;
    private final JTextField customField;

    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);

    /**
     * Creates a filter panel with only {@code *} and Custom.
     *
     * @param fieldWidth column width of the free-text field
     */
    public WildcardFilterPanel(int fieldWidth) {
        this(new OptionItem[0], fieldWidth);
    }

    /**
     * Creates a filter panel with {@code *}, the given fixed options, and Custom.
     *
     * @param fixedOptions additional known values between {@code *} and Custom
     * @param fieldWidth   column width of the free-text field
     */
    public WildcardFilterPanel(OptionItem[] fixedOptions, int fieldWidth) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 0));

        // Build combo items: * + fixed + Custom
        OptionItem[] items = new OptionItem[fixedOptions.length + 2];
        items[0] = new OptionItem("*", "* â€“ Keine EinschrÃ¤nkung");
        System.arraycopy(fixedOptions, 0, items, 1, fixedOptions.length);
        items[items.length - 1] = new OptionItem(CUSTOM_VALUE, "Custom â€“ Freier Wert");

        box = new JComboBox<>(items);

        customField = new JTextField(fieldWidth);
        customField.setToolTipText("Freien Wert eingeben");

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
        cards.show(cardPanel, CUSTOM_VALUE.equals(selectedRaw()) ? CARD_CUSTOM : CARD_NONE);
    }

    /** Returns the effective filter value. */
    public String value() {
        String raw = selectedRaw();
        if (CUSTOM_VALUE.equals(raw)) {
            String text = safe(customField.getText());
            return text.isEmpty() ? "*" : text;
        }
        return raw;
    }

    /** Pre-selects the matching entry or falls back to Custom. */
    public void applyInitialValue(String v) {
        String s = safe(v);
        if (s.isEmpty() || "*".equals(s)) {
            setSelectedByValue("*");
            updateCard();
            return;
        }
        // Try fixed entries
        for (int i = 0; i < box.getItemCount(); i++) {
            OptionItem it = box.getItemAt(i);
            if (!CUSTOM_VALUE.equals(it.value()) && it.value().equals(s)) {
                box.setSelectedIndex(i);
                updateCard();
                return;
            }
        }
        // Fallback: custom
        setSelectedByValue(CUSTOM_VALUE);
        customField.setText(s);
        updateCard();
    }

    public void setUiEnabled(boolean enabled) {
        SwingEnablement.setEnabledRecursive(this, enabled);
    }

    // ---- helpers ----

    private String selectedRaw() {
        Object sel = box.getSelectedItem();
        return sel instanceof OptionItem ? ((OptionItem) sel).value() : "*";
    }

    private void setSelectedByValue(String value) {
        for (int i = 0; i < box.getItemCount(); i++) {
            if (box.getItemAt(i).value().equals(value)) {
                box.setSelectedIndex(i);
                return;
            }
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}

