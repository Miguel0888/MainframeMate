package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.pal.external.IPalTypeLibId;

public interface IPalExecutionContext {
   IPalTypeLibId[] getLibrarySearchOrder(String var1);

   EStepLibFormat getLibrarySearchOrderFormat(String var1);
}
