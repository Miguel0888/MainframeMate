package de.bund.zrb.ui.filetab;

import de.bund.zrb.ui.VirtualResource;

public class FileTabModel {

    private VirtualResource resource;
    private String expectedHash = "";
    private boolean changed = false;
    private boolean append = false;
    private String sentenceType = "";

    public VirtualResource getResource() {
        return resource;
    }

    public void setResource(VirtualResource resource) {
        this.resource = resource;
    }

    public String getExpectedHash() {
        return expectedHash;
    }

    public void setExpectedHash(String expectedHash) {
        this.expectedHash = expectedHash == null ? "" : expectedHash;
    }

    public boolean isChanged() {
        return changed;
    }

    public void markChanged() {
        this.changed = true;
    }

    public void resetChanged() {
        this.changed = false;
    }

    public boolean isAppend() {
        return append;
    }

    public void setAppend(boolean append) {
        this.append = append;
    }

    public String getSentenceType() {
        return sentenceType;
    }

    public void setSentenceType(String sentenceType) {
        this.sentenceType = sentenceType;
    }

    public String getPath() {
        return resource != null ? resource.getResolvedPath() : null;
    }

    public String getFullPath() {
        return resource != null && resource.getResolvedPath() != null ? resource.getResolvedPath() : "";
    }
}
