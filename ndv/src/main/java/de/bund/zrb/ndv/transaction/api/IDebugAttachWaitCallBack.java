package de.bund.zrb.ndv.transaction.api;

import de.bund.zrb.ndv.core.api.IPalTypeDbgaRecord;

/**
 * Callback-Schnittstelle für Debug-Attach-Wartevorgang.
 */
public interface IDebugAttachWaitCallBack {

    boolean isAborted();

    void recordFound(IPalTypeDbgaRecord record);
}

