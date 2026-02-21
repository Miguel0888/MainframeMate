package de.bund.zrb.ndv.renumber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Werkzeugklasse zur Verwaltung von Zeilennummern in Natural-Quelltextzeilen.
 * Alle Methoden sind statisch; keine Instanziierung moeglich.
 */
public class RenumberSource {

    /** Hoechste darstellbare 4-stellige Zeilennummer. */
    private static final int MAX_LINE_NUMBER = 9999;

    /** Zeichenlaenge einer vollstaendigen Zeilenreferenz, z.B. {@code (0010)}. */
    private static final int LINE_REFERENCE_LENGTH = 6;

    /**
     * Syntaktisches Muster fuer eine Zeilenreferenz:
     * oeffnende Klammer + genau 4 ASCII-Ziffern + eines von ) / ,
     */
    private static final Pattern LINE_REFERENCE_PATTERN =
            Pattern.compile("\\([0-9]{4}[)/,]");

    /**
     * Muster fuer ein bestehendes Label am Zeilenanfang:
     * beliebig viele Buchstaben, dann beliebig viele Ziffern, dann Punkt.
     */
    private static final Pattern EXISTING_LABEL_PATTERN =
            Pattern.compile("^[a-zA-Z]*[0-9]*\\.");

    private RenumberSource() {
        // nicht instanziierbar
    }

    // --- isLineReference --- syntaktische Pruefung ---

    public static boolean isLineReference(int pos, String line) {
        if (pos + LINE_REFERENCE_LENGTH > line.length()) {
            return false;
        }
        String candidate = line.substring(pos, pos + LINE_REFERENCE_LENGTH);
        return LINE_REFERENCE_PATTERN.matcher(candidate).matches();
    }

    // --- isLineNumberReference --- semantische Pruefung ---

    public static boolean isLineNumberReference(int pos,
                                                String line,
                                                boolean insertLabelsMode,
                                                boolean hasLineNumberPrefix,
                                                boolean renConst) {
        if (pos == -1) {
            return false;
        }
        if (!isLineReference(pos, line)) {
            return false;
        }

        int commentOffset = hasLineNumberPrefix ? 5 : 0;
        if (line.length() >= commentOffset + 2) {
            String marker = line.substring(commentOffset, commentOffset + 2);
            if ("* ".equals(marker) || "**".equals(marker) || "*/".equals(marker)) {
                return !insertLabelsMode;
            }
        }

        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < pos; i++) {
            char ch = line.charAt(i);
            if (ch == '\'') {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"') {
                inDoubleQuote = !inDoubleQuote;
            } else if (ch == '/' && !inSingleQuote && !inDoubleQuote
                    && i + 1 < pos && line.charAt(i + 1) == '*') {
                return !insertLabelsMode;
            }
        }

        if (inSingleQuote || inDoubleQuote) {
            return renConst;
        }

