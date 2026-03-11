package de.bund.zrb.betaview;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class BetaViewResultUseCase {

    private final BetaViewClient client;

    public BetaViewResultUseCase(BetaViewClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    public String loadResultHtml(BetaViewSession session, String favoriteId, String locale, String extensionPattern) throws IOException {
        Objects.requireNonNull(session, "session must not be null");
        requireNotBlank(favoriteId, "favoriteId must not be blank");
        requireNotBlank(locale, "locale must not be blank");
        requireNotBlank(extensionPattern, "extensionPattern must not be blank");

        // 1) Load select page to obtain Struts form token
        String selectHtml = client.getText(session, "select.action?favoriteID=" + favoriteId);
        Map<String, String> hidden = HiddenInputExtractor.extractHiddenInputs(selectHtml);

        // 2) Build the form exactly like the browser
        Map<String, String> form = new LinkedHashMap<String, String>();

        // Add Struts token (dynamic)
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

        // Add selection parameters (copy from your captured request)
        form.put("getDateMask()", "DD.MM.YYYY");
        form.put("favID", favoriteId);
        form.put("locale", locale);

        form.put("focus", "we_id_EXTENSION");

        form.put("lastdate", "last");
        form.put("timesel", "sub");
        form.put("lastsel", "last7days");
        form.put("lasthoursdaysvalue", "60");
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
        form.put("FORM", "APZF");
        form.put("EXTENSION", extensionPattern);

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

        // 3) POST result.action (server responds 302 -> showResult.action)
        client.postFormText(session, "result.action", form);

        // 4) Load result page (HTML)
        return client.getText(session, "showResult.action");
    }

    private static void requireNotBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }
}