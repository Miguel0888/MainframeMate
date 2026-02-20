package de.bund.zrb.mcp.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parsed metadata for a single MCP server entry from the registry API.
 * <p>Robust parsing: tries multiple field name variants (camelCase and snake_case)
 * and handles both flat and nested response structures.</p>
 */
public class McpRegistryServerInfo {

    private static final java.util.Set<String> KNOWN_PUBLISHERS = new java.util.HashSet<>(java.util.Arrays.asList(
            "modelcontextprotocol", "anthropic", "github", "microsoft", "google",
            "aws", "cloudflare", "stripe", "linear", "sentry", "gitlab",
            "jetbrains", "docker", "vercel", "supabase", "mongodb", "elastic",
            "datadog", "grafana", "postman", "notion", "slack", "discord",
            "openai", "browserbase", "chromiumdevtools", "chromedevtools"
    ));

    private final String name;
    private final String title;
    private final String description;
    private final String status;
    private final String latestVersion;
    private final String repositoryUrl;
    private final boolean isOfficial;
    private final List<PackageInfo> packages;
    private final List<RemoteInfo> remotes;
    private final List<VariableInfo> variables;
    private final List<HeaderInfo> headers;

    private McpRegistryServerInfo(String name, String title, String description, String status,
                                  String latestVersion, String repositoryUrl, boolean isOfficial,
                                  List<PackageInfo> packages,
                                  List<RemoteInfo> remotes, List<VariableInfo> variables,
                                  List<HeaderInfo> headers) {
        this.name = name;
        this.title = title;
        this.description = description;
        this.status = status;
        this.latestVersion = latestVersion;
        this.repositoryUrl = repositoryUrl;
        this.isOfficial = isOfficial;
        this.packages = packages;
        this.remotes = remotes;
        this.variables = variables;
        this.headers = headers;
    }

    // ── Getters ─────────────────────────────────────────────────────

