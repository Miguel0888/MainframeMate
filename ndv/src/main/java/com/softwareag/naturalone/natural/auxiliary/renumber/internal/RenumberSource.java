package com.softwareag.naturalone.natural.auxiliary.renumber.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RenumberSource {

    private static final Pattern LINE_REFERENCE_PATTERN = Pattern.compile("\\([0-9]{4}[)/,]");
    private static final Pattern EXISTING_LABEL_PATTERN = Pattern.compile("^[a-zA-Z]*[0-9]*[.]");

    private RenumberSource() {
    }

    private static Pattern getLineReferencePattern() {
        return LINE_REFERENCE_PATTERN;
    }

    // ── 5) isLineReference ──────────────────────────────────────────────
    public static boolean isLineReference(int pos, String line) {
        if (pos + 6 > line.length()) {
            return false;
        }
        String candidate = line.substring(pos, pos + 6);
        return LINE_REFERENCE_PATTERN.matcher(candidate).matches();
    }

    // ── 6) isLineNumberReference ────────────────────────────────────────
    public static boolean isLineNumberReference(int pos, String line,
                                                boolean insertLabelsMode,
                                                boolean hasLineNumberPrefix,
                                                boolean renConst) {
        if (pos == -1) {
            return false;
        }
        if (!isLineReference(pos, line)) {
            return false;
        }

        int offset = hasLineNumberPrefix ? 5 : 0;

        // Check comment markers at offset
        if (offset < line.length()) {
            String fromOffset = line.substring(offset);
            if (fromOffset.startsWith("* ") || fromOffset.startsWith("**") || fromOffset.startsWith("*/")) {
                return !insertLabelsMode;
            }
        }

        // Scan from 0 to pos-1 for quotes and block comments
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < pos; i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (!inSingleQuote && !inDoubleQuote && c == '/' && i + 1 < pos && line.charAt(i + 1) == '*') {
                return !insertLabelsMode;
            }
        }

        if (inSingleQuote || inDoubleQuote) {
            return renConst;
        }

        return true;
    }

    // ── 3) addLineNumbers ───────────────────────────────────────────────
    public static StringBuffer[] addLineNumbers(String[] source, int step,
                                                String labelPrefix,
                                                boolean updateRefs,
                                                boolean openSystemsServer,
                                                boolean renConst) {
        if (source == null || source.length == 0) {
            return new StringBuffer[0];
        }

        // 3.1 Normalize step
        if (step == 0) {
            step = 1;
        }
        if ((long) step * source.length > 9999) {
            step = 10;
            while (step > 1 && (9999 / source.length) < step) {
                step = step / 2;
            }
        }

        // 3.2 Label-Mode detection
        boolean labelMode = false;
        if (labelPrefix != null && !labelPrefix.isEmpty()) {
            for (String line : source) {
                if (line != null && line.startsWith(labelPrefix)) {
                    int dotIndex = line.indexOf('.', labelPrefix.length());
                    if (dotIndex >= 0) {
                        String numPart = line.substring(labelPrefix.length(), dotIndex);
                        try {
                            Integer.parseInt(numPart);
                            labelMode = true;
                            break;
                        } catch (NumberFormatException e) {
                            // not a label definition
                        }
                    }
                }
            }
        }

        StringBuffer[] result = new StringBuffer[source.length];

        if (!labelMode) {
            // 3.3.1 Normal mode
            for (int i = 0; i < source.length; i++) {
                int lineNo = (i + 1) * step;
                String line = source[i] != null ? source[i] : "";

                if (updateRefs) {
                    // Replace references: scan for '('
                    StringBuilder sb = new StringBuilder(line);
                    for (int p = 0; p < sb.length(); p++) {
                        if (sb.charAt(p) == '(') {
                            if (isLineNumberReference(p, sb.toString(), false, false, renConst)) {
                                int ref = Integer.parseInt(sb.substring(p + 1, p + 5));
                                if (ref <= (i + 1) && ref > 0) {
                                    String newRef = String.format("%04d", ref * step);
                                    sb.replace(p + 1, p + 5, newRef);
                                }
                            }
                        }
                    }
                    line = sb.toString();
                }

                StringBuffer out = new StringBuffer();
                out.append(String.format("%04d", lineNo));
                out.append(' ');
                out.append(line);
                if (openSystemsServer) {
                    out.append(' ');
                }
                result[i] = out;
            }
        } else {
            // 3.3.2 Label mode
            // First pass: build label mapping (label definition -> line number string)
            Map<String, String> labelMap = new HashMap<>();
            int[] startIndexAfterLabel = new int[source.length];

            for (int i = 0; i < source.length; i++) {
                int lineNo = (i + 1) * step;
                String line = source[i] != null ? source[i] : "";
                startIndexAfterLabel[i] = 0;

                if (labelPrefix != null && line.startsWith(labelPrefix)) {
                    int dotIndex = line.indexOf('.', labelPrefix.length());
                    if (dotIndex >= 0) {
                        String key = line.substring(0, dotIndex + 1);
                        String numPart = line.substring(labelPrefix.length(), dotIndex);
                        try {
                            Integer.parseInt(numPart);
                            labelMap.put(key, String.format("%04d", lineNo));
                            startIndexAfterLabel[i] = dotIndex + 1;
                            if (startIndexAfterLabel[i] < line.length() && line.charAt(startIndexAfterLabel[i]) == ' ') {
                                startIndexAfterLabel[i]++;
                            }
                        } catch (NumberFormatException e) {
                            // not a label definition
                        }
                    }
                }
            }

            // Second pass: build output with label reference replacement
            for (int i = 0; i < source.length; i++) {
                int lineNo = (i + 1) * step;
                String line = source[i] != null ? source[i] : "";

                // Replace label references in the line content
                // Label references look like: (<labelPrefix><num>.) followed by ) / ,
                if (labelPrefix != null && !labelPrefix.isEmpty()) {
                    StringBuilder sb = new StringBuilder(line);
                    for (int p = 0; p < sb.length(); p++) {
                        if (sb.charAt(p) == '(' && p + 1 < sb.length()) {
                            String remaining = sb.substring(p + 1);
                            if (remaining.startsWith(labelPrefix)) {
                                // Find end pattern: ".)" or "./" or ".,"
                                int dotPos = remaining.indexOf('.');
                                if (dotPos >= 0 && dotPos + 1 < remaining.length()) {
                                    char afterDot = remaining.charAt(dotPos + 1);
                                    if (afterDot == ')' || afterDot == '/' || afterDot == ',') {
                                        String refKey = remaining.substring(0, dotPos + 1);
                                        if (labelMap.containsKey(refKey)) {
                                            String replacement = labelMap.get(refKey);
                                            sb.replace(p + 1, p + 1 + refKey.length(), replacement);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    line = sb.toString();
                }

                StringBuffer out = new StringBuffer();
                out.append(String.format("%04d", lineNo));
                out.append(' ');

                int sIdx = startIndexAfterLabel[i];
                if (sIdx < line.length()) {
                    out.append(line.substring(sIdx));
                    if (openSystemsServer) {
                        out.append(' ');
                    }
                }

                result[i] = out;
            }
        }

        return result;
    }

    // ── 4) removeLineNumbers ────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    public static String[] removeLineNumbers(List sourceWithLineNumbers,
                                             boolean updateRefs, boolean renConst,
                                             int prefixLength, int step,
                                             IInsertLabels insertLabels) {
        if (sourceWithLineNumbers == null || sourceWithLineNumbers.isEmpty()) {
            return new String[0];
        }

        List<StringBuffer> lines = new ArrayList<>();
        for (Object o : sourceWithLineNumbers) {
            lines.add(new StringBuffer(o.toString()));
        }

        boolean labelModeActive = insertLabels != null && insertLabels.isInsertLabels();
        String labelFormat = labelModeActive ? insertLabels.getLabelFormat() : null;
        boolean createNewLine = labelModeActive && insertLabels.isCreateNewLine();

        // Mapping: lineNoString (4 chars) -> label
        Map<String, String> labelMapping = new HashMap<>();
        int labelCounter = 1;
        int numberOfAlreadyCreatedLabels = 0;

        // 4.3 Rewrite references if updateRefs=true
        if (updateRefs) {
            for (int i = 0; i < lines.size(); i++) {
                StringBuffer lineBuf = lines.get(i);
                String line = lineBuf.toString();

                for (int p = 0; p < line.length(); p++) {
                    if (line.charAt(p) == '(') {
                        boolean refIsValidNormal = isLineNumberReference(p, line, false, true, renConst);
                        if (!refIsValidNormal) {
                            continue;
                        }
                        if (p + 5 > line.length()) {
                            continue;
                        }

                        String currentLineNoStr = line.length() >= 4 ? line.substring(0, 4) : "0000";
                        int currentLineNo;
                        try {
                            currentLineNo = Integer.parseInt(currentLineNoStr);
                        } catch (NumberFormatException e) {
                            continue;
                        }

                        int refLineNo;
                        try {
                            refLineNo = Integer.parseInt(line.substring(p + 1, p + 5));
                        } catch (NumberFormatException e) {
                            continue;
                        }

                        if (refLineNo <= 0 || refLineNo > currentLineNo) {
                            continue;
                        }

                        // Find the target line by searching backwards for a line whose
                        // first 4 chars match refLineNo
                        String refLineNoStr = String.format("%04d", refLineNo);
                        int targetIndex = -1;
                        for (int j = i; j >= 0; j--) {
                            String candidate = lines.get(j).toString();
                            if (candidate.length() >= 4 && candidate.substring(0, 4).equals(refLineNoStr)) {
                                targetIndex = j;
                                break;
                            }
                        }

                        if (targetIndex < 0) {
                            continue;
                        }

                        int logicalTarget = targetIndex + 1; // 1-based

                        if (labelModeActive) {
                            boolean labelRefIsAllowed = isLineNumberReference(p, line, true, true, renConst);
                            if (labelRefIsAllowed) {
                                // Determine label for target
                                String label;
                                if (labelMapping.containsKey(refLineNoStr)) {
                                    label = labelMapping.get(refLineNoStr);
                                } else {
                                    // Check if target line already has an existing label
                                    String targetLine = lines.get(targetIndex).toString();
                                    String targetContent = targetLine.length() > 4 ? targetLine.substring(4).trim() : "";
                                    String existingLabel = getExistingLabel(targetContent);
                                    if (existingLabel != null) {
                                        label = existingLabel;
                                    } else {
                                        // Generate new label
                                        label = String.format(labelFormat, labelCounter++);
                                        while (searchStringInSource(lines, label)) {
                                            label = String.format(labelFormat, labelCounter++);
                                        }
                                    }
                                    labelMapping.put(refLineNoStr, label);
                                }

                                // Replace the 4 digits of the reference with the label
                                StringBuilder sb = new StringBuilder(line);
                                sb.replace(p + 1, p + 5, label);
                                line = sb.toString();
                                lineBuf.replace(0, lineBuf.length(), line);
                            } else {
                                // Numeric replacement with offset
                                int offset = createNewLine ? numberOfAlreadyCreatedLabels : 0;
                                String newRef = String.format("%04d", logicalTarget + offset);
                                StringBuilder sb = new StringBuilder(line);
                                sb.replace(p + 1, p + 5, newRef);
                                line = sb.toString();
                                lineBuf.replace(0, lineBuf.length(), line);
                            }
                        } else {
                            // Numeric replacement
                            int offset = createNewLine ? numberOfAlreadyCreatedLabels : 0;
                            String newRef = String.format("%04d", logicalTarget + offset);
                            StringBuilder sb = new StringBuilder(line);
                            sb.replace(p + 1, p + 5, newRef);
                            line = sb.toString();
                            lineBuf.replace(0, lineBuf.length(), line);
                        }
                    }
                }
            }
        }

        // 4.4 Build result: apply labels and remove prefix
        List<String> result = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            StringBuffer lineBuf = lines.get(i);
            String lineNoStr = lineBuf.length() >= 4 ? lineBuf.substring(0, 4) : "";

            if (labelModeActive && labelMapping.containsKey(lineNoStr)) {
                String label = labelMapping.get(lineNoStr);
                if (createNewLine) {
                    // Insert label as separate line
                    result.add(label);
                    // Remove prefix from target line
                    if (lineBuf.length() > 4) {
                        lineBuf.delete(0, prefixLength);
                    } else {
                        lineBuf.delete(0, 4);
                    }
                    result.add(lineBuf.toString());
                } else {
                    // Replace first 4 chars with label, keep the space
                    lineBuf.replace(0, 4, label);
                    result.add(lineBuf.toString());
                }
            } else {
                // Normal prefix removal
                if (lineBuf.length() > 4) {
                    lineBuf.delete(0, prefixLength);
                } else if (lineBuf.length() == 4) {
                    lineBuf.delete(0, 4);
                }
                result.add(lineBuf.toString());
            }
        }

        return result.toArray(new String[0]);
    }

    // ── 7) updateLineReferences ─────────────────────────────────────────
    public static String[] updateLineReferences(String[] source, int delta, boolean renConst) {
        if (source == null) {
            return null;
        }

        String[] result = new String[source.length];
        for (int i = 0; i < source.length; i++) {
            String line = source[i] != null ? source[i] : "";
            StringBuilder sb = new StringBuilder(line);
            for (int p = 0; p < sb.length(); p++) {
                if (sb.charAt(p) == '(') {
                    if (isLineNumberReference(p, sb.toString(), false, false, renConst)) {
                        int ref = Integer.parseInt(sb.substring(p + 1, p + 5)) + delta;
                        if (ref > 0 && ref <= (i + 1)) {
                            String newRef = String.format("%04d", ref);
                            sb.replace(p + 1, p + 5, newRef);
                        }
                    }
                }
            }
            result[i] = sb.toString();
        }
        return result;
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private static String getExistingLabel(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher m = EXISTING_LABEL_PATTERN.matcher(text);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    private static boolean searchStringInSource(List list, String searchStr) {
        if (list == null || searchStr == null) {
            return false;
        }
        for (Object obj : list) {
            if (obj != null && obj.toString().contains(searchStr)) {
                return true;
            }
        }
        return false;
    }
}