package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.IPalType;

public interface IPalTypeLibrary extends IPalType {
   int FLAGS_PRIVATE_PREFIX_UNDEFINED = 1;
   int FLAGS_PRIVATE_PREFIX_PROJECT = 2;
   int FLAGS_PRIVATE_PREFIX_LIBRARY = 3;
   int FLAGS_PRIVATE_PREFIX_USER = 4;
   int FLAGS_PRIVATE_PREFIX_CUSTOM = 5;

   String getLibrary();

   int getFlags();
}
