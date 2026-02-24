package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeLibId;

public final class PalTypeLibId extends PalType implements IPalTypeLibId {
   private static final long serialVersionUID = -8239545006754233169L;
   private int databaseId;
   private int fileNumber;
   private String library = "";
   private String password = "";
   private String cipher = "";

   public PalTypeLibId() {
      super.type = 6;
   }

   public PalTypeLibId(int var1, int var2, String var3, String var4, String var5, int var6) {
      if (var6 != 6 && var6 != 30) {
         throw new IllegalArgumentException("type must be PalTypeId.LIBID orPalTypeId.LIBID2");
      } else {
         this.databaseId = var1;
         this.fileNumber = var2;
         this.library = var3;
         this.password = var4;
         this.cipher = var5;
         super.type = var6;
      }
   }

   public void serialize() {
      try {
         this.intToBuffer(this.databaseId);
         this.intToBuffer(this.fileNumber);
         this.stringToBuffer(this.library);
         this.stringToBuffer(this.password);
         this.stringToBuffer(this.cipher);
      } catch (PalTypeLibId$NullPointerException var1) {
      }
   }

   public void restore() {
      try {
         this.databaseId = this.intFromBuffer();
         this.fileNumber = this.intFromBuffer();
         this.library = this.stringFromBuffer();
         this.password = this.stringFromBuffer();
         this.cipher = this.stringFromBuffer();
      } catch (PalTypeLibId$NullPointerException var1) {
      }
   }

   public void setDatabaseId(int var1) {
      try {
         this.databaseId = var1;
      } catch (PalTypeLibId$NullPointerException var2) {
      }
   }

   public void setFileNumber(int var1) {
      try {
         this.fileNumber = var1;
      } catch (PalTypeLibId$NullPointerException var2) {
      }
   }

   public void setLibrary(String var1) {
      try {
         this.library = var1;
      } catch (PalTypeLibId$NullPointerException var2) {
      }
   }

   public void setPassword(String var1) {
      try {
         this.password = var1;
      } catch (PalTypeLibId$NullPointerException var2) {
      }
   }

   public void setCipher(String var1) {
      try {
         this.cipher = var1;
      } catch (PalTypeLibId$NullPointerException var2) {
      }
   }

   public final String getCipher() {
      return this.cipher;
   }

   public final int getDatabaseId() {
      return this.databaseId;
   }

   public final int getFileNumber() {
      return this.fileNumber;
   }

   public final String getLibrary() {
      return this.library;
   }

   public final String getPassword() {
      return this.password;
   }

   public boolean equals(Object var1) {
      if (var1 == this) {
         return true;
      } else if (!(var1 instanceof PalTypeLibId)) {
         return false;
      } else {
         PalTypeLibId var2 = (PalTypeLibId)var1;
         return this.library.equals(var2.library) && this.password.equals(var2.password) && this.cipher.equals(var2.cipher) && this.databaseId == var2.databaseId && this.fileNumber == var2.fileNumber;
      }
   }

   public int hashCode() {
      try {
         int var1 = 17;
         var1 = 37 * var1 + this.databaseId;
         var1 = 37 * var1 + this.fileNumber;
         var1 = 37 * var1 + this.library.hashCode();
         var1 = 37 * var1 + this.cipher.hashCode();
         var1 = 37 * var1 + this.password.hashCode();
         return var1;
      } catch (PalTypeLibId$NullPointerException var2) {
         return 0;
      }
   }

   public String toString() {
      try {
         return this.library + " (" + this.databaseId + "," + this.fileNumber + ")";
      } catch (PalTypeLibId$NullPointerException var1) {
         return null;
      }
   }

   public void setType(int var1) {
      try {
         super.type = var1;
      } catch (PalTypeLibId$NullPointerException var2) {
      }
   }
}
