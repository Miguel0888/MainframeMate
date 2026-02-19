package de.bund.zrb.files.impl.vfs.mvs;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MVS-aware FTP client wrapper.
 * Handles MVS-specific listing strategies and quote normalization.
 */
public class MvsFtpClient implements AutoCloseable {

    private static final int DEFAULT_PAGE_SIZE = 200;

    private final FTPClient ftpClient;
    private final String host;
    private final String user;
    private volatile boolean connected = false;
    private volatile boolean mvsMode = false;

    public MvsFtpClient(String host, String user) {
        this.ftpClient = new FTPClient();
        this.host = host;
        this.user = user;
    }

    /**
     * Connect and login to the FTP server.
     */
    public void connect(String password, String encoding) throws IOException {
        ftpClient.setControlEncoding(encoding != null ? encoding : "UTF-8");
        ftpClient.connect(host);

        if (!ftpClient.login(user, password)) {
            throw new IOException("FTP login failed");
        }

        ftpClient.enterLocalPassiveMode();

        // Detect MVS mode
        String systemType = ftpClient.getSystemType();
        mvsMode = systemType != null && systemType.toUpperCase().contains("MVS");

        if (mvsMode) {
            System.out.println("[MvsFtpClient] Detected MVS/zOS system: " + systemType);
            ftpClient.configure(new FTPClientConfig(FTPClientConfig.SYST_MVS));
        }

        // Set ASCII transfer mode
        ftpClient.setFileType(FTP.ASCII_FILE_TYPE);

        connected = true;
    }

    /**
     * Check if connected in MVS mode.
     */
    public boolean isMvsMode() {
        return mvsMode;
    }

    /**
     * Check if connected.
     */
    public boolean isConnected() {
        return connected && ftpClient.isConnected();
    }

    /**
     * Get the underlying FTP client for advanced operations.
     */
    public FTPClient getFtpClient() {
        return ftpClient;
    }

    /**
     * List children of an MVS location with pagination support.
     * Uses MvsListingService with robust strategy chain.
     *
     * @param location the MVS location to list
     * @param pageSize max items per page
     * @param cancellation token to cancel loading
     * @param callback callback for each page of results
     */
    public void listChildren(MvsLocation location, int pageSize, AtomicBoolean cancellation,
                            PageCallback callback) throws IOException {

        if (!mvsMode) {
            System.out.println("[MvsFtpClient] Not in MVS mode, listing may not work correctly");
        }

        // Use MvsListingService with strategy chain
        MvsListingService listingService = new MvsListingService(ftpClient);
        listingService.listChildren(location, pageSize, cancellation, new MvsListingService.PageCallback() {
            @Override
            public void onPage(List<MvsVirtualResource> items, boolean isLast) {
                callback.onPage(items, isLast);
            }
        });
    }


    /**
     * Read file content as input stream.
     */
    public InputStream retrieveFileStream(String path) throws IOException {
        String normalized = MvsQuoteNormalizer.normalize(path);
        return ftpClient.retrieveFileStream(normalized);
    }

    /**
     * Complete pending command after stream operation.
     */
    public boolean completePendingCommand() throws IOException {
        return ftpClient.completePendingCommand();
    }

    /**
     * Get FTP reply string (for diagnostics).
     */
    public String getReplyString() {
        return ftpClient.getReplyString();
    }

    @Override
    public void close() throws IOException {
        if (connected) {
            try {
                ftpClient.logout();
            } catch (IOException ignored) {
            }
            try {
                ftpClient.disconnect();
            } catch (IOException ignored) {
            }
            connected = false;
        }
    }

    /**
     * Callback for paginated listing.
     */
    public interface PageCallback {
        /**
         * Called for each page of results.
         * @param items the items in this page
         * @param isLast true if this is the last page
         */
        void onPage(List<MvsVirtualResource> items, boolean isLast);
    }
}

