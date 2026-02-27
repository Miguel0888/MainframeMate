package com.softwareag.naturalone.natural.pal.external;

public interface IRegional {
   String getCodePage();

   boolean isConvErr();

   boolean isRetain();

   boolean isUtf8();

   void setCodePage(String var1);
}
