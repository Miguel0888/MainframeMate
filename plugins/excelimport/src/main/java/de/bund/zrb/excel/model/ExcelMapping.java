package de.bund.zrb.excel.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ExcelMapping {

    private String name;                     // z. B. "kundendaten"
    private String sentenceType;             // Referenz auf Satzart
    private List<ExcelMappingEntry> entries; // Zuordnungen

    public ExcelMapping() {
        this.entries = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSentenceType() {
        return sentenceType;
    }

    public void setSentenceType(String sentenceType) {
        this.sentenceType = sentenceType;
    }

    public List<ExcelMappingEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<ExcelMappingEntry> entries) {
        this.entries = entries;
    }

    public void addEntry(ExcelMappingEntry entry) {
        this.entries.add(entry);
    }

    public String getContentForFieldName(String fieldName, Function<String, String> columnValueProvider) {
        for (ExcelMappingEntry entry : entries) {
            if (entry.getFieldName() != null && entry.getFieldName().equals(fieldName)) {
                if (entry.isFixed()) {
                    return entry.getFixedValue();
                }
                if (entry.isDynamic()) {
                    return evaluateExpression(entry.getExpression());
                }
                if (entry.isFromColumn()) {
                    return columnValueProvider.apply(entry.getExcelColumn());
                }
            }
        }
        return ""; // kein Mapping? → leer lassen
    }

    private String evaluateExpression(String expression) {

        // TODO
        return "";
    }

}
