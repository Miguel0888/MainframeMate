package de.bund.zrb.ui;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.files.model.FilePayload;
import de.bund.zrb.indexing.model.SourceType;
import de.bund.zrb.indexing.ui.IndexingSidebar;
import de.bund.zrb.ui.browser.BrowserSessionState;
import de.bund.zrb.ui.browser.PathNavigator;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ConnectionTabImpl implements ConnectionTab {

    private static final int MOUSE_BACK_BUTTON = 4;
    private static final int MOUSE_FORWARD_BUTTON = 5;

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
    private JButton backButton;
    private JButton forwardButton;
    private final IndexingSidebar indexingSidebar = new IndexingSidebar(SourceType.FTP);
    private boolean sidebarVisible = false;

    private final JTextField searchField = new JTextField();
    private final JLabel messageLabel = new JLabel();
    private final CardLayout listCardLayout = new CardLayout();
    private final JPanel listContainer = new JPanel(listCardLayout);
    private static final String CARD_LIST = "list";
    private static final String CARD_MESSAGE = "message";
    private List<String> currentDirectoryFiles = new ArrayList<>();
    private List<FileNode> currentDirectoryNodes = new ArrayList<>();

    /**
     * Constructor using VirtualResource + FileService.
     */
    public ConnectionTabImpl(VirtualResource resource, FileService fileService, TabbedPaneManager tabbedPaneManager, String searchPattern) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.resource = resource;
        this.fileService = fileService;
        this.previewOpener = new DocumentPreviewOpener(tabbedPaneManager);
        this.mainPanel = new JPanel(new BorderLayout());

        boolean mvsMode = resource.getFtpState() != null && Boolean.TRUE.equals(resource.getFtpState().getMvsMode());
        this.navigator = new PathNavigator(mvsMode);
        this.browserState = new BrowserSessionState(navigator.normalize(resource.getResolvedPath()));

        initUI(searchPattern);
    }

    public ConnectionTabImpl(VirtualResource resource, FileService fileService, TabbedPaneManager tabbedPaneManager) {
        this(resource, fileService, tabbedPaneManager, null);
    }

    private void initUI(String searchPattern) {
        JPanel pathPanel = new JPanel(new BorderLayout());

        // 🔄 Refresh-Button links
        JButton refreshButton = new JButton("🔄");
        refreshButton.setToolTipText("Aktuellen Pfad neu laden");
        refreshButton.setMargin(new Insets(0, 0, 0, 0));
        refreshButton.setFont(refreshButton.getFont().deriveFont(Font.PLAIN, 18f));
        refreshButton.addActionListener(e -> loadDirectory(pathField.getText()));

        // ⏴ Zurück-Button rechts
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

        // Öffnen-Button rechts
        JButton goButton = new JButton("Öffnen");
        goButton.addActionListener(e -> loadDirectory(pathField.getText()));
        pathField.addActionListener(e -> loadDirectory(pathField.getText()));

        // Rechte Buttongruppe (⏴ ⏵ Öffnen 📊)
        JPanel rightButtons = new JPanel(new GridLayout(1, 4, 0, 0));
        rightButtons.add(backButton);
        rightButtons.add(forwardButton);
        rightButtons.add(goButton);

        JToggleButton detailsButton = new JToggleButton("📊");
        detailsButton.setToolTipText("Indexierungs-Details anzeigen");
        detailsButton.setMargin(new Insets(0, 0, 0, 0));
        detailsButton.setFont(detailsButton.getFont().deriveFont(Font.PLAIN, 16f));
        detailsButton.addActionListener(e -> toggleSidebar());
        rightButtons.add(detailsButton);

        // Panelaufbau
        pathPanel.add(refreshButton, BorderLayout.WEST);
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(rightButtons, BorderLayout.EAST);

        // Message label setup (shown instead of list when needed)
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        messageLabel.setVerticalAlignment(SwingConstants.CENTER);
        messageLabel.setFont(messageLabel.getFont().deriveFont(Font.BOLD, 14f));
        messageLabel.setOpaque(true);
        messageLabel.setBackground(new Color(255, 255, 255, 200));

        // List setup – use CardLayout to switch between list and message (no overlay)
        JScrollPane scrollPane = new JScrollPane(fileList);
        listContainer.add(scrollPane, CARD_LIST);
        listContainer.add(messageLabel, CARD_MESSAGE);
        listCardLayout.show(listContainer, CARD_LIST);

        mainPanel.add(pathPanel, BorderLayout.NORTH);
        mainPanel.add(listContainer, BorderLayout.CENTER);
        indexingSidebar.setVisible(false);
        mainPanel.add(indexingSidebar, BorderLayout.EAST);
        mainPanel.add(createStatusBar(), BorderLayout.SOUTH);

        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        installMouseNavigation(pathField);
        installMouseNavigation(fileList);
        installMouseNavigation(mainPanel);
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                handleItemActivation(e.isControlDown());
            }
        });

        // Keyboard navigation: Enter, Left/Right arrows, circular Up/Down
        de.bund.zrb.ui.util.ListKeyboardNavigation.install(
                fileList, searchField,
                () -> handleItemActivation(false),
                this::navigateBack,
                this::navigateForward
        );

        // Initial load - only if path is meaningful
        String initialPath = browserState.getCurrentPath();
        pathField.setText(initialPath);

        // Don't try to list empty path or MVS root '' - wait for user input
        if (initialPath != null && !initialPath.isEmpty() && !"''".equals(initialPath) && !"/".equals(initialPath)) {
            updateFileList();
        } else {
            // Show hint for MVS
            showOverlayMessage("Bitte HLQ eingeben (z.B. BENUTZERKENNUNG)", Color.GRAY);
        }

        if (searchPattern != null && !searchPattern.trim().isEmpty()) {
            searchField.setText(searchPattern.trim());
            applySearchFilter();
        }

        updateNavigationButtons();
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

    private void handleItemActivation(boolean ctrlDown) {
        String selected = fileList.getSelectedValue();
        if (selected == null) return;

        FileNode node = findNodeByName(selected);
        if (node != null && node.isDirectory()) {
            browserState.goTo(node.getPath());
            pathField.setText(browserState.getCurrentPath());
            updateFileList();
            tabbedPaneManager.refreshStarForTab(ConnectionTabImpl.this);
            return;
        }

        // Best effort: try to treat as directory first
        String nextPath = navigator.childOf(browserState.getCurrentPath(), selected);
        try {
            List<FileNode> listed = fileService.list(nextPath);
            currentDirectoryNodes = listed == null ? new ArrayList<>() : listed;
            browserState.goTo(nextPath);
            pathField.setText(browserState.getCurrentPath());
            refreshListModelFromNodes();
            tabbedPaneManager.refreshStarForTab(ConnectionTabImpl.this);
            return;
        } catch (Exception ignore) {
            // not a directory
        }

        // CTRL = Raw-Datei öffnen, sonst Document Preview
        if (ctrlDown) {
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
        navigateTo(path, true);
    }

    private void navigateBack() {
        browserState.back();
        pathField.setText(browserState.getCurrentPath());
        updateNavigationButtons();
        updateFileList();
        tabbedPaneManager.refreshStarForTab(this);
    }

    private void navigateForward() {
        browserState.forward();
        pathField.setText(browserState.getCurrentPath());
        updateNavigationButtons();
        updateFileList();
        tabbedPaneManager.refreshStarForTab(this);
    }

    private void navigateTo(String path, boolean addToHistory) {
        String normalized = navigator.normalize(path);
        if (addToHistory) {
            browserState.goTo(normalized);
        }
        pathField.setText(browserState.getCurrentPath());
        updateNavigationButtons();
        updateFileList();
        // Update star button to reflect bookmark state for new directory
        tabbedPaneManager.refreshStarForTab(this);
    }

    private void updateNavigationButtons() {
        if (backButton != null) {
            backButton.setEnabled(browserState.canGoBack());
        }
        if (forwardButton != null) {
            forwardButton.setEnabled(browserState.canGoForward());
        }
    }

    private void toggleSidebar() {
        sidebarVisible = !sidebarVisible;
        indexingSidebar.setVisible(sidebarVisible);
        if (sidebarVisible) {
            indexingSidebar.setCurrentPath(pathField.getText());
        }
        mainPanel.revalidate();
        mainPanel.repaint();
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
            main.getBookmarkDrawer().setBookmarkForCurrentPath(getComponent(), getPath(), "FTP", "DIRECTORY");
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
                de.bund.zrb.util.AppLogger.get(de.bund.zrb.util.AppLogger.UI).fine("[ConnectionTab] Loading directory: " + currentPath);

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
     * Show a message in the file list area (replaces the list).
     */
    private void showOverlayMessage(String message, Color color) {
        messageLabel.setText(message);
        messageLabel.setForeground(color);
        listCardLayout.show(listContainer, CARD_MESSAGE);
    }

    /**
     * Show the file list again.
     */
    private void hideOverlay() {
        listCardLayout.show(listContainer, CARD_LIST);
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
