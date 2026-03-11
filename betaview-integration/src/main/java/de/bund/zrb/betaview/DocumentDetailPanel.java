package de.bund.zrb.betaview;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.HyperlinkListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Map;

/**
 * Shows the detail view for a document opened from the result list.
 * Left:  document metadata, page navigation, action buttons.
 * Right: PDF preview rendered via PDFBox.
 */
public final class DocumentDetailPanel extends JPanel {

    // --- UI components ---
    private final JLabel documentTitleLabel;
    private final JPanel infoPanel;
    private final JSpinner pageSpinner;
    private final JLabel pageCountLabel;
    private final JButton firstPageBtn;
    private final JButton prevPageBtn;
    private final JButton nextPageBtn;
    private final JButton lastPageBtn;
    private final JButton downloadButton;
    private final PdfPreviewPanel pdfPreview;

    // --- state extracted from the detail HTML ---
    private String currentDocumentId;
    private int currentPageCount;
    private int currentPage = 1;

    // --- Struts form context for page navigation ---
    private String navTokenName;
    private String navTokenValue;
    private String navDbinst;

    // --- callback so the frame can trigger downloads / page loads ---
    private PageChangeListener pageChangeListener;
    private DownloadListener downloadListener;

    public DocumentDetailPanel() {
        setLayout(new BorderLayout());

        // ---- left panel ----
        JPanel left = new JPanel(new BorderLayout(0, 8));
        left.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        documentTitleLabel = new JLabel("Kein Dokument");
        documentTitleLabel.setFont(documentTitleLabel.getFont().deriveFont(Font.BOLD, 14f));
        left.add(documentTitleLabel, BorderLayout.NORTH);

        // info grid
        infoPanel = new JPanel(new GridBagLayout());
        JScrollPane infoScroll = new JScrollPane(infoPanel);
        infoScroll.setBorder(BorderFactory.createTitledBorder("Dokumentinformationen"));
        left.add(infoScroll, BorderLayout.CENTER);

        // bottom: page nav + buttons
        JPanel bottom = new JPanel(new BorderLayout(0, 6));

        // page navigation row
        JPanel navRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        navRow.setBorder(BorderFactory.createTitledBorder("Seite"));

        firstPageBtn = new JButton("\u23EE");
        prevPageBtn  = new JButton("\u25C0");
        pageSpinner  = new JSpinner(new SpinnerNumberModel(1, 1, 1, 1));
        pageSpinner.setPreferredSize(new Dimension(60, 25));
        pageCountLabel = new JLabel("/ 1");
        nextPageBtn  = new JButton("\u25B6");
        lastPageBtn  = new JButton("\u23ED");

        navRow.add(firstPageBtn);
        navRow.add(prevPageBtn);
        navRow.add(pageSpinner);
        navRow.add(pageCountLabel);
        navRow.add(nextPageBtn);
        navRow.add(lastPageBtn);
        bottom.add(navRow, BorderLayout.NORTH);

        // action buttons
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        downloadButton = new JButton("Herunterladen");
        btnRow.add(downloadButton);
        bottom.add(btnRow, BorderLayout.CENTER);

        left.add(bottom, BorderLayout.SOUTH);
        left.setPreferredSize(new Dimension(340, 0));

        // ---- right: PDF preview ----
        pdfPreview = new PdfPreviewPanel();

        // split
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, pdfPreview);
        split.setDividerLocation(340);
        split.setResizeWeight(0.0);
        add(split, BorderLayout.CENTER);

        // wire navigation buttons
        firstPageBtn.addActionListener(e -> goToPage(1));
        prevPageBtn.addActionListener(e -> goToPage(currentPage - 1));
        nextPageBtn.addActionListener(e -> goToPage(currentPage + 1));
        lastPageBtn.addActionListener(e -> goToPage(currentPageCount));
        pageSpinner.addChangeListener(e -> goToPage((int) pageSpinner.getValue()));
        downloadButton.addActionListener(e -> {
            if (downloadListener != null) {
                downloadListener.onDownload();
            }
        });

