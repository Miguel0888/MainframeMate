package de.bund.zrb.runtime;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Compile and execute full expression classes with JShell on Java 9+.
 * <p>
 * The source is expected to be a normal Java source file containing
 * optional imports and one top-level expression class with an apply method.
 * <p>
 * This implementation does NOT try to extract method bodies or feed lines individually.
 * Instead, it feeds imports and the full type declaration as proper JShell snippets,
 * then instantiates the class and invokes apply(args).
 * <p>
 * All JShell API access is done via reflection so that this class compiles
 * cleanly under Java 8 (where {@code jdk.jshell} does not exist).
 */
public class JShellExpressionCompiler implements ExpressionCompiler {

    private static final String ARGS_VARIABLE_NAME = "__mmArgs";

    @Override
    public String compileAndExecute(String key, String source, List<String> args) throws Exception {
        Class<?> jshellClass = Class.forName("jdk.jshell.JShell");
        Class<?> snippetEventClass = Class.forName("jdk.jshell.SnippetEvent");

        Object jshell = jshellClass.getMethod("create").invoke(null);
        try {
            SourceParts sourceParts = splitSource(source);

            // 1) Feed imports individually
            evaluateImports(jshellClass, snippetEventClass, jshell, sourceParts.imports);

            // 2) Feed the entire class as one type-declaration snippet
            if (sourceParts.typeDeclaration.trim().isEmpty()) {
                throw new IllegalArgumentException("Expression source does not contain a class declaration.");
            }
            evaluateSnippet(jshellClass, snippetEventClass, jshell, sourceParts.typeDeclaration);

            // 3) Declare args variable
            String argsSnippet = buildArgsSnippet(args);
            evaluateSnippet(jshellClass, snippetEventClass, jshell, argsSnippet);

            // 4) Instantiate and invoke: new Expr_Date().apply(args)
            String className = extractClassName(source, key);
            String invocationSnippet = "new " + className + "().apply(" + ARGS_VARIABLE_NAME + ")";
            Object value = evaluateSnippet(jshellClass, snippetEventClass, jshell, invocationSnippet);

            return value == null ? null : cleanJShellValue(String.valueOf(value));
        } finally {
            jshellClass.getMethod("close").invoke(jshell);
        }
    }

    // ── Snippet evaluation ──────────────────────────────────────────────

    private void evaluateImports(Class<?> jshellClass,
                                 Class<?> snippetEventClass,
                                 Object jshell,
                                 List<String> imports) throws Exception {
        for (int i = 0; i < imports.size(); i++) {
            evaluateSnippet(jshellClass, snippetEventClass, jshell, imports.get(i));
        }
    }

    private Object evaluateSnippet(Class<?> jshellClass,
                                   Class<?> snippetEventClass,
                                   Object jshell,
                                   String snippet) throws Exception {
        Method evalMethod = jshellClass.getMethod("eval", String.class);
        List<?> events = (List<?>) evalMethod.invoke(jshell, snippet);

        Object lastValue = null;

        for (int i = 0; i < events.size(); i++) {
            Object event = events.get(i);

            // Check status
            Object status = snippetEventClass.getMethod("status").invoke(event);
            String statusText = String.valueOf(status);

            if ("REJECTED".equals(statusText)) {
                String diagnostics = getDiagnostics(jshellClass, jshell, event, snippetEventClass);
                throw new IllegalStateException(
                        "JShell rejected snippet: " + snippet
                                + (diagnostics.isEmpty() ? "" : "\n" + diagnostics));
            }

            // Check for runtime exception (SnippetEvent.exception() exists on some JDK versions)
            Object exception = getOptionalEventProperty(snippetEventClass, event, "exception");
            if (exception != null && exception instanceof Throwable) {
                throw new IllegalStateException(
                        "JShell execution failed for snippet: " + snippet,
                        (Throwable) exception);
            }

            // Collect value
            Object value = getOptionalEventProperty(snippetEventClass, event, "value");
            if (value != null) {
                lastValue = value;
            }
        }

        return lastValue;
    }

