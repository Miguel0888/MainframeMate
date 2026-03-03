package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeFileId;
import de.bund.zrb.ndv.core.api.PalDate;

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
        super.typSchluessel = 23;
    }

    public void serialize() {
        this.textInPuffer(this.object);
        this.textInPuffer(this.newObject);
        this.textInPuffer(this.user);
        this.ganzzahlInPuffer(this.sourceSize);
        this.ganzzahlInPuffer(this.gpSize);
        this.ganzzahlInPuffer(this.natKind);
        this.ganzzahlInPuffer(this.natType);
        this.ganzzahlInPuffer(this.isStructured ? 1 : 0);
        this.ganzzahlInPuffer(this.sourceDate.getDay());
        this.ganzzahlInPuffer(this.sourceDate.getMonth());
        this.ganzzahlInPuffer(this.sourceDate.getYear());
        this.ganzzahlInPuffer(this.sourceDate.getHour());
        this.ganzzahlInPuffer(this.sourceDate.getMinute());
        this.ganzzahlInPuffer(this.gpDate.getDay());
        this.ganzzahlInPuffer(this.gpDate.getMonth());
        this.ganzzahlInPuffer(this.gpDate.getYear());
        this.ganzzahlInPuffer(this.gpDate.getHour());
        this.ganzzahlInPuffer(this.gpDate.getMinute());
        this.textInPuffer(this.gpUser);
        this.ganzzahlInPuffer(this.databaseId);
        this.ganzzahlInPuffer(this.fileNumber);
        this.ganzzahlInPuffer(this.options);
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

