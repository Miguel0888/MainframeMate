package de.bund.zrb.runtime;

import java.lang.reflect.Method;
import java.util.List;

/**
 * JShell-based expression compiler for Java 9+.
 * <p>
 * All JShell API access is done via reflection so that this class compiles
 * cleanly under Java 8 (where {@code jdk.jshell} does not exist).
 * <p>
 * The expression source is expected to be a complete Java class implementing
 * {@code Function<Object, Object>}. This compiler:
 * <ol>
 *     <li>Strips the class wrapper and extracts the body of the {@code apply} method</li>
 *     <li>Feeds individual statements to JShell</li>
 *     <li>Returns the last evaluated value as a String</li>
 * </ol>
 * If the source cannot be unwrapped, it is passed to JShell verbatim.
 */
public class JShellExpressionCompiler implements ExpressionCompiler {

    @Override
    public String compileAndExecute(String key, String source, List<String> args) throws Exception {
        // Build JShell snippets from the full class source
        String snippet = buildSnippet(source, args);

        Class<?> jshellClass = Class.forName("jdk.jshell.JShell");
        Class<?> snippetEventClass = Class.forName("jdk.jshell.SnippetEvent");

        Object jshell = jshellClass.getMethod("create").invoke(null);
        try {
            // Feed each statement line to JShell
            String[] lines = snippet.split("\n");
            Object lastValue = null;

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                Method evalMethod = jshellClass.getMethod("eval", String.class);
                List<?> events = (List<?>) evalMethod.invoke(jshell, trimmed);

                for (Object event : events) {
                    // Check for rejection
                    Object status = snippetEventClass.getMethod("status").invoke(event);
                    if ("REJECTED".equals(String.valueOf(status))) {
                        // Try to get diagnostic info
                        String diag = getDiagnostics(jshellClass, jshell, event, snippetEventClass);
                        throw new IllegalStateException(
                                "JShell rejected snippet: " + trimmed
                                        + (diag.isEmpty() ? "" : "\n" + diag));
                    }

                    Object val = snippetEventClass.getMethod("value").invoke(event);
                    if (val != null && !val.toString().trim().isEmpty()) {
                        lastValue = val;
                    }
                }
            }

            return lastValue == null ? null : cleanJShellValue(lastValue.toString());
        } finally {
            jshellClass.getMethod("close").invoke(jshell);
        }
    }

    /**
     * Attempts to extract the body of the {@code apply} method from a full class source,
     * then prepends a variable declaration for the args parameter.
     * Falls back to feeding the raw source if extraction fails.
     */
    private String buildSnippet(String source, List<String> args) {
        StringBuilder sb = new StringBuilder();

        // Declare args as a variable in JShell context
        sb.append("java.util.List<String> args = java.util.Arrays.asList(");
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(escapeJava(args.get(i))).append("\"");
        }
        sb.append(");\n");

        // Try to extract the method body
        String body = extractApplyMethodBody(source);
        if (body != null) {
            sb.append(body);
        } else {
            // Fallback: strip package/import/class wrapper heuristically
            sb.append(stripClassWrapper(source));
        }

        return sb.toString();
    }

    /**
     * Extracts everything between the first {@code public Object apply(Object} line
     * and its matching closing brace.
     */
    private String extractApplyMethodBody(String source) {
        String[] lines = source.split("\n");
        int start = -1;
        int braceDepth = 0;
        boolean inMethod = false;

        StringBuilder body = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();

            if (!inMethod) {
                if (trimmed.contains("public") && trimmed.contains("apply") && trimmed.contains("Object")) {
                    inMethod = true;
                    // Count braces on this line
                    for (char c : lines[i].toCharArray()) {
                        if (c == '{') braceDepth++;
                        if (c == '}') braceDepth--;
                    }
                    start = i;
                }
            } else {
                for (char c : lines[i].toCharArray()) {
                    if (c == '{') braceDepth++;
                    if (c == '}') braceDepth--;
                }

                if (braceDepth <= 0) {
                    // We've found the matching closing brace
                    return body.toString();
                }

                body.append(trimmed).append("\n");
            }
        }

        return start >= 0 ? body.toString() : null;
    }

    /**
     * Simple heuristic: remove package, import, class declaration lines, and outer braces.
     */
    private String stripClassWrapper(String source) {
        StringBuilder sb = new StringBuilder();
        for (String line : source.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("package ")) continue;
            if (trimmed.startsWith("import ")) continue;
            if (trimmed.startsWith("public class ")) continue;
            if (trimmed.equals("}")) continue;
            if (trimmed.startsWith("@Override")) continue;
            if (trimmed.contains("public Object apply(")) continue;
            sb.append(trimmed).append("\n");
        }
        return sb.toString();
    }

    private String escapeJava(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Remove JShell's trailing type annotation like {@code "42\n" → "42"}.
     */
    private String cleanJShellValue(String raw) {
        if (raw == null) return null;
        // JShell wraps string values in quotes
        String trimmed = raw.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String getDiagnostics(Class<?> jshellClass, Object jshell,
                                  Object event, Class<?> snippetEventClass) {
        try {
            Object snippet = snippetEventClass.getMethod("snippet").invoke(event);
            Class<?> snippetClass = Class.forName("jdk.jshell.Snippet");
            Method diagnosticsMethod = jshellClass.getMethod("diagnostics", snippetClass);
            Object diagStream = diagnosticsMethod.invoke(jshell, snippet);
            // diagStream is a Stream – convert to list
            Method toArrayMethod = diagStream.getClass().getMethod("toArray");
            Object[] diags = (Object[]) toArrayMethod.invoke(diagStream);
            StringBuilder sb = new StringBuilder();
            for (Object d : diags) {
                Method getMessage = d.getClass().getMethod("getMessage", java.util.Locale.class);
                sb.append(getMessage.invoke(d, java.util.Locale.getDefault())).append("\n");
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }
}

