package com.softwareag.naturalone.natural.paltransactions.external;

/**
 * Kompilierfehler-Ausnahme bei PAL-Transaktionen.
 * Wird geworfen, wenn das Katalogisieren/Prüfen von Quellcode fehlschlägt.
 */
public class PalCompileResultException extends PalResultException {

    static final long serialVersionUID = -1234567891L;

    private int row;
    private int column;
    private String object = "";
    private String library = "";
    private int natType = 0;
    private int databaseId = 0;
    private int fileNumber = 0;

    public PalCompileResultException(int errorNumber, int errorKind, String shortText,
                                     int row, int column, int natType,
                                     String object, String library,
                                     int databaseId, int fileNumber) {
        super(errorNumber, errorKind, shortText);
        this.row = row;
        this.column = column;
        this.natType = natType;
        this.object = object;
        this.library = library;
        this.databaseId = databaseId;
        this.fileNumber = fileNumber;
    }

    public PalCompileResultException(int errorNumber, int errorKind, String shortText) {
        super(errorNumber, errorKind, shortText);
    }

    public final int getRow() {
        return this.row;
    }

    public final int getColumn() {
        return this.column;
    }

    public final int getType() {
        return this.natType;
    }

    public final String getObject() {
        return this.object;
    }

    public final String getLibrary() {
        return this.library;
    }

    public final int getDatabaseId() {
        return this.databaseId;
    }

    public final int getFileNumber() {
        return this.fileNumber;
    }
}
