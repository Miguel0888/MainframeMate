package de.bund.zrb.service.codeanalytics;

/**
 * Resolves a target name (e.g. "MYSUBPROG") to its source code.
 * <p>
 * Used by {@link CodeAnalyticsService#buildCallTree} for recursive call resolution.
 * Implementations may look up sources from NDV, local files, FTP, or cache.
 */
public interface SourceResolver {

    /**
     * Resolve a target name to source code.
     *
     * @param targetName the name of the called object (e.g. "MYSUBPROG", "IDCAMS")
     * @return the source code, or null if not resolvable
     */
    String resolve(String targetName);
}

