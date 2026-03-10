package de.bund.zrb.betaview.domain;

import java.util.Collections;
import java.util.List;

/**
 * Result of a BetaView search: the raw HTML plus parsed document references.
 */
public final class BetaViewSearchResult {

    private final String rawHtml;
    private final List<BetaViewDocumentRef> documents;

    public BetaViewSearchResult(String rawHtml, List<BetaViewDocumentRef> documents) {
        this.rawHtml = rawHtml != null ? rawHtml : "";
        this.documents = documents != null ? Collections.unmodifiableList(documents) : Collections.<BetaViewDocumentRef>emptyList();
    }

    /** The full HTML result page as returned by BetaView. */
    public String rawHtml() { return rawHtml; }

    /** Parsed document references from the result page. */
    public List<BetaViewDocumentRef> documents() { return documents; }
}

