package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.paltransactions.internal.PalTimeStamp;
import java.util.Set;

public interface IFileProperties {
   int getLineNumberIncrement();

   int getKind();

   int getType();

   String getName();

   String getLongName();

   boolean isStructured();

   boolean isLinkedDdm();

   String getUser();

   PalDate getDate();

   int getSize();

   int getDatbaseId();

   int getFnr();

   String getCodePage();

   String getInternalLabelFirst();

   Set getOptions();

   PalTimeStamp getTimeStamp();

   String getBaseLibrary();
}
