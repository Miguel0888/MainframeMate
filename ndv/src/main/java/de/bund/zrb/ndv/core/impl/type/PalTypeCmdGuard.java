package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IPalTypeCmdGuard;

public final class PalTypeCmdGuard extends PalType implements IPalTypeCmdGuard {
   private static final long serialVersionUID = 1L;
   private static final int DIALOG = 1;
   private static final int MAPGUI = 2;
   private static final int NEW_LIB = 4;
   private static final int EDT_PROGRAM = 8;
   private static final int EDT_SUBPRG = 16;
   private static final int EDT_TEXT = 32;
   private static final int EDT_MAP = 64;
   private static final int EDT_SUBROUT = 128;
   private static final int EDT_CPYCODE = 256;
   private static final int EDT_CLASS = 512;
   private static final int EDT_PDA = 1024;
   private static final int EDT_LDA = 2048;
   private static final int EDT_DDM = 4096;
   private static final int EDT_HLPROUT = 8192;
   private static final int EDT_FUNCT = 16384;
   private static final int EDT_DLG = 32768;
   private static final int EDT_GDA = 65536;
   private static final int LIST_DDM = 131072;
   private static final int NEW_DDM = 262144;
   private static final int CAT_DDM = 524288;
   private static final int REN_DDM = 1048576;
   private static final int DEL_DDM = 2097152;
   private static final int CUT_DDM = 4194304;
   private static final int COPY_DDM = 8388608;
   private static final int PASTE_DDM = 16777216;
   private static final int SAVE_DDM = 33554432;
   private static final int STOW_DDM = 67108864;
   private static final int NSC_INSTALLED = 134217728;
   private static final int EDT_ADAPTER = 268435456;
   private int erlaubteAktionen1;
   private static final int CAT = 1;
   private static final int UNCAT = 2;
   private static final int CHECK = 4;
   private static final int STOW = 8;
   private static final int SAVE = 16;
   private static final int LIST = 32;
   private static final int READ = 64;
   private static final int RUN = 128;
   private static final int DEBUG = 256;
   private static final int SCAN = 512;
   private static final int REG = 1024;
   private static final int UNREG = 2048;
   private static final int SCRATCH = 4096;
   private static final int PURGE = 8192;
   private static final int CUT = 16384;
   private static final int COPY = 32768;
   private static final int PASTE = 65536;
   private static final int RENAME = 131072;
   private static final int CATALL = 262144;
   private static final int GLOBALS = 524288;
   private static final int XREF = 1048576;
   private static final int HELP = 2097152;
   private static final int MAIL = 4194304;
   private static final int PROFILE = 8388608;
   private static final int AIV = 16777216;
   private static final int COMPOPT = 33554432;
   private static final int SYSMAIN = 67108864;
   private static final int CLEAR = 134217728;
   private static final int RETURN = 268435456;
   private static final int SETUP = 536870912;
   private static final int UPDATE = 1073741824;
   private int erlaubteAktionen2;
   private static final int DELETE = 1;
   private static final int INPL = 2;
   private static final int KEY = 4;
   private static final int DUMP = 8;
   private static final int ISPF = 16;
   private static final int MAINMENU = 32;
   private static final int IDL = 64;
   private static final int TECH = 128;
   private static final int HELLO = 256;
   private static final int OBJREN = 512;
   private static final int SULIPR = 1024;
   private static final int LIBDEL = 2048;
   private static final int PDBG = 4096;
   private static final int SCANR = 8192;
   private static final int UNLOCK = 16384;
   private static final int UNLOCKF = 32768;
   private static final int NOT_PRIVATE = 65536;
   private static final int FDIC = 131072;
   private static final int PRIVATEMODE_ALLOWED = 262144;
   private static final int SHAREDMODE_ALLOWED = 524288;
   private int erlaubteAktionen3;
   private int erlaubteAktionen4;

   public PalTypeCmdGuard() {
      super.typSchluessel = 27;
   }

   public void serialize() {
   }

   public void restore() {
      this.erlaubteAktionen1 = this.intFromBuffer();
      this.erlaubteAktionen2 = this.intFromBuffer();
      this.erlaubteAktionen3 = this.intFromBuffer();
      this.erlaubteAktionen4 = this.intFromBuffer();
   }

   public int getInfo1() {
      return this.erlaubteAktionen1;
   }

   public boolean isNSCInstalled() {
      return (this.erlaubteAktionen1 & 134217728) != 134217728;
   }

   public boolean isFDICInstalled() {
      return (this.erlaubteAktionen3 & 131072) == 131072;
   }

   public boolean isListAllowed() {
      return (this.erlaubteAktionen2 & 32) == 32;
   }

   public boolean isCatalogAllowed() {
      return (this.erlaubteAktionen2 & 1) == 1;
   }

   public boolean isSaveAllowed() {
      return (this.erlaubteAktionen2 & 16) == 16;
   }

   public boolean isCheckAllowed() {
      return (this.erlaubteAktionen2 & 4) == 4;
   }

   public boolean isStowAllowed() {
      return (this.erlaubteAktionen2 & 8) == 8;
   }

