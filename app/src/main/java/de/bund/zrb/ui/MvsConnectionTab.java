package de.bund.zrb.ui;

import de.bund.zrb.files.impl.vfs.mvs.*;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.indexing.model.SourceType;
import de.bund.zrb.indexing.ui.IndexingSidebar;
import de.bund.zrb.model.Settings;
import de.bund.zrb.security.SecurityFilterService;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.List;

/**
 * MVS-specific connection tab using the new VFS architecture.
 *
 * Features:
 * - Proper HLQ -> 'HLQ.*' query resolution
 * - Pagination with first page shown immediately
 * - No "filter frustration" - LoadedModel/ViewModel separation
 * - State-based navigation (HLQ/DATASET/MEMBER)
 * - Multi-selection with Ctrl/Shift + context menu
 * - Navigator toolbar with sort, details, blacklist filtering
 * - Security integration (whitelist/blacklist)
 * - File operations (new folder, new member, delete)
 */
public class MvsConnectionTab implements ConnectionTab, MvsBrowserController.BrowserListener {

    private static final int MOUSE_BACK_BUTTON = 4;
    private static final int MOUSE_FORWARD_BUTTON = 5;

    /** Sort modes for resource list. */
    enum SortMode {
        NAME_ASC("Name ↑"), NAME_DESC("Name ↓"),
        DATE_DESC("Datum ↓"), SIZE_DESC("Größe ↓");
        final String label;
        SortMode(String l) { this.label = l; }
        @Override public String toString() { return label; }
    }

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
    private JToggleButton detailsButton;
    private boolean sidebarVisible = false;

    // --- Navigator view state ---
    private SortMode sortMode = SortMode.NAME_ASC;
    private boolean showDetails = false;
    private boolean hideBlacklisted = false;

    // Full data for filtering (snapshot of current view model from controller)
    private List<MvsVirtualResource> currentViewResources = new ArrayList<>();

    // Connection info for reopening
    private final String host;
    private final String user;
    private final String password;
    private final String encoding;

    // State persistence prefix
    private static final String STATE_PREFIX = "mvs.nav.";

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

