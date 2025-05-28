package de.bund.zrb.ui;

import de.bund.zrb.model.BookmarkEntry;
import de.bund.zrb.util.BookmarkManager;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public class BookmarkDrawer extends JPanel {

    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final Consumer<String> onBookmarkClick;

    public BookmarkDrawer(Consumer<String> onBookmarkClick) {
        this.onBookmarkClick = onBookmarkClick;

        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(220, 0));
        setBorder(BorderFactory.createTitledBorder("📁 Bookmarks"));

        rootNode = new DefaultMutableTreeNode("Bookmarks");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);

        tree.setCellRenderer(new BookmarkTreeCellRenderer());

        JScrollPane scrollPane = new JScrollPane(tree);
        add(scrollPane, BorderLayout.CENTER);

        installMouseHandler();
        refreshBookmarks();
    }

    public void refreshBookmarks() {
        rootNode.removeAllChildren();
        List<BookmarkEntry> entries = BookmarkManager.loadBookmarks();
        for (BookmarkEntry entry : entries) {
            rootNode.add(createNode(entry));
        }
        treeModel.reload();
        expandAll();
    }

    public void setBookmarkForCurrentPath(Component parent, String path) {
        String label = new File(path).getName();
        BookmarkManager.addBookmark(new BookmarkEntry(label, path, false));
        refreshBookmarks();
    }

    private void installMouseHandler() {
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
                if (selPath == null) return;

                Object nodeObj = ((DefaultMutableTreeNode) selPath.getLastPathComponent()).getUserObject();
                if (!(nodeObj instanceof BookmarkEntry)) return;

                BookmarkEntry entry = (BookmarkEntry) nodeObj;

                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2 && entry.isLeaf()) {
                    onBookmarkClick.accept(entry.path);
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    showContextMenu(e.getComponent(), e.getX(), e.getY(), entry);
                }
            }
        });
    }

    private void showContextMenu(Component invoker, int x, int y, BookmarkEntry entry) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem renameItem = new JMenuItem("✏️ Umbenennen");
        renameItem.addActionListener(e -> {
            String newLabel = JOptionPane.showInputDialog(invoker, "Neuer Name:", entry.label);
            if (newLabel != null && !newLabel.trim().isEmpty()) {
                BookmarkManager.renameBookmark(entry.path, newLabel.trim());
                refreshBookmarks();
            }
        });
        menu.add(renameItem);

        JMenuItem deleteItem = new JMenuItem("❌ Entfernen");
        deleteItem.addActionListener(e -> {
            BookmarkManager.removeBookmarkByPath(entry.path);
            refreshBookmarks();
        });
        menu.add(deleteItem);

        menu.show(invoker, x, y);
    }

    private DefaultMutableTreeNode createNode(BookmarkEntry entry) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(entry);
        if (entry.folder && entry.children != null) {
            for (BookmarkEntry child : entry.children) {
                node.add(createNode(child));
            }
        }
        return node;
    }

    private void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private static class BookmarkTreeCellRenderer extends DefaultTreeCellRenderer {
        private final Icon folderIcon = UIManager.getIcon("FileView.directoryIcon");
        private final Icon fileIcon = UIManager.getIcon("FileView.fileIcon");

        @Override
        public Component getTreeCellRendererComponent(
                JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            Component comp = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObj = node.getUserObject();

            if (userObj instanceof BookmarkEntry) {
                BookmarkEntry entry = (BookmarkEntry) userObj;
                setText(entry.label);
                setToolTipText(entry.path);
                setIcon(entry.folder ? folderIcon : fileIcon);
            }

            return comp;
        }
    }
}
