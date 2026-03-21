package de.bund.zrb.ui.mail;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A ticker / marquee component that scrolls mail-notification snippets
 * from right to left.  Each snippet has its own colour (configurable per
 * sender-address in Settings).
 * <p>
 * Designed to sit inside a {@link JMenuBar}, to the right of the search
 * field.  The panel is transparent when idle (no messages queued).
 */
public class MailMarqueePanel extends JPanel {

    /** Single notification entry scrolling across the bar. */
    private static class Entry {
        final String text;
        final Color color;
        double x;          // current X position (fractional for smooth scroll)

        Entry(String text, Color color, double startX) {
            this.text = text;
            this.color = color;
            this.x = startX;
        }
    }

    private static final int TICK_MS = 30;          // repaint interval (~33 fps)
    private static final double SPEED_PX = 1.6;     // pixels per tick
    private static final int GAP = 60;              // min gap between entries

    private final List<Entry> entries = new ArrayList<Entry>();
    private final Timer timer;
    private Font tickerFont;

    /** Click listener – receives the sender text that was clicked. */
    public interface ClickListener {
        void onMarqueeClicked(String text);
    }

    private ClickListener clickListener;

    public MailMarqueePanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(200, 22));
        tickerFont = getFont() != null ? getFont().deriveFont(Font.BOLD, 12f)
                : new Font("SansSerif", Font.BOLD, 12);

        timer = new Timer(TICK_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tick();
            }
        });
        timer.setRepeats(true);

        // Click → find which entry was hit
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (clickListener == null) return;
                FontMetrics fm = getFontMetrics(tickerFont);
                synchronized (entries) {
                    for (Entry en : entries) {
                        int w = fm.stringWidth(en.text);
                        if (e.getX() >= (int) en.x && e.getX() <= (int) en.x + w) {
                            clickListener.onMarqueeClicked(en.text);
                            return;
                        }
                    }
                }
            }
        });

        // Hover cursor
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                boolean over = false;
                FontMetrics fm = getFontMetrics(tickerFont);
                synchronized (entries) {
                    for (Entry en : entries) {
                        int w = fm.stringWidth(en.text);
                        if (e.getX() >= (int) en.x && e.getX() <= (int) en.x + w) {
                            over = true;
                            break;
                        }
                    }
                }
                setCursor(over ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
            }
        });
    }

    public void setClickListener(ClickListener listener) {
        this.clickListener = listener;
    }

    // ================================================================
    //  Public API – add notification
    // ================================================================

    /**
     * Enqueue a notification that scrolls across the bar.
     *
     * @param text  display text (typically the sender name / address)
     * @param color text colour
     */
    public void addNotification(final String text, final Color color) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                double startX;
                synchronized (entries) {
                    if (entries.isEmpty()) {
                        startX = getWidth();
                    } else {
                        // Place after the last entry + gap
                        Entry last = entries.get(entries.size() - 1);
                        FontMetrics fm = getFontMetrics(tickerFont);
                        int lastW = fm.stringWidth(last.text);
                        double lastEnd = last.x + lastW + GAP;
                        startX = Math.max(getWidth(), lastEnd);
                    }
                    entries.add(new Entry(text, color, startX));
                }
                if (!timer.isRunning()) timer.start();
            }
        });
    }

    // ================================================================
    //  Animation
    // ================================================================

    private void tick() {
        boolean dirty = false;
        synchronized (entries) {
            Iterator<Entry> it = entries.iterator();
            FontMetrics fm = getFontMetrics(tickerFont);
            while (it.hasNext()) {
                Entry en = it.next();
                en.x -= SPEED_PX;
                int w = fm.stringWidth(en.text);
                if (en.x + w < 0) {
                    it.remove();  // scrolled off-screen
                } else {
                    dirty = true;
                }
            }
            if (entries.isEmpty()) {
                timer.stop();
            }
        }
        if (dirty || !timer.isRunning()) repaint();
    }

    // ================================================================
    //  Painting
    // ================================================================

    @Override
    protected void paintComponent(Graphics g) {
        // Transparent background – let menu bar shine through
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setFont(tickerFont);
        FontMetrics fm = g2.getFontMetrics();
        int baseline = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;

        // Clip to own bounds
        g2.setClip(0, 0, getWidth(), getHeight());

        synchronized (entries) {
            for (Entry en : entries) {
                g2.setColor(en.color);
                g2.drawString(en.text, (int) en.x, baseline);
            }
        }
        g2.dispose();
    }

    // ================================================================
    //  Lifecycle
    // ================================================================

    /** Stop the timer (call on application shutdown). */
    public void dispose() {
        timer.stop();
        synchronized (entries) {
            entries.clear();
        }
    }

    /** Set the ticker font (e.g. after theme changes). */
    public void setTickerFont(Font f) {
        this.tickerFont = f;
    }
}

