package de.bund.zrb.ui.branding;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central installer for the MainframeMate branding icon theme.
 * <p>
 * Replaces Java default icons (coffee cup) with brand icons from the classpath.
 * Icons are expected under {@code /app/} (app window icons) and optionally {@code /ui/}
 * (Swing UIManager default icons) on the classpath.
 * <p>
 * Must be called <b>before</b> any Swing window is created.
 *
 * <h3>Usage:</h3>
 * <pre>
 *   IconThemeInstaller.install(); // MINIMAL mode (auto-detect)
 * </pre>
 */
public final class IconThemeInstaller {

    private static final Logger LOG = Logger.getLogger(IconThemeInstaller.class.getName());

    /** Icon theme mode. */
    public enum Mode {
        /** Only app/window icons (taskbar, title bar). UIManager defaults unchanged. */
        MINIMAL,
        /** App icons + UIManager defaults (OptionPane, FileChooser, Tree, InternalFrame). */
        FULL
    }

    /** Icon variant (transparent vs. background). */
    public enum Variant {
        TRANSPARENT("transparent"),
        BG("bg");

        private final String suffix;

        Variant(String suffix) {
            this.suffix = suffix;
        }

        public String getSuffix() {
            return suffix;
        }
    }

    // ── Constants ────────────────────────────────────────────────────────────────

    private static final String APP_ICON_PREFIX = "/app/mainframemate_hub_";
    // Descending order: largest first → OS/Swing picks the highest available resolution
    private static final int[] APP_ICON_SIZES = {1024, 512, 256, 128, 64, 48, 32, 24, 16};
    private static final int[] REQUIRED_APP_SIZES = {16, 24, 32, 48, 64, 128, 256};

    // ── Cache ────────────────────────────────────────────────────────────────────

    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();
    private static volatile List<Image> appIconImages;
    private static volatile boolean installed = false;

    // ── UIManager key → resource path mapping (FULL mode) ────────────────────────

    private static final Map<String, String> UI_ICON_MAPPING = new LinkedHashMap<>();

    static {
        // OptionPane (32px)
        UI_ICON_MAPPING.put("OptionPane.informationIcon", "/ui/optionpane_information_32.png");
        UI_ICON_MAPPING.put("OptionPane.warningIcon", "/ui/optionpane_warning_32.png");
        UI_ICON_MAPPING.put("OptionPane.errorIcon", "/ui/optionpane_error_32.png");
        UI_ICON_MAPPING.put("OptionPane.questionIcon", "/ui/optionpane_question_32.png");

        // FileChooser / FileView (16px)
        UI_ICON_MAPPING.put("FileView.directoryIcon", "/ui/filechooser_directory_16.png");
        UI_ICON_MAPPING.put("FileView.fileIcon", "/ui/filechooser_file_16.png");
        UI_ICON_MAPPING.put("FileView.computerIcon", "/ui/filechooser_computer_16.png");
        UI_ICON_MAPPING.put("FileView.hardDriveIcon", "/ui/filechooser_harddrive_16.png");
        UI_ICON_MAPPING.put("FileView.floppyDriveIcon", "/ui/filechooser_floppy_16.png");

        // Tree (16px)
        UI_ICON_MAPPING.put("Tree.openIcon", "/ui/tree_open_16.png");
        UI_ICON_MAPPING.put("Tree.closedIcon", "/ui/tree_closed_16.png");
        UI_ICON_MAPPING.put("Tree.leafIcon", "/ui/tree_leaf_16.png");

        // InternalFrame (16px)
        UI_ICON_MAPPING.put("InternalFrame.closeIcon", "/ui/internalframe_close_16.png");
        UI_ICON_MAPPING.put("InternalFrame.minimizeIcon", "/ui/internalframe_minimize_16.png");
        UI_ICON_MAPPING.put("InternalFrame.maximizeIcon", "/ui/internalframe_maximize_16.png");
        UI_ICON_MAPPING.put("InternalFrame.iconifyIcon", "/ui/internalframe_iconify_16.png");
    }

