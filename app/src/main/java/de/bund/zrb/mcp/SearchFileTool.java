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
 * MCP Tool that searches for files by name/pattern and/or content.
 * Supports recursive directory traversal, regex patterns, context lines, and various limits.
 */
public class SearchFileTool implements McpTool {

    private final MainframeContext context;

    // Search modes
    public enum SearchMode {
        NAME,    // Search in file names/paths only
        CONTENT, // Search in file contents only
        BOTH     // Search in both name and content
    }

    public SearchFileTool(MainframeContext context) {
        this.context = context;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<>();
        properties.put("root", new ToolSpec.Property("string", "Startverzeichnis oder Datei (lokal oder FTP-Pfad)"));
        properties.put("mode", new ToolSpec.Property("string", "Suchmodus: NAME (nur Dateinamen), CONTENT (nur Inhalt), BOTH (beides)"));
        properties.put("query", new ToolSpec.Property("string", "Suchbegriff (Pflicht bei CONTENT/BOTH, optional bei NAME mit fileNamePattern)"));
        properties.put("recursive", new ToolSpec.Property("boolean", "Rekursiv in Unterverzeichnissen suchen (default: true)"));
        properties.put("fileNamePattern", new ToolSpec.Property("string", "Dateinamen-Pattern, z.B. '*.txt' oder '*.txt;*.json' (default: '*')"));
        properties.put("caseSensitive", new ToolSpec.Property("boolean", "Groß-/Kleinschreibung beachten (default: false)"));
        properties.put("regex", new ToolSpec.Property("boolean", "Query als regulären Ausdruck interpretieren (default: false)"));
        properties.put("maxHits", new ToolSpec.Property("integer", "Maximale Anzahl Treffer (default: 50)"));
        properties.put("maxMatchesPerFile", new ToolSpec.Property("integer", "Maximale Matches pro Datei (default: 5)"));
        properties.put("maxFileSizeBytes", new ToolSpec.Property("integer", "Maximale Dateigröße in Bytes (default: 5000000)"));
        properties.put("includeContext", new ToolSpec.Property("boolean", "Kontextzeilen um Treffer anzeigen (default: false)"));
        properties.put("contextLines", new ToolSpec.Property("integer", "Anzahl Kontextzeilen vor/nach Treffer (default: 2)"));
        properties.put("encoding", new ToolSpec.Property("string", "Zeichenkodierung (optional, default: System-Default)"));
        properties.put("includeBinary", new ToolSpec.Property("boolean", "Binärdateien durchsuchen (default: false)"));
        properties.put("timeoutMs", new ToolSpec.Property("integer", "Zeitlimit in Millisekunden (default: 5000)"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(properties, Arrays.asList("root", "mode"));

        Map<String, Object> example = new LinkedHashMap<>();
        example.put("root", "C:\\TEST");
        example.put("mode", "CONTENT");
        example.put("query", "Hamburg Hafen");
        example.put("recursive", true);
        example.put("fileNamePattern", "*.txt");
        example.put("maxHits", 50);

        return new ToolSpec(
                "search_file",
                "Durchsucht Dateien nach Name/Pattern und/oder Inhalt. " +
                "MODE=NAME: Findet Dateien anhand des Dateinamens (fileNamePattern). " +
                "MODE=CONTENT: Findet Dateien, die den query-Text enthalten. " +
                "MODE=BOTH: Kombiniert beide Sucharten. " +
                "Unterstützt Regex, Kontextzeilen und verschiedene Limits. " +
                "Für FTP-Pfade muss vorher eine Verbindung bestehen.",
                inputSchema,
                example
        );
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        JsonObject response = new JsonObject();
        response.addProperty("toolName", "search_file");

        try {
            // Validate required fields
            if (input == null || !input.has("root") || input.get("root").isJsonNull()) {
                return errorResponse("Pflichtfeld fehlt: root", "ToolExecutionError", resultVar);
            }
            if (!input.has("mode") || input.get("mode").isJsonNull()) {
                return errorResponse("Pflichtfeld fehlt: mode", "ToolExecutionError", resultVar);
            }

            // Parse input parameters
            String root = input.get("root").getAsString();
            SearchMode mode = parseMode(input.get("mode").getAsString());
            String query = getOptionalString(input, "query", null);
            boolean recursive = getOptionalBoolean(input, "recursive", true);
            String fileNamePattern = getOptionalString(input, "fileNamePattern", "*");
            boolean caseSensitive = getOptionalBoolean(input, "caseSensitive", false);
            boolean regex = getOptionalBoolean(input, "regex", false);
            int maxHits = getOptionalInt(input, "maxHits", 50);
            int maxMatchesPerFile = getOptionalInt(input, "maxMatchesPerFile", 5);
            long maxFileSizeBytes = getOptionalLong(input, "maxFileSizeBytes", 5_000_000L);
            boolean includeContext = getOptionalBoolean(input, "includeContext", false);
            int contextLines = getOptionalInt(input, "contextLines", 2);
            String encoding = getOptionalString(input, "encoding", null);
            boolean includeBinary = getOptionalBoolean(input, "includeBinary", false);
            long timeoutMs = getOptionalLong(input, "timeoutMs", 5000L);

            // Validate: query required for CONTENT and BOTH modes (unless fileNamePattern is set for BOTH)
            if ((mode == SearchMode.CONTENT || mode == SearchMode.BOTH) &&
                (query == null || query.trim().isEmpty())) {
                return errorResponse("Pflichtfeld 'query' fehlt für Modus " + mode, "ToolExecutionError", resultVar);
            }

            // Build search context
            SearchContext ctx = new SearchContext(
                    root, mode, query, recursive, fileNamePattern, caseSensitive, regex,
                    maxHits, maxMatchesPerFile, maxFileSizeBytes, includeContext, contextLines,
                    encoding, includeBinary, timeoutMs
            );

            // Execute search
            SearchResult result = executeSearch(ctx);

            // Build response
            response.addProperty("status", "success");
            response.addProperty("root", root);
            response.addProperty("mode", mode.name());
            if (query != null) {
                response.addProperty("query", query);
            }
            response.addProperty("recursive", recursive);
            response.addProperty("hitCount", result.hits.size());
            response.addProperty("skippedTooLarge", result.skippedTooLarge);
            response.addProperty("skippedBinary", result.skippedBinary);
            response.addProperty("skippedDecodeError", result.skippedDecodeError);
            response.addProperty("timedOut", result.timedOut);

            JsonArray hitsArray = new JsonArray();
            for (SearchHit hit : result.hits) {
                hitsArray.add(hit.toJson());
            }
            response.add("hits", hitsArray);

            return new McpToolResponse(response, resultVar, null);

        } catch (FileServiceException e) {
            return errorResponse(e.getMessage() != null ? e.getMessage() : e.getClass().getName(),
                    "FileServiceError", resultVar, e.getErrorCode() != null ? e.getErrorCode().name() : null);
        } catch (PatternSyntaxException e) {
            return errorResponse("Ungültiger regulärer Ausdruck: " + e.getMessage(),
                    "PatternSyntaxError", resultVar);
        } catch (Exception e) {
            return errorResponse(e.getMessage() != null ? e.getMessage() : e.getClass().getName(),
                    "ToolExecutionError", resultVar);
        }
    }

    private SearchResult executeSearch(SearchContext ctx) throws FileServiceException {
        SearchResult result = new SearchResult();
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
                searchSingleFile(fs, resource.getResolvedPath(), ctx, result, startTime);
            } else {
                // Directory search
                searchDirectory(fs, resource.getResolvedPath(), ctx, result, startTime);
            }
        }

        return result;
    }

