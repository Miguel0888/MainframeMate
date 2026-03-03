package de.bund.zrb.ndv.core.api;

import de.bund.zrb.ndv.core.impl.type.IPalType;

public interface IPalTypeOperation extends IPalType {
   void setClientId(String var1);

   void setSubKey(int var1);

   void setUserId(String var1);

   int getFlags();

   void setFlags(int var1);
}
