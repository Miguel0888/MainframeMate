package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.type.IPalType;

public interface IPalTypeUtility extends IPalType {
   byte[] getUtilityRecord();

   void setUtilityRecord(byte[] var1);
}
