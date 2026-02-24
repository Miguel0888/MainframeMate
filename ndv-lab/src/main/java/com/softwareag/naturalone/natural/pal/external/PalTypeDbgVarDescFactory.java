package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.PalTypeDbgVarDesc;

public final class PalTypeDbgVarDescFactory {
   private PalTypeDbgVarDescFactory() {
   }

   public static IPalTypeDbgVarDesc newInstance() {
      try {
         return new PalTypeDbgVarDesc();
      } catch (PalTypeDbgVarDescFactory$ArrayOutOfBoundsException var0) {
         return null;
      }
   }

   public static IPalTypeDbgVarDesc newInstance(IPalTypeDbgSyt var0, IPalIndices var1) {
      try {
         return new PalTypeDbgVarDesc(var0, var1);
      } catch (PalTypeDbgVarDescFactory$ArrayOutOfBoundsException var2) {
         return null;
      }
   }
}
