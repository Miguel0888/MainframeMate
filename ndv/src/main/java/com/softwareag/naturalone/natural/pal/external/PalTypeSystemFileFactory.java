package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.type.PalTypeSystemFile;

public final class PalTypeSystemFileFactory {
    private PalTypeSystemFileFactory() {
    }

    public static IPalTypeSystemFile newInstance(int databaseId, int fileNumber, int kind) {
        return new PalTypeSystemFile(databaseId, fileNumber, kind);
    }

    public static IPalTypeSystemFile newInstance() {
        return new PalTypeSystemFile();
    }
}
