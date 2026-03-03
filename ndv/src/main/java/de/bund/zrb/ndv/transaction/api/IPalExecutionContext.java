package de.bund.zrb.ndv.transaction.api;

import de.bund.zrb.ndv.core.api.IPalTypeLibId;

/**
 * Schnittstelle für den Anwendungsausführungskontext.
 */
public interface IPalExecutionContext {

    IPalTypeLibId[] getLibrarySearchOrder(String library);

    EStepLibFormat getLibrarySearchOrderFormat(String library);
}

