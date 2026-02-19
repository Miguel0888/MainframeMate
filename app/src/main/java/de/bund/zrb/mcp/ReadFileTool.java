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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Tool that reads file content or directory listings without opening a tab.
 * Returns the data as JSON to the bot.
 */
public class ReadFileTool implements McpTool {

    private final MainframeContext context;

    public ReadFileTool(MainframeContext context) {
        this.context = context;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<>();
        properties.put("path", new ToolSpec.Property("string", "Pfad zur Datei oder Verzeichnis (lokal oder FTP)"));
        properties.put("maxLines", new ToolSpec.Property("integer", "Maximale Anzahl Zeilen bei Dateien (optional, default: unbegrenzt)"));
        properties.put("encoding", new ToolSpec.Property("string", "Zeichenkodierung (optional, default: System-Default)"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(properties, Collections.singletonList("path"));

        Map<String, Object> example = new LinkedHashMap<>();
        example.put("path", "C:\\TEST\\datei.txt");
        example.put("maxLines", 100);

        return new ToolSpec(
                "read_file",
                "Liest den Inhalt einer Datei oder listet ein Verzeichnis auf, ohne einen Tab zu öffnen. " +
                "Bei Dateien wird der Textinhalt zurückgegeben. " +
                "Bei Verzeichnissen wird eine Liste der Einträge zurückgegeben. " +
                "Für FTP-Pfade muss vorher eine Verbindung bestehen.",
                inputSchema,
                example
        );
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        JsonObject response = new JsonObject();

        try {
            if (input == null || !input.has("path") || input.get("path").isJsonNull()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Pflichtfeld fehlt: path");
                return new McpToolResponse(response, resultVar, null);
            }

            String path = input.get("path").getAsString();
            Integer maxLines = input.has("maxLines") && !input.get("maxLines").isJsonNull()
                    ? input.get("maxLines").getAsInt() : null;
            String encoding = input.has("encoding") && !input.get("encoding").isJsonNull()
                    ? input.get("encoding").getAsString() : null;

            // Resolve the resource
            VirtualResourceResolver resolver = new VirtualResourceResolver();
            VirtualResource resource = resolver.resolve(path);

            // Create FileService
            CredentialsProvider credentialsProvider = new LoginManagerCredentialsProvider(
                    (host, user) -> LoginManager.getInstance().getCachedPassword(host, user)
            );

            try (FileService fs = new FileServiceFactory().create(resource, credentialsProvider)) {
                if (resource.getKind() == VirtualResourceKind.DIRECTORY) {
                    return readDirectory(fs, resource, resultVar);
                } else {
                    return readFile(fs, resource, maxLines, encoding, resultVar);
                }
            }

        } catch (FileServiceException e) {
            response.addProperty("status", "error");
            response.addProperty("message", e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            response.addProperty("errorCode", e.getErrorCode() != null ? e.getErrorCode().name() : "UNKNOWN");
            return new McpToolResponse(response, resultVar, null);
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            return new McpToolResponse(response, resultVar, null);
        }
    }

    private McpToolResponse readDirectory(FileService fs, VirtualResource resource, String resultVar)
            throws FileServiceException {
        JsonObject response = new JsonObject();

        List<FileNode> entries = fs.list(resource.getResolvedPath());

        JsonArray entriesArray = new JsonArray();
        for (FileNode node : entries) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", node.getName());
            entry.addProperty("path", node.getPath());
            entry.addProperty("isDirectory", node.isDirectory());
            entry.addProperty("size", node.getSize());
            entry.addProperty("lastModified", node.getLastModifiedMillis());
            entriesArray.add(entry);
        }

        response.addProperty("status", "success");
        response.addProperty("type", "directory");
        response.addProperty("path", resource.getResolvedPath());
        response.addProperty("local", resource.isLocal());
        response.addProperty("entryCount", entries.size());
        response.add("entries", entriesArray);

        return new McpToolResponse(response, resultVar, null);
    }

    private McpToolResponse readFile(FileService fs, VirtualResource resource,
                                     Integer maxLines, String encoding, String resultVar)
            throws FileServiceException {
        JsonObject response = new JsonObject();

        FilePayload payload = fs.readFile(resource.getResolvedPath());

        Charset charset;
        if (encoding != null && !encoding.trim().isEmpty()) {
            try {
                charset = Charset.forName(encoding);
            } catch (Exception e) {
                charset = payload.getCharset() != null ? payload.getCharset() : Charset.defaultCharset();
            }
        } else {
            charset = payload.getCharset() != null ? payload.getCharset() : Charset.defaultCharset();
        }

        // IMPORTANT: Use getEditorText() for proper RECORD_STRUCTURE handling
        String content = payload.getEditorText();

        // Apply maxLines limit if specified
        int lineCount = countLines(content);
        boolean truncated = false;
        if (maxLines != null && maxLines > 0 && lineCount > maxLines) {
            content = truncateToLines(content, maxLines);
            truncated = true;
        }

        response.addProperty("status", "success");
        response.addProperty("type", "file");
        response.addProperty("path", resource.getResolvedPath());
        response.addProperty("local", resource.isLocal());
        response.addProperty("encoding", charset.name());
        response.addProperty("size", payload.getBytes().length);
        response.addProperty("lineCount", lineCount);
        response.addProperty("truncated", truncated);
        if (truncated) {
            response.addProperty("maxLinesApplied", maxLines);
        }
        response.addProperty("content", content);

        return new McpToolResponse(response, resultVar, null);
    }

    private int countLines(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private String truncateToLines(String content, int maxLines) {
        if (content == null || content.isEmpty() || maxLines <= 0) {
            return content;
        }

        StringBuilder sb = new StringBuilder();
        int lineCount = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            sb.append(c);
            if (c == '\n') {
                lineCount++;
                if (lineCount >= maxLines) {
                    sb.append("\n... [truncated after ").append(maxLines).append(" lines]");
                    break;
                }
            }
        }
        return sb.toString();
    }
}

