package de.bund.zrb.ui;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.auth.CredentialsProvider;
import de.bund.zrb.files.impl.auth.LoginManagerCredentialsProvider;
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
 * - No FtpManager usage; uses VirtualResource + FileService.
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

        CredentialsProvider credentialsProvider = new LoginManagerCredentialsProvider(
                (host, user) -> LoginManager.getInstance().getCachedPassword(host, user)
        );

        try (FileService fs = new FileServiceFactory().create(resource, credentialsProvider)) {
            if (resource.getKind() == VirtualResourceKind.DIRECTORY) {
                return openDirectory(resource, fs, searchPattern);
            } else {
                return openFile(resource, fs, sentenceType, searchPattern, toCompare);
            }
        } catch (Exception e) {
            System.err.println("[VirtualResourceOpener] open failed: " + e.getMessage());
            return null;
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

        // Open file tab with VirtualResource (no FtpManager)
        return tabManager.openFileTab(resource, content, sentenceType, searchPattern, toCompare);
    }
}
