package com.acme.betaview;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.CardLayout;
import java.awt.FlowLayout;

public final class ReportFilterPanel extends JPanel {

    private static final String CARD_ANY = "ANY";
    private static final String CARD_PREFIX = "PREFIX";
    private static final String CARD_BA = "BA";
    private static final String CARD_K9999 = "K9999";
    private static final String CARD_BEWIRT = "BEWIRT";
    private static final String CARD_CUSTOM = "CUSTOM";

    private final JComboBox<OptionItem> modeBox;

    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);

    // Prefix (*A/*E/*K)
    private final JComboBox<OptionItem> prefixBox;

    // BAxxx + type
    private final JComboBox<OptionItem> baListBox;
    private final JComboBox<OptionItem> baTypeBox;

    // K9999 + BA + nnn + i
    private final JComboBox<OptionItem> kListBox;
    private final JTextField kListOverride = new JTextField(4); // 3 digits recommended
    private final JComboBox<OptionItem> kTypeBox;

    // Custom
    private final JTextField customField = new JTextField(18);

    // Bewirtschafternummer
    private final JTextField bewirtField = new JTextField(10);
    private final JLabel bewirtPreview = new JLabel("*...*");

    public ReportFilterPanel() {
        super(new FlowLayout(FlowLayout.LEFT, 6, 0));

        modeBox = new JComboBox<OptionItem>(new OptionItem[] {
                new OptionItem(CARD_ANY, "Report: * – Keine Einschränkung"),
                new OptionItem(CARD_PREFIX, "Report: *A/*E/*K – nach Kategorie filtern"),
                new OptionItem(CARD_BA, "Report: BAxxx + Typ – ZABAK Listen"),
                new OptionItem(CARD_K9999, "Report: K9999BAnnni – gezielter Report"),
                new OptionItem(CARD_BEWIRT, "Report: Bewirtschafternummer"),
                new OptionItem(CARD_CUSTOM, "Report: Custom – freier Wert")
        });

        prefixBox = new JComboBox<OptionItem>(new OptionItem[] {
                new OptionItem("*A", "*A – Alle Auslieferungen"),
                new OptionItem("*E", "*E – Alle Einlieferungen"),
                new OptionItem("*K", "*K – Alle Kontoinformationen")
        });

        baListBox = new JComboBox<OptionItem>(new OptionItem[] {
                new OptionItem("BA001", "BA001 – Protokolle Ladeprogramm"),
                new OptionItem("BA002", "BA002 – Protokolle Entladeprogramm"),
                new OptionItem("BA100", "BA100 – Einzelsatz (Dialog)"),
                new OptionItem("BA101", "BA101 – Sendung (Dialog)"),
                new OptionItem("BA200", "BA200 – Statistik (Dialog)")
        });

        baTypeBox = new JComboBox<OptionItem>();
        updateBaTypes("BA001");
        baListBox.addActionListener(e -> updateBaTypes(selectedValue(baListBox)));

        kListBox = new JComboBox<OptionItem>(new OptionItem[] {
                new OptionItem("001", "001 – BA001 (Ladeprogramm)"),
                new OptionItem("002", "002 – BA002 (Entladeprogramm)"),
                new OptionItem("100", "100 – BA100 (Einzelsatz)"),
                new OptionItem("101", "101 – BA101 (Sendung)"),
                new OptionItem("200", "200 – BA200 (Statistik)")
        });

        kTypeBox = new JComboBox<OptionItem>(new OptionItem[] {
                new OptionItem("A", "A – Auslieferung"),
                new OptionItem("E", "E – Einlieferung"),
                new OptionItem("K", "K – Kontoinformation")
        });

        cardPanel.add(buildAnyCard(), CARD_ANY);
        cardPanel.add(buildPrefixCard(), CARD_PREFIX);
        cardPanel.add(buildBaCard(), CARD_BA);
        cardPanel.add(buildK9999Card(), CARD_K9999);
        cardPanel.add(buildBewirtCard(), CARD_BEWIRT);
        cardPanel.add(buildCustomCard(), CARD_CUSTOM);

        add(modeBox);
        add(cardPanel);

        modeBox.addActionListener(e -> cards.show(cardPanel, selectedValue(modeBox)));
        cards.show(cardPanel, CARD_ANY);
    }

    public String value() {
        String mode = selectedValue(modeBox);

        if (CARD_ANY.equals(mode)) {
            return "*";
        }

        if (CARD_PREFIX.equals(mode)) {
            return selectedValue(prefixBox);
        }

        if (CARD_BA.equals(mode)) {
            String base = selectedValue(baListBox);     // BA001
            String type = selectedValue(baTypeBox);     // A/E/K/...
            return base + type;
        }

        if (CARD_K9999.equals(mode)) {
            String digits = digitsOnly(kListOverride.getText());
            if (digits.length() != 3) {
                digits = selectedValue(kListBox);
            }
            String type = selectedValue(kTypeBox);      // A/E/K
            return "K9999" + "BA" + digits + type;
        }

        if (CARD_BEWIRT.equals(mode)) {
            String nr = safe(bewirtField.getText());
            if (nr.isEmpty()) return "*";
            return "%" + nr + "*";
        }

        return safe(customField.getText());
    }

    public void applyInitialValue(String raw) {
        String v = safe(raw);
        if (v.isEmpty()) {
            return;
        }

        if ("*".equals(v)) {
            setMode(CARD_ANY);
            return;
        }

        if ("*A".equals(v) || "*E".equals(v) || "*K".equals(v)) {
            setMode(CARD_PREFIX);
            setSelectedByValue(prefixBox, v);
            return;
        }

        if (v.startsWith("BA") && v.length() == 6) {
            setMode(CARD_BA);
            String base = v.substring(0, 5);
            String type = v.substring(5);
            setSelectedByValue(baListBox, base);
            updateBaTypes(base);
            setSelectedByValue(baTypeBox, type);
            return;
        }

        if (v.startsWith("K9999BA") && v.length() == 10) {
            setMode(CARD_K9999);
            String digits = v.substring("K9999BA".length(), "K9999BA".length() + 3);
            String type = v.substring(v.length() - 1);
            kListOverride.setText(digits);
            setSelectedByValue(kTypeBox, type);
            return;
        }

        // %xxx* or *xxx* pattern (but not *A, *E, *K which are prefix) → Bewirtschafternummer
        if (v.startsWith("%") && v.endsWith("*") && v.length() > 2) {
            String inner = v.substring(1, v.length() - 1);
            setMode(CARD_BEWIRT);
            bewirtField.setText(inner);
            return;
        }
        if (v.startsWith("*") && v.endsWith("*") && v.length() > 2) {
            String inner = v.substring(1, v.length() - 1);
            setMode(CARD_BEWIRT);
            bewirtField.setText(inner);
            return;
        }

        setMode(CARD_CUSTOM);
        customField.setText(v);
    }

    public void setUiEnabled(boolean enabled) {
        SwingEnablement.setEnabledRecursive(this, enabled);
    }

    private JPanel buildAnyCard() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.add(new JLabel("(*)"));
        return p;
    }

    private JPanel buildPrefixCard() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.add(prefixBox);
        return p;
    }

    private JPanel buildBaCard() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.add(baListBox);
        p.add(baTypeBox);
        return p;
    }

    private JPanel buildK9999Card() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.add(new JLabel("K9999BA"));
        p.add(new JLabel("List"));
        p.add(kListBox);
        p.add(new JLabel("or digits"));
        p.add(kListOverride);
        p.add(new JLabel("Type"));
        p.add(kTypeBox);
        return p;
    }

    private JPanel buildCustomCard() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.add(customField);
        return p;
    }

    private JPanel buildBewirtCard() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bewirtField.setToolTipText("Bewirtschafternummer eingeben (wird automatisch mit * umschlossen)");
        bewirtPreview.setFont(bewirtPreview.getFont().deriveFont(java.awt.Font.ITALIC));
        p.add(bewirtField);
        p.add(bewirtPreview);
        bewirtField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e)  { updateBewirtPreview(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e)  { updateBewirtPreview(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateBewirtPreview(); }
        });
        updateBewirtPreview();
        return p;
    }

    private void updateBewirtPreview() {
        String nr = safe(bewirtField.getText());
        bewirtPreview.setText(nr.isEmpty() ? "→ *" : "→ %" + nr + "*");
    }

    private void updateBaTypes(String baBase) {
        DefaultComboBoxModel<OptionItem> model = new DefaultComboBoxModel<OptionItem>();

        if ("BA001".equals(baBase)) {
            model.addElement(new OptionItem("A", "A – PROTOKOLL AUSLIEFERUNGEN"));
            model.addElement(new OptionItem("E", "E – PROTOKOLL EINLIEFERUNGEN"));
            model.addElement(new OptionItem("K", "K – PROTOKOLL KONTOINFORMATIONEN"));
            model.addElement(new OptionItem("L", "L – ABSTIMMUNG HKR"));
        } else if ("BA002".equals(baBase)) {
            model.addElement(new OptionItem("B", "B – PROTOKOLL EXPORT BBK"));
            model.addElement(new OptionItem("F", "F – PROTOKOLL EXPORT BFF"));
            model.addElement(new OptionItem("V", "V – VERWAHRFÄLLE EINZELLISTE"));
        } else if ("BA100".equals(baBase)) {
            model.addElement(new OptionItem("A", "A – EINZELSATZ AUSLIEFERUNG"));
            model.addElement(new OptionItem("E", "E – EINZELSATZ EINLIEFERUNG"));
            model.addElement(new OptionItem("K", "K – EINZELSATZ KONTOINFORMATIONEN"));
        } else if ("BA101".equals(baBase)) {
            model.addElement(new OptionItem("A", "A – SENDUNG AUSLIEFERUNG"));
            model.addElement(new OptionItem("E", "E – SENDUNG EINLIEFERUNG"));
        } else if ("BA200".equals(baBase)) {
            model.addElement(new OptionItem("A", "A – STATISTIK AUSLIEFERUNG"));
            model.addElement(new OptionItem("E", "E – STATISTIK EINLIEFERUNG"));
        } else {
            model.addElement(new OptionItem("A", "A – Auslieferung"));
            model.addElement(new OptionItem("E", "E – Einlieferung"));
            model.addElement(new OptionItem("K", "K – Kontoinformation"));
        }

        baTypeBox.setModel(model);
        if (model.getSize() > 0) {
            baTypeBox.setSelectedIndex(0);
        }
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

    private void setSelectedByValue(JComboBox<OptionItem> box, String value) {
        for (int i = 0; i < box.getItemCount(); i++) {
            OptionItem it = box.getItemAt(i);
            if (it.value().equals(value)) {
                box.setSelectedIndex(i);
                return;
            }
        }
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