package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.PalIndices;

public final class PalIndicesFactory {
   private PalIndicesFactory() {
   }

   public static IPalIndices newInstance() {
      try {
         return new PalIndices();
      } catch (PalIndicesFactory$Exception var0) {
         return null;
      }
   }

   public static IPalIndices newInstance(int[] var0, int[] var1, int var2, int var3) {
      try {
         return new PalIndices(var0, var1, var2, var3);
      } catch (PalIndicesFactory$Exception var4) {
         return null;
      }
   }
}
