package com.acme.betaview;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class LoadResultsHtmlUseCase {

    private final BetaViewClient client;

    public LoadResultsHtmlUseCase(BetaViewClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    public String execute(BetaViewSession session, ResultFilter filter) throws IOException {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(filter, "filter must not be null");

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

        // Mirror captured request – all fields from filter
        form.put("getDateMask()", "DD.MM.YYYY");
        form.put("favID", filter.favoriteId());
        form.put("locale", filter.locale());
        form.put("focus", "we_id_searchbtn");

        form.put("lastdate", filter.lastdate());
        form.put("timesel", "sub");
        form.put("lastsel", filter.lastsel());
        form.put("lasthoursdaysvalue", Integer.toString(filter.daysBack()));
        form.put("timeunit", filter.timeunit());

        form.put("datefrom", filter.datefrom());
        form.put("timefrom", filter.timefrom());
        form.put("dateto", filter.dateto());
        form.put("timeto", filter.timeto());

        form.put("FOLDER", filter.folder());
        form.put("TAB", filter.tab());
        form.put("TITLE", filter.title());
        form.put("FTITLE", filter.ftitle());
        form.put("RECI", filter.recipient());

        if (filter.needsSelgen()) {
            form.put("SELGEN", "yes");
        }

        form.put("FORM", filter.form());
        form.put("EXTENSION", filter.extensionPattern());
        form.put("REPORT", filter.report());
        form.put("JOBNAME", filter.jobName());

        form.put("ONLINE", filter.online());
        form.put("LGRNOTE", filter.lgrnote());
        form.put("LGRXREAD", filter.lgrxread());
        form.put("ARCHIVE", filter.archive());
        form.put("PROCESS", filter.process());
        form.put("DELETE", filter.delete_());
        form.put("LGRSTAT", filter.lgrstat());
        form.put("RELOAD", filter.reload());

        client.postFormText(session, "result.action", form);
        return client.getText(session, "showResult.action");
    }
}