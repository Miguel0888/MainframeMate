package de.bund.zrb.ndv.core.impl.type;

import de.bund.zrb.ndv.core.api.IBuffSize;
import de.bund.zrb.ndv.core.api.ICharAssign;
import de.bund.zrb.ndv.core.api.ICompOpt;
import de.bund.zrb.ndv.core.api.IErr;
import de.bund.zrb.ndv.core.api.IFldApp;
import de.bund.zrb.ndv.core.api.ILimit;
import de.bund.zrb.ndv.core.api.IPalTypeNatParm;
import de.bund.zrb.ndv.core.api.IRegional;
import de.bund.zrb.ndv.core.api.IReport;
import de.bund.zrb.ndv.core.api.IRpc;
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

    public PalTypeNatParm(IPalTypeNatParm source) {
        super.type = 25;
        this.setRecordIndex(source.getRecordIndex());
        switch (this.getRecordIndex()) {
            case 0: this.setReport(new Report(source.getReport())); break;
            case 1: this.setLimit(new Limit(source.getLimit())); break;
            case 2: this.setFldApp(new FldApp(source.getFldApp())); break;
            case 3: this.setCharAssign(new CharAssign(source.getCharAssign())); break;
            case 4: this.setErr(new Err(source.getErr())); break;
            case 5: this.setCompOpt(new CompOpt(source.getCompOpt())); break;
            case 6: this.setRpc(new Rpc(source.getRpc())); break;
            case 7: this.setBuffSize(new BuffSize(source.getBuffSize())); break;
            case 8: this.setRegional(new Regional(source.getRegional())); break;
        }
    }

    public void serialize() {
        if (this.recordIndex >= 0 && this.recordIndex <= 8) {
            this.intToBuffer(this.recordIndex);
            switch (this.recordIndex) {
                case 0: this.report.serialize(); break;
                case 1: this.limit.serialize(); break;
                case 2: this.fldApp.serialize(); break;
                case 3: this.charAssign.serialize(); break;
                case 4: this.err.serialize(); break;
                case 5: this.compOpt.serialize(); break;
                case 6: this.rpc.serialize(); break;
                case 7: this.buffSize.serialize(); break;
                case 8: this.regional.serialize(); break;
            }
        }
    }

    public void setLimit(Limit limit) { this.limit = limit; }
    public void setReport(Report report) { this.report = report; }
    public void setFldApp(FldApp fldApp) { this.fldApp = fldApp; }
    public void setCharAssign(CharAssign charAssign) { this.charAssign = charAssign; }
    public void setErr(Err err) { this.err = err; }
    public void setCompOpt(CompOpt compOpt) { this.compOpt = compOpt; }
    public void setRpc(Rpc rpc) { this.rpc = rpc; }
    public void setBuffSize(BuffSize buffSize) { this.buffSize = buffSize; }
    public void setRegional(Regional regional) { this.regional = regional; }

    public void restore() {
        this.recordIndex = this.intFromBuffer();
        if (this.recordIndex >= 0 && this.recordIndex <= 8) {
            switch (this.recordIndex) {
                case 0: this.report = new Report(); this.report.restore(); break;
                case 1: this.limit = new Limit(); this.limit.restore(); break;
                case 2: this.fldApp = new FldApp(); this.fldApp.restore(); break;
                case 3: this.charAssign = new CharAssign(); this.charAssign.restore(); break;
                case 4: this.err = new Err(); this.err.restore(); break;
                case 5: this.compOpt = new CompOpt(); this.compOpt.restore(); break;
                case 6: this.rpc = new Rpc(); this.rpc.restore(); break;
                case 7: this.buffSize = new BuffSize(); this.buffSize.restore(); break;
                case 8: this.regional = new Regional(); this.regional.restore(); break;
            }
        }
    }

    public final Limit getLimit() { return this.limit; }
    public final BuffSize getBuffSize() { return this.buffSize; }
    public final CharAssign getCharAssign() { return this.charAssign; }
    public final CompOpt getCompOpt() { return this.compOpt; }
    public final Err getErr() { return this.err; }
    public final FldApp getFldApp() { return this.fldApp; }

    public void setRecordIndex(int index) { this.recordIndex = index; }
    public final int getRecordIndex() { return this.recordIndex; }

    public final Regional getRegional() { return this.regional; }
    public final Report getReport() { return this.report; }
    public final Rpc getRpc() { return this.rpc; }

    public String toString() {
        if (this.limit != null) return this.limit.toString();
        else if (this.report != null) return this.report.toString();
        else if (this.fldApp != null) return this.fldApp.toString();
        else if (this.charAssign != null) return this.charAssign.toString();
        else if (this.err != null) return this.err.toString();
        else if (this.compOpt != null) return this.compOpt.toString();
        else if (this.rpc != null) return this.rpc.toString();
        else if (this.buffSize != null) return this.buffSize.toString();
        else if (this.regional != null) return this.regional.toString();
        else return "";
    }

    // =========================================================================
    //  Inner class: BuffSize
    // =========================================================================
    public class BuffSize implements IBuffSize, Serializable {
        private static final long serialVersionUID = -5106606349201671321L;
        private int edtSize;
        private int size2;
        private int size3;
        private int size4;
        private int size5;

        public BuffSize() {}

        public BuffSize(BuffSize other) {
            if (other != null) {
                this.edtSize = other.edtSize;
                this.size2 = other.size2;
                this.size3 = other.size3;
                this.size4 = other.size4;
                this.size5 = other.size5;
            }
        }

        private void restore() {
            this.edtSize = PalTypeNatParm.this.intFromBuffer();
            this.size2 = PalTypeNatParm.this.intFromBuffer();
            this.size3 = PalTypeNatParm.this.intFromBuffer();
            this.size4 = PalTypeNatParm.this.intFromBuffer();
            this.size5 = PalTypeNatParm.this.intFromBuffer();
        }

        private void serialize() {
            PalTypeNatParm.this.intToBuffer(this.edtSize);
            PalTypeNatParm.this.intToBuffer(this.size2);
            PalTypeNatParm.this.intToBuffer(this.size3);
            PalTypeNatParm.this.intToBuffer(this.size4);
            PalTypeNatParm.this.intToBuffer(this.size5);
        }

        public final int getEdtSize() { return this.edtSize; }
        public final int getSize2() { return this.size2; }
        public final int getSize3() { return this.size3; }
        public final int getSize4() { return this.size4; }
        public final int getSize5() { return this.size5; }

        public String toString() {
            String s = "";
            if (this.edtSize != 0) s += "EDTBPSIZE=" + this.edtSize + " ";
            return s;
        }
    }

    // =========================================================================
    //  Inner class: CharAssign
    // =========================================================================
    public class CharAssign implements Serializable, ICharAssign {
        private static final long serialVersionUID = 177909023595058115L;
        private byte termCommandChar;
        private byte decimalChar;
        private byte inputAssignment;
        private byte inputDelimiter;
        private byte thousandSeperator;

        public CharAssign() {}

        public CharAssign(CharAssign other) {
            if (other != null) {
                this.termCommandChar = other.termCommandChar;
                this.decimalChar = other.decimalChar;
                this.inputAssignment = other.inputAssignment;
                this.inputDelimiter = other.inputDelimiter;
                this.thousandSeperator = other.thousandSeperator;
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
            PalTypeNatParm.this.byteToBuffer(this.termCommandChar);
            PalTypeNatParm.this.byteToBuffer(this.decimalChar);
            PalTypeNatParm.this.byteToBuffer(this.inputAssignment);
            PalTypeNatParm.this.byteToBuffer(this.inputDelimiter);
            PalTypeNatParm.this.byteToBuffer(this.thousandSeperator);
        }

        public final byte getDecimalChar() { return this.decimalChar; }
        public final byte getInputAssignment() { return this.inputAssignment; }
        public final byte getInputDelimiter() { return this.inputDelimiter; }
        public final byte getTermCommandChar() { return this.termCommandChar; }
        public final byte getThousandSeperator() { return this.thousandSeperator; }

        public final void setDecimalChar(byte v) { this.decimalChar = v; }
        public final void setInputAssignment(byte v) { this.inputAssignment = v; }
        public final void setInputDelimiter(byte v) { this.inputDelimiter = v; }
        public final void setTermCommandChar(byte v) { this.termCommandChar = v; }
        public final void setThousandSeperator(byte v) { this.thousandSeperator = v; }

        public String toString() {
            String s = "";
            if (this.termCommandChar != 0) s += "CF=" + (char)this.termCommandChar + " ";
            if (this.decimalChar != 0) s += "DC=" + (char)this.decimalChar + " ";
            if (this.inputAssignment != 0) s += "IA=" + (char)this.inputAssignment + " ";
            if (this.inputDelimiter != 0) s += "ID=" + (char)this.inputDelimiter + " ";
            if (this.thousandSeperator != 0) s += "THSEPCH=" + (char)this.thousandSeperator + " ";
            return s;
        }
    }

    // =========================================================================
    //  Inner class: CompOpt
    // =========================================================================
    public class CompOpt implements Serializable, ICompOpt {
        private static final long serialVersionUID = 2321652677518594514L;
        private int flags;
        private int sourceLinelength;
        private int maxprec;

        public CompOpt() {}

        public CompOpt(CompOpt other) {
            if (other != null) {
                this.flags = other.flags;
                this.sourceLinelength = other.sourceLinelength;
                this.maxprec = other.maxprec;
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
            PalTypeNatParm.this.intToBuffer(this.flags);
            PalTypeNatParm.this.intToBuffer(this.sourceLinelength);
            PalTypeNatParm.this.intToBuffer(this.maxprec);
        }

        public final int getFlags() { return this.flags; }
        public final int getSourceLinelength() { return this.sourceLinelength; }
        public final int getMaxprec() { return this.maxprec; }
        public final void setMaxprec(int v) { this.maxprec = v; }
        public final void setFlags(int v) { this.flags |= v; }
        public final void resetFlags(int v) { this.flags &= ~v; }

        public String toString() {
            String s = "";
            if ((getFlags() & 8) == 8) s += "FS ";
            if ((getFlags() & 16) == 16) s += "SM=ON "; else s += "SM=OFF ";
            if ((getFlags() & 32) == 32) s += "SYMGEN=ON "; else s += "SYMGEN=OFF ";
            if ((getFlags() & 512) == 512) s += "GFID=ON ";
            if ((getFlags() & 2048) == 2048) s += "XREF=ON ";
            if ((getFlags() & 65536) == 65536) s += "XREF=FORCE ";
            if ((getFlags() & 131072) == 131072) s += "XREF=DOC ";
            if ((getFlags() & 1) == 1) s += "RPCRETRY ";
            if (this.maxprec >= 7 && this.maxprec <= 29) s += "MAXPREC=" + this.maxprec + " ";
            return s;
        }
    }

    // =========================================================================
    //  Inner class: Err
    // =========================================================================
    public class Err implements IErr, Serializable {
        private static final long serialVersionUID = -810690567149007343L;
        private int flags;

        public Err() {}

        public Err(Err other) {
            if (other != null) this.flags = other.flags;
        }

        private void restore() { this.flags = PalTypeNatParm.this.intFromBuffer(); }
        private void serialize() { PalTypeNatParm.this.intToBuffer(this.flags); }

        public final int getFlags() { return this.flags; }

        public String toString() {
            String s = "";
            if ((getFlags() & 1) == 1) s += "SA=ON ";
            if ((getFlags() & 2) == 2) s += "ZD=ON ";
            if ((getFlags() & 4) == 4) s += "REINP=ON ";
            if ((getFlags() & 8) == 8) s += "WH=ON ";
            return s;
        }
    }

    // =========================================================================
    //  Inner class: FldApp
    // =========================================================================
    public class FldApp implements Serializable, IFldApp {
        private static final long serialVersionUID = 5389288311795757678L;
        private int flags;
        private byte dateFormatOutput;
        private byte dateFormatStack;
        private byte dateFormatTitle;
        private byte printMode;
        private byte dateFormat;
        private int maxyear;

        public FldApp() {}

        public FldApp(FldApp other) {
            if (other != null) {
                this.flags = other.flags;
                this.dateFormatOutput = other.dateFormatOutput;
                this.dateFormatStack = other.dateFormatStack;
                this.dateFormatTitle = other.dateFormatTitle;
                this.printMode = other.printMode;
                this.dateFormat = other.dateFormat;
                this.maxyear = other.maxyear;
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
            PalTypeNatParm.this.intToBuffer(this.flags);
            PalTypeNatParm.this.byteToBuffer(this.dateFormatOutput);
            PalTypeNatParm.this.byteToBuffer(this.dateFormatStack);
            PalTypeNatParm.this.byteToBuffer(this.dateFormatTitle);
            PalTypeNatParm.this.byteToBuffer(this.printMode);
            PalTypeNatParm.this.byteToBuffer(this.dateFormat);
            PalTypeNatParm.this.intToBuffer(this.maxyear);
        }

        public final byte getDateFormat() { return this.dateFormat; }
        public final byte getDateFormatOutput() { return this.dateFormatOutput; }
        public final byte getDateFormatStack() { return this.dateFormatStack; }
        public final byte getDateFormatTitle() { return this.dateFormatTitle; }
        public final int getFlags() { return this.flags; }
        public final int getMaxyear() { return this.maxyear; }
        public final byte getPrintMode() { return this.printMode; }
        public void setDateFormat(byte v) { this.dateFormat = v; }
        public void setDateFormatOutput(byte v) { this.dateFormatOutput = v; }
        public void setDateFormatStack(byte v) { this.dateFormatStack = v; }
        public void setDateFormatTitle(byte v) { this.dateFormatTitle = v; }
        public void setFlags(int v) { this.flags |= v; }
        public void setMaxyear(int v) { this.maxyear = v; }
        public void setPrintMode(byte v) { this.printMode = v; }

        public String toString() {
            String s = "";
            if ((getFlags() & 1) == 1) s += "ZP=ON ";
            if ((getFlags() & 2) == 2) s += "FCDP=ON ";
            if ((getFlags() & 4) == 4) s += "TS=ON ";
            if ((getFlags() & 8) == 8) s += "Message Line Top ";
            if ((getFlags() & 16) == 16) s += "Message Line Bottom ";
            if (this.dateFormatOutput != 0) s += "DFOUT=" + (char)this.dateFormatOutput + " ";
            if (this.dateFormatStack != 0) s += "DFSTACK=" + (char)this.dateFormatStack + " ";
            if (this.dateFormatTitle != 0) s += "DFTITLE=" + (char)this.dateFormatTitle + " ";
            if (this.printMode != 0) s += "PM=" + (char)this.printMode + " ";
            if (this.dateFormat != 0) s += "DTFORM=" + (char)this.dateFormat + " ";
            if (this.maxyear != 0) s += "MAXYEAR=" + this.maxyear + " ";
            return s;
        }
    }

    // =========================================================================
    //  Inner class: Limit
    // =========================================================================
    public class Limit implements Serializable, ILimit {
        private static final long serialVersionUID = -8664599396389272547L;
        private int flags;
        private int processingLoopLimit;
        private int maximumCPUTime;
        private int pageDataSet;

        public Limit() {}

        public Limit(Limit other) {
            if (other != null) {
                this.flags = other.flags;
                this.processingLoopLimit = other.processingLoopLimit;
                this.maximumCPUTime = other.maximumCPUTime;
                this.pageDataSet = other.pageDataSet;
            }
        }

        private void restore() {
            this.flags = PalTypeNatParm.this.intFromBuffer();
            this.processingLoopLimit = PalTypeNatParm.this.intFromBuffer();
            this.maximumCPUTime = PalTypeNatParm.this.intFromBuffer();
            this.pageDataSet = PalTypeNatParm.this.intFromBuffer();
        }

        private void serialize() {
            PalTypeNatParm.this.intToBuffer(this.flags);
            PalTypeNatParm.this.intToBuffer(this.processingLoopLimit);
            PalTypeNatParm.this.intToBuffer(this.maximumCPUTime);
            PalTypeNatParm.this.intToBuffer(this.pageDataSet);
        }

        public final int getFlags() { return this.flags; }
        public final int getMaximumCPUTime() { return this.maximumCPUTime; }
        public final int getPageDataSet() { return this.pageDataSet; }
        public final int getProcessingLoopLimit() { return this.processingLoopLimit; }
        public final void setProcessingLoopLimit(int v) { this.processingLoopLimit = v; }

        public String toString() {
            String s = "";
            if (this.processingLoopLimit != 0) s += "LT=" + this.processingLoopLimit + " ";
            if (this.maximumCPUTime != 0) s += "MCPU=" + this.maximumCPUTime + " ";
            if (this.pageDataSet != 0) s += "PAGEDATASET=" + this.pageDataSet + " ";
            return s;
        }
    }

    // =========================================================================
    //  Inner class: Regional
    // =========================================================================
    public class Regional implements IRegional, Serializable {
        private static final long serialVersionUID = -3078416794334092810L;
        private boolean isUtf8;
        private boolean isRetain;
        private boolean isConvErr;
        private String codePage = "";

        public Regional() {}

        public Regional(Regional other) {
            if (other != null) {
                this.isUtf8 = other.isUtf8;
                this.isRetain = other.isRetain;
                this.isConvErr = other.isConvErr;
                this.codePage = other.codePage;
            }
        }

        private void restore() {
            this.isUtf8 = PalTypeNatParm.this.booleanFromBuffer();
            this.isRetain = PalTypeNatParm.this.booleanFromBuffer();
            this.isConvErr = PalTypeNatParm.this.booleanFromBuffer();
            this.codePage = PalTypeNatParm.this.stringFromBuffer();
        }

        private void serialize() {
            PalTypeNatParm.this.booleanToBuffer(this.isUtf8);
            PalTypeNatParm.this.booleanToBuffer(this.isRetain);
            PalTypeNatParm.this.booleanToBuffer(this.isConvErr);
            PalTypeNatParm.this.stringToBuffer(this.codePage);
        }

        public final String getCodePage() { return this.codePage; }
        public final boolean isConvErr() { return this.isConvErr; }
        public final boolean isRetain() { return this.isRetain; }
        public final boolean isUtf8() { return this.isUtf8; }
        public void setCodePage(String v) { this.codePage = v; }

        public String toString() {
            return "Codepage=" + this.getCodePage();
        }
    }

    // =========================================================================
    //  Inner class: Report
    // =========================================================================
    public class Report implements Serializable, IReport {
        private static final long serialVersionUID = 6016168292113553687L;
        private int flags;
        private int lineSize;
        private int pageSize;
        private int spacingFactor;
        private byte terminalMode;

        public Report() {}

        public Report(Report other) {
            if (other != null) {
                this.flags = other.flags;
                this.lineSize = other.lineSize;
                this.pageSize = other.pageSize;
                this.spacingFactor = other.spacingFactor;
                this.terminalMode = other.terminalMode;
            }
        }

        private void restore() {
            this.flags = PalTypeNatParm.this.intFromBuffer();
            this.lineSize = PalTypeNatParm.this.intFromBuffer();
            this.pageSize = PalTypeNatParm.this.intFromBuffer();
            this.spacingFactor = PalTypeNatParm.this.intFromBuffer();
            this.terminalMode = PalTypeNatParm.this.byteFromBuffer();
        }

        private void serialize() {
            PalTypeNatParm.this.intToBuffer(this.flags);
            PalTypeNatParm.this.intToBuffer(this.lineSize);
            PalTypeNatParm.this.intToBuffer(this.pageSize);
            PalTypeNatParm.this.intToBuffer(this.spacingFactor);
            PalTypeNatParm.this.byteToBuffer(this.terminalMode);
        }

        public final int getFlags() { return this.flags; }
        public final int getLineSize() { return this.lineSize; }
        public final int getPageSize() { return this.pageSize; }
        public final int getSpacingFactor() { return this.spacingFactor; }
        public final byte getTerminalMode() { return this.terminalMode; }
        public final void setLineSize(int v) { this.lineSize = v; }
        public final void setPageSize(int v) { this.pageSize = v; }
        public final void setSpacingFactor(int v) { this.spacingFactor = v; }
        public final void setTerminalMode(byte v) { this.terminalMode = v; }

        public String toString() {
            String s = "";
            if (this.lineSize != 0) s += "LS=" + this.lineSize + " ";
            if (this.pageSize != 0) s += "PS=" + this.pageSize + " ";
            if (this.spacingFactor != 0) s += "SF=" + this.spacingFactor + " ";
            if (this.terminalMode != 0) s += "IM=" + (char)this.terminalMode + " ";
            return s;
        }
    }

    // =========================================================================
    //  Inner class: Rpc
    // =========================================================================
    public class Rpc implements IRpc, Serializable {
        private static final long serialVersionUID = -7477079415142593540L;
        private int flags;
        private int compression;
        private int timeout;

        public Rpc() {}

        public Rpc(Rpc other) {
            if (other != null) {
                this.flags = other.flags;
                this.compression = other.compression;
                this.timeout = other.timeout;
            }
        }

        private void restore() {
            this.flags = PalTypeNatParm.this.intFromBuffer();
            this.compression = PalTypeNatParm.this.intFromBuffer();
            this.timeout = PalTypeNatParm.this.intFromBuffer();
        }

        private void serialize() {
            PalTypeNatParm.this.intToBuffer(this.flags);
            PalTypeNatParm.this.intToBuffer(this.compression);
            PalTypeNatParm.this.intToBuffer(this.timeout);
        }

        public final int getCompression() { return this.compression; }
        public final int getFlags() { return this.flags; }
        public final int getTimeout() { return this.timeout; }

        public String toString() {
            return "";
        }
    }
}

