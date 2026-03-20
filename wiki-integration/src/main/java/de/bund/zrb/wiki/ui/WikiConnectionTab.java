package de.bund.zrb.wiki.ui;

import de.bund.zrb.wiki.domain.*;
import de.bund.zrb.wiki.port.WikiContentService;
import de.zrb.bund.newApi.ui.ConnectionTab;
import de.zrb.bund.newApi.ui.FindBarPanel;
import de.zrb.bund.newApi.ui.SearchBarPanel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * ConnectionTab for browsing MediaWiki sites.
 * Layout: vertical split – top: search controls + results table, bottom: HTML preview.
 * Outline is delegated to the RightDrawer via WikiFileTab.
 * Single-click on result → preview below. Double-click/Enter → open as new WikiFileTab.
 */
public class WikiConnectionTab implements ConnectionTab {

    private static final Logger LOG = Logger.getLogger(WikiConnectionTab.class.getName());

    private final JPanel mainPanel;
    private final WikiContentService service;
    private final JComboBox<WikiSiteDescriptor> siteSelector;
    private final JPanel siteCheckboxPanel;
    private final List<JCheckBox> siteCheckboxes = new ArrayList<JCheckBox>();
    private final SearchBarPanel searchBar;
    private final JEditorPane htmlPane;
    private final JLabel statusLabel;
    private final FindBarPanel resultFilterBar;

    // Results table
    private final WikiResultTableModel resultModel;
    private final JTable resultTable;
    private final TableRowSorter<WikiResultTableModel> resultSorter;

    /** Callback for opening a wiki page as FileTab in MainframeMate. */
    private OpenCallback openCallback;
    /** Callback to notify about outline changes (for RightDrawer). */
    private OutlineCallback outlineCallback;
    /** Callback for prefetching search results into local cache. */
    private WikiPrefetchCallback prefetchCallback;
    /** Callback to resolve wiki login credentials per site. */
    private CredentialsCallback credentialsCallback;
    /** Callback to persist credentials entered via the login prompt. */
    private CredentialsSaveCallback credentialsSaveCallback;
    /** Callback to index a wiki page into the search index. */
    private IndexCallback indexCallback;
    /** Callback to update the dependency/relations panel (LeftDrawer) for the previewed page. */
    private DependencyCallback dependencyCallback;

    private String currentPageTitle;
    private WikiSiteId currentSiteId;
    private WikiPageView currentPreview;

    /** Panel containing htmlScroll + optional ImageStripPanel for the preview area. */
    private JPanel previewPanel;

    /** true = rendered mode (images inline), false = text mode (images in side strip). */
    private boolean renderedMode = false;
    private JToggleButton textModeBtn;
    private JToggleButton renderedModeBtn;
    /** Reference to the htmlScroll pane for layout updates. */
    private JScrollPane htmlScrollPane;

    /** true when the image strip is expanded into the thumbnail split pane. */
    private boolean imageStripExpanded = false;
    private JSplitPane imageSplitPane;
    private ImageThumbnailPanel thumbnailPanel;
    private int lastDividerLocation = -1;
    private javax.swing.event.ChangeListener scrollSyncListener;

