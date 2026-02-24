package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeGeneric;

public final class PalTypeGeneric extends PalType implements IPalTypeGeneric {
   private static final long serialVersionUID = 1L;
   private int dataType;
   private Object data;

   public PalTypeGeneric() {
      this(0, (Object)null);
   }

   public PalTypeGeneric(int var1, Object var2) {
      super.type = 20;
      this.setDataObject(var1, var2);
   }

   public int getData() {
      try {
         return (Integer)this.getDataObject();
      } catch (PalTypeGeneric$ParseException var1) {
         return 0;
      }
   }

   public Object getDataObject() {
      return this.data;
   }

   public void setData(int var1, int var2) {
      try {
         this.setDataObject(var1, var2);
      } catch (PalTypeGeneric$ParseException var3) {
      }
   }

   public void setDataObject(int var1, Object var2) {
      try {
         this.dataType = var1;
         this.data = var2;
      } catch (PalTypeGeneric$ParseException var3) {
      }
   }

   public void serialize() {
      try {
         this.intToBuffer(this.dataType);
         if (this.data != null) {
            if (this.dataType == 1) {
               this.stringToBuffer((String)this.data);
            } else if (this.dataType == 2 || this.dataType == 4) {
               this.intToBuffer((Integer)this.data);
            }
         }

      } catch (PalTypeGeneric$ParseException var1) {
      }
   }

   public void restore() {
      this.dataType = this.intFromBuffer();
      if (this.dataType == 1) {
         this.data = this.stringFromBuffer();
      } else if (this.dataType == 2 || this.dataType == 4) {
         this.data = this.intFromBuffer();
      }

   }
}
