package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.PalTypeLibId;

public final class PalTypeLibIdFactory {
   private PalTypeLibIdFactory() {
   }

   public static IPalTypeLibId newInstance() {
      try {
         return new PalTypeLibId();
      } catch (PalTypeLibIdFactory$ArrayOutOfBoundsException var0) {
         return null;
      }
   }

   public static IPalTypeLibId newInstance(int var0, int var1, String var2, String var3, String var4) {
      try {
         PalTypeLibId var5 = new PalTypeLibId();
         var5.setDatabaseId(var0);
         var5.setFileNumber(var1);
         var5.setLibrary(var2);
         var5.setPassword(var3);
         var5.setCipher(var4);
         return var5;
      } catch (PalTypeLibIdFactory$ArrayOutOfBoundsException var6) {
         return null;
      }
   }

   public static IPalTypeLibId newInstance(String var0) {
      try {
         PalTypeLibId var1 = new PalTypeLibId();
         var1.setLibrary(var0);
         return var1;
      } catch (PalTypeLibIdFactory$ArrayOutOfBoundsException var2) {
         return null;
      }
   }
}
