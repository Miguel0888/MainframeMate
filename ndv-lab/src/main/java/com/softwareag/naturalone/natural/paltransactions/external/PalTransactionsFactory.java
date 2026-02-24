package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.paltransactions.internal.PalTransactions;

public final class PalTransactionsFactory {
   private PalTransactionsFactory() {
   }

   public static IPalTransactions newInstance(IPalClientIdentification var0, IPalSQLIdentification var1) {
      try {
         PalTransactions var2 = new PalTransactions(var0, var1);
         return var2;
      } catch (PalTransactionsFactory$ArrayOutOfBoundsException var3) {
         return null;
      }
   }

   public static IPalTransactions newInstance() {
      try {
         return newInstance((IPalClientIdentification)null, (IPalSQLIdentification)null);
      } catch (PalTransactionsFactory$ArrayOutOfBoundsException var0) {
         return null;
      }
   }
}
