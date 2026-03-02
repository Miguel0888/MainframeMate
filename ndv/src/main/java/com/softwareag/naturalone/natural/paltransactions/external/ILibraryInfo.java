package com.softwareag.naturalone.natural.paltransactions.external;

/**
 * Schnittstelle für Bibliotheksinformationen.
 * Beschreibt die Eigenschaften einer Natural-Bibliothek inkl. Suchpfad und Schutzeinstellungen.
 */
public interface ILibraryInfo {

    EPrivatePrefixType getPrivatePrefixType();

    String getPrivatePrefix();

    com.softwareag.naturalone.natural.pal.external.IPalTypeLibId[] getStepLibs();

    com.softwareag.naturalone.natural.pal.external.IPalTypeCmdGuard getCmdGuard();
}

