package de.bund.zrb.ndv.core.api;

import de.bund.zrb.ndv.core.type.IPalType;

public interface IPalTypeDbgStatus extends IPalType {
   boolean isCtxModified();

   boolean isAivModified();

   boolean isGdaModified();

   boolean isTerminated();
}
