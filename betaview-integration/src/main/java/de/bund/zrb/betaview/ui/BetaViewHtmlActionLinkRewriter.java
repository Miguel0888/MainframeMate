package de.bund.zrb.betaview.ui;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BetaViewHtmlActionLinkRewriter {

    private static final Pattern OPEN_DOCUMENT = Pattern.compile("openDocument\\('([^']+)'\\)");
    private static final Pattern POPUP = Pattern.compile("popup\\('([^']+)'\\)");
    private static final Pattern REGULAR_DOWNLOAD = Pattern.compile("regularDownload\\('([^']+)'\\)");
    private static final Pattern DIALOG_DOWNLOAD = Pattern.compile("dialogDownload\\('([^']+)'\\)");
    private static final Pattern HELP_OPEN = Pattern.compile("help\\.openDocument\\('([^']+)'\\)");

    public String rewriteToPlainLinks(String html) {
        if (html == null) {
            return "";
        }

        Document doc = Jsoup.parse(html);

        for (Element a : doc.select("a")) {
            String onclick = a.attr("onclick");
            String href = a.attr("href");

            String actionFromOnclick = extractAction(onclick);
            if (actionFromOnclick != null) {
                a.attr("href", actionFromOnclick);
                a.removeAttr("onclick");
                continue;
            }

            String actionFromHref = extractAction(href);
            if (actionFromHref != null) {
                a.attr("href", actionFromHref);
                a.removeAttr("onclick");
            }
        }

        return doc.outerHtml();
    }

    private String extractAction(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        String candidate = matchGroup(OPEN_DOCUMENT, text);
        if (candidate != null) {
            return decodeAmp(candidate);
        }

        candidate = matchGroup(POPUP, text);
        if (candidate != null) {
            return decodeAmp(candidate);
        }

        candidate = matchGroup(REGULAR_DOWNLOAD, text);
        if (candidate != null) {
            return decodeAmp(candidate);
        }

        candidate = matchGroup(DIALOG_DOWNLOAD, text);
        if (candidate != null) {
            return decodeAmp(candidate);
        }

        candidate = matchGroup(HELP_OPEN, text);
        if (candidate != null) {
            return decodeAmp(candidate);
        }

        return null;
    }

    private String matchGroup(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private String decodeAmp(String s) {
        return s.replace("&amp;", "&");
    }
}
