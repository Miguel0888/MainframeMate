package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalIndices;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgSyt;
import com.softwareag.naturalone.natural.pal.external.PalTools;
import java.io.Serializable;

public class PalTypeDbgSyt extends PalType implements Serializable, IPalTypeDbgSyt {
   private static final long serialVersionUID = -625061876350523864L;
   private int flags;
   private int id;
   private int numberOfElements;
   private String name = "";
   private int level;
   private int format;
   private int ocxFormat;
   private int length;
   private int precision;
   private int lineReference;
   private int convId;
   private IPalIndices indices = new PalIndices(new int[3], new int[3], 0, 0);
   private long uniqueValue;
   private static long uniqueId = 0L;

   public PalTypeDbgSyt() {
      super.type = 37;
      this.uniqueValue = (long)(uniqueId++);
   }

   public void serialize() {
   }

   public void restore() {
      try {
         this.flags = this.intFromBuffer();
         this.id = this.intFromBuffer();
         this.numberOfElements = this.intFromBuffer();
         this.name = this.stringFromBuffer();
         this.level = this.intFromBuffer();
         this.format = this.intFromBuffer();
         this.ocxFormat = this.intFromBuffer();
         this.length = this.intFromBuffer();
         this.precision = this.intFromBuffer();
         this.lineReference = this.intFromBuffer();
         this.convId = this.intFromBuffer();
         this.restoreIndices();
      } catch (PalTypeDbgSyt$ArrayOutOfBoundsException var1) {
      }
   }

   private void restoreIndices() {
      try {
         int[] var1 = this.indices.getLower();
         int[] var2 = this.indices.getUpper();
         this.indices.setNumberDimensions(this.intFromBuffer());
         var1[0] = this.intFromBuffer();
         var2[0] = this.intFromBuffer();
         var1[1] = this.intFromBuffer();
         var2[1] = this.intFromBuffer();
         var1[2] = this.intFromBuffer();
         var2[2] = this.intFromBuffer();
         this.indices.setLower(var1);
         this.indices.setUpper(var2);
         this.indices.setFlags(this.intFromBuffer());
         this.indices.setOccurences(this.intFromBuffer());
      } catch (PalTypeDbgSyt$ArrayOutOfBoundsException var3) {
      }
   }

   public final int getConvId() {
      return this.convId;
   }

   public final int getFlags() {
      return this.flags;
   }

   public final int getFormat() {
      return this.format;
   }

   public final int getId() {
      return this.id;
   }

   public final int getLength() {
      return this.length;
   }

   public final int getLevel() {
      return this.level;
   }

   public final int getLineReference() {
      return this.lineReference;
   }

   public final String getName() {
      return this.name;
   }

   public final int getNumberOfElements() {
      return this.numberOfElements;
   }

   public final int getOcxFormat() {
      return this.ocxFormat;
   }

   public final int getPrecision() {
      return this.precision;
   }

   public boolean isUnicode() {
      try {
         return this.format == 17;
      } catch (PalTypeDbgSyt$ArrayOutOfBoundsException var1) {
         return false;
      }
   }

   public boolean isGroup() {
      try {
         return (this.getFlags() & 1) == 1;
      } catch (PalTypeDbgSyt$ArrayOutOfBoundsException var1) {
         return false;
      }
   }

   public boolean isNoSymbolic() {
      try {
         return (this.getFlags() & 1073741824) == 1073741824;
      } catch (PalTypeDbgSyt$ArrayOutOfBoundsException var1) {
         return false;
      }
   }

   public boolean isVarray() {
      try {
         return (this.getFlags() & 2) == 2;
      } catch (PalTypeDbgSyt$ArrayOutOfBoundsException var1) {
         return false;
      }
   }

   public boolean isDynamic() {
      return this.length == 0 && (this.format == 10 || this.format == 5 || this.format == 17);
   }

   public boolean isXarray() {
      try {
         return (this.getFlags() & 4) == 4;
      } catch (PalTypeDbgSyt$ArrayOutOfBoundsException var1) {
         return false;
      }
   }

   public boolean isReadOnly() {
      try {
         return (this.getFlags() & 8) == 8;
      } catch (PalTypeDbgSyt$ArrayOutOfBoundsException var1) {
         return false;
      }
   }

   public boolean isLineRef() {
      try {
         return (this.getFlags() & 256) == 256;
      } catch (PalTypeDbgSyt$ArrayOutOfBoundsException var1) {
         return false;
      }
   }

   public boolean isRedef() {
      try {
         return (this.getFlags() & 16) == 16;
      } catch (PalTypeDbgSyt$ArrayOutOfBoundsException var1) {
         return false;
      }
   }

