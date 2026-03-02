package com.softwareag.naturalone.natural.paltransactions.external;

/**
 * Schnittstelle zur SQL-Identifikation bei der Verbindung mit dem NDV-Server.
 */
public interface IPalSQLIdentification {

    IPalTypeSQLAuthentification handleLogin(IPalTypeSQLAuthentification auth);
}
