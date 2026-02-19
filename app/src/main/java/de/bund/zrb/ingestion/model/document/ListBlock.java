package de.bund.zrb.ingestion.model.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A list block (ordered or unordered) containing items.
 * Immutable.
 */
public final class ListBlock implements Block {

    private final boolean ordered;
    private final List<String> items;

    public ListBlock(boolean ordered, List<String> items) {
        this.ordered = ordered;
        this.items = items != null
            ? Collections.unmodifiableList(new ArrayList<>(items))
            : Collections.<String>emptyList();
    }

    @Override
    public BlockType getType() {
        return BlockType.LIST;
    }

    public boolean isOrdered() {
        return ordered;
    }

    public List<String> getItems() {
        return items;
    }

    @Override
    public String toString() {
        return "ListBlock{ordered=" + ordered + ", items=" + items.size() + "}";
    }
}

