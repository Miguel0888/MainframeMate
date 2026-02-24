package com.softwareag.naturalone.natural.pal;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;

public abstract class PalType implements Serializable, IPalType {
   private static final long serialVersionUID = 1L;
   private ArrayList record = null;
   protected int type;
   protected int recordTail;
   protected int recordLength;
   protected int palVersion;
   protected int ndvType;
   protected String serverCodePage = null;

   public PalType() {
      this.setRecord(new ArrayList());
   }

   private final byte getByte() {
      try {
         return (Byte)this.record.get(this.recordTail++);
      } catch (PalType$NullPointerException var1) {
         return 0;
      }
   }

   private final byte[] getBuffer() {
      Object var1 = null;
      int var2 = 0;

      int var3;
      for(var3 = this.record.size(); this.recordTail + var2 < var3 && (Byte)this.record.get(this.recordTail + var2) != 0; ++var2) {
      }

      byte[] var4 = new byte[var2];

      for(int var5 = 0; this.recordTail < var3 && (Byte)this.record.get(this.recordTail) != 0; var4[var5++] = (Byte)this.record.get(this.recordTail++)) {
      }

      ++this.recordTail;
      return var4;
   }

   protected final byte[] getBuffer(int var1) {
      Object var2 = null;
      boolean var3 = false;
      int var4 = this.record.size();
      byte[] var5 = new byte[var1];

      for(int var6 = 0; this.recordTail < var4 && var6 < var1; var5[var6++] = (Byte)this.record.get(this.recordTail++)) {
      }

      return var5;
   }

   public final void setRecord(ArrayList var1) {
      try {
         this.recordTail = 0;
         this.recordLength = var1.size();
         this.record = var1;
      } catch (PalType$NullPointerException var2) {
      }
   }

   public final ArrayList getRecord() {
      return this.record;
   }

   public int get() {
      return this.type;
   }

   public void setPalVers(int var1) {
      try {
         this.palVersion = var1;
      } catch (PalType$NullPointerException var2) {
      }
   }

   public void setNdvType(int var1) {
      try {
         this.ndvType = var1;
      } catch (PalType$NullPointerException var2) {
      }
   }

   public void setServerCodePage(String var1) {
      try {
         this.serverCodePage = var1;
      } catch (PalType$NullPointerException var2) {
      }
   }

   public final int intFromBuffer() {
      try {
         return Integer.valueOf(new String(this.getBuffer()));
      } catch (PalType$NullPointerException var1) {
         return 0;
      }
   }

   public final String stringFromBuffer() {
      try {
         return new String(this.getBuffer());
      } catch (PalType$NullPointerException var1) {
         return null;
      }
   }

   public final String codePageStringFromBuffer() {
      if (this.serverCodePage != null && !this.serverCodePage.isEmpty()) {
         try {
            return ICUCharsetCoder.decode(this.serverCodePage, this.getBuffer());
         } catch (CharacterCodingException var1) {
         }
      }

      return this.stringFromBuffer();
   }

   public abstract void serialize();

   public abstract void restore();

   protected final void stringToBuffer(String var1) {
      byte[] var2 = var1.getBytes();

      for(int var3 = 0; var3 < var2.length; ++var3) {
         this.record.add(var2[var3]);
      }

      this.record.add((byte)0);
   }

   protected final void byteToBuffer(byte var1) {
      try {
         this.record.add(var1);
      } catch (PalType$NullPointerException var2) {
      }
   }

   protected final void intToBuffer(int var1) {
      try {
         Integer var2 = var1;
         String var3 = var2.toString();
         this.stringToBuffer(var3);
      } catch (PalType$NullPointerException var4) {
      }
   }

   protected final void byteArrayToBuffer(byte[] var1) {
      for(int var2 = 0; var2 < var1.length; ++var2) {
         this.record.add(var1[var2]);
      }

   }

   protected final byte byteFromBuffer() {
      return this.getByte();
   }

   protected final String stringFromBuffer(int var1) {
      try {
         return new String(this.getBuffer(var1));
      } catch (PalType$NullPointerException var2) {
         return null;
      }
   }

   protected final String codePageStringFromBuffer(int var1) {
      if (this.serverCodePage != null && !this.serverCodePage.isEmpty()) {
         try {
            return ICUCharsetCoder.decode(this.serverCodePage, this.getBuffer(var1));
         } catch (CharacterCodingException var2) {
         }
      }

      return this.stringFromBuffer(var1);
   }

   protected final void codePageStringToBuffer(String param1) {
      // $FF: Couldn't be decompiled
   }

   protected final char[] utf16ToCharsetToBase64(String var1, String var2, boolean var3) {
      try {
         int[] var4 = this.utf16ToCharset(var1, var2, var3);
         Object var5 = null;
         char[] var7 = Base64Coder.encode(var4, (byte)3);
         return var7;
      } catch (PalType$NullPointerException var6) {
         return null;
      }
   }

   protected final int[] utf16ToCharset(String var1, String var2, boolean var3) {
      int[] var4 = null;

      try {
         byte[] var5 = var1.getBytes(var2);
         if (var3) {
            var4 = new int[var5.length + 1];
         } else {
            var4 = new int[var5.length];
         }

         for(int var6 = 0; var6 < var5.length; ++var6) {
            var4[var6] = var5[var6];
            if (var4[var6] < 0) {
               var4[var6] += 256;
            }
         }

         if (var3) {
            var4[var5.length] = 0;
         }
      } catch (IOException var7) {
         var7.printStackTrace();
      }

      return var4;
   }

   protected final StringBuffer getUtf16(byte[] var1, String var2) throws UnsupportedEncodingException, IOException {
      ByteArrayInputStream var3 = new ByteArrayInputStream(var1);
      StringBuffer var4 = new StringBuffer();
      InputStreamReader var5 = new InputStreamReader(var3, var2);
      BufferedReader var6 = new BufferedReader(var5);

      int var7;
      while((var7 = ((Reader)var6).read()) > -1) {
         var4.append((char)var7);
      }

      ((Reader)var6).close();
      int var8 = var4.length();
      if (var4.charAt(var8 - 1) == 0) {
         var4.deleteCharAt(var8 - 1);
      }

      return var4;
   }

   protected final StringBuffer getUtf16ICU(byte[] var1, String var2) throws UnsupportedEncodingException, IOException {
      StringBuffer var3 = new StringBuffer();
      StringReader var4 = new StringReader(ICUCharsetCoder.decode(var2, var1));

      int var5;
      while((var5 = ((Reader)var4).read()) > -1) {
         var3.append((char)var5);
      }

      ((Reader)var4).close();
      int var6 = var3.length();
      if (var6 > 0 && var3.charAt(var6 - 1) == 0) {
         var3.deleteCharAt(var6 - 1);
      }

      return var3;
   }

   protected final boolean booleanFromBuffer() {
      return this.getByte() != 0;
   }

   protected final void booleanToBuffer(boolean var1) {
      try {
         int var2 = var1 ? 1 : 0;
         this.record.add(Byte.valueOf((byte)var2));
      } catch (PalType$NullPointerException var3) {
      }
   }

   protected final char[] recordToCharArray() {
      byte[] var1 = new byte[this.recordLength];

      for(int var2 = 0; var2 < this.recordLength; ++var2) {
         var1[var2] = (Byte)this.record.get(var2);
      }

      return (new String(var1)).toCharArray();
   }

   protected final byte[] recordToByteArray() {
      byte[] var1 = new byte[this.recordLength];

      for(int var2 = 0; var2 < this.recordLength; ++var2) {
         var1[var2] = (Byte)this.record.get(var2);
      }

      return var1;
   }
}
