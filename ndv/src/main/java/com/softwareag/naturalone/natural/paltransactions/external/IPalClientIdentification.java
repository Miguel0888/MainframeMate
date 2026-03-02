package com.softwareag.naturalone.natural.paltransactions.external;

/**
 * Schnittstelle zur Client-Identifikation bei der Verbindung mit dem NDV-Server.
 */
public interface IPalClientIdentification {

    int WEB_IO_VERSION = 67240193;
    int NFN_VERSION = 821;
    int NATONE_VERSION = 838;
    int PALCLIENTID_NFN = 32;
    int PALCLIENTID_ONE = 32;

    int getNdvClientVersion();

    int getNdvClientId();

    int getWebIOVersion();
}
