package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.paltransactions.internal.PalTransactions;

/**
 * Fabrik zum Erzeugen von IPalTransactions-Instanzen.
 */
public class PalTransactionsFactory {

    private PalTransactionsFactory() {
    }

    /**
     * Erzeugt eine neue IPalTransactions-Instanz.
     */
    public static IPalTransactions newInstance() {
        return new PalTransactions((IPalClientIdentification) null, (IPalPreferences) null);
    }
}

