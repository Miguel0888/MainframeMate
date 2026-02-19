package de.bund.zrb.files.impl.vfs.mvs;

import java.io.IOException;
import java.util.List;

/**
 * Strategy interface for MVS directory listing.
 * Multiple strategies can be chained to handle different server behaviors.
 */
public interface MvsListingStrategy {

    /**
     * Name of this strategy for logging.
     */
    String getName();

    /**
     * Try to list children using this strategy.
     *
     * @param queryPath the query path (e.g., 'USR042.*')
     * @param parentLocation the parent location for creating child locations
     * @return list of resources, or empty list if no results
     * @throws IOException if a non-recoverable error occurs
     */
    List<MvsVirtualResource> tryList(String queryPath, MvsLocation parentLocation) throws IOException;

    /**
     * Check if this strategy should be skipped based on the last result.
     * For example, if NLST returned 550, we should try LIST.
     */
    default boolean shouldContinueToNext(List<MvsVirtualResource> result, Exception lastError) {
        // Continue if result is empty and no fatal error
        return result.isEmpty();
    }
}

