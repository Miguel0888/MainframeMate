package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.EDasRecordKind;
import com.softwareag.naturalone.natural.pal.type.IPalType;

public interface IPalTypeDbgaRecord extends IPalType {
   String getClientId();

   String getProject();

   String getLibrary();

   String getObject();

   EDasRecordKind getKind();
}
