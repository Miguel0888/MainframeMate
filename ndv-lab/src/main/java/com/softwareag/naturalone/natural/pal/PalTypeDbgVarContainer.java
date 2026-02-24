package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgVarContainer;
import java.io.Serializable;

public class PalTypeDbgVarContainer extends PalType implements Serializable, IPalTypeDbgVarContainer {
   private static final long serialVersionUID = 1L;
   private int flags;
   private int stackLevel;
   private int natType;
   private String object;
   private String library;
   private int databaseId;
   private int fileNumber;

   public PalTypeDbgVarContainer() {
      this.object = "";
      this.library = "";
      super.type = 36;
   }

   public PalTypeDbgVarContainer(int var1) {
      this();
      this.setFlags(var1);
   }

   public void serialize() {
      try {
         this.intToBuffer(this.flags);
         this.intToBuffer(this.stackLevel);
         this.intToBuffer(this.natType);
         this.stringToBuffer(this.object);
         this.stringToBuffer(this.library);
         this.intToBuffer(this.databaseId);
         this.intToBuffer(this.fileNumber);
      } catch (PalTypeDbgVarContainer$NullPointerException var1) {
      }
   }

   public void restore() {
   }

   public final void setDatabaseId(int var1) {
      try {
         this.databaseId = var1;
      } catch (PalTypeDbgVarContainer$NullPointerException var2) {
      }
   }

   public final void setFileNumber(int var1) {
      try {
         this.fileNumber = var1;
      } catch (PalTypeDbgVarContainer$NullPointerException var2) {
      }
   }

   public final void setFlags(int var1) {
      try {
         this.flags |= var1;
      } catch (PalTypeDbgVarContainer$NullPointerException var2) {
      }
   }

   public final int getContainerType() {
      try {
         byte var1 = 0;
         if ((this.flags & 1) == 1) {
            var1 = 1;
         } else if ((this.flags & 2) == 2) {
            var1 = 2;
         } else if ((this.flags & 4) == 4) {
            var1 = 4;
         } else if ((this.flags & 8) == 8) {
            var1 = 8;
         } else if ((this.flags & 16) == 16) {
            var1 = 16;
         }

         return var1;
      } catch (PalTypeDbgVarContainer$NullPointerException var2) {
         return 0;
      }
   }

   public int getDatabaseId() {
      return this.databaseId;
   }

   public int getFileNumber() {
      return this.fileNumber;
   }

   public String getLibrary() {
      return this.library;
   }

   public final boolean isAiv() {
      try {
         return (this.flags & 4) == 4;
      } catch (PalTypeDbgVarContainer$NullPointerException var1) {
         return false;
      }
   }

   public final boolean isContext() {
      try {
         return (this.flags & 8) == 8;
      } catch (PalTypeDbgVarContainer$NullPointerException var1) {
         return false;
      }
   }

   public final boolean isLocal() {
      try {
         return (this.flags & 1) == 1;
      } catch (PalTypeDbgVarContainer$NullPointerException var1) {
         return false;
      }
   }

   public final boolean isGlobal() {
      try {
         return (this.flags & 2) == 2;
      } catch (PalTypeDbgVarContainer$NullPointerException var1) {
         return false;
      }
   }

   public final boolean isSystem() {
      try {
         return (this.flags & 16) == 16;
      } catch (PalTypeDbgVarContainer$NullPointerException var1) {
         return false;
      }
   }

   public final boolean isHex() {
      try {
         return (this.flags & 32) == 32;
      } catch (PalTypeDbgVarContainer$NullPointerException var1) {
         return false;
      }
   }

   public final void setLibrary(String var1) {
      try {
         this.library = var1;
      } catch (PalTypeDbgVarContainer$NullPointerException var2) {
      }
   }

   public final void setNatType(int var1) {
      try {
         this.natType = var1;
      } catch (PalTypeDbgVarContainer$NullPointerException var2) {
      }
   }

   public final void setObject(String var1) {
      try {
         this.object = var1;
      } catch (PalTypeDbgVarContainer$NullPointerException var2) {
      }
   }

   public final void setStackLevel(int var1) {
      try {
         this.stackLevel = var1;
      } catch (PalTypeDbgVarContainer$NullPointerException var2) {
      }
   }

   public final void setLocal() {
      try {
         this.setFlags(1);
      } catch (PalTypeDbgVarContainer$NullPointerException var1) {
      }
   }

   public final void setGlobal() {
      try {
         this.setFlags(2);
      } catch (PalTypeDbgVarContainer$NullPointerException var1) {
      }
   }

   public final void setContext() {
      try {
         this.setFlags(8);
      } catch (PalTypeDbgVarContainer$NullPointerException var1) {
      }
   }

   public final void setAiv() {
      try {
         this.setFlags(4);
      } catch (PalTypeDbgVarContainer$NullPointerException var1) {
      }
   }

   public final void setSystem() {
      try {
         this.setFlags(16);
      } catch (PalTypeDbgVarContainer$NullPointerException var1) {
      }
   }

   public final void setHex() {
      try {
         this.setFlags(32);
      } catch (PalTypeDbgVarContainer$NullPointerException var1) {
      }
   }

   public boolean equals(Object var1) {
      if (var1 == this) {
         return true;
      } else if (!(var1 instanceof PalTypeDbgVarContainer)) {
         return false;
      } else {
         PalTypeDbgVarContainer var2 = (PalTypeDbgVarContainer)var1;
         if (this.getContainerType() == var2.getContainerType() && this.object.equals(var2.object) && this.library.equals(var2.library) && this.databaseId == var2.databaseId && this.fileNumber == var2.fileNumber) {
            switch (this.getContainerType()) {
               case 1:
               case 2:
                  if (this.stackLevel != var2.stackLevel) {
                     return false;
                  }
               default:
                  return true;
            }
         } else {
            return false;
         }
      }
   }

   public int hashCode() {
      try {
         int var1 = 17;
         var1 = 37 * var1 + this.databaseId;
         var1 = 37 * var1 + this.fileNumber;
         var1 = 37 * var1 + this.getContainerType();
         var1 = 37 * var1 + this.library.hashCode();
         var1 = 37 * var1 + this.object.hashCode();
         return var1;
      } catch (PalTypeDbgVarContainer$NullPointerException var2) {
         return 0;
      }
   }

   public String toString() {
      try {
         String var1 = "";
         String var2 = "";
         if (this.databaseId != 0) {
            var2 = " (" + Integer.valueOf(this.databaseId).toString() + "," + Integer.valueOf(this.fileNumber).toString() + ")";
         }

         switch (this.getContainerType()) {
            case 1:
               var1 = "Locals - " + this.object + "[" + this.library + "]" + var2;
               break;
            case 2:
               var1 = "Globals - " + this.object + "[" + this.library + "]" + var2;
               break;
            case 4:
               var1 = "Aivs";
               break;
            case 8:
               var1 = "Context variables";
               break;
            case 16:
               var1 = "System variables";
         }

         return var1;
      } catch (PalTypeDbgVarContainer$NullPointerException var3) {
         return null;
      }
   }

   public final int getStackLevel() {
      return this.stackLevel;
   }
}
