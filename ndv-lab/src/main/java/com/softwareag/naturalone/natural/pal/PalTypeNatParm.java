package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IBuffSize;
import com.softwareag.naturalone.natural.pal.external.ICharAssign;
import com.softwareag.naturalone.natural.pal.external.ICompOpt;
import com.softwareag.naturalone.natural.pal.external.IErr;
import com.softwareag.naturalone.natural.pal.external.IFldApp;
import com.softwareag.naturalone.natural.pal.external.ILimit;
import com.softwareag.naturalone.natural.pal.external.IPalTypeNatParm;
import com.softwareag.naturalone.natural.pal.external.IRegional;
import com.softwareag.naturalone.natural.pal.external.IReport;
import com.softwareag.naturalone.natural.pal.external.IRpc;
import java.io.Serializable;

public final class PalTypeNatParm extends PalType implements IPalTypeNatParm {
   private static final long serialVersionUID = 5628054986435217381L;
   public static final int PREC_REPORT = 0;
   public static final int PREC_LIMIT = 1;
   public static final int PREC_FLDAPP = 2;
   public static final int PREC_CHARASS = 3;
   public static final int PREC_ERR = 4;
   public static final int PREC_COMPOPT = 5;
   public static final int PREC_RPC = 6;
   public static final int PREC_SIZES = 7;
   public static final int PREC_REGSET = 8;
   public static final int PREC_LAST = 8;
   public static final int P_LIM_LE = 1;
   public static final int P_LIM_NC = 2;
   public static final int P_LIM_OPF = 4;
   public static final char P_FLD_DTEURO = 'E';
   public static final char P_FLD_DTGERM = 'G';
   public static final char P_FLD_DTINTER = 'I';
   public static final char P_FLD_DTUSA = 'U';
   public static final int P_FLD_ZP = 1;
   public static final int P_FLD_FCDP = 2;
   public static final int P_FLD_TS = 4;
   public static final int P_FLD_MSTOP = 8;
   public static final int P_FLD_MSBOTTOM = 16;
   public static final int P_ERR_SA = 1;
   public static final int P_ERR_ZD = 2;
   public static final int P_ERR_REINP = 4;
   public static final int P_ERR_WH = 8;
   public static final int P_COPT_FS = 8;
   public static final int P_COPT_SM = 16;
   public static final int P_COPT_SYMGEN = 32;
   public static final int P_COPT_GFIDON = 512;
   public static final int P_COPT_GFIDVID = 1024;
   public static final int P_COPT_XREFON = 2048;
   public static final int P_COPT_PCHECK = 4096;
   public static final int P_COPT_KCHECK = 8192;
   public static final int P_COPT_THSEP = 16384;
   public static final int P_COPT_XREFFORCE = 65536;
   public static final int P_COPT_XREFDOC = 131072;
   public static final int P_COPT_RENCONST = 1048576;
   public static final int P_RPC_RETRY = 1;
   private Limit limit;
   private Report report;
   private FldApp fldApp;
   private CharAssign charAssign;
   private Err err;
   private CompOpt compOpt;
   private Rpc rpc;
   private BuffSize buffSize;
   private Regional regional;
   private int recordIndex;

   public PalTypeNatParm() {
      super.type = 25;
   }

   public PalTypeNatParm(IPalTypeNatParm var1) {
      super.type = 25;
      this.setRecordIndex(var1.getRecordIndex());
      switch (this.getRecordIndex()) {
         case 0:
            this.setReport(new Report(var1.getReport()));
            break;
         case 1:
            this.setLimit(new Limit(var1.getLimit()));
            break;
         case 2:
            this.setFldApp(new FldApp(var1.getFldApp()));
            break;
         case 3:
            this.setCharAssign(new CharAssign(var1.getCharAssign()));
            break;
         case 4:
            this.setErr(new Err(var1.getErr()));
            break;
         case 5:
            this.setCompOpt(new CompOpt(var1.getCompOpt()));
            break;
         case 6:
            this.setRpc(new Rpc(var1.getRpc()));
            break;
         case 7:
            this.setBuffSize(new BuffSize(var1.getBuffSize()));
            break;
         case 8:
            this.setRegional(new Regional(var1.getRegional()));
      }

   }

