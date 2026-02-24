package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeTimeStamp;

public final class PalTypeTimeStamp extends PalType implements IPalTypeTimeStamp {
   private static final long serialVersionUID = -6276280278103698762L;
   private int flags;
   private String timeStamp;
   private String userId;

   public PalTypeTimeStamp() {
      super.type = 54;
   }

   public PalTypeTimeStamp(int var1, String var2, String var3) {
      this();
      this.flags = var1;
      this.timeStamp = var2 == null ? "" : var2;
      this.userId = var3 == null ? "" : var3;
   }

   public void serialize() {
      try {
         this.intToBuffer(this.flags);
         this.stringToBuffer(this.timeStamp);
         this.stringToBuffer(this.userId);
      } catch (PalTypeTimeStamp$Exception var1) {
      }
   }

   public void restore() {
      try {
         this.flags = this.intFromBuffer();
         this.timeStamp = this.stringFromBuffer();
         this.userId = this.stringFromBuffer();
      } catch (PalTypeTimeStamp$Exception var1) {
      }
   }

   public int getFlags() {
      return this.flags;
   }

   public String getTimeStamp() {
      return this.timeStamp;
   }

   public String getUserId() {
      return this.userId;
   }

   public int hashCode() {
      try {
         int var1 = 17;
         var1 = 37 * var1 + this.flags;
         var1 = 37 * var1 + this.timeStamp.hashCode();
         var1 = 37 * var1 + this.userId.hashCode();
         return var1;
      } catch (PalTypeTimeStamp$Exception var2) {
         return 0;
      }
   }
}
