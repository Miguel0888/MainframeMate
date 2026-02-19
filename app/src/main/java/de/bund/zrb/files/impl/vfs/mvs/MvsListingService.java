package de.bund.zrb.files.impl.vfs.mvs;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPListParseEngine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for listing MVS datasets/members with robust strategy chain.
 *
 * Strategy order:
 * 1. NLST (names only) - fastest
 * 2. LIST via FTPListParseEngine (paged, parsed)
 * 3. LIST Raw fallback (parse raw listing lines)
 *
 * If one strategy returns empty/error, the next is tried automatically.
 */
public class MvsListingService {

    private static final int DEFAULT_PAGE_SIZE = 200;

    private final FTPClient ftpClient;

    public MvsListingService(FTPClient ftpClient) {
        this.ftpClient = ftpClient;
    }

    /**
     * List children of an MVS location with pagination.
     * Tries multiple strategies until one succeeds.
     */
    public void listChildren(MvsLocation location, int pageSize, AtomicBoolean cancellation,
                            PageCallback callback) throws IOException {

        if (location.getType() == MvsLocationType.ROOT) {
            System.out.println("[MvsListingService] Cannot list ROOT - HLQ required");
            callback.onPage(Collections.<MvsVirtualResource>emptyList(), true);
            return;
        }

        if (location.getType() == MvsLocationType.MEMBER) {
            System.out.println("[MvsListingService] Cannot list MEMBER - it's a file");
            callback.onPage(Collections.<MvsVirtualResource>emptyList(), true);
            return;
        }

        String queryPath = location.getQueryPath();
        System.out.println("[MvsListingService] Listing: logicalPath=" + location.getLogicalPath() +
                          ", queryPath=" + queryPath + ", type=" + location.getType());

        // Build query candidates
        List<String> queryCandidates = buildQueryCandidates(queryPath);

        List<MvsVirtualResource> results = Collections.emptyList();

        // Try each candidate with each strategy
        for (String candidate : queryCandidates) {
            if (cancellation.get()) {
                return;
            }

            // Strategy 1: NLST
            results = tryNlst(candidate, location, cancellation);
            if (!results.isEmpty()) {
                System.out.println("[MvsListingService] NLST succeeded with " + results.size() + " results for: " + candidate);
                break;
            }

            // Strategy 2: LIST with ParseEngine (paged)
            results = tryListPaged(candidate, location, pageSize, cancellation, callback);
            if (!results.isEmpty()) {
                System.out.println("[MvsListingService] LIST (paged) succeeded with " + results.size() + " results for: " + candidate);
                return; // Paged already delivered via callback
            }

            // Strategy 3: LIST Raw fallback
            results = tryListRaw(candidate, location, cancellation);
            if (!results.isEmpty()) {
                System.out.println("[MvsListingService] LIST (raw) succeeded with " + results.size() + " results for: " + candidate);
                break;
            }
        }

        // Deliver results via pagination
        deliverResultsPaged(results, pageSize, cancellation, callback);
    }

    /**
     * Build query candidates to try.
     */
    private List<String> buildQueryCandidates(String queryPath) {
        List<String> candidates = new ArrayList<String>();

        // Primary: as-is
        candidates.add(queryPath);

        // Try uppercase variant
        String unquoted = MvsQuoteNormalizer.unquote(queryPath);
        String uppercase = unquoted.toUpperCase();
        if (!uppercase.equals(unquoted)) {
            candidates.add(MvsQuoteNormalizer.normalize(uppercase));
        }

        // Try unquoted variant (some servers don't like quotes)
        if (!unquoted.equals(queryPath)) {
            candidates.add(unquoted);
        }

        return candidates;
    }

