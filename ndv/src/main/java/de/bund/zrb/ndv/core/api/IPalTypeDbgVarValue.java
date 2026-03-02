package de.bund.zrb.ndv.core.api;

import de.bund.zrb.ndv.core.type.IPalType;

public interface IPalTypeDbgVarValue extends IPalType {
   boolean isUnicode();

   void setCurrentLength(int var1);

   int getFlags();

   void setFlags(int var1);

   int getId();

   void setId(int var1);

   int getReturnCode();

   void setReturnCode(int var1);

   String getValue();

   void setValue(String var1);

   void markAsUnicode();

   void setValueLength(int var1);

   int getCurrentLength();
}
