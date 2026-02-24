package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeSrcDesc;

public class PalTypeSrcDesc extends PalType implements IPalTypeSrcDesc {
   private int lineCount;
   private int sourceLength;
   private int natType;
   private int flags;
   private String sourceName;
   private String sourceLongName;
   private int databaseId;
   private int fileNumber;
   private int options;

   public PalTypeSrcDesc() {
      this.sourceName = "";
      this.sourceLongName = "";
      super.type = 15;
   }

   public PalTypeSrcDesc(int var1, String var2, boolean var3, int var4) {
      this.sourceName = "";
      this.sourceLongName = "";
      super.type = 15;
      this.natType = var1;
      this.sourceName = var2;
      this.sourceLongName = var2;
      this.flags = var3 ? 1 : 0;
      this.options = var4;
   }

   public PalTypeSrcDesc(int var1, String var2, boolean var3, int var4, int var5) {
      this(var1, var2, var3, 0);
      this.databaseId = var4;
      this.fileNumber = var5;
   }

   public void serialize() {
      try {
         this.intToBuffer(this.lineCount);
         this.intToBuffer(this.sourceLength);
         this.intToBuffer(this.natType);
         this.intToBuffer(this.flags);
         this.stringToBuffer(this.sourceName);
         this.stringToBuffer(this.sourceLongName);
         this.intToBuffer(this.databaseId);
         this.intToBuffer(this.fileNumber);
         this.intToBuffer(this.options);
      } catch (PalTypeSrcDesc$ParseException var1) {
      }
   }

   public void restore() {
      try {
         this.lineCount = this.intFromBuffer();
         this.sourceLength = this.intFromBuffer();
         this.natType = this.intFromBuffer();
         this.flags = this.intFromBuffer();
         this.sourceName = this.stringFromBuffer();
         this.sourceLongName = this.stringFromBuffer();
         this.databaseId = this.intFromBuffer();
         this.fileNumber = this.intFromBuffer();
      } catch (PalTypeSrcDesc$ParseException var1) {
      }
   }

   public String getSourceLongName() {
      return this.sourceLongName;
   }
}
