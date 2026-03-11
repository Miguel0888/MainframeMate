package de.bund.zrb.wiki.domain;

import java.util.Collections;
import java.util.List;

/**
 * Immutable outline node for wiki page headings.
 */
public final class OutlineNode {
    private final String text;
    private final String anchor;
    private final List<OutlineNode> children;

    public OutlineNode(String text, String anchor, List<OutlineNode> children) {
        this.text = text;
        this.anchor = anchor;
        this.children = children != null
                ? Collections.unmodifiableList(children)
                : Collections.<OutlineNode>emptyList();
    }

    public String text() { return text; }
    public String anchor() { return anchor; }
    public List<OutlineNode> children() { return children; }
}

