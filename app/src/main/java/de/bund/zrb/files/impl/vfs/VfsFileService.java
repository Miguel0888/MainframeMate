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

import org.apache.commons.net.ftp.FTPClient;
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
 * Unified FileService backed entirely by Apache Commons VFS2.
 * <p>
 * Every resource — local, FTP, NDV — is accessed through the same
 * {@link org.apache.commons.vfs2.FileObject} API.  No backend-specific
 * operation code lives here.
 * <ul>
 *   <li>{@code file://} — local file system</li>
 *   <li>{@code ftp://}  — FTP (credentials via {@link StaticUserAuthenticator})</li>
 *   <li>{@code ndv://}  — Natural Development Server (custom VFS provider)</li>
 * </ul>
 * <p>
 * MVS-specific browsing (qualifier navigation, dual NLST, record structure)
 * is handled by {@code MvsConnectionTab / MvsFtpClient / MvsListingService}
 * at the UI layer — not here.
 */
public class VfsFileService implements FileService {

    private static final Logger LOG = Logger.getLogger(VfsFileService.class.getName());

    private final FileSystemManager manager;
    private final String baseUri;
    private final FileSystemOptions fsOptions;
    private final FileServiceException initError;

    // Metadata detected once during forFtp()
    private boolean mvsMode;
    private String systemType;

    // ═══════════════════════════════════════════════════════════════════
    //  Factory methods
    // ═══════════════════════════════════════════════════════════════════

    /** Local file system. */
    public static VfsFileService forLocal() {
        return new VfsFileService("file:///", null, null);
    }

    /** Local file system with a base root. */
    public static VfsFileService forLocal(Path baseRoot) {
        String uri = baseRoot != null
                ? baseRoot.toAbsolutePath().normalize().toUri().toString()
                : "file:///";
        return new VfsFileService(uri, null, null);
    }

    /**
     * FTP via VFS2 with {@link StaticUserAuthenticator}.
     * <p>
     * Credentials are passed through {@link FileSystemOptions}, never
     * embedded in the URI — so special characters (#, @, etc.) are safe.
     * <p>
     * MVS mode is detected once via a lightweight SYST probe and exposed
     * through {@link #isMvsMode()} so the UI layer can route to the
     * appropriate connection tab.
     */
    public static VfsFileService forFtp(String host, String user, String password) throws FileServiceException {
        Settings settings = SettingsHelper.load();
        String ftpUri = "ftp://" + host + "/";

        FileSystemOptions opts = buildFtpOptions(settings);
        StaticUserAuthenticator auth = new StaticUserAuthenticator(null, user, password);
        DefaultFileSystemConfigBuilder.getInstance().setUserAuthenticator(opts, auth);

        VfsFileService service = new VfsFileService(ftpUri, opts, null);

        // One-shot SYST detection (lightweight — connect, SYST, disconnect)
        service.detectSystemType(host, user, password);

        return service;
    }

    /** FTP via connection credentials. */
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

    /** NDV via custom VFS provider. */
    public static VfsFileService forNdv(NdvService service, String library, NdvObjectInfo objectInfo)
            throws FileServiceException {
        try {
            NdvFileProvider.ensureRegistered(service, library, objectInfo);
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
    //  FileService implementation — pure VFS2
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

                    long size = 0L;
                    long lastModified = 0L;
                    try { if (!isDir) size = child.getContent().getSize(); }
                    catch (Exception ignore) { /* unavailable on some servers */ }
                    try { lastModified = child.getContent().getLastModifiedTime(); }
                    catch (Exception ignore) { /* unavailable on MVS FTP */ }

                    String name = child.getName().getBaseName();
                    String path = sanitizeUri(child.getName().getURI());

                    // Convert file:// back to platform path
                    if (path.startsWith("file://")) {
                        try { path = new File(new URI(path)).getAbsolutePath(); }
                        catch (URISyntaxException ignore) { }
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
        // VFS manager is a shared singleton — nothing to close
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Public accessors
    // ═══════════════════════════════════════════════════════════════════

    public boolean isMvsMode()     { return mvsMode; }
    public String  getSystemType() { return systemType; }
    public String  getBaseUri()    { return baseUri; }

    // ═══════════════════════════════════════════════════════════════════
    //  Internal — VFS resolution
    // ═══════════════════════════════════════════════════════════════════

    private FileObject resolve(String path) throws FileServiceException {
        if (initError != null) throw initError;
        try {
            if (path == null || path.trim().isEmpty()) {
                return manager.resolveFile(baseUri, fsOptions);
            }
            String trimmed = path.trim();

            if (trimmed.contains("://")) {
                return manager.resolveFile(trimmed, fsOptions);
            }
            if (baseUri.startsWith("file://")) {
                return resolveLocal(trimmed);
            }
            if (baseUri.startsWith("ftp://")) {
                return resolveFtp(trimmed);
            }
            if (baseUri.startsWith("ndv://")) {
                return manager.resolveFile(baseUri, fsOptions);
            }
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
                try {
                    File base = new File(new URI(baseUri));
                    file = new File(base, path);
                } catch (URISyntaxException ignore) { }
            }
            return manager.resolveFile(file.toURI().toString(), fsOptions);
        } catch (FileSystemException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "VFS local resolve failed for: " + path, e);
        }
    }

    private FileObject resolveFtp(String path) throws FileServiceException {
        try {
            String ftpPath = path.startsWith("/") ? path : "/" + path;
            String base = baseUri.endsWith("/")
                    ? baseUri.substring(0, baseUri.length() - 1)
                    : baseUri;
            return manager.resolveFile(base + ftpPath, fsOptions);
        } catch (FileSystemException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "VFS FTP resolve failed for: " + path, e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Internal — FTP system type detection
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Lightweight one-shot SYST probe: connect, read system type, disconnect.
     * Only used once during {@link #forFtp} to detect MVS mode.
     */
    private void detectSystemType(String host, String user, String password) {
        FTPClient probe = new FTPClient();
        try {
            probe.connect(host);
            if (probe.login(user, password)) {
                systemType = probe.getSystemType();
                mvsMode = systemType != null && systemType.toUpperCase().contains("MVS");
                if (mvsMode) {
                    System.out.println("[VFS-FTP] Detected MVS/zOS: " + systemType);
                }
                probe.logout();
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "SYST probe failed for " + host, e);
        } finally {
            try { probe.disconnect(); } catch (IOException ignore) { }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Internal — helpers
    // ═══════════════════════════════════════════════════════════════════

    private static FileSystemOptions buildFtpOptions(Settings settings) {
        FileSystemOptions opts = new FileSystemOptions();
        try {
            FtpFileSystemConfigBuilder b = FtpFileSystemConfigBuilder.getInstance();
            b.setPassiveMode(opts, true);
            if (settings.ftpConnectTimeoutMs > 0)
                b.setConnectTimeout(opts, Duration.ofMillis(settings.ftpConnectTimeoutMs));
            if (settings.ftpDataTimeoutMs > 0)
                b.setDataTimeout(opts, Duration.ofMillis(settings.ftpDataTimeoutMs));
            if (settings.ftpControlTimeoutMs > 0)
                b.setSoTimeout(opts, Duration.ofMillis(settings.ftpControlTimeoutMs));
            if (settings.encoding != null)
                b.setControlEncoding(opts, settings.encoding);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to configure FTP options", e);
        }
        return opts;
    }

    private Charset determineCharset() {
        if (baseUri.startsWith("ftp://")) {
            Settings settings = SettingsHelper.load();
            String enc = settings.encoding != null ? settings.encoding : "UTF-8";
            try { return Charset.forName(enc); } catch (Exception e) { return Charset.defaultCharset(); }
        }
        return Charset.defaultCharset();
    }

    private byte[] readAllBytes(FileObject file) throws FileServiceException {
        try (InputStream in = file.getContent().getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "VFS read bytes failed", e);
        }
    }

    private void tryClose(FileObject file) {
        if (file == null) return;
        try { file.close(); } catch (FileSystemException ignore) { }
    }

    private static String encodeUriComponent(String value) {
        if (value == null) return "";
        try { return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
        catch (java.io.UnsupportedEncodingException e) { return value; }
    }

    /**
     * Strip credentials from a URI: "ftp://user:pass@host/path" → "ftp://host/path"
     */
    static String sanitizeUri(String uri) {
        if (uri == null) return null;
        int schemeEnd = uri.indexOf("://");
        if (schemeEnd < 0) return uri;
        int authStart = schemeEnd + 3;
        int atSign = uri.indexOf('@', authStart);
        if (atSign < 0) return uri;
        return uri.substring(0, authStart) + uri.substring(atSign + 1);
    }
}
