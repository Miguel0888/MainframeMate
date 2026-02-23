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
import de.bund.zrb.ui.preview.SplitPreviewTab;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;
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
 */
public class MailConnectionTab implements ConnectionTab {

    private static final Logger LOG = Logger.getLogger(MailConnectionTab.class.getName());
    private static final int MOUSE_BACK_BUTTON = 4;
    private static final int MOUSE_FORWARD_BUTTON = 5;

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
    private String currentMailboxPath = null;  // null = showing mailbox list
    private String currentFolderPath = null;   // null = showing top folders of a mailbox

    // Current items (for search & click resolution)
    private List<String> currentDisplayNames = new ArrayList<>();
    private List<MailboxRef> currentMailboxes = new ArrayList<>();
    private List<MailFolderRef> currentFolders = new ArrayList<>();
    private List<MailMessageHeader> currentMessages = new ArrayList<>();

    /**
     * Describes what kind of view is currently shown.
     */
    private enum ViewMode {
        MAILBOX_LIST,    // showing list of OST/PST files
        FOLDER_LIST,     // showing folders (+ messages) of a mailbox
        MESSAGE_LIST     // showing messages in a folder (+ sub-folders)
    }
    private ViewMode viewMode = ViewMode.MAILBOX_LIST;

    public MailConnectionTab(TabbedPaneManager tabbedPaneManager, String mailStorePath) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.mailStorePath = mailStorePath;

        // Wire up clean architecture
        MailStore mailStore = new FileSystemMailStore();
        MailboxReader mailboxReader = new PstMailboxReader();
        this.listMailboxesUseCase = new ListMailboxesUseCase(mailStore);
        this.listMailboxItemsUseCase = new ListMailboxItemsUseCase(mailboxReader);
        this.openMailMessageUseCase = new OpenMailMessageUseCase(mailboxReader);

