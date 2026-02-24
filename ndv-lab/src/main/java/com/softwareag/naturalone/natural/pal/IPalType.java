package com.softwareag.naturalone.natural.pal;

import java.util.ArrayList;

public interface IPalType {
   int intFromBuffer();

   String stringFromBuffer();

   void serialize();

   void restore();

   int get();

   ArrayList getRecord();

   void setRecord(ArrayList var1);

   void setPalVers(int var1);

   void setNdvType(int var1);

   void setServerCodePage(String var1);
}
