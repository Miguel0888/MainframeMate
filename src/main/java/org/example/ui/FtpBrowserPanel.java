package org.example.ui;

import org.apache.commons.net.ftp.FTPFile;
import org.example.ftp.FtpObserver;
import org.example.ftp.FtpService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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

        try {
            listModel.clear();
            fileMap.clear();
            pathField.setText(newPath);
            FTPFile[] files = ftpService.listDirectory(newPath);
            for (FTPFile file : files) {
                String name = file.getName();
                if (".".equals(name) || "..".equals(name)) continue;
                if (file.isDirectory()) name += "/";
                listModel.addElement(name);
                fileMap.put(name, file);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Fehler beim Laden des Verzeichnisses:\n" + e.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openFileInNewTab(String filename) {
        try {
            String currentPath = pathField.getText();
            String fullPath;

            if (ftpService.isMvsMode()) {
                if (!currentPath.startsWith("'")) currentPath = "'" + currentPath;
                if (!currentPath.endsWith("'")) currentPath += "'";

                // Wenn du in einer PDS bist (kein Slash, keine Extension mit .XYZ), dann ist filename ein Member
                boolean isPds = !currentPath.contains("/") && !filename.contains(".");

                if (isPds) {
                    fullPath = currentPath.substring(0, currentPath.length() - 1) + "(" + filename + ")'";
                } else {
                    fullPath = currentPath.substring(0, currentPath.length() - 1) + "." + filename + "'";
                }
            } else {
                fullPath = currentPath + "/" + filename;
            }

            System.out.println("→ FTP-Pfad: " + fullPath);

            ftpService.getClient().setFileType(org.apache.commons.net.ftp.FTPClient.ASCII_FILE_TYPE);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            boolean ok = ftpService.getClient().retrieveFile(fullPath, out);

            if (!ok) {
                int code = ftpService.getClient().getReplyCode();
                String reply = ftpService.getClient().getReplyString();
                System.err.println("Download fehlgeschlagen: " + fullPath);
                System.err.println("FTP Reply Code: " + code);
                System.err.println("FTP Reply Text: " + reply);
                throw new IOException("Download fehlgeschlagen für: " + fullPath + "\nFTP-Code: " + code + "\nAntwort: " + reply);
            }

            String content = out.toString(StandardCharsets.UTF_8.name());

            JTextArea textArea = new JTextArea(content);
            textArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(800, 600));

            JTabbedPane parentTabs = findParentTabbedPane();
            if (parentTabs != null) {
                parentTabs.addTab("📄 " + filename, scrollPane);
                parentTabs.setSelectedComponent(scrollPane);
            } else {
                JOptionPane.showMessageDialog(this, scrollPane, "📄 " + filename, JOptionPane.PLAIN_MESSAGE);
            }
        } catch (IOException e) {
            e.printStackTrace(); // für Konsole
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
