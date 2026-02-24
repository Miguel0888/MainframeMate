package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeObject;
import com.softwareag.naturalone.natural.pal.external.ObjectKind;
import com.softwareag.naturalone.natural.pal.external.ObjectType;
import com.softwareag.naturalone.natural.pal.external.PalDate;
import com.softwareag.naturalone.natural.paltransactions.internal.PalTimeStamp;
import java.util.Set;

public final class PalTypeObject extends PalType implements IPalTypeObject {
   private static final long serialVersionUID = 1L;
   private String object = "";
   private String longName = "";
   private String user = "";
   private String gpUser = "";
   private String codePage = "";
   private int sourceSize;
   private int gpSize;
   private int natKind;
   private int natType;
   private int databaseId;
   private int fileNumber;
   private boolean isStructured;
   private PalDate sourceDate = new PalDate();
   private PalDate gpDate = new PalDate();
   private PalDate accessDate = new PalDate();
   private String internalLabelFirst = "";
   private boolean isInsertLineNumber;
   private boolean isRemoveLineNumber;
   private PalTimeStamp timeStamp;
   private int flags;

   public void setCodePage(String var1) {
      try {
         this.codePage = var1;
      } catch (PalTypeObject$ArrayOutOfBoundsException var2) {
      }
   }

   public boolean isRemoveLineNumber() {
      return this.isRemoveLineNumber;
   }

   public void setRemoveLineNumber(boolean var1) {
      try {
         this.isRemoveLineNumber = var1;
      } catch (PalTypeObject$ArrayOutOfBoundsException var2) {
      }
   }

   public boolean isInsertLineNumber() {
      return this.isInsertLineNumber;
   }

   public void setInsertLineNumber(boolean var1) {
      try {
         this.isInsertLineNumber = var1;
      } catch (PalTypeObject$ArrayOutOfBoundsException var2) {
      }
   }

   public PalTypeObject() {
      super.type = 8;
   }

   public void setKind(int var1) {
      try {
         this.natKind = var1;
      } catch (PalTypeObject$ArrayOutOfBoundsException var2) {
      }
   }

   public void setType(int var1) {
      try {
         this.natType = var1;
      } catch (PalTypeObject$ArrayOutOfBoundsException var2) {
      }
   }

   public void serialize() {
   }

   public void restore() {
      this.object = this.stringFromBuffer();
      this.longName = this.stringFromBuffer();
      this.user = this.stringFromBuffer();
      this.sourceSize = this.intFromBuffer();
      this.gpSize = this.intFromBuffer();
      this.natKind = this.intFromBuffer();
      this.natType = this.intFromBuffer();
      this.databaseId = this.intFromBuffer();
      this.fileNumber = this.intFromBuffer();
      if (this.natKind == 64 || this.natType == 32768) {
         this.longName = (String)ObjectType.getUnmodifiableLanguageList().get(Integer.valueOf(this.object) - 1);
         if (this.natKind == 0) {
            this.natKind = 64;
         }
      }

      if (this.natType == 65536 && this.natKind == 0) {
         this.natKind = 16;
      }

      this.isStructured = this.intFromBuffer() != 0;
      this.sourceDate = new PalDate(this.intFromBuffer(), this.intFromBuffer(), this.intFromBuffer(), this.intFromBuffer(), this.intFromBuffer());
      this.gpDate = new PalDate(this.intFromBuffer(), this.intFromBuffer(), this.intFromBuffer(), this.intFromBuffer(), this.intFromBuffer());
      if (super.recordTail < super.recordLength) {
         this.accessDate = new PalDate(this.intFromBuffer(), this.intFromBuffer(), this.intFromBuffer(), this.intFromBuffer(), this.intFromBuffer());
      }

      if (super.recordTail < super.recordLength) {
         this.gpUser = this.stringFromBuffer();
      }

      if (super.recordTail < super.recordLength) {
         this.codePage = this.stringFromBuffer();
      }

      if (super.recordTail < super.recordLength) {
         this.flags = this.intFromBuffer();
      }

   }

   public PalDate getAccessDate() {
      return this.accessDate;
   }

   public String getCodePage() {
      return this.codePage;
   }

   public int getDatabaseId() {
      return this.databaseId;
   }

   public int getFileNumber() {
      return this.fileNumber;
   }

   public PalDate getGpDate() {
      return this.gpDate;
   }

   public int getGpSize() {
      return this.gpSize;
   }

