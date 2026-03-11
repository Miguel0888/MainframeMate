package de.bund.zrb.betaview;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the server HTML to extract document tab information.
 */
public final class DocumentTabParser {

    private static final Pattern LINK_ID_PATTERN =
            Pattern.compile("closeSingleDocument\\.action\\?linkID=([A-F0-9]+)");
    private static final Pattern DOCID_PATTERN =
            Pattern.compile("docid=([A-F0-9]+)");
    private static final Pattern FAVID_PATTERN =
            Pattern.compile("favid=([A-Za-z0-9]+)");

    private DocumentTabParser() {}

    /**
     * Parse server HTML (from opendocumentlink.action or open.action) to extract tabs.
     */
    public static List<DocumentTab> parse(String html) {
        List<DocumentTab> tabs = new ArrayList<>();
        if (html == null || html.isEmpty()) return tabs;

        Document doc = Jsoup.parse(html);
        Elements tabItems = doc.select("#we_id_docbrowser_tabs li");

        for (Element li : tabItems) {
            boolean active = li.hasClass("active");
            Element content = li.selectFirst(".el_tab_content");
            if (content == null) continue;

            Element link = content.selectFirst("a");
            if (link == null) continue;

            String href = link.attr("href").trim();

            // Extract docid and favid from href
            String docId = extractPattern(href, DOCID_PATTERN);
            String favId = extractPattern(href, FAVID_PATTERN);

            // Extract timestamp and title
            Elements tabElements = content.select(".el_tab_element");
            String timestamp = tabElements.size() > 0 ? tabElements.get(0).text().trim() : "";
            String title = tabElements.size() > 1 ? tabElements.get(1).text().trim() : "";

            // Extract linkID from close button
            Element closeBtn = content.selectFirst("button.el_tab_close");
            String linkID = "";
            if (closeBtn != null) {
                String onclick = closeBtn.attr("onclick");
                linkID = extractPattern(onclick, LINK_ID_PATTERN);
                if (linkID.isEmpty()) {
                    // try from id attribute: we_id_close_btn_XXXXX
                    String id = closeBtn.attr("id");
                    if (id.startsWith("we_id_close_btn_")) {
                        linkID = id.substring("we_id_close_btn_".length());
                    }
                }
            }

            String openAction = org.jsoup.parser.Parser.unescapeEntities(href, false).trim();

            tabs.add(new DocumentTab(docId, favId, linkID, title, timestamp, openAction, active));
        }

        return tabs;
    }

    private static String extractPattern(String text, Pattern pattern) {
        if (text == null) return "";
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : "";
    }
}
