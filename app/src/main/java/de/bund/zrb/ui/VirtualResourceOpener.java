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

    public VirtualResourceOpener(TabbedPaneManager tabManager) {
        this.tabManager = tabManager;
    }

    public FtpTab open(String rawPath,
                       @Nullable String sentenceType,
                       @Nullable String searchPattern,
                       @Nullable Boolean toCompare) {

        VirtualResourceRef ref = VirtualResourceRef.of(rawPath);

        // LOCAL
        if (ref.isFileUri() || ref.isLocalAbsolutePath()) {
            return openLocal(ref, sentenceType);
        }

        // REMOTE/FTP (legacy entry still based on settings)
        return openFtp(rawPath, sentenceType, searchPattern, toCompare);
    }

    private FtpTab openLocal(VirtualResourceRef ref,
                            @Nullable String sentenceType) {
        String localPath = toLocalPath(ref);

        try (FileService fs = new FileServiceFactory().createLocal()) {
            // First, try directory listing (same code path as directory handling)
            try {
                fs.list(localPath);

                LocalConnectionTabImpl tab = new LocalConnectionTabImpl(tabManager);
                tabManager.addTab(tab);
                tab.loadDirectory(localPath);
                return tab;
            } catch (Exception ignored) {
                // Not a directory -> fall through and try to read as file
            }

            // Then try to open as file
            FilePayload payload = fs.readFile(localPath);
            String content = new String(payload.getBytes(), payload.getCharset() != null ? payload.getCharset() : Charset.defaultCharset());
            return tabManager.openFileTab(new FtpManager(), content, sentenceType);
        } catch (Exception e) {
            return null;
        }
    }

    private FtpTab openFtp(String path,
                          @Nullable String sentenceType,
                          @Nullable String searchPattern,
                          @Nullable Boolean toCompare) {
        Settings settings = SettingsHelper.load();
        FtpManager ftpManager = new FtpManager();

        try {
            ftpManager.connect(settings.host, settings.user);

            String unquoted = de.bund.zrb.util.StringUtil.unquote(path);

            // 1) Try file
            try {
                FtpFileBuffer buffer = ftpManager.open(unquoted);
                if (buffer != null) {
                    return tabManager.openFileTab(ftpManager, buffer, sentenceType, searchPattern, toCompare);
                }
            } catch (Exception ignore) {
                // fall back to directory tab
            }

            ConnectionTabImpl tab = new ConnectionTabImpl(ftpManager, tabManager, searchPattern);
            tabManager.addTab(tab);
            tab.loadDirectory(unquoted);
            return tab;

        } catch (Exception e) {
            return null;
        }
    }

    private String toLocalPath(VirtualResourceRef ref) {
        if (ref.isFileUri()) {
            try {
                return new java.io.File(java.net.URI.create(ref.raw().trim())).getAbsolutePath();
            } catch (Exception ignore) {
                return ref.raw();
            }
        }
        return ref.normalizeLocalAbsolutePath();
    }
}
