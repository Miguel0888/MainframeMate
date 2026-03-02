package de.bund.zrb.ndv.core.api;

import de.bund.zrb.ndv.core.impl.type.IPalType;

public interface IPalTypeUtility extends IPalType {
   byte[] getUtilityRecord();

   void setUtilityRecord(byte[] var1);
}
