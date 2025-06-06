package de.bund.zrb.ui.components;

import de.zrb.bund.api.MainframeContext;

import javax.swing.*;
import java.awt.*;

/**
 * Displays workflow information or tools in the first tab.
 */
public class Workflow extends JPanel {

    public Workflow(MainframeContext context) {
        setLayout(new BorderLayout());

        JLabel label = new JLabel("Workflow-Funktionen folgen...");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        add(label, BorderLayout.CENTER);
    }
}
