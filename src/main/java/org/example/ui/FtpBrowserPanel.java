package org.example.ui;

import org.apache.commons.net.ftp.FTPFile;
import org.example.ftp.FtpFileBuffer;
import org.example.ftp.FtpObserver;
import org.example.ftp.FtpService;
import org.example.util.SettingsManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
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
        JButton goButton = new JButton("Öffnen"); // ToDo: Should work with ENTER, too
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
                    // ToDo: Show File in new Tab
                }
            }
        });

        this.add(new JScrollPane(fileList), BorderLayout.CENTER);
    }

    void loadDirectory(String path) {
        // ToDo
    }

    @Override
    public void onDirectoryChanged(String newPath) {
        if (!ftpService.isConnected()) return;

        listModel.clear();
        fileMap.clear();
        pathField.setText(newPath);
        List<FTPFile> files = null;
        try {
            files = ftpService.listDirectory(newPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (FTPFile file : files) {
            String name = file.getName();
            if (".".equals(name) || "..".equals(name)) continue;
            if (file.isDirectory()) name += "/";
            listModel.addElement(name);
            fileMap.put(name, file);
        }
    }

    private void openFileInNewTab(FtpFileBuffer file) {

    }

    public void init() {
        ftpService.addObserver(this);
    }

    public void dispose() {
        ftpService.removeObserver(this);
    }
}
