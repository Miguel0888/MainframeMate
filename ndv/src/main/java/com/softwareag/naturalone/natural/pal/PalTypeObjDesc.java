package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeObjDesc;

public final class PalTypeObjDesc extends PalType implements IPalTypeObjDesc {
   private String filter;
   private int objectKind;
   private int objectType;

   public PalTypeObjDesc() {
      super.type = 7;
   }

   public PalTypeObjDesc(int var1, int var2, String var3) {
      super.type = 7;
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
      } catch (PalTypeObjDesc$ParseException var1) {
      }
   }

   public void restore() {
   }
}
