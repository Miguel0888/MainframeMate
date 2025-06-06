package de.bund.zrb.ui.file;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.List;

public class DragAndDropImportHandler {

    private final JFrame parentFrame;

    public DragAndDropImportHandler(JFrame parentFrame) {
        this.parentFrame = parentFrame;
    }

    public void init() {
        parentFrame.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    Transferable transferable = support.getTransferable();
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                    for (File file : files) {
                        handleDroppedFile(file);
                    }
                    return true;
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(parentFrame,
                            "Fehler beim Dateiimport: " + e.getMessage(),
                            "Importfehler", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
        });
    }

    private void handleDroppedFile(File file) {
        if (!isExcelFile(file)) {
            int result = JOptionPane.showConfirmDialog(parentFrame,
                    "Die Datei '" + file.getName() + "' ist kein bekanntes Excel-Format.\n" +
                            "Unterstützte Formate sind: .xls, .xlsx, .xlsm\n\n" +
                            "Möchtest du den Import trotzdem fortsetzen?",
                    "Unbekanntes Format", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }
        new ExcelImportDialog(parentFrame, file).setVisible(true);
    }

    private boolean isExcelFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".xlsm");
    }
}
