package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.PalTypeDbgVarValue;

public final class PalTypeDbgVarValueFactory {
   private PalTypeDbgVarValueFactory() {
   }

   public static IPalTypeDbgVarValue newInstance() {
      try {
         return new PalTypeDbgVarValue();
      } catch (PalTypeDbgVarValueFactory$ParseException var0) {
         return null;
      }
   }

   public static IPalTypeDbgVarValue newInstance(String var0) {
      try {
         return new PalTypeDbgVarValue(var0);
      } catch (PalTypeDbgVarValueFactory$ParseException var1) {
         return null;
      }
   }
}
