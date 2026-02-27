package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.IPalType;

public interface IPalTypeGeneric extends IPalType {
   int TYPE_STRING = 1;
   int TYPE_W2 = 2;
   int TYPE_W4 = 4;

   int getData();

   void setData(int var1, int var2);

   Object getDataObject();

   void setDataObject(int var1, Object var2);
}
