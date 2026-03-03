package de.bund.zrb.ui.theme;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

/**
 * A ScrollBarUI that paints the thumb with a solid accent color fill.
 * Metal L&F ignores ScrollBar.thumb for actual fill rendering —
 * this UI class forces the accent color.
 */
public class AccentScrollBarUI extends BasicScrollBarUI {

    @SuppressWarnings("unused") // called reflectively by UIManager
    public static ComponentUI createUI(JComponent c) {
        return new AccentScrollBarUI();
    }

    @Override
    protected void configureScrollBarColors() {
        super.configureScrollBarColors();
        // thumbColor, trackColor etc. are set from UIManager by super
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
        if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color thumbColor = UIManager.getColor("ScrollBar.thumb");
        if (thumbColor == null) thumbColor = c.getForeground();

        g2.setColor(thumbColor);
        g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y + 2,
                thumbBounds.width - 4, thumbBounds.height - 4, 8, 8);
        g2.dispose();
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        Color trackColor = UIManager.getColor("ScrollBar.track");
        if (trackColor == null) trackColor = c.getBackground();

        g.setColor(trackColor);
        g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return createArrowButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return createArrowButton();
    }

    private JButton createArrowButton() {
        JButton btn = new JButton();
        btn.setPreferredSize(new Dimension(0, 0));
        btn.setMinimumSize(new Dimension(0, 0));
        btn.setMaximumSize(new Dimension(0, 0));
        return btn;
    }
}

