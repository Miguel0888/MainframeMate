package de.bund.zrb.betaview.infrastructure;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Loads BetaView search results as HTML.
 * Taken 1:1 from the tested betaview-example (com.acme.betaview.LoadResultsHtmlUseCase).
 */
public final class LoadResultsHtmlUseCase {

    private final BetaViewClient client;

    public LoadResultsHtmlUseCase(BetaViewClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    public String execute(BetaViewSession session, ResultFilter filter) throws IOException {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(filter, "filter must not be null");

        // Load select page to get the dynamic Struts token for the select form
        String selectHtml = client.getText(session, "select.action?favoriteID=" + filter.favoriteId());
        Map<String, String> hidden = HiddenInputExtractor.extractHiddenInputs(selectHtml);

        Map<String, String> form = new LinkedHashMap<String, String>();

        String tokenNameField = hidden.get("struts.token.name");
        if (tokenNameField == null) {
            throw new IOException("Missing struts.token.name on select page");
        }
        String tokenValue = hidden.get(tokenNameField);
        if (tokenValue == null) {
            throw new IOException("Missing token value for " + tokenNameField + " on select page");
        }

        form.put("struts.token.name", tokenNameField);
        form.put(tokenNameField, tokenValue);

        // Mirror the captured request
        form.put("getDateMask()", "DD.MM.YYYY");
        form.put("favID", filter.favoriteId());
        form.put("locale", filter.locale());
        form.put("focus", "we_id_EXTENSION");

        form.put("lastdate", "last");
        form.put("timesel", "sub");
        form.put("lastsel", "last7days");
        form.put("lasthoursdaysvalue", Integer.toString(filter.daysBack()));
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
        form.put("FORM", filter.form());
        form.put("EXTENSION", filter.extensionPattern());

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

        // POST result.action (302 -> showResult.action), then load showResult.action
        client.postFormText(session, "result.action", form);
        return client.getText(session, "showResult.action");
    }
}

