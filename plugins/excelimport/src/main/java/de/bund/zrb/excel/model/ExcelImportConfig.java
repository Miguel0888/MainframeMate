package de.bund.zrb.excel.model;

import de.zrb.bund.newApi.mcp.ToolSpec;

import java.io.File;
import java.util.Map;
import java.util.function.Function;

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
    public ExcelImportConfig(Map<String, Object> input,
                             ToolSpec.InputSchema schema,
                             Function<String, ExcelMapping> templateResolver) {
        try {
            this.file = new File(requireString(input, "file"));
            String sentenceType = requireString(input, "satzart");

            // Versuche das Template zu resolven, ansonsten nimm den Satzartnamen als Template-Name
            ExcelMapping mapping = (templateResolver != null) ? templateResolver.apply(sentenceType) : null;
            this.templateName = (mapping != null) ? mapping.getName() : sentenceType;

            this.hasHeader = requireBoolean(input, "hasHeader");
            this.headerRowIndex = requireInteger(input, "headerRowIndex");
            this.append = requireBoolean(input, "append");
            this.separator = String.valueOf(input.getOrDefault("trennzeile", ""));

        } catch (ClassCastException | NullPointerException e) {
            throw new IllegalArgumentException("Ungültige Parameter für ExcelImportConfig", e);
        }
    }


    private String requireString(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value instanceof String) return (String) value;
        throw new IllegalArgumentException("Feld '" + key + "' muss ein String sein.");
    }

    private boolean requireBoolean(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        throw new IllegalArgumentException("Feld '" + key + "' muss ein Boolean sein.");
    }

    private int requireInteger(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value instanceof Number) return ((Number) value).intValue(); // schneidet Nachkommastellen ab
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                Double doubleVal = tryParseDouble((String) value);
                if (doubleVal != null) return doubleVal.intValue(); // optional: kaufmännisch runden: Math.round(doubleVal)
            }
        }
        throw new IllegalArgumentException("Feld '" + key + "' muss eine Ganzzahl sein.");
    }

    private Double tryParseDouble(String str) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
