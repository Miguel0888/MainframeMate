package de.bund.zrb.betaview.ui;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.CardLayout;
import java.awt.FlowLayout;

public final class ExtensionFilterPanel extends JPanel {

    private static final String CARD_BK_FREE = "BK_FREE";
    private static final String CARD_BK_BANK = "BK_BANK";
    private static final String CARD_CONST = "CONST";
    private static final String CARD_BEW_FB = "BEW_FB";
    private static final String CARD_BEW_LB = "BEW_LB";
    private static final String CARD_CUSTOM = "CUSTOM";

    private final JComboBox<OptionItem> modeBox;

    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);

    // BK + nn + free suffix
    private final JTextField bkNumberField = new JTextField(3);
    private final JTextField bkSuffixField = new JTextField(10);

    // BK + nn + BANK + optional suffix
    private final JTextField bkBankNumberField = new JTextField(3);
    private final JTextField bkBankSuffixField = new JTextField(6);

    // Constant
    private final JComboBox<OptionItem> constantBox;

    // Bew-Nr patterns
    private final JTextField bewNrFbField = new JTextField(10);
    private final JTextField bewNrLbField = new JTextField(10);

    // Custom
    private final JTextField customField = new JTextField(18);

    public ExtensionFilterPanel() {
        super(new FlowLayout(FlowLayout.LEFT, 6, 0));

        modeBox = new JComboBox<OptionItem>(new OptionItem[] {
                new OptionItem(CARD_BK_FREE, "BKnn\u2026 \u2013 Bundeskasse: BK + Kassen-Nr + Freitext (z. B. BK18F05*)"),
                new OptionItem(CARD_BK_BANK, "BKnnBANK \u2013 Bundeskasse: BK + Kassen-Nr + BANK (+ optional *)"),
                new OptionItem(CARD_CONST, "Konstant \u2013 FA00BANK / WV00BANK / \u2026"),
                new OptionItem(CARD_BEW_FB, "B<BEW-NR>F13FB \u2013 Faxfreigabe-Best\u00e4tigung"),
                new OptionItem(CARD_BEW_LB, "B<BEW-NR>F13LB \u2013 Buchungsdatei-Best\u00e4tigung"),
                new OptionItem(CARD_CUSTOM, "Custom \u2013 Freier Extension-Wert")
        });

        constantBox = new JComboBox<OptionItem>(new OptionItem[] {
                new OptionItem("FA00BANK", "FA00BANK \u2013 Finanzagentur"),
                new OptionItem("WV00BANK", "WV00BANK \u2013 Wertpapierverwaltung"),
                new OptionItem("PA00BANK", "PA00BANK \u2013 Patent- und Markenamt"),
                new OptionItem("BW19BANK", "BW19BANK \u2013 ZID Stuttgart Branntwein-Steuer"),
                new OptionItem("*", "* \u2013 Keine Einschr\u00e4nkung")
        });

        cardPanel.add(buildBkFreeCard(), CARD_BK_FREE);
        cardPanel.add(buildBkBankCard(), CARD_BK_BANK);
        cardPanel.add(buildConstantCard(), CARD_CONST);
        cardPanel.add(buildBewFbCard(), CARD_BEW_FB);
        cardPanel.add(buildBewLbCard(), CARD_BEW_LB);
        cardPanel.add(buildCustomCard(), CARD_CUSTOM);

        add(modeBox);
        add(cardPanel);

        modeBox.addActionListener(e -> cards.show(cardPanel, selectedValue(modeBox)));
        cards.show(cardPanel, CARD_BK_FREE);
    }

    public String value() {
        String mode = selectedValue(modeBox);

        if (CARD_BK_FREE.equals(mode)) {
            String nn = digitsOnly(bkNumberField.getText());
            String suffix = safe(bkSuffixField.getText());
            return "BK" + nn + suffix;
        }

        if (CARD_BK_BANK.equals(mode)) {
            String nn = digitsOnly(bkBankNumberField.getText());
            String suffix = safe(bkBankSuffixField.getText());
            return "BK" + nn + "BANK" + suffix;
        }

        if (CARD_CONST.equals(mode)) {
            return selectedValue(constantBox);
        }

        if (CARD_BEW_FB.equals(mode)) {
            String bew = digitsOnly(bewNrFbField.getText());
            return "B" + bew + "F13FB";
        }

        if (CARD_BEW_LB.equals(mode)) {
            String bew = digitsOnly(bewNrLbField.getText());
            return "B" + bew + "F13LB";
        }

        return safe(customField.getText());
    }

    public void applyInitialValue(String raw) {
        String v = safe(raw);
        if (v.isEmpty()) {
            return;
        }

        // Constants
        for (int i = 0; i < constantBox.getItemCount(); i++) {
            OptionItem it = constantBox.getItemAt(i);
            if (it.value().equals(v)) {
                setMode(CARD_CONST);
                constantBox.setSelectedIndex(i);
                return;
            }
        }

        // B<bew>F13FB / F13LB
        if (v.startsWith("B") && v.endsWith("F13FB")) {
            setMode(CARD_BEW_FB);
            bewNrFbField.setText(v.substring(1, v.length() - "F13FB".length()));
            return;
        }
        if (v.startsWith("B") && v.endsWith("F13LB")) {
            setMode(CARD_BEW_LB);
            bewNrLbField.setText(v.substring(1, v.length() - "F13LB".length()));
            return;
        }

        // BKnnBANK...
        if (v.startsWith("BK") && v.contains("BANK")) {
            int bankIdx = v.indexOf("BANK");
            String nn = v.substring(2, bankIdx);
            String suffix = v.substring(bankIdx + "BANK".length());
            setMode(CARD_BK_BANK);
            bkBankNumberField.setText(nn);
            bkBankSuffixField.setText(suffix);
            return;
        }

        // BKnn...
        if (v.startsWith("BK") && v.length() >= 4) {
            setMode(CARD_BK_FREE);
            bkNumberField.setText(v.substring(2, Math.min(4, v.length())));
            bkSuffixField.setText(v.substring(Math.min(4, v.length())));
            return;
        }

        setMode(CARD_CUSTOM);
        customField.setText(v);
    }

    public void setUiEnabled(boolean enabled) {
        SwingEnablement.setEnabledRecursive(this, enabled);
    }

    private void setMode(String mode) {
        for (int i = 0; i < modeBox.getItemCount(); i++) {
            OptionItem it = modeBox.getItemAt(i);
            if (it.value().equals(mode)) {
                modeBox.setSelectedIndex(i);
                cards.show(cardPanel, mode);
                return;
            }
        }
    }

    private JPanel buildBkFreeCard() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.add(new JLabel("BK"));
        p.add(bkNumberField);
        p.add(new JLabel("Suffix"));
        p.add(bkSuffixField);
        return p;
    }

    private JPanel buildBkBankCard() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.add(new JLabel("BK"));
        p.add(bkBankNumberField);
        p.add(new JLabel("BANK"));
        p.add(new JLabel("Suffix"));
        p.add(bkBankSuffixField);
        return p;
    }

    private JPanel buildConstantCard() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.add(constantBox);
        return p;
    }

    private JPanel buildBewFbCard() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.add(new JLabel("B"));
        p.add(bewNrFbField);
        p.add(new JLabel("F13FB"));
        return p;
    }

    private JPanel buildBewLbCard() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.add(new JLabel("B"));
        p.add(bewNrLbField);
        p.add(new JLabel("F13LB"));
        return p;
    }

    private JPanel buildCustomCard() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.add(customField);
        return p;
    }

    private String selectedValue(JComboBox<OptionItem> box) {
        Object selected = box.getSelectedItem();
        if (selected instanceof OptionItem) {
            return ((OptionItem) selected).value();
        }
        return "";
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private String digitsOnly(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("[^0-9]", "");
    }
}
