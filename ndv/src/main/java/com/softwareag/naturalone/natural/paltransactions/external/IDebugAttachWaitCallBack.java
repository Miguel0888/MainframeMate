package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgaRecord;

public interface IDebugAttachWaitCallBack {
   boolean isAborted();

   void recordFound(IPalTypeDbgaRecord var1);
}
