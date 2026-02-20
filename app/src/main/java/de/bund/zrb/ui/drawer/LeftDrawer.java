package de.bund.zrb.ui.drawer;

import de.bund.zrb.model.BookmarkEntry;
import de.bund.zrb.helper.BookmarkHelper;
import de.bund.zrb.ui.util.BookmarkTreeTransferHandler;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public class LeftDrawer extends JPanel {

    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final Consumer<String> onBookmarkClick;

    public LeftDrawer(Consumer<String> onBookmarkClick) {
        this.onBookmarkClick = onBookmarkClick;

        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(220, 0));
        setBorder(BorderFactory.createTitledBorder("📁 Bookmarks"));

        BookmarkEntry rootEntry = new BookmarkEntry("Bookmarks", null, true);
        rootNode = new DefaultMutableTreeNode(rootEntry);
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);

        tree.setCellRenderer(new BookmarkTreeCellRenderer());
        tree.setDragEnabled(true);
        tree.setDropMode(DropMode.ON_OR_INSERT);
        tree.setTransferHandler(new BookmarkTreeTransferHandler(this));

        JScrollPane scrollPane = new JScrollPane(tree);
        add(scrollPane, BorderLayout.CENTER);

        installMouseHandler();
        refreshBookmarks();
    }

    public void refreshBookmarks() {
        rootNode.removeAllChildren();
        List<BookmarkEntry> entries = BookmarkHelper.loadBookmarks();
        for (BookmarkEntry entry : entries) {
            rootNode.add(createNode(entry));
        }
        treeModel.reload();
        expandAll();
    }

    public void setBookmarkForCurrentPath(Component parent, String path) {
        setBookmarkForCurrentPath(parent, path, null);
    }

    public void setBookmarkForCurrentPath(Component parent, String path, String backendType) {
        if (path == null || path.trim().isEmpty()) return;
        String prefixedPath = BookmarkEntry.buildPath(backendType, path);
        String label = new File(path).getName();
        // Ensure "Allgemein" folder exists
        ensureGeneralFolder();
        BookmarkEntry entry = new BookmarkEntry(label, prefixedPath, false);
        BookmarkHelper.addBookmarkToFolder("Allgemein", entry);
        refreshBookmarks();
    }

    /**
     * Check if a path (raw, without protocol prefix) is bookmarked.
     */
    public boolean isBookmarked(String rawPath, String backendType) {
        if (rawPath == null) return false;
        String prefixedPath = BookmarkEntry.buildPath(backendType, rawPath);
        return isBookmarkedRecursive(BookmarkHelper.loadBookmarks(), prefixedPath);
    }

    /**
     * Toggle bookmark: add if not present, remove if already bookmarked.
     * Returns true if bookmark was added, false if removed.
     */
    public boolean toggleBookmark(String rawPath, String backendType) {
        if (rawPath == null) return false;
        String prefixedPath = BookmarkEntry.buildPath(backendType, rawPath);
        if (isBookmarkedRecursive(BookmarkHelper.loadBookmarks(), prefixedPath)) {
            BookmarkHelper.removeBookmarkByPath(prefixedPath);
            refreshBookmarks();
            return false;
        } else {
            setBookmarkForCurrentPath(null, rawPath, backendType);
            return true;
        }
    }

    private boolean isBookmarkedRecursive(List<BookmarkEntry> entries, String prefixedPath) {
        for (BookmarkEntry e : entries) {
            if (!e.folder && prefixedPath.equals(e.path)) return true;
            if (e.folder && e.children != null) {
                if (isBookmarkedRecursive(e.children, prefixedPath)) return true;
            }
        }
        return false;
    }

    private void ensureGeneralFolder() {
        List<BookmarkEntry> bookmarks = BookmarkHelper.loadBookmarks();
        for (BookmarkEntry e : bookmarks) {
            if (e.folder && "Allgemein".equals(e.label)) return;
        }
        BookmarkHelper.addBookmark(new BookmarkEntry("Allgemein", null, true));
    }

    private void installMouseHandler() {
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());

                if (SwingUtilities.isRightMouseButton(e)) {
                    if (selPath == null) {
                        showContextMenu(e.getComponent(), e.getX(), e.getY(), null); // root context
                    } else {
                        Object nodeObj = ((DefaultMutableTreeNode) selPath.getLastPathComponent()).getUserObject();
                        if (nodeObj instanceof BookmarkEntry) {
                            BookmarkEntry entry = (BookmarkEntry) nodeObj;
                            showContextMenu(e.getComponent(), e.getX(), e.getY(), entry);
                        }
                    }
                } else if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2 && selPath != null) {
                    Object nodeObj = ((DefaultMutableTreeNode) selPath.getLastPathComponent()).getUserObject();
                    if (nodeObj instanceof BookmarkEntry) {
                        BookmarkEntry entry = (BookmarkEntry) nodeObj;
                        if (entry.isLeaf()) {
                            String routingPath = entry.getRoutingPath();
                            if ("NDV".equals(entry.getBackendType())) {
                                // NDV bookmarks can't be opened via openFileOrDirectory
                                JOptionPane.showMessageDialog(tree,
                                        "NDV-Dateien bitte über NDV-Verbindung öffnen.",
                                        "Hinweis", JOptionPane.INFORMATION_MESSAGE);
                            } else {
                                onBookmarkClick.accept(routingPath);
                            }
                        }
                    }
                }
            }
        });
    }

    private void showContextMenu(Component invoker, int x, int y, BookmarkEntry entry) {
        JPopupMenu menu = new JPopupMenu();

        if (entry == null) {
            JMenuItem newFolder = new JMenuItem("📁 Neuer Ordner");
            newFolder.addActionListener(e -> {
                String name = JOptionPane.showInputDialog(invoker, "Name des neuen Ordners:");
                if (name != null && !name.trim().isEmpty()) {
                    BookmarkEntry folder = new BookmarkEntry(name.trim(), null, true);
                    BookmarkHelper.addBookmark(folder);
                    refreshBookmarks();
                }
            });
            menu.add(newFolder);
        } else {
            JMenuItem renameItem = new JMenuItem("✏ Umbenennen");
            renameItem.addActionListener(e -> {
                String newLabel = JOptionPane.showInputDialog(invoker, "Neuer Name:", entry.label);
                if (newLabel != null && !newLabel.trim().isEmpty()) {
                    if (entry.folder) {
                        BookmarkHelper.renameFolder(entry.label, newLabel.trim());
                    } else {
                        BookmarkHelper.renameBookmark(entry.path, newLabel.trim());
                    }
                    refreshBookmarks();
                }
            });
            menu.add(renameItem);

            if (entry.folder) {
                JMenuItem newSubfolderItem = new JMenuItem("📁 Neuen Unterordner anlegen");
                newSubfolderItem.addActionListener(e -> {
                    String name = JOptionPane.showInputDialog(invoker, "Name des neuen Ordners:");
                    if (name != null && !name.trim().isEmpty()) {
                        BookmarkEntry folder = new BookmarkEntry(name.trim(), null, true);
                        BookmarkHelper.addBookmarkToFolder(entry.label, folder);
                        refreshBookmarks();
                    }
                });
                menu.add(newSubfolderItem);
            }

            JMenuItem deleteItem = new JMenuItem("❌ Entfernen");
            deleteItem.addActionListener(e -> {
                if (entry.folder) {
                    BookmarkHelper.removeFolderByLabel(entry.label);
                } else {
                    BookmarkHelper.removeBookmarkByPath(entry.path);
                }
                refreshBookmarks();
            });
            menu.add(deleteItem);
        }

        if (!entry.folder) {
            JMenuItem changePathItem = new JMenuItem("🛤 Pfad ändern");
            changePathItem.addActionListener(ev -> {
                String newPath = JOptionPane.showInputDialog(invoker, "Neuer Pfad:", entry.path);
                if (newPath != null && !newPath.trim().isEmpty()) {
                    BookmarkHelper.changeBookmarkPath(entry.path, newPath.trim());
                    refreshBookmarks();
                }
            });
            menu.add(changePathItem);
        }

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
                if (entry.folder) {
                    setToolTipText(null);
                    setIcon(folderIcon);
                } else {
                    String backend = entry.getBackendType();
                    String raw = entry.getRawPath();
                    setToolTipText("[" + backend + "] " + raw);
                    setIcon(fileIcon);
                }
            }

            return comp;
        }
    }


}
