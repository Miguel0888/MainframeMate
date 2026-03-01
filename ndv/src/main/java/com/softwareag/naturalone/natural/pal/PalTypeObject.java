package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeObject;
import com.softwareag.naturalone.natural.pal.external.PalDate;
import com.softwareag.naturalone.natural.paltransactions.internal.PalTimeStamp;

import java.util.Set;

public class PalTypeObject extends PalType implements IPalTypeObject {
    private static final long serialVersionUID = 1L;
    private String name = "";
    private String longName = "";
    private String sourceUser = "";
    private String gpUser = "";
    private String codePage = "";
    private int sourceSize;
    private int gpSize;
    private int natKind;
    private int natType;
    private int databaseId;
    private int fileNumber;
    private boolean structured;
    private PalDate sourceDate = new PalDate();
    private PalDate gpDate = new PalDate();
    private PalDate accessDate = new PalDate();
    private String internalLabelFirst = "";
    private boolean insertLineNumber;
    private boolean removeLineNumber;
    private PalTimeStamp timeStamp;
    private int extraFlags;

    public PalTypeObject() { super(); type = 8; }

    public void serialize() { /* server-only */ }
    public void restore() {
        name = stringFromBuffer();
        natKind = intFromBuffer();
        natType = intFromBuffer();
        sourceSize = intFromBuffer();
        gpSize = intFromBuffer();
        sourceUser = stringFromBuffer();
        sourceDate = readPalDate();
        gpDate = readPalDate();
        structured = booleanFromBuffer();
        databaseId = intFromBuffer();
        fileNumber = intFromBuffer();
        longName = stringFromBuffer();
        internalLabelFirst = stringFromBuffer();
        insertLineNumber = booleanFromBuffer();
        removeLineNumber = booleanFromBuffer();
        // optional fields
        if (recordTail < recordLength) accessDate = readPalDate();
        if (recordTail < recordLength) gpUser = stringFromBuffer();
        if (recordTail < recordLength) codePage = stringFromBuffer();
        if (recordTail < recordLength) extraFlags = intFromBuffer();
        // timeStamp optional
        if (recordTail < recordLength) {
            String tsText = stringFromBuffer();
            String tsUser = recordTail < recordLength ? stringFromBuffer() : "";
            if (tsText != null && !tsText.isEmpty()) {
                timeStamp = PalTimeStamp.get(tsText, tsUser);
            }
        }
    }

    private PalDate readPalDate() {
        int day = intFromBuffer(); int month = intFromBuffer(); int year = intFromBuffer();
        int hour = intFromBuffer(); int minute = intFromBuffer();
        return new PalDate(day, month, year, hour, minute);
    }

    public String getName() { return name; }
    public String getLongName() { return (longName != null && !longName.isEmpty()) ? longName : name; }
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
    public String getSourceUser() { return sourceUser; }
    public String getGpUser() { return gpUser; }
    public String getCodePage() { return codePage; }
    public void setCodePage(String cp) { codePage = cp; }
    public int getSourceSize() { return sourceSize; }
    public int getGpSize() { return gpSize; }
    public PalDate getSourceDate() { return sourceDate; }
    public PalDate getGpDate() { return gpDate; }
    public PalDate getAccessDate() { return accessDate; }
    public boolean isStructured() { return structured; }
    public void setStructured(boolean s) { structured = s; }

    public String getUser() {
        if (natKind == 1 || natKind == 16 || natKind == 64) return sourceUser;
        return gpUser;
    }
    public PalDate getDate() {
        if (natKind == 1 || natKind == 16 || natKind == 64) return sourceDate;
        return gpDate;
    }
    public int getSize() {
        if (natKind == 1 || natKind == 16 || natKind == 64) return sourceSize;
        return gpSize;
    }

    public boolean isInsertLineNumber() { return insertLineNumber; }
    public void setInsertLineNumber(boolean f) { insertLineNumber = f; }
    public boolean isRemoveLineNumber() { return removeLineNumber; }
    public void setRemoveLineNumber(boolean f) { removeLineNumber = f; }
    public String getInternalLabelFirst() { return internalLabelFirst; }
    public void setInternalLabelFirst(String l) { internalLabelFirst = l; }
    public Set getOptions() { return null; }
    public PalTimeStamp getTimeStamp() { return timeStamp; }
    public boolean isLinkedDdm() { return natType == 8 && (extraFlags & FLAGS_IS_LINKED_DDM) != 0; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PalTypeObject)) return false;
        PalTypeObject t = (PalTypeObject) o;
        return natType == t.natType && natKind == t.natKind && sourceSize == t.sourceSize && gpSize == t.gpSize &&
                name.equals(t.name) && longName.equals(t.longName);
    }
    public int hashCode() { int r = 17; r = 37 * r + natType; r = 37 * r + natKind; r = 37 * r + name.hashCode(); return r; }
    public String toString() { return getLongName() + " [" + natKind + "]"; }
}
