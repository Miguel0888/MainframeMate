package de.zrb.bund.newApi.ui;

public interface ConnectionTab extends AppTab {
    String getTitle();

    String getTooltip();

    void saveIfApplicable();

    String getContent();

    void markAsChanged();

    String getPath();
}
