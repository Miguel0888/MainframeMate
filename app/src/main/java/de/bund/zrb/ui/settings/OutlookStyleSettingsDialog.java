package de.bund.zrb.ui.settings;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic Outlook-style settings dialog.
 * <ul>
 *   <li>Left: category navigation ({@link JList})</li>
 *   <li>Right: card-based content panel – each card has its own {@link JScrollPane}</li>
 *   <li>Bottom: footer with left action-buttons and right Apply/OK/Cancel</li>
 * </ul>
 */
public class OutlookStyleSettingsDialog extends JDialog {

    private final List<SettingsCategory> categories = new ArrayList<>();
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> categoryList;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardContainer;
    private boolean applied = false;

    /**
     * @param owner           parent window (may be null)
     * @param title           dialog title
     * @param categories      ordered list of categories
     * @param leftButtons     optional buttons for the footer-left area (may be empty/null)
     */
    public OutlookStyleSettingsDialog(Window owner,
                                      String title,
                                      List<SettingsCategory> categories,
                                      List<JButton> leftButtons) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        for (SettingsCategory cat : categories) {
            this.categories.add(cat);
            listModel.addElement(cat.getTitle());
        }

        // ---- left navigation ----
        categoryList = new JList<>(listModel);
        categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categoryList.setFixedCellHeight(28);
        categoryList.setVisibleRowCount(Math.min(14, categories.size()));
        categoryList.setBorder(new EmptyBorder(4, 4, 4, 8));
        categoryList.setFont(categoryList.getFont().deriveFont(Font.PLAIN, 13f));

        JScrollPane leftScroll = new JScrollPane(categoryList);
        leftScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        leftScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        leftScroll.setMinimumSize(new Dimension(160, 100));

        // ---- right content: each card is its own JScrollPane ----
        cardContainer = new JPanel(cardLayout);

        for (SettingsCategory cat : categories) {
            JComponent comp = cat.getComponent();
            JScrollPane scrollPane = wrapInScrollPane(comp);
            cardContainer.add(scrollPane, cat.getId());
        }

        // ---- selection listener ----
        categoryList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int idx = categoryList.getSelectedIndex();
            if (idx >= 0 && idx < this.categories.size()) {
                cardLayout.show(cardContainer, this.categories.get(idx).getId());
            }
        });

        // ---- split pane ----
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, cardContainer);
        splitPane.setDividerLocation(180);
        splitPane.setResizeWeight(0.0); // navigation keeps fixed width when resizing
        splitPane.setContinuousLayout(true);

        // ---- footer ----
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(new EmptyBorder(8, 0, 0, 0));

        JPanel leftButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        if (leftButtons != null) {
            for (JButton btn : leftButtons) {
                btn.setFocusable(false);
                leftButtonPanel.add(btn);
            }
        }

        JPanel rightButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton btnApply  = new JButton("Übernehmen");
        JButton btnOk     = new JButton("OK");
        JButton btnCancel = new JButton("Abbrechen");

        btnApply.addActionListener(e -> doApply());
        btnOk.addActionListener(e -> {
            if (doApply()) {
                dispose();
            }
        });
        btnCancel.addActionListener(e -> dispose());

        rightButtonPanel.add(btnApply);
        rightButtonPanel.add(btnOk);
        rightButtonPanel.add(btnCancel);

        footer.add(leftButtonPanel, BorderLayout.WEST);
        footer.add(rightButtonPanel, BorderLayout.EAST);

        // ---- root panel ----
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(new EmptyBorder(10, 12, 10, 12));
        root.add(splitPane, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);

        // ---- sizing ----
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int w = clamp((int) (screen.width * 0.75), 900, 1400);
        int h = clamp((int) (screen.height * 0.85), 600, 1000);
        setSize(w, h);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(owner);

        // select first category
        if (!categories.isEmpty()) {
            categoryList.setSelectedIndex(0);
        }
    }

    /** Select a category by index (0-based). */
    public void selectCategory(int index) {
        if (index >= 0 && index < categories.size()) {
            categoryList.setSelectedIndex(index);
        }
    }

    /** @return true if Apply was invoked at least once. */
    public boolean wasApplied() {
        return applied;
    }

    // ---- internal ----

    /**
     * Wraps a component in a JScrollPane, ensuring proper sizing behaviour.
     * If the component is already a JScrollPane, it is returned as-is.
     */
    private static JScrollPane wrapInScrollPane(JComponent comp) {
        if (comp instanceof JScrollPane) {
            JScrollPane existing = (JScrollPane) comp;
            existing.getVerticalScrollBar().setUnitIncrement(16);
            return existing;
        }

        // Wrap in a StretchPanel so the content fills the viewport width
        StretchyPanel wrapper = new StretchyPanel(comp);

        JScrollPane scrollPane = new JScrollPane(wrapper);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        return scrollPane;
    }

    private boolean doApply() {
        for (SettingsCategory cat : categories) {
            try {
                cat.validate();
            } catch (IllegalArgumentException ex) {
                int idx = categories.indexOf(cat);
                if (idx >= 0) categoryList.setSelectedIndex(idx);
                JOptionPane.showMessageDialog(this,
                        ex.getMessage(),
                        "Validierungsfehler",
                        JOptionPane.WARNING_MESSAGE);
                return false;
            }
        }
        for (SettingsCategory cat : categories) {
            cat.apply();
        }
        applied = true;
        return true;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---- Scrollable wrapper ----

    /**
     * Wraps a content panel so that it stretches to the viewport width
     * but allows vertical scrolling. This avoids unwanted horizontal scrollbars
     * for GridBagLayout / BoxLayout panels.
     */
    private static final class StretchyPanel extends JPanel implements Scrollable {

        StretchyPanel(JComponent content) {
            super(new BorderLayout());
            setBorder(new EmptyBorder(4, 4, 4, 4));
            add(content, BorderLayout.CENTER);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return (orientation == SwingConstants.VERTICAL) ? visibleRect.height : visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true; // fill viewport width – avoid horizontal scrollbar
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false; // allow vertical scrolling
        }
    }
}
