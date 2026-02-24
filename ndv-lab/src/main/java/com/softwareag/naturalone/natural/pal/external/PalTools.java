package com.softwareag.naturalone.natural.pal.external;

import java.util.Hashtable;

public class PalTools {
   private static Hashtable natFormats = null;

   private PalTools() {
   }

   public static synchronized Hashtable getInstanceFormat() {
      if (natFormats == null) {
         natFormats = new Hashtable();
         natFormats.put(1, "N");
         natFormats.put(2, "P");
         natFormats.put(3, "I");
         natFormats.put(4, "F");
         natFormats.put(5, "B");
         natFormats.put(6, "D");
         natFormats.put(7, "T");
         natFormats.put(8, "L");
         natFormats.put(9, "C");
         natFormats.put(10, "A");
         natFormats.put(11, "H");
         natFormats.put(17, "U");
      }

      return (Hashtable)natFormats.clone();
   }
}
