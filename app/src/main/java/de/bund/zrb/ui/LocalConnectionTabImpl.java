package de.bund.zrb.ui;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.impl.factory.FileServiceFactory;
import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.files.model.FilePayload;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class LocalConnectionTabImpl implements ConnectionTab {

    private static final int MOUSE_BACK_BUTTON = 4;
    private static final int MOUSE_FORWARD_BUTTON = 5;

    private final FileService fileService;
    private final JPanel mainPanel;
    private final JTextField pathField = new JTextField();
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> fileList = new JList<>(listModel);
    private final TabbedPaneManager tabbedPaneManager;
    private final DocumentPreviewOpener previewOpener;

    private final JTextField searchField = new JTextField();
    private List<FileNode> currentNodes = new ArrayList<>();
    private List<String> currentFiles = new ArrayList<>();
    private final List<String> backHistory = new ArrayList<>();
    private final List<String> forwardHistory = new ArrayList<>();
    private JButton backButton;
    private JButton forwardButton;

    public LocalConnectionTabImpl(TabbedPaneManager tabbedPaneManager) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.previewOpener = new DocumentPreviewOpener(tabbedPaneManager);
        this.mainPanel = new JPanel(new BorderLayout());

        this.fileService = new FileServiceFactory().createLocal();

        JPanel pathPanel = new JPanel(new BorderLayout());

        JButton refreshButton = new JButton("🔄");
        refreshButton.setToolTipText("Aktuellen Pfad neu laden");
        refreshButton.setMargin(new Insets(0, 0, 0, 0));
        refreshButton.setFont(refreshButton.getFont().deriveFont(Font.PLAIN, 18f));
        refreshButton.addActionListener(e -> loadDirectory(pathField.getText()));

        backButton = new JButton("⏴");
        backButton.setToolTipText("Zurück zum übergeordneten Verzeichnis");
        backButton.setMargin(new Insets(0, 0, 0, 0));
        backButton.setFont(backButton.getFont().deriveFont(Font.PLAIN, 20f));
        backButton.addActionListener(e -> navigateBack());

        forwardButton = new JButton("⏵");
        forwardButton.setToolTipText("Vorwärts zum nächsten Verzeichnis");
        forwardButton.setMargin(new Insets(0, 0, 0, 0));
        forwardButton.setFont(forwardButton.getFont().deriveFont(Font.PLAIN, 20f));
        forwardButton.addActionListener(e -> navigateForward());

        JButton goButton = new JButton("Öffnen");
        goButton.addActionListener(e -> loadDirectory(pathField.getText()));
        pathField.addActionListener(e -> loadDirectory(pathField.getText()));

        JPanel rightButtons = new JPanel(new GridLayout(1, 3, 0, 0));
        rightButtons.add(backButton);
        rightButtons.add(forwardButton);
        rightButtons.add(goButton);

        pathPanel.add(refreshButton, BorderLayout.WEST);
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(rightButtons, BorderLayout.EAST);

        mainPanel.add(pathPanel, BorderLayout.NORTH);
        mainPanel.add(new JScrollPane(fileList), BorderLayout.CENTER);
        mainPanel.add(createStatusBar(), BorderLayout.SOUTH);

        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        installMouseNavigation(pathField);
        installMouseNavigation(fileList);
        installMouseNavigation(mainPanel);
        fileList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() != 2) return;
                String selected = fileList.getSelectedValue();
                if (selected == null) return;

                FileNode node = findNodeByName(selected);
                if (node != null && node.isDirectory()) {
                    loadDirectory(node.getPath());
                    return;
                }

                if (node != null) {
                    // Normaler Doppelklick = Datei im Editor öffnen (mit Compare, Suche, etc.)
                    // CTRL+Doppelklick = Document Preview (gerendertes Markdown, z.B. für PDFs)
                    if (e.isControlDown()) {
                        openDocumentPreview(node.getPath(), node.getName());
                    } else {
                        openLocalFile(node.getPath());
                    }
                }
            }
        });

        loadDirectory(defaultRootPath(), false);
    }

    @Override
    public String getTitle() {
        return "🖥 Dieser Computer";
    }

    @Override
    public String getTooltip() {
        return "Lokales Dateisystem";
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void onClose() {
        try {
            fileService.close();
        } catch (Exception ignore) {
            // ignore
        }
    }

    @Override
    public void saveIfApplicable() {
        // nothing to save
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
        // not used
    }

    @Override
    public String getPath() {
        return pathField.getText();
    }

    @Override
    public Type getType() {
        return Type.CONNECTION;
    }

    @Override
    public void focusSearchField() {
        searchField.requestFocusInWindow();
        searchField.selectAll();
    }

    @Override
    public void searchFor(String searchPattern) {
        if (searchPattern == null) return;
        searchField.setText(searchPattern.trim());
        applySearchFilter();
    }

    public void loadDirectory(String path) {
        loadDirectory(path, true);
    }

    private void loadDirectory(String path, boolean addToHistory) {
        String targetPath = normalizePath(path);
        String currentPath = pathField.getText();
        if (addToHistory && !currentPath.isEmpty() && !currentPath.equals(targetPath)) {
            backHistory.add(currentPath);
            forwardHistory.clear();
        }
        pathField.setText(targetPath);
        updateNavigationButtons();
        updateFileList();
    }

    private void navigateBack() {
        if (backHistory.isEmpty()) {
            return;
        }
        String currentPath = normalizePath(pathField.getText());
        forwardHistory.add(currentPath);
        String previousPath = backHistory.remove(backHistory.size() - 1);
        pathField.setText(previousPath);
        updateNavigationButtons();
        updateFileList();
    }

    private void navigateForward() {
        if (forwardHistory.isEmpty()) {
            return;
        }
        String currentPath = normalizePath(pathField.getText());
        backHistory.add(currentPath);
        String nextPath = forwardHistory.remove(forwardHistory.size() - 1);
        pathField.setText(nextPath);
        updateNavigationButtons();
        updateFileList();
    }

    private void updateNavigationButtons() {
        if (backButton != null) {
            backButton.setEnabled(!backHistory.isEmpty());
        }
        if (forwardButton != null) {
            forwardButton.setEnabled(!forwardHistory.isEmpty());
        }
    }

    private void installMouseNavigation(JComponent component) {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MOUSE_BACK_BUTTON) {
                    navigateBack();
                } else if (e.getButton() == MOUSE_FORWARD_BUTTON) {
                    navigateForward();
                }
            }
        });
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton newFileButton = new JButton("📄");
        newFileButton.setToolTipText("Neue Datei anlegen");
        newFileButton.addActionListener(e -> createNewFile());
        leftPanel.add(newFileButton);

        JButton newFolderButton = new JButton("📁+");
        newFolderButton.setToolTipText("Neuen Ordner anlegen");
        newFolderButton.addActionListener(e -> createNewFolder());
        leftPanel.add(newFolderButton);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton deleteButton = new JButton("🗑");
        deleteButton.setToolTipText("Ausgewählte Datei löschen");
        deleteButton.addActionListener(e -> deleteSelectedEntry());
        rightPanel.add(deleteButton);

        statusBar.add(leftPanel, BorderLayout.WEST);
        statusBar.add(createFilterPanel(), BorderLayout.CENTER);
        statusBar.add(rightPanel, BorderLayout.EAST);

        return statusBar;
    }

    private JPanel createFilterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        searchField.setToolTipText("<html>Regex-Filter für Dateinamen<br>Beispiel: <code>\\.JCL$</code></html>");
        panel.add(new JLabel("🔎 ", JLabel.RIGHT), BorderLayout.WEST);
        panel.add(searchField, BorderLayout.CENTER);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applySearchFilter(); }
            public void removeUpdate(DocumentEvent e) { applySearchFilter(); }
            public void changedUpdate(DocumentEvent e) { applySearchFilter(); }
        });

        return panel;
    }

    private void applySearchFilter() {
        String regex = searchField.getText().trim();
        listModel.clear();

        boolean hasMatch = false;
        for (String file : currentFiles) {
            try {
                if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(file).find()) {
                    listModel.addElement(file);
                    hasMatch = true;
                }
            } catch (Exception e) {
                hasMatch = false;
                break;
            }
        }

        searchField.setBackground(hasMatch || regex.isEmpty()
                ? UIManager.getColor("TextField.background")
                : new Color(255, 200, 200));
    }

    private void updateFileList() {
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            try {
                List<FileNode> nodes = fileService.list(pathField.getText());
                currentNodes = nodes == null ? new ArrayList<>() : nodes;
                refreshListModelFromNodes();
                searchField.setText("");
            } catch (Exception e) {
                JOptionPane.showMessageDialog(mainPanel, "Fehler beim Aktualisieren:\n" + e.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void refreshListModelFromNodes() {
        currentFiles = new ArrayList<>();
        for (FileNode n : currentNodes) {
            currentFiles.add(n.getName());
            listModel.addElement(n.getName());
        }
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

    private void createNewFile() {
        String name = JOptionPane.showInputDialog(mainPanel, "Name der neuen Datei:", "Neue Datei", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;

        try {
            String target = joinPath(pathField.getText(), name);
            FilePayload payload = FilePayload.fromBytes(new byte[0], Charset.defaultCharset(), false);
            fileService.writeFile(target, payload);
            updateFileList();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainPanel, "Fehler:\n" + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createNewFolder() {
        String name = JOptionPane.showInputDialog(mainPanel, "Name des neuen Ordners:", "Neuer Ordner", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;

        try {
            String target = joinPath(pathField.getText(), name.trim());
            if (fileService.createDirectory(target)) {
                updateFileList();
            } else {
                JOptionPane.showMessageDialog(mainPanel, "Ordner konnte nicht angelegt werden.", "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainPanel, "Fehler beim Anlegen des Ordners:\n" + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
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
            String target = joinPath(pathField.getText(), selected);
            if (fileService.delete(target)) {
                updateFileList();
            } else {
                JOptionPane.showMessageDialog(mainPanel, "Löschen fehlgeschlagen!", "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainPanel, "Fehler beim Löschen:\n" + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openLocalFile(String path) {
        try {
            FilePayload payload = fileService.readFile(path);
            // IMPORTANT: Use getEditorText() for proper RECORD_STRUCTURE handling
            String content = payload.getEditorText();
            VirtualResource resource = new VirtualResource(de.bund.zrb.files.path.VirtualResourceRef.of(path), VirtualResourceKind.FILE, path, true);
            tabbedPaneManager.openFileTab(resource, content, null, null, false);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainPanel, "Fehler beim Öffnen:\n" + e.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openDocumentPreview(String path, String fileName) {
        previewOpener.openPreviewAsync(fileService, path, fileName, mainPanel);
    }

    private String defaultRootPath() {
        if (File.separatorChar == '\\') {
            return "C:\\";
        }
        return "/";
    }

    private String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return defaultRootPath();
        }
        Path p = Paths.get(path.trim());
        return p.toAbsolutePath().normalize().toString();
    }

    private String joinPath(String parent, String name) {
        Path p = Paths.get(normalizePath(parent)).resolve(name);
        return p.toAbsolutePath().normalize().toString();
    }

}
