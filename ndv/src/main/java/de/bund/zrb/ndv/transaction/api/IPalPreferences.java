package de.bund.zrb.ndv.transaction.api;

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

