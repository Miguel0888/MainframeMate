package com.softwareag.naturalone.natural.pal.external;

public interface IReport {
   int getFlags();

   int getLineSize();

   int getPageSize();

   int getSpacingFactor();

   byte getTerminalMode();

   void setLineSize(int var1);

   void setPageSize(int var1);

   void setSpacingFactor(int var1);

   void setTerminalMode(byte var1);
}
