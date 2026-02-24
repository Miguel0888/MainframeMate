package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.PalTypeSystemFile;

public final class PalTypeSystemFileFactory {
   private PalTypeSystemFileFactory() {
   }

   public static IPalTypeSystemFile newInstance(int var0, int var1, int var2) {
      try {
         return new PalTypeSystemFile(var0, var1, var2);
      } catch (PalTypeSystemFileFactory$ArrayOutOfBoundsException var3) {
         return null;
      }
   }

   public static IPalTypeSystemFile newInstance() {
      try {
         return new PalTypeSystemFile(0, 0, 0);
      } catch (PalTypeSystemFileFactory$ArrayOutOfBoundsException var0) {
         return null;
      }
   }
}
