package de.zrb.bund.api;

import java.util.List;
import java.util.Map;

public interface FileTabAdapter {
    String getContent();
    void setStructuredContent(String content, List<Map<String, Object>> feldDefinitionen, int maxRowCount);
    void markAsChanged();
}
