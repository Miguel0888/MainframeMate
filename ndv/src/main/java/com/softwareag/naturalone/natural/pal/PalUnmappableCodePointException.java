package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeSource;

public class PalUnmappableCodePointException extends IllegalStateException {
    private static final long serialVersionUID = 1L;
    IPalTypeSource palTypeSource = null;
    int column = -1;
    int row = -1;

    public PalUnmappableCodePointException(String message, IPalTypeSource source, int column) {
        super(message);
        this.palTypeSource = source;
        this.column = column;
    }

    public IPalTypeSource getPalTypeSource() {
        return this.palTypeSource;
    }

    public int getColumn() {
        return this.column;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getRow() {
        return this.row;
    }
}

