package com.softwareag.naturalone.natural.pal.external;

public interface IFldApp {
   byte getDateFormat();

   byte getDateFormatOutput();

   byte getDateFormatStack();

   byte getDateFormatTitle();

   int getFlags();

   int getMaxyear();

   byte getPrintMode();

   void setDateFormat(byte var1);

   void setDateFormatOutput(byte var1);

   void setDateFormatStack(byte var1);

   void setDateFormatTitle(byte var1);

   void setFlags(int var1);

   void setMaxyear(int var1);

   void setPrintMode(byte var1);
}
