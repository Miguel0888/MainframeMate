package de.bund.zrb.betaview.ui;

import de.bund.zrb.betaview.infrastructure.BetaViewClient;
import de.bund.zrb.betaview.infrastructure.BetaViewSession;
import de.bund.zrb.betaview.infrastructure.DownloadDocumentUseCase;
import de.bund.zrb.betaview.infrastructure.DownloadResult;
import de.bund.zrb.betaview.infrastructure.HiddenInputExtractor;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A MainframeMate tab that displays a single BetaView document using the
 * original {@link DocumentPreviewPanel} with full server-side paging,
 * download, bookmark and navigation support.
 * <p>
 * This is the MainframeMate equivalent of one tab inside BetaView's
 * {@code DocumentTabbedPane}.  It faithfully follows the same server
 * protocol (struts tokens, document.page.get.action, etc.).
 */
public final class BetaViewDocumentTab implements de.zrb.bund.newApi.ui.ConnectionTab {

    private final DocumentTab docTab;
    private final BetaViewClient client;
    private final BetaViewSession session;
    private final DocumentPreviewPanel previewPanel;
    private final JPanel mainPanel;

    // Callbacks set by the owner (BetaViewConnectionTab)
    private Runnable onCloseCallback;    // notify parent to send closeSingleDocument.action
    private Runnable onBookmarkCallback; // delegate bookmark creation

    /**
     * @param docTab  parsed server-side tab metadata (docId, favId, linkID, title, ...)
     * @param html    the full HTML of the document page (returned by the server when opening a document)
     * @param client  the HTTP client that talks to BetaView
     * @param session the current authenticated session
     */
    public BetaViewDocumentTab(DocumentTab docTab,
                               String html,
                               BetaViewClient client,
                               BetaViewSession session) {
        this.docTab = docTab;
        this.client = client;
        this.session = session;

        this.previewPanel = new DocumentPreviewPanel();
        this.mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(previewPanel, BorderLayout.CENTER);

        wirePreviewListeners();

        // Feed the initial HTML to the preview panel.
        // This extracts docId, page count, struts tokens and triggers the
        // first page load via the PageChangeListener below.
        previewPanel.loadDocument(html);
    }

    // ======== ConnectionTab contract ========

    @Override
    public String getTitle() {
        String t = docTab.title();
        if (t == null || t.isEmpty()) t = "Dokument";
        String ts = docTab.timestamp();
        if (ts != null && !ts.isEmpty()) t = ts + " " + t;
        return "\uD83D\uDCC4 " + t;
    }