        setNavigationEnabled(false);
    }

    // ================================================================ public API

    /**
     * Parses the detail HTML, extracts metadata, and triggers loading
     * of the first page PDF preview.
     */
    public void loadDocument(String html) {
        if (html == null || html.trim().isEmpty()) {
            clearDocument();
            return;
        }

        Document doc = Jsoup.parse(html);

        // --- title ---
        Element headline = doc.selectFirst("#we_id_docprops_headline");
        documentTitleLabel.setText(headline != null ? headline.text() : "Dokument");

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

        // --- info panel ---
        infoPanel.removeAll();
        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.insets = new Insets(3, 6, 3, 6);
        gc.gridy = 0;

        // tabs
        Elements tabs = doc.select("li .el_tab_content");
        for (Element tab : tabs) {
            Elements parts = tab.select(".el_tab_element");
            if (parts.size() >= 2) {
                String timestamp = parts.get(0).text().trim();
                String docName   = parts.get(1).text().trim();
                boolean active = !parts.get(0).attr("style").contains("#b2b2b2");
                addInfoRow(gc, active ? "Dokument" : "Dokument (Tab)", docName);
                addInfoRow(gc, "Datum", timestamp);
            }
        }
        if (currentDocumentId != null && !currentDocumentId.isEmpty()) {
            addInfoRow(gc, "Dokument-ID", currentDocumentId);
        }
        addInfoRow(gc, "Seiten", String.valueOf(currentPageCount));

        // any server-populated info
        Element info = doc.selectFirst("#we_id_document_info");
        if (info != null) {
            Elements rows = info.select("div.row");
            for (Element row : rows) {
                Elements cols = row.select("div[class*=col]");
                if (cols.size() >= 2) {
                    addInfoRow(gc, cols.get(0).text().trim(), cols.get(1).text().trim());
                }
            }
        }

        gc.weighty = 1.0; gc.gridx = 0;
        infoPanel.add(new JLabel(), gc);
        infoPanel.revalidate();
        infoPanel.repaint();

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
            pdfPreview.showMessage("Warte auf Seitenvorschau...");
        }
    }

    /** Called by the frame after downloading a page. */
    public void showPageContent(DownloadResult result) {
        pdfPreview.showContent(result);
    }

    /** Called by the frame to show plain text page content. */
    public void showPreviewText(String text) {
        pdfPreview.showPlainText(text);
    }

    public void showPreviewMessage(String msg) {
        pdfPreview.showMessage(msg);
    }

    public void clearDocument() {
        documentTitleLabel.setText("Kein Dokument");
        infoPanel.removeAll();
        infoPanel.revalidate();
        infoPanel.repaint();
        pageCountLabel.setText("/ 1");
        pageSpinner.setValue(1);
        pdfPreview.clear();
        currentDocumentId = null;
        currentPageCount = 0;
        currentPage = 1;
        navTokenName = null;
        navTokenValue = null;
        navDbinst = null;
        setNavigationEnabled(false);
    }

    // --- getters ---
    public String  getCurrentDocumentId() { return currentDocumentId; }
    public int     getCurrentPageCount()  { return currentPageCount; }
    public int     getCurrentPage()       { return currentPage; }
    public String  getNavTokenName()      { return navTokenName; }
    public String  getNavTokenValue()     { return navTokenValue; }
    public String  getNavDbinst()         { return navDbinst; }
    public JButton getDownloadButton()    { return downloadButton; }

    /**
     * Re-parses the navigation form tokens from a page-navigation response HTML.
     * The server issues new Struts tokens after each POST, so we must update them.
     */
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
            if (db != null) {
                navDbinst = db;
            }
        }
    }

    // --- listeners ---
    public void setPageChangeListener(PageChangeListener l) { this.pageChangeListener = l; }
    public void setDownloadListener(DownloadListener l)     { this.downloadListener = l; }

    // kept for interface compat
    public void addHyperlinkListener(HyperlinkListener l) {}
    public void removeHyperlinkListener(HyperlinkListener l) {}

    // ================================================================ callbacks

    @FunctionalInterface
    public interface PageChangeListener {
        void onPageChange(int page);
    }

    @FunctionalInterface
    public interface DownloadListener {
        void onDownload();
    }

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

    private void addInfoRow(GridBagConstraints gc, String label, String value) {
        gc.gridx = 0; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE;
        JLabel lbl = new JLabel(label + ":");
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
        infoPanel.add(lbl, gc);

        gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL;
        infoPanel.add(new JLabel(value), gc);
        gc.gridy++;
    }
}
