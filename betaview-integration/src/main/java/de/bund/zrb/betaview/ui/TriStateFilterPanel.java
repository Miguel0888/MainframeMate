package de.bund.zrb.betaview.ui;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import java.awt.FlowLayout;

/**
 * Reusable filter panel for JA/NEIN/empty tri-state fields
 * (ONLINE, LGRNOTE, LGRXREAD, ARCHIVE, DELETE, RELOAD).
 */
public final class TriStateFilterPanel extends JPanel {

    private final JComboBox<OptionItem> box;

    public TriStateFilterPanel() {
        this("", "JA", "NEIN");
    }

    public TriStateFilterPanel(String emptyLabel, String yesLabel, String noLabel) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 0));
        box = new JComboBox<>(new OptionItem[]{
                new OptionItem("", emptyLabel.isEmpty() ? "(Alle)" : emptyLabel),
                new OptionItem("JA", yesLabel),
                new OptionItem("NEIN", noLabel)
        });
        add(box);
    }

    public String value() {
        Object sel = box.getSelectedItem();
        return sel instanceof OptionItem ? ((OptionItem) sel).value() : "";
    }

    public void applyInitialValue(String v) {
        if (v == null) return;
        for (int i = 0; i < box.getItemCount(); i++) {
            if (box.getItemAt(i).value().equals(v.trim())) {
                box.setSelectedIndex(i);
                return;
            }
        }
    }

    public void setUiEnabled(boolean enabled) {
        SwingEnablement.setEnabledRecursive(this, enabled);
    }
}

