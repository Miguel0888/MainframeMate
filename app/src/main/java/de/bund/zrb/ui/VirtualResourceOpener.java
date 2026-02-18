package de.bund.zrb.ui;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.impl.factory.FileServiceFactory;
import de.bund.zrb.files.model.FilePayload;
import de.bund.zrb.files.path.VirtualResourceRef;
import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.zrb.bund.newApi.ui.FtpTab;

import javax.annotation.Nullable;
import java.nio.charset.Charset;

/**
 * Central stateless open logic: given a virtual reference, decide whether to open
 * a connection tab (directory) or a file tab (file), for LOCAL or FTP.
 *
 * Contract:
 * - Returns the opened tab (ConnectionTab or FileTab), or null on error.
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
        } catch (de.bund.zrb.files.api.FileServiceException e) {
            return null;
        }

        if (resource.isLocal()) {
            if (resource.getKind() == VirtualResourceKind.DIRECTORY) {
                LocalConnectionTabImpl tab = new LocalConnectionTabImpl(tabManager);
                tabManager.addTab(tab);
                tab.loadDirectory(resource.getResolvedPath());
                return tab;
            }

            // Local file -> open editor
            try (FileService fs = new FileServiceFactory().createLocal()) {
                FilePayload payload = fs.readFile(resource.getResolvedPath());
                String content = new String(payload.getBytes(), payload.getCharset() != null ? payload.getCharset() : Charset.defaultCharset());
                return tabManager.openFileTab(new FtpManager(), content, sentenceType);
            } catch (Exception e) {
                return null;
            }
        }

        // FTP
        if (resource.getKind() == VirtualResourceKind.DIRECTORY) {
            Settings settings = SettingsHelper.load();
            FtpManager ftpManager = new FtpManager();
            try {
                ftpManager.connect(settings.host, settings.user);
                ConnectionTabImpl tab = new ConnectionTabImpl(ftpManager, tabManager, searchPattern);
                tabManager.addTab(tab);
                tab.loadDirectory(resource.getResolvedPath());
                return tab;
            } catch (Exception e) {
                return null;
            }
        }

        // FTP file
        Settings settings = SettingsHelper.load();
        FtpManager ftpManager = new FtpManager();
        try {
            ftpManager.connect(settings.host, settings.user);
            FtpFileBuffer buffer = ftpManager.open(resource.getResolvedPath());
            if (buffer != null) {
                return tabManager.openFileTab(ftpManager, buffer, sentenceType, searchPattern, toCompare);
            }
        } catch (Exception ignore) {
            // ignore
        }
        return null;
    }
}
