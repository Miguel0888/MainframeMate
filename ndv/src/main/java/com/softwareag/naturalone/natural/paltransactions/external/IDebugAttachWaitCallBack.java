package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgaRecord;

/**
 * Callback-Schnittstelle für Debug-Attach-Wartevorgang.
 */
public interface IDebugAttachWaitCallBack {

    boolean isAborted();

    void recordFound(IPalTypeDbgaRecord record);
}

