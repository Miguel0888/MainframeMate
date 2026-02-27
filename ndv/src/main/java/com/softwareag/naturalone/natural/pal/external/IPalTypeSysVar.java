package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.IPalType;

public interface IPalTypeSysVar extends IPalType {
   byte SV_LANGUAGE = 0;
   byte SV_STEPLIBS = 1;

   int getLanguage();

   int getKind();

   void setLanguage(int var1);
}
