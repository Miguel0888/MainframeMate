package de.bund.zrb.betaview.infrastructure;

import de.bund.zrb.betaview.domain.*;
import de.bund.zrb.betaview.port.BetaViewGateway;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter that communicates with a BetaView web application via HTTP.
 * Delegates HTTP, session and CSRF handling to dedicated infrastructure classes
 * (BetaViewClient, BetaViewSession, BetaViewHttpClient) based on the
 * betaview-example architecture.
 */
public class BetaViewGatewayHttpAdapter implements BetaViewGateway {

    private BetaViewClient client;
    private BetaViewSession session;

    // ── BetaViewGateway implementation ──────────────────────────────────

    @Override
    public BetaViewSearchResult search(BetaViewSearchQuery query) throws Exception {
        connect(query);

        String selectHtml = client.getText(session, "select.action?favoriteID=" + query.favoriteId());
        Map<String, String> hidden = HiddenInputExtractor.extractHiddenInputs(selectHtml);

        Map<String, String> form = new LinkedHashMap<String, String>();
        addStrutsToken(form, hidden);

        form.put("getDateMask()", "DD.MM.YYYY");
        form.put("favID", query.favoriteId());
        form.put("locale", query.locale());
        form.put("focus", "we_id_EXTENSION");

        form.put("lastdate", "last");
        form.put("timesel", "sub");
        form.put("lastsel", "last7days");
        form.put("lasthoursdaysvalue", Integer.toString(query.daysBack()));
        form.put("timeunit", "days");

        form.put("datefrom", "");
        form.put("timefrom", "");
        form.put("dateto", "");
        form.put("timeto", "");

        form.put("FOLDER", "*");
        form.put("TAB", "*");
        form.put("TITLE", "");
        form.put("FTITLE", "0");
        form.put("RECI", "*");
        form.put("FORM", query.form());
        form.put("EXTENSION", query.extensionPattern());

        form.put("REPORT", "*");
        form.put("JOBNAME", "*");
        form.put("ONLINE", "");
        form.put("LGRNOTE", "");
        form.put("LGRXREAD", "");
        form.put("ARCHIVE", "");
        form.put("PROCESS", "");
        form.put("DELETE", "");
        form.put("LGRSTAT", "");
        form.put("RELOAD", "");

        client.postFormText(session, "result.action", form);
        String rawHtml = client.getText(session, "showResult.action");

        List<BetaViewDocumentRef> docs = parseDocumentRefs(rawHtml);
        return new BetaViewSearchResult(rawHtml, docs);
    }

    @Override
    public BetaViewDocument loadDocument(BetaViewDocumentRef ref) throws Exception {
        ensureConnected();
        String html = client.getText(session, ref.actionPath());
        return new BetaViewDocument(ref, html, true);
    }

    @Override
    public String navigateLink(String relativePath) throws Exception {
        ensureConnected();
        return client.getText(session, relativePath);
    }

    @Override
    public boolean isConnected() {
        return session != null;
    }

    /**
     * Returns the active session for use by UI-level navigators.
     */
    public BetaViewSession getSession() {
        return session;
    }

    /**
     * Returns the underlying client for use by UI-level navigators.
     */
    public BetaViewClient getClient() {
        return client;
    }

    // ── Connection ──────────────────────────────────────────────────────

    private void connect(BetaViewSearchQuery query) throws IOException {
        BetaViewBaseUrl baseUrl = new BetaViewBaseUrl(query.baseUrl());
        client = new BetaViewHttpClient(baseUrl);
        session = client.login(new BetaViewCredentials(query.user(), query.password()));
    }

    private void ensureConnected() {
        if (session == null) {
            throw new IllegalStateException("Not connected to BetaView. Perform a search first.");
        }
    }

    // ── Form helpers ────────────────────────────────────────────────────

    private void addStrutsToken(Map<String, String> form, Map<String, String> hidden) {
        String tokenNameField = hidden.get("struts.token.name");
        if (tokenNameField != null) {
            form.put("struts.token.name", tokenNameField);
            String tokenValue = hidden.get(tokenNameField);
            if (tokenValue != null) {
                form.put(tokenNameField, tokenValue);
            }
        }
    }

    // ── Result parsing ──────────────────────────────────────────────────

    private List<BetaViewDocumentRef> parseDocumentRefs(String html) {
        List<BetaViewDocumentRef> refs = new ArrayList<BetaViewDocumentRef>();
        try {
            Document doc = Jsoup.parse(html);
            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String href = link.attr("href");
                String text = link.text().trim();
                if (href.contains("show") || href.contains("view") || href.contains("display")
                        || href.contains("docId") || href.contains("documentID")) {
                    if (!text.isEmpty()) {
                        refs.add(new BetaViewDocumentRef(text, href, ""));
                    }
                }
            }
        } catch (Exception e) {
            // If parsing fails, return empty list
        }
        return refs;
    }
}
