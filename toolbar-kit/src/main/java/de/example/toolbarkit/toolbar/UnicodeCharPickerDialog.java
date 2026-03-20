package de.example.toolbarkit.toolbar;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Dialog showing a browsable grid of Unicode characters (BMP + Supplementary Plane).
 * Characters are organized by Unicode block.  The user can search by hex codepoint
 * or character name, and select a character by click or double-click.
 * <p>
 * For emoji that support skin-tone modifiers (U+1F3FB..U+1F3FF) a variant panel
 * is shown so the user can optionally pick a coloured variant.
 * <p>
 * <b>Note:</b> Java&nbsp;8 ships Unicode&nbsp;6.2 — many newer Supplementary-Plane
 * characters are unknown to {@code Character.isDefined()}.  For blocks above U+FFFF
 * the defined-check is therefore skipped so that the characters still appear when the
 * installed font supports them.
 */
public class UnicodeCharPickerDialog extends JDialog {

    private String selectedChar;

    // ── Skin-tone modifiers (Fitzpatrick scale) ─────────────────
    private static final int[] SKIN_TONES = {
            0x1F3FB, // Type-1-2  (light)
            0x1F3FC, // Type-3    (medium-light)
            0x1F3FD, // Type-4    (medium)
            0x1F3FE, // Type-5    (medium-dark)
            0x1F3FF, // Type-6    (dark)
    };

    /** Unicode blocks: { display-name, hex-start, hex-end }. Popular blocks first. */
    private static final String[][] BLOCKS = {
            // ── Popular Pictographs & Emoji (Supplementary Plane) ─────
            {"Piktogramme",              "1F300", "1F5FF"},
            {"Emoticons",                "1F600", "1F64F"},
            {"Transport & Karten",       "1F680", "1F6FF"},
            {"Erg. Piktogramme",         "1F900", "1F9FF"},
            {"Piktogramme Erw.-A",       "1FA70", "1FAFF"},
            {"Eingerahmt Erg.",          "1F100", "1F1FF"},

            // ── Arrows ───────────────────────────────────────────────
            {"Pfeile",                   "2190", "21FF"},
            {"Erg. Pfeile-A",            "27F0", "27FF"},
            {"Erg. Pfeile-B",            "2900", "297F"},
            {"Erg. Pfeile-C",            "1F800", "1F8FF"},

            // ── Shapes & Symbols ─────────────────────────────────────
            {"Geometrische Formen",      "25A0", "25FF"},
            {"Geometr. Formen Erw.",     "1F780", "1F7FF"},
            {"Verschiedene Symbole",     "2600", "26FF"},
            {"Dingbats",                 "2700", "27BF"},
            {"Versch. Symbole & Pfeile", "2B00", "2BFF"},

            // ── Technical & Math ─────────────────────────────────────
            {"Technische Zeichen",       "2300", "23FF"},
            {"Mathematische Operatoren", "2200", "22FF"},
            {"Erg. Mathematik-A",        "27C0", "27EF"},
            {"Erg. Mathematik-B",        "2980", "29FF"},
            {"Erg. Math. Operatoren",    "2A00", "2AFF"},

            // ── Numbers & Letters ────────────────────────────────────
            {"Eingerahmte Zeichen",      "2460", "24FF"},
            {"Buchstabenartige Symbole", "2100", "214F"},
            {"Zahlformen",               "2150", "218F"},
            {"Hoch-/Tiefgestellt",       "2070", "209F"},

            // ── Punctuation & Currency ───────────────────────────────
            {"Allgemeine Interpunktion", "2000", "206F"},
            {"W\u00e4hrungssymbole",     "20A0", "20CF"},

            // ── Box & Block ──────────────────────────────────────────
            {"Blockelemente",            "2580", "259F"},
            {"Rahmenzeichen",            "2500", "257F"},

            // ── Latin & Scripts ──────────────────────────────────────
            {"Lateinisch Erg\u00e4nzung",  "00A0", "00FF"},
            {"Lateinisch Erweitert-A",     "0100", "017F"},
            {"Griechisch & Koptisch",      "0370", "03FF"},
            {"Kyrillisch",                 "0400", "04FF"},

            // ── Games & Other ────────────────────────────────────────
            {"Spielkarten",              "1F0A0", "1F0FF"},
            {"Mahjong-Steine",           "1F000", "1F02F"},
            {"Domino-Steine",            "1F030", "1F09F"},
            {"Braille-Muster",           "2800", "28FF"},
            {"Steuerzeichen-Bilder",     "2400", "243F"},
            {"Alchemie-Symbole",         "1F700", "1F77F"},
    };