   public void serialize() {
      try {
         if (this.recordIndex >= 0 && this.recordIndex <= 8) {
            this.intToBuffer(this.recordIndex);
            switch (this.recordIndex) {
               case 0:
                  this.report.serialize();
                  break;
               case 1:
                  this.limit.serialize();
                  break;
               case 2:
                  this.fldApp.serialize();
                  break;
               case 3:
                  this.charAssign.serialize();
                  break;
               case 4:
                  this.err.serialize();
                  break;
               case 5:
                  this.compOpt.serialize();
                  break;
               case 6:
                  this.rpc.serialize();
                  break;
               case 7:
                  this.buffSize.serialize();
                  break;
               case 8:
                  this.regional.serialize();
            }
         }

      } catch (PalTypeNatParm$ParseException var1) {
      }
   }

   public void setLimit(Limit var1) {
      try {
         this.limit = var1;
      } catch (PalTypeNatParm$ParseException var2) {
      }
   }

   public void setReport(Report var1) {
      try {
         this.report = var1;
      } catch (PalTypeNatParm$ParseException var2) {
      }
   }

   public void setFldApp(FldApp var1) {
      try {
         this.fldApp = var1;
      } catch (PalTypeNatParm$ParseException var2) {
      }
   }

   public void setCharAssign(CharAssign var1) {
      try {
         this.charAssign = var1;
      } catch (PalTypeNatParm$ParseException var2) {
      }
   }

   public void setErr(Err var1) {
      try {
         this.err = var1;
      } catch (PalTypeNatParm$ParseException var2) {
      }
   }

   public void setCompOpt(CompOpt var1) {
      try {
         this.compOpt = var1;
      } catch (PalTypeNatParm$ParseException var2) {
      }
   }

   public void setRpc(Rpc var1) {
      try {
         this.rpc = var1;
      } catch (PalTypeNatParm$ParseException var2) {
      }
   }

   public void setBuffSize(BuffSize var1) {
      try {
         this.buffSize = var1;
      } catch (PalTypeNatParm$ParseException var2) {
      }
   }

   public void setRegional(Regional var1) {
      try {
         this.regional = var1;
      } catch (PalTypeNatParm$ParseException var2) {
      }
   }

   public void restore() {
      try {
         this.recordIndex = this.intFromBuffer();
         if (this.recordIndex >= 0 && this.recordIndex <= 8) {
            switch (this.recordIndex) {
               case 0:
                  this.report = new Report();
                  this.report.restore();
                  break;
               case 1:
                  this.limit = new Limit();
                  this.limit.restore();
                  break;
               case 2:
                  this.fldApp = new FldApp();
                  this.fldApp.restore();
                  break;
               case 3:
                  this.charAssign = new CharAssign();
                  this.charAssign.restore();
                  break;
               case 4:
                  this.err = new Err();
                  this.err.restore();
                  break;
               case 5:
                  this.compOpt = new CompOpt();
                  this.compOpt.restore();
                  break;
               case 6:
                  this.rpc = new Rpc();
                  this.rpc.restore();
                  break;
               case 7:
                  this.buffSize = new BuffSize();
                  this.buffSize.restore();
                  break;
               case 8:
                  this.regional = new Regional();
                  this.regional.restore();
            }
         }

      } catch (PalTypeNatParm$ParseException var1) {
      }
   }

   public final Limit getLimit() {
      return this.limit;
   }

   public final BuffSize getBuffSize() {
      return this.buffSize;
   }

   public final CharAssign getCharAssign() {
      return this.charAssign;
   }

