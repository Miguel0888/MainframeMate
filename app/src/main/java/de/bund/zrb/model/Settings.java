package de.bund.zrb.model;

import de.bund.zrb.files.ftpconfig.FtpFileStructure;
import de.bund.zrb.files.ftpconfig.FtpFileType;
import de.bund.zrb.files.ftpconfig.FtpTextFormat;
import de.bund.zrb.files.ftpconfig.FtpTransferMode;

import java.util.*;

public class Settings {
    public String host;
    public String user;
    public String encryptedPassword;
    public boolean autoConnect = true;
    public boolean savePassword = false;

    @Deprecated // können entfernt, werden da sie in eine seperate JSON ausgelagert wurden
    public Map<String, String> bookmarks = new HashMap<>(); // Pfad → Name
    public Map<String, String> applicationState = new HashMap<>(); // Zustände wie Seitenleiste ein oder aus

    public String encoding = "ISO-8859-1"; // Standardwert
    public boolean soundEnabled = true;
    public int importDelay = 3;
    public List<String> supportedFiles = Arrays.asList( ".xls", ".xlsx", ".xlsm");

    /** Password encryption method: "WINDOWS_DPAPI" (default), "POWERSHELL_DPAPI", "JAVA_AES", or "KEEPASS". */
    public String passwordMethod = "WINDOWS_DPAPI";

    /** Path to KeePass 2.x installation directory (containing KeePass.exe). Only used when passwordMethod = KEEPASS. */
    public String keepassInstallPath = resolveKeePassDefault();

    /** Path to the .kdbx database file (only used when passwordMethod = KEEPASS). */
    public String keepassDatabasePath = "G:\\Datenbank.kdbx";

    /** Entry title inside the KeePass database to read/write credentials (only used when passwordMethod = KEEPASS). */
    public String keepassEntryTitle = "MainframeMate";

    /** KeePass access method: "POWERSHELL" (default) or "RPC" (KeePassRPC plugin). */
    public String keepassAccessMethod = "POWERSHELL";

    /** Default host for KeePassRPC — IPv4 to avoid IPv6 "Connection reset". */
    public static final String DEFAULT_RPC_HOST = "127.0.0.1";

    /** KeePassRPC host address (default 127.0.0.1). Only used when keepassAccessMethod = RPC. */
    public String keepassRpcHost = DEFAULT_RPC_HOST;

    /** KeePassRPC port (default 12546). Only used when keepassAccessMethod = RPC. */
    public int keepassRpcPort = 12546;

    /** Shared SRP key for KeePassRPC authentication (from KeePass pairing dialog). */
    public String keepassRpcKey = "";

    /** Origin scheme prefix for WebSocket connections to KeePassRPC (e.g. "chrome-extension://"). */
    public String keepassRpcOriginScheme = "chrome-extension://";

    /** Origin identifier appended to the scheme (e.g. "mainframemate" or a UUID). */
    public String keepassRpcOriginId = "mainframemate";

    /** Returns the effective RPC host, falling back to {@link #DEFAULT_RPC_HOST} if null/empty. */
    public String getEffectiveRpcHost() {
        return (keepassRpcHost != null && !keepassRpcHost.trim().isEmpty())
                ? keepassRpcHost.trim()
                : DEFAULT_RPC_HOST;
    }

    /** Returns the full Origin header value for KeePassRPC WebSocket connections. */
    public String getEffectiveRpcOrigin() {
        String scheme = (keepassRpcOriginScheme != null && !keepassRpcOriginScheme.isEmpty())
                ? keepassRpcOriginScheme : "chrome-extension://";
        String id = (keepassRpcOriginId != null && !keepassRpcOriginId.isEmpty())
                ? keepassRpcOriginId : "mainframemate";
        return scheme + id;
    }

    public boolean lockEnabled = true;
    public int lockDelay = 180_000;
    public int lockPrenotification = 10_000;
    public int lockStyle = 0;

    // Plugin-Settings pro Plugin-Name
    public Map<String, Map<String, String>> pluginSettings = new LinkedHashMap<>();

