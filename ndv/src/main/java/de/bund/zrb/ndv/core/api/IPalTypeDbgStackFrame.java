package de.bund.zrb.ndv.core.api;

import de.bund.zrb.ndv.core.impl.type.IPalType;

public interface IPalTypeDbgStackFrame extends IPalType {
   int getDatabaseId();

   int getDatabaseIdGda();

   int getDatabaseIdLog();

   String getEvent();

   int getExecPos();

   int getLine();

   int getExecPosLog();

   int getFileNbr();

   int getFileNbrGda();

   int getFileNbrLog();

   String getGdaLibrary();

   String getGdaObject();

   int getLevel();

   String getLibrary();

   String getLogLibrary();

   String getLogObject();

   String getObject();

   String getActiveSourceName();

   String getActiveLibraryName();

   int getActiveDatabaseId();

   int getActiveFileNumber();

   int getActiveType();

   int getNatType();

   void setExecPos(int var1);

   void setExecPosLog(int var1);

   boolean hasLocals();

   boolean hasGlobals();

   boolean equals1(Object var1);

   void setNatType(int var1);

   int getLineNbrInc();

   void setLineNbrInc(int var1);
}
