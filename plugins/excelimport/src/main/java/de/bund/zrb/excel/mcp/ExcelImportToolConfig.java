package de.bund.zrb.excel.mcp;

import de.zrb.bund.newApi.mcp.ToolConfig;

/**
 * Configuration for the Excel Import tool.
 * These settings control import behavior and are persisted via Gson serialization.
 */
public class ExcelImportToolConfig extends ToolConfig {

    private String lastJsonPath = "";
    private String lastExcelPath = "";
    private String separator = "";
    private boolean showConfirmation = true;
    private boolean autoOpen = true;
    private boolean stopOnEmptyRequired = true;
    private boolean requireAllFieldsEmpty = false;

    public ExcelImportToolConfig() {
        // Default constructor for Gson
    }

    @Override
    public boolean isEmpty() {
        return false; // Always has config fields
    }

    // ── Getters ──────────────────────────────────────────────────────

    public String getLastJsonPath() { return lastJsonPath; }
    public String getLastExcelPath() { return lastExcelPath; }
    public String getSeparator() { return separator; }
    public boolean isShowConfirmation() { return showConfirmation; }
    public boolean isAutoOpen() { return autoOpen; }
    public boolean isStopOnEmptyRequired() { return stopOnEmptyRequired; }
    public boolean isRequireAllFieldsEmpty() { return requireAllFieldsEmpty; }

    // ── Setters ──────────────────────────────────────────────────────

    public void setLastJsonPath(String lastJsonPath) { this.lastJsonPath = lastJsonPath; }
    public void setLastExcelPath(String lastExcelPath) { this.lastExcelPath = lastExcelPath; }
    public void setSeparator(String separator) { this.separator = separator; }
    public void setShowConfirmation(boolean showConfirmation) { this.showConfirmation = showConfirmation; }
    public void setAutoOpen(boolean autoOpen) { this.autoOpen = autoOpen; }
    public void setStopOnEmptyRequired(boolean stopOnEmptyRequired) { this.stopOnEmptyRequired = stopOnEmptyRequired; }
    public void setRequireAllFieldsEmpty(boolean requireAllFieldsEmpty) { this.requireAllFieldsEmpty = requireAllFieldsEmpty; }
}

