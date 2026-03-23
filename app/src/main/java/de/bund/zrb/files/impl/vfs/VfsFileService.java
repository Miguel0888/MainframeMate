package de.bund.zrb.files.impl.vfs;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceErrorCode;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.api.FileWriteResult;
import de.bund.zrb.files.auth.ConnectionId;
import de.bund.zrb.files.auth.Credentials;
import de.bund.zrb.files.auth.CredentialsProvider;
import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.files.model.FilePayload;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ndv.NdvObjectInfo;
import de.bund.zrb.ndv.NdvService;
import de.bund.zrb.files.impl.vfs.ndv.NdvFileProvider;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.auth.StaticUserAuthenticator;
import org.apache.commons.vfs2.impl.DefaultFileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.ftp.FtpFileSystemConfigBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Unified FileService implementation backed entirely by Apache Commons VFS2.
 * <p>
 * Supports ALL backends through VFS URI schemes:
 * <ul>
 *   <li>{@code file://} — local file system</li>
 *   <li>{@code ftp://} — FTP (including MVS/z/OS hosts)</li>
 *   <li>{@code ndv://} — Natural Development Server (via custom VFS provider)</li>
 * </ul>
 * <p>
 * This replaces the previous separate implementations (VfsLocalFileService,
 * CommonsNetFtpFileService, NdvFileService) with a single VFS-based service.
 */
public class VfsFileService implements FileService {

    private static final Logger LOG = Logger.getLogger(VfsFileService.class.getName());

    private final FileSystemManager manager;
    private final String baseUri;
    private final FileSystemOptions fsOptions;
    private final FileServiceException initError;

    // Metadata for callers that need FTP system info
    private boolean mvsMode;
    private String systemType;

    // ═══════════════════════════════════════════════════════════════════
    //  Factory methods
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Create a VFS FileService for the local file system.
     */
    public static VfsFileService forLocal() {
        return new VfsFileService("file:///", null, null);
    }

    /**
     * Create a VFS FileService for the local file system with a base root.
     */
    public static VfsFileService forLocal(Path baseRoot) {
        String uri = baseRoot != null
                ? baseRoot.toAbsolutePath().normalize().toUri().toString()
                : "file:///";
        return new VfsFileService(uri, null, null);
    }

    /**
     * Create a VFS FileService for FTP.
     */
    public static VfsFileService forFtp(String host, String user, String password) throws FileServiceException {
        Settings settings = SettingsHelper.load();

        // Build FTP URI WITHOUT credentials — auth is handled via FileSystemOptions
        // This avoids URI-encoding issues with special chars in passwords (e.g. # → %23)
        String ftpUri = "ftp://" + host + "/";

        FileSystemOptions opts = buildFtpOptions(settings);

        // Pass credentials via StaticUserAuthenticator (raw, no encoding needed)
        StaticUserAuthenticator auth = new StaticUserAuthenticator(null, user, password);
        DefaultFileSystemConfigBuilder.getInstance().setUserAuthenticator(opts, auth);

        VfsFileService service = new VfsFileService(ftpUri, opts, null);
        service.detectSystemType();
        return service;
    }

    /**
     * Create a VFS FileService for FTP using connection credentials.
     */
    public static VfsFileService forFtp(ConnectionId connectionId, CredentialsProvider credentialsProvider)
            throws FileServiceException {
        if (connectionId == null) {
            throw new FileServiceException(FileServiceErrorCode.AUTH_FAILED, "Missing ConnectionId");
        }
        if (credentialsProvider == null) {
            throw new FileServiceException(FileServiceErrorCode.AUTH_FAILED, "Missing CredentialsProvider");
        }

        Credentials credentials = credentialsProvider.resolve(connectionId)
                .orElseThrow(new java.util.function.Supplier<FileServiceException>() {
                    @Override
                    public FileServiceException get() {
                        return new FileServiceException(FileServiceErrorCode.AUTH_FAILED, "No credentials available");
                    }
                });

        return forFtp(credentials.getHost(), credentials.getUsername(), credentials.getPassword());
    }

    /**
     * Create a VFS FileService for NDV (Natural Development Server).
     * Registers the custom NDV VFS provider and builds an ndv:// URI.
     */
    public static VfsFileService forNdv(NdvService service, String library, NdvObjectInfo objectInfo)
            throws FileServiceException {
        try {
            // Ensure NDV provider is registered
            NdvFileProvider.ensureRegistered(service, library, objectInfo);

            // Build NDV URI: ndv://library/objectType/objectName
            String ndvUri = "ndv://" + encodeUriComponent(library) + "/"
                    + encodeUriComponent(objectInfo.getTypeExtension()) + "/"
                    + encodeUriComponent(objectInfo.getName());

            return new VfsFileService(ndvUri, null, null);
        } catch (Exception e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "Failed to create NDV VFS service: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Constructor
    // ═══════════════════════════════════════════════════════════════════

    private VfsFileService(String baseUri, FileSystemOptions fsOptions, FileServiceException initError) {
        FileSystemManager resolvedManager = null;
        FileServiceException resolvedError = initError;

        try {
            resolvedManager = VFS.getManager();
        } catch (FileSystemException e) {
            if (resolvedError == null) {
                resolvedError = new FileServiceException(FileServiceErrorCode.IO_ERROR,
                        "VFS manager init failed", e);
            }
        }

        this.manager = resolvedManager;
        this.baseUri = baseUri != null ? baseUri : "file:///";
        this.fsOptions = fsOptions != null ? fsOptions : new FileSystemOptions();
        this.initError = resolvedError;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FileService implementation — all via VFS
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public List<FileNode> list(String absolutePath) throws FileServiceException {
        FileObject root = resolve(absolutePath);
        try {
            if (!root.exists()) {
                return Collections.emptyList();
            }
            FileType rootType = root.getType();
            if (rootType != FileType.FOLDER && rootType != FileType.FILE_OR_FOLDER) {
                throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                        "Path is not a directory: " + sanitizeUri(root.getName().getURI()));
            }

            FileObject[] children = root.getChildren();
            List<FileNode> nodes = new ArrayList<FileNode>(children.length);
            for (FileObject child : children) {
                try {
                    FileType type = child.getType();
                    boolean isDir = type == FileType.FOLDER || type == FileType.FILE_OR_FOLDER;
                    long size = isDir ? 0L : child.getContent().getSize();
                    long lastModified = child.getContent().getLastModifiedTime();
                    String name = child.getName().getBaseName();
                    String path = sanitizeUri(child.getName().getURI());

                    // For local files, convert back to platform path
                    if (path.startsWith("file://")) {
                        try {
                            path = new File(new URI(path)).getAbsolutePath();
                        } catch (URISyntaxException ignore) {
                            // keep URI format
                        }
                    }

                    nodes.add(new FileNode(name, path, isDir, size, lastModified));
                } finally {
                    tryClose(child);
                }
            }
            return nodes;
        } catch (FileSystemException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "VFS list failed: " + e.getMessage(), e);
        } finally {
            tryClose(root);
        }
    }

    @Override
    public FilePayload readFile(String absolutePath) throws FileServiceException {
        FileObject file = resolve(absolutePath);
        try {
            if (!file.exists()) {
                throw new FileServiceException(FileServiceErrorCode.NOT_FOUND,
                        "File not found: " + sanitizeUri(file.getName().getURI()));
            }
            if (file.getType() == FileType.FOLDER) {
                throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                        "Path is a directory: " + sanitizeUri(file.getName().getURI()));
            }

            byte[] bytes = readAllBytes(file);
            Charset charset = determineCharset();
            return FilePayload.fromBytes(bytes, charset, false);
        } catch (FileSystemException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "VFS read failed: " + e.getMessage(), e);
        } finally {
            tryClose(file);
        }
    }

    @Override
    public FilePayload readFileBinary(String absolutePath) throws FileServiceException {
        FileObject file = resolve(absolutePath);
        try {
            if (!file.exists()) {
                throw new FileServiceException(FileServiceErrorCode.NOT_FOUND,
                        "File not found (binary): " + sanitizeUri(file.getName().getURI()));
            }
            if (file.getType() == FileType.FOLDER) {
                throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                        "Path is a directory: " + sanitizeUri(file.getName().getURI()));
            }

            byte[] bytes = readAllBytes(file);
            return FilePayload.fromBytes(bytes, null, false);
        } catch (FileSystemException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "VFS binary read failed: " + e.getMessage(), e);
        } finally {
            tryClose(file);
        }
    }

    @Override
    public void writeFile(String absolutePath, FilePayload payload) throws FileServiceException {
        if (payload == null) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Payload is required");
        }

        FileObject file = resolve(absolutePath);
        try (OutputStream out = file.getContent().getOutputStream()) {
            out.write(payload.getBytes());
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "VFS write failed: " + e.getMessage(), e);
        } finally {
            tryClose(file);
        }
    }

    @Override
    public void writeFileBinary(String absolutePath, FilePayload payload) throws FileServiceException {
        // VFS handles binary transparently — same as writeFile
        writeFile(absolutePath, payload);
    }

    @Override
    public FileWriteResult writeIfUnchanged(String absolutePath, FilePayload payload, String expectedHash)
            throws FileServiceException {
        if (expectedHash == null || expectedHash.isEmpty()) {
            writeFile(absolutePath, payload);
            return FileWriteResult.success();
        }

        FilePayload current = readFile(absolutePath);
        if (!expectedHash.equals(current.getHash())) {
            return FileWriteResult.conflict(current);
        }

        writeFile(absolutePath, payload);
        return FileWriteResult.success();
    }

    @Override
    public boolean delete(String absolutePath) throws FileServiceException {
        FileObject file = resolve(absolutePath);
        try {
            return file.delete();
        } catch (FileSystemException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "VFS delete failed: " + e.getMessage(), e);
        } finally {
            tryClose(file);
        }
    }

    @Override
    public boolean createDirectory(String absolutePath) throws FileServiceException {
        FileObject file = resolve(absolutePath);
        try {
            file.createFolder();
            return true;
        } catch (FileSystemException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "VFS create directory failed: " + e.getMessage(), e);
        } finally {
            tryClose(file);
        }
    }

    @Override
    public void close() throws FileServiceException {
        // VFS manager is shared — individual file systems are closed when no longer referenced
        // For FTP, VFS manages connection pooling internally
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Public accessors (metadata)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Whether this FTP connection is to an MVS/z/OS system.
     */
    public boolean isMvsMode() {
        return mvsMode;
    }

    /**
     * The FTP system type string (e.g. "MVS is the operating system...").
     */
    public String getSystemType() {
        return systemType;
    }

    /**
     * The base VFS URI of this service.
     */
    public String getBaseUri() {
        return baseUri;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Internal helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Resolve a path to a VFS FileObject.
     * Handles both absolute paths and paths relative to the base URI.
     */
    private FileObject resolve(String path) throws FileServiceException {
        if (initError != null) {
            throw initError;
        }
        try {
            if (path == null || path.trim().isEmpty()) {
                return manager.resolveFile(baseUri, fsOptions);
            }

            String trimmed = path.trim();

            // Already a VFS URI (ftp://, file://, ndv://, etc.)
            if (isVfsUri(trimmed)) {
                return manager.resolveFile(trimmed, fsOptions);
            }

            // For local base: convert platform path to file:// URI
            if (baseUri.startsWith("file://")) {
                return resolveLocal(trimmed);
            }

            // For FTP base: append path to base URI
            if (baseUri.startsWith("ftp://")) {
                return resolveFtp(trimmed);
            }

            // For NDV base: the path IS the object reference
            if (baseUri.startsWith("ndv://")) {
                return manager.resolveFile(baseUri, fsOptions);
            }

            // Fallback: try as-is relative to base
            String combined = baseUri.endsWith("/") ? baseUri + trimmed : baseUri + "/" + trimmed;
            return manager.resolveFile(combined, fsOptions);
        } catch (FileSystemException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "VFS resolve failed for: " + path, e);
        }
    }

    private FileObject resolveLocal(String path) throws FileServiceException {
        try {
            if (path.toLowerCase().startsWith("file:")) {
                return manager.resolveFile(path, fsOptions);
            }
            File file = new File(path);
            if (!file.isAbsolute() && baseUri.startsWith("file://")) {
                // Resolve relative to base
                try {
                    File base = new File(new URI(baseUri));
                    file = new File(base, path);
                } catch (URISyntaxException ignore) {
                    // fall through
                }
            }
            return manager.resolveFile(file.toURI().toString(), fsOptions);
        } catch (FileSystemException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "VFS local resolve failed for: " + path, e);
        }
    }

    private FileObject resolveFtp(String path) throws FileServiceException {
        try {
            // Strip any existing leading slash for consistency
            String ftpPath = path.startsWith("/") ? path : "/" + path;

            // Extract base without trailing slash, then append path
            String base = baseUri.endsWith("/") ? baseUri.substring(0, baseUri.length() - 1) : baseUri;
            return manager.resolveFile(base + ftpPath, fsOptions);
        } catch (FileSystemException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "VFS FTP resolve failed for: " + path, e);
        }
    }

    private boolean isVfsUri(String path) {
        return path.contains("://");
    }

    /**
     * Detect the FTP system type after connection.
     */
    private void detectSystemType() {
        if (!baseUri.startsWith("ftp://")) return;

        try {
            FileObject root = manager.resolveFile(baseUri, fsOptions);
            // VFS will connect when resolving — the system type is detected internally
            // We need to access it through VFS's filesystem info
            try {
                org.apache.commons.vfs2.FileSystem fs = root.getFileSystem();
                // Try to get the attribute from the file system
                Object sysType = fs.getAttribute("systemType");
                if (sysType != null) {
                    systemType = sysType.toString();
                    mvsMode = systemType.toUpperCase().contains("MVS");
                }
            } catch (Exception e) {
                // VFS may not expose system type as attribute — try listing root as detection
                LOG.log(Level.FINE, "Could not detect FTP system type via attribute", e);
            }
            tryClose(root);
        } catch (FileSystemException e) {
            LOG.log(Level.WARNING, "FTP system type detection failed", e);
        }
    }

    /**
     * Build FTP FileSystemOptions from Settings.
     */
    private static FileSystemOptions buildFtpOptions(Settings settings) {
        FileSystemOptions opts = new FileSystemOptions();
        try {
            FtpFileSystemConfigBuilder builder = FtpFileSystemConfigBuilder.getInstance();

            // Passive mode (standard for mainframe FTP)
            builder.setPassiveMode(opts, true);

            // Timeouts
            if (settings.ftpConnectTimeoutMs > 0) {
                builder.setConnectTimeout(opts, Duration.ofMillis(settings.ftpConnectTimeoutMs));
            }
            if (settings.ftpDataTimeoutMs > 0) {
                builder.setDataTimeout(opts, Duration.ofMillis(settings.ftpDataTimeoutMs));
            }
            if (settings.ftpControlTimeoutMs > 0) {
                builder.setSoTimeout(opts, Duration.ofMillis(settings.ftpControlTimeoutMs));
            }

            // Control encoding
            if (settings.encoding != null) {
                builder.setControlEncoding(opts, settings.encoding);
            }

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to configure FTP options", e);
        }
        return opts;
    }

    private Charset determineCharset() {
        if (baseUri.startsWith("ftp://")) {
            Settings settings = SettingsHelper.load();
            String encoding = settings.encoding != null ? settings.encoding : "UTF-8";
            try {
                return Charset.forName(encoding);
            } catch (Exception e) {
                return Charset.defaultCharset();
            }
        }
        return Charset.defaultCharset();
    }

    private byte[] readAllBytes(FileObject file) throws FileServiceException {
        try (InputStream in = file.getContent().getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "VFS read bytes failed", e);
        }
    }

    private void tryClose(FileObject file) {
        if (file == null) return;
        try {
            file.close();
        } catch (FileSystemException ignore) {
            // ignore close failures
        }
    }

    /**
     * URL-encode a URI component (user/password).
     */
    private static String encodeUriComponent(String value) {
        if (value == null) return "";
        try {
            return java.net.URLEncoder.encode(value, "UTF-8")
                    .replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            return value; // UTF-8 is always available
        }
    }

    /**
     * Strip credentials (user:password@) from a URI to prevent leaking
     * sensitive information in error messages and returned file paths.
     * Example: "ftp://user:pass@host/path" → "ftp://host/path"
     */
    static String sanitizeUri(String uri) {
        if (uri == null) return null;
        // Match scheme://userinfo@host  →  scheme://host
        int schemeEnd = uri.indexOf("://");
        if (schemeEnd < 0) return uri;
        int authStart = schemeEnd + 3;
        int atSign = uri.indexOf('@', authStart);
        if (atSign < 0) return uri; // no credentials present
        // Find where the host-part starts (after the @)
        return uri.substring(0, authStart) + uri.substring(atSign + 1);
    }
}