    public String getName() { return name; }
    public String getTitle() { return title != null && !title.isEmpty() ? title : name; }
    public String getDescription() { return description != null ? description : ""; }
    public String getStatus() { return status != null ? status : "active"; }
    public String getLatestVersion() { return latestVersion; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public boolean isOfficial() { return isOfficial; }
    public List<PackageInfo> getPackages() { return packages; }
    public List<RemoteInfo> getRemotes() { return remotes; }
    public List<VariableInfo> getVariables() { return variables; }
    public List<HeaderInfo> getHeaders() { return headers; }

    public boolean isDeprecated() { return "deprecated".equalsIgnoreCase(status); }
    public boolean isDeleted() { return "deleted".equalsIgnoreCase(status); }
    public boolean hasRemotes() { return remotes != null && !remotes.isEmpty(); }
    public boolean hasPackages() { return packages != null && !packages.isEmpty(); }

    /**
     * Returns true if this server is from a well-known/trusted publisher.
     */
    public boolean isKnownPublisher() {
        if (name == null) return false;
        // Extract publisher from name like "io.github.modelcontextprotocol/fetch"
        String lower = name.toLowerCase();
        for (String known : KNOWN_PUBLISHERS) {
            if (lower.contains(known)) return true;
        }
        return false;
    }

    /**
     * Sort priority: lower = more prominent.
     * 0 = official + known publisher, 1 = known publisher, 2 = official, 3 = normal, 9 = deprecated/deleted
     */
    public int getSortPriority() {
        if (isDeleted()) return 9;
        if (isDeprecated()) return 8;
        boolean known = isKnownPublisher();
        if (isOfficial && known) return 0;
        if (known) return 1;
        if (isOfficial) return 2;
        return 3;
    }

    // ── Parsing ─────────────────────────────────────────────────────

    /**
     * Parse a server entry from the registry list endpoint.
     * The official API wraps each entry as: {@code {"server": {...}, "_meta": {...}}}
     */
    public static McpRegistryServerInfo fromListEntry(JsonObject obj) {
        // Unwrap "server" envelope if present; keep original for _meta
        JsonObject envelope = obj;
        JsonObject serverObj = getObj(obj, "server");
        if (serverObj != null) obj = serverObj;

        String name = getStr(obj, "name");
        String title = getStr(obj, "title", "display_name");
        String description = getStr(obj, "description");
        String status = getStr(obj, "status");
        String version = getStr(obj, "version");

        // Read status + official flag from _meta
        boolean official = false;
        {
            JsonObject meta = getObj(envelope, "_meta");
            if (meta != null) {
                JsonObject offObj = getObj(meta, "io.modelcontextprotocol.registry/official");
                if (offObj != null) {
                    official = true;
                    String metaStatus = getStr(offObj, "status");
                    if (status == null && metaStatus != null) status = metaStatus;
                }
            }
        }

        // Extract repository URL
        String repoUrl = null;
        JsonObject repoObj = getObj(obj, "repository");
        if (repoObj != null) repoUrl = getStr(repoObj, "url");

        List<PackageInfo> pkgs = new ArrayList<>();
        List<RemoteInfo> rems = new ArrayList<>();

        // Try nested version_detail (official registry format)
        JsonObject vd = getObj(obj, "version_detail");
        if (vd != null) {
            if (version == null) version = getStr(vd, "version");
            if (description == null || description.isEmpty()) description = getStr(vd, "description");
            if (title == null || title.isEmpty()) title = getStr(vd, "title", "display_name");
            pkgs.addAll(parsePackages(vd));
            rems.addAll(parseRemotes(vd));
        }

        // Also try top-level packages/remotes
        pkgs.addAll(parsePackages(obj));
        rems.addAll(parseRemotes(obj));

        return new McpRegistryServerInfo(name, title, description, status, version,
                repoUrl, official,
                pkgs, rems, Collections.<VariableInfo>emptyList(), Collections.<HeaderInfo>emptyList());
    }

    /**
     * Parse a full server detail from the versions/latest endpoint.
     * The official API wraps as: {@code {"server": {...}, "_meta": {...}}}
     */
    public static McpRegistryServerInfo fromDetail(JsonObject obj) {
        // Unwrap "server" envelope if present; keep original for _meta
        JsonObject envelope = obj;
        JsonObject serverObj = getObj(obj, "server");
        if (serverObj != null) obj = serverObj;

        String name = getStr(obj, "name");
        String title = getStr(obj, "title", "display_name");
        String description = getStr(obj, "description");
        String status = getStr(obj, "status");
        String version = getStr(obj, "version");

        // Try reading status + official flag from _meta
        boolean officialFlag = false;
        {
            JsonObject meta = getObj(envelope, "_meta");
            if (meta != null) {
                JsonObject offObj = getObj(meta, "io.modelcontextprotocol.registry/official");
                if (offObj != null) {
                    officialFlag = true;
                    String metaStatus = getStr(offObj, "status");
                    if (status == null && metaStatus != null) status = metaStatus;
                }
            }
        }

        // Extract repository URL
        String repoUrl = null;
        JsonObject repoObj = getObj(obj, "repository");
        if (repoObj != null) repoUrl = getStr(repoObj, "url");

        // Try nested version_detail
        JsonObject vd = getObj(obj, "version_detail");
        if (vd != null) {
            if (version == null) version = getStr(vd, "version");
            if (description == null || description.isEmpty()) description = getStr(vd, "description");
            if (title == null || title.isEmpty()) title = getStr(vd, "title", "display_name");
        }

        List<PackageInfo> pkgs = new ArrayList<>();
        pkgs.addAll(parsePackages(obj));
        if (vd != null) pkgs.addAll(parsePackages(vd));

        List<RemoteInfo> rems = new ArrayList<>();
        rems.addAll(parseRemotes(obj));
        if (vd != null) rems.addAll(parseRemotes(vd));

        List<VariableInfo> vars = new ArrayList<>();
        vars.addAll(parseVariables(obj));
        if (vd != null) vars.addAll(parseVariables(vd));
        for (RemoteInfo rem : rems) vars.addAll(rem.getVariables());

        List<HeaderInfo> hdrs = new ArrayList<>();
        hdrs.addAll(parseHeaders(obj));
        if (vd != null) hdrs.addAll(parseHeaders(vd));
        for (RemoteInfo rem : rems) hdrs.addAll(rem.getHeaders());

        return new McpRegistryServerInfo(name, title, description, status, version,
                repoUrl, officialFlag,
                pkgs, rems, vars, hdrs);
    }

    // ── Robust field access ─────────────────────────────────────────

    private static String getStr(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                return obj.get(key).getAsString();
            }
        }
        return null;
    }

    private static JsonObject getObj(JsonObject obj, String key) {
        if (obj.has(key) && obj.get(key).isJsonObject()) {
            return obj.getAsJsonObject(key);
        }
        return null;
    }

    private static JsonArray getArr(JsonObject obj, String key) {
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            return obj.getAsJsonArray(key);
        }
        return null;
    }

    private static List<PackageInfo> parsePackages(JsonObject obj) {
        List<PackageInfo> result = new ArrayList<>();
        JsonArray arr = getArr(obj, "packages");
        if (arr != null) {
            for (JsonElement el : arr) {
                if (el.isJsonObject()) result.add(PackageInfo.parse(el.getAsJsonObject()));
            }
        }
        return result;
    }

    private static List<RemoteInfo> parseRemotes(JsonObject obj) {
        List<RemoteInfo> result = new ArrayList<>();
        JsonArray arr = getArr(obj, "remotes");
        if (arr != null) {
            for (JsonElement el : arr) {
                if (el.isJsonObject()) result.add(RemoteInfo.parse(el.getAsJsonObject()));
            }
        }
        return result;
    }

    private static List<VariableInfo> parseVariables(JsonObject obj) {
        List<VariableInfo> result = new ArrayList<>();
        JsonArray arr = getArr(obj, "variables");
        if (arr != null) {
            for (JsonElement el : arr) {
                if (el.isJsonObject()) result.add(VariableInfo.parse(el.getAsJsonObject()));
            }
        }
        return result;
    }

    private static List<HeaderInfo> parseHeaders(JsonObject obj) {
        List<HeaderInfo> result = new ArrayList<>();
        JsonArray arr = getArr(obj, "headers");
        if (arr != null) {
            for (JsonElement el : arr) {
                if (el.isJsonObject()) result.add(HeaderInfo.parse(el.getAsJsonObject()));
            }
        }
        return result;
    }

    // ── Inner classes ───────────────────────────────────────────────

    public static class PackageInfo {
        public final String registryType;
        public final String name;
        public final String version;
        public final String transportType;

        PackageInfo(String registryType, String name, String version, String transportType) {
            this.registryType = registryType;
            this.name = name;
            this.version = version;
            this.transportType = transportType;
        }

        static PackageInfo parse(JsonObject obj) {
            String regType = getStr(obj, "registry_type", "registryType");
            String name = getStr(obj, "name", "identifier");
            String ver = getStr(obj, "version");
            String ttype = null;
            JsonObject transport = getObj(obj, "transport");
            if (transport != null) ttype = getStr(transport, "type");
            return new PackageInfo(regType, name, ver, ttype != null ? ttype : "stdio");
        }

        @Override
        public String toString() {
            return (registryType != null ? registryType + ": " : "") + name
                    + (version != null ? "@" + version : "")
                    + " [" + transportType + "]";
        }

        private static String getStr(JsonObject o, String... keys) {
            return McpRegistryServerInfo.getStr(o, keys);
        }

        private static JsonObject getObj(JsonObject o, String key) {
            return McpRegistryServerInfo.getObj(o, key);
        }
    }

    public static class RemoteInfo {
        public final String type;
        public final String url;
        private final List<VariableInfo> variables;
        private final List<HeaderInfo> headers;

        RemoteInfo(String type, String url, List<VariableInfo> variables, List<HeaderInfo> headers) {
            this.type = type;
            this.url = url;
            this.variables = variables;
            this.headers = headers;
        }

        public List<VariableInfo> getVariables() { return variables; }
        public List<HeaderInfo> getHeaders() { return headers; }

        static RemoteInfo parse(JsonObject obj) {
            String type = getStr(obj, "transport_type", "type");
            String url = getStr(obj, "url");

            List<VariableInfo> vars = McpRegistryServerInfo.parseVariables(obj);
            List<HeaderInfo> hdrs = McpRegistryServerInfo.parseHeaders(obj);

            return new RemoteInfo(type, url, vars, hdrs);
        }

        @Override
        public String toString() {
            return (type != null ? type : "http") + ": " + url;
        }

        private static String getStr(JsonObject o, String... keys) {
            return McpRegistryServerInfo.getStr(o, keys);
        }
    }

    public static class VariableInfo {
        public final String name;
        public final String description;
        public final boolean required;
        public final String defaultValue;
        public final boolean isSecret;
        public final List<String> choices;

        VariableInfo(String name, String description, boolean required, String defaultValue,
                     boolean isSecret, List<String> choices) {
            this.name = name;
            this.description = description;
            this.required = required;
            this.defaultValue = defaultValue;
            this.isSecret = isSecret;
            this.choices = choices;
        }

        static VariableInfo parse(JsonObject obj) {
            String name = getStr(obj, "name");
            String desc = getStr(obj, "description");
            boolean req = obj.has("required") && !obj.get("required").isJsonNull() && obj.get("required").getAsBoolean();
            String def = getStr(obj, "default");
            boolean secret = obj.has("isSecret") && !obj.get("isSecret").isJsonNull() && obj.get("isSecret").getAsBoolean();
            if (!secret) secret = obj.has("is_secret") && !obj.get("is_secret").isJsonNull() && obj.get("is_secret").getAsBoolean();
            List<String> choices = new ArrayList<>();
            JsonArray arr = McpRegistryServerInfo.getArr(obj, "choices");
            if (arr != null) {
                for (JsonElement el : arr) choices.add(el.getAsString());
            }
            return new VariableInfo(name, desc, req, def, secret, choices);
        }

        private static String getStr(JsonObject o, String... keys) {
            return McpRegistryServerInfo.getStr(o, keys);
        }
    }

    public static class HeaderInfo {
        public final String name;
        public final String description;
        public final boolean required;
        public final boolean isSecret;

        HeaderInfo(String name, String description, boolean required, boolean isSecret) {
            this.name = name;
            this.description = description;
            this.required = required;
            this.isSecret = isSecret;
        }

        static HeaderInfo parse(JsonObject obj) {
            String name = getStr(obj, "name");
            String desc = getStr(obj, "description");
            boolean req = obj.has("required") && !obj.get("required").isJsonNull() && obj.get("required").getAsBoolean();
            boolean secret = obj.has("isSecret") && !obj.get("isSecret").isJsonNull() && obj.get("isSecret").getAsBoolean();
            if (!secret) secret = obj.has("is_secret") && !obj.get("is_secret").isJsonNull() && obj.get("is_secret").getAsBoolean();
            return new HeaderInfo(name, desc, req, secret);
        }

        private static String getStr(JsonObject o, String... keys) {
            return McpRegistryServerInfo.getStr(o, keys);
        }
    }
}