        restoreNavigatorState();
        initUI();
    }

    private void initUI() {
        // ── Path panel ──
        JPanel pathPanel = new JPanel(new BorderLayout());

        JButton refreshButton = new JButton("🔄");
        refreshButton.setToolTipText("Aktualisieren");
        refreshButton.setMargin(new Insets(0, 0, 0, 0));
        refreshButton.setFont(refreshButton.getFont().deriveFont(Font.PLAIN, 18f));
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

        detailsButton = new JToggleButton("📊");
        detailsButton.setToolTipText("Indexierungs-Details anzeigen");
        detailsButton.setMargin(new Insets(0, 0, 0, 0));
        detailsButton.setFont(detailsButton.getFont().deriveFont(Font.PLAIN, 16f));
        detailsButton.setSelected(sidebarVisible);
        detailsButton.addActionListener(e -> toggleSidebar());
        rightButtons.add(detailsButton);

        pathPanel.add(refreshButton, BorderLayout.WEST);
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(rightButtons, BorderLayout.EAST);

        // ── Overlay setup ──
        overlayLabel.setHorizontalAlignment(SwingConstants.CENTER);
        overlayLabel.setVerticalAlignment(SwingConstants.CENTER);
        overlayLabel.setFont(overlayLabel.getFont().deriveFont(Font.BOLD, 14f));
        overlayLabel.setOpaque(true);
        overlayLabel.setVisible(false);
        overlayLabel.setAlignmentX(0.5f);
        overlayLabel.setAlignmentY(0.5f);

        // ── List setup with multi-selection ──
        JScrollPane scrollPane = new JScrollPane(fileList);
        scrollPane.setAlignmentX(0.5f);
        scrollPane.setAlignmentY(0.5f);

        listContainer.setLayout(new OverlayLayout(listContainer));
        listContainer.add(overlayLabel);
        listContainer.add(scrollPane);

        fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && sidebarVisible) {
                updateSidebarInfo();
            }
        });
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && !e.isPopupTrigger()) {
                    handleDoubleClick();
                }
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e);
            }
        });

        installMouseNavigation(pathField);
        installMouseNavigation(fileList);
        installMouseNavigation(mainPanel);

        // Keyboard navigation: Enter, Left/Right arrows, circular Up/Down
        de.bund.zrb.ui.util.ListKeyboardNavigation.install(
                fileList, searchField,
                this::handleDoubleClick,
                this::navigateBack,
                this::navigateForward
        );
        // Arrow keys in path field → jump into file list
        de.bund.zrb.ui.util.ListKeyboardNavigation.installFieldNavigation(pathField, fileList);

        // ── Navigator toolbar ──
        JPanel navigatorToolbar = createNavigatorToolbar();

        // ── Status bar ──
        JPanel statusBar = createStatusBar();

        // ── Top panel: path bar + navigator toolbar ──
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        pathPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        pathPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        navigatorToolbar.setAlignmentX(Component.LEFT_ALIGNMENT);
        navigatorToolbar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        topPanel.add(pathPanel);
        topPanel.add(navigatorToolbar);

        // ── Sidebar setup ──
        indexingSidebar = new IndexingSidebar(SourceType.FTP);
        indexingSidebar.setVisible(sidebarVisible);
        indexingSidebar.setSecurityGroup("FTP");
        indexingSidebar.setSecurityPathSupplier(this::getSelectedPrefixedPaths);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(listContainer, BorderLayout.CENTER);
        mainPanel.add(indexingSidebar, BorderLayout.EAST);
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        // Show initial hint
        showOverlayMessage("Bitte HLQ eingeben (z.B. BENUTZERKENNUNG)", Color.GRAY);
        updateNavigationButtons();
    }

    // ═══════════════════════════════════════════════════════════
    //  Navigator Toolbar
    // ═══════════════════════════════════════════════════════════

    private JPanel createNavigatorToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        toolbar.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));

        // Sort dropdown button
        JButton sortButton = new JButton("↕");
        sortButton.setToolTipText("Sortierung");
        sortButton.setMargin(new Insets(1, 4, 1, 4));
        sortButton.setFocusable(false);
        sortButton.addActionListener(e -> {
            JPopupMenu popup = new JPopupMenu();
            for (final SortMode mode : SortMode.values()) {
                JCheckBoxMenuItem item = new JCheckBoxMenuItem(mode.label, mode == sortMode);
                item.addActionListener(ev -> {
                    sortMode = mode;
                    saveNavigatorState();
                    applyLocalSort();
                });
                popup.add(item);
            }
            popup.show(sortButton, 0, sortButton.getHeight());
        });
        toolbar.add(sortButton);

        toolbar.add(Box.createHorizontalStrut(8));

        // Show Details toggle (RECFM, LRECL, size)
        JToggleButton detailsToggle = new JToggleButton("📋");
        detailsToggle.setToolTipText("Details anzeigen (RECFM, LRECL, Größe)");
        detailsToggle.setMargin(new Insets(1, 4, 1, 4));
        detailsToggle.setFocusable(false);
        detailsToggle.setSelected(showDetails);
        detailsToggle.addActionListener(e -> {
            showDetails = detailsToggle.isSelected();
            saveNavigatorState();
            fileList.repaint();
        });
        toolbar.add(detailsToggle);

        toolbar.add(Box.createHorizontalStrut(8));

        // Hide Blacklisted toggle
        JToggleButton hideBlacklistedToggle = new JToggleButton("🔒");
        hideBlacklistedToggle.setToolTipText("Gesperrte Elemente ausblenden (Blacklist)");
        hideBlacklistedToggle.setMargin(new Insets(1, 4, 1, 4));
        hideBlacklistedToggle.setFocusable(false);
        hideBlacklistedToggle.setSelected(hideBlacklisted);
        hideBlacklistedToggle.addActionListener(e -> {
            hideBlacklisted = hideBlacklistedToggle.isSelected();
            saveNavigatorState();
            applyLocalSort();
        });
        toolbar.add(hideBlacklistedToggle);

        // ── Separator: File operations ──
        toolbar.add(Box.createHorizontalStrut(12));
        toolbar.add(createToolbarSeparator());
        toolbar.add(Box.createHorizontalStrut(8));

        // New Folder / Dataset
        JButton newFolderButton = new JButton("📂⁺");
        newFolderButton.setToolTipText("Neues Dataset / PDS anlegen (Mainframe)");
        newFolderButton.setMargin(new Insets(1, 4, 1, 4));
        newFolderButton.setFocusable(false);
        newFolderButton.addActionListener(e -> createNewFolder());
        toolbar.add(newFolderButton);

        // New File / Member
        JButton newFileButton = new JButton("📄⁺");
        newFileButton.setToolTipText("Neues Member anlegen");
        newFileButton.setMargin(new Insets(1, 4, 1, 4));
        newFileButton.setFocusable(false);
        newFileButton.addActionListener(e -> createNewMember());
        toolbar.add(newFileButton);

        toolbar.add(Box.createHorizontalStrut(4));

        // Delete
        JButton deleteButton = new JButton("🗑");
        deleteButton.setToolTipText("Ausgewählte Einträge löschen");
        deleteButton.setMargin(new Insets(1, 4, 1, 4));
        deleteButton.setFocusable(false);
        deleteButton.addActionListener(e -> deleteSelectedEntries());
        toolbar.add(deleteButton);

        return toolbar;
    }

    /** Small vertical separator line for the toolbar. */
    private static JComponent createToolbarSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(2, 18));
        return sep;
    }

    // ═══════════════════════════════════════════════════════════
    //  Status Bar
    // ═══════════════════════════════════════════════════════════

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

        // Status
        statusLabel.setForeground(Color.GRAY);

        statusBar.add(filterPanel, BorderLayout.CENTER);
        statusBar.add(statusLabel, BorderLayout.EAST);

        return statusBar;
    }

    // ═══════════════════════════════════════════════════════════
    //  Navigation
    // ═══════════════════════════════════════════════════════════

    private void navigateToPath() {
        String path = pathField.getText().trim();
        hideOverlay();
        controller.navigateTo(path);
        // Update star button to reflect bookmark state for new directory
        tabbedPaneManager.refreshStarForTab(this);
    }

    private void navigateBack() {
        if (!controller.canGoBack()) {
            showOverlayMessage("Bitte HLQ eingeben (z.B. BENUTZERKENNUNG)", Color.GRAY);
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

    // ═══════════════════════════════════════════════════════════
    //  Sidebar
    // ═══════════════════════════════════════════════════════════

    private void toggleSidebar() {
        sidebarVisible = !sidebarVisible;
        indexingSidebar.setVisible(sidebarVisible);
        detailsButton.setSelected(sidebarVisible);
        if (sidebarVisible) {
            updateSidebarInfo();
        }
        saveNavigatorState();
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    private void updateSidebarInfo() {
        try {
            String currentPath = controller.getCurrentPath();
            String displayPath = "ftp://" + host + "/" + currentPath;
            indexingSidebar.setCurrentPath(displayPath);

            List<MvsVirtualResource> sel = fileList.getSelectedValuesList();
            if (sel.isEmpty()) {
                indexingSidebar.setScopeInfo("Alle " + listModel.getSize() + " Einträge");
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(sel.size(), 3); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(sel.get(i).getDisplayName());
                }
                if (sel.size() > 3) sb.append(" … (+" + (sel.size() - 3) + ")");
                indexingSidebar.setScopeInfo("Auswahl: " + sel.size() + " Einträge");
            }
        } catch (Exception e) {
            System.err.println("[MvsConnectionTab] Error updating sidebar info: " + e.getMessage());
        }
    }

    /**
     * Build prefixed paths from the current selection (for SecurityFilterService).
     * Format: "ftp://host/path"
     */
    private List<String> getSelectedPrefixedPaths() {
        try {
            List<MvsVirtualResource> selected = fileList.getSelectedValuesList();
            String currentPath = controller.getCurrentPath();
            String prefix = "ftp://" + host + "/";

            if (selected.isEmpty()) {
                if (currentPath == null || currentPath.isEmpty()) return Collections.emptyList();
                return Collections.singletonList(prefix + currentPath);
            }

            List<String> paths = new ArrayList<>();
            for (MvsVirtualResource res : selected) {
                paths.add(prefix + res.getOpenPath());
            }
            return paths;
        } catch (Exception e) {
            String currentPath = controller.getCurrentPath();
            if (currentPath != null && !currentPath.isEmpty()) {
                return Collections.singletonList("ftp://" + host + "/" + currentPath);
            }
            return Collections.emptyList();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Context Menu & Selection
    // ═══════════════════════════════════════════════════════════

    private void showContextMenu(MouseEvent e) {
        // Right-click on unselected item: select it (without clearing multi-selection)
        int idx = fileList.locationToIndex(e.getPoint());
        if (idx >= 0 && !fileList.isSelectedIndex(idx)) {
            fileList.setSelectedIndex(idx);
        }

        JPopupMenu menu = new JPopupMenu();

        // Select all / none
        JMenuItem selectAll = new JMenuItem("Alles auswählen");
        selectAll.addActionListener(ev -> {
            int size = fileList.getModel().getSize();
            if (size > 0) fileList.setSelectionInterval(0, size - 1);
        });
        menu.add(selectAll);

        JMenuItem selectNone = new JMenuItem("Nichts auswählen");
        selectNone.addActionListener(ev -> fileList.clearSelection());
        menu.add(selectNone);

        menu.addSeparator();

        // File operations
        JMenuItem newFolderItem = new JMenuItem("📂 Neues Dataset anlegen…");
        newFolderItem.addActionListener(ev -> createNewFolder());
        menu.add(newFolderItem);

        JMenuItem newFileItem = new JMenuItem("📄 Neues Member anlegen…");
        newFileItem.addActionListener(ev -> createNewMember());
        menu.add(newFileItem);

        List<MvsVirtualResource> sel = fileList.getSelectedValuesList();
        if (!sel.isEmpty()) {
            menu.addSeparator();
            JMenuItem deleteItem = new JMenuItem("🗑 Löschen (" + sel.size() + ")…");
            deleteItem.addActionListener(ev -> deleteSelectedEntries());
            menu.add(deleteItem);
        }

        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    // ═══════════════════════════════════════════════════════════
    //  Item Activation (double-click)
    // ═══════════════════════════════════════════════════════════

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
        de.bund.zrb.util.AppLogger.get(de.bund.zrb.util.AppLogger.FTP).fine("[MvsConnectionTab] Opening resource async: " + path);

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

    // ═══════════════════════════════════════════════════════════
    //  File Operations
    // ═══════════════════════════════════════════════════════════

    private void createNewFolder() {
        MvsLocation currentLocation = controller.getCurrentLocation();
        if (currentLocation != null && currentLocation.getType() == MvsLocationType.MEMBER) {
            JOptionPane.showMessageDialog(mainPanel, "In einem Member kann kein Ordner angelegt werden.", "Hinweis", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String currentPath = controller.getCurrentPath();
        final String baseQualifier = resolveBaseQualifier(currentPath);

        // Build dialog with context + live preview
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Context label
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        String ctxText = baseQualifier.isEmpty()
                ? "<html><b>Kein Kontext</b> — bitte vollständigen Dataset-Namen eingeben</html>"
                : "<html>Aktueller Kontext: <b>" + baseQualifier + "</b></html>";
        panel.add(new JLabel(ctxText), gbc);

        // Input field
        gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(new JLabel("Neuer Qualifier:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        final JTextField nameField = new JTextField("", 25);
        nameField.setToolTipText("<html>Nur den neuen Teil eingeben (z.B. <b>NEW</b>)<br>"
                + "Für vollständigen Pfad mit Punkt eingeben (z.B. <b>USR1.KOMPLETT.NEU</b>)</html>");
        panel.add(nameField, gbc);

        // Preview label
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weightx = 0;
        final JLabel previewLabel = new JLabel(" ");
        previewLabel.setForeground(new Color(0, 100, 0));
        panel.add(previewLabel, gbc);

        // Live preview update
        nameField.getDocument().addDocumentListener(new DocumentListener() {
            private void update() {
                String text = nameField.getText().trim();
                if (text.isEmpty()) {
                    previewLabel.setText(" ");
                } else {
                    String full = buildDatasetName(text);
                    previewLabel.setText("→ " + full);
                }
            }
            public void insertUpdate(DocumentEvent e) { update(); }
            public void removeUpdate(DocumentEvent e) { update(); }
            public void changedUpdate(DocumentEvent e) { update(); }
        });

        int result = JOptionPane.showConfirmDialog(mainPanel, panel,
                "Neues Mainframe-Dataset anlegen", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;

        String input = nameField.getText().trim();
        if (input.isEmpty()) return;

        String target = buildDatasetName(input);

        try {
            boolean created = ftpClient.getFtpClient().makeDirectory(MvsQuoteNormalizer.normalize(target));
            if (created) {
                controller.refresh();
            } else {
                JOptionPane.showMessageDialog(mainPanel,
                        "Dataset konnte nicht angelegt werden:\n" + ftpClient.getReplyString(),
                        "Fehler",
                        JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Fehler beim Anlegen:\n" + e.getMessage(),
                    "Fehler",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createNewMember() {
        MvsLocation currentLocation = controller.getCurrentLocation();
        String currentPath = controller.getCurrentPath();

        if (currentLocation == null || currentLocation.getType() == MvsLocationType.ROOT) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Bitte zuerst in ein Dataset (PDS) navigieren, um ein Member anzulegen.",
                    "Hinweis", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Check if the current listing actually contains members (= is a PDS)
        // If only directories are shown, this path is just a qualifier, not a PDS
        boolean hasMembers = false;
        for (int i = 0; i < listModel.getSize(); i++) {
            if (!listModel.getElementAt(i).isDirectory()) {
                hasMembers = true;
                break;
            }
        }

        if (!hasMembers && listModel.getSize() > 0) {
            int answer = JOptionPane.showConfirmDialog(mainPanel,
                    "<html>Der aktuelle Pfad <b>" + currentPath + "</b> enthält nur Sub-Datasets,<br>"
                            + "ist also kein Partitioned Data Set (PDS).<br><br>"
                            + "Ein Member kann nur in einem PDS angelegt werden.<br>"
                            + "Soll zuerst ein neues PDS als Dataset angelegt werden?</html>",
                    "Kein PDS", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
                createNewFolder();
            }
            return;
        }

        // Build dialog
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1;
        panel.add(new JLabel("Member-Name:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        JTextField memberField = new JTextField(15);
        memberField.setToolTipText("Max. 8 Zeichen, z.B. NEWMEMBR");
        panel.add(memberField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        String info = currentPath != null
                ? "<html><small>Dataset: " + currentPath + "</small></html>"
                : "<html><small>Kein Dataset ausgewählt.</small></html>";
        panel.add(new JLabel(info), gbc);

        int result = JOptionPane.showConfirmDialog(mainPanel, panel,
                "Neues Mainframe-Member anlegen", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;

        String memberName = memberField.getText().trim().toUpperCase();
        if (memberName.isEmpty()) return;

        // Truncate to 8 chars (PDS member limit)
        if (memberName.length() > 8) {
            memberName = memberName.substring(0, 8);
        }

        try {
            // Build the member path: 'DATASET(MEMBER)'
            String dataset = MvsQuoteNormalizer.unquote(currentPath);
            String memberPath = MvsQuoteNormalizer.normalize(dataset + "(" + memberName + ")");

            // Write empty content to create the member
            OutputStream os = ftpClient.getFtpClient().storeFileStream(memberPath);
            if (os != null) {
                os.close();
                ftpClient.getFtpClient().completePendingCommand();
                controller.refresh();
            } else {
                JOptionPane.showMessageDialog(mainPanel,
                        "Member konnte nicht angelegt werden:\n" + ftpClient.getReplyString(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Fehler beim Anlegen:\n" + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Delete selected entries with confirmation dialog.
     * Supports multi-selection.
     */
    private void deleteSelectedEntries() {
        List<MvsVirtualResource> selected = fileList.getSelectedValuesList();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Bitte erst Einträge auswählen.", "Hinweis", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Build confirmation message
        StringBuilder msg = new StringBuilder();
        if (selected.size() == 1) {
            MvsVirtualResource res = selected.get(0);
            msg.append("Wirklich löschen?\n\n");
            msg.append(res.isDirectory() ? "📁 " : "📄 ").append(res.getDisplayName());
        } else {
            msg.append(selected.size()).append(" Einträge wirklich löschen?\n\n");
            for (int i = 0; i < Math.min(selected.size(), 8); i++) {
                msg.append("  • ").append(selected.get(i).getDisplayName()).append("\n");
            }
            if (selected.size() > 8) {
                msg.append("  … und ").append(selected.size() - 8).append(" weitere\n");
            }
        }

        int confirm = JOptionPane.showConfirmDialog(mainPanel, msg.toString(),
                "Löschen bestätigen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        int success = 0;
        int failed = 0;
        StringBuilder errors = new StringBuilder();

        for (MvsVirtualResource res : selected) {
            try {
                String path = MvsQuoteNormalizer.normalize(res.getOpenPath());
                boolean deleted;
                if (res.isDirectory()) {
                    deleted = ftpClient.getFtpClient().removeDirectory(path);
                } else {
                    deleted = ftpClient.getFtpClient().deleteFile(path);
                }
                if (deleted) {
                    success++;
                } else {
                    failed++;
                    errors.append("• ").append(res.getDisplayName())
                            .append(" — ").append(ftpClient.getReplyString()).append("\n");
                }
            } catch (Exception e) {
                failed++;
                errors.append("• ").append(res.getDisplayName())
                        .append(" — ").append(e.getMessage()).append("\n");
            }
        }

        if (failed > 0) {
            JOptionPane.showMessageDialog(mainPanel,
                    success + " gelöscht, " + failed + " fehlgeschlagen:\n\n" + errors.toString(),
                    "Löschen", JOptionPane.WARNING_MESSAGE);
        }

        controller.refresh();
    }

    // ═══════════════════════════════════════════════════════════
    //  Filtering & Sorting
    // ═══════════════════════════════════════════════════════════

    private void applyFilter() {
        String filter = searchField.getText();
        controller.setFilter(filter);
    }

    /**
     * Apply local sort and blacklist filtering on the current view model.
     * Called when sort mode or hideBlacklisted changes.
     */
    private void applyLocalSort() {
        List<MvsVirtualResource> resources = new ArrayList<>(currentViewResources);

        // Apply blacklist filtering
        if (hideBlacklisted && host != null && !host.isEmpty()) {
            SecurityFilterService sfs = SecurityFilterService.getInstance();
            List<MvsVirtualResource> allowed = new ArrayList<>();
            for (MvsVirtualResource res : resources) {
                String prefixedPath = "ftp://" + host + "/" + res.getOpenPath();
                if (sfs.isAllowed("FTP", prefixedPath)) {
                    allowed.add(res);
                }
            }
            resources = allowed;
        }

        // Sort
        sortResources(resources);

        // Populate list
        listModel.clear();
        for (MvsVirtualResource res : resources) {
            listModel.addElement(res);
        }
    }

    private void sortResources(List<MvsVirtualResource> items) {
        Collections.sort(items, new Comparator<MvsVirtualResource>() {
            @Override
            public int compare(MvsVirtualResource a, MvsVirtualResource b) {
                // Directories first
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;

                switch (sortMode) {
                    case NAME_DESC:
                        return b.getDisplayName().compareToIgnoreCase(a.getDisplayName());
                    case SIZE_DESC:
                        return Long.compare(b.getSize(), a.getSize());
                    case DATE_DESC:
                        return Long.compare(b.getLastModified(), a.getLastModified());
                    case NAME_ASC:
                    default:
                        return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  State Persistence
    // ═══════════════════════════════════════════════════════════

    private void saveNavigatorState() {
        Settings settings = SettingsHelper.load();
        Map<String, String> state = settings.applicationState;

        state.put(STATE_PREFIX + "sortMode", sortMode.name());
        state.put(STATE_PREFIX + "showDetails", String.valueOf(showDetails));
        state.put(STATE_PREFIX + "sidebarVisible", String.valueOf(sidebarVisible));
        state.put(STATE_PREFIX + "hideBlacklisted", String.valueOf(hideBlacklisted));

        SettingsHelper.save(settings);
    }

    private void restoreNavigatorState() {
        Settings settings = SettingsHelper.load();
        Map<String, String> state = settings.applicationState;

        String sortVal = state.get(STATE_PREFIX + "sortMode");
        if (sortVal != null) {
            try {
                sortMode = SortMode.valueOf(sortVal);
            } catch (IllegalArgumentException ignored) {}
        }

        String detailsVal = state.get(STATE_PREFIX + "showDetails");
        if (detailsVal != null) showDetails = Boolean.parseBoolean(detailsVal);

        String sidebarVal = state.get(STATE_PREFIX + "sidebarVisible");
        if (sidebarVal != null) sidebarVisible = Boolean.parseBoolean(sidebarVal);

        String hideBlVal = state.get(STATE_PREFIX + "hideBlacklisted");
        if (hideBlVal != null) hideBlacklisted = Boolean.parseBoolean(hideBlVal);
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    /**
     * Resolve the base qualifier from a current path for auto-prefixing.
     * Strips quotes, wildcards, and trailing separators.
     * Example: "'USR1.*'" → "USR1", "'USR1.DATA'" → "USR1.DATA"
     */
    private String resolveBaseQualifier(String path) {
        if (path == null || path.trim().isEmpty()) return "";
        String base = MvsQuoteNormalizer.unquote(path).toUpperCase().trim();
        // Strip trailing wildcards and separators
        while (base.endsWith(".*") || base.endsWith("*") || base.endsWith(".")) {
            if (base.endsWith(".*")) base = base.substring(0, base.length() - 2);
            else if (base.endsWith("*")) base = base.substring(0, base.length() - 1);
            else if (base.endsWith(".")) base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    /**
     * Build a full dataset name from user input.
     * If input contains a dot, it's treated as a fully qualified name.
     * Otherwise, the current path context is prepended automatically.
     */
    private String buildDatasetName(String input) {
        String upper = input.toUpperCase().trim();
        if (upper.contains(".")) {
            // Fully qualified — user provided the full path
            return upper;
        }

        // Short name — prepend current context
        String base = resolveBaseQualifier(controller.getCurrentPath());
        if (base.isEmpty()) {
            return upper;
        }

        return base + "." + upper;
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

    // ═══════════════════════════════════════════════════════════
    //  Overlay helpers
    // ═══════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════
    //  MvsBrowserController.BrowserListener
    // ═══════════════════════════════════════════════════════════

    @Override
    public void onViewModelChanged(final List<MvsVirtualResource> viewModel) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                // Save full view model for local sort/filter
                currentViewResources = new ArrayList<>(viewModel);

                // Apply sort + blacklist filter + populate
                applyLocalSort();

                if (viewModel.isEmpty() && !controller.isLoading()) {
                    showOverlayMessage("Keine Einträge gefunden", Color.ORANGE.darker());
                } else {
                    hideOverlay();
                }

                // Update path field
                pathField.setText(controller.getCurrentPath());
                updateNavigationButtons();

                if (sidebarVisible) updateSidebarInfo();
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

    // ═══════════════════════════════════════════════════════════
    //  ConnectionTab interface
    // ═══════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════
    //  Cell Renderer
    // ═══════════════════════════════════════════════════════════

    private class MvsResourceCellRenderer extends DefaultListCellRenderer {
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

                if (showDetails) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(icon).append(" ").append(resource.getDisplayName());
                    String recfm = resource.getRecordFormat();
                    int lrecl = resource.getLogicalRecordLength();
                    long size = resource.getSize();
                    if (recfm != null && !recfm.isEmpty()) {
                        sb.append("  [").append(recfm);
                        if (lrecl > 0) sb.append("/").append(lrecl);
                        sb.append("]");
                    }
                    if (size > 0) {
                        sb.append("  (").append(formatSize(size)).append(")");
                    }
                    setText(sb.toString());
                } else {
                    setText(icon + " " + resource.getDisplayName());
                }
            }

            return this;
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
