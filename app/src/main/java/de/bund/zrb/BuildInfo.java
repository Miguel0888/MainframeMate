package de.bund.zrb;

import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides build-time information (version, name, build timestamp)
 * injected by Gradle via {@code version.properties}.
 */
public final class BuildInfo {

    private static final Logger LOG = Logger.getLogger(BuildInfo.class.getName());

    private static final String PROPS_FILE = "/version.properties";
    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = BuildInfo.class.getResourceAsStream(PROPS_FILE)) {
            if (in != null) {
                PROPS.load(in);
            } else {
                LOG.warning("[BuildInfo] " + PROPS_FILE + " not found on classpath – using defaults.");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[BuildInfo] Failed to load " + PROPS_FILE, e);
        }
    }

    private BuildInfo() { /* utility */ }

    /** Application version, e.g. "5.4.0". Falls back to "DEV" if not available. */
    public static String getVersion() {
        return PROPS.getProperty("app.version", "DEV");
    }

    /** Application name, e.g. "MainframeMate". */
    public static String getAppName() {
        return PROPS.getProperty("app.name", "MainframeMate");
    }

    /** ISO timestamp of when the build was created. */
    public static String getBuildTimestamp() {
        return PROPS.getProperty("build.timestamp", "unknown");
    }
}

