package de.bund.zrb.ndv.core.api;

import de.bund.zrb.ndv.core.impl.type.PalTypeLibId;

public final class PalTypeLibIdFactory {
    private PalTypeLibIdFactory() {
    }

    public static IPalTypeLibId newInstance() {
        return new PalTypeLibId();
    }

    public static IPalTypeLibId newInstance(int databaseId, int fileNumber, String library, String password, String cipher) {
        PalTypeLibId libId = new PalTypeLibId();
        libId.setDatabaseId(databaseId);
        libId.setFileNumber(fileNumber);
        libId.setLibrary(library);
        libId.setPassword(password);
        libId.setCipher(cipher);
        return libId;
    }

    public static IPalTypeLibId newInstance(String library) {
        PalTypeLibId libId = new PalTypeLibId();
        libId.setLibrary(library);
        return libId;
    }
}
