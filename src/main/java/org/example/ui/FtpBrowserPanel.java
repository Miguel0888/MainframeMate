package org.example.ui;

import org.apache.commons.net.ftp.FTPFile;
import org.example.ftp.FtpObserver;
import org.example.ftp.FtpService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;

public class FtpBrowserPanel extends JPanel  implements FtpObserver {

    private final FtpService ftpService;
    private final JTextField pathField;
    private final DefaultListModel<String> listModel;
    private final JList<String> fileList;

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
                    if (selected != null && selected.endsWith("/")) {
                        String newPath = pathField.getText();
                        if (!newPath.endsWith("/")) newPath += "/";
                        newPath += selected.substring(0, selected.length() - 1);
                        pathField.setText(newPath);
                        loadDirectory(newPath);
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
            pathField.setText(newPath);
            FTPFile[] files = ftpService.listDirectory(newPath);
            for (FTPFile file : files) {
                String name = file.getName();
                if (".".equals(name) || "..".equals(name)) continue;
                if (file.isDirectory()) name += "/";
                listModel.addElement(name);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Fehler beim Laden des Verzeichnisses:\n" + e.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void init() {
        ftpService.addObserver(this);
    }

    public void dispose() {
        ftpService.removeObserver(this);
    }
}
