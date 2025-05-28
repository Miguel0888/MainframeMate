package de.bund.zrb.ui;

import de.bund.zrb.model.Settings;
import de.bund.zrb.util.SettingsManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

public class BookmarkDrawer extends JPanel {

    private final JPanel listPanel;
    private final Consumer<String> onBookmarkClick;

    public BookmarkDrawer(Consumer<String> onBookmarkClick) {
        this.onBookmarkClick = onBookmarkClick;

        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(220, 0));
        setBorder(BorderFactory.createTitledBorder("📁 Bookmarks"));

        listPanel = new JPanel(new GridLayout(0, 1, 4, 4));
        listPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(null);

        add(scrollPane, BorderLayout.CENTER);

        refreshBookmarks();
    }

    public void refreshBookmarks() {
        listPanel.removeAll();

        Settings settings = SettingsManager.load();
        settings.bookmarks.forEach((path, label) -> {
            JButton button = new JButton(label);
            button.setToolTipText(path);
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));

            button.addActionListener(e -> onBookmarkClick.accept(path));
            attachContextMenu(button, path);

            listPanel.add(button);
        });

        revalidate();
        repaint();
    }

    public void setBookmarkForCurrentPath(Component parent, String path) {
        if (path == null || path.trim().isEmpty()) return;

        SettingsManager.addBookmark(path);
        refreshBookmarks();

        JOptionPane.showMessageDialog(parent, "Bookmark gesetzt für: " + path);
    }

    private void attachContextMenu(JButton button, String path) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem removeItem = new JMenuItem("❌ Entfernen");
        removeItem.addActionListener(e -> {
            SettingsManager.removeBookmark(path);
            refreshBookmarks();
        });

        menu.add(removeItem);

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    menu.show(button, e.getX(), e.getY());
                }
            }
        });
    }
}
