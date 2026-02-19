package de.bund.zrb.jcl.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single JCL element (JOB, EXEC, DD, etc.) for outline view.
 */
public class JclElement {

    private final JclElementType type;
    private final String name;
    private final int lineNumber;
    private final int endLineNumber;
    private final String rawText;
    private final Map<String, String> parameters = new LinkedHashMap<>();
    private final List<JclElement> children = new ArrayList<>();
    private JclElement parent;

    public JclElement(JclElementType type, String name, int lineNumber, String rawText) {
        this.type = type;
        this.name = name;
        this.lineNumber = lineNumber;
        this.endLineNumber = lineNumber;
        this.rawText = rawText;
    }

    public JclElement(JclElementType type, String name, int lineNumber, int endLineNumber, String rawText) {
        this.type = type;
        this.name = name;
        this.lineNumber = lineNumber;
        this.endLineNumber = endLineNumber;
        this.rawText = rawText;
    }

    public JclElementType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getEndLineNumber() {
        return endLineNumber;
    }

    public String getRawText() {
        return rawText;
    }

    public void addParameter(String key, String value) {
        parameters.put(key, value);
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public String getParameter(String key) {
        return parameters.get(key);
    }

    public void addChild(JclElement child) {
        child.parent = this;
        children.add(child);
    }

    public List<JclElement> getChildren() {
        return children;
    }

    public JclElement getParent() {
        return parent;
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    /**
     * Get display text for outline view.
     */
    public String getDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.getIcon()).append(" ");

        if (name != null && !name.isEmpty()) {
            sb.append(name);
        } else {
            sb.append("(unnamed)");
        }

        // Add key info based on type
        switch (type) {
            case EXEC:
                String pgm = parameters.get("PGM");
                if (pgm != null) {
                    sb.append(" → PGM=").append(pgm);
                } else {
                    // Might be PROC call
                    String proc = parameters.get("PROC");
                    if (proc != null) {
                        sb.append(" → PROC=").append(proc);
                    }
                }
                break;
            case DD:
                String dsn = parameters.get("DSN");
                if (dsn != null) {
                    sb.append(" → ").append(truncate(dsn, 30));
                }
                String disp = parameters.get("DISP");
                if (disp != null) {
                    sb.append(" [").append(disp).append("]");
                }
                break;
            case SET:
                // Show variable assignment
                if (!parameters.isEmpty()) {
                    Map.Entry<String, String> first = parameters.entrySet().iterator().next();
                    sb.append(" ").append(first.getKey()).append("=").append(truncate(first.getValue(), 20));
                }
                break;
            case INCLUDE:
            case COPY_STMT:
                String member = parameters.get("MEMBER");
                if (member == null) member = parameters.get("COPYBOOK");
                if (member != null) {
                    sb.append(" → ").append(member);
                }
                break;
            // COBOL types
            case DATA_ITEM:
                String pic = parameters.get("PIC");
                if (pic != null) {
                    sb.append("  PIC ").append(truncate(pic, 20));
                }
                String value = parameters.get("VALUE");
                if (value != null) {
                    sb.append("  = ").append(truncate(value, 15));
                }
                break;
            case CALL_STMT:
                String target = parameters.get("TARGET");
                if (target != null) {
                    sb.append(" → ").append(target);
                }
                break;
            case PERFORM_STMT:
                String perfTarget = parameters.get("TARGET");
                if (perfTarget != null) {
                    sb.append(" → ").append(perfTarget);
                }
                break;
            case LEVEL_88:
                String val88 = parameters.get("VALUE");
                if (val88 != null) {
                    sb.append("  = ").append(truncate(val88, 25));
                }
                break;
            default:
                break;
        }

        return sb.toString();
    }

    /**
     * Get tooltip text with full details.
     */
    public String getTooltipText() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html>");
        sb.append("<b>").append(type.getDisplayName()).append("</b>");
        if (name != null && !name.isEmpty()) {
            sb.append(": ").append(name);
        }
        sb.append("<br>");
        sb.append("Line: ").append(lineNumber);
        if (endLineNumber > lineNumber) {
            sb.append(" - ").append(endLineNumber);
        }

        if (!parameters.isEmpty()) {
            sb.append("<br><br><b>Parameters:</b><br>");
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                sb.append("&nbsp;&nbsp;").append(entry.getKey())
                  .append(" = ").append(escapeHtml(entry.getValue())).append("<br>");
            }
        }

        sb.append("</html>");
        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public String toString() {
        return getDisplayText();
    }
}

