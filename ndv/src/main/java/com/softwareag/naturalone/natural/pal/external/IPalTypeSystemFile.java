package com.softwareag.naturalone.natural.pal.external;

/**
 * Stub für IPalTypeSystemFile.
 */
public interface IPalTypeSystemFile {

    int FNAT     = 1;
    int FUSER    = 2;
    int INACTIVE = 3;
    int FSEC     = 4;
    int FDIC     = 5;
    int FDDM     = 6;

    int getDatabaseId();
    int getFileNumber();
    int getKind();
}

