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

        // Navigation and toolbar config buttons are right-aligned by default
        cfg.rightSideIds.add("navigate.back");
        cfg.rightSideIds.add("navigate.forward");
        cfg.rightSideIds.add("settings.toolbar");

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

        // The IDs and icons we want in the default toolbar (order matters).
        // Icons MUST match the simple BMP characters used in the menu labels.
        String[][] defaults = {
                { "connection.ftp",    "\u2195"    },  // ↕   FTP
                { "connection.local",  "\u2302"    },  // ⌂   Lokal
                { "connection.ndv",    "\u25A3"    },  // ▣   NDV
                { "file.saveAndClose", "\u2714"    },  // ✔   Speichern & Schließen
                { "navigate.back",     "\u25C0"    },  // ◀   Zurück
                { "navigate.forward",  "\u25B6"    },  // ▶   Vorwärts
                { "settings.toolbar",  "\u2630"    },  // ☰   Toolbar-Einstellungen
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

    /**
     * Return a default icon character for a command when placed on the toolbar.
     * Uses the same simple BMP characters that appear in the menu labels so that
     * toolbar buttons and menu items look consistent.
     * <p>
     * <b>No extended / colored emoji</b> — only U+0000..U+FFFF characters that
     * render reliably in Java 8 Swing on all platforms.
     */
    public static String defaultIconFor(String idRaw) {
        String id = (idRaw == null) ? "" : idRaw.toLowerCase(Locale.ROOT);

        // ── File ────────────────────────────────────────────────
        if (id.equals("file.save"))            return "\u270E";    // ✎
        if (id.equals("file.close"))           return "\u2716";    // ✖
        if (id.equals("file.saveandclose"))    return "\u2714";    // ✔
        if (id.equals("file.cache"))           return "\u25C9";    // ◉
        if (id.equals("file.exit"))            return "\u2399";    // ⎙

        // ── Connection ─────────────────────────────────────────
        if (id.equals("connection.ftp"))        return "\u2195";   // ↕
        if (id.equals("connection.local"))      return "\u2302";   // ⌂
        if (id.equals("connection.ndv"))        return "\u25A3";   // ▣
        if (id.equals("connection.sharepoint")) return "\u25C6";   // ◆
        if (id.equals("connection.mail"))       return "\u2709";   // ✉
        if (id.equals("connection.wiki"))       return "\u00B6";   // ¶
        if (id.equals("connection.confluence")) return "\u25C8";   // ◈
        if (id.equals("connection.tn3270"))     return "\u25A0";   // ■
        if (id.equals("connection.jes"))        return "\u25B7";   // ▷
        if (id.equals("connection.betaview"))   return "\u25A4";   // ▤
        if (id.equals("connection.browser"))    return "\u25CE";   // ◎
        if (id.equals("connection.dos"))        return "\u25A6";   // ▦

        // ── Navigate ───────────────────────────────────────────
        if (id.equals("navigate.back"))            return "\u25C0"; // ◀
        if (id.equals("navigate.forward"))         return "\u25B6"; // ▶
        if (id.equals("navigate.searcheverywhere")) return "\u2315"; // ⌕

        // ── Edit ───────────────────────────────────────────────
        if (id.equals("edit.search"))          return "\u2315";    // ⌕
        if (id.equals("edit.compare"))         return "\u2194";    // ↔
        if (id.equals("edit.bookmark"))        return "\u2606";    // ☆

        // ── View ───────────────────────────────────────────────
        if (id.equals("view.leftdrawer"))      return "\u25E7";    // ◧
        if (id.equals("view.rightdrawer"))     return "\u25E8";    // ◨

        // ── Settings ───────────────────────────────────────────
        if (id.equals("settings.general"))     return "\u2699";    // ⚙
        if (id.equals("settings.passwords"))   return "\u2731";    // ✱
        if (id.equals("settings.sentences"))   return "\u2261";    // ≡
        if (id.equals("settings.expressions")) return "\u0192";    // ƒ
        if (id.equals("settings.tools"))       return "\u229E";    // ⊞
        if (id.equals("settings.shortcuts"))   return "\u2328";    // ⌨
        if (id.equals("settings.mails"))       return "\u2709";    // ✉
        if (id.equals("settings.indexing"))    return "\u25CE";    // ◎
        if (id.equals("settings.toolbar"))     return "\u2630";    // ☰

        // ── Help ───────────────────────────────────────────────
        if (id.equals("help.features"))        return "\u2605";    // ★
        if (id.equals("help.video"))           return "\u25C9";    // ◉
        if (id.equals("help.about"))           return "\u2139";    // ℹ

        // ── Plugins ────────────────────────────────────────────
        if (id.equals("plugin.install"))       return "\u2295";    // ⊕
        if (id.equals("plugin.excel.import"))  return "\u25A6";    // ▦

        // ── Generic fallbacks (BMP only) ───────────────────────
        if (id.contains("save") && id.contains("close")) return "\u2714"; // ✔
        if (id.contains("save"))       return "\u270E";    // ✎
        if (id.contains("connect"))    return "\u2195";    // ↕
        if (id.contains("close"))      return "\u2716";    // ✖
        if (id.contains("toolbar"))    return "\u2630";    // ☰
        if (id.contains("settings") || id.contains("configure")) return "\u2699"; // ⚙
        if (id.contains("about"))      return "\u2139";    // ℹ
        if (id.contains("exit"))       return "\u2399";    // ⎙
        if (id.contains("bookmark"))   return "\u2606";    // ☆
        if (id.contains("search"))     return "\u2315";    // ⌕
        if (id.contains("compare"))    return "\u2194";    // ↔
        if (id.contains("shortcut"))   return "\u2328";    // ⌨
        if (id.contains("tool"))       return "\u229E";    // ⊞
        if (id.contains("expression")) return "\u0192";    // ƒ
        if (id.contains("sentence"))   return "\u2261";    // ≡
        if (id.contains("feature"))    return "\u2605";    // ★
        if (id.contains("plugin"))     return "\u2295";    // ⊕
        if (id.contains("mail"))       return "\u2709";    // ✉
        if (id.contains("index"))      return "\u25CE";    // ◎
        if (id.contains("video"))      return "\u25C9";    // ◉
        if (id.contains("password"))   return "\u2731";    // ✱
        if (id.contains("wiki"))       return "\u00B6";    // ¶
        if (id.contains("browser"))    return "\u25CE";    // ◎
        if (id.contains("excel"))      return "\u25A6";    // ▦

        return "\u25CF"; // ● (generic dot)
    }

    public static String defaultBackgroundHexFor(String idRaw) {
        // No default background colors – keep a clean look.
        return null;
    }

}