    public String editorFont = "Monospaced"; // default
    public int editorFontSize = 12; // Standardgröße
    public String lineEnding = "FF01"; // Hex-Werte
    public String fileEndMarker = "FF02"; // z.B. "FF02", oder leer/null = deaktiviert
    public String padding = "00"; // "00", oder leer/null = deaktiviert
    public int marginColumn = 80; // 0 bedeutet: keine Linie

    public Map<String, String> fieldColorOverrides = new HashMap<>();

    public FtpFileType ftpFileType;
    public FtpTextFormat ftpTextFormat;
    public FtpFileStructure ftpFileStructure; // Enum-Name aus FtpFileStructure oder null für "Automatisch"
    public FtpTransferMode ftpTransferMode;

    public boolean enableHexDump = false; // Standard = aus
    public boolean removeFinalNewline = true; // Standard = an

    // FTP Timeouts (0 = deaktiviert/unendlich)
    public int ftpConnectTimeoutMs = 0;      // Connect timeout in ms (0 = aus)
    public int ftpControlTimeoutMs = 0;      // Control socket SO_TIMEOUT in ms (0 = aus)
    public int ftpDataTimeoutMs = 0;         // Data transfer timeout in ms (0 = aus)

    // FTP Retry-Konfiguration
    public int ftpRetryMaxAttempts = 2;           // Gesamtanzahl Versuche (1 = kein Retry)
    public int ftpRetryBackoffMs = 0;             // Wartezeit zwischen Versuchen (0 = keine)
    public String ftpRetryBackoffStrategy = "FIXED"; // FIXED oder EXPONENTIAL
    public int ftpRetryMaxBackoffMs = 0;          // Max Backoff bei EXPONENTIAL (0 = keine Kappung)
    public boolean ftpRetryOnTimeout = true;      // Retry bei Timeout-Exceptions
    public boolean ftpRetryOnTransientIo = true;  // Retry bei transienten IO-Fehlern
    public String ftpRetryOnReplyCodes = "";      // Kommaseparierte FTP Reply Codes (z.B. "421,425,426")

    // FTP Initial HLQ (Startverzeichnis nach Login)
    public boolean ftpUseLoginAsHlq = true;       // true = Login-Name als HLQ verwenden
    public String ftpCustomHlq = "";              // Benutzerdefinierter HLQ (nur wenn ftpUseLoginAsHlq=false)

    // NDV (Natural Development Server) Settings
    public int ndvPort = 8011;                     // NDV-Server Port (Standard: 8011)
    public String ndvDefaultLibrary = "";           // Default-Bibliothek (optional, leer = keine)
    public String ndvLibPath = "";                  // Pfad zu NDV-JARs (leer = ~/.mainframemate/lib/)

    /** JCL-Steplib → NDV library mapping for Natural programs (e.g. "ABAK-M" → "ABAK-T"). */
    public Map<String, String> naturalLibraryMappings = new LinkedHashMap<>();

    // BetaView Settings
    public String betaviewUrl = "";                 // BetaView Base URL (z.B. https://betaview.example.com/betaview/)
    public boolean betaviewUseSharedCredentials = true;  // true = use host/user from Server Settings (FTP/NDV)
    public String betaviewHost = "";                // Eigener BetaView-Host (nur wenn betaviewUseSharedCredentials=false)
    public String betaviewUser = "";                // Eigener BetaView-User (nur wenn betaviewUseSharedCredentials=false)
    public String betaviewEncryptedPassword = "";   // Eigenes verschlüsseltes Passwort (nur wenn betaviewUseSharedCredentials=false)
    public String betaviewFavoriteId = "";           // Default Favorite ID
    public String betaviewLocale = "de";             // Default Locale
    public String betaviewExtension = "*";           // Default Extension Pattern
    public String betaviewForm = "APZF";             // Default Form
    public int betaviewDaysBack = 60;                // Default Tage zurück

    // JES Spool Settings
    public String jesSpoolDdNameMode = "FAST";         // FAST = schnell laden + Content-Erkennung, PROBE = parallel einzeln abrufen, OFF = SPOOL#n + Hintergrund-Nachladen
    public int jesProbeParallelConnections = 1;        // Anzahl paralleler FTP-Verbindungen für Probe (1-10, default 1)
    public boolean jesFastBackgroundProbe = false;     // Im FAST-Modus DDNames im Hintergrund per Probe nachladen

