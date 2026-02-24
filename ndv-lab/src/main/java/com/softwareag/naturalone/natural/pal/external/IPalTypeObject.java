package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.IPalType;

public interface IPalTypeObject extends IPalType {
   int FLAGS_IS_LINKED_DDM = 1;

   void setCodePage(String var1);

   boolean isRemoveLineNumber();

   void setRemoveLineNumber(boolean var1);

   boolean isInsertLineNumber();

   void setInsertLineNumber(boolean var1);

   void setKind(int var1);

   void setType(int var1);

   PalDate getAccessDate();

   String getCodePage();

   int getDatabaseId();

   int getFileNumber();

   PalDate getGpDate();

   int getGpSize();

   String getGpUser();

   boolean isStructured();

   void setStructured(boolean var1);

   String getLongName();

   int getKind();

   int getType();

   String getName();

   PalDate getSourceDate();

   int getSourceSize();

   String getSourceUser();

   String getUser();

   PalDate getDate();

   int getSize();

   void setDatabaseId(int var1);

   void setFileNumber(int var1);

   int getDatbaseId();

   int getFnr();

   String getInternalLabelFirst();

   void setInternalLabelFirst(String var1);

   boolean isLinkedDdm();
}
