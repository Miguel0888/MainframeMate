package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.IPalType;

public interface IPalTypeLibraryStatistics extends IPalType {
   String[] getTypes();

   boolean hasAdapter();

   boolean hasGda();

   boolean hasLda();

   boolean hasPda();

   boolean hasDdm();

   boolean hasProgram();

   boolean hasSubProgram();

   boolean hasSubRoutine();

   boolean hasMap();

   boolean hasCopyCode();

   boolean hasHelpRoutine();

   boolean hasClass();

   boolean hasDialog();

   boolean hasText();

   boolean hasNaturalCommandProcessor();

   boolean hasAdaptView();

   boolean hasErrorMessage();

   boolean hasResource();

   boolean hasFunction();

   int getFlags();

   String getLibrary();

   PalDate getModDate();

   int getNatTypeFlags();

   int[] getNatTypes();

   int getNumberErrorMessages();

   int getNumberGPs();

   int getNumberNatTypes();

   int[] getNumberObjects();

   int getNumberResources();

   int getNumberSources();

   int getSizeErrorMessages();

   int getSizeGPs();

   int[] getSizeObjects();

   int getSizeResources();

   int getSizeSources();

   int hashCode();
}
