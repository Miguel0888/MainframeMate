package com.softwareag.naturalone.natural.paltransactions.external;

public class PalCompileResultException extends PalResultException {
   static final long serialVersionUID = -1234567891L;
   private int row;
   private int column;
   private String object = "";
   private String library = "";
   private int natType = 0;
   private int databaseId = 0;
   private int fileNumber = 0;

   public PalCompileResultException(int var1, int var2, String var3, int var4, int var5, int var6, String var7, String var8, int var9, int var10) {
      super(var1, var2, var3);
      this.row = var4;
      this.column = var5;
      this.natType = var6;
      this.object = var7;
      this.library = var8;
      this.databaseId = var9;
      this.fileNumber = var10;
   }

   public final int getRow() {
      return this.row;
   }

   public final int getColumn() {
      return this.column;
   }

   public final int getType() {
      return this.natType;
   }

   public final String getObject() {
      return this.object;
   }

   public final String getLibrary() {
      return this.library;
   }

   public final int getDatabaseId() {
      return this.databaseId;
   }

   public final int getFileNumber() {
      return this.fileNumber;
   }
}
