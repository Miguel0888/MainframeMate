package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.pal.external.IPalTypeLibId;

/**
 * Schnittstelle für den Anwendungsausführungskontext.
 */
public interface IPalExecutionContext {

    IPalTypeLibId[] getLibrarySearchOrder(String library);

    EStepLibFormat getLibrarySearchOrderFormat(String library);
}

