package de.bund.zrb.ingestion.model.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A table block with headers and rows.
 * Immutable.
 */
public final class TableBlock implements Block {

    private final List<String> headers;
    private final List<List<String>> rows;

    public TableBlock(List<String> headers, List<List<String>> rows) {
        this.headers = headers != null
            ? Collections.unmodifiableList(new ArrayList<>(headers))
            : Collections.<String>emptyList();

        if (rows != null) {
            List<List<String>> rowsCopy = new ArrayList<>();
            for (List<String> row : rows) {
                rowsCopy.add(Collections.unmodifiableList(new ArrayList<>(row)));
            }
            this.rows = Collections.unmodifiableList(rowsCopy);
        } else {
            this.rows = Collections.emptyList();
        }
    }

    @Override
    public BlockType getType() {
        return BlockType.TABLE;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public int getColumnCount() {
        if (!headers.isEmpty()) {
            return headers.size();
        }
        if (!rows.isEmpty()) {
            return rows.get(0).size();
        }
        return 0;
    }

    @Override
    public String toString() {
        return "TableBlock{headers=" + headers.size() + ", rows=" + rows.size() + "}";
    }
}

