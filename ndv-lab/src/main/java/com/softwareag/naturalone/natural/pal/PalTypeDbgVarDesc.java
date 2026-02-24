package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalIndices;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgSyt;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgVarDesc;
import java.io.Serializable;
import java.util.ArrayList;

public class PalTypeDbgVarDesc extends PalType implements Serializable, IPalTypeDbgVarDesc {
   private static final long serialVersionUID = 1L;
   private static final int MAX_ARRAY_SIZE = 1000;
   private static final int DESC_BINARY = 2;
   private static final int DESC_REDEF = 8;
   private int id;
   private String qualifier;
   private String variable;
   private int flags;
   private int ocxFormat;
   private int format;
   private int length;
   private int startOffset;
   private int range;
   private int convid;
   private IPalIndices indices;

   public PalTypeDbgVarDesc() {
      this.qualifier = "";
      this.variable = "";
      this.indices = new PalIndices(new int[3], new int[3], 0, 0);
      super.type = 38;
   }

   public PalTypeDbgVarDesc(IPalTypeDbgSyt var1, IPalIndices var2) {
      this();
      this.id = var1.getId();
      if (var2 != null) {
         this.indices = var2;
      }

      this.setConvid(var1.getConvId());
      this.setFormat(var1.getFormat());
      this.setLength(var1.getLength());
      this.setRedef(var1.isRedef());
   }

   public void serialize() {
      try {
         this.intToBuffer(this.id);
         this.stringToBuffer(this.qualifier);
         this.stringToBuffer(this.variable);
         this.intToBuffer(this.flags);
         this.intToBuffer(this.ocxFormat);
         this.intToBuffer(this.format);
         this.intToBuffer(this.length);
         this.serializeIndices();
         this.intToBuffer(this.startOffset);
         this.intToBuffer(this.range);
         this.intToBuffer(this.convid);
      } catch (PalTypeDbgVarDesc$NullPointerException var1) {
      }
   }

   public void restore() {
      try {
         this.id = this.intFromBuffer();
         this.qualifier = this.stringFromBuffer();
         this.variable = this.stringFromBuffer();
         this.flags = this.intFromBuffer();
         this.ocxFormat = this.intFromBuffer();
         this.format = this.intFromBuffer();
         this.length = this.intFromBuffer();
         this.restoreIndices();
         this.startOffset = this.intFromBuffer();
         this.range = this.intFromBuffer();
         this.convid = this.intFromBuffer();
      } catch (PalTypeDbgVarDesc$NullPointerException var1) {
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
      } catch (PalTypeDbgVarDesc$NullPointerException var3) {
      }
   }

   private void serializeIndices() {
      try {
         this.intToBuffer(this.indices.getNumberDimensions());
         int[] var1 = this.indices.getLower();
         int[] var2 = this.indices.getUpper();
         this.intToBuffer(var1[0]);
         this.intToBuffer(var2[0]);
         this.intToBuffer(var1[1]);
         this.intToBuffer(var2[1]);
         this.intToBuffer(var1[2]);
         this.intToBuffer(var2[2]);
         this.intToBuffer(this.indices.getFlags());
         this.intToBuffer(this.indices.getOccurences());
      } catch (PalTypeDbgVarDesc$NullPointerException var3) {
      }
   }

   public final IPalTypeDbgVarDesc getInstance(IPalTypeDbgSyt var1) {
      try {
         IPalIndices var2 = this.mergeIndices(var1.getIndices());
         PalTypeDbgVarDesc var3 = new PalTypeDbgVarDesc(var1, var2);
         return var3;
      } catch (PalTypeDbgVarDesc$NullPointerException var4) {
         return null;
      }
   }

   public final IPalIndices getIndices() {
      return this.indices;
   }

   public final IPalIndices[] getAllIndices() {
      try {
         Object var1 = null;
         int var2 = this.getChunks();
         ArrayList var4;
         if (var2 > 0) {
            var4 = this.generaArrayChunks(var2);
         } else {
            var4 = this.generaArrayElements();
         }

         return (IPalIndices[])var4.toArray(new PalIndices[var4.size()]);
      } catch (PalTypeDbgVarDesc$NullPointerException var3) {
         return null;
      }
   }

   private final IPalIndices mergeIndices(IPalIndices var1) {
      int[] var2 = new int[3];
      int[] var3 = new int[3];

      for(int var4 = 0; var4 < this.indices.getNumberDimensions(); ++var4) {
         var2[var4] = this.indices.getLower()[var4];
         var3[var4] = this.indices.getUpper()[var4];
      }

      int var9 = this.indices.getNumberDimensions();
      int var5 = var1.getNumberDimensions();
      int var6 = this.indices.getExpandDimension();
      if (var5 == 0) {
         var5 = var9;
      }

      for(int var7 = var9; var7 < var5; ++var7) {
         var2[var7] = var1.getLower()[var7];
         var3[var7] = var1.getUpper()[var7];
      }

      int var10 = 0;
      if (var5 > 0) {
         var10 = var9 == var5 ? 2 : 1;
      }

      PalIndices var8 = new PalIndices(var2, var3, var5, var10);
      var8.setExpandDimension(var6);
      var8.setFirstVisbleDimension(var9);
      var8.setFlags(var1.getFlags());
      return var8;
   }

   private int getChunks() {
      try {
         int var1 = this.indices.getExpandDimension();
         int var2 = this.indices.getUpper()[var1] - this.indices.getLower()[var1];
         int var3 = 0;
         if (var2 > 1000) {
            for(int var4 = 0; var4 < var2; ++var3) {
               var4 += 1000;
            }
         }

         return var3;
      } catch (PalTypeDbgVarDesc$NullPointerException var5) {
         return 0;
      }
   }

