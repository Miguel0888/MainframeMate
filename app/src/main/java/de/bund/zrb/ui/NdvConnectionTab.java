package de.bund.zrb.ui;

import de.bund.zrb.ndv.NdvClient;
import de.bund.zrb.ndv.NdvException;
import de.bund.zrb.ndv.NdvObjectInfo;
import de.bund.zrb.files.path.VirtualResourceRef;
import com.softwareag.naturalone.natural.pal.external.IPalTypeSystemFile;
import com.softwareag.naturalone.natural.pal.external.ObjectKind;
import de.zrb.bund.api.Bookmarkable;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * NDV (Natural Development Server) connection tab.
 * Allows browsing Natural libraries and objects via the NATSPOD protocol,
 * and opening source code in editor/preview tabs.
 *
 * Navigation levels:
 * 1. Libraries (root) - list of Natural libraries
 * 2. Objects - list of Natural objects in a library
 */
public class NdvConnectionTab implements ConnectionTab {

    private static final int MOUSE_BACK_BUTTON = 4;
    private static final int MOUSE_FORWARD_BUTTON = 5;

    private final TabbedPaneManager tabbedPaneManager;
    private final NdvClient client;
    private final String host;
    private final String user;

    // UI components
    private final JPanel mainPanel;
    private final JTextField pathField = new JTextField();
    private DefaultListModel<Object> listModel = new DefaultListModel<Object>();
    private final JList<Object> fileList;
    private final JTextField searchField = new JTextField();
    private final JLabel overlayLabel = new JLabel();
    private final JPanel listContainer;
    private final JLabel statusLabel = new JLabel(" ");
    private JButton backButton;
    private JButton forwardButton;

    // Navigation state
    private enum BrowseLevel { LIBRARIES, OBJECTS }
    private BrowseLevel currentLevel = BrowseLevel.LIBRARIES;
    private String currentLibrary = null;
    private IPalTypeSystemFile currentSysFile;

    // Full data for filtering
    private List<Object> allItems = new ArrayList<Object>();

    // History
    private final List<String> history = new ArrayList<String>();
    private int historyIndex = -1;

    public NdvConnectionTab(TabbedPaneManager tabbedPaneManager, NdvClient client) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.client = client;
        this.host = client.getHost();
        this.user = client.getUser();
        this.currentSysFile = resolveSystemFile(client);

        this.mainPanel = new JPanel(new BorderLayout());
        this.fileList = new JList<Object>(listModel);
        this.listContainer = new JPanel();

        fileList.setCellRenderer(new NdvCellRenderer());

