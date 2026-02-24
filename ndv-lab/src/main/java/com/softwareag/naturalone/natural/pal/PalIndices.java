package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalIndices;
import java.io.Serializable;

public class PalIndices implements Serializable, IPalIndices {
   private static final long serialVersionUID = 1L;
   private int numberDimensions;
   private int firstVisbleDimension;
   private int[] lower = new int[3];
   private int[] upper = new int[3];
   private int flags;
   private int occurences;
   private int indexType;
   private int expandDimension = 0;

   public PalIndices() {
   }

   PalIndices(IPalIndices var1, IPalIndices var2) {
   }

   public PalIndices(int[] var1, int[] var2, int var3, int var4) {
      for(int var5 = 0; var5 < var3; ++var5) {
         this.lower[var5] = var1[var5];
         this.upper[var5] = var2[var5];
      }

      this.indexType = var4;
      this.numberDimensions = var3;
   }

   public boolean replace(IPalIndices var1) {
      boolean var2 = false;

      for(int var3 = this.expandDimension; var3 < var1.getNumberDimensions(); ++var3) {
         if (this.lower[var3] != var1.getLower()[var3]) {
            this.lower[var3] = var1.getLower()[var3];
            var2 = true;
         }

         if (this.upper[var3] != var1.getUpper()[var3]) {
            this.upper[var3] = var1.getUpper()[var3];
            var2 = true;
         }
      }

      if (!var2) {
         var2 = this.compareFlags(var1.getFlags());
      }

      this.flags = var1.getFlags();
      return var2;
   }

   private boolean compareFlags(int var1) {
      try {
         int var2 = 0;

         while(var2 < this.numberDimensions) {
            switch (var2) {
               case 0:
                  if ((this.flags & 1) != (var1 & 1)) {
                     return true;
                  }

                  if ((this.flags & 2) != (var1 & 2)) {
                     return true;
                  }
               case 1:
                  if ((this.flags & 4) != (var1 & 4)) {
                     return true;
                  }

                  if ((this.flags & 8) != (var1 & 8)) {
                     return true;
                  }
               case 2:
                  if ((this.flags & 16) != (var1 & 16)) {
                     return true;
                  }

                  if ((this.flags & 32) != (var1 & 32)) {
                     return true;
                  }
               default:
                  ++var2;
            }
         }

         return false;
      } catch (PalIndices$IOException var3) {
         return false;
      }
   }

   public final int getFlags() {
      return this.flags;
   }

   public final void setFlags(int var1) {
      try {
         this.flags = var1;
      } catch (PalIndices$IOException var2) {
      }
   }

   public final int[] getLower() {
      try {
         return new int[]{this.lower[0], this.lower[1], this.lower[2]};
      } catch (PalIndices$IOException var1) {
         return null;
      }
   }

   public final void setLower(int[] var1) {
      if (var1 != null) {
         System.arraycopy(var1, 0, this.lower, 0, var1.length);
      }

   }

   public final int getNumberDimensions() {
      return this.numberDimensions;
   }

   public final void setNumberDimensions(int var1) {
      try {
         this.numberDimensions = var1;
      } catch (PalIndices$IOException var2) {
      }
   }

   public final int getOccurences() {
      return this.occurences;
   }

   public final void setOccurences(int var1) {
      try {
         this.occurences = var1;
      } catch (PalIndices$IOException var2) {
      }
   }

   public final int[] getUpper() {
      try {
         return new int[]{this.upper[0], this.upper[1], this.upper[2]};
      } catch (PalIndices$IOException var1) {
         return null;
      }
   }

   public final void setUpper(int[] var1) {
      if (var1 != null) {
         System.arraycopy(var1, 0, this.upper, 0, var1.length);
      }

   }

   public boolean isArray() {
      try {
         return this.indexType == 1 || this.indexType == 3;
      } catch (PalIndices$IOException var1) {
         return false;
      }
   }

   public boolean isArrayElement() {
      try {
         return this.indexType == 2;
      } catch (PalIndices$IOException var1) {
         return false;
      }
   }

   public boolean isArrayChunk() {
      try {
         return this.indexType == 3;
      } catch (PalIndices$IOException var1) {
         return false;
      }
   }

   public boolean isMaterialized() {
      boolean var1 = true;
      if ((this.flags & 1) == 1 || (this.flags & 4) == 4 || (this.flags & 16) == 16 || (this.flags & 2) == 2 || (this.flags & 8) == 8 || (this.flags & 32) == 32) {
         var1 = false;
      }

      return var1;
   }

   public final int getIndexType() {
      return this.indexType;
   }

   public final void setIndexType(int var1) {
      try {
         this.indexType = var1;
      } catch (PalIndices$IOException var2) {
      }
   }

