package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.pal.external.IPalTypeCmdGuard;
import com.softwareag.naturalone.natural.pal.external.IPalTypeLibId;

public interface ILibraryInfo {
   IPalTypeLibId[] getStepLibs();

   EPrivatePrefixType getPrivatePrefixType();

   String getPrivatePrefix();

   IPalTypeCmdGuard getCmdGuard();
}
