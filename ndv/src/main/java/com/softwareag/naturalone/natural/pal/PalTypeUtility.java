package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeUtility;

import java.util.Arrays;

public final class PalTypeUtility extends PalType implements IPalTypeUtility {
    private static final long serialVersionUID = 1L;
    private byte[] utilityRecord;

    public PalTypeUtility() { super(); type = 14; }
    public PalTypeUtility(byte[] record) {
        this();
        if (record != null) this.utilityRecord = Arrays.copyOf(record, record.length);
    }

    public void serialize() {
        if (utilityRecord != null) byteArrayToBuffer(utilityRecord);
    }
    public void restore() { utilityRecord = recordToByteArray(); }

    public byte[] getUtilityRecord() {
        return utilityRecord != null ? Arrays.copyOf(utilityRecord, utilityRecord.length) : null;
    }
    public void setUtilityRecord(byte[] record) {
        if (record != null) {
            this.utilityRecord = Arrays.copyOf(record, record.length);
        }
    }
}
