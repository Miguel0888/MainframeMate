package de.bund.zrb.files.model;

import java.util.Collections;
import java.util.Map;

public class FileNode {

    private final String name;
    private final String path;
    private final boolean directory;
    private final long size;
    private final long lastModifiedMillis;
    private final Map<String, String> attributes;

    public FileNode(String name, String path, boolean directory, long size, long lastModifiedMillis) {
        this(name, path, directory, size, lastModifiedMillis, Collections.<String, String>emptyMap());
    }

    public FileNode(String name, String path, boolean directory, long size, long lastModifiedMillis,
                    Map<String, String> attributes) {
        this.name = name;
        this.path = path;
        this.directory = directory;
        this.size = size;
        this.lastModifiedMillis = lastModifiedMillis;
        this.attributes = attributes == null ? Collections.<String, String>emptyMap() : attributes;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public boolean isDirectory() {
        return directory;
    }

    public long getSize() {
        return size;
    }

    public long getLastModifiedMillis() {
        return lastModifiedMillis;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }
}

