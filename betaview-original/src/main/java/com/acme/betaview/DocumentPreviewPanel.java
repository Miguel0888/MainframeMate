package com.acme.betaview;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;

/**
 * Document preview panel with a compact toolbar at the top and the content
 * preview below.  The toolbar contains page navigation, action buttons, and
 * toggle buttons for sidebar drawers (notes, marked pages, fulltext search)
 * that expand downward when clicked and collapse again on a second click.
 */
public final class DocumentPreviewPanel extends JPanel {

    private static final String DRAWER_NONE     = "NONE";
    private static final String DRAWER_NOTES    = "NOTES";
    private static final String DRAWER_MARKS    = "MARKS";
    private static final String DRAWER_SEARCH   = "SEARCH";

    // ---- toolbar components ----
    private final JButton firstPageBtn;
    private final JButton prevPageBtn;
    private final JSpinner pageSpinner;
    private final JLabel pageCountLabel;
    private final JButton nextPageBtn;
    private final JButton lastPageBtn;
    private final JButton downloadButton;
    private final JButton downloadOptionsButton;
    private final JButton bookmarkButton;
    private final JButton printButton;
    private final JButton docInfoButton;
    private final JButton infoButton;
    private final JButton tabsButton;

    private final JButton refreshButton;

    // ---- sidebar toggle buttons ----
    private final JButton notesToggle;
    private final JButton marksToggle;
    private final JButton searchToggle;

    // ---- drawer panel (expandable) ----
    private final CardLayout drawerCards = new CardLayout();
    private final JPanel drawerPanel = new JPanel(drawerCards);
    private String activeDrawer = DRAWER_NONE;

    // ---- notes drawer ----
    private final JTextArea notesArea;
    private final JButton notesRefreshBtn;

    // ---- marked pages drawer ----
    private final JTextArea marksArea;
    private final JButton marksRefreshBtn;

    // ---- fulltext search drawer ----
    private final JTextField searchField;
    private final JButton searchBtn;
    private final JTextArea searchResultArea;

    // ---- info popup ----
    private final JPopupMenu infoPopup;
    private final JPanel infoContent;

    // ---- tabs popup ----
    private final JPopupMenu tabsPopup;
    private final JPanel tabsContent;

    // ---- preview ----
    private final PdfPreviewPanel preview;

    // ---- state ----
    private String currentDocumentId;
    private int currentPageCount;
    private int currentPage = 1;

    private String navTokenName;
    private String navTokenValue;
    private String navDbinst;

    private PageChangeListener pageChangeListener;
    private DownloadListener downloadListener;
    private Runnable downloadOptionsListener;
    private Runnable bookmarkListener;
    private Runnable printListener;
    private Runnable docInfoListener;
    private Runnable notesRefreshListener;
    private Runnable marksRefreshListener;
    private Runnable refreshListener;
    private Runnable tabsRefreshListener;
    private FulltextSearchListener fulltextSearchListener;