    public WikiConnectionTab(WikiContentService service) {
        this.service = service;
        this.mainPanel = new JPanel(new BorderLayout(0, 0));

        // ═══════════════════════════════════════════════════════════
        //  Top panel: site selector + search
        // ═══════════════════════════════════════════════════════════
        JPanel topControls = new JPanel();
        topControls.setLayout(new BoxLayout(topControls, BoxLayout.Y_AXIS));
        topControls.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // Row 1: Site checkboxes (left) + Indexieren button (right)
        siteCheckboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        siteCheckboxPanel.add(new JLabel("Wikis:"));
        siteSelector = new JComboBox<WikiSiteDescriptor>(); // hidden, used internally for preview/open
        for (WikiSiteDescriptor site : service.listSites()) {
            siteSelector.addItem(site);
            JCheckBox cb = new JCheckBox(site.displayName(), true);
            cb.setToolTipText(site.apiUrl());
            cb.putClientProperty("wikiSite", site);
            cb.addItemListener(e -> {
                if (stateSaveCallback != null) stateSaveCallback.run();
            });
            siteCheckboxes.add(cb);
            siteCheckboxPanel.add(cb);
        }

        // Index button for current preview — placed right-aligned on the checkbox row
        JButton indexPageButton = new JButton("📥 Indexieren");
        indexPageButton.setToolTipText("Aktuelle Seite in den Suchindex aufnehmen");
        indexPageButton.setFocusable(false);
        indexPageButton.addActionListener(e -> indexCurrentPreview());

        JPanel checkboxRow = new JPanel(new BorderLayout(4, 0));
        checkboxRow.add(siteCheckboxPanel, BorderLayout.CENTER);
        checkboxRow.add(indexPageButton, BorderLayout.EAST);
        checkboxRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        topControls.add(checkboxRow);
        topControls.add(Box.createVerticalStrut(4));

        // Row 2: Search
        searchBar = new SearchBarPanel("Wiki durchsuchen\u2026", "Wiki durchsuchen (Enter / Klick)");
        searchBar.addSearchAction(e -> searchWiki());
        searchBar.getTextField().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int rowCount = resultTable.getRowCount();
                if (rowCount == 0) return;
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    resultTable.setRowSelectionInterval(0, 0);
                    resultTable.scrollRectToVisible(resultTable.getCellRect(0, 0, true));
                    resultTable.requestFocusInWindow();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    int last = rowCount - 1;
                    resultTable.setRowSelectionInterval(last, last);
                    resultTable.scrollRectToVisible(resultTable.getCellRect(last, 0, true));
                    resultTable.requestFocusInWindow();
                    e.consume();
                }
            }
        });
        searchBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        topControls.add(searchBar);

        // ═══════════════════════════════════════════════════════════
        //  Results table
        // ═══════════════════════════════════════════════════════════
        resultModel = new WikiResultTableModel();
        resultTable = new JTable(resultModel);
        resultTable.setRowHeight(22);
        resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(300);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        resultTable.getColumnModel().getColumn(1).setMaxWidth(180);

        resultSorter = new TableRowSorter<WikiResultTableModel>(resultModel);
        resultTable.setRowSorter(resultSorter);

        // Single-click → preview
        resultTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int viewRow = resultTable.getSelectedRow();
                if (viewRow >= 0) {
                    int modelRow = resultTable.convertRowIndexToModel(viewRow);
                    String title = resultModel.getTitleAt(modelRow);
                    loadPreview(title);
                }
            }
        });

        // Double-click → open as tab
        resultTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedAsTab();
                }
            }
        });

        // Enter → open as tab
        resultTable.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openWikiTab");
        resultTable.getActionMap().put("openWikiTab", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openSelectedAsTab();
            }
        });

        JScrollPane resultScroll = new JScrollPane(resultTable);
        resultScroll.setBorder(BorderFactory.createTitledBorder("Suchergebnisse"));

        // Result filter bar (orange FindBarPanel)
        resultFilterBar = new FindBarPanel("Ergebnisse filtern (Regex)\u2026");
        resultFilterBar.getTextField().getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyResultFilter(); }
            public void removeUpdate(DocumentEvent e) { applyResultFilter(); }
            public void changedUpdate(DocumentEvent e) { applyResultFilter(); }
        });

        // Top half: controls + results (no filter bar here)
        JPanel topHalf = new JPanel(new BorderLayout(0, 0));
        topHalf.add(topControls, BorderLayout.NORTH);
        topHalf.add(resultScroll, BorderLayout.CENTER);

        // ═══════════════════════════════════════════════════════════
        //  HTML preview pane
        // ═══════════════════════════════════════════════════════════
        htmlPane = new JEditorPane();
        htmlPane.setEditable(false);
        htmlPane.setContentType("text/html");
        htmlPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                handleLink(e);
            }
        });

        // Click on inline image in rendered mode → open image overlay dialog
        htmlPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!renderedMode || currentPreview == null) return;
                java.util.List<ImageRef> imgs = currentPreview.images();
                if (imgs == null || imgs.isEmpty()) return;
                int pos = htmlPane.viewToModel(e.getPoint());
                if (pos < 0) return;
                javax.swing.text.Document doc = htmlPane.getDocument();
                if (!(doc instanceof javax.swing.text.html.HTMLDocument)) return;
                javax.swing.text.Element elem =
                        ((javax.swing.text.html.HTMLDocument) doc).getCharacterElement(pos);
                if (elem == null) return;
                String src = extractImgSrc(elem);
                if (src == null) return;
                int index = findImageIndex(imgs, src);
                if (index >= 0) {
                    ImageStripPanel.openOverlay(htmlPane, imgs, index);
                }
            }
        });
        // Hand cursor when hovering over images in rendered mode
        htmlPane.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (!renderedMode || currentPreview == null) {
                    return;
                }
                java.util.List<ImageRef> imgs = currentPreview.images();
                if (imgs == null || imgs.isEmpty()) return;
                int pos = htmlPane.viewToModel(e.getPoint());
                if (pos >= 0) {
                    javax.swing.text.Document doc = htmlPane.getDocument();
                    if (doc instanceof javax.swing.text.html.HTMLDocument) {
                        javax.swing.text.Element elem =
                                ((javax.swing.text.html.HTMLDocument) doc).getCharacterElement(pos);
                        if (extractImgSrc(elem) != null) {
                            htmlPane.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                            return;
                        }
                    }
                }
                htmlPane.setCursor(Cursor.getDefaultCursor());
            }
        });
        JScrollPane htmlScroll = new JScrollPane(htmlPane);
        htmlScroll.setBorder(BorderFactory.createTitledBorder("Vorschau"));
        htmlScrollPane = htmlScroll;

        previewPanel = new JPanel(new BorderLayout(0, 0));
        previewPanel.add(htmlScroll, BorderLayout.CENTER);

        // ═══════════════════════════════════════════════════════════
        //  Main split: top/bottom
        // ═══════════════════════════════════════════════════════════
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topHalf, previewPanel);
        mainSplit.setDividerLocation(300);
        mainSplit.setResizeWeight(0.4);
        mainPanel.add(mainSplit, BorderLayout.CENTER);

        // ── Toggle buttons: Text vs Rendered ────────────────────
        textModeBtn = new JToggleButton("Aa");
        textModeBtn.setToolTipText("Textmodus (Bilder als Seitenleiste)");
        textModeBtn.setFocusable(false);
        textModeBtn.setSelected(true);
        textModeBtn.setMargin(new Insets(2, 6, 2, 6));

        renderedModeBtn = new JToggleButton("\uD83D\uDDBC");
        renderedModeBtn.setToolTipText("Gerenderte Ansicht (Bilder inline)");
        renderedModeBtn.setFocusable(false);
        renderedModeBtn.setMargin(new Insets(2, 6, 2, 6));

        ButtonGroup viewModeGroup = new ButtonGroup();
        viewModeGroup.add(textModeBtn);
        viewModeGroup.add(renderedModeBtn);

        textModeBtn.addActionListener(e -> {
            if (!renderedMode) return;
            renderedMode = false;
            refreshPreviewMode();
        });
        renderedModeBtn.addActionListener(e -> {
            if (renderedMode) return;
            renderedMode = true;
            refreshPreviewMode();
        });

        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        togglePanel.add(textModeBtn);
        togglePanel.add(renderedModeBtn);

        // Filter bar below everything
        resultFilterBar.addEastComponent(togglePanel);
        // statusLabel is kept as a field but not added to the layout — no "Vorschau:" text visible
        statusLabel = new JLabel(" ");
        mainPanel.add(resultFilterBar, BorderLayout.SOUTH);

        if (siteSelector.getItemCount() > 0) {
            siteSelector.setSelectedIndex(0);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Actions
    // ═══════════════════════════════════════════════════════════

    private void loadPreview(String pageTitle) {
        if (pageTitle == null || pageTitle.isEmpty()) return;
        // Determine site from selected result row
        int viewRow = resultTable.getSelectedRow();
        WikiSiteDescriptor site = null;
        if (viewRow >= 0) {
            int modelRow = resultTable.convertRowIndexToModel(viewRow);
            WikiSiteId siteId = resultModel.getSiteIdAt(modelRow);
            site = findSiteDescriptor(siteId);
        }
        if (site == null) {
            site = (WikiSiteDescriptor) siteSelector.getSelectedItem();
        }
        if (site == null) return;

        final WikiSiteDescriptor targetSite = site;
        currentSiteId = targetSite.id();
        currentPageTitle = pageTitle;

        // Check in-memory prefetch cache first (O(1))
        if (prefetchCallback != null) {
            WikiPageView cached = prefetchCallback.getCached(targetSite.id(), pageTitle);
            if (cached != null) {
                applyPreview(cached);
                triggerPrefetchFromCursor();
                return;
            }
        }

        statusLabel.setText("⏳ Lade Vorschau: " + pageTitle + "…");

        // Use dedicated priority thread if available, otherwise plain SwingWorker
        new SwingWorker<WikiPageView, Void>() {
            @Override
            protected WikiPageView doInBackground() throws Exception {
                if (prefetchCallback != null) {
                    // loadPriority uses its own dedicated thread, never blocked by prefetch pool
                    WikiPageView view = prefetchCallback.loadPriority(targetSite.id(), pageTitle);
                    if (view != null) return view;
                }
                // Fallback: direct load
                WikiCredentials creds = getCredentials(targetSite);
                return service.loadPage(targetSite.id(), pageTitle, creds);
            }

            @Override
            protected void done() {
                try {
                    WikiPageView view = get();
                    if (view != null) {
                        applyPreview(view);
                        triggerPrefetchFromCursor();
                    } else {
                        statusLabel.setText("❌ Seite konnte nicht geladen werden");
                    }
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "[Wiki] Failed to load preview: " + pageTitle, ex);
                    htmlPane.setText("<html><body><h2>Fehler</h2><p>" + escHtml(getRootMessage(ex)) + "</p></body></html>");
                    statusLabel.setText("❌ Fehler: " + getRootMessage(ex));
                }
            }
        }.execute();
    }

    /**
     * Trigger prefetch of pages ahead of the currently selected row.
     */
    private void triggerPrefetchFromCursor() {
        if (prefetchCallback == null) return;

        int viewRow = resultTable.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = resultTable.convertRowIndexToModel(viewRow);

        WikiSiteId siteId = resultModel.getSiteIdAt(modelRow);

        // Collect titles for the same site
        List<String> siteTitles = new ArrayList<String>();
        for (int i = 0; i < resultModel.getRowCount(); i++) {
            if (resultModel.getSiteIdAt(i).equals(siteId) && !resultModel.getTitleAt(i).startsWith("⚠️")) {
                siteTitles.add(resultModel.getTitleAt(i));
            }
        }
        if (!siteTitles.isEmpty()) {
            prefetchCallback.prefetchSearchResults(siteId, siteTitles, Math.max(0, siteTitles.indexOf(resultModel.getTitleAt(modelRow))));
        }
    }

    /**
     * Index the currently previewed wiki page into the search index.
     */
    private void indexCurrentPreview() {
        if (currentPreview == null || currentSiteId == null) {
            statusLabel.setText("⚠️ Keine Seite zum Indexieren geladen");
            return;
        }
        if (indexCallback == null) {
            statusLabel.setText("⚠️ Indexierung nicht verfügbar");
            return;
        }

        final WikiSiteId siteId = currentSiteId;
        final String title = currentPreview.title();
        final String html = currentPreview.cleanedHtml();
        statusLabel.setText("⏳ Indexiere: " + title + "…");

        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                return indexCallback.indexPage(siteId, title, html);
            }

            @Override
            protected void done() {
                try {
                    int chunks = get();
                    if (chunks > 0) {
                        statusLabel.setText("✅ Indexiert: " + title + " (" + chunks + " Chunks)");
                    } else if (chunks == 0) {
                        statusLabel.setText("⚠️ Kein Text extrahiert: " + title);
                    } else {
                        statusLabel.setText("❌ Indexierung fehlgeschlagen: " + title);
                    }
                } catch (Exception ex) {
                    statusLabel.setText("❌ Fehler: " + ex.getMessage());
                    LOG.log(Level.WARNING, "[Wiki] Index failed for: " + title, ex);
                }
            }
        }.execute();
    }

    /** Apply a loaded WikiPageView to the preview pane and outline. */
    private void applyPreview(WikiPageView view) {
        currentPreview = view;
        applyPreviewContent(view);
        statusLabel.setText("✅ Vorschau: " + view.title());

        if (outlineCallback != null) {
            outlineCallback.onOutlineChanged(view.outline(), view.title());
        }
        if (dependencyCallback != null && currentSiteId != null) {
            dependencyCallback.onDependenciesChanged(currentSiteId, view.title());
        }
    }

    /**
     * Apply the correct HTML content and image display based on the current view mode.
     */
    private void applyPreviewContent(WikiPageView view) {
        if (view == null) return;

        // Collapse expanded thumbnails if switching content
        if (imageStripExpanded) {
            collapsePreviewImageStripSilently();
        }

        // Remove existing image strip (EAST component)
        Component eastComp = ((java.awt.BorderLayout) previewPanel.getLayout())
                .getLayoutComponent(java.awt.BorderLayout.EAST);
        if (eastComp != null) {
            previewPanel.remove(eastComp);
        }

        if (renderedMode && view.htmlWithImages() != null) {
            // ── Rendered mode: images inline ─────────────────────
            String fullHtml = WikiAsyncImageLoader.wrapHtmlWithImages(view.htmlWithImages());
            htmlPane.setText(fullHtml);
            htmlPane.setCaretPosition(0);
            // Load images asynchronously (reuses Confluence pattern)
            WikiAsyncImageLoader.loadImagesAsync(htmlPane, view.htmlWithImages(), fullHtml);
        } else {
            // ── Text mode: images in side strip ──────────────────
            htmlPane.setText(wrapHtml(view.cleanedHtml()));
            htmlPane.setCaretPosition(0);
            if (view.images() != null && !view.images().isEmpty()) {
                ImageStripPanel strip = new ImageStripPanel(view.images());
                strip.setExpandCallback(this::expandPreviewImageStrip);
                previewPanel.add(strip, java.awt.BorderLayout.EAST);
            }
        }

        previewPanel.revalidate();
        previewPanel.repaint();
    }

    /**
     * Re-apply current preview using the newly selected view mode.
     * Called when the user toggles between text and rendered mode.
     */
    private void refreshPreviewMode() {
        if (currentPreview != null) {
            applyPreviewContent(currentPreview);
        }
    }

    private void openSelectedAsTab() {
        int viewRow = resultTable.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = resultTable.convertRowIndexToModel(viewRow);
        String title = resultModel.getTitleAt(modelRow);
        if (title == null || title.isEmpty() || title.startsWith("⚠️")) return;

        WikiSiteId siteId = resultModel.getSiteIdAt(modelRow);
        WikiSiteDescriptor site = findSiteDescriptor(siteId);
        if (site == null) site = (WikiSiteDescriptor) siteSelector.getSelectedItem();
        if (site == null || openCallback == null) return;

        final WikiSiteDescriptor targetSite = site;

        // 1) Use current preview if it matches (already loaded)
        if (currentPreview != null && title.equals(currentPreview.title())) {
            openCallback.openWikiPage(targetSite.id().value(), title, currentPreview.cleanedHtml(),
                    currentPreview.htmlWithImages(), currentPreview.outline(), currentPreview.images());
            return;
        }

        // 2) Use prefetch cache or priority thread
        statusLabel.setText("⏳ Öffne: " + title + "…");
        new SwingWorker<WikiPageView, Void>() {
            @Override
            protected WikiPageView doInBackground() throws Exception {
                if (prefetchCallback != null) {
                    WikiPageView view = prefetchCallback.loadPriority(targetSite.id(), title);
                    if (view != null) return view;
                }
                return service.loadPage(targetSite.id(), title, getCredentials(targetSite));
            }

            @Override
            protected void done() {
                try {
                    WikiPageView view = get();
                    if (view != null && openCallback != null) {
                        openCallback.openWikiPage(targetSite.id().value(), view.title(),
                                view.cleanedHtml(), view.htmlWithImages(), view.outline(), view.images());
                        statusLabel.setText("✅ Geöffnet: " + view.title());
                    }
                } catch (Exception ex) {
                    statusLabel.setText("❌ Fehler: " + getRootMessage(ex));
                }
            }
        }.execute();
    }

    private void searchWiki() {
        String query = searchBar.getText().trim();
        if (query.isEmpty()) return;

        // Collect all checked wikis
        final List<WikiSiteDescriptor> selectedSites = new ArrayList<WikiSiteDescriptor>();
        for (JCheckBox cb : siteCheckboxes) {
            if (cb.isSelected()) {
                WikiSiteDescriptor site = (WikiSiteDescriptor) cb.getClientProperty("wikiSite");
                if (site != null) selectedSites.add(site);
            }
        }
        if (selectedSites.isEmpty()) {
            statusLabel.setText("⚠️ Bitte mindestens ein Wiki auswählen.");
            return;
        }

        statusLabel.setText("🔍 Suche in " + selectedSites.size() + " Wiki(s): " + query + "…");
        resultModel.clear();

        new SwingWorker<List<WikiResultTableModel.ResultEntry>, Void>() {
            @Override
            protected List<WikiResultTableModel.ResultEntry> doInBackground() throws Exception {
                List<WikiResultTableModel.ResultEntry> allResults =
                        new ArrayList<WikiResultTableModel.ResultEntry>();

                // Search each selected wiki (sequentially to avoid credential prompts overlapping)
                for (WikiSiteDescriptor site : selectedSites) {
                    try {
                        WikiCredentials creds = getCredentials(site);
                        List<String> titles = service.searchPages(site.id(), query, creds, 30);
                        for (String title : titles) {
                            allResults.add(new WikiResultTableModel.ResultEntry(
                                    title, site.displayName(), site.id()));
                        }
                    } catch (Exception ex) {
                        LOG.log(Level.WARNING, "[Wiki] Search failed for " + site.displayName(), ex);
                        // Add an error entry so the user sees which wiki failed
                        allResults.add(new WikiResultTableModel.ResultEntry(
                                "⚠️ Fehler: " + getRootMessage(ex), site.displayName(), site.id()));
                    }
                }
                return allResults;
            }

            @Override
            protected void done() {
                try {
                    List<WikiResultTableModel.ResultEntry> results = get();
                    resultModel.setEntries(results);

                    long errorCount = 0;
                    for (WikiResultTableModel.ResultEntry e : results) {
                        if (e.title.startsWith("⚠️")) errorCount++;
                    }
                    long realResults = results.size() - errorCount;
                    statusLabel.setText(realResults + " Ergebnisse für \"" + query + "\""
                            + (selectedSites.size() > 1 ? " (in " + selectedSites.size() + " Wikis)" : ""));

                    if (!results.isEmpty() && resultTable.getRowCount() > 0) {
                        resultTable.setRowSelectionInterval(0, 0);
                    }

                    // Prefetch results for each site
                    if (prefetchCallback != null) {
                        for (WikiSiteDescriptor site : selectedSites) {
                            List<String> siteTitles = new ArrayList<String>();
                            for (WikiResultTableModel.ResultEntry e : results) {
                                if (e.siteId.equals(site.id()) && !e.title.startsWith("⚠️")) {
                                    siteTitles.add(e.title);
                                }
                            }
                            if (!siteTitles.isEmpty()) {
                                prefetchCallback.prefetchSearchResults(site.id(), siteTitles);
                            }
                        }
                    }
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "[Wiki] Search failed", ex);
                    statusLabel.setText("❌ Suche fehlgeschlagen: " + getRootMessage(ex));
                }
            }
        }.execute();
    }

    private void handleLink(HyperlinkEvent e) {
        String desc = e.getDescription();
        if (desc != null && desc.startsWith("#")) {
            htmlPane.scrollToReference(desc.substring(1));
            return;
        }

        String url = e.getURL() != null ? e.getURL().toString() : desc;
        if (url == null) return;

        String pageTitle = extractPageTitle(url);
        if (pageTitle != null) {
            WikiSiteDescriptor site = (WikiSiteDescriptor) siteSelector.getSelectedItem();
            if (site != null && openCallback != null) {
                statusLabel.setText("⏳ Öffne: " + pageTitle + "…");
                new SwingWorker<WikiPageView, Void>() {
                    @Override
                    protected WikiPageView doInBackground() throws Exception {
                        return service.loadPage(site.id(), pageTitle, getCredentials(site));
                    }

                    @Override
                    protected void done() {
                        try {
                            WikiPageView view = get();
                            openCallback.openWikiPage(site.id().value(), view.title(),
                                    view.cleanedHtml(), view.htmlWithImages(), view.outline(), view.images());
                            statusLabel.setText("✅ Geöffnet: " + view.title());
                        } catch (Exception ex) {
                            statusLabel.setText("❌ Fehler: " + getRootMessage(ex));
                        }
                    }
                }.execute();
            }
        } else {
            try {
                Desktop.getDesktop().browse(e.getURL().toURI());
            } catch (Exception ex) {
                LOG.log(Level.FINE, "[Wiki] Could not open external link", ex);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Preview image strip expand / collapse
    // ═══════════════════════════════════════════════════════════

    private void expandPreviewImageStrip() {
        if (currentPreview == null || imageStripExpanded || renderedMode) return;
        java.util.List<ImageRef> imgs = currentPreview.images();
        if (imgs == null || imgs.isEmpty()) return;
        imageStripExpanded = true;

        // Remove ImageStripPanel
        Component eastComp = ((java.awt.BorderLayout) previewPanel.getLayout())
                .getLayoutComponent(java.awt.BorderLayout.EAST);
        if (eastComp != null) previewPanel.remove(eastComp);

        // Detach htmlScroll
        previewPanel.remove(htmlScrollPane);

        // Create thumbnail panel
        thumbnailPanel = new ImageThumbnailPanel(imgs);
        thumbnailPanel.setCollapseCallback(this::collapsePreviewImageStrip);

        // Create horizontal split pane
        imageSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, htmlScrollPane, thumbnailPanel);
        imageSplitPane.setResizeWeight(0.7);
        int divLoc = lastDividerLocation > 0 ? lastDividerLocation
                : previewPanel.getWidth() * 3 / 4;
        imageSplitPane.setDividerLocation(divLoc);

        previewPanel.add(imageSplitPane, java.awt.BorderLayout.CENTER);
        installPreviewScrollSync();
        previewPanel.revalidate();
        previewPanel.repaint();
    }

    private void collapsePreviewImageStrip() {
        if (!imageStripExpanded) return;
        if (imageSplitPane != null) {
            lastDividerLocation = imageSplitPane.getDividerLocation();
        }
        collapsePreviewImageStripSilently();

        // Re-add ImageStripPanel
        if (currentPreview != null && currentPreview.images() != null && !currentPreview.images().isEmpty()) {
            ImageStripPanel strip = new ImageStripPanel(currentPreview.images());
            strip.setExpandCallback(this::expandPreviewImageStrip);
            previewPanel.add(strip, java.awt.BorderLayout.EAST);
        }
        previewPanel.revalidate();
        previewPanel.repaint();
    }

    private void collapsePreviewImageStripSilently() {
        if (!imageStripExpanded) return;
        imageStripExpanded = false;
        removePreviewScrollSync();
        if (imageSplitPane != null) {
            imageSplitPane.remove(htmlScrollPane);
            previewPanel.remove(imageSplitPane);
        }
        previewPanel.add(htmlScrollPane, java.awt.BorderLayout.CENTER);
        imageSplitPane = null;
        thumbnailPanel = null;
    }

    private void installPreviewScrollSync() {
        if (scrollSyncListener != null) return;
        scrollSyncListener = new javax.swing.event.ChangeListener() {
            @Override
            public void stateChanged(javax.swing.event.ChangeEvent e) {
                if (thumbnailPanel == null || currentPreview == null) return;
                java.util.List<ImageRef> imgs = currentPreview.images();
                if (imgs == null || imgs.isEmpty()) return;
                JViewport viewport = htmlScrollPane.getViewport();
                int viewY = viewport.getViewPosition().y;
                int totalH = htmlPane.getPreferredSize().height - viewport.getHeight();
                if (totalH <= 0) return;
                double fraction = Math.min(1.0, Math.max(0.0, (double) viewY / totalH));
                int targetIndex = (int) Math.round(fraction * (imgs.size() - 1));
                thumbnailPanel.scrollToImage(targetIndex);
            }
        };
        htmlScrollPane.getViewport().addChangeListener(scrollSyncListener);
    }

    private void removePreviewScrollSync() {
        if (scrollSyncListener != null) {
            htmlScrollPane.getViewport().removeChangeListener(scrollSyncListener);
            scrollSyncListener = null;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Filter logic
    // ═══════════════════════════════════════════════════════════

    private void applyResultFilter() {
        String regex = resultFilterBar.getText().trim();
        JTextField field = resultFilterBar.getTextField();
        if (regex.isEmpty()) {
            resultSorter.setRowFilter(null);
            field.setBackground(UIManager.getColor("TextField.background"));
        } else {
            try {
                resultSorter.setRowFilter(RowFilter.regexFilter("(?i)" + regex, 0));
                field.setBackground(UIManager.getColor("TextField.background"));
            } catch (Exception ex) {
                field.setBackground(new Color(255, 200, 200));
            }
        }
    }


    // ═══════════════════════════════════════════════════════════
    //  Image click helpers (rendered mode)
    // ═══════════════════════════════════════════════════════════

    /**
     * Extract the img src attribute from an HTMLDocument element.
     */
    private static String extractImgSrc(javax.swing.text.Element elem) {
        if (elem == null) return null;
        javax.swing.text.AttributeSet as = elem.getAttributes();
        if (as.getAttribute(javax.swing.text.StyleConstants.NameAttribute)
                == javax.swing.text.html.HTML.Tag.IMG) {
            return (String) as.getAttribute(javax.swing.text.html.HTML.Attribute.SRC);
        }
        Object imgAs = as.getAttribute(javax.swing.text.html.HTML.Tag.IMG);
        if (imgAs instanceof javax.swing.text.AttributeSet) {
            return (String) ((javax.swing.text.AttributeSet) imgAs)
                    .getAttribute(javax.swing.text.html.HTML.Attribute.SRC);
        }
        javax.swing.text.Element parent = elem.getParentElement();
        if (parent != null) {
            javax.swing.text.AttributeSet pas = parent.getAttributes();
            if (pas.getAttribute(javax.swing.text.StyleConstants.NameAttribute)
                    == javax.swing.text.html.HTML.Tag.IMG) {
                return (String) pas.getAttribute(javax.swing.text.html.HTML.Attribute.SRC);
            }
            Object pImgAs = pas.getAttribute(javax.swing.text.html.HTML.Tag.IMG);
            if (pImgAs instanceof javax.swing.text.AttributeSet) {
                return (String) ((javax.swing.text.AttributeSet) pImgAs)
                        .getAttribute(javax.swing.text.html.HTML.Attribute.SRC);
            }
        }
        return null;
    }

    /**
     * Find the index of the ImageRef matching the given src URL.
     */
    private static int findImageIndex(java.util.List<ImageRef> images, String src) {
        if (src == null || images == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (src.equals(images.get(i).src())) return i;
        }
        for (int i = 0; i < images.size(); i++) {
            String imgSrc = images.get(i).src();
            if (imgSrc != null && (imgSrc.contains(src) || src.contains(imgSrc))) return i;
        }
        int lastSlash = src.lastIndexOf('/');
        String srcFile = lastSlash >= 0 && lastSlash < src.length() - 1
                ? src.substring(lastSlash + 1) : src;
        int qMark = srcFile.indexOf('?');
        if (qMark > 0) srcFile = srcFile.substring(0, qMark);
        for (int i = 0; i < images.size(); i++) {
            String imgSrc = images.get(i).src();
            if (imgSrc != null && imgSrc.contains(srcFile)) return i;
        }
        return -1;
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    static String extractPageTitle(String url) {
        int wikiIdx = url.indexOf("/wiki/");
        if (wikiIdx >= 0) {
            String raw = url.substring(wikiIdx + 6);
            int hashIdx = raw.indexOf('#');
            if (hashIdx >= 0) raw = raw.substring(0, hashIdx);
            int queryIdx = raw.indexOf('?');
            if (queryIdx >= 0) raw = raw.substring(0, queryIdx);
            return decodeUrl(raw.replace('_', ' '));
        }
        int titleIdx = url.indexOf("title=");
        if (titleIdx >= 0) {
            String raw = url.substring(titleIdx + 6);
            int ampIdx = raw.indexOf('&');
            if (ampIdx >= 0) raw = raw.substring(0, ampIdx);
            return decodeUrl(raw.replace('_', ' '));
        }
        return null;
    }

    private static String decodeUrl(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private WikiCredentials getCredentials(WikiSiteDescriptor site) {
        if (!site.requiresLogin()) {
            LOG.fine("[Wiki] getCredentials: site '" + site.displayName() + "' does not require login");
            return WikiCredentials.anonymous();
        }
        if (credentialsCallback != null) {
            WikiCredentials creds = credentialsCallback.getCredentials(site.id());
            LOG.fine("[Wiki] getCredentials: callback returned " + (creds == null ? "null" : (creds.isAnonymous() ? "anonymous" : "user='" + creds.username() + "'")));
            if (creds != null && !creds.isAnonymous()) return creds;
        } else {
            LOG.warning("[Wiki] getCredentials: credentialsCallback is NULL for site '" + site.displayName() + "'");
        }

        // No credentials stored – prompt the user via dialog
        LOG.info("[Wiki] getCredentials: no credentials available, prompting user for site '" + site.displayName() + "'");
        final WikiCredentials[] result = new WikiCredentials[]{null};

        Runnable dialogRunnable = () -> {
            javax.swing.JTextField userField = new javax.swing.JTextField(20);
            javax.swing.JPasswordField passField = new javax.swing.JPasswordField(20);
            javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.GridBagLayout());
            java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
            gbc.insets = new java.awt.Insets(4, 4, 4, 4);
            gbc.anchor = java.awt.GridBagConstraints.WEST;

            gbc.gridx = 0; gbc.gridy = 0;
            panel.add(new javax.swing.JLabel("Wiki:"), gbc);
            gbc.gridx = 1;
            panel.add(new javax.swing.JLabel(site.displayName()), gbc);

            gbc.gridx = 0; gbc.gridy = 1;
            panel.add(new javax.swing.JLabel("Benutzername:"), gbc);
            gbc.gridx = 1; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
            panel.add(userField, gbc);

            gbc.gridx = 0; gbc.gridy = 2; gbc.fill = java.awt.GridBagConstraints.NONE; gbc.weightx = 0;
            panel.add(new javax.swing.JLabel("Passwort:"), gbc);
            gbc.gridx = 1; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
            panel.add(passField, gbc);

            int choice = javax.swing.JOptionPane.showConfirmDialog(
                    mainPanel, panel,
                    "Wiki-Login: " + site.displayName(),
                    javax.swing.JOptionPane.OK_CANCEL_OPTION,
                    javax.swing.JOptionPane.PLAIN_MESSAGE);

            if (choice == javax.swing.JOptionPane.OK_OPTION) {
                String user = userField.getText().trim();
                String pass = new String(passField.getPassword());
                if (!user.isEmpty() && !pass.isEmpty()) {
                    result[0] = new WikiCredentials(user, pass.toCharArray());
                    // Save to settings for next time
                    if (credentialsSaveCallback != null) {
                        credentialsSaveCallback.saveCredentials(site.id(), user, pass);
                    }
                    LOG.info("[Wiki] getCredentials: user entered credentials for '" + site.displayName() + "' user='" + user + "'");
                } else {
                    LOG.warning("[Wiki] getCredentials: user or password empty in prompt dialog");
                }
            }
        };

        try {
            if (javax.swing.SwingUtilities.isEventDispatchThread()) {
                dialogRunnable.run();
            } else {
                javax.swing.SwingUtilities.invokeAndWait(dialogRunnable);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Wiki] Credentials prompt failed", e);
        }

        if (result[0] != null) {
            return result[0];
        }
        LOG.warning("[Wiki] getCredentials: user cancelled or empty – falling back to anonymous");
        return WikiCredentials.anonymous();
    }

    static String wrapHtml(String bodyHtml) {
        return "<html><head><style>"
                + "body { font-family: sans-serif; font-size: 13px; margin: 12px; }"
                + "h1,h2,h3 { color: #333; }"
                + "a { color: #0645ad; }"
                + "pre, code { background: #f4f4f4; padding: 4px; }"
                + "table { border-collapse: collapse; } "
                + "td, th { border: 1px solid #ccc; padding: 4px; }"
                + "</style></head><body>"
                + bodyHtml
                + "</body></html>";
    }

    static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String getRootMessage(Exception ex) {
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }

    public WikiContentService getService() { return service; }

    /** @return the currently previewed page's site id, or {@code null} if no preview is loaded */
    public WikiSiteId getCurrentSiteId() { return currentSiteId; }

    /** @return the currently previewed page's title, or {@code null} */
    public String getCurrentPageTitle() { return currentPageTitle; }

    /** @return the currently previewed page's outline, or {@code null} */
    public OutlineNode getCurrentOutline() {
        return currentPreview != null ? currentPreview.outline() : null;
    }

    /**
     * Scroll the preview pane to a heading anchor (called from RightDrawer outline).
     */
    public void scrollToAnchor(String anchor) {
        if (anchor != null) {
            htmlPane.scrollToReference(anchor);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ConnectionTab interface
    // ═══════════════════════════════════════════════════════════

    @Override public String getTitle() { return "📖 Wiki"; }
    @Override public String getTooltip() { return "MediaWiki-Seiten durchsuchen und anzeigen"; }
    @Override public JComponent getComponent() { return mainPanel; }
    @Override public void onClose() {
        if (prefetchCallback != null) {
            prefetchCallback.shutdown();
        }
    }
    @Override public void saveIfApplicable() { /* read-only */ }
    @Override public String getContent() { return ""; }
    @Override public void markAsChanged() { /* not applicable */ }
    @Override public String getPath() {
        String query = searchBar.getText().trim();
        if (!query.isEmpty()) {
            return "search-wiki://" + query;
        }
        return "wiki://";
    }
    @Override public Type getType() { return Type.CONNECTION; }

    @Override
    public void focusSearchField() {
        searchBar.focusField();
    }

    @Override
    public void searchFor(String searchPattern) {
        searchBar.setText(searchPattern);
        searchWiki();
    }

    // ═══════════════════════════════════════════════════════════
    //  Callbacks
    // ═══════════════════════════════════════════════════════════

    public void setOpenCallback(OpenCallback callback) {
        this.openCallback = callback;
    }

    public void setOutlineCallback(OutlineCallback callback) {
        this.outlineCallback = callback;
    }

    public void setPrefetchCallback(WikiPrefetchCallback callback) {
        this.prefetchCallback = callback;
    }

    /**
     * Open a wiki page externally (e.g. from a relation entry in the LeftDrawer).
     * Uses the priority thread if prefetch is available, otherwise loads directly.
     */
    public void openPageExternally(String siteIdStr, String pageTitle) {
        if (openCallback == null) return;

        WikiSiteDescriptor site = findSiteById(siteIdStr);
        if (site == null && siteSelector.getItemCount() > 0) {
            site = (WikiSiteDescriptor) siteSelector.getItemAt(0);
        }
        if (site == null) return;

        final WikiSiteDescriptor targetSite = site;

        // Check prefetch cache first
        if (prefetchCallback != null) {
            WikiPageView cached = prefetchCallback.getCached(targetSite.id(), pageTitle);
            if (cached != null) {
                openCallback.openWikiPage(targetSite.id().value(), cached.title(),
                        cached.cleanedHtml(), cached.htmlWithImages(), cached.outline(), cached.images());
                return;
            }
        }

        // Load in background
        statusLabel.setText("⏳ Öffne: " + pageTitle + "…");
        new SwingWorker<WikiPageView, Void>() {
            @Override
            protected WikiPageView doInBackground() throws Exception {
                if (prefetchCallback != null) {
                    WikiPageView v = prefetchCallback.loadPriority(targetSite.id(), pageTitle);
                    if (v != null) return v;
                }
                return service.loadPage(targetSite.id(), pageTitle, getCredentials(targetSite));
            }

            @Override
            protected void done() {
                try {
                    WikiPageView view = get();
                    if (view != null && openCallback != null) {
                        openCallback.openWikiPage(targetSite.id().value(), view.title(),
                                view.cleanedHtml(), view.htmlWithImages(), view.outline(), view.images());
                        statusLabel.setText("✅ Geöffnet: " + view.title());
                    }
                } catch (Exception ex) {
                    statusLabel.setText("❌ Fehler: " + getRootMessage(ex));
                }
            }
        }.execute();
    }

    private WikiSiteDescriptor findSiteById(String siteIdStr) {
        for (int i = 0; i < siteSelector.getItemCount(); i++) {
            WikiSiteDescriptor s = (WikiSiteDescriptor) siteSelector.getItemAt(i);
            if (s.id().value().equals(siteIdStr)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Select (check) the wiki site matching the given siteId and optionally uncheck others.
     * Used when navigating to a system function article so the search targets the right wiki.
     *
     * @param siteIdStr site identifier, e.g. "wikipedia_de"
     */
    public void selectSiteById(String siteIdStr) {
        if (siteIdStr == null) return;
        boolean found = false;
        for (JCheckBox cb : siteCheckboxes) {
            WikiSiteDescriptor site = (WikiSiteDescriptor) cb.getClientProperty("wikiSite");
            if (site != null && site.id().value().equals(siteIdStr)) {
                cb.setSelected(true);
                found = true;
            }
        }
        // Also update the internal combo for preview
        if (found) {
            WikiSiteDescriptor descriptor = findSiteById(siteIdStr);
            if (descriptor != null) {
                siteSelector.setSelectedItem(descriptor);
            }
        }
    }

    private WikiSiteDescriptor findSiteDescriptor(WikiSiteId siteId) {
        if (siteId == null) return null;
        return findSiteById(siteId.value());
    }

    public interface OpenCallback {
        void openWikiPage(String siteId, String pageTitle, String htmlContent,
                          String htmlWithImages,
                          OutlineNode outline, java.util.List<de.bund.zrb.wiki.domain.ImageRef> images);
    }

    public interface OutlineCallback {
        void onOutlineChanged(OutlineNode outline, String pageTitle);
    }

    /** Callback to update the dependency/relations panel when a preview page changes. */
    public interface DependencyCallback {
        void onDependenciesChanged(WikiSiteId siteId, String pageTitle);
    }

    public interface CredentialsCallback {
        /** Return credentials for the given wiki site, or null if unavailable. */
        WikiCredentials getCredentials(WikiSiteId siteId);
    }

    /** Callback to persist credentials entered via the login prompt dialog. */
    public interface CredentialsSaveCallback {
        void saveCredentials(WikiSiteId siteId, String username, String password);
    }

    /** Callback to index a single wiki page into the search index. */
    public interface IndexCallback {
        /**
         * @param siteId    wiki site id
         * @param pageTitle page title
         * @param html      cleaned HTML content
         * @return number of chunks indexed, or -1 on failure
         */
        int indexPage(WikiSiteId siteId, String pageTitle, String html);
    }

    public void setIndexCallback(IndexCallback callback) {
        this.indexCallback = callback;
    }

    public void setDependencyCallback(DependencyCallback callback) {
        this.dependencyCallback = callback;
    }

    public void setCredentialsCallback(CredentialsCallback callback) {
        this.credentialsCallback = callback;
    }

    public void setCredentialsSaveCallback(CredentialsSaveCallback callback) {
        this.credentialsSaveCallback = callback;
    }

    // ═══════════════════════════════════════════════════════════
    //  Application State persistence (wiki checkbox selection)
    // ═══════════════════════════════════════════════════════════

    /** Callback to persist state changes immediately (e.g. on checkbox toggle). */
    private Runnable stateSaveCallback;

    public void setStateSaveCallback(Runnable callback) {
        this.stateSaveCallback = callback;
    }

    /** Write current checkbox states into the applicationState map. */
    public void addApplicationState(Map<String, String> state) {
        if (state == null) return;
        for (JCheckBox cb : siteCheckboxes) {
            WikiSiteDescriptor site = (WikiSiteDescriptor) cb.getClientProperty("wikiSite");
            if (site != null) {
                state.put("wiki.site.checked." + site.id().value(), String.valueOf(cb.isSelected()));
            }
        }
    }

    /** Restore checkbox states from the applicationState map. */
    public void restoreApplicationState(Map<String, String> state) {
        if (state == null) return;
        for (JCheckBox cb : siteCheckboxes) {
            WikiSiteDescriptor site = (WikiSiteDescriptor) cb.getClientProperty("wikiSite");
            if (site != null) {
                String val = state.get("wiki.site.checked." + site.id().value());
                if (val != null) {
                    cb.setSelected(Boolean.parseBoolean(val));
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Result Table Model (title + source wiki)
    // ═══════════════════════════════════════════════════════════

    static final class WikiResultTableModel extends AbstractTableModel {

        static final class ResultEntry {
            final String title;
            final String sourceName;
            final WikiSiteId siteId;

            ResultEntry(String title, String sourceName, WikiSiteId siteId) {
                this.title = title;
                this.sourceName = sourceName;
                this.siteId = siteId;
            }
        }

        private final List<ResultEntry> entries = new ArrayList<ResultEntry>();

        void setEntries(List<ResultEntry> results) {
            entries.clear();
            entries.addAll(results);
            fireTableDataChanged();
        }

        /** Legacy helper: set simple title list (single-site search). */
        void setResults(List<String> results) {
            entries.clear();
            for (String t : results) {
                entries.add(new ResultEntry(t, "", new WikiSiteId("")));
            }
            fireTableDataChanged();
        }

        void clear() {
            entries.clear();
            fireTableDataChanged();
        }

        String getTitleAt(int row) {
            return entries.get(row).title;
        }

        WikiSiteId getSiteIdAt(int row) {
            return entries.get(row).siteId;
        }

        List<String> getAllTitles() {
            List<String> titles = new ArrayList<String>();
            for (ResultEntry e : entries) {
                if (!e.title.startsWith("⚠️")) titles.add(e.title);
            }
            return titles;
        }

        @Override public int getRowCount() { return entries.size(); }
        @Override public int getColumnCount() { return 2; }
        @Override public String getColumnName(int col) {
            return col == 0 ? "Seitentitel" : "Quelle";
        }

        @Override
        public Object getValueAt(int row, int col) {
            ResultEntry e = entries.get(row);
            return col == 0 ? e.title : e.sourceName;
        }
    }
}

