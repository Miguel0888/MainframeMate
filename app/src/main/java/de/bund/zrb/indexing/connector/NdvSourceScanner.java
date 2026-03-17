package de.bund.zrb.indexing.connector;

import de.bund.zrb.indexing.model.IndexSource;
import de.bund.zrb.indexing.model.ScannedItem;
import de.bund.zrb.indexing.port.SourceScanner;
import de.bund.zrb.ndv.NdvClient;
import de.bund.zrb.ndv.NdvObjectInfo;
import de.bund.zrb.ndv.NdvService;
import de.bund.zrb.ndv.core.api.ObjectKind;
import de.bund.zrb.service.NdvSourceCacheService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Scans NDV (Natural Development Server) libraries for indexable Natural sources.
 *
 * Uses NdvService to list objects in each library (scope path).
 * Content is fetched from the NdvSourceCacheService memory cache or downloaded
 * from the NDV server on demand.
 *
 * The NdvService must be set before scanning (it's wired by NdvConnectionTab
 * when the user connects to an NDV server).
 */
public class NdvSourceScanner implements SourceScanner {

    private static final Logger LOG = Logger.getLogger(NdvSourceScanner.class.getName());

    private volatile NdvService ndvService;

    /**
     * Set the active NDV service. Called by NdvConnectionTab when a connection is established.
     */
    public void setNdvService(NdvService ndvService) {
        this.ndvService = ndvService;
    }

    public NdvService getNdvService() {
        return ndvService;
    }

    @Override
    public List<ScannedItem> scan(IndexSource source) throws Exception {
        if (ndvService == null) {
            throw new IllegalStateException(
                    "NDV nicht verbunden – bitte zuerst einen NDV-Verbindungs-Tab öffnen.");
        }

        List<ScannedItem> items = new ArrayList<>();

        for (String scopePath : source.getScopePaths()) {
            String library = scopePath.toUpperCase().trim();
            if (library.isEmpty()) continue;

            LOG.info("[Indexing] NDV scan: listing objects in " + library);

            final List<NdvObjectInfo> objects = new ArrayList<>();
            try {
                ndvService.listObjectsProgressive(library, "*",
                        ObjectKind.SOURCE, 0,
                        new NdvClient.PageCallback() {
                            @Override
                            public boolean onPage(List<NdvObjectInfo> pageItems, int totalSoFar) {
                                objects.addAll(pageItems);
                                return true; // continue loading
                            }
                        });
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[Indexing] NDV scan failed for library: " + library, e);
                throw new IllegalStateException("NDV-Scan fehlgeschlagen für Bibliothek '"
                        + library + "': " + e.getMessage(), e);
            }

            for (NdvObjectInfo obj : objects) {
                if (!isNaturalSourceType(obj)) continue;

                String path = NdvSourceCacheService.documentId(
                        library, obj.getName(), obj.getTypeExtension());
                long lastModified = System.currentTimeMillis(); // NDV doesn't provide epoch timestamp
                long size = obj.getSourceSize();
                String mimeType = mimeTypeForExtension(obj.getTypeExtension());

                items.add(new ScannedItem(path, lastModified, size, false, mimeType));
            }

            LOG.info("[Indexing] NDV scan: found " + items.size() + " source objects in " + library);
        }

        return items;
    }

    @Override
    public byte[] fetchContent(IndexSource source, String itemPath) throws Exception {
        if (ndvService == null) {
            throw new IllegalStateException("NDV nicht verbunden");
        }

        // itemPath format: "NDV:LIBRARY/OBJNAME.EXT"
        String withoutPrefix = itemPath.startsWith("NDV:") ? itemPath.substring(4) : itemPath;
        int slashIdx = withoutPrefix.indexOf('/');
        if (slashIdx < 0) {
            throw new IllegalArgumentException("Ungültiger NDV-Pfad: " + itemPath);
        }

        String library = withoutPrefix.substring(0, slashIdx);
        String nameWithExt = withoutPrefix.substring(slashIdx + 1);

        int dotIdx = nameWithExt.lastIndexOf('.');
        String objectName = dotIdx >= 0 ? nameWithExt.substring(0, dotIdx) : nameWithExt;
        String extension = dotIdx >= 0 ? nameWithExt.substring(dotIdx + 1) : "";

        // Try memory cache first (instant, O(1))
        NdvSourceCacheService cacheService = NdvSourceCacheService.getInstance();
        String cached = cacheService.getCachedSource(library, objectName);
        if (cached != null) {
            return cached.getBytes(StandardCharsets.UTF_8);
        }

        // Fetch from NDV server
        NdvObjectInfo objInfo = NdvObjectInfo.forBookmark(objectName, extension);
        String sourceText = ndvService.readSource(library, objInfo);
        if (sourceText != null && !sourceText.isEmpty()) {
            // Cache for future use (memory + H2 + Lucene)
            cacheService.cacheSource(library, objectName, extension, sourceText);
            return sourceText.getBytes(StandardCharsets.UTF_8);
        }

        return new byte[0];
    }

    // ─── Helpers ───

    private static boolean isNaturalSourceType(NdvObjectInfo objInfo) {
        String ext = objInfo.getTypeExtension();
        if (ext == null) return true;
        String upper = ext.toUpperCase();
        return upper.startsWith("NS") || upper.equals("NAT");
    }

    private static String mimeTypeForExtension(String ext) {
        if (ext == null) return "text/plain";
        String upper = ext.toUpperCase();
        if (upper.equals("NSP")) return "text/x-natural-program";
        if (upper.equals("NSS")) return "text/x-natural-subprogram";
        if (upper.equals("NSN")) return "text/x-natural-subroutine";
        if (upper.equals("NSH")) return "text/x-natural-helproutine";
        if (upper.equals("NSC")) return "text/x-natural-copycode";
        if (upper.equals("NSL")) return "text/x-natural-lda";
        if (upper.equals("NSA")) return "text/x-natural-pda";
        if (upper.equals("NSG")) return "text/x-natural-gda";
        if (upper.equals("NSM")) return "text/x-natural-map";
        if (upper.equals("NS4")) return "text/x-natural-function";
        if (upper.equals("NSD")) return "text/x-natural-dialog";
        return "text/x-natural";
    }
}

