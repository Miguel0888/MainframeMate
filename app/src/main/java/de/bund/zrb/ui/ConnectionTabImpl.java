package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.ftp.FtpObserver;
import de.zrb.bund.api.Bookmarkable;
import de.zrb.bund.newApi.ui.ConnectionTab;
import de.zrb.bund.newApi.ui.FtpTab;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConnectionTabImpl implements ConnectionTab, FtpObserver {

    private final FtpManager ftpManager;
    private final JPanel mainPanel;
    private final JTextField pathField = new JTextField();
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> fileList = new JList<>(listModel);
    private final TabbedPaneManager tabbedPaneManager;

    private final JTextField searchField = new JTextField();
    private List<String> currentDirectoryFiles = new ArrayList<>();

    public ConnectionTabImpl(FtpManager ftpManager, TabbedPaneManager tabbedPaneManager) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.ftpManager = ftpManager;
        this.mainPanel = new JPanel(new BorderLayout());

        JPanel pathPanel = new JPanel(new BorderLayout());

        // 🔄 Refresh-Button links
        JButton refreshButton = new JButton("🔄");
        refreshButton.setToolTipText("Aktuellen Pfad neu laden");
        refreshButton.setMargin(new Insets(0, 0, 0, 0));
        refreshButton.setFont(refreshButton.getFont().deriveFont(Font.PLAIN, 18f));
        refreshButton.addActionListener(e -> loadDirectory(pathField.getText()));

        // ⏴ Zurück-Button rechts
        JButton backButton = new JButton("⏴");
        backButton.setToolTipText("Zurück zum übergeordneten Verzeichnis");
        backButton.setMargin(new Insets(0, 0, 0, 0));
        backButton.setFont(backButton.getFont().deriveFont(Font.PLAIN, 20f));
        backButton.addActionListener(e -> {
            try {
                if (ftpManager.changeToParentDirectory()) {
                    pathField.setText(ftpManager.getCurrentPath());
                    updateFileList();
                } else {
                    JOptionPane.showMessageDialog(mainPanel, "Kein übergeordnetes Verzeichnis vorhanden.",
                            "Hinweis", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(mainPanel, "Fehler beim Zurückwechseln:\n" + ex.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Öffnen-Button rechts
        JButton goButton = new JButton("Öffnen");
        goButton.addActionListener(e -> loadDirectory(pathField.getText()));
        pathField.addActionListener(e -> loadDirectory(pathField.getText()));

        // Rechte Buttongruppe (⏴ Öffnen)
        JPanel rightButtons = new JPanel(new GridLayout(1, 2, 0, 0));
        rightButtons.add(backButton);
        rightButtons.add(goButton);

        // Panelaufbau
        pathPanel.add(refreshButton, BorderLayout.WEST);
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(rightButtons, BorderLayout.EAST);

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
                            tabbedPaneManager.openFileTab(ftpManager, buffer, null, false);
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

    // Statusleiste mit Filterfeld in der Mitte
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
        statusBar.add(createFilterPanel(), BorderLayout.CENTER);
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

    @Override
    public String getContent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < listModel.size(); i++) {
            sb.append(listModel.get(i)).append("\n");
        }
        return sb.toString();
    }

    @Override
    public void markAsChanged() {
        // Optional: Visual indication, if ever needed
        // tabbedPaneManager.updateTitleFor(this);
    }

    @Override
    public String getPath() {
        return getCurrentPath(); // oder pathField.getText()
    }

    @Override
    public Type getType() {
        return Type.CONNECTION;
    }

    // Suchfeld ins UI einbauen
    private JPanel createFilterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        searchField.setToolTipText("Zeilenfilter: Teilstring oder Regex, groß/klein ignoriert");
        panel.add(new JLabel("🔎 ", JLabel.RIGHT), BorderLayout.WEST);
        panel.add(searchField, BorderLayout.CENTER);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void filter() {
                String query = searchField.getText().trim().toUpperCase();
                listModel.clear();
                boolean hasMatch = false;
                for (String file : currentDirectoryFiles) {
                    if (file.toUpperCase().contains(query)) {
                        listModel.addElement(file);
                        hasMatch = true;
                    }
                }
                searchField.setBackground(hasMatch || query.isEmpty()
                        ? UIManager.getColor("TextField.background")
                        : new Color(255, 200, 200));
            }
            public void insertUpdate(DocumentEvent e) { filter(); }
            public void removeUpdate(DocumentEvent e) { filter(); }
            public void changedUpdate(DocumentEvent e) { filter(); }
        });
        return panel;
    }

    // updateFileList anpassen:
    private void updateFileList() {
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            try {
                List<String> files = ftpManager.listDirectory();
                currentDirectoryFiles = files;
                for (String file : files) {
                    listModel.addElement(file);
                }
                searchField.setText(""); // Filter zurücksetzen
            } catch (Exception e) {
                JOptionPane.showMessageDialog(mainPanel, "Fehler beim Aktualisieren:\n" + e.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

}
