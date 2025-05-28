package de.bund.zrb.ui;

import de.bund.zrb.model.Settings;
import de.bund.zrb.util.SettingsManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

@Deprecated
public class BookmarkToolbar extends JPanel {
    private final Consumer<String> onBookmarkClick;

    public BookmarkToolbar(Consumer<String> onBookmarkClick) {
        this.onBookmarkClick = onBookmarkClick;
        setLayout(new FlowLayout(FlowLayout.LEFT));
        setBorder(BorderFactory.createTitledBorder("Bookmarks"));

        Settings settings = SettingsManager.load();
        settings.bookmarks.forEach((path, label) -> {
            JButton button = new JButton(label);
            button.setToolTipText(path);
            button.addActionListener(e -> onBookmarkClick.accept(path));
            add(button);
        });
    }

    public void refreshBookmarks() {
        removeAll();

        Settings settings = SettingsManager.load();
        settings.bookmarks.forEach((path, label) -> {
            JButton button = new JButton(label);
            button.setToolTipText(path);
            button.addActionListener(e -> onBookmarkClick.accept(path));
            attachContextMenu(button, path);
            add(button);
        });

        revalidate();
        repaint();
    }

    public void setBookmarkForCurrentPath(Component parent, String path) {
        if (path == null || path.trim().isEmpty()) return;

        SettingsManager.addBookmark(path);
        refreshBookmarks();

//        JOptionPane.showMessageDialog(parent, "Bookmark gesetzt für: " + path);
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
