package org.example.ui;

import org.apache.commons.net.ftp.FTPFile;
import org.example.ftp.FtpService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;

public class FtpBrowserPanel extends JPanel {

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

    public void loadInitialDirectory() {
        pathField.setText("/");
        loadDirectory("/");
    }

    void loadDirectory(String path) {
        if (!ftpService.isConnected()) {
            JOptionPane.showMessageDialog(this, "Nicht verbunden!", "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            listModel.clear();
            FTPFile[] files = ftpService.listDirectory(path);
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
}
