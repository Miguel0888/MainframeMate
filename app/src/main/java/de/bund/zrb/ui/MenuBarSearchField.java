package de.bund.zrb.ui;

import de.bund.zrb.model.BookmarkEntry;
import de.bund.zrb.ui.search.SearchTab;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.ui.FtpTab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;
import java.util.List;

/**
 * A polished search control designed to sit inside a {@link JMenuBar}.
 * <p>
 * Visual design:
 * <pre>
 *   ┌──────────────────────────────────────┐
 *   │  🔍  Überall suchen…          [ → ] │
 *   └──────────────────────────────────────┘
 * </pre>
 * <ul>
 *   <li>Rounded border with subtle shadow effect</li>
 *   <li>Magnifying-glass icon painted on the left</li>
 *   <li>Placeholder text that fades on focus</li>
 *   <li>Square "go" button on the right</li>
 *   <li>Enter / button-click opens a {@link SearchTab}</li>
 *   <li>Escape clears and returns focus to the active tab</li>
 * </ul>
 */
public class MenuBarSearchField extends JPanel {

    private static final int ARC = 10;
    private static final Color BORDER_NORMAL  = new Color(0xBBBBBB);
    private static final Color BORDER_FOCUSED = new Color(0x2979FF);
    private static final Color BG_NORMAL      = new Color(0xF4F4F4);
    private static final Color BG_FOCUSED     = Color.WHITE;
    private static final Color ICON_COLOR     = new Color(0x888888);
    private static final Color PLACEHOLDER_FG = new Color(0xAAAAAA);

    /** Protocol prefixes that trigger direct resource opening instead of search. */
    private static final List<String> RESOURCE_PREFIXES = Arrays.asList(
            BookmarkEntry.PREFIX_FTP,        // ftp://
            BookmarkEntry.PREFIX_NDV,        // ndv://
            BookmarkEntry.PREFIX_LOCAL,      // local://
            BookmarkEntry.PREFIX_MAIL,       // mail://
            BookmarkEntry.PREFIX_SHAREPOINT, // sp://
            BookmarkEntry.PREFIX_BETAVIEW,   // betaview://
            BookmarkEntry.PREFIX_TN3270,     // tn3270://
            BookmarkEntry.PREFIX_HTTP,       // http://
            BookmarkEntry.PREFIX_HTTPS,      // https://
            BookmarkEntry.PREFIX_CONFLUENCE, // confluence://
            BookmarkEntry.PREFIX_WIKI        // wiki://
    );

    /** Short-hand prefixes (without "//") that also trigger direct open. */
    private static final List<String> SHORT_PREFIXES = Arrays.asList(
            "ftp:", "ndv:", "local:", "mail:", "sp:", "betaview:", "tn3270:",
            "confluence:", "wiki:"
    );

    private final JTextField textField;
    private final JButton goButton;
    private final TabbedPaneManager tabManager;
    private boolean fieldFocused;

    public MenuBarSearchField(TabbedPaneManager tabManager) {
        this.tabManager = tabManager;

        setOpaque(false);
        setLayout(new BorderLayout(0, 0));

        // ── Text field (no border, transparent) ─────────────────
        textField = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Paint placeholder when empty
                if (getText().isEmpty()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(hasFocus() ? new Color(0xCC, 0xCC, 0xCC) : PLACEHOLDER_FG);
                    g2.setFont(getFont().deriveFont(Font.PLAIN));
                    FontMetrics fm = g2.getFontMetrics();
                    int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString("\u00DCberall suchen\u2026", getInsets().left, y);
                    g2.dispose();
                }
            }
        };
        textField.setOpaque(false);
        textField.setBorder(new EmptyBorder(2, 4, 2, 4));
        textField.setFont(textField.getFont().deriveFont(12.5f));

        // ── Go button ───────────────────────────────────────────
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

        // ── Left icon area (magnifying glass) ───────────────────
        JPanel iconLabel = new JPanel() {
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
        iconLabel.setOpaque(false);

        // ── Assembly ────────────────────────────────────────────
        add(iconLabel, BorderLayout.WEST);
        add(textField, BorderLayout.CENTER);
        add(goButton, BorderLayout.EAST);

        // ── Focus tracking for border highlight ─────────────────
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

        // ── Actions ─────────────────────────────────────────────
        textField.addActionListener(e -> performSearch());
        goButton.addActionListener(e -> performSearch());

        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    textField.setText("");
                    tabManager.getSelectedTab().ifPresent(tab -> {
                        if (tab instanceof FtpTab) {
                            ((FtpTab) tab).getComponent().requestFocusInWindow();
                        }
                    });
                }
            }
        });

        // Click anywhere on the panel → focus the text field
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                textField.requestFocusInWindow();
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  Search action
    // ═══════════════════════════════════════════════════════════════

    private void performSearch() {
        String query = textField.getText().trim();

        // ── Check for bookmark / resource syntax (e.g. "ftp://host/path", "ndv:LIB/OBJ") ──
        if (!query.isEmpty() && isResourcePath(query)) {
            String resourcePath = normalizeResourcePath(query);
            textField.setText("");
            MainframeContext ctx = tabManager.getMainframeContext();
            if (ctx != null) {
                ctx.openFileOrDirectory(resourcePath);
            }
            return;
        }

        // ── Normal full-text search ─────────────────────────────
        SearchTab searchTab = new SearchTab(tabManager);
        tabManager.addTab(searchTab);
        if (!query.isEmpty()) {
            searchTab.searchFor(query);
        } else {
            SwingUtilities.invokeLater(searchTab::focusSearchField);
        }
        textField.setText("");
    }

    /**
     * Returns {@code true} if the input looks like a resource path with a known protocol prefix.
     */
    private static boolean isResourcePath(String input) {
        String lower = input.toLowerCase();
        for (String prefix : RESOURCE_PREFIXES) {
            if (lower.startsWith(prefix.toLowerCase())) return true;
        }
        for (String prefix : SHORT_PREFIXES) {
            if (lower.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Normalise short-hand prefixes into their full {@code protocol://} form so that
     * {@link MainframeContext#openFileOrDirectory(String)} can route them correctly.
     * E.g. {@code "ftp:host/path"} → {@code "ftp://host/path"}.
     */
    private static String normalizeResourcePath(String input) {
        for (String shortPrefix : SHORT_PREFIXES) {
            if (input.toLowerCase().startsWith(shortPrefix)
                    && !input.toLowerCase().startsWith(shortPrefix + "//")) {
                // Insert "//" after the short prefix
                return input.substring(0, shortPrefix.length()) + "//"
                        + input.substring(shortPrefix.length());
            }
        }
        return input;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Custom painting — rounded container with icon
    // ═══════════════════════════════════════════════════════════════

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

    /** Paint a simple magnifying-glass icon. */
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

    // ═══════════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════════

    /** Request focus on the text field (e.g. via keyboard shortcut). */
    public void focusSearch() {
        textField.requestFocusInWindow();
        textField.selectAll();
    }

    /** The text field inside this control. */
    public JTextField getTextField() {
        return textField;
    }
}