    private final JList<String> blockList;
    private final CharGridPanel charGrid;
    private final JLabel previewLabel;
    private final JLabel infoLabel;
    private final JPanel variantPanel;
    private final JTextField searchField;

    public UnicodeCharPickerDialog(Window owner) {
        super(owner, "Unicode Zeichentabelle", ModalityType.APPLICATION_MODAL);

        DefaultListModel<String> blockModel = new DefaultListModel<String>();
        for (String[] block : BLOCKS) {
            blockModel.addElement(block[0]);
        }
        blockList = new JList<String>(blockModel);
        blockList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        blockList.setSelectedIndex(0);

        charGrid = new CharGridPanel();

        previewLabel = new JLabel("", SwingConstants.CENTER);
        previewLabel.setFont(new Font(Font.DIALOG, Font.PLAIN, 64));
        previewLabel.setPreferredSize(new Dimension(100, 80));

        infoLabel = new JLabel(" ", SwingConstants.CENTER);
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.PLAIN, 11f));

        variantPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 0));
        variantPanel.setBorder(new EmptyBorder(4, 0, 0, 0));

        searchField = new JTextField();

        buildUI();
        loadBlock(0);

        setSize(920, 600);
        setMinimumSize(new Dimension(700, 400));
        setLocationRelativeTo(owner);
    }

    /** Show the dialog modally and return the selected character, or {@code null}. */
    public String showDialog() {
        setVisible(true);
        return selectedChar;
    }

    // ═══════════════════════════════════════════════════════════════
    //  UI
    // ═══════════════════════════════════════════════════════════════

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        // ── Top: search ──────────────────────────────────────────
        JPanel topPanel = new JPanel(new BorderLayout(6, 0));
        topPanel.add(new JLabel("Suche (Hex / Name):"), BorderLayout.WEST);
        topPanel.add(searchField, BorderLayout.CENTER);
        JButton searchBtn = new JButton("Suchen");
        topPanel.add(searchBtn, BorderLayout.EAST);
        searchBtn.addActionListener(e -> doSearch());
        searchField.addActionListener(e -> doSearch());

        // ── Left: block list ─────────────────────────────────────
        blockList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                l.setBorder(new EmptyBorder(5, 10, 5, 10));
                return l;
            }
        });
        JScrollPane blockScroll = new JScrollPane(blockList);
        blockScroll.setPreferredSize(new Dimension(210, 0));
        blockList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = blockList.getSelectedIndex();
                if (idx >= 0) loadBlock(idx);
            }
        });

        // ── Center: char grid ────────────────────────────────────
        JScrollPane gridScroll = new JScrollPane(charGrid);
        gridScroll.getVerticalScrollBar().setUnitIncrement(CharGridPanel.CELL_SIZE);

        // ── Right: preview + variants ────────────────────────────
        JPanel previewOuter = new JPanel(new BorderLayout(4, 4));
        previewOuter.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Vorschau"),
                new EmptyBorder(8, 8, 8, 8)));
        previewOuter.setPreferredSize(new Dimension(160, 0));

        JPanel previewContent = new JPanel(new BorderLayout(0, 4));
        previewContent.add(previewLabel, BorderLayout.CENTER);
        previewContent.add(infoLabel, BorderLayout.SOUTH);
        previewOuter.add(previewContent, BorderLayout.NORTH);
        previewOuter.add(variantPanel, BorderLayout.CENTER);

        // ── Selection callbacks ──────────────────────────────────
        charGrid.setSelectionListener(new Consumer<String>() {
            @Override
            public void accept(String ch) {
                onCharSelected(ch);
            }
        });
        charGrid.setDoubleClickListener(new Consumer<String>() {
            @Override
            public void accept(String ch) {
                selectedChar = ch;
                dispose();
            }
        });

        // ── Bottom: buttons ──────────────────────────────────────
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton okBtn = new JButton("\u00DCbernehmen");
        JButton cancelBtn = new JButton("Abbrechen");
        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);
        okBtn.addActionListener(e -> {
            String sel = charGrid.getSelectedChar();
            if (sel != null) selectedChar = sel;
            dispose();
        });
        cancelBtn.addActionListener(e -> dispose());

        // ── Assembly ─────────────────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, blockScroll, gridScroll);
        split.setDividerLocation(210);

        JPanel centerAndPreview = new JPanel(new BorderLayout(8, 0));
        centerAndPreview.add(split, BorderLayout.CENTER);
        centerAndPreview.add(previewOuter, BorderLayout.EAST);

        root.add(topPanel, BorderLayout.NORTH);
        root.add(centerAndPreview, BorderLayout.CENTER);
        root.add(btnPanel, BorderLayout.SOUTH);
        setContentPane(root);
    }

    /** Called when a character is clicked in the grid. */
    private void onCharSelected(String ch) {
        previewLabel.setText(ch);
        int cp = ch.codePointAt(0);
        String name = charName(cp);
        infoLabel.setText("<html><center>U+" + String.format("%04X", cp)
                + "<br><small>" + name + "</small></center></html>");

        // Show skin-tone variants for supplementary emoji
        variantPanel.removeAll();
        if (cp >= 0x1F000 && cp <= 0x1FAFF) {
            variantPanel.add(makeSmallLabel("Farbvarianten:"));

            // Base (no modifier)
            final String base = ch;
            JButton baseBtn = makeVariantButton(base, "Ohne Modifikator");
            baseBtn.addActionListener(e -> {
                selectedChar = base;
                previewLabel.setText(base);
            });
            variantPanel.add(baseBtn);

            // 5 skin-tone variants
            for (int tone : SKIN_TONES) {
                final String variant = ch + new String(Character.toChars(tone));
                String toneName = skinToneName(tone);
                JButton btn = makeVariantButton(variant, toneName);
                btn.addActionListener(e -> {
                    selectedChar = variant;
                    previewLabel.setText(variant);
                });
                variantPanel.add(btn);
            }
        }
        variantPanel.revalidate();
        variantPanel.repaint();
    }

    private JButton makeVariantButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setFont(new Font(Font.DIALOG, Font.PLAIN, 20));
        Dimension d = new Dimension(36, 36);
        btn.setPreferredSize(d);
        btn.setMinimumSize(d);
        btn.setMaximumSize(d);
        btn.setMargin(new Insets(0, 0, 0, 0));
        btn.setFocusable(false);
        btn.setToolTipText(tooltip);
        return btn;
    }

    private static JLabel makeSmallLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(10f));
        return l;
    }

    private static String skinToneName(int cp) {
        switch (cp) {
            case 0x1F3FB: return "Hell (Typ 1\u20132)";
            case 0x1F3FC: return "Mittel-hell (Typ 3)";
            case 0x1F3FD: return "Mittel (Typ 4)";
            case 0x1F3FE: return "Mittel-dunkel (Typ 5)";
            case 0x1F3FF: return "Dunkel (Typ 6)";
            default: return "";
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Block loading & search
    // ═══════════════════════════════════════════════════════════════

    private void loadBlock(int index) {
        int start = Integer.parseInt(BLOCKS[index][1], 16);
        int end = Integer.parseInt(BLOCKS[index][2], 16);
        boolean supplementary = (start >= 0x10000);

        List<String> chars = new ArrayList<String>();
        for (int cp = start; cp <= end; cp++) {
            if (supplementary) {
                // Java 8 ships Unicode 6.2 — many newer Supplementary-Plane
                // characters are unknown to Character.isDefined().
                // Include all codepoints and let the font decide what to render.
                chars.add(new String(Character.toChars(cp)));
            } else {
                // BMP: use the normal filter
                if (Character.isDefined(cp) && !Character.isISOControl(cp)
                        && Character.getType(cp) != Character.FORMAT
                        && Character.getType(cp) != Character.SPACE_SEPARATOR) {
                    chars.add(new String(Character.toChars(cp)));
                }
            }
        }
        charGrid.setCharacters(chars);
    }

    private void doSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) return;

        // Try hex code-point
        String hex = query.toUpperCase(Locale.ROOT).replace("U+", "").replace("0X", "");
        if (hex.matches("[0-9A-F]{2,6}")) {
            try {
                int cp = Integer.parseInt(hex, 16);
                if (cp >= 0 && cp <= 0x1FAFF) {
                    String ch = new String(Character.toChars(cp));
                    charGrid.setCharacters(Collections.singletonList(ch));
                    onCharSelected(ch);
                    return;
                }
            } catch (NumberFormatException ignore) { /* fall through */ }
        }

        // Search by Unicode character name (BMP + supplementary ranges)
        List<String> results = new ArrayList<String>();
        String upper = query.toUpperCase(Locale.ROOT);

        // BMP
        for (int cp = 0x00A0; cp <= 0xFFFF; cp++) {
            if (!Character.isDefined(cp) || Character.isISOControl(cp)) continue;
            String name = Character.getName(cp);
            if (name != null && name.contains(upper)) {
                results.add(new String(Character.toChars(cp)));
                if (results.size() >= 512) break;
            }
        }

        // Supplementary Plane (popular pictograph ranges)
        if (results.size() < 512) {
            int[][] supplementaryRanges = {
                    {0x1F000, 0x1F1FF}, {0x1F300, 0x1F9FF}, {0x1FA70, 0x1FAFF}
            };
            for (int[] range : supplementaryRanges) {
                for (int cp = range[0]; cp <= range[1] && results.size() < 512; cp++) {
                    String name = Character.getName(cp);
                    if (name != null && name.contains(upper)) {
                        results.add(new String(Character.toChars(cp)));
                    }
                }
            }
        }

        charGrid.setCharacters(results);
    }

    /** Return the Unicode name of a codepoint, or empty string if unknown. */
    private static String charName(int cp) {
        String name = Character.getName(cp);
        return (name != null) ? name : "";
    }

    // ═══════════════════════════════════════════════════════════════
    //  CharGridPanel — custom-painted scrollable grid of characters
    // ═══════════════════════════════════════════════════════════════

    static class CharGridPanel extends JPanel implements Scrollable {

        static final int CELL_SIZE = 38;

        private List<String> characters = new ArrayList<String>();
        private int hoveredIndex = -1;
        private int selectedIndex = -1;

        private Consumer<String> selectionListener;
        private Consumer<String> doubleClickListener;

        CharGridPanel() {
            setBackground(Color.WHITE);
            setFont(new Font(Font.DIALOG, Font.PLAIN, 22));
            ToolTipManager.sharedInstance().registerComponent(this);

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int idx = indexAt(e.getPoint());
                    if (idx != hoveredIndex) {
                        hoveredIndex = idx;
                        repaint();
                    }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int idx = indexAt(e.getPoint());
                    if (idx >= 0 && idx < characters.size()) {
                        selectedIndex = idx;
                        repaint();
                        String ch = characters.get(idx);
                        if (selectionListener != null) selectionListener.accept(ch);
                        if (e.getClickCount() == 2 && doubleClickListener != null) {
                            doubleClickListener.accept(ch);
                        }
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (hoveredIndex >= 0) {
                        hoveredIndex = -1;
                        repaint();
                    }
                }
            });
        }

        void setSelectionListener(Consumer<String> l) { selectionListener = l; }

        void setDoubleClickListener(Consumer<String> l) { doubleClickListener = l; }

        void setCharacters(List<String> chars) {
            this.characters = new ArrayList<String>(chars);
            hoveredIndex = -1;
            selectedIndex = -1;
            revalidate();
            repaint();
            Container parent = getParent();
            if (parent instanceof JViewport) {
                ((JViewport) parent).setViewPosition(new Point(0, 0));
            }
        }

        String getSelectedChar() {
            return (selectedIndex >= 0 && selectedIndex < characters.size())
                    ? characters.get(selectedIndex) : null;
        }

        private int getCols() {
            int w = getWidth();
            return (w > 0) ? Math.max(1, w / CELL_SIZE) : 14;
        }

        private int indexAt(Point p) {
            int cols = getCols();
            int col = p.x / CELL_SIZE;
            int row = p.y / CELL_SIZE;
            if (col < 0 || col >= cols) return -1;
            int idx = row * cols + col;
            return (idx >= 0 && idx < characters.size()) ? idx : -1;
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            int idx = indexAt(e.getPoint());
            if (idx >= 0 && idx < characters.size()) {
                int cp = characters.get(idx).codePointAt(0);
                String name = Character.getName(cp);
                return String.format("U+%04X  %s", cp, name != null ? name : "");
            }
            return null;
        }

        @Override
        public Dimension getPreferredSize() {
            int cols = getCols();
            if (cols <= 0) cols = 14;
            int rows = (characters.size() + cols - 1) / cols;
            return new Dimension(cols * CELL_SIZE, Math.max(rows * CELL_SIZE, 60));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (characters.isEmpty()) return;

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int cols = getCols();
            Rectangle clip = g.getClipBounds();
            FontMetrics fm = g2.getFontMetrics();

            int startRow = (clip != null) ? Math.max(0, clip.y / CELL_SIZE) : 0;
            int endRow = (clip != null)
                    ? (clip.y + clip.height) / CELL_SIZE + 1
                    : (characters.size() + cols - 1) / cols;

            for (int row = startRow; row <= endRow; row++) {
                for (int col = 0; col < cols; col++) {
                    int i = row * cols + col;
                    if (i < 0 || i >= characters.size()) continue;

                    int x = col * CELL_SIZE;
                    int y = row * CELL_SIZE;

                    // Background highlight
                    if (i == selectedIndex) {
                        g2.setColor(new Color(0x2979FF));
                        g2.fillRect(x, y, CELL_SIZE, CELL_SIZE);
                    } else if (i == hoveredIndex) {
                        g2.setColor(new Color(0xDDEEFF));
                        g2.fillRect(x, y, CELL_SIZE, CELL_SIZE);
                    }

                    // Grid
                    g2.setColor(new Color(0xE8E8E8));
                    g2.drawRect(x, y, CELL_SIZE - 1, CELL_SIZE - 1);

                    // Character
                    g2.setColor(i == selectedIndex ? Color.WHITE : Color.BLACK);
                    String ch = characters.get(i);
                    int cw = fm.stringWidth(ch);
                    g2.drawString(ch,
                            x + (CELL_SIZE - cw) / 2,
                            y + (CELL_SIZE - fm.getHeight()) / 2 + fm.getAscent());
                }
            }
        }

        // ── Scrollable ──

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(14 * CELL_SIZE, 10 * CELL_SIZE);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return CELL_SIZE;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return CELL_SIZE * 4;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() { return true; }

        @Override
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
