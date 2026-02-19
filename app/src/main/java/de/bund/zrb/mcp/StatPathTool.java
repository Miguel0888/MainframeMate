package de.bund.zrb.mcp;

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

import java.io.File;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Tool that returns metadata about a file or directory without reading its contents.
 * Useful for existence checks, type detection, size limits, encoding/MIME hints.
 *
 * accessType: READ (always)
 */
public class StatPathTool implements McpTool {

    private final MainframeContext context;

    // Path kinds
    public enum PathKind {
        FILE("file"),
        DIRECTORY("directory"),
        SYMLINK("symlink"),
        UNKNOWN("unknown");

        private final String value;

        PathKind(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public StatPathTool(MainframeContext context) {
        this.context = context;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<>();
        properties.put("path", new ToolSpec.Property("string", "Pfad zu Datei oder Verzeichnis (lokal oder FTP)"));
        properties.put("followSymlinks", new ToolSpec.Property("boolean", "Symlinks folgen (default: true)"));
        properties.put("detectMime", new ToolSpec.Property("boolean", "MIME-Typ ermitteln (default: true)"));
        properties.put("detectTextEncoding", new ToolSpec.Property("boolean", "Text-Encoding erkennen (default: false)"));
        properties.put("maxProbeBytes", new ToolSpec.Property("integer", "Max. Bytes für MIME/Encoding-Erkennung (default: 4096)"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(properties, Collections.singletonList("path"));

        Map<String, Object> example = new LinkedHashMap<>();
        example.put("path", "C:\\TEST\\test-data.txt");

        return new ToolSpec(
                "stat_path",
                "Liefert Metadaten zu einem Pfad (Datei oder Verzeichnis), ohne Inhalte zu lesen. " +
                "Gibt Existenz, Typ (file/directory/symlink), Größe, Zeitstempel, Berechtigungen, " +
                "MIME-Typ und Encoding-Hinweise zurück. " +
                "Nützlich zur Vorabprüfung vor read_file oder search_file. " +
                "Für FTP-Pfade muss vorher eine Verbindung bestehen.",
                inputSchema,
                example
        );
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        JsonObject response = new JsonObject();
        response.addProperty("toolName", "stat_path");

        try {
            // Validate required fields
            if (input == null || !input.has("path") || input.get("path").isJsonNull()) {
                return errorResponse("Pflichtfeld fehlt: path", "ToolExecutionError", resultVar);
            }

            // Parse input parameters
            String path = input.get("path").getAsString();
            boolean followSymlinks = getOptionalBoolean(input, "followSymlinks", true);
            boolean detectMime = getOptionalBoolean(input, "detectMime", true);
            boolean detectTextEncoding = getOptionalBoolean(input, "detectTextEncoding", false);
            int maxProbeBytes = getOptionalInt(input, "maxProbeBytes", 4096);

            response.addProperty("path", path);

            // Try to resolve as local path first
            if (isLocalPath(path)) {
                return statLocalPath(path, followSymlinks, detectMime, detectTextEncoding, maxProbeBytes, resultVar);
            } else {
                // FTP or other remote path
                return statRemotePath(path, detectMime, detectTextEncoding, maxProbeBytes, resultVar);
            }

        } catch (Exception e) {
            return errorResponse(e.getMessage() != null ? e.getMessage() : e.getClass().getName(),
                    "ToolExecutionError", resultVar);
        }
    }

    private McpToolResponse statLocalPath(String path, boolean followSymlinks, boolean detectMime,
                                           boolean detectTextEncoding, int maxProbeBytes, String resultVar) {
        JsonObject response = new JsonObject();
        response.addProperty("status", "success");
        response.addProperty("toolName", "stat_path");
        response.addProperty("path", path);
        response.addProperty("local", true);

        File file = new File(path);
        Path filePath = file.toPath();

        // Check existence
        boolean exists = file.exists();
        response.addProperty("exists", exists);

        if (!exists) {
            response.addProperty("kind", PathKind.UNKNOWN.getValue());
            return new McpToolResponse(response, resultVar, null);
        }

        // Determine kind
        PathKind kind = determineKind(filePath, followSymlinks);
        response.addProperty("kind", kind.getValue());

        // Get file attributes
        try {
            LinkOption[] linkOptions = followSymlinks ? new LinkOption[0] : new LinkOption[]{LinkOption.NOFOLLOW_LINKS};
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class, linkOptions);

            response.addProperty("lastModified", attrs.lastModifiedTime().toMillis());

            if (kind == PathKind.FILE || kind == PathKind.SYMLINK) {
                response.addProperty("sizeBytes", attrs.size());
            }
        } catch (Exception e) {
            // Best effort - continue without attributes
        }

        // Permissions (best effort)
        response.addProperty("readable", file.canRead());
        response.addProperty("writable", file.canWrite());

        // For directories, count entries (hint only)
        if (kind == PathKind.DIRECTORY) {
            try {
                String[] entries = file.list();
                if (entries != null) {
                    response.addProperty("entryCountHint", entries.length);
                }
            } catch (Exception e) {
                // Best effort - skip entry count
            }
        }

        // For files, detect MIME and encoding
        if (kind == PathKind.FILE && file.canRead()) {
            if (detectMime || detectTextEncoding) {
                addMimeAndEncodingInfo(response, file, detectMime, detectTextEncoding, maxProbeBytes);
            }
        }

        return new McpToolResponse(response, resultVar, null);
    }

    private McpToolResponse statRemotePath(String path, boolean detectMime, boolean detectTextEncoding,
                                            int maxProbeBytes, String resultVar) {
        JsonObject response = new JsonObject();
        response.addProperty("toolName", "stat_path");
        response.addProperty("path", path);
        response.addProperty("local", false);

        try {
            // Resolve the resource using existing infrastructure
            VirtualResourceResolver resolver = new VirtualResourceResolver();
            VirtualResource resource = resolver.resolve(path);

            // Create FileService
            CredentialsProvider credentialsProvider = new LoginManagerCredentialsProvider(
                    (host, user) -> LoginManager.getInstance().getCachedPassword(host, user)
            );

            try (FileService fs = new FileServiceFactory().create(resource, credentialsProvider)) {
                response.addProperty("status", "success");
                response.addProperty("exists", true);

                if (resource.getKind() == VirtualResourceKind.DIRECTORY) {
                    response.addProperty("kind", PathKind.DIRECTORY.getValue());

                    // Try to get entry count
                    try {
                        List<FileNode> entries = fs.list(resource.getResolvedPath());
                        response.addProperty("entryCountHint", entries.size());
                    } catch (Exception e) {
                        // Best effort
                    }

                    response.addProperty("readable", true);
                    response.addProperty("writable", false); // Conservative for FTP

                } else {
                    response.addProperty("kind", PathKind.FILE.getValue());

                    // Try to get file info
                    try {
                        FilePayload payload = fs.readFile(resource.getResolvedPath());
                        response.addProperty("sizeBytes", payload.getBytes().length);
                        response.addProperty("readable", true);
                        response.addProperty("writable", false);

                        // MIME and encoding detection
                        if (detectMime || detectTextEncoding) {
                            byte[] bytes = payload.getBytes();
                            int probeLength = Math.min(bytes.length, maxProbeBytes);
                            byte[] probe = new byte[probeLength];
                            System.arraycopy(bytes, 0, probe, 0, probeLength);

                            if (detectMime) {
                                String mimeType = guessMimeType(path, probe);
                                if (mimeType != null) {
                                    response.addProperty("mimeType", mimeType);
                                }
                                response.addProperty("isBinary", isBinary(probe));
                            }

                            if (detectTextEncoding && !isBinary(probe)) {
                                String encoding = detectEncoding(probe);
                                if (encoding != null) {
                                    response.addProperty("encodingHint", encoding);
                                }
                            }
                        }
                    } catch (Exception e) {
                        // File exists but can't read - still success
                        response.addProperty("readable", false);
                    }
                }
            }

            return new McpToolResponse(response, resultVar, null);

        } catch (FileServiceException e) {
            // Check if it's a "not found" error
            if (e.getErrorCode() != null &&
                e.getErrorCode().name().contains("NOT_FOUND")) {
                response.addProperty("status", "success");
                response.addProperty("exists", false);
                response.addProperty("kind", PathKind.UNKNOWN.getValue());
                return new McpToolResponse(response, resultVar, null);
            }
            return errorResponse(e.getMessage() != null ? e.getMessage() : e.getClass().getName(),
                    "FileServiceError", resultVar, e.getErrorCode() != null ? e.getErrorCode().name() : null);
        } catch (Exception e) {
            return errorResponse(e.getMessage() != null ? e.getMessage() : e.getClass().getName(),
                    "ToolExecutionError", resultVar);
        }
    }

    private PathKind determineKind(Path path, boolean followSymlinks) {
        try {
            if (!followSymlinks && Files.isSymbolicLink(path)) {
                return PathKind.SYMLINK;
            }
            if (Files.isDirectory(path)) {
                return PathKind.DIRECTORY;
            }
            if (Files.isRegularFile(path)) {
                return PathKind.FILE;
            }
        } catch (Exception e) {
            // Fall through to unknown
        }
        return PathKind.UNKNOWN;
    }

    private void addMimeAndEncodingInfo(JsonObject response, File file, boolean detectMime,
                                         boolean detectTextEncoding, int maxProbeBytes) {
        byte[] probe = null;

        // Read probe bytes
        try {
            long fileSize = file.length();
            int probeLength = (int) Math.min(fileSize, maxProbeBytes);
            probe = new byte[probeLength];

            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                int read = fis.read(probe);
                if (read < probeLength) {
                    byte[] actual = new byte[read];
                    System.arraycopy(probe, 0, actual, 0, read);
                    probe = actual;
                }
            }
        } catch (Exception e) {
            // Can't read probe - skip MIME/encoding detection
            return;
        }

        if (detectMime) {
            String mimeType = guessMimeType(file.getName(), probe);
            if (mimeType != null) {
                response.addProperty("mimeType", mimeType);
            }
            response.addProperty("isBinary", isBinary(probe));
        }

        if (detectTextEncoding && !isBinary(probe)) {
            String encoding = detectEncoding(probe);
            if (encoding != null) {
                response.addProperty("encodingHint", encoding);
            }
        }
    }

    private String guessMimeType(String filename, byte[] probe) {
        // First try by filename
        String mimeByName = URLConnection.guessContentTypeFromName(filename);
        if (mimeByName != null) {
            return mimeByName;
        }

        // Try by content
        try {
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(probe);
            String mimeByContent = URLConnection.guessContentTypeFromStream(bais);
            if (mimeByContent != null) {
                return mimeByContent;
            }
        } catch (Exception e) {
            // Ignore
        }

        // Fallback heuristics based on extension
        String lowerName = filename.toLowerCase();
        if (lowerName.endsWith(".txt")) return "text/plain";
        if (lowerName.endsWith(".json")) return "application/json";
        if (lowerName.endsWith(".xml")) return "application/xml";
        if (lowerName.endsWith(".html") || lowerName.endsWith(".htm")) return "text/html";
        if (lowerName.endsWith(".css")) return "text/css";
        if (lowerName.endsWith(".js")) return "application/javascript";
        if (lowerName.endsWith(".java")) return "text/x-java-source";
        if (lowerName.endsWith(".py")) return "text/x-python";
        if (lowerName.endsWith(".md")) return "text/markdown";
        if (lowerName.endsWith(".csv")) return "text/csv";
        if (lowerName.endsWith(".properties")) return "text/x-java-properties";
        if (lowerName.endsWith(".yaml") || lowerName.endsWith(".yml")) return "text/yaml";
        if (lowerName.endsWith(".pdf")) return "application/pdf";
        if (lowerName.endsWith(".zip")) return "application/zip";
        if (lowerName.endsWith(".jar")) return "application/java-archive";
        if (lowerName.endsWith(".png")) return "image/png";
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) return "image/jpeg";
        if (lowerName.endsWith(".gif")) return "image/gif";
        if (lowerName.endsWith(".bmp")) return "image/bmp";
        if (lowerName.endsWith(".ico")) return "image/x-icon";
        if (lowerName.endsWith(".svg")) return "image/svg+xml";

        // Check if binary
        if (isBinary(probe)) {
            return "application/octet-stream";
        }

        return "text/plain"; // Default for text files
    }

