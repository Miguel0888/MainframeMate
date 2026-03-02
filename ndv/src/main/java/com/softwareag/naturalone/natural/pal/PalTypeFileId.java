package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeFileId;
import com.softwareag.naturalone.natural.pal.external.PalDate;

public final class PalTypeFileId extends PalType implements IPalTypeFileId {
    private static final long serialVersionUID = 1L;
    private String object = "";
    private String newObject = "";
    private String user = "";
    private String gpUser = "";
    private int sourceSize;
    private int gpSize;
    private int natKind;
    private int natType;
    private int databaseId;
    private int fileNumber;
    private boolean isStructured;
    private int options;
    private PalDate sourceDate = new PalDate();
    private PalDate gpDate = new PalDate();

    public String getNewObject() {
        return this.newObject;
    }

    public boolean isStructured() {
        return this.isStructured;
    }

    public PalTypeFileId() {
        super.type = 23;
    }

    public void serialize() {
        this.stringToBuffer(this.object);
        this.stringToBuffer(this.newObject);
        this.stringToBuffer(this.user);
        this.intToBuffer(this.sourceSize);
        this.intToBuffer(this.gpSize);
        this.intToBuffer(this.natKind);
        this.intToBuffer(this.natType);
        this.intToBuffer(this.isStructured ? 1 : 0);
        this.intToBuffer(this.sourceDate.getDay());
        this.intToBuffer(this.sourceDate.getMonth());
        this.intToBuffer(this.sourceDate.getYear());
        this.intToBuffer(this.sourceDate.getHour());
        this.intToBuffer(this.sourceDate.getMinute());
        this.intToBuffer(this.gpDate.getDay());
        this.intToBuffer(this.gpDate.getMonth());
        this.intToBuffer(this.gpDate.getYear());
        this.intToBuffer(this.gpDate.getHour());
        this.intToBuffer(this.gpDate.getMinute());
        this.stringToBuffer(this.gpUser);
        this.intToBuffer(this.databaseId);
        this.intToBuffer(this.fileNumber);
        this.intToBuffer(this.options);
    }

    public void restore() {
    }

    public void setDatabaseId(int databaseId) {
        this.databaseId = databaseId;
    }

    public void setFileNumber(int fileNumber) {
        this.fileNumber = fileNumber;
    }

    public void setGpDate(PalDate gpDate) {
        this.gpDate = gpDate;
    }

    public void setGpSize(int gpSize) {
        this.gpSize = gpSize;
    }

    public void setGpUser(String gpUser) {
        this.gpUser = gpUser;
    }

    public void setStructured(boolean structured) {
        this.isStructured = structured;
    }

    public void setNatKind(int natKind) {
        this.natKind = natKind;
    }

    public void setNatType(int natType) {
        this.natType = natType;
    }

    public void setNewObject(String newObject) {
        this.newObject = newObject;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public void setSourceDate(PalDate sourceDate) {
        this.sourceDate = sourceDate;
    }

    public void setSourceSize(int sourceSize) {
        this.sourceSize = sourceSize;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public final String getObject() {
        return this.object;
    }

    public final int getNatType() {
        return this.natType;
    }

    public int getNatKind() {
        return this.natKind;
    }

    public void setOptions(int options) {
        this.options |= options;
    }
}

