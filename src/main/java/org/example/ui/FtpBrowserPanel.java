package org.example.ui;

import org.apache.commons.net.ftp.FTPFile;
import org.example.ftp.FtpObserver;
import org.example.ftp.FtpService;
import org.example.util.SettingsManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FtpBrowserPanel extends JPanel implements FtpObserver {

    private final FtpService ftpService;
    private final JTextField pathField;
    private final DefaultListModel<String> listModel;
    private final JList<String> fileList;
    private final Map<String, FTPFile> fileMap = new HashMap<>();

    public FtpBrowserPanel(FtpService ftpService) {
        this.ftpService = ftpService;
        this.setLayout(new BorderLayout());

        // Pfadfeld + Button
        pathField = new JTextField("/");
        JButton goButton = new JButton("Öffnen");
        goButton.addActionListener(e -> loadDirectory(pathField.getText()));

        JPanel pathPanel = new JPanel(new BorderLayout());
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(goButton, BorderLayout.EAST);
        this.add(pathPanel, BorderLayout.NORTH);

        // Dateiliste
        listModel = new DefaultListModel<>();
        fileList = new JList<>(listModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = fileList.getSelectedValue();
                    if (selected == null) return;

                    FTPFile file = fileMap.get(selected);
                    if (file == null) return;

                    if (file.isDirectory()) {
                        String newPath = pathField.getText();
                        if (!newPath.endsWith("/")) newPath += "/";
                        newPath += selected.substring(0, selected.length() - 1);
                        pathField.setText(newPath);
                        loadDirectory(newPath);
                    } else {
                        openFileInNewTab(file.getName());
                    }
                }
            }
        });

        this.add(new JScrollPane(fileList), BorderLayout.CENTER);
    }

    void loadDirectory(String path) {
        try {
            boolean success = ftpService.changeDirectory(path);
            if (!success) {
                JOptionPane.showMessageDialog(this, "Verzeichnis nicht gefunden: " + path,
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Fehler beim Wechseln des Verzeichnisses:\n" + e.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void onDirectoryChanged(String newPath) {
        if (!ftpService.isConnected()) return;

        listModel.clear();
        fileMap.clear();
        pathField.setText(newPath);
        List<FTPFile> files = ftpService.listDirectory(newPath);
        for (FTPFile file : files) {
            String name = file.getName();
            if (".".equals(name) || "..".equals(name)) continue;
            if (file.isDirectory()) name += "/";
            listModel.addElement(name);
            fileMap.put(name, file);
        }
    }

    private void openFileInNewTab(String filename) {
        try {
            String currentPath = pathField.getText();
            String fullPath;

            if (ftpService.isMvsMode()) {
                if (!currentPath.startsWith("'")) currentPath = "'" + currentPath;
                if (!currentPath.endsWith("'")) currentPath += "'";

                boolean isPds = !currentPath.contains("/") && !filename.contains(".");
                if (isPds) {
                    fullPath = currentPath.substring(0, currentPath.length() - 1) + "(" + filename + ")'";
                } else {
                    fullPath = currentPath.substring(0, currentPath.length() - 1) + "." + filename + "'";
                }
            } else {
                fullPath = currentPath + "/" + filename;
            }

            String content = ftpService.getFile(fullPath);

            JTextArea textArea = new JTextArea(content);
            textArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(800, 600));

            JTabbedPane parentTabs = findParentTabbedPane();
            if (parentTabs != null) {
                String tabTitle = "📄 " + filename;
                JPanel tabPanel = new JPanel(new BorderLayout());
                tabPanel.add(scrollPane, BorderLayout.CENTER);

                // Tab mit Schließen- und Bookmark-Button
                JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                tabHeader.setOpaque(false);
                JLabel titleLabel = new JLabel(tabTitle);

                JButton bookmarkButton = new JButton("☆");
                bookmarkButton.setMargin(new Insets(0, 4, 0, 4));
                bookmarkButton.setFocusable(false);
                bookmarkButton.addActionListener(e -> {
                    String bookmarkPath;

                    if (ftpService.isMvsMode()) {
                        String base = pathField.getText();
                        if (!base.startsWith("'")) base = "'" + base;
                        if (!base.endsWith("'")) base += "'";
                        String member = filename.replaceAll("\\.\\w+$", ""); // optional .txt entfernen
                        bookmarkPath = base.substring(0, base.length() - 1) + "(" + member + ")'";
                    } else {
                        bookmarkPath = pathField.getText() + "/" + filename;
                    }

                    SettingsManager.addBookmark(bookmarkPath);
                    MainFrame main = (MainFrame) SwingUtilities.getWindowAncestor(this);
                    main.getBookmarkToolbar().refreshBookmarks();

                    JOptionPane.showMessageDialog(this, "Bookmark gesetzt für: " + bookmarkPath);
                });

                JButton closeButton = new JButton("x");
                closeButton.setMargin(new Insets(0, 4, 0, 4));
                closeButton.setFocusable(false);
                closeButton.addActionListener(e -> parentTabs.remove(tabPanel));

                tabHeader.add(titleLabel);
                tabHeader.add(bookmarkButton);
                tabHeader.add(closeButton);

                parentTabs.addTab(null, tabPanel);
                int index = parentTabs.indexOfComponent(tabPanel);
                parentTabs.setTabComponentAt(index, tabHeader);
                parentTabs.setSelectedComponent(tabPanel);
            } else {
                JOptionPane.showMessageDialog(this, scrollPane, "📄 " + filename, JOptionPane.PLAIN_MESSAGE);
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Fehler beim Öffnen der Datei:\n" + e.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JTabbedPane findParentTabbedPane() {
        Container parent = this.getParent();
        while (parent != null) {
            if (parent instanceof JTabbedPane) {
                return (JTabbedPane) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    public void init() {
        ftpService.addObserver(this);
    }

    public void dispose() {
        ftpService.removeObserver(this);
    }
}
