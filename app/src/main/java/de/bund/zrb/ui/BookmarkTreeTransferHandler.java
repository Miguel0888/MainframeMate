package de.bund.zrb.ui;

import de.bund.zrb.model.BookmarkEntry;
import de.bund.zrb.util.BookmarkManagerImpl;

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
        if (!support.isDataFlavorSupported(BOOKMARK_FLAVOR)) return false;

        try {
            // Hole die zu verschiebende Entry
            BookmarkEntry moved = (BookmarkEntry)
                    support.getTransferable().getTransferData(BOOKMARK_FLAVOR);
            if (moved == null) return false;

            TreePath dropPath = ((JTree.DropLocation) support.getDropLocation()).getPath();
            if (dropPath == null) return false;

            DefaultMutableTreeNode dropNode = (DefaultMutableTreeNode) dropPath.getLastPathComponent();
            Object userObj = dropNode.getUserObject();
            if (!(userObj instanceof BookmarkEntry)) return false;

            BookmarkEntry target = (BookmarkEntry) userObj;

            // ❌ Blockiere Drop eines Folders auf einem Bookmark
            boolean isMovingFolder = moved.folder;
            boolean isDroppingOnFile = !target.folder;
            if (isMovingFolder && isDroppingOnFile) return false;

            // ❌ Verhindere Drop in eigene Substruktur
            if (isDescendant(moved, target)) return false;

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) return false;

        try {
            BookmarkEntry moved = (BookmarkEntry)
                    support.getTransferable().getTransferData(BOOKMARK_FLAVOR);
            if (moved == null) return false;

            JTree.DropLocation dropLocation = (JTree.DropLocation) support.getDropLocation();
            TreePath dropPath = dropLocation.getPath();
            if (dropPath == null) return false;

            DefaultMutableTreeNode dropNode = (DefaultMutableTreeNode) dropPath.getLastPathComponent();
            BookmarkEntry target = (BookmarkEntry) dropNode.getUserObject();

            boolean dropOnNode = dropLocation.getChildIndex() == -1;
            if (dropOnNode) {
                BookmarkManagerImpl.moveBookmarkTo(moved.id, target.id);
            } else {
                BookmarkManagerImpl.moveBookmarkTo(moved.id, target.id, dropLocation.getChildIndex());
            }
            SwingUtilities.invokeLater(() -> bookmarkDrawer.refreshBookmarks());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean isDescendant(BookmarkEntry parent, BookmarkEntry potentialChild) {
        if (parent == null || potentialChild == null || parent.children == null) return false;
        for (BookmarkEntry child : parent.children) {
            if (child.id.equals(potentialChild.id)) return true;
            if (isDescendant(child, potentialChild)) return true;
        }
        return false;
    }



}

