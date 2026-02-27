package com.softwareag.naturalone.natural.pal.external;

public interface IPalTypeCmdGuard {
   int getInfo1();

   boolean isNSCInstalled();

   boolean isFDICInstalled();

   boolean isListAllowed();

   boolean isCatalogAllowed();

   boolean isSaveAllowed();

   boolean isCheckAllowed();

   boolean isStowAllowed();

   boolean isReadAllowed();

   boolean isListDdmAllowed();

   boolean isCatalogDdmAllowed();

   boolean isCopyAllowed();

   boolean isCutAllowed();

   boolean isCopyDdmAllowed();

   boolean isCutDdmAllowed();

   boolean isPasteAllowed();

   boolean isPasteDdmAllowed();

   boolean isRenameAllowed();

   boolean isRenameLibraryAllowed();

   boolean isRenameDdmAllowed();

   boolean isDeleteAllowed();

   boolean isDeleteLibraryAllowed();

   boolean isDeleteDdmAllowed();

   boolean isSaveDdmAllowed();

   boolean isStowDdmAllowed();

   boolean isUnlockAllowed();

   boolean isUnlockForcedAllowed();

   boolean isPrivate();

   boolean isEditAllowed(int var1);

   boolean isEditForAllAllowed(boolean var1);

   boolean isPrivateModeMandatory();

   boolean isSharedModeMandatory();

   int getInfo3();

   int getInfo4();
}
