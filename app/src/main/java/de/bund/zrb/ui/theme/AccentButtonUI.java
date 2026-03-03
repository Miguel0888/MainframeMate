package de.bund.zrb.ui.theme;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalButtonUI;
import java.awt.*;

/**
 * A ButtonUI that extends MetalButtonUI but paints a solid accent-colored
 * background fill instead of Metal's gradient. All other behavior
 * (icons, text, borders, insets, layout) remains unchanged.
 */
public class AccentButtonUI extends MetalButtonUI {

    @SuppressWarnings("unused") // called reflectively by UIManager
    public static ComponentUI createUI(JComponent c) {
        return new AccentButtonUI();
    }

    @Override
    public void update(Graphics g, JComponent c) {
        if (c.isOpaque()) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel model = b.getModel();

            Color fill = b.getBackground();
            if (model.isPressed() || model.isArmed()) {
                fill = darker(fill, 0.8f);
            } else if (model.isRollover()) {
                fill = brighter(fill, 1.15f);
            }

            g.setColor(fill);
            g.fillRect(0, 0, c.getWidth(), c.getHeight());
        }
        paint(g, c);
    }

    @Override
    protected void paintButtonPressed(Graphics g, AbstractButton b) {
        Color fill = darker(b.getBackground(), 0.8f);
        g.setColor(fill);
        g.fillRect(0, 0, b.getWidth(), b.getHeight());
    }

    private static Color darker(Color c, float factor) {
        return new Color(
                Math.max((int) (c.getRed() * factor), 0),
                Math.max((int) (c.getGreen() * factor), 0),
                Math.max((int) (c.getBlue() * factor), 0),
                c.getAlpha());
    }

    private static Color brighter(Color c, float factor) {
        return new Color(
                Math.min((int) (c.getRed() * factor), 255),
                Math.min((int) (c.getGreen() * factor), 255),
                Math.min((int) (c.getBlue() * factor), 255),
                c.getAlpha());
    }
}

