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
 *   <li>Right: card-based content panel (one card per category)</li>
 *   <li>Bottom: footer with left action-buttons and right Apply/OK/Cancel</li>
 * </ul>
 */
public class OutlookStyleSettingsDialog extends JDialog {

    private final List<SettingsCategory> categories = new ArrayList<SettingsCategory>();
    private final DefaultListModel<String> listModel = new DefaultListModel<String>();
    private final JList<String> categoryList;
    private final CardLayout cardLayout = new CardLayout();
    private final ScrollableCardContainer cardContainer;
    private final JPanel leftButtonPanel;
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
        categoryList = new JList<String>(listModel);
        categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categoryList.setFixedCellHeight(26);
        categoryList.setVisibleRowCount(Math.min(12, categories.size()));
        categoryList.setBorder(new EmptyBorder(0, 0, 0, 8));

        // ---- right content (cards) ----
        cardContainer = new ScrollableCardContainer(cardLayout);
        for (SettingsCategory cat : categories) {
            cardContainer.add(cat.getComponent(), cat.getId());
        }

        JScrollPane rightScroll = new JScrollPane(cardContainer);
        rightScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        rightScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        rightScroll.getVerticalScrollBar().setUnitIncrement(16);

        // selection listener – switch card
        categoryList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int idx = categoryList.getSelectedIndex();
            if (idx >= 0 && idx < OutlookStyleSettingsDialog.this.categories.size()) {
                cardLayout.show(cardContainer, OutlookStyleSettingsDialog.this.categories.get(idx).getId());
            }
        });

        JScrollPane leftScroll = new JScrollPane(categoryList);
        leftScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        leftScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // ---- split pane ----
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightScroll);
        splitPane.setResizeWeight(0.25);
        splitPane.setContinuousLayout(true);

        // ---- footer ----
        JPanel footer = new JPanel(new BorderLayout());

        leftButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
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
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(10, 12, 10, 12));
        root.add(Box.createVerticalStrut(8), BorderLayout.NORTH);
        root.add(splitPane, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);

        // ---- sizing ----
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int w = clamp((int) (screen.width * 0.9), 900, 1400);
        int h = clamp((int) (screen.height * 0.9), 600, 1000);
        setSize(w, h);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

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

    private boolean doApply() {
        // Validate all categories first
        for (SettingsCategory cat : categories) {
            try {
                cat.validate();
            } catch (IllegalArgumentException ex) {
                // Select the offending category
                int idx = categories.indexOf(cat);
                if (idx >= 0) categoryList.setSelectedIndex(idx);
                JOptionPane.showMessageDialog(this,
                        ex.getMessage(),
                        "Validierungsfehler",
                        JOptionPane.WARNING_MESSAGE);
                return false;
            }
        }
        // Apply all
        for (SettingsCategory cat : categories) {
            cat.apply();
        }
        applied = true;
        return true;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---- Scrollable card container ----

    /**
     * A JPanel with CardLayout that implements Scrollable so that it
     * tracks the viewport width (no horizontal scrollbar) but allows
     * vertical scrolling.
     */
    private static final class ScrollableCardContainer extends JPanel implements Scrollable {

        ScrollableCardContainer(CardLayout layout) {
            super(layout);
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
            return true; // stretch to viewport width – avoid horizontal scrollbar
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false; // allow vertical scrolling
        }
    }
}
