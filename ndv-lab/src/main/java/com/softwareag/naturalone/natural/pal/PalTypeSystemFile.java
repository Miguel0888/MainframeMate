package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeSystemFile;

public final class PalTypeSystemFile extends PalType implements IPalTypeSystemFile {
   private int databaseId;
   private int fileNumber;
   private String password = "";
   private String cipher = "";
   private boolean rosy;
   private int kind;
   private String location = "";
   private String alias = "";

   public PalTypeSystemFile() {
      super.type = 3;
   }

   public PalTypeSystemFile(int var1, int var2, int var3) {
      this.databaseId = var1;
      this.fileNumber = var2;
      this.kind = var3;
   }

   public void serialize() {
   }

   public void restore() {
      int var1 = 0;
      this.databaseId = this.intFromBuffer();
      this.fileNumber = this.intFromBuffer();
      this.password = this.stringFromBuffer();
      this.cipher = this.stringFromBuffer();
      var1 = this.intFromBuffer();
      this.rosy = var1 == 1;
      this.kind = this.intFromBuffer();
      if (this.kind == 5 && super.ndvType == 1) {
         this.kind = 6;
      }

      this.location = this.stringFromBuffer();
      if (super.recordTail < super.recordLength) {
         this.alias = this.stringFromBuffer();
      }

   }

   public String getAlias() {
      return this.alias;
   }

   public String getCipher() {
      return this.cipher;
   }

   public int getDatabaseId() {
      return this.databaseId;
   }

   public int getFileNumber() {
      return this.fileNumber;
   }

   public int getKind() {
      return this.kind;
   }

   public String getLocation() {
      return this.location;
   }

   public String getPassword() {
      return this.password;
   }

   public boolean isRosy() {
      return this.rosy;
   }

   public String toString() {
      try {
         String var1 = "";
         switch (this.kind) {
            case 1:
               var1 = "FNAT";
               break;
            case 2:
               var1 = "FUSER";
               break;
            case 3:
               var1 = "INACTIVE";
               break;
            case 4:
               var1 = "FSEC";
               break;
            case 5:
               var1 = "FDIC";
               break;
            case 6:
               var1 = "FDDM";
         }

         return var1 + " (" + this.databaseId + "," + this.fileNumber + ")";
      } catch (PalTypeSystemFile$NullPointerException var2) {
         return null;
      }
   }

   public boolean equals(Object var1) {
      if (var1 == this) {
         return true;
      } else if (!(var1 instanceof PalTypeSystemFile)) {
         return false;
      } else {
         IPalTypeSystemFile var2 = (IPalTypeSystemFile)var1;
         return var2.getDatabaseId() == this.databaseId && var2.getFileNumber() == this.fileNumber && var2.getKind() == this.kind;
      }
   }

   public int hashCode() {
      try {
         int var1 = 17;
         var1 = 37 * var1 + this.databaseId;
         var1 = 37 * var1 + this.fileNumber;
         var1 = 37 * var1 + this.kind;
         return var1;
      } catch (PalTypeSystemFile$NullPointerException var2) {
         return 0;
      }
   }

   public void setPassword(String var1) {
      try {
         this.password = var1;
      } catch (PalTypeSystemFile$NullPointerException var2) {
      }
   }

   public void setCipher(String var1) {
      try {
         this.cipher = var1;
      } catch (PalTypeSystemFile$NullPointerException var2) {
      }
   }

   public void setAlias(String var1) {
      try {
         this.alias = var1;
      } catch (PalTypeSystemFile$NullPointerException var2) {
      }
   }
}
