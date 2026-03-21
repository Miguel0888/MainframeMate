package de.bund.zrb;

import de.bund.zrb.history.LocalHistoryService;
import de.bund.zrb.indexing.service.IndexingService;
import de.bund.zrb.mail.service.MailService;
import de.bund.zrb.ui.MainFrame;
import de.bund.zrb.ui.syntax.MainframeSyntaxSupport;
import de.bund.zrb.ui.branding.IconThemeInstaller;
import de.bund.zrb.ui.util.UnicodeFontFix;

import de.bund.zrb.runtime.PluginManager;
import de.bund.zrb.ui.theme.ThemeManager;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        // MUST be the very first call — configures JNA to load jnidispatch.dll
        // from ~/.mainframemate/native/ instead of %TEMP% (blocked on hardened Windows 11)
        de.bund.zrb.util.JnaBootstrap.configure();

//        UnicodeFontFix.apply(); // for windows 11 required to display emojis correctly
        MainframeSyntaxSupport.register(); // Register Natural/JCL/COBOL syntax highlighting


        // Install branding icon theme (before any Swing window is created)
        IconThemeInstaller.install();

        // Apply log settings from preferences (before any logging happens)
        de.bund.zrb.util.AppLogger.applySettings();

        // Safety net: ensure all plugin resources (e.g. Firefox processes) are cleaned up on JVM exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                PluginManager.shutdownAll();
                MailService.getInstance().shutdown();
                de.bund.zrb.archive.service.ArchiveService.getInstance().shutdown();
            } catch (Exception e) {
                System.err.println("[Shutdown] Error during plugin shutdown: " + e.getMessage());
            }
        }, "plugin-shutdown-hook"));

        // Start local history auto-pruning in background
        LocalHistoryService.getInstance().autoPruneAsync();

        // Start indexing scheduler (runs ON_STARTUP sources, schedules INTERVAL sources)
        IndexingService.getInstance().startScheduler();

        // Initialize mail sync service (indexes PST/OST mails, watches for changes)
        try {
            String mailStorePath = de.bund.zrb.helper.SettingsHelper.load().mailStorePath;
            if (mailStorePath != null && !mailStorePath.trim().isEmpty()) {
                MailService.getInstance().initialize(mailStorePath);
            }
        } catch (Exception e) {
            System.err.println("[Mail] Failed to initialize mail service: " + e.getMessage());
        }

        // Initialize Archive system (H2 database + register archive tools)
        de.bund.zrb.archive.service.ArchiveService.getInstance().registerTools();

        SwingUtilities.invokeLater(() -> {
            // Apply global UI theme (before creating any windows)
            try {
                int lockStyle = de.bund.zrb.helper.SettingsHelper.load().lockStyle;
                ThemeManager.getInstance().applyTheme(lockStyle);
            } catch (Exception e) {
                System.err.println("[Theme] Failed to apply initial theme: " + e.getMessage());
            }

            MainFrame gui = new MainFrame();
            gui.setVisible(true);

            // Refresh after visible so title bar theming works (needs native HWND)
            ThemeManager.getInstance().refreshWindow(gui);
        });
    }
}