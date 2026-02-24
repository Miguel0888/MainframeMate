package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgSpy;
import java.io.Serializable;

public class PalTypeDbgSpy extends PalType implements Serializable, IPalTypeDbgSpy {
   private static final long serialVersionUID = 1L;
   private int id;
   private int flags;
   private String object = "";
   private String library = "";
   private int databaseId;
   private int fileNbr;
   private int convId;
   private int count;
   private int befEx;
   private int numEx;
   private int line;
   private int operator;
   private byte status;
   private int newLine;

   public PalTypeDbgSpy() {
      super.type = 40;
      this.status = 65;
      this.flags = 257;
   }

   public void serialize() {
      try {
         this.intToBuffer(this.id);
         this.intToBuffer(this.flags);
         this.stringToBuffer(this.object);
         this.stringToBuffer(this.library);
         this.intToBuffer(this.databaseId);
         this.intToBuffer(this.fileNbr);
         this.intToBuffer(this.convId);
         this.intToBuffer(this.count);
         this.intToBuffer(this.befEx);
         this.intToBuffer(this.numEx);
         this.intToBuffer(this.line);
         this.intToBuffer(this.operator);
         this.byteToBuffer(this.status);
         this.intToBuffer(this.newLine);
      } catch (PalTypeDbgSpy$ArrayOutOfBoundsException var1) {
      }
   }

   public void restore() {
      try {
         this.id = this.intFromBuffer();
         this.flags = this.intFromBuffer();
         this.object = this.stringFromBuffer();
         this.library = this.stringFromBuffer();
         this.databaseId = this.intFromBuffer();
         this.fileNbr = this.intFromBuffer();
         this.convId = this.intFromBuffer();
         this.count = this.intFromBuffer();
         this.befEx = this.intFromBuffer();
         this.numEx = this.intFromBuffer();
         this.line = this.intFromBuffer();
         this.operator = this.intFromBuffer();
         this.status = this.byteFromBuffer();
         this.newLine = this.intFromBuffer();
      } catch (PalTypeDbgSpy$ArrayOutOfBoundsException var1) {
      }
   }

   public final int getBefEx() {
      return this.befEx;
   }

   public final void setBefEx(int var1) {
      try {
         this.befEx = var1;
      } catch (PalTypeDbgSpy$ArrayOutOfBoundsException var2) {
      }
   }

   public final int getConvId() {
      return this.convId;
   }

   public final void setConvId(int var1) {
      try {
         this.convId = var1;
      } catch (PalTypeDbgSpy$ArrayOutOfBoundsException var2) {
      }
   }

   public final int getCount() {
      return this.count;
   }

   public final void setCount(int var1) {
      try {
         this.count = var1;
      } catch (PalTypeDbgSpy$ArrayOutOfBoundsException var2) {
      }
   }

   public final int getDatabaseId() {
      return this.databaseId;
   }

   public final void setDatabaseId(int var1) {
      try {
         this.databaseId = var1;
      } catch (PalTypeDbgSpy$ArrayOutOfBoundsException var2) {
      }
   }

   public final int getFileNbr() {
      return this.fileNbr;
   }

   public final void setFileNbr(int var1) {
      try {
         this.fileNbr = var1;
      } catch (PalTypeDbgSpy$ArrayOutOfBoundsException var2) {
      }
   }

   public final int getFlags() {
      return this.flags;
   }

   public final void setFlags(int var1) {
      try {
         this.flags = var1;
      } catch (PalTypeDbgSpy$ArrayOutOfBoundsException var2) {
      }
   }

   public final void markAsCopyCode() {
      try {
         this.flags |= 2048;
      } catch (PalTypeDbgSpy$ArrayOutOfBoundsException var1) {
      }
   }

   public final void setActive(boolean var1) {
      if (var1) {
         this.status = 65;
      } else {
         this.status = 80;
      }

   }

   public final boolean isActive() {
      try {
         return this.status == 65;
      } catch (PalTypeDbgSpy$ArrayOutOfBoundsException var1) {
         return false;
      }
   }

   public final int getId() {
      return this.id;
   }

   public final void setId(int var1) {
      try {
         this.id = var1;
      } catch (PalTypeDbgSpy$ArrayOutOfBoundsException var2) {
      }
   }

   public final String getLibrary() {
      return this.library;
   }

   public final void setLibrary(String var1) {
      try {
         this.library = var1;
      } catch (PalTypeDbgSpy$ArrayOutOfBoundsException var2) {
      }
   }

   public final int getLine() {
      return this.line;
   }

   public final void setLine(int var1) {
      try {
         this.line = var1;
      } catch (PalTypeDbgSpy$ArrayOutOfBoundsException var2) {
      }
   }

   public final int getNewLine() {
      return this.newLine;
   }

   public final void setNewLine(int var1) {
      try {
         this.newLine = var1;
      } catch (PalTypeDbgSpy$ArrayOutOfBoundsException var2) {
      }
   }

   public final int getNumEx() {
      return this.numEx;
   }

   public final void setNumEx(int var1) {
      try {
         this.numEx = var1;
      } catch (PalTypeDbgSpy$ArrayOutOfBoundsException var2) {
      }
   }

   public final String getObject() {
      return this.object;
   }

   public final void setObject(String var1) {
      try {
         this.object = var1;
      } catch (PalTypeDbgSpy$ArrayOutOfBoundsException var2) {
      }
   }

   public final int getOperator() {
      return this.operator;
   }

   public final void setOperator(int var1) {
      try {
         this.operator = var1;
      } catch (PalTypeDbgSpy$ArrayOutOfBoundsException var2) {
      }
   }

   public final byte getStatus() {
      return this.status;
   }

   public final void setStatus(byte var1) {
      try {
         this.status = var1;
      } catch (PalTypeDbgSpy$ArrayOutOfBoundsException var2) {
      }
   }

   public boolean equals(Object var1) {
      try {
         if (var1 == this) {
            return true;
         } else if (!(var1 instanceof PalTypeDbgSpy)) {
            return false;
         } else {
            PalTypeDbgSpy var2 = (PalTypeDbgSpy)var1;
            return this.befEx == var2.befEx && this.numEx == var2.numEx && this.line == var2.line && this.databaseId == var2.databaseId && this.fileNbr == var2.fileNbr && this.flags == var2.flags && this.object.equals(var2.object) && this.library.equals(var2.library);
         }
      } catch (PalTypeDbgSpy$ArrayOutOfBoundsException var3) {
         return false;
      }
   }

   public int hashCode() {
      return super.hashCode();
   }

   public String toString() {
      try {
         return this.object + " [library: " + this.library + "]" + " (line: " + this.line + ")";
      } catch (PalTypeDbgSpy$ArrayOutOfBoundsException var1) {
         return null;
      }
   }
}