        return true;
    }

    // --- addLineNumbers ---

    public static StringBuffer[] addLineNumbers(String[] source,
                                                int step,
                                                String labelPrefix,
                                                boolean updateRefs,
                                                boolean openSystemsServer,
                                                boolean renConst) {
        if (source.length == 0) {
            return new StringBuffer[0];
        }

        if (step == 0) {
            step = 1;
        }
        if ((long) step * source.length > MAX_LINE_NUMBER) {
            step = 10;
            while (step > 1 && MAX_LINE_NUMBER / source.length < step) {
                step = step / 2;
            }
        }

        final int dotSearchStart = labelPrefix.length() > 1
                ? labelPrefix.length() + 1
                : labelPrefix.length();

        boolean labelMode = false;
        for (String line : source) {
            if (line.indexOf(labelPrefix) == 0) {
                int dotPos = line.indexOf('.', dotSearchStart);
                if (dotPos != -1) {
                    String numberPart = line.substring(dotSearchStart, dotPos);
                    try {
                        Integer.parseInt(numberPart);
                        labelMode = true;
                        break;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        StringBuffer[] result = new StringBuffer[source.length];

        if (!labelMode) {
            for (int i = 0; i < source.length; i++) {
                int lineNumber = (i + 1) * step;
                StringBuffer sb = new StringBuffer(String.format("%04d", lineNumber))
                        .append(' ').append(source[i]);
                if (updateRefs) {
                    rewriteForwardRefs(sb, i, step, renConst);
                }
                if (openSystemsServer) {
                    sb.append(' ');
                }
                result[i] = sb;
            }
        } else {
            Map<String, String> labelMap = new HashMap<>();

            for (int i = 0; i < source.length; i++) {
                int lineNumber = (i + 1) * step;
                String lineNumber4 = String.format("%04d", lineNumber);
                String line = source[i];
                StringBuffer sb = new StringBuffer(lineNumber4);
                int contentStart = 0;
                boolean labelDefinitionFound = false;

                int searchFrom = 0;
                while (true) {
                    int prefixPos = line.indexOf(labelPrefix, searchFrom);
                    if (prefixPos == -1 || prefixPos >= line.length()) {
                        break;
                    }

                    if (prefixPos == 0) {
                        int dotPos = line.indexOf('.', labelPrefix.length());
                        if (dotPos != -1) {
                            String labelKey = line.substring(0, dotPos + 1);
                            if (!labelMap.containsKey(labelKey)) {
                                labelMap.put(labelKey, lineNumber4);
                                labelDefinitionFound = true;
                                contentStart = dotPos + 1;
                                if (line.charAt(contentStart) == ' ') {
                                    contentStart++;
                                }
                            }
                        }
                        searchFrom = prefixPos + Math.max(labelPrefix.length(), 1);

                    } else if (line.charAt(prefixPos - 1) == '(') {
                        String refSubstr = line.substring(prefixPos);
                        int endPos = findLabelRefEnd(refSubstr);

                        if (endPos != -1) {
                            String labelKey = refSubstr.substring(0, endPos + 1);
                            if (labelMap.containsKey(labelKey)) {
                                String replacement = labelMap.get(labelKey);
                                char terminator = refSubstr.charAt(endPos + 1);
                                int replaceStart = prefixPos - 1;
                                int replaceEnd   = prefixPos + endPos + 2;
                                String newRef = "(" + replacement + terminator;
                                line = line.substring(0, replaceStart)
                                        + newRef + line.substring(replaceEnd);
                                searchFrom = replaceStart + newRef.length();
                                continue;
                            }
                        }
                        searchFrom = prefixPos + Math.max(labelPrefix.length(), 1);

                    } else {
                        searchFrom = prefixPos + Math.max(labelPrefix.length(), 1);
                    }
                }

                sb.append(' ');
                if (labelDefinitionFound) {
                    if (contentStart < line.length()) {
                        sb.append(line.substring(contentStart));
                        if (openSystemsServer) {
                            sb.append(' ');
                        }
                    }
                } else {
                    sb.append(line);
                    if (openSystemsServer) {
                        sb.append(' ');
                    }
                }
                result[i] = sb;
            }
        }

        return result;
    }

    private static int findLabelRefEnd(String refSubstr) {
        int dotParen = refSubstr.indexOf(".)");
        int dotSlash = refSubstr.indexOf("./");
        int dotComma = refSubstr.indexOf(".,");
        if (dotParen != -1) return dotParen;
        if (dotSlash != -1) return dotSlash;
        return dotComma;
    }

    private static void rewriteForwardRefs(StringBuffer sb,
                                           int lineIndex,
                                           int step,
                                           boolean renConst) {
        int searchFrom = 0;
        while (true) {
            int parenPos = sb.indexOf("(", searchFrom);
            if (parenPos == -1) break;
            String sbStr = sb.toString();
            if (isLineNumberReference(parenPos, sbStr, false, false, renConst)) {
                int ref = Integer.parseInt(sbStr.substring(parenPos + 1, parenPos + 5));
                if (ref <= lineIndex + 1) {
                    sb.replace(parenPos + 1, parenPos + 5, String.format("%04d", ref * step));
                }
            }
            searchFrom = parenPos + 1;
        }
    }

    // --- removeLineNumbers ---

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String[] removeLineNumbers(List source,
                                             boolean updateRefs,
                                             boolean renConst,
                                             int prefixLength,
                                             int step,
                                             IInsertLabels insertLabels) {
        List<StringBuffer> sourceList = (List<StringBuffer>) source;

        boolean labelModeActive = insertLabels != null && insertLabels.isInsertLabels();

        Map<String, String> labelTable = new HashMap<>();
        int[] labelCounter = {1};

        if (updateRefs) {
            for (int k = 0; k < sourceList.size(); k++) {
                StringBuffer lineBuf = sourceList.get(k);
                String lineStr = lineBuf.toString();
                int searchFrom = 0;

                while (true) {
                    int parenPos = lineStr.indexOf('(', searchFrom);
                    if (parenPos == -1) break;

                    boolean refValid = isLineNumberReference(
                            parenPos, lineStr, false, true, renConst);
                    boolean labelAllowed = labelModeActive && refValid
                            && isLineNumberReference(parenPos, lineStr, true, true, renConst);

                    if (refValid) {
                        int currentLineNo = Integer.parseInt(lineStr.substring(0, 4));
                        int refLineNo     = Integer.parseInt(lineStr.substring(parenPos + 1, parenPos + 5));

                        if (refLineNo > 0 && refLineNo <= currentLineNo) {
                            int targetIndex = findTargetLine(sourceList, k, refLineNo);

                            if (targetIndex != -1) {
                                if (labelAllowed) {
                                    String label = resolveLabel(
                                            sourceList, targetIndex, refLineNo,
                                            insertLabels, labelTable, labelCounter);
                                    lineBuf.replace(parenPos + 1, parenPos + 5, label);
                                } else {
                                    int extraLines = (labelModeActive && insertLabels.isCreateNewLine())
                                            ? labelTable.size() : 0;
                                    lineBuf.replace(parenPos + 1, parenPos + 5,
                                            String.format("%04d", targetIndex + 1 + extraLines));
                                }
                                lineStr = lineBuf.toString();
                            }

                        } else if (refLineNo > currentLineNo) {
                            if (refLineNo > 0 && refLineNo % step == 0) {
                                @SuppressWarnings("unused")
                                int unused = refLineNo / step;
                            }
                        }
                    }
                    searchFrom = parenPos + 1;
                }
            }
        }

        List<String> resultList = new ArrayList<>();

        for (int i = 0; i < sourceList.size(); i++) {
            StringBuffer lineBuf = sourceList.get(i);
            int len = lineBuf.length();

            if (len > 4) {
                String labelForLine = null;
                if (labelModeActive && !labelTable.isEmpty()) {
                    labelForLine = labelTable.get(lineBuf.substring(0, 4));
                }

                if (labelForLine != null) {
                    if (insertLabels.isCreateNewLine()) {
                        resultList.add(labelForLine);
                        lineBuf.delete(0, prefixLength);
                        resultList.add(lineBuf.toString());
                    } else {
                        lineBuf.replace(0, 4, labelForLine);
                        resultList.add(lineBuf.toString());
                    }
                } else {
                    lineBuf.delete(0, prefixLength);
                    resultList.add(lineBuf.toString());
                }
            } else if (len == 4) {
                lineBuf.delete(0, 4);
                resultList.add("");
            } else {
                resultList.add(lineBuf.toString());
            }
        }

        return resultList.toArray(new String[0]);
    }

    private static int findTargetLine(List<StringBuffer> sourceList,
                                      int fromIndex,
                                      int targetLineNo) {
        for (int t = fromIndex; t >= 0; t--) {
            String line = sourceList.get(t).toString();
            if (line.length() >= 4 && Integer.parseInt(line.substring(0, 4)) == targetLineNo) {
                return t;
            }
        }
        return -1;
    }

    private static String resolveLabel(List<StringBuffer> sourceList,
                                       int targetIndex,
                                       int refLineNo,
                                       IInsertLabels insertLabels,
                                       Map<String, String> labelTable,
                                       int[] labelCounter) {
        String targetLine = sourceList.get(targetIndex).toString();
        String contentAfterPrefix = targetLine.length() > 4
                ? targetLine.substring(5).trim() : "";
        String existingLabel = getExistingLabel(contentAfterPrefix);
        if (existingLabel != null) {
            return existingLabel;
        }

        String refKey = String.format("%04d", refLineNo);
        if (labelTable.containsKey(refKey)) {
            return labelTable.get(refKey);
        }

        String label;
        do {
            label = String.format(insertLabels.getLabelFormat(), labelCounter[0]++);
        } while (searchStringInSource(sourceList, label));

        labelTable.put(refKey, label);
        return label;
    }

    // --- updateLineReferences ---

    public static String[] updateLineReferences(String[] source, int delta, boolean renConst) {
        for (int i = 0; i < source.length; i++) {
            String line = source[i];
            int searchFrom = 0;
            while (true) {
                int parenPos = line.indexOf('(', searchFrom);
                if (parenPos == -1) break;
                if (isLineNumberReference(parenPos, line, false, false, renConst)) {
                    int ref    = Integer.parseInt(line.substring(parenPos + 1, parenPos + 5));
                    int newRef = ref + delta;
                    if (newRef > 0 && newRef <= i + 1) {
                        line = line.substring(0, parenPos + 1)
                                + String.format("%04d", newRef)
                                + line.substring(parenPos + 5);
                        source[i] = line;
                    }
                }
                searchFrom = parenPos + 1;
            }
        }
        return source;
    }

    // --- getExistingLabel ---

    private static String getExistingLabel(String lineContent) {
        Matcher m = EXISTING_LABEL_PATTERN.matcher(lineContent);
        return m.find() ? m.group() : null;
    }

    // --- searchStringInSource ---

    private static boolean searchStringInSource(List<StringBuffer> sourceList,
                                                String searchString) {
        if (searchString == null) {
            return false;
        }
        for (StringBuffer line : sourceList) {
            if (line != null && line.indexOf(searchString) != -1) {
                return true;
            }
        }
        return false;
    }
}