    // TN3270 Terminal Settings
    public int tn3270Port = 992;                       // TN3270 Port (Standard: 992 für TLS, 23 für Klartext)
    public boolean tn3270Tls = true;                   // TLS/SSL verwenden (Standard: an)
    public String tn3270TermType = "IBM-3278-2";       // Terminal-Typ (z.B. IBM-3278-2, IBM-3279-2)
    public int tn3270KeepAliveTimeout = 0;             // KeepAlive in Sekunden (0 = deaktiviert)
    public boolean tn3270AutoLogin = true;             // Auto-Login nach Verbindung (Standard: an)
    public boolean tn3270AutoCommand = true;           // Nach Login automatisch einen Befehl senden (Standard: an)
    public String tn3270AutoCommandText = "a";         // Der zu sendende Befehl (Standard: "a")
    public int tn3270ActionDelayMs = 1000;             // Wartezeit in ms nach AID-Tasten bei Auto-Login/Makro (Standard: 1000)
    public int tn3270FkeyOverlayOpacity = 50;            // Transparenz der F-Tasten-Leiste in Prozent (0=unsichtbar, 100=deckend, Standard: 50)
    public boolean cosmicClockEnabled = true;              // Kosmische Uhr als Terminalhintergrund (false = klassisch schwarz)
    public double cosmicClockTimeFactor = 120;             // Zeitfaktor für die Cosmic Clock (1 = Echtzeit, 120 = 2min ≈ 4h)
    public boolean cosmicClockGermanNames = true;          // Sternbild-Namen auf Deutsch anzeigen (false = Englisch/Latein)
    public java.util.List<MouseFkeyBinding> tn3270MouseFkeyBindings = MouseFkeyBinding.getDefaults(); // Maus→F-Key Zuordnungen

    /**
     * @deprecated Wiki sites are now managed via {@link #passwordEntries} (category "Wiki").
     * This field is kept only for backward compatibility with old settings files.
     */
    @Deprecated
    public List<String> wikiSites = new ArrayList<>(java.util.Arrays.asList(
            "wikipedia_de|Wikipedia (DE)|https://de.wikipedia.org/w/|false",
            "wikipedia_en|Wikipedia (EN)|https://en.wikipedia.org/w/|false"
    ));

    /**
     * Encrypted wiki credentials per site. Key = siteId, Value = encrypted "user|password".
     * @deprecated Use {@link #componentCredentials} with "wiki:&lt;siteId&gt;" keys instead.
     */
    @Deprecated
    public Map<String, String> wikiCredentials = new HashMap<>();

    /**
     * Generalized credential store. Any component can store credentials here.
     * <p>Key = component identifier (e.g. "wiki:wikipedia_de", "betaview", "ftp:myhost", "pwd:myentry"),
     * Value = encrypted "user|password" (via {@link de.bund.zrb.util.WindowsCryptoUtil}).
     * <p>Managed centrally in Einstellungen → Allgemein → Sicherheit.
     */
    public Map<String, String> componentCredentials = new LinkedHashMap<>();

    /**
     * Central password entry metadata managed under <em>Hilfe → Passwörter</em>.
     * <p>Each entry stores metadata (category, display name, URL, flags).
     * The encrypted credentials (user|password) are stored in {@link #componentCredentials}
     * with key {@code "pwd:<id>"}.
     */
    public List<PasswordEntryMeta> passwordEntries = new ArrayList<>();

    /**
     * Metadata for a single password entry. Serialised as part of {@code settings.json}.
     * The actual password is stored encrypted in {@link #componentCredentials}.
     */
    public static class PasswordEntryMeta {
        public String id = "";
        public String category = "Mainframe";
        public String displayName = "";
        public String url = "";
        public String certAlias = "";
        public boolean requiresLogin;
        public boolean useProxy;
        public boolean autoIndex;
        public boolean savePassword;
        public boolean sessionCache;
    }

