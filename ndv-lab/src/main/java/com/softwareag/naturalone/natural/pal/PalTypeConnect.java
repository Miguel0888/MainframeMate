package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeConnect;

public final class PalTypeConnect extends PalType implements IPalTypeConnect {
   private static final long serialVersionUID = 1L;
   private String user;
   private String password;
   private String commandline;

   public PalTypeConnect(String var1, String var2, String var3) {
      this.user = var1;
      this.password = var2;
      this.commandline = var3;
      super.type = 1;
   }

   public void restore() {
   }

   public void serialize() {
      try {
         this.stringToBuffer(this.user);
         this.stringToBuffer(this.password);
         this.stringToBuffer(this.commandline);
      } catch (PalTypeConnect$ParseException var1) {
      }
   }

   public final String getPassword() {
      return this.password;
   }

   public final String getUser() {
      return this.user;
   }
}
