package de.bund.zrb.ui.mail;

import de.bund.zrb.mail.infrastructure.FileSystemMailStore;
import de.bund.zrb.mail.infrastructure.MailMetadataIndex;
import de.bund.zrb.mail.infrastructure.PstMailboxReader;
import de.bund.zrb.mail.infrastructure.PstStderrFilter;
import de.bund.zrb.mail.model.*;
import de.bund.zrb.mail.port.MailStore;
import de.bund.zrb.mail.port.MailboxReader;
import de.bund.zrb.mail.service.MailService;
import de.bund.zrb.mail.usecase.ListMailboxItemsUseCase;
import de.bund.zrb.mail.usecase.ListMailboxesUseCase;
import de.bund.zrb.mail.usecase.OpenMailMessageUseCase;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.indexing.model.SourceType;
import de.bund.zrb.indexing.ui.IndexingSidebar;
import de.bund.zrb.model.Settings;
import de.bund.zrb.search.SearchResult;
import de.bund.zrb.search.SearchService;
import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.newApi.ui.ConnectionTab;
import de.zrb.bund.newApi.ui.FindBarPanel;
import de.zrb.bund.newApi.ui.Navigable;
import de.zrb.bund.newApi.ui.SearchBarPanel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * ConnectionTab for browsing local Outlook mail stores (OST/PST files).
 * Read-only: no write operations allowed.
 *
 * Navigation hierarchy:
 *   Mailbox list → Category page (Mail/Kalender/…) → Folder list → Messages (paged)
 */
public class MailConnectionTab implements ConnectionTab, Navigable {

    private static final Logger LOG = Logger.getLogger(MailConnectionTab.class.getName());
    private static final int PAGE_SIZE = 200;

    private static final String LOAD_MORE_MARKER = "⏬ Weitere Nachrichten laden…";

    private final TabbedPaneManager tabbedPaneManager;
    private final String mailStorePath;

    // UseCases
    private final ListMailboxesUseCase listMailboxesUseCase;
    private final ListMailboxItemsUseCase listMailboxItemsUseCase;
    private final OpenMailMessageUseCase openMailMessageUseCase;

    // UI
    private final JPanel mainPanel;
    private final JTextField pathField = new JTextField();
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> fileList = new JList<>(listModel);
    private final FindBarPanel searchBar = new FindBarPanel("Regex-Filter…");
    private final JLabel statusLabel = new JLabel(" ");

    // Toolbar: sort & mail search
    private static final String SORT_ORIGINAL = "Originalreihenfolge";
    private static final String SORT_DATE_DESC = "📅 Datum ↓ (neueste zuerst)";
    private static final String SORT_DATE_ASC  = "📅 Datum ↑ (älteste zuerst)";
    private final JComboBox<String> sortCombo = new JComboBox<>(new String[]{
            SORT_ORIGINAL, SORT_DATE_DESC, SORT_DATE_ASC});
    private final SearchBarPanel mailSearchBar = new SearchBarPanel("Mails durchsuchen…",
            "Volltextsuche über indizierte Mails (Lucene-Syntax)");

    // Navigation state
    private final List<String> backHistory = new ArrayList<>();
    private final List<String> forwardHistory = new ArrayList<>();
    private JButton backButton;
    private JButton forwardButton;
    private IndexingSidebar indexingSidebar;
    private boolean sidebarVisible = false;

    // Current view state
    private String currentMailboxPath = null;
    private String currentFolderPath = null;
    private MailboxCategory currentCategory = null;

    // Paging state
    private int currentOffset = 0;
    private int totalMessageCount = 0;
    private boolean hasMoreMessages = false;

    // Current items (for search & click resolution)
    private List<String> currentDisplayNames = new ArrayList<>();
    private List<MailboxRef> currentMailboxes = new ArrayList<>();
    private List<MailFolderRef> currentFolders = new ArrayList<>();
    private List<MailMessageHeader> currentMessages = new ArrayList<>();
    /** Maps display string → folder ref for click resolution (handles custom display strings). */
    private final java.util.Map<String, MailFolderRef> displayToFolder = new java.util.LinkedHashMap<>();
    /** Maps display string → message header for click resolution. */
    private final java.util.Map<String, MailMessageHeader> displayToMessage = new java.util.LinkedHashMap<>();
    /** Maps display string → metadata entry for click resolution (index-based mode). */
    private final java.util.Map<String, MailMetadataIndex.MailMetadataEntry> displayToMetadata = new java.util.LinkedHashMap<>();
    /** True when current view is driven by the Lucene metadata index (sorted mode). */
    private boolean indexMode = false;

    private enum ViewMode {
        MAILBOX_LIST,    // list of OST/PST files
        CATEGORY_LIST,   // category page of a mailbox (Mail, Kalender, …)
        FOLDER_LIST,     // folders of a category
        MESSAGE_LIST     // messages in a folder (+ sub-folders, paged)
    }
    private ViewMode viewMode = ViewMode.MAILBOX_LIST;

    // State persistence prefix
    private static final String STATE_PREFIX = "mail.nav.";

    public MailConnectionTab(TabbedPaneManager tabbedPaneManager, String mailStorePath) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.mailStorePath = mailStorePath;

