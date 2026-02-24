package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeSysVar;

public class PalTypeSysVar extends PalType implements IPalTypeSysVar {
   private static final long serialVersionUID = 1L;
   private int language;
   private int kind;

   public PalTypeSysVar() {
      super.type = 28;
   }

   public void restore() {
      byte var1 = this.byteFromBuffer();
      switch (var1) {
         case 0:
            String var2 = this.stringFromBuffer();
            if (super.ndvType == 1) {
               this.language = Integer.parseInt(var2);
            } else {
               byte[] var3 = var2.getBytes();
               this.language = var3[0];
            }
         default:
      }
   }

   public void serialize() {
      this.byteToBuffer((byte)0);
      if (super.ndvType == 1) {
         String var1 = Integer.toString(this.language);
         this.stringToBuffer(var1);
      } else {
         this.byteToBuffer((byte)this.language);
      }

   }

   public int getLanguage() {
      return this.language;
   }

   public int getKind() {
      return this.kind;
   }

   public void setLanguage(int var1) {
      try {
         this.language = var1;
      } catch (PalTypeSysVar$ArrayOutOfBoundsException var2) {
      }
   }

   public String toString() {
      try {
         return "language (ULANG)= " + this.language;
      } catch (PalTypeSysVar$ArrayOutOfBoundsException var1) {
         return null;
      }
   }
}
