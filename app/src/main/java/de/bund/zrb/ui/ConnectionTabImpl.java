package de.bund.zrb.ui;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.files.model.FilePayload;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.mvs.MvsInitialPathProvider;
import de.bund.zrb.ui.browser.BrowserSessionState;
import de.bund.zrb.ui.browser.PathNavigator;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ConnectionTabImpl implements ConnectionTab {

    private VirtualResource resource;
    private final FileService fileService;
    private final BrowserSessionState browserState;
    private final PathNavigator navigator;
    private final DocumentPreviewOpener previewOpener;

    private final JPanel mainPanel;
    private final JTextField pathField = new JTextField();
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> fileList = new JList<>(listModel);
    private final TabbedPaneManager tabbedPaneManager;

    private final JTextField searchField = new JTextField();
    private final JLabel overlayLabel = new JLabel();
    private final JPanel listContainer = new JPanel(new BorderLayout());
    private List<String> currentDirectoryFiles = new ArrayList<>();
    private List<FileNode> currentDirectoryNodes = new ArrayList<>();

    private final boolean mvsMode;
    private final String loginName;
    private final Settings settings;
    private final MvsInitialPathProvider initialPathProvider = new MvsInitialPathProvider();

    private JCheckBox useLoginAsHlqBox;
    private JTextField customHlqField;

    /**
     * Constructor using VirtualResource + FileService.
     */
    public ConnectionTabImpl(VirtualResource resource, FileService fileService, TabbedPaneManager tabbedPaneManager, String searchPattern) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.resource = resource;
        this.fileService = fileService;
        this.previewOpener = new DocumentPreviewOpener(tabbedPaneManager);
        this.mainPanel = new JPanel(new BorderLayout());

        this.settings = SettingsHelper.load();

        this.mvsMode = resource.getFtpState() != null && Boolean.TRUE.equals(resource.getFtpState().getMvsMode());
        this.loginName = resolveLoginName(resource);

        this.navigator = new PathNavigator(mvsMode);
        this.browserState = new BrowserSessionState(navigator.normalize(resource.getResolvedPath()));

        initUI(searchPattern);
    }

    private String resolveLoginName(VirtualResource resource) {
        if (resource == null || resource.getFtpState() == null || resource.getFtpState().getConnectionId() == null) {
            return "";
        }
        return resource.getFtpState().getConnectionId().getUsername();
    }

    public ConnectionTabImpl(VirtualResource resource, FileService fileService, TabbedPaneManager tabbedPaneManager) {
        this(resource, fileService, tabbedPaneManager, null);
    }

    private void initUI(String searchPattern) {
        JPanel pathPanel = new JPanel(new BorderLayout());

        JButton refreshButton = new JButton("🔄");
        refreshButton.setToolTipText("Aktuellen Pfad neu laden");
        refreshButton.setMargin(new Insets(0, 0, 0, 0));
        refreshButton.setFont(refreshButton.getFont().deriveFont(Font.PLAIN, 18f));
        refreshButton.addActionListener(e -> loadDirectory(pathField.getText()));

        JButton backButton = new JButton("⏴");
        backButton.setToolTipText("Zurück zum übergeordneten Verzeichnis");
        backButton.setMargin(new Insets(0, 0, 0, 0));
        backButton.setFont(backButton.getFont().deriveFont(Font.PLAIN, 20f));
        backButton.addActionListener(e -> {
            String parent = navigator.parentOf(browserState.getCurrentPath());
            browserState.goTo(parent);
            pathField.setText(browserState.getCurrentPath());
            updateFileList();
        });

        JButton goButton = new JButton("Öffnen");
        goButton.addActionListener(e -> loadDirectory(pathField.getText()));
        pathField.addActionListener(e -> loadDirectory(pathField.getText()));

        JPanel rightButtons = new JPanel(new GridLayout(1, 2, 0, 0));
        rightButtons.add(backButton);
        rightButtons.add(goButton);

        pathPanel.add(refreshButton, BorderLayout.WEST);
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(rightButtons, BorderLayout.EAST);

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(pathPanel, BorderLayout.NORTH);
        if (mvsMode) {
            northPanel.add(createInitialHlqPanel(), BorderLayout.SOUTH);
        }

        overlayLabel.setHorizontalAlignment(SwingConstants.CENTER);
        overlayLabel.setVerticalAlignment(SwingConstants.CENTER);
        overlayLabel.setFont(overlayLabel.getFont().deriveFont(Font.BOLD, 14f));
        overlayLabel.setOpaque(true);
        overlayLabel.setVisible(false);

        JScrollPane scrollPane = new JScrollPane(fileList);
        listContainer.add(scrollPane, BorderLayout.CENTER);

        mainPanel.add(northPanel, BorderLayout.NORTH);
        mainPanel.add(listContainer, BorderLayout.CENTER);
        mainPanel.add(createStatusBar(), BorderLayout.SOUTH);

        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;

                String selected = fileList.getSelectedValue();
                if (selected == null) return;

                FileNode node = findNodeByName(selected);
                if (node != null && node.isDirectory()) {
                    browserState.goTo(node.getPath());
                    pathField.setText(browserState.getCurrentPath());
                    updateFileList();
                    return;
                }

                String nextPath = navigator.childOf(browserState.getCurrentPath(), selected);
                try {
                    List<FileNode> listed = fileService.list(nextPath);
                    currentDirectoryNodes = listed == null ? new ArrayList<>() : listed;
                    browserState.goTo(nextPath);
                    pathField.setText(browserState.getCurrentPath());
                    refreshListModelFromNodes();
                    return;
                } catch (Exception ignore) {
                    // not a directory
                }

                if (e.isControlDown()) {
                    try {
                        FilePayload payload = fileService.readFile(nextPath);
                        String content = payload.getEditorText();
                        VirtualResource fileResource = buildResourceForPath(nextPath, VirtualResourceKind.FILE);
                        tabbedPaneManager.openFileTab(fileResource, content, null, null, false);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(mainPanel, "Fehler beim Öffnen:\n" + ex.getMessage(),
                                "Fehler", JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    previewOpener.openPreviewAsync(fileService, nextPath, selected, mainPanel);
                }
            }
        });

        if (mvsMode) {
            applyInitialHlqToBrowserStateIfConfigured();
        }

        String initialPath = browserState.getCurrentPath();
        pathField.setText(initialPath);

        if (initialPath != null && !initialPath.isEmpty() && !"''".equals(initialPath) && !"/".equals(initialPath)) {
            updateFileList();
        } else {
            showOverlayMessage("Bitte HLQ eingeben (z.B. USERID)", Color.GRAY);
        }

        if (searchPattern != null && !searchPattern.trim().isEmpty()) {
            searchField.setText(searchPattern.trim());
            applySearchFilter();
        }
    }

    private void applyInitialHlqToBrowserStateIfConfigured() {
        String currentPath = browserState.getCurrentPath();
        if (!initialPathProvider.isRoot(currentPath)) {
            return;
        }

        String resolved = initialPathProvider.resolveInitialPath(currentPath, loginName, settings);
        if (resolved == null || initialPathProvider.isRoot(resolved)) {
            return;
        }

        browserState.goTo(navigator.normalize(resolved));
    }

    private JPanel createInitialHlqPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));

        useLoginAsHlqBox = new JCheckBox("Login als HLQ verwenden");
        useLoginAsHlqBox.setToolTipText("Wenn aktiv, wird der Loginname als Start-HLQ gesetzt");
        useLoginAsHlqBox.setSelected(settings.ftpUseLoginAsHlq);

        panel.add(useLoginAsHlqBox);

        panel.add(new JLabel("Default HLQ:"));

        customHlqField = new JTextField(settings.ftpCustomHlq != null ? settings.ftpCustomHlq : "", 14);
        customHlqField.setToolTipText("Wird nur verwendet, wenn die Checkbox aus ist (leer = wie bisher)");
        customHlqField.setEnabled(!useLoginAsHlqBox.isSelected());
        panel.add(customHlqField);

        useLoginAsHlqBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                customHlqField.setEnabled(!useLoginAsHlqBox.isSelected());
                persistInitialHlqSettingsFromUi();
                applyInitialHlqNowFromSettingsOrShowHint();
            }
        });

        customHlqField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                persistInitialHlqSettingsFromUi();
                applyInitialHlqNowFromSettingsOrShowHint();
            }
        });

        customHlqField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                persistInitialHlqSettingsFromUi();
            }
        });

        return panel;
    }

    private void persistInitialHlqSettingsFromUi() {
        settings.ftpUseLoginAsHlq = useLoginAsHlqBox.isSelected();
        settings.ftpCustomHlq = customHlqField.getText() == null ? "" : customHlqField.getText().trim();
        SettingsHelper.save(settings);
    }

    private void applyInitialHlqNowFromSettingsOrShowHint() {
        String configured = initialPathProvider.resolveConfiguredInitialHlqPath(loginName, settings);

        if (configured == null || initialPathProvider.isRoot(configured)) {
            resetToMvsRootAndShowHint();
            return;
        }

        loadDirectory(configured);
    }

    private void resetToMvsRootAndShowHint() {
        browserState.goTo(navigator.normalize(""));
        pathField.setText(browserState.getCurrentPath());

        currentDirectoryFiles = new ArrayList<>();
        currentDirectoryNodes = new ArrayList<>();
        listModel.clear();

        showOverlayMessage("Bitte HLQ eingeben (z.B. USERID)", Color.GRAY);
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
        try {
            fileService.close();
        } catch (Exception ignore) {
            // ignore
        }
    }

    @Override
    public void saveIfApplicable() {
        // nichts zu speichern
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
    }

    @Override
    public String getPath() {
        return pathField.getText();
    }

    public void loadDirectory(String path) {
        browserState.goTo(navigator.normalize(path));
        pathField.setText(browserState.getCurrentPath());
        updateFileList();
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

    public void searchFor(String searchPattern) {
        if (searchPattern == null) return;
        searchField.setText(searchPattern.trim());
        applySearchFilter();
    }

    @Override
    public JPopupMenu createContextMenu(Runnable onCloseCallback) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem bookmarkItem = new JMenuItem("🕮 Bookmark setzen");
        bookmarkItem.addActionListener(e -> {
            MainFrame main = (MainFrame) SwingUtilities.getWindowAncestor(getComponent());
            main.getBookmarkDrawer().setBookmarkForCurrentPath(getComponent(), getPath());
        });

        JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
        closeItem.addActionListener(e -> onCloseCallback.run());

        menu.add(bookmarkItem);
        menu.add(closeItem);
        return menu;
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton newFileButton = new JButton("📄");
        newFileButton.setToolTipText("Neue Datei anlegen");
        newFileButton.addActionListener(e -> createNewFile());
        leftPanel.add(newFileButton);

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
        searchField.setToolTipText("<html>Regex-Filter für Dateinamen<br>Beispiel: <code>\\.JCL$</code> findet alle JCL-Dateien<br><i>(Groß-/Kleinschreibung wird ignoriert)</i></html>");
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
        for (String file : currentDirectoryFiles) {
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
            showOverlayMessage("Lade...", Color.GRAY);
        });

        // Run listing in background to avoid UI freeze
        new Thread(() -> {
            try {
                String currentPath = browserState.getCurrentPath();
                System.out.println("[ConnectionTab] Loading directory: " + currentPath);

                List<FileNode> nodes = fileService.list(currentPath);

                SwingUtilities.invokeLater(() -> {
                    currentDirectoryNodes = nodes == null ? new ArrayList<>() : nodes;
                    refreshListModelFromNodes();

                    if (currentDirectoryNodes.isEmpty()) {
                        showOverlayMessage("Keine Einträge gefunden", Color.ORANGE.darker());
                    } else {
                        hideOverlay();
                    }
                });
            } catch (Exception e) {
                System.err.println("[ConnectionTab] Error loading directory: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    showOverlayMessage("Fehler: " + e.getMessage(), Color.RED);
                });
            }
        }).start();
    }

    /**
     * Show an overlay message in the file list area.
     */
    private void showOverlayMessage(String message, Color color) {
        overlayLabel.setText(message);
        overlayLabel.setForeground(color);
        overlayLabel.setBackground(new Color(255, 255, 255, 200));
        overlayLabel.setVisible(true);

        // Add overlay to list container if not already there
        if (overlayLabel.getParent() != listContainer) {
            listContainer.add(overlayLabel, BorderLayout.CENTER);
        }
        listContainer.revalidate();
        listContainer.repaint();
    }

    /**
     * Hide the overlay message.
     */
    private void hideOverlay() {
        overlayLabel.setVisible(false);
        listContainer.remove(overlayLabel);
        listContainer.revalidate();
        listContainer.repaint();
    }

    private void refreshListModelFromNodes() {
        currentDirectoryFiles = new ArrayList<>();
        for (FileNode n : currentDirectoryNodes) {
            currentDirectoryFiles.add(n.getName());
            listModel.addElement(n.getName());
        }
    }

    private FileNode findNodeByName(String name) {
        if (name == null) return null;
        for (FileNode n : currentDirectoryNodes) {
            if (name.equals(n.getName())) {
                return n;
            }
        }
        return null;
    }

    private VirtualResource buildResourceForPath(String path, VirtualResourceKind kind) {
        if (resource == null) {
            return new VirtualResource(de.bund.zrb.files.path.VirtualResourceRef.of(path), kind, path, true);
        }
        return new VirtualResource(de.bund.zrb.files.path.VirtualResourceRef.of(path),
                kind,
                path,
                resource.getBackendType(),
                resource.getFtpState());
    }

    private void createNewFile() {
        String name = JOptionPane.showInputDialog(mainPanel, "Name der neuen Datei:", "Neue Datei", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;

        try {
            String target = navigator.childOf(browserState.getCurrentPath(), name);
            FilePayload payload = FilePayload.fromBytes(new byte[0], Charset.defaultCharset(), false);
            fileService.writeFile(target, payload);
            updateFileList();
        } catch (Exception e) {
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
            String target = navigator.childOf(browserState.getCurrentPath(), selected);
            if (fileService.delete(target)) {
                updateFileList();
            } else {
                JOptionPane.showMessageDialog(mainPanel, "Löschen fehlgeschlagen!", "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainPanel, "Fehler beim Löschen:\n" + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }
}
