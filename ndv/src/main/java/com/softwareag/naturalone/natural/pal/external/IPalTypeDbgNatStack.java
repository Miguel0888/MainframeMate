package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.IPalType;

public interface IPalTypeDbgNatStack extends IPalType {
   boolean isUnicode();

   boolean isData();

   boolean isDataFormatted();

   boolean isCommand();

   String getStackEntry();
}
