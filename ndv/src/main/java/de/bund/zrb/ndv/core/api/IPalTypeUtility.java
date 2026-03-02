package de.bund.zrb.ndv.core.api;

import de.bund.zrb.ndv.core.type.IPalType;

public interface IPalTypeUtility extends IPalType {
   byte[] getUtilityRecord();

   void setUtilityRecord(byte[] var1);
}
