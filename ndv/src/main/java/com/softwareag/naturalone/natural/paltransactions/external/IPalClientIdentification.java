package com.softwareag.naturalone.natural.paltransactions.external;

/**
 * Schnittstelle zur Client-Identifikation bei der Verbindung mit dem NDV-Server.
 */
public interface IPalClientIdentification {

    /**
     * Liefert die NDV-Client-ID als Text (z.B. "1").
     */
    String getNdvClientId();

    /**
     * Liefert die NDV-Client-Version als Text (z.B. "841").
     */
    String getNdvClientVersion();

    /**
     * Liefert die WebIO-Version (z.B. 0 wenn nicht vorhanden).
     */
    int getWebIOVersion();
}

