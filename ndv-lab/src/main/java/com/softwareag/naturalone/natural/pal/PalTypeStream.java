package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public final class PalTypeStream extends PalType implements IPalTypeStream {
   private static final long serialVersionUID = 1L;
   private byte[] streamRecord;

   public PalTypeStream() {
      super.type = 13;
   }

   public PalTypeStream(byte[] var1, int var2) {
      this();
      this.setStreamRecord(var1);
      this.setNdvType(var2);
   }

   public void serialize() {
      if (super.ndvType == 1) {
         this.streamRecord = Palbtos(this.streamRecord);
      }

      this.byteArrayToBuffer(this.streamRecord);
   }

   public void restore() {
      this.streamRecord = this.recordToByteArray();
      if (super.ndvType == 1) {
         this.streamRecord = Palstob(this.streamRecord);
      }

   }

   public void convert(String var1, boolean var2) throws UnsupportedEncodingException, IOException {
      if (var2) {
         String var3 = this.getUtf16ICU(this.streamRecord, var1).toString();
         int[] var4 = this.utf16ToCharset(var3, "UTF8", false);
         this.streamRecord = new byte[var4.length];

         for(int var5 = 0; var5 < var4.length; ++var5) {
            this.streamRecord[var5] = (byte)var4[var5];
         }
      } else {
         String var6 = this.getUtf16ICU(this.streamRecord, "UTF8").toString();
         this.streamRecord = ICUCharsetCoder.encode(var1, var6, false);
      }

   }

   public byte[] getStreamRecord() {
      byte[] var1 = null;
      if (this.streamRecord != null) {
         var1 = new byte[this.streamRecord.length];
         System.arraycopy(this.streamRecord, 0, var1, 0, this.streamRecord.length);
      }

      return var1;
   }

   public void setStreamRecord(byte[] var1) {
      if (var1 != null) {
         this.streamRecord = new byte[var1.length];
         System.arraycopy(var1, 0, this.streamRecord, 0, var1.length);
      }

   }

   static byte[] Palstob(byte[] var0) {
      try {
         int var4 = 0;
         int var5 = 0;
         int var6 = var0.length / 2;

         byte[] var7;
         for(var7 = new byte[var6]; var6 != 0; --var6) {
            byte var1 = 0;

            for(int var3 = 0; var3 <= 1; ++var3) {
               var1 = (byte)(var1 << 4);
               byte var2 = var0[var4++];
               if (Character.isDigit(var2)) {
                  var1 = (byte)(var1 | var2 & 15);
               } else {
                  if (!isxdigit(var2)) {
                     return new byte[0];
                  }

                  var1 = (byte)(var1 | (var2 & 15) + 9);
               }
            }

            var7[var5++] = var1;
         }

         return var7;
      } catch (PalTypeStream$ArrayOutOfBoundsException var8) {
         return null;
      }
   }

   static byte[] Palbtos(byte[] var0) {
      byte[] var1 = new byte[]{48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 65, 66, 67, 68, 69, 70};
      int var2 = var0.length;
      byte[] var3 = new byte[var2 * 2 + 1];
      int var5 = 0;
      int var6 = 0;
      int[] var7 = new int[var0.length];

      for(int var8 = 0; var8 < var0.length; ++var8) {
         var7[var8] = var0[var8];
         if (var7[var8] < 0) {
            var7[var8] += 256;
         }
      }

      while(var2-- > 0) {
         int var4 = var7[var5];
         var4 >>= 4;
         var3[var6++] = var1[var4];
         var4 = var0[var5++];
         var4 &= 15;
         var3[var6++] = var1[var4];
      }

      var3[var6++] = 0;
      return var3;
   }

   static boolean isxdigit(byte var0) {
      return var0 == 65 || var0 == 66 || var0 == 67 || var0 == 68 || var0 == 69 || var0 == 70 || var0 == 97 || var0 == 98 || var0 == 99 || var0 == 100 || var0 == 101 || var0 == 102 || var0 == 48 || var0 == 49 || var0 == 50 || var0 == 51 || var0 == 52 || var0 == 53 || var0 == 54 || var0 == 55 || var0 == 56 || var0 == 57;
   }
}
