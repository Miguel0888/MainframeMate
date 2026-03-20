package de.bund.zrb.search.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.confluence.ConfluenceConnectionConfig;
import de.bund.zrb.confluence.ConfluenceRestClient;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.search.BackendSearchProvider;
import de.bund.zrb.search.SearchResult;
import de.bund.zrb.util.CredentialStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton live backend search provider for Confluence Data Center.
 * <p>
 * Reads configured Confluence entries from {@code settings.passwordEntries}
 * (category "Confluence") on each search call — no constructor wiring needed.
 * Credentials and certificates are resolved on-demand.
 * <p>
 * Uses CQL ({@code text~"query"}) via the Confluence REST API to search
 * across all accessible spaces per configured instance.
 */
public final class ConfluenceSearchProvider implements BackendSearchProvider {

    private static final Logger LOG = Logger.getLogger(ConfluenceSearchProvider.class.getName());
    private static final ConfluenceSearchProvider INSTANCE = new ConfluenceSearchProvider();

    private ConfluenceSearchProvider() {}

    public static ConfluenceSearchProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public SearchResult.SourceType getSourceType() {
        return SearchResult.SourceType.CONFLUENCE;
    }

    @Override
    public boolean isAvailable() {
        return !loadEntries(null).isEmpty();
    }

    @Override
    public List<SearchResult> search(String query, int maxResults, String networkZone) throws Exception {
        List<ConfEntry> entries = loadEntries(networkZone);
        if (entries.isEmpty()) return Collections.emptyList();

        List<SearchResult> results = new ArrayList<SearchResult>();

        for (ConfEntry entry : entries) {
            if (results.size() >= maxResults) break;
            try {
                ConfluenceRestClient client = buildClient(entry);
                String cql = "type=page AND text~\"" + query.replace("\"", "\\\"") + "\"";
                int limit = Math.min(maxResults - results.size(), 50);

                String json = client.searchJson(cql, 0, limit);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonArray arr = root.getAsJsonArray("results");
                if (arr == null) continue;

                for (JsonElement el : arr) {
                    if (results.size() >= maxResults) break;
                    JsonObject pg = el.getAsJsonObject();
                    JsonObject content = pg.has("content") ? pg.getAsJsonObject("content") : pg;

                    String id = content.has("id") ? content.get("id").getAsString() : "";
                    String title = content.has("title") ? content.get("title").getAsString() : "";
                    String spaceKey = "";
                    if (content.has("space") && content.getAsJsonObject("space").has("key")) {
                        spaceKey = content.getAsJsonObject("space").get("key").getAsString();
                    }

                    String excerpt = "";
                    if (pg.has("excerpt")) {
                        excerpt = pg.get("excerpt").getAsString()
                                .replaceAll("<[^>]+>", "")
                                .replaceAll("@@@hl@@@|@@@endhl@@@", "");
                        if (excerpt.length() > 300) excerpt = excerpt.substring(0, 300) + "…";
                    }

                    String docId = "confluence://" + id;
                    String path = spaceKey.isEmpty() ? entry.displayName
                            : spaceKey + " @ " + entry.displayName;

                    results.add(new SearchResult(
                            SearchResult.SourceType.CONFLUENCE,
                            docId,
                            title,
                            path,
                            excerpt,
                            0.8f,
                            docId,
                            spaceKey
                    ));
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[ConfluenceSearch] CQL search failed for " + entry.displayName, e);
                // Continue with other instances
            }
        }
        return results;
    }

    // ── Internal helpers ──────────────────────────────────────────

    /** Build a fresh ConfluenceRestClient for one entry. */
    private static ConfluenceRestClient buildClient(ConfEntry entry) {
        ConfluenceConnectionConfig config = new ConfluenceConnectionConfig(
                entry.url, entry.user, entry.password, entry.certAlias,
                15000, 30000);
        return new ConfluenceRestClient(config);
    }

    /** Load Confluence entries from settings, optionally filtered by networkZone. */
    private static List<ConfEntry> loadEntries(String networkZone) {
        Settings settings = SettingsHelper.load();
        List<ConfEntry> result = new ArrayList<ConfEntry>();
        for (Settings.PasswordEntryMeta meta : settings.passwordEntries) {
            if (!"Confluence".equalsIgnoreCase(meta.category)) continue;
            if (meta.url == null || meta.url.isEmpty()) continue;
            if (networkZone != null && !networkZone.equals(meta.networkZone)) continue;

            String user = "";
            String pass = "";
            if (meta.requiresLogin) {
                try {
                    String[] cred = CredentialStore.resolveIncludingEmpty("pwd:" + meta.id);
                    if (cred != null) {
                        user = cred[0];
                        pass = cred[1];
                    }
                } catch (Exception ignore) {}
            }

            String name = (meta.displayName != null && !meta.displayName.isEmpty())
                    ? meta.displayName : meta.id;
            result.add(new ConfEntry(meta.id, name, meta.url,
                    meta.certAlias != null ? meta.certAlias : "",
                    user, pass));
        }
        return result;
    }

    /** Lightweight holder for a Confluence entry from settings. */
    private static final class ConfEntry {
        final String id;
        final String displayName;
        final String url;
        final String certAlias;
        final String user;
        final String password;

        ConfEntry(String id, String displayName, String url,
                  String certAlias, String user, String password) {
            this.id = id;
            this.displayName = displayName;
            this.url = url;
            this.certAlias = certAlias;
            this.user = user;
            this.password = password;
        }
    }
}
