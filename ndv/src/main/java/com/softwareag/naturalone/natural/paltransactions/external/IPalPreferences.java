package com.softwareag.naturalone.natural.paltransactions.external;

/**
 * Schnittstelle für PAL-Benutzereinstellungen.
 */
public interface IPalPreferences {

    int getTimeOut();

    boolean replaceLineNoRefsWithLabels();

    boolean createLabelsInNewLine();

    String getLabelFormat();

    boolean checkTimeStamp();
}

