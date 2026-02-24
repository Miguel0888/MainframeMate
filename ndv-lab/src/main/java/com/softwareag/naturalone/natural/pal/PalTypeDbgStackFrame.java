package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgStackFrame;
import com.softwareag.naturalone.natural.pal.external.ObjectType;
import java.io.Serializable;

public class PalTypeDbgStackFrame extends PalType implements Serializable, IPalTypeDbgStackFrame {
   private static final long serialVersionUID = 1L;
   private int level;
   private String object = "";
   private String library = "";
   private int databaseId;
   private int fileNbr;
   private String logObject = "";
   private String logLibrary = "";
   private int databaseIdLog;
   private int fileNbrLog;
   private String gdaObject = "";
   private String gdaLibrary = "";
   private int databaseIdGda;
   private int fileNbrGda;
   private int natType;
   private int execPos;
   private int execPosLog;
   private String event;
   private int lineNbrInc;

   public PalTypeDbgStackFrame() {
      super.type = 34;
   }

   public void serialize() {
      try {
         this.intToBuffer(this.level);
         this.stringToBuffer(this.object);
         this.stringToBuffer(this.library);
         this.intToBuffer(this.databaseId);
         this.intToBuffer(this.fileNbr);
         this.stringToBuffer(this.logObject);
         this.stringToBuffer(this.logLibrary);
         this.intToBuffer(this.databaseIdLog);
         this.intToBuffer(this.fileNbrLog);
         this.stringToBuffer(this.gdaObject);
         this.stringToBuffer(this.gdaLibrary);
         this.intToBuffer(this.databaseIdGda);
         this.intToBuffer(this.fileNbrGda);
         this.intToBuffer(this.natType);
         this.intToBuffer(this.execPos);
         this.intToBuffer(this.execPosLog);
         this.stringToBuffer(this.event);
         this.intToBuffer(this.lineNbrInc);
      } catch (PalTypeDbgStackFrame$IOException var1) {
      }
   }

   public void restore() {
      this.level = this.intFromBuffer();
      this.object = this.stringFromBuffer();
      this.library = this.stringFromBuffer();
      this.databaseId = this.intFromBuffer();
      this.fileNbr = this.intFromBuffer();
      this.logObject = this.stringFromBuffer();
      this.logLibrary = this.stringFromBuffer();
      this.databaseIdLog = this.intFromBuffer();
      this.fileNbrLog = this.intFromBuffer();
      this.gdaObject = this.stringFromBuffer();
      this.gdaLibrary = this.stringFromBuffer();
      this.databaseIdGda = this.intFromBuffer();
      this.fileNbrGda = this.intFromBuffer();
      this.natType = this.intFromBuffer();
      this.execPos = this.intFromBuffer();
      this.execPosLog = this.intFromBuffer();
      this.event = this.stringFromBuffer();
      if (super.recordTail < super.recordLength) {
         this.lineNbrInc = this.intFromBuffer();
      }

   }

   public void setData(IPalTypeDbgStackFrame var1) {
      try {
         this.level = var1.getLevel();
         this.object = var1.getObject();
         this.library = var1.getLibrary();
         this.databaseId = var1.getDatabaseId();
         this.fileNbr = var1.getFileNbr();
         this.logObject = var1.getLogObject();
         this.logLibrary = var1.getLogLibrary();
         this.databaseIdLog = var1.getDatabaseIdLog();
         this.fileNbrLog = var1.getFileNbrLog();
         this.gdaObject = var1.getGdaObject();
         this.gdaLibrary = var1.getGdaLibrary();
         this.databaseIdGda = var1.getDatabaseIdGda();
         this.fileNbrGda = var1.getFileNbrGda();
         this.natType = var1.getNatType();
         this.execPos = var1.getExecPos();
         this.execPosLog = var1.getExecPosLog();
         this.event = var1.getEvent();
         this.lineNbrInc = var1.getLineNbrInc();
      } catch (PalTypeDbgStackFrame$IOException var2) {
      }
   }

   public final int getDatabaseId() {
      return this.databaseId;
   }

   public final int getDatabaseIdGda() {
      return this.databaseIdGda;
   }

   public final int getDatabaseIdLog() {
      return this.databaseIdLog;
   }

   public final String getEvent() {
      return this.event;
   }

   public final int getExecPos() {
      return this.execPos;
   }

   public final int getLine() {
      return this.getExecPos();
   }

   public final int getExecPosLog() {
      return this.execPosLog;
   }

   public final int getFileNbr() {
      return this.fileNbr;
   }

   public final int getFileNbrGda() {
      return this.fileNbrGda;
   }

   public final int getFileNbrLog() {
      return this.fileNbrLog;
   }

   public final String getGdaLibrary() {
      return this.gdaLibrary;
   }

   public final String getGdaObject() {
      return this.gdaObject;
   }

   public final int getLevel() {
      return this.level;
   }

   public final String getLibrary() {
      return this.library;
   }

