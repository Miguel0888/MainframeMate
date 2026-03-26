package de.zrb.bund.newApi.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A variant of {@link SearchBarPanel} for <em>in-tab</em> text search ("Find in page").
 * <p>
 * Visual differences from the main {@code SearchBarPanel}:
 * <ul>
 *   <li>Warm amber/orange accent instead of blue when focused.</li>
 *   <li>Softer, warmer background tint.</li>
 * </ul>
 * <p>
 * <b>Navigation mode</b> ({@link #setStepSearchEnabled(boolean)}):
 * <ul>
 *   <li>When disabled (default): Enter highlights <em>all</em> matches at once.</li>
 *   <li>When enabled: first Enter/button press triggers the search,
 *       the enter button then transforms into ◀ ▶ prev/next arrows that
 *       step through matches one by one.</li>
 * </ul>
 * The mode can be toggled via a right-click context menu on the search bar
 * and persisted externally.
 */
public class FindBarPanel extends SearchBarPanel {

    // -- Warm palette (amber / orange) --
    private static final Color FIND_BORDER_NORMAL  = new Color(0xCCCCCC);
    private static final Color FIND_BORDER_FOCUSED = new Color(0xF57C00); // amber-700
    private static final Color FIND_BG_NORMAL      = new Color(0xFAFAFA);
    private static final Color FIND_BG_FOCUSED     = new Color(0xFFF8E1); // warm cream

    private boolean focused;

    // ── Navigation buttons ──────────────────────────────────────
    private final JButton enterButton;   // ↵  (initial state)
    private final JButton prevButton;    // ◀  (after first search, step-search mode)
    private final JButton nextButton;    // ▶  (after first search, step-search mode)
    private final JButton clearButton;   // ✕  (always visible)

    // ── Step-search state ───────────────────────────────────────
    private boolean stepSearchEnabled = false;
    private boolean arrowsVisible = false;

    // ── Listeners ───────────────────────────────────────────────
    private final List<ActionListener> searchListeners = new ArrayList<ActionListener>();
    private ActionListener prevListener;
    private ActionListener nextListener;

    // ── Persistence callback ────────────────────────────────────
    /** Called when the user toggles step-search mode via context menu. */
    private StepSearchModeListener stepSearchModeListener;

    /** Callback for persisting the step-search preference. */
    public interface StepSearchModeListener {
        void onStepSearchModeChanged(boolean stepSearch);
    }

    // ═════════════════════════════════════════════════════════════
    //  Constructors
    // ═════════════════════════════════════════════════════════════

    public FindBarPanel(String placeholder, String tooltip) {
        super(placeholder, tooltip);

        // ── Override the inherited go button: make it the "clear" (✕) button ──
        clearButton = getGoButton();
        clearButton.setText("\u2715"); // ✕
        clearButton.setToolTipText("Suchfeld leeren");
        for (ActionListener al : clearButton.getActionListeners()) {
            clearButton.removeActionListener(al);
        }
        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setText("");
                resetToEnterButton();
            }
        });

        // ── Enter button (↵) — triggers first search ──
        enterButton = makeNavButton("\u21B5", "Suche starten (Enter)"); // ↵
        enterButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fireSearch(e);
                if (stepSearchEnabled && !getText().trim().isEmpty()) {
                    showArrows();
                }
            }
        });

        // ── Prev button (◀) — previous match ──
        prevButton = makeNavButton("\u25C0", "Vorheriger Treffer");
        prevButton.setVisible(false);
        prevButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (prevListener != null) prevListener.actionPerformed(e);
            }
        });

        // ── Next button (▶) — next match ──
        nextButton = makeNavButton("\u25B6", "N\u00e4chster Treffer");
        nextButton.setVisible(false);
        nextButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (nextListener != null) nextListener.actionPerformed(e);
            }
        });

        // ── Add navigation buttons to the east panel (before the clear button) ──
        JPanel east = getEastPanel();
        east.removeAll();
        east.add(enterButton);
        east.add(prevButton);
        east.add(nextButton);
        east.add(clearButton);

        // ── Right-click context menu for toggling search mode ──
        installContextMenu();
    }

    public FindBarPanel(String placeholder) {
        this(placeholder, null);
    }

    // ═════════════════════════════════════════════════════════════
    //  Button factory
    // ═════════════════════════════════════════════════════════════

    private static JButton makeNavButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 11f));
        btn.setMargin(new Insets(0, 2, 0, 2));
        btn.setFocusable(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);
        return btn;
    }

    // ═════════════════════════════════════════════════════════════
    //  Search action wiring
    // ═════════════════════════════════════════════════════════════

    /**
     * Register a search action. In non-step mode the action fires on every Enter.
     * In step mode it fires only on the first Enter (or when query text changes).
     */
    @Override
    public void addSearchAction(final ActionListener listener) {
        searchListeners.add(listener);
        // Enter key on text field triggers the same logic as the enter button
        getTextField().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fireSearch(e);
                if (stepSearchEnabled && !getText().trim().isEmpty()) {
                    showArrows();
                }
            }
        });
    }

    /** Set the listener for "previous match" navigation. */
    public void setPrevAction(ActionListener listener) {
        this.prevListener = listener;
    }

    /** Set the listener for "next match" navigation. */
    public void setNextAction(ActionListener listener) {
        this.nextListener = listener;
    }

    private void fireSearch(ActionEvent e) {
        for (ActionListener l : searchListeners) {
            l.actionPerformed(e);
        }
    }

    // ═════════════════════════════════════════════════════════════
    //  Arrow / Enter state management
    // ═════════════════════════════════════════════════════════════

    /**
     * Switch to arrow (◀ ▶) navigation mode.
     * Can be called externally e.g. when diagram search finds results.
     */
    public void showArrows() {
        if (arrowsVisible) return;
        arrowsVisible = true;
        enterButton.setVisible(false);
        prevButton.setVisible(true);
        nextButton.setVisible(true);
        revalidate();
        repaint();
    }

    /** Reset to Enter button (hide arrows). Called e.g. when text is cleared. */
    public void resetToEnterButton() {
        arrowsVisible = false;
        enterButton.setVisible(true);
        prevButton.setVisible(false);
        nextButton.setVisible(false);
        revalidate();
        repaint();
    }

    /** Returns true if step-search navigation is currently active (arrows visible). */
    public boolean isArrowsVisible() {
        return arrowsVisible;
    }

    // ═════════════════════════════════════════════════════════════
    //  Step-search mode configuration
    // ═════════════════════════════════════════════════════════════

    /** Enable or disable step-search (prev/next) mode. Default: {@code false}. */
    public void setStepSearchEnabled(boolean enabled) {
        this.stepSearchEnabled = enabled;
        if (!enabled) {
            resetToEnterButton();
        }
    }

    /** Returns whether step-search mode is enabled. */
    public boolean isStepSearchEnabled() {
        return stepSearchEnabled;
    }

    /** Register a callback for when the user toggles step-search via context menu. */
    public void setStepSearchModeListener(StepSearchModeListener listener) {
        this.stepSearchModeListener = listener;
    }

    // ═════════════════════════════════════════════════════════════
    //  Context menu (right-click on search bar)
    // ═════════════════════════════════════════════════════════════

    private void installContextMenu() {
        final JPopupMenu popup = new JPopupMenu();
        final JCheckBoxMenuItem stepItem = new JCheckBoxMenuItem("Treffer einzeln durchgehen");
        stepItem.setToolTipText("Aktiviert: Pfeil-Navigation von Treffer zu Treffer. "
                + "Deaktiviert: Alle Treffer gleichzeitig hervorheben.");
        stepItem.setSelected(stepSearchEnabled);
        stepItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stepSearchEnabled = stepItem.isSelected();
                if (!stepSearchEnabled) {
                    resetToEnterButton();
                }
                if (stepSearchModeListener != null) {
                    stepSearchModeListener.onStepSearchModeChanged(stepSearchEnabled);
                }
            }
        });
        popup.add(stepItem);

        MouseAdapter rightClickAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showPopup(e);
            }

            private void showPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    stepItem.setSelected(stepSearchEnabled);
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        };
        addMouseListener(rightClickAdapter);
        getTextField().addMouseListener(rightClickAdapter);
    }

    // ═════════════════════════════════════════════════════════════
    //  Focus-aware painting (warm palette override)
    // ═════════════════════════════════════════════════════════════

    @Override
    protected void paintComponent(Graphics g) {
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

    {
        // Instance initialiser: track focus state for our warm paintComponent
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
