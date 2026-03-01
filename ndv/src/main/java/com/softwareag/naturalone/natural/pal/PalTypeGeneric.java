package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeGeneric;

public final class PalTypeGeneric extends PalType implements IPalTypeGeneric {
    private static final long serialVersionUID = 1L;
    private int dataType;
    private Object data;

    public PalTypeGeneric() { super(); type = 20; }
    public PalTypeGeneric(int dataType, Object data) { this(); this.dataType = dataType; this.data = data; }

    public void serialize() {
        intToBuffer(dataType);
        if (data != null) {
            if (dataType == TYPE_STRING) { stringToBuffer(data.toString()); }
            else { intToBuffer(((Number) data).intValue()); }
        }
    }
    public void restore() {
        dataType = intFromBuffer();
        if (dataType == TYPE_STRING) { data = stringFromBuffer(); }
        else { data = intFromBuffer(); }
    }

    public int getData() { return ((Number) data).intValue(); }
    public Object getDataObject() { return data; }
    public void setData(int dataType, int value) { this.dataType = dataType; this.data = value; }
    public void setDataObject(int dataType, Object data) { this.dataType = dataType; this.data = data; }
}
