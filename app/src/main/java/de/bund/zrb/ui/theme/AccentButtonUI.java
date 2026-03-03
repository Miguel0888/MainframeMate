package de.bund.zrb.ui.theme;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;

/**
 * A simple ButtonUI that paints a solid accent-colored background fill.
 * Metal L&F ignores Button.background due to its gradient rendering —
 * this UI class forces a real filled background.
 */
public class AccentButtonUI extends BasicButtonUI {

    private static final AccentButtonUI INSTANCE = new AccentButtonUI();

    @SuppressWarnings("unused") // called reflectively by UIManager
    public static ComponentUI createUI(JComponent c) {
        return INSTANCE;
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        AbstractButton b = (AbstractButton) c;
        ButtonModel model = b.getModel();

        Color fill = b.getBackground();
        if (model.isPressed() || model.isArmed()) {
            // Pressed: use slightly different shade
            fill = darker(fill, 0.8f);
        } else if (model.isRollover()) {
            fill = brighter(fill, 1.15f);
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 6, 6);

        // Paint border
        g2.setColor(darker(fill, 0.7f));
        g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 6, 6);
        g2.dispose();

        // Paint text/icon via super (but skip background painting)
        super.paint(g, c);
    }

    @Override
    public void update(Graphics g, JComponent c) {
        // Skip default opaque fill — we do it ourselves in paint()
        paint(g, c);
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

