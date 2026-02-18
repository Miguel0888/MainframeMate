package de.bund.zrb.excel.model;

import de.zrb.bund.newApi.mcp.ToolSpec;

import java.io.File;
import java.util.Map;
import java.util.function.Function;

public class ExcelImportConfig {
    private File file;
    private String destination;
    private String satzart;
    private String templateName; // kann über Satzart gefunden werden, kann aber zu mehreren Ergebnissen führen
    private boolean hasHeader = true;
    private int headerRowIndex = 0;
    private boolean append = false;
    private String separator = "";

    private boolean forceReload = false;

    public String getSearchPattern() {
        return searchPattern;
    }

    public void setSearchPattern(String searchPattern) {
        this.searchPattern = searchPattern;
    }

    public Boolean getToCompare() {
        return toCompare;
    }

    public void setToCompare(Boolean toCompare) {
        this.toCompare = toCompare;
    }

    private String searchPattern = null;
    private Boolean toCompare = null;

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

    public boolean isForceReload() {
        return forceReload;
    }

    public void setForceReload(boolean forceReload) {
        this.forceReload = forceReload;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getSatzart() {
        return satzart;
    }

    public void setSatzart(String satzart) {
        this.satzart = satzart;
    }

    /**
     * Konstruktor für die ExcelImportConfig.
     *
     * @param input           Eingabedaten als Map
     * @param schema          Schema für die Validierung der Eingabedaten
     * @param templateResolver Funktion, um eine ExcelMapping-Vorlage anhand der Satzart zu finden
     */
    public ExcelImportConfig(Map<String, Object> input,
                             ToolSpec.InputSchema schema,
                             Function<String, ExcelMapping> templateResolver) {
        try {
            this.file = new File(requireString("file", input, schema));
            this.destination = optionalString("destination", input, schema, this.destination);
            this.hasHeader = optionalBoolean("hasHeader", input, schema, this.hasHeader);
            this.headerRowIndex = optionalInteger("headerRowIndex", input, schema, this.headerRowIndex);
            this.append = optionalBoolean("append", input, schema, this.append);
            this.separator = optionalString("separator", input, schema, this.separator);
            this.forceReload = optionalBoolean("forceReload", input, schema, this.forceReload);
            this.searchPattern = optionalString("search", input, schema, this.searchPattern);
            this.toCompare = optionalBoolean("toCompare", input, schema, this.toCompare);

            resolveTemplate(input, schema, templateResolver);

        } catch (ClassCastException | NullPointerException e) {
            throw new IllegalArgumentException("Ungültige Parameter für ExcelImportConfig", e);
        }
    }

    // Determine and set the template name using explicit value or resolver
    private void resolveTemplate(Map<String, Object> input,
                                 ToolSpec.InputSchema schema,
                                 Function<String, ExcelMapping> templateResolver) {
        String explicitTemplate = optionalString("template", input, schema, null);

        if (!isEmpty(explicitTemplate)) {
            this.templateName = explicitTemplate;
            return;
        }

        String satzart = optionalString("satzart", input, schema, null);

        if (!isEmpty(satzart) && templateResolver != null) {
            ExcelMapping mapping = templateResolver.apply(satzart);
            this.templateName = (mapping != null) ? mapping.getName() : satzart;
            return;
        }

        throw new IllegalArgumentException("Template ist erforderlich, konnte aber nicht bestimmt werden.");
    }

    private String requireString(String key, Map<String, Object> input, ToolSpec.InputSchema schema) {
        Object value = input.get(key);
        if (value instanceof String) return (String) value;
        if (schema.getRequired().contains(key)) {
            throw new IllegalArgumentException("Pflichtfeld '" + key + "' fehlt oder ist kein String.");
        }
        return null;
    }

    private String optionalString(String key, Map<String, Object> input, ToolSpec.InputSchema schema, String defaultValue) {
        Object value = input.get(key);
        if (value instanceof String) return (String) value;
        return defaultValue;
    }

    private Boolean optionalBoolean(String key, Map<String, Object> input, ToolSpec.InputSchema schema, Boolean defaultValue) {
        Object value = input.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return defaultValue;
    }

    private int optionalInteger(String key, Map<String, Object> input, ToolSpec.InputSchema schema, int defaultValue) {
        Object value = input.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                Double d = tryParseDouble((String) value);
                if (d != null) return d.intValue();
            }
        }
        return defaultValue;
    }

    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }


    private Double tryParseDouble(String str) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
