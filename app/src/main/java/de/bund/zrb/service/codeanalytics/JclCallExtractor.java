package de.bund.zrb.service.codeanalytics;

import de.bund.zrb.jcl.model.JclElement;
import de.bund.zrb.jcl.model.JclElementType;
import de.bund.zrb.jcl.model.JclOutlineModel;
import de.bund.zrb.jcl.parser.AntlrJclParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts external calls from JCL source code.
 * <p>
 * External calls in JCL:
 * <ul>
 *   <li>EXEC PGM=xxx — program execution</li>
 *   <li>EXEC PROC=xxx (or EXEC xxx) — cataloged procedure</li>
 *   <li>Natural program from PARM/ZPARM STACK=(LOGON lib;prog)</li>
 * </ul>
 */
class JclCallExtractor implements CallExtractor {

    private final AntlrJclParser parser = new AntlrJclParser();

    private static final Pattern STACK_LOGON_PATTERN =
            Pattern.compile(
                    "STACK\\s*=\\s*\\(\\s*LOGON\\s+([A-Za-z0-9_-]+)\\s*;\\s*([A-Za-z0-9_-]+)",
                    Pattern.CASE_INSENSITIVE);

    @Override
    public List<ExternalCall> extractExternalCalls(String sourceCode, String sourceName) {
        List<ExternalCall> result = new ArrayList<ExternalCall>();
        if (sourceCode == null || sourceCode.isEmpty()) return result;

        JclOutlineModel model = parser.parse(sourceCode, sourceName);
        Set<String> seen = new LinkedHashSet<String>();

        for (JclElement elem : model.getElements()) {
            if (elem.getType() != JclElementType.EXEC) continue;

            String pgm = param(elem, "PGM", null);
            if (pgm != null && !pgm.isEmpty()) {
                String key = "PGM:" + pgm.toUpperCase();
                if (seen.add(key)) {
                    String stepName = elem.getName();
                    result.add(new ExternalCall(pgm.toUpperCase(), "EXEC PGM",
                            elem.getLineNumber(), elem.getRawText()));
                }
            } else {
                String proc = param(elem, "PROC", null);
                if (proc != null && !proc.isEmpty()) {
                    String key = "PROC:" + proc.toUpperCase();
                    if (seen.add(key)) {
                        result.add(new ExternalCall(proc.toUpperCase(), "EXEC PROC",
                                elem.getLineNumber(), elem.getRawText()));
                    }
                }
            }

            // Check for Natural program in PARM/ZPARM
            extractNatural(elem, result, seen);
        }

        return result;
    }

    private void extractNatural(JclElement elem, List<ExternalCall> result, Set<String> seen) {
        String parm = param(elem, "PARM", null);
        String zparm = param(elem, "ZPARM", null);
        String rawText = elem.getRawText();
        String[] sources = { parm, zparm, rawText };

        for (String src : sources) {
            if (src == null || src.isEmpty()) continue;
            Matcher matcher = STACK_LOGON_PATTERN.matcher(src);
            if (matcher.find()) {
                String program = matcher.group(2).toUpperCase();
                String key = "NAT:" + program;
                if (seen.add(key)) {
                    result.add(new ExternalCall(program, "NAT STACK",
                            elem.getLineNumber(), rawText));
                }
                return;
            }
        }
    }

    @Override
    public SourceLanguage getLanguage() {
        return SourceLanguage.JCL;
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
        return fallback;
    }
}

