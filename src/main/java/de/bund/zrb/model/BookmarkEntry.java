package de.bund.zrb.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BookmarkEntry {
    public String id = UUID.randomUUID().toString();
    public String label;
    public String path;
    public boolean folder;
    public List<BookmarkEntry> children;

    public BookmarkEntry() {
        // Für GSON
    }

    public BookmarkEntry(String label, String path, boolean folder) {
        this.label = label;
        this.path = path;
        this.folder = folder;
        if (folder) this.children = new ArrayList<>();
    }

    public boolean isLeaf() {
        return !folder;
    }

    @Override
    public String toString() {
        return label;
    }
}

