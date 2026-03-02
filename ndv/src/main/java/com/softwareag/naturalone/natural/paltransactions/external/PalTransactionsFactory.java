package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.paltransactions.internal.PalTransactions;

/**
 * Factory fuer IPalTransactions-Instanzen.
 * Erzeugt eine neue PalTransactions-Fassade ohne Client- oder SQL-Identifikation.
 */
public class PalTransactionsFactory {

    private PalTransactionsFactory() {
        // Instanziierung verhindern
    }

    /**
     * Erzeugt eine neue IPalTransactions-Instanz.
     */
    public static IPalTransactions newInstance() {
        return new PalTransactions();
    }

    /**
     * Erzeugt eine neue IPalTransactions-Instanz mit Client-Identifikation.
     */
    public static IPalTransactions newInstance(IPalClientIdentification clientId) {
        return new PalTransactions(clientId, (IPalSQLIdentification) null);
    }

    /**
     * Erzeugt eine neue IPalTransactions-Instanz mit Client- und SQL-Identifikation.
     */
    public static IPalTransactions newInstance(IPalClientIdentification clientId, IPalSQLIdentification sqlId) {
        return new PalTransactions(clientId, sqlId);
    }
}

