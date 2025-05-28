
package de.bund.zrb.plugins;

import de.bund.zrb.ui.MainFrame;
import java.util.ArrayList;
import java.util.List;

public class PluginManager {
    private static final List<MainframeMatePlugin> plugins = new ArrayList<>();

    public static void registerPlugin(MainframeMatePlugin plugin) {
        plugins.add(plugin);
    }

    public static void initializePlugins(MainFrame mainFrame) {
        for (MainframeMatePlugin plugin : plugins) {
            plugin.initialize(mainFrame);
        }
    }

    public static List<MainframeMatePlugin> getPlugins() {
        return plugins;
    }
}