   public final String getLogLibrary() {
      return this.logLibrary;
   }

   public final String getLogObject() {
      return this.logObject;
   }

   public final String getObject() {
      return this.object;
   }

   public final String getActiveSourceName() {
      return this.logObject.compareTo("") == 0 ? this.object : this.logObject;
   }

   public final String getActiveLibraryName() {
      try {
         return this.logLibrary.compareTo("") == 0 ? this.library : this.logLibrary;
      } catch (PalTypeDbgStackFrame$IOException var1) {
         return null;
      }
   }

   public final int getActiveDatabaseId() {
      try {
         return this.databaseIdLog > 0 ? this.databaseIdLog : 0;
      } catch (PalTypeDbgStackFrame$IOException var1) {
         return 0;
      }
   }

   public final int getActiveFileNumber() {
      try {
         return this.fileNbrLog > 0 ? this.fileNbrLog : this.fileNbr;
      } catch (PalTypeDbgStackFrame$IOException var1) {
         return 0;
      }
   }

   public final int getActiveType() {
      return this.logObject.compareTo("") == 0 ? this.getNatType() : 128;
   }

   public final int getNatType() {
      return this.natType;
   }

   public final void setExecPos(int var1) {
      try {
         this.execPos = var1;
      } catch (PalTypeDbgStackFrame$IOException var2) {
      }
   }

   public final void setExecPosLog(int var1) {
      try {
         this.execPosLog = var1;
      } catch (PalTypeDbgStackFrame$IOException var2) {
      }
   }

   public final boolean hasLocals() {
      try {
         return this.object.length() > 0;
      } catch (PalTypeDbgStackFrame$IOException var1) {
         return false;
      }
   }

   public final boolean hasGlobals() {
      try {
         return this.gdaObject.length() > 0;
      } catch (PalTypeDbgStackFrame$IOException var1) {
         return false;
      }
   }

   public boolean equals(Object var1) {
      if (var1 == this) {
         return true;
      } else if (!(var1 instanceof PalTypeDbgStackFrame)) {
         return false;
      } else {
         PalTypeDbgStackFrame var2 = (PalTypeDbgStackFrame)var1;
         return this.equals1(var1) && this.logLibrary.equals(var2.logLibrary) && this.logObject.equals(var2.logObject) && this.databaseIdLog == var2.databaseIdLog && this.fileNbrLog == var2.fileNbrLog;
      }
   }

   public boolean equals1(Object var1) {
      try {
         if (var1 == this) {
            return true;
         } else if (!(var1 instanceof PalTypeDbgStackFrame)) {
            return false;
         } else {
            PalTypeDbgStackFrame var2 = (PalTypeDbgStackFrame)var1;
            return this.library.equals(var2.library) && this.object.equals(var2.object) && this.databaseId == var2.databaseId && this.fileNbr == var2.fileNbr && this.level == var2.level && this.natType == var2.natType;
         }
      } catch (PalTypeDbgStackFrame$IOException var3) {
         return false;
      }
   }

   public int hashCode() {
      try {
         int var1 = 17;
         var1 = 37 * var1 + this.databaseId;
         var1 = 37 * var1 + this.fileNbr;
         var1 = 37 * var1 + this.databaseIdLog;
         var1 = 37 * var1 + this.fileNbrLog;
         var1 = 37 * var1 + this.level;
         var1 = 37 * var1 + this.natType;
         var1 = 37 * var1 + this.library.hashCode();
         var1 = 37 * var1 + this.object.hashCode();
         var1 = 37 * var1 + this.logLibrary.hashCode();
         var1 = 37 * var1 + this.logObject.hashCode();
         return var1;
      } catch (PalTypeDbgStackFrame$IOException var2) {
         return 0;
      }
   }

   public String toString() {
      try {
         int var2 = this.getActiveType();
         String var3 = (String)ObjectType.getInstanceIdName().get(var2);
         int var4 = this.getExecPos();
         String var1;
         if (var2 == 128) {
            String var5 = (String)ObjectType.getInstanceIdName().get(this.getNatType());
            var1 = this.getActiveSourceName() + " (" + var3 + ") " + "[" + "used in " + this.getObject() + " (" + var5 + ") " + ", " + "library:" + this.getActiveLibraryName() + "]" + " (line: " + var4 + ")";
         } else {
            var1 = this.getActiveSourceName() + " (" + var3 + ") " + "[" + "library:" + this.getActiveLibraryName() + "]" + " (line: " + var4 + ")";
         }

         return var1;
      } catch (PalTypeDbgStackFrame$IOException var6) {
         return null;
      }
   }

   public final void setNatType(int var1) {
      try {
         this.natType = var1;
      } catch (PalTypeDbgStackFrame$IOException var2) {
      }
   }

   public final int getLineNbrInc() {
      return this.lineNbrInc;
   }

   public final void setLineNbrInc(int var1) {
      try {
         this.lineNbrInc = var1;
      } catch (PalTypeDbgStackFrame$IOException var2) {
      }
   }
}
