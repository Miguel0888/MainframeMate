package de.bund.zrb.betaview.infrastructure;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Downloads a document (or single page) as PDF from the BetaView server.
 *
 * Flow:
 * 1. POST downloadDialog.action?&init=yes&source=docbrowser  â†’ HTML with form + hidden fields
 * 2. POST downloadWithOptions.action  â†’ binary PDF response
 */
public final class DownloadDocumentUseCase {

    public enum PageSelection {
        CURRENT_PAGE("printCurrentPage"),
        ALL_PAGES("printAllPages");

        private final String value;
        PageSelection(String value) { this.value = value; }
        public String value() { return value; }
    }

    private final BetaViewClient client;

    public DownloadDocumentUseCase(BetaViewClient client) {
        this.client = Objects.requireNonNull(client);
    }

    /**
     * Downloads the document that is currently open in the viewer.
     *
     * @param session       active session
     * @param pageSelection whether to download current page or all pages
     * @return download result with raw bytes and content type
     */
    public DownloadResult execute(BetaViewSession session, PageSelection pageSelection) throws IOException {
        // Step 1: open the download dialog to get form fields
        String dialogHtml = client.postFormText(session, "downloadDialog.action?&init=yes&source=docbrowser",
                new LinkedHashMap<String, String>());

        Map<String, String> hidden = HiddenInputExtractor.extractHiddenInputs(dialogHtml);

        // Step 2: build the download form
        Map<String, String> form = new LinkedHashMap<>();

        // Struts token
        copyRequired(hidden, form, "struts.token.name");
        String tokenName = hidden.get("struts.token.name");
        if (tokenName != null) {
            copyRequired(hidden, form, tokenName);
        }

        // fixed fields from the dialog
        form.put("function", getOrDefault(hidden, "function", "download"));
        form.put("product", getOrDefault(hidden, "product", "BetaDocZ"));
        form.put("source", getOrDefault(hidden, "source", "docbrowser"));
        form.put("pages", getOrDefault(hidden, "pages", "1"));
        form.put("docLink", getOrDefault(hidden, "docLink", "false"));
        form.put("convert", getOrDefault(hidden, "convert", "N"));
        form.put("convertEnabled", getOrDefault(hidden, "convertEnabled", "false"));
        form.put("csvEnabled", getOrDefault(hidden, "csvEnabled", "true"));
        form.put("doctype", getOrDefault(hidden, "doctype", "txt"));
        form.put("jobinfo", getOrDefault(hidden, "jobinfo", "false"));
        form.put("textenco", getOrDefault(hidden, "textenco", "UTF-8"));
        form.put("bss_ukey", getOrDefault(hidden, "bss_ukey", ""));
        form.put("idxstatus", getOrDefault(hidden, "idxstatus", ""));
        form.put("hasHiddenRanges", getOrDefault(hidden, "hasHiddenRanges", "false"));
        form.put("dbinst", getOrDefault(hidden, "dbinst", ""));
        form.put("docid", getOrDefault(hidden, "docid", ""));
        form.put("lineColumnRanges", getOrDefault(hidden, "lineColumnRanges", ""));

        // page selection fields
        form.put("markedPages", "");
        form.put("suffix", "");
        form.put("rangePrintStyle", "");
        form.put("printStyle", pageSelection.value());
        form.put("rangeBegin", "1");
        form.put("rangeEnd", getOrDefault(hidden, "pages", "1"));

        // format: Original
        form.put("downloadFormat", "ORIG");
        form.put("csvStyle", "csv_int");
        form.put("delimiter", "comma");
        form.put("csv_int", "10");
        form.put("csv_char", "");
        form.put("csv_flex", "");
        form.put("dataInput", "");
        form.put("csv_trim", "false");

        // notes: text only
        form.put("printWhatStyle", "printTextOnly");
        form.put("antsPrintStyle", "antsToDoc");
        form.put("antsType", "all");

        // Step 3: submit â€“ the response is the document content (PDF, text, etc.)
        return client.postFormDownload(session, "downloadWithOptions.action", form);
    }

    private static void copyRequired(Map<String, String> src, Map<String, String> dst, String key) {
        String v = src.get(key);
        if (v != null) {
            dst.put(key, v);
        }
    }

    private static String getOrDefault(Map<String, String> map, String key, String fallback) {
        String v = map.get(key);
        return v != null ? v : fallback;
    }
}


