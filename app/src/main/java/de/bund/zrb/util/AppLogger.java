package de.bund.zrb.util;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central logging facade that reads log levels from Settings.
 * <p>
 * Usage:
 * <pre>
 *   private static final Logger LOG = AppLogger.get(AppLogger.MAIL);
 *   LOG.fine("detail message");   // only shown when MAIL category is FINE or lower
 * </pre>
 * <p>
 * Categories are mapped to JUL logger names under "de.bund.zrb.{category}".
 * The global log level is the root logger level.
 */
public final class AppLogger {

    // ── Well-known categories ──
    public static final String MAIL     = "MAIL";
    public static final String STAR     = "STAR";
    public static final String FTP      = "FTP";
    public static final String NDV      = "NDV";
    public static final String TOOL     = "TOOL";
    public static final String INDEX    = "INDEX";
    public static final String SEARCH   = "SEARCH";   // Lucene index search operations
    public static final String RAG      = "RAG";
    public static final String UI       = "UI";
    public static final String PLUGIN   = "PLUGIN";   // Plugin lifecycle & logging
    public static final String BROWSER  = "BROWSER";  // Browser/BiDi/WebDriver logs
    public static final String AI       = "AI";       // AI/LLM chat request/response logging

    private static volatile boolean initialized = false;

    private AppLogger() {}

    /**
     * Get a Logger for the given category. The logger's level is
     * determined by Settings.logCategoryLevels or Settings.logLevel (fallback).
     */
    public static Logger get(String category) {
        ensureInitialized();
        return Logger.getLogger("de.bund.zrb." + category.toLowerCase());
    }

    /**
     * Get a Logger for a class (uses class name as logger name).
     */
    public static Logger get(Class<?> clazz) {
        ensureInitialized();
        return Logger.getLogger(clazz.getName());
    }

    /**
     * Apply log levels from Settings to JUL loggers.
     * Call this at startup and after settings change.
     */
    public static void applySettings() {
        Settings settings;
        try {
            settings = SettingsHelper.load();
        } catch (Exception e) {
            return; // settings not available yet
        }

        // Global level on root logger
        Level globalLevel = parseLevel(settings.logLevel, Level.INFO);
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(globalLevel);

        // Set the app-wide parent logger
        Logger appLogger = Logger.getLogger("de.bund.zrb");
        appLogger.setLevel(globalLevel);

        // Per-category overrides
        if (settings.logCategoryLevels != null) {
            for (Map.Entry<String, String> entry : settings.logCategoryLevels.entrySet()) {
                String category = entry.getKey();
                Level catLevel = parseLevel(entry.getValue(), globalLevel);
                Logger.getLogger("de.bund.zrb." + category.toLowerCase()).setLevel(catLevel);
            }
        }

        // Ensure console handler respects the level
        for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
            if (handler instanceof java.util.logging.ConsoleHandler) {
                handler.setLevel(Level.ALL); // let the loggers decide
            }
        }

        initialized = true;
    }

    private static void ensureInitialized() {
        if (!initialized) {
            applySettings();
        }
    }

    private static Level parseLevel(String levelName, Level fallback) {
        if (levelName == null || levelName.trim().isEmpty()) return fallback;
        try {
            return Level.parse(levelName.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
