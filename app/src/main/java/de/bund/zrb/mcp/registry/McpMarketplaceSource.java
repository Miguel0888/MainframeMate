package de.bund.zrb.mcp.registry;

import java.util.ArrayList;
import java.util.List;

/**
 * A configured marketplace source that can be browsed in the MCP Registry Browser.
 * Supports different types:
 * <ul>
 *   <li>{@code REGISTRY} – official MCP Registry API (default)</li>
 *   <li>{@code GITHUB_ORG} – list repos from a GitHub organization</li>
 * </ul>
 */
public class McpMarketplaceSource {

    public enum Type { REGISTRY, GITHUB_ORG }

    private String name;
    private Type type;
    private String url;
    private boolean enabled = true;

    public McpMarketplaceSource() {}

    public McpMarketplaceSource(String name, Type type, String url) {
        this.name = name;
        this.type = type;
        this.url = url;
    }

    public String getName()  { return name; }
    public Type   getType()  { return type; }
    public String getUrl()   { return url; }
    public boolean isEnabled() { return enabled; }

    public void setName(String name)     { this.name = name; }
    public void setType(Type type)       { this.type = type; }
    public void setUrl(String url)       { this.url = url; }
    public void setEnabled(boolean e)    { this.enabled = e; }

    @Override
    public String toString() { return name + " (" + type + ")"; }

    // ── Defaults ────────────────────────────────────────────────────

    /**
     * Returns the default marketplace sources shipped with MainframeMate.
     */
    public static List<McpMarketplaceSource> defaults() {
        List<McpMarketplaceSource> list = new ArrayList<>();
        list.add(new McpMarketplaceSource(
                "MCP Registry",
                Type.REGISTRY,
                "https://registry.modelcontextprotocol.io"));
        list.add(new McpMarketplaceSource(
                "MCP Official (GitHub)",
                Type.GITHUB_ORG,
                "https://github.com/modelcontextprotocol"));
        return list;
    }
}

