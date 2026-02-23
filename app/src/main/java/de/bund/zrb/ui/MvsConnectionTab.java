package de.bund.zrb.ui;

import de.bund.zrb.files.impl.vfs.mvs.*;
import de.bund.zrb.indexing.model.SourceType;
import de.bund.zrb.indexing.ui.IndexingSidebar;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.List;

/**
 * MVS-specific connection tab using the new VFS architecture.
 *
 * Features:
 * - Proper HLQ -> 'HLQ.*' query resolution
 * - Pagination with first page shown immediately
 * - No "filter frustration" - LoadedModel/ViewModel separation
 * - State-based navigation (HLQ/DATASET/MEMBER)
 */
public class MvsConnectionTab implements ConnectionTab, MvsBrowserController.BrowserListener {

    private static final int MOUSE_BACK_BUTTON = 4;
    private static final int MOUSE_FORWARD_BUTTON = 5;

    private final TabbedPaneManager tabbedPaneManager;
    private final MvsFtpClient ftpClient;
    private final MvsBrowserController controller;
    private final AsyncRawFileOpener rawFileOpener;

    private final JPanel mainPanel;
    private final JTextField pathField = new JTextField();
    private final DefaultListModel<MvsVirtualResource> listModel = new DefaultListModel<>();
    private final JList<MvsVirtualResource> fileList;
    private final JTextField searchField = new JTextField();
    private final JLabel overlayLabel = new JLabel();
    private final JPanel listContainer = new JPanel();
    private final JLabel statusLabel = new JLabel(" ");
    private JButton backButton;
    private JButton forwardButton;
    private IndexingSidebar indexingSidebar;
    private boolean sidebarVisible = false;

    // Connection info for reopening
    private final String host;
    private final String user;
    private final String password;
    private final String encoding;

    /**
     * Create MVS connection tab.
     */
    public MvsConnectionTab(TabbedPaneManager tabbedPaneManager,
                            String host, String user, String password, String encoding)
            throws IOException {
        this.tabbedPaneManager = tabbedPaneManager;
        this.host = host;
        this.user = user;
        this.password = password;
        this.encoding = encoding;

        // Create FTP client and connect
        this.ftpClient = new MvsFtpClient(host, user);
        ftpClient.connect(password, encoding);

        // Create controller
        this.controller = new MvsBrowserController(ftpClient);
        controller.addListener(this);

        // Setup UI
        this.mainPanel = new JPanel(new BorderLayout());
        this.fileList = new JList<>(listModel);
        fileList.setCellRenderer(new MvsResourceCellRenderer());
        this.rawFileOpener = new AsyncRawFileOpener(tabbedPaneManager, mainPanel, host, user, password, encoding);

        initUI();
    }

