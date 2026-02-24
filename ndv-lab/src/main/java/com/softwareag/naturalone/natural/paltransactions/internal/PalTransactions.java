package com.softwareag.naturalone.natural.paltransactions.internal;

import com.softwareag.naturalone.natural.auxiliary.renumber.internal.IInsertLabels;
import com.softwareag.naturalone.natural.auxiliary.renumber.internal.RenumberSource;
import com.softwareag.naturalone.natural.pal.ICUCharsetCoder;
import com.softwareag.naturalone.natural.pal.IPalType;
import com.softwareag.naturalone.natural.pal.Pal;
import com.softwareag.naturalone.natural.pal.PalTypeCP;
import com.softwareag.naturalone.natural.pal.PalTypeClientConfig;
import com.softwareag.naturalone.natural.pal.PalTypeCmdGuard;
import com.softwareag.naturalone.natural.pal.PalTypeConnect;
import com.softwareag.naturalone.natural.pal.PalTypeDbgNatStack;
import com.softwareag.naturalone.natural.pal.PalTypeDbgSpy;
import com.softwareag.naturalone.natural.pal.PalTypeDbgStackFrame;
import com.softwareag.naturalone.natural.pal.PalTypeDbgStatus;
import com.softwareag.naturalone.natural.pal.PalTypeDbgSyt;
import com.softwareag.naturalone.natural.pal.PalTypeDbgVarValue;
import com.softwareag.naturalone.natural.pal.PalTypeDbgaRecord;
import com.softwareag.naturalone.natural.pal.PalTypeEnviron;
import com.softwareag.naturalone.natural.pal.PalTypeFileId;
import com.softwareag.naturalone.natural.pal.PalTypeGeneric;
import com.softwareag.naturalone.natural.pal.PalTypeLibId;
import com.softwareag.naturalone.natural.pal.PalTypeLibrary;
import com.softwareag.naturalone.natural.pal.PalTypeLibraryStatistics;
import com.softwareag.naturalone.natural.pal.PalTypeMonitorInfo;
import com.softwareag.naturalone.natural.pal.PalTypeNatParm;
import com.softwareag.naturalone.natural.pal.PalTypeNotify;
import com.softwareag.naturalone.natural.pal.PalTypeObjDesc;
import com.softwareag.naturalone.natural.pal.PalTypeObjDesc2;
import com.softwareag.naturalone.natural.pal.PalTypeObject;
import com.softwareag.naturalone.natural.pal.PalTypeOperation;
import com.softwareag.naturalone.natural.pal.PalTypeSQLAuthentification;
import com.softwareag.naturalone.natural.pal.PalTypeSource;
import com.softwareag.naturalone.natural.pal.PalTypeSourceCP;
import com.softwareag.naturalone.natural.pal.PalTypeSourceCodePage;
import com.softwareag.naturalone.natural.pal.PalTypeSourceUnicode;
import com.softwareag.naturalone.natural.pal.PalTypeSrcDesc;
import com.softwareag.naturalone.natural.pal.PalTypeStack;
import com.softwareag.naturalone.natural.pal.PalTypeStream;
import com.softwareag.naturalone.natural.pal.PalTypeSysVar;
import com.softwareag.naturalone.natural.pal.PalTypeSystemFile;
import com.softwareag.naturalone.natural.pal.PalTypeTimeStamp;
import com.softwareag.naturalone.natural.pal.PalTypeUtility;
import com.softwareag.naturalone.natural.pal.PalUnmappableCodePointException;
import com.softwareag.naturalone.natural.pal.external.EAttachSessionType;
import com.softwareag.naturalone.natural.pal.external.IBuffSize;
import com.softwareag.naturalone.natural.pal.external.ICharAssign;
import com.softwareag.naturalone.natural.pal.external.ICompOpt;
import com.softwareag.naturalone.natural.pal.external.IErr;
import com.softwareag.naturalone.natural.pal.external.IFileProperties;
import com.softwareag.naturalone.natural.pal.external.IFldApp;
import com.softwareag.naturalone.natural.pal.external.ILimit;
import com.softwareag.naturalone.natural.pal.external.IPalTimeoutHandler;
import com.softwareag.naturalone.natural.pal.external.IPalTypeCP;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgSpy;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgStackFrame;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgVarContainer;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgVarDesc;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgVarValue;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgaRecord;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbmsInfo;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDevEnv;
import com.softwareag.naturalone.natural.pal.external.IPalTypeEnviron;
import com.softwareag.naturalone.natural.pal.external.IPalTypeFileId;
import com.softwareag.naturalone.natural.pal.external.IPalTypeGeneric;
import com.softwareag.naturalone.natural.pal.external.IPalTypeLibId;
import com.softwareag.naturalone.natural.pal.external.IPalTypeLibraryStatistics;
import com.softwareag.naturalone.natural.pal.external.IPalTypeNatParm;
import com.softwareag.naturalone.natural.pal.external.IPalTypeNotify;
import com.softwareag.naturalone.natural.pal.external.IPalTypeObject;
import com.softwareag.naturalone.natural.pal.external.IPalTypeResult;
import com.softwareag.naturalone.natural.pal.external.IPalTypeResultEx;
import com.softwareag.naturalone.natural.pal.external.IPalTypeSource;
import com.softwareag.naturalone.natural.pal.external.IPalTypeSrcDesc;
import com.softwareag.naturalone.natural.pal.external.IPalTypeStream;
import com.softwareag.naturalone.natural.pal.external.IPalTypeSysVar;
import com.softwareag.naturalone.natural.pal.external.IPalTypeSystemFile;
import com.softwareag.naturalone.natural.pal.external.IPalTypeTimeStamp;
import com.softwareag.naturalone.natural.pal.external.IPalTypeUtility;
import com.softwareag.naturalone.natural.pal.external.IRegional;
import com.softwareag.naturalone.natural.pal.external.IReport;
import com.softwareag.naturalone.natural.pal.external.IRpc;
import com.softwareag.naturalone.natural.pal.external.ObjectType;
import com.softwareag.naturalone.natural.pal.external.PalTimeoutException;
import com.softwareag.naturalone.natural.pal.external.PalTrace;
import com.softwareag.naturalone.natural.pal.external.PalTypeLibIdFactory;
import com.softwareag.naturalone.natural.pal.external.PalTypeSystemFileFactory;
import com.softwareag.naturalone.natural.paltransactions.external.EDownLoadOption;
import com.softwareag.naturalone.natural.paltransactions.external.EFileOptions;
import com.softwareag.naturalone.natural.paltransactions.external.ELibraryStatisticsOption;
import com.softwareag.naturalone.natural.paltransactions.external.EObjectKind;
import com.softwareag.naturalone.natural.paltransactions.external.EReadOption;
import com.softwareag.naturalone.natural.paltransactions.external.EStepLibFormat;
import com.softwareag.naturalone.natural.paltransactions.external.EUploadOption;
import com.softwareag.naturalone.natural.paltransactions.external.IDebugAttachWaitCallBack;
import com.softwareag.naturalone.natural.paltransactions.external.IDownloadResult;
import com.softwareag.naturalone.natural.paltransactions.external.ILibraryInfo;
import com.softwareag.naturalone.natural.paltransactions.external.INatParm;
import com.softwareag.naturalone.natural.paltransactions.external.IPalArabicShaping;
import com.softwareag.naturalone.natural.paltransactions.external.IPalClientIdentification;
import com.softwareag.naturalone.natural.paltransactions.external.IPalExecutionContext;
import com.softwareag.naturalone.natural.paltransactions.external.IPalPreferences;
import com.softwareag.naturalone.natural.paltransactions.external.IPalProperties;
import com.softwareag.naturalone.natural.paltransactions.external.IPalSQLIdentification;
import com.softwareag.naturalone.natural.paltransactions.external.IPalTransactions;
import com.softwareag.naturalone.natural.paltransactions.external.IPalTypeSQLAuthentification;
import com.softwareag.naturalone.natural.paltransactions.external.IServerConfiguration;
import com.softwareag.naturalone.natural.paltransactions.external.ISourceLookupResult;
import com.softwareag.naturalone.natural.paltransactions.external.ISuspendResult;
import com.softwareag.naturalone.natural.paltransactions.external.ITransactionContext;
import com.softwareag.naturalone.natural.paltransactions.external.ITransactionContextDownload;
import com.softwareag.naturalone.natural.paltransactions.external.InvalidSourceException;
import com.softwareag.naturalone.natural.paltransactions.external.LibraryInfo;
import com.softwareag.naturalone.natural.paltransactions.external.More;
import com.softwareag.naturalone.natural.paltransactions.external.ObjectProperties;
import com.softwareag.naturalone.natural.paltransactions.external.PalCompileResultException;
import com.softwareag.naturalone.natural.paltransactions.external.PalConnectResultException;
import com.softwareag.naturalone.natural.paltransactions.external.PalResultException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PalTransactions implements IPalTransactions {
   private boolean isAutomaticLogon;
   private boolean isConnected;
   private boolean isdisconnected;
   private IPalTypeCP[] codePages;
   private PalProperties palProperties;
   private boolean isNotifyActive;
   private Pal pal;
   private IServerConfiguration serverConfiguration;
   private PalTypeClientConfig[] clientConfig;
   private IPalTypeSysVar[] systemVariables;
   private IPalTypeDbmsInfo[] dbmsInfo;
   private int currentNotify;
   private int errorKind;
   private boolean isUtilityCallActive;
   private String internalLabelPrefix;
   private static final int NONERETRIEVAL = 0;
   private static final int LIBRETRIEVAL = 1;
   private static final int OBJRETRIEVAL = 2;
   private int retrievalKind;
   private boolean isDuplicatePossible;
   private Set serverList;
   private INatParm naturalParameters;
   private ITransactionContext transactionContext;
   private static final int ADDTOLINESIZE = 4;
   private static final int MAX_LINENO = 9999;
   private static final PalTypeSystemFile[] NULL_SYSTEMFILE_ARRAY;
   private static final PalTypeLibrary[] NULL_LIBRARY_ARRAY;
   private static final IPalTypeObject[] NULL_OBJECT_ARRAY;
   private static final String[] NULL_STRING_ARRAY;
   private static final PalTypeLibId[] NULL_LIBID_ARRAY;
   private static final PalTypeSysVar[] NULL_SYSVAR_ARRAY;
   private static final IPalTypeLibraryStatistics[] NULL_LIBSTAT_ARRAY;
   private static final IPalTypeNatParm[] NULL_NATPARM_ARRAY;
   private static final IPalTypeCP[] NULL_CP_ARRAY;
   private static String emptyLine;
   private static String blankLine;
   private static String separatorLine;
   private static String textLine;
   private static String explanationLine;
   private static String actionLine;
   private static String textLineLead;
   private static int errorMessageLines;
   private static int longTextEndLineNumber;
   private static int explanationEndLineNumber;
   private static final int LONGTEXT_SECT = 0;
   private static final int EXPLANATION_SECT = 1;
   private static final int ACTION_SECT = 2;
   private static final int BINARY_SIZE_RECORD = 253;
   private IPalClientIdentification identification;
   private IPalSQLIdentification palSQLIdentification;
   private IPalExecutionContext executionContext;
   private IPalArabicShaping shapingContext;
   private IPalPreferences palPreferences;
   private IPalTimeoutHandler timeoutHandler;
   private String host;
   private String port;

   static {
      NULL_SYSTEMFILE_ARRAY = new PalTypeSystemFile[0];
         NULL_LIBRARY_ARRAY = new PalTypeLibrary[0];
         NULL_OBJECT_ARRAY = new IPalTypeObject[0];
         NULL_STRING_ARRAY = new String[0];
         NULL_LIBID_ARRAY = new PalTypeLibId[0];
         NULL_SYSVAR_ARRAY = new PalTypeSysVar[0];
         NULL_LIBSTAT_ARRAY = new IPalTypeLibraryStatistics[0];
         NULL_NATPARM_ARRAY = new IPalTypeNatParm[0];
         NULL_CP_ARRAY = new IPalTypeCP[0];
         emptyLine = ".";
         blankLine = "";
         separatorLine = "#-------------------------------------------------------------------------------";
         textLine = "#TEXT:";
         explanationLine = "#EXPL:";
         actionLine = "#ACTN:";
         textLineLead = "#";
         errorMessageLines = 25;
         longTextEndLineNumber = 4;
         explanationEndLineNumber = 18;
   }

   public PalTransactions() {
      this.isAutomaticLogon = true;
      this.isConnected = false;
      this.codePages = null;
      this.isNotifyActive = false;
      this.internalLabelPrefix = null;
      this.serverList = new TreeSet();
      this.shapingContext = null;
      this.palPreferences = null;
      this.host = "";
      this.port = "";
      this.currentNotify = 0;
      this.retrievalKind = 0;
      this.pal = null;
   }

   public PalTransactions(IPalClientIdentification var1) {
      this();
      this.identification = var1;
   }

   public PalTransactions(IPalClientIdentification var1, IPalSQLIdentification var2) {
      this(var1);
      this.setPalSQLIdentification(var2);
   }

   public PalTransactions(IPalClientIdentification var1, IPalPreferences var2) {
      this(var1);
      this.setPalPreferences(var2);
   }

   public void abortFileOperation(Set var1) throws IOException, PalResultException {
      try {
         if (this.pal == null) {
            throw new IllegalStateException("connection to ndv server not available");
         } else {
            PalTrace.header("abortFileOperation");
            this.fileOperationAbort(var1);
         }
      } catch (PalTransactions$Exception var2) {
      }
   }

   public void catalog(IPalTypeSystemFile var1, String var2, IFileProperties var3, String[] var4) throws IOException, PalResultException, PalCompileResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else {
         PalTrace.header("catalog");
         this.handleCommand(var1, this.getLibrary(var1, var2), var3.getBaseLibrary(), var3.getType() == 8 ? var3.getLongName() : var3.getName(), var3.getType(), "CAT", var3.isStructured(), true, var4, 0, (String)null, var3.getDatbaseId(), var3.getFnr(), false, var3.getTimeStamp(), var3.isLinkedDdm());
      }
   }

   public void check(IPalTypeSystemFile var1, String var2, IFileProperties var3, String[] var4) throws IOException, PalResultException, PalCompileResultException {
      try {
         if (this.pal == null) {
            throw new IllegalStateException("connection to ndv server not available");
         } else {
            PalTrace.header("check");
            this.handleCommand(var1, this.getLibrary(var1, var2), var3.getBaseLibrary(), var3.getType() == 8 ? var3.getLongName() : var3.getName(), var3.getType(), "CHECK", var3.isStructured(), true, var4, var3.getLineNumberIncrement(), (String)null, 0, 0, false, var3.getTimeStamp(), var3.isLinkedDdm());
         }
      } catch (PalTransactions$Exception var5) {
      }
   }

   public void close() throws IOException, PalResultException {
      try {
         this.intClose(0);
      } catch (PalTransactions$Exception var1) {
      }
   }

   public void close(int var1) throws IOException, PalResultException {
      try {
         this.intClose(var1);
      } catch (PalTransactions$Exception var2) {
      }
   }

   private void intClose(int var1) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else {
         PalTrace.header("close");
         if (this.isConnected()) {
            PalTypeOperation var2 = new PalTypeOperation(20, var1);
            this.pal.add((IPalType)var2);
            this.pal.commit();
            this.pal.closeSocket();
            this.pal = null;
            this.setConnected(false);
         }

      }
   }

   public More connect(Map var1) throws IOException, UnknownHostException, ConnectException, PalConnectResultException {
      More var2 = null;
      PalConnectResultException var3 = null;
      this.host = (String)var1.get("host");
      this.port = (String)var1.get("port");
      String var4 = (String)var1.get("user id");
      String var5 = (String)var1.get("password");
      String var6 = (String)var1.get("session parameters");
      String var7 = (String)var1.get("internal session parameters");
      String var8 = (String)var1.get("new password");
      String var9 = (String)var1.get("rich gui");
      String var10 = (String)var1.get("webio version");
      String var11 = (String)var1.get("nfn private mode");
      String var12 = (String)var1.get("logon counter");
      String var13 = (String)var1.get("monitor session ID");
      String var14 = (String)var1.get("monitor event filter");
      if (this.pal != null) {
         throw new IllegalStateException("connection already established");
      } else if (this.host == null) {
         throw new IllegalArgumentException("HOST value must not be null");
      } else if (this.port == null) {
         throw new IllegalArgumentException("PORT value must not be null");
      } else if (var4 == null) {
         throw new IllegalArgumentException("USERID value must not be null");
      } else {
         if (var5 == null) {
            var5 = "";
         }

         if (var6 == null) {
            var6 = "";
         }

         if (var5.length() > 8) {
            throw new IllegalArgumentException("password must not exceed 8 characters");
         } else {
            if (var8 == null) {
               var8 = "";
            }

            if (var12 == null) {
               var12 = "0";
            }

            boolean var15 = false;
            if (var11 == null) {
               var11 = "false";
            }

            if (var11.equalsIgnoreCase("true")) {
               var15 = true;
            }

            boolean var16 = false;
            if (var9 == null) {
               var9 = "false";
            }

            if (var9.equalsIgnoreCase("true")) {
               var16 = true;
            }

            if (var10 == null) {
               if (this.identification != null) {
                  var10 = Integer.valueOf(this.identification.getWebIOVersion()).toString();
               } else {
                  var10 = "0";
               }
            }

            int var17 = 0;
            int var18 = 0;
            int var19 = 0;
            int var20 = 0;
            int var21 = 0;
            int var22 = 0;
            String var23 = "";
            boolean var24 = false;
            boolean var25 = false;
            boolean var26 = false;
            boolean var27 = false;
            String var28 = "";
            String var29 = "";
            String var30 = "";
            String var31 = "";
            EAttachSessionType var32 = EAttachSessionType.NDV;

            try {
               PalTrace.header("connect");
               this.pal = new Pal(this.palPreferences != null ? this.palPreferences.getTimeOut() : 0, this.timeoutHandler);
               this.pal.connect(this.host, this.port);
               PalTypeOperation var33 = new PalTypeOperation(18);
               this.pal.setUserId(var4);
               var33.setUserId(var4);
               this.pal.add((IPalType)var33);
               String var34 = PassWord.encode(var4, var5, "", var8);
               if (var7 != null) {
                  var6 = String.format("%s %s", var7, var6);
               }

               PalTypeConnect var35 = new PalTypeConnect(var4, var34, var6.trim());
               this.pal.add((IPalType)var35);
               PalTypeEnviron var36 = new PalTypeEnviron(Integer.valueOf(var12));
               var36.setRichGui(var16);
               var36.setWebVersion(Integer.valueOf(var10));
               if (this.palPreferences != null && this.palPreferences.checkTimeStamp()) {
                  var36.setTimeStampChecks(true);
               }

               var36.setWebBrowserIO(true);
               var36.setNfnPrivateMode(var15);
               if (this.getIdentification() != null) {
                  var36.setNdvClientClientId(this.getIdentification().getNdvClientId());
                  var36.setNdvClientClientVersion(this.getIdentification().getNdvClientVersion());
               }

               this.pal.add((IPalType)var36);
               PalTypeCP var37 = new PalTypeCP(Charset.defaultCharset().displayName());
               this.pal.add((IPalType)var37);
               if (var13 != null) {
                  PalTypeMonitorInfo var38 = new PalTypeMonitorInfo(var13);
                  if (var14 != null) {
                     var38.setEventFilter(var14);
                  }

                  this.pal.add((IPalType)var38);
               }

               this.pal.commit();
               byte var52 = 0;
               int var39 = this.getError();
               if (var39 != 0) {
                  this.palProperties = null;
                  String var40 = this.getErrorText();
                  String[] var41 = this.getErrorTextLong();
                  if (this.errorKind == 1) {
                     var40 = this.removeLeadingLength(var40);
                  }

                  switch (var39) {
                     case 829:
                        var52 = 3;
                        break;
                     case 838:
                        var52 = 2;
                        break;
                     case 855:
                        var52 = 5;
                        break;
                     case 873:
                        var52 = 1;
                        break;
                     case 876:
                        var52 = 4;
                  }

                  String var42 = this.getDetailMessage(var41, var40);
                  var3 = new PalConnectResultException(var39, var42, this.errorKind, var52);
                  var3.setLongText(var41);
                  var3.setShortText(var40);
               }

               IPalTypeEnviron[] var53 = (IPalTypeEnviron[])this.pal.retrieve(0);
               IPalTypeStream[] var54 = (IPalTypeStream[])this.pal.retrieve(13);
               if (var54 != null) {
                  throw new PalConnectResultException(9999, "invalid I/O performed on server", 3, 0);
               }

               if (var53 != null) {
                  var17 = var53[0].getNdvType();
                  var2 = new More();
                  var2.setCommands(var53[0].getStartupCommands());
                  if (var17 == 1 && var54 == null) {
                     var2 = null;
                  }

                  var21 = var53[0].getWebVersion();
                  var19 = var53[0].getNatVersion();
                  var18 = var53[0].getNdvVersion();
                  var20 = var53[0].getPalVersion();
                  var23 = var53[0].getSessionId();
                  var24 = var53[0].isMfUnicodeSrcPossible();
                  var25 = var53[0].isWebIOServer();
                  var22 = var53[0].getLogonCounter();
                  var27 = var53[0].performsTimeStampChecks();
                  var32 = var53[0].getAttachSessionType();
                  this.pal.setNdvType(var17);
                  this.pal.setPalVersion(var20);
                  this.pal.setSessionId(var23);
               }

               IPalTypeDevEnv[] var55 = (IPalTypeDevEnv[])this.pal.retrieve(52);
               if (var55 != null) {
                  var26 = var55[0].isDevEnv();
                  var29 = var55[0].getDevEnvPath();
                  var30 = var55[0].getHostName();
               }

               int var43 = this.errorKind;
               if (this.errorKind == 0 || this.errorKind == 1) {
                  this.getServerConfig(true);
                  this.getCodePages();
                  var31 = this.getLogonLib(var17, var53[0].getStartupCommands());
                  if (this.naturalParameters != null && this.naturalParameters.getRegional() != null) {
                     var28 = this.naturalParameters.getRegional().getCodePage().trim();
                  }
               }

               this.createPalProperties(var17, var18, var19, var20, var23, var24, var25, var21, var22, var28, var26, var29, var30, var27, var31, var32);
               if (var43 != 0) {
                  if (var43 != 1) {
                     this.pal = null;
                  } else {
                     this.setConnected(true);
                  }

                  throw var3;
               }
            } catch (PalConnectResultException var44) {
               throw var44;
            } catch (PalResultException var45) {
               if (var45.isWarning()) {
                  this.createPalProperties(var17, var18, var19, var20, var23, var24, var25, var21, var22, var28, var26, var29, var30, var27, var31, var32);
                  this.setConnected(true);
               } else {
                  this.pal = null;
               }

               throw new PalConnectResultException(var45.getErrorNumber(), var45.getMessage(), var45.getErrorKind(), 0);
            } catch (IllegalStateException var46) {
               this.pal = null;
               throw var46;
            } catch (IllegalArgumentException var47) {
               this.pal = null;
               throw var47;
            } catch (UnknownHostException var48) {
               this.pal = null;
               throw var48;
            } catch (ConnectException var49) {
               this.pal = null;
               throw var49;
            } catch (PalTimeoutException var50) {
               this.pal = null;
               throw var50;
            } catch (IOException var51) {
               this.pal = null;
               throw var51;
            }

            this.setConnected(true);
            return var2;
         }
      }
   }

   public void copy(IPalTypeSystemFile var1, String var2, String var3, IPalTypeSystemFile var4, String var5, int var6, int var7) throws IOException, PalResultException {
      try {
         PalTypeObject var8 = new PalTypeObject();
         var8.setKind(var6);
         var8.setType(var7);
         this.copy(var1, var2, var3, var4, var5, var8);
      } catch (PalTransactions$Exception var9) {
      }
   }

   public void copy(IPalTypeSystemFile var1, String var2, String var3, IPalTypeSystemFile var4, String var5, IPalTypeObject var6) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var2 != null && var5 != null) {
         if (var3 == null) {
            throw new IllegalArgumentException("objectName must not be null");
         } else {
            try {
               PalTrace.header("copy");
               PalTypeFileId var7 = new PalTypeFileId();
               var7.setNatKind(var6.getKind());
               var7.setNatType(var6.getType());
               var7.setObject(var3);
               var7.setNewObject(var3);
               var7.setSourceSize(var6.getSourceSize());
               var7.setUser(var6.getSourceUser());
               var7.setSourceDate(var6.getSourceDate());
               var7.setGpSize(var6.getGpSize());
               var7.setGpUser(var6.getGpUser());
               var7.setGpDate(var6.getGpDate());
               this.fileOperationServerLocal(40, var1, var2, var4, var5, var7);
            } catch (PalTimeoutException var8) {
               throw var8;
            }
         }
      } else {
         throw new IllegalArgumentException("library must not be null");
      }
   }

   public Object createTransactionContext(Class var1) {
      if (this.getTransactionContext() != null) {
         throw new IllegalStateException("The transaction context cannot be created since there is still another transaction context in use");
      } else if (ITransactionContextDownload.class.equals(var1)) {
         this.setTransactionContext(new ContextDownload((ContextDownload)null));
         return this.getTransactionContext();
      } else {
         throw new IllegalStateException("The transaction context " + var1.getCanonicalName() + " is illegal");
      }
   }

   public ISuspendResult dasDebugStart() throws IOException, PalResultException {
      try {
         PalTrace.header("dasDebugStart");
         this.pal.add((IPalType)(new PalTypeOperation(83)));
         this.pal.commit();
         ISuspendResult var1 = this.getSuspendResult();
         PalResultException var2 = var1.getException();
         if (var2 != null) {
            throw var2;
         } else {
            return var1;
         }
      } catch (PalTransactions$Exception var3) {
         return null;
      }
   }

   public ISuspendResult debugStart(String var1, String var2, String var3) throws IOException, PalResultException {
      ISuspendResult var4 = null;

      try {
         PalTrace.header("debug");
         String var5 = "RDEBUGON " + var2 + " " + var3;
         String var6;
         if (this.getPalProperties().getNdvType() == 1) {
            var6 = "TEST " + var5;
         } else {
            var6 = "DEBUG " + var5;
         }

         this.pal.add((IPalType)(new PalTypeOperation(2, 7)));
         this.pal.add((IPalType)(new PalTypeStack(var6)));
         this.pal.commit();
         var4 = this.getSuspendResult();
         PalResultException var7 = var4.getException();
         if (var7 != null) {
            throw var7;
         } else {
            return var4;
         }
      } catch (PalTimeoutException var8) {
         throw var8;
      }
   }

   public ISuspendResult debugStepInto() throws IOException {
      try {
         PalTrace.header("debugStepInto");
         this.pal.add((IPalType)(new PalTypeOperation(62, 1)));
         this.pal.commit();
      } catch (PalTimeoutException var2) {
         throw var2;
      }

      return this.getSuspendResult();
   }

   public ISuspendResult debugStepOver() throws IOException {
      try {
         PalTrace.header("debugStepOver");
         this.pal.add((IPalType)(new PalTypeOperation(62, 2)));
         this.pal.commit();
      } catch (PalTimeoutException var2) {
         throw var2;
      }

      return this.getSuspendResult();
   }

   public ISuspendResult debugStepReturn() throws IOException {
      try {
         PalTrace.header("debugStepReturn");
         this.pal.add((IPalType)(new PalTypeOperation(62, 3)));
         this.pal.commit();
      } catch (PalTimeoutException var2) {
         throw var2;
      }

      return this.getSuspendResult();
   }

   public ISuspendResult debugResume() throws IOException {
      try {
         PalTrace.header("debugResume");
         this.pal.add((IPalType)(new PalTypeOperation(62, 4)));
         this.pal.commit();
      } catch (PalTimeoutException var2) {
         throw var2;
      }

      return this.getSuspendResult();
   }

   public void debugExit() throws IOException, PalResultException {
      try {
         PalTrace.header("debugExit");
         this.pal.add((IPalType)(new PalTypeOperation(61)));
         this.pal.commit();
         PalResultException var1 = this.getResultException();
         if (var1 != null) {
            throw var1;
         }
      } catch (PalTimeoutException var2) {
         throw var2;
      }
   }

   public void delete(IPalTypeSystemFile var1, String var2, IFileProperties var3) throws IOException, PalResultException {
      try {
         if (this.pal == null) {
            throw new IllegalStateException("connection to ndv server not available");
         } else {
            int var4 = this.palProperties.getNdvType();
            if (var3.getType() == 8 && var3.getKind() != 1 && var3.getKind() != 3 && var4 == 1) {
               throw new IllegalArgumentException("kind  must be ObjectKind.SOURCE for Ndv Mainframe servers");
            } else if (var3.getKind() != 2 && var3.getKind() != 1 && var3.getKind() != 3 && var3.getKind() != 64 && var3.getKind() != 16) {
               throw new IllegalArgumentException("kind  must be ObjectKind.SOURCE orObjectKind.GP or ObjectKind.SOURCE_OR_GP or ObjectKind.ERRORMSG ");
            } else if (var2 == null) {
               throw new IllegalArgumentException("library must not be null");
            } else {
               PalTrace.header("delete");
               this.fileOperationDelete(var1, var2, var3.getType(), var3.getKind(), var3.getName(), var3.getBaseLibrary());
            }
         }
      } catch (PalTransactions$Exception var5) {
      }
   }

   public void delete1(IPalTypeSystemFile var1, String var2, int var3, int var4, String var5) throws IOException, PalResultException {
      try {
         this.delete2(var1, var2, var3, var4, var5, (String)null);
      } catch (PalTransactions$Exception var6) {
      }
   }

   public void delete2(IPalTypeSystemFile var1, String var2, int var3, int var4, String var5, String var6) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var2 == null) {
         throw new IllegalArgumentException("library must not be null");
      } else if (!ObjectType.getInstanceIdExtension().containsKey(var3)) {
         throw new IllegalArgumentException("type must be one of the ids defined inside utility class 'sag.pal.ObjectType'");
      } else {
         try {
            PalTrace.header("delete");
            this.fileOperationDelete(var1, var2, var3, var4, var5, var6);
         } catch (PalTimeoutException var8) {
            throw var8;
         }

      }
   }

   public void delete(IPalTypeSystemFile var1, int var2, String var3) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else {
         int var4 = this.palProperties.getNdvType();
         if (var2 != 1 && var4 == 1) {
            throw new IllegalArgumentException("kind  must be ObjectKind.SOURCE for Ndv Mainframe servers");
         } else if (var2 != 2 && var2 != 1 && var2 != 3) {
            throw new IllegalArgumentException("kind  must be ObjectKind.SOURCE orObjectKind.GP or ObjectKind.SOURCE_OR_GP");
         } else {
            try {
               PalTrace.header("delete");
               this.fileOperationDelete(var1, "", 8, var2, var3, (String)null);
            } catch (PalTimeoutException var6) {
               throw var6;
            }
         }
      }
   }

   public void disconnect() throws IOException {
      try {
         this.isdisconnected = true;
         this.setConnected(false);
         this.pal.disconnect();
      } catch (PalTransactions$Exception var1) {
      }
   }

   public void reconnect() throws IOException, PalResultException {
      if (this.isMainframe()) {
         try {
            this.isdisconnected = false;
            this.pal.connect(this.host, this.port);
            this.setConnected(true);
            this.reinitSession();
         } catch (UnknownHostException var2) {
            var2.printStackTrace();
         } catch (IOException var3) {
            throw var3;
         }
      }

   }

   public void disposeTransactionContext(ITransactionContext var1) {
      if (var1 != this.getTransactionContext()) {
         throw new IllegalStateException("The transaction context is not active");
      } else {
         if (var1 instanceof ITransactionContextDownload) {
            ContextDownload var2 = (ContextDownload)var1;
            if (var2.isStarted() && !var2.isTerminated()) {
               try {
                  this.fileOperationAbort(var2.getInitOptions());
               } catch (IOException var4) {
                  throw new IllegalStateException("connecion broken", var4);
               } catch (PalResultException var5) {
                  throw new IllegalStateException(var5.getMessage(), var5);
               }
            }
         }

         this.setTransactionContext((ITransactionContext)null);
      }
   }

   public IDownloadResult downloadSource(ITransactionContext var1, IPalTypeSystemFile var2, String var3, IFileProperties var4, Set var5) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var2 == null) {
         throw new IllegalArgumentException("systemFileKey must not be null");
      } else if (var3 == null) {
         throw new IllegalArgumentException("library must not be null");
      } else if (var4 == null) {
         throw new IllegalArgumentException("properties must not be null");
      } else {
         ITransactionContextDownload var6 = null;
         if (var1 instanceof ITransactionContextDownload) {
            var6 = (ITransactionContextDownload)var1;
         }

         IDownloadResult var7 = null;

         try {
            PalTrace.header("downloadSource");
            var7 = this.fileOperationDownloadSource(var6, var2, this.getLibrary(var2, var3), var4, var5);
            return var7;
         } catch (InvalidSourceException var9) {
            throw var9;
         }
      }
   }

   /** @deprecated */
   public ByteArrayOutputStream downloadBinary(IPalTypeSystemFile var1, String var2, String var3, int var4) throws IOException, PalResultException {
      try {
         return this.downloadBinary(var1, var2, (new ObjectProperties.Builder(var3, var4)).longName(var3).build());
      } catch (PalTransactions$Exception var5) {
         return null;
      }
   }

   /** @deprecated */
   public ByteArrayOutputStream downloadBinary(IPalTypeSystemFile var1, String var2, String var3, String var4, int var5) throws IOException, PalResultException {
      try {
         return this.downloadBinary(var1, var2, (new ObjectProperties.Builder(var3, var5)).longName(var4).build());
      } catch (PalTransactions$Exception var6) {
         return null;
      }
   }

   public ByteArrayOutputStream downloadBinary(ITransactionContext var1, IPalTypeSystemFile var2, String var3, IFileProperties var4) throws IOException, PalResultException {
      try {
         if (this.pal == null) {
            throw new IllegalStateException("connection to ndv server not available");
         } else if (var2 == null) {
            throw new IllegalArgumentException("systemFileKey must not be null");
         } else if (var3 == null) {
            throw new IllegalArgumentException("library must not be null");
         } else if (var4.getName() == null) {
            throw new IllegalArgumentException("sourceName must not be null");
         } else {
            Object var5 = null;
            if (var1 instanceof ITransactionContextDownload) {
               ITransactionContextDownload var8 = (ITransactionContextDownload)var1;
               Object var6 = null;
               PalTrace.header("downloadBinary");
               ByteArrayOutputStream var9 = this.fileOperationDownloadBinary(var8, var2, var3, var4);
               return var9;
            } else {
               throw new IllegalStateException("the download context is illegal");
            }
         }
      } catch (PalTransactions$Exception var7) {
         return null;
      }
   }

   public ByteArrayOutputStream downloadBinary(IPalTypeSystemFile var1, String var2, IFileProperties var3) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var1 == null) {
         throw new IllegalArgumentException("systemFileKey must not be null");
      } else if (var2 == null) {
         throw new IllegalArgumentException("library must not be null");
      } else if (var3.getName() == null) {
         throw new IllegalArgumentException("sourceName must not be null");
      } else {
         Object var4 = null;
         PalTrace.header("downloadBinary");
         ByteArrayOutputStream var5 = this.fileOperationDownloadBinary((ITransactionContext)null, var1, var2, var3);
         return var5;
      }
   }

   public ISuspendResult execute(String var1) throws IOException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var1 == null) {
         throw new IllegalArgumentException("program must not be null");
      } else {
         try {
            PalTrace.header("execute");
            this.pal.add((IPalType)(new PalTypeOperation(2, 6)));
            this.pal.add((IPalType)(new PalTypeStack("EXECUTE " + var1)));
            this.pal.commit();
         } catch (PalTimeoutException var3) {
            throw var3;
         }

         return this.getSuspendResult();
      }
   }

   public void executeWithoutIO(String var1) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var1 == null) {
         throw new IllegalArgumentException("program must not be null");
      } else {
         try {
            PalTrace.header("executeWithoutIO");
            this.pal.add((IPalType)(new PalTypeOperation(2, 6)));
            this.pal.add((IPalType)(new PalTypeStack(var1)));
            this.pal.commit();
            IPalTypeStream[] var2 = (IPalTypeStream[])this.pal.retrieve(13);
            if (var2 != null) {
               throw new IllegalStateException("the program displays data which  cannot be processed");
            } else {
               PalResultException var3 = this.getResultException();
               if (var3 != null) {
                  throw var3;
               }
            }
         } catch (PalTimeoutException var4) {
            throw var4;
         }
      }
   }

   public IPalTypeObject exists(IPalTypeSystemFile var1, String var2, String var3, int var4) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var1 == null) {
         throw new IllegalArgumentException("systemFileKey must not be null");
      } else if (var2 == null) {
         throw new IllegalArgumentException("library must not be null");
      } else if (var3 == null) {
         throw new IllegalArgumentException("file must not be null");
      } else if (var3.length() == 0) {
         throw new IllegalArgumentException("file parameter must not be empty");
      } else if (var2.length() == 0) {
         throw new IllegalArgumentException("library parameter must not be empty");
      } else {
         IPalTypeObject var5 = null;

         try {
            PalTrace.header("exists");
            int var6 = 0;
            IPalTypeObject[] var7 = null;
            if ((var4 & 2) == 2) {
               var6 |= 2;
            }

            if ((var4 & 1) == 1) {
               var6 |= 1;
            }

            if (var6 != 0) {
               var7 = this.objectsFirst(3, var1, var2, var3, var6, 131072, 3 | (this.isOpenSystemsServer() ? 32 : 16));
               if (var7 != null && var7.length > 1) {
                  throw new IllegalArgumentException("more than one file found. The file parameter must not contain filter characters");
               }
            }

            if (var7 == null) {
               var6 = 0;
               if ((var4 & 16) == 16) {
                  var6 |= 16;
               }

               if (var6 != 0 && this.isOpenSystemsServer()) {
                  var7 = this.objectsFirst(3, var1, var2, var3, var6, 0, 3);
               }

               if (var7 == null) {
                  var6 = 0;
                  if ((var4 & 64) == 64) {
                     var6 |= 64;
                  }

                  if (var6 != 0) {
                     String var8 = var3;
                     if (ObjectType.getUnmodifiableLanguageList().contains(var3)) {
                        if (this.isOpenSystemsServer()) {
                           var3 = "*";
                        }

                        var7 = this.objectsFirst(3, var1, var2, var3, var6, 0, 3);
                        if (var7 != null) {
                           boolean var9 = false;

                           for(int var10 = 0; var10 < var7.length; ++var10) {
                              if (var7[var10].getLongName().compareToIgnoreCase(var8) == 0) {
                                 var5 = var7[var10];
                                 var9 = true;
                                 break;
                              }
                           }

                           if (!var9) {
                              var7 = null;
                           }
                        }
                     }
                  }
               } else {
                  var5 = var7[0];
               }
            } else {
               var5 = var7[0];
            }
         } finally {
            this.currentNotify = 0;
         }

         return var5;
      }
   }

   public IPalTypeObject exists(IPalTypeSystemFile var1, String var2, int var3) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var1 == null) {
         throw new IllegalArgumentException("systemFileKey must not be null");
      } else if (var2 == null) {
         throw new IllegalArgumentException("file must not be null");
      } else if (var2.length() == 0) {
         throw new IllegalArgumentException("file parameter must not be empty");
      } else {
         Object var4 = null;
         IPalTypeObject[] var10 = null;

         try {
            PalTrace.header("exists");
            int var5 = this.palProperties.getNdvType();
            if (var5 == 1) {
               var3 = 0;
            } else if (this.isOpenSystemsServer() && var3 != 2 && var3 != 1 && var3 != 3) {
               throw new IllegalArgumentException("invalid kind");
            }

            int var6 = this.isOpenSystemsServer() ? 3 : 23;
            var10 = this.objectsFirst(var6, var1, "", var2, var3, 8, 3 | (this.isOpenSystemsServer() ? 32 : 16));
         } finally {
            this.currentNotify = 0;
         }

         return var10 != null ? var10[0] : null;
      }
   }

   public String[] generateAdabasDdm(int var1, int var2, String var3) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var3 == null) {
         throw new IllegalStateException("password must not be null");
      } else {
         Object var4 = null;
         IDownloadResult var9 = null;

         try {
            String var5 = String.format("GEN A %d %d %s", var1, var2, var3);
            var9 = this.genDdm(var5);
         } catch (PalTimeoutException var6) {
            throw var6;
         } catch (IOException var7) {
            throw var7;
         } catch (PalResultException var8) {
            throw var8;
         }

         return var9.getSource();
      }
   }

   public String[] generateSqlDdm(int var1, int var2, String var3, String var4) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else {
         Object var5 = null;
         IDownloadResult var10 = null;

         try {
            String var6 = String.format("GEN 2 %d %d %s %s Y", var1, var2, var3, var4);
            var10 = this.genDdm(var6);
         } catch (PalTimeoutException var7) {
            throw var7;
         } catch (IOException var8) {
            throw var8;
         } catch (PalResultException var9) {
            throw var9;
         }

         return var10.getSource();
      }
   }

   public String[] generateXmlDdm(int var1, int var2, String var3, String var4, String var5, String var6) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else {
         Object var7 = null;
         IDownloadResult var12 = null;

         try {
            String var8 = String.format("GEN I %d %d %s %s %s %s", var1, var2, var3, var4, var5, var6);
            var12 = this.genDdm(var8);
         } catch (PalTimeoutException var9) {
            throw var9;
         } catch (IOException var10) {
            throw var10;
         } catch (PalResultException var11) {
            throw var11;
         }

         return var12.getSource();
      }
   }

   public PalTypeCmdGuard getCmdGuardInfo(int var1, IPalTypeSystemFile var2, String var3) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else {
         Object var4 = null;
         PalTypeCmdGuard[] var8 = null;

         try {
            PalTrace.header("getCmdGuardInfo");
            this.pal.add((IPalType)(new PalTypeOperation(56, var1)));
            PalTypeLibId var5 = new PalTypeLibId(var2.getDatabaseId(), var2.getFileNumber(), var3, var2.getPassword(), var2.getCipher(), 6);
            this.pal.add((IPalType)var5);
            this.pal.commit();
            PalResultException var6 = this.getResultException();
            if (var6 != null) {
               throw var6;
            }

            var8 = (PalTypeCmdGuard[])this.pal.retrieve(27);
         } catch (PalTimeoutException var7) {
            throw var7;
         }

         return var8 != null ? var8[0] : null;
      }
   }

   public IPalTypeCP[] getCodePages() throws IOException, PalResultException {
      try {
         if (this.codePages == null) {
            if (this.pal == null) {
               throw new IllegalStateException("connection to Ndv server not available");
            }

            PalTrace.header("getCodePages");
            PalTypeOperation var1 = new PalTypeOperation(71);
            this.pal.add((IPalType)var1);
            this.pal.commit();
            PalResultException var2 = this.getResultException();
            if (var2 != null) {
               throw var2;
            }

            this.codePages = (IPalTypeCP[])this.pal.retrieve(45);
         }

         return this.codePages == null ? NULL_CP_ARRAY : (IPalTypeCP[])this.codePages.clone();
      } catch (PalTransactions$Exception var3) {
         return null;
      }
   }

   public IPalClientIdentification getIdentification() {
      return this.identification;
   }

   public PalTypeLibrary[] getLibrariesFirst(IPalTypeSystemFile var1, String var2) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var2 == null) {
         throw new IllegalArgumentException("filter must not be null");
      } else if (var1 == null) {
         throw new IllegalArgumentException("systemFileKey must not be null");
      } else if (var2.length() == 0) {
         throw new IllegalArgumentException("filter parameter must not be empty");
      } else {
         Object var3 = null;
         PalTrace.header("getLibrariesFirst");
         this.retrievalKind = 1;
         this.isDuplicatePossible = this.duplicatesPossible(var2);
         PalTypeOperation var4 = new PalTypeOperation(4);
         this.pal.add((IPalType)var4);
         PalTypeLibId var5 = new PalTypeLibId(var1.getDatabaseId(), var1.getFileNumber(), var2, var1.getPassword(), var1.getCipher(), 30);
         this.pal.add((IPalType)var5);
         PalTypeNotify var6 = new PalTypeNotify(17);
         this.pal.add((IPalType)var6);
         this.pal.commit();
         PalResultException var7 = this.getResultException();
         if (var7 != null) {
            throw var7;
         } else {
            IPalTypeNotify[] var8 = (IPalTypeNotify[])this.pal.retrieve(19);
            IPalTypeGeneric[] var9 = (IPalTypeGeneric[])this.pal.retrieve(20);
            int var10 = -1;
            boolean var11 = false;
            if (var9 != null && var8 != null) {
               var10 = var9[0].getData();
               var11 = var10 > 0;
            }

            PalTypeLibrary[] var12 = this.nextLibrariesChunk(var11);
            if (var12 == null) {
               this.currentNotify = 0;
            }

            return var12 == null ? NULL_LIBRARY_ARRAY : var12;
         }
      }
   }

   public PalTypeLibrary[] getLibrariesNext() throws IOException, PalResultException {
      PalTypeLibrary[] var1 = null;
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (this.currentNotify == 0) {
         throw new IllegalStateException("getLibrariesNext cannot be used without a getLibrariesFirst call");
      } else if (this.retrievalKind == 2) {
         throw new IllegalStateException("getLibrariesFirst must be called first");
      } else {
         PalTrace.header("getLibrariesNext");
         if (this.currentNotify != 7) {
            do {
               var1 = this.nextLibrariesChunk(true);
            } while(var1 != null && var1.length == 0);
         }

         if (var1 == null) {
            this.currentNotify = 0;
         }

         return var1 == null ? NULL_LIBRARY_ARRAY : var1;
      }
   }

   public IPalTypeLibraryStatistics getLibraryStatistics(Set var1, IPalTypeSystemFile var2, String var3) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var2 == null) {
         throw new IllegalArgumentException("systemFileKey must not be null");
      } else if (var3 == null) {
         throw new IllegalArgumentException("library must not be null");
      } else if (var3.length() == 0) {
         throw new IllegalArgumentException("library parameter must not be empty");
      } else {
         Object var4 = null;
         PalTrace.header("getLibraryStatistics");
         int var5 = var1.contains(ELibraryStatisticsOption.REBUILD_STATISTICS_RECORD) ? 5 : 3;
         if (var1.contains(ELibraryStatisticsOption.GET_LINKED_DDMS)) {
            var5 |= 16;
         }

         PalTypeOperation var6 = new PalTypeOperation(5, var5);
         this.pal.add((IPalType)var6);
         PalTypeLibId var7 = new PalTypeLibId(var2.getDatabaseId(), var2.getFileNumber(), var3, var2.getPassword(), var2.getCipher(), 30);
         this.pal.add((IPalType)var7);
         this.pal.commit();
         IPalTypeNotify[] var8 = (IPalTypeNotify[])this.pal.retrieve(19);
         IPalTypeGeneric[] var9 = (IPalTypeGeneric[])this.pal.retrieve(20);
         boolean var10 = false;
         if (var9 != null && var8 != null) {
            var10 = true;
         }

         PalTypeLibraryStatistics[] var11 = this.nextLibrariesStatChunk(var10);
         this.currentNotify = 0;
         return var11 != null ? var11[0] : null;
      }
   }

   private PalTypeLibraryStatistics[] nextLibrariesStatChunk(boolean var1) throws IOException, PalResultException {
      PalTypeLibraryStatistics[] var2 = null;

      try {
         if (var1) {
            this.currentNotify = this.notifyContinue();
         }

         PalResultException var3 = this.getResultException();
         if (var3 != null) {
            throw var3;
         }

         var2 = (PalTypeLibraryStatistics[])this.pal.retrieve(4);
      } catch (PalTimeoutException var4) {
         throw var4;
      }

      return var2;
   }

   public String getLogonLibrary() throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else {
         Object var1 = null;
         IPalTypeLibId[] var4 = null;

         try {
            PalTrace.header("getLogonLibrary");
            this.pal.add((IPalType)(new PalTypeOperation(45)));
            this.pal.commit();
            PalResultException var2 = this.getResultException();
            if (var2 != null) {
               throw var2;
            }

            var4 = (IPalTypeLibId[])this.pal.retrieve(6);
            if (var4 == null) {
               throw new IllegalStateException("Fatal:Ndv server did not deliver the Logon library");
            }
         } catch (PalTimeoutException var3) {
            throw var3;
         }

         return var4[0].getLibrary();
      }
   }

   public int getNumberOfLibraries(IPalTypeSystemFile var1, String var2) throws IOException, PalResultException {
      try {
         int var3 = -1;
         if (this.pal == null) {
            throw new IllegalStateException("connection to ndv server not available");
         } else if (var2 == null) {
            throw new IllegalArgumentException("filter must not be null");
         } else if (var1 == null) {
            throw new IllegalArgumentException("systemFileKey must not be null");
         } else if (var2.length() == 0) {
            throw new IllegalArgumentException("filter parameter must not be empty");
         } else {
            PalTrace.header("getNumberOfLibraries");
            this.retrievalKind = 1;
            PalTypeOperation var4 = new PalTypeOperation(4);
            this.pal.add((IPalType)var4);
            PalTypeLibId var5 = new PalTypeLibId(var1.getDatabaseId(), var1.getFileNumber(), var2, var1.getPassword(), var1.getCipher(), 30);
            this.pal.add((IPalType)var5);
            PalTypeNotify var6 = new PalTypeNotify(17);
            this.pal.add((IPalType)var6);
            this.pal.commit();
            PalResultException var7 = this.getResultException();
            if (var7 != null) {
               throw var7;
            } else {
               IPalTypeNotify[] var8 = (IPalTypeNotify[])this.pal.retrieve(19);
               IPalTypeGeneric[] var9 = (IPalTypeGeneric[])this.pal.retrieve(20);
               this.isNotifyActive = false;
               if (var9 != null && var8 != null) {
                  var3 = var9[0].getData();
                  this.isNotifyActive = var3 > 0;
               }

               this.nextLibrariesChunk(this.isNotifyActive);
               this.terminateRetrieval();
               return var3;
            }
         }
      } catch (PalTransactions$Exception var10) {
         return 0;
      }
   }

   public int getNumberOfObjects(IPalTypeSystemFile var1, String var2, String var3, int var4, int var5) throws IOException, PalResultException {
      int var6 = -1;
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var4 < 0) {
         throw new IllegalArgumentException("kind must be one of the ids defined inside utility class 'sag.pal.ObjectKind'");
      } else if (var5 != 131072 && var5 != 0 && !ObjectType.getInstanceIdExtension().containsKey(var5)) {
         throw new IllegalArgumentException("type must be one of the ids defined inside utility class 'sag.pal.ObjectType'");
      } else if (var4 == 64 && var5 != 0) {
         throw new IllegalArgumentException("kind 'ObjectKind.ERRMSG' only allowed inconjunction with type 'ObjectType.NONE'");
      } else if (var1 == null) {
         throw new IllegalArgumentException("systemFileKey must not be null");
      } else if (var2 == null) {
         throw new IllegalArgumentException("library must not be null");
      } else if (var3 == null) {
         throw new IllegalArgumentException("filter must not be null");
      } else if (var3.length() == 0) {
         throw new IllegalArgumentException("filter parameter must not be empty");
      } else if (var2.length() == 0) {
         throw new IllegalArgumentException("library parameter must not be empty");
      } else {
         try {
            PalTrace.header("getNumberOfObjects");
            var6 = this.numberOfObjects(3, var1, var2, var3, var4, var5);
            if (var6 == -1) {
               var6 = 0;
            } else {
               this.nextObjectsChunk(this.isNotifyActive);
            }

            this.terminateRetrieval();
            return var6;
         } catch (PalTimeoutException var8) {
            throw var8;
         }
      }
   }

   public int getNumberOfObjects(IPalTypeSystemFile var1, String var2, int var3) throws IOException, PalResultException {
      int var4 = -1;
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var1 == null) {
         throw new IllegalArgumentException("systemFileKey must not be null");
      } else if (var2 == null) {
         throw new IllegalArgumentException("filter must not be null");
      } else if (var2.length() == 0) {
         throw new IllegalArgumentException("filter parameter must not be empty");
      } else {
         PalTrace.header("getNumberOfObjects");
         int var5 = this.palProperties.getNdvType();
         if (var3 != 1 && var5 == 1) {
            throw new IllegalArgumentException("kind  must be ObjectKind.SOURCE for Ndv Mainframe servers");
         } else if (var3 != 2 && var3 != 3 && var3 != 1) {
            throw new IllegalArgumentException("kind  must be ObjectKind.GP or ObjectKind.SOURCE_OR_GP or ObjectKind.SOURCE");
         } else {
            int var6 = this.isOpenSystemsServer() ? 3 : 23;
            var4 = this.numberOfObjects(var6, var1, "", var2, var3, 8);
            if (var4 == -1) {
               var4 = 0;
            } else {
               this.nextObjectsChunk(this.isNotifyActive);
            }

            this.terminateRetrieval();
            return var4;
         }
      }
   }

   public INatParm getNaturalParameters() {
      return this.naturalParameters;
   }

   public IPalTypeLibId getLibraryOfObject(IPalTypeLibId var1, String var2, Set var3) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var1 == null) {
         throw new IllegalArgumentException("libId parameter must not be null");
      } else if (var2 == null) {
         throw new IllegalArgumentException("objectName must not be null");
      } else if (var2.length() == 0) {
         throw new IllegalArgumentException("objectName parameter must not be empty");
      } else {
         PalTrace.header("getLibraryOfObject");
         IPalTypeLibId var4 = null;

         try {
            byte var5 = 0;
            if (var3.contains(EObjectKind.SOURCE) && var3.contains(EObjectKind.GP)) {
               var5 = 3;
            } else if (var3.contains(EObjectKind.SOURCE)) {
               var5 = 1;
            } else if (var3.contains(EObjectKind.GP)) {
               var5 = 2;
            }

            byte var6 = 7;
            IPalTypeSystemFile var7 = PalTypeSystemFileFactory.newInstance(var1.getDatabaseId(), var1.getFileNumber(), 0);
            IPalTypeObject[] var8 = this.objectsFirst(3, var7, var1.getLibrary(), var2, var5, 131072, var6);
            PalResultException var9 = this.getResultException();
            if (var9 != null) {
               throw var9;
            }

            if (var8 != null) {
               IPalTypeLibId[] var10 = (IPalTypeLibId[])this.pal.retrieve(6);
               if (var10 != null) {
                  var4 = var10[0];
                  if (var4.getDatabaseId() == 0 && var4.getFileNumber() == 0) {
                     var4.setDatabaseId(var1.getDatabaseId());
                     var4.setFileNumber(var1.getFileNumber());
                  }
               } else {
                  var4 = var1;
               }
            }
         } finally {
            this.currentNotify = 0;
         }

         return var4;
      }
   }

   public ISourceLookupResult getObjectByLongName(IPalTypeSystemFile var1, String var2, String var3, int var4, boolean var5) throws IOException, PalResultException {
      try {
         return this.getObjectByName(var1, var2, var3, var4, var5, true);
      } catch (PalTransactions$Exception var6) {
         return null;
      }
   }

   public ISourceLookupResult getObjectByName(IPalTypeSystemFile var1, String var2, String var3, int var4, boolean var5) throws IOException, PalResultException {
      try {
         return this.getObjectByName(var1, var2, var3, var4, var5, false);
      } catch (PalTransactions$Exception var6) {
         return null;
      }
   }

   private ISourceLookupResult getObjectByName(IPalTypeSystemFile var1, String var2, String var3, int var4, boolean var5, boolean var6) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var1 == null) {
         throw new IllegalArgumentException("systemFileKey must not be null");
      } else if (var2 == null) {
         throw new IllegalArgumentException("library must not be null");
      } else if (var3 == null) {
         throw new IllegalArgumentException("name must not be null");
      } else if (var3.length() == 0) {
         throw new IllegalArgumentException("name parameter must not be empty");
      } else if (var2.length() == 0) {
         throw new IllegalArgumentException("library parameter must not be empty");
      } else if (var6 && var4 != 524288 && var4 != 8 && var4 != 256 && var4 != 1024) {
         throw new IllegalArgumentException("type must be one of the typesFUNCTION, DDM, SUBROUTINE or CLASS as defined inside utility class 'sag.pal.ObjectType'");
      } else {
         Object var7 = null;
         Object var8 = null;
         PalTrace.header(var6 ? "getObjectsByLongName" : "getObjectsByName");
         if (var2.length() > 0 && this.isAutomaticLogon()) {
            this.logon(var2);
         }

         int var9 = 3 | (var6 ? 8 : 0);
         if (var5) {
            var9 |= 4;
         }

         String var10 = null;
         int var11 = -1;
         int var12 = -1;
         IPalTypeObject var18 = null;

         try {
            IPalTypeObject[] var17 = this.objectsFirst(3, var1, var2, var3, 3, var4, var9);
            if (var17 == null) {
               throw new PalResultException(82, 2, "");
            }

            var18 = var17[0];
            IPalTypeLibId[] var13 = (IPalTypeLibId[])this.pal.retrieve(6);
            if (var13 != null) {
               var10 = var13[0].getLibrary();
               var11 = var13[0].getDatabaseId();
               var12 = var13[0].getFileNumber();
            }
         } finally {
            this.currentNotify = 0;
         }

         return new SourceLookupResult(var18, var10, var11, var12, (SourceLookupResult)null);
      }
   }

   public IPalTypeObject[] getObjectsFirst(IPalTypeSystemFile var1, String var2, String var3, int var4, int var5) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var4 < 0) {
         throw new IllegalArgumentException("kind must be one of the ids defined inside utility class 'sag.pal.ObjectKind'");
      } else if (var5 != 131072 && var5 != 0 && !ObjectType.getInstanceIdExtension().containsKey(var5)) {
         throw new IllegalArgumentException("type must be one of the ids defined inside utility class 'sag.pal.ObjectType'");
      } else if (var4 == 64 && var5 != 0) {
         throw new IllegalArgumentException("kind 'ObjectKind.ERRMSG' only allowed inconjunction with type 'ObjectType.NONE'");
      } else if (var5 == 8 && var4 != 2 && var4 != 3 && var4 != 1) {
         throw new IllegalArgumentException("kind  must be ObjectKind.GP or ObjectKind.SOURCE_OR_GP or ObjectKind.SOURCE");
      } else if (var1 == null) {
         throw new IllegalArgumentException("systemFileKey must not be null");
      } else if (var2 == null) {
         throw new IllegalArgumentException("library must not be null");
      } else if (var3 == null) {
         throw new IllegalArgumentException("filter must not be null");
      } else if (var3.length() == 0) {
         throw new IllegalArgumentException("filter parameter must not be empty");
      } else if (var2.length() == 0 && var5 != 8) {
         throw new IllegalArgumentException("library parameter must not be empty");
      } else {
         Object var6 = null;
         PalTrace.header("getObjectsFirst");
         this.isDuplicatePossible = this.duplicatesPossible(var3);
         byte var7 = 3;
         int var8 = 3;
         String var9 = var2;
         if (var5 == 8) {
            if (var1.getKind() == 6) {
               var9 = "";
               if (!this.isOpenSystemsServer()) {
                  var7 = 23;
               }
            } else {
               var8 |= 16;
            }
         }

         IPalTypeObject[] var10 = this.objectsFirst(var7, var1, var9, var3, var4, var5, var8);
         return var10 == null ? NULL_OBJECT_ARRAY : var10;
      }
   }

   public IPalTypeObject[] getObjectsFirst(IPalTypeSystemFile var1, String var2, int var3) throws IOException, PalResultException {
      try {
         return this.getObjectsFirst(var1, "", var2, var3, 8);
      } catch (PalTransactions$Exception var4) {
         return null;
      }
   }

   public IPalTypeObject[] getObjectsNext() throws IOException, PalResultException {
      IPalTypeObject[] var1 = null;
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (this.currentNotify == 0) {
         throw new IllegalStateException("getObjectsFirst must be called first");
      } else if (this.retrievalKind == 1) {
         throw new IllegalStateException("getObjectsNext cannot be used without a getObjectsFirst call");
      } else {
         PalTrace.header("getObjectsNext");
         if (this.currentNotify != 7) {
            var1 = this.nextObjectsChunk(true);
         }

         if (var1 == null) {
            this.currentNotify = 0;
         }

         return var1 == null ? NULL_OBJECT_ARRAY : var1;
      }
   }

   public IServerConfiguration getServerConfiguration(boolean var1) throws IOException, PalResultException {
      try {
         if (var1) {
            this.getServerConfig(false);
            this.serverConfiguration = null;
         }

         if (this.naturalParameters == null) {
            throw new NullPointerException("server data (natparm) not available. Possible reason: 'connect' method not called or failed");
         } else {
            if (this.serverConfiguration == null) {
               this.serverConfiguration = new IServerConfiguration() {
                  private boolean isPrivateMode = false;

                  private IPalTypeDbmsInfo[] getDbmsInfo() {
                     try {
                        return PalTransactions.this.dbmsInfo != null ? PalTransactions.this.dbmsInfo : new IPalTypeDbmsInfo[0];
                     } catch (PalTransactions$Exception var1) {
                        return null;
                     }
                  }

                  private PalTypeClientConfig getClientConfig() {
                     try {
                        return PalTransactions.this.clientConfig != null ? PalTransactions.this.clientConfig[0] : new PalTypeClientConfig();
                     } catch (PalTransactions$Exception var1) {
                        return null;
                     }
                  }

                  public char[] geDdmNameValid1st() {
                     try {
                        return this.getClientConfig().getDdm1stValid().toCharArray();
                     } catch (PalTransactions$Exception var1) {
                        return null;
                     }
                  }

                  public char[] geDdmNameValidSubSequent() {
                     try {
                        return this.getClientConfig().getDdmSubValid().toCharArray();
                     } catch (PalTransactions$Exception var1) {
                        return null;
                     }
                  }

                  public char[] geIdentifiertValid1st() {
                     try {
                        return this.getClientConfig().getIdent1stValid().toCharArray();
                     } catch (PalTransactions$Exception var1) {
                        return null;
                     }
                  }

                  public char[] geIdentifiertValidSubSequent() {
                     try {
                        return this.getClientConfig().getIdentSubsequentValid().toCharArray();
                     } catch (PalTransactions$Exception var1) {
                        return null;
                     }
                  }

                  public char[] geLibraryNameValid1st() {
                     try {
                        return this.getClientConfig().getLib1stValid().toCharArray();
                     } catch (PalTransactions$Exception var1) {
                        return null;
                     }
                  }

                  public char[] geLibraryNameValidSubSequent() {
                     try {
                        return this.getClientConfig().getLibSubValid().toCharArray();
                     } catch (PalTransactions$Exception var1) {
                        return null;
                     }
                  }

                  public char[] geObjectNameValid1st() {
                     try {
                        return this.getClientConfig().getObject1stValid().toCharArray();
                     } catch (PalTransactions$Exception var1) {
                        return null;
                     }
                  }

                  public char[] geObjectNameValidSubSequent() {
                     try {
                        return this.getClientConfig().getObjectSubValid().toCharArray();
                     } catch (PalTransactions$Exception var1) {
                        return null;
                     }
                  }

                  public char getAlternatCaretCharacter() {
                     try {
                        return this.getClientConfig().getAltCharet();
                     } catch (PalTransactions$Exception var1) {
                        return '\u0000';
                     }
                  }

                  public char getSqlSeparatorCharacter() {
                     try {
                        return this.getClientConfig().getSqlSep();
                     } catch (PalTransactions$Exception var1) {
                        return '\u0000';
                     }
                  }

                  public char getDynamicSourceCharacter() {
                     try {
                        return this.getClientConfig().getDynSrc();
                     } catch (PalTransactions$Exception var1) {
                        return '\u0000';
                     }
                  }

                  public char getGlobalVariableCharacter() {
                     try {
                        return this.getClientConfig().getGlobalVar();
                     } catch (PalTransactions$Exception var1) {
                        return '\u0000';
                     }
                  }

                  public char getNonDBFieldCharacter() {
                     try {
                        return this.getClientConfig().getNonDbField();
                     } catch (PalTransactions$Exception var1) {
                        return '\u0000';
                     }
                  }

                  public IPalTypeDbmsInfo[] getDbmsAssignments() {
                     return this.getDbmsInfo();
                  }

                  private char getCharFromByte(char var1, byte var2) {
                     byte[] var3 = new byte[]{var2};
                     char var4 = var1;

                     try {
                        String var5 = ICUCharsetCoder.decode(Charset.defaultCharset().displayName(), var3);
                        if (var5.length() == 1) {
                           var4 = var5.toCharArray()[0];
                        }
                     } catch (Exception var6) {
                     }

                     return var4;
                  }

                  private byte getByteFromChar(char var1) {
                     char[] var3 = new char[]{var1};

                     try {
                        byte[] var2 = ICUCharsetCoder.encode(Charset.defaultCharset().displayName(), new String(var3), true);
                        if (var2.length > 0) {
                           return var2[0];
                        }
                     } catch (Exception var4) {
                     }

                     return 32;
                  }

                  public char getDecimalChar() {
                     return PalTransactions.this.naturalParameters.getCharAssign() != null ? this.getCharFromByte('.', PalTransactions.this.naturalParameters.getCharAssign().getDecimalChar()) : '.';
                  }

                  public char getInputAssignment() {
                     return PalTransactions.this.naturalParameters.getCharAssign() != null ? this.getCharFromByte('\u0000', PalTransactions.this.naturalParameters.getCharAssign().getInputAssignment()) : '\u0000';
                  }

                  public char getInputDelimiter() {
                     try {
                        return PalTransactions.this.naturalParameters.getCharAssign() != null ? this.getCharFromByte(',', PalTransactions.this.naturalParameters.getCharAssign().getInputDelimiter()) : ',';
                     } catch (PalTransactions$Exception var1) {
                        return '\u0000';
                     }
                  }

                  public char getTermCommandChar() {
                     try {
                        return PalTransactions.this.naturalParameters.getCharAssign() != null ? this.getCharFromByte('\u0000', PalTransactions.this.naturalParameters.getCharAssign().getTermCommandChar()) : '\u0000';
                     } catch (PalTransactions$Exception var1) {
                        return '\u0000';
                     }
                  }

                  public char getThousandSeperator() {
                     return PalTransactions.this.naturalParameters.getCharAssign() != null ? this.getCharFromByte('\u0000', PalTransactions.this.naturalParameters.getCharAssign().getThousandSeperator()) : '\u0000';
                  }

                  public int getLineSize() {
                     try {
                        return PalTransactions.this.naturalParameters.getReport() != null ? PalTransactions.this.naturalParameters.getReport().getLineSize() : 0;
                     } catch (PalTransactions$Exception var1) {
                        return 0;
                     }
                  }

                  public int getPageSize() {
                     try {
                        return PalTransactions.this.naturalParameters.getReport() != null ? PalTransactions.this.naturalParameters.getReport().getPageSize() : 0;
                     } catch (PalTransactions$Exception var1) {
                        return 0;
                     }
                  }

                  public int getSpacingFactor() {
                     try {
                        return PalTransactions.this.naturalParameters.getReport() != null ? PalTransactions.this.naturalParameters.getReport().getSpacingFactor() : 0;
                     } catch (PalTransactions$Exception var1) {
                        return 0;
                     }
                  }

                  public void setDecimalChar(char var1) {
                     if (PalTransactions.this.naturalParameters.getCharAssign() != null) {
                        PalTransactions.this.naturalParameters.getCharAssign().setDecimalChar(this.getByteFromChar(var1));
                     }

                  }

                  public void setInputAssignment(char var1) {
                     if (PalTransactions.this.naturalParameters.getCharAssign() != null) {
                        PalTransactions.this.naturalParameters.getCharAssign().setInputAssignment(this.getByteFromChar(var1));
                     }

                  }

                  public void setInputDelimiter(char var1) {
                     if (PalTransactions.this.naturalParameters.getCharAssign() != null) {
                        PalTransactions.this.naturalParameters.getCharAssign().setInputDelimiter(this.getByteFromChar(var1));
                     }

                  }

                  public void setTermCommandChar(char var1) {
                     if (PalTransactions.this.naturalParameters.getCharAssign() != null) {
                        PalTransactions.this.naturalParameters.getCharAssign().setTermCommandChar(this.getByteFromChar(var1));
                     }

                  }

                  public void setThousandSeperator(char var1) {
                     if (PalTransactions.this.naturalParameters.getCharAssign() != null) {
                        PalTransactions.this.naturalParameters.getCharAssign().setThousandSeperator(this.getByteFromChar(var1));
                     }

                  }

                  public void setLineSize(int var1) {
                     if (PalTransactions.this.naturalParameters.getReport() != null) {
                        PalTransactions.this.naturalParameters.getReport().setLineSize(var1);
                     }

                  }

                  public void setPageSize(int var1) {
                     if (PalTransactions.this.naturalParameters.getReport() != null) {
                        PalTransactions.this.naturalParameters.getReport().setPageSize(var1);
                     }

                  }

                  public void setSpacingFactor(int var1) {
                     if (PalTransactions.this.naturalParameters.getReport() != null) {
                        PalTransactions.this.naturalParameters.getReport().setSpacingFactor(var1);
                     }

                  }

                  public boolean getDatabaseShortName() {
                     boolean var1 = true;
                     if (PalTransactions.this.naturalParameters.getCompOpt() != null) {
                        var1 = (PalTransactions.this.naturalParameters.getCompOpt().getFlags() & 64) == 64;
                     }

                     return var1;
                  }

                  public boolean getDynamicThousandsSeparator() {
                     boolean var1 = false;
                     if (PalTransactions.this.naturalParameters.getCompOpt() != null) {
                        var1 = (PalTransactions.this.naturalParameters.getCompOpt().getFlags() & 16384) == 16384;
                     }

                     return var1;
                  }

                  public boolean getFormatSpecification() {
                     boolean var1 = false;
                     if (PalTransactions.this.naturalParameters.getCompOpt() != null) {
                        var1 = (PalTransactions.this.naturalParameters.getCompOpt().getFlags() & 8) == 8;
                     }

                     return var1;
                  }

                  public int getProcessingLoopLimit() {
                     int var1 = 99999999;
                     if (PalTransactions.this.naturalParameters.getLimit() != null) {
                        var1 = PalTransactions.this.naturalParameters.getLimit().getProcessingLoopLimit();
                     }

                     return var1;
                  }

                  public boolean getStructuredMode() {
                     boolean var1 = false;
                     if (PalTransactions.this.naturalParameters.getCompOpt() != null) {
                        var1 = (PalTransactions.this.naturalParameters.getCompOpt().getFlags() & 16) == 16;
                     }

                     return var1;
                  }

                  public int getMaxprec() {
                     int var1 = 0;
                     if (PalTransactions.this.naturalParameters.getCompOpt() != null) {
                        var1 = PalTransactions.this.naturalParameters.getCompOpt().getMaxprec();
                     }

                     if (var1 < 7 || var1 > 29) {
                        var1 = 7;
                     }

                     return var1;
                  }

                  public void setDatabaseShortName(boolean var1) {
                     try {
                        if (PalTransactions.this.naturalParameters.getCompOpt() != null) {
                           if (var1) {
                              PalTransactions.this.naturalParameters.getCompOpt().setFlags(64);
                           } else {
                              PalTransactions.this.naturalParameters.getCompOpt().resetFlags(64);
                           }
                        }

                     } catch (PalTransactions$Exception var2) {
                     }
                  }

                  public void setDynamicThousandsSeparator(boolean var1) {
                     if (PalTransactions.this.naturalParameters.getCompOpt() != null) {
                        if (var1) {
                           PalTransactions.this.naturalParameters.getCompOpt().setFlags(16384);
                        } else {
                           PalTransactions.this.naturalParameters.getCompOpt().resetFlags(16384);
                        }
                     }

                  }

                  public void setFormatSpecification(boolean var1) {
                     try {
                        if (PalTransactions.this.naturalParameters.getCompOpt() != null) {
                           if (var1) {
                              PalTransactions.this.naturalParameters.getCompOpt().setFlags(8);
                           } else {
                              PalTransactions.this.naturalParameters.getCompOpt().resetFlags(8);
                           }
                        }

                     } catch (PalTransactions$Exception var2) {
                     }
                  }

                  public void setProcessingLoopLimit(int var1) {
                     if (PalTransactions.this.naturalParameters.getLimit() != null) {
                        PalTransactions.this.naturalParameters.getLimit().setProcessingLoopLimit(var1);
                     }

                  }

                  public void setStructuredMode(boolean var1) {
                     try {
                        if (PalTransactions.this.naturalParameters.getCompOpt() != null) {
                           if (var1) {
                              PalTransactions.this.naturalParameters.getCompOpt().setFlags(16);
                           } else {
                              PalTransactions.this.naturalParameters.getCompOpt().resetFlags(16);
                           }
                        }

                     } catch (PalTransactions$Exception var2) {
                     }
                  }

                  public void setMaxprec(int var1) {
                     if (PalTransactions.this.naturalParameters.getCompOpt() != null) {
                        PalTransactions.this.naturalParameters.getCompOpt().setMaxprec(var1);
                     }

                  }

                  public boolean getCALLNATParameterChecking() {
                     boolean var1 = false;
                     if (PalTransactions.this.naturalParameters.getCompOpt() != null) {
                        var1 = (PalTransactions.this.naturalParameters.getCompOpt().getFlags() & 4096) == 4096;
                     }

                     return var1;
                  }

                  public boolean getKeywordChecking() {
                     try {
                        boolean var1 = false;
                        if (PalTransactions.this.naturalParameters.getCompOpt() != null) {
                           var1 = (PalTransactions.this.naturalParameters.getCompOpt().getFlags() & 8192) == 8192;
                        }

                        return var1;
                     } catch (PalTransactions$Exception var2) {
                        return false;
                     }
                  }

                  public void setCALLNATParameterChecking(boolean var1) {
                     if (PalTransactions.this.naturalParameters.getCompOpt() != null) {
                        if (var1) {
                           PalTransactions.this.naturalParameters.getCompOpt().setFlags(4096);
                        } else {
                           PalTransactions.this.naturalParameters.getCompOpt().resetFlags(4096);
                        }
                     }

                  }

                  public void setKeywordChecking(boolean var1) {
                     if (PalTransactions.this.naturalParameters.getCompOpt() != null) {
                        if (var1) {
                           PalTransactions.this.naturalParameters.getCompOpt().setFlags(8192);
                        } else {
                           PalTransactions.this.naturalParameters.getCompOpt().resetFlags(8192);
                        }
                     }

                  }

                  public char getDateFormat() {
                     return PalTransactions.this.naturalParameters.getFldApp() != null ? (char)PalTransactions.this.naturalParameters.getFldApp().getDateFormat() : '\u0000';
                  }

                  public char getDateFormatOutput() {
                     try {
                        return PalTransactions.this.naturalParameters.getFldApp() != null ? (char)PalTransactions.this.naturalParameters.getFldApp().getDateFormatOutput() : '\u0000';
                     } catch (PalTransactions$Exception var1) {
                        return '\u0000';
                     }
                  }

                  public char getPm() {
                     return PalTransactions.this.naturalParameters.getFldApp() != null ? (char)PalTransactions.this.naturalParameters.getFldApp().getPrintMode() : '\u0000';
                  }

                  public char getDateFormatStack() {
                     try {
                        return PalTransactions.this.naturalParameters.getFldApp() != null ? (char)PalTransactions.this.naturalParameters.getFldApp().getDateFormatStack() : '\u0000';
                     } catch (PalTransactions$Exception var1) {
                        return '\u0000';
                     }
                  }

                  public char getDateFormatTitle() {
                     try {
                        return PalTransactions.this.naturalParameters.getFldApp() != null ? (char)PalTransactions.this.naturalParameters.getFldApp().getDateFormatTitle() : '\u0000';
                     } catch (PalTransactions$Exception var1) {
                        return '\u0000';
                     }
                  }

                  public boolean isFillerCharacterProtected() {
                     boolean var1 = false;
                     if (PalTransactions.this.naturalParameters.getFldApp() != null) {
                        var1 = (PalTransactions.this.naturalParameters.getFldApp().getFlags() & 2) == 2;
                     }

                     return var1;
                  }

                  public boolean isMessageLineBottom() {
                     boolean var1 = false;
                     if (PalTransactions.this.naturalParameters.getFldApp() != null) {
                        var1 = (PalTransactions.this.naturalParameters.getFldApp().getFlags() & 16) == 16;
                     }

                     return var1;
                  }

                  public boolean isMessageLineTop() {
                     boolean var1 = false;
                     if (PalTransactions.this.naturalParameters.getFldApp() != null) {
                        var1 = (PalTransactions.this.naturalParameters.getFldApp().getFlags() & 8) == 8;
                     }

                     return var1;
                  }

                  public boolean isTranslateOutput() {
                     boolean var1 = false;
                     if (PalTransactions.this.naturalParameters.getFldApp() != null) {
                        var1 = (PalTransactions.this.naturalParameters.getFldApp().getFlags() & 4) == 4;
                     }

                     return var1;
                  }

                  public boolean isZeroPrinting() {
                     boolean var1 = false;
                     if (PalTransactions.this.naturalParameters.getFldApp() != null) {
                        var1 = (PalTransactions.this.naturalParameters.getFldApp().getFlags() & 1) == 1;
                     }

                     return var1;
                  }

                  public void setDateFormat(char var1) {
                     if (PalTransactions.this.naturalParameters.getFldApp() != null) {
                        PalTransactions.this.naturalParameters.getFldApp().setDateFormat((byte)var1);
                     }

                  }

                  public void setDateFormatOutput(char var1) {
                     if (PalTransactions.this.naturalParameters.getFldApp() != null) {
                        PalTransactions.this.naturalParameters.getFldApp().setDateFormatOutput((byte)var1);
                     }

                  }

                  public void setPm(char var1) {
                     if (PalTransactions.this.naturalParameters.getFldApp() != null) {
                        PalTransactions.this.naturalParameters.getFldApp().setPrintMode((byte)var1);
                     }

                  }

                  public void setDateFormatStack(char var1) {
                     if (PalTransactions.this.naturalParameters.getFldApp() != null) {
                        PalTransactions.this.naturalParameters.getFldApp().setDateFormatStack((byte)var1);
                     }

                  }

                  public void setDateFormatTitle(char var1) {
                     if (PalTransactions.this.naturalParameters.getFldApp() != null) {
                        PalTransactions.this.naturalParameters.getFldApp().setDateFormatTitle((byte)var1);
                     }

                  }

                  public void setFillerCharacterProtected() {
                     if (PalTransactions.this.naturalParameters.getFldApp() != null) {
                        PalTransactions.this.naturalParameters.getFldApp().setFlags(2);
                     }

                  }

                  public void setMessageLineBottom() {
                     if (PalTransactions.this.naturalParameters.getFldApp() != null) {
                        PalTransactions.this.naturalParameters.getFldApp().setFlags(16);
                     }

                  }

                  public void setMessageLineTop() {
                     if (PalTransactions.this.naturalParameters.getFldApp() != null) {
                        PalTransactions.this.naturalParameters.getFldApp().setFlags(8);
                     }

                  }

                  public void setTranslateOutput() {
                     if (PalTransactions.this.naturalParameters.getFldApp() != null) {
                        PalTransactions.this.naturalParameters.getFldApp().setFlags(4);
                     }

                  }

                  public void setZeroPrinting() {
                     if (PalTransactions.this.naturalParameters.getFldApp() != null) {
                        PalTransactions.this.naturalParameters.getFldApp().setFlags(1);
                     }

                  }

                  public int getLanguageCode() {
                     try {
                        int var1 = 0;
                        if (PalTransactions.this.systemVariables != null) {
                           IPalTypeSysVar[] var5;
                           for(IPalTypeSysVar var2 : var5 = PalTransactions.this.systemVariables) {
                              if (var2.getKind() == 0) {
                                 var1 = var2.getLanguage();
                              }
                           }
                        }

                        return var1;
                     } catch (PalTransactions$Exception var6) {
                        return 0;
                     }
                  }

                  public void setLanguageCode(int var1) {
                     if (PalTransactions.this.systemVariables != null) {
                        PalTransactions.this.systemVariables[0].setLanguage(var1);
                     }

                  }

                  public boolean getPrivateMode() {
                     return this.isPrivateMode;
                  }

                  public void setPrivateMode(boolean var1) {
                     try {
                        this.isPrivateMode = var1;
                     } catch (PalTransactions$Exception var2) {
                     }
                  }

                  public int getMaxyear() {
                     int var1 = 0;
                     if (PalTransactions.this.naturalParameters.getFldApp() != null) {
                        var1 = PalTransactions.this.naturalParameters.getFldApp().getMaxyear();
                     }

                     if (var1 == 0) {
                        var1 = 2699;
                     }

                     return var1;
                  }

                  public void setMaxyear(int var1) {
                     if (PalTransactions.this.naturalParameters.getFldApp() != null) {
                        PalTransactions.this.naturalParameters.getFldApp().setMaxyear(var1);
                     }

                  }

                  public void sendToServer() throws IOException, PalResultException {
                     try {
                        PalTransactions.access$11(PalTransactions.this, this.isPrivateMode);
                     } catch (PalTransactions$Exception var1) {
                     }
                  }

                  public boolean getRenConst() {
                     try {
                        boolean var1 = false;
                        if (PalTransactions.this.naturalParameters.getCompOpt() != null) {
                           var1 = (PalTransactions.this.naturalParameters.getCompOpt().getFlags() & 1048576) == 1048576;
                        }

                        return var1;
                     } catch (PalTransactions$Exception var2) {
                        return false;
                     }
                  }

                  public void setRenConst(boolean var1) {
                     try {
                        if (PalTransactions.this.naturalParameters.getCompOpt() != null) {
                           if (var1) {
                              PalTransactions.this.naturalParameters.getCompOpt().setFlags(1048576);
                           } else {
                              PalTransactions.this.naturalParameters.getCompOpt().resetFlags(1048576);
                           }
                        }

                     } catch (PalTransactions$Exception var2) {
                     }
                  }
               };
            }

            return this.serverConfiguration;
         }
      } catch (PalTransactions$Exception var2) {
         return null;
      }
   }

   public boolean isLibraryEmpty(IPalTypeSystemFile var1, String var2) throws IOException, PalResultException {
      try {
         int var3 = 0;
         if (this.pal == null) {
            throw new IllegalStateException("connection to ndv server not available");
         } else if (var1 == null) {
            throw new IllegalArgumentException("systemFileKey must not be null");
         } else if (var2 == null) {
            throw new IllegalArgumentException("library must not be null");
         } else {
            PalTrace.header("isLibraryEmpty");
            var3 = this.handleLibraryEmpty(var1, var2);
            return var3 != 0;
         }
      } catch (PalTransactions$Exception var4) {
         return false;
      }
   }

   public IPalTypeLibraryStatistics getLibraryStatistics(IPalTypeSystemFile var1, String var2) throws IOException, PalResultException {
      try {
         return this.getLibraryStatistics(EnumSet.of(ELibraryStatisticsOption.RAW_STATISTICS_RECORD), var1, var2);
      } catch (PalTransactions$Exception var3) {
         return null;
      }
   }

   public IPalProperties getPalProperties() {
      return this.palProperties;
   }

   public PalTypeLibId[] getStepLibs() throws IOException, PalResultException {
      try {
         if (this.pal == null) {
            throw new IllegalStateException("connection to ndv server not available");
         } else {
            Object var1 = null;
            PalTrace.header("getStepLibs");
            this.pal.add((IPalType)(new PalTypeOperation(4, 4)));
            this.pal.commit();
            PalTypeLibId[] var4 = (PalTypeLibId[])this.pal.retrieve(6);
            PalResultException var2 = this.getResultException();
            if (var2 != null) {
               throw var2;
            } else {
               return var4 == null ? NULL_LIBID_ARRAY : var4;
            }
         }
      } catch (PalTransactions$Exception var3) {
         return null;
      }
   }

   public void dasConnect(Map var1, int var2) throws IOException, ConnectException, PalResultException {
      try {
         PalTrace.header("dasConnect");
         this.pal = new Pal(this.palPreferences != null ? this.palPreferences.getTimeOut() : 0, this.timeoutHandler);
         this.host = (String)var1.get("host");
         this.port = (String)var1.get("port");
         this.pal.connect(this.host, this.port);
         this.pal.add((IPalType)(new PalTypeOperation(78, var2)));
         this.pal.commit();
         Object var3 = null;
         if (4 == var2) {
            PalResultException var7 = this.getResultException();
            this.pal.closeSocket();
         } else {
            PalResultException var8 = this.getResultException();
            if (var8 != null) {
               throw var8;
            }

            IPalTypeEnviron[] var4 = (IPalTypeEnviron[])this.pal.retrieve(0);
            if (var4 != null) {
               String var5 = var4[0].getSessionId();
               this.pal.setSessionId(var5);
            }

            this.setConnected(true);
         }

      } catch (PalTimeoutException var6) {
         throw var6;
      }
   }

   public void dasBindToAttachSession(String var1, String var2, String var3) throws IOException, PalResultException {
      try {
         PalTrace.header("dasBindToAttachSession");
         this.pal.add((IPalType)(new PalTypeOperation(84)));
         this.pal.add((IPalType)(new PalTypeDbgaRecord(var3, "", var1, var2)));
         this.pal.commit();
         PalResultException var4 = this.getResultException();
         if (var4 != null) {
            throw var4;
         }
      } catch (PalTransactions$Exception var5) {
      }
   }

   public void dasSignIn() throws IOException, PalResultException {
      PalTrace.header("dasSignIn");
      PalTypeOperation var1 = new PalTypeOperation(18);
      this.pal.setUserId("");
      var1.setUserId("");
      this.pal.add((IPalType)var1);
      PalTypeConnect var2 = new PalTypeConnect("", "", "");
      this.pal.add((IPalType)var2);
      PalTypeEnviron var3 = new PalTypeEnviron();
      var3.setWebBrowserIO(true);
      var3.setNfnPrivateMode(false);
      if (this.getIdentification() != null) {
         var3.setNdvClientClientId(this.getIdentification().getNdvClientId());
         var3.setNdvClientClientVersion(this.getIdentification().getNdvClientVersion());
      }

      this.pal.add((IPalType)var3);
      PalTypeCP var4 = new PalTypeCP(Charset.defaultCharset().displayName());
      this.pal.add((IPalType)var4);
      this.pal.commit();
      IPalTypeEnviron[] var5 = (IPalTypeEnviron[])this.pal.retrieve(0);
      IPalTypeCP[] var6 = (IPalTypeCP[])this.pal.retrieve(45);
      IPalTypeStream[] var7 = (IPalTypeStream[])this.pal.retrieve(13);
      if (var7 != null) {
         throw new PalConnectResultException(9999, "invalid I/O performed on server", 3, 0);
      } else {
         PalResultException var8 = this.getResultException();
         if (var8 != null) {
            throw var8;
         } else {
            int var9 = 0;
            int var10 = 0;
            int var11 = 0;
            int var12 = 0;
            int var13 = 0;
            int var14 = 0;
            String var15 = "";
            boolean var16 = false;
            boolean var17 = false;
            boolean var18 = false;
            boolean var19 = false;
            String var20 = "";
            String var21 = "";
            String var22 = "";
            String var23 = "";
            EAttachSessionType var24 = EAttachSessionType.NDV;
            if (var5 != null) {
               var9 = var5[0].getNdvType();
               var13 = var5[0].getWebVersion();
               var11 = var5[0].getNatVersion();
               var10 = var5[0].getNdvVersion();
               var12 = var5[0].getPalVersion();
               var15 = var5[0].getSessionId();
               var16 = var5[0].isMfUnicodeSrcPossible();
               var17 = var5[0].isWebIOServer();
               var14 = var5[0].getLogonCounter();
               var19 = var5[0].performsTimeStampChecks();
               var24 = var5[0].getAttachSessionType();
               this.pal.setNdvType(var9);
               this.pal.setPalVersion(var12);
               this.pal.setSessionId(var15);
            }

            if (var6 != null) {
               var20 = var6[0].getCodePage();
            }

            this.createPalProperties(var9, var10, var11, var12, var15, var16, var17, var13, var14, var20, var18, var21, var22, var19, var23, var24);
         }
      }
   }

   public PalTypeDbgSyt[] getSymbolTable(IPalTypeDbgVarContainer var1) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var1 == null) {
         throw new IllegalArgumentException("container must not be null");
      } else {
         PalTypeDbgSyt[] var2 = null;

         try {
            PalTrace.header("getSymbolTable");
            this.pal.add((IPalType)(new PalTypeOperation(64)));
            this.pal.add((IPalType)var1);
            this.pal.commit();
            PalResultException var3 = this.getResultException();
            if (var3 != null) {
               throw var3;
            } else {
               var2 = (PalTypeDbgSyt[])this.pal.retrieve(37);
               return var2;
            }
         } catch (PalTimeoutException var4) {
            throw var4;
         }
      }
   }

   public PalTypeSystemFile[] getSystemFiles() throws IOException, PalResultException {
      Object var1 = null;
      if (this.pal == null) {
         throw new IllegalStateException("connection to Ndv server not available");
      } else {
         PalTrace.header("getSystemFiles");
         PalTypeOperation var2 = new PalTypeOperation(6);
         this.pal.add((IPalType)var2);
         this.pal.commit();
         PalResultException var3 = this.getResultException();
         if (var3 != null) {
            throw var3;
         } else {
            PalTypeSystemFile[] var4 = (PalTypeSystemFile[])this.pal.retrieve(3);
            return var4 == null ? NULL_SYSTEMFILE_ARRAY : (PalTypeSystemFile[])var4.clone();
         }
      }
   }

   public PalTypeDbgVarValue[] getValue(IPalTypeDbgVarContainer var1, IPalTypeDbgVarDesc var2) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var1 == null) {
         throw new IllegalArgumentException("container must not be null");
      } else if (var2 == null) {
         throw new IllegalArgumentException("description must not be null");
      } else {
         PalTypeDbgVarValue[] var3 = null;

         try {
            PalTrace.header("getValue");
            this.pal.add((IPalType)(new PalTypeOperation(67)));
            this.pal.add((IPalType)var1);
            this.pal.add((IPalType)var2);
            this.pal.setServerCodePage(this.palProperties.getDefaultCodePage());
            this.pal.commit();
            PalResultException var4 = this.getResultException();
            if (var4 != null) {
               throw var4;
            }

            var3 = (PalTypeDbgVarValue[])this.pal.retrieve(39);
         } catch (PalTimeoutException var5) {
            throw var5;
         }

         return var3;
      }
   }

   public IPalTypeDbgStackFrame[] setNextStatement(IPalTypeDbgStackFrame var1) throws IOException, PalResultException {
      try {
         if (this.pal == null) {
            throw new IllegalStateException("connection to ndv server not available");
         } else if (var1 == null) {
            throw new IllegalArgumentException("stack frame must not be null");
         } else {
            IPalTypeDbgStackFrame[] var2 = null;
             PalTrace.header("setNextStatement");
            this.pal.add((IPalType)(new PalTypeOperation(72)));
            this.pal.add((IPalType)var1);
            this.pal.commit();
            PalResultException var3 = this.getResultException();
            if (var3 != null) {
               throw var3;
            } else {
               var2 = (PalTypeDbgStackFrame[])this.pal.retrieve(34);
               return var2;
            }
         }
      } catch (PalTransactions$Exception var4) {
         return null;
      }
   }

   public void isLocked(IPalTypeSystemFile var1, String var2, String var3, int var4, int var5) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var4 < 0) {
         throw new IllegalArgumentException("kind must be one of the ids defined inside utility class 'sag.pal.ObjectKind'");
      } else if (var5 != 131072 && var5 != 0 && !ObjectType.getInstanceIdExtension().containsKey(var5)) {
         throw new IllegalArgumentException("type must be one of the ids defined inside utility class 'sag.pal.ObjectType'");
      } else if (var5 != 65536) {
         if (var1 == null) {
            throw new IllegalArgumentException("systemFileKey must not be null");
         } else if (var2 == null) {
            throw new IllegalArgumentException("library must not be null");
         } else if (var3 == null) {
            throw new IllegalArgumentException("filter must not be null");
         } else if (var3.length() == 0) {
            throw new IllegalArgumentException("name parameter must not be empty");
         } else {
            PalTrace.header("isLocked");
            this.handleLocking(44, var1, var2, var3, var4, var5);
         }
      }
   }

   public void lock(IPalTypeSystemFile var1, String var2, String var3, int var4, int var5) throws IOException, PalResultException {
      try {
         if (this.pal == null) {
            throw new IllegalStateException("connection to ndv server not available");
         } else if (var4 < 0) {
            throw new IllegalArgumentException("kind must be one of the ids defined inside utility class 'sag.pal.ObjectKind'");
         } else if (var5 != 131072 && var5 != 0 && !ObjectType.getInstanceIdExtension().containsKey(var5)) {
            throw new IllegalArgumentException("type must be one of the ids defined inside utility class 'sag.pal.ObjectType'");
         } else if (var5 != 65536) {
            if (var1 == null) {
               throw new IllegalArgumentException("systemFileKey must not be null");
            } else if (var2 == null) {
               throw new IllegalArgumentException("library must not be null");
            } else if (var3 == null) {
               throw new IllegalArgumentException("filter must not be null");
            } else if (var3.length() == 0) {
               throw new IllegalArgumentException("name parameter must not be empty");
            } else {
               PalTrace.header("lock");
               this.handleLocking(29, var1, var2, var3, var4, var5);
            }
         }
      } catch (PalTransactions$Exception var6) {
      }
   }

   public void logon(String var1) throws IOException, PalResultException {
      String var2 = this.getLogonLibrary();
      if (var2 == null || !this.getLogonLibrary().equals(var1)) {
         if (this.pal == null) {
            throw new IllegalStateException("connection to ndv server not available");
         }

         if (var1 == null) {
            throw new IllegalArgumentException("library must not be null");
         }

         IPalTypeLibId[] var3 = new IPalTypeLibId[]{PalTypeLibIdFactory.newInstance()};
         this.doLogon(var1, var3);
      }

   }

   public void logon(String var1, IPalTypeLibId[] var2) throws IOException, PalResultException {
      try {
         if (this.pal == null) {
            throw new IllegalStateException("connection to ndv server not available");
         } else if (var1 == null) {
            throw new IllegalArgumentException("library must not be null");
         } else if (var2 == null) {
            throw new IllegalArgumentException("the palTypeLibIds must not be null");
         } else {
            this.doLogon(var1, var2);
         }
      } catch (PalTransactions$Exception var3) {
      }
   }

   public void modifyValue(IPalTypeDbgVarContainer var1, IPalTypeDbgVarDesc var2, IPalTypeDbgVarValue var3) throws IOException, PalResultException {
      if (var1 == null) {
         throw new IllegalArgumentException("container must not be null");
      } else if (var2 == null) {
         throw new IllegalArgumentException("description must not be null");
      } else if (var3 == null) {
         throw new IllegalArgumentException("value must not be null");
      } else {
         try {
            PalTrace.header("modifyValue");
            this.pal.add((IPalType)(new PalTypeOperation(69)));
            this.pal.add((IPalType)var1);
            this.pal.add((IPalType)var2);
            this.pal.setServerCodePage(this.palProperties.getDefaultCodePage());
            var3.setPalVers(this.getPalProperties().getPalVersion());
            var3.setNdvType(this.getPalProperties().getNdvType());
            this.pal.add((IPalType)var3);
            this.pal.commit();
            PalResultException var4 = this.getResultException();
            if (var4 != null) {
               throw var4;
            }
         } catch (PalTimeoutException var5) {
            throw var5;
         }
      }
   }

   public void move(IPalTypeSystemFile var1, String var2, String var3, IPalTypeSystemFile var4, String var5, String var6, int var7, int var8) throws IOException, PalResultException {
      try {
         PalTypeObject var9 = new PalTypeObject();
         var9.setKind(var7);
         var9.setType(var8);
         this.move(var1, var2, var3, var4, var5, var6, var9);
      } catch (PalTransactions$Exception var10) {
      }
   }

   public void move(IPalTypeSystemFile var1, String var2, String var3, IPalTypeSystemFile var4, String var5, String var6, IPalTypeObject var7) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var2 != null && var5 != null) {
         if (var3 != null && var6 != null) {
            try {
               PalTrace.header("move");
               PalTypeFileId var8 = new PalTypeFileId();
               var8.setNatKind(var7.getKind());
               var8.setNatType(var7.getType());
               var8.setObject(var3);
               var8.setNewObject(var6);
               var8.setUser(var7.getSourceUser());
               var8.setSourceDate(var7.getSourceDate());
               var8.setGpSize(var7.getGpSize());
               var8.setGpUser(var7.getGpUser());
               var8.setGpDate(var7.getGpDate());
               this.fileOperationServerLocal(41, var1, var2, var4, var5, var8);
            } catch (PalTimeoutException var9) {
               throw var9;
            }
         } else {
            throw new IllegalArgumentException("objectName must not be null");
         }
      } else {
         throw new IllegalArgumentException("library must not be null");
      }
   }

   public ISuspendResult nextScreen() throws IOException {
      try {
         PalTrace.header("nextScreen");
         this.pal.add((IPalType)(new PalTypeNotify(6)));
         this.pal.commit();
      } catch (PalTimeoutException var2) {
         throw var2;
      }

      return this.getSuspendResult();
   }

   public String[] read(IPalTypeSystemFile var1, String var2, String var3, Set var4) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var2 == null) {
         throw new IllegalArgumentException("library must not be null");
      } else if (var3 == null) {
         throw new IllegalArgumentException("sourceName must not be null");
      } else {
         return this.readInternal(var1, var2, var3, var4, true);
      }
   }

   public Object[] receiveFiles(IPalTypeSystemFile var1, String var2, IFileProperties var3, Set var4) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var1 == null) {
         throw new IllegalArgumentException("systemFileKey must not be null");
      } else if (var2 == null) {
         throw new IllegalArgumentException("library must not be null");
      } else if (var3 == null) {
         throw new IllegalArgumentException("properties must not be null");
      } else {
         Object var5 = null;

         try {
            PalTrace.header("receiveFiles");
            PalTypeFileId var6 = new PalTypeFileId();
            var6.setObject(var3.getName());
            var6.setNewObject(var3.getLongName());
            if (var3.getType() == 32768) {
               var6.setNatKind(0);
            } else {
               var6.setNatKind(1);
            }

            var6.setNatType(var3.getType());
            var6.setStructured(var3.isStructured());
            if (var1.getKind() == 6) {
               var6.setNatKind(1);
               var6.setNatType(8);
            }

            var5 = this.fileOperationReceiveFiles(var1, this.getLibrary(var1, var2), var6, var4, var3.getTimeStamp());
            return (Object[])var5;
         } catch (PalTimeoutException var7) {
            throw var7;
         }
      }
   }

   public IPalTypeDbgVarDesc[] resolveIndices(boolean param1, IPalTypeDbgVarContainer param2, IPalTypeDbgVarDesc param3) throws IOException, PalResultException {
      // $FF: Couldn't be decompiled
      throw new UnsupportedOperationException("Decompilation failed");
   }

   public void save(IPalTypeSystemFile var1, String var2, IFileProperties var3, String[] var4) throws IOException, PalResultException, PalCompileResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var3 == null) {
         throw new IllegalArgumentException("properties must not be null");
      } else {
         PalTrace.header("save");
         boolean var5 = false;
         if (var3.getOptions().contains(EFileOptions.OLD_DATAAREA_FORMAT)) {
            var5 = true;
         }

         this.handleCommand(var1, this.getLibrary(var1, var2), var3.getBaseLibrary(), var3.getType() == 8 ? var3.getLongName() : var3.getName(), var3.getType(), "SAVE", var3.isStructured(), true, var4, var3.getLineNumberIncrement(), var3.getCodePage(), var3.getDatbaseId(), var3.getFnr(), var5, var3.getTimeStamp(), var3.isLinkedDdm());
      }
   }

   public void sendFiles(IPalTypeSystemFile var1, String var2, ObjectProperties var3, Set var4, Object[] var5) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var1 == null) {
         throw new IllegalArgumentException("systemFileKey must not be null");
      } else if (var2 == null) {
         throw new IllegalArgumentException("library must not be null");
      } else if (var3 == null) {
         throw new IllegalArgumentException("properties must not be null");
      } else if (var5 == null) {
         throw new IllegalArgumentException("files must not be null");
      } else if (var5.length == 0) {
         throw new IllegalArgumentException("files array is empty");
      } else {
         try {
            PalTrace.header("sendFiles");
            PalTypeFileId var6 = new PalTypeFileId();
            var6.setNatKind(var3.getKind());
            var6.setNatType(var3.getType());
            if (var3.getType() == 32768) {
               var6.setNatKind(64);
               if (var3.getName().length() > 2) {
                  var6.setNewObject(var3.getName() + ".MSG");
                  String var7 = var3.getName().substring(1, 3);
                  if (var7.substring(0, 1).equals("0")) {
                     var7 = var7.substring(1, 2);
                  }

                  var6.setObject(var7);
               } else {
                  var6.setNewObject(var3.getName());
                  var6.setObject(var3.getName());
               }
            } else if (var3.getType() == 8) {
               var6.setObject(var3.getLongName());
               var6.setNewObject(var3.getName());
            } else {
               var6.setObject(var3.getName());
            }

            var6.setStructured(var3.isStructured());
            var6.setUser(var3.getUser());
            var6.setGpUser(var3.getGpUser());
            var6.setSourceDate(var3.getSourceDate());
            var6.setGpDate(var3.getGpDate());
            var6.setSourceSize(var3.getSourceSize());
            var6.setGpSize(var3.getGpSize());
            var6.setDatabaseId(var3.getDatbaseId());
            var6.setFileNumber(var3.getFnr());
            this.fileOperationSendFiles(var1, this.getLibrary(var1, var2), var3.getBaseLibrary(), var6, var5, var3.getLineNumberIncrement(), var3.getInternalLabelFirst(), var4, var3.getTimeStamp());
         } catch (PalTimeoutException var8) {
            throw var8;
         }
      }
   }

   public ISuspendResult sendScreen(byte[] var1) throws IOException {
      try {
         PalTrace.header("sendScreen");
         if (var1 != null) {
            this.pal.add((IPalType)(new PalTypeStream(var1, this.palProperties.getNdvType())));
         } else {
            this.pal.add((IPalType)(new PalTypeNotify(7)));
         }

         this.pal.commit();
         return this.getSuspendResult();
      } catch (PalTransactions$Exception var2) {
         return null;
      }
   }

   public void setIdentification(IPalClientIdentification var1) {
      try {
         this.identification = var1;
      } catch (PalTransactions$Exception var2) {
      }
   }

   public void setApplicationExecutionContext(IPalExecutionContext var1) {
      try {
         this.executionContext = var1;
      } catch (PalTransactions$Exception var2) {
      }
   }

   public void setArabicShapingContext(IPalArabicShaping var1) {
      try {
         this.shapingContext = var1;
      } catch (PalTransactions$Exception var2) {
      }
   }

   public void setCodePageOfSource(IPalTypeSystemFile param1, String param2, String param3, int param4, String param5) throws IOException, PalResultException {
      // $FF: Couldn't be decompiled
   }

   public void setNaturalParameters(INatParm var1) {
      try {
         this.naturalParameters = var1;
      } catch (PalTransactions$Exception var2) {
      }
   }

   public void setPalProperties(IPalProperties var1) {
   }

   public void setPalSQLIdentification(IPalSQLIdentification var1) {
      try {
         this.palSQLIdentification = var1;
      } catch (PalTransactions$Exception var2) {
      }
   }

   public void setPalPreferences(IPalPreferences var1) {
      try {
         this.palPreferences = var1;
      } catch (PalTransactions$Exception var2) {
      }
   }

   public void setPalTimeoutHandler(IPalTimeoutHandler var1) {
      this.timeoutHandler = var1;
      if (this.pal != null) {
         this.pal.setPalTimeoutHandler(var1);
      }

   }

   public void setStepLibs(IPalTypeLibId[] var1, EStepLibFormat var2) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else {
         try {
            PalTrace.header("setStepLibs");
            this.pal.add((IPalType)(new PalTypeOperation(77, var2 == EStepLibFormat.SHARED ? 1 : (var2 == EStepLibFormat.PRIVATE ? 2 : 0))));
            this.pal.add((IPalType[])var1);
            this.pal.commit();
            PalResultException var3 = this.getResultException();
            if (var3 != null) {
               throw var3;
            }
         } catch (PalTimeoutException var4) {
            throw var4;
         }
      }
   }

   public IPalTypeDbgSpy spyDelete(IPalTypeDbgSpy var1) throws IOException, PalResultException {
      IPalTypeDbgSpy var2 = null;

      try {
         PalTrace.header("spyDelete");
         var2 = this.handleSpy(2, var1, (IPalTypeDbgVarDesc)null, (IPalTypeDbgVarValue)null);
         return var2;
      } catch (PalTimeoutException var4) {
         throw var4;
      }
   }

   public IPalTypeDbgSpy spyModify(IPalTypeDbgSpy var1) throws IOException, PalResultException {
      IPalTypeDbgSpy var2 = null;

      try {
         PalTrace.header("spyModify");
         var2 = this.handleSpy(3, var1, (IPalTypeDbgVarDesc)null, (IPalTypeDbgVarValue)null);
      } catch (PalTimeoutException var4) {
         throw var4;
      }

      return var2;
   }

   public IPalTypeDbgSpy spyModify(IPalTypeDbgSpy var1, IPalTypeDbgVarDesc var2, IPalTypeDbgVarValue var3) throws IOException, PalResultException {
      IPalTypeDbgSpy var4 = null;

      try {
         PalTrace.header("spyModify");
         var4 = this.handleSpy(3, var1, var2, var3);
         return var4;
      } catch (PalTimeoutException var6) {
         throw var6;
      }
   }

   public IPalTypeDbgSpy spySet(IPalTypeDbgSpy var1, IPalTypeDbgVarDesc var2, IPalTypeDbgVarValue var3) throws IOException, PalResultException {
      IPalTypeDbgSpy var4 = null;

      try {
         PalTrace.header("spySet");
         var4 = this.handleSpy(1, var1, var2, var3);
         return var4;
      } catch (PalTimeoutException var6) {
         throw var6;
      }
   }

   public IPalTypeDbgSpy spySet(IPalTypeDbgSpy var1) throws IOException, PalResultException {
      IPalTypeDbgSpy var2 = null;

      try {
         PalTrace.header("spySet");
         var2 = this.handleSpy(1, var1, (IPalTypeDbgVarDesc)null, (IPalTypeDbgVarValue)null);
      } catch (PalTimeoutException var4) {
         throw var4;
      }

      return var2;
   }

   public void stow(IPalTypeSystemFile var1, String var2, IFileProperties var3, String[] var4) throws IOException, PalResultException, PalCompileResultException {
      try {
         if (this.pal == null) {
            throw new IllegalStateException("connection to ndv server not available");
         } else if (var3 == null) {
            throw new IllegalArgumentException("properties must not be null");
         } else {
            PalTrace.header("stow");
            boolean var5 = false;
            if (var3.getOptions().contains(EFileOptions.OLD_DATAAREA_FORMAT)) {
               var5 = true;
            }

            this.handleCommand(var1, this.getLibrary(var1, var2), var3.getBaseLibrary(), var3.getType() == 8 ? var3.getLongName() : var3.getName(), var3.getType(), "STOW", var3.isStructured(), true, var4, var3.getLineNumberIncrement(), var3.getCodePage(), var3.getDatbaseId(), var3.getFnr(), var5, var3.getTimeStamp(), var3.isLinkedDdm());
         }
      } catch (PalTransactions$Exception var6) {
      }
   }

   public void terminateIO() throws IOException, PalResultException {
      try {
         if (this.pal == null) {
            throw new IllegalStateException("connection to ndv server not available");
         } else {
            PalTrace.header("terminateIO");
            PalTypeNotify var1 = new PalTypeNotify(5);
            this.pal.add((IPalType)var1);
            this.pal.commit();
            PalResultException var2 = this.getResultException();
            if (var2 != null) {
               throw var2;
            }
         }
      } catch (PalTransactions$Exception var3) {
      }
   }

   public void terminateRetrieval() throws IOException, PalResultException {
      try {
         if (this.pal == null) {
            throw new IllegalStateException("connection to ndv server not available");
         } else if (this.retrievalKind == 0) {
            throw new IllegalStateException("retrieval is not active");
         } else {
            if (this.currentNotify == 6) {
               PalTypeNotify var1 = new PalTypeNotify(5);
               this.pal.add((IPalType)var1);
               this.pal.commit();
               IPalTypeNotify[] var2 = (IPalTypeNotify[])this.pal.retrieve(19);
               PalResultException var3 = this.getResultException();
               if (var3 != null) {
                  throw var3;
               }

               var2[0].getNotification();
            }

            this.currentNotify = 0;
            this.retrievalKind = 0;
            this.isDuplicatePossible = false;
            this.serverList.clear();
         }
      } catch (PalTransactions$Exception var4) {
      }
   }

   public void unlock(IPalTypeSystemFile var1, String var2, String var3, int var4, int var5) throws IOException, PalResultException {
      try {
         if (this.pal == null) {
            throw new IllegalStateException("connection to ndv server not available");
         } else if (var4 < 0) {
            throw new IllegalArgumentException("kind must be one of the ids defined inside utility class 'sag.pal.ObjectKind'");
         } else if (var5 != 131072 && var5 != 0 && !ObjectType.getInstanceIdExtension().containsKey(var5)) {
            throw new IllegalArgumentException("type must be one of the ids defined inside utility class 'sag.pal.ObjectType'");
         } else if (var5 != 65536) {
            if (var1 == null) {
               throw new IllegalArgumentException("systemFileKey must not be null");
            } else if (var2 == null) {
               throw new IllegalArgumentException("library must not be null");
            } else if (var3 == null) {
               throw new IllegalArgumentException("filter must not be null");
            } else if (var3.length() == 0) {
               throw new IllegalArgumentException("name parameter must not be empty");
            } else {
               PalTrace.header("unlock");
               this.handleLocking(30, var1, var2, var3, var4, var5);
            }
         }
      } catch (PalTransactions$Exception var6) {
      }
   }

   public void uploadBinary(IPalTypeSystemFile var1, String var2, IFileProperties var3, ByteArrayOutputStream var4) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var1 == null) {
         throw new IllegalArgumentException("systemFileKey must not be null");
      } else if (var2 == null) {
         throw new IllegalArgumentException("library must not be null");
      } else if (var3 == null) {
         throw new IllegalArgumentException("properties must not be null");
      } else if (var4 == null) {
         throw new IllegalArgumentException("contents must not be null");
      } else if (var3.getKind() != 16 && var3.getKind() != 2) {
         throw new IllegalArgumentException("the FileProperties kind must be ObjectKind.RESOURCE");
      } else {
         try {
            PalTrace.header("uploadBinary");
            PalTypeFileId var5 = new PalTypeFileId();
            var5.setNatKind(var3.getKind());
            var5.setNatType(var3.getType());
            var5.setObject(var3.getName());
            var5.setNewObject(var3.getLongName());
            var5.setUser(var3.getUser());
            var5.setDatabaseId(var3.getDatbaseId());
            var5.setFileNumber(var3.getFnr());
            if (var3.getKind() == 2) {
               var5.setStructured(var3.isStructured());
               var5.setGpDate(var3.getDate());
               if (var3.getSize() != 0) {
                  var5.setGpSize(var3.getSize());
               } else {
                  var5.setGpSize(var4.size());
               }
            } else {
               var5.setSourceDate(var3.getDate());
               if (var3.getSize() != 0) {
                  var5.setSourceSize(var3.getSize());
               } else {
                  var5.setSourceSize(var4.size());
               }
            }

            this.fileOperationUploadBinary(var1, var2, var5, var4, var3.getTimeStamp(), var3.getBaseLibrary());
         } catch (PalTimeoutException var6) {
            throw var6;
         }
      }
   }

   public void uploadSource(IPalTypeSystemFile var1, String var2, IFileProperties var3, Set var4, String[] var5) throws IOException, PalResultException {
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var1 == null) {
         throw new IllegalArgumentException("systemFileKey must not be null");
      } else if (var2 == null) {
         throw new IllegalArgumentException("library must not be null");
      } else if (var3 == null) {
         throw new IllegalArgumentException("properties must not be null");
      } else if (var5 == null) {
         throw new IllegalArgumentException("lines must not be null");
      } else if (var5.length == 0) {
         throw new IllegalArgumentException("lines array is empty");
      } else if (var3.getKind() != 1) {
         throw new IllegalArgumentException("the FileProperties kind must be ObjectKind.SOURCE");
      } else {
         if (var3.getType() == 32768) {
            this.uploadErrorMessage(var1, var2, var3, var4, var5);
         } else {
            this.uploadFile(var1, var2, var3, var4, var5);
         }

      }
   }

   public void utilityBufferSend(short var1, byte[] var2) throws IOException {
      if (this.isUtilityCallActive) {
         throw new IllegalStateException("utilityBufferReceive was not called");
      } else if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else if (var2 == null) {
         throw new IllegalArgumentException("utilityBuffer must not be null");
      } else if (var2.length > 5000) {
         throw new IllegalArgumentException("the size of the utilityBuffer must not exceed 5000 Bytes");
      } else {
         try {
            PalTrace.header("utilityBufferSend");
            this.pal.add((IPalType)(new PalTypeOperation(36, var1)));
            int var3 = 0;
            int var4 = 0;

            for(var3 = var2.length - 1; var3 > 0 && var2[var3] == 32; --var3) {
            }

            ++var3;
            if (var3 % 100 != 0) {
               var4 = var3 / 100 + 1;
            } else {
               var4 = var3 / 100;
            }

            PalTypeUtility[] var5 = new PalTypeUtility[var4];
            int var6 = 0;

            for(int var7 = 0; var3 > 0; var3 -= 100) {
               int var8 = var3 > 100 ? 100 : var3;
               byte[] var9 = new byte[var8];

               for(int var10 = 0; var10 < var8; ++var10) {
                  var9[var10] = var2[var6++];
               }

               var3 -= var8;
               var5[var7++] = new PalTypeUtility(var9);
            }

            this.pal.add((IPalType[])var5);
            this.pal.commit();
            this.isUtilityCallActive = true;
         } catch (PalTimeoutException var11) {
            throw var11;
         }
      }
   }

   public byte[] utilityBufferReceive() throws IOException, PalResultException {
      byte[] var1 = null;
      boolean var2 = false;
      this.isUtilityCallActive = false;

      try {
         PalResultException var3;
         do {
            PalTrace.header("utilityBufferReceive");
            var3 = this.getResultException();
         } while(var3 == null && this.performSQLAuthentification());

         IPalTypeUtility[] var4 = (IPalTypeUtility[])this.pal.retrieve(14);
         if (var4 != null) {
            int var5 = 0;

            for(int var6 = 0; var6 < var4.length; ++var6) {
               var5 += var4[var6].getUtilityRecord().length;
            }

            if (var5 < 5000) {
               var2 = true;
               ++var5;
            }

            var1 = new byte[var5];
            int var12 = 0;

            for(int var7 = 0; var7 < var4.length; ++var7) {
               byte[] var8 = var4[var7].getUtilityRecord();

               for(int var9 = 0; var9 < var8.length; ++var9) {
                  var1[var12++] = var8[var9];
               }
            }

            if (var2) {
               var1[var12] = 32;
            }
         }

         if (var3 != null) {
            throw var3;
         } else {
            IPalTypeStream[] var11 = (IPalTypeStream[])this.pal.retrieve(13);
            if (var11 != null) {
               throw new IOException("The server transaction tried to perform I/O (stub rc=7)");
            } else {
               return var1;
            }
         }
      } catch (PalTimeoutException var10) {
         throw var10;
      }
   }

   public String toString() {
      try {
         Object var1 = null;
         String var3;
         if (this.isConnected()) {
            var3 = this.pal.toString();
         } else {
            var3 = "no connection available";
         }

         return var3;
      } catch (PalTransactions$Exception var2) {
         return null;
      }
   }

   private boolean isMainframe() {
      boolean var1 = false;
      IPalProperties var2 = this.getPalProperties();
      if (var2 != null) {
         var1 = this.getPalProperties().getNdvType() == 1;
      }

      return var1;
   }

   private void reinitSession() throws IOException, PalResultException {
      try {
         PalTrace.header("reinitSession");
         Object var1 = null;
         this.pal.add((IPalType)(new PalTypeOperation(60, 0)));
         this.pal.commit();
         PalResultException var3 = this.getResultException();
         if (var3 != null) {
            throw var3;
         } else {
            this.pal.setConnectionLost(false);
         }
      } catch (PalTransactions$Exception var2) {
      }
   }

   private String[] readInternal(IPalTypeSystemFile var1, String var2, String var3, Set var4, boolean var5) throws IOException, PalResultException {
      byte var6 = 0;
      if (var4.contains(EReadOption.READ)) {
         var6 = 10;
      }

      if (var4.contains(EReadOption.READDDM)) {
         var6 = 11;
      }

      if (var4.contains(EReadOption.LIST)) {
         var6 = 8;
      }

      if (var4.contains(EReadOption.LISTDDM)) {
         var6 = 24;
      }

      if (var4.contains(EReadOption.EDITDDM)) {
         var6 = 23;
      }

      if (var4.contains(EReadOption.EDIT)) {
         var6 = 21;
      }

      this.pal.add((IPalType)(new PalTypeOperation(2, var6)));
      this.pal.add((IPalType)(new PalTypeStack("READ " + var3 + " " + var2)));
      this.pal.commit();
      PalResultException var7 = this.getResultException();
      if (var7 != null) {
         throw var7;
      } else {
         return this.sourceFromPal(var1, var2, var3, var5, false, false).getSource();
      }
   }

   private void createPalProperties(int var1, int var2, int var3, int var4, String var5, boolean var6, boolean var7, int var8, int var9, String var10, boolean var11, String var12, String var13, boolean var14, String var15, EAttachSessionType var16) {
      try {
         this.palProperties = new PalProperties(var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16);
      } catch (PalTransactions$Exception var17) {
      }
   }

   private void rebuildStatisticsRecord() throws IOException, PalResultException {
      for(int var1 = 6; var1 == 6; var1 = this.rebuildStatisticsRecordGetObjects()) {
      }

   }

   private int rebuildStatisticsRecordGetObjects() throws IOException, PalResultException {
      int var1 = 6;
      Object var2 = null;
      Object var3 = null;

      while(var1 == 6) {
         var1 = this.notifyContinue();
         IPalTypeObject[] var4 = (IPalTypeObject[])this.pal.retrieve(8);
         PalResultException var5 = this.getResultException();
         if (var5 != null) {
            throw var5;
         }

         if (var4 == null) {
            var1 = 7;
         }
      }

      return var1;
   }

   private String getInternalLabelPrefix() {
      try {
         if (this.internalLabelPrefix != null) {
            return this.internalLabelPrefix;
         } else {
            if (this.internalLabelPrefix == null) {
               if (this.clientConfig != null && this.clientConfig.length != 0) {
                  String var1 = this.clientConfig[0].getIdent1stValid();
                  if (var1.indexOf(33) == -1) {
                     this.internalLabelPrefix = "!";
                  } else if (var1.indexOf(36) == -1) {
                     this.internalLabelPrefix = "$";
                  } else if (var1.indexOf(37) == -1) {
                     this.internalLabelPrefix = "%";
                  } else if (var1.indexOf(45) == -1) {
                     this.internalLabelPrefix = "-";
                  } else if (var1.indexOf(58) == -1) {
                     this.internalLabelPrefix = ":";
                  } else if (var1.indexOf(59) == -1) {
                     this.internalLabelPrefix = ";";
                  } else {
                     this.internalLabelPrefix = " ";
                  }
               } else {
                  this.internalLabelPrefix = "!";
               }
            }

            return this.internalLabelPrefix;
         }
      } catch (PalTransactions$Exception var2) {
         return null;
      }
   }

   public IPalTypeSysVar[] getSystemVariables() throws IOException, PalResultException {
      try {
         if (this.systemVariables == null) {
            if (this.pal == null) {
               throw new IllegalStateException("connection to ndv server not available");
            }

            PalTrace.header("getSystemVariables");
            this.pal.add((IPalType)(new PalTypeOperation(57, 1)));
            this.pal.commit();
            this.systemVariables = (PalTypeSysVar[])this.pal.retrieve(28);
            PalResultException var1 = this.getResultException();
            if (var1 != null) {
               throw var1;
            }
         }

         return (IPalTypeSysVar[])(this.systemVariables == null ? NULL_SYSVAR_ARRAY : this.systemVariables);
      } catch (PalTransactions$Exception var2) {
         return null;
      }
   }

   private ITransactionContext getTransactionContext() {
      return this.transactionContext;
   }

   private void setTransactionContext(ITransactionContext var1) {
      try {
         this.transactionContext = var1;
      } catch (PalTransactions$Exception var2) {
      }
   }

   private String removeLeadingLength(String var1) {
      try {
         String var2 = "^[ \\s]*[0-9]*[ \\s]*";
         String var3 = "";
         Pattern var4 = Pattern.compile(var2);
         Matcher var5 = var4.matcher(var1);
         return var5.replaceAll(var3);
      } catch (PalTransactions$Exception var6) {
         return null;
      }
   }

   private void getServerConfig(boolean var1) throws IOException, PalResultException {
      try {
         if (this.pal == null) {
            throw new IllegalStateException("connection to ndv server not available");
         } else {
            PalTrace.header("getServerConfig");
            PalTypeOperation var2 = new PalTypeOperation(10, 1);
            var2.setFlags(var1 ? 1 : 0);
            this.pal.add((IPalType)var2);
            this.pal.commit();
            PalResultException var3 = this.getResultException();
            if (var3 != null) {
               throw var3;
            } else {
               IPalTypeNatParm[] var4 = (IPalTypeNatParm[])this.pal.retrieve(25);
               this.naturalParameters = new NatParm(var4);
               this.clientConfig = (PalTypeClientConfig[])this.pal.retrieve(50);
               this.dbmsInfo = (IPalTypeDbmsInfo[])this.pal.retrieve(49);
               this.systemVariables = (PalTypeSysVar[])this.getSystemVariables();
            }
         }
      } catch (PalTransactions$Exception var5) {
      }
   }

   private void putServerConfig(boolean var1) throws IOException, PalResultException {
      try {
         if (this.pal == null) {
            throw new IllegalStateException("connection to ndv server not available");
         } else {
            PalTrace.header("putServerConfig");
            PalTypeOperation var2 = new PalTypeOperation(10, 2);
            this.pal.add((IPalType)var2);
            IPalTypeNatParm[] var3 = this.naturalParameters.get(this.getPalProperties().getNdvType());
            this.pal.add((IPalType[])var3);
            PalTypeGeneric var4 = new PalTypeGeneric(4, var1 ? 1 : 0);
            this.pal.add((IPalType)var4);
            this.pal.commit();
            PalResultException var5 = this.getResultException();
            if (var5 != null) {
               throw var5;
            } else {
               if (this.getPalProperties().getNdvType() != 1 && this.getPalProperties().getPalVersion() > 37 || this.getPalProperties().getNdvType() == 1) {
                  var2 = new PalTypeOperation(57, 2);
                  this.pal.add((IPalType)var2);
                  this.pal.add((IPalType[])this.systemVariables);
                  this.pal.commit();
                  var5 = this.getResultException();
                  if (var5 != null) {
                     throw var5;
                  }
               }

            }
         }
      } catch (PalTransactions$Exception var6) {
      }
   }

   private void uploadErrorMessage(IPalTypeSystemFile var1, String var2, IFileProperties var3, Set var4, String[] var5) throws IOException, PalResultException {
      PalTrace.header("uploadErrorMessage");
      PalTypeFileId var6 = new PalTypeFileId();
      var6.setNatKind(64);
      var6.setNatType(var3.getType());
      if (var3.getName().length() > 2) {
         var6.setNewObject(var3.getName() + ".MSG");
         String var7 = var3.getName().substring(1, 3);
         if (var7.substring(0, 1).equals("0")) {
            var7 = var7.substring(1, 2);
         }

         var6.setObject(var7);
      } else {
         var6.setNewObject(var3.getName());
         var6.setObject(var3.getName());
      }

      var6.setUser(var3.getUser());
      var6.setSourceDate(var3.getDate());
      var6.setDatabaseId(var3.getDatbaseId());
      var6.setFileNumber(var3.getFnr());
      if (var3.getSize() != 0) {
         var6.setSourceSize(var3.getSize());
      } else {
         var6.setSourceSize(this.calculateSourceSize(var5));
      }

      this.fileOperationUploadErrorMsg(var1, var2, var3.getBaseLibrary(), var6, var5, var3.getTimeStamp());
   }

   private void uploadFile(IPalTypeSystemFile var1, String var2, IFileProperties var3, Set var4, String[] var5) throws IOException, PalResultException {
      try {
         PalTrace.header("uploadSource");
         PalTypeFileId var6 = new PalTypeFileId();
         var6.setNatKind(var3.getKind());
         var6.setNatType(var3.getType());
         var6.setStructured(var3.isStructured());
         if (var3.getType() == 8) {
            var6.setObject(var3.getLongName());
            var6.setNewObject(var3.getName());
            var6.setStructured(true);
         } else {
            var6.setObject(var3.getName());
         }

         var6.setUser(var3.getUser());
         var6.setSourceDate(var3.getDate());
         var6.setDatabaseId(var3.getDatbaseId());
         var6.setFileNumber(var3.getFnr());
         if (var3.getSize() != 0) {
            var6.setSourceSize(var3.getSize());
         } else {
            var6.setSourceSize(this.calculateSourceSize(var5));
         }

         if (var3.getOptions().contains(EFileOptions.OLD_DATAAREA_FORMAT)) {
            var6.setOptions(1);
         }

         this.fileOperationUploadSource(var1, this.getLibrary(var1, var2), var6, var5, var3, var4);
      } catch (PalTimeoutException var7) {
         throw var7;
      }
   }

   private String getLibrary(IPalTypeSystemFile var1, String var2) {
      try {
         String var3 = "";
         if (var1.getKind() == 6) {
            var3 = this.isOpenSystemsServer() ? "SYSTEM" : "";
         } else {
            var3 = var2;
         }

         return var3;
      } catch (PalTransactions$Exception var4) {
         return null;
      }
   }

   private void handleLocking(int var1, IPalTypeSystemFile var2, String var3, String var4, int var5, int var6) throws IOException, PalResultException {
      this.pal.add((IPalType)(new PalTypeOperation(var1, 0)));
      String var7 = var3;
      if (var6 == 8 && this.getPalProperties().getNdvType() == 1) {
         var7 = "";
      }

      PalTypeLibId var8 = new PalTypeLibId(var2.getDatabaseId(), var2.getFileNumber(), var7, var2.getPassword(), var2.getCipher(), 6);
      this.pal.add((IPalType)var8);
      PalTypeObjDesc var9 = new PalTypeObjDesc(var6, var5, var4);
      this.pal.add((IPalType)var9);
      this.pal.commit();
      PalResultException var10 = this.getResultException();
      if (var10 != null) {
         throw var10;
      }
   }

   private int handleLibraryEmpty(IPalTypeSystemFile var1, String var2) throws IOException, PalResultException {
      try {
         this.pal.add((IPalType)(new PalTypeOperation(46, 0)));
         PalTypeLibId var4 = new PalTypeLibId(var1.getDatabaseId(), var1.getFileNumber(), var2, var1.getPassword(), var1.getCipher(), 6);
         this.pal.add((IPalType)var4);
         this.pal.commit();
         return this.getError();
      } catch (PalTransactions$Exception var5) {
         return 0;
      }
   }

   private int numberOfObjects(int var1, IPalTypeSystemFile var2, String var3, String var4, int var5, int var6) throws IOException, PalResultException {
      int var7 = -1;
      boolean var8 = true;

      try {
         if (var5 == 64) {
            var5 = 0;
            var6 = 32768;
         }

         this.retrievalKind = 2;
         this.pal.add((IPalType)(new PalTypeOperation(var1, 3)));
         PalTypeLibId var9 = new PalTypeLibId(var2.getDatabaseId(), var2.getFileNumber(), var3, var2.getPassword(), var2.getCipher(), 6);
         this.pal.add((IPalType)var9);
         PalTypeObjDesc2 var10 = new PalTypeObjDesc2(var6, var5, var4);
         this.pal.add((IPalType)var10);
         PalTypeNotify var11 = new PalTypeNotify(17);
         this.pal.add((IPalType)var11);
         this.pal.commit();
         PalResultException var12 = this.getResultException();
         if (var12 != null) {
            if (var12.getErrorNumber() != 82) {
               throw var12;
            }

            var8 = false;
         }

         if (var8) {
            IPalTypeNotify[] var13 = (IPalTypeNotify[])this.pal.retrieve(19);
            IPalTypeGeneric[] var14 = (IPalTypeGeneric[])this.pal.retrieve(20);
            this.isNotifyActive = false;
            if (var13 != null && var14 != null && var14[0].getData() > 0) {
               this.isNotifyActive = true;
               var7 = var14[0].getData();
            }
         }

         return var7;
      } catch (PalTimeoutException var15) {
         throw var15;
      }
   }

   private IPalTypeObject[] objectsFirst(int var1, IPalTypeSystemFile var2, String var3, String var4, int var5, int var6, int var7) throws IOException, PalResultException {
      boolean var8 = true;
      IPalTypeObject[] var9 = null;

      try {
         if (var5 == 64) {
            var5 = 0;
            var6 = 32768;
         }

         this.retrievalKind = 2;
         this.pal.add((IPalType)(new PalTypeOperation(var1, var7)));
         PalTypeLibId var10 = new PalTypeLibId(var2.getDatabaseId(), var2.getFileNumber(), var3, var2.getPassword(), var2.getCipher(), 6);
         this.pal.add((IPalType)var10);
         PalTypeObjDesc2 var11 = new PalTypeObjDesc2(var6, var5, var4);
         this.pal.add((IPalType)var11);
         PalTypeNotify var12 = new PalTypeNotify(17);
         this.pal.add((IPalType)var12);
         this.pal.commit();
         PalResultException var13 = this.getResultException();
         if (var13 != null) {
            if (var13.getErrorNumber() != 82) {
               throw var13;
            }

            var8 = false;
         }

         if (var8) {
            IPalTypeNotify[] var14 = (IPalTypeNotify[])this.pal.retrieve(19);
            IPalTypeGeneric[] var15 = (IPalTypeGeneric[])this.pal.retrieve(20);
            boolean var16 = false;
            if (var14 != null && var15 != null && var15[0].getData() > 0) {
               var16 = true;
            }

            var9 = this.nextObjectsChunk(var16);
         }

         if (var9 == null) {
            this.currentNotify = 0;
         }

         return var9;
      } catch (PalTimeoutException var17) {
         throw var17;
      }
   }

   private PalTypeLibrary[] nextLibrariesChunk(boolean var1) throws IOException, PalResultException {
      PalTypeLibrary[] var2 = null;

      try {
         if (var1) {
            this.currentNotify = this.notifyContinue();
         }

         PalResultException var3 = this.getResultException();
         if (var3 != null) {
            throw var3;
         } else {
            var2 = (PalTypeLibrary[])this.pal.retrieve(5);
            if (this.isDuplicatePossible && var2 != null) {
               ArrayList var4 = new ArrayList();

               for(PalTypeLibrary var5 : var2) {
                  if (!this.serverList.contains(var5.getLibrary())) {
                     var4.add(var5);
                     this.serverList.add(var5.getLibrary());
                  }
               }

               var2 = (PalTypeLibrary[])var4.toArray(new PalTypeLibrary[var4.size()]);
            }

            return var2;
         }
      } catch (PalTimeoutException var9) {
         throw var9;
      }
   }

   private IPalTypeObject[] nextObjectsChunk(boolean var1) throws IOException, PalResultException {
      IPalTypeObject[] var2 = null;

      try {
         if (var1) {
            this.currentNotify = this.notifyContinue();
         } else {
            this.currentNotify = 7;
         }

         PalResultException var3 = this.getResultException();
         if (var3 != null) {
            throw var3;
         } else {
            var2 = (IPalTypeObject[])this.pal.retrieve(8);
            if (var2 != null && this.isDuplicatePossible) {
               ArrayList var4 = new ArrayList();

               for(IPalTypeObject var5 : var2) {
                  String var9 = var5.getType() == 8 ? var5.getLongName() : var5.getName();
                  if (!this.serverList.contains(var9)) {
                     var4.add(var5);
                     this.serverList.add(var9);
                  }
               }

               var2 = (IPalTypeObject[])var4.toArray(new IPalTypeObject[var4.size()]);
            }

            return var2;
         }
      } catch (PalTimeoutException var10) {
         throw var10;
      }
   }

   private boolean duplicatesPossible(String var1) {
      return this.palProperties != null && this.palProperties.getNdvType() == 1 && var1.contains(";");
   }

   private int notifyContinue() throws IOException, PalResultException {
      int var1 = 0;

      try {
         PalTypeNotify var2 = new PalTypeNotify(4);
         this.pal.add((IPalType)var2);
         this.pal.commit();
         IPalTypeNotify[] var3 = (IPalTypeNotify[])this.pal.retrieve(19);
         var1 = var3[0].getNotification();
         return var1;
      } catch (PalTimeoutException var4) {
         throw var4;
      }
   }

   private int getError() throws IOException {
      int var1 = 0;

      try {
         this.errorKind = 0;
         IPalTypeResult[] var2 = (IPalTypeResult[])this.pal.retrieve(10);
         if (var2 != null) {
            var1 = var2[0].getNaturalResult();
         }

         if (var1 == 0) {
            IPalTypeResultEx[] var3 = (IPalTypeResultEx[])this.pal.retrieve(11);
            if (var3 != null) {
               String var4 = var3[0].getShortText();
               if (var4.length() > 0) {
                  this.errorKind = 3;
                  var1 = 9999;
               }
            }

            if (var2 != null) {
               int var6 = var2[0].getSystemResult();
               if (var6 != 0) {
                  var1 = var6;
               }
            }
         } else if (var1 == 7000) {
            this.errorKind = 1;
         } else {
            this.errorKind = 2;
         }
      } catch (PalTimeoutException var5) {
         throw var5;
      }

      return var1;
   }

   private String getDetailMessage(String[] var1, String var2) {
      String var3 = var2;
      if (var1 != null) {
         StringBuilder var4 = new StringBuilder();

         for(int var5 = 0; var5 < var1.length; ++var5) {
            var4.append(var1[var5]);
            if (var5 == var1.length - 1) {
               var4.append(".");
            } else {
               var4.append("\\n");
            }
         }

         var3 = var4.toString();
      }

      return var3;
   }

   private String getErrorText() throws IOException {
      String var1 = null;

      try {
         IPalTypeResultEx[] var2 = (IPalTypeResultEx[])this.pal.retrieve(11);
         if (var2 != null) {
            var1 = var2[0].getShortText();
         }

         return var1;
      } catch (PalTimeoutException var3) {
         throw var3;
      }
   }

   private String[] getErrorTextLong() throws IOException {
      String[] var1 = null;

      try {
         PalTypeSourceCodePage[] var2 = (PalTypeSourceCodePage[])this.pal.retrieve(12);
         if (var2 != null) {
            var1 = new String[var2.length];

            for(int var3 = 0; var3 < var2.length; ++var3) {
               var1[var3] = var2[var3].getSourceRecord();
            }
         }

         return var1;
      } catch (PalTimeoutException var4) {
         throw var4;
      }
   }

   private String getLogonLib(int var1, String var2) {
      String var3 = "";
      if (var1 == 1) {
         var3 = var2.trim();
      } else {
         int var4 = var2.indexOf("LOGON");
         if (var4 != -1) {
            var3 = var2.substring(var4 + 6);
            if (var3.length() > 0) {
               var3 = var3.substring(0, var3.length() - 1);
            }
         }
      }

      if (var3.length() == 0) {
         var3 = "SYSTEM";
      }

      return var3;
   }

   private PalResultException getResultException() throws IOException {
      PalResultException var1 = null;

      try {
         int var2 = this.getError();
         if (var2 != 0) {
            IPalTypeResultEx[] var3 = (IPalTypeResultEx[])this.pal.retrieve(11);
            if (var3 != null) {
               String var4 = var3[0].getShortText();
               if (var4.length() == 0) {
                  var4 = var3[0].getSystemText();
               }

               String[] var5 = this.getErrorTextLong();
               if (var4.length() == 0) {
                  var4 = "Nat" + String.valueOf(var2) + ": " + var5[0];
               }

               this.getDetailMessage(var5, var4);
               var1 = new PalResultException(var2, 2, var4);
               var1.setLongText(var5);
               var1.setShortText(var4);
            }
         }

         return var1;
      } catch (PalTimeoutException var6) {
         throw var6;
      }
   }

   private PalCompileResultException getCompileResultException() throws IOException {
      // $FF: Couldn't be decompiled
      return null;
   }

   private void fileOperationNotifyNull(IPalTypeNotify[] var1, PalResultException var2) throws IOException, PalResultException {
      try {
         if (var1 == null) {
            if (var2 != null) {
               var2.setErrorKind(4);
               throw var2;
            } else {
               throw new IllegalArgumentException();
            }
         }
      } catch (PalTransactions$Exception var3) {
      }
   }

   private void fileOperationAbort(Set var1) throws IOException, PalResultException, IllegalArgumentException {
      try {
         PalTypeNotify var2 = new PalTypeNotify(var1.contains(EDownLoadOption.DELETE_ON_TARGET) ? 12 : 5);
         this.pal.add((IPalType)var2);
         this.pal.commit();
         PalResultException var3 = this.getResultException();
         IPalTypeNotify[] var4 = (IPalTypeNotify[])this.pal.retrieve(19);
         this.fileOperationNotifyNull(var4, var3);
         var4[0].getNotification();
      } catch (PalTimeoutException var5) {
         throw var5;
      }

   }

   private void fileOperationInitiate(int var1, IPalTypeSystemFile var2, String var3, String var4) throws IOException, PalResultException {
      try {
         this.pal.add((IPalType)(new PalTypeOperation(var1)));
         if (var1 == 1 && this.palProperties.getNdvType() == 1) {
            PalTypeLibId[] var7 = new PalTypeLibId[]{new PalTypeLibId(var2.getDatabaseId(), var2.getFileNumber(), var3, var2.getPassword(), var2.getCipher(), 6), new PalTypeLibId(var2.getDatabaseId(), var2.getFileNumber(), var3, var2.getPassword(), var2.getCipher(), 6)};
            this.pal.add((IPalType[])var7);
         } else {
            PalTypeLibId var5 = new PalTypeLibId(var2.getDatabaseId(), var2.getFileNumber(), var3, var2.getPassword(), var2.getCipher(), 6);
            this.pal.add((IPalType)var5);
         }

         if (var4 != null) {
            this.pal.add((IPalType)(new PalTypeLibId(var2.getDatabaseId(), var2.getFileNumber(), var4, var2.getPassword(), var2.getCipher(), 30)));
         }

         this.pal.commit();
         PalResultException var8 = this.getResultException();
         if (var8 != null) {
            throw var8;
         }
      } catch (PalTimeoutException var6) {
         throw var6;
      }
   }

   private IPalTypeNotify fileOperationSendDescription(PalTypeFileId param1, ITransactionContext param2) throws IOException, PalResultException, NullPointerException {
      // $FF: Couldn't be decompiled
      throw new UnsupportedOperationException("Decompilation failed");
   }

   private int calculateSourceSize(String[] var1) {
      int var2 = 0;
      byte var3 = 4;

      for(int var4 = 0; var4 < var1.length; ++var4) {
         var2 += var1[var4].length() + var3;
      }

      return var2;
   }

   private void fileOperationUploadSource(IPalTypeSystemFile var1, String var2, PalTypeFileId var3, String[] var4, IFileProperties var5, Set var6) throws IOException, PalResultException {
      PalResultException var7 = null;
      if (var3.getNatType() == 32768) {
         this.fileOperationUploadErrorMsg(var1, var2, var5.getBaseLibrary(), var3, var4, var5.getTimeStamp());
      } else {
         try {
            this.fileOperationInitiate(12, var1, var2, var5.getBaseLibrary());
            IPalTypeNotify var8 = this.fileOperationSendDescription(var3, (ITransactionContext)null);
            if (var8.getNotification() == 13) {
               try {
                  this.sourceToPal(var4, var5.getLineNumberIncrement(), var5.getInternalLabelFirst(), var5.getCodePage(), !var6.contains(EUploadOption.SOURCE_UNCHANGED), this.objectHasLineNumberReferences(var3.getNatType()));
               } catch (PalUnmappableCodePointException var11) {
                  this.pal.init();
                  throw new PalCompileResultException(3422, 3, var11.getMessage(), var11.getRow(), var11.getColumn(), var3.getNatType(), var3.getObject(), var2, var1.getDatabaseId(), var1.getFileNumber());
               }

               if (var5.getCodePage() != null) {
                  PalTypeCP var9 = new PalTypeCP();
                  var9.setCodePage(var5.getCodePage());
                  this.pal.add((IPalType)var9);
               }

               if (var5.getTimeStamp() != null) {
                  this.sendTimeStampToServer(var5.getTimeStamp());
               }

               this.pal.commit();
               IPalTypeNotify[] var13 = (IPalTypeNotify[])this.pal.retrieve(19);
               if (var5.getTimeStamp() != null) {
                  var5.getTimeStamp().copy(this.getTimeStampFromServer());
               }

               var7 = this.getResultException();
               this.fileOperationNotifyNull(var13, var7);
               if (var13[0].getNotification() == 9) {
                  this.fileOperationAbort(EnumSet.of(EDownLoadOption.NONE));
               } else if (var13[0].getNotification() == 6) {
                  this.fileOperationAbort(EnumSet.of(EDownLoadOption.NONE));
               }
            }
         } catch (NullPointerException var12) {
            throw new NullPointerException();
         }

         if (var7 != null) {
            throw var7;
         }
      }

   }

   private void fileOperationSendFiles(IPalTypeSystemFile var1, String var2, String var3, PalTypeFileId var4, Object[] var5, int var6, String var7, Set var8, PalTimeStamp var9) throws IOException, PalResultException {
      Object var10 = null;
      this.fileOperationInitiate(12, var1, var2, var3);
      IPalTypeNotify var11 = this.fileOperationSendDescription(var4, (ITransactionContext)null);
      Object var12 = null;
      if (var11.getNotification() == 13) {
         for(Object var13 : var5) {
            if (var13 instanceof String[]) {
               if (var4.getNatType() == 32768) {
                  if (var9 != null) {
                     this.sendTimeStampToServer(var9);
                  }

                  this.errorMsgFileToPal(var4, (String[])var13, false);
               } else {
                  try {
                     this.sourceToPal((String[])var13, var6, var7, (String)null, false, this.objectHasLineNumberReferences(var4.getNatType()));
                  } catch (PalUnmappableCodePointException var20) {
                     this.pal.init();
                     throw new PalCompileResultException(3422, 3, var20.getMessage(), var20.getRow(), var20.getColumn(), var4.getNatType(), var4.getObject(), var2, var1.getDatabaseId(), var1.getFileNumber());
                  }
               }
            } else if (!(var13 instanceof IPalTypeStream[])) {
               if (var13 instanceof ByteArrayOutputStream) {
                  if (((ByteArrayOutputStream)var13).size() != 0) {
                     this.binaryToPal((ByteArrayOutputStream)var13, var4.getObject());
                  } else {
                     this.pal.add((IPalType)(new PalTypeNotify(18)));
                  }
               }
            } else {
               IPalTypeStream[] var17 = (IPalTypeStream[])var13;
               PalTypeStream[] var18 = new PalTypeStream[var17.length];

               for(int var19 = 0; var19 < var17.length; ++var19) {
                  var18[var19] = new PalTypeStream(var17[var19].getStreamRecord(), this.palProperties.getNdvType());
               }

               this.pal.add((IPalType[])var18);
            }
         }
      }

      if (var4.getNatType() != 32768) {
         if (var9 != null) {
            this.sendTimeStampToServer(var9);
         }

         this.pal.commit();
      }

      IPalTypeNotify[] var22 = (IPalTypeNotify[])this.pal.retrieve(19);
      if (var9 != null) {
         var9.copy(this.getTimeStampFromServer());
      }

      PalResultException var21 = this.getResultException();
      this.fileOperationNotifyNull(var22, var21);
      if (var21 != null) {
         throw var21;
      }
   }

   private void fileOperationUploadErrorMsg(IPalTypeSystemFile var1, String var2, String var3, PalTypeFileId var4, String[] var5, PalTimeStamp var6) throws IOException, PalResultException {
      PalResultException var7 = null;

      try {
         int var8 = this.errorMsgFileToPal(var4, var5, true);
         if (var8 != -1) {
            String var12 = String.format("Error message file %s contains invalid format in line %d", var4.getNewObject(), var8);
            throw new IllegalArgumentException(var12);
         }

         this.fileOperationInitiate(12, var1, var2, var3);
         IPalTypeNotify var9 = this.fileOperationSendDescription(var4, (ITransactionContext)null);
         if (var9.getNotification() == 13) {
            if (var6 != null) {
               this.sendTimeStampToServer(var6);
            }

            this.errorMsgFileToPal(var4, var5, false);
            IPalTypeNotify[] var10 = (IPalTypeNotify[])this.pal.retrieve(19);
            if (var6 != null) {
               var6.copy(this.getTimeStampFromServer());
            }

            var7 = this.getResultException();
            this.fileOperationNotifyNull(var10, var7);
            if (var10[0].getNotification() == 9) {
               this.fileOperationAbort(EnumSet.of(EDownLoadOption.NONE));
            } else if (var10[0].getNotification() == 6) {
               this.fileOperationAbort(EnumSet.of(EDownLoadOption.NONE));
            }
         }
      } catch (NullPointerException var11) {
         throw new NullPointerException();
      }

      if (var7 != null) {
         throw var7;
      }
   }

   private void fileOperationUploadBinary(IPalTypeSystemFile var1, String var2, PalTypeFileId var3, ByteArrayOutputStream var4, PalTimeStamp var5, String var6) throws IOException, PalResultException {
      PalResultException var7 = null;

      try {
         this.fileOperationInitiate(12, var1, var2, var6);
         IPalTypeNotify var8 = this.fileOperationSendDescription(var3, (ITransactionContext)null);
         if (var8.getNotification() == 13) {
            this.binaryToPal(var4, var3.getObject());
            if (var5 != null) {
               this.sendTimeStampToServer(var5);
            }

            this.pal.commit();
            IPalTypeNotify[] var9 = (IPalTypeNotify[])this.pal.retrieve(19);
            if (var5 != null) {
               var5.copy(this.getTimeStampFromServer());
            }

            var7 = this.getResultException();
            this.fileOperationNotifyNull(var9, var7);
            if (var9[0].getNotification() == 9) {
               this.fileOperationAbort(EnumSet.of(EDownLoadOption.NONE));
            } else if (var9[0].getNotification() == 6) {
               this.fileOperationAbort(EnumSet.of(EDownLoadOption.NONE));
            }
         }
      } catch (NullPointerException var10) {
         throw new NullPointerException();
      }

      if (var7 != null) {
         throw var7;
      }
   }

   private IDownloadResult fileOperationDownloadSource(ITransactionContextDownload var1, IPalTypeSystemFile var2, String var3, IFileProperties var4, Set var5) throws UnsupportedEncodingException, IOException, PalResultException {
      IDownloadResult var6 = null;
      PalTypeFileId var7 = new PalTypeFileId();
      var7.setObject(var4.getName());
      var7.setNewObject(var4.getLongName());
      if (var4.getType() == 32768) {
         var7.setNatKind(0);
      } else {
         var7.setNatKind(1);
      }

      var7.setNatType(var4.getType());
      var7.setStructured(var4.isStructured());
      if (var2.getKind() == 6) {
         var7.setNatKind(1);
         var7.setNatType(8);
      }

      ContextDownload var8 = (ContextDownload)var1;
      if (this.isAutomaticLogon() && var8 == null && var4.getType() == 8 && var3.length() > 0) {
         this.logon(var3);
      }

      boolean var9 = false;

      try {
         if (var8 == null || !var8.isStarted()) {
            if (var8 != null) {
               PalTransactions.ContextDownload.access$5(var8, var5);
               PalTransactions.ContextDownload.access$6(var8, true);
            }

            this.fileOperationInitiate(var5.contains(EDownLoadOption.DELETE_ON_TARGET) ? 43 : 11, var2, var3, var4.getBaseLibrary());
         }

         if (var4.getTimeStamp() != null) {
            this.sendTimeStampToServer(var4.getTimeStamp());
         }

         Object var10 = null;

         try {
            IPalTypeNotify var35 = this.fileOperationSendDescription(var7, var1);
            if (var7.getNatType() == 32768) {
               var6 = this.fileOperationDownloadErrorMsg(var2, var3, var7);
            } else if (var35.getNotification() == 6) {
               boolean var11 = false;
               if (this.isIBM420CodePage(var4.getCodePage()) || var4.getCodePage().isEmpty() && this.isIBM420CodePage(this.naturalParameters.getRegional().getCodePage())) {
                  var11 = true;
               }

               var6 = this.sourceFromPal(var2, var3, var7.getObject(), !var5.contains(EDownLoadOption.KEEP_LINE_NUMBERS), this.objectHasLineNumberReferences(var7.getNatType()), var11);
               if (var4.getTimeStamp() != null) {
                  var4.getTimeStamp().copy(this.getTimeStampFromServer());
               }
            }
         } finally {
            if (var4.getTimeStamp() != null) {
               var4.getTimeStamp().copy(this.getTimeStampFromServer());
            }

         }

         if (var8 == null) {
            this.fileOperationAbort(var5);
         }
      } catch (NullPointerException var29) {
         var9 = true;
         throw new NullPointerException();
      } catch (PalUnmappableCodePointException var30) {
         if (var8 == null) {
            this.pal.init();
            this.fileOperationAbort(var5);
         }

         throw var30;
      } catch (UnsupportedEncodingException var31) {
         if (var8 == null) {
            this.fileOperationAbort(var5);
         }

         throw var31;
      } catch (PalResultException var32) {
         if (var32.getErrorKind() == 4) {
            var9 = true;
         }

         throw var32;
      } catch (InvalidSourceException var33) {
         if (var8 == null) {
            this.fileOperationAbort(var5);
         }

         throw var33;
      } finally {
         if (var8 != null) {
            PalTransactions.ContextDownload.access$4(var8, var9);
         }

      }

      return var6;
   }

   private boolean objectHasLineNumberReferences(int var1) {
      return var1 != 4 && var1 != 1 && var1 != 2 && var1 != 4096 && var1 != 8;
   }

   private ByteArrayOutputStream fileOperationDownloadBinary(ITransactionContext var1, IPalTypeSystemFile var2, String var3, IFileProperties var4) throws IOException, PalResultException {
      ByteArrayOutputStream var5 = null;
      boolean var6 = false;
      ContextDownload var7 = (ContextDownload)var1;

      try {
         PalTimeStamp var8 = var4.getTimeStamp();
         PalTypeFileId var9 = new PalTypeFileId();
         var9.setObject(var4.getName());
         var9.setNewObject(var4.getLongName());
         var9.setNatType(var4.getType());
         if (var4.getType() == 65536) {
            var9.setNatKind(16);
         } else {
            var9.setNatKind(2);
         }

         if (var7 == null || !var7.isStarted()) {
            if (var7 != null) {
               PalTransactions.ContextDownload.access$5(var7, EnumSet.of(EDownLoadOption.NONE));
               PalTransactions.ContextDownload.access$6(var7, true);
            }

            this.fileOperationInitiate(11, var2, var3, var4.getBaseLibrary());
         }

         if (var8 != null) {
            this.sendTimeStampToServer(var8);
         }

         Object var10 = null;
         IPalTypeNotify var22 = null;

         try {
            var22 = this.fileOperationSendDescription(var9, var1);
         } finally {
            if (var8 != null) {
               var8.copy(this.getTimeStampFromServer());
            }

         }

         if (var22.getNotification() == 6) {
            var5 = this.binaryFromPal(var2, var3, var9.getObject());
            if (var7 == null) {
               this.fileOperationAbort(EnumSet.of(EDownLoadOption.NONE));
            }
         } else if (var22.getNotification() == 18) {
            var5 = new ByteArrayOutputStream();
         }
      } catch (NullPointerException var20) {
         var6 = true;
         throw new NullPointerException();
      } finally {
         if (var7 != null) {
            PalTransactions.ContextDownload.access$4(var7, var6);
         }

      }

      return var5;
   }

   private Object[] fileOperationReceiveFiles(IPalTypeSystemFile param1, String param2, PalTypeFileId param3, Set param4, PalTimeStamp param5) throws UnsupportedEncodingException, IOException, PalResultException {
      // $FF: Couldn't be decompiled
      throw new UnsupportedOperationException("Decompilation failed");
   }

   private IDownloadResult fileOperationDownloadErrorMsg(IPalTypeSystemFile param1, String param2, IPalTypeFileId param3) throws IOException, PalResultException {
      // $FF: Couldn't be decompiled
      throw new UnsupportedOperationException("Decompilation failed");
   }

   private IDownloadResult sourceFromPal(IPalTypeSystemFile var1, String var2, String var3, boolean var4, boolean var5, boolean var6) throws UnsupportedEncodingException, IOException {
      Object var7 = null;
      int var8 = 0;
      Object var9 = null;
      int var10 = 1;

      try {
         Object var11 = (PalTypeSourceUnicode[])this.pal.retrieve(42);
         if (var11 == null) {
            var11 = (PalTypeSourceCodePage[])this.pal.retrieve(12);
            if (var11 == null) {
               var11 = (PalTypeSourceCP[])this.pal.retrieve(48);
               if (var11 != null) {
                  String var21 = this.getCharsetName();

                  for(int var13 = 0; var13 < ((Object[])var11).length; ++var13) {
                     ((IPalTypeSource)((Object[])var11)[var13]).convert(var21);
                     ++var10;
                  }
               }
            }
         }

         if (var11 != null) {
            if (var4) {
               boolean var22 = this.palPreferences != null ? this.palPreferences.replaceLineNoRefsWithLabels() : false;
               var9 = this.extractLineNumbers4Pal((IPalTypeSource[])var11, var5, var22, var22 ? this.palPreferences.createLabelsInNewLine() : false, var22 ? this.convertLabel2Internal(this.palPreferences.getLabelFormat()) : "", var6);
            } else {
               int var23 = 0;
               String[] var18 = new String[((Object[])var11).length];

               for(int var24 = 0; var24 < ((Object[])var11).length; ++var24) {
                  var18[var24] = ((IPalTypeSource)((Object[])var11)[var24]).getSourceRecord();
                  if (var23 == 0) {
                     var23 = Integer.valueOf(var18[var24].substring(0, 4));
                  } else if (var8 == 0) {
                     var8 = Integer.valueOf(var18[var24].substring(0, 4)) - var23;
                  }
               }

               var8 = var8 == 0 ? 1 : var8;
               if (var6 && this.shapingContext != null) {
                  for(String var25 : var18) {
                     this.shapingContext.unshape(var25);
                  }
               }

               var9 = new DownloadResult(var18, var8, (DownloadResult)null);
            }
         } else {
            var9 = new DownloadResult(NULL_STRING_ARRAY, 0, (DownloadResult)null);
         }
      } catch (Exception var17) {
         if (var17 instanceof PalUnmappableCodePointException) {
            this.pal.init();
            String var12 = String.format("Conversion error in line %d: %s", var10, var17.getMessage());
            throw new PalUnmappableCodePointException(var12, ((PalUnmappableCodePointException)var17).getPalTypeSource(), ((PalUnmappableCodePointException)var17).getColumn());
         }

         throw new InvalidSourceException("illegal Natural source", var17);
      }

      return (IDownloadResult)var9;
   }

   private String convertLabel2Internal(String var1) {
      StringBuffer var2 = new StringBuffer(var1);
      int var3 = var2.indexOf("{count}");
      if (var3 != -1) {
         var2.replace(var3, var3 + 7, "%d");
         var2.append(".");
      } else {
         var2.append("%d.");
      }

      return var2.toString();
   }

   private boolean isRenConst() {
      boolean var1 = false;

      try {
         var1 = this.getServerConfiguration(false).getRenConst();
      } catch (PalResultException | IOException var2) {
      }

      return var1;
   }

   private IDownloadResult extractLineNumbers4Pal(IPalTypeSource[] var1, boolean var2, final boolean var3, final boolean var4, final String var5, boolean var6) {
      ArrayList var7 = new ArrayList();
      int var8 = 0;
      int var9 = 0;

      for(int var10 = 0; var10 < var1.length; ++var10) {
         String var11 = var1[var10].getSourceRecord();
         if (var6 && this.shapingContext != null && var11.length() > 0) {
            var11 = this.shapingContext.unshape(var11);
         }

         var7.add(new StringBuffer(var11));
         if (this.isOpenSystemsServer() && ((StringBuffer)var7.get(var10)).length() > 1) {
            ((StringBuffer)var7.get(var10)).deleteCharAt(((StringBuffer)var7.get(var10)).length() - 1);
         }

         try {
            if (var8 == 0) {
               var8 = Integer.valueOf(((StringBuffer)var7.get(var10)).substring(0, 4));
            } else if (var9 == 0) {
               var9 = Integer.valueOf(((StringBuffer)var7.get(var10)).substring(0, 4)) - var8;
            }
         } catch (NumberFormatException var12) {
            var9 = 1;
         }
      }

      var9 = var9 == 0 ? 1 : var9;
      return new DownloadResult(RenumberSource.removeLineNumbers(var7, var2, this.isRenConst(), 5, var9, new IInsertLabels() {
         public boolean isInsertLabels() {
            return var3;
         }

         public boolean isCreateNewLine() {
            return var4;
         }

         public String getLabelFormat() {
            return var5;
         }
      }), var9, (DownloadResult)null);
   }

   private String[] errMsgFromPal(IPalTypeSystemFile param1, String param2, String param3) throws IOException {
      // $FF: Couldn't be decompiled
      throw new UnsupportedOperationException("Decompilation failed");
   }

   private ByteArrayOutputStream binaryFromPal(IPalTypeSystemFile param1, String param2, String param3) throws IOException {
      // $FF: Couldn't be decompiled
      throw new UnsupportedOperationException("Decompilation failed");
   }

   private boolean isMainframeProfileOrCoverageResource(String var1) {
      boolean var2 = false;
      String var3 = "";
      if (this.palProperties.getNdvType() == 1) {
         int var4 = var1.indexOf(46);
         if (var4 != -1) {
            var3 = var1.substring(var4 + 1);
            var2 = var3.equalsIgnoreCase("ncvf") || var3.equalsIgnoreCase("nprf") || var3.equalsIgnoreCase("nprc");
         }
      }

      return var2;
   }

   private void sourceToPal(String[] var1, int var2, String var3, String var4, boolean var5, boolean var6) throws IOException {
      PalTypeSource[] var7 = new PalTypeSource[var1.length];

      try {
         Class var8 = this.getSourceClass();
         String var18 = var4;
         if (var4 == null || var4.length() == 0) {
            var18 = this.getPalProperties().getDefaultCodePage();
         }

         if (this.isIBM420CodePage(var18) && this.shapingContext != null) {
            for(int var19 = 0; var19 < var1.length; ++var19) {
               var1[var19] = this.shapingContext.shapeIBM420(var1[var19]);
            }
         }

         if (!var5) {
            for(int var21 = 0; var21 < var1.length; ++var21) {
               var7[var21] = (PalTypeSource)var8.newInstance();
               var7[var21].setPalVers(this.getPalProperties().getPalVersion());
               if (this.isOpenSystemsServer()) {
                  var7[var21].setSourceRecord(var1[var21] + " ");
               } else {
                  var7[var21].setSourceRecord(var1[var21]);
               }

               var7[var21].setCharSetName(var18);
            }
         } else {
            String var20 = this.getInternalLabelPrefix();
            if (var3 != null) {
               for(int var11 = 0; var11 < var3.length(); ++var11) {
                  if (var3.charAt(var11) != ' ') {
                     var20 = var3;
                     break;
                  }
               }
            }

            StringBuffer[] var22 = RenumberSource.addLineNumbers(var1, var2, var20, var6, this.isOpenSystemsServer(), this.isRenConst());

            for(int var12 = 0; var12 < var1.length; ++var12) {
               var7[var12] = (PalTypeSource)var8.newInstance();
               var7[var12].setPalVers(this.getPalProperties().getPalVersion());
               var7[var12].setNdvType(this.getPalProperties().getNdvType());
               var7[var12].setSourceRecord(var22[var12].toString());
               var7[var12].setCharSetName(var18);
            }
         }

         this.pal.add((IPalType[])var7);
      } catch (InstantiationException var13) {
         var13.printStackTrace();
      } catch (IllegalAccessException var14) {
         var14.printStackTrace();
      } catch (PalTimeoutException var15) {
         throw var15;
      } catch (PalUnmappableCodePointException var16) {
         this.pal.init();
         PalUnmappableCodePointException var9 = var16;

         for(int var10 = 0; var10 < var7.length; ++var10) {
            if (var7[var10] == var9.getPalTypeSource()) {
               var9.setRow(var10 + 1);
               throw var9;
            }
         }
      } catch (Throwable var17) {
         this.pal.init();
         throw var17;
      }

   }

   private int errorMsgFileToPal(IPalTypeFileId var1, String[] var2, boolean var3) throws IOException, PalResultException {
      Object var4 = null;
      int var5 = 0;
      int var6 = 0;
      int var7 = 0;
      int var8 = 0;
      if (var2[0].length() > 8) {
         return 0;
      } else {
         try {
            var7 = Integer.parseInt(var2[1]);
         } catch (NumberFormatException var16) {
            return 1;
         }

         try {
            var8 = Integer.parseInt(var2[2]);
         } catch (NumberFormatException var15) {
            return 2;
         }

         if (var8 < var7) {
            return 2;
         } else {
            String var9 = var2[2].substring(0, 4);
            boolean var10 = false;
            var5 += 3;

            while(var5 < var2.length && !var10) {
               String[] var11 = new String[100];
               PalTypeFileId var12 = null;
               int var13 = 0;
               var6 = var5;
               if (!var3) {
                  var12 = new PalTypeFileId();
                  var12.setNatType(var1.getNatType());
                  var12.setNatKind(var1.getNatKind());
                  var12.setNewObject(var2[var5].substring(0, 4));
                  if (var2[var5].substring(0, 4).equals(var9)) {
                     var12.setObject("9999");
                     var10 = true;
                  } else {
                     var12.setObject(var1.getObject());
                  }
               } else if (var2[var5].substring(0, 4).equals(var9)) {
                  var10 = true;
               }

               var11[var13] = var2[var5];
               ++var5;
               ++var13;

               for(; var5 < var2.length && (var2[var5].length() == 0 || var2[var5].substring(0, 1).equals("#")) && var13 < 100; ++var5) {
                  if (var2[var5].length() > 0) {
                     var11[var13] = var2[var5];
                     ++var13;
                  }
               }

               if (var3) {
                  int var14 = this.errorMsgToPal((PalTypeFileId)null, var11, var13);
                  if (var14 != -1) {
                     return var6 + var14 + 1;
                  }
               } else {
                  if (var5 == var2.length) {
                     var12.setObject("9999");
                  }

                  this.errorMsgToPal(var12, var11, var13);
                  PalResultException var17 = this.getResultException();
                  if (var17 != null) {
                     throw var17;
                  }
               }
            }

            return -1;
         }
      }
   }

   private int errorMsgToPal(PalTypeFileId var1, String[] var2, int var3) throws IOException {
      try {
         int var5 = 1;
         String var6 = blankLine;
         PalTypeSourceCodePage[] var7 = null;
         if (this.palProperties.getNdvType() == 1) {
            var6 = emptyLine;
         }

         if (var1 != null) {
            var7 = new PalTypeSourceCodePage[errorMessageLines];
            var7[0] = new PalTypeSourceCodePage();
         }

         if (var2[0].length() <= 6) {
            return 0;
         } else {
            for(int var8 = 0; var8 < 4; ++var8) {
               if (!Character.isDigit(var2[0].charAt(var8))) {
                  return 0;
               }
            }

            if (var2[0].charAt(4) != 'E') {
               return 0;
            } else {
               if (var1 != null) {
                  var7[0].setSourceRecord(var2[0].substring(6));

                  for(int var4 = 1; var4 < errorMessageLines; ++var4) {
                     var7[var4] = new PalTypeSourceCodePage();
                     var7[var4].setSourceRecord(var6);
                  }
               }

               int var13 = 1;
               int var9 = longTextEndLineNumber;
               int var10 = explanationEndLineNumber;

               for(byte var11 = -1; var5 < var3; ++var5) {
                  if (!var2[var5].equals(separatorLine) && var2[var5].charAt(0) != ';' && !var2[var5].equals(blankLine)) {
                     if (var2[var5].equals(textLine)) {
                        var11 = 0;
                     } else if (var2[var5].equals(explanationLine)) {
                        var11 = 1;
                     } else if (var2[var5].equals(actionLine)) {
                        var11 = 2;
                     } else {
                        if (!var2[var5].substring(0, 1).equals(textLineLead)) {
                           return var5;
                        }

                        if (var11 == 0) {
                           if (var13 < longTextEndLineNumber) {
                              if (var1 != null) {
                                 if (var2[var5].length() > 3) {
                                    var7[var13].setSourceRecord(var2[var5].substring(3));
                                 } else {
                                    var7[var13].setSourceRecord(var2[var5].substring(1));
                                 }
                              }

                              ++var13;
                           }
                        } else if (var11 == 1) {
                           if (var9 < explanationEndLineNumber) {
                              if (var1 != null) {
                                 if (var2[var5].length() > 3) {
                                    var7[var9].setSourceRecord(var2[var5].substring(3));
                                 } else {
                                    var7[var9].setSourceRecord(var2[var5].substring(1));
                                 }
                              }

                              ++var9;
                           }
                        } else if (var11 == 2 && var10 < errorMessageLines) {
                           if (var1 != null) {
                              if (var2[var5].length() > 3) {
                                 var7[var10].setSourceRecord(var2[var5].substring(3));
                              } else {
                                 var7[var10].setSourceRecord(var2[var5].substring(1));
                              }
                           }

                           ++var10;
                        }
                     }
                  }
               }

               if (var1 != null) {
                  this.pal.add((IPalType)var1);
                  this.pal.add((IPalType[])var7);
                  this.pal.commit();
               }

               return -1;
            }
         }
      } catch (PalTransactions$Exception var12) {
         return 0;
      }
   }

   private void binaryToPal(ByteArrayOutputStream var1, String var2) throws IOException {
      try {
         if (this.palProperties.getNdvType() == 1) {
            ByteBuffer var13 = ByteBuffer.wrap(var1.toByteArray());
            int var14 = 0;
            int var5 = 0;
            int var6 = var13.capacity() % 253 == 0 ? var13.capacity() / 253 : var13.capacity() / 253 + 1;
            int var7 = 0;
            PalTypeStream[] var8 = new PalTypeStream[var6];

            for(boolean var9 = this.isMainframeProfileOrCoverageResource(var2); var14 < var13.capacity(); ++var7) {
               if (var13.capacity() - var14 < 253) {
                  var5 = var13.capacity() - var14;
               } else {
                  var5 = 253;
               }

               byte[] var10 = new byte[var5];

               for(int var11 = 0; var11 < var5; ++var11) {
                  var10[var11] = var13.get();
               }

               var14 += var5;
               var8[var7] = new PalTypeStream(var10, this.palProperties.getNdvType());
               if (var9) {
                  var8[var7].convert(this.palProperties.getDefaultCodePage(), false);
               }
            }

            this.pal.add((IPalType[])var8);
         } else {
            byte[] var3 = var1.toByteArray();
            if (var3.length > 0) {
               PalTypeStream var4 = new PalTypeStream(var1.toByteArray(), this.palProperties.getNdvType());
               this.pal.add((IPalType)var4);
            } else {
               this.pal.add((IPalType)(new PalTypeNotify(18)));
            }
         }
      } catch (PalTimeoutException var12) {
         throw var12;
      }

   }

   private Class getSourceClass() {
      int var1 = this.palProperties.getNdvType();
      int var2 = this.getNdvMajorVersion();
      Object var3 = null;
      Class var4;
      if (var1 == 1) {
         if (this.getPalProperties().isMfUnicodeSrcPossible()) {
            var4 = PalTypeSourceUnicode.class;
         } else if (var2 >= 224) {
            var4 = PalTypeSourceCP.class;
         } else {
            var4 = PalTypeSourceCodePage.class;
         }
      } else if (var2 >= 220) {
         var4 = PalTypeSourceUnicode.class;
      } else {
         var4 = PalTypeSourceCodePage.class;
      }

      return var4;
   }

   private String getCharsetName() throws IOException {
      Object var1 = null;

      try {
         String var5 = this.getPalProperties().getDefaultCodePage();
         IPalTypeCP[] var2 = (IPalTypeCP[])this.pal.retrieve(45);
         if (var2 != null) {
            String var3 = var2[0].getCodePage();
            if (var3 != null && var3.length() != 0) {
               var5 = var3.trim();
            }
         }

         return var5;
      } catch (PalTimeoutException var4) {
         throw var4;
      }
   }

   private void doLogon(String var1, IPalTypeLibId[] var2) throws IOException, PalResultException {
      try {
         PalTrace.header("logon");
         this.pal.add((IPalType)(new PalTypeOperation(2, 12)));
         this.pal.add((IPalType)(new PalTypeStack("LOGON " + var1)));
         this.pal.add((IPalType[])var2);
         this.pal.commit();
         PalResultException var3 = this.getResultException();
         if (var3 != null) {
            throw var3;
         }
      } catch (PalTransactions$Exception var4) {
      }
   }

   private void handleCommand(IPalTypeSystemFile var1, String var2, String var3, String var4, int var5, String var6, boolean var7, boolean var8, String[] var9, int var10, String var11, int var12, int var13, boolean var14, PalTimeStamp var15, boolean var16) throws IOException, PalResultException {
      if (var1 == null) {
         throw new IllegalArgumentException("systemFileKey must not be null");
      } else if (var2 == null) {
         throw new IllegalArgumentException("library must not be null");
      } else if (var4 == null) {
         throw new IllegalArgumentException("sourceName must not be null");
      } else if (!ObjectType.getInstanceIdExtension().containsKey(var5)) {
         throw new IllegalArgumentException("type must be one of the ids defined inside utility class 'sag.pal.ObjectType'");
      } else {
         byte var17 = 28;
         if (this.isAutomaticLogon() && var2.length() > 0) {
            this.logon(var2);
         }

         if (var9 != null) {
            String var18 = var11;
            if (var11 != null && !var11.trim().equals("") && !Arrays.asList(this.getCodePages()).contains(new PalTypeCP(var11))) {
               var18 = this.getPalProperties().getDefaultCodePage();
            }

            try {
               this.sourceToPal(var9, var10, this.getInternalLabelPrefix(), var18, true, this.objectHasLineNumberReferences(var5));
            } catch (PalUnmappableCodePointException var21) {
               this.pal.init();
               throw new PalCompileResultException(3422, 3, var21.getMessage(), var21.getRow(), var21.getColumn(), var5, var4, var2, var1.getDatabaseId(), var1.getFileNumber());
            }

            if (var18 != null) {
               PalTypeCP var19 = new PalTypeCP();
               var19.setCodePage(var18);
               this.pal.add((IPalType)var19);
            }

            var17 = 2;
         } else if (var5 == 8) {
            if (var16) {
               var9 = this.readInternal(var1, "", var4, EnumSet.of(EReadOption.READDDM), false);
            } else {
               var9 = this.readInternal(var1, var2, var4, EnumSet.of(EReadOption.READDDM), false);
            }

            if (this.palProperties.getNdvType() == 1) {
               try {
                  this.sourceToPal(var9, var10, this.getInternalLabelPrefix(), (String)null, false, this.objectHasLineNumberReferences(var5));
               } catch (PalUnmappableCodePointException var22) {
                  this.pal.init();
                  throw new PalCompileResultException(3422, 3, var22.getMessage(), var22.getRow(), var22.getColumn(), var5, var4, var2, var1.getDatabaseId(), var1.getFileNumber());
               }
            }
         }

         if (var6.equals("SAVE")) {
            var17 = 4;
         }

         this.pal.add((IPalType)(new PalTypeOperation(2, var17)));
         this.pal.add((IPalType)(new PalTypeStack(var6)));
         this.pal.add((IPalType)(new PalTypeLibId(var1.getDatabaseId(), var1.getFileNumber(), var2, var1.getPassword(), var1.getCipher(), 6)));
         if (var3 != null) {
            this.pal.add((IPalType)(new PalTypeLibId(var1.getDatabaseId(), var1.getFileNumber(), var3, var1.getPassword(), var1.getCipher(), 30)));
         }

         PalTypeSrcDesc var24 = var5 == 8 ? new PalTypeSrcDesc(var5, var4, var7, var12, var13) : new PalTypeSrcDesc(var5, var4, var7, var14 ? 1 : 0);
         this.pal.add((IPalType)var24);
         if (var15 != null) {
            this.sendTimeStampToServer(var15);
         }

         this.pal.commit();
         PalCompileResultException var25 = this.getCompileResultException();
         if (var15 != null) {
            var15.copy(this.getTimeStampFromServer());
         }

         if (var25 != null) {
            throw var25;
         }
      }
   }

   private void sendTimeStampToServer(PalTimeStamp var1) throws IOException {
      try {
         this.pal.add((IPalType)(new PalTypeTimeStamp(var1.getFlags(), var1.getCompactString(), var1.getUser())));
      } catch (PalTransactions$Exception var2) {
      }
   }

   private PalTimeStamp getTimeStampFromServer() throws IOException {
      try {
         PalTimeStamp var1 = null;
         IPalTypeTimeStamp[] var2 = (IPalTypeTimeStamp[])this.pal.retrieve(54);
         if (var2 != null) {
            var1 = PalTimeStamp.get(var2[0].getTimeStamp(), var2[0].getUserId().trim());
         }

         return var1 == null ? PalTimeStamp.get() : var1;
      } catch (PalTransactions$Exception var3) {
         return null;
      }
   }

   private void fileOperationDelete(IPalTypeSystemFile var1, String var2, int var3, int var4, String var5, String var6) throws IOException, PalResultException {
      if (var1 == null) {
         throw new IllegalArgumentException("systemFileKey must not be null");
      } else if (var5 == null) {
         throw new IllegalArgumentException("objectName must not be null");
      } else {
         try {
            this.fileOperationInitiate(1, var1, var2.length() == 0 && this.isOpenSystemsServer() ? "SYSTEM" : var2, var6);
            PalTypeFileId var7 = new PalTypeFileId();
            var7.setNatKind(var4);
            if (var1.getKind() == 6) {
               var7.setNatType(8);
            } else {
               var7.setNatType(var3);
            }

            if (var4 == 64) {
               var7.setNatType(32768);
            } else if (var4 == 16) {
               var7.setNatType(65536);
            }

            var7.setObject(var5);
            IPalTypeNotify var8 = this.fileOperationSendDescription(var7, (ITransactionContext)null);
            if (var8.getNotification() == 6) {
               this.fileOperationAbort(EnumSet.of(EDownLoadOption.NONE));
            }

         } catch (NullPointerException var9) {
            throw new NullPointerException();
         } catch (PalTimeoutException var10) {
            throw var10;
         }
      }
   }

   private void fileOperationServerLocal(int var1, IPalTypeSystemFile var2, String var3, IPalTypeSystemFile var4, String var5, PalTypeFileId var6) throws IOException, PalResultException {
      if (var2 != null && var4 != null) {
         try {
            this.pal.add((IPalType)(new PalTypeOperation(var1)));
            PalTypeLibId[] var7 = new PalTypeLibId[]{new PalTypeLibId(var2.getDatabaseId(), var2.getFileNumber(), var3, var2.getPassword(), var2.getCipher(), 6), new PalTypeLibId(var4.getDatabaseId(), var4.getFileNumber(), var5, var4.getPassword(), var4.getCipher(), 6)};
            this.pal.add((IPalType[])var7);
            this.pal.commit();
            PalResultException var8 = this.getResultException();
            if (var8 != null) {
               throw var8;
            } else {
               IPalTypeNotify var9 = this.fileOperationSendDescription(var6, (ITransactionContext)null);
               if (var9.getNotification() == 6) {
                  this.fileOperationAbort(EnumSet.of(EDownLoadOption.NONE));
               }

            }
         } catch (NullPointerException var10) {
            throw new NullPointerException();
         } catch (PalTimeoutException var11) {
            throw var11;
         }
      } else {
         throw new IllegalArgumentException("systemFileKey must not be null");
      }
   }

   private ISuspendResult getSuspendResult() throws IOException {
      Object var1 = null;
      Object var2 = null;
      Object var3 = null;
      Object var4 = null;
      Object var5 = null;
      Object var6 = null;
      Object var7 = null;
      Object var8 = null;
      byte var9 = 46;
      PalTrace.header("getSuspendResult");

      while(true) {
         try {
            PalResultException var12 = this.getResultException();
            if (var12 != null || !this.performSQLAuthentification() && !this.performLibrarySearchOrder()) {
               PalTypeDbgStatus[] var13 = (PalTypeDbgStatus[])this.pal.retrieve(35);
               PalTypeDbgStackFrame[] var14 = (PalTypeDbgStackFrame[])this.pal.retrieve(34);
               PalTypeDbgNatStack[] var15 = (PalTypeDbgNatStack[])this.pal.retrieve(53);
               IPalTypeNatParm[] var16 = (IPalTypeNatParm[])this.pal.retrieve(25);
               PalTypeDbgSpy[] var17 = (PalTypeDbgSpy[])this.pal.retrieve(40);
               PalTypeStream[] var18 = (PalTypeStream[])this.pal.retrieve(13);
               PalTypeNotify[] var19 = (PalTypeNotify[])this.pal.retrieve(19);
               if (var16 == null) {
                  return new SuspendResult(var13 != null ? var13[0] : null, var14, var17 != null ? var17[0] : null, var18 != null ? var18[0] : null, var19 != null ? var19[0] : null, var9, var12, var15);
               }

               for(int var10 = 0; var10 < var16.length; ++var10) {
                  if (var16[var10].getCharAssign() != null) {
                     var9 = var16[var10].getCharAssign().getDecimalChar();
                     return new SuspendResult(var13 != null ? var13[0] : null, var14, var17 != null ? var17[0] : null, var18 != null ? var18[0] : null, var19 != null ? var19[0] : null, var9, var12, var15);
                  }
               }

               return new SuspendResult(var13 != null ? var13[0] : null, var14, var17 != null ? var17[0] : null, var18 != null ? var18[0] : null, var19 != null ? var19[0] : null, var9, var12, var15);
            }
         } catch (PalTimeoutException var11) {
            throw var11;
         }
      }
   }

   private boolean performLibrarySearchOrder() throws IOException {
      boolean var1 = false;
      if (this.executionContext != null) {
         IPalTypeLibId[] var2 = (IPalTypeLibId[])this.pal.retrieve(30);
         if (var2 != null) {
            String var3 = var2[0].getLibrary();
            IPalTypeLibId[] var4 = this.executionContext.getLibrarySearchOrder(var3);
            if (var4.length == 0) {
               var4[0] = PalTypeLibIdFactory.newInstance();
            }

            this.pal.add((IPalType[])var4);
            EStepLibFormat var5 = this.executionContext.getLibrarySearchOrderFormat(var3);
            PalTypeNotify var6 = new PalTypeNotify(var5 == EStepLibFormat.SHARED ? 1 : (var5 == EStepLibFormat.PRIVATE ? 2 : 0));
            this.pal.add((IPalType)var6);
            this.pal.commit();
            var1 = true;
         }
      }

      return var1;
   }

   private boolean performSQLAuthentification() throws IOException {
      boolean var1 = false;
      if (this.palSQLIdentification != null) {
         Object var2 = null;
         PalTypeSQLAuthentification[] var4 = (PalTypeSQLAuthentification[])this.pal.retrieve(26);
         if (var4 != null) {
            IPalTypeSQLAuthentification var3 = this.palSQLIdentification.handleLogin(var4[0]);
            var4[0] = (PalTypeSQLAuthentification)var3;
            this.pal.add((IPalType[])var4);
            this.pal.commit();
            var1 = true;
         }
      }

      return var1;
   }

   private IPalTypeDbgSpy handleSpy(int var1, IPalTypeDbgSpy var2, IPalTypeDbgVarDesc var3, IPalTypeDbgVarValue var4) throws IOException, PalResultException {
      IPalTypeDbgSpy var5 = null;

      try {
         this.pal.add((IPalType)(new PalTypeOperation(63, var1)));
         this.pal.add((IPalType)var2);
         if (var3 != null) {
            this.pal.add((IPalType)var3);
         }

         if (var4 != null) {
            this.pal.add((IPalType)var4);
         }

         this.pal.commit();
         IPalTypeDbgSpy[] var6 = (IPalTypeDbgSpy[])this.pal.retrieve(40);
         if (var6 != null) {
            var5 = var6[0];
         }

         PalResultException var7 = this.getResultException();
         if (var7 != null) {
            throw var7;
         } else {
            return var5;
         }
      } catch (PalTimeoutException var8) {
         throw var8;
      }
   }

   private boolean isOpenSystemsServer() {
      int var1 = this.palProperties.getNdvType();
      return var1 == 2 || var1 == 3 || var1 == 4;
   }

   private IDownloadResult genDdm(String var1) throws IOException, PalResultException {
      IDownloadResult var2 = null;

      try {
         this.pal.add((IPalType)(new PalTypeOperation(2, 17)));
         this.pal.add((IPalType)(new PalTypeStack(var1)));
         this.pal.commit();
         PalResultException var3 = this.getResultException();
         if (var3 != null) {
            throw var3;
         } else {
            IPalTypeSrcDesc[] var10000 = (IPalTypeSrcDesc[])this.pal.retrieve(15);
            IPalTypeLibId[] var4 = (IPalTypeLibId[])this.pal.retrieve(6);
            PalTypeSystemFile var5 = new PalTypeSystemFile(var4[0].getDatabaseId(), var4[0].getFileNumber(), 0);
            boolean var6 = false;
            if (this.isIBM420CodePage(this.naturalParameters.getRegional().getCodePage())) {
               var6 = true;
            }

            var2 = this.sourceFromPal(var5, var4[0].getLibrary(), "", true, false, var6);
            return var2;
         }
      } catch (PalTimeoutException var7) {
         throw var7;
      }
   }

   private boolean isIBM420CodePage(String var1) {
      try {
         String var2 = var1.trim();
         return var2.equals("IBM420");
      } catch (PalTransactions$Exception var3) {
         return false;
      }
   }

   private int getNdvMajorVersion() {
      try {
         return Integer.valueOf(Integer.valueOf(this.palProperties.getNdvVersion()).toString().substring(0, 3));
      } catch (PalTransactions$Exception var1) {
         return 0;
      }
   }

   private int getNdvMinorVersion() {
      try {
         String var1 = Integer.valueOf(this.palProperties.getNdvVersion()).toString();
         int var2 = var1.length();
         return Integer.valueOf(var1.substring(3, var2));
      } catch (PalTransactions$Exception var3) {
         return 0;
      }
   }

   private int getNatMajorVersion() {
      try {
         return Integer.valueOf(Integer.valueOf(this.palProperties.getNatVersion()).toString().substring(0, 3));
      } catch (PalTransactions$Exception var1) {
         return 0;
      }
   }

   private int getNatMinorVersion() {
      try {
         String var1 = Integer.valueOf(this.palProperties.getNatVersion()).toString();
         int var2 = var1.length();
         return Integer.valueOf(var1.substring(3, var2));
      } catch (PalTransactions$Exception var3) {
         return 0;
      }
   }

   public void setAutomaticLogon(boolean var1) {
      try {
         this.isAutomaticLogon = var1;
      } catch (PalTransactions$Exception var2) {
      }
   }

   private boolean isAutomaticLogon() {
      return this.isAutomaticLogon;
   }

   public ISuspendResult command(String var1, int var2) throws IOException {
      if (this.pal == null) {
      throw new IllegalStateException("connection to ndv server not available");
      } else if (var1 == null) {
         throw new IllegalArgumentException("command must not be null");
      } else {
         try {
            PalTrace.header("command");
            this.pal.add((IPalType)(new PalTypeOperation(2, var2)));
            this.pal.add((IPalType)(new PalTypeStack(var1)));
            this.pal.commit();
         } catch (PalTimeoutException var4) {
            throw var4;
         }

         return this.getSuspendResult();
      }
   }

   public static synchronized IPalTypeLibId[] librarySearchOrder2DisplayOrder(IPalTypeLibId[] var0) {
      ArrayList var1 = new ArrayList();

      for(int var2 = 1; var2 < var0.length; ++var2) {
         String var3 = var0[var2].getLibrary();
         if (var3 != null) {
            if (var3.length() != 0) {
               if (!var1.contains(var0[var2])) {
                  var1.add(var0[var2]);
               }
            }
         }
      }

      if (var0.length > 0 && var0[0].getLibrary() != null && var0[0].getLibrary().length() > 0) {
         var1.add(var0[0]);
      }

      return (IPalTypeLibId[])var1.toArray(new IPalTypeLibId[0]);
   }

   public void setSystemVariables(IPalTypeSysVar[] var1) {
      try {
         this.systemVariables = var1;
      } catch (PalTransactions$Exception var2) {
      }
   }

   public boolean isConnected() {
      return this.isConnected && this.pal != null && !this.pal.isConnectionLost();
   }

   public boolean isDisconnected() {
      return this.isdisconnected;
   }

   private void setConnected(boolean var1) {
      try {
         this.isConnected = var1;
      } catch (PalTransactions$Exception var2) {
      }
   }

   public static IPalTypeSystemFile createInactiveSystemFileKey(String var0, int var1, int var2) {
      return var0 != null && var1 > 0 && var2 > 0 ? PalTypeSystemFileFactory.newInstance(var1, var2, 3) : null;
   }

   public ILibraryInfo getLibraryInfo(int var1, IPalTypeSystemFile var2, String var3) throws IOException, PalResultException {
      Object var4 = null;
      if (this.pal == null) {
         throw new IllegalStateException("connection to ndv server not available");
      } else {
         Object var5 = null;
         PalTypeCmdGuard[] var10 = null;

         try {
            PalTrace.header("getLibraryInfo");
            this.pal.add((IPalType)(new PalTypeOperation(56, var1)));
            PalTypeLibId var6 = new PalTypeLibId(var2.getDatabaseId(), var2.getFileNumber(), var3, var2.getPassword(), var2.getCipher(), 6);
            this.pal.add((IPalType)var6);
            this.pal.commit();
            PalResultException var7 = this.getResultException();
            if (var7 != null) {
               throw var7;
            }

            var10 = (PalTypeCmdGuard[])this.pal.retrieve(27);
         } catch (PalTimeoutException var8) {
            throw var8;
         }

         PalTypeLibrary[] var9 = (PalTypeLibrary[])this.pal.retrieve(5);
         LibraryInfo var11 = (new LibraryInfo.Builder(var3)).prefix(var9).cmdGuard(var10).build();
         return var11;
      }
   }

   public void dasWaitForAttach(String var1, IDebugAttachWaitCallBack var2) throws PalResultException {
      boolean var3 = false;
      boolean var4 = true;

      try {
         PalTypeOperation var5 = new PalTypeOperation(81);
         this.pal.add((IPalType)var5);
         this.pal.add((IPalType)(new PalTypeDbgaRecord(var1)));
         this.pal.commit();

         while(var4) {
            IPalTypeDbgaRecord[] var6 = (IPalTypeDbgaRecord[])this.pal.retrieve(55);
            PalResultException var7 = this.getResultException();
            if (var7 != null) {
               throw var7;
            }

            if (var3) {
               break;
            }

            if (var6 != null) {
               var2.recordFound(var6[0]);
               this.pal.add((IPalType)(new PalTypeNotify(4)));
               this.pal.commit();
            } else {
               IPalTypeNotify[] var8 = (IPalTypeNotify[])this.pal.retrieve(19);
               if (var8 != null && var8[0].getNotification() == 6) {
                  Object var9 = null;
                  PalTypeNotify var11;
                  if (var2.isAborted()) {
                     var11 = new PalTypeNotify(5);
                     var3 = true;
                  } else {
                     var11 = new PalTypeNotify(4);
                  }

                  this.pal.add((IPalType)var11);
                  this.pal.commit();
               }
            }
         }
      } catch (IOException var10) {
      }

   }

   public void dasRegisterDebugAttachRecord(IPalTypeDbgaRecord var1) throws PalResultException {
      try {
         PalTypeOperation var2 = new PalTypeOperation(79);
         this.pal.add((IPalType)var2);
         this.pal.add((IPalType)var1);
         this.pal.commit();
         PalResultException var3 = this.getResultException();
         if (var3 != null) {
            throw var3;
         }
      } catch (IOException var4) {
      } catch (Exception var5) {
         var5.printStackTrace();
      }

   }

   public void dasUnregisterDebugAttachRecord(IPalTypeDbgaRecord var1, int var2) throws PalResultException {
      try {
         PalTypeOperation var3 = new PalTypeOperation(80, var2);
         this.pal.add((IPalType)var3);
         this.pal.add((IPalType)var1);
         this.pal.commit();
         PalResultException var4 = this.getResultException();
         if (var4 != null) {
            throw var4;
         }
      } catch (IOException var5) {
      } catch (Exception var6) {
         var6.printStackTrace();
      }

   }

   public String getSessionId() {
      try {
         return this.pal != null ? this.pal.getSessionId() : null;
      } catch (PalTransactions$Exception var1) {
         return null;
      }
   }

   public void initTraceSession(String var1) throws PalResultException {
      try {
         PalTrace.header("initTraceSession");
         this.pal.add((IPalType)(new PalTypeOperation(85)));
         this.pal.add((IPalType)(new PalTypeGeneric(1, var1)));
         this.pal.commit();
         PalResultException var2 = this.getResultException();
         if (var2 != null) {
            throw var2;
         }
      } catch (IOException var3) {
         var3.printStackTrace();
      }

   }

   // $FF: synthetic method
   static void access$11(PalTransactions var0, boolean var1) throws IOException, PalResultException {
      try {
         var0.putServerConfig(var1);
      } catch (PalTransactions$Exception var2) {
      }
   }

   private static class ContextDownload implements ITransactionContextDownload {
      private Set options;
      private boolean terminated;
      private boolean started;

      private ContextDownload() {
         this.terminated = false;
         this.started = false;
      }

      private void setTerminated(boolean var1) {
         try {
            this.terminated = var1;
         } catch (PalTransactions$Exception var2) {
         }
      }

      private void setStarted(boolean var1) {
         try {
            this.started = var1;
         } catch (PalTransactions$Exception var2) {
         }
      }

      private boolean isStarted() {
         return this.started;
      }

      private boolean isTerminated() {
         return this.terminated;
      }

      private void setInitOptions(Set var1) {
         try {
            this.options = var1;
         } catch (PalTransactions$Exception var2) {
         }
      }

      private Set getInitOptions() {
         return this.options;
      }

      // $FF: synthetic method
      ContextDownload(ContextDownload var1) {
         this();
      }

      // $FF: synthetic method
      static void access$4(ContextDownload var0, boolean var1) {
         try {
            var0.setTerminated(var1);
         } catch (PalTransactions$Exception var2) {
         }
      }

      // $FF: synthetic method
      static void access$5(ContextDownload var0, Set var1) {
         try {
            var0.setInitOptions(var1);
         } catch (PalTransactions$Exception var2) {
         }
      }

      // $FF: synthetic method
      static void access$6(ContextDownload var0, boolean var1) {
         try {
            var0.setStarted(var1);
         } catch (PalTransactions$Exception var2) {
         }
      }
   }

   private static class DownloadResult implements IDownloadResult {
      private String[] source;
      private int lineIncrement;

      private DownloadResult(String[] var1, int var2) {
         this.source = var1;
         this.lineIncrement = var2;
      }

      public String[] getSource() {
         return this.source;
      }

      public int getLineIncrement() {
         return this.lineIncrement;
      }

      // $FF: synthetic method
      DownloadResult(String[] var1, int var2, DownloadResult var3) {
         this(var1, var2);
      }
   }

   public static class NatParm implements INatParm, Serializable {
      private static final long serialVersionUID = 1L;
      private IPalTypeNatParm[] natParm = null;
      private IReport report = null;
      private ICharAssign charAssign = null;
      private ICompOpt compOpt = null;
      private ILimit limit = null;
      private IFldApp fldApp = null;
      private IRegional regional = null;
      private IRpc rpc = null;
      private IErr err = null;
      private IBuffSize buffSize = null;

      public NatParm(IPalTypeNatParm[] var1) {
         this.natParm = var1;
      }

      public IRpc getRpc() {
         if (this.rpc == null) {
            IPalTypeNatParm[] var4;
            for(IPalTypeNatParm var1 : var4 = this.natParm) {
               if (var1.getRpc() != null) {
                  this.rpc = var1.getRpc();
                  break;
               }
            }
         }

         return this.rpc;
      }

      public IRegional getRegional() {
         if (this.regional == null) {
            IPalTypeNatParm[] var4;
            for(IPalTypeNatParm var1 : var4 = this.natParm) {
               if (var1.getRegional() != null) {
                  this.regional = var1.getRegional();
                  break;
               }
            }
         }

         return this.regional;
      }

      public IFldApp getFldApp() {
         try {
            if (this.fldApp == null) {
               IPalTypeNatParm[] var4;
               for(IPalTypeNatParm var1 : var4 = this.natParm) {
                  if (var1.getFldApp() != null) {
                     this.fldApp = var1.getFldApp();
                     break;
                  }
               }
            }

            return this.fldApp;
         } catch (PalTransactions$Exception var5) {
            return null;
         }
      }

      public IReport getReport() {
         if (this.report == null) {
            IPalTypeNatParm[] var4;
            for(IPalTypeNatParm var1 : var4 = this.natParm) {
               if (var1.getReport() != null) {
                  this.report = var1.getReport();
                  break;
               }
            }
         }

         return this.report;
      }

      public ICharAssign getCharAssign() {
         if (this.charAssign == null) {
            IPalTypeNatParm[] var4;
            for(IPalTypeNatParm var1 : var4 = this.natParm) {
               if (var1.getCharAssign() != null) {
                  this.charAssign = var1.getCharAssign();
                  break;
               }
            }
         }

         return this.charAssign;
      }

      public ICompOpt getCompOpt() {
         try {
            if (this.compOpt == null) {
               IPalTypeNatParm[] var4;
               for(IPalTypeNatParm var1 : var4 = this.natParm) {
                  if (var1.getCompOpt() != null) {
                     this.compOpt = var1.getCompOpt();
                     break;
                  }
               }
            }

            return this.compOpt;
         } catch (PalTransactions$Exception var5) {
            return null;
         }
      }

      public ILimit getLimit() {
         try {
            if (this.limit == null) {
               IPalTypeNatParm[] var4;
               for(IPalTypeNatParm var1 : var4 = this.natParm) {
                  if (var1.getLimit() != null) {
                     this.limit = var1.getLimit();
                     break;
                  }
               }
            }

            return this.limit;
         } catch (PalTransactions$Exception var5) {
            return null;
         }
      }

      public IBuffSize getBuffSize() {
         try {
            if (this.buffSize == null) {
               IPalTypeNatParm[] var4;
               for(IPalTypeNatParm var1 : var4 = this.natParm) {
                  if (var1.getBuffSize() != null) {
                     this.buffSize = var1.getBuffSize();
                     break;
                  }
               }
            }

            return this.buffSize;
         } catch (PalTransactions$Exception var5) {
            return null;
         }
      }

      public IErr getErr() {
         try {
            if (this.err == null) {
               IPalTypeNatParm[] var4;
               for(IPalTypeNatParm var1 : var4 = this.natParm) {
                  if (var1.getErr() != null) {
                     this.err = var1.getErr();
                     break;
                  }
               }
            }

            return this.err;
         } catch (PalTransactions$Exception var5) {
            return null;
         }
      }

      public IPalTypeNatParm[] get(int var1) {
         try {
            ArrayList var2 = new ArrayList();
            boolean var3 = true;

            IPalTypeNatParm[] var7;
            for(IPalTypeNatParm var4 : var7 = this.natParm) {
               var3 = true;
               if (var4.getRecordIndex() == 6 && var1 == 1) {
                  var3 = false;
               }

               if (var4.getRecordIndex() == 7 && var1 == 1) {
                  var3 = false;
               }

               if (var3) {
                  var2.add(new PalTypeNatParm(var4));
               }
            }

            return (IPalTypeNatParm[])var2.toArray(new IPalTypeNatParm[0]);
         } catch (PalTransactions$Exception var8) {
            return null;
         }
      }
   }

   private static class SourceLookupResult implements ISourceLookupResult {
      private IPalTypeObject palTypeObject;
      private String library;
      private int databaseId;
      private int fileNumber;

      private SourceLookupResult(IPalTypeObject var1, String var2, int var3, int var4) {
         this.library = var2;
         this.palTypeObject = var1;
         this.databaseId = var3 == 0 && var4 == 0 ? -1 : var3;
         this.fileNumber = var3 == 0 && var4 == 0 ? -1 : var4;
      }

      public IPalTypeObject getObject() {
         return this.palTypeObject;
      }

      public String getLibrary() {
         return this.library;
      }

      public int getDatabaseId() {
         return this.databaseId;
      }

      public int getFileNumber() {
         return this.fileNumber;
      }

      // $FF: synthetic method
      SourceLookupResult(IPalTypeObject var1, String var2, int var3, int var4, SourceLookupResult var5) {
         this(var1, var2, var3, var4);
      }
   }
}
