package de.bund.zrb.service.codeanalytics;

import java.util.List;

/**
 * Language-specific extractor for external calls from source code.
 * Implementations exist for Natural, COBOL, and JCL.
 */
public interface CallExtractor {

    /**
     * Extract all external calls from the given source code.
     * <p>
     * "External" means the call target is outside the current file:
     * <ul>
     *   <li>Natural: CALLNAT, FETCH, CALL (3GL) — but NOT inline PERFORM</li>
     *   <li>COBOL: CALL — but NOT internal PERFORM (paragraph calls)</li>
     *   <li>JCL: EXEC PGM=, EXEC PROC=, Natural program from STACK=(LOGON ...)</li>
     * </ul>
     *
     * @param sourceCode the source text
     * @param sourceName name of the source object
     * @return list of external calls, never null
     */
    List<ExternalCall> extractExternalCalls(String sourceCode, String sourceName);

    /**
     * @return the language this extractor handles
     */
    SourceLanguage getLanguage();
}

