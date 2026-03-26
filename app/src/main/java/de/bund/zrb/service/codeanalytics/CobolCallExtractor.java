package de.bund.zrb.service.codeanalytics;

import de.bund.zrb.jcl.model.JclElement;
import de.bund.zrb.jcl.model.JclElementType;
import de.bund.zrb.jcl.model.JclOutlineModel;
import de.bund.zrb.jcl.parser.CobolParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Extracts external calls from COBOL source code.
 * <p>
 * External calls in COBOL:
 * <ul>
 *   <li>CALL 'PROGRAM' — call to external program</li>
 * </ul>
 * <p>
 * NOT external: PERFORM (calls paragraphs within same source).
 */
class CobolCallExtractor implements CallExtractor {

    private final CobolParser parser = new CobolParser();

    @Override
    public List<ExternalCall> extractExternalCalls(String sourceCode, String sourceName) {
        List<ExternalCall> result = new ArrayList<ExternalCall>();
        if (sourceCode == null || sourceCode.isEmpty()) return result;

        JclOutlineModel model = parser.parse(sourceCode, sourceName);
        Set<String> seen = new LinkedHashSet<String>();

        for (JclElement elem : model.getElements()) {
            if (elem.getType() != JclElementType.CALL_STMT) continue;

            String target = param(elem, "TARGET", elem.getName());
            if (target == null || target.isEmpty()) continue;

            String key = "CALL:" + target.toUpperCase();
            if (!seen.add(key)) continue;

            result.add(new ExternalCall(target, "CALL", elem.getLineNumber(), elem.getRawText()));
        }

        return result;
    }

    @Override
    public SourceLanguage getLanguage() {
        return SourceLanguage.COBOL;
    }

    private static String param(JclElement elem, String key, String fallback) {
        String val = elem.getParameter(key);
        if (val != null && !val.isEmpty()) {
            if (val.length() >= 2
                    && ((val.charAt(0) == '\'' && val.charAt(val.length() - 1) == '\'')
                    || (val.charAt(0) == '"' && val.charAt(val.length() - 1) == '"'))) {
                val = val.substring(1, val.length() - 1);
            }
            return val;
        }
        return fallback != null ? fallback : "";
    }
}

