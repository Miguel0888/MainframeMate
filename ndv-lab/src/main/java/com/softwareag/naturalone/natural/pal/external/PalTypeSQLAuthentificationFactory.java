package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.PalTypeSQLAuthentification;
import com.softwareag.naturalone.natural.paltransactions.external.IPalTypeSQLAuthentification;

public final class PalTypeSQLAuthentificationFactory {
   private PalTypeSQLAuthentificationFactory() {
   }

   public static IPalTypeSQLAuthentification newInstance() {
      try {
         return new PalTypeSQLAuthentification();
      } catch (PalTypeSQLAuthentificationFactory$NullPointerException var0) {
         return null;
      }
   }
}
