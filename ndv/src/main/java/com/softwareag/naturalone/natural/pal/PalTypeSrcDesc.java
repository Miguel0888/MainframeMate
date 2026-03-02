package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeSrcDesc;

public class PalTypeSrcDesc extends PalType implements IPalTypeSrcDesc {
    private static final long serialVersionUID = 1L;
    private int lineCount;
    private int sourceLength;
    private int natType;
    private int flags;
    private String shortName = "";
    private String longName = "";
    private int databaseId;
    private int fileNumber;
    private int options;
    private int errorLine;
    private int errorColumn;

    public PalTypeSrcDesc() { super(); type = 15; }
    public PalTypeSrcDesc(int natType, String sourceName, boolean isSaved, int options) {
        this(); this.natType = natType;
        this.shortName = sourceName != null ? sourceName : "";
        this.longName = sourceName != null ? sourceName : "";
        if (isSaved) this.flags |= 1;
        this.options = options;
    }
    public PalTypeSrcDesc(int natType, String sourceName, boolean isSaved, int databaseId, int fileNumber) {
        this(natType, sourceName, isSaved, 0);
        this.databaseId = databaseId; this.fileNumber = fileNumber;
    }

    public void serialize() {
        intToBuffer(lineCount); intToBuffer(sourceLength); intToBuffer(natType); intToBuffer(flags);
        stringToBuffer(shortName); stringToBuffer(longName);
        intToBuffer(databaseId); intToBuffer(fileNumber); intToBuffer(options);
    }
    public void restore() {
        lineCount = intFromBuffer(); sourceLength = intFromBuffer(); natType = intFromBuffer(); flags = intFromBuffer();
        shortName = stringFromBuffer(); longName = stringFromBuffer();
        databaseId = intFromBuffer(); fileNumber = intFromBuffer();
    }

    public String getSourceLongName() { return longName; }
    public void setType(int natType) { this.natType = natType; }
    public int getType() { return natType; }
    public void setObject(String name) { this.shortName = name != null ? name : ""; this.longName = this.shortName; }
    public String getObject() { return shortName; }
    public int getErrorLine() { return errorLine; }
    public int getErrorColumn() { return errorColumn; }
}
