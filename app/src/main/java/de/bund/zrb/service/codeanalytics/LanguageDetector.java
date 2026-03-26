package de.bund.zrb.service.codeanalytics;

/**
 * Heuristic language detection for mainframe source code.
 */
final class LanguageDetector {

    private LanguageDetector() {}

    /**
     * Detect the language of source code by scanning the first lines.
     */
    static SourceLanguage detect(String content) {
        if (content == null || content.isEmpty()) return SourceLanguage.UNKNOWN;

        String[] lines = content.split("\\r?\\n", 50);
        int naturalHits = 0;
        int cobolHits = 0;
        int jclHits = 0;

        for (String line : lines) {
            String trimmed = line.trim().toUpperCase();

            // Natural indicators
            if (trimmed.startsWith("DEFINE DATA")
                    || trimmed.startsWith("END-DEFINE")
                    || trimmed.startsWith("DEFINE SUBROUTINE")
                    || trimmed.startsWith("CALLNAT ")
                    || trimmed.startsWith("END-SUBROUTINE")
                    || trimmed.startsWith("LOCAL USING")
                    || trimmed.startsWith("PARAMETER USING")
                    || trimmed.startsWith("DECIDE ON")
                    || trimmed.startsWith("DECIDE FOR")
                    || trimmed.startsWith("INPUT USING MAP")
                    || trimmed.startsWith("FETCH RETURN")) {
                naturalHits++;
            }

            // COBOL indicators
            if (trimmed.contains("IDENTIFICATION DIVISION")
                    || trimmed.contains("PROCEDURE DIVISION")
                    || trimmed.contains("DATA DIVISION")
                    || trimmed.contains("ENVIRONMENT DIVISION")
                    || trimmed.contains("WORKING-STORAGE SECTION")
                    || trimmed.contains("PROGRAM-ID")) {
                cobolHits++;
            }

            // JCL indicators
            if (trimmed.startsWith("//")) {
                jclHits++;
            }
        }

        // Priority: Natural > COBOL > JCL (Natural is most specific)
        if (naturalHits >= 2) return SourceLanguage.NATURAL;
        if (cobolHits >= 1) return SourceLanguage.COBOL;
        if (jclHits >= 3) return SourceLanguage.JCL;

        // Fallback: single hits
        if (naturalHits > 0) return SourceLanguage.NATURAL;
        if (cobolHits > 0) return SourceLanguage.COBOL;
        if (jclHits > 0) return SourceLanguage.JCL;

        return SourceLanguage.UNKNOWN;
    }
}

