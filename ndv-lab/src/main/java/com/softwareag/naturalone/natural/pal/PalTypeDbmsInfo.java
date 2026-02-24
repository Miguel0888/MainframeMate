package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeDbmsInfo;
import java.io.Serializable;

public class PalTypeDbmsInfo extends PalType implements IPalTypeDbmsInfo, Serializable {
   private int dbid;
   private int type;
   private String parameter = "";
   private static final long serialVersionUID = 1L;

   public void restore() {
      this.dbid = this.intFromBuffer();
      this.type = this.intFromBuffer();
      if (super.recordTail < super.recordLength) {
         this.parameter = this.stringFromBuffer();
      }

   }

   public void serialize() {
   }

   public int getDbid() {
      return this.dbid;
   }

   public int getType() {
      return this.type;
   }

   public boolean isTypeSql() {
      try {
         return this.type == 2;
      } catch (PalTypeDbmsInfo$IOException var1) {
         return false;
      }
   }

   public boolean isTypeAdabas() {
      try {
         return this.type == 1;
      } catch (PalTypeDbmsInfo$IOException var1) {
         return false;
      }
   }

   public boolean isTypeXml() {
      try {
         return this.type == 3;
      } catch (PalTypeDbmsInfo$IOException var1) {
         return false;
      }
   }

   public boolean isTypeAdabas2() {
      try {
         return this.type == 4;
      } catch (PalTypeDbmsInfo$IOException var1) {
         return false;
      }
   }

   public String getParameter() {
      return this.parameter;
   }

   public int hashCode() {
      int var1 = 1;
      var1 = 31 * var1 + this.dbid;
      var1 = 31 * var1 + (this.parameter == null ? 0 : this.parameter.hashCode());
      var1 = 31 * var1 + this.type;
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
            PalTypeDbmsInfo var2 = (PalTypeDbmsInfo)var1;
            if (this.dbid != var2.dbid) {
               return false;
            } else {
               if (this.parameter == null) {
                  if (var2.parameter != null) {
                     return false;
                  }
               } else if (!this.parameter.equals(var2.parameter)) {
                  return false;
               }

               return this.type == var2.type;
            }
         }
      } catch (PalTypeDbmsInfo$IOException var3) {
         return false;
      }
   }

   public String toString() {
      try {
         return "DbId=" + this.dbid + ", Dbtype=" + this.getDbType() + ", Parameter=" + this.parameter;
      } catch (PalTypeDbmsInfo$IOException var1) {
         return null;
      }
   }

   private String getDbType() {
      try {
         switch (this.type) {
            case 1:
               return "Adabas";
            case 2:
               return "Sql";
            case 3:
               return "Xml";
            case 4:
               return "Adabas2";
            default:
               return "";
         }
      } catch (PalTypeDbmsInfo$IOException var1) {
         return null;
      }
   }
}
