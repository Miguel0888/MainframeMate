package com.softwareag.naturalone.natural.pal.type;

import com.softwareag.naturalone.natural.pal.external.IPalTypeObject;
import com.softwareag.naturalone.natural.pal.external.ObjectKind;
import com.softwareag.naturalone.natural.pal.external.ObjectType;
import com.softwareag.naturalone.natural.pal.external.PalDate;
import com.softwareag.naturalone.natural.paltransactions.internal.PalTimeStamp;

import java.util.Set;

public final class PalTypeObject extends PalType implements IPalTypeObject {
    private static final long serialVersionUID = 1L;
    private String object = "";
    private String longName = "";
    private String user = "";
    private String gpUser = "";
    private String codePage = "";
    private int sourceSize;
    private int gpSize;
    private int natKind;
    private int natType;
    private int databaseId;
    private int fileNumber;
    private boolean isStructured;
    private PalDate sourceDate = new PalDate();
    private PalDate gpDate = new PalDate();
    private PalDate accessDate = new PalDate();
    private String internalLabelFirst = "";
    private boolean isInsertLineNumber;
    private boolean isRemoveLineNumber;
    private PalTimeStamp timeStamp;
    private int flags;

    public PalTypeObject() { super.type = 8; }

    public void serialize() { /* server-only type, not sent by client */ }

    public void restore() {
        object = stringFromBuffer();
        longName = stringFromBuffer();
        user = stringFromBuffer();
        sourceSize = intFromBuffer();
        gpSize = intFromBuffer();
        natKind = intFromBuffer();
        natType = intFromBuffer();
        databaseId = intFromBuffer();
        fileNumber = intFromBuffer();

        // Language/error message special logic
        if (natKind == 64 || natType == 32768) {
            longName = (String) ObjectType.getUnmodifiableLanguageList().get(Integer.valueOf(object) - 1);
            if (natKind == 0) {
                natKind = 64;
            }
        }

        // Resource special logic
        if (natType == 65536 && natKind == 0) {
            natKind = 16;
        }

        isStructured = intFromBuffer() != 0;
        sourceDate = new PalDate(intFromBuffer(), intFromBuffer(), intFromBuffer(), intFromBuffer(), intFromBuffer());
        gpDate = new PalDate(intFromBuffer(), intFromBuffer(), intFromBuffer(), intFromBuffer(), intFromBuffer());

        if (recordTail < recordLength) {
            accessDate = new PalDate(intFromBuffer(), intFromBuffer(), intFromBuffer(), intFromBuffer(), intFromBuffer());
        }
        if (recordTail < recordLength) gpUser = stringFromBuffer();
        if (recordTail < recordLength) codePage = stringFromBuffer();
        if (recordTail < recordLength) flags = intFromBuffer();
    }

    public String getName() { return object; }

    public String getLongName() {
        if (longName.compareTo("") == 0) {
            longName = getName();
        }
        return longName;
    }

    public int getKind() { return natKind; }
    public void setKind(int k) { natKind = k; }
    public int getType() { return natType; }
    public void setType(int t) { natType = t; }
    public int getDatabaseId() { return databaseId; }
    public int getDatbaseId() { return databaseId; }
    public void setDatabaseId(int id) { databaseId = id; }
    public int getFileNumber() { return fileNumber; }
    public int getFnr() { return fileNumber; }
    public void setFileNumber(int n) { fileNumber = n; }
    public String getSourceUser() { return user; }
    public String getGpUser() { return gpUser; }
    public String getCodePage() { return codePage; }
    public void setCodePage(String cp) { codePage = cp; }
    public int getSourceSize() { return sourceSize; }
    public int getGpSize() { return gpSize; }
    public PalDate getSourceDate() { return sourceDate; }
    public PalDate getGpDate() { return gpDate; }
    public PalDate getAccessDate() { return accessDate; }
    public boolean isStructured() { return isStructured; }
    public void setStructured(boolean s) { isStructured = s; }

    public String getUser() {
        return natKind != 1 && natKind != 16 && natKind != 64 ? gpUser : user;
    }

    public PalDate getDate() {
        return natKind != 1 && natKind != 16 && natKind != 64 ? gpDate : sourceDate;
    }

    public int getSize() {
        return natKind != 1 && natKind != 16 && natKind != 64 ? gpSize : sourceSize;
    }

    public boolean isInsertLineNumber() { return isInsertLineNumber; }
    public void setInsertLineNumber(boolean f) { isInsertLineNumber = f; }
    public boolean isRemoveLineNumber() { return isRemoveLineNumber; }
    public void setRemoveLineNumber(boolean f) { isRemoveLineNumber = f; }
    public String getInternalLabelFirst() { return internalLabelFirst; }
    public void setInternalLabelFirst(String l) { internalLabelFirst = l; }
    public Set getOptions() { return null; }
    public PalTimeStamp getTimeStamp() { return timeStamp; }
    public boolean isLinkedDdm() { return getType() == 8 && (flags & 1) == 1; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PalTypeObject)) return false;
        PalTypeObject t = (PalTypeObject) o;
        return natType == t.natType && natKind == t.natKind
                && sourceSize == t.sourceSize && gpSize == t.gpSize
                && isStructured == t.isStructured
                && object.equals(t.object) && longName.equals(t.longName)
                && user.equals(t.user) && gpUser.equals(t.gpUser)
                && gpDate.equals(t.gpDate) && sourceDate.equals(t.sourceDate)
                && accessDate.equals(t.accessDate);
    }

    public int hashCode() {
        int r = 17;
        r = 37 * r + natType;
        r = 37 * r + natKind;
        r = 37 * r + sourceSize;
        r = 37 * r + gpSize;
        r = 37 * r + longName.hashCode();
        r = 37 * r + object.hashCode();
        return r;
    }

    public String toString() {
        String displayName;
        String detail;
        if (longName != null && longName.length() > 0) {
            displayName = longName;
        } else {
            displayName = object;
        }
        if (natKind == 1 || natKind == 2 || natKind == 3) {
            String mode = isStructured ? "STRUCTURED" : "REPORTING";
            detail = " [" + ObjectType.getInstanceIdName().get(natType) + " - " + ObjectKind.get(natKind) + " - " + mode + "]";
        } else {
            detail = " [" + ObjectKind.get(natKind) + "]";
        }
        return displayName + detail;
    }
}
