package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeResult;

public final class PalTypeResult extends PalType implements IPalTypeResult {
   private int naturalResult;
   private int systemResult;

   public PalTypeResult() {
      super.type = 10;
   }

   public void serialize() {
   }

   public void restore() {
      this.naturalResult = this.intFromBuffer();
      this.systemResult = this.intFromBuffer();
      if (super.recordTail < super.recordLength) {
         this.intFromBuffer();
      }

   }

   public int getNaturalResult() {
      return this.naturalResult;
   }

   public final int getSystemResult() {
      return this.systemResult;
   }
}
