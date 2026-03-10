package de.bund.zrb.betaview.ui;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import java.awt.FlowLayout;

/**
 * Reusable combo box filter panel for fields like PROCESS, LGRSTAT.
 */
public final class ComboFilterPanel extends JPanel {

    private final JComboBox<OptionItem> box;

    public ComboFilterPanel(OptionItem[] items) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 0));
        box = new JComboBox<>(items);
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

