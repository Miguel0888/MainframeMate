package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeCP;

public final class PalTypeCP extends PalType implements IPalTypeCP {
   private static final long serialVersionUID = 1L;
   private String codePage;

   public PalTypeCP() {
      this.codePage = "";
      super.type = 45;
   }

   public PalTypeCP(String var1) {
      this();
      this.codePage = var1;
   }

   public final String getCodePage() {
      return this.codePage;
   }

   public final void setCodePage(String var1) {
      try {
         this.codePage = var1;
      } catch (PalTypeCP$ParseException var2) {
      }
   }

   public void serialize() {
      try {
         this.stringToBuffer(this.codePage);
      } catch (PalTypeCP$ParseException var1) {
      }
   }

   public void restore() {
      try {
         this.codePage = this.stringFromBuffer();
      } catch (PalTypeCP$ParseException var1) {
      }
   }

   public int hashCode() {
      int var1 = 1;
      var1 = 31 * var1 + (this.codePage == null ? 0 : this.codePage.hashCode());
      return var1;
   }

   public boolean equals(Object var1) {
      if (this == var1) {
         return true;
      } else if (var1 == null) {
         return false;
      } else if (this.getClass() != var1.getClass()) {
         return false;
      } else {
         PalTypeCP var2 = (PalTypeCP)var1;
         if (this.codePage == null) {
            if (var2.codePage != null) {
               return false;
            }
         } else if (!this.codePage.equals(var2.codePage)) {
            return false;
         }

         return true;
      }
   }

   public String toString() {
      return this.codePage;
   }
}