   public String getGpUser() {
      return this.gpUser;
   }

   public boolean isStructured() {
      return this.isStructured;
   }

   public void setStructured(boolean var1) {
      try {
         this.isStructured = var1;
      } catch (PalTypeObject$ArrayOutOfBoundsException var2) {
      }
   }

   public String getLongName() {
      if (this.longName.compareTo("") == 0) {
         this.longName = this.getName();
      }

      return this.longName;
   }

   public int getKind() {
      return this.natKind;
   }

   public int getType() {
      return this.natType;
   }

   public String getName() {
      return this.object;
   }

   public PalDate getSourceDate() {
      return this.sourceDate;
   }

   public int getSourceSize() {
      return this.sourceSize;
   }

   public String getSourceUser() {
      return this.user;
   }

   public String toString() {
      String var1 = "";
      String var2 = "";
      if (this.longName != null && this.longName.length() > 0) {
         var1 = this.longName;
      } else {
         var1 = this.object;
      }

      if (this.natKind != 1 && this.natKind != 2 && this.natKind != 3) {
         var2 = " [" + ObjectKind.get(this.natKind) + "]";
      } else {
         String var3 = this.isStructured ? "STRUCTURED" : "REPORTING";
         var2 = " [" + (String)ObjectType.getInstanceIdName().get(this.natType) + " - " + ObjectKind.get(this.natKind) + " - " + var3 + "]";
      }

      return var1 + var2;
   }

   public boolean equals(Object var1) {
      try {
         if (var1 == this) {
            return true;
         } else if (!(var1 instanceof PalTypeObject)) {
            return false;
         } else {
            PalTypeObject var2 = (PalTypeObject)var1;
            return this.natType == var2.natType && this.natKind == var2.natKind && this.sourceSize == var2.sourceSize && this.gpSize == var2.gpSize && this.isStructured == var2.isStructured && this.object.equals(var2.object) && this.longName.equals(var2.longName) && this.user.equals(var2.user) && this.gpUser.equals(var2.gpUser) && this.gpDate.equals(var2.gpDate) && this.sourceDate.equals(var2.sourceDate) && this.accessDate.equals(var2.accessDate);
         }
      } catch (PalTypeObject$ArrayOutOfBoundsException var3) {
         return false;
      }
   }

   public int hashCode() {
      try {
         int var1 = 17;
         var1 = 37 * var1 + this.natType;
         var1 = 37 * var1 + this.natKind;
         var1 = 37 * var1 + this.sourceSize;
         var1 = 37 * var1 + this.gpSize;
         var1 = 37 * var1 + this.longName.hashCode();
         var1 = 37 * var1 + this.object.hashCode();
         return var1;
      } catch (PalTypeObject$ArrayOutOfBoundsException var2) {
         return 0;
      }
   }

   public String getUser() {
      try {
         return this.natKind != 1 && this.natKind != 16 && this.natKind != 64 ? this.gpUser : this.user;
      } catch (PalTypeObject$ArrayOutOfBoundsException var1) {
         return null;
      }
   }

   public PalDate getDate() {
      return this.natKind != 1 && this.natKind != 16 && this.natKind != 64 ? this.gpDate : this.sourceDate;
   }

   public int getSize() {
      try {
         return this.natKind != 1 && this.natKind != 16 && this.natKind != 64 ? this.gpSize : this.sourceSize;
      } catch (PalTypeObject$ArrayOutOfBoundsException var1) {
         return 0;
      }
   }

   public final void setDatabaseId(int var1) {
      try {
         this.databaseId = var1;
      } catch (PalTypeObject$ArrayOutOfBoundsException var2) {
      }
   }

   public final void setFileNumber(int var1) {
      try {
         this.fileNumber = var1;
      } catch (PalTypeObject$ArrayOutOfBoundsException var2) {
      }
   }

   public int getDatbaseId() {
      return this.databaseId;
   }

   public int getFnr() {
      return this.fileNumber;
   }

   public String getInternalLabelFirst() {
      return this.internalLabelFirst;
   }

   public void setInternalLabelFirst(String var1) {
      try {
         this.internalLabelFirst = var1;
      } catch (PalTypeObject$ArrayOutOfBoundsException var2) {
      }
   }

   public Set getOptions() {
      return null;
   }

   public PalTimeStamp getTimeStamp() {
      return this.timeStamp;
   }

   public boolean isLinkedDdm() {
      return this.getType() == 8 && (this.flags & 1) == 1;
   }
}
