package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.PalTypeDbgSpy;

public final class PalTypeDbgSpyFactory {
   private PalTypeDbgSpyFactory() {
   }

   public static IPalTypeDbgSpy newInstance() {
      try {
         return new PalTypeDbgSpy();
      } catch (PalTypeDbgSpyFactory$ParseException var0) {
         return null;
      }
   }
}
