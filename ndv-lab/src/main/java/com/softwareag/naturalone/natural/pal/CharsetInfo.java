package com.softwareag.naturalone.natural.pal;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

public class CharsetInfo {
   private Charset charset;
   private CharsetDecoder decoder;
   private CharsetEncoder encoder;

   public CharsetInfo(Charset var1, CharsetDecoder var2, CharsetEncoder var3) {
      this.setCharset(var1);
      this.setDecoder(var2);
      this.setEncoder(var3);
   }

   public Charset getCharset() {
      return this.charset;
   }

   private void setCharset(Charset var1) {
      try {
         this.charset = var1;
      } catch (CharsetInfo$Exception var2) {
      }
   }

   public CharsetDecoder getDecoder() {
      return this.decoder;
   }

   private void setDecoder(CharsetDecoder var1) {
      try {
         this.decoder = var1;
      } catch (CharsetInfo$Exception var2) {
      }
   }

   public CharsetEncoder getEncoder() {
      return this.encoder;
   }

   private void setEncoder(CharsetEncoder var1) {
      try {
         this.encoder = var1;
      } catch (CharsetInfo$Exception var2) {
      }
   }
}
