package de.bund.zrb.ndv;

import de.bund.zrb.ndv.core.api.IPalTypeObject;
import de.bund.zrb.ndv.core.api.ObjectKind;
import de.bund.zrb.ndv.core.api.ObjectType;
import de.bund.zrb.ndv.core.api.PalDate;

import java.util.Hashtable;

/**
 * Info about a Natural object on the NDV server.
 * Wraps the data from IPalTypeObject into a simple POJO.
 */
public class NdvObjectInfo {

    private final String name;
    private final String longName;
    private final int kind;
    private final int type;
    private final String typeName;
    private final String typeExtension;
    private final int sourceSize;
    private final String user;
    private final String sourceDate;
    private final int databaseId;
    private final int fileNumber;
    private final boolean structured;
    private final String codePage;
    private final String gpUser;
    private final String gpDate;
    private final String accessDate;

    public NdvObjectInfo(String name, String longName, int kind, int type,
                         String typeName, String typeExtension,
                         int sourceSize, String user, String sourceDate,
                         int databaseId, int fileNumber) {
        this(name, longName, kind, type, typeName, typeExtension,
                sourceSize, user, sourceDate, databaseId, fileNumber,
                false, "", "", "", "");
    }

    public NdvObjectInfo(String name, String longName, int kind, int type,
                         String typeName, String typeExtension,
                         int sourceSize, String user, String sourceDate,
                         int databaseId, int fileNumber,
                         boolean structured, String codePage,
                         String gpUser, String gpDate, String accessDate) {
        this.name = name;
        this.longName = longName;
        this.kind = kind;
        this.type = type;
        this.typeName = typeName;
        this.typeExtension = typeExtension;
        this.sourceSize = sourceSize;
        this.user = user;
        this.sourceDate = sourceDate;
        this.databaseId = databaseId;
        this.fileNumber = fileNumber;
        this.structured = structured;
        this.codePage = codePage != null ? codePage : "";
        this.gpUser = gpUser != null ? gpUser : "";
        this.gpDate = gpDate != null ? gpDate : "";
        this.accessDate = accessDate != null ? accessDate : "";
    }

    public static NdvObjectInfo fromPalObject(IPalTypeObject obj) {
        String name = obj.getName() != null ? obj.getName().trim() : "";
        String longName = obj.getLongName() != null ? obj.getLongName().trim() : "";
        int kind = obj.getKind();
        int type = obj.getType();

        // Resolve typSchluessel name and extension
        Hashtable idNames = ObjectType.getInstanceIdName();
        Hashtable idExts = ObjectType.getInstanceIdExtension();
        String typeName = idNames.containsKey(type) ? (String) idNames.get(type) : "Unknown";
        String typeExt = idExts.containsKey(type) ? (String) idExts.get(type) : "";

        int sourceSize = obj.getSourceSize();
        String user = obj.getUser() != null ? obj.getUser().trim() : "";

        // Format date
        String dateStr = "";
        PalDate srcDate = obj.getSourceDate();
        if (srcDate != null) {
            dateStr = srcDate.toString();
        }

        // Capture DATENBANK_NUMMER/DATEI_NUMMER for downloadSource (critical for Mainframe/Adabas)
        int dbid = obj.getDatabaseId();
        int fnr = obj.getFileNumber();

        // ── Additional metadata (Eclipse NaturalONE-style) ──
        boolean structured = obj.isStructured();
        String codePage = obj.getCodePage() != null ? obj.getCodePage().trim() : "";
        String gpUser = obj.getGpUser() != null ? obj.getGpUser().trim() : "";

        String gpDateStr = "";
        PalDate gDate = obj.getGpDate();
        if (gDate != null) {
            gpDateStr = gDate.toString();
        }

        String accessDateStr = "";
        PalDate aDate = obj.getAccessDate();
        if (aDate != null) {
            accessDateStr = aDate.toString();
        }

        return new NdvObjectInfo(name, longName, kind, type, typeName, typeExt,
                sourceSize, user, dateStr, dbid, fnr,
                structured, codePage, gpUser, gpDateStr, accessDateStr);
    }

    public String getName() { return name; }
    public String getLongName() { return longName; }

