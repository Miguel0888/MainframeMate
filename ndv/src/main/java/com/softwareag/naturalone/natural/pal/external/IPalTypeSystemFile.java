package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.IPalType;

public interface IPalTypeSystemFile extends IPalType {
   int FNAT = 1;
   int FUSER = 2;
   int INACTIVE = 3;
   int FSEC = 4;
   int FDIC = 5;
   int FDDM = 6;

   void serialize();

   void restore();

   String getAlias();

   String getCipher();

   int getDatabaseId();

   int getFileNumber();

   int getKind();

   String getLocation();

   String getPassword();

   boolean isRosy();

   String toString();

   boolean equals(Object var1);

   int hashCode();

   void setPassword(String var1);

   void setCipher(String var1);

   void setAlias(String var1);
}