    /** Maximum size in MB for volatile (prefetch) wiki cache entries. Default: 50 MB. */
    public int wikiPrefetchCacheMaxMb = 50;
    /** Max number of wiki pages to prefetch starting from cursor position. Default: 100. */
    public int wikiPrefetchMaxItems = 100;
    /** Number of concurrent prefetch HTTP requests. Default: 4. */
    public int wikiPrefetchConcurrency = 4;

    public HashMap<String, String> aiConfig = new HashMap<>();
    public HashMap<String, String> embeddingConfig = new HashMap<>(); // Separate embedding settings
    public String defaultWorkflow = "";
    public HashMap<String, List<String>> fileImportVariables = new HashMap<>();
    public long workflowTimeout = 10_000; // 10 Sekunden default
    public boolean compareByDefault = false;
    public boolean showHelpIcons = true; // Hilfe-Icons anzeigen (für erfahrene Benutzer deaktivierbar)

    // Proxy
    public boolean proxyEnabled = false;
    public String proxyMode = "WINDOWS_PAC";
    public String proxyHost = "";
    public int proxyPort = 0;
    public boolean proxyNoProxyLocal = true;
    public String proxyPacScript = de.bund.zrb.net.ProxyDefaults.DEFAULT_PAC_SCRIPT;
    public String proxyTestUrl = de.bund.zrb.net.ProxyDefaults.DEFAULT_TEST_URL;

    // Mail (OST) Settings
    public String mailStorePath = "";                     // Pfad zum OST-Ordner
    public String mailContainerClasses = "IPF.Note,IPF.Imap"; // ContainerClasses die als MAIL gelten (kommasepariert)
    public java.util.Set<String> mailHtmlWhitelistedSenders = new java.util.HashSet<>(); // Absender, die immer in HTML geöffnet werden

    // Local History
    public boolean historyEnabled = true;                 // Local History aktiviert
    public int historyMaxVersionsPerFile = 100;           // Max Versionen pro Datei
    public int historyMaxAgeDays = 90;                    // Max Alter in Tagen

    // Debug / Logging
    public String logLevel = "INFO";                      // Global log level: OFF, SEVERE, WARNING, INFO, FINE, FINER, FINEST, ALL
    public Map<String, String> logCategoryLevels = new LinkedHashMap<>(); // Per-category overrides e.g. "MAIL" -> "FINE"

    // Video Recording Settings (key-value map for flexibility)
    public Map<String, Object> videoSettings = new LinkedHashMap<String, Object>();

    // Browser Connection Settings
    public String browserType = "Firefox";                  // Firefox, Chrome, Edge
    public String browserPath = "";                         // Pfad zum Browser-Executable (leer = Default)
    public boolean browserHeadless = true;                  // Headless-Modus (Standard: an)
    public int browserDebugPort = 0;                        // Debug-Port (0 = automatisch)
    public int browserNavigateTimeoutSeconds = 30;          // Navigations-Timeout in Sekunden
    public String browserHomePage = "https://zrb.bund.de"; // Startseite

    // SharePoint Connection Settings
    public String sharepointParentPageUrl = "";              // URL der Parent-Seite, aus der SharePoint-Links extrahiert werden
    public int sharepointCacheConcurrency = 2;               // Parallele Downloads beim Caching
    /** JSON array of discovered links: [{"name":"..","url":"..","selected":true}, ...] */
    public String sharepointSitesJson = "[]";
    /** SharePoint WebDAV username (DOMAIN\\user or user@domain). */
    public String sharepointUser = "";
    /** SharePoint WebDAV encrypted password (via WindowsCryptoUtil). */
    public String sharepointEncryptedPassword = "";


    /**
     * Resolves the default KeePass 2.x installation directory using %ProgramFiles(x86)%.
     * Falls back to empty string if the environment variable is not set.
     */
    private static String resolveKeePassDefault() {
        String pf86 = System.getenv("ProgramFiles(x86)");
        if (pf86 != null && !pf86.isEmpty()) {
            return pf86 + "\\KeePass2x";
        }
        return "";
    }
}
