package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeSourceCP;
import com.softwareag.naturalone.natural.pal.external.PalTrace;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.UnmappableCharacterException;

public class PalTypeSourceCP extends PalTypeSource implements IPalTypeSourceCP {
   private static final long serialVersionUID = 1L;
   private byte[] ebcdicRecord = null;

   public PalTypeSourceCP() {
      super.type = 48;
   }

   public void convert(String var1) throws UnsupportedEncodingException, IOException {
      try {
         super.sourceLine = this.getUtf16ICU(this.ebcdicRecord, var1).toString();
      } catch (UnmappableCharacterException var4) {
         ConversionErrorInfo var2 = ICUCharsetCoder.getUnsupportedCodePoint(var1, this.ebcdicRecord);
         String var3 = String.format("NAT3422 Code point 0x%02X is not in code page %s.", var2.getUnmappableCodePoint(), var1);
         throw new PalUnmappableCodePointException(var3, this, var2.getOffset());
      }

   }

   public void restore() {
      if (super.palVersion <= 35) {
         this.ebcdicRecord = Base64Coder.decode(this.recordToCharArray(), (byte)3);
      } else {
         this.ebcdicRecord = this.recordToByteArray();
         this.ebcdicRecord = PalTypeStream.Palstob(this.ebcdicRecord);
      }

   }

   public void serialize() {
      if (super.palVersion <= 35) {
         char[] var1 = this.utf16ToCharsetToBase64(super.sourceLine, super.charSetName, true);
         this.byteArrayToBuffer((new String(var1)).getBytes());
      } else {
         try {
            String var8 = super.sourceLine + ' ';
            byte[] var10 = ICUCharsetCoder.encode(super.charSetName, var8, false);
            var10 = PalTypeStream.Palbtos(var10);
            this.byteArrayToBuffer(var10);
         } catch (CharacterCodingException var4) {
            try {
               String var5 = "SourceLine: ";

               for(int var2 = 0; var2 < super.sourceLine.length(); ++var2) {
                  var5 = String.format("%s 0x%04x", var5, Integer.valueOf(super.sourceLine.charAt(var2)));
               }

               PalTrace.text("character encoding problem:" + var5);
            } catch (IOException var3) {
            }

            int var6 = -1;
            String var9 = String.format("NAT3422 Character U+%04X is not in code page %s.", var6 = ICUCharsetCoder.getUnsupportedCodePoint(super.charSetName, super.sourceLine), super.charSetName);
            throw new PalUnmappableCodePointException(var9, this, super.sourceLine.indexOf(var6));
         }
      }

   }
}
