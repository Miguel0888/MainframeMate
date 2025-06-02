package de.zrb.bund.newApi.sentence;

public class SentenceMeta {

    private String path;
    private boolean append;
    private String pathPattern; // für regulären Ausdruck

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isAppend() {
        return append;
    }

    public void setAppend(boolean append) {
        this.append = append;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public void setPathPattern(String pathPattern) {
        this.pathPattern = pathPattern;
    }
}
