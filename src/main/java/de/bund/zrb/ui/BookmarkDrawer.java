package de.bund.zrb.ui;

import de.bund.zrb.model.Settings;
import de.bund.zrb.util.SettingsManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.function.Consumer;

public class BookmarkDrawer extends JPanel {

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> bookmarkList = new JList<>(listModel);
    private final Consumer<String> onBookmarkClick;

    public BookmarkDrawer(Consumer<String> onBookmarkClick) {
        this.onBookmarkClick = onBookmarkClick;

        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(220, 0));
        setBorder(BorderFactory.createTitledBorder("📁 Bookmarks"));

        bookmarkList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bookmarkList.setCellRenderer(new BookmarkCellRenderer());

        JScrollPane scrollPane = new JScrollPane(bookmarkList);
        add(scrollPane, BorderLayout.CENTER);

        installMouseListener();
        refreshBookmarks();
    }

    public void refreshBookmarks() {
        listModel.clear();
        Settings settings = SettingsManager.load();
        settings.bookmarks.forEach((path, label) -> listModel.addElement(label + " ::: " + path));
    }

    public void setBookmarkForCurrentPath(Component parent, String path) {
        if (path == null || path.trim().isEmpty()) return;

        SettingsManager.addBookmark(path);
        refreshBookmarks();

//        JOptionPane.showMessageDialog(parent, "Bookmark gesetzt für: " + path);
    }

    private void installMouseListener() {
        bookmarkList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = bookmarkList.locationToIndex(e.getPoint());
                if (index < 0) return;

                String value = listModel.get(index);
                String path = extractPath(value);

                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                    onBookmarkClick.accept(path);
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    showContextMenu(e.getComponent(), e.getX(), e.getY(), path);
                }
            }
        });
    }

    private void showContextMenu(Component invoker, int x, int y, String path) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem removeItem = new JMenuItem("❌ Entfernen");
        removeItem.addActionListener(e -> {
            SettingsManager.removeBookmark(path);
            refreshBookmarks();
        });
        menu.add(removeItem);
        menu.show(invoker, x, y);
    }

    private String extractPath(String value) {
        int sep = value.lastIndexOf(" ::: ");
        return (sep >= 0) ? value.substring(sep + 5) : value;
    }

    private static class BookmarkCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {

            String displayText = String.valueOf(value);
            int sep = displayText.lastIndexOf(" ::: ");
            String label = (sep >= 0) ? displayText.substring(0, sep) : displayText;

            JLabel labelComponent = (JLabel) super.getListCellRendererComponent(
                    list, label, index, isSelected, cellHasFocus);
            labelComponent.setToolTipText(displayText.substring(sep + 5));
            return labelComponent;
        }
    }
}
