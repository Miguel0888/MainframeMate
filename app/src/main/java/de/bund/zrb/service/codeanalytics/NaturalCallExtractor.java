package de.bund.zrb.service.codeanalytics;

import de.bund.zrb.jcl.model.JclElement;
import de.bund.zrb.jcl.model.JclElementType;
import de.bund.zrb.jcl.model.JclOutlineModel;
import de.bund.zrb.jcl.parser.NaturalParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Extracts external calls from Natural source code.
 * <p>
 * External calls in Natural:
 * <ul>
 *   <li>CALLNAT — call to external subprogram</li>
 *   <li>FETCH / FETCH RETURN — invoke external program</li>
 *   <li>CALL — 3GL call to external routine</li>
 * </ul>
 * <p>
 * NOT external: PERFORM (calls inline subroutines within same source).
 */
class NaturalCallExtractor implements CallExtractor {

    private final NaturalParser parser = new NaturalParser();

    @Override
    public List<ExternalCall> extractExternalCalls(String sourceCode, String sourceName) {
        List<ExternalCall> result = new ArrayList<ExternalCall>();
        if (sourceCode == null || sourceCode.isEmpty()) return result;

        JclOutlineModel model = parser.parse(sourceCode, sourceName);
        Set<String> seen = new LinkedHashSet<String>(); // dedup by target+type

        for (JclElement elem : model.getElements()) {
            JclElementType type = elem.getType();
            String target;
            String callType;

            switch (type) {
                case NAT_CALLNAT:
                    target = param(elem, "TARGET", elem.getName());
                    callType = "CALLNAT";
                    break;
                case NAT_FETCH:
                    target = param(elem, "TARGET", elem.getName());
                    callType = "FETCH";
                    break;
                case NAT_CALL:
                    target = param(elem, "TARGET", elem.getName());
                    callType = "CALL";
                    break;
                default:
                    continue;
            }

            if (target == null || target.isEmpty()) continue;
            String key = callType + ":" + target.toUpperCase();
            if (!seen.add(key)) continue; // skip duplicates

            result.add(new ExternalCall(target, callType, elem.getLineNumber(), elem.getRawText()));
        }

        return result;
    }

    @Override
    public SourceLanguage getLanguage() {
        return SourceLanguage.NATURAL;
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

