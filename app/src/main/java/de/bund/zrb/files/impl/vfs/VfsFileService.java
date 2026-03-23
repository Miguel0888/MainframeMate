package de.bund.zrb.files.impl.vfs;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceErrorCode;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.api.FileWriteResult;
import de.bund.zrb.files.auth.ConnectionId;
import de.bund.zrb.files.auth.Credentials;
import de.bund.zrb.files.auth.CredentialsProvider;
import de.bund.zrb.files.codec.RecordStructureCodec;
import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.files.model.FilePayload;
import de.bund.zrb.files.path.MvsPathDialect;
import de.bund.zrb.files.path.PathDialect;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ndv.NdvObjectInfo;
import de.bund.zrb.ndv.NdvService;
import de.bund.zrb.files.impl.vfs.ndv.NdvFileProvider;
import de.bund.zrb.util.ByteUtil;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.VFS;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Unified FileService implementation.
 * &lt;ul&gt;
 *   &lt;li&gt;{@code file://} — local file system (via Apache Commons VFS2)&lt;/li&gt;
 *   &lt;li&gt;{@code ftp://} — FTP including MVS/z/OS (via direct Apache Commons Net FTPClient)&lt;/li&gt;
 *   &lt;li&gt;{@code ndv://} — Natural Development Server (via custom VFS provider)&lt;/li&gt;
 * &lt;/ul&gt;
 * &lt;p&gt;
 * FTP uses a direct {@link FTPClient} instead of VFS2's FTP provider to support
 * MVS-specific operations: dual NLST listing, qualifier navigation, member
 * detection, record structure, and binary mode switching.
 */
public class VfsFileService implements FileService {

    private static final Logger LOG = Logger.getLogger(VfsFileService.class.getName());

    // ── VFS fields (local / NDV) ──
    private final FileSystemManager manager;
    private final String baseUri;
    private final FileSystemOptions fsOptions;
    private final FileServiceException initError;

    // ── FTP fields (direct FTPClient) ──
    private FTPClient ftpClient;
    private PathDialect mvsDialect;
    private Settings ftpSettings;
    private Byte padding;
    private boolean recordStructure;
    private boolean ftpClosed;

    // ── Shared metadata ──
    private boolean mvsMode;
    private String systemType;

    // ═══════════════════════════════════════════════════════════════════
    //  Factory methods
    // ═══════════════════════════════════════════════════════════════════

    /** Create a FileService for the local file system. */
    public static VfsFileService forLocal() {
        return new VfsFileService("file:///", null, null);
    }

    /** Create a FileService for the local file system with a base root. */
    public static VfsFileService forLocal(Path baseRoot) {
        String uri = baseRoot != null
                ? baseRoot.toAbsolutePath().normalize().toUri().toString()
                : "file:///";
        return new VfsFileService(uri, null, null);
    }

    /**
     * Create a FileService for FTP.
     * Uses a direct Apache Commons Net FTPClient — no VFS2 FTP provider —
     * so that MVS-specific commands (NLST, quoting, record structure) work.
     */
    public static VfsFileService forFtp(String host, String user, String password) throws FileServiceException {
        Settings settings = SettingsHelper.load();
        String ftpUri = "ftp://" + host + "/";
        VfsFileService service = new VfsFileService(ftpUri, null, null);
        service.connectFtp(host, user, password, settings);
        return service;
    }

    /** Create a FileService for FTP using connection credentials. */
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
     * Create a FileService for NDV (Natural Development Server).
     * Registers the custom VFS provider and builds an ndv:// URI.
     */
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
    //  Constructor (VFS-only init; FTP fields set by connectFtp)
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
    //  FTP connection (direct Apache Commons Net)
    // ═══════════════════════════════════════════════════════════════════

    private void connectFtp(String host, String user, String password, Settings settings) throws FileServiceException {
        this.ftpClient = new FTPClient();
        this.ftpSettings = settings;
        this.mvsDialect = new MvsPathDialect();

        Integer ftpFileType = settings.ftpFileType == null ? null : settings.ftpFileType.getCode();
        this.padding = ftpFileType == null || ftpFileType == FTP.ASCII_FILE_TYPE
                ? ByteUtil.parseHexByte(settings.padding)
                : null;
        this.recordStructure = false;

        try {
            int connectTimeout = settings.ftpConnectTimeoutMs;
            int controlTimeout = settings.ftpControlTimeoutMs;
            int dataTimeout    = settings.ftpDataTimeoutMs;

            System.out.println("[FTP] Connecting to " + host + " with timeouts: " +
                    "connect=" + (connectTimeout == 0 ? "disabled" : connectTimeout + "ms") + ", " +
                    "control=" + (controlTimeout == 0 ? "disabled" : controlTimeout + "ms") + ", " +
                    "data="    + (dataTimeout    == 0 ? "disabled" : dataTimeout    + "ms"));

            ftpClient.setControlEncoding(settings.encoding);

            if (connectTimeout > 0) {
                ftpClient.setDefaultTimeout(connectTimeout);
                ftpClient.setConnectTimeout(connectTimeout);
            }

            ftpClient.connect(host);
            ftpClient.setSoTimeout(controlTimeout);
            applyDataTimeout(dataTimeout);

            if (!ftpClient.login(user, password)) {
                throw new FileServiceException(FileServiceErrorCode.AUTH_FAILED, "FTP login failed");
            }

            ftpClient.enterLocalPassiveMode();

            String sysType = ftpClient.getSystemType();
            mvsMode    = sysType != null && sysType.toUpperCase().contains("MVS");
            systemType = sysType;

            if (mvsMode) {
                System.out.println("[FTP] Detected MVS/zOS system, configuring MVS parser");
                ftpClient.configure(new FTPClientConfig(FTPClientConfig.SYST_MVS));
            } else if (sysType != null && sysType.toUpperCase().contains("WIN32NT")) {
                ftpClient.configure(new FTPClientConfig(FTPClientConfig.SYST_NT));
            }

            applyTransferSettings(settings);
            recordStructure = settings.ftpFileStructure != null
                    ? settings.ftpFileStructure.getCode() == FTP.RECORD_STRUCTURE
                    : mvsMode;

        } catch (IOException e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            System.err.println("[FTP] Connection failed: " + root.getClass().getSimpleName()
                    + " - " + root.getMessage());
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "FTP connection failed", e);
        }
    }

    private void applyTransferSettings(Settings settings) throws IOException {
        Integer ftpFileType = settings.ftpFileType == null ? null : settings.ftpFileType.getCode();
        if (ftpFileType != null) {
            if (settings.ftpTextFormat != null) {
                ftpClient.setFileType(ftpFileType, settings.ftpTextFormat.getCode());
            } else {
                ftpClient.setFileType(ftpFileType);
            }
        } else {
            if (settings.ftpTextFormat != null) {
                ftpClient.setFileType(FTP.ASCII_FILE_TYPE, settings.ftpTextFormat.getCode());
            } else {
                ftpClient.setFileType(FTP.ASCII_FILE_TYPE);
            }
        }

        if (settings.ftpFileStructure != null) {
            ftpClient.setFileStructure(settings.ftpFileStructure.getCode());
        } else if (mvsMode) {
            ftpClient.setFileStructure(FTP.RECORD_STRUCTURE);
        } else {
            ftpClient.setFileStructure(FTP.FILE_STRUCTURE);
        }

        if (settings.ftpTransferMode != null) {
            ftpClient.setFileTransferMode(settings.ftpTransferMode.getCode());
        } else {
            ftpClient.setFileTransferMode(FTP.STREAM_TRANSFER_MODE);
        }
    }

    private void applyDataTimeout(int timeoutMs) {
        try {
            Method intMethod = FTPClient.class.getMethod("setDataTimeout", int.class);
            intMethod.invoke(ftpClient, timeoutMs);
            return;
        } catch (Exception ignore) { }
        try {
            Class<?> durationClass = Class.forName("java.time.Duration");
            Method ofMillis = durationClass.getMethod("ofMillis", long.class);
            Object duration = ofMillis.invoke(null, (long) timeoutMs);
            Method durationMethod = FTPClient.class.getMethod("setDataTimeout", durationClass);
            durationMethod.invoke(ftpClient, duration);
        } catch (Exception ignore) { }
    }

    private boolean isFtpMode() {
        return ftpClient != null;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FileService implementation — delegates to FTP or VFS
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public List<FileNode> list(String absolutePath) throws FileServiceException {
        if (isFtpMode()) return listFtp(absolutePath);
        return listVfs(absolutePath);
    }

    @Override
    public FilePayload readFile(String absolutePath) throws FileServiceException {
        if (isFtpMode()) return readFileFtp(absolutePath);
        return readFileVfs(absolutePath);
    }

    @Override
    public FilePayload readFileBinary(String absolutePath) throws FileServiceException {
        if (isFtpMode()) return readFileBinaryFtp(absolutePath);
        return readFileBinaryVfs(absolutePath);
    }

    @Override
    public void writeFile(String absolutePath, FilePayload payload) throws FileServiceException {
        if (payload == null) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Payload is required");
        }
        if (isFtpMode()) { writeFileFtp(absolutePath, payload); return; }
        writeFileVfs(absolutePath, payload);
    }

    @Override
    public void writeFileBinary(String absolutePath, FilePayload payload) throws FileServiceException {
        if (payload == null) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Payload is required");
        }
        if (isFtpMode()) { writeFileBinaryFtp(absolutePath, payload); return; }
        writeFileVfs(absolutePath, payload);   // VFS handles binary transparently
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
        if (isFtpMode()) return deleteFtp(absolutePath);
        return deleteVfs(absolutePath);
    }

    @Override
    public boolean createDirectory(String absolutePath) throws FileServiceException {
        if (isFtpMode()) return createDirectoryFtp(absolutePath);
        return createDirectoryVfs(absolutePath);
    }

    @Override
    public void close() throws FileServiceException {
        if (isFtpMode()) closeFtp();
        // VFS manager is shared/singleton — nothing to close
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Public accessors (metadata)
    // ═══════════════════════════════════════════════════════════════════

    public boolean isMvsMode()       { return mvsMode; }
    public String  getSystemType()   { return systemType; }
    public String  getBaseUri()      { return baseUri; }

    // ═══════════════════════════════════════════════════════════════════
    //  VFS operations (local / NDV)
    // ═══════════════════════════════════════════════════════════════════

    private List<FileNode> listVfs(String absolutePath) throws FileServiceException {
        FileObject root = resolveVfs(absolutePath);
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
                    try { if (!isDir) size = child.getContent().getSize(); } catch (Exception ignore) { }
                    try { lastModified = child.getContent().getLastModifiedTime(); } catch (Exception ignore) { }

                    String name = child.getName().getBaseName();
                    String path = sanitizeUri(child.getName().getURI());
                    if (path.startsWith("file://")) {
                        try { path = new File(new URI(path)).getAbsolutePath(); } catch (URISyntaxException ignore) { }
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

    private FilePayload readFileVfs(String absolutePath) throws FileServiceException {
        FileObject file = resolveVfs(absolutePath);
        try {
            if (!file.exists()) {
                throw new FileServiceException(FileServiceErrorCode.NOT_FOUND,
                        "File not found: " + sanitizeUri(file.getName().getURI()));
            }
            if (file.getType() == FileType.FOLDER) {
                throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                        "Path is a directory: " + sanitizeUri(file.getName().getURI()));
            }
            byte[] bytes = readVfsBytes(file);
            return FilePayload.fromBytes(bytes, Charset.defaultCharset(), false);
        } catch (FileSystemException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "VFS read failed: " + e.getMessage(), e);
        } finally {
            tryClose(file);
        }
    }

    private FilePayload readFileBinaryVfs(String absolutePath) throws FileServiceException {
        FileObject file = resolveVfs(absolutePath);
        try {
            if (!file.exists()) {
                throw new FileServiceException(FileServiceErrorCode.NOT_FOUND,
                        "File not found (binary): " + sanitizeUri(file.getName().getURI()));
            }
            if (file.getType() == FileType.FOLDER) {
                throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                        "Path is a directory: " + sanitizeUri(file.getName().getURI()));
            }
            byte[] bytes = readVfsBytes(file);
            return FilePayload.fromBytes(bytes, null, false);
        } catch (FileSystemException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "VFS binary read failed: " + e.getMessage(), e);
        } finally {
            tryClose(file);
        }
    }

    private void writeFileVfs(String absolutePath, FilePayload payload) throws FileServiceException {
        FileObject file = resolveVfs(absolutePath);
        try (OutputStream out = file.getContent().getOutputStream()) {
            out.write(payload.getBytes());
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "VFS write failed: " + e.getMessage(), e);
        } finally {
            tryClose(file);
        }
    }

    private boolean deleteVfs(String absolutePath) throws FileServiceException {
        FileObject file = resolveVfs(absolutePath);
        try {
            return file.delete();
        } catch (FileSystemException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "VFS delete failed: " + e.getMessage(), e);
        } finally {
            tryClose(file);
        }
    }

    private boolean createDirectoryVfs(String absolutePath) throws FileServiceException {
        FileObject file = resolveVfs(absolutePath);
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

    // ═══════════════════════════════════════════════════════════════════
    //  FTP Listing (direct FTPClient — full MVS support)
    // ═══════════════════════════════════════════════════════════════════

    private List<FileNode> listFtp(String absolutePath) throws FileServiceException {
        String resolved = resolveFtpPath(absolutePath);
        try {
            if (mvsMode) return listMvs(resolved);
            return listUnix(resolved);
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "FTP list failed: " + e.getMessage(), e);
        }
    }

    /** List files on Unix/standard FTP servers using listFiles(). */
    private List<FileNode> listUnix(String resolved) throws IOException {
        FTPFile[] files = ftpClient.listFiles(resolved);
        if (files == null || files.length == 0) {
            System.out.println("[FTP] listFiles returned empty for: " + resolved
                    + " - reply: " + ftpClient.getReplyString());
            return Collections.emptyList();
        }
        List<FileNode> nodes = new ArrayList<FileNode>(files.length);
        for (FTPFile file : files) {
            String name = file.getName();
            if (name == null || name.isEmpty() || ".".equals(name) || "..".equals(name)) continue;
            String childPath = joinFtpPath(resolved, name);
            long size = file.getSize();
            Calendar ts = file.getTimestamp();
            long lastModified = ts == null ? 0L : ts.getTimeInMillis();
            nodes.add(new FileNode(name, childPath, file.isDirectory(), size, lastModified));
        }
        return nodes;
    }

    /**
     * List datasets/members on MVS/zOS.
     * Dual listing strategy:
     * <ol>
     *   <li>NLST 'RESOLVED'   → PDS members (files)</li>
     *   <li>NLST 'RESOLVED.*' → sub-datasets (folders)</li>
     * </ol>
     */
    private List<FileNode> listMvs(String resolved) throws IOException {
        if (resolved == null || resolved.isEmpty() || "''".equals(resolved)) {
            System.out.println("[FTP/MVS] Cannot list MVS root - HLQ required");
            return Collections.emptyList();
        }

        Set<String> seenKeys = new LinkedHashSet<String>();
        List<FileNode> allNodes = new ArrayList<FileNode>();

        // ── 1. Direct listing: PDS members or matching datasets ──
        String[] directNames = ftpClient.listNames(resolved);
        if (directNames != null && directNames.length > 0) {
            System.out.println("[FTP/MVS] listNames (direct) returned " + directNames.length
                    + " entries for: " + resolved);
            List<FileNode> directNodes = buildMvsFileNodes(resolved, directNames);
            for (FileNode node : directNodes) {
                String key = node.getPath().toUpperCase();
                if (seenKeys.add(key)) allNodes.add(node);
            }
        }

        // ── 2. Wildcard listing: sub-datasets 'RESOLVED.*' ──
        String wildcardPath = buildMvsWildcardPath(resolved);
        if (wildcardPath != null) {
            try {
                String[] subNames = ftpClient.listNames(wildcardPath);
                if (subNames != null && subNames.length > 0) {
                    System.out.println("[FTP/MVS] listNames (wildcard) returned " + subNames.length
                            + " entries for: " + wildcardPath);
                    List<FileNode> subNodes = buildMvsSubDatasetNodes(resolved, subNames);
                    for (FileNode node : subNodes) {
                        String key = node.getPath().toUpperCase();
                        if (seenKeys.add(key)) allNodes.add(node);
                    }
                }
            } catch (IOException e) {
                System.out.println("[FTP/MVS] Wildcard listing failed for "
                        + wildcardPath + ": " + e.getMessage());
            }
        }

        if (!allNodes.isEmpty()) return allNodes;

        // ── 3. Fallback: listFiles ──
        System.out.println("[FTP/MVS] Both NLST strategies empty, trying listFiles for: "
                + resolved + " - reply: " + ftpClient.getReplyString());
        FTPFile[] files = ftpClient.listFiles(resolved);
        if (files == null || files.length == 0) {
            System.out.println("[FTP/MVS] listFiles also empty for: "
                    + resolved + " - reply: " + ftpClient.getReplyString());
            return Collections.emptyList();
        }

        System.out.println("[FTP/MVS] listFiles returned " + files.length + " entries for: " + resolved);
        List<FileNode> nodes = new ArrayList<FileNode>(files.length);
        for (FTPFile file : files) {
            String name = file.getName();
            if (name == null || name.isEmpty()) continue;
            String childPath = joinPathMvs(resolved, name);
            long size = file.getSize();
            Calendar ts = file.getTimestamp();
            long lastModified = ts == null ? 0L : ts.getTimeInMillis();
            boolean isDirectory = file.isDirectory() || isPds(name);
            nodes.add(new FileNode(name, childPath, isDirectory, size, lastModified));
        }
        return nodes;
    }

    /** Build wildcard path: 'USR1.TMP' → "'USR1.TMP.*'" */
    private String buildMvsWildcardPath(String resolved) {
        String unquoted = unquote(resolved);
        if (unquoted.isEmpty() || unquoted.endsWith("*") || unquoted.contains("(")) return null;
        return "'" + unquoted + ".*'";
    }

    /**
     * Build FileNodes from a direct NLST result.
     * Fully qualified names (starting with parent + ".") are sub-datasets (directories).
     */
    private List<FileNode> buildMvsFileNodes(String parent, String[] names) {
        List<FileNode> nodes = new ArrayList<FileNode>(names.length);
        String unquotedParent = unquote(parent).toUpperCase();

        for (String name : names) {
            if (name == null || name.isEmpty()) continue;
            String trimmedName = name.trim();
            if (trimmedName.isEmpty()) continue;

            String unquotedName = unquote(trimmedName).toUpperCase();
            if (unquotedName.equals(unquotedParent)) {
                System.out.println("[FTP/MVS] Skipping parent entry: " + trimmedName);
                continue;
            }

            String displayName;
            String fullPath;
            boolean isSubDataset = false;

            if (unquotedName.startsWith(unquotedParent + ".")) {
                // ── Sub-dataset ──
                isSubDataset = true;
                String originalUnquoted = unquote(trimmedName);
                displayName = originalUnquoted.substring(unquotedParent.length() + 1);
                fullPath = mvsDialect.toAbsolutePath(originalUnquoted);
                System.out.println("[FTP/MVS] Sub-dataset: " + trimmedName + " -> display: " + displayName);
            } else if (trimmedName.startsWith("'") && trimmedName.endsWith("'")) {
                String originalUnquoted = unquote(trimmedName);
                if (originalUnquoted.toUpperCase().startsWith(unquotedParent + ".")) {
                    isSubDataset = true;
                    displayName = originalUnquoted.substring(unquotedParent.length() + 1);
                } else {
                    displayName = originalUnquoted;
                }
                fullPath = trimmedName;
                System.out.println("[FTP/MVS] Quoted path: " + trimmedName
                        + " -> display: " + displayName + " isSubDs=" + isSubDataset);
            } else {
                // Relative name → PDS member
                displayName = trimmedName;
                fullPath = joinPathMvs(parent, trimmedName);
                System.out.println("[FTP/MVS] Member: " + trimmedName + " -> fullPath: " + fullPath);
            }

            boolean isDirectory;
            if (isSubDataset) {
                isDirectory = true;
            } else if (displayName.contains("(")) {
                isDirectory = false;
            } else {
                isDirectory = !isMemberName(displayName);
            }

            nodes.add(new FileNode(displayName, fullPath, isDirectory, 0L, 0L));
        }
        return nodes;
    }

    /**
     * Build FileNodes from wildcard NLST (e.g. 'USR1.TMP.*').
     * All results are sub-datasets → always isDirectory=true.
     */
    private List<FileNode> buildMvsSubDatasetNodes(String parent, String[] names) {
        List<FileNode> nodes = new ArrayList<FileNode>(names.length);
        String unquotedParent = unquote(parent).toUpperCase();

        for (String name : names) {
            if (name == null || name.isEmpty()) continue;
            String trimmedName = name.trim();
            if (trimmedName.isEmpty()) continue;

            String unquotedName = unquote(trimmedName).toUpperCase();
            if (unquotedName.equals(unquotedParent)) continue;

            String originalUnquoted = unquote(trimmedName);
            String displayName;
            String fullPath;

            if (unquotedName.startsWith(unquotedParent + ".")) {
                displayName = originalUnquoted.substring(unquotedParent.length() + 1);
                fullPath = mvsDialect.toAbsolutePath(originalUnquoted);
            } else {
                displayName = originalUnquoted;
                fullPath = mvsDialect.toAbsolutePath(originalUnquoted);
            }

            // Show only the next qualifier level
            if (displayName.contains(".")) {
                String nextLevel = displayName.substring(0, displayName.indexOf('.'));
                displayName = nextLevel;
                fullPath = mvsDialect.toAbsolutePath(unquotedParent + "." + nextLevel);
            }

            System.out.println("[FTP/MVS] Sub-dataset (wildcard): " + trimmedName
                    + " -> display: " + displayName + " path: " + fullPath);
            nodes.add(new FileNode(displayName, fullPath, true, 0L, 0L));
        }
        return nodes;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FTP Read (direct FTPClient)
    // ═══════════════════════════════════════════════════════════════════

    private FilePayload readFileFtp(String absolutePath) throws FileServiceException {
        List<String> candidates = resolveReadCandidates(absolutePath);
        FileServiceException lastError = null;
        for (String candidate : candidates) {
            try {
                return readFtpInternal(candidate);
            } catch (FileServiceException e) {
                lastError = e;
            }
        }
        if (lastError != null) throw lastError;
        throw new FileServiceException(FileServiceErrorCode.NOT_FOUND, "FTP file not found");
    }

    private FilePayload readFtpInternal(String resolvedPath) throws FileServiceException {
        InputStream in = null;
        try {
            long t0 = System.currentTimeMillis();
            in = ftpClient.retrieveFileStream(resolvedPath);
            if (in == null) {
                throw new FileServiceException(FileServiceErrorCode.NOT_FOUND,
                        "FTP file not found: " + resolvedPath + " reply=" + ftpClient.getReplyString());
            }

            byte[] bytes = readFtpBytes(in);
            long t1 = System.currentTimeMillis();
            System.out.println("[FTP] readAllBytes took " + (t1 - t0) + "ms, bytes=" + bytes.length);

            in.close();
            in = null;

            if (!ftpClient.completePendingCommand()) {
                throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                        "FTP transfer incomplete: " + resolvedPath);
            }
            long t2 = System.currentTimeMillis();
            System.out.println("[FTP] completePendingCommand took " + (t2 - t1) + "ms");

            Charset charset = Charset.forName(ftpClient.getControlEncoding());

            if (recordStructure) {
                String editorText = RecordStructureCodec.decodeForEditor(bytes, charset, ftpSettings);
                System.out.println("[FTP] readFtpInternal total: " + (System.currentTimeMillis() - t0) + "ms");
                return FilePayload.fromBytesWithEditorText(bytes, charset, recordStructure, editorText);
            }

            System.out.println("[FTP] readFtpInternal total: " + (System.currentTimeMillis() - t0) + "ms");
            return FilePayload.fromBytes(bytes, charset, recordStructure);
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "FTP read failed: " + resolvedPath, e);
        } finally {
            if (in != null) { try { in.close(); } catch (IOException ignore) { } }
        }
    }

    private FilePayload readFileBinaryFtp(String absolutePath) throws FileServiceException {
        List<String> candidates = resolveReadCandidates(absolutePath);
        FileServiceException lastError = null;
        boolean originalRecordStructure = recordStructure;
        try {
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            ftpClient.setFileStructure(FTP.FILE_STRUCTURE);
            recordStructure = false;
            System.out.println("[FTP] readFileBinary: switched to BINARY mode for " + absolutePath);

            for (String candidate : candidates) {
                try {
                    return readFtpInternalBinary(candidate);
                } catch (FileServiceException e) {
                    lastError = e;
                }
            }
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "Failed to switch FTP to binary mode: " + e.getMessage(), e);
        } finally {
            try {
                applyTransferSettings(ftpSettings);
                recordStructure = originalRecordStructure;
                System.out.println("[FTP] readFileBinary: restored original transfer mode");
            } catch (IOException e) {
                System.err.println("[FTP] Warning: failed to restore transfer settings: " + e.getMessage());
            }
        }
        if (lastError != null) throw lastError;
        throw new FileServiceException(FileServiceErrorCode.NOT_FOUND, "FTP file not found (binary)");
    }

    private FilePayload readFtpInternalBinary(String resolvedPath) throws FileServiceException {
        InputStream in = null;
        try {
            long t0 = System.currentTimeMillis();
            in = ftpClient.retrieveFileStream(resolvedPath);
            if (in == null) {
                throw new FileServiceException(FileServiceErrorCode.NOT_FOUND,
                        "FTP file not found: " + resolvedPath + " reply=" + ftpClient.getReplyString());
            }
            byte[] bytes = readFtpBytesRaw(in);
            long t1 = System.currentTimeMillis();
            System.out.println("[FTP] readFtpInternalBinary: " + bytes.length
                    + " bytes in " + (t1 - t0) + "ms");

            in.close();
            in = null;

            if (!ftpClient.completePendingCommand()) {
                throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                        "FTP transfer incomplete: " + resolvedPath);
            }
            return FilePayload.fromBytes(bytes, null, false);
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "FTP binary read failed: " + resolvedPath, e);
        } finally {
            if (in != null) { try { in.close(); } catch (IOException ignore) { } }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FTP Write (direct FTPClient)
    // ═══════════════════════════════════════════════════════════════════

    private void writeFileFtp(String absolutePath, FilePayload payload) throws FileServiceException {
        List<String> candidates = resolveWriteCandidates(absolutePath);
        FileServiceException lastError = null;
        for (String candidate : candidates) {
            try {
                writeFtpInternal(candidate, payload);
                return;
            } catch (FileServiceException e) {
                lastError = e;
            }
        }
        if (lastError != null) throw lastError;
        throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "FTP write failed");
    }

    private void writeFtpInternal(String resolvedPath, FilePayload payload) throws FileServiceException {
        ByteArrayInputStream in = new ByteArrayInputStream(payload.getBytes());
        try {
            boolean success = ftpClient.storeFile(resolvedPath, in);
            if (!success) {
                throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                        "FTP write failed: " + ftpClient.getReplyString());
            }
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "FTP write failed", e);
        }
    }

    private void writeFileBinaryFtp(String absolutePath, FilePayload payload) throws FileServiceException {
        boolean originalRecordStructure = recordStructure;
        try {
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            ftpClient.setFileStructure(FTP.FILE_STRUCTURE);
            recordStructure = false;
            System.out.println("[FTP] writeFileBinary: switched to BINARY mode for " + absolutePath);

            List<String> candidates = resolveWriteCandidates(absolutePath);
            FileServiceException lastError = null;
            for (String candidate : candidates) {
                try {
                    writeFtpInternal(candidate, payload);
                    System.out.println("[FTP] writeFileBinary: wrote " + payload.getBytes().length
                            + " bytes to " + candidate);
                    return;
                } catch (FileServiceException e) {
                    lastError = e;
                }
            }
            if (lastError != null) throw lastError;
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "FTP binary write failed");
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "Failed to switch FTP to binary mode for write: " + e.getMessage(), e);
        } finally {
            try {
                applyTransferSettings(ftpSettings);
                recordStructure = originalRecordStructure;
                System.out.println("[FTP] writeFileBinary: restored original transfer mode");
            } catch (IOException e) {
                System.err.println("[FTP] Warning: failed to restore transfer settings after binary write: "
                        + e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FTP Delete / CreateDirectory / Close
    // ═══════════════════════════════════════════════════════════════════

    private boolean deleteFtp(String absolutePath) throws FileServiceException {
        String resolved = resolveFtpPath(absolutePath);
        try {
            return ftpClient.deleteFile(resolved) || ftpClient.removeDirectory(resolved);
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "FTP delete failed", e);
        }
    }

    private boolean createDirectoryFtp(String absolutePath) throws FileServiceException {
        String resolved = resolveFtpPath(absolutePath);
        try {
            return ftpClient.makeDirectory(resolved);
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "FTP create directory failed", e);
        }
    }

    private void closeFtp() throws FileServiceException {
        if (ftpClosed) return;
        ftpClosed = true;
        try {
            if (ftpClient.isConnected()) {
                ftpClient.logout();
                ftpClient.disconnect();
            }
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "FTP disconnect failed", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FTP path helpers
    // ═══════════════════════════════════════════════════════════════════

    private String resolveFtpPath(String path) {
        if (mvsMode) return mvsDialect.toAbsolutePath(path);
        if (path == null || path.trim().isEmpty()) return "/";
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private List<String> resolveReadCandidates(String path) {
        if (!mvsMode) return Collections.singletonList(resolveFtpPath(path));
        return resolveMvsCandidates(path);
    }

    private List<String> resolveWriteCandidates(String path) {
        if (!mvsMode) return Collections.singletonList(resolveFtpPath(path));
        return resolveMvsCandidates(path);
    }

    private List<String> resolveMvsCandidates(String path) {
        if (mvsDialect instanceof MvsPathDialect) {
            return ((MvsPathDialect) mvsDialect).resolveCandidates(path);
        }
        String trimmed = path == null ? "" : path.trim();
        return Collections.singletonList(mvsDialect.toAbsolutePath(trimmed));
    }

    private String joinFtpPath(String parent, String name) {
        if (mvsMode) return joinPathMvs(parent, name);
        if (parent.endsWith("/")) return parent + name;
        if ("/".equals(parent)) return "/" + name;
        return parent + "/" + name;
    }

    private String joinPathMvs(String parent, String name) {
        if (mvsDialect instanceof MvsPathDialect) {
            return ((MvsPathDialect) mvsDialect).childOf(parent, name);
        }
        String rawParent = unquote(parent);
        if (rawParent.isEmpty()) return mvsDialect.toAbsolutePath(name);
        return mvsDialect.toAbsolutePath(rawParent + "." + name);
    }

    private boolean isPds(String name) {
        return name != null && !name.isEmpty() && !name.contains("(");
    }

    private boolean isMemberName(String name) {
        if (name == null || name.isEmpty() || name.length() > 8) return false;
        return !name.contains(".") && !name.contains("(") && !name.contains(")");
    }

    private String unquote(String path) {
        if (path == null) return "";
        String trimmed = path.trim();
        if (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    /** Read FTP bytes with padding removal (for text/record mode). */
    private byte[] readFtpBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (padding != null) {
                for (int i = 0; i < read; i++) {
                    if (buffer[i] != padding) out.write(buffer[i]);
                }
            } else {
                out.write(buffer, 0, read);
            }
        }
        return out.toByteArray();
    }

    /** Read FTP bytes raw — no padding removal (for binary transfers). */
    private byte[] readFtpBytesRaw(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  VFS internal helpers (local / NDV)
    // ═══════════════════════════════════════════════════════════════════

    /** Resolve a path to a VFS FileObject (local / NDV only). */
    private FileObject resolveVfs(String path) throws FileServiceException {
        if (initError != null) throw initError;
        try {
            if (path == null || path.trim().isEmpty()) {
                return manager.resolveFile(baseUri, fsOptions);
            }
            String trimmed = path.trim();

            if (isVfsUri(trimmed)) {
                return manager.resolveFile(trimmed, fsOptions);
            }
            if (baseUri.startsWith("file://")) {
                return resolveLocal(trimmed);
            }
            if (baseUri.startsWith("ndv://")) {
                return manager.resolveFile(baseUri, fsOptions);
            }

            // Fallback
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

    private boolean isVfsUri(String path) {
        return path.contains("://");
    }

    private byte[] readVfsBytes(FileObject file) throws FileServiceException {
        try (InputStream in = file.getContent().getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "VFS read bytes failed", e);
        }
    }

    private void tryClose(FileObject file) {
        if (file == null) return;
        try { file.close(); } catch (FileSystemException ignore) { }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Static utilities
    // ═══════════════════════════════════════════════════════════════════

    private static String encodeUriComponent(String value) {
        if (value == null) return "";
        try {
            return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }

    /**
     * Strip credentials from a URI to prevent leaking sensitive information.
     * Example: "ftp://user:pass@host/path" → "ftp://host/path"
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
