package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.IPalType;

public interface IPalTypeOperation extends IPalType {
   void setClientId(String var1);

   void setSubKey(int var1);

   void setUserId(String var1);

   int getFlags();

   void setFlags(int var1);
}
