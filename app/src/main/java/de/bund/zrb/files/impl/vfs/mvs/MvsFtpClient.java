package de.bund.zrb.files.impl.vfs.mvs;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
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
     * List children of an MVS location with pagination support.
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

        String queryPath = location.getQueryPath();
        System.out.println("[MvsFtpClient] Listing: logical=" + location.getLogicalPath() +
                          ", query=" + queryPath + ", type=" + location.getType());

        // Cannot list MVS root without HLQ
        if (location.getType() == MvsLocationType.ROOT) {
            System.out.println("[MvsFtpClient] Cannot list ROOT - HLQ required");
            callback.onPage(Collections.<MvsVirtualResource>emptyList(), true);
            return;
        }

        // Try NLST first (more reliable on MVS)
        List<MvsVirtualResource> results = listViaNlst(location, queryPath, cancellation);

        if (results.isEmpty() && !cancellation.get()) {
            // Fallback: Try LIST
            System.out.println("[MvsFtpClient] NLST returned empty, trying LIST fallback");
            results = listViaList(location, queryPath, cancellation);
        }

        // Paginate results
        if (results.isEmpty()) {
            callback.onPage(Collections.<MvsVirtualResource>emptyList(), true);
            return;
        }

        int total = results.size();
        int offset = 0;

        while (offset < total && !cancellation.get()) {
            int end = Math.min(offset + pageSize, total);
            List<MvsVirtualResource> page = results.subList(offset, end);
            boolean isLast = (end >= total);

            callback.onPage(page, isLast);
            offset = end;

            if (!isLast) {
                // Small delay between pages to allow UI to update
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * List using NLST command.
     */
    private List<MvsVirtualResource> listViaNlst(MvsLocation parentLocation, String queryPath,
                                                  AtomicBoolean cancellation) throws IOException {
        String[] names = ftpClient.listNames(queryPath);

        if (names == null || names.length == 0) {
            System.out.println("[MvsFtpClient] NLST returned null/empty for: " + queryPath +
                              " - reply: " + ftpClient.getReplyString());
            return Collections.emptyList();
        }

        System.out.println("[MvsFtpClient] NLST returned " + names.length + " entries for: " + queryPath);

        List<MvsVirtualResource> results = new ArrayList<MvsVirtualResource>();
        String parentUnquoted = MvsQuoteNormalizer.unquote(parentLocation.getLogicalPath()).toUpperCase();

        for (String name : names) {
            if (cancellation.get()) {
                break;
            }

            if (name == null || name.trim().isEmpty()) {
                continue;
            }

            String trimmed = name.trim();
            String unquoted = MvsQuoteNormalizer.unquote(trimmed).toUpperCase();

            // Skip if name equals parent (server returned the HLQ/dataset itself)
            if (unquoted.equals(parentUnquoted)) {
                System.out.println("[MvsFtpClient] Skipping parent entry: " + trimmed);
                continue;
            }

            // Create child location
            MvsLocation childLocation = createChildLocation(parentLocation, trimmed, parentUnquoted);

            if (childLocation != null && !childLocation.equals(parentLocation)) {
                MvsVirtualResource resource = MvsVirtualResource.builder(childLocation).build();
                results.add(resource);
            }
        }

        return results;
    }

    /**
     * List using LIST command (fallback).
     */
    private List<MvsVirtualResource> listViaList(MvsLocation parentLocation, String queryPath,
                                                  AtomicBoolean cancellation) throws IOException {
        FTPFile[] files = ftpClient.listFiles(queryPath);

        if (files == null || files.length == 0) {
            System.out.println("[MvsFtpClient] LIST returned null/empty for: " + queryPath +
                              " - reply: " + ftpClient.getReplyString());
            return Collections.emptyList();
        }

        System.out.println("[MvsFtpClient] LIST returned " + files.length + " entries for: " + queryPath);

        List<MvsVirtualResource> results = new ArrayList<MvsVirtualResource>();
        String parentUnquoted = MvsQuoteNormalizer.unquote(parentLocation.getLogicalPath()).toUpperCase();

        for (FTPFile file : files) {
            if (cancellation.get()) {
                break;
            }

            String name = file.getName();
            if (name == null || name.trim().isEmpty()) {
                continue;
            }

            String trimmed = name.trim();
            String unquoted = MvsQuoteNormalizer.unquote(trimmed).toUpperCase();

            // Skip parent entry
            if (unquoted.equals(parentUnquoted)) {
                continue;
            }

            // Create child location
            MvsLocation childLocation = createChildLocation(parentLocation, trimmed, parentUnquoted);

            if (childLocation != null && !childLocation.equals(parentLocation)) {
                MvsVirtualResource.Builder builder = MvsVirtualResource.builder(childLocation);

                // Add metadata if available
                if (file.getSize() >= 0) {
                    builder.size(file.getSize());
                }

                Calendar timestamp = file.getTimestamp();
                if (timestamp != null) {
                    builder.lastModified(timestamp.getTimeInMillis());
                }

                results.add(builder.build());
            }
        }

        return results;
    }

    /**
     * Create a child location from a listing entry.
     */
    private MvsLocation createChildLocation(MvsLocation parent, String childName, String parentUnquoted) {
        String unquotedChild = MvsQuoteNormalizer.unquote(childName);
        String unquotedChildUpper = unquotedChild.toUpperCase();

        // Determine the actual name to use
        String actualName;

        // Check if child is fully qualified (starts with parent + ".")
        if (!parentUnquoted.isEmpty() && unquotedChildUpper.startsWith(parentUnquoted + ".")) {
            // Extract relative part
            actualName = unquotedChild.substring(parentUnquoted.length() + 1);
        } else {
            actualName = unquotedChild;
        }

        if (actualName.isEmpty()) {
            return null;
        }

        // Use parent's createChild method for proper type determination
        return parent.createChild(actualName);
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

