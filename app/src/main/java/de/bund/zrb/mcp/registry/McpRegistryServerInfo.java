package de.bund.zrb.mcp.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parsed metadata for a single MCP server entry from the registry API.
 */
public class McpRegistryServerInfo {

    private final String name;
    private final String title;
    private final String description;
    private final String status; // "active", "deprecated", "deleted"
    private final String latestVersion;
    private final List<PackageInfo> packages;
    private final List<RemoteInfo> remotes;
    private final List<VariableInfo> variables;
    private final List<HeaderInfo> headers;

    private McpRegistryServerInfo(String name, String title, String description, String status,
                                  String latestVersion, List<PackageInfo> packages,
                                  List<RemoteInfo> remotes, List<VariableInfo> variables,
                                  List<HeaderInfo> headers) {
        this.name = name;
        this.title = title;
        this.description = description;
        this.status = status;
        this.latestVersion = latestVersion;
        this.packages = packages;
        this.remotes = remotes;
        this.variables = variables;
        this.headers = headers;
    }

    // ── Getters ─────────────────────────────────────────────────────

    public String getName() { return name; }
    public String getTitle() { return title != null ? title : name; }
    public String getDescription() { return description != null ? description : ""; }
    public String getStatus() { return status != null ? status : "active"; }
    public String getLatestVersion() { return latestVersion; }
    public List<PackageInfo> getPackages() { return packages; }
    public List<RemoteInfo> getRemotes() { return remotes; }
    public List<VariableInfo> getVariables() { return variables; }
    public List<HeaderInfo> getHeaders() { return headers; }

    public boolean isDeprecated() { return "deprecated".equalsIgnoreCase(status); }
    public boolean isDeleted() { return "deleted".equalsIgnoreCase(status); }
    public boolean hasRemotes() { return remotes != null && !remotes.isEmpty(); }
    public boolean hasPackages() { return packages != null && !packages.isEmpty(); }

    // ── Parsing ─────────────────────────────────────────────────────

    /**
     * Parse a server entry from the registry list endpoint.
     */
    public static McpRegistryServerInfo fromListEntry(JsonObject obj) {
        String name = getString(obj, "name");
        String title = getString(obj, "title");
        String description = getString(obj, "description");
        String status = getString(obj, "status");
        return new McpRegistryServerInfo(name, title, description, status,
                null, Collections.<PackageInfo>emptyList(), Collections.<RemoteInfo>emptyList(),
                Collections.<VariableInfo>emptyList(), Collections.<HeaderInfo>emptyList());
    }

    /**
     * Parse a full server detail from the versions/latest endpoint.
     */
    public static McpRegistryServerInfo fromDetail(JsonObject obj) {
        String name = getString(obj, "name");
        String title = getString(obj, "title");
        String description = getString(obj, "description");
        String status = getString(obj, "status");
        String version = getString(obj, "version");

        List<PackageInfo> pkgs = new ArrayList<>();
        if (obj.has("packages") && obj.get("packages").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("packages")) {
                pkgs.add(PackageInfo.parse(el.getAsJsonObject()));
            }
        }

        List<RemoteInfo> rems = new ArrayList<>();
        if (obj.has("remotes") && obj.get("remotes").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("remotes")) {
                rems.add(RemoteInfo.parse(el.getAsJsonObject()));
            }
        }

        // Variables may be nested in remotes or at top-level
        List<VariableInfo> vars = new ArrayList<>();
        if (obj.has("variables") && obj.get("variables").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("variables")) {
                vars.add(VariableInfo.parse(el.getAsJsonObject()));
            }
        }
        // Also check inside each remote
        for (RemoteInfo rem : rems) {
            vars.addAll(rem.getVariables());
        }

        List<HeaderInfo> hdrs = new ArrayList<>();
        if (obj.has("headers") && obj.get("headers").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("headers")) {
                hdrs.add(HeaderInfo.parse(el.getAsJsonObject()));
            }
        }
        for (RemoteInfo rem : rems) {
            hdrs.addAll(rem.getHeaders());
        }

        return new McpRegistryServerInfo(name, title, description, status, version,
                pkgs, rems, vars, hdrs);
    }

    private static String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    // ── Inner classes ───────────────────────────────────────────────

    public static class PackageInfo {
        public final String registryType; // npm, pypi, nuget, oci, mcpb
        public final String name;
        public final String version;
        public final String transportType; // stdio

        PackageInfo(String registryType, String name, String version, String transportType) {
            this.registryType = registryType;
            this.name = name;
            this.version = version;
            this.transportType = transportType;
        }

        static PackageInfo parse(JsonObject obj) {
            String regType = getString(obj, "registryType");
            String name = getString(obj, "name");
            if (name == null) name = getString(obj, "identifier");
            String ver = getString(obj, "version");
            String ttype = null;
            if (obj.has("transport") && obj.get("transport").isJsonObject()) {
                ttype = getString(obj.getAsJsonObject("transport"), "type");
            }
            return new PackageInfo(regType, name, ver, ttype != null ? ttype : "stdio");
        }

        @Override
        public String toString() {
            return (registryType != null ? registryType + ": " : "") + name
                    + (version != null ? "@" + version : "")
                    + " [" + transportType + "]";
        }

        private static String getString(JsonObject o, String k) {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
        }
    }

    public static class RemoteInfo {
        public final String type; // streamable-http, sse
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
            String type = getString(obj, "type");
            String url = getString(obj, "url");

            List<VariableInfo> vars = new ArrayList<>();
            if (obj.has("variables") && obj.get("variables").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("variables")) {
                    vars.add(VariableInfo.parse(el.getAsJsonObject()));
                }
            }

            List<HeaderInfo> hdrs = new ArrayList<>();
            if (obj.has("headers") && obj.get("headers").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("headers")) {
                    hdrs.add(HeaderInfo.parse(el.getAsJsonObject()));
                }
            }

            return new RemoteInfo(type, url, vars, hdrs);
        }

        @Override
        public String toString() {
            return type + ": " + url;
        }

        private static String getString(JsonObject o, String k) {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
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
            String name = getString(obj, "name");
            String desc = getString(obj, "description");
            boolean req = obj.has("required") && obj.get("required").getAsBoolean();
            String def = getString(obj, "default");
            boolean secret = obj.has("isSecret") && obj.get("isSecret").getAsBoolean();
            List<String> choices = new ArrayList<>();
            if (obj.has("choices") && obj.get("choices").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("choices")) {
                    choices.add(el.getAsString());
                }
            }
            return new VariableInfo(name, desc, req, def, secret, choices);
        }

        private static String getString(JsonObject o, String k) {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
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
            String name = getString(obj, "name");
            String desc = getString(obj, "description");
            boolean req = obj.has("required") && obj.get("required").getAsBoolean();
            boolean secret = obj.has("isSecret") && obj.get("isSecret").getAsBoolean();
            return new HeaderInfo(name, desc, req, secret);
        }

        private static String getString(JsonObject o, String k) {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
        }
    }
}

