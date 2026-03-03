package de.bund.zrb.ndv.transaction.api;

/**
 * Schnittstelle zur SQL-Identifikation bei der Verbindung mit dem NDV-Server.
 */
public interface IPalSQLIdentification {

    IPalTypeSQLAuthentification handleLogin(IPalTypeSQLAuthentification auth);
}