   public final CompOpt getCompOpt() {
      return this.compOpt;
   }

   public final Err getErr() {
      return this.err;
   }

   public final FldApp getFldApp() {
      return this.fldApp;
   }

   public void setRecordIndex(int var1) {
      try {
         this.recordIndex = var1;
      } catch (PalTypeNatParm$ParseException var2) {
      }
   }

   public final int getRecordIndex() {
      return this.recordIndex;
   }

   public final Regional getRegional() {
      return this.regional;
   }

   public final Report getReport() {
      return this.report;
   }

   public final Rpc getRpc() {
      return this.rpc;
   }

   public String toString() {
      if (this.limit != null) {
         return this.limit.toString();
      } else if (this.report != null) {
         return this.report.toString();
      } else if (this.fldApp != null) {
         return this.fldApp.toString();
      } else if (this.charAssign != null) {
         return this.charAssign.toString();
      } else if (this.err != null) {
         return this.err.toString();
      } else if (this.compOpt != null) {
         return this.compOpt.toString();
      } else if (this.rpc != null) {
         return this.rpc.toString();
      } else if (this.buffSize != null) {
         return this.buffSize.toString();
      } else {
         return this.regional != null ? this.regional.toString() : "";
      }
   }

   public class BuffSize implements IBuffSize, Serializable {
      private static final long serialVersionUID = -5106606349201671321L;
      private int edtSize;
      private int size2;
      private int size3;
      private int size4;
      private int size5;

      public BuffSize() {
      }

      public BuffSize(BuffSize var2) {
         if (var2 != null) {
            this.edtSize = var2.edtSize;
            this.size2 = var2.size2;
            this.size3 = var2.size3;
            this.size4 = var2.size4;
            this.size5 = var2.size5;
         }

      }

      private void restore() {
         try {
            this.edtSize = PalTypeNatParm.this.intFromBuffer();
            this.size2 = PalTypeNatParm.this.intFromBuffer();
            this.size3 = PalTypeNatParm.this.intFromBuffer();
            this.size4 = PalTypeNatParm.this.intFromBuffer();
            this.size5 = PalTypeNatParm.this.intFromBuffer();
         } catch (PalTypeNatParm$ParseException var1) {
         }
      }

      private void serialize() {
         try {
            PalTypeNatParm.this.intToBuffer(this.edtSize);
            PalTypeNatParm.this.intToBuffer(this.size2);
            PalTypeNatParm.this.intToBuffer(this.size3);
            PalTypeNatParm.this.intToBuffer(this.size4);
            PalTypeNatParm.this.intToBuffer(this.size5);
         } catch (PalTypeNatParm$ParseException var1) {
         }
      }

      public final int getEdtSize() {
         return this.edtSize;
      }

      public final int getSize2() {
         return this.size2;
      }

      public final int getSize3() {
         return this.size3;
      }

      public final int getSize4() {
         return this.size4;
      }

      public final int getSize5() {
         return this.size5;
      }

      public String toString() {
         String var1 = "";
         if (this.edtSize != 0) {
            var1 = var1 + "EDTBPSIZE=" + this.edtSize + " ";
         }

         return var1;
      }
   }

   public class CharAssign implements Serializable, ICharAssign {
      private static final long serialVersionUID = 177909023595058115L;
      private byte termCommandChar;
      private byte decimalChar;
      private byte inputAssignment;
      private byte inputDelimiter;
      private byte thousandSeperator;

      public CharAssign() {
      }

      public CharAssign(CharAssign var2) {
         if (var2 != null) {
            this.termCommandChar = var2.termCommandChar;
            this.decimalChar = var2.decimalChar;
            this.inputAssignment = var2.inputAssignment;
            this.inputDelimiter = var2.inputDelimiter;
            this.thousandSeperator = var2.thousandSeperator;
         }

      }