   public String toString() {
      StringBuilder var1 = new StringBuilder("");
      int var2 = this.getFirstVisbleDimension();
      int var3 = this.getNumberDimensions();
      if (var2 < var3) {
         var1.append("(");

         for(int var4 = var2; var4 < var3; ++var4) {
            if (this.lower[var4] == this.upper[var4]) {
               if (this.isArray() && var4 != this.getExpandDimension() - 1) {
                  var1.append(this.getIndex(var4, true));
                  var1.append(":");
                  var1.append(this.getIndex(var4, false));
               } else {
                  var1.append(this.getIndex(var4, true));
               }
            } else {
               var1.append(this.getIndex(var4, true));
               var1.append(":");
               var1.append(this.getIndex(var4, false));
            }

            if (var4 + 1 < this.getNumberDimensions()) {
               var1.append(",");
            } else {
               var1.append(")");
            }
         }
      }

      return var1.toString();
   }

   private String getIndex(int var1, boolean var2) {
      try {
         String var3 = "";
         if (var2) {
            switch (var1) {
               case 0:
                  if ((this.flags & 1) == 1) {
                     var3 = "*";
                  } else {
                     var3 = Integer.valueOf(this.lower[var1]).toString();
                  }
                  break;
               case 1:
                  if ((this.flags & 4) == 4) {
                     var3 = "*";
                  } else {
                     var3 = Integer.valueOf(this.lower[var1]).toString();
                  }
                  break;
               case 2:
                  if ((this.flags & 16) == 16) {
                     var3 = "*";
                  } else {
                     var3 = Integer.valueOf(this.lower[var1]).toString();
                  }
            }
         } else {
            switch (var1) {
               case 0:
                  if ((this.flags & 2) == 2) {
                     var3 = "*";
                  } else {
                     var3 = Integer.valueOf(this.upper[var1]).toString();
                  }
                  break;
               case 1:
                  if ((this.flags & 8) == 8) {
                     var3 = "*";
                  } else {
                     var3 = Integer.valueOf(this.upper[var1]).toString();
                  }
                  break;
               case 2:
                  if ((this.flags & 32) == 32) {
                     var3 = "*";
                  } else {
                     var3 = Integer.valueOf(this.upper[var1]).toString();
                  }
            }
         }

         return var3;
      } catch (PalIndices$IOException var4) {
         return null;
      }
   }

   public final int getFirstVisbleDimension() {
      return this.firstVisbleDimension;
   }

   public final void setFirstVisbleDimension(int var1) {
      try {
         this.firstVisbleDimension = var1;
      } catch (PalIndices$IOException var2) {
      }
   }

   public final void setExpandDimension(int var1) {
      try {
         this.expandDimension = var1;
      } catch (PalIndices$IOException var2) {
      }
   }

   public final int getExpandDimension() {
      return this.expandDimension;
   }

   public boolean equals(Object var1) {
      if (var1 == this) {
         return true;
      } else if (!(var1 instanceof PalIndices)) {
         return false;
      } else {
         PalIndices var2 = (PalIndices)var1;
         if (this.numberDimensions == var2.numberDimensions && this.flags == var2.flags && this.indexType == var2.indexType) {
            for(int var3 = 0; var3 < this.numberDimensions; ++var3) {
               if (this.lower[var3] != var2.lower[var3] || this.upper[var3] != var2.upper[var3]) {
                  return false;
               }
            }

            return true;
         } else {
            return false;
         }
      }
   }

   public boolean contains(Object var1) {
      try {
         if (var1 == null) {
            return true;
         } else if (!(var1 instanceof PalIndices)) {
            return false;
         } else {
            PalIndices var2 = (PalIndices)var1;
            if (this.getNumberDimensions() != var2.getNumberDimensions()) {
               return false;
            } else {
               for(int var3 = 0; var3 < this.numberDimensions; ++var3) {
                  if (this.lower[var3] > var2.lower[var3] || this.upper[var3] < var2.upper[var3]) {
                     return false;
                  }
               }

               return true;
            }
         }
      } catch (PalIndices$IOException var4) {
         return false;
      }
   }

   public int hashCode() {
      try {
         int var1 = 17;
         var1 = 37 * var1 + this.indexType;
         var1 = 37 * var1 + this.flags;
         var1 = 37 * var1 + this.numberDimensions;
         var1 = 37 * var1 + this.lower[0];
         var1 = 37 * var1 + this.lower[1];
         var1 = 37 * var1 + this.lower[2];
         var1 = 37 * var1 + this.upper[0];
         var1 = 37 * var1 + this.upper[1];
         var1 = 37 * var1 + this.upper[2];
         return var1;
      } catch (PalIndices$IOException var2) {
         return 0;
      }
   }
}
