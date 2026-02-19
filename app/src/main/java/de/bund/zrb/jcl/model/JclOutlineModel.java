package de.bund.zrb.jcl.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a parsed mainframe source document structure (JCL or COBOL) for outline view.
 */
public class JclOutlineModel {

    public enum Language { JCL, COBOL, UNKNOWN }

    private final List<JclElement> elements = new ArrayList<>();
    private String sourceName;
    private int totalLines;
    private Language language = Language.UNKNOWN;

    public void addElement(JclElement element) {
        elements.add(element);
    }

    public List<JclElement> getElements() {
        return elements;
    }

    // ── JCL helpers ─────────────────────────────────────────────────

    public List<JclElement> getJobs() {
        return filterByType(JclElementType.JOB);
    }

    public List<JclElement> getSteps() {
        return filterByType(JclElementType.EXEC);
    }

    public List<JclElement> getProcs() {
        return filterByType(JclElementType.PROC);
    }

    // ── COBOL helpers ───────────────────────────────────────────────

    public List<JclElement> getDivisions() {
        return filterByType(JclElementType.DIVISION);
    }

    public List<JclElement> getSections() {
        return filterByType(JclElementType.SECTION);
    }

    public List<JclElement> getParagraphs() {
        return filterByType(JclElementType.PARAGRAPH);
    }

    public List<JclElement> getDataItems() {
        List<JclElement> items = new ArrayList<>();
        for (JclElement e : elements) {
            if (e.getType() == JclElementType.DATA_ITEM
                    || e.getType() == JclElementType.LEVEL_01
                    || e.getType() == JclElementType.LEVEL_77
                    || e.getType() == JclElementType.LEVEL_88) {
                items.add(e);
            }
        }
        return items;
    }

    // ── generic helpers ─────────────────────────────────────────────

    private List<JclElement> filterByType(JclElementType type) {
        List<JclElement> result = new ArrayList<>();
        for (JclElement e : elements) {
            if (e.getType() == type) {
                result.add(e);
            }
        }
        return result;
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public int getTotalLines() {
        return totalLines;
    }

    public void setTotalLines(int totalLines) {
        this.totalLines = totalLines;
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    public int getElementCount() {
        return elements.size();
    }
}