      private void restore() {
         this.termCommandChar = PalTypeNatParm.this.byteFromBuffer();
         this.decimalChar = PalTypeNatParm.this.byteFromBuffer();
         this.inputAssignment = PalTypeNatParm.this.byteFromBuffer();
         this.inputDelimiter = PalTypeNatParm.this.byteFromBuffer();
         if (PalTypeNatParm.super.recordTail < PalTypeNatParm.super.recordLength) {
            this.thousandSeperator = PalTypeNatParm.this.byteFromBuffer();
         }

      }

      private void serialize() {
         try {
            PalTypeNatParm.this.byteToBuffer(this.termCommandChar);
            PalTypeNatParm.this.byteToBuffer(this.decimalChar);
            PalTypeNatParm.this.byteToBuffer(this.inputAssignment);
            PalTypeNatParm.this.byteToBuffer(this.inputDelimiter);
            PalTypeNatParm.this.byteToBuffer(this.thousandSeperator);
         } catch (PalTypeNatParm$ParseException var1) {
         }
      }

      public final byte getDecimalChar() {
         return this.decimalChar;
      }

      public final byte getInputAssignment() {
         return this.inputAssignment;
      }

      public final byte getInputDelimiter() {
         return this.inputDelimiter;
      }

      public final byte getTermCommandChar() {
         return this.termCommandChar;
      }

      public final byte getThousandSeperator() {
         return this.thousandSeperator;
      }

