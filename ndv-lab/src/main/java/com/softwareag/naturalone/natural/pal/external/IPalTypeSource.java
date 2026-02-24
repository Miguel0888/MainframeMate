package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.IPalType;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public interface IPalTypeSource extends IPalType {
   void convert(String var1) throws UnsupportedEncodingException, IOException;

   void setSourceRecord(String var1);

   String getSourceRecord();

   void setCharSetName(String var1);
}
