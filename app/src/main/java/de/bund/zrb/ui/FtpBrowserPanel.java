package de.bund.zrb.ui;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.impl.factory.FileServiceFactory;
import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.ui.browser.BrowserSessionState;
import de.bund.zrb.ui.browser.PathNavigator;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Legacy panel that was previously CWD/observer driven.
 * Now migrated to stateless-by-path browsing.
 */
public class FtpBrowserPanel extends JPanel {

    private final FtpManager ftpManager;
    private final FileService fileService;
    private final BrowserSessionState browserState;
    private final PathNavigator navigator;

    private final JTextField pathField;
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> fileList = new JList<>(listModel);
    private List<FileNode> currentNodes = new ArrayList<>();

    public FtpBrowserPanel(FtpManager ftpManager) {
        this.ftpManager = ftpManager;
        this.setLayout(new BorderLayout());

        de.bund.zrb.model.Settings s = de.bund.zrb.helper.SettingsHelper.load();
        String host = s.host;
        String user = s.user;
        String password = de.bund.zrb.login.LoginManager.getInstance().getPassword(host, user);

        try {
            this.fileService = new FileServiceFactory().createFtp(host, user, password);
        } catch (FileServiceException e) {
            throw new RuntimeException("Konnte FileService nicht initialisieren", e);
        }

        this.navigator = new PathNavigator(ftpManager.isMvsMode());
        this.browserState = new BrowserSessionState(navigator.normalize("/"));

        // Pfadfeld + Button
        pathField = new JTextField(browserState.getCurrentPath());
        JButton goButton = new JButton("Öffnen");
        goButton.addActionListener(e -> loadDirectory(pathField.getText()));
        pathField.addActionListener(e -> loadDirectory(pathField.getText()));

        JPanel pathPanel = new JPanel(new BorderLayout());
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(goButton, BorderLayout.EAST);
        this.add(pathPanel, BorderLayout.NORTH);

        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() != 2) return;
                String selected = fileList.getSelectedValue();
                if (selected == null) return;

                FileNode node = findNodeByName(selected);
                if (node != null && node.isDirectory()) {
                    loadDirectory(node.getPath());
                }
            }
        });

        this.add(new JScrollPane(fileList), BorderLayout.CENTER);

        // initial load
        refresh();
    }

    void loadDirectory(String path) {
        browserState.goTo(navigator.normalize(path));
        pathField.setText(browserState.getCurrentPath());
        refresh();
    }

    private void refresh() {
        try {
            currentNodes = fileService.list(browserState.getCurrentPath());
            listModel.clear();
            if (currentNodes != null) {
                for (FileNode n : currentNodes) {
                    listModel.addElement(n.getName());
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Fehler beim Laden:\n" + e.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    public String getCurrentPath() {
        return pathField.getText();
    }

    private FileNode findNodeByName(String name) {
        if (name == null) return null;
        for (FileNode n : currentNodes) {
            if (name.equals(n.getName())) {
                return n;
            }
        }
        return null;
    }

    public void init() {
        // no observers anymore
    }

    public void dispose() {
        try {
            fileService.close();
        } catch (Exception ignore) {
            // ignore
        }
    }
}
