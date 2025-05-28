package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.ftp.FtpObserver;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.List;

public class ConnectionTab implements FtpTab, FtpObserver {

    private final FtpManager ftpManager;
    private final JPanel mainPanel;
    private final JTextField pathField = new JTextField();
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> fileList = new JList<>(listModel);
    private final TabbedPaneManager tabbedPaneManager;

    public ConnectionTab(FtpManager ftpManager, TabbedPaneManager tabbedPaneManager) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.ftpManager = ftpManager;
        this.mainPanel = new JPanel(new BorderLayout());

        JPanel pathPanel = new JPanel(new BorderLayout());
        JButton goButton = new JButton("Öffnen");
        goButton.addActionListener(e -> loadDirectory(pathField.getText()));
        pathField.addActionListener(e -> loadDirectory(pathField.getText()));
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(goButton, BorderLayout.EAST);

        mainPanel.add(pathPanel, BorderLayout.NORTH);
        mainPanel.add(new JScrollPane(fileList), BorderLayout.CENTER);

        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = fileList.getSelectedValue();
                    if (selected == null) return;
                    try {
                        FtpFileBuffer buffer = ftpManager.open(selected);
                        if( buffer != null) // no DIR
                        {
                            tabbedPaneManager.openFileTab(ftpManager, buffer);
                        }
                        else
                        {
                            pathField.setText(ftpManager.getCurrentPath());
                            updateFileList();
                        }
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(mainPanel, "Fehler beim Öffnen:\n" + ex.getMessage(),
                                "Fehler", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        mainPanel.add(createStatusBar(), BorderLayout.SOUTH);

        ftpManager.addObserver(this);
    }

    @Override
    public String getTitle() {
        return "📁 Verbindung";
    }

    @Override
    public String getTooltip() {
        return "";
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void onClose() {
        ftpManager.removeObserver(this);
    }

    @Override
    public void saveIfApplicable() {
        // nichts zu speichern
    }

    public String getCurrentPath() {
        return pathField.getText();
    }

    @Override
    public void onDirectoryChanged(String newPath) {
        pathField.setText(newPath);
        updateFileList();
    }

    public void loadDirectory(String path) {
        try {
            if (ftpManager.changeDirectory(path)) {
                updateFileList();
            } else {
                JOptionPane.showMessageDialog(mainPanel, "Verzeichnis nicht gefunden: " + path,
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(mainPanel, "Fehler beim Laden:\n" + e.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateFileList() {
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            try {
                List<String> files = ftpManager.listDirectory();
                for (String file : files) {
                    listModel.addElement(file);
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(mainPanel, "Fehler beim Aktualisieren:\n" + e.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    @Override
    public JPopupMenu createContextMenu(Runnable onCloseCallback) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem bookmarkItem = new JMenuItem("🕮 Bookmark setzen");
        bookmarkItem.addActionListener(e -> {
            MainFrame main = (MainFrame) SwingUtilities.getWindowAncestor(getComponent());
            main.getBookmarkDrawer().setBookmarkForCurrentPath(getComponent(), getCurrentPath());
        });

        JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
        closeItem.addActionListener(e -> onCloseCallback.run());

        menu.add(bookmarkItem);
        menu.add(closeItem);
        return menu;
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());

        // Linker Bereich: Encoding, Neue Datei etc.
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton newFileButton = new JButton("📄");
        newFileButton.setToolTipText("Neue Datei anlegen");
        newFileButton.addActionListener(e -> createNewFile());

        JButton newPdsButton = new JButton("📁");
        newPdsButton.setToolTipText("Neues PDS anlegen");
        newPdsButton.addActionListener(e -> createNewPds());

        leftPanel.add(newFileButton);
        //leftPanel.add(newPdsButton); //ToDo: Fix, currently not working

        // Rechter Bereich: Löschen
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton deleteButton = new JButton("🗑");
        deleteButton.setToolTipText("Ausgewählte Datei löschen");
        deleteButton.addActionListener(e -> deleteSelectedEntry());
        rightPanel.add(deleteButton);

        // Einfügen
        statusBar.add(leftPanel, BorderLayout.WEST);
        statusBar.add(rightPanel, BorderLayout.EAST);

        return statusBar;
    }

    private void createNewFile() {
        String name = JOptionPane.showInputDialog(mainPanel, "Name der neuen Datei:", "Neue Datei", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;

        try {
            if (ftpManager.createEmptyFile(name)) {
                JOptionPane.showMessageDialog(mainPanel, "Datei erstellt: " + name);
                updateFileList();
            } else {
                JOptionPane.showMessageDialog(mainPanel, "Fehler beim Erstellen der Datei.", "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(mainPanel, "Fehler:\n" + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedEntry() {
        String selected = fileList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(mainPanel, "Bitte erst eine Datei auswählen.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(mainPanel, "Wirklich löschen?\n" + selected,
                "Löschen bestätigen", JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            if (ftpManager.delete(selected)) {
                JOptionPane.showMessageDialog(mainPanel, "Gelöscht: " + selected);
                updateFileList();
            } else {
                JOptionPane.showMessageDialog(mainPanel, "Löschen fehlgeschlagen!", "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(mainPanel, "Fehler beim Löschen:\n" + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createNewPds() {
        String dataset = JOptionPane.showInputDialog(mainPanel, "Name des neuen PDS (z. B. USER.TEST.PDS):", "Neues PDS", JOptionPane.PLAIN_MESSAGE);
        if (dataset == null || dataset.trim().isEmpty()) return;

        try {
            if (ftpManager.createPds(dataset)) {
                JOptionPane.showMessageDialog(mainPanel, "PDS erstellt: " + dataset);
                updateFileList();
            } else {
                JOptionPane.showMessageDialog(mainPanel, "Fehler beim Erstellen des PDS.", "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException | UnsupportedOperationException e) {
            JOptionPane.showMessageDialog(mainPanel, "Fehler:\n" + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

}
