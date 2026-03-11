package de.bund.zrb.betaview;

import javax.swing.SwingWorker;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.net.URL;
import java.util.Objects;

public final class BetaViewHtmlDetailNavigator implements HyperlinkListener {

    private final BetaViewClient client;
    private final BetaViewSession session;
    private final DocumentDetailPanel view;
    private final URL baseUrl;

    private final BetaViewActionPathResolver resolver = new BetaViewActionPathResolver();

    public BetaViewHtmlDetailNavigator(BetaViewClient client, BetaViewSession session, DocumentDetailPanel view, URL baseUrl) {
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
                    view.loadDocument(html);
                } catch (Exception ex) {
                    view.clearDocument();
                }
            }
        }.execute();
    }

    public void showDocument(String html) {
        view.loadDocument(html);
    }

    private URL resolveRelativeUrl(String description) {
        try {
            return new URL(baseUrl, description);
        } catch (Exception e) {
            return null;
        }
    }
}

