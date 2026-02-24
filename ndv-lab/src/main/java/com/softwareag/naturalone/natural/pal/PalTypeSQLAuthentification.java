package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.paltransactions.external.IPalTypeSQLAuthentification;
import java.io.Serializable;

public class PalTypeSQLAuthentification extends PalType implements IPalTypeSQLAuthentification, Serializable {
   private String title = "";
   private String text = "";
   private String prompt1 = "";
   private String prompt2 = "";
   private String uid = "";
   private String pwd = "";
   private String dummy1 = "";
   private String dummy2 = "";
   int lengthUid;
   int lengthPwd;
   int lengthDummy1;
   int lengthDummy2;
   private static final long serialVersionUID = 1L;

   public PalTypeSQLAuthentification() {
      super.type = 26;
   }

   public void restore() {
      try {
         this.title = this.stringFromBuffer();
         this.text = this.stringFromBuffer();
         this.prompt1 = this.stringFromBuffer();
         this.prompt2 = this.stringFromBuffer();
         this.uid = this.stringFromBuffer();
         this.pwd = this.stringFromBuffer();
         this.dummy1 = this.stringFromBuffer();
         this.dummy2 = this.stringFromBuffer();
         this.lengthUid = this.intFromBuffer();
         this.lengthPwd = this.intFromBuffer();
         this.lengthDummy1 = this.intFromBuffer();
         this.lengthDummy2 = this.intFromBuffer();
      } catch (PalTypeSQLAuthentification$Exception var1) {
      }
   }

   public void serialize() {
      try {
         this.stringToBuffer(this.title);
         this.stringToBuffer(this.text);
         this.stringToBuffer(this.prompt1);
         this.stringToBuffer(this.prompt2);
         this.stringToBuffer(this.uid);
         this.stringToBuffer(this.pwd);
         this.stringToBuffer(this.dummy1);
         this.stringToBuffer(this.dummy2);
         this.intToBuffer(this.lengthUid);
         this.intToBuffer(this.lengthPwd);
         this.intToBuffer(this.lengthDummy1);
         this.intToBuffer(this.lengthDummy2);
      } catch (PalTypeSQLAuthentification$Exception var1) {
      }
   }

   public String getTitle() {
      return this.title;
   }

   public void setTitle(String var1) {
      try {
         this.title = var1;
      } catch (PalTypeSQLAuthentification$Exception var2) {
      }
   }

   public String getText() {
      return this.text;
   }

   public void setText(String var1) {
      try {
         this.text = var1;
      } catch (PalTypeSQLAuthentification$Exception var2) {
      }
   }

   public String getPrompt1() {
      return this.prompt1;
   }

   public void setPrompt1(String var1) {
      try {
         this.prompt1 = var1;
      } catch (PalTypeSQLAuthentification$Exception var2) {
      }
   }

   public String getPrompt2() {
      return this.prompt2;
   }

   public void setPrompt2(String var1) {
      try {
         this.prompt2 = var1;
      } catch (PalTypeSQLAuthentification$Exception var2) {
      }
   }

   public String getUid() {
      return this.uid;
   }

   public void setUid(String var1) {
      try {
         this.uid = var1;
      } catch (PalTypeSQLAuthentification$Exception var2) {
      }
   }

   public String getPwd() {
      return this.pwd;
   }

   public void setPwd(String var1) {
      try {
         this.pwd = var1;
      } catch (PalTypeSQLAuthentification$Exception var2) {
      }
   }

   public int getLengthUid() {
      return this.lengthUid;
   }

   public void setLengthUid(int var1) {
      try {
         this.lengthUid = var1;
      } catch (PalTypeSQLAuthentification$Exception var2) {
      }
   }

   public int getLengthPwd() {
      return this.lengthPwd;
   }

   public void setLengthPwd(int var1) {
      try {
         this.lengthPwd = var1;
      } catch (PalTypeSQLAuthentification$Exception var2) {
      }
   }
}
