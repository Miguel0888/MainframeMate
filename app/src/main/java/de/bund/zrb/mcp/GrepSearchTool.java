package de.bund.zrb.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.auth.CredentialsProvider;
import de.bund.zrb.files.impl.auth.LoginManagerCredentialsProvider;
import de.bund.zrb.files.impl.factory.FileServiceFactory;
import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.files.model.FilePayload;
import de.bund.zrb.login.LoginManager;
import de.bund.zrb.ui.VirtualResource;
import de.bund.zrb.ui.VirtualResourceKind;
import de.bund.zrb.ui.VirtualResourceResolver;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * MCP Tool that searches for text patterns in files, similar to grep/ripgrep.
 * Supports regex, context lines, various output modes (MATCHES, FILES, COUNT),
 * and performance limits.
 *
 * accessType: READ (always)
 */
public class GrepSearchTool implements McpTool {

    private final MainframeContext context;

    // Output modes
    public enum OutputMode {
        MATCHES, // Full matches with line/line number (default)
        FILES,   // Only paths with at least one match (grep -l)
        COUNT    // Only match count per file and total (grep -c)
    }

    public GrepSearchTool(MainframeContext context) {
        this.context = context;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<>();
        properties.put("root", new ToolSpec.Property("string", "Datei oder Verzeichnis (lokal oder FTP)"));
        properties.put("pattern", new ToolSpec.Property("string", "Suchmuster (Text oder Regex)"));
        properties.put("recursive", new ToolSpec.Property("boolean", "Rekursiv in Unterverzeichnissen suchen (default: true)"));
        properties.put("fileNamePattern", new ToolSpec.Property("string", "Dateinamen-Filter, z.B. '*.txt;*.json' (default: '*')"));
        properties.put("regex", new ToolSpec.Property("boolean", "Pattern als Regex interpretieren (default: false)"));
        properties.put("caseSensitive", new ToolSpec.Property("boolean", "Groß-/Kleinschreibung beachten (default: false)"));
        properties.put("wholeWord", new ToolSpec.Property("boolean", "Nur ganze Wörter (default: false, nur bei regex=false)"));
        properties.put("invertMatch", new ToolSpec.Property("boolean", "Zeilen ohne Match ausgeben, wie grep -v (default: false)"));
        properties.put("maxHits", new ToolSpec.Property("integer", "Max. Treffer gesamt (default: 200)"));
        properties.put("maxMatchesPerFile", new ToolSpec.Property("integer", "Max. Treffer pro Datei (default: 50)"));
        properties.put("maxFileSizeBytes", new ToolSpec.Property("integer", "Max. Dateigröße in Bytes (default: 5000000)"));
        properties.put("timeoutMs", new ToolSpec.Property("integer", "Zeitlimit in ms (default: 5000)"));
        properties.put("encoding", new ToolSpec.Property("string", "Zeichenkodierung (optional)"));
        properties.put("includeBinary", new ToolSpec.Property("boolean", "Binärdateien durchsuchen (default: false)"));
        properties.put("includeContext", new ToolSpec.Property("boolean", "Kontextzeilen anzeigen (default: false)"));
        properties.put("contextLines", new ToolSpec.Property("integer", "Anzahl Kontextzeilen (default: 2)"));
        properties.put("outputMode", new ToolSpec.Property("string", "Ausgabemodus: MATCHES (default), FILES (nur Pfade), COUNT (nur Anzahl)"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(properties, Arrays.asList("root", "pattern"));

        Map<String, Object> example = new LinkedHashMap<>();
        example.put("root", "C:\\TEST");
        example.put("pattern", "Hamburg Hafen");
        example.put("recursive", true);
        example.put("includeContext", true);
        example.put("contextLines", 2);

        return new ToolSpec(
                "grep_search",
                "Durchsucht Dateien nach Text oder Regex-Muster (wie grep/ripgrep). " +
                "Gibt Datei, Zeilennummer und Trefferzeilen zurück. " +
                "outputMode=MATCHES: alle Treffer mit Details. " +
                "outputMode=FILES: nur Pfade mit Treffern (wie grep -l). " +
                "outputMode=COUNT: nur Trefferanzahl (wie grep -c). " +
                "Für FTP-Pfade muss vorher eine Verbindung bestehen.",
                inputSchema,
                example
        );
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        JsonObject response = new JsonObject();
        response.addProperty("toolName", "grep_search");

        try {
            // Validate required fields
            if (input == null || !input.has("root") || input.get("root").isJsonNull()) {
                return errorResponse("Pflichtfeld fehlt: root", "ToolExecutionError", resultVar);
            }
            if (!input.has("pattern") || input.get("pattern").isJsonNull()) {
                return errorResponse("Pflichtfeld fehlt: pattern", "ToolExecutionError", resultVar);
            }

            // Parse input parameters
            String root = input.get("root").getAsString();
            String pattern = input.get("pattern").getAsString();
            boolean recursive = getOptionalBoolean(input, "recursive", true);
            String fileNamePattern = getOptionalString(input, "fileNamePattern", "*");
            boolean regex = getOptionalBoolean(input, "regex", false);
            boolean caseSensitive = getOptionalBoolean(input, "caseSensitive", false);
            boolean wholeWord = getOptionalBoolean(input, "wholeWord", false);
            boolean invertMatch = getOptionalBoolean(input, "invertMatch", false);
            int maxHits = getOptionalInt(input, "maxHits", 200);
            int maxMatchesPerFile = getOptionalInt(input, "maxMatchesPerFile", 50);
            long maxFileSizeBytes = getOptionalLong(input, "maxFileSizeBytes", 5_000_000L);
            long timeoutMs = getOptionalLong(input, "timeoutMs", 5000L);
            String encoding = getOptionalString(input, "encoding", null);
            boolean includeBinary = getOptionalBoolean(input, "includeBinary", false);
            boolean includeContext = getOptionalBoolean(input, "includeContext", false);
            int contextLines = getOptionalInt(input, "contextLines", 2);
            OutputMode outputMode = parseOutputMode(getOptionalString(input, "outputMode", "MATCHES"));

            // Build search context
            GrepContext ctx = new GrepContext(
                    root, pattern, recursive, fileNamePattern, regex, caseSensitive, wholeWord,
                    invertMatch, maxHits, maxMatchesPerFile, maxFileSizeBytes, timeoutMs,
                    encoding, includeBinary, includeContext, contextLines, outputMode
            );

            // Compile the search pattern
            ctx.compiledPattern = compilePattern(pattern, regex, caseSensitive, wholeWord);

            // Execute search
            GrepResult result = executeGrep(ctx);

            // Build response based on output mode
            return buildResponse(ctx, result, resultVar);

        } catch (PatternSyntaxException e) {
            return errorResponse("Ungültiger regulärer Ausdruck: " + e.getMessage(),
                    "PatternSyntaxError", resultVar);
        } catch (FileServiceException e) {
            return errorResponse(e.getMessage() != null ? e.getMessage() : e.getClass().getName(),
                    "FileServiceError", resultVar, e.getErrorCode() != null ? e.getErrorCode().name() : null);
        } catch (Exception e) {
            return errorResponse(e.getMessage() != null ? e.getMessage() : e.getClass().getName(),
                    "ToolExecutionError", resultVar);
        }
    }

    private McpToolResponse buildResponse(GrepContext ctx, GrepResult result, String resultVar) {
        JsonObject response = new JsonObject();
        response.addProperty("status", "success");
        response.addProperty("toolName", "grep_search");
        response.addProperty("root", ctx.root);
        response.addProperty("pattern", ctx.pattern);
        response.addProperty("regex", ctx.regex);
        response.addProperty("recursive", ctx.recursive);
        response.addProperty("outputMode", ctx.outputMode.name());

        switch (ctx.outputMode) {
            case FILES:
                return buildFilesResponse(response, result, resultVar);
            case COUNT:
                return buildCountResponse(response, result, resultVar);
            case MATCHES:
            default:
                return buildMatchesResponse(response, ctx, result, resultVar);
        }
    }

    private McpToolResponse buildMatchesResponse(JsonObject response, GrepContext ctx,
                                                   GrepResult result, String resultVar) {
        int totalHits = 0;
        for (GrepFileResult fr : result.fileResults) {
            totalHits += fr.matches.size();
        }

        response.addProperty("hitCount", totalHits);
        response.addProperty("fileHitCount", result.fileResults.size());
        response.addProperty("skippedTooLarge", result.skippedTooLarge);
        response.addProperty("skippedBinary", result.skippedBinary);
        response.addProperty("skippedDecodeError", result.skippedDecodeError);
        response.addProperty("timedOut", result.timedOut);

        JsonArray hitsArray = new JsonArray();
        for (GrepFileResult fr : result.fileResults) {
            JsonObject fileHit = new JsonObject();
            fileHit.addProperty("path", fr.path);
            fileHit.addProperty("matchCount", fr.matches.size());

            JsonArray matchesArray = new JsonArray();
            for (GrepMatch match : fr.matches) {
                matchesArray.add(match.toJson(ctx.includeContext));
            }
            fileHit.add("matches", matchesArray);
            hitsArray.add(fileHit);
        }
        response.add("hits", hitsArray);

        return new McpToolResponse(response, resultVar, null);
    }

    private McpToolResponse buildFilesResponse(JsonObject response, GrepResult result, String resultVar) {
        response.addProperty("fileHitCount", result.fileResults.size());

        JsonArray pathsArray = new JsonArray();
        for (GrepFileResult fr : result.fileResults) {
            pathsArray.add(fr.path);
        }
        response.add("paths", pathsArray);

        return new McpToolResponse(response, resultVar, null);
    }

    private McpToolResponse buildCountResponse(JsonObject response, GrepResult result, String resultVar) {
        int totalMatches = 0;
        JsonArray countsArray = new JsonArray();

        for (GrepFileResult fr : result.fileResults) {
            totalMatches += fr.matches.size();
            JsonObject countObj = new JsonObject();
            countObj.addProperty("path", fr.path);
            countObj.addProperty("matchCount", fr.matches.size());
            countsArray.add(countObj);
        }

        response.addProperty("totalMatches", totalMatches);
        response.add("counts", countsArray);

        return new McpToolResponse(response, resultVar, null);
    }

    private GrepResult executeGrep(GrepContext ctx) throws FileServiceException {
        GrepResult result = new GrepResult();
        long startTime = System.currentTimeMillis();

        // Resolve the resource
        VirtualResourceResolver resolver = new VirtualResourceResolver();
        VirtualResource resource = resolver.resolve(ctx.root);

        // Create FileService
        CredentialsProvider credentialsProvider = new LoginManagerCredentialsProvider(
                (host, user) -> LoginManager.getInstance().getCachedPassword(host, user)
        );

        try (FileService fs = new FileServiceFactory().create(resource, credentialsProvider)) {
            if (resource.getKind() == VirtualResourceKind.FILE) {
                // Single file search
                grepSingleFile(fs, resource.getResolvedPath(), ctx, result, startTime);
            } else {
                // Directory search
                grepDirectory(fs, resource.getResolvedPath(), ctx, result, startTime);
            }
        }

        return result;
    }

    private void grepDirectory(FileService fs, String dirPath, GrepContext ctx,
                                GrepResult result, long startTime) throws FileServiceException {
        // Check timeout
        if (isTimedOut(ctx, startTime)) {
            result.timedOut = true;
            return;
        }

        // Check max hits
        if (getTotalHits(result) >= ctx.maxHits) {
            return;
        }

        List<FileNode> entries;
        try {
            entries = fs.list(dirPath);
        } catch (FileServiceException e) {
            // Skip inaccessible directories
            return;
        }

        // Compile filename patterns
        List<Pattern> filePatterns = compileFileNamePatterns(ctx.fileNamePattern, false);

        for (FileNode node : entries) {
            // Check limits
            if (isTimedOut(ctx, startTime)) {
                result.timedOut = true;
                return;
            }
            if (getTotalHits(result) >= ctx.maxHits) {
                return;
            }

            if (node.isDirectory()) {
                if (ctx.recursive) {
                    grepDirectory(fs, node.getPath(), ctx, result, startTime);
                }
            } else {
                // Check if filename matches pattern
                if (matchesFileNamePattern(node.getName(), filePatterns)) {
                    grepSingleFile(fs, node.getPath(), node.getName(), node.getSize(), ctx, result, startTime);
                }
            }
        }
    }

    private void grepSingleFile(FileService fs, String filePath, GrepContext ctx,
                                 GrepResult result, long startTime) throws FileServiceException {
        java.io.File file = new java.io.File(filePath);
        grepSingleFile(fs, filePath, file.getName(), file.length(), ctx, result, startTime);
    }

    private void grepSingleFile(FileService fs, String filePath, String fileName, long fileSize,
                                 GrepContext ctx, GrepResult result, long startTime) {
        // Check timeout
        if (isTimedOut(ctx, startTime)) {
            result.timedOut = true;
            return;
        }

        // Check max hits
        if (getTotalHits(result) >= ctx.maxHits) {
            return;
        }

        // Check file size limit
        if (fileSize > ctx.maxFileSizeBytes) {
            result.skippedTooLarge++;
            return;
        }

        try {
            FilePayload payload = fs.readFile(filePath);
            byte[] bytes = payload.getBytes();

            // Check for binary content
            if (!ctx.includeBinary && isBinary(bytes)) {
                result.skippedBinary++;
                return;
            }

            // Decode content
            Charset charset = resolveCharset(ctx.encoding, payload);
            String content;
            try {
                content = new String(bytes, charset);
            } catch (Exception e) {
                result.skippedDecodeError++;
                return;
            }

            // Search in content
            List<GrepMatch> matches = searchContent(content, ctx, result);

            if (!matches.isEmpty()) {
                GrepFileResult fileResult = new GrepFileResult();
                fileResult.path = filePath;
                fileResult.matches = matches;
                result.fileResults.add(fileResult);
            }

        } catch (FileServiceException e) {
            // Skip unreadable files
        }
    }

    private List<GrepMatch> searchContent(String content, GrepContext ctx, GrepResult result) {
        List<GrepMatch> matches = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        int globalHitsRemaining = ctx.maxHits - getTotalHits(result);
        int fileMatchCount = 0;

        for (int i = 0; i < lines.length; i++) {
            if (fileMatchCount >= ctx.maxMatchesPerFile || fileMatchCount >= globalHitsRemaining) {
                break;
            }

            String line = lines[i];
            Matcher matcher = ctx.compiledPattern.matcher(line);
            boolean hasMatch = matcher.find();

            // Handle invert match (grep -v)
            boolean includeThisLine = ctx.invertMatch ? !hasMatch : hasMatch;

            if (includeThisLine) {
                GrepMatch match = new GrepMatch();
                match.lineNumber = i + 1;
                match.line = truncateLine(line, 500);

                // Find all match ranges (not for invertMatch)
                if (!ctx.invertMatch) {
                    match.matchRanges = new ArrayList<>();
                    matcher.reset();
                    while (matcher.find()) {
                        int[] range = new int[]{matcher.start(), matcher.end()};
                        match.matchRanges.add(range);
                    }
                }

                // Add context if requested
                if (ctx.includeContext) {
                    match.contextBefore = new ArrayList<>();
                    for (int j = Math.max(0, i - ctx.contextLines); j < i; j++) {
                        match.contextBefore.add(truncateLine(lines[j], 500));
                    }

                    match.contextAfter = new ArrayList<>();
                    for (int j = i + 1; j <= Math.min(lines.length - 1, i + ctx.contextLines); j++) {
                        match.contextAfter.add(truncateLine(lines[j], 500));
                    }
                }

                matches.add(match);
                fileMatchCount++;
            }
        }

        return matches;
    }

    private String truncateLine(String line, int maxLength) {
        if (line == null) return "";
        if (line.length() <= maxLength) return line;
        return line.substring(0, maxLength) + "...";
    }

    private Pattern compilePattern(String pattern, boolean regex, boolean caseSensitive, boolean wholeWord) {
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;

        String regexPattern;
        if (regex) {
            regexPattern = pattern;
        } else {
            // Escape special regex characters
            regexPattern = Pattern.quote(pattern);
            // Add word boundaries if wholeWord is requested
            if (wholeWord) {
                regexPattern = "\\b" + regexPattern + "\\b";
            }
        }

        return Pattern.compile(regexPattern, flags);
    }

    private List<Pattern> compileFileNamePatterns(String fileNamePattern, boolean caseSensitive) {
        List<Pattern> patterns = new ArrayList<>();
        if (fileNamePattern == null || fileNamePattern.isEmpty() || "*".equals(fileNamePattern)) {
            patterns.add(Pattern.compile(".*"));
            return patterns;
        }

        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
        String[] parts = fileNamePattern.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                String regexPattern = globToRegex(trimmed);
                patterns.add(Pattern.compile(regexPattern, flags));
            }
        }

        return patterns.isEmpty() ? Collections.singletonList(Pattern.compile(".*")) : patterns;
    }

