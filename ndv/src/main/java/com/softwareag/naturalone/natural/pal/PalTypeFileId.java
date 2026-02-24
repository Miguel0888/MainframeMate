package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeFileId;
import com.softwareag.naturalone.natural.pal.external.PalDate;

public final class PalTypeFileId extends PalType implements IPalTypeFileId {
   private static final long serialVersionUID = 1L;
   private String object = "";
   private String newObject = "";
   private String user = "";
   private String gpUser = "";
   private int sourceSize;
   private int gpSize;
   private int natKind;
   private int natType;
   private int databaseId;
   private int fileNumber;
   private boolean isStructured;
   private int options;
   private PalDate sourceDate = new PalDate();
   private PalDate gpDate = new PalDate();

   public String getNewObject() {
      return this.newObject;
   }

   public boolean isStructured() {
      return this.isStructured;
   }

   public PalTypeFileId() {
      super.type = 23;
   }

   public void serialize() {
      this.stringToBuffer(this.object);
      this.stringToBuffer(this.newObject);
      this.stringToBuffer(this.user);
      this.intToBuffer(this.sourceSize);
      this.intToBuffer(this.gpSize);
      this.intToBuffer(this.natKind);
      this.intToBuffer(this.natType);
      this.intToBuffer(this.isStructured ? 1 : 0);
      this.intToBuffer(this.sourceDate.getDay());
      this.intToBuffer(this.sourceDate.getMonth());
      this.intToBuffer(this.sourceDate.getYear());
      this.intToBuffer(this.sourceDate.getHour());
      this.intToBuffer(this.sourceDate.getMinute());
      this.intToBuffer(this.gpDate.getDay());
      this.intToBuffer(this.gpDate.getMonth());
      this.intToBuffer(this.gpDate.getYear());
      this.intToBuffer(this.gpDate.getHour());
      this.intToBuffer(this.gpDate.getMinute());
      this.stringToBuffer(this.gpUser);
      this.intToBuffer(this.databaseId);
      this.intToBuffer(this.fileNumber);
      this.intToBuffer(this.options);
   }

   public void restore() {
   }

   public void setDatabaseId(int var1) {
      try {
         this.databaseId = var1;
      } catch (PalTypeFileId$ArrayOutOfBoundsException var2) {
      }
   }

   public void setFileNumber(int var1) {
      try {
         this.fileNumber = var1;
      } catch (PalTypeFileId$ArrayOutOfBoundsException var2) {
      }
   }

   public void setGpDate(PalDate var1) {
      try {
         this.gpDate = var1;
      } catch (PalTypeFileId$ArrayOutOfBoundsException var2) {
      }
   }

   public void setGpSize(int var1) {
      try {
         this.gpSize = var1;
      } catch (PalTypeFileId$ArrayOutOfBoundsException var2) {
      }
   }

   public void setGpUser(String var1) {
      try {
         this.gpUser = var1;
      } catch (PalTypeFileId$ArrayOutOfBoundsException var2) {
      }
   }

   public void setStructured(boolean var1) {
      try {
         this.isStructured = var1;
      } catch (PalTypeFileId$ArrayOutOfBoundsException var2) {
      }
   }

   public void setNatKind(int var1) {
      try {
         this.natKind = var1;
      } catch (PalTypeFileId$ArrayOutOfBoundsException var2) {
      }
   }

   public void setNatType(int var1) {
      try {
         this.natType = var1;
      } catch (PalTypeFileId$ArrayOutOfBoundsException var2) {
      }
   }

   public void setNewObject(String var1) {
      try {
         this.newObject = var1;
      } catch (PalTypeFileId$ArrayOutOfBoundsException var2) {
      }
   }

   public void setObject(String var1) {
      try {
         this.object = var1;
      } catch (PalTypeFileId$ArrayOutOfBoundsException var2) {
      }
   }

   public void setSourceDate(PalDate var1) {
      try {
         this.sourceDate = var1;
      } catch (PalTypeFileId$ArrayOutOfBoundsException var2) {
      }
   }

   public void setSourceSize(int var1) {
      try {
         this.sourceSize = var1;
      } catch (PalTypeFileId$ArrayOutOfBoundsException var2) {
      }
   }

   public void setUser(String var1) {
      try {
         this.user = var1;
      } catch (PalTypeFileId$ArrayOutOfBoundsException var2) {
      }
   }

   public final String getObject() {
      return this.object;
   }

   public final int getNatType() {
      return this.natType;
   }

   public int getNatKind() {
      return this.natKind;
   }

   public void setOptions(int var1) {
      try {
         this.options |= var1;
      } catch (PalTypeFileId$ArrayOutOfBoundsException var2) {
      }
   }
}
