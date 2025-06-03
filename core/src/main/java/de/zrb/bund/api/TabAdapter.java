package de.zrb.bund.api;

import java.util.List;
import java.util.Map;

public interface TabAdapter {
    String getContent();
    void markAsChanged();
    String getPath();
    TabType getType();

    String getTitle();
}
