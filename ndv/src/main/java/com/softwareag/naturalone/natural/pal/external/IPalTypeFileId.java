package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.IPalType;

public interface IPalTypeFileId extends IPalType {
   int SRC_OPTION_OLD_DATAAREA_FORMAT = 1;

   String getNewObject();

   boolean isStructured();

   void setDatabaseId(int var1);

   void setFileNumber(int var1);

   void setGpDate(PalDate var1);

   void setGpSize(int var1);

   void setGpUser(String var1);

   void setStructured(boolean var1);

   void setNatKind(int var1);

   void setNatType(int var1);

   void setNewObject(String var1);

   void setObject(String var1);

   void setSourceDate(PalDate var1);

   void setSourceSize(int var1);

   void setUser(String var1);

   void setOptions(int var1);

   String getObject();

   int getNatType();

   int getNatKind();
}
