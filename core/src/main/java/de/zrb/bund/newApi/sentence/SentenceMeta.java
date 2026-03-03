package de.zrb.bund.newApi.sentence;

import java.util.ArrayList;
import java.util.List;

public class SentenceMeta {

    private List<String> paths = new ArrayList<>();          // Konkrete Pfade (optional)
    private String pathPattern;          // Regex-Muster (optional)
    private boolean append;             // Ob an bestehende Sätze angehängt werden soll
    private List<String> extensions = new ArrayList<>();     // Dateiendungen z.B. ["pdf", "doc"] (optional)
    private String transferMode;        // "ascii" (default) oder "binary" (für FTP)
    private String syntaxStyle;         // RSyntaxTextArea syntax style (e.g. "text/java", "text/jcl") (optional)

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(List<String> path) {
        this.paths = path == null ? new ArrayList<>() : path;
    }

    public boolean isAppend() {
        return append;
    }

    public void setAppend(boolean append) {
        this.append = append;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public void setPathPattern(String pathPattern) {
        this.pathPattern = pathPattern;
    }

    public List<String> getExtensions() {
        return extensions;
    }

    public void setExtensions(List<String> extensions) {
        this.extensions = extensions == null ? new ArrayList<>() : extensions;
    }

    public String getTransferMode() {
        return transferMode;
    }

    public void setTransferMode(String transferMode) {
        this.transferMode = transferMode;
    }

    /**
     * Returns true if binary transfer mode is configured.
     */
    public boolean isBinaryTransfer() {
        return "binary".equalsIgnoreCase(transferMode);
    }

    public String getSyntaxStyle() {
        return syntaxStyle;
    }

    public void setSyntaxStyle(String syntaxStyle) {
        this.syntaxStyle = syntaxStyle;
    }

    /**
     * Returns true if a syntax highlighting style is configured for this entry.
     */
    public boolean hasSyntaxStyle() {
        return syntaxStyle != null && !syntaxStyle.trim().isEmpty();
    }
}
