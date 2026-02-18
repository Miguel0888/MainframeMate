package de.bund.zrb.files.impl.ftp;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceErrorCode;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.api.FileWriteResult;
import de.bund.zrb.files.auth.ConnectionId;
import de.bund.zrb.files.auth.Credentials;
import de.bund.zrb.files.auth.CredentialsProvider;
import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.files.model.FilePayload;
import de.bund.zrb.files.path.MvsPathDialect;
import de.bund.zrb.files.path.PathDialect;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.util.ByteUtil;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class CommonsNetFtpFileService implements FileService {

    private final FTPClient ftpClient = new FTPClient();
    private final Settings settings;
    private final PathDialect mvsDialect;
    private final Byte padding;
    private boolean recordStructure;
    private boolean mvsMode;
    private boolean closed;
    private final ConnectionId connectionId;
    private final CredentialsProvider credentialsProvider;

    public CommonsNetFtpFileService(String host, String user, String password) throws FileServiceException {
        this(host, user, password, SettingsHelper.load(), new MvsPathDialect());
    }

    public CommonsNetFtpFileService(String host, String user, String password, Settings settings, PathDialect mvsDialect)
            throws FileServiceException {
        this.settings = settings == null ? SettingsHelper.load() : settings;
        this.mvsDialect = mvsDialect == null ? new MvsPathDialect() : mvsDialect;
        Integer ftpFileType = this.settings.ftpFileType == null ? null : this.settings.ftpFileType.getCode();
        this.padding = ftpFileType == null || ftpFileType == FTP.ASCII_FILE_TYPE
                ? ByteUtil.parseHexByte(this.settings.padding)
                : null;
        this.recordStructure = false;

        this.connectionId = new ConnectionId("ftp", host, user);
        this.credentialsProvider = null;

        connect(host, user, password);
    }

    public CommonsNetFtpFileService(CredentialsProvider credentialsProvider, ConnectionId connectionId) throws FileServiceException {
        this(credentialsProvider, connectionId, SettingsHelper.load(), new MvsPathDialect());
    }

    public CommonsNetFtpFileService(CredentialsProvider credentialsProvider,
                                   ConnectionId connectionId,
                                   Settings settings,
                                   PathDialect mvsDialect) throws FileServiceException {
        this.settings = settings == null ? SettingsHelper.load() : settings;
        this.mvsDialect = mvsDialect == null ? new MvsPathDialect() : mvsDialect;
        Integer ftpFileType = this.settings.ftpFileType == null ? null : this.settings.ftpFileType.getCode();
        this.padding = ftpFileType == null || ftpFileType == FTP.ASCII_FILE_TYPE
                ? ByteUtil.parseHexByte(this.settings.padding)
                : null;
        this.recordStructure = false;

        this.connectionId = connectionId;
        this.credentialsProvider = credentialsProvider;

        connect();
    }

    private void connect() throws FileServiceException {
        if (connectionId == null) {
            throw new FileServiceException(FileServiceErrorCode.AUTH_FAILED, "Missing ConnectionId");
        }
        if (credentialsProvider == null) {
            throw new FileServiceException(FileServiceErrorCode.AUTH_FAILED, "Missing CredentialsProvider");
        }

        Credentials credentials = credentialsProvider.resolve(connectionId)
                .orElseThrow(() -> new FileServiceException(FileServiceErrorCode.AUTH_FAILED, "No credentials available"));

        connect(credentials.getHost(), credentials.getUsername(), credentials.getPassword());
    }

    private void connect(String host, String user, String password) throws FileServiceException {
        try {
            ftpClient.setControlEncoding(settings.encoding);
            ftpClient.connect(host);

            if (!ftpClient.login(user, password)) {
                throw new FileServiceException(FileServiceErrorCode.AUTH_FAILED, "FTP login failed");
            }

            ftpClient.enterLocalPassiveMode();

            String systemType = ftpClient.getSystemType();
            mvsMode = systemType != null && systemType.toUpperCase().contains("MVS");

            applyTransferSettings(settings);
            recordStructure = settings.ftpFileStructure != null
                    ? settings.ftpFileStructure.getCode() == FTP.RECORD_STRUCTURE
                    : mvsMode;

            if (systemType != null && systemType.toUpperCase().contains("WIN32NT")) {
                ftpClient.configure(new FTPClientConfig(FTPClientConfig.SYST_NT));
            }
        } catch (IOException e) {
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

    @Override
    public List<FileNode> list(String absolutePath) throws FileServiceException {
        String resolved = resolvePath(absolutePath);
        try {
            FTPFile[] files = ftpClient.listFiles(resolved);
            if (files == null) {
                return Collections.emptyList();
            }

            List<FileNode> nodes = new ArrayList<FileNode>(files.length);
            for (FTPFile file : files) {
                String name = file.getName();
                String childPath = joinPath(resolved, name);
                long size = file.getSize();
                Calendar timestamp = file.getTimestamp();
                long lastModified = timestamp == null ? 0L : timestamp.getTimeInMillis();
                nodes.add(new FileNode(name, childPath, file.isDirectory(), size, lastModified));
            }
            return nodes;
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "FTP list failed", e);
        }
    }

    @Override
    public FilePayload readFile(String absolutePath) throws FileServiceException {
        List<String> candidates = resolveReadCandidates(absolutePath);
        FileServiceException lastError = null;
        for (String candidate : candidates) {
            try {
                return readFileInternal(candidate);
            } catch (FileServiceException e) {
                lastError = e;
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new FileServiceException(FileServiceErrorCode.NOT_FOUND, "FTP file not found");
    }

    private FilePayload readFileInternal(String resolvedPath) throws FileServiceException {
        InputStream in = null;
        try {
            in = ftpClient.retrieveFileStream(resolvedPath);
            if (in == null) {
                throw new FileServiceException(FileServiceErrorCode.NOT_FOUND,
                        "FTP file not found: " + resolvedPath + " reply=" + ftpClient.getReplyString());
            }

            byte[] bytes = readAllBytes(in);
            if (!ftpClient.completePendingCommand()) {
                throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                        "FTP transfer incomplete: " + resolvedPath);
            }

            Charset charset = Charset.forName(ftpClient.getControlEncoding());
            return FilePayload.fromBytes(bytes, charset, recordStructure);
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "FTP read failed: " + resolvedPath, e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignore) {
                    // ignore close errors
                }
            }
        }
    }

    @Override
    public void writeFile(String absolutePath, FilePayload payload) throws FileServiceException {
        if (payload == null) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "Payload is required");
        }
        List<String> candidates = resolveWriteCandidates(absolutePath);
        FileServiceException lastError = null;
        for (String candidate : candidates) {
            try {
                writeFileInternal(candidate, payload);
                return;
            } catch (FileServiceException e) {
                lastError = e;
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "FTP write failed");
    }

    private void writeFileInternal(String resolvedPath, FilePayload payload) throws FileServiceException {
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
        String resolved = resolvePath(absolutePath);
        try {
            return ftpClient.deleteFile(resolved) || ftpClient.removeDirectory(resolved);
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "FTP delete failed", e);
        }
    }

    @Override
    public boolean createDirectory(String absolutePath) throws FileServiceException {
        String resolved = resolvePath(absolutePath);
        try {
            return ftpClient.makeDirectory(resolved);
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "FTP create directory failed", e);
        }
    }

    @Override
    public void close() throws FileServiceException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (ftpClient.isConnected()) {
                ftpClient.logout();
                ftpClient.disconnect();
            }
        } catch (IOException e) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR, "FTP disconnect failed", e);
        }
    }

    private byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            // Immer 1:1 übernehmen. Padding wird erst am Ende getrimmt.
            out.write(buffer, 0, read);
        }

        byte[] bytes = out.toByteArray();
        return TrailingPaddingTrimmer.trim(bytes, padding);
    }

    private String resolvePath(String path) {
        if (mvsMode) {
            return mvsDialect.toAbsolutePath(path);
        }
        if (path == null || path.trim().isEmpty()) {
            return "/";
        }
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private List<String> resolveReadCandidates(String path) {
        if (!mvsMode) {
            return Collections.singletonList(resolvePath(path));
        }
        return resolveMvsCandidates(path);
    }

    private List<String> resolveWriteCandidates(String path) {
        if (!mvsMode) {
            return Collections.singletonList(resolvePath(path));
        }
        return resolveMvsCandidates(path);
    }

    private List<String> resolveMvsCandidates(String path) {
        if (mvsDialect instanceof MvsPathDialect) {
            return ((MvsPathDialect) mvsDialect).resolveCandidates(path);
        }
        String trimmed = path == null ? "" : path.trim();
        return Collections.singletonList(mvsDialect.toAbsolutePath(trimmed));
    }

    private String joinPath(String parent, String name) {
        if (mvsMode) {
            if (mvsDialect instanceof MvsPathDialect) {
                return ((MvsPathDialect) mvsDialect).childOf(parent, name);
            }

            String rawParent = unquote(parent);
            if (rawParent.isEmpty()) {
                return mvsDialect.toAbsolutePath(name);
            }
            return mvsDialect.toAbsolutePath(rawParent + "." + name);
        }

        if (parent.endsWith("/")) {
            return parent + name;
        }
        if ("/".equals(parent)) {
            return "/" + name;
        }
        return parent + "/" + name;
    }

    private String unquote(String path) {
        if (path == null) {
            return "";
        }
        String trimmed = path.trim();
        if (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    public boolean isMvsMode() {
        return mvsMode;
    }

    public String getSystemType() {
        try {
            return ftpClient.getSystemType();
        } catch (Exception e) {
            return null;
        }
    }
}
