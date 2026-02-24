package com.softwareag.naturalone.natural.pal;

import com.ibm.icu.charset.CharsetICU;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.UnmappableCharacterException;
import java.util.HashMap;
import java.util.Map;

public class ICUCharsetCoder {
   private static Map charsetMap = null;

   private static CharsetInfo getCharsetInfo(String var0) {
      CharsetInfo var1 = null;
      if (charsetMap == null) {
         charsetMap = new HashMap();
      } else {
         var1 = (CharsetInfo)charsetMap.get(var0);
      }

      if (var1 == null) {
         Charset var2 = CharsetICU.forNameICU(var0);
         var1 = new CharsetInfo(var2, var2.newDecoder(), var2.newEncoder());
         charsetMap.put(var0, var1);
      }

      return var1;
   }

   private static CharsetDecoder getCharsetDecoder(String var0) {
      try {
         return getCharsetInfo(var0).getDecoder();
      } catch (ICUCharsetCoder$IOException var1) {
         return null;
      }
   }

   private static CharsetEncoder getCharsetEncoder(String var0) {
      try {
         return getCharsetInfo(var0).getEncoder();
      } catch (ICUCharsetCoder$IOException var1) {
         return null;
      }
   }

   public static String decode(String var0, byte[] var1) throws CharacterCodingException {
      try {
         ByteBuffer var2 = ByteBuffer.wrap(var1);
         CharsetDecoder var3 = getCharsetDecoder(var0);
         return var3.decode(var2).toString();
      } catch (ICUCharsetCoder$IOException var4) {
         return null;
      }
   }

   public static byte[] encode(String var0, String var1, boolean var2) throws CharacterCodingException, UnmappableCharacterException {
      CharsetEncoder var3 = getCharsetEncoder(var0);
      CharBuffer var4 = CharBuffer.wrap(var1.toCharArray());

      try {
         ByteBuffer var5 = var3.encode(var4);
         return var5.array();
      } catch (UnmappableCharacterException var10) {
         if (var2) {
            try {
               Charset var6 = Charset.forName(var0);
               if (var6 != null) {
                  CharsetEncoder var7 = var6.newEncoder();
                  var7.onUnmappableCharacter(CodingErrorAction.REPORT);
                  ByteBuffer var8 = var7.encode(var4);
                  return var8.array();
               }
            } catch (CharacterCodingException var9) {
               throw var10;
            }

            return new byte[0];
         } else {
            throw var10;
         }
      }
   }

   public static int getUnsupportedCodePoint(String var0, String var1) {
      int var2 = 0;

      for(int var3 = 1; var3 < var1.length(); ++var3) {
         String var4 = var1.substring(0, var3);

         try {
            encode(var0, var4, false);
         } catch (CharacterCodingException var5) {
            var2 = var4.codePointAt(var3 - 1);
            break;
         }
      }

      return var2;
   }

   public static ConversionErrorInfo getUnsupportedCodePoint(String var0, byte[] var1) {
      ConversionErrorInfo var2 = new ConversionErrorInfo();
      byte var4 = var1[0];
      var2.setOffset(1);

      for(int var5 = var1.length - 1; var5 > 0; --var5) {
         var4 = var1[var5];
         var1[var5] = 0;

         try {
            String var3 = decode(var0, var1);
            var2.setOffset(var3.length());
            break;
         } catch (Exception ignored) {
         }
      }

      var2.setUnmappableCodePoint(var4);
      return var2;
   }
}

