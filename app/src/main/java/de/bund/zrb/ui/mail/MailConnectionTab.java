package de.bund.zrb.ui.mail;

import de.bund.zrb.mail.infrastructure.FileSystemMailStore;
import de.bund.zrb.mail.infrastructure.PstMailboxReader;
import de.bund.zrb.mail.model.*;
import de.bund.zrb.mail.port.MailStore;
import de.bund.zrb.mail.port.MailboxReader;
import de.bund.zrb.mail.usecase.ListMailboxItemsUseCase;
import de.bund.zrb.mail.usecase.ListMailboxesUseCase;
import de.bund.zrb.mail.usecase.OpenMailMessageUseCase;
import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
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
public class MailConnectionTab implements ConnectionTab {

    private static final Logger LOG = Logger.getLogger(MailConnectionTab.class.getName());
    private static final int MOUSE_BACK_BUTTON = 4;
    private static final int MOUSE_FORWARD_BUTTON = 5;
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
    private final JTextField searchField = new JTextField();
    private final JLabel statusLabel = new JLabel(" ");

    // Navigation state
    private final List<String> backHistory = new ArrayList<>();
    private final List<String> forwardHistory = new ArrayList<>();
    private JButton backButton;
    private JButton forwardButton;

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

    private enum ViewMode {
        MAILBOX_LIST,    // list of OST/PST files
        CATEGORY_LIST,   // category page of a mailbox (Mail, Kalender, …)
        FOLDER_LIST,     // folders of a category
        MESSAGE_LIST     // messages in a folder (+ sub-folders, paged)
    }
    private ViewMode viewMode = ViewMode.MAILBOX_LIST;

    public MailConnectionTab(TabbedPaneManager tabbedPaneManager, String mailStorePath) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.mailStorePath = mailStorePath;

        MailStore mailStore = new FileSystemMailStore();
        MailboxReader mailboxReader = new PstMailboxReader();
        this.listMailboxesUseCase = new ListMailboxesUseCase(mailStore);
        this.listMailboxItemsUseCase = new ListMailboxItemsUseCase(mailboxReader);
        this.openMailMessageUseCase = new OpenMailMessageUseCase(mailboxReader);

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
        mainPanel.add(pathPanel, BorderLayout.NORTH);

        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        mainPanel.add(new JScrollPane(fileList), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(createFilterPanel(), BorderLayout.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        installMouseNavigation(pathField);
        installMouseNavigation(fileList);
        installMouseNavigation(mainPanel);

        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                if (fileList.getSelectedIndex() < 0) return;
                handleDoubleClick();
            }
        });
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

    private void navigateBack() {
        if (backHistory.isEmpty()) return;
        forwardHistory.add(encodeState());
        restoreState(backHistory.remove(backHistory.size() - 1));
        updateNavigationButtons();
    }

    private void navigateForward() {
        if (forwardHistory.isEmpty()) return;
        backHistory.add(encodeState());
        restoreState(forwardHistory.remove(forwardHistory.size() - 1));
        updateNavigationButtons();
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
        searchField.setText("");
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
        searchField.setText("");
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
        searchField.setText("");
        statusLabel.setText("Lade Ordner…");
        resetPaging();

        SwingWorker<List<MailFolderRef>, Void> worker = new SwingWorker<List<MailFolderRef>, Void>() {
            @Override
            protected List<MailFolderRef> doInBackground() throws Exception {
                return listMailboxItemsUseCase.listFoldersByCategory(mailboxPath, category);
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
        pathField.setText("mailbox: " + new java.io.File(mailboxPath).getName() + " ▸ " + folderPath);
        searchField.setText("");
        statusLabel.setText("Lade…");
        resetPaging();

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private List<MailFolderRef> folders = new ArrayList<>();
            private List<MailMessageHeader> messages = new ArrayList<>();
            private int msgCount = 0;
            private String error = null;

            @Override
            protected Void doInBackground() {
                try {
                    folders = listMailboxItemsUseCase.listSubFolders(mailboxPath, folderPath);
                    msgCount = listMailboxItemsUseCase.getMessageCount(mailboxPath, folderPath);
                    messages = listMailboxItemsUseCase.listMessages(mailboxPath, folderPath, 0, PAGE_SIZE);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Error loading folder contents", e);
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
                if (hasMoreMessages) {
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
                return listMailboxItemsUseCase.listMessages(currentMailboxPath, currentFolderPath,
                        offset, PAGE_SIZE);
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

                    if (hasMoreMessages) {
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
                return openMailMessageUseCase.execute(
                        currentMailboxPath, header.getFolderPath(), header.getDescriptorNodeId());
            }

            @Override
            protected void done() {
                mainPanel.setCursor(Cursor.getDefaultCursor());
                try {
                    MailMessageContent content = get();
                    String title = content.getHeader().getSubject();
                    if (title == null || title.isEmpty()) title = "(kein Betreff)";

                    MailPreviewTab mailTab = new MailPreviewTab(content);
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
        JPanel panel = new JPanel(new BorderLayout());
        searchField.setToolTipText("<html>Regex-Filter für Einträge<br>Beispiel: <code>Rechnung</code></html>");
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

        searchField.setBackground(hasMatch || regex.isEmpty()
                ? UIManager.getColor("TextField.background")
                : new Color(255, 200, 200));
    }

    // ─── Helpers ───

    private void installMouseNavigation(JComponent component) {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MOUSE_BACK_BUTTON) navigateBack();
                else if (e.getButton() == MOUSE_FORWARD_BUTTON) navigateForward();
            }
        });
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
        searchField.requestFocusInWindow();
        searchField.selectAll();
    }

    @Override
    public void searchFor(String searchPattern) {
        if (searchPattern == null) return;
        searchField.setText(searchPattern.trim());
        applySearchFilter();
    }
}
