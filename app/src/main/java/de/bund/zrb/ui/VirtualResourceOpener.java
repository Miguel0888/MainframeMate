package de.bund.zrb.ui;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.auth.CredentialsProvider;
import de.bund.zrb.files.impl.auth.InteractiveCredentialsProvider;
import de.bund.zrb.files.impl.factory.FileServiceFactory;
import de.bund.zrb.files.model.FilePayload;
import de.bund.zrb.login.LoginManager;
import de.zrb.bund.newApi.ui.FtpTab;

import javax.annotation.Nullable;
import java.nio.charset.Charset;

/**
 * Central stateless open logic: given a virtual reference, decide whether to open
 * a connection tab (directory) or a file tab (file), for LOCAL or FTP.
 *
 * Contract:
 * - Returns the opened tab (ConnectionTab or FileTab), or null on error.
 * - No Legacy manager usage; uses VirtualResource + FileService.
 */
public final class VirtualResourceOpener {

    private final TabbedPaneManager tabManager;
    private final VirtualResourceResolver resolver = new VirtualResourceResolver();

    public VirtualResourceOpener(TabbedPaneManager tabManager) {
        this.tabManager = tabManager;
    }

    public FtpTab open(String rawPath,
                       @Nullable String sentenceType,
                       @Nullable String searchPattern,
                       @Nullable Boolean toCompare) {

        VirtualResource resource;
        try {
            resource = resolver.resolve(rawPath);
        } catch (FileServiceException e) {
            System.err.println("[VirtualResourceOpener] resolve failed: " + e.getMessage());
            return null;
        }

        // Use interactive credentials provider that can show password dialog
        CredentialsProvider credentialsProvider = new InteractiveCredentialsProvider(
                (host, user) -> LoginManager.getInstance().getPassword(host, user)
        );

        FileService fs = null;
        try {
            fs = new FileServiceFactory().create(resource, credentialsProvider);

            // Mark session as active after successful FTP connection
            if (!resource.isLocal() && resource.getFtpState() != null) {
                LoginManager.getInstance().onLoginSuccess(
                        resource.getFtpState().getConnectionId().getHost(),
                        resource.getFtpState().getConnectionId().getUsername()
                );
            }

            if (resource.getKind() == VirtualResourceKind.DIRECTORY) {
                // IMPORTANT: For directory tabs, DO NOT close FileService here!
                // The tab owns the FileService and will close it in onClose().
                return openDirectory(resource, fs, searchPattern);
            } else {
                // For file tabs, we can close after reading
                try {
                    return openFile(resource, fs, sentenceType, searchPattern, toCompare);
                } finally {
                    closeQuietly(fs);
                }
            }
        } catch (Exception e) {
            System.err.println("[VirtualResourceOpener] open failed: " + e.getMessage());

            // On FTP auth failure, clear the password immediately
            if (!resource.isLocal() && resource.getFtpState() != null) {
                String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                if (errorMsg.contains("auth") || errorMsg.contains("login") || errorMsg.contains("credentials")) {
                    LoginManager.getInstance().onLoginFailed(
                            resource.getFtpState().getConnectionId().getHost(),
                            resource.getFtpState().getConnectionId().getUsername()
                    );
                }
            }

            closeQuietly(fs);
            return null;
        }
    }

    private void closeQuietly(FileService fs) {
        if (fs != null) {
            try {
                fs.close();
            } catch (Exception ignored) {
            }
        }
    }

    private FtpTab openDirectory(VirtualResource resource, FileService fs, String searchPattern) {
        if (resource.isLocal()) {
            LocalConnectionTabImpl tab = new LocalConnectionTabImpl(tabManager);
            tabManager.addTab(tab);
            tab.loadDirectory(resource.getResolvedPath());
            return tab;
        }

        ConnectionTabImpl tab = new ConnectionTabImpl(resource, fs, tabManager, searchPattern);
        tabManager.addTab(tab);
        tab.loadDirectory(resource.getResolvedPath());
        if (searchPattern != null && !searchPattern.trim().isEmpty()) {
            tab.searchFor(searchPattern);
        }
        return tab;
    }

    private FtpTab openFile(VirtualResource resource, FileService fs,
                            String sentenceType, String searchPattern, Boolean toCompare) throws FileServiceException {
        FilePayload payload = fs.readFile(resource.getResolvedPath());
        Charset charset = payload.getCharset() != null ? payload.getCharset() : Charset.defaultCharset();
        String content = new String(payload.getBytes(), charset);

        // Open file tab with VirtualResource (no Legacy manager)
        return tabManager.openFileTab(resource, content, sentenceType, searchPattern, toCompare);
    }
}
