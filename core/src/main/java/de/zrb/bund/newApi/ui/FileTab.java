package de.zrb.bund.newApi.ui;

import de.zrb.bund.api.Bookmarkable;

public interface FileTab extends FtpTab, Bookmarkable {
    String getTitle();

    String getTooltip();

    void saveIfApplicable();

    void setContent(String text, String sentenceType);

    void markAsChanged();

    String getPath();

    String getContent();

    void setContent(String content);
}
