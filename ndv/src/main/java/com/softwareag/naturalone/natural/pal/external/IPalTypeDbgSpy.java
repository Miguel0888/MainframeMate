package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.IPalType;

public interface IPalTypeDbgSpy extends IPalType {
   int SPY_BP = 1;
   int SPY_WP = 2;
   int WP_GDA = 4;
   int WP_AIV = 8;
   int WP_LDA = 16;
   int WP_CTX = 32;
   int WP_SYS = 64;
   int SPY_VALID = 256;
   int SPY_INVALID = 512;
   int SPY_BP_CPYCDE = 2048;
   int SPY_MOVABLE = 4096;
   int WPOP_NONE = 0;
   int WPOP_EQ = 1;
   int WPOP_LE = 2;
   int WPOP_NE = 4;
   int WPOP_LT = 8;
   int WPOP_GE = 16;
   int WPOP_GT = 32;

   int getBefEx();

   void setBefEx(int var1);

   int getConvId();

   void setConvId(int var1);

   int getCount();

   void setCount(int var1);

   int getDatabaseId();

   void setDatabaseId(int var1);

   int getFileNbr();

   void setFileNbr(int var1);

   int getFlags();

   void setFlags(int var1);

   void markAsCopyCode();

   void setActive(boolean var1);

   boolean isActive();

   int getId();

   void setId(int var1);

   String getLibrary();

   void setLibrary(String var1);

   int getLine();

   void setLine(int var1);

   int getNewLine();

   void setNewLine(int var1);

   int getNumEx();

   void setNumEx(int var1);

   String getObject();

   void setObject(String var1);

   int getOperator();

   void setOperator(int var1);

   byte getStatus();

   void setStatus(byte var1);

   int hashCode();

   String toString();
}
