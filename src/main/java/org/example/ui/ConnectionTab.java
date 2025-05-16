package org.example.ui;

import org.example.ftp.FtpFileBuffer;
import org.example.ftp.FtpManager;
import org.example.ftp.FtpObserver;
import org.example.util.SettingsManager;

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
                    if (selected == null || selected.endsWith("/")) return;
                    try {
                        FtpFileBuffer buffer = ftpManager.open(selected);
                        MainFrame main = (MainFrame) SwingUtilities.getWindowAncestor(mainPanel);
                        tabbedPaneManager.openFileTab(ftpManager, buffer);
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(mainPanel, "Fehler beim Öffnen:\n" + ex.getMessage(),
                                "Fehler", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        ftpManager.addObserver(this);
    }

    @Override
    public String getTitle() {
        return "📁 Verbindung";
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
            String path = getCurrentPath();
            SettingsManager.addBookmark(path);

            MainFrame main = (MainFrame) SwingUtilities.getWindowAncestor(getComponent());
            main.getBookmarkToolbar().refreshBookmarks();

            JOptionPane.showMessageDialog(getComponent(), "Bookmark gesetzt für: " + path);
        });

        JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
        closeItem.addActionListener(e -> onCloseCallback.run());

        menu.add(bookmarkItem);
        menu.add(closeItem);
        return menu;
    }
}