      public final void setDecimalChar(byte var1) {
         try {
            this.decimalChar = var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }

      public final void setInputAssignment(byte var1) {
         try {
            this.inputAssignment = var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }

      public final void setInputDelimiter(byte var1) {
         try {
            this.inputDelimiter = var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }

      public final void setTermCommandChar(byte var1) {
         try {
            this.termCommandChar = var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }

      public final void setThousandSeperator(byte var1) {
         try {
            this.thousandSeperator = var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }

      public String toString() {
         String var1 = "";
         if (this.termCommandChar != 0) {
            var1 = var1 + "CF=" + (char)this.termCommandChar + " ";
         }

         if (this.decimalChar != 0) {
            var1 = var1 + "DC=" + (char)this.decimalChar + " ";
         }

         if (this.inputAssignment != 0) {
            var1 = var1 + "IA=" + (char)this.inputAssignment + " ";
         }

         if (this.inputDelimiter != 0) {
            var1 = var1 + "ID=" + (char)this.inputDelimiter + " ";
         }

         if (this.thousandSeperator != 0) {
            var1 = var1 + "THSEPCH=" + (char)this.thousandSeperator + " ";
         }

         return var1;
      }
   }

   public class CompOpt implements Serializable, ICompOpt {
      private static final long serialVersionUID = 2321652677518594514L;
      private int flags;
      private int sourceLinelength;
      private int maxprec;

      public CompOpt() {
      }

      public CompOpt(CompOpt var2) {
         if (var2 != null) {
            this.flags = var2.flags;
            this.sourceLinelength = var2.sourceLinelength;
            this.maxprec = var2.maxprec;
         }

      }

      private void restore() {
         this.flags = PalTypeNatParm.this.intFromBuffer();
         this.sourceLinelength = PalTypeNatParm.this.intFromBuffer();
         if (PalTypeNatParm.super.recordTail < PalTypeNatParm.super.recordLength) {
            this.maxprec = PalTypeNatParm.this.intFromBuffer();
         }

      }

      private void serialize() {
         try {
            PalTypeNatParm.this.intToBuffer(this.flags);
            PalTypeNatParm.this.intToBuffer(this.sourceLinelength);
            PalTypeNatParm.this.intToBuffer(this.maxprec);
         } catch (PalTypeNatParm$ParseException var1) {
         }
      }

      public final int getFlags() {
         return this.flags;
      }

      public final int getSourceLinelength() {
         return this.sourceLinelength;
      }

      public final int getMaxprec() {
         return this.maxprec;
      }

      public final void setMaxprec(int var1) {
         try {
            this.maxprec = var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }

      public final void setFlags(int var1) {
         try {
            this.flags |= var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }

      public final void resetFlags(int var1) {
         try {
            this.flags &= ~var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }

      public String toString() {
         String var1 = "";
         if ((this.getFlags() & 8) == 8) {
            var1 = var1 + "FS ";
         }

         if ((this.getFlags() & 16) == 16) {
            var1 = var1 + "SM=ON ";
         } else {
            var1 = var1 + "SM=OFF ";
         }

         if ((this.getFlags() & 32) == 32) {
            var1 = var1 + "SYMGEN=ON ";
         } else {
            var1 = var1 + "SYMGEN=OFF ";
         }

         if ((this.getFlags() & 512) == 512) {
            var1 = var1 + "GFID=ON ";
         }

         if ((this.getFlags() & 2048) == 2048) {
            var1 = var1 + "XREF=ON ";
         }

         if ((this.getFlags() & 65536) == 65536) {
            var1 = var1 + "XREF=FORCE ";
         }

         if ((this.getFlags() & 131072) == 131072) {
            var1 = var1 + "XREF=DOC ";
         }

         if ((this.getFlags() & 1) == 1) {
            var1 = var1 + "RPCRETRY ";
         }

         if (this.maxprec >= 7 && this.maxprec <= 29) {
            var1 = var1 + "MAXPREC=" + this.maxprec + " ";
         }

         return var1;
      }
   }

   public class Err implements IErr, Serializable {
      private static final long serialVersionUID = -810690567149007343L;
      private int flags;

      public Err() {
      }

      public Err(Err var2) {
         if (var2 != null) {
            this.flags = var2.flags;
         }

      }

      private void restore() {
         try {
            this.flags = PalTypeNatParm.this.intFromBuffer();
         } catch (PalTypeNatParm$ParseException var1) {
         }
      }

      private void serialize() {
         try {
            PalTypeNatParm.this.intToBuffer(this.flags);
         } catch (PalTypeNatParm$ParseException var1) {
         }
      }

      public final int getFlags() {
         return this.flags;
      }

      public String toString() {
         String var1 = "";
         if ((this.getFlags() & 1) == 1) {
            var1 = var1 + "SA=ON ";
         }

         if ((this.getFlags() & 2) == 2) {
            var1 = var1 + "ZD=ON ";
         }

         if ((this.getFlags() & 4) == 4) {
            var1 = var1 + "REINP=ON ";
         }

         if ((this.getFlags() & 8) == 8) {
            var1 = var1 + "WH=ON ";
         }

         return var1;
      }
   }

   public class FldApp implements Serializable, IFldApp {
      private static final long serialVersionUID = 5389288311795757678L;
      private int flags;
      private byte dateFormatOutput;
      private byte dateFormatStack;
      private byte dateFormatTitle;
      private byte printMode;
      private byte dateFormat;
      private int maxyear;

      public String toString() {
         String var1 = "";
         if ((this.getFlags() & 1) == 1) {
            var1 = var1 + "ZP=ON ";
         }

         if ((this.getFlags() & 2) == 2) {
            var1 = var1 + "FCDP=ON ";
         }

         if ((this.getFlags() & 4) == 4) {
            var1 = var1 + "TS=ON ";
         }

         if ((this.getFlags() & 8) == 8) {
            var1 = var1 + "Message Line Top ";
         }

         if ((this.getFlags() & 16) == 16) {
            var1 = var1 + "Message Line Bottom ";
         }

         if (this.dateFormatOutput != 0) {
            var1 = var1 + "DFOUT=" + (char)this.dateFormatOutput + " ";
         }

         if (this.dateFormatStack != 0) {
            var1 = var1 + "DFSTACK=" + (char)this.dateFormatStack + " ";
         }

         if (this.dateFormatTitle != 0) {
            var1 = var1 + "DFTITLE=" + (char)this.dateFormatTitle + " ";
         }

         if (this.printMode != 0) {
            var1 = var1 + "PM=" + (char)this.printMode + " ";
         }

         if (this.dateFormat != 0) {
            var1 = var1 + "DTFORM=" + (char)this.dateFormat + " ";
         }

         if (this.maxyear != 0) {
            var1 = var1 + "MAXYEAR=" + this.maxyear + " ";
         }

         return var1;
      }

      public FldApp() {
      }

      public FldApp(FldApp var2) {
         if (var2 != null) {
            this.flags = var2.flags;
            this.dateFormatOutput = var2.dateFormatOutput;
            this.dateFormatStack = var2.dateFormatStack;
            this.dateFormatTitle = var2.dateFormatTitle;
            this.printMode = var2.printMode;
            this.dateFormat = var2.dateFormat;
            this.maxyear = var2.maxyear;
         }

      }

      private void restore() {
         this.flags = PalTypeNatParm.this.intFromBuffer();
         this.dateFormatOutput = PalTypeNatParm.this.byteFromBuffer();
         this.dateFormatStack = PalTypeNatParm.this.byteFromBuffer();
         this.dateFormatTitle = PalTypeNatParm.this.byteFromBuffer();
         this.printMode = PalTypeNatParm.this.byteFromBuffer();
         if (PalTypeNatParm.super.recordTail < PalTypeNatParm.super.recordLength) {
            this.dateFormat = PalTypeNatParm.this.byteFromBuffer();
         }

         if (PalTypeNatParm.super.recordTail < PalTypeNatParm.super.recordLength) {
            this.maxyear = PalTypeNatParm.this.intFromBuffer();
         }

      }

      private void serialize() {
         try {
            PalTypeNatParm.this.intToBuffer(this.flags);
            PalTypeNatParm.this.byteToBuffer(this.dateFormatOutput);
            PalTypeNatParm.this.byteToBuffer(this.dateFormatStack);
            PalTypeNatParm.this.byteToBuffer(this.dateFormatTitle);
            PalTypeNatParm.this.byteToBuffer(this.printMode);
            PalTypeNatParm.this.byteToBuffer(this.dateFormat);
            PalTypeNatParm.this.intToBuffer(this.maxyear);
         } catch (PalTypeNatParm$ParseException var1) {
         }
      }

      public final byte getDateFormat() {
         return this.dateFormat;
      }

      public final byte getDateFormatOutput() {
         return this.dateFormatOutput;
      }

      public final byte getDateFormatStack() {
         return this.dateFormatStack;
      }

      public final byte getDateFormatTitle() {
         return this.dateFormatTitle;
      }

      public final int getFlags() {
         return this.flags;
      }

      public final int getMaxyear() {
         return this.maxyear;
      }

      public final byte getPrintMode() {
         return this.printMode;
      }

      public void setDateFormat(byte var1) {
         try {
            this.dateFormat = var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }

      public void setDateFormatOutput(byte var1) {
         try {
            this.dateFormatOutput = var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }

      public void setDateFormatStack(byte var1) {
         try {
            this.dateFormatStack = var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }

      public void setDateFormatTitle(byte var1) {
         try {
            this.dateFormatTitle = var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }

      public void setFlags(int var1) {
         try {
            this.flags |= var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }

      public void setMaxyear(int var1) {
         try {
            this.maxyear = var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }

      public void setPrintMode(byte var1) {
         try {
            this.printMode = var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }
   }

   public class Limit implements Serializable, ILimit {
      private static final long serialVersionUID = -8664599396389272547L;
      private int flags;
      private int processingLoopLimit;
      private int maximumCPUTime;
      private int pageDataSet;

      public Limit() {
      }

      public String toString() {
         String var1 = "";
         if (this.processingLoopLimit != 0) {
            var1 = var1 + "LT=" + this.processingLoopLimit + " ";
         }

         if (this.maximumCPUTime != 0) {
            var1 = var1 + "MCPU=" + this.maximumCPUTime + " ";
         }

         if (this.pageDataSet != 0) {
            var1 = var1 + "PAGEDATASET=" + this.pageDataSet + " ";
         }

         return var1;
      }

      public Limit(Limit var2) {
         if (var2 != null) {
            this.flags = var2.flags;
            this.processingLoopLimit = var2.processingLoopLimit;
            this.maximumCPUTime = var2.maximumCPUTime;
            this.pageDataSet = var2.pageDataSet;
         }

      }

      private void restore() {
         try {
            this.flags = PalTypeNatParm.this.intFromBuffer();
            this.processingLoopLimit = PalTypeNatParm.this.intFromBuffer();
            this.maximumCPUTime = PalTypeNatParm.this.intFromBuffer();
            this.pageDataSet = PalTypeNatParm.this.intFromBuffer();
         } catch (PalTypeNatParm$ParseException var1) {
         }
      }

      private void serialize() {
         try {
            PalTypeNatParm.this.intToBuffer(this.flags);
            PalTypeNatParm.this.intToBuffer(this.processingLoopLimit);
            PalTypeNatParm.this.intToBuffer(this.maximumCPUTime);
            PalTypeNatParm.this.intToBuffer(this.pageDataSet);
         } catch (PalTypeNatParm$ParseException var1) {
         }
      }

      public final int getFlags() {
         return this.flags;
      }

      public final int getMaximumCPUTime() {
         return this.maximumCPUTime;
      }

      public final int getPageDataSet() {
         return this.pageDataSet;
      }

      public final int getProcessingLoopLimit() {
         return this.processingLoopLimit;
      }

      public final void setProcessingLoopLimit(int var1) {
         try {
            this.processingLoopLimit = var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }
   }

   public class Regional implements IRegional, Serializable {
      private static final long serialVersionUID = -3078416794334092810L;
      private boolean isUtf8;
      private boolean isRetain;
      private boolean isConvErr;
      private String codePage = "";

      public Regional() {
      }

      public Regional(Regional var2) {
         if (var2 != null) {
            this.isUtf8 = var2.isUtf8;
            this.isRetain = var2.isRetain;
            this.isConvErr = var2.isConvErr;
            this.codePage = var2.codePage;
         }

      }

      private void restore() {
         try {
            this.isUtf8 = PalTypeNatParm.this.booleanFromBuffer();
            this.isRetain = PalTypeNatParm.this.booleanFromBuffer();
            this.isConvErr = PalTypeNatParm.this.booleanFromBuffer();
            this.codePage = PalTypeNatParm.this.stringFromBuffer();
         } catch (PalTypeNatParm$ParseException var1) {
         }
      }

      private void serialize() {
         try {
            PalTypeNatParm.this.booleanToBuffer(this.isUtf8);
            PalTypeNatParm.this.booleanToBuffer(this.isRetain);
            PalTypeNatParm.this.booleanToBuffer(this.isConvErr);
            PalTypeNatParm.this.stringToBuffer(this.codePage);
         } catch (PalTypeNatParm$ParseException var1) {
         }
      }

      public final String getCodePage() {
         return this.codePage;
      }

      public final boolean isConvErr() {
         return this.isConvErr;
      }

      public final boolean isRetain() {
         return this.isRetain;
      }

      public final boolean isUtf8() {
         return this.isUtf8;
      }

      public void setCodePage(String var1) {
         try {
            this.codePage = var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }

      public String toString() {
         try {
            return "Codepage=" + this.getCodePage();
         } catch (PalTypeNatParm$ParseException var1) {
            return null;
         }
      }
   }

   public class Report implements Serializable, IReport {
      private static final long serialVersionUID = 6016168292113553687L;
      private int flags;
      private int lineSize;
      private int pageSize;
      private int spacingFactor;
      private byte terminalMode;

      public String toString() {
         String var1 = "";
         if (this.lineSize != 0) {
            var1 = var1 + "LS=" + this.lineSize + " ";
         }

         if (this.pageSize != 0) {
            var1 = var1 + "PS=" + this.pageSize + " ";
         }

         if (this.spacingFactor != 0) {
            var1 = var1 + "SF=" + this.spacingFactor + " ";
         }

         if (this.terminalMode != 0) {
            var1 = var1 + "IM=" + (char)this.terminalMode + " ";
         }

         return var1;
      }

      public Report() {
      }

      public Report(Report var2) {
         if (var2 != null) {
            this.flags = var2.flags;
            this.lineSize = var2.lineSize;
            this.pageSize = var2.pageSize;
            this.spacingFactor = var2.spacingFactor;
            this.terminalMode = var2.terminalMode;
         }

      }

      private void restore() {
         try {
            this.flags = PalTypeNatParm.this.intFromBuffer();
            this.lineSize = PalTypeNatParm.this.intFromBuffer();
            this.pageSize = PalTypeNatParm.this.intFromBuffer();
            this.spacingFactor = PalTypeNatParm.this.intFromBuffer();
            this.terminalMode = PalTypeNatParm.this.byteFromBuffer();
         } catch (PalTypeNatParm$ParseException var1) {
         }
      }

      private void serialize() {
         try {
            PalTypeNatParm.this.intToBuffer(this.flags);
            PalTypeNatParm.this.intToBuffer(this.lineSize);
            PalTypeNatParm.this.intToBuffer(this.pageSize);
            PalTypeNatParm.this.intToBuffer(this.spacingFactor);
            PalTypeNatParm.this.byteToBuffer(this.terminalMode);
         } catch (PalTypeNatParm$ParseException var1) {
         }
      }

      public final int getFlags() {
         return this.flags;
      }

      public final int getLineSize() {
         return this.lineSize;
      }

      public final int getPageSize() {
         return this.pageSize;
      }

      public final int getSpacingFactor() {
         return this.spacingFactor;
      }

      public final byte getTerminalMode() {
         return this.terminalMode;
      }

      public final void setLineSize(int var1) {
         try {
            this.lineSize = var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }

      public final void setPageSize(int var1) {
         try {
            this.pageSize = var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }

      public final void setSpacingFactor(int var1) {
         try {
            this.spacingFactor = var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }

      public final void setTerminalMode(byte var1) {
         try {
            this.terminalMode = var1;
         } catch (PalTypeNatParm$ParseException var2) {
         }
      }
   }

   public class Rpc implements IRpc, Serializable {
      private static final long serialVersionUID = -7477079415142593540L;
      private int flags;
      private int compression;
      private int timeout;

      public Rpc() {
      }

      public Rpc(Rpc var2) {
         if (var2 != null) {
            this.flags = var2.flags;
            this.compression = var2.compression;
            this.timeout = var2.timeout;
         }

      }

      private void restore() {
         try {
            this.flags = PalTypeNatParm.this.intFromBuffer();
            this.compression = PalTypeNatParm.this.intFromBuffer();
            this.timeout = PalTypeNatParm.this.intFromBuffer();
         } catch (PalTypeNatParm$ParseException var1) {
         }
      }

      private void serialize() {
         try {
            PalTypeNatParm.this.intToBuffer(this.flags);
            PalTypeNatParm.this.intToBuffer(this.compression);
            PalTypeNatParm.this.intToBuffer(this.timeout);
         } catch (PalTypeNatParm$ParseException var1) {
         }
      }

      public final int getCompression() {
         return this.compression;
      }

      public final int getFlags() {
         return this.flags;
      }

      public final int getTimeout() {
         return this.timeout;
      }

      public String toString() {
         try {
            String var1 = "";
            return var1;
         } catch (PalTypeNatParm$ParseException var2) {
            return null;
         }
      }
   }
}
