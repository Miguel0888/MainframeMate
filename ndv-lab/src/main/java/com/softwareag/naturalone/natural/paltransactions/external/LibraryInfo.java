package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.pal.external.IPalTypeCmdGuard;
import com.softwareag.naturalone.natural.pal.external.IPalTypeLibId;
import com.softwareag.naturalone.natural.pal.external.IPalTypeLibrary;

public class LibraryInfo implements ILibraryInfo {
   private String name;
   EPrivatePrefixType prefixType;
   private String privatePrefix;
   private IPalTypeLibId[] stepLibs;
   private IPalTypeCmdGuard cmdGuard;

   public EPrivatePrefixType getPrivatePrefixType() {
      return null;
   }

   public String getPrivatePrefix() {
      return this.privatePrefix;
   }

   public IPalTypeLibId[] getStepLibs() {
      return this.stepLibs;
   }

   public IPalTypeCmdGuard getCmdGuard() {
      return this.cmdGuard;
   }

   private LibraryInfo(Builder var1) {
      this.name = null;
      this.prefixType = EPrivatePrefixType.UNDEFINED;
      this.privatePrefix = null;
      this.stepLibs = null;
      this.cmdGuard = null;
      this.name = var1.name;
      this.stepLibs = var1.stepLibs;
      if (var1.privatePrefix != null) {
         this.privatePrefix = var1.privatePrefix.getLibrary();
         int var2 = var1.privatePrefix.getFlags();
         if (var2 == 1) {
            this.prefixType = EPrivatePrefixType.UNDEFINED;
         } else if (var2 == 2) {
            this.prefixType = EPrivatePrefixType.PROJECT;
         } else if (var2 == 3) {
            this.prefixType = EPrivatePrefixType.LIBRARY;
         } else if (var2 == 4) {
            this.prefixType = EPrivatePrefixType.USER;
         } else if (var2 == 5) {
            this.prefixType = EPrivatePrefixType.CUSTOM;
         }
      }

      if (var1.cmdGuard != null) {
         this.cmdGuard = var1.cmdGuard;
      }

   }

   // $FF: synthetic method
   LibraryInfo(Builder var1, LibraryInfo var2) {
      this(var1);
   }

   public static class Builder {
      private String name = null;
      private IPalTypeLibrary privatePrefix = null;
      private IPalTypeLibId[] stepLibs = null;
      private IPalTypeCmdGuard cmdGuard = null;

      public Builder(String var1) {
         this.name = var1;
      }

      public Builder cmdGuard(IPalTypeCmdGuard[] var1) {
         try {
            if (var1 != null && var1.length > 0) {
               this.cmdGuard = var1[0];
            }

            return this;
         } catch (LibraryInfo$NullPointerException var2) {
            return null;
         }
      }

      public Builder prefix(IPalTypeLibrary[] var1) {
         if (var1 != null && var1.length > 0) {
            this.privatePrefix = var1[0];
         }

         return this;
      }

      public LibraryInfo build() {
         try {
            return new LibraryInfo(this, (LibraryInfo)null);
         } catch (LibraryInfo$NullPointerException var1) {
            return null;
         }
      }
   }
}