    /**
     * Get the effective name for display and download operations.
     * For DDMs (type == 8), the authoritative name is in longName (up to 32 chars).
     * The short name (getName) may be empty or truncated.
     * The original Eclipse plugin always uses getLongName() for DDMs:
     * {@code var3.getType() == 8 ? var3.getLongName() : var3.getName()}
     */
    public String getEffectiveName() {
        if (type == ObjectType.DDM && longName != null && !longName.isEmpty()) {
            return longName;
        }
        return name;
    }
    public int getKind() { return kind; }
    public int getType() { return type; }
    public String getTypeName() { return typeName; }
    public String getTypeExtension() { return typeExtension; }
    public int getSourceSize() { return sourceSize; }
    public String getUser() { return user; }
    public String getSourceDate() { return sourceDate; }
    public int getDatabaseId() { return databaseId; }
    public int getFileNumber() { return fileNumber; }
    public boolean isStructured() { return structured; }
    public String getCodePage() { return codePage; }
    public String getGpUser() { return gpUser; }
    public String getGpDate() { return gpDate; }
    public String getAccessDate() { return accessDate; }

    /**
     * Get the programming mode label (Structured/Reporting/–).
     * Only meaningful for Source or Source/GP kinds.
     */
    public String getProgrammingMode() {
        if (kind == ObjectKind.SOURCE || kind == ObjectKind.SOURCE_OR_GP) {
            return structured ? "Structured" : "Reporting";
        }
        return "";
    }

    /**
     * Get the object kind label (Source, GP, Source/GP, Resource, etc.).
     */
    public String getKindName() {
        return ObjectKind.get(kind);
    }

    /**
     * Get display name: name (typSchluessel).
     */
    public String getDisplayName() {
        return getEffectiveName() + " (" + typeName + ")";
    }

    /**
     * Get icon for this object typSchluessel.
     */
    public String getIcon() {
        switch (type) {
            case ObjectType.PROGRAM:    return "▶";
            case ObjectType.SUBPROGRAM: return "⚙";
            case ObjectType.SUBROUTINE: return "🔧";
            case ObjectType.COPYCODE:   return "📋";
            case ObjectType.MAP:        return "🗺";
            case ObjectType.GDA:        return "🌐";
            case ObjectType.LDA:        return "📊";
            case ObjectType.PDA:        return "📑";
            case ObjectType.DDM:        return "🗄";
            case ObjectType.HELPROUTINE:return "❓";
            case ObjectType.TEXT:       return "📝";
            case ObjectType.CLASS:      return "🏛";
            case ObjectType.FUNCTION:   return "ƒ";
            default:                    return "📄";
        }
    }

    /**
     * Check if this object has downloadable source code.
     */
    public boolean hasSource() {
        return kind == ObjectKind.SOURCE || kind == ObjectKind.SOURCE_OR_GP;
    }

    @Override
    public String toString() {
        return getIcon() + " " + getEffectiveName() + " [" + typeName + "]";
    }

    /**
     * Resolve Natural object typSchluessel from file extension (e.g. "NSP" → PROGRAM).
     * Used when reopening bookmarks where only the extension is known.
     *
     * @return the ObjectType id, or ObjectType.PROGRAM as fallback
     */
    @SuppressWarnings("unchecked")
    public static int typeFromExtension(String ext) {
        if (ext == null || ext.isEmpty()) return ObjectType.PROGRAM;
        String upper = ext.toUpperCase();
        Hashtable<Integer, String> idExts = ObjectType.getInstanceIdExtension();
        for (java.util.Map.Entry<Integer, String> entry : idExts.entrySet()) {
            if (upper.equals(String.valueOf(entry.getValue()).toUpperCase())) {
                return entry.getKey();
            }
        }
        return ObjectType.PROGRAM; // fallback
    }

    /**
     * Create a minimal NdvObjectInfo for bookmark reopening (name + typSchluessel from extension).
     * DATENBANK_NUMMER/DATEI_NUMMER default to -1 (will trigger fallback resolution in NdvClient).
     */
    public static NdvObjectInfo forBookmark(String name, String extension) {
        int type = typeFromExtension(extension);
        Hashtable idNames = ObjectType.getInstanceIdName();
        String typeName = idNames.containsKey(type) ? (String) idNames.get(type) : "Unknown";
        return new NdvObjectInfo(name, name, ObjectKind.SOURCE, type, typeName, extension,
                0, "", "", -1, -1);
    }
}

