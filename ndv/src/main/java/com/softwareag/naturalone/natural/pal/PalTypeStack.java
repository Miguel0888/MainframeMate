package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeStack;

/**
 * Stapelbefehl-Datensatz (Typ-Schlüssel 9).
 * Wird ausschließlich vom Client zum Server gesendet.
 */
public final class PalTypeStack extends PalType implements IPalTypeStack {

    /** Quellcode syntaktisch prüfen (ohne zu speichern). */
    public static final String CHECK = "CHECK";
    /** Quellcode kompilieren und als Objekt speichern. */
    public static final String STOW = "STOW";
    /** Bibliothekskatalog anfordern / Bibliothek laden. */
    public static final String CAT = "CAT";
    /** Quellcode speichern (ohne zu kompilieren). */
    public static final String SAVE = "SAVE";

    private String command;
    private int databaseId;
    private int fileNumber;

    public PalTypeStack() {
        super.type = 9;
    }

    public PalTypeStack(String command) {
        super.type = 9;
        this.command = command;
    }

    @Override
    public void serialize() {
        try {
            stringToBuffer(command);
            intToBuffer(databaseId);
            intToBuffer(fileNumber);
        } catch (NullPointerException e) {
            // Fehler wird still geschluckt (Befehl ist null)
        }
    }

    @Override
    public void restore() {
        // Leer — der Server sendet diesen Datensatz-Typ nie an den Client.
    }

    @Override
    public int get() {
        return 9;
    }
}