    public DocumentPreviewPanel() {
        setLayout(new BorderLayout());

        // ======== Toolbar ========
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        // -- refresh --
        refreshButton = toolbarButton("\u21BB", "Tab neu laden");
        toolbar.add(refreshButton);
        toolbar.add(toolbarSeparator());

        // -- page navigation --
        firstPageBtn = toolbarButton("\u23EE", "Erste Seite");
        prevPageBtn  = toolbarButton("\u25C0", "Vorherige Seite");

        pageSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1, 1));
        pageSpinner.setPreferredSize(new Dimension(55, 24));

        pageCountLabel = new JLabel("/ 1");
        pageCountLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));

        nextPageBtn = toolbarButton("\u25B6", "Nächste Seite");
        lastPageBtn = toolbarButton("\u23ED", "Letzte Seite");

        toolbar.add(firstPageBtn);
        toolbar.add(prevPageBtn);
        toolbar.add(pageSpinner);
        toolbar.add(pageCountLabel);
        toolbar.add(nextPageBtn);
        toolbar.add(lastPageBtn);

        toolbar.add(toolbarSeparator());

        // -- download --
        downloadButton = toolbarButton("\u2B07", "Herunterladen (Schnell)");
        downloadButton.setText("\u2B07");
        toolbar.add(downloadButton);

        downloadOptionsButton = toolbarButton("\u2B07\u2699", "Herunterladen mit Optionen");
        downloadOptionsButton.setText("\u2B07+");
        toolbar.add(downloadOptionsButton);

        toolbar.add(toolbarSeparator());

        // -- bookmark (merken) --
        bookmarkButton = toolbarButton("\u2606", "Dokument merken");
        bookmarkButton.setText("\u2606 Merken");
        toolbar.add(bookmarkButton);

        toolbar.add(toolbarSeparator());

        // -- print --
        printButton = toolbarButton("", "Druckinformationen");
        printButton.setText("Drucken");
        printButton.setEnabled(false);
        toolbar.add(printButton);

        // -- doc info --
        docInfoButton = toolbarButton("", "Dokumentinformationen (Detail)");
        docInfoButton.setText("Dok-Info");
        toolbar.add(docInfoButton);

        // -- quick info --
        infoButton = toolbarButton("\u2139", "Kurzinfo anzeigen");
        toolbar.add(infoButton);

        toolbar.add(toolbarSeparator());

        // -- sidebar toggle buttons (disabled – not yet implemented) --
        notesToggle  = toolbarToggle("\uD83D\uDDD2 Notizen", "Notizen ein-/ausblenden");
        marksToggle  = toolbarToggle("\u2691 Markiert", "Markierte Seiten ein-/ausblenden");
        searchToggle = toolbarToggle("\uD83D\uDD0D Suche", "Volltextsuche ein-/ausblenden");
        notesToggle.setEnabled(false);
        marksToggle.setEnabled(false);
        searchToggle.setEnabled(false);
        toolbar.add(notesToggle);
        toolbar.add(marksToggle);
        toolbar.add(searchToggle);

        // -- tabs icon (right-aligned) --
        tabsButton = toolbarButton("\uD83D\uDCCB", "Offene Tabs anzeigen");
        JPanel toolbarWrapper = new JPanel(new BorderLayout());
        toolbarWrapper.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
        toolbar.setBorder(null); // border moves to wrapper
        toolbarWrapper.add(toolbar, BorderLayout.CENTER);
        JPanel tabsBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        tabsBtnPanel.add(tabsButton);
        toolbarWrapper.add(tabsBtnPanel, BorderLayout.EAST);

        // ======== Drawer Panel (expandable below toolbar) ========
        drawerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        // empty card
        drawerPanel.add(new JPanel() {{
            setPreferredSize(new Dimension(0, 0));
        }}, DRAWER_NONE);

        // notes card
        notesArea = new JTextArea(4, 40);
        notesArea.setEditable(false);
        notesArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesRefreshBtn = new JButton("Aktualisieren");
        JPanel notesCard = new JPanel(new BorderLayout(4, 2));
        notesCard.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        notesCard.add(notesRefreshBtn, BorderLayout.NORTH);
        notesCard.add(new JScrollPane(notesArea), BorderLayout.CENTER);
        drawerPanel.add(notesCard, DRAWER_NOTES);

        // marked pages card
        marksArea = new JTextArea(4, 40);
        marksArea.setEditable(false);
        marksArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        marksArea.setLineWrap(true);
        marksArea.setWrapStyleWord(true);
        marksRefreshBtn = new JButton("Aktualisieren");
        JPanel marksCard = new JPanel(new BorderLayout(4, 2));
        marksCard.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        marksCard.add(marksRefreshBtn, BorderLayout.NORTH);
        marksCard.add(new JScrollPane(marksArea), BorderLayout.CENTER);
        drawerPanel.add(marksCard, DRAWER_MARKS);

        // fulltext search card
        searchField = new JTextField(20);
        searchBtn = new JButton("Suchen");
        searchResultArea = new JTextArea(4, 40);
        searchResultArea.setEditable(false);
        searchResultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        searchResultArea.setLineWrap(true);
        searchResultArea.setWrapStyleWord(true);
        JPanel searchTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        searchTop.add(new JLabel("Suche:"));
        searchTop.add(searchField);
        searchTop.add(searchBtn);
        JPanel searchCard = new JPanel(new BorderLayout(4, 2));
        searchCard.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        searchCard.add(searchTop, BorderLayout.NORTH);
        searchCard.add(new JScrollPane(searchResultArea), BorderLayout.CENTER);
        drawerPanel.add(searchCard, DRAWER_SEARCH);

        drawerCards.show(drawerPanel, DRAWER_NONE);
        drawerPanel.setVisible(false);

        // ======== Top area: toolbar + drawer ========
        JPanel topArea = new JPanel(new BorderLayout());
        topArea.add(toolbarWrapper, BorderLayout.NORTH);
        topArea.add(drawerPanel, BorderLayout.CENTER);
        add(topArea, BorderLayout.NORTH);

        // ======== Info popup ========
        infoPopup = new JPopupMenu();
        infoContent = new JPanel(new GridBagLayout());
        infoContent.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        infoPopup.add(infoContent);

        // ======== Tabs popup ========
        tabsPopup = new JPopupMenu();
        tabsContent = new JPanel(new GridBagLayout());
        tabsContent.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        tabsPopup.add(tabsContent);

        // ======== Preview ========
        preview = new PdfPreviewPanel();
        add(preview, BorderLayout.CENTER);

        // ======== Wiring ========
        refreshButton.addActionListener(e -> {
            if (refreshListener != null) refreshListener.run();
        });
        firstPageBtn.addActionListener(e -> goToPage(1));
        prevPageBtn.addActionListener(e -> goToPage(currentPage - 1));
        nextPageBtn.addActionListener(e -> goToPage(currentPage + 1));
        lastPageBtn.addActionListener(e -> goToPage(currentPageCount));
        pageSpinner.addChangeListener(e -> goToPage((int) pageSpinner.getValue()));

        downloadButton.addActionListener(e -> {
            if (downloadListener != null) downloadListener.onDownload();
        });
        downloadOptionsButton.addActionListener(e -> {
            if (downloadOptionsListener != null) downloadOptionsListener.run();
        });
        bookmarkButton.addActionListener(e -> {
            if (bookmarkListener != null) bookmarkListener.run();
        });
        printButton.addActionListener(e -> {
            if (printListener != null) printListener.run();
        });
        docInfoButton.addActionListener(e -> {
            if (docInfoListener != null) docInfoListener.run();
        });

        infoButton.addActionListener(e ->
                showInfoPopup(infoButton, 0, infoButton.getHeight()));

        tabsButton.addActionListener(e -> {
            if (tabsRefreshListener != null) tabsRefreshListener.run();
            if (tabsContent.getComponentCount() > 0)
                tabsPopup.show(tabsButton, 0, tabsButton.getHeight());
        });

        // sidebar toggles
        notesToggle.addActionListener(e -> toggleDrawer(DRAWER_NOTES));
        marksToggle.addActionListener(e -> toggleDrawer(DRAWER_MARKS));
        searchToggle.addActionListener(e -> toggleDrawer(DRAWER_SEARCH));

        notesRefreshBtn.addActionListener(e -> {
            if (notesRefreshListener != null) notesRefreshListener.run();
        });
        marksRefreshBtn.addActionListener(e -> {
            if (marksRefreshListener != null) marksRefreshListener.run();
        });
        searchBtn.addActionListener(e -> fireFulltextSearch());
        searchField.addActionListener(e -> fireFulltextSearch());

        setNavigationEnabled(false);
    }

    // ================================================================ public API

    public void loadDocument(String html) {
        if (html == null || html.trim().isEmpty()) {
            clearDocument();
            return;
        }

        Document doc = Jsoup.parse(html);

        // --- document ID ---
        Element docIdEl = doc.selectFirst("#we-documentid");
        currentDocumentId = docIdEl != null ? docIdEl.attr("value") : null;

        // --- page count ---
        Element numPages = doc.selectFirst("#numberPages");
        currentPageCount = 1;
        if (numPages != null) {
            try { currentPageCount = Integer.parseInt(numPages.attr("value")); } catch (Exception ignored) {}
        }

        // --- Struts navigation form context ---
        Element navForm = doc.selectFirst("#we_id_doc_navigation_form");
        if (navForm != null) {
            Map<String, String> hidden = HiddenInputExtractor.extractHiddenInputs(navForm.outerHtml());
            navTokenName = hidden.get("struts.token.name");
            if (navTokenName != null) {
                navTokenValue = hidden.get(navTokenName);
            }
            navDbinst = hidden.get("dbinst");
        }

        // --- build info popup content ---
        rebuildInfoContent(doc);


        // --- configure spinner ---
        currentPage = 1;
        SpinnerNumberModel model = (SpinnerNumberModel) pageSpinner.getModel();
        model.setMinimum(1);
        model.setMaximum(currentPageCount);
        model.setValue(1);
        pageCountLabel.setText("/ " + currentPageCount);
        setNavigationEnabled(true);
        updateNavButtons();

        // --- trigger first page load ---
        if (pageChangeListener != null) {
            pageChangeListener.onPageChange(1);
        } else {
            preview.showMessage("Warte auf Seitenvorschau...");
        }
    }

    public void showPageContent(DownloadResult result) { preview.showContent(result); }
    public void showPreviewText(String text)           { preview.showPlainText(text); }
    public void showPreviewMessage(String msg)         { preview.showMessage(msg); }

    public void clearDocument() {
        pageCountLabel.setText("/ 1");
        pageSpinner.setValue(1);
        preview.clear();
        currentDocumentId = null;
        currentPageCount = 0;
        currentPage = 1;
        navTokenName = null;
        navTokenValue = null;
        navDbinst = null;
        infoContent.removeAll();
        setNavigationEnabled(false);
    }

    // ---- getters ----
    public String  getCurrentDocumentId() { return currentDocumentId; }
    public int     getCurrentPageCount()  { return currentPageCount; }
    public int     getCurrentPage()       { return currentPage; }
    public String  getNavTokenName()      { return navTokenName; }
    public String  getNavTokenValue()     { return navTokenValue; }
    public String  getNavDbinst()         { return navDbinst; }

    public void updateNavigationTokens(String html) {
        if (html == null) return;
        Document doc = Jsoup.parse(html);
        Element navForm = doc.selectFirst("#we_id_doc_navigation_form");
        if (navForm != null) {
            Map<String, String> hidden = HiddenInputExtractor.extractHiddenInputs(navForm.outerHtml());
            navTokenName = hidden.get("struts.token.name");
            if (navTokenName != null) {
                navTokenValue = hidden.get(navTokenName);
            }
            String db = hidden.get("dbinst");
            if (db != null) navDbinst = db;
        }
    }

    // ---- listeners ----
    public void setPageChangeListener(PageChangeListener l) { this.pageChangeListener = l; }
    public void setRefreshListener(Runnable l)             { this.refreshListener = l; }
    public void setDownloadListener(DownloadListener l)     { this.downloadListener = l; }
    public void setDownloadOptionsListener(Runnable l)      { this.downloadOptionsListener = l; }
    public void setBookmarkListener(Runnable l)             { this.bookmarkListener = l; }
    public void setPrintListener(Runnable l)                { this.printListener = l; }
    public void setDocInfoListener(Runnable l)              { this.docInfoListener = l; }
    public void setNotesRefreshListener(Runnable l)         { this.notesRefreshListener = l; }
    public void setMarksRefreshListener(Runnable l)         { this.marksRefreshListener = l; }
    public void setFulltextSearchListener(FulltextSearchListener l) { this.fulltextSearchListener = l; }
    public void setTabsRefreshListener(Runnable l)               { this.tabsRefreshListener = l; }

    // ---- drawer content setters ----
    public void setNotesContent(String text) {
        notesArea.setText(text != null ? text : "");
        notesArea.setCaretPosition(0);
    }

    public void setMarksContent(String text) {
        marksArea.setText(text != null ? text : "");
        marksArea.setCaretPosition(0);
    }

    public void setSearchResult(String text) {
        searchResultArea.setText(text != null ? text : "");
        searchResultArea.setCaretPosition(0);
    }

    @FunctionalInterface
    public interface PageChangeListener { void onPageChange(int page); }

    @FunctionalInterface
    public interface DownloadListener { void onDownload(); }

    @FunctionalInterface
    public interface FulltextSearchListener { void onSearch(String query); }

    // ================================================================ private

    private void goToPage(int page) {
        if (page < 1) page = 1;
        if (page > currentPageCount) page = currentPageCount;
        if (page == currentPage) return;
        currentPage = page;
        pageSpinner.setValue(page);
        updateNavButtons();
        if (pageChangeListener != null) {
            pageChangeListener.onPageChange(page);
        }
    }

    private void updateNavButtons() {
        firstPageBtn.setEnabled(currentPage > 1);
        prevPageBtn.setEnabled(currentPage > 1);
        nextPageBtn.setEnabled(currentPage < currentPageCount);
        lastPageBtn.setEnabled(currentPage < currentPageCount);
    }

    private void setNavigationEnabled(boolean on) {
        firstPageBtn.setEnabled(on);
        prevPageBtn.setEnabled(on);
        nextPageBtn.setEnabled(on);
        lastPageBtn.setEnabled(on);
        pageSpinner.setEnabled(on);
        downloadButton.setEnabled(on);
    }

    private void showInfoPopup(java.awt.Component invoker, int x, int y) {
        if (infoContent.getComponentCount() == 0) return;
        infoPopup.show(invoker, x, y);
    }

    private void rebuildInfoContent(Document doc) {
        infoContent.removeAll();
        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.insets = new Insets(2, 4, 2, 4);
        gc.gridy = 0;

        // title / headline
        Element headline = doc.selectFirst("#we_id_docprops_headline");
        if (headline != null) {
            gc.gridx = 0; gc.gridwidth = 2;
            JLabel title = new JLabel(headline.text());
            title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
            infoContent.add(title, gc);
            gc.gridwidth = 1;
            gc.gridy++;
        }

        // document ID
        if (currentDocumentId != null && !currentDocumentId.isEmpty()) {
            addInfoRow(gc, "Dokument-ID", currentDocumentId);
        }

        // page count
        addInfoRow(gc, "Seiten", String.valueOf(currentPageCount));

        // document properties from server (el_panel_info rows)
        Elements panelInfoRows = doc.select(".el_panel_info .row");
        for (Element row : panelInfoRows) {
            Elements labels = row.select(".el_label_desc");
            Elements values = row.select(".el_label_value");
            for (int i = 0; i < Math.min(labels.size(), values.size()); i++) {
                String l = labels.get(i).text().trim();
                String v = values.get(i).text().trim();
                if (!l.isEmpty() || !v.isEmpty()) {
                    addInfoRow(gc, l, v);
                }
            }
        }

        // server-populated info block (#we_id_document_info)
        Element info = doc.selectFirst("#we_id_document_info");
        if (info != null) {
            Elements rows = info.select("div.row");
            for (Element row : rows) {
                Elements cols = row.select("div[class*=col]");
                if (cols.size() >= 2) {
                    String label = cols.get(0).text().trim();
                    String value = cols.get(1).text().trim();
                    if (!label.isEmpty() || !value.isEmpty()) {
                        addInfoRow(gc, label, value);
                    }
                }
            }
        }

        // filler
        gc.weighty = 1.0; gc.gridx = 0;
        infoContent.add(new JLabel(), gc);
        infoContent.revalidate();
    }

    private void addInfoRow(GridBagConstraints gc, String label, String value) {
        gc.gridx = 0; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE;
        JLabel lbl = new JLabel(label + ":");
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
        infoContent.add(lbl, gc);

        gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL;
        infoContent.add(new JLabel(value), gc);
        gc.gridy++;
    }

    /**
     * Populate the tabs popup with the given list of open document tabs.
     * Called externally (e.g. from the frame) with the current set of open tabs.
     */
    public void setOpenTabs(List<DocumentTab> openTabs) {
        tabsContent.removeAll();
        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.insets = new Insets(2, 4, 2, 4);
        gc.gridy = 0;

        for (DocumentTab tab : openTabs) {
            gc.gridx = 0; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE;
            JLabel dlbl = new JLabel("Dokument:");
            dlbl.setFont(dlbl.getFont().deriveFont(Font.BOLD));
            tabsContent.add(dlbl, gc);
            gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL;
            tabsContent.add(new JLabel(tab.title()), gc);
            gc.gridy++;

            String ts = tab.timestamp();
            if (ts != null && !ts.isEmpty()) {
                gc.gridx = 0; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE;
                JLabel tlbl = new JLabel("Datum:");
                tlbl.setFont(tlbl.getFont().deriveFont(Font.BOLD));
                tabsContent.add(tlbl, gc);
                gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL;
                tabsContent.add(new JLabel(ts), gc);
                gc.gridy++;
            }
        }

        gc.weighty = 1.0; gc.gridx = 0;
        tabsContent.add(new JLabel(), gc);
        tabsContent.revalidate();
    }

    // ---- toolbar helpers ----

    private static JButton toolbarButton(String text, String tooltip) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.setFocusable(false);
        b.setMargin(new Insets(2, 6, 2, 6));
        return b;
    }

    private static JButton toolbarToggle(String text, String tooltip) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.setFocusable(false);
        b.setMargin(new Insets(2, 6, 2, 6));
        return b;
    }

    private static JSeparator toolbarSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(2, 22));
        return sep;
    }

    // ---- drawer toggle/helper methods ----

    private void toggleDrawer(String drawer) {
        if (activeDrawer.equals(drawer)) {
            activeDrawer = DRAWER_NONE;
            drawerCards.show(drawerPanel, DRAWER_NONE);
            drawerPanel.setVisible(false);
        } else {
            activeDrawer = drawer;
            drawerCards.show(drawerPanel, drawer);
            drawerPanel.setVisible(true);
            if (DRAWER_NOTES.equals(drawer) && notesArea.getText().isEmpty()
                    && notesRefreshListener != null) {
                notesRefreshListener.run();
            } else if (DRAWER_MARKS.equals(drawer) && marksArea.getText().isEmpty()
                    && marksRefreshListener != null) {
                marksRefreshListener.run();
            }
        }
        updateToggleButtons();
        revalidate();
        repaint();
    }

    private void updateToggleButtons() {
        notesToggle.setSelected(DRAWER_NOTES.equals(activeDrawer));
        marksToggle.setSelected(DRAWER_MARKS.equals(activeDrawer));
        searchToggle.setSelected(DRAWER_SEARCH.equals(activeDrawer));
    }

    private void fireFulltextSearch() {
        String q = searchField.getText().trim();
        if (!q.isEmpty() && fulltextSearchListener != null) {
            fulltextSearchListener.onSearch(q);
        }
    }
}
