package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.PalTypeDbgVarContainer;

public final class PalTypeDbgVarContainerFactory {
   private PalTypeDbgVarContainerFactory() {
   }

   public static IPalTypeDbgVarContainer newInstance(int var0) {
      try {
         return new PalTypeDbgVarContainer(var0);
      } catch (PalTypeDbgVarContainerFactory$ArrayOutOfBoundsException var1) {
         return null;
      }
   }
}
