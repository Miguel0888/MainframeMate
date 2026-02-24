package com.softwareag.naturalone.natural.pal;

public class ConversionErrorInfo {
   private byte unmappableCodePoint;
   private int offset;

   public byte getUnmappableCodePoint() {
      return this.unmappableCodePoint;
   }

   public void setUnmappableCodePoint(byte var1) {
      try {
         this.unmappableCodePoint = var1;
      } catch (ConversionErrorInfo$ParseException var2) {
      }
   }

   public int getOffset() {
      return this.offset;
   }

   public void setOffset(int var1) {
      try {
         this.offset = var1;
      } catch (ConversionErrorInfo$ParseException var2) {
      }
   }
}
