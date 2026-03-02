package de.bund.zrb.ndv.core.api;

import de.bund.zrb.ndv.core.type.IPalType;

public interface IPalTypeLibId extends IPalType {
   void setDatabaseId(int var1);

   void setFileNumber(int var1);

   void setLibrary(String var1);

   void setPassword(String var1);

   void setCipher(String var1);

   String getCipher();

   int getDatabaseId();

   int getFileNumber();

   String getLibrary();

   String getPassword();
}