    @Override
    public String getTooltip() {
        return "BetaView Dokument: " + docTab.title()
                + (docTab.docId() != null ? " [" + docTab.docId() + "]" : "");
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void onClose() {
        // Tell the server to close this document tab
        if (docTab.linkID() != null && !docTab.linkID().isEmpty()) {
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    try {
                        client.getText(session,
                                "closeSingleDocument.action?linkID=" + docTab.linkID());
                    } catch (Exception ignore) { }
                    return null;
                }
            }.execute();
        }
        if (onCloseCallback != null) onCloseCallback.run();
    }

    @Override
    public void saveIfApplicable() { /* read-only */ }

    @Override
    public String getContent() {
        return ""; // BetaView documents are server-side, no local content
    }

    @Override
    public void markAsChanged() { /* read-only */ }

    @Override
    public String getPath() {
        String docId = docTab.docId() != null ? docTab.docId() : "";
        String favId = docTab.favId() != null ? docTab.favId() : "";
        String linkID = docTab.linkID() != null ? docTab.linkID() : "";
        return "betaview://doc/" + docId + "?favid=" + favId + "&linkID=" + linkID;
    }

    @Override
    public Type getType() {
        return Type.CONNECTION;
    }

    @Override
    public void focusSearchField() { /* no search in doc preview */ }

    @Override
    public void searchFor(String searchPattern) { /* no search in doc preview */ }

    @Override
    public JPopupMenu createContextMenu(Runnable closeCallback) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem close = new JMenuItem("Tab schließen");
        close.addActionListener(e -> closeCallback.run());
        menu.add(close);
        return menu;
    }

    // ======== Public setters ========

    public void setOnCloseCallback(Runnable cb) { this.onCloseCallback = cb; }
    public void setOnBookmarkCallback(Runnable cb) { this.onBookmarkCallback = cb; }

    public DocumentTab getDocTab() { return docTab; }

    // ======== Wiring — this is where the server protocol lives ========

    private void wirePreviewListeners() {

        // ---- Page change: POST document.page.get.action with struts tokens ----
        previewPanel.setPageChangeListener(page -> {
            new SwingWorker<DownloadResult, Void>() {
                @Override
                protected DownloadResult doInBackground() throws Exception {
                    LinkedHashMap<String, String> form = new LinkedHashMap<String, String>();

                    String tokenName = previewPanel.getNavTokenName();
                    String tokenValue = previewPanel.getNavTokenValue();
                    if (tokenName != null && tokenValue != null) {
                        form.put("struts.token.name", tokenName);
                        form.put(tokenName, tokenValue);
                    }

                    String dbinst = previewPanel.getNavDbinst();
                    if (dbinst != null) form.put("dbinst", dbinst);

                    String docId = previewPanel.getCurrentDocumentId();
                    if (docId != null) form.put("docid", docId);

                    form.put("pageNumber", String.valueOf(page));

                    // POST the page request — server returns the page content (text/pdf)
                    DownloadResult result = client.postFormDownload(session,
                            "document.page.get.action", form);

                    // Re-fetch document frame to get fresh navigation tokens
                    try {
                        String refreshHtml = client.getText(session, "showResult.action");
                        previewPanel.updateNavigationTokens(refreshHtml);
                    } catch (Exception ignore) { /* token refresh best-effort */ }

                    return result;
                }

                @Override
                protected void done() {
                    try {
                        DownloadResult result = get();
                        previewPanel.showPageContent(result);
                    } catch (Exception ex) {
                        previewPanel.showPreviewMessage(
                                "Fehler beim Laden der Seite: " + ex.getMessage());
                    }
                }
            }.execute();
        });

        // ---- Refresh: reload the current document HTML ----
        previewPanel.setRefreshListener(() -> {
            if (docTab.openAction() == null || docTab.openAction().isEmpty()) return;
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() throws Exception {
                    return client.getText(session, docTab.openAction());
                }
                @Override
                protected void done() {
                    try {
                        previewPanel.loadDocument(get());
                    } catch (Exception ex) {
                        previewPanel.showPreviewMessage("Refresh fehlgeschlagen: " + ex.getMessage());
                    }
                }
            }.execute();
        });

        // ---- Download (quick) ----
        previewPanel.setDownloadListener(() -> {
            new SwingWorker<DownloadResult, Void>() {
                @Override
                protected DownloadResult doInBackground() throws Exception {
                    return new DownloadDocumentUseCase(client)
                            .execute(session, DownloadDocumentUseCase.PageSelection.ALL_PAGES);
                }
                @Override
                protected void done() {
                    try {
                        DownloadResult result = get();
                        saveDownloadResult(result);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(mainPanel,
                                "Download fehlgeschlagen:\n" + ex.getMessage(),
                                "Fehler", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        // ---- Download with options ----
        previewPanel.setDownloadOptionsListener(() -> {
            // For now, same as quick download with current page only
            new SwingWorker<DownloadResult, Void>() {
                @Override
                protected DownloadResult doInBackground() throws Exception {
                    return new DownloadDocumentUseCase(client)
                            .execute(session, DownloadDocumentUseCase.PageSelection.CURRENT_PAGE);
                }
                @Override
                protected void done() {
                    try {
                        saveDownloadResult(get());
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(mainPanel,
                                "Download fehlgeschlagen:\n" + ex.getMessage(),
                                "Fehler", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        // ---- Bookmark (merken) ----
        previewPanel.setBookmarkListener(() -> {
            if (onBookmarkCallback != null) onBookmarkCallback.run();
        });
    }

    private void saveDownloadResult(DownloadResult result) {
        if (result == null || result.data() == null || result.data().length == 0) {
            JOptionPane.showMessageDialog(mainPanel, "Kein Inhalt zum Speichern.",
                    "Download", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String suggestedName = result.filename();
        if (suggestedName == null || suggestedName.isEmpty()) {
            suggestedName = "betaview_document";
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(suggestedName));
        int choice = chooser.showSaveDialog(mainPanel);
        if (choice != JFileChooser.APPROVE_OPTION) return;

        File target = chooser.getSelectedFile();
        try (FileOutputStream fos = new FileOutputStream(target)) {
            fos.write(result.data());
            JOptionPane.showMessageDialog(mainPanel,
                    "Gespeichert: " + target.getAbsolutePath(),
                    "Download", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Speichern fehlgeschlagen:\n" + ex.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }
}
