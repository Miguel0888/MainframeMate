package de.bund.zrb;

import de.bund.zrb.history.LocalHistoryService;
import de.bund.zrb.indexing.service.IndexingService;
import de.bund.zrb.ui.MainFrame;
import de.bund.zrb.ui.syntax.MainframeSyntaxSupport;
import de.bund.zrb.ui.util.UnicodeFontFix;

import de.bund.zrb.runtime.PluginManager;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
//        UnicodeFontFix.apply(); // for windows 11 required to display emojis correctly
        MainframeSyntaxSupport.register(); // Register Natural/JCL/COBOL syntax highlighting

        // Apply log settings from preferences (before any logging happens)
        de.bund.zrb.util.AppLogger.applySettings();

        // Safety net: ensure all plugin resources (e.g. Firefox processes) are cleaned up on JVM exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                PluginManager.shutdownAll();
            } catch (Exception e) {
                System.err.println("[Shutdown] Error during plugin shutdown: " + e.getMessage());
            }
        }, "plugin-shutdown-hook"));

        // Start local history auto-pruning in background
        LocalHistoryService.getInstance().autoPruneAsync();

        // Start indexing scheduler (runs ON_STARTUP sources, schedules INTERVAL sources)
        IndexingService.getInstance().startScheduler();

        SwingUtilities.invokeLater(() -> {
            MainFrame gui = new MainFrame();
            gui.setVisible(true);
        });
    }
}