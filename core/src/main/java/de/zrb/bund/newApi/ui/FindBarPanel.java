package de.zrb.bund.newApi.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * A variant of {@link SearchBarPanel} for <em>in-tab</em> text search ("Find in page").
 * <p>
 * Visual differences from the main {@code SearchBarPanel}:
 * <ul>
 *   <li>Warm amber/orange accent instead of blue when focused — signals
 *       "I'm filtering the current view" rather than "I'm searching for something new".</li>
 *   <li>Softer, warmer background tint.</li>
 *   <li>Uses a binoculars-style icon instead of the standard magnifying glass.</li>
 *   <li>Go-button shows a down-arrow (&#x25BC; "find next") instead of play (&#x25B6;).</li>
 * </ul>
 * <p>
 * Used at the bottom of reader/preview tabs (e.g. {@code ConfluenceReaderTab},
 * {@code WikiFileTab}) where Enter/click highlights matches in the HTML pane.
 */
public class FindBarPanel extends SearchBarPanel {

    // -- Warm palette (amber / orange) --
    private static final Color FIND_BORDER_NORMAL  = new Color(0xCCCCCC);
    private static final Color FIND_BORDER_FOCUSED = new Color(0xF57C00); // amber-700
    private static final Color FIND_BG_NORMAL      = new Color(0xFAFAFA);
    private static final Color FIND_BG_FOCUSED     = new Color(0xFFF8E1); // warm cream
    private static final Color FIND_ICON_COLOR     = new Color(0xA0A0A0);

    private boolean focused;

    public FindBarPanel(String placeholder, String tooltip) {
        super(placeholder, tooltip);
        // Override the go-button: clear button instead of find-next
        getGoButton().setText("\u2715"); // ✕
        getGoButton().setToolTipText("Suchfeld leeren");
        // Remove inherited search-action listeners from the go button
        for (java.awt.event.ActionListener al : getGoButton().getActionListeners()) {
            getGoButton().removeActionListener(al);
        }
        // Clear button clears the text field
        getGoButton().addActionListener(e -> setText(""));
    }

    public FindBarPanel(String placeholder) {
        this(placeholder, null);
    }

    /**
     * Override: only wire Enter key to the search action, not the clear button.
     */
    @Override
    public void addSearchAction(java.awt.event.ActionListener listener) {
        getTextField().addActionListener(listener);
    }

    // ====================================================================
    //  Override focus tracking to repaint with our palette
    // ====================================================================

    @Override
    protected void paintComponent(Graphics g) {
        // We paint our own rounded rect with the warm palette
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int inset = 1;

        RoundRectangle2D bg = new RoundRectangle2D.Float(
                inset, inset, w - 2 * inset, h - 2 * inset, 10, 10);
        g2.setColor(focused ? FIND_BG_FOCUSED : FIND_BG_NORMAL);
        g2.fill(bg);

        g2.setColor(focused ? FIND_BORDER_FOCUSED : FIND_BORDER_NORMAL);
        g2.setStroke(new BasicStroke(focused ? 1.5f : 1f));
        g2.draw(bg);

        g2.dispose();
    }

    /**
     * Track focus state — called by the parent's FocusListener on the text field,
     * but we also install our own to override the colours.
     */
    {
        // Instance initialiser: add a second focus listener that tracks state
        // for *our* paintComponent. The parent's listener still fires repaint().
        getTextField().addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                focused = true;
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                focused = false;
            }
        });
    }
}
