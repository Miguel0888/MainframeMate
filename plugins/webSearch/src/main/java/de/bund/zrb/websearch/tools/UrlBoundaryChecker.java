package de.bund.zrb.websearch.tools;

import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Prüft URLs gegen konfigurierte Whitelist- und Blacklist-Regex-Patterns.
 *
 * <p>Regeln:</p>
 * <ul>
 *   <li>Wenn eine Whitelist definiert ist (nicht leer), MUSS die URL mindestens einem Pattern matchen.</li>
 *   <li>Wenn eine Blacklist definiert ist, DARF die URL keinem Pattern matchen.</li>
 *   <li>Blacklist hat Vorrang: auch wenn die URL auf der Whitelist steht,
 *       wird sie abgelehnt, wenn ein Blacklist-Pattern matcht.</li>
 * </ul>
 *
 * <p>Patterns werden gegen die vollständige URL geprüft (find, nicht fullMatch),
 * d.h. eine Domain wie {@code yahoo\.com} matcht auch {@code https://news.yahoo.com/path}.</p>
 */
public class UrlBoundaryChecker {

    private static final Logger LOG = Logger.getLogger(UrlBoundaryChecker.class.getName());

    private final List<Pattern> whitelist;
    private final List<Pattern> blacklist;
    private final List<String> whitelistRaw;
    private final List<String> blacklistRaw;

    public UrlBoundaryChecker(List<String> whitelistPatterns, List<String> blacklistPatterns) {
        this.whitelistRaw = whitelistPatterns != null ? whitelistPatterns : Collections.<String>emptyList();
        this.blacklistRaw = blacklistPatterns != null ? blacklistPatterns : Collections.<String>emptyList();
        this.whitelist = compilePatterns(this.whitelistRaw);
        this.blacklist = compilePatterns(this.blacklistRaw);
    }

    /**
     * Prüft, ob die URL erlaubt ist.
     *
     * @return null wenn erlaubt, sonst eine Fehlermeldung mit Hinweis auf erlaubte URLs
     */
    public String check(String url) {
        if (url == null || url.isEmpty()) {
            return null; // Keine URL → kein Check nötig
        }

        // Blacklist hat Vorrang
        for (int i = 0; i < blacklist.size(); i++) {
            if (blacklist.get(i).matcher(url).find()) {
                return buildBlacklistError(url, blacklistRaw.get(i));
            }
        }

        // Whitelist prüfen (nur wenn definiert)
        if (!whitelist.isEmpty()) {
            boolean matched = false;
            for (Pattern p : whitelist) {
                if (p.matcher(url).find()) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return buildWhitelistError(url);
            }
        }

        return null; // URL ist erlaubt
    }

    /**
     * @return true wenn mindestens ein Boundary (Whitelist oder Blacklist) konfiguriert ist.
     */
    public boolean hasBoundaries() {
        return !whitelist.isEmpty() || !blacklist.isEmpty();
    }

    private String buildBlacklistError(String url, String matchedPattern) {
        StringBuilder sb = new StringBuilder();
        sb.append("❌ URL blocked by blacklist: ").append(url).append("\n");
        sb.append("Matched blacklist pattern: ").append(matchedPattern).append("\n\n");
        appendAllowedHint(sb);
        return sb.toString();
    }

    private String buildWhitelistError(String url) {
        StringBuilder sb = new StringBuilder();
        sb.append("❌ URL not in whitelist: ").append(url).append("\n\n");
        appendAllowedHint(sb);
        return sb.toString();
    }

    private void appendAllowedHint(StringBuilder sb) {
        if (!whitelistRaw.isEmpty()) {
            sb.append("Erlaubte URL-Patterns (Whitelist):\n");
            for (String p : whitelistRaw) {
                sb.append("  ✅ ").append(p).append("\n");
            }
        } else {
            sb.append("Keine Whitelist konfiguriert – alle URLs grundsätzlich erlaubt.\n");
        }
        if (!blacklistRaw.isEmpty()) {
            sb.append("Gesperrte URL-Patterns (Blacklist):\n");
            for (String p : blacklistRaw) {
                sb.append("  🚫 ").append(p).append("\n");
            }
        }
        sb.append("\nBitte verwende nur URLs, die den konfigurierten Boundaries entsprechen.");
    }

    private static List<Pattern> compilePatterns(List<String> rawPatterns) {
        List<Pattern> compiled = new ArrayList<>();
        for (String raw : rawPatterns) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;
            try {
                compiled.add(Pattern.compile(trimmed, Pattern.CASE_INSENSITIVE));
            } catch (PatternSyntaxException e) {
                LOG.warning("[UrlBoundaryChecker] Invalid regex pattern '" + trimmed + "': " + e.getMessage());
                // Treat as literal string match
                compiled.add(Pattern.compile(Pattern.quote(trimmed), Pattern.CASE_INSENSITIVE));
            }
        }
        return compiled;
    }

    /**
     * Parst ein mehrzeiliges Textfeld zu einer Liste von Patterns.
     * Jede Zeile ist ein Pattern. Leere Zeilen und Kommentare (#) werden ignoriert.
     */
    public static List<String> parsePatternList(String text) {
        List<String> patterns = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return patterns;
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                patterns.add(trimmed);
            }
        }
        return patterns;
    }

    /**
     * Konvertiert eine Liste von Patterns zu einem mehrzeiligen String.
     */
    public static String patternsToText(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String p : patterns) {
            sb.append(p).append("\n");
        }
        return sb.toString().trim();
    }
}

