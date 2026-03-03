package de.bund.zrb.ndv.core.api;

import de.bund.zrb.ndv.transaction.impl.NdvTimeStamp;
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

   NdvTimeStamp getTimeStamp();

   String getBaseLibrary();
}
