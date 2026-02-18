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

        System.out.println("[VirtualResourceOpener] open called with rawPath=" + rawPath);

        VirtualResource resource;
        try {
            resource = resolver.resolve(rawPath);
        } catch (FileServiceException e) {
            System.err.println("[VirtualResourceOpener] resolve failed: " + e.getMessage());
            return null;
        }

        System.out.println("[VirtualResourceOpener] resolved: kind=" + resource.getKind() +
                " backendType=" + resource.getBackendType() +
                " isLocal=" + resource.isLocal() +
                " resolvedPath=" + resource.getResolvedPath());

        CredentialsProvider credentialsProvider = new LoginManagerCredentialsProvider(
                (host, user) -> LoginManager.getInstance().getCachedPassword(host, user)
        );

        try (FileService fs = new FileServiceFactory().create(resource, credentialsProvider)) {
            System.out.println("[VirtualResourceOpener] FileService created: " + fs.getClass().getSimpleName());

            if (resource.getKind() == VirtualResourceKind.DIRECTORY) {
                System.out.println("[VirtualResourceOpener] opening as DIRECTORY");
                return openDirectory(resource, fs, searchPattern);
            } else {
                System.out.println("[VirtualResourceOpener] opening as FILE");
                return openFile(resource, fs, sentenceType, searchPattern, toCompare);
            }
        } catch (Exception e) {
            System.err.println("[VirtualResourceOpener] open failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private FtpTab openDirectory(VirtualResource resource, FileService fs, String searchPattern) {
        if (resource.isLocal()) {
            System.out.println("[VirtualResourceOpener] creating LocalConnectionTabImpl for " + resource.getResolvedPath());
            LocalConnectionTabImpl tab = new LocalConnectionTabImpl(tabManager);
            System.out.println("[VirtualResourceOpener] adding tab to tabManager");
            tabManager.addTab(tab);
            System.out.println("[VirtualResourceOpener] calling loadDirectory");
            tab.loadDirectory(resource.getResolvedPath());
            System.out.println("[VirtualResourceOpener] LocalConnectionTabImpl ready, returning");
            return tab;
        }

        System.out.println("[VirtualResourceOpener] creating ConnectionTabImpl (FTP) for " + resource.getResolvedPath());
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
