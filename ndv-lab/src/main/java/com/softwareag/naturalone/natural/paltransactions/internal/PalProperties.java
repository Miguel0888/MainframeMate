package com.softwareag.naturalone.natural.paltransactions.internal;

import com.softwareag.naturalone.natural.pal.external.EAttachSessionType;
import com.softwareag.naturalone.natural.paltransactions.external.IPalProperties;
import java.io.Serializable;

public class PalProperties implements Serializable, IPalProperties {
   private static final long serialVersionUID = 1L;
   private int ndvType;
   private int ndvVersion;
   private int natVersion;
   private int palVersion;
   private int webioVersion;
   private String ndvSessionId;
   private boolean isMfUnicodeSrcPossible;
   private boolean isWebIOServer;
   private int logonCounter;
   private String defaultCodePage;
   private boolean isDevEnv;
   private String pathDevEnv;
   private String hostName;
   private boolean timeStampCheck;
   private String logonLibrary = "";
   private EAttachSessionType attachType;

   public PalProperties() {
   }

   public String getDefaultCodePage() {
      return this.defaultCodePage;
   }

   public boolean equals(Object var1) {
      if (var1 == this) {
         return true;
      } else if (!(var1 instanceof PalProperties)) {
         return false;
      } else {
         PalProperties var2 = (PalProperties)var1;
         return this.ndvType == var2.ndvType && this.ndvVersion == var2.ndvVersion && this.natVersion == var2.natVersion && this.palVersion == var2.palVersion && this.ndvSessionId.equals(var2.ndvSessionId);
      }
   }

   public int hashCode() {
      try {
         int var1 = 17;
         var1 = 37 * var1 + this.ndvType;
         var1 = 37 * var1 + this.ndvVersion;
         var1 = 37 * var1 + this.natVersion;
         var1 = 37 * var1 + this.palVersion;
         var1 = 37 * var1 + this.ndvSessionId.hashCode();
         return var1;
      } catch (PalProperties$ParseException var2) {
         return 0;
      }
   }

   public String toString() {
      try {
         return "NdvType=" + this.ndvType + ", NdvVersion=" + this.ndvVersion + ", NatVersion=" + this.natVersion + ", PalVersion=" + this.palVersion + ", NdvSessionId=" + this.ndvSessionId + ", Session type=" + this.attachType;
      } catch (PalProperties$ParseException var1) {
         return null;
      }
   }

   public PalProperties(int var1, int var2, int var3, int var4, String var5, boolean var6, boolean var7, int var8, int var9, String var10, boolean var11, String var12, String var13, boolean var14, String var15, EAttachSessionType var16) {
      this.ndvType = var1;
      this.ndvVersion = var2;
      this.natVersion = var3;
      this.palVersion = var4;
      this.ndvSessionId = var5;
      this.isMfUnicodeSrcPossible = var6;
      this.isWebIOServer = var7;
      this.webioVersion = var8;
      this.logonCounter = var9;
      this.defaultCodePage = var10;
      this.isDevEnv = var11;
      this.pathDevEnv = var12;
      this.hostName = var13;
      this.timeStampCheck = var14;
      this.logonLibrary = var15;
      this.attachType = var16;
   }

   public final int getNatVersion() {
      return this.natVersion;
   }

   public final int getNdvType() {
      return this.ndvType;
   }

   public final String getNdvTypeString() {
      try {
         String var1 = "";
         switch (this.getNdvType()) {
            case 1:
               var1 = "Mainframe";
               break;
            case 2:
               var1 = "UNIX";
               break;
            case 3:
               var1 = "Windows";
               break;
            case 4:
               var1 = "OpenVMS";
         }

         return var1;
      } catch (PalProperties$ParseException var2) {
         return null;
      }
   }

   public final int getNdvVersion() {
      return this.ndvVersion;
   }

   public final int getPalVersion() {
      return this.palVersion;
   }

   public final String getNdvSessionId() {
      return this.ndvSessionId;
   }

   public final boolean isMfUnicodeSrcPossible() {
      return this.isMfUnicodeSrcPossible;
   }

   public final boolean isWebIOServer() {
      return this.isWebIOServer;
   }

   public final int getWebioVersion() {
      return this.webioVersion;
   }

   public final int getLogonCounter() {
      return this.logonCounter;
   }

   public final boolean isDevEnv() {
      return this.isDevEnv;
   }

   public final String getDevEnvPath() {
      return this.pathDevEnv;
   }

   public final String getHostName() {
      return this.hostName;
   }

   public final boolean timeStampCheck() {
      return this.timeStampCheck;
   }

   public String getLogonLibrary() {
      return this.logonLibrary;
   }

   public EAttachSessionType getAttachSessionType() {
      return this.attachType;
   }
}
