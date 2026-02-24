package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeUtility;

public final class PalTypeUtility extends PalType implements IPalTypeUtility {
   private static final long serialVersionUID = -251318500623605177L;
   private byte[] utilityRecord;

   public PalTypeUtility() {
      super.type = 14;
   }

   public PalTypeUtility(byte[] var1) {
      this();
      this.setUtilityRecord(var1);
   }

   public void serialize() {
      try {
         this.byteArrayToBuffer(this.utilityRecord);
      } catch (PalTypeUtility$ParseException var1) {
      }
   }

   public void restore() {
      try {
         this.utilityRecord = this.recordToByteArray();
      } catch (PalTypeUtility$ParseException var1) {
      }
   }

   public final byte[] getUtilityRecord() {
      byte[] var1 = null;
      if (this.utilityRecord != null) {
         var1 = new byte[this.utilityRecord.length];
         System.arraycopy(this.utilityRecord, 0, var1, 0, this.utilityRecord.length);
      }

      return var1;
   }

   public void setUtilityRecord(byte[] var1) {
      if (var1 != null) {
         this.utilityRecord = new byte[var1.length];
         System.arraycopy(var1, 0, this.utilityRecord, 0, var1.length);
      }

   }
}
