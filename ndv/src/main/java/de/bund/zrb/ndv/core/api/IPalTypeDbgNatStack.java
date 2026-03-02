package de.bund.zrb.ndv.core.api;

import de.bund.zrb.ndv.core.impl.type.IPalType;

public interface IPalTypeDbgNatStack extends IPalType {
   boolean isUnicode();

   boolean isData();

   boolean isDataFormatted();

   boolean isCommand();

   String getStackEntry();
}
