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

/**
 * Hyperlink listener that intercepts clicks in the BetaView HTML view
 * and loads the target via the BetaView session (server-side navigation).
 * <p>
 * Taken 1:1 from the tested betaview-example (com.acme.betaview.BetaViewHtmlNavigator),
 * only adapted for MainframeMate infrastructure package paths.
 */
public final class BetaViewHtmlNavigator implements HyperlinkListener {

    private final BetaViewClient client;
    private final BetaViewSession session;
    private final JEditorPane view;
    private final URL baseUrl;

    private final BetaViewHtmlActionLinkRewriter rewriter = new BetaViewHtmlActionLinkRewriter();
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
                    String rewritten = rewriter.rewriteToPlainLinks(html);
                    setHtml(rewritten);
                } catch (Exception ex) {
                    setHtml("<html><body><pre>" + escape(ex.getMessage()) + "</pre></body></html>");
                }
            }
        }.execute();
    }

    public void showInitialHtml(String html) {
        String rewritten = rewriter.rewriteToPlainLinks(html);
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
