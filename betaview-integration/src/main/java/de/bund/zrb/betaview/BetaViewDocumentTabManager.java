package de.bund.zrb.betaview;

import de.zrb.bund.newApi.ui.FtpTab;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages BetaView document tabs within the MainframeMate TabbedPaneManager.
 *
 * This class replaces the document management logic from the original BetaViewSwingFrame.
 * It creates BetaViewDocumentTab instances and adds them to the TabbedPaneManager
 * instead of a separate DocumentTabbedPane.
 *
 * Usage:
 * 1. Create a BetaViewDocumentTabManager for a connection
 * 2. Set it as the DocumentOpenListener on BetaViewConnectionTab
 * 3. It automatically creates/manages document tabs
 */
public final class BetaViewDocumentTabManager implements BetaViewConnectionTab.DocumentOpenListener {

    /** Simple interface to add/remove tabs without depending on TabbedPaneManager directly. */
    public interface TabHost {
        void addTab(FtpTab tab);
        void removeTab(FtpTab tab);
    }

    private final BetaViewClient client;
    private final BetaViewSession session;
    private final String displayName;
    private final TabHost tabHost;

    /** Tracks open document tabs by key. */
    private final Map<String, BetaViewDocumentTab> openTabs = new LinkedHashMap<>();

    public BetaViewDocumentTabManager(BetaViewClient client, BetaViewSession session,
                                      String displayName, TabHost tabHost) {
        this.client = client;
        this.session = session;
        this.displayName = displayName;
        this.tabHost = tabHost;
    }

    // ======== DocumentOpenListener ========

    @Override
    public void onOpenDocument(String html, String action) {
        List<DocumentTab> tabs = DocumentTabParser.parse(html);

        DocumentTab activeTab = null;
        for (DocumentTab t : tabs) {
            getOrCreateTab(t);
            if (t.isActive()) activeTab = t;
        }
        if (activeTab == null) {
            activeTab = new DocumentTab("", "", action, "Dokument", "", action, true);
        }

        BetaViewDocumentTab docTab = getOrCreateTab(activeTab);
        docTab.loadDocument(html);
    }

    @Override
    public void onOpenBookmarkDocument(String html, SidebarPanel.BookmarkItem item) {
        List<DocumentTab> tabs = DocumentTabParser.parse(html);

        DocumentTab activeTab = null;
        for (DocumentTab t : tabs) {
            getOrCreateTab(t);
            if (t.isActive()) activeTab = t;
        }
        if (activeTab == null) {
            activeTab = new DocumentTab("", "", item.action(), item.name(), "", item.action(), true);
        }

        BetaViewDocumentTab docTab = getOrCreateTab(activeTab);
        docTab.loadDocument(html);
    }

    @Override
    public void onCloseAllTabs() {
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                client.getText(session, "closeAllDocuments.action");
                return null;
            }
            @Override protected void done() {
                try { get(); } catch (Exception ignored) {}
                for (BetaViewDocumentTab tab : openTabs.values()) {
                    tabHost.removeTab(tab);
                }
                openTabs.clear();
            }
        }.execute();
    }

    // ======== Lazy load server tabs ========

    /**
     * Fetches open server-side docbrowser tabs and creates lazy BetaViewDocumentTab instances.
     */
    public void fetchAndOpenServerTabs() {
        new SwingWorker<List<DocumentTab>, Void>() {
            @Override protected List<DocumentTab> doInBackground() throws Exception {
                LinkedHashMap<String, String> form = new LinkedHashMap<>();
                form.put("csrfToken", session.csrfToken().value());
                String json = client.postFormText(session, "getDBBookmarksJSON.json.action", form);
                return parseDocBrowserBookmarks(json);
            }
            @Override protected void done() {
                try {
                    List<DocumentTab> tabs = get();
                    for (DocumentTab tab : tabs) {
                        // Create tab but don't load content (lazy)
                        BetaViewDocumentTab docTab = getOrCreateTab(tab);
                        // NOT calling loadDocument → stays unloaded → lazy
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    // ======== Private ========

    private BetaViewDocumentTab getOrCreateTab(DocumentTab tab) {
        String key = tab.key();
        BetaViewDocumentTab existing = openTabs.get(key);
        if (existing != null) {
            return existing;
        }

        BetaViewDocumentTab docTab = new BetaViewDocumentTab(client, session, tab, displayName);
        openTabs.put(key, docTab);
        tabHost.addTab(docTab);
        return docTab;
    }

    /**
     * Parse the JSON from getDBBookmarksJSON.json.action and extract docbrowser tabs.
     * Manual parsing to avoid adding a JSON library dependency.
     */
    private static List<DocumentTab> parseDocBrowserBookmarks(String json) {
        List<DocumentTab> result = new java.util.ArrayList<>();
        if (json == null || json.isEmpty()) return result;

        int arrStart = json.indexOf("\"dbbookmarks\":[");
        if (arrStart < 0) return result;
        arrStart = json.indexOf('[', arrStart);
        int arrEnd = json.indexOf(']', arrStart);
        if (arrEnd < 0) return result;
        String arrContent = json.substring(arrStart + 1, arrEnd);

        String[] objects = arrContent.split("\\},\\s*\\{");
        for (String obj : objects) {
            obj = obj.trim();
            if (obj.startsWith("{")) obj = obj.substring(1);
            if (obj.endsWith("}")) obj = obj.substring(0, obj.length() - 1);

            String fav = extractJsonString(obj, "fav");
            if (!"<docbrowsertab>".equals(fav)) continue;

            String link   = extractJsonString(obj, "link");
            String linkID = extractJsonString(obj, "linkID");
            String name   = extractJsonString(obj, "name");
            String desc   = extractJsonString(obj, "description");

            String docId = "", favId = "";
            for (String part : link.split("&")) {
                if (part.startsWith("docid=")) docId = part.substring(6);
                else if (part.startsWith("favid=")) favId = part.substring(6);
            }

            String openAction = "opendocumentlink.action?" + link;
            result.add(new DocumentTab(docId, favId, linkID, name, desc, openAction, false));
        }
        return result;
    }

    private static String extractJsonString(String obj, String key) {
        String search = "\"" + key + "\":\"";
        int start = obj.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        int end = obj.indexOf('"', start);
        return end > start ? obj.substring(start, end) : "";
    }
}
