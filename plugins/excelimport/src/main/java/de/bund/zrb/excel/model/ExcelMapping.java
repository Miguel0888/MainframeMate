package de.bund.zrb.excel.model;

import java.util.ArrayList;
import java.util.List;

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
}
