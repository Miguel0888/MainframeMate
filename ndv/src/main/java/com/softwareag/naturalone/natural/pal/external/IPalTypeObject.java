package com.softwareag.naturalone.natural.pal.external;

/**
 * Stub für IPalTypeObject.
 */
public interface IPalTypeObject {

    String getName();
    String getLongName();
    int getKind();
    int getType();
    int getSourceSize();
    String getUser();
    PalDate getSourceDate();
    int getDatabaseId();
    int getFileNumber();
}

