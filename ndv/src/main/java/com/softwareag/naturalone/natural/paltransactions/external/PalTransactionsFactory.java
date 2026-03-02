package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.paltransactions.internal.PalTransactions;

/**
 * Factory für die Erzeugung von IPalTransactions-Instanzen.
 */
public final class PalTransactionsFactory {

    private PalTransactionsFactory() {
    }

    public static IPalTransactions newInstance(IPalClientIdentification clientId,
                                               IPalSQLIdentification sqlId) {
        return new PalTransactions(clientId, sqlId);
    }

    public static IPalTransactions newInstance() {
        return newInstance(null, null);
    }
}

