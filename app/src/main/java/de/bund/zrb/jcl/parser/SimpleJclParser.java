package de.bund.zrb.jcl.parser;

import de.bund.zrb.jcl.model.JclElement;
import de.bund.zrb.jcl.model.JclElementType;
import de.bund.zrb.jcl.model.JclOutlineModel;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple JCL parser for outline extraction.
 * This is a regex-based parser that extracts structural elements from JCL.
 * It can be replaced by ANTLR-generated parser for more accurate parsing.
 */
public class SimpleJclParser {

    // JCL line patterns
    private static final Pattern JOB_PATTERN = Pattern.compile(
            "^//([A-Z@#$][A-Z0-9@#$]*)\\s+JOB\\s*(.*)?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern EXEC_PATTERN = Pattern.compile(
            "^//([A-Z@#$][A-Z0-9@#$]*)?\\s*EXEC\\s+(.*)?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern DD_PATTERN = Pattern.compile(
            "^//([A-Z@#$][A-Z0-9@#$]*)?\\s*DD\\s*(.*)?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern PROC_PATTERN = Pattern.compile(
            "^//([A-Z@#$][A-Z0-9@#$]*)?\\s*PROC\\s*(.*)?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern PEND_PATTERN = Pattern.compile(
            "^//\\s*PEND\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern SET_PATTERN = Pattern.compile(
            "^//\\s*SET\\s+(.*)?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern INCLUDE_PATTERN = Pattern.compile(
            "^//\\s*INCLUDE\\s+(.*)?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern JCLLIB_PATTERN = Pattern.compile(
            "^//\\s*JCLLIB\\s+(.*)?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern IF_PATTERN = Pattern.compile(
            "^//\\s*IF\\s+(.*)\\s+THEN\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern ELSE_PATTERN = Pattern.compile(
            "^//\\s*ELSE\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern ENDIF_PATTERN = Pattern.compile(
            "^//\\s*ENDIF\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern OUTPUT_PATTERN = Pattern.compile(
            "^//([A-Z@#$][A-Z0-9@#$]*)?\\s*OUTPUT\\s*(.*)?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern COMMENT_PATTERN = Pattern.compile(
            "^//\\*(.*)$");

    private static final Pattern PARAM_PATTERN = Pattern.compile(
            "([A-Z@#$][A-Z0-9@#$]*)=([^,\\s]+|\\([^)]*\\)|'[^']*')", Pattern.CASE_INSENSITIVE);

