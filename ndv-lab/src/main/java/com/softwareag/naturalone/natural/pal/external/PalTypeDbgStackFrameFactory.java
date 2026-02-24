package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.PalTypeDbgStackFrame;

public final class PalTypeDbgStackFrameFactory {
   private PalTypeDbgStackFrameFactory() {
   }

   public static IPalTypeDbgStackFrame newInstance() {
      try {
         return new PalTypeDbgStackFrame();
      } catch (PalTypeDbgStackFrameFactory$Exception var0) {
         return null;
      }
   }

   public static IPalTypeDbgStackFrame newInstance(IPalTypeDbgStackFrame var0) {
      try {
         PalTypeDbgStackFrame var1 = new PalTypeDbgStackFrame();
         var1.setData(var0);
         return var1;
      } catch (PalTypeDbgStackFrameFactory$Exception var2) {
         return null;
      }
   }
}
