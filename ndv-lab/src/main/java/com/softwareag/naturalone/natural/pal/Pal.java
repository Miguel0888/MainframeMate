package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTimeoutHandler;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgaRecord;
import com.softwareag.naturalone.natural.pal.external.IPalTypeOperation;
import com.softwareag.naturalone.natural.pal.external.PalTimeoutException;
import com.softwareag.naturalone.natural.pal.external.PalTools;
import com.softwareag.naturalone.natural.pal.external.PalTrace;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class Pal {
   private boolean connectionLost;
   private IPalTimeoutHandler timeoutHandler;
   private int timeout;
   private IOException palThreadIOException;
   private PalTimeoutException palThreadTimeoutException;
   private boolean isPalThreadException;
   private String host = "";
   private String port;
   private int type;
   private Set typeSet;
   private boolean isRetrievePhase;
   private boolean isNewheader;
   private int bufferTail;
   private byte[] buffer;
   private byte[] getBuffer;
   private int numberOfRecordsReceived;
   private ArrayList[] typeCacheOfReceived;
   private int positionOfRecNumSend;
   private int recNumSend;
   private boolean isReadBuffer;
   private int palVersion;
   private String sessionId = "";
   private String userId = "";
   private Socket ndvConnection;
   private OutputStream outputStream;
   private InputStream inputStream;
   private TransferArea transferArea;
   private int ndvType;
   private String serverCodePage = null;
   private static final int NATRMT_SIZE_TRANSACTION = 12;
   private static final int NATRMT_SIZE_SIZEREC = 12;
   private static final int NATRMT_SIZE_NUMRECS = 12;
   private static final int NATRMT_SIZE_PROT = 6;
   private static final int NATRMT_SPACE_1STREC = 42;
   private static final int NATRMT_SPACE_REC = 24;
   private static final int NATRMT_SIZE_TA = 4000;
   private static final String PAL_HD_SIG = "NATSPOD";
   private static final int PAL_HD_SIG_LEN = 7;
   private static final int PAL_HD_LEN = 8;
   private static final int PAL_HD_NUM_LEN = 3;
   private static final int PAL_HD_PACKAGE_LEN = 8;
   private static final int PAL_HD_SIGNNEWACK = 2;
   private static final int PAL_HD_ENTRIES = 1;
   private static final int PAL_HD_SIGNNEW = 1;
   private static final int PAL_HD_SIZE = 26;
   private static final String PAL_HD_NOTIFYCHUNK = "NATSPODNEXTCHUNK          ";
   private static final String PAL_HD_DISCONNECT = "NATSPODDISCONNECT         ";
   private final String[] PALTYPECLASSES = new String[]{"com.softwareag.naturalone.natural.pal.PalTypeEnviron", "com.softwareag.naturalone.natural.pal.PalTypeConnect", "com.softwareag.naturalone.natural.pal.PalTypeOperation", "com.softwareag.naturalone.natural.pal.PalTypeSystemFile", "com.softwareag.naturalone.natural.pal.PalTypeLibraryStatistics", "com.softwareag.naturalone.natural.pal.PalTypeLibrary", "com.softwareag.naturalone.natural.pal.PalTypeLibId", "com.softwareag.naturalone.natural.pal.PalTypeObjDesc", "com.softwareag.naturalone.natural.pal.PalTypeObject", "com.softwareag.naturalone.natural.pal.PalTypeStackCmd", "com.softwareag.naturalone.natural.pal.PalTypeResult", "com.softwareag.naturalone.natural.pal.PalTypeResultEx", "com.softwareag.naturalone.natural.pal.PalTypeSourceCodePage", "com.softwareag.naturalone.natural.pal.PalTypeStream", "com.softwareag.naturalone.natural.pal.PalTypeUtility", "com.softwareag.naturalone.natural.pal.PalTypeSrcDesc", "com.softwareag.naturalone.natural.pal.PalTypeSrvAppList", "com.softwareag.naturalone.natural.pal.PalTypeAppId", "com.softwareag.naturalone.natural.pal.PalTypeCatallDesc", "com.softwareag.naturalone.natural.pal.PalTypeNotify", "com.softwareag.naturalone.natural.pal.PalTypeGeneric", "com.softwareag.naturalone.natural.pal.PalTypeAttrList", "com.softwareag.naturalone.natural.pal.PalTypeDescrip", "com.softwareag.naturalone.natural.pal.PalTypeFileId", "com.softwareag.naturalone.natural.pal.PalTypeStream", "com.softwareag.naturalone.natural.pal.PalTypeNatParm", "com.softwareag.naturalone.natural.pal.PalTypeSQLAuthentification", "com.softwareag.naturalone.natural.pal.PalTypeCmdGuard", "com.softwareag.naturalone.natural.pal.PalTypeSysVar", "com.softwareag.naturalone.natural.pal.PalTypeObjDesc2", "com.softwareag.naturalone.natural.pal.PalTypeLibId", "com.softwareag.naturalone.natural.pal.PalTypeFindInfo", "com.softwareag.naturalone.natural.pal.PalTypeFindResult", "com.softwareag.naturalone.natural.pal.PalTypeFindStatus", "com.softwareag.naturalone.natural.pal.PalTypeDbgStackFrame", "com.softwareag.naturalone.natural.pal.PalTypeDbgStatus", "com.softwareag.naturalone.natural.pal.PalTypeDbgVarContainer", "com.softwareag.naturalone.natural.pal.PalTypeDbgSyt", "com.softwareag.naturalone.natural.pal.PalTypeDbgVarDesc", "com.softwareag.naturalone.natural.pal.PalTypeDbgVarValue", "com.softwareag.naturalone.natural.pal.PalTypeDbgSpy", "com.softwareag.naturalone.natural.pal.PalTypeDbgVarDescHdl", "com.softwareag.naturalone.natural.pal.PalTypeSourceUnicode", "com.softwareag.naturalone.natural.pal.PalTypeVarValueHdl", "com.softwareag.naturalone.natural.pal.PalTypeProxyConnect", "com.softwareag.naturalone.natural.pal.PalTypeCP", "com.softwareag.naturalone.natural.pal.PalTypeLibId", "com.softwareag.naturalone.natural.pal.PalTypeSuppressLine", "com.softwareag.naturalone.natural.pal.PalTypeSourceCP", "com.softwareag.naturalone.natural.pal.PalTypeDbmsInfo", "com.softwareag.naturalone.natural.pal.PalTypeClientConfig", "com.softwareag.naturalone.natural.pal.PalTypeEnviron1", "com.softwareag.naturalone.natural.pal.PalTypeDevEnv", "com.softwareag.naturalone.natural.pal.PalTypeDbgNatStack", "com.softwareag.naturalone.natural.pal.PalTypeTimeStamp", "com.softwareag.naturalone.natural.pal.PalTypeDbgaRecord", "com.softwareag.naturalone.natural.pal.PalTypeMonitorInfo"};

   public Pal(int var1, IPalTimeoutHandler var2) {
      this.timeout = var1 * 1000;
      this.timeoutHandler = var2;
   }

   public void init() {
      try {
         this.type = 65535;
         this.typeSet = new HashSet();
         this.typeCacheOfReceived = new ArrayList[57];
         this.buffer = new byte[4000];
         this.bufferTail = 38;
         this.transferArea = new TransferArea((TransferArea)null);
         this.isReadBuffer = true;
         this.palThreadIOException = null;
         this.palThreadTimeoutException = null;
         this.isPalThreadException = false;
         this.isRetrievePhase = false;
      } catch (Pal$ArrayOutOfBoundsException var1) {
      }
   }

   public void add(IPalType var1) throws IOException {
      int var2 = var1.get();

      try {
         try {
            if (this.isRetrievePhase) {
               if (this.type != 32004) {
                   try {
                      boolean finished1 = false;
                      try {
                         this.numberOfRecordsReceived = 0;
                         if (this.isReadBuffer) {
                            this.getBuffer = this.transferArea.get();
                            if (this.isPalThreadException) {
                               finished1 = true;
                            } else {
                               PalTrace.buffer(this.getBuffer, true, this.getSessionId());
                               Object var11 = null;
                               boolean var13 = false;
                               int var14;
                               for (var14 = 0; this.getBuffer[0 + var14] != 0; ++var14) {
                               }
                               byte[] var15 = new byte[var14];
                               for (int var16 = 0; this.getBuffer[0 + var16] != 0; ++var16) {
                                  var15[var16] = this.getBuffer[0 + var16];
                               }
                               Integer.valueOf(new String(var15));
                               this.bufferTail = 12;
                               this.isReadBuffer = false;
                               Object var16 = null;
                               boolean var17 = false;
                               int var18;
                               for (var18 = 0; this.getBuffer[this.bufferTail + var18] != 0; ++var18) {
                               }
                               byte[] var19 = new byte[var18];
                               for (int var20 = 0; this.getBuffer[this.bufferTail] != 0; var19[var20++] = this.getBuffer[this.bufferTail++]) {
                               }
                               ++this.bufferTail;
                               this.type = Integer.valueOf(new String(var19));
                            }

                         }
                         if (!finished1) {
                            if (this.type != 32004) {
                               Object var11 = null;
                               boolean var13 = false;
                               int var14 = 0;
                               for (var14 = 0; this.getBuffer[this.bufferTail + var14] != 0; ++var14) {
                               }

                               byte[] var15 = new byte[var14];

                               for (int var16 = 0; this.getBuffer[this.bufferTail + var16] != 0; ++var16) {
                                  var15[var16] = this.getBuffer[this.bufferTail + var16];
                               }

                               this.numberOfRecordsReceived = Integer.valueOf(new String(var15));
                               this.bufferTail += 12;
                            }

                         }

                      } catch (Pal$ArrayOutOfBoundsException var11) {
                      }
                      if (!this.isPalThreadException) {
                         while(this.type != 32004 && this.type != 32004) {
                            this.readRecords(32004);
                            if (this.isPalThreadException) {
                                break;
                            }
             
                            if (this.type == 32004) {
                               break;
                            }

                            boolean finished = false;
                            try {
                               this.numberOfRecordsReceived = 0;
                               if (this.isReadBuffer) {
                                  this.getBuffer = this.transferArea.get();
                                  if (this.isPalThreadException) {
                                     finished = true;
                                  } else {
                                     PalTrace.buffer(this.getBuffer, true, this.getSessionId());
                                     Object var9 = null;
                                     boolean var10 = false;
                                     int var12;
                                     for (var12 = 0; this.getBuffer[0 + var12] != 0; ++var12) {
                                     }
                                     byte[] var5 = new byte[var12];
                                     for (int var6 = 0; this.getBuffer[0 + var6] != 0; ++var6) {
                                        var5[var6] = this.getBuffer[0 + var6];
                                     }
                                     Integer.valueOf(new String(var5));
                                     this.bufferTail = 12;
                                     this.isReadBuffer = false;
                                     Object var6 = null;
                                     boolean var7 = false;
                                     int var8;
                                     for (var8 = 0; this.getBuffer[this.bufferTail + var8] != 0; ++var8) {
                                     }
                                     byte[] var3 = new byte[var8];
                                     for (int var4 = 0; this.getBuffer[this.bufferTail] != 0; var3[var4++] = this.getBuffer[this.bufferTail++]) {
                                     }
                                     ++this.bufferTail;
                                     this.type = Integer.valueOf(new String(var3));
                                  }

                               }
                               if (!finished) {
                                  if (this.type != 32004) {
                                     Object var3 = null;
                                     boolean var4 = false;
                                     int var7 = 0;
                                     for (var7 = 0; this.getBuffer[this.bufferTail + var7] != 0; ++var7) {
                                     }

                                     byte[] var5 = new byte[var7];

                                     for (int var6 = 0; this.getBuffer[this.bufferTail + var6] != 0; ++var6) {
                                        var5[var6] = this.getBuffer[this.bufferTail + var6];
                                     }

                                     this.numberOfRecordsReceived = Integer.valueOf(new String(var5));
                                     this.bufferTail += 12;
                                  }

                               }

                            } catch (Pal$ArrayOutOfBoundsException var3) {
                            }
                            if (this.isPalThreadException) {
                                break;
                            }
                         }
             
                      }
                   } catch (Pal$ArrayOutOfBoundsException var5) {
                   }
                   try {
                     if (this.isPalThreadException) {
                        if (this.palThreadIOException != null) {
                           throw this.palThreadIOException;
                        }
            
                        if (this.palThreadTimeoutException != null) {
                           throw this.palThreadTimeoutException;
                        }
                     }
            
                  } catch (Pal$ArrayOutOfBoundsException var3) {
                  }
               }
   
               Arrays.fill(this.typeCacheOfReceived, (Object)null);
               this.isRetrievePhase = false;
               this.bufferTail = 38;
               this.positionOfRecNumSend = 0;
               this.recNumSend = 0;
               this.isReadBuffer = true;
               Arrays.fill(this.buffer, (byte)0);
               PalTrace.text("\r\n");
            }
         } catch (PalTimeoutException var4) {
            throw var4;
         }

         if (var1 instanceof IPalTypeOperation) {
            IPalTypeOperation var3 = (IPalTypeOperation)var1;
            var3.setClientId(this.sessionId);
            var3.setUserId(this.userId);
         }

         if (!this.typeSet.add(var2)) {
            throw new IllegalStateException("The last PAL transaction was not commited (Type " + var2 + " is still in queue)");
         }

         PalTrace.type(this.PALTYPECLASSES[var2], false);
         var1.setRecord(new ArrayList());
         var1.setServerCodePage(this.serverCodePage);
         var1.serialize();
         int var34 = var1.get();
         ArrayList var35 = var1.getRecord();
         boolean var36 = true;
         int var4 = 0;
         byte var5 = 0;
         int var6 = var35.size();
         int var7 = 0;

         while(true) {
            if (var36) {
               var5 = 42;
            } else {
               var5 = 24;
            }
   
            if (this.bufferTail + var5 + var6 < this.buffer.length) {
               if (var36) {
                  if (this.positionOfRecNumSend != 0) {
                     try {
                        int var11 = 0;
                        Integer var12 = this.recNumSend;
                        String var13 = var12.toString();
                        int result;
                        try {
                           byte[] var8 = var13.getBytes();
                           int var9 = var8.length;
                           System.arraycopy(var8, 0, this.buffer, this.positionOfRecNumSend, var9);
                           this.buffer[this.positionOfRecNumSend + var9] = 0;
                           result = var9 + 1;
                        } catch (Pal$ArrayOutOfBoundsException var8) {
                           result = 0;
                        }
                        var11 = result;
                     } catch (Pal$ArrayOutOfBoundsException var8) {
                     }
                     this.recNumSend = 0;
                  }
   
                  int res;
                  try {
                     int var11 = 0;
                     Integer var12 = var34;
                     String var13 = var12.toString();
                     int result;
                     try {
                        byte[] var8 = var13.getBytes();
                        int var9 = var8.length;
                        System.arraycopy(var8, 0, this.buffer, this.bufferTail, var9);
                        this.buffer[this.bufferTail + var9] = 0;
                        result = var9 + 1;
                     } catch (Pal$ArrayOutOfBoundsException var8) {
                        result = 0;
                     }
                     var11 = result;
                     res = this.bufferTail + var11;
                  } catch (Pal$ArrayOutOfBoundsException var8) {
                     res = 0;
                  }
                  this.bufferTail = res;
                  this.positionOfRecNumSend = this.bufferTail;
                  Arrays.fill(this.buffer, this.positionOfRecNumSend, this.positionOfRecNumSend + 12, (byte)32);
                  this.bufferTail += 12;
               }
   
               int res1;
               try {
                  int var18 = 0;
                  Integer var19 = var6;
                  String var20 = var19.toString();
                  int result1;
                  try {
                     byte[] var21 = var20.getBytes();
                     int var22 = var21.length;
                     System.arraycopy(var21, 0, this.buffer, this.bufferTail, var22);
                     this.buffer[this.bufferTail + var22] = 0;
                     result1 = var22 + 1;
                  } catch (Pal$ArrayOutOfBoundsException var21) {
                     result1 = 0;
                  }
                  var18 = result1;
                  res1 = this.bufferTail + var18;
               } catch (Pal$ArrayOutOfBoundsException var18) {
                  res1 = 0;
               }
               this.bufferTail = res1;
               byte[] var11;
               try {
                  Object var9 = null;
                  byte[] var10 = new byte[var35.size()];
         
                  for(int var12 = 0; var12 < var35.size(); ++var12) {
                     var10[var12] = (Byte) var35.get(var12);
                  }
   
                  var11 = var10;
               } catch (Pal$ArrayOutOfBoundsException var9) {
                  var11 = null;
               }
               System.arraycopy(var11, var7, this.buffer, this.bufferTail, var6);
               this.bufferTail += var6;
               int res;
               try {
                  int var13 = 0;
                  Integer var14 = 32003;
                  String var15 = var14.toString();
                  int result;
                  try {
                     byte[] var8 = var15.getBytes();
                     int var9 = var8.length;
                     System.arraycopy(var8, 0, this.buffer, this.bufferTail, var9);
                     this.buffer[this.bufferTail + var9] = 0;
                     result = var9 + 1;
                  } catch (Pal$ArrayOutOfBoundsException var8) {
                     result = 0;
                  }
                  var13 = result;
                  res = this.bufferTail + var13;
               } catch (Pal$ArrayOutOfBoundsException var8) {
                  res = 0;
               }
               this.bufferTail = res;
               ++this.recNumSend;
               break;
            }
   
            var4 = this.buffer.length - (this.bufferTail + var5);
            if (var4 <= 0) {
               int var32 = this.bufferTail - 26;
               int var33 = 26;
               Arrays.fill(this.buffer, var33, var33 + 12, (byte)0);
               try {
                  int var27 = 0;
                  Integer var28 = var32;
                  String var29 = var28.toString();
                  int result4;
                  try {
                     byte[] var30 = var29.getBytes();
                     int var31 = var30.length;
                     System.arraycopy(var30, 0, this.buffer, var33, var31);
                     this.buffer[var33 + var31] = 0;
                     result4 = var31 + 1;
                  } catch (Pal$ArrayOutOfBoundsException var30) {
                     result4 = 0;
                  }
                  var27 = result4;
               } catch (Pal$ArrayOutOfBoundsException var27) {
               }
               int res;
               try {
                  int var22 = 0;
                  Integer var23 = 32001;
                  String var24 = var23.toString();
                  int result3;
                  try {
                     byte[] var25 = var24.getBytes();
                     int var26 = var25.length;
                     System.arraycopy(var25, 0, this.buffer, this.bufferTail, var26);
                     this.buffer[this.bufferTail + var26] = 0;
                     result3 = var26 + 1;
                  } catch (Pal$ArrayOutOfBoundsException var25) {
                     result3 = 0;
                  }
                  var22 = result3;
                  res = this.bufferTail + var22;
               } catch (Pal$ArrayOutOfBoundsException var22) {
                  res = 0;
               }
               this.bufferTail = res;
               if (this.positionOfRecNumSend != 0) {
                  var33 = this.positionOfRecNumSend;
                  try {
                     int var11 = 0;
                     Integer var12 = this.recNumSend;
                     String var13 = var12.toString();
                     int result;
                     try {
                        byte[] var8 = var13.getBytes();
                        int var9 = var8.length;
                        System.arraycopy(var8, 0, this.buffer, var33, var9);
                        this.buffer[var33 + var9] = 0;
                        result = var9 + 1;
                     } catch (Pal$ArrayOutOfBoundsException var8) {
                        result = 0;
                     }
                     var11 = result;
                  } catch (Pal$ArrayOutOfBoundsException var8) {
                  }
               }
   
               var33 = 0;
               try {
                  byte[] var9 = "NATSPOD".getBytes();
                  int var8 = var9.length;
                  System.arraycopy(var9, 0, this.buffer, var33, var8);
                  this.buffer[var33 + var8] = 0;
               } catch (Pal$ArrayOutOfBoundsException var8) {
               }
               var33 += 7;
               try {
                  int var17 = 0;
                  Integer var18 = 26;
                  String var19 = var18.toString();
                  int result2;
                  try {
                     byte[] var20 = var19.getBytes();
                     int var21 = var20.length;
                     System.arraycopy(var20, 0, this.buffer, var33, var21);
                     this.buffer[var33 + var21] = 0;
                     result2 = var21 + 1;
                  } catch (Pal$ArrayOutOfBoundsException var20) {
                     result2 = 0;
                  }
                  var17 = result2;
               } catch (Pal$ArrayOutOfBoundsException var17) {
               }
               var33 += 8;
               try {
                  int var12 = 0;
                  Integer var13 = 1;
                  String var14 = var13.toString();
                  int result1;
                  try {
                     byte[] var15 = var14.getBytes();
                     int var16 = var15.length;
                     System.arraycopy(var15, 0, this.buffer, var33, var16);
                     this.buffer[var33 + var16] = 0;
                     result1 = var16 + 1;
                  } catch (Pal$ArrayOutOfBoundsException var15) {
                     result1 = 0;
                  }
                  var12 = result1;
               } catch (Pal$ArrayOutOfBoundsException var12) {
               }
               var33 += 3;
               Arrays.fill(this.buffer, var33, var33 + 8, (byte)0);
               try {
                  int var12 = 0;
                  Integer var11 = this.bufferTail - 6;
                  String var13 = var11.toString();
                  int result;
                  try {
                     byte[] var8 = var13.getBytes();
                     int var9 = var8.length;
                     System.arraycopy(var8, 0, this.buffer, var33, var9);
                     this.buffer[var33 + var9] = 0;
                     result = var9 + 1;
                  } catch (Pal$ArrayOutOfBoundsException var8) {
                     result = 0;
                  }
                  var12 = result;
               } catch (Pal$ArrayOutOfBoundsException var8) {
               }
               if (!this.isNewheader) {
                  this.buffer[var33 + 8 - 1] = 1;
               }
   
               byte[] var8 = new byte[this.bufferTail];
               System.arraycopy(this.buffer, 0, var8, 0, this.bufferTail);
               this.outputStream.write(var8);
               PalTrace.buffer(var8, false, this.getSessionId());
               this.bufferTail = 38;
               Arrays.fill(this.buffer, (byte)0);
               if (this.getPalVersion() >= 17) {
                  this.getBuffer = this.transferArea.get();
                  if (this.isPalThreadException) {
                     break;
                  }
   
                  PalTrace.buffer(this.getBuffer, true, this.getSessionId());
                  if (!Arrays.equals(this.getBuffer, "NATSPODNEXTCHUNK          ".getBytes())) {
                     throw new IOException("Internal Error: server deliverd wrong data");
                  }
               }
   
               var36 = true;
            } else {
               if (var36) {
                  if (this.positionOfRecNumSend != 0) {
                     try {
                        int var11 = 0;
                        Integer var12 = this.recNumSend;
                        String var13 = var12.toString();
                        int result;
                        try {
                           byte[] var8 = var13.getBytes();
                           int var9 = var8.length;
                           System.arraycopy(var8, 0, this.buffer, this.positionOfRecNumSend, var9);
                           this.buffer[this.positionOfRecNumSend + var9] = 0;
                           result = var9 + 1;
                        } catch (Pal$ArrayOutOfBoundsException var8) {
                           result = 0;
                        }
                        var11 = result;
                     } catch (Pal$ArrayOutOfBoundsException var8) {
                     }
                     this.recNumSend = 0;
                  }
   
                  int res;
                  try {
                     int var11 = 0;
                     Integer var12 = var34;
                     String var13 = var12.toString();
                     int result;
                     try {
                        byte[] var8 = var13.getBytes();
                        int var9 = var8.length;
                        System.arraycopy(var8, 0, this.buffer, this.bufferTail, var9);
                        this.buffer[this.bufferTail + var9] = 0;
                        result = var9 + 1;
                     } catch (Pal$ArrayOutOfBoundsException var8) {
                        result = 0;
                     }
                     var11 = result;
                     res = this.bufferTail + var11;
                  } catch (Pal$ArrayOutOfBoundsException var8) {
                     res = 0;
                  }
                  this.bufferTail = res;
                  this.positionOfRecNumSend = this.bufferTail;
                  Arrays.fill(this.buffer, this.positionOfRecNumSend, this.positionOfRecNumSend + 12, (byte)32);
                  this.bufferTail += 12;
               }
   
               int res1;
               try {
                  int var18 = 0;
                  Integer var19 = var4;
                  String var20 = var19.toString();
                  int result1;
                  try {
                     byte[] var21 = var20.getBytes();
                     int var22 = var21.length;
                     System.arraycopy(var21, 0, this.buffer, this.bufferTail, var22);
                     this.buffer[this.bufferTail + var22] = 0;
                     result1 = var22 + 1;
                  } catch (Pal$ArrayOutOfBoundsException var21) {
                     result1 = 0;
                  }
                  var18 = result1;
                  res1 = this.bufferTail + var18;
               } catch (Pal$ArrayOutOfBoundsException var18) {
                  res1 = 0;
               }
               this.bufferTail = res1;
               byte[] var8;
               try {
                  Object var10 = null;
                  byte[] var11 = new byte[var35.size()];
         
                  for(int var12 = 0; var12 < var35.size(); ++var12) {
                     var11[var12] = (Byte) var35.get(var12);
                  }
   
                  var8 = var11;
               } catch (Pal$ArrayOutOfBoundsException var10) {
                  var8 = null;
               }
               System.arraycopy(var8, var7, this.buffer, this.bufferTail, var4);
               this.bufferTail += var4;
               var7 += var4;
               var6 -= var4;
               ++this.recNumSend;
               if (var6 == 0) {
                  int res;
                  try {
                     int var12 = 0;
                     Integer var13 = 32003;
                     String var14 = var13.toString();
                     int result;
                     try {
                        byte[] var15 = var14.getBytes();
                        int var9 = var15.length;
                        System.arraycopy(var15, 0, this.buffer, this.bufferTail, var9);
                        this.buffer[this.bufferTail + var9] = 0;
                        result = var9 + 1;
                     } catch (Pal$ArrayOutOfBoundsException var9) {
                        result = 0;
                     }
                     var12 = result;
                     res = this.bufferTail + var12;
                  } catch (Pal$ArrayOutOfBoundsException var9) {
                     res = 0;
                  }
                  this.bufferTail = res;
                  break;
               }
   
               int var32 = this.bufferTail - 26;
               int var33 = 26;
               Arrays.fill(this.buffer, var33, var33 + 12, (byte)0);
               try {
                  int var27 = 0;
                  Integer var28 = var32;
                  String var29 = var28.toString();
                  int result4;
                  try {
                     byte[] var30 = var29.getBytes();
                     int var31 = var30.length;
                     System.arraycopy(var30, 0, this.buffer, var33, var31);
                     this.buffer[var33 + var31] = 0;
                     result4 = var31 + 1;
                  } catch (Pal$ArrayOutOfBoundsException var30) {
                     result4 = 0;
                  }
                  var27 = result4;
               } catch (Pal$ArrayOutOfBoundsException var27) {
               }
               int res;
               try {
                  int var22 = 0;
                  Integer var23 = 32002;
                  String var24 = var23.toString();
                  int result3;
                  try {
                     byte[] var25 = var24.getBytes();
                     int var26 = var25.length;
                     System.arraycopy(var25, 0, this.buffer, this.bufferTail, var26);
                     this.buffer[this.bufferTail + var26] = 0;
                     result3 = var26 + 1;
                  } catch (Pal$ArrayOutOfBoundsException var25) {
                     result3 = 0;
                  }
                  var22 = result3;
                  res = this.bufferTail + var22;
               } catch (Pal$ArrayOutOfBoundsException var22) {
                  res = 0;
               }
               this.bufferTail = res;
               if (this.positionOfRecNumSend != 0) {
                  var33 = this.positionOfRecNumSend;
                  try {
                     int var11 = 0;
                     Integer var12 = this.recNumSend;
                     String var13 = var12.toString();
                     int result;
                     try {
                        byte[] var14 = var13.getBytes();
                        int var9 = var14.length;
                        System.arraycopy(var14, 0, this.buffer, var33, var9);
                        this.buffer[var33 + var9] = 0;
                        result = var9 + 1;
                     } catch (Pal$ArrayOutOfBoundsException var9) {
                        result = 0;
                     }
                     var11 = result;
                  } catch (Pal$ArrayOutOfBoundsException var9) {
                  }
               }
   
               var33 = 0;
               try {
                  byte[] var9 = "NATSPOD".getBytes();
                  int var11 = var9.length;
                  System.arraycopy(var9, 0, this.buffer, var33, var11);
                  this.buffer[var33 + var11] = 0;
               } catch (Pal$ArrayOutOfBoundsException var9) {
               }
               var33 += 7;
               try {
                  int var17 = 0;
                  Integer var18 = 26;
                  String var19 = var18.toString();
                  int result2;
                  try {
                     byte[] var20 = var19.getBytes();
                     int var21 = var20.length;
                     System.arraycopy(var20, 0, this.buffer, var33, var21);
                     this.buffer[var33 + var21] = 0;
                     result2 = var21 + 1;
                  } catch (Pal$ArrayOutOfBoundsException var20) {
                     result2 = 0;
                  }
                  var17 = result2;
               } catch (Pal$ArrayOutOfBoundsException var17) {
               }
               var33 += 8;
               try {
                  int var12 = 0;
                  Integer var13 = 1;
                  String var14 = var13.toString();
                  int result1;
                  try {
                     byte[] var15 = var14.getBytes();
                     int var16 = var15.length;
                     System.arraycopy(var15, 0, this.buffer, var33, var16);
                     this.buffer[var33 + var16] = 0;
                     result1 = var16 + 1;
                  } catch (Pal$ArrayOutOfBoundsException var15) {
                     result1 = 0;
                  }
                  var12 = result1;
               } catch (Pal$ArrayOutOfBoundsException var12) {
               }
               var33 += 3;
               Arrays.fill(this.buffer, var33, var33 + 8, (byte)0);
               try {
                  int var12 = 0;
                  Integer var11 = this.bufferTail - 6;
                  String var13 = var11.toString();
                  int result;
                  try {
                     byte[] var14 = var13.getBytes();
                     int var9 = var14.length;
                     System.arraycopy(var14, 0, this.buffer, var33, var9);
                     this.buffer[var33 + var9] = 0;
                     result = var9 + 1;
                  } catch (Pal$ArrayOutOfBoundsException var9) {
                     result = 0;
                  }
                  var12 = result;
               } catch (Pal$ArrayOutOfBoundsException var9) {
               }
               if (!this.isNewheader) {
                  this.buffer[var33 + 8 - 1] = 1;
               }
   
               byte[] var9 = new byte[this.bufferTail];
               System.arraycopy(this.buffer, 0, var9, 0, this.bufferTail);
               this.outputStream.write(var9);
               PalTrace.buffer(var9, false, this.getSessionId());
               this.bufferTail = 38;
               Arrays.fill(this.buffer, (byte)0);
               if (this.getPalVersion() >= 17) {
                  this.getBuffer = this.transferArea.get();
                  if (this.isPalThreadException) {
                     break;
                  }
   
                  PalTrace.buffer(this.getBuffer, true, this.getSessionId());
                  if (!Arrays.equals(this.getBuffer, "NATSPODNEXTCHUNK          ".getBytes())) {
                     throw new IOException("Internal Error: server deliverd wrong data");
                  }
               }
   
               var36 = true;
            }
         }

         try {
            if (this.isPalThreadException) {
               if (this.palThreadIOException != null) {
                  throw this.palThreadIOException;
               }
   
               if (this.palThreadTimeoutException != null) {
                  throw this.palThreadTimeoutException;
               }
            }
   
         } catch (Pal$ArrayOutOfBoundsException var3) {
         }
         this.type = var2;
      } catch (PalTimeoutException var4) {
         throw var4;
      }

   }

   public void add(IPalType[] var1) throws IOException {
      try {
         int var2 = var1[0].get();
         try {
            if (this.isRetrievePhase) {
               if (this.type != 32004) {
                   try {
                      boolean finished1 = false;
                      try {
                         this.numberOfRecordsReceived = 0;
                         if (this.isReadBuffer) {
                            this.getBuffer = this.transferArea.get();
                            if (this.isPalThreadException) {
                               finished1 = true;
                            } else {
                               PalTrace.buffer(this.getBuffer, true, this.getSessionId());
                               Object var11 = null;
                               boolean var13 = false;
                               int var14;
                               for (var14 = 0; this.getBuffer[0 + var14] != 0; ++var14) {
                               }
                               byte[] var15 = new byte[var14];
                               for (int var16 = 0; this.getBuffer[0 + var16] != 0; ++var16) {
                                  var15[var16] = this.getBuffer[0 + var16];
                               }
                               Integer.valueOf(new String(var15));
                               this.bufferTail = 12;
                               this.isReadBuffer = false;
                               Object var16 = null;
                               boolean var17 = false;
                               int var18;
                               for (var18 = 0; this.getBuffer[this.bufferTail + var18] != 0; ++var18) {
                               }
                               byte[] var19 = new byte[var18];
                               for (int var20 = 0; this.getBuffer[this.bufferTail] != 0; var19[var20++] = this.getBuffer[this.bufferTail++]) {
                               }
                               ++this.bufferTail;
                               this.type = Integer.valueOf(new String(var19));
                            }

                         }
                         if (!finished1) {
                            if (this.type != 32004) {
                               Object var11 = null;
                               boolean var13 = false;
                               int var14 = 0;
                               for (var14 = 0; this.getBuffer[this.bufferTail + var14] != 0; ++var14) {
                               }

                               byte[] var15 = new byte[var14];

                               for (int var16 = 0; this.getBuffer[this.bufferTail + var16] != 0; ++var16) {
                                  var15[var16] = this.getBuffer[this.bufferTail + var16];
                               }

                               this.numberOfRecordsReceived = Integer.valueOf(new String(var15));
                               this.bufferTail += 12;
                            }

                         }

                      } catch (Pal$ArrayOutOfBoundsException var11) {
                      }
                      if (!this.isPalThreadException) {
                         while(this.type != 32004 && this.type != 32004) {
                            this.readRecords(32004);
                            if (this.isPalThreadException) {
                                break;
                            }
             
                            if (this.type == 32004) {
                               break;
                            }

                            boolean finished = false;
                            try {
                               this.numberOfRecordsReceived = 0;
                               if (this.isReadBuffer) {
                                  this.getBuffer = this.transferArea.get();
                                  if (this.isPalThreadException) {
                                     finished = true;
                                  } else {
                                     PalTrace.buffer(this.getBuffer, true, this.getSessionId());
                                     Object var9 = null;
                                     boolean var10 = false;
                                     int var12;
                                     for (var12 = 0; this.getBuffer[0 + var12] != 0; ++var12) {
                                     }
                                     byte[] var5 = new byte[var12];
                                     for (int var6 = 0; this.getBuffer[0 + var6] != 0; ++var6) {
                                        var5[var6] = this.getBuffer[0 + var6];
                                     }
                                     Integer.valueOf(new String(var5));
                                     this.bufferTail = 12;
                                     this.isReadBuffer = false;
                                     Object var6 = null;
                                     boolean var7 = false;
                                     int var8;
                                     for (var8 = 0; this.getBuffer[this.bufferTail + var8] != 0; ++var8) {
                                     }
                                     byte[] var3 = new byte[var8];
                                     for (int var4 = 0; this.getBuffer[this.bufferTail] != 0; var3[var4++] = this.getBuffer[this.bufferTail++]) {
                                     }
                                     ++this.bufferTail;
                                     this.type = Integer.valueOf(new String(var3));
                                  }

                               }
                               if (!finished) {
                                  if (this.type != 32004) {
                                     Object var3 = null;
                                     boolean var4 = false;
                                     int var7 = 0;
                                     for (var7 = 0; this.getBuffer[this.bufferTail + var7] != 0; ++var7) {
                                     }

                                     byte[] var5 = new byte[var7];

                                     for (int var6 = 0; this.getBuffer[this.bufferTail + var6] != 0; ++var6) {
                                        var5[var6] = this.getBuffer[this.bufferTail + var6];
                                     }

                                     this.numberOfRecordsReceived = Integer.valueOf(new String(var5));
                                     this.bufferTail += 12;
                                  }

                               }

                            } catch (Pal$ArrayOutOfBoundsException var3) {
                            }
                            if (this.isPalThreadException) {
                                break;
                            }
                         }
             
                      }
                   } catch (Pal$ArrayOutOfBoundsException var5) {
                   }
                   try {
                     if (this.isPalThreadException) {
                        if (this.palThreadIOException != null) {
                           throw this.palThreadIOException;
                        }
            
                        if (this.palThreadTimeoutException != null) {
                           throw this.palThreadTimeoutException;
                        }
                     }
            
                  } catch (Pal$ArrayOutOfBoundsException var3) {
                  }
               }
   
               Arrays.fill(this.typeCacheOfReceived, (Object)null);
               this.isRetrievePhase = false;
               this.bufferTail = 38;
               this.positionOfRecNumSend = 0;
               this.recNumSend = 0;
               this.isReadBuffer = true;
               Arrays.fill(this.buffer, (byte)0);
               PalTrace.text("\r\n");
            }
         } catch (PalTimeoutException var4) {
            throw var4;
         }

         if (!this.typeSet.add(var2)) {
            throw new IllegalStateException("The last PAL transaction was not commited (Type " + var2 + " is still in queue)");
         } else {
            PalTrace.type(this.PALTYPECLASSES[var2], false);

            for(int var3 = 0; var3 < var1.length; ++var3) {
               var1[var3].setRecord(new ArrayList());
               var1[var3].serialize();
               int var34 = var1[var3].get();
               ArrayList var35 = var1[var3].getRecord();
               boolean var36 = var3 == 0 && var2 != this.type;
               int var37 = 0;
               byte var5 = 0;
               int var6 = var35.size();
               int var7 = 0;

               while(true) {
                  if (var36) {
                     var5 = 42;
                  } else {
                     var5 = 24;
                  }
         
                  if (this.bufferTail + var5 + var6 < this.buffer.length) {
                     if (var36) {
                        if (this.positionOfRecNumSend != 0) {
                           try {
                              int var11 = 0;
                              Integer var12 = this.recNumSend;
                              String var13 = var12.toString();
                              int result;
                              try {
                                 byte[] var8 = var13.getBytes();
                                 int var9 = var8.length;
                                 System.arraycopy(var8, 0, this.buffer, this.positionOfRecNumSend, var9);
                                 this.buffer[this.positionOfRecNumSend + var9] = 0;
                                 result = var9 + 1;
                              } catch (Pal$ArrayOutOfBoundsException var8) {
                                 result = 0;
                              }
                              var11 = result;
                           } catch (Pal$ArrayOutOfBoundsException var8) {
                           }
                           this.recNumSend = 0;
                        }
         
                        int res;
                        try {
                           int var11 = 0;
                           Integer var12 = var34;
                           String var13 = var12.toString();
                           int result;
                           try {
                              byte[] var8 = var13.getBytes();
                              int var9 = var8.length;
                              System.arraycopy(var8, 0, this.buffer, this.bufferTail, var9);
                              this.buffer[this.bufferTail + var9] = 0;
                              result = var9 + 1;
                           } catch (Pal$ArrayOutOfBoundsException var8) {
                              result = 0;
                           }
                           var11 = result;
                           res = this.bufferTail + var11;
                        } catch (Pal$ArrayOutOfBoundsException var8) {
                           res = 0;
                        }
                        this.bufferTail = res;
                        this.positionOfRecNumSend = this.bufferTail;
                        Arrays.fill(this.buffer, this.positionOfRecNumSend, this.positionOfRecNumSend + 12, (byte)32);
                        this.bufferTail += 12;
                     }
         
                     int res1;
                     try {
                        int var18 = 0;
                        Integer var19 = var6;
                        String var20 = var19.toString();
                        int result1;
                        try {
                           byte[] var21 = var20.getBytes();
                           int var22 = var21.length;
                           System.arraycopy(var21, 0, this.buffer, this.bufferTail, var22);
                           this.buffer[this.bufferTail + var22] = 0;
                           result1 = var22 + 1;
                        } catch (Pal$ArrayOutOfBoundsException var21) {
                           result1 = 0;
                        }
                        var18 = result1;
                        res1 = this.bufferTail + var18;
                     } catch (Pal$ArrayOutOfBoundsException var18) {
                        res1 = 0;
                     }
                     this.bufferTail = res1;
                     byte[] var11;
                     try {
                        Object var9 = null;
                        byte[] var10 = new byte[var35.size()];
               
                        for(int var12 = 0; var12 < var35.size(); ++var12) {
                           var10[var12] = (Byte) var35.get(var12);
                        }
         
                        var11 = var10;
                     } catch (Pal$ArrayOutOfBoundsException var9) {
                        var11 = null;
                     }
                     System.arraycopy(var11, var7, this.buffer, this.bufferTail, var6);
                     this.bufferTail += var6;
                     int res;
                     try {
                        int var13 = 0;
                        Integer var14 = 32003;
                        String var15 = var14.toString();
                        int result;
                        try {
                           byte[] var8 = var15.getBytes();
                           int var9 = var8.length;
                           System.arraycopy(var8, 0, this.buffer, this.bufferTail, var9);
                           this.buffer[this.bufferTail + var9] = 0;
                           result = var9 + 1;
                        } catch (Pal$ArrayOutOfBoundsException var8) {
                           result = 0;
                        }
                        var13 = result;
                        res = this.bufferTail + var13;
                     } catch (Pal$ArrayOutOfBoundsException var8) {
                        res = 0;
                     }
                     this.bufferTail = res;
                     ++this.recNumSend;
                     break;
                  }
         
                  var37 = this.buffer.length - (this.bufferTail + var5);
                  if (var37 <= 0) {
                     int var32 = this.bufferTail - 26;
                     int var33 = 26;
                     Arrays.fill(this.buffer, var33, var33 + 12, (byte)0);
                     try {
                        int var27 = 0;
                        Integer var28 = var32;
                        String var29 = var28.toString();
                        int result4;
                        try {
                           byte[] var30 = var29.getBytes();
                           int var31 = var30.length;
                           System.arraycopy(var30, 0, this.buffer, var33, var31);
                           this.buffer[var33 + var31] = 0;
                           result4 = var31 + 1;
                        } catch (Pal$ArrayOutOfBoundsException var30) {
                           result4 = 0;
                        }
                        var27 = result4;
                     } catch (Pal$ArrayOutOfBoundsException var27) {
                     }
                     int res;
                     try {
                        int var22 = 0;
                        Integer var23 = 32001;
                        String var24 = var23.toString();
                        int result3;
                        try {
                           byte[] var25 = var24.getBytes();
                           int var26 = var25.length;
                           System.arraycopy(var25, 0, this.buffer, this.bufferTail, var26);
                           this.buffer[this.bufferTail + var26] = 0;
                           result3 = var26 + 1;
                        } catch (Pal$ArrayOutOfBoundsException var25) {
                           result3 = 0;
                        }
                        var22 = result3;
                        res = this.bufferTail + var22;
                     } catch (Pal$ArrayOutOfBoundsException var22) {
                        res = 0;
                     }
                     this.bufferTail = res;
                     if (this.positionOfRecNumSend != 0) {
                        var33 = this.positionOfRecNumSend;
                        try {
                           int var11 = 0;
                           Integer var12 = this.recNumSend;
                           String var13 = var12.toString();
                           int result;
                           try {
                              byte[] var8 = var13.getBytes();
                              int var9 = var8.length;
                              System.arraycopy(var8, 0, this.buffer, var33, var9);
                              this.buffer[var33 + var9] = 0;
                              result = var9 + 1;
                           } catch (Pal$ArrayOutOfBoundsException var8) {
                              result = 0;
                           }
                           var11 = result;
                        } catch (Pal$ArrayOutOfBoundsException var8) {
                        }
                     }
         
                     var33 = 0;
                     try {
                        byte[] var9 = "NATSPOD".getBytes();
                        int var8 = var9.length;
                        System.arraycopy(var9, 0, this.buffer, var33, var8);
                        this.buffer[var33 + var8] = 0;
                     } catch (Pal$ArrayOutOfBoundsException var8) {
                     }
                     var33 += 7;
                     try {
                        int var17 = 0;
                        Integer var18 = 26;
                        String var19 = var18.toString();
                        int result2;
                        try {
                           byte[] var20 = var19.getBytes();
                           int var21 = var20.length;
                           System.arraycopy(var20, 0, this.buffer, var33, var21);
                           this.buffer[var33 + var21] = 0;
                           result2 = var21 + 1;
                        } catch (Pal$ArrayOutOfBoundsException var20) {
                           result2 = 0;
                        }
                        var17 = result2;
                     } catch (Pal$ArrayOutOfBoundsException var17) {
                     }
                     var33 += 8;
                     try {
                        int var12 = 0;
                        Integer var13 = 1;
                        String var14 = var13.toString();
                        int result1;
                        try {
                           byte[] var15 = var14.getBytes();
                           int var16 = var15.length;
                           System.arraycopy(var15, 0, this.buffer, var33, var16);
                           this.buffer[var33 + var16] = 0;
                           result1 = var16 + 1;
                        } catch (Pal$ArrayOutOfBoundsException var15) {
                           result1 = 0;
                        }
                        var12 = result1;
                     } catch (Pal$ArrayOutOfBoundsException var12) {
                     }
                     var33 += 3;
                     Arrays.fill(this.buffer, var33, var33 + 8, (byte)0);
                     try {
                        int var12 = 0;
                        Integer var11 = this.bufferTail - 6;
                        String var13 = var11.toString();
                        int result;
                        try {
                           byte[] var8 = var13.getBytes();
                           int var9 = var8.length;
                           System.arraycopy(var8, 0, this.buffer, var33, var9);
                           this.buffer[var33 + var9] = 0;
                           result = var9 + 1;
                        } catch (Pal$ArrayOutOfBoundsException var8) {
                           result = 0;
                        }
                        var12 = result;
                     } catch (Pal$ArrayOutOfBoundsException var8) {
                     }
                     if (!this.isNewheader) {
                        this.buffer[var33 + 8 - 1] = 1;
                     }
         
                     byte[] var8 = new byte[this.bufferTail];
                     System.arraycopy(this.buffer, 0, var8, 0, this.bufferTail);
                     this.outputStream.write(var8);
                     PalTrace.buffer(var8, false, this.getSessionId());
                     this.bufferTail = 38;
                     Arrays.fill(this.buffer, (byte)0);
                     if (this.getPalVersion() >= 17) {
                        this.getBuffer = this.transferArea.get();
                        if (this.isPalThreadException) {
                           break;
                        }
         
                        PalTrace.buffer(this.getBuffer, true, this.getSessionId());
                        if (!Arrays.equals(this.getBuffer, "NATSPODNEXTCHUNK          ".getBytes())) {
                           throw new IOException("Internal Error: server deliverd wrong data");
                        }
                     }
         
                     var36 = true;
                  } else {
                     if (var36) {
                        if (this.positionOfRecNumSend != 0) {
                           try {
                              int var11 = 0;
                              Integer var12 = this.recNumSend;
                              String var13 = var12.toString();
                              int result;
                              try {
                                 byte[] var8 = var13.getBytes();
                                 int var9 = var8.length;
                                 System.arraycopy(var8, 0, this.buffer, this.positionOfRecNumSend, var9);
                                 this.buffer[this.positionOfRecNumSend + var9] = 0;
                                 result = var9 + 1;
                              } catch (Pal$ArrayOutOfBoundsException var8) {
                                 result = 0;
                              }
                              var11 = result;
                           } catch (Pal$ArrayOutOfBoundsException var8) {
                           }
                           this.recNumSend = 0;
                        }
         
                        int res;
                        try {
                           int var11 = 0;
                           Integer var12 = var34;
                           String var13 = var12.toString();
                           int result;
                           try {
                              byte[] var8 = var13.getBytes();
                              int var9 = var8.length;
                              System.arraycopy(var8, 0, this.buffer, this.bufferTail, var9);
                              this.buffer[this.bufferTail + var9] = 0;
                              result = var9 + 1;
                           } catch (Pal$ArrayOutOfBoundsException var8) {
                              result = 0;
                           }
                           var11 = result;
                           res = this.bufferTail + var11;
                        } catch (Pal$ArrayOutOfBoundsException var8) {
                           res = 0;
                        }
                        this.bufferTail = res;
                        this.positionOfRecNumSend = this.bufferTail;
                        Arrays.fill(this.buffer, this.positionOfRecNumSend, this.positionOfRecNumSend + 12, (byte)32);
                        this.bufferTail += 12;
                     }
         
                     int res1;
                     try {
                        int var18 = 0;
                        Integer var19 = var37;
                        String var20 = var19.toString();
                        int result1;
                        try {
                           byte[] var21 = var20.getBytes();
                           int var22 = var21.length;
                           System.arraycopy(var21, 0, this.buffer, this.bufferTail, var22);
                           this.buffer[this.bufferTail + var22] = 0;
                           result1 = var22 + 1;
                        } catch (Pal$ArrayOutOfBoundsException var21) {
                           result1 = 0;
                        }
                        var18 = result1;
                        res1 = this.bufferTail + var18;
                     } catch (Pal$ArrayOutOfBoundsException var18) {
                        res1 = 0;
                     }
                     this.bufferTail = res1;
                     byte[] var8;
                     try {
                        Object var10 = null;
                        byte[] var11 = new byte[var35.size()];
               
                        for(int var12 = 0; var12 < var35.size(); ++var12) {
                           var11[var12] = (Byte) var35.get(var12);
                        }
         
                        var8 = var11;
                     } catch (Pal$ArrayOutOfBoundsException var10) {
                        var8 = null;
                     }
                     System.arraycopy(var8, var7, this.buffer, this.bufferTail, var37);
                     this.bufferTail += var37;
                     var7 += var37;
                     var6 -= var37;
                     ++this.recNumSend;
                     if (var6 == 0) {
                        int res;
                        try {
                           int var12 = 0;
                           Integer var13 = 32003;
                           String var14 = var13.toString();
                           int result;
                           try {
                              byte[] var15 = var14.getBytes();
                              int var9 = var15.length;
                              System.arraycopy(var15, 0, this.buffer, this.bufferTail, var9);
                              this.buffer[this.bufferTail + var9] = 0;
                              result = var9 + 1;
                           } catch (Pal$ArrayOutOfBoundsException var9) {
                              result = 0;
                           }
                           var12 = result;
                           res = this.bufferTail + var12;
                        } catch (Pal$ArrayOutOfBoundsException var9) {
                           res = 0;
                        }
                        this.bufferTail = res;
                        break;
                     }
         
                     int var32 = this.bufferTail - 26;
                     int var33 = 26;
                     Arrays.fill(this.buffer, var33, var33 + 12, (byte)0);
                     try {
                        int var27 = 0;
                        Integer var28 = var32;
                        String var29 = var28.toString();
                        int result4;
                        try {
                           byte[] var30 = var29.getBytes();
                           int var31 = var30.length;
                           System.arraycopy(var30, 0, this.buffer, var33, var31);
                           this.buffer[var33 + var31] = 0;
                           result4 = var31 + 1;
                        } catch (Pal$ArrayOutOfBoundsException var30) {
                           result4 = 0;
                        }
                        var27 = result4;
                     } catch (Pal$ArrayOutOfBoundsException var27) {
                     }
                     int res;
                     try {
                        int var22 = 0;
                        Integer var23 = 32002;
                        String var24 = var23.toString();
                        int result3;
                        try {
                           byte[] var25 = var24.getBytes();
                           int var26 = var25.length;
                           System.arraycopy(var25, 0, this.buffer, this.bufferTail, var26);
                           this.buffer[this.bufferTail + var26] = 0;
                           result3 = var26 + 1;
                        } catch (Pal$ArrayOutOfBoundsException var25) {
                           result3 = 0;
                        }
                        var22 = result3;
                        res = this.bufferTail + var22;
                     } catch (Pal$ArrayOutOfBoundsException var22) {
                        res = 0;
                     }
                     this.bufferTail = res;
                     if (this.positionOfRecNumSend != 0) {
                        var33 = this.positionOfRecNumSend;
                        try {
                           int var11 = 0;
                           Integer var12 = this.recNumSend;
                           String var13 = var12.toString();
                           int result;
                           try {
                              byte[] var14 = var13.getBytes();
                              int var9 = var14.length;
                              System.arraycopy(var14, 0, this.buffer, var33, var9);
                              this.buffer[var33 + var9] = 0;
                              result = var9 + 1;
                           } catch (Pal$ArrayOutOfBoundsException var9) {
                              result = 0;
                           }
                           var11 = result;
                        } catch (Pal$ArrayOutOfBoundsException var9) {
                        }
                     }
         
                     var33 = 0;
                     try {
                        byte[] var9 = "NATSPOD".getBytes();
                        int var11 = var9.length;
                        System.arraycopy(var9, 0, this.buffer, var33, var11);
                        this.buffer[var33 + var11] = 0;
                     } catch (Pal$ArrayOutOfBoundsException var9) {
                     }
                     var33 += 7;
                     try {
                        int var17 = 0;
                        Integer var18 = 26;
                        String var19 = var18.toString();
                        int result2;
                        try {
                           byte[] var20 = var19.getBytes();
                           int var21 = var20.length;
                           System.arraycopy(var20, 0, this.buffer, var33, var21);
                           this.buffer[var33 + var21] = 0;
                           result2 = var21 + 1;
                        } catch (Pal$ArrayOutOfBoundsException var20) {
                           result2 = 0;
                        }
                        var17 = result2;
                     } catch (Pal$ArrayOutOfBoundsException var17) {
                     }
                     var33 += 8;
                     try {
                        int var12 = 0;
                        Integer var13 = 1;
                        String var14 = var13.toString();
                        int result1;
                        try {
                           byte[] var15 = var14.getBytes();
                           int var16 = var15.length;
                           System.arraycopy(var15, 0, this.buffer, var33, var16);
                           this.buffer[var33 + var16] = 0;
                           result1 = var16 + 1;
                        } catch (Pal$ArrayOutOfBoundsException var15) {
                           result1 = 0;
                        }
                        var12 = result1;
                     } catch (Pal$ArrayOutOfBoundsException var12) {
                     }
                     var33 += 3;
                     Arrays.fill(this.buffer, var33, var33 + 8, (byte)0);
                     try {
                        int var12 = 0;
                        Integer var11 = this.bufferTail - 6;
                        String var13 = var11.toString();
                        int result;
                        try {
                           byte[] var14 = var13.getBytes();
                           int var9 = var14.length;
                           System.arraycopy(var14, 0, this.buffer, var33, var9);
                           this.buffer[var33 + var9] = 0;
                           result = var9 + 1;
                        } catch (Pal$ArrayOutOfBoundsException var9) {
                           result = 0;
                        }
                        var12 = result;
                     } catch (Pal$ArrayOutOfBoundsException var9) {
                     }
                     if (!this.isNewheader) {
                        this.buffer[var33 + 8 - 1] = 1;
                     }
         
                     byte[] var9 = new byte[this.bufferTail];
                     System.arraycopy(this.buffer, 0, var9, 0, this.bufferTail);
                     this.outputStream.write(var9);
                     PalTrace.buffer(var9, false, this.getSessionId());
                     this.bufferTail = 38;
                     Arrays.fill(this.buffer, (byte)0);
                     if (this.getPalVersion() >= 17) {
                        this.getBuffer = this.transferArea.get();
                        if (this.isPalThreadException) {
                           break;
                        }
         
                        PalTrace.buffer(this.getBuffer, true, this.getSessionId());
                        if (!Arrays.equals(this.getBuffer, "NATSPODNEXTCHUNK          ".getBytes())) {
                           throw new IOException("Internal Error: server deliverd wrong data");
                        }
                     }
         
                     var36 = true;
                  }
               }

               try {
                  if (this.isPalThreadException) {
                     if (this.palThreadIOException != null) {
                        throw this.palThreadIOException;
                     }
         
                     if (this.palThreadTimeoutException != null) {
                        throw this.palThreadTimeoutException;
                     }
                  }
         
               } catch (Pal$ArrayOutOfBoundsException var4) {
               }
            }

            this.type = var2;
         }
      } catch (Pal$ArrayOutOfBoundsException var4) {
      }
   }

   public void closeSocket() throws IOException {
      if (this.ndvConnection != null) {
         this.ndvConnection.close();
         this.ndvConnection = null;
      }

   }

   public void disconnect() throws IOException {
      if (this.ndvConnection != null) {
         if (this.getPalVersion() >= 47) {
            try {
               this.outputStream.write("NATSPODDISCONNECT         ".getBytes());
               PalTrace.buffer("NATSPODDISCONNECT         ".getBytes(), false, this.getSessionId());
            } catch (Pal$ArrayOutOfBoundsException var1) {
            }
         }

         this.closeSocket();
      }

   }

   public void commit() throws IOException {
      try {
         this.typeSet.clear();
         this.type = 65535;
         PalTrace.text("\r\n");
         int var2 = this.bufferTail - 26;
         int var3 = 26;
         Arrays.fill(this.buffer, var3, var3 + 12, (byte)0);
         try {
            int var27 = 0;
            Integer var28 = var2;
            String var29 = var28.toString();
            int result4;
            try {
               byte[] var30 = var29.getBytes();
               int var31 = var30.length;
               System.arraycopy(var30, 0, this.buffer, var3, var31);
               this.buffer[var3 + var31] = 0;
               result4 = var31 + 1;
            } catch (Pal$ArrayOutOfBoundsException var30) {
               result4 = 0;
            }
            var27 = result4;
         } catch (Pal$ArrayOutOfBoundsException var27) {
         }
         int res;
         try {
            int var22 = 0;
            Integer var23 = 32004;
            String var24 = var23.toString();
            int result3;
            try {
               byte[] var25 = var24.getBytes();
               int var26 = var25.length;
               System.arraycopy(var25, 0, this.buffer, this.bufferTail, var26);
               this.buffer[this.bufferTail + var26] = 0;
               result3 = var26 + 1;
            } catch (Pal$ArrayOutOfBoundsException var25) {
               result3 = 0;
            }
            var22 = result3;
            res = this.bufferTail + var22;
         } catch (Pal$ArrayOutOfBoundsException var22) {
            res = 0;
         }
         this.bufferTail = res;
         if (this.positionOfRecNumSend != 0) {
            var3 = this.positionOfRecNumSend;
            try {
               int var6 = 0;
               Integer var4 = this.recNumSend;
               String var5 = var4.toString();
               int result;
               try {
                  byte[] var8 = var5.getBytes();
                  int var9 = var8.length;
                  System.arraycopy(var8, 0, this.buffer, var3, var9);
                  this.buffer[var3 + var9] = 0;
                  result = var9 + 1;
               } catch (Pal$ArrayOutOfBoundsException var8) {
                  result = 0;
               }
               var6 = result;
            } catch (Pal$ArrayOutOfBoundsException var6) {
            }
         }

         var3 = 0;
         try {
            byte[] var5 = "NATSPOD".getBytes();
            int var8 = var5.length;
            System.arraycopy(var5, 0, this.buffer, var3, var8);
            this.buffer[var3 + var8] = 0;
         } catch (Pal$ArrayOutOfBoundsException var5) {
         }
         var3 += 7;
         try {
            int var17 = 0;
            Integer var18 = 26;
            String var19 = var18.toString();
            int result2;
            try {
               byte[] var20 = var19.getBytes();
               int var21 = var20.length;
               System.arraycopy(var20, 0, this.buffer, var3, var21);
               this.buffer[var3 + var21] = 0;
               result2 = var21 + 1;
            } catch (Pal$ArrayOutOfBoundsException var20) {
               result2 = 0;
            }
            var17 = result2;
         } catch (Pal$ArrayOutOfBoundsException var17) {
         }
         var3 += 8;
         try {
            int var12 = 0;
            Integer var13 = 1;
            String var14 = var13.toString();
            int result1;
            try {
               byte[] var15 = var14.getBytes();
               int var16 = var15.length;
               System.arraycopy(var15, 0, this.buffer, var3, var16);
               this.buffer[var3 + var16] = 0;
               result1 = var16 + 1;
            } catch (Pal$ArrayOutOfBoundsException var15) {
               result1 = 0;
            }
            var12 = result1;
         } catch (Pal$ArrayOutOfBoundsException var12) {
         }
         var3 += 3;
         Arrays.fill(this.buffer, var3, var3 + 8, (byte)0);
         try {
            int var6 = 0;
            Integer var11 = this.bufferTail - 6;
            String var5 = var11.toString();
            int result;
            try {
               byte[] var8 = var5.getBytes();
               int var9 = var8.length;
               System.arraycopy(var8, 0, this.buffer, var3, var9);
               this.buffer[var3 + var9] = 0;
               result = var9 + 1;
            } catch (Pal$ArrayOutOfBoundsException var8) {
               result = 0;
            }
            var6 = result;
         } catch (Pal$ArrayOutOfBoundsException var6) {
         }
         if (!this.isNewheader) {
            this.buffer[var3 + 8 - 1] = 1;
         }

         byte[] var4 = new byte[this.bufferTail];
         System.arraycopy(this.buffer, 0, var4, 0, this.bufferTail);
         this.outputStream.write(var4);
         PalTrace.buffer(var4, false, this.getSessionId());
         this.bufferTail = 38;
         Arrays.fill(this.buffer, (byte)0);
      } catch (Pal$ArrayOutOfBoundsException var1) {
      }
   }

   public void connect(String var1, String var2) throws IOException, UnknownHostException {
      try {
         this.init();
         byte[] result;

         synchronized (PalTools.class) {
            String[] var5 = var1.split("\\.");
            byte[] var6 = new byte[4];
            if (var5.length > 1) {
               try {
                  for(int var4 = 0; var4 < var5.length; ++var4) {
                     var6[var4] = Integer.valueOf(var5[var4]).byteValue();
                  }
               } catch (NumberFormatException var4) {
                  var6 = null;
               }
            } else {
               var6 = null;
            }
            result = var6;
         }
         byte[] var3 = result;
         if (var3 != null) {
            this.ndvConnection = new Socket(InetAddress.getByAddress(var3), Integer.valueOf(var2));
         } else {
            this.ndvConnection = new Socket(var1, Integer.valueOf(var2));
         }

         if (this.timeout != 0) {
            this.ndvConnection.setSoTimeout(this.timeout);
         }

         this.ndvConnection.setSoLinger(true, 1);
         this.outputStream = new DataOutputStream(this.ndvConnection.getOutputStream());
         this.inputStream = new DataInputStream(this.ndvConnection.getInputStream());
         (new Thread("PalReceiveThread") {
            private boolean initialiRead = true;

            public void run() {
               byte[] var1 = new byte[26];
               byte[] var2 = new byte[7];
               byte[] var3 = new byte[8];
               byte[] var4 = new byte[3];
               byte[] var5 = new byte[8];
               int var6 = 0;
               int var7 = 0;

               try {
                  while(true) {
                     var7 = (byte)0;
                     int var8 = this.getServerData(var1);
                     if (Pal.this.isPalThreadException || var8 == -1) {
                        if (var8 == -1) {
                           Pal.this.setConnectionLost(true);
                        }

                        return;
                     }

                     if (Arrays.equals(var1, "NATSPODNEXTCHUNK          ".getBytes())) {
                        byte[] var21 = new byte[26];
                        System.arraycopy(var1, 0, var21, 0, var8);
                        Pal.TransferArea.access$1(Pal.this.transferArea, var21);
                     } else {
                        System.arraycopy(var1, 0, var2, 0, 7);
                        if (!Arrays.equals(var2, "NATSPOD".getBytes())) {
                           this.terminateThread(new IOException("Invalid pal header"), (PalTimeoutException)null);
                           return;
                        }

                        var7 += 7;
                        System.arraycopy(var1, var7, var3, 0, 8);
                        Pal.access$2(Pal.this, var3, 0);
                        var7 += 8;
                        System.arraycopy(var1, var7, var4, 0, 3);
                        Pal.access$2(Pal.this, var4, 0);
                        var7 += 3;
                        System.arraycopy(var1, var7, var5, 0, 8);
                        var6 = Pal.access$2(Pal.this, var5, 0);
                        var6 += 6;
                        var6 -= 26;
                        if (!Pal.this.isNewheader && var5[7] == 2) {
                           Pal.access$4(Pal.this, true);
                        }

                        int var9 = 0;
                        byte[] var10 = new byte[4000];
                        byte[] var11 = new byte[var6];

                        do {
                           var8 = Pal.this.inputStream.read(var10, 0, 4000);
                           System.arraycopy(var10, 0, var11, var9, var8);
                           var9 += var8;
                        } while(var9 < var6);

                        Pal.TransferArea.access$1(Pal.this.transferArea, var11);
                     }
                  }
               } catch (IOException var12) {
                  this.terminateThread(var12, (PalTimeoutException)null);
               }
            }

            private void terminateThread(IOException var1, PalTimeoutException var2) {
               try {
                  Pal.this.setConnectionLost(true);
                  Pal.access$6(Pal.this, var1);
                  Pal.access$7(Pal.this, var2);
                  Pal.access$8(Pal.this, true);
                  Pal.TransferArea.access$1(Pal.this.transferArea, new byte[1]);
               } catch (Pal$ArrayOutOfBoundsException var3) {
               }
            }

            private int getServerData(byte[] var1) {
               int var2 = 0;
               boolean var3 = true;

               while(var3) {
                  try {
                     var2 = Pal.this.inputStream.read(var1);
                     if (var2 == -1 && this.initialiRead) {
                        this.terminateThread(new IOException(), (PalTimeoutException)null);
                     }

                     var3 = false;
                     this.initialiRead = false;
                  } catch (SocketTimeoutException var5) {
                     if (Pal.this.timeoutHandler == null) {
                        var3 = false;
                     } else {
                        var3 = Pal.this.timeoutHandler.continueOperation();
                     }

                     if (!var3) {
                        this.terminateThread((IOException)null, new PalTimeoutException("Timeout occured while waiting for Ndv server reply", var5));
                     }
                  } catch (IOException var6) {
                     this.terminateThread(var6, (PalTimeoutException)null);
                     var3 = false;
                  } catch (Exception var7) {
                     var3 = false;
                  }
               }

               return var2;
            }
         }).start();
         this.port = var2;
         this.host = var1;
      } catch (NumberFormatException var4) {
         throw new IllegalArgumentException("port is invalid", var4);
      }

   }

   public boolean isConnectionLost() {
      return this.connectionLost;
   }

   public IPalType[] retrieve(int var1) throws IOException {
      this.isRetrievePhase = true;
      if (var1 >= 0 && var1 < 57) {
         if (this.type != 32004 && this.typeCacheOfReceived[var1] == null) {
             try {
                boolean finished1 = false;
                try {
                   this.numberOfRecordsReceived = 0;
                   if (this.isReadBuffer) {
                      this.getBuffer = this.transferArea.get();
                      if (this.isPalThreadException) {
                         finished1 = true;
                      } else {
                         PalTrace.buffer(this.getBuffer, true, this.getSessionId());
                         Object var11 = null;
                         boolean var13 = false;
                         int var14;
                         for (var14 = 0; this.getBuffer[0 + var14] != 0; ++var14) {
                         }
                         byte[] var15 = new byte[var14];
                         for (int var16 = 0; this.getBuffer[0 + var16] != 0; ++var16) {
                            var15[var16] = this.getBuffer[0 + var16];
                         }
                         Integer.valueOf(new String(var15));
                         this.bufferTail = 12;
                         this.isReadBuffer = false;
                         Object var16 = null;
                         boolean var17 = false;
                         int var18;
                         for (var18 = 0; this.getBuffer[this.bufferTail + var18] != 0; ++var18) {
                         }
                         byte[] var19 = new byte[var18];
                         for (int var20 = 0; this.getBuffer[this.bufferTail] != 0; var19[var20++] = this.getBuffer[this.bufferTail++]) {
                         }
                         ++this.bufferTail;
                         this.type = Integer.valueOf(new String(var19));
                      }

                   }
                   if (!finished1) {
                      if (this.type != 32004) {
                         Object var11 = null;
                         boolean var13 = false;
                         int var14 = 0;
                         for (var14 = 0; this.getBuffer[this.bufferTail + var14] != 0; ++var14) {
                         }

                         byte[] var15 = new byte[var14];

                         for (int var16 = 0; this.getBuffer[this.bufferTail + var16] != 0; ++var16) {
                            var15[var16] = this.getBuffer[this.bufferTail + var16];
                         }

                         this.numberOfRecordsReceived = Integer.valueOf(new String(var15));
                         this.bufferTail += 12;
                      }

                   }

                } catch (Pal$ArrayOutOfBoundsException var11) {
                }
                if (!this.isPalThreadException) {
                   while(this.type != var1 && this.type != 32004) {
                      this.readRecords(var1);
                      if (this.isPalThreadException) {
                          break;
                      }
       
                      if (this.type == 32004) {
                         break;
                      }

                      boolean finished = false;
                      try {
                         this.numberOfRecordsReceived = 0;
                         if (this.isReadBuffer) {
                            this.getBuffer = this.transferArea.get();
                            if (this.isPalThreadException) {
                               finished = true;
                            } else {
                               PalTrace.buffer(this.getBuffer, true, this.getSessionId());
                               Object var9 = null;
                               boolean var10 = false;
                               int var12;
                               for (var12 = 0; this.getBuffer[0 + var12] != 0; ++var12) {
                               }
                               byte[] var5 = new byte[var12];
                               for (int var6 = 0; this.getBuffer[0 + var6] != 0; ++var6) {
                                  var5[var6] = this.getBuffer[0 + var6];
                               }
                               Integer.valueOf(new String(var5));
                               this.bufferTail = 12;
                               this.isReadBuffer = false;
                               Object var6 = null;
                               boolean var2 = false;
                               int var8;
                               for (var8 = 0; this.getBuffer[this.bufferTail + var8] != 0; ++var8) {
                               }
                               byte[] var3 = new byte[var8];
                               for (int var4 = 0; this.getBuffer[this.bufferTail] != 0; var3[var4++] = this.getBuffer[this.bufferTail++]) {
                               }
                               ++this.bufferTail;
                               this.type = Integer.valueOf(new String(var3));
                            }

                         }
                         if (!finished) {
                            if (this.type != 32004) {
                               Object var3 = null;
                               boolean var4 = false;
                               int var7 = 0;
                               for (var7 = 0; this.getBuffer[this.bufferTail + var7] != 0; ++var7) {
                               }

                               byte[] var5 = new byte[var7];

                               for (int var6 = 0; this.getBuffer[this.bufferTail + var6] != 0; ++var6) {
                                  var5[var6] = this.getBuffer[this.bufferTail + var6];
                               }

                               this.numberOfRecordsReceived = Integer.valueOf(new String(var5));
                               this.bufferTail += 12;
                            }

                         }

                      } catch (Pal$ArrayOutOfBoundsException var2) {
                      }
                      if (this.isPalThreadException) {
                          break;
                      }
                   }
       
                }
             } catch (Pal$ArrayOutOfBoundsException var5) {
             }
             try {
               if (this.isPalThreadException) {
                  if (this.palThreadIOException != null) {
                     throw this.palThreadIOException;
                  }
      
                  if (this.palThreadTimeoutException != null) {
                     throw this.palThreadTimeoutException;
                  }
               }
      
            } catch (Pal$ArrayOutOfBoundsException var3) {
            }
            if (this.type != 32004) {
               this.readRecords(var1);
               try {
                  if (this.isPalThreadException) {
                     if (this.palThreadIOException != null) {
                        throw this.palThreadIOException;
                     }
         
                     if (this.palThreadTimeoutException != null) {
                        throw this.palThreadTimeoutException;
                     }
                  }
         
               } catch (Pal$ArrayOutOfBoundsException var2) {
               }
            }
         }

         try {
            Object var2 = null;
            if (this.typeCacheOfReceived[var1] != null) {
               switch (var1) {
                  case 0:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeEnviron[0]);
                     break;
                  case 1:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeConnect[0]);
                  case 2:
                  case 7:
                  case 9:
                  case 16:
                  case 17:
                  case 18:
                  case 21:
                  case 22:
                  case 23:
                  case 24:
                  case 29:
                  case 31:
                  case 32:
                  case 33:
                  case 41:
                  case 43:
                  case 44:
                  case 46:
                  case 47:
                  case 51:
                  default:
                     break;
                  case 3:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeSystemFile[0]);
                     break;
                  case 4:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeLibraryStatistics[0]);
                     break;
                  case 5:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeLibrary[0]);
                     break;
                  case 6:
                  case 30:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeLibId[0]);
                     break;
                  case 8:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeObject[0]);
                     break;
                  case 10:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeResult[0]);
                     break;
                  case 11:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeResultEx[0]);
                     break;
                  case 12:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeSourceCodePage[0]);
                     break;
                  case 13:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeStream[0]);
                     break;
                  case 14:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeUtility[0]);
                     break;
                  case 15:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeSrcDesc[0]);
                     break;
                  case 19:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeNotify[0]);
                     break;
                  case 20:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeGeneric[0]);
                     break;
                  case 25:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeNatParm[0]);
                     break;
                  case 26:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeSQLAuthentification[0]);
                     break;
                  case 27:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeCmdGuard[0]);
                     break;
                  case 28:
                     var2 = (PalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeSysVar[0]);
                     break;
                  case 34:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeDbgStackFrame[0]);
                     break;
                  case 35:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeDbgStatus[0]);
                     break;
                  case 36:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeDbgVarContainer[0]);
                     break;
                  case 37:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeDbgSyt[0]);
                     break;
                  case 38:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeDbgVarDesc[0]);
                     break;
                  case 39:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeDbgVarValue[0]);
                     break;
                  case 40:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeDbgSpy[0]);
                     break;
                  case 42:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeSourceUnicode[0]);
                     break;
                  case 45:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeCP[0]);
                     break;
                  case 48:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeSourceCP[0]);
                     break;
                  case 49:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeDbmsInfo[0]);
                     break;
                  case 50:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeClientConfig[0]);
                     break;
                  case 52:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeDevEnv[0]);
                     break;
                  case 53:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeDbgNatStack[0]);
                     break;
                  case 54:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new PalTypeTimeStamp[0]);
                     break;
                  case 55:
                     var2 = (IPalType[]) this.typeCacheOfReceived[var1].toArray(new IPalTypeDbgaRecord[0]);
               }
            }
   
            return (IPalType[])var2;
         } catch (Pal$ArrayOutOfBoundsException var3) {
            return null;
         }
      } else {
         throw new IllegalArgumentException("illegal PalType Id: " + var1 + ", please refer to 'PalTypeId'class)");
      }
   }

   public void setSessionId(String var1) {
      try {
         this.sessionId = var1;
      } catch (Pal$ArrayOutOfBoundsException var2) {
      }
   }

   public void setUserId(String var1) {
      try {
         this.userId = var1;
      } catch (Pal$ArrayOutOfBoundsException var2) {
      }
   }

   public void setPalVersion(int var1) {
      try {
         this.palVersion = var1;
      } catch (Pal$ArrayOutOfBoundsException var2) {
      }
   }

   public void setNdvType(int var1) {
      try {
         this.ndvType = var1;
      } catch (Pal$ArrayOutOfBoundsException var2) {
      }
   }

   public void setServerCodePage(String var1) {
      try {
         this.serverCodePage = var1;
      } catch (Pal$ArrayOutOfBoundsException var2) {
      }
   }

   public String toString() {
      try {
         return "Pal connection to " + this.host + "-" + this.port;
      } catch (Pal$ArrayOutOfBoundsException var1) {
         return null;
      }
   }

   public void setPalTimeoutHandler(IPalTimeoutHandler var1) {
      try {
         this.timeoutHandler = var1;
      } catch (Pal$ArrayOutOfBoundsException var2) {
      }
   }

   int getPalVersion() {
      return this.palVersion;
   }

   int getNdvType() {
      return this.ndvType;
   }

   public String getSessionId() {
      return this.sessionId;
   }

   private void readRecords(int param1) throws IOException {
      // $FF: Couldn't be decompiled
      
      // ToDo: Probably calls otherwise unused getRecord???
   }

   // currently unused
   private void getRecord(ArrayList var1) throws IOException {
      int var2 = 0;
      int var3 = 0;
      int var4 = 0;
      Object var9 = null;
      boolean var10 = false;
      int var26;
      for(var26 = 0; this.getBuffer[this.bufferTail + var26] != 0; ++var26) {
      }

      byte[] var11 = new byte[var26];

      for(int var12 = 0; this.getBuffer[this.bufferTail] != 0; var11[var12++] = this.getBuffer[this.bufferTail++]) {
      }

      ++this.bufferTail;
      var4 = Integer.valueOf(new String(var11));
      var3 = this.bufferTail;
      var2 = var3 + var4;
      Object var18 = null;
      boolean var19 = false;
      int var24;
      for(var24 = 0; this.getBuffer[var2 + var24] != 0; ++var24) {
      }

      byte[] var20 = new byte[var24];

      for(int var21 = 0; this.getBuffer[var2 + var21] != 0; ++var21) {
         var20[var21] = this.getBuffer[var2 + var21];
      }

      this.type = Integer.valueOf(new String(var20));
      if (this.type == 32002) {
         while(this.type == 32002) {
            ArrayList var25 = new ArrayList();

            for(int var27 = 0; var27 < var4; ++var27) {
               var25.add(this.getBuffer[var3 + var27]);
            }

            var1.addAll(var25);
            if (this.getPalVersion() >= 17) {
               try {
                  this.outputStream.write("NATSPODNEXTCHUNK          ".getBytes());
                  PalTrace.buffer("NATSPODNEXTCHUNK          ".getBytes(), false, this.getSessionId());
               } catch (Pal$ArrayOutOfBoundsException var5) {
               }
            }

            this.isReadBuffer = true;
            boolean finished = false;
            try {
               this.numberOfRecordsReceived = 0;
               if (this.isReadBuffer) {
                  this.getBuffer = this.transferArea.get();
                  if (this.isPalThreadException) {
                     finished = true;
                  } else {
                     PalTrace.buffer(this.getBuffer, true, this.getSessionId());
                     Object var21 = null;
                     boolean var23 = false;
                     int var12;
                     for (var12 = 0; this.getBuffer[0 + var12] != 0; ++var12) {
                     }
                     byte[] var27 = new byte[var12];
                     for (int var28 = 0; this.getBuffer[0 + var28] != 0; ++var28) {
                        var27[var28] = this.getBuffer[0 + var28];
                     }
                     Integer.valueOf(new String(var27));
                     this.bufferTail = 12;
                     this.isReadBuffer = false;
                     Object var28 = null;
                     boolean var29 = false;
                     int var30;
                     for (var30 = 0; this.getBuffer[this.bufferTail + var30] != 0; ++var30) {
                     }
                     byte[] var31 = new byte[var30];
                     for (int var32 = 0; this.getBuffer[this.bufferTail] != 0; var31[var32++] = this.getBuffer[this.bufferTail++]) {
                     }
                     ++this.bufferTail;
                     this.type = Integer.valueOf(new String(var31));
                  }

               }
               if (!finished) {
                  if (this.type != 32004) {
                     Object var12 = null;
                     boolean var21 = false;
                     int var23 = 0;
                     for (var23 = 0; this.getBuffer[this.bufferTail + var23] != 0; ++var23) {
                     }

                     byte[] var27 = new byte[var23];

                     for (int var28 = 0; this.getBuffer[this.bufferTail + var28] != 0; ++var28) {
                        var27[var28] = this.getBuffer[this.bufferTail + var28];
                     }

                     this.numberOfRecordsReceived = Integer.valueOf(new String(var27));
                     this.bufferTail += 12;
                  }

               }

            } catch (Pal$ArrayOutOfBoundsException var12) {
            }
            if (this.isPalThreadException) {
               return;
            }

            Object var5 = null;
            boolean var6 = false;
            int var22;
            for(var22 = 0; this.getBuffer[this.bufferTail + var22] != 0; ++var22) {
            }

            byte[] var7 = new byte[var22];

            for(int var8 = 0; this.getBuffer[this.bufferTail] != 0; var7[var8++] = this.getBuffer[this.bufferTail++]) {
            }

            ++this.bufferTail;
            var4 = Integer.valueOf(new String(var7));
            var3 = this.bufferTail;
            var2 = var3 + var4;
            Object var13 = null;
            boolean var14 = false;
            int var17;
            for(var17 = 0; this.getBuffer[var2 + var17] != 0; ++var17) {
            }

            byte[] var15 = new byte[var17];

            for(int var16 = 0; this.getBuffer[var2 + var16] != 0; ++var16) {
               var15[var16] = this.getBuffer[var2 + var16];
            }

            this.type = Integer.valueOf(new String(var15));
         }
      }

      if (this.type == 32003) {
         ArrayList var12 = new ArrayList();

         for(int var5 = 0; var5 < var4; ++var5) {
            var12.add(this.getBuffer[var3 + var5]);
         }

         var1.addAll(var12);
         this.bufferTail = var2 + 6;
      }

   }

   public void setConnectionLost(boolean var1) {
      try {
         this.connectionLost = var1;
      } catch (Pal$ArrayOutOfBoundsException var2) {
      }
   }

   // $FF: synthetic method
   static int access$2(Pal var0, byte[] var1, int var2) {
      try {
         Object var3 = null;
         boolean var4 = false;
         int var9;
         for(var9 = 0; var1[var2 + var9] != 0; ++var9) {
         }

         byte[] var5 = new byte[var9];

         for(int var6 = 0; var1[var2 + var6] != 0; ++var6) {
            var5[var6] = var1[var2 + var6];
         }

         return Integer.valueOf(new String(var5));
      } catch (Pal$ArrayOutOfBoundsException var3) {
         return 0;
      }
   }

   // $FF: synthetic method
   static void access$4(Pal var0, boolean var1) {
      try {
         var0.isNewheader = var1;
      } catch (Pal$ArrayOutOfBoundsException var2) {
      }
   }

   // $FF: synthetic method
   static void access$6(Pal var0, IOException var1) {
      try {
         var0.palThreadIOException = var1;
      } catch (Pal$ArrayOutOfBoundsException var2) {
      }
   }

   // $FF: synthetic method
   static void access$7(Pal var0, PalTimeoutException var1) {
      try {
         var0.palThreadTimeoutException = var1;
      } catch (Pal$ArrayOutOfBoundsException var2) {
      }
   }

   // $FF: synthetic method
   static void access$8(Pal var0, boolean var1) {
      try {
         var0.isPalThreadException = var1;
      } catch (Pal$ArrayOutOfBoundsException var2) {
      }
   }

   private static class TransferArea {
      private byte[] buffer;
      private boolean available;

      private TransferArea() {
         this.available = false;
      }

      private synchronized byte[] get() {
         while(!this.available) {
            try {
               this.wait();
            } catch (InterruptedException var1) {
            }
         }

         this.available = false;
         this.notifyAll();
         return this.buffer;
      }

      private synchronized void put(byte[] var1) {
         while(this.available) {
            try {
               this.wait();
            } catch (InterruptedException var2) {
            }
         }

         this.buffer = var1;
         this.available = true;
         this.notifyAll();
      }

      // $FF: synthetic method
      TransferArea(TransferArea var1) {
         this();
      }

      // $FF: synthetic method
      static void access$1(TransferArea var0, byte[] var1) {
         try {
            var0.put(var1);
         } catch (Pal$ArrayOutOfBoundsException var2) {
         }
      }
   }
}
