package de.bund.zrb.betaview.ui;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sidebar with tabs: Gespeicherte Suchen, Gemerkte Dokumente, Volltextsuche, Notizen.
 */
public final class SidebarPanel extends JPanel {

    // --- Gespeicherte Suchen ---
    private final DefaultListModel<SavedItem> savedSearchModel = new DefaultListModel<>();
    private final JList<SavedItem> savedSearchList = new JList<>(savedSearchModel);

    // --- Gemerkte Dokumente ---
    private final DefaultListModel<BookmarkItem> bookmarkModel = new DefaultListModel<>();
    private final JList<BookmarkItem> bookmarkList = new JList<>(bookmarkModel);

    // --- Volltextsuche ---
    private final JTextField fulltextField = new JTextField(20);
    private final JButton fulltextSearchBtn = new JButton("Suchen");
    private final JTextArea fulltextResult = new JTextArea();

    // --- Notizen ---
    private final JTextArea notesArea = new JTextArea();

    // --- Buttons ---
    private final JButton refreshSavedBtn = new JButton("Aktualisieren");
    private final JButton refreshBookmarksBtn = new JButton("Aktualisieren");
    private final JButton refreshNotesBtn = new JButton("Aktualisieren");

    // --- Listeners ---
    private SidebarListener listener;

