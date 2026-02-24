package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeDevEnv;

public final class PalTypeDevEnv extends PalType implements IPalTypeDevEnv {
   private static final long serialVersionUID = 1L;
   private String pathDevEnv;
   private String hostName;

   public PalTypeDevEnv() {
      super.type = 52;
   }

   public void serialize() {
   }

   public void restore() {
      try {
         this.pathDevEnv = this.stringFromBuffer();
         this.hostName = this.stringFromBuffer();
      } catch (PalTypeDevEnv$ArrayOutOfBoundsException var1) {
      }
   }

   public String getDevEnvPath() {
      return this.pathDevEnv;
   }

   public boolean isDevEnv() {
      try {
         return this.getDevEnvPath() != null && this.getDevEnvPath().length() > 0;
      } catch (PalTypeDevEnv$ArrayOutOfBoundsException var1) {
         return false;
      }
   }

   public String getHostName() {
      return this.hostName;
   }
}