    private void searchDirectory(FileService fs, String dirPath, SearchContext ctx,
                                  SearchResult result, long startTime) throws FileServiceException {
        // Check timeout
        if (isTimedOut(ctx, startTime)) {
            result.timedOut = true;
            return;
        }

        // Check max hits
        if (result.hits.size() >= ctx.maxHits) {
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
        List<Pattern> filePatterns = compileFileNamePatterns(ctx.fileNamePattern, ctx.caseSensitive);

        for (FileNode node : entries) {
            // Check limits
            if (isTimedOut(ctx, startTime)) {
                result.timedOut = true;
                return;
            }
            if (result.hits.size() >= ctx.maxHits) {
                return;
            }

            if (node.isDirectory()) {
                if (ctx.recursive) {
                    searchDirectory(fs, node.getPath(), ctx, result, startTime);
                }
            } else {
                // Check if filename matches pattern
                if (matchesFileNamePattern(node.getName(), filePatterns)) {
                    searchSingleFile(fs, node.getPath(), node.getName(), node.getSize(),
                            node.getLastModifiedMillis(), ctx, result, startTime);
                }
            }
        }
    }

    private void searchSingleFile(FileService fs, String filePath, SearchContext ctx,
                                   SearchResult result, long startTime) throws FileServiceException {
        java.io.File file = new java.io.File(filePath);
        searchSingleFile(fs, filePath, file.getName(), file.length(),
                file.lastModified(), ctx, result, startTime);
    }

    private void searchSingleFile(FileService fs, String filePath, String fileName, long fileSize,
                                   long lastModified, SearchContext ctx, SearchResult result,
                                   long startTime) throws FileServiceException {
        // Check timeout
        if (isTimedOut(ctx, startTime)) {
            result.timedOut = true;
            return;
        }

        // Check max hits
        if (result.hits.size() >= ctx.maxHits) {
            return;
        }

        SearchHit hit = new SearchHit();
        hit.path = filePath;
        hit.isDirectory = false;
        hit.fileSize = fileSize;
        hit.lastModified = lastModified;

        boolean nameMatched = false;
        boolean contentMatched = false;

        // NAME search: check if filename/path matches query
        if (ctx.mode == SearchMode.NAME || ctx.mode == SearchMode.BOTH) {
            if (ctx.query != null && !ctx.query.isEmpty()) {
                Pattern queryPattern = compileQueryPattern(ctx.query, ctx.caseSensitive, ctx.regex);
                if (queryPattern.matcher(filePath).find() || queryPattern.matcher(fileName).find()) {
                    nameMatched = true;
                    hit.matchCount++;
                }
            } else {
                // NAME mode without query but with fileNamePattern - already filtered
                nameMatched = true;
            }
        }

        // CONTENT search
        if (ctx.mode == SearchMode.CONTENT || ctx.mode == SearchMode.BOTH) {
            // Check file size limit
            if (fileSize > ctx.maxFileSizeBytes) {
                result.skippedTooLarge++;
                if (ctx.mode == SearchMode.NAME && nameMatched) {
                    result.hits.add(hit);
                }
                return;
            }

            try {
                FilePayload payload = fs.readFile(filePath);
                byte[] bytes = payload.getBytes();

                // Check for binary content
                if (!ctx.includeBinary && isBinary(bytes)) {
                    result.skippedBinary++;
                    if (ctx.mode == SearchMode.NAME && nameMatched) {
                        result.hits.add(hit);
                    }
                    return;
                }

                // Decode content
                Charset charset = resolveCharset(ctx.encoding, payload);
                String content;
                try {
                    content = new String(bytes, charset);
                } catch (Exception e) {
                    result.skippedDecodeError++;
                    if (ctx.mode == SearchMode.NAME && nameMatched) {
                        result.hits.add(hit);
                    }
                    return;
                }

                // Search in content
                List<SearchMatch> matches = searchInContent(content, ctx);
                if (!matches.isEmpty()) {
                    contentMatched = true;
                    hit.matchCount += matches.size();
                    hit.matches = matches;
                }

            } catch (FileServiceException e) {
                // Skip unreadable files
                return;
            }
        }

        // Add hit if matched according to mode
        if (ctx.mode == SearchMode.NAME && nameMatched) {
            result.hits.add(hit);
        } else if (ctx.mode == SearchMode.CONTENT && contentMatched) {
            result.hits.add(hit);
        } else if (ctx.mode == SearchMode.BOTH && (nameMatched || contentMatched)) {
            result.hits.add(hit);
        }
    }

    private List<SearchMatch> searchInContent(String content, SearchContext ctx) {
        List<SearchMatch> matches = new ArrayList<>();
        if (ctx.query == null || ctx.query.isEmpty()) {
            return matches;
        }

        Pattern pattern = compileQueryPattern(ctx.query, ctx.caseSensitive, ctx.regex);
        String[] lines = content.split("\n", -1);

        for (int i = 0; i < lines.length && matches.size() < ctx.maxMatchesPerFile; i++) {
            String line = lines[i];
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                SearchMatch match = new SearchMatch();
                match.lineNumber = i + 1;
                match.line = line.length() > 500 ? line.substring(0, 500) + "..." : line;

                if (ctx.includeContext) {
                    // Context before
                    match.contextBefore = new ArrayList<>();
                    for (int j = Math.max(0, i - ctx.contextLines); j < i; j++) {
                        match.contextBefore.add(lines[j]);
                    }

                    // Context after
                    match.contextAfter = new ArrayList<>();
                    for (int j = i + 1; j <= Math.min(lines.length - 1, i + ctx.contextLines); j++) {
                        match.contextAfter.add(lines[j]);
                    }
                }

                matches.add(match);
            }
        }

        return matches;
    }