        initUI();
        loadLibraries();
    }

    /**
     * Resolve the actual system file (FNAT/FUSER) from the server for listing operations.
     * Prefers FUSER (kind=2), falls back to first valid, then default (0,0,0).
     */
    private static IPalTypeSystemFile resolveSystemFile(NdvClient client) {
        try {
            IPalTypeSystemFile[] sysFiles = client.getSystemFiles();
            if (sysFiles != null && sysFiles.length > 0) {
                IPalTypeSystemFile fuserFile = null;
                IPalTypeSystemFile firstValid = null;

                for (int i = 0; i < sysFiles.length; i++) {
                    IPalTypeSystemFile s = sysFiles[i];
                    System.out.println("[NdvConnectionTab]   sysFile[" + i + "]: kind=" + s.getKind()
                            + ", dbid=" + s.getDatabaseId() + ", fnr=" + s.getFileNumber());
                    if (s.getKind() == IPalTypeSystemFile.FUSER && fuserFile == null) {
                        fuserFile = s;
                    }
                    if (firstValid == null && s.getDatabaseId() > 0 && s.getFileNumber() > 0) {
                        firstValid = s;
                    }
                }

                IPalTypeSystemFile chosen = fuserFile != null ? fuserFile
                        : firstValid != null ? firstValid : sysFiles[0];
                System.out.println("[NdvConnectionTab] Using system file: kind=" + chosen.getKind()
                        + ", dbid=" + chosen.getDatabaseId() + ", fnr=" + chosen.getFileNumber()
                        + " (out of " + sysFiles.length + " available)");
                return chosen;
            }
        } catch (Exception e) {
            System.err.println("[NdvConnectionTab] Could not resolve system files, using defaults: " + e.getMessage());
        }
        // Fallback to defaults
        return client.getDefaultSystemFile();
    }

    private void initUI() {
        // Path panel
        JPanel pathPanel = new JPanel(new BorderLayout());

        JButton refreshButton = new JButton("🔄");
        refreshButton.setToolTipText("Aktualisieren");
        refreshButton.addActionListener(e -> refresh());

        backButton = new JButton("⏴");
        backButton.setToolTipText("Zurück");
        backButton.setMargin(new Insets(0, 0, 0, 0));
        backButton.setFont(backButton.getFont().deriveFont(Font.PLAIN, 20f));
        backButton.addActionListener(e -> navigateBack());

        forwardButton = new JButton("⏵");
        forwardButton.setToolTipText("Vorwärts");
        forwardButton.setMargin(new Insets(0, 0, 0, 0));
        forwardButton.setFont(forwardButton.getFont().deriveFont(Font.PLAIN, 20f));
        forwardButton.addActionListener(e -> navigateForward());

        JButton goButton = new JButton("Öffnen");
        goButton.addActionListener(e -> navigateToPath());

        pathField.addActionListener(e -> navigateToPath());

        JPanel rightButtons = new JPanel(new GridLayout(1, 3, 0, 0));
        rightButtons.add(backButton);
        rightButtons.add(forwardButton);
        rightButtons.add(goButton);

        pathPanel.add(refreshButton, BorderLayout.WEST);
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(rightButtons, BorderLayout.EAST);

        // Overlay setup
        overlayLabel.setHorizontalAlignment(SwingConstants.CENTER);
        overlayLabel.setVerticalAlignment(SwingConstants.CENTER);
        overlayLabel.setFont(overlayLabel.getFont().deriveFont(Font.BOLD, 14f));
        overlayLabel.setOpaque(true);
        overlayLabel.setVisible(false);

        // List setup
        JScrollPane scrollPane = new JScrollPane(fileList);
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
        mainPanel.add(pathPanel, BorderLayout.NORTH);
        mainPanel.add(listContainer, BorderLayout.CENTER);
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        updateNavigationButtons();
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());

        JPanel filterPanel = new JPanel(new BorderLayout());
        filterPanel.add(new JLabel("🔎 "), BorderLayout.WEST);
        filterPanel.add(searchField, BorderLayout.CENTER);

        searchField.setToolTipText("Filter");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        statusLabel.setForeground(Color.GRAY);

        statusBar.add(filterPanel, BorderLayout.CENTER);
        statusBar.add(statusLabel, BorderLayout.EAST);

        return statusBar;
    }

    // ==================== Navigation ====================

    private void navigateToPath() {
        String path = pathField.getText().trim();
        if (path.isEmpty()) {
            loadLibraries();
            addToHistory("");
        } else {
            openLibrary(path);
            addToHistory(path);
        }
    }

    private void navigateBack() {
        if (historyIndex > 0) {
            historyIndex--;
            String path = history.get(historyIndex);
            if (path.isEmpty()) {
                loadLibraries();
            } else {
                openLibraryWithoutHistory(path);
            }
            updateNavigationButtons();
        } else if (currentLevel == BrowseLevel.OBJECTS) {
            loadLibraries();
            addToHistory("");
        }
    }

    private void navigateForward() {
        if (historyIndex < history.size() - 1) {
            historyIndex++;
            String path = history.get(historyIndex);
            if (path.isEmpty()) {
                loadLibraries();
            } else {
                openLibraryWithoutHistory(path);
            }
            updateNavigationButtons();
        }
    }

    private void addToHistory(String path) {
        // Remove forward history
        while (history.size() > historyIndex + 1) {
            history.remove(history.size() - 1);
        }
        history.add(path);
        historyIndex = history.size() - 1;
        updateNavigationButtons();
    }

    private void updateNavigationButtons() {
        backButton.setEnabled(historyIndex > 0 || currentLevel == BrowseLevel.OBJECTS);
        forwardButton.setEnabled(historyIndex < history.size() - 1);
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

    // ==================== Loading ====================

    private void loadLibraries() {
        currentLevel = BrowseLevel.LIBRARIES;
        currentLibrary = null;
        pathField.setText("");
        showOverlayMessage("Lade Bibliotheken...", Color.GRAY);
        statusLabel.setText("Laden...");

        SwingWorker<List<String>, Void> worker = new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return client.listLibraries(currentSysFile, "*");
            }

            @Override
            protected void done() {
                try {
                    List<String> libs = get();
                    allItems.clear();
                    allItems.addAll(libs);
                    applyFilter();
                    hideOverlay();
                    statusLabel.setText(libs.size() + " Bibliotheken");
                    if (libs.isEmpty()) {
                        showOverlayMessage("Keine Bibliotheken gefunden", new Color(200, 100, 0));
                    }
                } catch (Exception e) {
                    showOverlayMessage("Fehler: " + e.getMessage(), Color.RED);
                    statusLabel.setText("Fehler");
                    System.err.println("[NdvConnectionTab] Error loading libraries: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    /**
     * Navigate to a specific library (public entry point for bookmarks).
     */
    public void navigateToLibrary(String library) {
        openLibrary(library);
    }

    private void openLibrary(String library) {
        openLibraryInternal(library);
        addToHistory(library);
    }

    private void openLibraryWithoutHistory(String library) {
        openLibraryInternal(library);
    }

    private void openLibraryInternal(String library) {
        currentLevel = BrowseLevel.OBJECTS;
        currentLibrary = library.toUpperCase();
        pathField.setText(currentLibrary);
        showOverlayMessage("Lade Objekte aus " + currentLibrary + "...", Color.GRAY);
        statusLabel.setText("Laden...");
        allItems.clear();

        SwingWorker<Void, List<NdvObjectInfo>> worker = new SwingWorker<Void, List<NdvObjectInfo>>() {
            @Override
            protected Void doInBackground() throws Exception {
                // Logon to the library first
                try {
                    client.logon(currentLibrary);
                } catch (NdvException e) {
                    System.err.println("[NdvConnectionTab] Logon warning (may continue): " + e.getMessage());
                }
                client.listObjectsProgressive(currentSysFile, currentLibrary, "*",
                        ObjectKind.SOURCE, 0, new NdvClient.PageCallback() {
                            @Override
                            public boolean onPage(List<NdvObjectInfo> pageItems, int totalSoFar) {
                                publish(pageItems);
                                return !isCancelled();
                            }
                        });
                return null;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void process(List<List<NdvObjectInfo>> chunks) {
                for (List<NdvObjectInfo> page : chunks) {
                    allItems.addAll(page);
                }
                applyFilter();
                hideOverlay();
                statusLabel.setText(allItems.size() + " Objekte in " + currentLibrary + " (laden...)");
            }

            @Override
            protected void done() {
                try {
                    get(); // rethrow exceptions
                    applyFilter();
                    hideOverlay();
                    statusLabel.setText(allItems.size() + " Objekte in " + currentLibrary);
                    if (allItems.isEmpty()) {
                        showOverlayMessage("Keine Objekte in " + currentLibrary, new Color(200, 100, 0));
                    }
                } catch (Exception e) {
                    if (!allItems.isEmpty()) {
                        // Partial results available
                        applyFilter();
                        hideOverlay();
                        statusLabel.setText(allItems.size() + " Objekte (unvollständig)");
                    } else {
                        showOverlayMessage("Fehler: " + e.getMessage(), Color.RED);
                        statusLabel.setText("Fehler");
                    }
                    System.err.println("[NdvConnectionTab] Error loading objects: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void refresh() {
        if (currentLevel == BrowseLevel.LIBRARIES) {
            loadLibraries();
        } else if (currentLibrary != null) {
            openLibraryInternal(currentLibrary);
        }
    }

    // ==================== Double-click handling ====================

    private void handleDoubleClick() {
        Object selected = fileList.getSelectedValue();
        if (selected == null) return;

        if (selected instanceof String) {
            // Library name -> open library
            openLibrary((String) selected);
        } else if (selected instanceof NdvObjectInfo) {
            // Object -> download and open source
            NdvObjectInfo objInfo = (NdvObjectInfo) selected;
            openSource(objInfo);
        }
    }

    private void openSource(NdvObjectInfo objInfo) {
        if (!objInfo.hasSource()) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Dieses Objekt hat keinen Quellcode (Typ: " + objInfo.getTypeName() + ")",
                    "Kein Quellcode", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        statusLabel.setText("Lade Quellcode: " + objInfo.getName() + "...");
        mainPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return client.readSource(currentLibrary, objInfo);
            }

            @Override
            protected void done() {
                mainPanel.setCursor(Cursor.getDefaultCursor());
                try {
                    String source = get();
                    if (source == null || source.isEmpty()) {
                        JOptionPane.showMessageDialog(mainPanel,
                                "Leerer Quellcode für " + objInfo.getName(),
                                "Kein Inhalt", JOptionPane.WARNING_MESSAGE);
                        statusLabel.setText("Leerer Quellcode");
                        return;
                    }

                    // Build a VirtualResource with NDV backend (enables save via FileTabImpl)
                    String fullPath = currentLibrary + "/" + objInfo.getName() + "." + objInfo.getTypeExtension();
                    NdvResourceState ndvState = new NdvResourceState(client, currentLibrary, objInfo);
                    VirtualResource resource = new VirtualResource(
                            de.bund.zrb.files.path.VirtualResourceRef.of(fullPath),
                            VirtualResourceKind.FILE,
                            fullPath,
                            VirtualBackendType.NDV,
                            null,  // no FTP state
                            ndvState
                    );

                    // Open in FileTabImpl (editable, saveable, with search/compare)
                    FileTabImpl fileTab = new FileTabImpl(
                            tabbedPaneManager,
                            resource,
                            source,
                            null,   // sentenceType
                            null,   // searchPattern
                            false   // toCompare
                    );

                    tabbedPaneManager.addTab(fileTab);
                    statusLabel.setText("Geöffnet: " + objInfo.getName());
                } catch (Exception e) {
                    String msg = e.getMessage();
                    if (msg == null || msg.isEmpty()) {
                        msg = e.getClass().getSimpleName();
                    }
                    if (e instanceof java.util.concurrent.ExecutionException && e.getCause() != null) {
                        Throwable cause = e.getCause();
                        msg = cause.getClass().getSimpleName();
                        if (cause.getMessage() != null) {
                            msg += ": " + cause.getMessage();
                        }
                        if (cause instanceof NullPointerException) {
                            StackTraceElement[] st = cause.getStackTrace();
                            if (st.length > 0) {
                                msg += " at " + st[0].toString();
                            }
                        }
                    }
                    JOptionPane.showMessageDialog(mainPanel,
                            "Fehler beim Laden des Quellcodes:\n" + msg,
                            "Fehler", JOptionPane.ERROR_MESSAGE);
                    statusLabel.setText("Fehler beim Laden");
                    System.err.println("[NdvConnectionTab] Error reading source: " + msg);
                    if (e.getCause() != null) {
                        e.getCause().printStackTrace();
                    } else {
                        e.printStackTrace();
                    }
                }
            }
        };
        worker.execute();
    }

    // ==================== Filter ====================

    private void applyFilter() {
        String filter = searchField.getText().trim().toLowerCase();
        // Build new model off-EDT-event-storm: create a fresh model, populate, then swap
        DefaultListModel<Object> newModel = new DefaultListModel<Object>();
        for (Object item : allItems) {
            String text;
            if (item instanceof String) {
                text = ((String) item).toLowerCase();
            } else if (item instanceof NdvObjectInfo) {
                text = ((NdvObjectInfo) item).toString().toLowerCase();
            } else {
                text = item.toString().toLowerCase();
            }
            if (filter.isEmpty() || text.contains(filter)) {
                newModel.addElement(item);
            }
        }
        // Single atomic swap – only one UI repaint
        listModel = newModel;
        fileList.setModel(listModel);
    }

    // ==================== Overlay ====================

    private void showOverlayMessage(String message, Color color) {
        overlayLabel.setText(message);
        overlayLabel.setForeground(color);
        overlayLabel.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
        overlayLabel.setVisible(true);
    }

    private void hideOverlay() {
        overlayLabel.setVisible(false);
    }

    // ==================== Cell Renderer ====================

    private static class NdvCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                       int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof String) {
                // Library
                setText("📚 " + value);
                setFont(getFont().deriveFont(Font.BOLD));
            } else if (value instanceof NdvObjectInfo) {
                NdvObjectInfo obj = (NdvObjectInfo) value;
                setText(obj.getIcon() + " " + obj.getName() + "  [" + obj.getTypeName() + "]"
                        + (obj.getUser().isEmpty() ? "" : "  (" + obj.getUser() + ")"));
                setFont(getFont().deriveFont(Font.PLAIN));
            }

            return this;
        }
    }

    // ==================== ConnectionTab interface ====================

    @Override
    public String getTitle() {
        return "🔗 NDV: " + user + "@" + host;
    }

    @Override
    public String getTooltip() {
        return "NDV: " + user + "@" + host + ":" + client.getPort()
                + (currentLibrary != null ? " - " + currentLibrary : "");
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void onClose() {
        try {
            client.close();
        } catch (IOException e) {
            System.err.println("[NdvConnectionTab] Error closing: " + e.getMessage());
        }
    }

    @Override
    public void saveIfApplicable() {
        // Not applicable
    }

    @Override
    public String getContent() {
        return "";
    }

    @Override
    public void markAsChanged() {
        // Not applicable
    }

    @Override
    public String getPath() {
        if (currentLibrary != null) {
            return "ndv://" + host + "/" + currentLibrary;
        }
        return "ndv://" + host;
    }

    @Override
    public Bookmarkable.Type getType() {
        return Bookmarkable.Type.CONNECTION;
    }

    @Override
    public void focusSearchField() {
        searchField.requestFocusInWindow();
    }

    @Override
    public void searchFor(String searchPattern) {
        searchField.setText(searchPattern);
        applyFilter();
    }

    @Override
    public JPopupMenu createContextMenu(Runnable onCloseCallback) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
        closeItem.addActionListener(e -> onCloseCallback.run());

        JMenuItem refreshItem = new JMenuItem("🔄 Aktualisieren");
        refreshItem.addActionListener(e -> refresh());

        menu.add(refreshItem);
        menu.addSeparator();
        menu.add(closeItem);

        return menu;
    }
}

