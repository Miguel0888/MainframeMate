package de.zrb.bund.newApi.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Reusable search bar with the same polished look as the menu-bar search field:
 * rounded border, focus highlight, magnifying-glass icon, placeholder text,
 * and a flat "go" button.
 * <p>
 * Layout:
 * <pre>
 *   +----------------------------------------------+
 *   |  (magnifier)  placeholder...    [>] [extras] |
 *   +----------------------------------------------+
 * </pre>
 * <ul>
 *   <li>Enter key and button click both trigger the search action</li>
 *   <li>Optional extra components can be added to the right via {@link #addEastComponent}</li>
 *   <li>Rounded border with subtle background that highlights on focus</li>
 * </ul>
 */
public class SearchBarPanel extends JPanel {

    private static final int ARC = 10;
    private static final Color BORDER_NORMAL  = new Color(0xBBBBBB);
    private static final Color BORDER_FOCUSED = new Color(0x2979FF);
    private static final Color BG_NORMAL      = new Color(0xF4F4F4);
    private static final Color BG_FOCUSED     = Color.WHITE;
    private static final Color ICON_COLOR     = new Color(0x888888);
    private static final Color PLACEHOLDER_FG = new Color(0xAAAAAA);

    private final JTextField textField;
    private final JButton goButton;
    private final JPanel eastPanel;
    private boolean fieldFocused;

    /**
     * Create a search bar with placeholder and tooltip.
     *
     * @param placeholder placeholder text shown when field is empty (may be {@code null})
     * @param tooltip     tooltip for the text field (may be {@code null})
     */
    public SearchBarPanel(String placeholder, String tooltip) {
        setOpaque(false);
        setLayout(new BorderLayout(0, 0));

        // -- Text field (no border, transparent) --
        final String placeholderText = placeholder;
        textField = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && placeholderText != null) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(hasFocus() ? new Color(0xCC, 0xCC, 0xCC) : PLACEHOLDER_FG);
                    g2.setFont(getFont().deriveFont(Font.PLAIN));
                    FontMetrics fm = g2.getFontMetrics();
                    int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString(placeholderText, getInsets().left, y);
                    g2.dispose();
                }
            }
        };
        textField.setOpaque(false);
        textField.setBorder(new EmptyBorder(2, 4, 2, 4));
        textField.setFont(textField.getFont().deriveFont(12.5f));
        if (tooltip != null) {
            textField.setToolTipText(tooltip);
        }

        // -- Go button (flat, same style as menu bar) --
        goButton = new JButton("\u25B6") {
            @Override
            public Dimension getPreferredSize() {
                int h = textField.getPreferredSize().height;
                int side = Math.max(h, 20);
                return new Dimension(side, side);
            }
        };
        goButton.setFont(goButton.getFont().deriveFont(11f));
        goButton.setMargin(new Insets(0, 0, 0, 0));
        goButton.setFocusable(false);
        goButton.setBorderPainted(false);
        goButton.setContentAreaFilled(false);
        goButton.setOpaque(false);
        goButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        goButton.setToolTipText("Suche starten");

        // -- Left icon area (magnifying glass) --
        JPanel iconPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintMagnifyingGlass((Graphics2D) g, getWidth(), getHeight());
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(24, 20);
            }
        };
        iconPanel.setOpaque(false);

        // -- East panel (go button + optional extras) --
        eastPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        eastPanel.setOpaque(false);
        eastPanel.add(goButton);

        // -- Assembly --
        add(iconPanel, BorderLayout.WEST);
        add(textField, BorderLayout.CENTER);
        add(eastPanel, BorderLayout.EAST);

        // -- Focus tracking for border highlight --
        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                fieldFocused = true;
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                fieldFocused = false;
                repaint();
            }
        });

        // Click anywhere on the panel -> focus the text field
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                textField.requestFocusInWindow();
            }
        });
    }

    /**
     * Convenience constructor with placeholder only (no tooltip).
     */
    public SearchBarPanel(String placeholder) {
        this(placeholder, null);
    }

    // ====================================================================
    //  Custom painting -- rounded container with icon (same as menu bar)
    // ====================================================================

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int inset = 1;

        // Background fill
        RoundRectangle2D bg = new RoundRectangle2D.Float(
                inset, inset, w - 2 * inset, h - 2 * inset, ARC, ARC);
        g2.setColor(fieldFocused ? BG_FOCUSED : BG_NORMAL);
        g2.fill(bg);

        // Border
        g2.setColor(fieldFocused ? BORDER_FOCUSED : BORDER_NORMAL);
        g2.setStroke(new BasicStroke(fieldFocused ? 1.5f : 1f));
        g2.draw(bg);

        g2.dispose();
    }

    @Override
    public Insets getInsets() {
        return new Insets(3, 4, 3, 3);
    }

    // ====================================================================
    //  Magnifying-glass icon painting
    // ====================================================================

    private static void paintMagnifyingGlass(Graphics2D g2, int w, int h) {
        g2 = (Graphics2D) g2.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(ICON_COLOR);
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int cx = w / 2 - 1;
        int cy = h / 2 - 1;
        int r = 5;

        g2.drawOval(cx - r, cy - r, 2 * r, 2 * r);

        int hx = cx + (int) (r * 0.7);
        int hy = cy + (int) (r * 0.7);
        g2.drawLine(hx, hy, hx + 4, hy + 4);

        g2.dispose();
    }

    // ====================================================================
    //  Search action wiring
    // ====================================================================

    /**
     * Register an action that is triggered on Enter key <em>and</em> go-button click.
     */
    public void addSearchAction(ActionListener listener) {
        textField.addActionListener(listener);
        goButton.addActionListener(listener);
    }

    // ====================================================================
    //  Public API -- text access
    // ====================================================================

    /** Return the current search text (not trimmed). */
    public String getText() {
        return textField.getText();
    }

    /** Set the search text. */
    public void setText(String text) {
        textField.setText(text);
    }

    /** Return the inner text field for custom key listeners, etc. */
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

    // ====================================================================
    //  Extra components on the right side
    // ====================================================================

    /**
     * Add a component to the east panel (right of the go button).
     * Useful for toggle buttons, pagination controls, etc.
     */
    public void addEastComponent(Component component) {
        eastPanel.add(component);
    }

    /** Access the go button (e.g. to enable/disable). */
    protected JButton getGoButton() {
        return goButton;
    }
}