    /**
     * Parse JCL content and return outline model.
     */
    public JclOutlineModel parse(String content, String sourceName) {
        JclOutlineModel model = new JclOutlineModel();
        model.setSourceName(sourceName);

        if (content == null || content.isEmpty()) {
            return model;
        }

        String[] lines = content.split("\\r?\\n");
        model.setTotalLines(lines.length);

        JclElement currentJob = null;
        JclElement currentStep = null;
        JclElement currentProc = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;

            // Skip empty lines
            if (line.trim().isEmpty()) {
                continue;
            }

            // Skip lines that don't start with //
            if (!line.startsWith("//")) {
                continue;
            }

            Matcher m;

            // Check for JOB
            m = JOB_PATTERN.matcher(line);
            if (m.matches()) {
                String name = m.group(1);
                String params = m.group(2);
                JclElement job = new JclElement(JclElementType.JOB, name, lineNum, line);
                parseParameters(params, job);
                model.addElement(job);
                currentJob = job;
                currentStep = null;
                continue;
            }

            // Check for EXEC
            m = EXEC_PATTERN.matcher(line);
            if (m.matches()) {
                String name = m.group(1);
                String params = m.group(2);
                JclElement exec = new JclElement(JclElementType.EXEC, name != null ? name : "", lineNum, line);
                parseParameters(params, exec);

                // Try to extract PGM or PROC from parameters
                if (params != null) {
                    extractExecTarget(params, exec);
                }

                model.addElement(exec);
                if (currentJob != null) {
                    currentJob.addChild(exec);
                }
                currentStep = exec;
                continue;
            }

            // Check for DD
            m = DD_PATTERN.matcher(line);
            if (m.matches()) {
                String name = m.group(1);
                String params = m.group(2);
                JclElement dd = new JclElement(JclElementType.DD, name != null ? name : "", lineNum, line);
                parseParameters(params, dd);
                model.addElement(dd);
                if (currentStep != null) {
                    currentStep.addChild(dd);
                }
                continue;
            }

            // Check for PROC
            m = PROC_PATTERN.matcher(line);
            if (m.matches()) {
                String name = m.group(1);
                String params = m.group(2);
                JclElement proc = new JclElement(JclElementType.PROC, name != null ? name : "", lineNum, line);
                parseParameters(params, proc);
                model.addElement(proc);
                currentProc = proc;
                continue;
            }

            // Check for PEND
            m = PEND_PATTERN.matcher(line);
            if (m.matches()) {
                JclElement pend = new JclElement(JclElementType.PEND, "", lineNum, line);
                model.addElement(pend);
                if (currentProc != null) {
                    currentProc.addChild(pend);
                }
                currentProc = null;
                continue;
            }

            // Check for SET
            m = SET_PATTERN.matcher(line);
            if (m.matches()) {
                String params = m.group(1);
                JclElement set = new JclElement(JclElementType.SET, "", lineNum, line);
                parseParameters(params, set);
                model.addElement(set);
                continue;
            }

            // Check for INCLUDE
            m = INCLUDE_PATTERN.matcher(line);
            if (m.matches()) {
                String params = m.group(1);
                JclElement include = new JclElement(JclElementType.INCLUDE, "", lineNum, line);
                parseParameters(params, include);
                model.addElement(include);
                continue;
            }

            // Check for JCLLIB
            m = JCLLIB_PATTERN.matcher(line);
            if (m.matches()) {
                String params = m.group(1);
                JclElement jcllib = new JclElement(JclElementType.JCLLIB, "", lineNum, line);
                parseParameters(params, jcllib);
                model.addElement(jcllib);
                continue;
            }

            // Check for IF
            m = IF_PATTERN.matcher(line);
            if (m.matches()) {
                String condition = m.group(1);
                JclElement ifStmt = new JclElement(JclElementType.IF, condition, lineNum, line);
                model.addElement(ifStmt);
                continue;
            }

            // Check for ELSE
            m = ELSE_PATTERN.matcher(line);
            if (m.matches()) {
                JclElement elseStmt = new JclElement(JclElementType.ELSE, "", lineNum, line);
                model.addElement(elseStmt);
                continue;
            }

            // Check for ENDIF
            m = ENDIF_PATTERN.matcher(line);
            if (m.matches()) {
                JclElement endif = new JclElement(JclElementType.ENDIF, "", lineNum, line);
                model.addElement(endif);
                continue;
            }

            // Check for OUTPUT
            m = OUTPUT_PATTERN.matcher(line);
            if (m.matches()) {
                String name = m.group(1);
                String params = m.group(2);
                JclElement output = new JclElement(JclElementType.OUTPUT, name != null ? name : "", lineNum, line);
                parseParameters(params, output);
                model.addElement(output);
                continue;
            }

            // Check for comment
            m = COMMENT_PATTERN.matcher(line);
            if (m.matches()) {
                // Only add significant comments (not empty ones)
                String commentText = m.group(1).trim();
                if (!commentText.isEmpty() && commentText.length() > 2) {
                    JclElement comment = new JclElement(JclElementType.COMMENT, commentText, lineNum, line);
                    model.addElement(comment);
                }
                continue;
            }
        }

        return model;
    }

    /**
     * Parse parameters from a parameter string and add to element.
     */
    private void parseParameters(String params, JclElement element) {
        if (params == null || params.isEmpty()) {
            return;
        }

        Matcher m = PARAM_PATTERN.matcher(params);
        while (m.find()) {
            String key = m.group(1).toUpperCase();
            String value = m.group(2);
            // Remove surrounding quotes if present
            if (value.startsWith("'") && value.endsWith("'")) {
                value = value.substring(1, value.length() - 1);
            }
            element.addParameter(key, value);
        }
    }

    /**
     * Extract PGM= or PROC= from EXEC parameters.
     */
    private void extractExecTarget(String params, JclElement exec) {
        // Check for PGM=
        Pattern pgmPattern = Pattern.compile("PGM=([A-Z@#$][A-Z0-9@#$]*)", Pattern.CASE_INSENSITIVE);
        Matcher m = pgmPattern.matcher(params);
        if (m.find()) {
            exec.addParameter("PGM", m.group(1));
            return;
        }

        // Check for PROC= (explicit)
        Pattern procPattern = Pattern.compile("PROC=([A-Z@#$][A-Z0-9@#$]*)", Pattern.CASE_INSENSITIVE);
        m = procPattern.matcher(params);
        if (m.find()) {
            exec.addParameter("PROC", m.group(1));
            return;
        }

        // Check for implicit PROC call (first positional parameter)
        String trimmed = params.trim();
        if (!trimmed.isEmpty() && !trimmed.contains("=")) {
            // First word is probably the PROC name
            String[] parts = trimmed.split("[,\\s]");
            if (parts.length > 0 && parts[0].matches("[A-Z@#$][A-Z0-9@#$]*")) {
                exec.addParameter("PROC", parts[0]);
            }
        }
    }
}

