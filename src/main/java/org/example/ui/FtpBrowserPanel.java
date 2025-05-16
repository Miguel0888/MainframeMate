package org.example.ui;

import org.example.ftp.FtpFileBuffer;
import org.example.ftp.FtpObserver;
import org.example.ftp.FtpManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.List;

public class FtpBrowserPanel extends JPanel implements FtpObserver {

    private final FtpManager ftpManager;
    private final JTextField pathField;
    private final DefaultListModel<String> listModel;
    private final JList<String> fileList;

    public FtpBrowserPanel(FtpManager ftpManager) {
        this.ftpManager = ftpManager;
        this.setLayout(new BorderLayout());

        // Pfadfeld + Button
        pathField = new JTextField("/");
        JButton goButton = new JButton("Öffnen");
        goButton.addActionListener(e -> loadDirectory(pathField.getText()));
        pathField.addActionListener(e -> loadDirectory(pathField.getText()));


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
                    if (selected == null || selected.endsWith("/")) return;

                    // Open Directory or File
                    try {
                        FtpFileBuffer buffer = ftpManager.open(selected);
                        openFileInNewTab(buffer);
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(FtpBrowserPanel.this,
                                "Fehler beim Öffnen:\n" + ex.getMessage(),
                                "Fehler", JOptionPane.ERROR_MESSAGE);
                    }

                }
            }
        });


        this.add(new JScrollPane(fileList), BorderLayout.CENTER);
    }

    void loadDirectory(String path) {
        try {
            boolean success = ftpManager.changeDirectory(path);
            if (!success) {
                JOptionPane.showMessageDialog(this, "Verzeichnis nicht gefunden: " + path,
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Fehler beim Öffnen:\n" + e.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }


    @Override
    public void onDirectoryChanged(String newPath) {
        if (!ftpManager.isConnected()) return;

        listModel.clear();
        pathField.setText(newPath);
        List<String> files = null;
        files = ftpManager.listDirectory();

        for (String file : files) {
            listModel.addElement(file);
        }
    }

    private void openFileInNewTab(FtpFileBuffer buffer) {

    }

    public void init() {
        ftpManager.addObserver(this);
    }

    public void dispose() {
        ftpManager.removeObserver(this);
    }

    public String getCurrentPath() {
        return pathField.getText();
    }
}
