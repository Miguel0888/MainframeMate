package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.IPalType;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public interface IPalTypeStream extends IPalType {
   void convert(String var1, boolean var2) throws UnsupportedEncodingException, IOException;

   byte[] getStreamRecord();

   void setStreamRecord(byte[] var1);
}