   public boolean isReadAllowed() {
      return (this.erlaubteAktionen2 & 64) == 64;
   }

   public boolean isListDdmAllowed() {
      return (this.erlaubteAktionen1 & 131072) == 131072;
   }

   public boolean isCatalogDdmAllowed() {
      return (this.erlaubteAktionen1 & 524288) == 524288;
   }

   public boolean isCopyAllowed() {
      return (this.erlaubteAktionen2 & 32768) == 32768;
   }

   public boolean isCutAllowed() {
      return (this.erlaubteAktionen2 & 16384) == 16384;
   }

   public boolean isCopyDdmAllowed() {
      return (this.erlaubteAktionen1 & 8388608) == 8388608;
   }

   public boolean isCutDdmAllowed() {
      return (this.erlaubteAktionen1 & 4194304) == 4194304;
   }

   public boolean isPasteAllowed() {
      return (this.erlaubteAktionen2 & 65536) == 65536;
   }

   public boolean isPasteDdmAllowed() {
      return (this.erlaubteAktionen1 & 16777216) == 16777216;
   }

   public boolean isRenameAllowed() {
      return (this.erlaubteAktionen3 & 512) == 512;
   }

   public boolean isRenameLibraryAllowed() {
      return (this.erlaubteAktionen2 & 131072) == 131072;
   }

   public boolean isRenameDdmAllowed() {
      return (this.erlaubteAktionen1 & 1048576) == 1048576;
   }

   public boolean isDeleteAllowed() {
      return (this.erlaubteAktionen3 & 1) == 1;
   }

   public boolean isDeleteLibraryAllowed() {
      return (this.erlaubteAktionen3 & 2048) == 2048;
   }

   public boolean isDeleteDdmAllowed() {
      return (this.erlaubteAktionen1 & 2097152) == 2097152;
   }

   public boolean isSaveDdmAllowed() {
      return (this.erlaubteAktionen1 & 33554432) == 33554432;
   }

   public boolean isStowDdmAllowed() {
      return (this.erlaubteAktionen1 & 67108864) == 67108864;
   }

   public boolean isUnlockAllowed() {
      return (this.erlaubteAktionen3 & 16384) == 16384;
   }

   public boolean isUnlockForcedAllowed() {
      return (this.erlaubteAktionen3 & 32768) == 32768;
   }

   public boolean isPrivate() {
      return (this.erlaubteAktionen3 & 65536) != 65536;
   }

   public boolean isEditAllowed(int var1) {
      boolean var2 = true;
      if (var1 == 2097152) {
         var2 = (this.erlaubteAktionen1 & 268435456) == 268435456;
      } else if (var1 == 1024) {
         var2 = (this.erlaubteAktionen1 & 512) == 512;
      } else if (var1 == 128) {
         var2 = (this.erlaubteAktionen1 & 256) == 256;
      } else if (var1 == 8) {
         var2 = (this.erlaubteAktionen1 & 4096) == 4096;
      } else if (var1 == 2048) {
         var2 = (this.erlaubteAktionen1 & 32768) == 32768;
      } else if (var1 == 524288) {
         var2 = (this.erlaubteAktionen1 & 16384) == 16384;
      } else if (var1 == 1) {
         var2 = (this.erlaubteAktionen1 & 65536) == 65536;
      } else if (var1 == 512) {
         var2 = (this.erlaubteAktionen1 & 8192) == 8192;
      } else if (var1 == 2) {
         var2 = (this.erlaubteAktionen1 & 2048) == 2048;
      } else if (var1 == 64) {
         var2 = (this.erlaubteAktionen1 & 64) == 64;
      } else if (var1 == 4) {
         var2 = (this.erlaubteAktionen1 & 1024) == 1024;
      } else if (var1 == 16) {
         var2 = (this.erlaubteAktionen1 & 8) == 8;
      } else if (var1 == 32) {
         var2 = (this.erlaubteAktionen1 & 16) == 16;
      } else if (var1 == 256) {
         var2 = (this.erlaubteAktionen1 & 128) == 128;
      } else if (var1 == 4096) {
         var2 = (this.erlaubteAktionen1 & 32) == 32;
      }

      return var2;
   }

   public boolean isEditForAllAllowed(boolean var1) {
      int var2 = 0;
      var2 = 268562424;
      if (var1) {
         var2 |= 4096;
      }

      return (this.erlaubteAktionen1 & var2) == var2;
   }

   public boolean isPrivateModeMandatory() {
      boolean var1 = (this.erlaubteAktionen3 & 262144) == 262144;
      boolean var2 = (this.erlaubteAktionen3 & 524288) == 524288;
      return var1 && !var2;
   }

   public boolean isSharedModeMandatory() {
      boolean var1 = (this.erlaubteAktionen3 & 262144) == 262144;
      boolean var2 = (this.erlaubteAktionen3 & 524288) == 524288;
      return !var1 && var2;
   }

   public int getInfo3() {
      return this.erlaubteAktionen3;
   }

   public int getInfo4() {
      return this.erlaubteAktionen4;
   }
}
