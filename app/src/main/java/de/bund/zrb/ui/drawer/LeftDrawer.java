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
    private final Consumer<BookmarkEntry> onBookmarkOpen;

    private final JTabbedPane tabbedPane;
    private final JPanel bookmarkPanel;

    // ── Relations tab ──
    private final JTree relationsTree;
    private final DefaultTreeModel relationsModel;
    private final DefaultMutableTreeNode relationsRoot;
    private final JPanel relationsPanel;
    private final JLabel relationsStatusLabel;

    /** Callback for opening a relation target (e.g. wiki link). */
    private Consumer<RelationEntry> onRelationOpen;

    public LeftDrawer(Consumer<BookmarkEntry> onBookmarkOpen) {
        this.onBookmarkOpen = onBookmarkOpen;

        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(220, 0));

        // ═══════════════════════════════════════════════
        //  Tabbed pane (non-closable tabs, like RightDrawer)
        // ═══════════════════════════════════════════════
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);

        // ── Tab 1: Bookmarks ──
        bookmarkPanel = new JPanel(new BorderLayout());

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

        bookmarkPanel.add(new JScrollPane(tree), BorderLayout.CENTER);
        installMouseHandler();

        tabbedPane.addTab("📁 Bookmarks", bookmarkPanel);

        // ── Tab 2: Relations ──
        relationsPanel = new JPanel(new BorderLayout());
        relationsRoot = new DefaultMutableTreeNode("Beziehungen");
        relationsModel = new DefaultTreeModel(relationsRoot);
        relationsTree = new JTree(relationsModel);
        relationsTree.setRootVisible(false);
        relationsTree.setShowsRootHandles(true);

        relationsTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = relationsTree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        if (node.getUserObject() instanceof RelationEntry) {
                            RelationEntry entry = (RelationEntry) node.getUserObject();
                            if (onRelationOpen != null) {
                                onRelationOpen.accept(entry);
                            }
                        }
                    }
                }
            }
        });

        relationsStatusLabel = new JLabel(" ");
        relationsStatusLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        relationsStatusLabel.setFont(relationsStatusLabel.getFont().deriveFont(Font.ITALIC, 11f));

        relationsPanel.add(new JScrollPane(relationsTree), BorderLayout.CENTER);
        relationsPanel.add(relationsStatusLabel, BorderLayout.SOUTH);

        tabbedPane.addTab("🔗 Beziehungen", relationsPanel);

        add(tabbedPane, BorderLayout.CENTER);

        refreshBookmarks();
    }

    // ═══════════════════════════════════════════════════════════
    //  Relations API (called by TabbedPaneManager on tab switch)
    // ═══════════════════════════════════════════════════════════

    /**
     * Update the relations tree with the given entries.
     * @param sectionLabel root label, e.g. "Wiki-Links" or "Dependencies"
     * @param entries      list of relation entries to display
     */
    public void updateRelations(String sectionLabel, List<RelationEntry> entries) {
        relationsRoot.removeAllChildren();

        if (entries == null || entries.isEmpty()) {
            relationsRoot.add(new DefaultMutableTreeNode("(keine)"));
        } else {
            for (RelationEntry entry : entries) {
                relationsRoot.add(new DefaultMutableTreeNode(entry));
            }
        }

        relationsModel.reload();
        relationsStatusLabel.setText(entries != null ? entries.size() + " Beziehungen" : " ");

        // Expand all
        for (int i = 0; i < relationsTree.getRowCount(); i++) {
            relationsTree.expandRow(i);
        }
    }

    /**
     * Show a placeholder message (e.g. for program tabs where dependencies aren't implemented yet).
     */
    public void showRelationsPlaceholder(String message) {
        relationsRoot.removeAllChildren();
        relationsRoot.add(new DefaultMutableTreeNode(message));
        relationsModel.reload();
        relationsStatusLabel.setText(" ");
    }

    /**
     * Show a loading indicator in the relations tree.
     */
    public void showRelationsLoading() {
        relationsRoot.removeAllChildren();
        relationsRoot.add(new DefaultMutableTreeNode("⏳ Lade Beziehungen…"));
        relationsModel.reload();
        relationsStatusLabel.setText(" ");
    }

    /**
     * Clear relations (no tab selected).
     */
    public void clearRelations() {
        relationsRoot.removeAllChildren();
        relationsModel.reload();
        relationsStatusLabel.setText(" ");
    }

    public void setOnRelationOpen(Consumer<RelationEntry> callback) {
        this.onRelationOpen = callback;
    }

    // ═══════════════════════════════════════════════════════════
    //  Bookmark API (unchanged)
    // ═══════════════════════════════════════════════════════════

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
        setBookmarkForCurrentPath(parent, path, backendType, "FILE");
    }

    public void setBookmarkForCurrentPath(Component parent, String path, String backendType, String resourceKind) {
        if (path == null || path.trim().isEmpty()) return;
        String prefixedPath = BookmarkEntry.buildPath(backendType, path);
        String label = new File(path).getName();
        if (label.isEmpty()) label = path;
        ensureGeneralFolder();
        BookmarkEntry entry = new BookmarkEntry(label, prefixedPath, false);
        entry.resourceKind = resourceKind != null ? resourceKind : "FILE";
        BookmarkHelper.addBookmarkToFolder("Allgemein", entry);
        refreshBookmarks();
    }

    public boolean isBookmarked(String rawPath, String backendType) {
        if (rawPath == null) return false;
        String prefixedPath = BookmarkEntry.buildPath(backendType, rawPath);
        return isBookmarkedRecursive(BookmarkHelper.loadBookmarks(), prefixedPath);
    }

    public boolean toggleBookmark(String rawPath, String backendType) {
        return toggleBookmark(rawPath, backendType, "FILE");
    }

    public boolean toggleBookmark(String rawPath, String backendType, String resourceKind) {
        return toggleBookmark(rawPath, backendType, resourceKind, null);
    }

    public boolean toggleBookmark(String rawPath, String backendType, String resourceKind,
                                  de.bund.zrb.ui.NdvResourceState ndvState) {
        return toggleBookmark(rawPath, backendType, resourceKind, ndvState, null);
    }

    public boolean toggleBookmark(String rawPath, String backendType, String resourceKind,
                                  de.bund.zrb.ui.NdvResourceState ndvState, String tn3270MacroSteps) {
        if (rawPath == null) return false;
        String prefixedPath = BookmarkEntry.buildPath(backendType, rawPath);
        if (isBookmarkedRecursive(BookmarkHelper.loadBookmarks(), prefixedPath)) {
            BookmarkHelper.removeBookmarkByPath(prefixedPath);
            refreshBookmarks();
            return false;
        } else {
            String label = new java.io.File(rawPath).getName();
            if (label.isEmpty()) label = rawPath;
            ensureGeneralFolder();
            BookmarkEntry entry = new BookmarkEntry(label, prefixedPath, false);
            entry.resourceKind = resourceKind != null ? resourceKind : "FILE";
            if (ndvState != null && "FILE".equals(resourceKind)) {
                de.bund.zrb.ndv.NdvObjectInfo obj = ndvState.getObjectInfo();
                if (obj != null) {
                    entry.ndvLibrary = ndvState.getLibrary();
                    entry.ndvObjectName = obj.getName();
                    entry.ndvObjectType = obj.getType();
                    entry.ndvTypeExtension = obj.getTypeExtension();
                    entry.ndvDbid = obj.getDatabaseId();
                    entry.ndvFnr = obj.getFileNumber();
                }
            }
            if (tn3270MacroSteps != null && !tn3270MacroSteps.isEmpty()) {
                entry.tn3270MacroSteps = tn3270MacroSteps;
            }
            BookmarkHelper.addBookmarkToFolder("Allgemein", entry);
            refreshBookmarks();
            return true;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Internal
    // ═══════════════════════════════════════════════════════════

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
                        showContextMenu(e.getComponent(), e.getX(), e.getY(), null);
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
                            onBookmarkOpen.accept(entry);
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

        if (entry != null && !entry.folder) {
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

    // ═══════════════════════════════════════════════════════════
    //  Relation entry model
    // ═══════════════════════════════════════════════════════════

    /**
     * A single relation entry displayed in the relations tree.
     * Used for wiki links, and later for program dependencies.
     */
    public static class RelationEntry {
        private final String label;
        private final String targetPath;  // e.g. "wiki://wikipedia_de/Seite" or later "ndv://LIB/OBJ"
        private final String type;        // "WIKI_LINK", "DEPENDENCY", etc.

        public RelationEntry(String label, String targetPath, String type) {
            this.label = label;
            this.targetPath = targetPath;
            this.type = type;
        }

        public String getLabel() { return label; }
        public String getTargetPath() { return targetPath; }
        public String getType() { return type; }

        @Override
        public String toString() {
            return label;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Renderers
    // ═══════════════════════════════════════════════════════════

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
