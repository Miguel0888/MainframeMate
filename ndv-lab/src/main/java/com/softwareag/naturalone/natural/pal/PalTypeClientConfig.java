package com.softwareag.naturalone.natural.pal;

import java.io.Serializable;

public final class PalTypeClientConfig extends PalType implements Serializable {
   private static final long serialVersionUID = 1L;
   private char nonDbField;
   private char sqlSep;
   private char dynSrc;
   private char globalVar;
   private char altCharet;
   private String ident1stValid = "";
   private String identSubsequentValid = "";
   private String object1stValid = "";
   private String objectSubValid = "";
   private String ddm1stValid = "";
   private String ddmSubValid = "";
   private String lib1stValid = "";
   private String libSubValid = "";

   public void restore() {
      try {
         this.nonDbField = (char)this.byteFromBuffer();
         this.sqlSep = (char)this.byteFromBuffer();
         this.dynSrc = (char)this.byteFromBuffer();
         this.globalVar = (char)this.byteFromBuffer();
         this.altCharet = (char)this.byteFromBuffer();
         this.ident1stValid = this.stringFromBuffer();
         this.identSubsequentValid = this.stringFromBuffer();
         this.object1stValid = this.stringFromBuffer();
         this.objectSubValid = this.stringFromBuffer();
         this.ddm1stValid = this.stringFromBuffer();
         this.ddmSubValid = this.stringFromBuffer();
         this.lib1stValid = this.stringFromBuffer();
         this.libSubValid = this.stringFromBuffer();
      } catch (PalTypeClientConfig$ParseException var1) {
      }
   }

   public void serialize() {
   }

   public static long getSerialVersionUID() {
      return 1L;
   }

   public char getNonDbField() {
      return this.nonDbField;
   }

   public char getSqlSep() {
      return this.sqlSep;
   }

   public char getDynSrc() {
      return this.dynSrc;
   }

   public char getGlobalVar() {
      return this.globalVar;
   }

   public char getAltCharet() {
      return this.altCharet;
   }

   public String getIdent1stValid() {
      return this.ident1stValid;
   }

   public String getIdentSubsequentValid() {
      return this.identSubsequentValid;
   }

   public String getObject1stValid() {
      return this.object1stValid;
   }

   public String getObjectSubValid() {
      return this.objectSubValid;
   }

   public String getDdm1stValid() {
      return this.ddm1stValid;
   }

   public String getDdmSubValid() {
      return this.ddmSubValid;
   }

   public String getLib1stValid() {
      return this.lib1stValid;
   }

   public String getLibSubValid() {
      return this.libSubValid;
   }
}
