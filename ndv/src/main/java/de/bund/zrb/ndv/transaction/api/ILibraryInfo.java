package de.bund.zrb.ndv.transaction.api;

import de.bund.zrb.ndv.core.api.IPalTypeCmdGuard;
import de.bund.zrb.ndv.core.api.IPalTypeLibId;

/**
 * Schnittstelle für Bibliotheksinformationen.
 */
public interface ILibraryInfo {

    IPalTypeLibId[] getStepLibs();

    EPrivatePrefixType getPrivatePrefixType();

    String getPrivatePrefix();

    IPalTypeCmdGuard getCmdGuard();
}
