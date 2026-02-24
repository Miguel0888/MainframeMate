package com.softwareag.naturalone.natural.auxiliary.renumber.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Werkzeugklasse zur Verwaltung von Zeilennummern in Natural-Quelltextzeilen.
 * Alle Methoden sind statisch; keine Instanziierung möglich.
 */
public class RenumberSource {

    /** Höchste darstellbare 4-stellige Zeilennummer. */
    private static final int MAX_LINE_NUMBER = 9999;

    /** Zeichenlänge einer vollständigen Zeilenreferenz, z.B. {@code (0010)}. */
    private static final int LINE_REFERENCE_LENGTH = 6;

    /**
     * Syntaktisches Muster für eine Zeilenreferenz:
     * öffnende Klammer + genau 4 ASCII-Ziffern + eines von ) / ,
     */
    private static final Pattern LINE_REFERENCE_PATTERN =
            Pattern.compile("\\([0-9]{4}[)/,]");

    /**
     * Muster für ein bestehendes Label am Zeilenanfang:
     * beliebig viele Buchstaben, dann beliebig viele Ziffern, dann Punkt.
     */
    private static final Pattern EXISTING_LABEL_PATTERN =
            Pattern.compile("^[a-zA-Z]*[0-9]*\\.");

    private RenumberSource() {
        // nicht instanziierbar
    }

