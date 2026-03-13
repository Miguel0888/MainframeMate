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
    public java.util.List<MouseFkeyBinding> tn3270MouseFkeyBindings = MouseFkeyBinding.getDefaults(); // Maus→F-Key Zuordnungen

    // Wiki Settings
    // Each entry: "id|displayName|apiUrl|requiresLogin|useProxy|autoIndex"
    // e.g. "wikipedia_de|Wikipedia (DE)|https://de.wikipedia.org/w/|false|false|false"
    public List<String> wikiSites = new ArrayList<>(java.util.Arrays.asList(
            "wikipedia_de|Wikipedia (DE)|https://de.wikipedia.org/w/|false",
            "wikipedia_en|Wikipedia (EN)|https://en.wikipedia.org/w/|false"
    ));

    /** Encrypted wiki credentials per site. Key = siteId, Value = "encryptedUser|encryptedPassword". */
    public Map<String, String> wikiCredentials = new HashMap<>();

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

}
