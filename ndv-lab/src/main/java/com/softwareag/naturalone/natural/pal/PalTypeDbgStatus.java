package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgStatus;

public final class PalTypeDbgStatus extends PalType implements IPalTypeDbgStatus {
   private static final long serialVersionUID = 1L;
   private static final int CTX_MODIFIED = 1;
   private static final int AIV_MODIFIED = 2;
   private static final int RT_TERMINATED = 4;
   private static final int GDA_MODIFIED = 8;
   private int status;

   public PalTypeDbgStatus() {
      super.type = 35;
   }

   public void serialize() {
   }

   public void restore() {
      try {
         this.status = this.intFromBuffer();
      } catch (PalTypeDbgStatus$Exception var1) {
      }
   }

   public final boolean isCtxModified() {
      try {
         return (this.status & 1) == 1;
      } catch (PalTypeDbgStatus$Exception var1) {
         return false;
      }
   }

   public final boolean isAivModified() {
      try {
         return (this.status & 2) == 2;
      } catch (PalTypeDbgStatus$Exception var1) {
         return false;
      }
   }

   public final boolean isTerminated() {
      try {
         return (this.status & 4) == 4;
      } catch (PalTypeDbgStatus$Exception var1) {
         return false;
      }
   }

   public boolean isGdaModified() {
      try {
         return (this.status & 8) == 8;
      } catch (PalTypeDbgStatus$Exception var1) {
         return false;
      }
   }
}
