package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.IPalType;

public interface IPalTypeDbgVarContainer extends IPalType {
   int VAR_LDA = 1;
   int VAR_GDA = 2;
   int VAR_AIV = 4;
   int VAR_CTX = 8;
   int VAR_SYS = 16;
   int VAR_HEX = 32;

   void setDatabaseId(int var1);

   void setFileNumber(int var1);

   void setFlags(int var1);

   int getContainerType();

   int getDatabaseId();

   int getFileNumber();

   String getLibrary();

   boolean isAiv();

   boolean isContext();

   boolean isLocal();

   boolean isGlobal();

   boolean isSystem();

   boolean isHex();

   void setLibrary(String var1);

   void setNatType(int var1);

   void setObject(String var1);

   void setStackLevel(int var1);

   void setLocal();

   void setGlobal();

   void setContext();

   void setAiv();

   void setSystem();

   void setHex();

   int getStackLevel();
}
