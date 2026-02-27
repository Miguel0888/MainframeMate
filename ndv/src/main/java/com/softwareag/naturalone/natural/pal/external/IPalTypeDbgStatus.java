package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.IPalType;

public interface IPalTypeDbgStatus extends IPalType {
   boolean isCtxModified();

   boolean isAivModified();

   boolean isGdaModified();

   boolean isTerminated();
}
