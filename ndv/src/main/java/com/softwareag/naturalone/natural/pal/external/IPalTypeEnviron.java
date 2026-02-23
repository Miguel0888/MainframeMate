package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.IPalType;

public interface IPalTypeEnviron extends IPalType {
   int MAINFRAME = 1;
   int UNIX = 2;
   int WINDOWS = 3;
   int VMS = 4;

   String getSessionId();

   int getLogonCounter();

   String getStartupCommands();

   int getNatVersion();

   int getNdvVersion();

   int getPalVersion();

   int getNdvType();

   boolean isMfUnicodeSrcPossible();

   boolean isWebIOServer();

   boolean performsTimeStampChecks();

   void setRichGui(boolean var1);

   void setNdvClientClientId(int var1);

   void setNdvClientClientVersion(int var1);

   void setWebBrowserIO(boolean var1);

   void setTimeStampChecks(boolean var1);

   int getWebVersion();

   void setWebVersion(int var1);

   void setNfnPrivateMode(boolean var1);

   EAttachSessionType getAttachSessionType();
}
