package de.bund.zrb.excel.model;

import de.zrb.bund.newApi.mcp.ToolSpec;

import java.io.File;
import java.util.Map;

public class ExcelImportConfig {
    private File file;
    private String templateName;
    private boolean hasHeader;
    private int headerRowIndex;
    private boolean append;
    private String separator;

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public boolean isHasHeader() {
        return hasHeader;
    }

    public void setHasHeader(boolean hasHeader) {
        this.hasHeader = hasHeader;
    }

    public int getHeaderRowIndex() {
        return headerRowIndex;
    }

    public void setHeaderRowIndex(int headerRowIndex) {
        this.headerRowIndex = headerRowIndex;
    }

    public boolean isAppend() {
        return append;
    }

    public void setAppend(boolean append) {
        this.append = append;
    }

    public String getSeparator() {
        return separator;
    }

    public void setSeparator(String separator) {
        this.separator = separator;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    // ggf. zusätzlich: Map<String, Object> rawInput für flexible Initialisierung
    public ExcelImportConfig(Map<String, Object> input, ToolSpec.InputSchema schema) {
        this.file = new File((String) input.get("file"));
        this.templateName = (String) input.get("satzart"); // ToDo: rename to sentence type everywhere
        this.hasHeader = Boolean.TRUE.equals(input.get("hasHeader"));
        this.headerRowIndex = (Integer) input.get("headerRowIndex");
        this.append = Boolean.TRUE.equals(input.get("append"));
        this.separator = input.getOrDefault("trennzeile", "").toString(); // ToDo: rename to separator everywhere
    }
}