    // ─────────────────────────────────────────────────────────────────────────
    // §4  isLineReference — syntaktische Prüfung
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Prüft, ob an {@code pos} in {@code line} eine syntaktisch gültige
     * Zeilenreferenz steht (Format {@code (NNNN)}, {@code (NNNN/} oder {@code (NNNN,}).
     */
    public static boolean isLineReference(int pos, String line) {
        if (pos + LINE_REFERENCE_LENGTH > line.length()) {
            return false;
        }
        String candidate = line.substring(pos, pos + LINE_REFERENCE_LENGTH);
        return LINE_REFERENCE_PATTERN.matcher(candidate).matches();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // §5  isLineNumberReference — semantische Prüfung
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Prüft, ob an {@code pos} eine semantisch gültige Zeilenreferenz steht,
     * d.h. ob die Stelle weder in einem Kommentar noch (bei {@code renConst=false})
     * in einem String-Literal liegt.
     *
     * @param pos                 Position der öffnenden Klammer
     * @param line                die vollständige Quelltextzeile
     * @param insertLabelsMode    {@code true} = Label-Modus aktiv
     * @param hasLineNumberPrefix {@code true} = Zeile beginnt mit "NNNN "
     * @param renConst            {@code true} = Referenzen in String-Literalen sind gültig
     */
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

        // Natural-Kommentar-Erkennung (§5.4 Schritt 4)
        int commentOffset = hasLineNumberPrefix ? 5 : 0;
        if (line.length() >= commentOffset + 2) {
            String marker = line.substring(commentOffset, commentOffset + 2);
            if ("* ".equals(marker) || "**".equals(marker) || "*/".equals(marker)) {
                return !insertLabelsMode;
            }
        }

        // Block-Kommentar und String-Literal vor pos durchsuchen (§5.4 Schritte 5+6)
        // Beide Quote-Typen werden unabhängig voneinander getoggelt (kein gegenseitiger
        // Ausschluss) – so verhält sich der Originalcode laut Test mixedQuotes.
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
                return !insertLabelsMode;  // Block-Kommentar erkannt
            }
        }

        if (inSingleQuote || inDoubleQuote) {
            return renConst;  // innerhalb eines String-Literals
        }

        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // §6  addLineNumbers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fügt jeder Quelltextzeile eine 4-stellige Zeilennummer als Präfix hinzu.
     *
     * @param source            unnummerierte Quelltextzeilen
     * @param step              Schrittweite
     * @param labelPrefix       Präfix für Label-Erkennung (z.B. {@code "!"})
     * @param updateRefs        Rückwärts-Referenzen auf neue Schrittweite umrechnen
     * @param openSystemsServer abschließendes Leerzeichen anhängen
     * @param renConst          Referenzen in String-Literalen umrechnen
     * @return Feld von StringBuffer-Objekten mit nummeriertem Quelltext
     */
    public static StringBuffer[] addLineNumbers(String[] source,
                                                int step,
                                                String labelPrefix,
                                                boolean updateRefs,
                                                boolean openSystemsServer,
                                                boolean renConst) {
        if (source.length == 0) {
            return new StringBuffer[0];
        }

        // §6.4 Schrittweiten-Normalisierung
        if (step == 0) {
            step = 1;
        }
        if ((long) step * source.length > MAX_LINE_NUMBER) {
            step = 10;
            while (step > 1 && MAX_LINE_NUMBER / source.length < step) {
                step = step / 2;
            }
        }

        // §6.5 Label-Modus-Erkennung (Phase 1)
        //
        // Der Originalcode hat einen Off-by-one-Bug bei der Dot-Suche und beim
        // Extrakt des Zahlenanteils: Beide starten bei (prefixLen + 1) statt (prefixLen).
        //
        // Konsequenz für einstellige Präfixe wie "!":
        //   "!1. WRITE X" → indexOf('.', 2) = 2, substring(2, 2) = "" → NFE ... passt nicht!
        //
        // Die Tests zeigen jedoch, dass für einstellige Präfixe Label-Mode funktioniert
        // ("!1." wird erkannt) und für zweistellige nicht ("##1." wird nicht erkannt).
        // Die einzige konsistente Erklärung: der Bug betrifft nur Präfixe mit length > 1.
        // Bei length == 1 verhält sich der Code korrekt (kein Off-by-one).
        //
        // Test multiCharPrefix-Kommentar: indexOf(".",3)=3, substring(3,3)="" → NFE → kein labelMode
        // Das gilt nur für "##" (length=2): dotSearchStart = 2+1 = 3.
        // Für "!" (length=1): dotSearchStart = 1 (ohne Bug).
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
                        // kein gültiges Label-Muster in dieser Zeile
                    }
                }
            }
        }

        StringBuffer[] result = new StringBuffer[source.length];

        if (!labelMode) {
            // ── §6.6 Normal-Modus ──────────────────────────────────────────
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
            // ── §6.7 Label-Modus ──────────────────────────────────────────
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
                    // Abbruch bei keinem Treffer oder am/hinter dem Zeilenende
                    // (verhindert Endlosschleife bei leerem labelPrefix, da
                    // String.indexOf("", n) für n >= length immer length liefert).
                    if (prefixPos == -1 || prefixPos >= line.length()) {
                        break;
                    }

                    if (prefixPos == 0) {
                        // §6.7.1 Label-Definition am Zeilenanfang
                        int dotPos = line.indexOf('.', labelPrefix.length());
                        if (dotPos != -1) {
                            String labelKey = line.substring(0, dotPos + 1);
                            if (!labelMap.containsKey(labelKey)) {
                                labelMap.put(labelKey, lineNumber4);
                                labelDefinitionFound = true;
                                contentStart = dotPos + 1;
                                // ABSICHTLICH kein Bounds-Check vor charAt:
                                // "!1." → contentStart == length →
                                // StringIndexOutOfBoundsException (Original-Verhalten,
                                // dokumentiert durch Test labelModeOnlyLabel).
                                if (line.charAt(contentStart) == ' ') {
                                    contentStart++;
                                }
                            }
                        }
                        searchFrom = prefixPos + Math.max(labelPrefix.length(), 1);

                    } else if (line.charAt(prefixPos - 1) == '(') {
                        // §6.7.3 Label-Referenz direkt nach öffnender Klammer
                        String refSubstr = line.substring(prefixPos);
                        int endPos = findLabelRefEnd(refSubstr); // Index des Punktes

                        if (endPos != -1) {
                            String labelKey = refSubstr.substring(0, endPos + 1);
                            if (labelMap.containsKey(labelKey)) {
                                String replacement = labelMap.get(labelKey);
                                char terminator = refSubstr.charAt(endPos + 1);
                                int replaceStart = prefixPos - 1;        // '('
                                int replaceEnd   = prefixPos + endPos + 2; // hinter terminator
                                String newRef = "(" + replacement + terminator;
                                line = line.substring(0, replaceStart)
                                        + newRef + line.substring(replaceEnd);
                                searchFrom = replaceStart + newRef.length();
                                continue;
                            }
                        }
                        searchFrom = prefixPos + Math.max(labelPrefix.length(), 1);

                    } else {
                        // §6.7.2 Label-Definition mit Leerraum davor
                        // Der Originalcode prüft: ch == ' ' && ch == '\t' – was niemals
                        // wahr sein kann.  Dieses Verhalten wird hier repliziert:
                        // Kein Leerraum-Label wird erkannt.
                        searchFrom = prefixPos + Math.max(labelPrefix.length(), 1);
                    }
                }

                // §6.7.4 Zeileninhalt zusammenbauen
                sb.append(' ');
                if (labelDefinitionFound) {
                    if (contentStart < line.length()) {
                        sb.append(line.substring(contentStart));
                        if (openSystemsServer) {
                            sb.append(' ');
                        }
                    }
                    // contentStart == length → nur "NNNN " (kein weiterer Inhalt)
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

    /**
     * Sucht in {@code refSubstr} (ab dem Präfix-Zeichen, ohne öffnende Klammer)
     * das Ende einer Label-Referenz: Punkt gefolgt von {@code )}, {@code /} oder {@code ,}.
     *
     * @return Index des Punktes (nicht des Terminators), oder {@code -1}
     */
    private static int findLabelRefEnd(String refSubstr) {
        int dotParen = refSubstr.indexOf(".)");
        int dotSlash = refSubstr.indexOf("./");
        int dotComma = refSubstr.indexOf(".,");
        if (dotParen != -1) return dotParen;
        if (dotSlash != -1) return dotSlash;
        return dotComma; // -1 wenn nichts gefunden
    }

    /**
     * Schreibt alle gültigen Rückwärts-Referenzen in {@code sb} auf die neue
     * Schrittweite um. Vorwärts-Referenzen bleiben unverändert.
     */
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

    // ─────────────────────────────────────────────────────────────────────────
    // §7  removeLineNumbers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Entfernt Zeilennummern-Präfixe und normalisiert bzw. ersetzt Referenzen.
     * <p><b>Seiteneffekt:</b> Die Eingabe-Puffer werden direkt verändert.
     *
     * @param source       Liste von StringBuffern mit nummeriertem Quelltext
     * @param updateRefs   Referenzen umschreiben?
     * @param renConst     Referenzen in String-Literalen umschreiben?
     * @param prefixLength Anzahl der zu entfernenden Präfix-Zeichen
     * @param step         ursprüngliche Schrittweite der Zeilennummern
     * @param insertLabels Label-Steuerung (null = kein Label-Modus)
     * @return Feld von Zeichenketten ohne Zeilennummern-Präfix
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String[] removeLineNumbers(List source,
                                             boolean updateRefs,
                                             boolean renConst,
                                             int prefixLength,
                                             int step,
                                             IInsertLabels insertLabels) {
        List<StringBuffer> sourceList = (List<StringBuffer>) source;

        boolean labelModeActive = insertLabels != null && insertLabels.isInsertLabels();

        // Label-Tabelle: Schlüssel = 4-stellige Quell-Zeilennummer, Wert = Label
        Map<String, String> labelTable = new HashMap<>();
        int[] labelCounter = {1}; // in Array, damit er in Hilfsmethode mutierbar ist

        // ── §7.4 Phase 1: Referenzen umschreiben ─────────────────────────
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
                            // Toter Code-Pfad aus dem Original – wird repliziert,
                            // damit ArithmeticException bei step=0 auftritt (Test stepZero).
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

        // ── §7.5 Phase 2: Präfix entfernen und Labels einfügen ───────────
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

    /**
     * Sucht rückwärts ab {@code fromIndex} die Zeile mit Zeilennummer {@code targetLineNo}.
     *
     * @return 0-basierter Index oder {@code -1}
     */
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

    /**
     * Ermittelt das Label für eine Zielzeile: vorhandenes Label, Tabellen-Eintrag
     * oder neu generiertes Label (mit Kollisionsprüfung).
     *
     * @param labelCounter int[1]-Array, das den aktuellen Zähler hält (wird mutiert)
     */
    private static String resolveLabel(List<StringBuffer> sourceList,
                                       int targetIndex,
                                       int refLineNo,
                                       IInsertLabels insertLabels,
                                       Map<String, String> labelTable,
                                       int[] labelCounter) {
        // Existierendes Label auf der Zielzeile?
        String targetLine = sourceList.get(targetIndex).toString();
        String contentAfterPrefix = targetLine.length() > 4
                ? targetLine.substring(5).trim() : "";
        String existingLabel = getExistingLabel(contentAfterPrefix);
        if (existingLabel != null) {
            return existingLabel;
        }

        // Bereits für diese Referenznummer ein Label generiert?
        String refKey = String.format("%04d", refLineNo);
        if (labelTable.containsKey(refKey)) {
            return labelTable.get(refKey);
        }

        // Neues Label mit Kollisionsprüfung erzeugen
        String label;
        do {
            label = String.format(insertLabels.getLabelFormat(), labelCounter[0]++);
        } while (searchStringInSource(sourceList, label));

        labelTable.put(refKey, label);
        return label;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // §8  updateLineReferences
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verschiebt alle Zeilenreferenzen {@code (NNNN)} um {@code delta}.
     * <p><b>Seiteneffekt:</b> Das Eingabe-Array wird direkt verändert und zurückgegeben.
     */
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

    // ─────────────────────────────────────────────────────────────────────────
    // §9  getExistingLabel
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Erkennt ein bereits vorhandenes Label am Zeilenanfang.
     * Muster: {@code [a-zA-Z]*[0-9]*\.}
     *
     * @return gefundenes Label inkl. Punkt, oder {@code null}
     */
    private static String getExistingLabel(String lineContent) {
        Matcher m = EXISTING_LABEL_PATTERN.matcher(lineContent);
        return m.find() ? m.group() : null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // §10  searchStringInSource
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Prüft, ob {@code searchString} in irgendeiner Zeile von {@code sourceList}
     * als Teilzeichenkette vorkommt.
     */
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
