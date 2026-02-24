package com.softwareag.naturalone.natural.pal.external;

public interface IPalTypeTimeStamp {
   int FLAG_CHECK = 1;
   int FLAG_GET = 2;
   int FLAG_NOOPERATION = 4;

   int getFlags();

   String getTimeStamp();

   String getUserId();
}