    private void initUI() {
        // Path panel
        JPanel pathPanel = new JPanel(new BorderLayout());

        JButton refreshButton = new JButton("🔄");
        refreshButton.setToolTipText("Aktualisieren");
        refreshButton.addActionListener(e -> controller.refresh());

        backButton = new JButton("⏴");
        backButton.setToolTipText("Zurück zur vorherigen Ansicht");
        backButton.setMargin(new Insets(0, 0, 0, 0));
        backButton.setFont(backButton.getFont().deriveFont(Font.PLAIN, 20f));
        backButton.addActionListener(e -> navigateBack());

        forwardButton = new JButton("⏵");
        forwardButton.setToolTipText("Vorwärts zur nächsten Ansicht");
        forwardButton.setMargin(new Insets(0, 0, 0, 0));
        forwardButton.setFont(forwardButton.getFont().deriveFont(Font.PLAIN, 20f));
        forwardButton.addActionListener(e -> navigateForward());

        JButton goButton = new JButton("Öffnen");
        goButton.addActionListener(e -> navigateToPath());

        pathField.addActionListener(e -> navigateToPath());

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

        pathPanel.add(refreshButton, BorderLayout.WEST);
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(rightButtons, BorderLayout.EAST);

        // Overlay setup
        overlayLabel.setHorizontalAlignment(SwingConstants.CENTER);
        overlayLabel.setVerticalAlignment(SwingConstants.CENTER);
        overlayLabel.setFont(overlayLabel.getFont().deriveFont(Font.BOLD, 14f));
        overlayLabel.setOpaque(true);
        overlayLabel.setVisible(false);
        overlayLabel.setAlignmentX(0.5f);
        overlayLabel.setAlignmentY(0.5f);

        // List setup
        JScrollPane scrollPane = new JScrollPane(fileList);
        scrollPane.setAlignmentX(0.5f);
        scrollPane.setAlignmentY(0.5f);

        listContainer.setLayout(new OverlayLayout(listContainer));
        listContainer.add(overlayLabel);
        listContainer.add(scrollPane);

        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        installMouseNavigation(pathField);
        installMouseNavigation(fileList);
        installMouseNavigation(mainPanel);
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleDoubleClick();
                }
            }
        });

        // Status bar
        JPanel statusBar = createStatusBar();

        // Main layout
        indexingSidebar = new IndexingSidebar(SourceType.FTP);
        indexingSidebar.setVisible(false);
        mainPanel.add(pathPanel, BorderLayout.NORTH);
        mainPanel.add(listContainer, BorderLayout.CENTER);
        mainPanel.add(indexingSidebar, BorderLayout.EAST);
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        // Show initial hint
        showOverlayMessage("Bitte HLQ eingeben (z.B. USERID)", Color.GRAY);
        updateNavigationButtons();
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());

        // Filter panel
        JPanel filterPanel = new JPanel(new BorderLayout());
        filterPanel.add(new JLabel("🔎 "), BorderLayout.WEST);
        filterPanel.add(searchField, BorderLayout.CENTER);

        searchField.setToolTipText("Filter (Regex mit / beginnen)");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton newFolderButton = new JButton("📁+");
        newFolderButton.setToolTipText("Neuen Ordner/Qualifier anlegen");
        newFolderButton.addActionListener(e -> createNewFolder());
        leftPanel.add(newFolderButton);

        // Status
        statusLabel.setForeground(Color.GRAY);

        statusBar.add(leftPanel, BorderLayout.WEST);
        statusBar.add(filterPanel, BorderLayout.CENTER);
        statusBar.add(statusLabel, BorderLayout.EAST);

        return statusBar;
    }

    private void navigateToPath() {
        String path = pathField.getText().trim();
        hideOverlay();
        controller.navigateTo(path);
        // Update star button to reflect bookmark state for new directory
        tabbedPaneManager.refreshStarForTab(this);
    }

    private void navigateBack() {
        if (!controller.canGoBack()) {
            showOverlayMessage("Bitte HLQ eingeben (z.B. USERID)", Color.GRAY);
            return;
        }

        hideOverlay();
        controller.goBack();
        updateNavigationButtons();
        tabbedPaneManager.refreshStarForTab(this);
    }

    private void navigateForward() {
        if (!controller.canGoForward()) {
            return;
        }

        hideOverlay();
        controller.goForward();
        updateNavigationButtons();
        tabbedPaneManager.refreshStarForTab(this);
    }

    private void updateNavigationButtons() {
        if (backButton != null) {
            backButton.setEnabled(controller.canGoBack());
        }
        if (forwardButton != null) {
            forwardButton.setEnabled(controller.canGoForward());
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

    private void handleDoubleClick() {
        MvsVirtualResource selected = fileList.getSelectedValue();
        if (selected == null) {
            return;
        }

        boolean handled = controller.openResource(selected);

        if (!handled) {
            // Open as file
            openMember(selected);
        }
    }

    private void openMember(MvsVirtualResource resource) {
        String path = resource.getOpenPath();
        System.out.println("[MvsConnectionTab] Opening resource async: " + path);

        rawFileOpener.openAsync(path,
                new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText("Öffne Datei...");
                        statusLabel.setForeground(Color.BLUE.darker());
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText(" ");
                        statusLabel.setForeground(Color.GRAY);
                    }
                });
    }

    private void createNewFolder() {
        MvsLocation currentLocation = controller.getCurrentLocation();
        if (currentLocation != null && currentLocation.getType() == MvsLocationType.MEMBER) {
            JOptionPane.showMessageDialog(mainPanel, "In einem Member kann kein Ordner angelegt werden.", "Hinweis", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String suggestion = controller.getCurrentPath();
        String input = JOptionPane.showInputDialog(mainPanel,
                "Name des neuen Ordners/Qualifiers (z.B. USER.TEST):",
                suggestion == null ? "" : suggestion);

        if (input == null || input.trim().isEmpty()) {
            return;
        }

        String target = buildDatasetName(input.trim());

        try {
            boolean created = ftpClient.getFtpClient().makeDirectory(MvsQuoteNormalizer.normalize(target));
            if (created) {
                controller.refresh();
            } else {
                JOptionPane.showMessageDialog(mainPanel,
                        "Ordner konnte nicht angelegt werden:\n" + ftpClient.getReplyString(),
                        "Fehler",
                        JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Fehler beim Anlegen des Ordners:\n" + e.getMessage(),
                    "Fehler",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private String buildDatasetName(String input) {
        if (input.contains(".")) {
            return input.toUpperCase();
        }

        String currentPath = controller.getCurrentPath();
        if (currentPath == null || currentPath.trim().isEmpty()) {
            return input.toUpperCase();
        }

        String base = MvsQuoteNormalizer.unquote(currentPath).toUpperCase();
        if (base.endsWith(".*")) {
            base = base.substring(0, base.length() - 2);
        }
        if (base.endsWith("*")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith(".")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isEmpty()) {
            return input.toUpperCase();
        }

        return base + "." + input.toUpperCase();
    }

    private void applyFilter() {
        String filter = searchField.getText();
        controller.setFilter(filter);
    }

    // === MvsBrowserController.BrowserListener ===

    @Override
    public void onViewModelChanged(final List<MvsVirtualResource> viewModel) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                listModel.clear();
                for (MvsVirtualResource resource : viewModel) {
                    listModel.addElement(resource);
                }

                if (viewModel.isEmpty() && !controller.isLoading()) {
                    showOverlayMessage("Keine Einträge gefunden", Color.ORANGE.darker());
                } else {
                    hideOverlay();
                }

                // Update path field
                pathField.setText(controller.getCurrentPath());
                updateNavigationButtons();
            }
        });
    }

    @Override
    public void onLoadingStateChanged(final boolean isLoading, final int loadedCount) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (isLoading) {
                    statusLabel.setText(loadedCount + " Einträge, lade...");
                    statusLabel.setForeground(Color.BLUE);
                    if (loadedCount == 0) {
                        showOverlayMessage("Lade Einträge...", Color.GRAY);
                    }
                } else {
                    statusLabel.setText(loadedCount + " Einträge");
                    statusLabel.setForeground(Color.DARK_GRAY);
                    if (loadedCount == 0) {
                        showOverlayMessage("Keine Einträge gefunden", Color.ORANGE.darker());
                    } else {
                        hideOverlay();
                    }
                }
            }
        });
    }

    @Override
    public void onError(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                showOverlayMessage("Fehler: " + message, Color.RED);
            }
        });
    }

    @Override
    public void onStatusMessage(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (message.startsWith("Bitte")) {
                    showOverlayMessage(message, Color.GRAY);
                } else {
                    statusLabel.setText(message);
                }
            }
        });
    }

    // === Overlay helpers ===

    private void showOverlayMessage(String message, Color color) {
        overlayLabel.setText(message);
        overlayLabel.setForeground(color);
        overlayLabel.setBackground(new Color(255, 255, 255, 220));
        overlayLabel.setVisible(true);
        listContainer.revalidate();
        listContainer.repaint();
    }

    private void hideOverlay() {
        overlayLabel.setVisible(false);
        listContainer.revalidate();
        listContainer.repaint();
    }

    // === ConnectionTab interface ===

    @Override
    public String getTitle() {
        return "📁 MVS: " + user + "@" + host;
    }

    @Override
    public String getTooltip() {
        return "MVS FTP: " + user + "@" + host + " - " + controller.getCurrentPath();
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public JPopupMenu createContextMenu(Runnable onCloseCallback) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
        closeItem.addActionListener(e -> onCloseCallback.run());

        menu.add(closeItem);
        return menu;
    }

    @Override
    public void onClose() {
        controller.shutdown();
        rawFileOpener.shutdown();
        try {
            ftpClient.close();
        } catch (IOException e) {
            System.err.println("[MvsConnectionTab] Error closing FTP connection: " + e.getMessage());
        }
    }

    @Override
    public void saveIfApplicable() {
        // Not applicable for connection tabs
    }

    @Override
    public String getContent() {
        // Connection tabs don't have content
        return "";
    }

    @Override
    public void markAsChanged() {
        // Not applicable
    }

    @Override
    public String getPath() {
        return controller.getCurrentPath();
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
        if (searchPattern != null) {
            searchField.setText(searchPattern.trim());
            applyFilter();
        }
    }

    public void loadDirectory(String path) {
        pathField.setText(path);
        navigateToPath();
    }

    // === Cell Renderer ===

    private static class MvsResourceCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof MvsVirtualResource) {
                MvsVirtualResource resource = (MvsVirtualResource) value;

                String icon;
                switch (resource.getType()) {
                    case HLQ:
                    case QUALIFIER_CONTEXT:
                        icon = "📁";
                        break;
                    case DATASET:
                        icon = "📂";
                        break;
                    case MEMBER:
                        icon = "📄";
                        break;
                    default:
                        icon = "📄";
                }

                setText(icon + " " + resource.getDisplayName());
            }

            return this;
        }
    }
}