    private Object getOptionalEventProperty(Class<?> snippetEventClass, Object event, String methodName) {
        try {
            Method method = snippetEventClass.getMethod(methodName);
            return method.invoke(event);
        } catch (Exception ex) {
            return null;
        }
    }

    // ── Source splitting ─────────────────────────────────────────────────

    /**
     * Splits the source into import statements and the remaining type declaration.
     * Package declarations are silently dropped (JShell doesn't support them).
     */
    private SourceParts splitSource(String source) {
        List<String> imports = new ArrayList<String>();
        StringBuilder typeDeclaration = new StringBuilder();

        String[] lines = source.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                if (typeDeclaration.length() > 0) {
                    typeDeclaration.append('\n');
                }
                continue;
            }

            // Drop package declaration (not supported in JShell)
            if (trimmed.startsWith("package ")) {
                continue;
            }

            // Collect import statements separately
            if (trimmed.startsWith("import ")) {
                imports.add(trimmed);
                continue;
            }

            // Everything else is the type declaration
            typeDeclaration.append(line).append('\n');
        }

        return new SourceParts(imports, typeDeclaration.toString().trim());
    }

    // ── Args snippet ────────────────────────────────────────────────────

    private String buildArgsSnippet(List<String> args) {
        StringBuilder sb = new StringBuilder();
        sb.append("java.util.List<String> ").append(ARGS_VARIABLE_NAME)
                .append(" = java.util.Arrays.asList(");

        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("\"").append(escapeJava(args.get(i))).append("\"");
        }

        sb.append(");");
        return sb.toString();
    }

    // ── Class name extraction ───────────────────────────────────────────

    private String extractClassName(String source, String fallback) {
        String[] lines = source.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            if (line.contains(" class ")) {
                // Normalize: remove '{' so tokenizing works cleanly
                String normalized = line.replace("{", " ").trim();
                String[] tokens = normalized.split("\\s+");
                for (int j = 0; j < tokens.length - 1; j++) {
                    if ("class".equals(tokens[j])) {
                        return tokens[j + 1];
                    }
                }
            }
        }

        return "Expr_" + fallback.replaceAll("[^a-zA-Z0-9_$]", "_");
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private String escapeJava(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    /**
     * JShell returns string values wrapped in quotes, e.g. {@code "\"2026-03-10\""}.
     * This method strips the outer quotes if present.
     */
    private String cleanJShellValue(String raw) {
        if (raw == null) {
            return null;
        }

        String trimmed = raw.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String getDiagnostics(Class<?> jshellClass,
                                  Object jshell,
                                  Object event,
                                  Class<?> snippetEventClass) {
        try {
            Object snippet = snippetEventClass.getMethod("snippet").invoke(event);
            Class<?> snippetClass = Class.forName("jdk.jshell.Snippet");
            Method diagnosticsMethod = jshellClass.getMethod("diagnostics", snippetClass);
            Object stream = diagnosticsMethod.invoke(jshell, snippet);

            Method toArrayMethod = stream.getClass().getMethod("toArray");
            Object[] diagnostics = (Object[]) toArrayMethod.invoke(stream);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < diagnostics.length; i++) {
                Object diagnostic = diagnostics[i];
                Method getMessage = diagnostic.getClass().getMethod("getMessage", java.util.Locale.class);
                Object message = getMessage.invoke(diagnostic, java.util.Locale.getDefault());
                if (message != null) {
                    sb.append(message).append('\n');
                }
            }
            return sb.toString().trim();
        } catch (Exception ex) {
            return "";
        }
    }

    // ── Internal model ──────────────────────────────────────────────────

    private static final class SourceParts {

        private final List<String> imports;
        private final String typeDeclaration;

        private SourceParts(List<String> imports, String typeDeclaration) {
            this.imports = imports;
            this.typeDeclaration = typeDeclaration;
        }
    }
}
