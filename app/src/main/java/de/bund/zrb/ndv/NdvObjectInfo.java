package de.bund.zrb.ndv;

import com.softwareag.naturalone.natural.pal.external.IPalTypeObject;
import com.softwareag.naturalone.natural.pal.external.ObjectKind;
import com.softwareag.naturalone.natural.pal.external.ObjectType;
import com.softwareag.naturalone.natural.pal.external.PalDate;

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

    public NdvObjectInfo(String name, String longName, int kind, int type,
                         String typeName, String typeExtension,
                         int sourceSize, String user, String sourceDate,
                         int databaseId, int fileNumber) {
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
    }

    public static NdvObjectInfo fromPalObject(IPalTypeObject obj) {
        String name = obj.getName() != null ? obj.getName().trim() : "";
        String longName = obj.getLongName() != null ? obj.getLongName().trim() : "";
        int kind = obj.getKind();
        int type = obj.getType();

        // Resolve type name and extension
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

        // Capture DBID/FNR for downloadSource (critical for Mainframe/Adabas)
        int dbid = obj.getDatabaseId();
        int fnr = obj.getFileNumber();

        return new NdvObjectInfo(name, longName, kind, type, typeName, typeExt, sourceSize, user, dateStr, dbid, fnr);
    }

    public String getName() { return name; }
    public String getLongName() { return longName; }
    public int getKind() { return kind; }
    public int getType() { return type; }
    public String getTypeName() { return typeName; }
    public String getTypeExtension() { return typeExtension; }
    public int getSourceSize() { return sourceSize; }
    public String getUser() { return user; }
    public String getSourceDate() { return sourceDate; }
    public int getDatabaseId() { return databaseId; }
    public int getFileNumber() { return fileNumber; }

    /**
     * Get display name: name (type).
     */
    public String getDisplayName() {
        return name + " (" + typeName + ")";
    }

    /**
     * Get icon for this object type.
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
        return getIcon() + " " + name + " [" + typeName + "]";
    }

    /**
     * Resolve Natural object type from file extension (e.g. "NSP" → PROGRAM).
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
     * Create a minimal NdvObjectInfo for bookmark reopening (name + type from extension).
     * DBID/FNR default to -1 (will trigger fallback resolution in NdvClient).
     */
    public static NdvObjectInfo forBookmark(String name, String extension) {
        int type = typeFromExtension(extension);
        Hashtable idNames = ObjectType.getInstanceIdName();
        String typeName = idNames.containsKey(type) ? (String) idNames.get(type) : "Unknown";
        return new NdvObjectInfo(name, name, ObjectKind.SOURCE, type, typeName, extension,
                0, "", "", -1, -1);
    }
}