    // ── Required UI icons in FULL mode ───────────────────────────────────────────

    private static final List<String> REQUIRED_UI_ICONS = Arrays.asList(
            "/ui/optionpane_information_32.png",
            "/ui/optionpane_warning_32.png",
            "/ui/optionpane_error_32.png",
            "/ui/optionpane_question_32.png"
    );

    private IconThemeInstaller() {
        // utility class
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Public API
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Install the icon theme with auto-detected mode.
     * If {@code /ui/} resources exist, FULL mode is used; otherwise MINIMAL.
     * Uses TRANSPARENT variant by default.
     */
    public static void install() {
        Mode mode = detectMode();
        install(mode, Variant.TRANSPARENT);
    }

    /**
     * Install the icon theme with the specified mode and variant.
     *
     * @param mode    MINIMAL or FULL
     * @param variant TRANSPARENT or BG
     */
    public static void install(Mode mode, Variant variant) {
        if (installed) {
            LOG.fine("[IconTheme] Already installed, skipping.");
            return;
        }

        LOG.info("[IconTheme] Installing icon theme (mode=" + mode + ", variant=" + variant.getSuffix() + ")");

        try {
            installAppIcons(variant);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[IconTheme] Failed to install app icons: " + e.getMessage(), e);
        }

        if (mode == Mode.FULL) {
            try {
                installUIDefaults();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[IconTheme] Failed to install UI defaults: " + e.getMessage(), e);
            }
        }

        installed = true;
        LOG.info("[IconTheme] Installation complete.");
    }

    /**
     * Returns the multi-size app icon list for use with {@link Window#setIconImages(List)}.
     * Returns an empty list if icons have not been loaded yet.
     */
    public static List<Image> getAppIcons() {
        List<Image> icons = appIconImages;
        return icons != null ? Collections.unmodifiableList(icons) : Collections.<Image>emptyList();
    }

    /**
     * Returns a single app icon of the closest available size.
     * Useful for system tray etc.
     *
     * @param preferredSize desired icon size in px
     * @return Image or null if not available
     */
    public static Image getAppIcon(int preferredSize) {
        List<Image> icons = getAppIcons();
        if (icons.isEmpty()) return null;

        // Find closest size; on tie prefer the larger icon (higher resolution)
        Image best = icons.get(0);
        int bestDiff = Math.abs(best.getWidth(null) - preferredSize);
        for (Image img : icons) {
            int w = img.getWidth(null);
            int diff = Math.abs(w - preferredSize);
            if (diff < bestDiff || (diff == bestDiff && w > best.getWidth(null))) {
                bestDiff = diff;
                best = img;
            }
        }
        return best;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Internal: App icons
    // ══════════════════════════════════════════════════════════════════════════════

    private static void installAppIcons(Variant variant) {
        List<Image> icons = new ArrayList<>();

        for (int size : APP_ICON_SIZES) {
            String resourcePath = APP_ICON_PREFIX + variant.getSuffix() + "_" + size + ".png";
            Image img = loadImage(resourcePath);
            if (img != null) {
                icons.add(img);
            } else {
                // Check if this is a required size
                if (isRequiredSize(size)) {
                    LOG.warning("[IconTheme] Required app icon missing: " + resourcePath);
                }
            }
        }

        if (icons.isEmpty()) {
            LOG.warning("[IconTheme] No app icons found! Window icons will not be set.");
            return;
        }

        appIconImages = icons;

        // Set default icons for all future windows via a hidden root frame
        // This ensures JOptionPane dialogs etc. also get the brand icon
        try {
            // Use Toolkit to set default icon (Java 9+)
            // For Java 8 compatibility, we set via the shared owner frame
            Frame[] frames = Frame.getFrames();
            for (Frame f : frames) {
                f.setIconImages(icons);
            }

            // Set on the "shared owner frame" used by JOptionPane/JDialog without parent
            // This is the key to replacing the coffee-cup icon everywhere
            try {
                java.lang.reflect.Method m = SwingUtilities.class.getDeclaredMethod("getSharedOwnerFrame");
                m.setAccessible(true);
                Frame sharedFrame = (Frame) m.invoke(null);
                sharedFrame.setIconImages(icons);
            } catch (Exception ex) {
                LOG.log(Level.FINE, "[IconTheme] Could not set shared owner frame icons via reflection", ex);
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "[IconTheme] Could not set shared owner frame icons", e);
        }

        // Also try Taskbar icon (Java 9+), gracefully degrade
        // Use the largest available icon for maximum resolution
        try {
            Class<?> taskbarClass = Class.forName("java.awt.Taskbar");
            Object taskbar = taskbarClass.getMethod("getTaskbar").invoke(null);
            // icons list is sorted descending, so icons.get(0) is the largest
            taskbarClass.getMethod("setIconImage", Image.class).invoke(taskbar, icons.get(0));
        } catch (Exception e) {
            LOG.log(Level.FINE, "[IconTheme] Taskbar API not available (Java 8?), skipping.", e);
        }

        LOG.info("[IconTheme] App icons installed (" + icons.size() + " sizes).");
    }

    private static boolean isRequiredSize(int size) {
        for (int s : REQUIRED_APP_SIZES) {
            if (s == size) return true;
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Internal: UIManager defaults
    // ══════════════════════════════════════════════════════════════════════════════

    private static void installUIDefaults() {
        int applied = 0;
        int skipped = 0;

        for (Map.Entry<String, String> entry : UI_ICON_MAPPING.entrySet()) {
            String uiKey = entry.getKey();
            String resourcePath = entry.getValue();

            ImageIcon icon = loadIcon(resourcePath);
            if (icon != null) {
                UIManager.put(uiKey, icon);
                applied++;
            } else {
                // Only warn for required icons
                if (REQUIRED_UI_ICONS.contains(resourcePath)) {
                    LOG.warning("[IconTheme] Required UI icon missing: " + resourcePath + " (key: " + uiKey + ")");
                } else {
                    LOG.fine("[IconTheme] Optional UI icon not found: " + resourcePath + " (key: " + uiKey + ")");
                }
                skipped++;
            }
        }

        LOG.info("[IconTheme] UI defaults applied: " + applied + ", skipped: " + skipped);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Internal: Mode detection
    // ══════════════════════════════════════════════════════════════════════════════

    private static Mode detectMode() {
        // Check if any /ui/ resource exists
        URL probe = IconThemeInstaller.class.getResource("/ui/optionpane_information_32.png");
        if (probe != null) {
            LOG.info("[IconTheme] UI icons detected → FULL mode.");
            return Mode.FULL;
        }
        LOG.info("[IconTheme] No UI icons found → MINIMAL mode.");
        return Mode.MINIMAL;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Internal: Image loading & caching
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Load an image from the classpath, with caching.
     *
     * @param resourcePath classpath resource, e.g. "/app/mainframemate_hub_transparent_32.png"
     * @return loaded Image, or null if not found
     */
    private static Image loadImage(String resourcePath) {
        Image cached = IMAGE_CACHE.get(resourcePath);
        if (cached != null) return cached;

        URL url = IconThemeInstaller.class.getResource(resourcePath);
        if (url == null) {
            return null;
        }

        try {
            ImageIcon imageIcon = new ImageIcon(url);
            Image img = imageIcon.getImage();
            if (img == null || img.getWidth(null) <= 0) {
                LOG.warning("[IconTheme] Failed to decode image: " + resourcePath);
                return null;
            }
            IMAGE_CACHE.put(resourcePath, img);
            return img;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[IconTheme] Error loading image: " + resourcePath, e);
            return null;
        }
    }

    /**
     * Load an ImageIcon from the classpath, with caching.
     *
     * @param resourcePath classpath resource
     * @return ImageIcon or null if not found
     */
    private static ImageIcon loadIcon(String resourcePath) {
        Image img = loadImage(resourcePath);
        if (img == null) return null;
        return new ImageIcon(img);
    }
}

