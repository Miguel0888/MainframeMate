package de.bund.zrb.files.impl.ftp.vfs;

import de.bund.zrb.files.auth.ConnectionId;
import de.bund.zrb.files.auth.Credentials;
import de.bund.zrb.files.auth.CredentialsProvider;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.UserAuthenticator;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.auth.StaticUserAuthenticator;
import org.apache.commons.vfs2.provider.ftp.FtpFileSystemConfigBuilder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Manual spike tests for VFS FTP evaluation.
 *
 * These tests are skipped unless required env vars are present.
 */
class VfsFtpSpikeTest {

    private static final String ENV_HOST = "VFS_FTP_HOST";
    private static final String ENV_USER = "VFS_FTP_USER";
    private static final String ENV_PASS = "VFS_FTP_PASS";
    private static final String ENV_ROOT = "VFS_FTP_ROOT"; // optional
    private static final String ENV_MVS_DATASET = "VFS_MVS_DATASET"; // e.g. ABC.DEF
    private static final String ENV_MVS_PDS = "VFS_MVS_PDS"; // e.g. ABC.DEF.PDS
    private static final String ENV_MVS_MEMBER = "VFS_MVS_MEMBER"; // e.g. MEMBER
    private static final String ENV_RW_PATH = "VFS_RW_PATH"; // optional writable test file

    @Test
    void listRootOrProvidedPath() throws Exception {
        EnvContext ctx = EnvContext.fromEnv();
        Assumptions.assumeTrue(ctx.isConfigured(), "VFS FTP env vars missing");

        FileObject root = resolve(ctx, ctx.rootPath);
        try {
            Assumptions.assumeTrue(root.exists(), "Root path does not exist");
            Assumptions.assumeTrue(root.getType() == FileType.FOLDER, "Root path is not a folder");
            for (FileObject child : root.getChildren()) {
                child.close();
            }
        } finally {
            root.close();
        }
    }

    @Test
    void listMvsDatasetPdsMember() throws Exception {
        EnvContext ctx = EnvContext.fromEnv();
        Assumptions.assumeTrue(ctx.isConfigured(), "VFS FTP env vars missing");
        Assumptions.assumeTrue(ctx.mvsDataset != null && ctx.mvsPds != null && ctx.mvsMember != null,
                "MVS env vars missing");

        // Dataset list
        FileObject dataset = resolve(ctx, ctx.mvsDataset);
        try {
            dataset.getChildren();
        } finally {
            dataset.close();
        }

        // PDS list
        FileObject pds = resolve(ctx, ctx.mvsPds);
        try {
            pds.getChildren();
        } finally {
            pds.close();
        }

        // Member list (PDS(member))
        FileObject member = resolve(ctx, ctx.mvsPds + "(" + ctx.mvsMember + ")");
        try {
            Assumptions.assumeTrue(member.exists(), "Member not found");
        } finally {
            member.close();
        }
    }

    @Test
    void readWriteMemberOrPath() throws Exception {
        EnvContext ctx = EnvContext.fromEnv();
        Assumptions.assumeTrue(ctx.isConfigured(), "VFS FTP env vars missing");
        Assumptions.assumeTrue(ctx.rwPath != null, "VFS_RW_PATH not set");

        FileObject target = resolve(ctx, ctx.rwPath);
        try {
            String payload = "vfs-spike-" + System.currentTimeMillis();
            try (OutputStream out = target.getContent().getOutputStream()) {
                out.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            String readBack = readAsString(target);
            Assumptions.assumeTrue(readBack.contains(payload), "Read/write mismatch");
        } finally {
            target.close();
        }
    }

    private FileObject resolve(EnvContext ctx, String path) throws FileSystemException {
        FileSystemManager manager = VFS.getManager();
        FileSystemOptions options = new FileSystemOptions();

        UserAuthenticator auth = new StaticUserAuthenticator(null, ctx.user, ctx.password);
        org.apache.commons.vfs2.impl.DefaultFileSystemConfigBuilder.getInstance()
                .setUserAuthenticator(options, auth);

        FtpFileSystemConfigBuilder.getInstance().setUserDirIsRoot(options, false);
        FtpFileSystemConfigBuilder.getInstance().setPassiveMode(options, true);

        String normalized = path == null || path.trim().isEmpty() ? "" : path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }

        String uri = "ftp://" + ctx.host + normalized;
        return manager.resolveFile(uri, options);
    }

    private String readAsString(FileObject file) throws Exception {
        try (InputStream in = file.getContent().getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static final class EnvContext {
        private final String host;
        private final String user;
        private final String password;
        private final String rootPath;
        private final String mvsDataset;
        private final String mvsPds;
        private final String mvsMember;
        private final String rwPath;

        private EnvContext(String host, String user, String password, String rootPath,
                           String mvsDataset, String mvsPds, String mvsMember, String rwPath) {
            this.host = host;
            this.user = user;
            this.password = password;
            this.rootPath = rootPath;
            this.mvsDataset = mvsDataset;
            this.mvsPds = mvsPds;
            this.mvsMember = mvsMember;
            this.rwPath = rwPath;
        }

        static EnvContext fromEnv() {
            return new EnvContext(
                    System.getenv(ENV_HOST),
                    System.getenv(ENV_USER),
                    System.getenv(ENV_PASS),
                    System.getenv(ENV_ROOT),
                    System.getenv(ENV_MVS_DATASET),
                    System.getenv(ENV_MVS_PDS),
                    System.getenv(ENV_MVS_MEMBER),
                    System.getenv(ENV_RW_PATH)
            );
        }

        boolean isConfigured() {
            return host != null && user != null && password != null;
        }
    }
}

