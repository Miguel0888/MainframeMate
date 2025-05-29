package de.zrb.bund.api;

import java.util.List;
import java.util.Map;

public interface TabAdapter {
    String getContent();
    void setStructuredContent(String content, List<Map<String, Object>> feldDefinitionen, int maxRowCount);
    void markAsChanged();
    String getPath();
    TabType getType();
}
