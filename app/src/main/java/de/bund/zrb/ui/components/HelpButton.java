package de.bund.zrb.ui.components;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;

/**
 * Kleiner, runder Hilfe-Button mit blauem Hintergrund und weißem "?".
 * Entspricht den UI/UX-Anforderungen:
 * - Feste Größe: 22×22 px
 * - Hintergrund: blauer Kreis (#1E88E5)
 * - Vordergrund: weißes "?" (fett, zentriert)
 * - Hand-Cursor
 * - Kein Fokus
 */
public class HelpButton extends JButton {

    private static final int SIZE = 22;
    private static final Color BACKGROUND_COLOR = new Color(0x1E88E5);
    private static final Color FOREGROUND_COLOR = Color.WHITE;

    public HelpButton() {
        this(null);
    }

    public HelpButton(String tooltip) {
        super("?");

        // Feste Größe
        setPreferredSize(new Dimension(SIZE, SIZE));
        setMinimumSize(new Dimension(SIZE, SIZE));
        setMaximumSize(new Dimension(SIZE, SIZE));

        // Keine Standard-Button-Optik
        setBorderPainted(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setOpaque(false);

        // Kein Fokus
        setFocusable(false);

        // Hand-Cursor
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Tooltip
        if (tooltip != null && !tooltip.isEmpty()) {
            setToolTipText(tooltip);
        } else {
            setToolTipText("Hilfe anzeigen");
        }

        // Font: fett und zentriert
        setFont(getFont().deriveFont(Font.BOLD, 12f));
        setHorizontalAlignment(SwingConstants.CENTER);
        setVerticalAlignment(SwingConstants.CENTER);
    }

    /**
     * Erstellt einen HelpButton mit einem ActionListener.
     */
    public HelpButton(String tooltip, ActionListener action) {
        this(tooltip);
        if (action != null) {
            addActionListener(action);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        int diameter = Math.min(width, height);

        // Kreis zentrieren
        int x = (width - diameter) / 2;
        int y = (height - diameter) / 2;

        // Blauer Kreis zeichnen
        g2.setColor(BACKGROUND_COLOR);
        g2.fill(new Ellipse2D.Double(x, y, diameter, diameter));

        // Weißes "?" zeichnen
        g2.setColor(FOREGROUND_COLOR);
        g2.setFont(getFont());

        FontMetrics fm = g2.getFontMetrics();
        String text = getText();
        int textX = (width - fm.stringWidth(text)) / 2;
        int textY = (height - fm.getHeight()) / 2 + fm.getAscent();

        g2.drawString(text, textX, textY);
        g2.dispose();
    }

    @Override
    public boolean contains(int x, int y) {
        // Nur Klicks innerhalb des Kreises akzeptieren
        int diameter = Math.min(getWidth(), getHeight());
        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;
        int radius = diameter / 2;

        double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));
        return distance <= radius;
    }
}