    private String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        sb.append("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*':
                    sb.append(".*");
                    break;
                case '?':
                    sb.append(".");
                    break;
                case '.':
                case '\\':
                case '[':
                case ']':
                case '{':
                case '}':
                case '(':
                case ')':
                case '+':
                case '^':
                case '$':
                case '|':
                    sb.append("\\").append(c);
                    break;
                default:
                    sb.append(c);
            }
        }
        sb.append("$");
        return sb.toString();
    }

    private boolean matchesFileNamePattern(String fileName, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(fileName).matches()) {
                return true;
            }
        }
        return false;
    }

    private boolean isBinary(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        int checkLength = Math.min(bytes.length, 8192);
        for (int i = 0; i < checkLength; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private Charset resolveCharset(String encoding, FilePayload payload) {
        if (encoding != null && !encoding.trim().isEmpty()) {
            try {
                return Charset.forName(encoding);
            } catch (Exception ignored) {
            }
        }
        if (payload.getCharset() != null) {
            return payload.getCharset();
        }
        return Charset.defaultCharset();
    }

    private boolean isTimedOut(GrepContext ctx, long startTime) {
        return System.currentTimeMillis() - startTime > ctx.timeoutMs;
    }

    private int getTotalHits(GrepResult result) {
        int total = 0;
        for (GrepFileResult fr : result.fileResults) {
            total += fr.matches.size();
        }
        return total;
    }

    private OutputMode parseOutputMode(String modeStr) {
        if (modeStr == null || modeStr.isEmpty()) {
            return OutputMode.MATCHES;
        }
        try {
            return OutputMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return OutputMode.MATCHES;
        }
    }

    // Helper methods for parsing input
    private String getOptionalString(JsonObject input, String key, String defaultValue) {
        if (input.has(key) && !input.get(key).isJsonNull()) {
            return input.get(key).getAsString();
        }
        return defaultValue;
    }

    private boolean getOptionalBoolean(JsonObject input, String key, boolean defaultValue) {
        if (input.has(key) && !input.get(key).isJsonNull()) {
            return input.get(key).getAsBoolean();
        }
        return defaultValue;
    }

    private int getOptionalInt(JsonObject input, String key, int defaultValue) {
        if (input.has(key) && !input.get(key).isJsonNull()) {
            return input.get(key).getAsInt();
        }
        return defaultValue;
    }

    private long getOptionalLong(JsonObject input, String key, long defaultValue) {
        if (input.has(key) && !input.get(key).isJsonNull()) {
            return input.get(key).getAsLong();
        }
        return defaultValue;
    }

    private McpToolResponse errorResponse(String message, String errorType, String resultVar) {
        return errorResponse(message, errorType, resultVar, null);
    }

    private McpToolResponse errorResponse(String message, String errorType, String resultVar, String errorCode) {
        JsonObject response = new JsonObject();
        response.addProperty("status", "error");
        response.addProperty("toolName", "grep_search");
        response.addProperty("errorType", errorType);
        response.addProperty("message", message);
        if (errorCode != null) {
            response.addProperty("errorCode", errorCode);
        }
        response.addProperty("hint", "Prüfe den Pfad oder die FTP-Verbindung.");
        return new McpToolResponse(response, resultVar, null);
    }

    // Inner classes for grep context and results

    private static class GrepContext {
        final String root;
        final String pattern;
        final boolean recursive;
        final String fileNamePattern;
        final boolean regex;
        final boolean caseSensitive;
        final boolean wholeWord;
        final boolean invertMatch;
        final int maxHits;
        final int maxMatchesPerFile;
        final long maxFileSizeBytes;
        final long timeoutMs;
        final String encoding;
        final boolean includeBinary;
        final boolean includeContext;
        final int contextLines;
        final OutputMode outputMode;
        Pattern compiledPattern;

        GrepContext(String root, String pattern, boolean recursive, String fileNamePattern,
                    boolean regex, boolean caseSensitive, boolean wholeWord, boolean invertMatch,
                    int maxHits, int maxMatchesPerFile, long maxFileSizeBytes, long timeoutMs,
                    String encoding, boolean includeBinary, boolean includeContext, int contextLines,
                    OutputMode outputMode) {
            this.root = root;
            this.pattern = pattern;
            this.recursive = recursive;
            this.fileNamePattern = fileNamePattern;
            this.regex = regex;
            this.caseSensitive = caseSensitive;
            this.wholeWord = wholeWord;
            this.invertMatch = invertMatch;
            this.maxHits = maxHits;
            this.maxMatchesPerFile = maxMatchesPerFile;
            this.maxFileSizeBytes = maxFileSizeBytes;
            this.timeoutMs = timeoutMs;
            this.encoding = encoding;
            this.includeBinary = includeBinary;
            this.includeContext = includeContext;
            this.contextLines = contextLines;
            this.outputMode = outputMode;
        }
    }

    private static class GrepResult {
        List<GrepFileResult> fileResults = new ArrayList<>();
        int skippedTooLarge = 0;
        int skippedBinary = 0;
        int skippedDecodeError = 0;
        boolean timedOut = false;
    }

    private static class GrepFileResult {
        String path;
        List<GrepMatch> matches = new ArrayList<>();
    }

    private static class GrepMatch {
        int lineNumber;
        String line;
        List<int[]> matchRanges; // [start, end] pairs
        List<String> contextBefore;
        List<String> contextAfter;

        JsonObject toJson(boolean includeContext) {
            JsonObject obj = new JsonObject();
            obj.addProperty("lineNumber", lineNumber);
            obj.addProperty("line", line);

            if (matchRanges != null && !matchRanges.isEmpty()) {
                JsonArray rangesArray = new JsonArray();
                for (int[] range : matchRanges) {
                    JsonObject rangeObj = new JsonObject();
                    rangeObj.addProperty("start", range[0]);
                    rangeObj.addProperty("end", range[1]);
                    rangesArray.add(rangeObj);
                }
                obj.add("matchRanges", rangesArray);
            }

            if (includeContext) {
                if (contextBefore != null) {
                    JsonArray before = new JsonArray();
                    for (String s : contextBefore) {
                        before.add(s);
                    }
                    obj.add("contextBefore", before);
                }

                if (contextAfter != null) {
                    JsonArray after = new JsonArray();
                    for (String s : contextAfter) {
                        after.add(s);
                    }
                    obj.add("contextAfter", after);
                }
            }

            return obj;
        }
    }
}

