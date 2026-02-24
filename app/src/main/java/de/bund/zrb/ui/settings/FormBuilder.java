package de.bund.zrb.ui.settings;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Helper for building two-column form panels (label left, control right)
 * with consistent alignment and spacing, used in the Outlook-style settings dialog.
 */
final class FormBuilder {

    private final JPanel panel;
    private final GridBagConstraints gbc;

    FormBuilder() {
        panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(8, 12, 8, 12));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridy = 0;
    }

    /** The built panel. */
    JPanel getPanel() {
        // Filler to push everything to the top
        gbc.gridx = 0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(Box.createGlue(), gbc);
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        return panel;
    }

    // ---- Row types ----

    /** Label (col 0) + component (col 1) on the same row. */
    FormBuilder addRow(String label, JComponent component) {
        return addRow(new JLabel(label), component);
    }

    /** Custom label component (col 0) + component (col 1) on the same row. */
    FormBuilder addRow(JComponent labelComp, JComponent component) {
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(labelComp, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(component, gbc);
        gbc.weightx = 0;

        gbc.gridy++;
        return this;
    }

    /** Full-width component (spans both columns). */
    FormBuilder addWide(JComponent component) {
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(component, gbc);
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.gridy++;
        return this;
    }

    /** Full-width component that also expands vertically (e.g. tables, text areas). */
    FormBuilder addWideGrow(JComponent component) {
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        panel.add(component, gbc);
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridy++;
        return this;
    }

    /** Section header – bold label spanning both columns. */
    FormBuilder addSection(String title) {
        addSeparator();
        JLabel lbl = new JLabel(title);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, lbl.getFont().getSize2D() + 1f));
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 4, 4, 8);
        panel.add(lbl, gbc);
        gbc.gridwidth = 1;
        gbc.insets = new Insets(4, 4, 4, 8);
        gbc.gridy++;
        return this;
    }

    /** Thin separator line. */
    FormBuilder addSeparator() {
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 4, 6, 8);
        panel.add(new JSeparator(), gbc);
        gbc.gridwidth = 1;
        gbc.insets = new Insets(4, 4, 4, 8);
        gbc.gridy++;
        return this;
    }

    /** Vertical gap. */
    FormBuilder addGap(int height) {
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        panel.add(Box.createVerticalStrut(height), gbc);
        gbc.gridwidth = 1;
        gbc.gridy++;
        return this;
    }

    /** Info text (gray, small, spanning both columns). */
    FormBuilder addInfo(String htmlText) {
        JLabel info = new JLabel("<html><small>" + htmlText + "</small></html>");
        info.setForeground(Color.GRAY);
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(info, gbc);
        gbc.gridwidth = 1;
        gbc.gridy++;
        return this;
    }

    /** Label (col 0) + component + extra button on the right, all on one row. */
    FormBuilder addRowWithButton(String label, JComponent component, JButton button) {
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);

        JPanel rightPanel = new JPanel(new BorderLayout(4, 0));
        rightPanel.add(component, BorderLayout.CENTER);
        rightPanel.add(button, BorderLayout.EAST);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(rightPanel, gbc);
        gbc.weightx = 0;

        gbc.gridy++;
        return this;
    }

    /** Buttons panel (right-aligned, spanning both columns). */
    FormBuilder addButtons(JButton... buttons) {
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (JButton b : buttons) bp.add(b);
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(bp, gbc);
        gbc.gridwidth = 1;
        gbc.gridy++;
        return this;
    }
}
