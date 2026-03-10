package de.bund.zrb.betaview.domain;

/**
 * A loaded BetaView document with its content.
 */
public final class BetaViewDocument {

    private final BetaViewDocumentRef ref;
    private final String content;
    private final boolean html;

    public BetaViewDocument(BetaViewDocumentRef ref, String content, boolean html) {
        this.ref = ref;
        this.content = content != null ? content : "";
        this.html = html;
    }

    public BetaViewDocumentRef ref() { return ref; }

    /** The document content (plain text or HTML). */
    public String content() { return content; }

    /** Whether the content is HTML that should be rendered. */
    public boolean isHtml() { return html; }
}

