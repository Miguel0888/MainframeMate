package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgVarValue;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.charset.CharacterCodingException;

public class PalTypeDbgVarValue extends PalType implements Serializable, IPalTypeDbgVarValue {
   private static final long serialVersionUID = 1L;
   private static final int VARVAL_U = 1;
   private static final int VAL_OK = 0;
   private static final int VAL_DISABLE = -1;
   private static final int VAL_INVALIDCONTENTS = -2;
   private static final int VAL_NOTACCESSIBLE = -3;
   private static final int VAL_TOOLARGE = -4;
   private static final int VAL_NOTSPECIFIED = -5;
   private static final int VAL_INTERNALERR = -6;
   private int flags;
   private int currentLength;
   private int id;
   private int returnCode;
   private int valueLength;
   private String value;
   private byte[] ebcdicRecord;

   public PalTypeDbgVarValue() {
      this.value = "";
      this.ebcdicRecord = null;
      super.type = 39;
   }

   public PalTypeDbgVarValue(String var1) {
      this();
      this.value = var1;
   }

   public void serialize() {
      try {
         this.intToBuffer(this.flags);
         this.intToBuffer(this.currentLength);
         this.intToBuffer(this.id);
         this.intToBuffer(this.returnCode);
         if (this.isUnicode()) {
            char[] var1 = this.utf16ToCharsetToBase64(this.value, "UTF-8", true);
            byte[] var2 = (new String(var1)).getBytes();
            this.intToBuffer(var2.length);
            this.byteArrayToBuffer(var2);
         } else if (super.ndvType == 1) {
            if (super.palVersion >= 41 && !this.isUnicode()) {
               this.intToBuffer(this.value.length() * 2);
               byte[] var4 = ICUCharsetCoder.encode(super.serverCodePage, this.value, false);
               var4 = PalTypeStream.Palbtos(var4);
               this.byteArrayToBuffer(var4);
            } else {
               this.intToBuffer(this.value.length());
               this.stringToBuffer(this.value);
            }
         } else {
            this.intToBuffer(this.value.length());
            this.codePageStringToBuffer(this.value);
         }
      } catch (CharacterCodingException var3) {
      }

   }

   public void restore() {
      try {
         this.flags = this.intFromBuffer();
         this.currentLength = this.intFromBuffer();
         this.id = this.intFromBuffer();
         this.returnCode = this.intFromBuffer();
         int var1 = this.intFromBuffer();
         if (super.ndvType == 1) {
            if (super.palVersion >= 41 && !this.isUnicode()) {
               this.ebcdicRecord = this.getBuffer(var1);
               this.ebcdicRecord = PalTypeStream.Palstob(this.ebcdicRecord);
               this.value = this.getUtf16ICU(this.ebcdicRecord, super.serverCodePage).toString();
            } else {
               this.value = this.stringFromBuffer(var1);
            }
         } else {
            this.value = this.codePageStringFromBuffer(var1);
         }

         if (this.isUnicode()) {
            byte[] var2 = Base64Coder.decode(this.value.toCharArray(), (byte)3);
            this.value = this.getUtf16(var2, "UTF8").toString();
         }
      } catch (UnsupportedEncodingException var3) {
         var3.printStackTrace();
      } catch (IOException var4) {
         var4.printStackTrace();
      }

   }

   public final boolean isUnicode() {
      try {
         return (this.flags & 1) == 1;
      } catch (PalTypeDbgVarValue$ArrayOutOfBoundsException var1) {
         return false;
      }
   }

   public final void setCurrentLength(int var1) {
      try {
         this.currentLength = var1;
      } catch (PalTypeDbgVarValue$ArrayOutOfBoundsException var2) {
      }
   }

   public final int getFlags() {
      return this.flags;
   }

   public final void setFlags(int var1) {
      try {
         this.flags = var1;
      } catch (PalTypeDbgVarValue$ArrayOutOfBoundsException var2) {
      }
   }

   public final int getId() {
      return this.id;
   }

   public final void setId(int var1) {
      try {
         this.id = var1;
      } catch (PalTypeDbgVarValue$ArrayOutOfBoundsException var2) {
      }
   }

   public final int getReturnCode() {
      return this.returnCode;
   }

   public final void setReturnCode(int var1) {
      try {
         this.returnCode = var1;
      } catch (PalTypeDbgVarValue$ArrayOutOfBoundsException var2) {
      }
   }

   public final String getValue() {
      return this.value;
   }

   public final void setValue(String var1) {
      try {
         this.value = var1;
      } catch (PalTypeDbgVarValue$ArrayOutOfBoundsException var2) {
      }
   }

   public final void markAsUnicode() {
      try {
         this.flags |= 1;
      } catch (PalTypeDbgVarValue$ArrayOutOfBoundsException var1) {
      }
   }

   public final void setValueLength(int var1) {
      try {
         this.valueLength = var1;
      } catch (PalTypeDbgVarValue$ArrayOutOfBoundsException var2) {
      }
   }

   public final int getCurrentLength() {
      return this.currentLength;
   }

   public String toString() {
      try {
         String var1 = "";
         switch (this.returnCode) {
            case -6:
               var1 = "<< Internal error >>";
               break;
            case -5:
               var1 = "<< Parameter is not specified >>";
               break;
            case -4:
               var1 = "??????";
               break;
            case -3:
               var1 = "<< Value not accessible >>";
               break;
            case -2:
               var1 = "<< Invalid contents >>";
               break;
            case -1:
               var1 = "??????";
               break;
            case 0:
               var1 = this.value;
         }

         return var1;
      } catch (PalTypeDbgVarValue$ArrayOutOfBoundsException var2) {
         return null;
      }
   }
}
