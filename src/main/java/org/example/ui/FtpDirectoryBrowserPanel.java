package org.example.ui;

import org.example.ftp.FtpFileBuffer;
import org.example.ftp.FtpDirectoryObserver;
import org.example.ftp.FtpManager;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;

public class FtpDirectoryBrowserPanel extends JPanel implements FtpDirectoryObserver {

    private final FtpManager ftpManager;
    private final JTextField pathField;

    public FtpDirectoryBrowserPanel(FtpManager ftpManager) {
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

        pathField.setText(newPath);
        List<String> files = null;
        files = ftpManager.listDirectory();
    }

    public void dispose() {
        ftpManager.removeDirectoryObserver(this);
    }

    public String getCurrentPath() {
        return pathField.getText();
    }
}
