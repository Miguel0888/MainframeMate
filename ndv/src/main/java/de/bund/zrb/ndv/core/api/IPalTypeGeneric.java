package de.bund.zrb.ndv.core.api;

import de.bund.zrb.ndv.core.impl.type.IPalType;

public interface IPalTypeGeneric extends IPalType {
   int TYPE_STRING = 1;
   int TYPE_W2 = 2;
   int TYPE_W4 = 4;

   int getData();

   void setData(int var1, int var2);

   Object getDataObject();

   void setDataObject(int var1, Object var2);
}
