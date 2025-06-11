package de.bund.zrb.ui.filetab;

import de.bund.zrb.ftp.FtpFileBuffer;

public class FileTabModel {

    private FtpFileBuffer buffer;
    private boolean changed = false;
    private boolean append = false;
    private String sentenceType = "";

    public FtpFileBuffer getBuffer() {
        return buffer;
    }

    public void setBuffer(FtpFileBuffer buffer) {
        this.buffer = buffer;
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
        return buffer != null ? buffer.getRemotePath() : null;
    }

    public String getFullPath() {
        return buffer != null ? buffer.getLink() : "";
    }
}