        MailStore mailStore = new FileSystemMailStore();
        MailboxReader mailboxReader = new PstMailboxReader();
        this.listMailboxesUseCase = new ListMailboxesUseCase(mailStore);
        this.listMailboxItemsUseCase = new ListMailboxItemsUseCase(mailboxReader);
        this.openMailMessageUseCase = new OpenMailMessageUseCase(mailboxReader);

        restoreNavigatorState();

        this.mainPanel = new JPanel(new BorderLayout());
        buildUI();
        loadMailboxList();
    }

    private void buildUI() {
        JPanel pathPanel = new JPanel(new BorderLayout());

        JButton refreshButton = new JButton("🔄");
        refreshButton.setToolTipText("Aktualisieren");
        refreshButton.setMargin(new Insets(0, 0, 0, 0));
        refreshButton.setFont(refreshButton.getFont().deriveFont(Font.PLAIN, 18f));
        refreshButton.addActionListener(e -> refresh());

        backButton = new JButton("⏴");
        backButton.setToolTipText("Zurück");
        backButton.setMargin(new Insets(0, 0, 0, 0));
        backButton.setFont(backButton.getFont().deriveFont(Font.PLAIN, 20f));
        backButton.addActionListener(e -> navigateBack());
        backButton.setEnabled(false);

        forwardButton = new JButton("⏵");
        forwardButton.setToolTipText("Vorwärts");
        forwardButton.setMargin(new Insets(0, 0, 0, 0));
        forwardButton.setFont(forwardButton.getFont().deriveFont(Font.PLAIN, 20f));
        forwardButton.addActionListener(e -> navigateForward());
        forwardButton.setEnabled(false);

        JButton upButton = new JButton("⏶");
        upButton.setToolTipText("Eine Ebene nach oben");
        upButton.setMargin(new Insets(0, 0, 0, 0));
        upButton.setFont(upButton.getFont().deriveFont(Font.PLAIN, 20f));
        upButton.addActionListener(e -> navigateUp());

        JPanel rightButtons = new JPanel(new GridLayout(1, 3, 0, 0));
        rightButtons.add(backButton);
        rightButtons.add(forwardButton);
        rightButtons.add(upButton);

        pathPanel.add(refreshButton, BorderLayout.WEST);
        pathField.setEditable(false);
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(rightButtons, BorderLayout.EAST);

        // ── Toolbar: Sort dropdown | Mail search bar | Details toggle ──
        JPanel toolbarPanel = new JPanel(new BorderLayout(4, 0));
        toolbarPanel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        // Sort combo (left)
        sortCombo.setToolTipText("Sortierung der Nachrichten nach Datum");
        sortCombo.setMaximumRowCount(4);
        sortCombo.addActionListener(e -> applySortOrder());

        // Mail search bar (center, blue style)
        mailSearchBar.addSearchAction(e -> performMailSearch());
        mailSearchBar.getTextField().addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                    mailSearchBar.setText("");
                }
            }
        });

        // Details toggle (right)
        JToggleButton detailsButton = new JToggleButton("📊");
        detailsButton.setToolTipText("Indexierungs-Details anzeigen");
        detailsButton.setMargin(new Insets(0, 0, 0, 0));
        detailsButton.setFont(detailsButton.getFont().deriveFont(Font.PLAIN, 16f));
        detailsButton.setSelected(sidebarVisible);
        detailsButton.addActionListener(e -> toggleSidebar());

        JPanel sortPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        sortPanel.setOpaque(false);
        sortPanel.add(sortCombo);

        JPanel detailsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        detailsPanel.setOpaque(false);
        detailsPanel.add(detailsButton);

        toolbarPanel.add(sortPanel, BorderLayout.WEST);
        toolbarPanel.add(mailSearchBar, BorderLayout.CENTER);
        toolbarPanel.add(detailsPanel, BorderLayout.EAST);

        // ── Top area: path bar + toolbar ──
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(pathPanel, BorderLayout.NORTH);
        topPanel.add(toolbarPanel, BorderLayout.SOUTH);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        mainPanel.add(new JScrollPane(fileList), BorderLayout.CENTER);

        // Indexing sidebar (apply restored visibility)
        indexingSidebar = new IndexingSidebar(SourceType.MAIL);
        indexingSidebar.setVisible(sidebarVisible);
        mainPanel.add(indexingSidebar, BorderLayout.EAST);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(createFilterPanel(), BorderLayout.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);


        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                if (fileList.getSelectedIndex() < 0) return;
                handleDoubleClick();
            }
        });

        // Keyboard navigation: Enter, Left/Right arrows, circular Up/Down
        de.bund.zrb.ui.util.ListKeyboardNavigation.install(
                fileList, searchBar.getTextField(),
                this::handleDoubleClick,
                this::navigateBack,
                this::navigateForward
        );
        // Arrow keys in path field → jump into file list
        de.bund.zrb.ui.util.ListKeyboardNavigation.installFieldNavigation(pathField, fileList);
    }

    // ─── Navigation ───

    private void handleDoubleClick() {
        String selectedText = fileList.getSelectedValue();
        if (selectedText == null) return;

        // "Load more" action
        if (selectedText.startsWith(LOAD_MORE_MARKER)) {
            loadMoreMessages();
            return;
        }

        switch (viewMode) {
            case MAILBOX_LIST:
                for (MailboxRef ref : currentMailboxes) {
                    if (selectedText.equals("📬 " + ref.getDisplayName())) {
                        pushHistory();
                        loadCategoryPage(ref.getPath());
                        return;
                    }
                }
                break;

            case CATEGORY_LIST:
                for (MailboxCategory cat : MailboxCategory.values()) {
                    if (selectedText.equals(cat.getDisplayName())) {
                        pushHistory();
                        loadCategoryFolders(currentMailboxPath, cat);
                        return;
                    }
                }
                break;

            case FOLDER_LIST:
                if (displayToFolder.containsKey(selectedText)) {
                    MailFolderRef folder = displayToFolder.get(selectedText);
                    pushHistory();
                    loadFolderContents(currentMailboxPath, folder.getFolderPath());
                    return;
                }
                break;

            case MESSAGE_LIST:
                // Check folders first, then messages
                if (displayToFolder.containsKey(selectedText)) {
                    MailFolderRef folder = displayToFolder.get(selectedText);
                    pushHistory();
                    loadFolderContents(currentMailboxPath, folder.getFolderPath());
                    return;
                }
                if (displayToMessage.containsKey(selectedText)) {
                    MailMessageHeader msg = displayToMessage.get(selectedText);
                    openMailReadOnly(msg);
                    return;
                }
                if (displayToMetadata.containsKey(selectedText)) {
                    MailMetadataIndex.MailMetadataEntry metadata = displayToMetadata.get(selectedText);
                    openMailFromMetadata(metadata);
                    return;
                }
                break;
        }
    }

    private void pushHistory() {
        String state = encodeState();
        if (!state.isEmpty()) {
            backHistory.add(state);
            forwardHistory.clear();
        }
        updateNavigationButtons();
    }

    private void navigateUp() {
        switch (viewMode) {
            case MAILBOX_LIST:
                return;
            case CATEGORY_LIST:
                pushHistory();
                loadMailboxList();
                break;
            case FOLDER_LIST:
                pushHistory();
                loadCategoryPage(currentMailboxPath);
                break;
            case MESSAGE_LIST:
                pushHistory();
                if (currentFolderPath != null) {
                    int lastSlash = currentFolderPath.lastIndexOf('/');
                    if (lastSlash > 0) {
                        loadFolderContents(currentMailboxPath, currentFolderPath.substring(0, lastSlash));
                    } else if (currentCategory != null) {
                        loadCategoryFolders(currentMailboxPath, currentCategory);
                    } else {
                        loadCategoryPage(currentMailboxPath);
                    }
                } else {
                    loadCategoryPage(currentMailboxPath);
                }
                break;
        }
        updateNavigationButtons();
    }

    @Override
    public void navigateBack() {
        if (backHistory.isEmpty()) return;
        forwardHistory.add(encodeState());
        restoreState(backHistory.remove(backHistory.size() - 1));
        updateNavigationButtons();
    }

    @Override
    public void navigateForward() {
        if (forwardHistory.isEmpty()) return;
        backHistory.add(encodeState());
        restoreState(forwardHistory.remove(forwardHistory.size() - 1));
        updateNavigationButtons();
    }

    @Override
    public boolean canNavigateBack() {
        return !backHistory.isEmpty();
    }

    @Override
    public boolean canNavigateForward() {
        return !forwardHistory.isEmpty();
    }

    private void refresh() {
        restoreState(encodeState());
    }

    private String encodeState() {
        if (currentMailboxPath == null) return "store";
        if (viewMode == ViewMode.CATEGORY_LIST) return "cats:" + currentMailboxPath;
        if (viewMode == ViewMode.FOLDER_LIST && currentCategory != null) {
            return "catfold:" + currentMailboxPath + "#" + currentCategory.name();
        }
        if (currentFolderPath != null) return "folder:" + currentMailboxPath + "#" + currentFolderPath;
        return "cats:" + currentMailboxPath;
    }

    private void restoreState(String state) {
        if (state == null || "store".equals(state)) {
            loadMailboxList();
        } else if (state.startsWith("cats:")) {
            loadCategoryPage(state.substring(5));
        } else if (state.startsWith("catfold:")) {
            String rest = state.substring(8);
            int hash = rest.indexOf('#');
            String mbPath = rest.substring(0, hash);
            String catName = rest.substring(hash + 1);
            loadCategoryFolders(mbPath, MailboxCategory.valueOf(catName));
        } else if (state.startsWith("folder:")) {
            String rest = state.substring(7);
            int hash = rest.indexOf('#');
            String mbPath = rest.substring(0, hash);
            String folderPath = rest.substring(hash + 1);
            loadFolderContents(mbPath, folderPath);
        } else {
            loadMailboxList();
        }
    }

    private void updateNavigationButtons() {
        backButton.setEnabled(!backHistory.isEmpty());
        forwardButton.setEnabled(!forwardHistory.isEmpty());
    }

    // ─── Data Loading ───

    private void loadMailboxList() {
        this.currentMailboxPath = null;
        this.currentFolderPath = null;
        this.currentCategory = null;
        this.viewMode = ViewMode.MAILBOX_LIST;
        pathField.setText("mailstore: " + mailStorePath);
        searchBar.setText("");
        resetPaging();

        SwingWorker<List<MailboxRef>, Void> worker = new SwingWorker<List<MailboxRef>, Void>() {
            @Override
            protected List<MailboxRef> doInBackground() {
                return listMailboxesUseCase.execute(mailStorePath);
            }

            @Override
            protected void done() {
                try {
                    List<MailboxRef> mailboxes = get();
                    currentMailboxes = mailboxes;
                    currentFolders = Collections.emptyList();
                    currentMessages = Collections.emptyList();
                    rebuildDisplayList();

                    if (mailboxes.isEmpty()) {
                        statusLabel.setText("Keine OST/PST-Dateien gefunden in: " + mailStorePath);
                    } else {
                        for (MailboxRef ref : mailboxes) {
                            String display = "📬 " + ref.getDisplayName();
                            currentDisplayNames.add(display);
                            listModel.addElement(display);
                        }
                        statusLabel.setText(mailboxes.size() + " Postfach/Postfächer gefunden");
                    }
                    tabbedPaneManager.refreshStarForTab(MailConnectionTab.this);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Error listing mailboxes", e);
                    showError("Fehler beim Lesen der Postfächer:\n" + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    /**
     * Shows the category start page for a mailbox:
     *   📧 E-Mails
     *   📅 Kalender
     *   👥 Kontakte
     *   ✅ Aufgaben
     *   📝 Notizen
     */
    private void loadCategoryPage(String mailboxPath) {
        this.currentMailboxPath = mailboxPath;
        this.currentFolderPath = null;
        this.currentCategory = null;
        this.viewMode = ViewMode.CATEGORY_LIST;
        pathField.setText("mailbox: " + new java.io.File(mailboxPath).getName());
        searchBar.setText("");
        resetPaging();

        currentMailboxes = Collections.emptyList();
        currentFolders = Collections.emptyList();
        currentMessages = Collections.emptyList();
        rebuildDisplayList();

        for (MailboxCategory cat : MailboxCategory.values()) {
            String display = cat.getDisplayName();
            currentDisplayNames.add(display);
            listModel.addElement(display);
        }

        statusLabel.setText("Kategorie wählen");
        tabbedPaneManager.refreshStarForTab(this);
    }

    /**
     * Shows all folders of a given category (flat list).
     */
    private void loadCategoryFolders(String mailboxPath, MailboxCategory category) {
        this.currentMailboxPath = mailboxPath;
        this.currentFolderPath = null;
        this.currentCategory = category;
        this.viewMode = ViewMode.FOLDER_LIST;
        pathField.setText("mailbox: " + new java.io.File(mailboxPath).getName() + " ▸ " + category.getLabel());
        searchBar.setText("");
        statusLabel.setText("Lade Ordner…");
        resetPaging();

        SwingWorker<List<MailFolderRef>, Void> worker = new SwingWorker<List<MailFolderRef>, Void>() {
            @Override
            protected List<MailFolderRef> doInBackground() throws Exception {
                PstStderrFilter.Guard g = PstStderrFilter.install();
                try {
                    return listMailboxItemsUseCase.listFoldersByCategory(mailboxPath, category);
                } finally {
                    g.uninstall();
                }
            }

            @Override
            protected void done() {
                try {
                    List<MailFolderRef> folders = get();
                    currentMailboxes = Collections.emptyList();
                    currentFolders = folders;
                    currentMessages = Collections.emptyList();
                    rebuildDisplayList();

                    if (folders.isEmpty()) {
                        statusLabel.setText("Keine Ordner in Kategorie " + category.getLabel());
                    } else {
                        for (MailFolderRef f : folders) {
                            // Show with path for clarity in flat category view
                            String display = "📁 " + f.getFolderPath().substring(1) // remove leading /
                                    + (f.getItemCount() > 0 ? " (" + f.getItemCount() + ")" : "");
                            currentDisplayNames.add(display);
                            displayToFolder.put(display, f);
                            listModel.addElement(display);
                        }
                        statusLabel.setText(folders.size() + " Ordner");
                    }
                    tabbedPaneManager.refreshStarForTab(MailConnectionTab.this);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Error loading category folders", e);
                    handleLoadError(e);
                }
            }
        };
        worker.execute();
    }

    /**
     * Shows sub-folders + messages (paged) of a folder.
     */
    private void loadFolderContents(String mailboxPath, String folderPath) {
        this.currentMailboxPath = mailboxPath;
        this.currentFolderPath = folderPath;
        this.viewMode = ViewMode.MESSAGE_LIST;
        this.indexMode = false;
        pathField.setText("mailbox: " + new java.io.File(mailboxPath).getName() + " \u25B8 " + folderPath);
        searchBar.setText("");
        statusLabel.setText("Lade\u2026");
        resetPaging();
        if (sidebarVisible) updateSidebarPath();

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private List<MailFolderRef> folders = new ArrayList<>();
            private List<MailMessageHeader> messages = new ArrayList<>();
            private int msgCount = 0;
            private String error = null;

            @Override
            protected Void doInBackground() {
                PstStderrFilter.Guard g = PstStderrFilter.install();
                try {
                    folders = listMailboxItemsUseCase.listSubFolders(mailboxPath, folderPath);
                    msgCount = listMailboxItemsUseCase.getMessageCount(mailboxPath, folderPath);
                    messages = listMailboxItemsUseCase.listMessages(mailboxPath, folderPath, 0, PAGE_SIZE);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Error loading folder contents", e);
                    error = extractErrorMessage(e);
                } finally {
                    g.uninstall();
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    showError(error);
                    statusLabel.setText("Fehler");
                    return;
                }

                currentFolders = folders;
                currentMessages = messages;
                currentMailboxes = Collections.emptyList();
                totalMessageCount = msgCount;
                currentOffset = messages.size();
                hasMoreMessages = currentOffset < totalMessageCount;
                rebuildDisplayList();

                for (MailFolderRef f : folders) {
                    String display = f.toString();
                    currentDisplayNames.add(display);
                    displayToFolder.put(display, f);
                    listModel.addElement(display);
                }
                for (MailMessageHeader m : messages) {
                    String display = m.toString();
                    currentDisplayNames.add(display);
                    displayToMessage.put(display, m);
                    listModel.addElement(display);
                }
                if (hasMoreMessages && !messages.isEmpty()) {
                    String marker = LOAD_MORE_MARKER + " (" + currentOffset + "/" + totalMessageCount + ")";
                    currentDisplayNames.add(marker);
                    listModel.addElement(marker);
                }

                updateStatusText();
                tabbedPaneManager.refreshStarForTab(MailConnectionTab.this);
            }
        };
        worker.execute();
    }

    /**
     * Loads the next page of messages and appends to current list.
     */
    private void loadMoreMessages() {
        // Delegate to index-based loader when in sorted mode
        if (indexMode) {
            loadMoreFromIndex();
            return;
        }
        if (!hasMoreMessages || currentMailboxPath == null || currentFolderPath == null) return;

        statusLabel.setText("Lade weitere Nachrichten…");

        // Remove the "load more" marker
        if (!currentDisplayNames.isEmpty()) {
            String last = currentDisplayNames.get(currentDisplayNames.size() - 1);
            if (last.startsWith(LOAD_MORE_MARKER)) {
                currentDisplayNames.remove(currentDisplayNames.size() - 1);
                listModel.removeElement(last);
            }
        }

        final int offset = currentOffset;
        SwingWorker<List<MailMessageHeader>, Void> worker = new SwingWorker<List<MailMessageHeader>, Void>() {
            @Override
            protected List<MailMessageHeader> doInBackground() throws Exception {
                PstStderrFilter.Guard g = PstStderrFilter.install();
                try {
                    return listMailboxItemsUseCase.listMessages(currentMailboxPath, currentFolderPath,
                            offset, PAGE_SIZE);
                } finally {
                    g.uninstall();
                }
            }

            @Override
            protected void done() {
                try {
                    List<MailMessageHeader> newMessages = get();
                    currentMessages.addAll(newMessages);
                    currentOffset += newMessages.size();
                    hasMoreMessages = currentOffset < totalMessageCount && !newMessages.isEmpty();

                    for (MailMessageHeader m : newMessages) {
                        String display = m.toString();
                        currentDisplayNames.add(display);
                        displayToMessage.put(display, m);
                        listModel.addElement(display);
                    }

                    if (hasMoreMessages && !newMessages.isEmpty()) {
                        String marker = LOAD_MORE_MARKER + " (" + currentOffset + "/" + totalMessageCount + ")";
                        currentDisplayNames.add(marker);
                        listModel.addElement(marker);
                    }

                    updateStatusText();
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Error loading more messages", e);
                    showError("Fehler beim Laden:\n" + extractErrorMessage(e));
                }
            }
        };
        worker.execute();
    }

    private void resetPaging() {
        currentOffset = 0;
        totalMessageCount = 0;
        hasMoreMessages = false;
    }

    private void rebuildDisplayList() {
        currentDisplayNames = new ArrayList<>();
        displayToFolder.clear();
        displayToMessage.clear();
        displayToMetadata.clear();
        listModel.clear();
    }

    private void updateStatusText() {
        List<String> parts = new ArrayList<>();
        if (!currentFolders.isEmpty()) {
            parts.add(currentFolders.size() + " Ordner");
        }
        if (totalMessageCount > 0) {
            String msgPart = currentMessages.size() + " von " + totalMessageCount + " Nachrichten";
            parts.add(msgPart);
        } else if (!currentMessages.isEmpty()) {
            parts.add(currentMessages.size() + " Nachrichten");
        }
        if (parts.isEmpty()) {
            statusLabel.setText("Leer");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(parts.get(i));
            }
            statusLabel.setText(sb.toString());
        }
    }

    // ─── Mail Preview ───

    private void openMailReadOnly(MailMessageHeader header) {
        statusLabel.setText("Lade Nachricht…");
        mainPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<MailMessageContent, Void> worker = new SwingWorker<MailMessageContent, Void>() {
            @Override
            protected MailMessageContent doInBackground() throws Exception {
                PstStderrFilter.Guard g = PstStderrFilter.install();
                try {
                    return openMailMessageUseCase.execute(
                            currentMailboxPath, header.getFolderPath(), header.getDescriptorNodeId());
                } finally {
                    g.uninstall();
                }
            }

            @Override
            protected void done() {
                mainPanel.setCursor(Cursor.getDefaultCursor());
                try {
                    MailMessageContent content = get();
                    String title = content.getHeader().getSubject();
                    if (title == null || title.isEmpty()) title = "(kein Betreff)";

                    MailPreviewTab mailTab = new MailPreviewTab(content, currentMailboxPath);
                    tabbedPaneManager.addTab(mailTab);
                    statusLabel.setText("Nachricht geöffnet: " + title);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Error opening mail", e);
                    showError("Fehler beim Öffnen der Nachricht:\n" + extractErrorMessage(e));
                    statusLabel.setText("Fehler beim Öffnen");
                }
            }
        };
        worker.execute();
    }

    // ─── Filter/Search ───

    private JPanel createFilterPanel() {
        searchBar.getTextField().setToolTipText("<html>Regex-Filter für Einträge<br>Beispiel: <code>Rechnung</code></html>");

        searchBar.getTextField().getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applySearchFilter(); }
            public void removeUpdate(DocumentEvent e) { applySearchFilter(); }
            public void changedUpdate(DocumentEvent e) { applySearchFilter(); }
        });

        return searchBar;
    }

    private void applySearchFilter() {
        String regex = searchBar.getText().trim();
        listModel.clear();

        boolean hasMatch = false;
        for (String name : currentDisplayNames) {
            try {
                if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(name).find()) {
                    listModel.addElement(name);
                    hasMatch = true;
                }
            } catch (Exception e) {
                hasMatch = false;
                break;
            }
        }

        searchBar.getTextField().setBackground(hasMatch || regex.isEmpty()
                ? UIManager.getColor("TextField.background")
                : new Color(255, 200, 200));
    }

    // ─── Sort ───

    /**
     * Re-sort the current message list according to the selected sort order.
     * <p>
     * "Originalreihenfolge" → reload directly from PST (page-by-page, unsorted).
     * "Datum ↓/↑" → switch to index-based mode: query {@link MailMetadataIndex}
     * for ALL mails in the folder, sorted by delivery time, with paging.
     */
    private void applySortOrder() {
        if (viewMode != ViewMode.MESSAGE_LIST) return;
        if (currentMailboxPath == null || currentFolderPath == null) return;

        String selected = (String) sortCombo.getSelectedItem();

        if (SORT_ORIGINAL.equals(selected)) {
            // Switch back to PST-based unsorted view
            indexMode = false;
            loadFolderContents(currentMailboxPath, currentFolderPath);
            return;
        }

        // Sorted mode → use Lucene metadata index
        boolean ascending = SORT_DATE_ASC.equals(selected);
        loadFolderFromIndex(currentMailboxPath, currentFolderPath, ascending);
    }

    // ─── Index-based sorted loading ───

    /**
     * Load folder contents from the {@link MailMetadataIndex} (sorted by date).
     * Sub-folders are still loaded from PST (fast), but messages come from the index.
     * <p>
     * If the index has no data for this folder yet, triggers a fast metadata-only scan
     * (reads only mail headers, no full-text extraction) so the user can sort immediately.
     */
    private void loadFolderFromIndex(final String mailboxPath, final String folderPath,
                                     final boolean ascending) {
        statusLabel.setText("Lade sortiert aus Index…");

        new SwingWorker<Void, Void>() {
            private List<MailFolderRef> folders = new ArrayList<>();
            private List<MailMetadataIndex.MailMetadataEntry> metaEntries = new ArrayList<>();
            private int indexCount = 0;
            private String error = null;

            @Override
            protected Void doInBackground() {
                try {
                    // Sub-folders from PST (lightweight)
                    PstStderrFilter.Guard g = PstStderrFilter.install();
                    try {
                        folders = listMailboxItemsUseCase.listSubFolders(mailboxPath, folderPath);
                    } finally {
                        g.uninstall();
                    }

                    // Messages from Lucene metadata index
                    MailMetadataIndex idx = MailMetadataIndex.getInstance();
                    indexCount = idx.countByFolder(mailboxPath, folderPath);

                    if (indexCount == 0) {
                        // Index has no data for this folder yet → build it now (fast, header-only)
                        LOG.info("[MailConnectionTab] Index empty for " + folderPath + " — building on demand…");
                        int built = MailService.getInstance()
                                .ensureMetadataIndexForFolder(mailboxPath, folderPath);
                        indexCount = idx.countByFolder(mailboxPath, folderPath);
                        LOG.info("[MailConnectionTab] On-demand index built: " + built
                                + " entries, count now: " + indexCount);
                    }

                    if (indexCount > 0) {
                        metaEntries = idx.listByFolder(mailboxPath, folderPath,
                                ascending, 0, PAGE_SIZE);
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Error loading from index", e);
                    error = extractErrorMessage(e);
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    showError(error);
                    statusLabel.setText("Fehler");
                    return;
                }

                if (indexCount == 0) {
                    // Still empty after on-demand build — no messages in this folder
                    indexMode = true;
                    currentFolders = folders;
                    currentMessages = Collections.emptyList();
                    currentMailboxes = Collections.emptyList();
                    totalMessageCount = 0;
                    rebuildDisplayList();

                    for (MailFolderRef f : folders) {
                        String display = f.toString();
                        currentDisplayNames.add(display);
                        displayToFolder.put(display, f);
                        listModel.addElement(display);
                    }
                    statusLabel.setText("Leer");
                    tabbedPaneManager.refreshStarForTab(MailConnectionTab.this);
                    return;
                }

                // Switch to index mode
                indexMode = true;
                currentFolders = folders;
                currentMessages = Collections.emptyList(); // not used in index mode
                currentMailboxes = Collections.emptyList();
                totalMessageCount = indexCount;
                currentOffset = metaEntries.size();
                hasMoreMessages = currentOffset < totalMessageCount;
                rebuildDisplayList();

                // Sub-folders at top
                for (MailFolderRef f : folders) {
                    String display = f.toString();
                    currentDisplayNames.add(display);
                    displayToFolder.put(display, f);
                    listModel.addElement(display);
                }

                // Sorted messages from index
                for (MailMetadataIndex.MailMetadataEntry entry : metaEntries) {
                    String display = entry.toDisplayString();
                    currentDisplayNames.add(display);
                    displayToMetadata.put(display, entry);
                    listModel.addElement(display);
                }

                if (hasMoreMessages && !metaEntries.isEmpty()) {
                    String marker = LOAD_MORE_MARKER + " (" + currentOffset + "/" + totalMessageCount + ")";
                    currentDisplayNames.add(marker);
                    listModel.addElement(marker);
                }

                updateIndexStatusText();
                tabbedPaneManager.refreshStarForTab(MailConnectionTab.this);

                // Re-apply filter if active
                String filter = searchBar.getText().trim();
                if (!filter.isEmpty()) applySearchFilter();
            }
        }.execute();
    }

    /**
     * Load the next page of sorted messages from the metadata index.
     */
    private void loadMoreFromIndex() {
        if (!hasMoreMessages || currentMailboxPath == null || currentFolderPath == null) return;

        statusLabel.setText("Lade weitere sortierte Nachrichten…");

        // Remove the "load more" marker
        if (!currentDisplayNames.isEmpty()) {
            String last = currentDisplayNames.get(currentDisplayNames.size() - 1);
            if (last.startsWith(LOAD_MORE_MARKER)) {
                currentDisplayNames.remove(currentDisplayNames.size() - 1);
                listModel.removeElement(last);
            }
        }

        final int offset = currentOffset;
        final boolean ascending = SORT_DATE_ASC.equals(sortCombo.getSelectedItem());

        new SwingWorker<List<MailMetadataIndex.MailMetadataEntry>, Void>() {
            @Override
            protected List<MailMetadataIndex.MailMetadataEntry> doInBackground() {
                MailMetadataIndex idx = MailMetadataIndex.getInstance();
                return idx.listByFolder(currentMailboxPath, currentFolderPath,
                        ascending, offset, PAGE_SIZE);
            }

            @Override
            protected void done() {
                try {
                    List<MailMetadataIndex.MailMetadataEntry> newEntries = get();
                    currentOffset += newEntries.size();
                    hasMoreMessages = currentOffset < totalMessageCount && !newEntries.isEmpty();

                    for (MailMetadataIndex.MailMetadataEntry entry : newEntries) {
                        String display = entry.toDisplayString();
                        currentDisplayNames.add(display);
                        displayToMetadata.put(display, entry);
                        listModel.addElement(display);
                    }

                    if (hasMoreMessages && !newEntries.isEmpty()) {
                        String marker = LOAD_MORE_MARKER + " (" + currentOffset + "/" + totalMessageCount + ")";
                        currentDisplayNames.add(marker);
                        listModel.addElement(marker);
                    }

                    updateIndexStatusText();
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Error loading more from index", e);
                    showError("Fehler beim Laden:\n" + extractErrorMessage(e));
                }
            }
        }.execute();
    }

    private void updateIndexStatusText() {
        List<String> parts = new ArrayList<>();
        if (!currentFolders.isEmpty()) {
            parts.add(currentFolders.size() + " Ordner");
        }
        if (totalMessageCount > 0) {
            int shown = displayToMetadata.size();
            parts.add(shown + " von " + totalMessageCount + " Nachrichten (Index-sortiert)");
        }
        if (parts.isEmpty()) {
            statusLabel.setText("Leer");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(parts.get(i));
            }
            statusLabel.setText(sb.toString());
        }
    }

    /**
     * Open a mail from a metadata index entry (used in index-sorted mode).
     * Uses the same mailbox path, folder path, and descriptor node ID as the header-based version.
     */
    private void openMailFromMetadata(final MailMetadataIndex.MailMetadataEntry entry) {
        statusLabel.setText("Lade Nachricht…");
        mainPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new SwingWorker<MailMessageContent, Void>() {
            @Override
            protected MailMessageContent doInBackground() throws Exception {
                PstStderrFilter.Guard g = PstStderrFilter.install();
                try {
                    return openMailMessageUseCase.execute(
                            entry.mailboxPath, entry.folderPath, entry.nodeId);
                } finally {
                    g.uninstall();
                }
            }

            @Override
            protected void done() {
                mainPanel.setCursor(Cursor.getDefaultCursor());
                try {
                    MailMessageContent content = get();
                    String title = content.getHeader().getSubject();
                    if (title == null || title.isEmpty()) title = "(kein Betreff)";

                    MailPreviewTab mailTab = new MailPreviewTab(content, entry.mailboxPath);
                    tabbedPaneManager.addTab(mailTab);
                    statusLabel.setText("Nachricht geöffnet: " + title);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Error opening mail from index", e);
                    showError("Fehler beim Öffnen der Nachricht:\n" + extractErrorMessage(e));
                    statusLabel.setText("Fehler beim Öffnen");
                }
            }
        }.execute();
    }

    // ─── Mail Index Search (Lucene) ───

    /**
     * Perform a Lucene full-text search over indexed mails (the blue search bar).
     * Results are shown in the list, replacing the current folder view temporarily.
     */
    private void performMailSearch() {
        String query = mailSearchBar.getText().trim();
        if (query.isEmpty()) {
            // Empty query → restore current folder view
            restoreState(encodeState());
            return;
        }

        statusLabel.setText("🔍 Suche nach: \"" + query + "\"…");
        mainPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new SwingWorker<List<SearchResult>, Void>() {
            @Override
            protected List<SearchResult> doInBackground() {
                Set<SearchResult.SourceType> sources = new LinkedHashSet<>();
                sources.add(SearchResult.SourceType.MAIL);
                return SearchService.getInstance().search(query, sources, 200, false);
            }

            @Override
            protected void done() {
                mainPanel.setCursor(Cursor.getDefaultCursor());
                try {
                    List<SearchResult> results = get();
                    rebuildDisplayList();

                    if (results.isEmpty()) {
                        statusLabel.setText("Keine Treffer für: \"" + query + "\"");
                    } else {
                        for (SearchResult r : results) {
                            String display = "🔎 " + r.getDocumentName();
                            if (r.getSnippet() != null && !r.getSnippet().isEmpty()) {
                                String snippet = r.getSnippet().replace('\n', ' ');
                                if (snippet.length() > 80) snippet = snippet.substring(0, 80) + "…";
                                display += "  —  " + snippet;
                            }
                            currentDisplayNames.add(display);
                            listModel.addElement(display);
                        }
                        statusLabel.setText(results.size() + " Treffer für: \"" + query + "\"");
                    }
                    tabbedPaneManager.refreshStarForTab(MailConnectionTab.this);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Mail search failed", e);
                    statusLabel.setText("Suchfehler: " + e.getMessage());
                }
            }
        }.execute();
    }

    // ─── Helpers ───

    private void toggleSidebar() {
        sidebarVisible = !sidebarVisible;
        indexingSidebar.setVisible(sidebarVisible);
        if (sidebarVisible) {
            updateSidebarPath();
        }
        saveNavigatorState();
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    // ═══════════════════════════════════════════════════════════
    //  Navigator State Persistence
    // ═══════════════════════════════════════════════════════════

    private void saveNavigatorState() {
        Settings settings = SettingsHelper.load();
        java.util.Map<String, String> state = settings.applicationState;
        state.put(STATE_PREFIX + "sidebarVisible", String.valueOf(sidebarVisible));
        SettingsHelper.save(settings);
    }

    private void restoreNavigatorState() {
        Settings settings = SettingsHelper.load();
        java.util.Map<String, String> state = settings.applicationState;
        String sidebarVal = state.get(STATE_PREFIX + "sidebarVisible");
        if (sidebarVal != null) sidebarVisible = Boolean.parseBoolean(sidebarVal);
    }

    /**
     * Update the sidebar path based on current navigation context.
     * Format: "mailboxPath#folderPath" so the scanner can target exactly this folder.
     */
    private void updateSidebarPath() {
        if (currentMailboxPath != null && currentFolderPath != null) {
            // Specific folder in a mailbox
            indexingSidebar.setCurrentPath(currentMailboxPath + "#" + currentFolderPath);
        } else if (currentMailboxPath != null) {
            // Mailbox level (all folders)
            indexingSidebar.setCurrentPath(currentMailboxPath);
        } else {
            // Store level (all mailboxes)
            indexingSidebar.setCurrentPath(mailStorePath);
        }
    }


    private void showError(String message) {
        JOptionPane.showMessageDialog(mainPanel, message, "Fehler", JOptionPane.ERROR_MESSAGE);
    }

    private String extractErrorMessage(Exception e) {
        String msg = e.getMessage();
        if (e.getCause() != null && e.getCause().getMessage() != null) {
            msg = e.getCause().getMessage();
        }
        if (msg != null && (msg.toLowerCase().contains("lock")
                || msg.toLowerCase().contains("access denied")
                || msg.toLowerCase().contains("being used")
                || msg.toLowerCase().contains("zugriff"))) {
            msg = "OST kann nicht gelesen werden. Bitte Outlook schließen oder Postfach als PST exportieren.\n\n" + msg;
        }
        return msg;
    }

    private void handleLoadError(Exception e) {
        String msg = extractErrorMessage(e);
        showError(msg != null ? msg : "Unbekannter Fehler");
        statusLabel.setText("Fehler");
    }

    // ─── ConnectionTab Interface ───

    @Override
    public String getTitle() {
        return "📧 Mails";
    }

    @Override
    public String getTooltip() {
        return "Mail-Speicherort: " + mailStorePath;
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void onClose() { }

    @Override
    public void saveIfApplicable() { }

    @Override
    public String getContent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < listModel.size(); i++) {
            sb.append(listModel.get(i)).append("\n");
        }
        return sb.toString();
    }

    @Override
    public void markAsChanged() { }

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
        searchBar.focusAndSelectAll();
    }

    @Override
    public void searchFor(String searchPattern) {
        if (searchPattern == null) return;
        searchBar.setText(searchPattern.trim());
        applySearchFilter();
    }
}
