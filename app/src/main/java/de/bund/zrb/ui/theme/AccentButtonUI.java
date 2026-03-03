package de.bund.zrb.ui.theme;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalButtonUI;
import java.awt.*;

/**
 * Extends MetalButtonUI to paint a solid accent-colored background.
 * Only overrides background painting; all icon layout, text rendering,
 * borders and insets remain unchanged from MetalButtonUI.
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
                fill = scale(fill, 0.8f);
            } else if (model.isRollover()) {
                fill = scale(fill, 1.15f);
            }
            g.setColor(fill);
            g.fillRect(0, 0, c.getWidth(), c.getHeight());
        }
        paint(g, c);
    }

    @Override
    protected void paintButtonPressed(Graphics g, AbstractButton b) {
        g.setColor(scale(b.getBackground(), 0.8f));
        g.fillRect(0, 0, b.getWidth(), b.getHeight());
    }

    private static Color scale(Color c, float factor) {
        return new Color(
                clamp((int) (c.getRed() * factor)),
                clamp((int) (c.getGreen() * factor)),
                clamp((int) (c.getBlue() * factor)),
                c.getAlpha());
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}

