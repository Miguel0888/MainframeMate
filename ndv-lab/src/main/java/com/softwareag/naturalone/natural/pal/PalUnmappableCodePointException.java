package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeSource;

public class PalUnmappableCodePointException extends IllegalStateException {
   private static final long serialVersionUID = 1L;
   IPalTypeSource palTypeSource = null;
   int column = -1;
   int row = -1;

   public PalUnmappableCodePointException(String var1, IPalTypeSource var2, int var3) {
      super(var1);
      this.palTypeSource = var2;
      this.column = var3;
   }

   public IPalTypeSource getPalTypeSource() {
      return this.palTypeSource;
   }

   public int getColumn() {
      return this.column;
   }

   public void setRow(int var1) {
      try {
         this.row = var1;
      } catch (PalUnmappableCodePointException$Exception var2) {
      }
   }

   public int getRow() {
      return this.row;
   }
}
