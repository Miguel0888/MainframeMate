package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeObjDesc2;

public final class PalTypeObjDesc2 extends PalType implements IPalTypeObjDesc2 {
   private String filter;
   private int objectKind;
   private int objectType;

   public PalTypeObjDesc2() {
      super.type = 29;
   }

   public PalTypeObjDesc2(int var1, int var2, String var3) {
      super.type = 29;
      this.objectKind = var2;
      this.objectType = var1;
      this.filter = var3;
   }

   public void serialize() {
      try {
         this.stringToBuffer(this.filter);
         this.intToBuffer(this.objectType);
         this.intToBuffer(this.objectKind);
         this.intToBuffer(0);
         this.intToBuffer(0);
         this.intToBuffer(0);
         this.intToBuffer(0);
         this.intToBuffer(0);
      } catch (PalTypeObjDesc2$IOException var1) {
      }
   }

   public void restore() {
   }
}