    public SidebarPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        // SidebarPanel now only provides saved-searches and bookmarks panels
        // which are embedded as filter tabs via getSavedSearchesPanel() / getBookmarksPanel().
    }

    // ======== Public API ========

    public void setSidebarListener(SidebarListener l) { this.listener = l; }

    public void loadSavedSearches(String html) {
        savedSearchModel.clear();
        if (html == null) return;
        Document doc = Jsoup.parse(html);
        // Each saved search is a card: div.el_wall_btn.we-saved-selection
        Elements items = doc.select(".el_wall_btn");
        System.out.println("DEBUG: loadSavedSearches - found " + items.size() + " .el_wall_btn elements");
        if (items.isEmpty()) {
            System.out.println("DEBUG: loadSavedSearches - HTML starts with: "
                    + html.substring(0, Math.min(500, html.length())));
        }
        for (Element item : items) {
            // Name: <div name="we_id_selection_name" class="el_wall_btn_name">
            Element nameEl = item.selectFirst("[name=we_id_selection_name]");
            if (nameEl == null) continue;

            // Description: <div name="selection_desc" class="el_wall_btn_desc">
            Element descEl = item.selectFirst("[name=selection_desc]");

            String name = nameEl.text().trim();
            String desc = descEl != null ? descEl.text().trim() : "";

            // The action comes from the embedded form:
            // <form id="we_id_savedSelectionParaForm4198" action="executeSavedSelection.action?ssID=4198">
            String action = "";
            String formTokenName = null;
            String formTokenValue = null;
            Element form = item.selectFirst("form[action*=executeSavedSelection]");
            if (form != null) {
                action = form.attr("action");
                // strip leading / if present (e.g. /betaview/executeSavedSelection.action?ssID=4198)
                if (action.contains("/")) {
                    int idx = action.lastIndexOf("/");
                    action = action.substring(idx + 1);
                }
                // extract struts token from hidden inputs in the form
                Element tokenNameInput = form.selectFirst("input[name=struts.token.name]");
                if (tokenNameInput != null) {
                    formTokenName = tokenNameInput.attr("value");
                    Element tokenValInput = form.selectFirst("input[name=" + formTokenName + "]");
                    if (tokenValInput != null) {
                        formTokenValue = tokenValInput.attr("value");
                    }
                }
            }

            // fallback: try to extract from javascript link
            if (action.isEmpty()) {
                Element link = item.selectFirst("a[href*=savedSelection]");
                if (link != null) {
                    String href = link.attr("href");
                    Matcher m = Pattern.compile("executeByID\\((\\d+)\\)").matcher(href);
                    if (m.find()) {
                        action = "executeSavedSelection.action?ssID=" + m.group(1);
                    }
                }
            }

            // extract the ssID from the form for the ssIDForm hidden field
            Element ssIdInput = item.selectFirst("input[name=ssIDForm]");
            String ssId = ssIdInput != null ? ssIdInput.attr("value") : "";

            savedSearchModel.addElement(new SavedItem(name, desc, action,
                    formTokenName, formTokenValue, ssId));
        }
    }

    public void loadBookmarks(String html) {
        bookmarkModel.clear();
        if (html == null) return;
        Document doc = Jsoup.parse(html);
        Elements items = doc.select(".el_wall_btn");
        for (Element item : items) {
            Element nameEl = item.selectFirst("[name=bookmark_name]");
            Element descEl = item.selectFirst("[name=bookmark_desc]");
            Element link = item.selectFirst("a.we_id_bookmark_link");
            if (nameEl == null) continue;

            String name = nameEl.text().trim();
            String desc = descEl != null ? descEl.text().trim() : "";
            String action = "";
            if (link != null) {
                String href = link.attr("href").trim();
                // e.g. opendocumentlink.action?docid=XXX&favid=YYY
                action = org.jsoup.parser.Parser.unescapeEntities(href, false);
            }

            // extract bookmark ID for edit/delete
            int bookmarkId = -1;
            Element editLink = item.selectFirst("a[id^=we_id_bookmark_edit]");
            if (editLink != null) {
                Matcher m = Pattern.compile("editByID\\((\\d+)\\)").matcher(editLink.attr("href"));
                if (m.find()) bookmarkId = Integer.parseInt(m.group(1));
            }

            bookmarkModel.addElement(new BookmarkItem(name, desc, action, bookmarkId));
        }
    }

    public void loadNotes(String html) {
        notesArea.setText("");
        if (html == null) return;
        Document doc = Jsoup.parse(html);
        // notes are in a list
        Elements notes = doc.select(".el_note_content, .we_note_text, textarea");
        StringBuilder sb = new StringBuilder();
        for (Element n : notes) {
            String text = n.text().trim();
            if (!text.isEmpty()) {
                sb.append(text).append("\n\n");
            }
        }
        if (sb.length() == 0) {
            sb.append(doc.text());
        }
        notesArea.setText(sb.toString().trim());
        notesArea.setCaretPosition(0);
    }

    public void loadFulltextResult(String html) {
        fulltextResult.setText("");
        if (html == null) return;
        Document doc = Jsoup.parse(html);
        fulltextResult.setText(doc.text());
        fulltextResult.setCaretPosition(0);
    }

    public String getFulltextQuery() {
        return fulltextField.getText().trim();
    }

    // ======== Tab Builders (public for embedding in other containers) ========

    private JPanel savedSearchesPanel;
    private JPanel bookmarksPanel;
    private JPanel fulltextPanel;
    private JPanel notesPanel;

    /** Returns the saved-searches panel (can be embedded in external tabs). */
    public JPanel getSavedSearchesPanel() {
        if (savedSearchesPanel == null) savedSearchesPanel = buildSavedSearchesTab();
        return savedSearchesPanel;
    }

    /** Returns the bookmarks panel (can be embedded in external tabs). */
    public JPanel getBookmarksPanel() {
        if (bookmarksPanel == null) bookmarksPanel = buildBookmarksTab();
        return bookmarksPanel;
    }

    /** Returns the fulltext search panel (can be embedded in external tabs). */
    public JPanel getFulltextPanel() {
        return new JPanel(); // no longer used in sidebar
    }

    /** Returns the notes panel (can be embedded in external tabs). */
    public JPanel getNotesPanel() {
        return new JPanel(); // no longer used in sidebar
    }

    private JPanel buildSavedSearchesTab() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        topBar.add(refreshSavedBtn);
        p.add(topBar, BorderLayout.NORTH);

        savedSearchList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        savedSearchList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && listener != null) {
                SavedItem sel = savedSearchList.getSelectedValue();
                if (sel != null) listener.onSavedSearchSelected(sel);
            }
        });
        p.add(new JScrollPane(savedSearchList), BorderLayout.CENTER);

        refreshSavedBtn.addActionListener(e -> {
            if (listener != null) listener.onRefreshSavedSearches();
        });

        return p;
    }

    private JPanel buildBookmarksTab() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        topBar.add(refreshBookmarksBtn);
        p.add(topBar, BorderLayout.NORTH);

        bookmarkList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bookmarkList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && listener != null) {
                BookmarkItem sel = bookmarkList.getSelectedValue();
                if (sel != null) listener.onBookmarkSelected(sel);
            }
        });
        p.add(new JScrollPane(bookmarkList), BorderLayout.CENTER);

        refreshBookmarksBtn.addActionListener(e -> {
            if (listener != null) listener.onRefreshBookmarks();
        });

        return p;
    }


    // ======== Helper ========

    private static String extractAction(String jsHref) {
        // extract URL from javascript:... calls
        Matcher m = Pattern.compile("'([^']+\\.action[^']*)'").matcher(jsHref);
        if (m.find()) return m.group(1);
        return jsHref;
    }

    // ======== Data classes ========

    public static final class SavedItem {
        private final String name;
        private final String description;
        private final String action;
        private final String tokenName;
        private final String tokenValue;
        private final String ssId;

        public SavedItem(String name, String description, String action,
                         String tokenName, String tokenValue, String ssId) {
            this.name = name;
            this.description = description;
            this.action = action;
            this.tokenName = tokenName;
            this.tokenValue = tokenValue;
            this.ssId = ssId;
        }

        public String name()        { return name; }
        public String description() { return description; }
        public String action()      { return action; }
        public String tokenName()   { return tokenName; }
        public String tokenValue()  { return tokenValue; }
        public String ssId()        { return ssId; }

        @Override public String toString() {
            return description.isEmpty() ? name : name + " â€“ " + description;
        }
    }

    public static final class BookmarkItem {
        private final String name;
        private final String description;
        private final String action;
        private final int bookmarkId;

        public BookmarkItem(String name, String description, String action, int bookmarkId) {
            this.name = name;
            this.description = description;
            this.action = action;
            this.bookmarkId = bookmarkId;
        }

        public String name()        { return name; }
        public String description() { return description; }
        public String action()      { return action; }
        public int    bookmarkId()  { return bookmarkId; }

        @Override public String toString() {
            return description.isEmpty() ? name : name + " â€“ " + description;
        }
    }

    // ======== Listener ========

    public interface SidebarListener {
        void onSavedSearchSelected(SavedItem item);
        void onBookmarkSelected(BookmarkItem item);
        void onRefreshSavedSearches();
        void onRefreshBookmarks();
    }
}

