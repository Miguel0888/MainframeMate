package de.bund.zrb;

import de.bund.zrb.history.LocalHistoryService;
import de.bund.zrb.indexing.service.IndexingService;
import de.bund.zrb.ui.MainFrame;
import de.bund.zrb.ui.syntax.MainframeSyntaxSupport;
import de.bund.zrb.ui.util.UnicodeFontFix;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
//        UnicodeFontFix.apply(); // for windows 11 required to display emojis correctly
        MainframeSyntaxSupport.register(); // Register Natural/JCL/COBOL syntax highlighting

        // Apply log settings from preferences (before any logging happens)
        de.bund.zrb.util.AppLogger.applySettings();

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