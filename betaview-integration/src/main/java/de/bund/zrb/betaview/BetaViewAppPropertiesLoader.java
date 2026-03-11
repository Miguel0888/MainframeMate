package de.bund.zrb.betaview;

import java.io.InputStream;
import java.util.Properties;

public final class BetaViewAppPropertiesLoader {

    private BetaViewAppPropertiesLoader() {
        // Prevent instantiation
    }

    public static BetaViewAppProperties load() {
        Properties p = new Properties();

        try (InputStream in = BetaViewAppPropertiesLoader.class.getClassLoader()
                .getResourceAsStream("betaview.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception ignored) {
            // Ignore errors and fall back to defaults
        }

        String url = p.getProperty("url", "");
        String user = p.getProperty("user", "");
        String password = p.getProperty("password", "");

        String favoriteId = p.getProperty("favoriteId", "A158");
        String locale = p.getProperty("locale", "de");
        String extension = p.getProperty("extension", "*");
        String form = p.getProperty("form", "APZF");

        String report = p.getProperty("report", "*");
        String jobName = p.getProperty("jobName", "*");

        int daysBack = parseInt(p.getProperty("daysBack", "60"), 60);

        return new BetaViewAppProperties(
                url,
                user,
                password,
                favoriteId,
                locale,
                extension,
                form,
                report,
                jobName,
                daysBack
        );
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}