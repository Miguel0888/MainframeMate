package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeResultEx;

public final class PalTypeResultEx extends PalType implements IPalTypeResultEx {
   private String naturalText;
   private String systemText;
   private String sourceName;
   private String library;
   private int naturalType;
   private int row;
   private int column;
   private int symbolLength;
   private int databaseId;
   private int fileNumber;
   private int errorType;

   public PalTypeResultEx() {
      super.type = 11;
   }

   public void serialize() {
   }

   public void restore() {
      try {
         this.naturalText = this.stringFromBuffer();
         this.systemText = this.stringFromBuffer();
         this.sourceName = this.stringFromBuffer();
         this.library = this.stringFromBuffer();
         this.naturalType = this.intFromBuffer();
         this.row = this.intFromBuffer();
         this.intFromBuffer();
         this.column = this.intFromBuffer();
         this.symbolLength = this.intFromBuffer();
         this.databaseId = this.intFromBuffer();
         this.fileNumber = this.intFromBuffer();
         this.intFromBuffer();
         this.errorType = this.intFromBuffer();
         this.intFromBuffer();
      } catch (PalTypeResultEx$IOException var1) {
      }
   }

   public String getShortText() {
      return this.naturalText;
   }

   public int getRow() {
      return this.row;
   }

   public int getColumn() {
      return this.column;
   }

   public int getLengthSymbol() {
      return this.symbolLength;
   }

   public String getName() {
      return this.sourceName;
   }

   public String getLibrary() {
      return this.library;
   }

   public int getKind() {
      return this.errorType;
   }

   public int getType() {
      return this.naturalType;
   }

   public final int getDatabaseId() {
      return this.databaseId;
   }

   public final int getFileNumber() {
      return this.fileNumber;
   }

   public final String getSystemText() {
      return this.systemText;
   }
}
