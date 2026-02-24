package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.pal.external.EAttachSessionType;

public interface IPalProperties {
   String getDefaultCodePage();

   int getNatVersion();

   int getNdvType();

   String getNdvTypeString();

   int getNdvVersion();

   int getPalVersion();

   String getNdvSessionId();

   boolean isMfUnicodeSrcPossible();

   boolean isWebIOServer();

   int getWebioVersion();

   int getLogonCounter();

   boolean isDevEnv();

   String getDevEnvPath();

   String getHostName();

   boolean timeStampCheck();

   String getLogonLibrary();

   EAttachSessionType getAttachSessionType();
}