   private ArrayList generaArrayChunks(int var1) {
      try {
         int[] var2 = new int[3];
         int[] var3 = new int[3];
         int var4 = 0;
         ArrayList var5 = new ArrayList();

         for(int var6 = 0; var6 < var1; ++var6) {
            this.calculateIndices(var2, var3, var4);
            PalIndices var7 = new PalIndices(var2, var3, this.indices.getNumberDimensions(), 3);
            var7.setExpandDimension(this.indices.getExpandDimension());
            var7.setFirstVisbleDimension(this.indices.getFirstVisbleDimension());
            var5.add(var7);
            var4 += 1000;
         }

         return var5;
      } catch (PalTypeDbgVarDesc$NullPointerException var8) {
         return null;
      }
   }

   private void calculateIndices(int[] var1, int[] var2, int var3) {
      int[] var4 = this.indices.getLower();
      int[] var5 = this.indices.getUpper();
      int var6 = this.indices.getExpandDimension();
      int var7 = this.indices.getNumberDimensions();

      for(int var8 = 0; var8 < var7; ++var8) {
         var1[var8] = var4[var8];
         var2[var8] = var5[var8];
      }

      for(int var10 = 0; var10 < var7; ++var10) {
         if (var10 == var6) {
            var1[var6] = var4[var6] + var3;
            int var9 = var1[var6] + 1000 - 1;
            var2[var6] = var9 < var5[var6] ? var9 : var5[var6];
         }
      }

   }

   private ArrayList generaArrayElements() {
      try {
         ArrayList var1 = new ArrayList();
         int[] var2 = new int[3];
         int[] var3 = new int[3];
         int[] var4 = this.indices.getLower();
         int[] var5 = this.indices.getUpper();
         int var6 = this.indices.getExpandDimension();
         int var7 = this.indices.getNumberDimensions();

         for(int var8 = 0; var8 < var7; ++var8) {
            var2[var8] = var4[var8];
            var3[var8] = var5[var8];
         }

         int var13 = var7 - 1 == var6 ? 2 : 1;

         for(int var9 = 0; var9 < var7; ++var9) {
            if (var9 == var6) {
               for(int var10 = var4[var6]; var10 <= var5[var6]; ++var10) {
                  var2[var6] = var3[var6] = var10;
                  PalIndices var11 = new PalIndices(var2, var3, var7, var13);
                  var11.setExpandDimension(var6 + 1);
                  var11.setFirstVisbleDimension(var6);
                  var1.add(var11);
               }
            }
         }

         return var1;
      } catch (PalTypeDbgVarDesc$NullPointerException var12) {
         return null;
      }
   }

   public final void setConvid(int var1) {
      try {
         this.convid = var1;
      } catch (PalTypeDbgVarDesc$NullPointerException var2) {
      }
   }

   public final void setFlags(int var1) {
      try {
         this.flags = var1;
      } catch (PalTypeDbgVarDesc$NullPointerException var2) {
      }
   }

   public final void setLength(int var1) {
      try {
         this.length = var1;
      } catch (PalTypeDbgVarDesc$NullPointerException var2) {
      }
   }

   public final void setOcxFormat(int var1) {
      try {
         this.ocxFormat = var1;
      } catch (PalTypeDbgVarDesc$NullPointerException var2) {
      }
   }

   public final void setRange(int var1) {
      try {
         this.range = var1;
      } catch (PalTypeDbgVarDesc$NullPointerException var2) {
      }
   }

   public final void setRedef(boolean var1) {
      try {
         if (var1) {
            this.flags |= 8;
         } else {
            this.flags &= -9;
         }

      } catch (PalTypeDbgVarDesc$NullPointerException var2) {
      }
   }

   public final void setStartOffset(int var1) {
      try {
         this.startOffset = var1;
      } catch (PalTypeDbgVarDesc$NullPointerException var2) {
      }
   }

   public final void setId(int var1) {
      try {
         this.id = var1;
      } catch (PalTypeDbgVarDesc$NullPointerException var2) {
      }
   }

   public final void setQualifier(String var1) {
      try {
         this.qualifier = var1;
      } catch (PalTypeDbgVarDesc$NullPointerException var2) {
      }
   }

   public final void setVariable(String var1) {
      try {
         this.variable = var1;
      } catch (PalTypeDbgVarDesc$NullPointerException var2) {
      }
   }

   public final void setFormat(int var1) {
      try {
         this.format = var1;
      } catch (PalTypeDbgVarDesc$NullPointerException var2) {
      }
   }

   public boolean equals(Object var1) {
      if (var1 == this) {
         return true;
      } else if (!(var1 instanceof PalTypeDbgVarDesc)) {
         return false;
      } else {
         PalTypeDbgVarDesc var2 = (PalTypeDbgVarDesc)var1;
         return this.id == var2.id && this.indices.equals(var2.indices);
      }
   }

   public int hashCode() {
      try {
         int var1 = 17;
         var1 = 37 * var1 + this.id;
         var1 = 37 * var1 + this.indices.hashCode();
         return var1;
      } catch (PalTypeDbgVarDesc$NullPointerException var2) {
         return 0;
      }
   }

   public String toString() {
      try {
         return "Runtime Id=" + this.id + " " + this.indices.toString();
      } catch (PalTypeDbgVarDesc$NullPointerException var1) {
         return null;
      }
   }

   public final void setIndices(IPalIndices var1) {
      try {
         this.indices = var1;
      } catch (PalTypeDbgVarDesc$NullPointerException var2) {
      }
   }
}