    private boolean isBinary(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        // Check for NUL bytes (common binary indicator)
        for (byte b : bytes) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    private String detectEncoding(byte[] probe) {
        if (probe == null || probe.length == 0) {
            return null;
        }

        // Check for BOM (Byte Order Mark)
        if (probe.length >= 3 && probe[0] == (byte) 0xEF && probe[1] == (byte) 0xBB && probe[2] == (byte) 0xBF) {
            return "UTF-8";
        }
        if (probe.length >= 2 && probe[0] == (byte) 0xFE && probe[1] == (byte) 0xFF) {
            return "UTF-16BE";
        }
        if (probe.length >= 2 && probe[0] == (byte) 0xFF && probe[1] == (byte) 0xFE) {
            return "UTF-16LE";
        }
        if (probe.length >= 4 && probe[0] == 0 && probe[1] == 0 && probe[2] == (byte) 0xFE && probe[3] == (byte) 0xFF) {
            return "UTF-32BE";
        }
        if (probe.length >= 4 && probe[0] == (byte) 0xFF && probe[1] == (byte) 0xFE && probe[2] == 0 && probe[3] == 0) {
            return "UTF-32LE";
        }

        // Try to decode as UTF-8
        try {
            String decoded = new String(probe, StandardCharsets.UTF_8);
            byte[] reencoded = decoded.getBytes(StandardCharsets.UTF_8);
            // If re-encoding gives different length, it might not be valid UTF-8
            // But this is a simple heuristic
            if (Math.abs(reencoded.length - probe.length) <= probe.length * 0.1) {
                // Check for UTF-8 multi-byte sequences
                boolean hasHighBytes = false;
                for (byte b : probe) {
                    if ((b & 0x80) != 0) {
                        hasHighBytes = true;
                        break;
                    }
                }
                if (hasHighBytes) {
                    return "UTF-8";
                }
                return "ASCII"; // Pure ASCII is valid UTF-8 subset
            }
        } catch (Exception e) {
            // Not valid UTF-8
        }

        // Try ISO-8859-1 (always succeeds for any byte sequence)
        return "ISO-8859-1";
    }

    private boolean isLocalPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        // Check for Windows absolute path (C:\...) or Unix absolute path (/...)
        if (path.length() >= 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':') {
            return true;
        }
        if (path.startsWith("/")) {
            return true;
        }
        if (path.startsWith("file:")) {
            return true;
        }
        // Check for UNC path (\\server\...)
        if (path.startsWith("\\\\")) {
            return true;
        }
        return false;
    }

    // Helper methods for parsing input
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

    private McpToolResponse errorResponse(String message, String errorType, String resultVar) {
        return errorResponse(message, errorType, resultVar, null);
    }

    private McpToolResponse errorResponse(String message, String errorType, String resultVar, String errorCode) {
        JsonObject response = new JsonObject();
        response.addProperty("status", "error");
        response.addProperty("toolName", "stat_path");
        response.addProperty("errorType", errorType);
        response.addProperty("message", message);
        if (errorCode != null) {
            response.addProperty("errorCode", errorCode);
        }
        response.addProperty("hint", "Prüfe Berechtigungen oder ob eine FTP-Verbindung besteht.");
        return new McpToolResponse(response, resultVar, null);
    }
}

