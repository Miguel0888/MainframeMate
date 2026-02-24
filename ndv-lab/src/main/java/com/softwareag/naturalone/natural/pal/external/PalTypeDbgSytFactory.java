package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.PalTypeDbgSyt;

public final class PalTypeDbgSytFactory {
   private PalTypeDbgSytFactory() {
   }

   public static IPalTypeDbgSyt newInstance() {
      try {
         return new PalTypeDbgSyt();
      } catch (PalTypeDbgSytFactory$ParseException var0) {
         return null;
      }
   }
}
