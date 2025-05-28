package de.bund.zrb.ui;

import de.bund.zrb.model.BookmarkEntry;
import de.bund.zrb.util.BookmarkManager;

import javax.activation.DataHandler;
import javax.swing.*;
import javax.swing.tree.*;
import java.awt.datatransfer.*;

public class BookmarkTreeTransferHandler extends TransferHandler {
    private static final DataFlavor BOOKMARK_FLAVOR =
            new DataFlavor(BookmarkEntry.class, "application/x-bookmark-entry");

    private final DataFlavor nodeFlavor;
    private DefaultMutableTreeNode draggedNode;
    private BookmarkDrawer bookmarkDrawer;

    public BookmarkTreeTransferHandler(BookmarkDrawer bookmarkDrawer) {
        this.bookmarkDrawer = bookmarkDrawer;
        nodeFlavor = new DataFlavor(DefaultMutableTreeNode.class, "TreeNode");
    }

    @Override
    public int getSourceActions(JComponent c) {
        return MOVE;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        JTree tree = (JTree) c;
        TreePath path = tree.getSelectionPath();
        if (path == null) return null;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        BookmarkEntry entry = (BookmarkEntry) node.getUserObject();

        return new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{BOOKMARK_FLAVOR};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return BOOKMARK_FLAVOR.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
                return entry;
            }
        };
    }

    @Override
    public boolean canImport(TransferSupport support) {
        if (!support.isDrop()) return false;

        // Akzeptiere nur unseren Bookmark-Flavor
        return support.isDataFlavorSupported(BOOKMARK_FLAVOR);
    }


    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) return false;

        try {
            BookmarkEntry moved = (BookmarkEntry)
                    support.getTransferable().getTransferData(BOOKMARK_FLAVOR);

            if (moved == null) return false;

            TreePath dropPath = ((JTree.DropLocation) support.getDropLocation()).getPath();
            DefaultMutableTreeNode dropNode = (DefaultMutableTreeNode) dropPath.getLastPathComponent();

            BookmarkEntry target = (BookmarkEntry) dropNode.getUserObject();
            if (moved == target || (target != null && !target.folder)) {
                return false;
            }

            String targetPath = (target != null && target.folder) ? target.path : null;
            int insertIndex = ((JTree.DropLocation) support.getDropLocation()).getChildIndex();
            if (insertIndex < 0) insertIndex = dropNode.getChildCount();

            BookmarkManager.moveBookmarkTo(moved.path, targetPath, insertIndex);
            bookmarkDrawer.refreshBookmarks();
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}

