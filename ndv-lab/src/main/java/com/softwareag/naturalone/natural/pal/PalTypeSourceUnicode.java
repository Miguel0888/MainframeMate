package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeSourceUnicode;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public final class PalTypeSourceUnicode extends PalTypeSource implements IPalTypeSourceUnicode {
   private static final long serialVersionUID = 1L;

   public PalTypeSourceUnicode() {
      super.type = 42;
   }

   public PalTypeSourceUnicode(String var1) {
      this();
      super.sourceLine = var1;
   }

   public void serialize() {
      if (super.palVersion >= 39 && (super.palVersion < 39 || super.ndvType == 1)) {
         Object var5 = null;
         String var2 = super.sourceLine + ' ';

         try {
            byte[] var6 = var2.getBytes("UTF-8");
            byte[] var3 = PalTypeStream.Palbtos(var6);
            this.byteArrayToBuffer(var3);
         } catch (UnsupportedEncodingException var4) {
            var4.printStackTrace();
         }
      } else {
         char[] var1 = this.utf16ToCharsetToBase64(super.sourceLine, "UTF-8", true);
         this.byteArrayToBuffer((new String(var1)).getBytes());
      }

   }

   public void restore() {
      Object var1 = null;
      byte[] var5;
      if (super.palVersion >= 39 && (super.palVersion < 39 || super.ndvType == 1)) {
         var5 = this.recordToByteArray();
         var5 = PalTypeStream.Palstob(var5);
      } else {
         var5 = Base64Coder.decode(this.recordToCharArray(), (byte)3);
      }

      try {
         super.sourceLine = this.getUtf16(var5, "UTF8").toString();
      } catch (UnsupportedEncodingException var3) {
         var3.printStackTrace();
      } catch (IOException var4) {
         var4.printStackTrace();
      }

   }

   public void convert(String var1) {
   }
}
