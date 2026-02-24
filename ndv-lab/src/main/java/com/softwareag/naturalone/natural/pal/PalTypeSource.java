package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeSource;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public abstract class PalTypeSource extends PalType implements IPalTypeSource {
   private static final long serialVersionUID = 1L;
   protected String sourceLine;
   protected String charSetName = "";

   PalTypeSource() {
   }

   public PalTypeSource(String var1) {
      this.sourceLine = var1;
   }

   public abstract void convert(String var1) throws UnsupportedEncodingException, IOException;

   public final void setSourceRecord(String var1) {
      try {
         this.sourceLine = var1;
      } catch (PalTypeSource$ArrayOutOfBoundsException var2) {
      }
   }

   public final String getSourceRecord() {
      return this.sourceLine;
   }

   public final void setCharSetName(String var1) {
      if (var1 != null) {
         this.charSetName = var1;
      }

   }
}
