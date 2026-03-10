package de.bund.zrb.betaview.ui;

import de.bund.zrb.betaview.infrastructure.BetaViewActionPathResolver;
import de.bund.zrb.betaview.infrastructure.BetaViewClient;
import de.bund.zrb.betaview.infrastructure.BetaViewSession;

import javax.swing.JEditorPane;
import javax.swing.SwingWorker;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import java.net.URL;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hyperlink listener that intercepts clicks in the BetaView HTML view
 * and loads the target via the BetaView session (server-side navigation).
 * Adapted from the betaview-example BetaViewHtmlNavigator.
 */
public final class BetaViewHtmlNavigator implements HyperlinkListener {

    private static final Pattern OPEN_DOCUMENT = Pattern.compile("openDocument\\('([^']+)'\\)");
    private static final Pattern POPUP = Pattern.compile("popup\\('([^']+)'\\)");
    private static final Pattern REGULAR_DOWNLOAD = Pattern.compile("regularDownload\\('([^']+)'\\)");
    private static final Pattern DIALOG_DOWNLOAD = Pattern.compile("dialogDownload\\('([^']+)'\\)");

    private final BetaViewClient client;
    private final BetaViewSession session;
    private final JEditorPane view;
    private final URL baseUrl;

    private final BetaViewActionPathResolver resolver = new BetaViewActionPathResolver();

    public BetaViewHtmlNavigator(BetaViewClient client, BetaViewSession session, JEditorPane view, URL baseUrl) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.session = Objects.requireNonNull(session, "session must not be null");
        this.view = Objects.requireNonNull(view, "view must not be null");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
    }

    @Override
    public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
            return;
        }

        URL url = e.getURL();
        if (url == null) {
            url = resolveRelativeUrl(e.getDescription());
        }
        if (url == null) {
            return;
        }

        final String action = resolver.toRelativeAction(url);

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return client.getText(session, action);
            }

            @Override
            protected void done() {
                try {
                    String html = get();
                    String rewritten = rewriteToPlainLinks(html);
                    setHtml(rewritten);
                } catch (Exception ex) {
                    setHtml("<html><body><pre>" + escape(ex.getMessage()) + "</pre></body></html>");
                }
            }
        }.execute();
    }

    public void showInitialHtml(String html) {
        String rewritten = rewriteToPlainLinks(html);
        setHtml(rewritten);
    }

    private void setHtml(String html) {
        view.setContentType("text/html");
        view.setText(html);
        view.setCaretPosition(0);

        if (view.getDocument() instanceof HTMLDocument) {
            ((HTMLDocument) view.getDocument()).setBase(baseUrl);
        }
    }

    /**
     * Rewrite JavaScript onclick/href actions to plain href links for Swing's JEditorPane.
     */
    private String rewriteToPlainLinks(String html) {
        if (html == null) {
            return "";
        }
        // Simple replacement: extract action URLs from onclick handlers
        html = html.replaceAll("href\\s*=\\s*[\"']javascript:[^\"']*[\"']", "href=\"#\"");

        // Replace onclick="openDocument('...')" patterns
        for (Pattern p : new Pattern[]{OPEN_DOCUMENT, POPUP, REGULAR_DOWNLOAD, DIALOG_DOWNLOAD}) {
            Matcher m = p.matcher(html);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String action = m.group(1).replace("&amp;", "&");
                m.appendReplacement(sb, "href=\"" + Matcher.quoteReplacement(action) + "\"");
            }
            m.appendTail(sb);
            html = sb.toString();
        }
        return html;
    }

    private URL resolveRelativeUrl(String description) {
        try {
            return new URL(baseUrl, description);
        } catch (Exception e) {
            return null;
        }
    }

    private String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

