package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeMonitorInfo;

public class PalTypeMonitorInfo extends PalType implements IPalTypeMonitorInfo {
   private static final long serialVersionUID = 1L;
   private static final String DEFAULT_EVENT_FILTER = "0000000000000FFF";
   private String sessionId;
   private String eventFilter;

   public PalTypeMonitorInfo(String var1) {
      this(var1, "0000000000000FFF");
   }

   public PalTypeMonitorInfo(String var1, String var2) {
      super.type = 56;
      this.sessionId = var1;
      this.eventFilter = var2;
   }

   public String getSessionId() {
      return this.sessionId;
   }

   public void setSessionId(String var1) {
      try {
         this.sessionId = var1;
      } catch (PalTypeMonitorInfo$ArrayOutOfBoundsException var2) {
      }
   }

   public String getEventFilter() {
      return this.eventFilter;
   }

   public void setEventFilter(String var1) {
      try {
         this.eventFilter = var1;
      } catch (PalTypeMonitorInfo$ArrayOutOfBoundsException var2) {
      }
   }

   public void serialize() {
      try {
         this.stringToBuffer(this.sessionId);
         this.stringToBuffer(this.eventFilter);
      } catch (PalTypeMonitorInfo$ArrayOutOfBoundsException var1) {
      }
   }

   public void restore() {
      try {
         this.sessionId = this.stringFromBuffer();
         this.eventFilter = this.stringFromBuffer();
      } catch (PalTypeMonitorInfo$ArrayOutOfBoundsException var1) {
      }
   }

   public String toString() {
      try {
         return String.format("%s(%s %d)", this.sessionId, this.eventFilter);
      } catch (PalTypeMonitorInfo$ArrayOutOfBoundsException var1) {
         return null;
      }
   }
}