    /**
     * Strategy 1: NLST (names only).
     */
    private List<MvsVirtualResource> tryNlst(String queryPath, MvsLocation parentLocation,
                                             AtomicBoolean cancellation) {
        try {
            System.out.println("[MvsListingService] Trying NLST: " + queryPath);
            String[] names = ftpClient.listNames(queryPath);

            int replyCode = ftpClient.getReplyCode();
            String replyString = ftpClient.getReplyString();

            if (names == null || names.length == 0) {
                // 550 or empty is not fatal - just means no results with this strategy
                System.out.println("[MvsListingService] NLST empty/null for: " + queryPath +
                                  " (reply=" + replyCode + ": " + replyString.trim() + ")");
                return Collections.emptyList();
            }

            System.out.println("[MvsListingService] NLST returned " + names.length + " entries");
            return buildResourcesFromNames(names, parentLocation, cancellation);

        } catch (IOException e) {
            System.out.println("[MvsListingService] NLST failed for: " + queryPath + " - " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Strategy 2: LIST with FTPListParseEngine (paged).
     */
    private List<MvsVirtualResource> tryListPaged(String queryPath, MvsLocation parentLocation,
                                                   int pageSize, AtomicBoolean cancellation,
                                                   PageCallback callback) {
        try {
            System.out.println("[MvsListingService] Trying LIST (paged): " + queryPath);
            FTPListParseEngine engine = ftpClient.initiateListParsing(queryPath);

            if (engine == null) {
                System.out.println("[MvsListingService] LIST engine null for: " + queryPath);
                return Collections.emptyList();
            }

            List<MvsVirtualResource> allResults = new ArrayList<MvsVirtualResource>();
            boolean isFirst = true;

            while (engine.hasNext() && !cancellation.get()) {
                FTPFile[] page = engine.getNext(pageSize);
                if (page == null || page.length == 0) {
                    break;
                }

                List<MvsVirtualResource> pageResults = buildResourcesFromFtpFiles(page, parentLocation, cancellation);
                allResults.addAll(pageResults);

                // Deliver first page immediately
                if (isFirst && !pageResults.isEmpty()) {
                    System.out.println("[MvsListingService] Delivering first page: " + pageResults.size() + " items");
                    callback.onPage(pageResults, !engine.hasNext());
                    isFirst = false;
                } else if (!pageResults.isEmpty()) {
                    callback.onPage(pageResults, !engine.hasNext());
                }
            }

            if (allResults.isEmpty()) {
                System.out.println("[MvsListingService] LIST (paged) returned empty for: " + queryPath);
            }

            return allResults;

        } catch (IOException e) {
            System.out.println("[MvsListingService] LIST (paged) failed for: " + queryPath + " - " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Strategy 3: LIST with raw line parsing (fallback).
     */
    private List<MvsVirtualResource> tryListRaw(String queryPath, MvsLocation parentLocation,
                                                 AtomicBoolean cancellation) {
        try {
            System.out.println("[MvsListingService] Trying LIST (raw): " + queryPath);
            FTPFile[] files = ftpClient.listFiles(queryPath);

            if (files == null || files.length == 0) {
                System.out.println("[MvsListingService] LIST (raw) empty for: " + queryPath +
                                  " (reply=" + ftpClient.getReplyCode() + ")");
                return Collections.emptyList();
            }

            // Check for unparseable entries and try to extract from raw listing
            List<String> names = new ArrayList<String>();
            for (FTPFile file : files) {
                if (cancellation.get()) {
                    break;
                }

                String name = file.getName();
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                } else if (file.getRawListing() != null) {
                    // Try to extract name from raw listing
                    String extracted = extractNameFromRawListing(file.getRawListing());
                    if (extracted != null && !extracted.isEmpty()) {
                        names.add(extracted);
                    }
                }
            }

            if (names.isEmpty()) {
                return Collections.emptyList();
            }

            System.out.println("[MvsListingService] LIST (raw) extracted " + names.size() + " names");
            return buildResourcesFromNames(names.toArray(new String[0]), parentLocation, cancellation);

        } catch (IOException e) {
            System.out.println("[MvsListingService] LIST (raw) failed for: " + queryPath + " - " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extract dataset/member name from raw MVS listing line.
     * MVS listing format varies, but typically the dataset name is the last "word".
     */
    private String extractNameFromRawListing(String rawLine) {
        if (rawLine == null || rawLine.isEmpty()) {
            return null;
        }

        String trimmed = rawLine.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // For MVS, the dataset name is often at the end of the line
        // Example formats:
        // "Volume Unit    Referred Ext Used Recfm Lrecl BlkSz Dsorg Dsname"
        // "MIGRAT  ... USERID.DATASET"

        String[] parts = trimmed.split("\\s+");
        if (parts.length > 0) {
            String lastPart = parts[parts.length - 1];
            // Validate it looks like a dataset name
            if (isValidDatasetName(lastPart)) {
                return lastPart;
            }
        }

        return null;
    }

    /**
     * Check if a string looks like a valid MVS dataset name.
     */
    private boolean isValidDatasetName(String name) {
        if (name == null || name.isEmpty() || name.length() > 44) {
            return false;
        }

        // Must contain only valid characters: A-Z, 0-9, @, #, $, ., (, )
        for (char c : name.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '.' && c != '@' && c != '#' && c != '$' &&
                c != '(' && c != ')') {
                return false;
            }
        }

        return true;
    }

    /**
     * Build MvsVirtualResource list from NLST names.
     */
    private List<MvsVirtualResource> buildResourcesFromNames(String[] names, MvsLocation parentLocation,
                                                              AtomicBoolean cancellation) {
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

            // Skip parent entry
            if (unquoted.equals(parentUnquoted)) {
                System.out.println("[MvsListingService] Skipping parent entry: " + trimmed);
                continue;
            }

            MvsLocation childLocation = createChildLocation(parentLocation, trimmed, parentUnquoted);
            if (childLocation != null && !childLocation.equals(parentLocation)) {
                results.add(MvsVirtualResource.builder(childLocation).build());
            }
        }

        return results;
    }

    /**
     * Build MvsVirtualResource list from FTPFile array.
     */
    private List<MvsVirtualResource> buildResourcesFromFtpFiles(FTPFile[] files, MvsLocation parentLocation,
                                                                 AtomicBoolean cancellation) {
        List<MvsVirtualResource> results = new ArrayList<MvsVirtualResource>();
        String parentUnquoted = MvsQuoteNormalizer.unquote(parentLocation.getLogicalPath()).toUpperCase();

        for (FTPFile file : files) {
            if (cancellation.get()) {
                break;
            }

            String name = file.getName();
            if (name == null || name.trim().isEmpty()) {
                // Try raw listing
                if (file.getRawListing() != null) {
                    name = extractNameFromRawListing(file.getRawListing());
                }
            }

            if (name == null || name.trim().isEmpty()) {
                continue;
            }

            String trimmed = name.trim();
            String unquoted = MvsQuoteNormalizer.unquote(trimmed).toUpperCase();

            // Skip parent entry
            if (unquoted.equals(parentUnquoted)) {
                continue;
            }

            MvsLocation childLocation = createChildLocation(parentLocation, trimmed, parentUnquoted);
            if (childLocation != null && !childLocation.equals(parentLocation)) {
                MvsVirtualResource.Builder builder = MvsVirtualResource.builder(childLocation);

                if (file.getSize() >= 0) {
                    builder.size(file.getSize());
                }
                if (file.getTimestamp() != null) {
                    builder.lastModified(file.getTimestamp().getTimeInMillis());
                }

                results.add(builder.build());
            }
        }

        return results;
    }

    /**
     * Create child location from listing entry.
     */
    private MvsLocation createChildLocation(MvsLocation parent, String childName, String parentUnquoted) {
        String unquotedChild = MvsQuoteNormalizer.unquote(childName);
        String unquotedChildUpper = unquotedChild.toUpperCase();

        String actualName;
        if (!parentUnquoted.isEmpty() && unquotedChildUpper.startsWith(parentUnquoted + ".")) {
            actualName = unquotedChild.substring(parentUnquoted.length() + 1);
        } else {
            actualName = unquotedChild;
        }

        if (actualName.isEmpty()) {
            return null;
        }

        return parent.createChild(actualName);
    }

    /**
     * Deliver results via pagination.
     */
    private void deliverResultsPaged(List<MvsVirtualResource> results, int pageSize,
                                     AtomicBoolean cancellation, PageCallback callback) {
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
     * Callback for paginated results.
     */
    public interface PageCallback {
        void onPage(List<MvsVirtualResource> items, boolean isLast);
    }
}

