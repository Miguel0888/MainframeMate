package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeNotify;

public final class PalTypeNotify extends PalType implements IPalTypeNotify {
   private int notification;
   private int extent;

   public PalTypeNotify() {
      super.type = 19;
   }

   public PalTypeNotify(int var1) {
      super.type = 19;
      this.notification = var1;
   }

   public void serialize() {
      try {
         this.intToBuffer(this.notification);
         this.intToBuffer(this.extent);
      } catch (PalTypeNotify$ArrayOutOfBoundsException var1) {
      }
   }

   public void restore() {
      try {
         this.notification = this.intFromBuffer();
         this.extent = this.intFromBuffer();
      } catch (PalTypeNotify$ArrayOutOfBoundsException var1) {
      }
   }

   public int getNotification() {
      return this.notification;
   }
}