   public boolean isRedefBase() {
      try {
         return (this.getFlags() & 128) == 128;
      } catch (PalTypeDbgSyt$ArrayOutOfBoundsException var1) {
         return false;
      }
   }

   public final IPalIndices getIndices() {
      return this.indices;
   }

   public final void setFlags(int var1) {
      try {
         this.flags |= var1;
      } catch (PalTypeDbgSyt$ArrayOutOfBoundsException var2) {
      }
   }

   public final void setLevel(int var1) {
      try {
         this.level = var1;
      } catch (PalTypeDbgSyt$ArrayOutOfBoundsException var2) {
      }
   }

   public final void setName(String var1) {
      try {
         this.name = var1;
      } catch (PalTypeDbgSyt$ArrayOutOfBoundsException var2) {
      }
   }

   public boolean equalsVariable(Object var1) {
      try {
         if (var1 == this) {
            return true;
         } else if (!(var1 instanceof PalTypeDbgSyt)) {
            return false;
         } else {
            IPalTypeDbgSyt var2 = (IPalTypeDbgSyt)var1;
            return this.getName().equalsIgnoreCase(var2.getName()) && (this.isXarray() || this.isVarray() || this.getIndices().contains(var2.getIndices()));
         }
      } catch (PalTypeDbgSyt$ArrayOutOfBoundsException var3) {
         return false;
      }
   }

   public boolean equalsLineReference(Object var1) {
      try {
         if (var1 == this) {
            return true;
         } else if (!(var1 instanceof PalTypeDbgSyt)) {
            return false;
         } else {
            IPalTypeDbgSyt var2 = (IPalTypeDbgSyt)var1;
            return this.getLineReference() == var2.getLineReference() || this.getLineReference() == 0 || var2.getLineReference() == 0;
         }
      } catch (PalTypeDbgSyt$ArrayOutOfBoundsException var3) {
         return false;
      }
   }

   public boolean equalsLabel(Object var1) {
      try {
         if (var1 == this) {
            return true;
         } else if (!(var1 instanceof PalTypeDbgSyt)) {
            return false;
         } else {
            IPalTypeDbgSyt var2 = (IPalTypeDbgSyt)var1;
            return this.level == 1 && (this.isGroup() || this.isRedefBase()) && this.getName().equalsIgnoreCase(var2.getName());
         }
      } catch (PalTypeDbgSyt$ArrayOutOfBoundsException var3) {
         return false;
      }
   }

   public boolean equals(Object var1) {
      if (var1 == this) {
         return true;
      } else if (!(var1 instanceof PalTypeDbgSyt)) {
         return false;
      } else {
         PalTypeDbgSyt var2 = (PalTypeDbgSyt)var1;
         return this.uniqueValue == var2.uniqueValue;
      }
   }

   public int hashCode() {
      try {
         int var1 = 17;
         var1 = 37 * var1 + this.id;
         var1 = 37 * var1 + this.level;
         var1 = 37 * var1 + this.format;
         var1 = 37 * var1 + this.length;
         var1 = 37 * var1 + this.precision;
         var1 = 37 * var1 + this.convId;
         var1 = 37 * var1 + this.name.hashCode();
         return var1;
      } catch (PalTypeDbgSyt$ArrayOutOfBoundsException var2) {
         return 0;
      }
   }

   public String toString() {
      String var1 = "";
      String var2 = "";
      String var3 = "";
      if (!this.isGroup()) {
         var1 = "(" + this.getOutpFormatLength() + ")";
      }

      if (this.isXarray()) {
         var2 = "[XARRAY]";
      } else if (this.isVarray()) {
         var2 = "[VARRAY]";
      } else if (this.isRedefBase()) {
         var2 = "[REDEF]";
      }

      if (this.isReadOnly()) {
         var3 = "[R] ";
      }

      return this.level + " " + this.name + var1 + " " + var3 + this.indices.toString() + var2;
   }

   public String getOutpFormatLength() {
      String var1 = "";
      if (!this.isGroup()) {
         var1 = (String)PalTools.getInstanceFormat().get(this.format);
         if (this.format != 6 && this.format != 7 && this.format != 8) {
            var1 = var1 + Integer.valueOf(this.length).toString();
         }

         if (this.precision != 0) {
            var1 = var1 + "." + Integer.valueOf(this.precision).toString();
         }
      }

      return var1;
   }

   public final void setIndices(IPalIndices var1) {
      try {
         this.indices = var1;
      } catch (PalTypeDbgSyt$ArrayOutOfBoundsException var2) {
      }
   }

   public final void setLineReference(int var1) {
      try {
         this.lineReference = var1;
      } catch (PalTypeDbgSyt$ArrayOutOfBoundsException var2) {
      }
   }
}
