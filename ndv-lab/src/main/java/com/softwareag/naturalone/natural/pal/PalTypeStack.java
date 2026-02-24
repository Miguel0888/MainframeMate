package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeStack;

public final class PalTypeStack extends PalType implements IPalTypeStack {
   public static final String CHECK = "CHECK";
   public static final String STOW = "STOW";
   public static final String CAT = "CAT";
   public static final String SAVE = "SAVE";
   private String command;
   private int databaseId = 0;
   private int fileNumber = 0;

   public PalTypeStack() {
      super.type = 9;
   }

   public PalTypeStack(String var1) {
      super.type = 9;
      this.command = var1;
   }

   public void serialize() {
      try {
         this.stringToBuffer(this.command);
         this.intToBuffer(this.databaseId);
         this.intToBuffer(this.fileNumber);
      } catch (PalTypeStack$NullPointerException var1) {
      }
   }

   public void restore() {
   }
}
