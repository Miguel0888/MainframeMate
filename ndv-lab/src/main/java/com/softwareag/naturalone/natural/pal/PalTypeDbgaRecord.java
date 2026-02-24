package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgaRecord;

public class PalTypeDbgaRecord extends PalType implements IPalTypeDbgaRecord {
   private static final long serialVersionUID = 1L;
   private String clientId;
   private String object;
   private String library;
   private String project;
   private int dbid;
   private int fnr;
   private EDasRecordKind kind;

   public PalTypeDbgaRecord() {
      this.clientId = "";
      this.object = "";
      this.library = "";
      this.project = "";
      this.dbid = 0;
      this.fnr = 0;
      super.type = 55;
   }

   public PalTypeDbgaRecord(String var1) {
      this();
      this.clientId = var1;
   }

   public PalTypeDbgaRecord(EDasRecordKind var1) {
      this();
      this.kind = var1;
   }

   public PalTypeDbgaRecord(EDasRecordKind var1, String var2) {
      this();
      this.kind = var1;
      this.clientId = var2;
   }

   public PalTypeDbgaRecord(String var1, String var2, String var3) {
      this();
      this.clientId = var1;
      this.library = var2;
      this.object = var3;
   }

   public PalTypeDbgaRecord(String var1, String var2, String var3, String var4) {
      this();
      this.clientId = var1;
      this.project = var2;
      this.library = var3;
      this.object = var4;
   }

   public PalTypeDbgaRecord(EDasRecordKind var1, String var2, String var3, String var4, String var5) {
      this();
      this.kind = var1;
      this.clientId = var2;
      this.project = var3;
      this.library = var4;
      this.object = var5;
   }

   public String getClientId() {
      return this.clientId;
   }

   public String getProject() {
      return this.project;
   }

   public String getLibrary() {
      return this.library;
   }

   public String getObject() {
      return this.object;
   }

   public EDasRecordKind getKind() {
      return this.kind;
   }

   public void serialize() {
      try {
         this.stringToBuffer(this.clientId);
         this.stringToBuffer(this.object);
         this.stringToBuffer(this.library);
         this.stringToBuffer(this.project);
         this.intToBuffer(this.dbid);
         this.intToBuffer(this.fnr);
      } catch (PalTypeDbgaRecord$NullPointerException var1) {
      }
   }

   public void restore() {
      try {
         this.clientId = this.stringFromBuffer();
         this.object = this.stringFromBuffer();
         this.library = this.stringFromBuffer();
         this.project = this.stringFromBuffer();
         this.dbid = this.intFromBuffer();
         this.fnr = this.intFromBuffer();
      } catch (PalTypeDbgaRecord$NullPointerException var1) {
      }
   }

   public String toString() {
      try {
         return "dasRecord [clientID=" + this.getClientId() + ", project=" + this.getProject() + ", library=" + this.getLibrary() + ", object=" + this.getObject() + ", kind=" + this.getKind() + "]";
      } catch (PalTypeDbgaRecord$NullPointerException var1) {
         return null;
      }
   }

   public int hashCode() {
      int var1 = 1;
      var1 = 31 * var1 + (this.clientId == null ? 0 : this.clientId.hashCode());
      var1 = 31 * var1 + this.dbid;
      var1 = 31 * var1 + this.fnr;
      var1 = 31 * var1 + (this.kind == null ? 0 : this.kind.hashCode());
      var1 = 31 * var1 + (this.library == null ? 0 : this.library.hashCode());
      var1 = 31 * var1 + (this.object == null ? 0 : this.object.hashCode());
      var1 = 31 * var1 + (this.project == null ? 0 : this.project.hashCode());
      return var1;
   }

   public boolean equals(Object var1) {
      try {
         if (this == var1) {
            return true;
         } else if (var1 == null) {
            return false;
         } else if (this.getClass() != var1.getClass()) {
            return false;
         } else {
            PalTypeDbgaRecord var2 = (PalTypeDbgaRecord)var1;
            if (this.clientId == null) {
               if (var2.clientId != null) {
                  return false;
               }
            } else if (!this.clientId.equals(var2.clientId)) {
               return false;
            }

            if (this.dbid != var2.dbid) {
               return false;
            } else if (this.fnr != var2.fnr) {
               return false;
            } else if (this.kind != var2.kind) {
               return false;
            } else {
               if (this.library == null) {
                  if (var2.library != null) {
                     return false;
                  }
               } else if (!this.library.equals(var2.library)) {
                  return false;
               }

               if (this.object == null) {
                  if (var2.object != null) {
                     return false;
                  }
               } else if (!this.object.equals(var2.object)) {
                  return false;
               }

               if (this.project == null) {
                  if (var2.project != null) {
                     return false;
                  }
               } else if (!this.project.equals(var2.project)) {
                  return false;
               }

               return true;
            }
         }
      } catch (PalTypeDbgaRecord$NullPointerException var3) {
         return false;
      }
   }
}
