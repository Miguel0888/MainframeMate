package de.bund.zrb.service;

import de.bund.zrb.ndv.NdvObjectInfo;
import de.bund.zrb.ndv.NdvService;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrates building a {@link NaturalDependencyGraph} for a library by downloading
 * all Natural source objects from the NDV server and analyzing them.
 * <p>
 * This class bridges the NDV server access (source download) with the client-side
 * dependency graph construction. It mirrors the NaturalONE approach where:
 * <ol>
 *   <li>Sources are downloaded/synced from the server</li>
 *   <li>Each source is parsed locally to extract external references</li>
 *   <li>A bidirectional dependency graph is built (callers ↔ callees)</li>
 * </ol>
 * <p>
 * Usage (typically from a SwingWorker):
 * <pre>
 *   NaturalDependencyGraphBuilder builder = new NaturalDependencyGraphBuilder(ndvService);
 *   NaturalDependencyGraph graph = builder.buildForLibrary("MYLIB", progressCallback);
 * </pre>
 */
public class NaturalDependencyGraphBuilder {

    private static final Logger LOG = Logger.getLogger(NaturalDependencyGraphBuilder.class.getName());

    private final NdvService ndvService;

    public NaturalDependencyGraphBuilder(NdvService ndvService) {
        this.ndvService = ndvService;
    }

    /**
     * Callback for progress reporting during graph construction.
     */
    public interface ProgressCallback {
        /**
         * Called when progress is made.
         * @param current number of objects processed so far
         * @param total   total number of objects to process (may be -1 if unknown)
         * @param objectName name of the object currently being processed
         */
        void onProgress(int current, int total, String objectName);

        /**
         * Called when the build is complete.
         * @param graph the completed dependency graph
         */
        void onComplete(NaturalDependencyGraph graph);

        /**
         * Called if an error occurs during the build.
         */
        void onError(String message, Exception e);
    }

    /**
     * Build a dependency graph for a library by listing and downloading all source objects.
     * This method is blocking — call it from a background thread.
     *
     * @param library  the Natural library name
     * @param callback optional progress callback (may be null)
     * @return the built dependency graph
     */
    public NaturalDependencyGraph buildForLibrary(String library, ProgressCallback callback) {
        NaturalDependencyGraph graph = new NaturalDependencyGraph();
        graph.setLibrary(library);

        try {
            // Step 1: List all source objects in the library
            // We want source (kind=1) objects of all types (type=0)
            final List<NdvObjectInfo> objects = new ArrayList<NdvObjectInfo>();

            ndvService.listObjectsProgressive(library, "*", 1, 0,
                    new de.bund.zrb.ndv.NdvClient.PageCallback() {
                        @Override
                        public boolean onPage(List<NdvObjectInfo> page, int totalSoFar) {
                            objects.addAll(page);
                            return true; // continue loading
                        }
                    });

            LOG.info("[GraphBuilder] Library '" + library + "': found " + objects.size() + " source objects");

            if (objects.isEmpty()) {
                graph.build();
                if (callback != null) callback.onComplete(graph);
                return graph;
            }

            // Step 2: Download each source and add to graph
            int processed = 0;
            int total = objects.size();
            int errors = 0;

            for (NdvObjectInfo objInfo : objects) {
                String objName = objInfo.getName();
                try {
                    if (callback != null) {
                        callback.onProgress(processed, total, objName);
                    }

                    // Only process Natural source types (NSP, NSS, NSL, NSA, etc.)
                    if (isNaturalSourceType(objInfo)) {
                        String source = ndvService.readSource(library, objInfo);
                        if (source != null && !source.isEmpty()) {
                            graph.addSource(library, objName, source);
                        }
                    }
                } catch (Exception e) {
                    errors++;
                    LOG.log(Level.FINE, "Failed to read source for " + objName + " in " + library, e);
                }
                processed++;
            }

            LOG.info("[GraphBuilder] Library '" + library + "': analyzed " + (processed - errors)
                    + " sources (" + errors + " errors)");

            // Step 3: Build passive XRefs
            graph.build();

            if (callback != null) {
                callback.onComplete(graph);
            }

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to build dependency graph for library: " + library, e);
            if (callback != null) {
                callback.onError("Fehler beim Aufbau des Abhängigkeitsgraphen: " + e.getMessage(), e);
            }
            // Return partial graph
            graph.build();
        }

        return graph;
    }

    /**
     * Check if the object is a Natural source type that should be parsed for dependencies.
     * Natural object types:
     *   P = Program, S = Subprogram, N = Subroutine, H = Helproutine,
     *   C = Copycode, L = LDA, A = PDA, G = GDA, M = Map,
     *   T = Text, 4 = Function, D = Dialog, Z = Class, etc.
     */
    private boolean isNaturalSourceType(NdvObjectInfo objInfo) {
        String ext = objInfo.getTypeExtension();
        if (ext == null) return true; // assume source if unknown

        String upper = ext.toUpperCase();
        // Natural source extensions: NSP, NSS, NSN, NSH, NSC, NSL, NSA, NSG, NSM, NS4, NSD
        // Also: .NS7 (text), etc.
        return upper.startsWith("NS") || upper.equals("NAT");
    }
}

