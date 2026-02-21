package com.softwareag.naturalone.natural.auxiliary.renumber.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RenumberSource {

    private static final int MAX_LINE_NUMBER = 9999;
    private static final int LINE_REFERENCE_LENGTH = 6;

    private static final Pattern LINE_REFERENCE_PATTERN = Pattern.compile("\\([0-9]{4}[)/,]");
    private static final Pattern EXISTING_LABEL_PATTERN = Pattern.compile("^[a-zA-Z]*[0-9]*\\.");

    private RenumberSource() {
    }

    private static Pattern getLineReferencePattern() {
        return LINE_REFERENCE_PATTERN;
    }

    // §4: isLineReference — syntactic check
    public static boolean isLineReference(int pos, String line) {
        if (pos + LINE_REFERENCE_LENGTH > line.length()) {
            return false;
        }
        String sub = line.substring(pos, pos + LINE_REFERENCE_LENGTH);
        return LINE_REFERENCE_PATTERN.matcher(sub).matches();
    }

    // §5: isLineNumberReference — semantic check
    public static boolean isLineNumberReference(int pos, String line, boolean insertLabelsMode, boolean hasLineNumberPrefix, boolean renConst) {
        // Step 1: pos == -1 → false
        if (pos == -1) {
            return false;
        }

        // Step 2: syntactic check
        if (!isLineReference(pos, line)) {
            return false;
        }

        // Step 3: compute comment offset
        int commentOffset = hasLineNumberPrefix ? 5 : 0;

        // Step 4: Natural comment detection
        if (line.length() >= commentOffset + 2) {
            String twoChars = line.substring(commentOffset, commentOffset + 2);
            if (twoChars.equals("* ") || twoChars.equals("**") || twoChars.equals("*/")) {
                return !insertLabelsMode;
            }
        }

        // Step 5: Block comment /* detection before pos
        // Step 6: String literal detection before pos
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < pos; i++) {
            char ch = line.charAt(i);

            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (ch == '/' && !inSingleQuote && !inDoubleQuote) {
                if (i + 1 < pos && line.charAt(i + 1) == '*') {
                    return !insertLabelsMode;
                }
            }
        }

        // Check if inside a string literal
        if (inSingleQuote || inDoubleQuote) {
            return renConst;
        }

        // Step 7: valid reference
        return true;
    }

    // §6: addLineNumbers
    public static StringBuffer[] addLineNumbers(String[] source, int step, String labelPrefix, boolean updateRefs, boolean openSystemsServer, boolean renConst) {
        if (source.length == 0) {
            return new StringBuffer[0];
        }

        // Step normalization
        if (step == 0) {
            step = 1;
        }

        if (step * source.length > MAX_LINE_NUMBER) {
            step = 10;
            while (step > 1 && MAX_LINE_NUMBER / source.length < step) {
                step = step / 2;
            }
        }

        // Label mode detection (Phase 1)
        boolean labelMode = false;
        for (String line : source) {
            int prefixPos = line.indexOf(labelPrefix);
            if (prefixPos == 0) {
                int dotPos = line.indexOf('.', labelPrefix.length());
                if (dotPos != -1) {
                    String numberPart = line.substring(labelPrefix.length(), dotPos);
                    try {
                        Integer.parseInt(numberPart);
                        labelMode = true;
                        break;
                    } catch (NumberFormatException e) {
                        // not a valid label, continue
                    }
                }
            }
        }

        StringBuffer[] result = new StringBuffer[source.length];

        if (!labelMode) {
            // Normal mode (Phase 2a)
            for (int i = 0; i < source.length; i++) {
                int lineNumber = (i + 1) * step;
                String formattedNumber = String.format("%04d", lineNumber);
                StringBuffer sb = new StringBuffer(formattedNumber + " " + source[i]);

                if (updateRefs) {
                    // Find and update references
                    int searchFrom = 0;
                    while (true) {
                        int parenPos = sb.indexOf("(", searchFrom);
                        if (parenPos == -1) {
                            break;
                        }
                        String sbStr = sb.toString();
                        if (isLineNumberReference(parenPos, sbStr, false, false, renConst)) {
                            int refValue = Integer.parseInt(sbStr.substring(parenPos + 1, parenPos + 5));
                            if (refValue <= i + 1) {
                                int newValue = refValue * step;
                                String newRef = String.format("%04d", newValue);
                                sb.replace(parenPos + 1, parenPos + 5, newRef);
                            }
                        }
                        searchFrom = parenPos + 1;
                    }
                }

                if (openSystemsServer) {
                    sb.append(" ");
                }

                result[i] = sb;
            }
        } else {
            // Label mode (Phase 2b)
            Map<String, String> labelMap = new HashMap<>();

            for (int i = 0; i < source.length; i++) {
                int lineNumber = (i + 1) * step;
                String formattedNumber = String.format("%04d", lineNumber);
                String line = source[i];

                StringBuffer sb = new StringBuffer(formattedNumber);

                int contentStart = 0;
                boolean labelDefinitionFound = false;

                // Search for label prefix occurrences
                int searchFrom = 0;
                while (true) {
                    int prefixPos = line.indexOf(labelPrefix, searchFrom);
                    if (prefixPos == -1 || prefixPos >= line.length()) {
                        break;
                    }

                    if (prefixPos == 0) {
                        // Label definition at position 0 (§6.7.1)
                        int dotPos = line.indexOf('.', labelPrefix.length());
                        if (dotPos != -1 && !labelMap.containsKey(line.substring(0, dotPos + 1))) {
                            String labelKey = line.substring(0, dotPos + 1);
                            labelMap.put(labelKey, formattedNumber);
                            labelDefinitionFound = true;
                            contentStart = dotPos + 1;
                            // Original code does charAt(contentStart) without bounds check,
                            // which throws StringIndexOutOfBoundsException when contentStart == line.length()
                            if (line.charAt(contentStart) == ' ') {
                                contentStart++;
                            }
                        }
                        searchFrom = prefixPos + Math.max(labelPrefix.length(), 1);
                    } else if (prefixPos > 0 && line.charAt(prefixPos - 1) == '(') {
                        // Label reference after '(' (§6.7.3)
                        String refSubstr = line.substring(prefixPos);
                        int dotParenPos = refSubstr.indexOf(".)");
                        int dotSlashPos = refSubstr.indexOf("./");
                        int dotCommaPos = refSubstr.indexOf(".,");

                        int endPos = -1;
                        if (dotParenPos != -1) {
                            endPos = dotParenPos;
                        } else if (dotSlashPos != -1) {
                            endPos = dotSlashPos;
                        } else if (dotCommaPos != -1) {
                            endPos = dotCommaPos;
                        }

                        if (endPos != -1) {
                            String labelKey = refSubstr.substring(0, endPos + 1);
                            if (labelMap.containsKey(labelKey)) {
                                String replacement = labelMap.get(labelKey);
                                char endChar = refSubstr.charAt(endPos + 1);
                                // Replace the label reference: (labelKey + endChar) -> (replacement + endChar)
                                int replaceStart = prefixPos - 1; // the '('
                                int replaceEnd = prefixPos + endPos + 2; // after the end char
                                String newRef = "(" + replacement + endChar;
                                line = line.substring(0, replaceStart) + newRef + line.substring(replaceEnd);
                                searchFrom = replaceStart + newRef.length();
                                continue;
                            }
                        }
                        searchFrom = prefixPos + Math.max(labelPrefix.length(), 1);
                    } else {
                        // §6.7.2: Check if only whitespace before prefix
                        boolean onlyWhitespace = true;
                        for (int k = prefixPos - 1; k >= 0 && onlyWhitespace; k--) {
                            char ch = line.charAt(k);
                            if (ch != ' ' && ch != '\t') {
                                onlyWhitespace = false;
                            }
                        }
                        // Due to the && bug in the original: the whitespace backtrack loop
                        // uses (charAt == ' ' && charAt == '\t') which is always false,
                        // so it never actually identifies whitespace-only prefixes.
                        // We replicate this: var28 stays at prefixPos-1, and since
                        // prefixPos-1 >= 0 (because prefixPos > 0), var28 != -1, so
                        // no label definition is recognized.
                        // So we do NOT set labelDefinitionFound here.
                        searchFrom = prefixPos + Math.max(labelPrefix.length(), 1);
                    }
                }

                // Build result line
                sb.append(" ");
                if (labelDefinitionFound) {
                    if (contentStart < line.length()) {
                        sb.append(line.substring(contentStart));
                        if (openSystemsServer) {
                            sb.append(" ");
                        }
                    }
                } else {
                    sb.append(line);
                    if (openSystemsServer) {
                        sb.append(" ");
                    }
                }

                result[i] = sb;
            }
        }

        return result;
    }

    // §7: removeLineNumbers
    @SuppressWarnings("unchecked")
    public static String[] removeLineNumbers(List source, boolean updateRefs, boolean renConst, int prefixLength, int step, IInsertLabels insertLabels) {
        List<StringBuffer> sourceList = (List<StringBuffer>) source;

        boolean labelModeActive = insertLabels != null && insertLabels.isInsertLabels();

        // Label table: maps 4-digit line number string -> label string
        Map<String, String> labelTable = new HashMap<>();
        int labelCounter = 1;

        // Phase 1: Reference rewriting
        if (updateRefs) {
            for (int k = 0; k < sourceList.size(); k++) {
                StringBuffer lineBuf = sourceList.get(k);
                String lineStr = lineBuf.toString();

                int searchFrom = 0;
                while (true) {
                    int parenPos = lineStr.indexOf('(', searchFrom);
                    if (parenPos == -1) {
                        break;
                    }

                    boolean refValid = isLineNumberReference(parenPos, lineStr, false, true, renConst);
                    boolean labelRefAllowed = false;

                    if (labelModeActive && refValid) {
                        labelRefAllowed = isLineNumberReference(parenPos, lineStr, true, true, renConst);
                    }

                    if (refValid) {
                        // Read current line number from first 4 chars
                        int currentLineNo = Integer.parseInt(lineStr.substring(0, 4));
                        // Read referenced number
                        int refLineNo = Integer.parseInt(lineStr.substring(parenPos + 1, parenPos + 5));

                        if (refLineNo > 0 && refLineNo <= currentLineNo) {
                            // Search backward for the target line
                            int targetIndex = -1;
                            for (int t = k; t >= 0; t--) {
                                String targetLine = sourceList.get(t).toString();
                                if (targetLine.length() >= 4) {
                                    int targetLineNo = Integer.parseInt(targetLine.substring(0, 4));
                                    if (targetLineNo == refLineNo) {
                                        targetIndex = t;
                                        break;
                                    }
                                }
                            }

                            if (targetIndex != -1) {
                                if (!labelRefAllowed) {
                                    // Numeric replacement
                                    int labelLinesCount = 0;
                                    if (labelModeActive && insertLabels.isCreateNewLine()) {
                                        labelLinesCount = labelTable.size();
                                    }
                                    int newNumber = targetIndex + 1 + labelLinesCount;
                                    String newRef = String.format("%04d", newNumber);
                                    lineBuf.replace(parenPos + 1, parenPos + 5, newRef);
                                    lineStr = lineBuf.toString();
                                } else {
                                    // Label replacement
                                    String targetContent = sourceList.get(targetIndex).toString();
                                    String contentAfterPrefix = targetContent.length() > 4 ? targetContent.substring(5).trim() : "";

                                    String existingLabel = getExistingLabel(contentAfterPrefix);
                                    String label;

                                    if (existingLabel != null) {
                                        label = existingLabel;
                                    } else {
                                        String refKey = String.format("%04d", refLineNo);
                                        if (labelTable.containsKey(refKey)) {
                                            label = labelTable.get(refKey);
                                        } else {
                                            // Generate new label with collision check
                                            label = String.format(insertLabels.getLabelFormat(), labelCounter++);
                                            while (searchStringInSource(sourceList, label)) {
                                                label = String.format(insertLabels.getLabelFormat(), labelCounter++);
                                            }
                                            labelTable.put(refKey, label);
                                        }
                                    }

                                    lineBuf.replace(parenPos + 1, parenPos + 5, label);
                                    lineStr = lineBuf.toString();
                                }
                            }
                        } else if (refLineNo > currentLineNo) {
                            // Forward reference: dead code path replication
                            if ((refLineNo <= 0 || refLineNo % step == 0) && refLineNo > 0) {
                                @SuppressWarnings("unused")
                                int deadCodeVar = refLineNo / step;
                            }
                        }
                    }

                    searchFrom = parenPos + 1;
                }
            }
        }

        // Phase 2: Remove prefix and insert labels
        List<String> resultList = new ArrayList<>();

        for (int i = 0; i < sourceList.size(); i++) {
            StringBuffer lineBuf = sourceList.get(i);

            if (lineBuf.length() > 4) {
                String labelForLine = null;

                if (labelModeActive && !labelTable.isEmpty()) {
                    String lineNoKey = lineBuf.substring(0, 4);
                    if (labelTable.containsKey(lineNoKey)) {
                        labelForLine = labelTable.get(lineNoKey);
                    }
                }

                if (labelForLine != null) {
                    if (insertLabels.isCreateNewLine()) {
                        // Label as separate line
                        resultList.add(labelForLine);
                        lineBuf.delete(0, prefixLength);
                        resultList.add(lineBuf.toString());
                    } else {
                        // Replace first 4 chars with label
                        lineBuf.replace(0, 4, labelForLine);
                        resultList.add(lineBuf.toString());
                    }
                } else {
                    lineBuf.delete(0, prefixLength);
                    resultList.add(lineBuf.toString());
                }
            } else if (lineBuf.length() == 4) {
                lineBuf.delete(0, 4);
                resultList.add("");
            } else {
                resultList.add(lineBuf.toString());
            }
        }

        return resultList.toArray(new String[0]);
    }

    // §8: updateLineReferences
    public static String[] updateLineReferences(String[] source, int delta, boolean renConst) {
        for (int i = 0; i < source.length; i++) {
            String line = source[i];
            int searchFrom = 0;

            while (true) {
                int parenPos = line.indexOf('(', searchFrom);
                if (parenPos == -1) {
                    break;
                }

                if (isLineNumberReference(parenPos, line, false, false, renConst)) {
                    int refValue = Integer.parseInt(line.substring(parenPos + 1, parenPos + 5));
                    int newValue = refValue + delta;

                    if (newValue > 0 && newValue <= i + 1) {
                        String newRef = String.format("%04d", newValue);
                        line = line.substring(0, parenPos + 1) + newRef + line.substring(parenPos + 5);
                        source[i] = line;
                    }
                }

                searchFrom = parenPos + 1;
            }
        }

        return source;
    }

    // §9: getExistingLabel
    private static String getExistingLabel(String lineContent) {
        Matcher matcher = EXISTING_LABEL_PATTERN.matcher(lineContent);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    // §10: searchStringInSource
    private static boolean searchStringInSource(List source, String searchString) {
        if (searchString == null) {
            return false;
        }
        for (Object line : source) {
            if (line != null && line.toString().contains(searchString)) {
                return true;
            }
        }
        return false;
    }
}

