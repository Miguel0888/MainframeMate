package de.example.toolbarkit.toolbar;

import de.example.toolbarkit.command.ToolbarCommand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Create an initial toolbar configuration.
 * Keep this class small and replace token rules in your own project if needed.
 */
public final class ToolbarDefaults {

    private ToolbarDefaults() {}

    /**
     * Create a minimal configuration:
     * - show a small set of important commands if detectable
     * - hide all remaining commands in the visibility list
     */
    public static ToolbarConfig createInitialConfig(List<ToolbarCommand> allCommands) {
        ToolbarConfig cfg = new ToolbarConfig();
        cfg.buttonSizePx = 48;
        cfg.fontSizeRatio = 0.75f;
        cfg.groupColors = new LinkedHashMap<String, String>();
        cfg.rightSideIds = new LinkedHashSet<String>();

        List<ToolbarButtonConfig> visible = buildImportantDefaultButtons(allCommands);
        cfg.buttons = new ArrayList<ToolbarButtonConfig>(visible);

        cfg.hiddenCommandIds = new LinkedHashSet<String>();
        Set<String> visibleIds = new LinkedHashSet<String>();
        for (ToolbarButtonConfig b : visible) {
            visibleIds.add(b.id);
        }
        for (ToolbarCommand cmd : allCommands) {
            String id = Objects.toString(cmd.getId(), "");
            if (!visibleIds.contains(id)) {
                cfg.hiddenCommandIds.add(id);
            }
        }

        return cfg;
    }

    /**
     * MainframeMate defaults: connection tabs + save &amp; close.
     * No background colors – clean look.
     */
    public static List<ToolbarButtonConfig> buildImportantDefaultButtons(List<ToolbarCommand> allCommands) {
        List<ToolbarButtonConfig> visible = new ArrayList<ToolbarButtonConfig>();

        // The IDs and icons we want in the default toolbar (order matters)
        String[][] defaults = {
                { "connection.ftp",    cp(0x1F310) },  // 🌐  FTP
                { "connection.local",  cp(0x1F4BB) },  // 💻  Lokal
                { "connection.ndv",    cp(0x1F5A5) },  // 🖥  NDV
                { "file.saveAndClose", "\u2714"    },  // ✔   Speichern & Schließen
        };

        int pos = 1;
        for (String[] def : defaults) {
            String wantedId = def[0];
            String icon     = def[1];

            // Only add the button if the command actually exists
            String foundId = findExactId(allCommands, wantedId);
            if (foundId == null) continue;

            ToolbarButtonConfig tbc = new ToolbarButtonConfig(foundId, icon);
            tbc.order = Integer.valueOf(pos++);
            // no backgroundHex – clean default look
            visible.add(tbc);
        }

        return visible;
    }

    /** Find a command by exact id. */
    private static String findExactId(List<ToolbarCommand> all, String id) {
        for (ToolbarCommand c : all) {
            if (id.equals(c.getId())) return id;
        }
        return null;
    }

    public static String defaultIconFor(String idRaw) {
        String id = (idRaw == null) ? "" : idRaw.toLowerCase(Locale.ROOT);

        // MainframeMate specific
        if (id.equals("connection.ftp"))       return cp(0x1F310); // 🌐
        if (id.equals("connection.local"))     return cp(0x1F4BB); // 💻
        if (id.equals("connection.ndv"))       return cp(0x1F5A5); // 🖥
        if (id.equals("file.saveandclose"))    return "\u2714";    // ✔
        if (id.equals("file.save"))            return cp(0x1F4BE); // 💾
        if (id.equals("file.exit"))            return cp(0x1F6AA); // 🚪

        // Generic fallbacks
        if (id.contains("save") && id.contains("close")) return "\u2714"; // ✔
        if (id.contains("save"))     return cp(0x1F4BE); // 💾
        if (id.contains("connect"))  return cp(0x1F310); // 🌐
        if (id.contains("close"))    return "\u2716";     // ✖
        if (id.contains("settings") || id.contains("configure")) return "\u2699"; // ⚙
        if (id.contains("about"))    return "\u2139";     // ℹ
        if (id.contains("exit"))     return cp(0x1F6AA);  // 🚪
        if (id.contains("bookmark")) return cp(0x1F516);  // 🔖
        if (id.contains("search"))   return cp(0x1F50D);  // 🔍
        if (id.contains("compare"))  return cp(0x1F504);  // 🔄
        if (id.contains("shortcut")) return "\u2328";     // ⌨
        if (id.contains("tool"))     return cp(0x1F6E0);  // 🛠
        if (id.contains("expression")) return "fx";
        if (id.contains("sentence"))   return cp(0x1F4DD); // 📝
        if (id.contains("feature"))    return "\u2B50";    // ⭐
        if (id.contains("server"))     return cp(0x1F5A7); // 🖧
        if (id.contains("plugin"))     return cp(0x1F9E9); // 🧩

        return "\u25CF"; // ●
    }

    public static String defaultBackgroundHexFor(String idRaw) {
        // No default background colors – keep a clean look.
        return null;
    }

    private static String findIdContaining(List<ToolbarCommand> all, String... tokens) {
        for (int i = 0; i < tokens.length; i++) {
            String needle = tokens[i].toLowerCase(Locale.ROOT);
            for (ToolbarCommand c : all) {
                String id = Objects.toString(c.getId(), "");
                if (id.toLowerCase(Locale.ROOT).contains(needle)) return id;
            }
        }
        return null;
    }

    private static String cp(int codePoint) {
        return new String(Character.toChars(codePoint));
    }
}
