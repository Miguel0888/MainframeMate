package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.EAttachSessionType;
import com.softwareag.naturalone.natural.pal.external.IPalTypeEnviron;
import com.softwareag.naturalone.natural.pal.external.PalTrace;
import java.io.IOException;

public final class PalTypeEnviron extends PalType implements IPalTypeEnviron {
   private static final long serialVersionUID = 1L;
   private int ndvClientVersion;
   private int natVersion;
   private int palVersion;
   private String opSys = "";
   private String sessionId = "";
   private int opsysVer;
   private String startupCommands = "";
   private int ndvVersion;
   private int tModel;
   private int logonCounter;
   private int flags;
   private int webVersion;
   private int ndvType;
   private static final String OPSYS_OS390 = "OS/390";
   private static final String OPSYS_UNIX = "UNIX";
   private static final String OPSYS_WIN = "PC";
   private static final String OPSYS_VMS = "VMS";
   private static final int PAL_VERSION = 47;

   public PalTypeEnviron() {
   }

   public PalTypeEnviron(int var1) {
      super.type = 0;
      this.natVersion = 8380;
      this.opSys = System.getProperty("os.name");
      String[] var2 = System.getProperty("os.version").split("\\.");
      if (var2 != null) {
         String var3 = "";

         for(int var4 = 0; var4 < var2.length; ++var4) {
            try {
               Integer.valueOf(var2[var4]);
               var3 = var3 + var2[var4];
            } catch (NumberFormatException var6) {
               try {
                  PalTrace.text("os.version problem:" + var2[var4]);
               } catch (IOException var5) {
               }
            }
         }

         this.opsysVer = Integer.valueOf(var3);
      }

      this.logonCounter = var1;
   }

   public void restore() {
      this.natVersion = this.intFromBuffer();
      this.palVersion = this.intFromBuffer();
      this.opSys = this.stringFromBuffer();
      this.ndvType = 1;
      if (this.opSys.compareTo("OS/390") == 0) {
         this.ndvType = 1;
      } else if (this.opSys.compareTo("UNIX") == 0) {
         this.ndvType = 2;
      } else if (this.opSys.compareTo("PC") == 0) {
         this.ndvType = 3;
      } else if (this.opSys.compareTo("VMS") == 0) {
         this.ndvType = 4;
      }

      this.sessionId = this.stringFromBuffer();
      this.opsysVer = this.intFromBuffer();
      if (super.recordTail < super.recordLength) {
         this.startupCommands = this.stringFromBuffer();
      }

      if (super.recordTail < super.recordLength) {
         this.ndvVersion = this.intFromBuffer();
      }

      if (super.recordTail < super.recordLength) {
         this.tModel = this.intFromBuffer();
      }

      if (super.recordTail < super.recordLength) {
         this.logonCounter = this.intFromBuffer();
      }

      if (super.recordTail < super.recordLength) {
         this.flags = this.intFromBuffer();
      }

      if (super.recordTail < super.recordLength) {
         this.webVersion = this.intFromBuffer();
      }

   }

   public void serialize() {
      try {
         this.intToBuffer(this.natVersion);
         this.intToBuffer(47);
         this.stringToBuffer(this.opSys);
         this.stringToBuffer(this.sessionId);
         this.intToBuffer(this.opsysVer);
         this.stringToBuffer(this.startupCommands);
         this.intToBuffer(this.ndvVersion);
         this.intToBuffer(this.tModel);
         this.intToBuffer(this.logonCounter);
         this.intToBuffer(this.flags);
         this.intToBuffer(this.webVersion);
         this.intToBuffer(this.ndvClientVersion);
      } catch (PalTypeEnviron$ParseException var1) {
      }
   }

   public String getSessionId() {
      return this.sessionId;
   }

   public int getLogonCounter() {
      return this.logonCounter;
   }

   public String getStartupCommands() {
      return this.startupCommands;
   }

   public int getNatVersion() {
      return this.natVersion;
   }

   public int getNdvVersion() {
      return this.ndvVersion;
   }

   public int getPalVersion() {
      return this.palVersion;
   }

   public int getNdvType() {
      return this.ndvType;
   }

   public boolean isMfUnicodeSrcPossible() {
      try {
         return (this.flags & 8) == 8;
      } catch (PalTypeEnviron$ParseException var1) {
         return false;
      }
   }

   public boolean isWebIOServer() {
      return (this.flags & 4) == 4;
   }

   public boolean performsTimeStampChecks() {
      return (this.flags & 256) == 256;
   }

   public void setRichGui(boolean var1) {
      if (var1) {
         this.flags |= 16;
      }

   }

   public void setNdvClientClientId(int var1) {
      try {
         this.flags |= var1;
      } catch (PalTypeEnviron$ParseException var2) {
      }
   }

   public void setNdvClientClientVersion(int var1) {
      try {
         this.ndvClientVersion = var1;
      } catch (PalTypeEnviron$ParseException var2) {
      }
   }

   public void setWebBrowserIO(boolean var1) {
      if (var1) {
         this.flags |= 4;
      }

   }

   public int getWebVersion() {
      return this.webVersion;
   }

   public void setWebVersion(int var1) {
      try {
         this.webVersion = var1;
      } catch (PalTypeEnviron$ParseException var2) {
      }
   }

   public void setNfnPrivateMode(boolean var1) {
      if (var1) {
         this.flags |= 64;
      }

   }

   public void setTimeStampChecks(boolean var1) {
      if (var1) {
         this.flags |= 256;
      }

   }

   public EAttachSessionType getAttachSessionType() {
      EAttachSessionType var1 = EAttachSessionType.NDV;
      if ((this.flags & 2048) == 2048) {
         var1 = EAttachSessionType.NJX;
      } else if ((this.flags & 512) == 512) {
         var1 = EAttachSessionType.RPC;
      } else if ((this.flags & 1024) == 1024) {
         var1 = EAttachSessionType.NAT;
      }

      return var1;
   }
}
