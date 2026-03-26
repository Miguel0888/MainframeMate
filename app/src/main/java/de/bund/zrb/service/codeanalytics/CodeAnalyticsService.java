package de.bund.zrb.service.codeanalytics;

import java.util.List;

/**
 * Central service for cross-file code analysis — analogous to IntelliJ's Code Analytics.
 * <p>
 * Provides language-agnostic APIs for:
 * <ul>
 *   <li><b>Callees</b> — external calls from a file (Active XRefs)</li>
 *   <li><b>Callers</b> — who calls this file (Passive XRefs) — future</li>
 *   <li><b>Call chains</b> — recursive callee/caller hierarchies to configurable depth</li>
 *   <li><b>Dependencies</b> — forward dependency chains (analogous to callees but for includes, data areas, etc.)</li>
 *   <li><b>Dependents</b> — backward dependency chains (who depends on this)</li>
 * </ul>
 * <p>
 * This is <em>not</em> an outline service — it only handles cross-file analysis.
 * <p>
 * Thread-safe singleton. Language-specific logic is delegated to {@link CallExtractor} implementations.
 */
public class CodeAnalyticsService {

    private static volatile CodeAnalyticsService instance;

    private final NaturalCallExtractor naturalExtractor = new NaturalCallExtractor();
    private final CobolCallExtractor cobolExtractor = new CobolCallExtractor();
    private final JclCallExtractor jclExtractor = new JclCallExtractor();

    public static synchronized CodeAnalyticsService getInstance() {
        if (instance == null) {
            instance = new CodeAnalyticsService();
        }
        return instance;
    }

    private CodeAnalyticsService() {}

    // ═══════════════════════════════════════════════════════════
    //  Language detection + extractor selection
    // ═══════════════════════════════════════════════════════════

    /**
     * Detect language of source code and return the appropriate extractor.
     */
    public CallExtractor getExtractor(SourceLanguage language) {
        switch (language) {
            case NATURAL: return naturalExtractor;
            case COBOL:   return cobolExtractor;
            case JCL:     return jclExtractor;
            default:      return null;
        }
    }

    /**
     * Auto-detect the language from source content.
     */
    public SourceLanguage detectLanguage(String content) {
        if (content == null || content.isEmpty()) return SourceLanguage.UNKNOWN;
        return LanguageDetector.detect(content);
    }

    // ═══════════════════════════════════════════════════════════
    //  High-level API: External calls (callees)
    // ═══════════════════════════════════════════════════════════

    /**
     * Extract all external calls from the given source code.
     * Only returns calls that reference objects <em>outside</em> the current file
     * (CALLNAT, FETCH, CALL for Natural; CALL for COBOL; EXEC PGM/PROC for JCL).
     *
     * @param sourceCode the source text
     * @param sourceName file/object name
     * @param language   source language (or UNKNOWN for auto-detection)
     * @return list of external call references, never null
     */
    public List<ExternalCall> extractExternalCalls(String sourceCode, String sourceName,
                                                    SourceLanguage language) {
        if (language == SourceLanguage.UNKNOWN) {
            language = detectLanguage(sourceCode);
        }
        CallExtractor extractor = getExtractor(language);
        if (extractor == null) {
            return java.util.Collections.emptyList();
        }
        return extractor.extractExternalCalls(sourceCode, sourceName);
    }

    /**
     * Build a recursive call tree starting from the given source.
     * Each external call target is resolved (if a {@link SourceResolver} is provided)
     * and its external calls are recursively extracted up to {@code maxDepth}.
     *
     * @param sourceCode  root source code
     * @param sourceName  root source name
     * @param language    source language
     * @param maxDepth    maximum recursion depth (1 = only direct calls)
     * @param resolver    resolves target names to source code (may return null for unresolvable)
     * @return root node of the call tree
     */
    public CallTreeNode buildCallTree(String sourceCode, String sourceName,
                                       SourceLanguage language, int maxDepth,
                                       SourceResolver resolver) {
        if (language == SourceLanguage.UNKNOWN) {
            language = detectLanguage(sourceCode);
        }
        CallExtractor extractor = getExtractor(language);
        if (extractor == null) {
            return new CallTreeNode(sourceName, null, 0);
        }

        CallTreeNode root = new CallTreeNode(sourceName, null, 0);
        java.util.Set<String> visited = new java.util.HashSet<String>();
        visited.add(sourceName.toUpperCase());
        buildCallTreeRecursive(root, sourceCode, language, extractor, maxDepth, 0,
                               visited, resolver);
        return root;
    }

    private void buildCallTreeRecursive(CallTreeNode parent, String sourceCode,
                                         SourceLanguage language, CallExtractor extractor,
                                         int maxDepth, int currentDepth,
                                         java.util.Set<String> visited,
                                         SourceResolver resolver) {
        if (currentDepth >= maxDepth || sourceCode == null) return;

        List<ExternalCall> calls = extractor.extractExternalCalls(sourceCode, parent.getName());

        for (ExternalCall call : calls) {
            String targetKey = call.getTargetName().toUpperCase();
            boolean recursive = visited.contains(targetKey);
            CallTreeNode child = new CallTreeNode(call.getTargetName(), call.getCallType(),
                                                   call.getLineNumber());
            child.setRecursive(recursive);
            parent.addChild(child);

            if (!recursive && resolver != null) {
                visited.add(targetKey);
                String targetSource = resolver.resolve(call.getTargetName());
                if (targetSource != null) {
                    // Auto-detect target language (may differ from parent, e.g. JCL calling Natural)
                    SourceLanguage targetLang = detectLanguage(targetSource);
                    CallExtractor targetExtractor = getExtractor(targetLang);
                    if (targetExtractor == null) targetExtractor = extractor;
                    buildCallTreeRecursive(child, targetSource, targetLang, targetExtractor,
                                           maxDepth, currentDepth + 1, visited, resolver);
                }
                visited.remove(targetKey);
            }
        }
    }
}

