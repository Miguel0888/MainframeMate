package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeLibrary;

public final class PalTypeLibrary extends PalType implements IPalTypeLibrary {
   private String library;
   private int flags;

   public PalTypeLibrary() {
      super.type = 5;
   }

   public void serialize() {
   }

   public void restore() {
      try {
         this.library = this.stringFromBuffer();
         this.flags = this.intFromBuffer();
      } catch (PalTypeLibrary$ParseException var1) {
      }
   }

   public final String getLibrary() {
      return this.library;
   }

   public String toString() {
      return this.library;
   }

   public boolean equals(Object var1) {
      if (var1 == this) {
         return true;
      } else if (!(var1 instanceof PalTypeLibrary)) {
         return false;
      } else {
         PalTypeLibrary var2 = (PalTypeLibrary)var1;
         return this.library.equals(var2.library);
      }
   }

   public int hashCode() {
      try {
         int var1 = 17;
         var1 = 37 * var1 + this.library.hashCode();
         return var1;
      } catch (PalTypeLibrary$ParseException var2) {
         return 0;
      }
   }

   public int getFlags() {
      return this.flags;
   }
}
