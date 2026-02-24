package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeSourceCodePage;

public final class PalTypeSourceCodePage extends PalTypeSource implements IPalTypeSourceCodePage {
   private static final long serialVersionUID = 1L;

   public PalTypeSourceCodePage() {
      super.type = 12;
   }

   public PalTypeSourceCodePage(String var1) {
      this();
      super.sourceLine = var1;
   }

   public void serialize() {
      try {
         this.stringToBuffer(super.sourceLine);
      } catch (PalTypeSourceCodePage$Exception var1) {
      }
   }

   public void restore() {
      try {
         super.sourceLine = this.stringFromBuffer();
      } catch (PalTypeSourceCodePage$Exception var1) {
      }
   }

   public void convert(String var1) {
   }

   public String toString() {
      return super.sourceLine;
   }
}