        // Build UI
        this.mainPanel = new JPanel(new BorderLayout());
        buildUI();
        loadMailboxList();
    }

    private void buildUI() {
        // --- Top: path bar ---
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

        // --- Center: list ---
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        mainPanel.add(new JScrollPane(fileList), BorderLayout.CENTER);

        // --- Bottom: search + status ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(createFilterPanel(), BorderLayout.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Mouse listeners
        installMouseNavigation(pathField);
        installMouseNavigation(fileList);
        installMouseNavigation(mainPanel);

        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                int index = fileList.getSelectedIndex();
                if (index < 0) return;
                handleDoubleClick(index);
            }
        });
    }

    // ─── Navigation ───

    private void handleDoubleClick(int selectedIndex) {
        switch (viewMode) {
            case MAILBOX_LIST:
                if (selectedIndex < currentMailboxes.size()) {
                    MailboxRef ref = currentMailboxes.get(selectedIndex);
                    navigateTo(ref.getPath(), null);
                }
                break;
            case FOLDER_LIST:
            case MESSAGE_LIST:
                // Could be a folder or a message
                if (selectedIndex < currentFolders.size()) {
                    // It's a folder
                    MailFolderRef folder = currentFolders.get(selectedIndex);
                    navigateTo(currentMailboxPath, folder.getFolderPath());
                } else {
                    // It's a message
                    int msgIndex = selectedIndex - currentFolders.size();
                    if (msgIndex >= 0 && msgIndex < currentMessages.size()) {
                        openMailReadOnly(currentMessages.get(msgIndex));
                    }
                }
                break;
        }
    }

    private void navigateTo(String mailboxPath, String folderPath) {
        // Save current state to history
        String currentState = encodeState();
        if (!currentState.isEmpty()) {
            backHistory.add(currentState);
            forwardHistory.clear();
        }
        updateNavigationButtons();

        if (mailboxPath == null) {
            loadMailboxList();
        } else {
            loadMailboxContents(mailboxPath, folderPath);
        }
    }

    private void navigateUp() {
        String currentState = encodeState();

        if (viewMode == ViewMode.MAILBOX_LIST) {
            return; // already at top
        }

        if (currentFolderPath != null) {
            // Go up one folder level
            int lastSlash = currentFolderPath.lastIndexOf('/');
            if (lastSlash > 0) {
                String parentFolder = currentFolderPath.substring(0, lastSlash);
                backHistory.add(currentState);
                forwardHistory.clear();
                loadMailboxContents(currentMailboxPath, parentFolder);
            } else {
                // At top folder level -> show folder list of mailbox
                backHistory.add(currentState);
                forwardHistory.clear();
                loadMailboxContents(currentMailboxPath, null);
            }
        } else {
            // Currently showing top folders of a mailbox -> go to mailbox list
            backHistory.add(currentState);
            forwardHistory.clear();
            loadMailboxList();
        }
        updateNavigationButtons();
    }

    private void navigateBack() {
        if (backHistory.isEmpty()) return;
        String currentState = encodeState();
        forwardHistory.add(currentState);
        String prev = backHistory.remove(backHistory.size() - 1);
        restoreState(prev);
        updateNavigationButtons();
    }

    private void navigateForward() {
        if (forwardHistory.isEmpty()) return;
        String currentState = encodeState();
        backHistory.add(currentState);
        String next = forwardHistory.remove(forwardHistory.size() - 1);
        restoreState(next);
        updateNavigationButtons();
    }

    private void refresh() {
        switch (viewMode) {
            case MAILBOX_LIST:
                loadMailboxList();
                break;
            case FOLDER_LIST:
            case MESSAGE_LIST:
                loadMailboxContents(currentMailboxPath, currentFolderPath);
                break;
        }
    }

    private String encodeState() {
        if (currentMailboxPath == null) {
            return "mailstore:" + mailStorePath;
        }
        if (currentFolderPath == null) {
            return "mailbox:" + currentMailboxPath;
        }
        return "mailbox:" + currentMailboxPath + "#" + currentFolderPath;
    }

    private void restoreState(String state) {
        if (state == null || state.isEmpty()) {
            loadMailboxList();
            return;
        }
        if (state.startsWith("mailstore:")) {
            loadMailboxList();
        } else if (state.startsWith("mailbox:")) {
            String rest = state.substring("mailbox:".length());
            int hashIndex = rest.indexOf('#');
            if (hashIndex >= 0) {
                String mbPath = rest.substring(0, hashIndex);
                String folderPath = rest.substring(hashIndex + 1);
                loadMailboxContents(mbPath, folderPath);
            } else {
                loadMailboxContents(rest, null);
            }
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
        this.viewMode = ViewMode.MAILBOX_LIST;
        pathField.setText("mailstore: " + mailStorePath);
        searchField.setText("");

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
                    currentDisplayNames = new ArrayList<>();
                    listModel.clear();

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

    private void loadMailboxContents(String mailboxPath, String folderPath) {
        this.currentMailboxPath = mailboxPath;
        this.currentFolderPath = folderPath;
        this.viewMode = (folderPath == null) ? ViewMode.FOLDER_LIST : ViewMode.MESSAGE_LIST;

        String displayPath = "mailbox: " + new java.io.File(mailboxPath).getName();
        if (folderPath != null) {
            displayPath += " ▸ " + folderPath;
        }
        pathField.setText(displayPath);
        searchField.setText("");
        statusLabel.setText("Lade…");

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private List<MailFolderRef> folders = new ArrayList<>();
            private List<MailMessageHeader> messages = new ArrayList<>();
            private String error = null;

            @Override
            protected Void doInBackground() {
                try {
                    if (folderPath == null) {
                        // Top-level folders
                        folders = listMailboxItemsUseCase.listTopFolders(mailboxPath);
                    } else {
                        // Sub-folders + messages
                        folders = listMailboxItemsUseCase.listSubFolders(mailboxPath, folderPath);
                        messages = listMailboxItemsUseCase.listMessages(mailboxPath, folderPath);
                    }
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Error loading mailbox contents", e);
                    error = e.getMessage();
                    // Check for common lock issues
                    if (error != null && (error.contains("locked") || error.contains("access")
                            || error.contains("being used"))) {
                        error = "OST kann nicht gelesen werden. Bitte Outlook schließen oder Postfach als PST exportieren.\n\n"
                                + error;
                    }
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
                currentDisplayNames = new ArrayList<>();
                listModel.clear();

                // First folders, then messages
                for (MailFolderRef f : folders) {
                    String display = f.toString();
                    currentDisplayNames.add(display);
                    listModel.addElement(display);
                }
                for (MailMessageHeader m : messages) {
                    String display = m.toString();
                    currentDisplayNames.add(display);
                    listModel.addElement(display);
                }

                int total = folders.size() + messages.size();
                String status = "";
                if (!folders.isEmpty()) {
                    status += folders.size() + " Ordner";
                }
                if (!messages.isEmpty()) {
                    if (!status.isEmpty()) status += ", ";
                    status += messages.size() + " Nachrichten";
                }
                if (status.isEmpty()) {
                    status = "Leer";
                }
                statusLabel.setText(status);
                tabbedPaneManager.refreshStarForTab(MailConnectionTab.this);
            }
        };
        worker.execute();
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
                    String markdown = content.toMarkdown();
                    String title = content.getHeader().getSubject();
                    if (title == null || title.isEmpty()) {
                        title = "(kein Betreff)";
                    }

                    DocumentMetadata metadata = DocumentMetadata.builder()
                            .sourceName(title)
                            .build();

                    // Open as read-only preview (no save/edit possible)
                    SplitPreviewTab previewTab = new SplitPreviewTab(
                            "✉ " + title,
                            markdown,
                            metadata,
                            Collections.<String>emptyList(),
                            null,   // no Document model needed
                            false   // not remote
                    );
                    tabbedPaneManager.addTab(previewTab);
                    statusLabel.setText("Nachricht geöffnet: " + title);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Error opening mail", e);
                    showError("Fehler beim Öffnen der Nachricht:\n" + e.getMessage());
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

    private void showError(String message) {
        JOptionPane.showMessageDialog(mainPanel, message, "Fehler", JOptionPane.ERROR_MESSAGE);
    }

    // ─── ConnectionTab / FtpTab Interface ───

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
    public void onClose() {
        // nothing to close (read-only, no persistent connection)
    }

    @Override
    public void saveIfApplicable() {
        // read-only – nothing to save
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
}
