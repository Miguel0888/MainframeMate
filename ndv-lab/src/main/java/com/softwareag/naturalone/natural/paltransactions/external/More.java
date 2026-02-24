package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.pal.external.IPalTypeStream;

public class More {
   private final IPalTypeStream screen = null;
   private String commands;

   public final String getCommands() {
      return this.commands;
   }

   public final IPalTypeStream getScreen() {
      return this.screen;
   }

   public final void setCommands(String var1) {
      try {
         this.commands = var1;
      } catch (More$IOException var2) {
      }
   }
}
