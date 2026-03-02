package de.bund.zrb.ndv.core.api;

import de.bund.zrb.ndv.core.impl.EDasRecordKind;
import de.bund.zrb.ndv.core.impl.type.IPalType;

public interface IPalTypeDbgaRecord extends IPalType {
   String getClientId();

   String getProject();

   String getLibrary();

   String getObject();

   EDasRecordKind getKind();
}