    private Pattern compileQueryPattern(String query, boolean caseSensitive, boolean regex) {
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
        if (regex) {
            return Pattern.compile(query, flags);
        } else {
            return Pattern.compile(Pattern.quote(query), flags);
        }
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
                // Convert glob pattern to regex
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
        // Check first 8KB for NUL bytes (common binary indicator)
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

    private boolean isTimedOut(SearchContext ctx, long startTime) {
        return System.currentTimeMillis() - startTime > ctx.timeoutMs;
    }

    private SearchMode parseMode(String modeStr) {
        if (modeStr == null || modeStr.isEmpty()) {
            return SearchMode.CONTENT;
        }
        try {
            return SearchMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SearchMode.CONTENT;
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
        response.addProperty("toolName", "search_file");
        response.addProperty("errorType", errorType);
        response.addProperty("message", message);
        if (errorCode != null) {
            response.addProperty("errorCode", errorCode);
        }
        response.addProperty("hint", "Prüfe den Pfad oder ob die Verbindung (FTP) besteht.");
        return new McpToolResponse(response, resultVar, null);
    }

    // Inner classes for search context and results

    private static class SearchContext {
        final String root;
        final SearchMode mode;
        final String query;
        final boolean recursive;
        final String fileNamePattern;
        final boolean caseSensitive;
        final boolean regex;
        final int maxHits;
        final int maxMatchesPerFile;
        final long maxFileSizeBytes;
        final boolean includeContext;
        final int contextLines;
        final String encoding;
        final boolean includeBinary;
        final long timeoutMs;

        SearchContext(String root, SearchMode mode, String query, boolean recursive,
                      String fileNamePattern, boolean caseSensitive, boolean regex,
                      int maxHits, int maxMatchesPerFile, long maxFileSizeBytes,
                      boolean includeContext, int contextLines, String encoding,
                      boolean includeBinary, long timeoutMs) {
            this.root = root;
            this.mode = mode;
            this.query = query;
            this.recursive = recursive;
            this.fileNamePattern = fileNamePattern;
            this.caseSensitive = caseSensitive;
            this.regex = regex;
            this.maxHits = maxHits;
            this.maxMatchesPerFile = maxMatchesPerFile;
            this.maxFileSizeBytes = maxFileSizeBytes;
            this.includeContext = includeContext;
            this.contextLines = contextLines;
            this.encoding = encoding;
            this.includeBinary = includeBinary;
            this.timeoutMs = timeoutMs;
        }
    }

    private static class SearchResult {
        List<SearchHit> hits = new ArrayList<>();
        int skippedTooLarge = 0;
        int skippedBinary = 0;
        int skippedDecodeError = 0;
        boolean timedOut = false;
    }

    private static class SearchHit {
        String path;
        boolean isDirectory;
        long fileSize;
        long lastModified;
        int matchCount = 0;
        List<SearchMatch> matches;

        JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("path", path);
            obj.addProperty("isDirectory", isDirectory);
            obj.addProperty("fileSize", fileSize);
            obj.addProperty("lastModified", lastModified);
            obj.addProperty("matchCount", matchCount);

            if (matches != null && !matches.isEmpty()) {
                JsonArray matchesArray = new JsonArray();
                for (SearchMatch match : matches) {
                    matchesArray.add(match.toJson());
                }
                obj.add("matches", matchesArray);
            }

            return obj;
        }
    }

    private static class SearchMatch {
        int lineNumber;
        String line;
        List<String> contextBefore;
        List<String> contextAfter;

        JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("lineNumber", lineNumber);
            obj.addProperty("line", line);

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

            return obj;
        }
    }
}

