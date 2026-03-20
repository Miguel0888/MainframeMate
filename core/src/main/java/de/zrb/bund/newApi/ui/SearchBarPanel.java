package de.zrb.bund.newApi.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * Reusable search bar with magnifying-glass icon, text field, and search button.
 * <p>
 * Layout:
 * <pre>
 *   ┌──────────────────────────────────────────────────┐
 *   │  🔍  placeholder…              [🔎] [extras…]  │
 *   └──────────────────────────────────────────────────┘
 * </pre>
 * <ul>
 *   <li>Enter key and button click both trigger the search action</li>
 *   <li>Optional extra components can be added to the right</li>
 *   <li>Supports placeholder text and tooltip</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 *   SearchBarPanel bar = new SearchBarPanel("Suchen…", "Suchbegriff eingeben");
 *   bar.addSearchAction(e -&gt; performSearch());
 *   panel.add(bar, BorderLayout.NORTH);
 * </pre>
 */
public class SearchBarPanel extends JPanel {

    private static final Color ICON_COLOR = new Color(0x888888);

    private final JTextField textField;
    private final JButton searchButton;
    private final JPanel eastPanel;

    /**
     * Create a search bar with a placeholder and tooltip.
     *
     * @param placeholder placeholder text shown when field is empty (may be {@code null})
     * @param tooltip     tooltip for the text field (may be {@code null})
     */
    public SearchBarPanel(String placeholder, String tooltip) {
        setLayout(new BorderLayout(2, 0));

        // ── Magnifying-glass icon ───────────────────────────────
        JPanel iconPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintMagnifyingGlass((Graphics2D) g, getWidth(), getHeight());
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(22, 20);
            }
        };
        iconPanel.setOpaque(false);

        // ── Text field ──────────────────────────────────────────
        final String placeholderText = placeholder;
        textField = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !hasFocus() && placeholderText != null) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(new Color(0xAAAAAA));
                    g2.setFont(getFont().deriveFont(Font.PLAIN));
                    FontMetrics fm = g2.getFontMetrics();
                    int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString(placeholderText, getInsets().left + 2, y);
                    g2.dispose();
                }
            }
        };
        if (tooltip != null) {
            textField.setToolTipText(tooltip);
        }
        // Repaint on focus change to toggle placeholder visibility
        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) { textField.repaint(); }
            @Override
            public void focusLost(FocusEvent e) { textField.repaint(); }
        });

        // ── Search button ───────────────────────────────────────
        searchButton = new JButton("🔎");
        searchButton.setMargin(new Insets(1, 4, 1, 4));
        searchButton.setFocusable(false);
        searchButton.setToolTipText("Suche starten");

        // ── East panel (button + optional extras) ───────────────
        eastPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        eastPanel.setOpaque(false);
        eastPanel.add(searchButton);

        // ── Assembly ────────────────────────────────────────────
        add(iconPanel, BorderLayout.WEST);
        add(textField, BorderLayout.CENTER);
        add(eastPanel, BorderLayout.EAST);
    }

    /**
     * Convenience constructor with placeholder only (no tooltip).
     */
    public SearchBarPanel(String placeholder) {
        this(placeholder, null);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Search action wiring
    // ═══════════════════════════════════════════════════════════════

    /**
     * Register an action that is triggered on Enter key <em>and</em> search-button click.
     *
     * @param listener action listener
     */
    public void addSearchAction(ActionListener listener) {
        textField.addActionListener(listener);
        searchButton.addActionListener(listener);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Public API — text access
    // ═══════════════════════════════════════════════════════════════

    /** Return the current search text (not trimmed). */
    public String getText() {
        return textField.getText();
    }

    /** Set the search text. */
    public void setText(String text) {
        textField.setText(text);
    }

    /** Return the inner text field for adding custom key listeners, etc. */
    public JTextField getTextField() {
        return textField;
    }

    /** Request focus on the text field. */
    public void focusField() {
        textField.requestFocusInWindow();
    }

    /** Request focus and select all text. */
    public void focusAndSelectAll() {
        textField.requestFocusInWindow();
        textField.selectAll();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Extra components on the right side
    // ═══════════════════════════════════════════════════════════════

    /**
     * Add a component to the east panel (right of the search button).
     * Useful for toggle buttons, advanced-search toggles, etc.
     */
    public void addEastComponent(Component component) {
        eastPanel.add(component);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Magnifying-glass icon painting
    // ═══════════════════════════════════════════════════════════════

    private static void paintMagnifyingGlass(Graphics2D g2, int w, int h) {
        g2 = (Graphics2D) g2.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(ICON_COLOR);
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int cx = w / 2 - 1;
        int cy = h / 2 - 1;
        int r = 5;

        // Circle
        g2.drawOval(cx - r, cy - r, 2 * r, 2 * r);

        // Handle (bottom-right diagonal)
        int hx = cx + (int) (r * 0.7);
        int hy = cy + (int) (r * 0.7);
        g2.drawLine(hx, hy, hx + 4, hy + 4);

        g2.dispose();
    }
}

