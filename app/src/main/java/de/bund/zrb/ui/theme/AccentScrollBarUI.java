package de.bund.zrb.ui.theme;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalScrollBarUI;
import java.awt.*;

/**
 * Extends MetalScrollBarUI to paint the thumb with the accent color.
 * Arrow buttons are hidden for a minimal modern look.
 */
public class AccentScrollBarUI extends MetalScrollBarUI {

    @SuppressWarnings("unused")
    public static ComponentUI createUI(JComponent c) {
        return new AccentScrollBarUI();
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
        if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color thumb = UIManager.getColor("ScrollBar.thumb");
        g2.setColor(thumb != null ? thumb : c.getForeground());
        g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y + 2,
                thumbBounds.width - 4, thumbBounds.height - 4, 8, 8);
        g2.dispose();
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        Color track = UIManager.getColor("ScrollBar.track");
        g.setColor(track != null ? track : c.getBackground());
        g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return createZeroButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return createZeroButton();
    }

    private static JButton createZeroButton() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(0, 0));
        b.setMinimumSize(new Dimension(0, 0));
        b.setMaximumSize(new Dimension(0, 0));
        return b;
    }
}

