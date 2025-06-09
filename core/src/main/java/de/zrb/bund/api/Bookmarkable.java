package de.zrb.bund.api;

public interface Bookmarkable {
    String getContent();
    void markAsChanged();
    String getPath();
    Type getType();

    String getTitle();

    enum Type {
        FILE,
        CONNECTION,
        LOG,
        PREVIEW
    }
}
