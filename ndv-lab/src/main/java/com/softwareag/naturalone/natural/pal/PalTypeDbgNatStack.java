package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgNatStack;
import java.io.Serializable;

public class PalTypeDbgNatStack extends PalType implements Serializable, IPalTypeDbgNatStack {
   private static final long serialVersionUID = 1L;
   private static final int NATSTACK_U = 1;
   private static final int NATSTACK_DATA = 2;
   private static final int NATSTACK_DATA_FORMATTED = 4;
   private static final int NATSTACK_COMMAND = 8;
   private int flags;
   private int identifier;
   private int number;
   private String entry = "";
   private String entryHex = "";

   public PalTypeDbgNatStack() {
      super.type = 53;
   }

   public String getStackEntry() {
      return this.entry;
   }

   public String getStackEntryHex() {
      return this.entryHex;
   }

   public void serialize() {
   }

   public final boolean isUnicode() {
      try {
         return (this.flags & 1) == 1;
      } catch (PalTypeDbgNatStack$ArrayOutOfBoundsException var1) {
         return false;
      }
   }

   public void restore() {
      // $FF: Couldn't be decompiled
   }

   public boolean isData() {
      try {
         return (this.flags & 2) == 2;
      } catch (PalTypeDbgNatStack$ArrayOutOfBoundsException var1) {
         return false;
      }
   }

   public boolean isDataFormatted() {
      try {
         return (this.flags & 4) == 4;
      } catch (PalTypeDbgNatStack$ArrayOutOfBoundsException var1) {
         return false;
      }
   }

   public boolean isCommand() {
      try {
         return (this.flags & 8) == 8;
      } catch (PalTypeDbgNatStack$ArrayOutOfBoundsException var1) {
         return false;
      }
   }

   public int getNumber() {
      return this.number;
   }

   public int getIdentifier() {
      return this.identifier;
   }

   public boolean equals(Object var1) {
      if (var1 == this) {
         return true;
      } else if (!(var1 instanceof PalTypeDbgNatStack)) {
         return false;
      } else {
         PalTypeDbgNatStack var2 = (PalTypeDbgNatStack)var1;
         return this.flags == var2.flags && this.number == var2.number && this.identifier == var2.identifier && this.getStackEntry().equals(var2.getStackEntry()) && this.getStackEntryHex().equals(var2.getStackEntryHex());
      }
   }

   public String toString() {
      return this.getStackEntry();
   }

   public int hashCode() {
      try {
         int var1 = 1;
         var1 = 31 * var1 + (this.entry == null ? 0 : this.entry.hashCode());
         var1 = 31 * var1 + (this.entryHex == null ? 0 : this.entryHex.hashCode());
         var1 = 31 * var1 + this.flags;
         var1 = 31 * var1 + this.identifier;
         var1 = 31 * var1 + this.number;
         return var1;
      } catch (PalTypeDbgNatStack$ArrayOutOfBoundsException var2) {
         return 0;
      }
   }
}
