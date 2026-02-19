package de.bund.zrb.jcl.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a parsed JCL document structure for outline view.
 */
public class JclOutlineModel {

    private final List<JclElement> elements = new ArrayList<>();
    private String sourceName;
    private int totalLines;

    public void addElement(JclElement element) {
        elements.add(element);
    }

    public List<JclElement> getElements() {
        return elements;
    }

    public List<JclElement> getJobs() {
        List<JclElement> jobs = new ArrayList<>();
        for (JclElement e : elements) {
            if (e.getType() == JclElementType.JOB) {
                jobs.add(e);
            }
        }
        return jobs;
    }

    public List<JclElement> getSteps() {
        List<JclElement> steps = new ArrayList<>();
        for (JclElement e : elements) {
            if (e.getType() == JclElementType.EXEC) {
                steps.add(e);
            }
        }
        return steps;
    }

    public List<JclElement> getProcs() {
        List<JclElement> procs = new ArrayList<>();
        for (JclElement e : elements) {
            if (e.getType() == JclElementType.PROC) {
                procs.add(e);
            }
        }
        return procs;
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

