package de.bund.zrb.dosbox.cpu;

import de.bund.zrb.dosbox.hardware.memory.Memory;

/**
 * x87 FPU (Floating Point Unit) emulation.
 * 8-register stack, control/status/tag words, and all common instructions.
 *
 * Ported from: src/fpu/fpu.cpp, src/fpu/fpu_instructions.h
 */
public class FPU {

    // ── Register stack ──────────────────────────────────────
    private final double[] st = new double[8];
    private int top;

    // ── Control, status, tag words ──────────────────────────
    private int cw;   // control word
    private int sw;   // status word
    private int tw;   // tag word

    // ── Status word bit positions ───────────────────────────
    private static final int SW_C0 = 0x0100;
    private static final int SW_C1 = 0x0200;
    private static final int SW_C2 = 0x0400;
    private static final int SW_C3 = 0x4000;

    public FPU() {
        init();
    }

    /** FINIT – reset FPU to default state. */
    public void init() {
        top = 0;
        cw = 0x037F;   // all exceptions masked, round nearest, 64-bit
        sw = 0;
        tw = 0xFFFF;   // all empty
        for (int i = 0; i < 8; i++) st[i] = 0.0;
    }

    // ── Stack operations ────────────────────────────────────

    private void updateTop() {
        sw = (sw & ~0x3800) | ((top & 7) << 11);
    }

    public void push(double val) {
        top = (top - 1) & 7;
        st[top] = val;
        setTag(top, classifyTag(val));
        updateTop();
    }

    public double pop() {
        double val = st[top];
        setTag(top, 3); // empty
        top = (top + 1) & 7;
        updateTop();
        return val;
    }

    public double getST(int i) {
        return st[(top + i) & 7];
    }

    public void setST(int i, double val) {
        int idx = (top + i) & 7;
        st[idx] = val;
        setTag(idx, classifyTag(val));
    }

    private int classifyTag(double val) {
        if (val == 0.0) return 1;  // zero
        if (Double.isNaN(val) || Double.isInfinite(val)) return 2; // special
        return 0; // valid
    }

    private void setTag(int physIdx, int tag) {
        int shift = (physIdx & 7) * 2;
        tw = (tw & ~(3 << shift)) | ((tag & 3) << shift);
    }

    // ── Word access ─────────────────────────────────────────

    public int getStatusWord()  { return sw; }
    public int getControlWord() { return cw; }
    public int getTagWord()     { return tw; }
    public void setControlWord(int v) { cw = v; }

    public void clearExceptions() {
        sw &= ~0x80FF;
    }

    // ── Condition codes ─────────────────────────────────────

    private void setCC(boolean c3, boolean c2, boolean c0) {
        sw &= ~(SW_C0 | SW_C1 | SW_C2 | SW_C3);
        if (c0) sw |= SW_C0;
        if (c2) sw |= SW_C2;
        if (c3) sw |= SW_C3;
    }

    // ── Main dispatcher — called by CPU ─────────────────────

    /**
     * Execute FPU instruction.
     * @param escape opcode D8-DF
     * @param modrm  ModR/M byte
     * @param ea     effective address (-1 if register mode)
     * @param mem    memory reference
     */
    public void execute(int escape, int modrm, int ea, Memory mem) {
        int mod = (modrm >> 6) & 3;
        int reg = (modrm >> 3) & 7;
        int rm  = modrm & 7;

        switch (escape & 7) {
            case 0: execD8(mod, reg, rm, ea, mem); break;
            case 1: execD9(mod, reg, rm, ea, mem); break;
            case 2: execDA(mod, reg, rm, ea, mem); break;
            case 3: execDB(mod, reg, rm, ea, mem); break;
            case 4: execDC(mod, reg, rm, ea, mem); break;
            case 5: execDD(mod, reg, rm, ea, mem); break;
            case 6: execDE(mod, reg, rm, ea, mem); break;
            case 7: execDF(mod, reg, rm, ea, mem); break;
        }
    }

    // ═══════════════════════════════════════════════════════
    // D8: FADD/FMUL/FCOM/FCOMP/FSUB/FSUBR/FDIV/FDIVR  (float32 mem / ST reg)
    // ═══════════════════════════════════════════════════════
    private void execD8(int mod, int reg, int rm, int ea, Memory mem) {
        double val = (mod != 3) ? readFloat32(ea, mem) : getST(rm);
        switch (reg) {
            case 0: setST(0, getST(0) + val); break;
            case 1: setST(0, getST(0) * val); break;
            case 2: fcom(val, false); break;
            case 3: fcom(val, true); break;
            case 4: setST(0, getST(0) - val); break;
            case 5: setST(0, val - getST(0)); break;
            case 6: fdiv(0, getST(0), val); break;
            case 7: fdiv(0, val, getST(0)); break;
        }
    }

    // ═══════════════════════════════════════════════════════
    // D9: FLD/FST/FSTP/FLDCW/FSTCW + register-form misc
    // ═══════════════════════════════════════════════════════
    private void execD9(int mod, int reg, int rm, int ea, Memory mem) {
        if (mod != 3) {
            switch (reg) {
                case 0: push(readFloat32(ea, mem)); break;        // FLD float32
                case 2: writeFloat32(ea, mem, getST(0)); break;   // FST float32
                case 3: writeFloat32(ea, mem, getST(0)); pop(); break; // FSTP
                case 4: break; // FLDENV (stub)
                case 5: cw = mem.readWord(ea); break;             // FLDCW
                case 6: break; // FNSTENV (stub)
                case 7: mem.writeWord(ea, cw); break;             // FNSTCW
            }
            return;
        }
        // ── register mode ──
        switch (reg) {
            case 0: { // FLD ST(i)
                double v = getST(rm);
                push(v);
                break;
            }
            case 1: { // FXCH ST(i)
                double tmp = getST(0); setST(0, getST(rm)); setST(rm, tmp);
                break;
            }
            case 2: break; // FNOP
            case 3: setST(rm, getST(0)); pop(); break; // FSTP ST(i)
            case 4: // D9 E0-E7
                switch (rm) {
                    case 0: setST(0, -getST(0)); break;            // FCHS
                    case 1: setST(0, Math.abs(getST(0))); break;   // FABS
                    case 4: ftst(); break;                          // FTST
                    case 5: fxam(); break;                          // FXAM
                }
                break;
            case 5: // D9 E8-EF  constants
                switch (rm) {
                    case 0: push(1.0); break;                               // FLD1
                    case 1: push(Math.log(10.0) / Math.log(2.0)); break;    // FLDL2T
                    case 2: push(1.0 / Math.log(2.0)); break;               // FLDL2E
                    case 3: push(Math.PI); break;                            // FLDPI
                    case 4: push(Math.log10(2.0)); break;                    // FLDLG2
                    case 5: push(Math.log(2.0)); break;                      // FLDLN2
                    case 6: push(0.0); break;                                // FLDZ
                }
                break;
            case 6: // D9 F0-F7
                switch (rm) {
                    case 0: setST(0, Math.pow(2, getST(0)) - 1); break;   // F2XM1
                    case 1: { // FYL2X
                        double x = getST(0), y = getST(1);
                        pop();
                        setST(0, y * (Math.log(x) / Math.log(2)));
                        break;
                    }
                    case 2: setST(0, Math.tan(getST(0))); push(1.0); break; // FPTAN
                    case 3: { // FPATAN
                        double x = getST(0), y = getST(1);
                        pop();
                        setST(0, Math.atan2(y, x));
                        break;
                    }
                    case 4: break; // FXTRACT (stub)
                    case 5: // FPREM1
                        if (getST(1) != 0) setST(0, Math.IEEEremainder(getST(0), getST(1)));
                        sw &= ~SW_C2; // complete
                        break;
                    case 6: top = (top - 1) & 7; updateTop(); break; // FDECSTP
                    case 7: top = (top + 1) & 7; updateTop(); break; // FINCSTP
                }
                break;
            case 7: // D9 F8-FF
                switch (rm) {
                    case 0: // FPREM
                        if (getST(1) != 0) setST(0, getST(0) % getST(1));
                        sw &= ~SW_C2;
                        break;
                    case 1: { // FYL2XP1
                        double x = getST(0), y = getST(1);
                        pop();
                        setST(0, y * (Math.log(x + 1) / Math.log(2)));
                        break;
                    }
                    case 2: setST(0, Math.sqrt(getST(0))); break; // FSQRT
                    case 3: { // FSINCOS
                        double v = getST(0);
                        setST(0, Math.sin(v));
                        push(Math.cos(v));
                        break;
                    }
                    case 4: setST(0, Math.rint(getST(0))); break; // FRNDINT
                    case 5: // FSCALE
                        setST(0, getST(0) * Math.pow(2, Math.floor(getST(1))));
                        break;
                    case 6: setST(0, Math.sin(getST(0))); break;  // FSIN
                    case 7: setST(0, Math.cos(getST(0))); break;  // FCOS
                }
                break;
        }
    }

    // ═══════════════════════════════════════════════════════
    // DA: FIADD/FIMUL/FICOM/FICOMP/FISUB/FISUBR/FIDIV/FIDIVR (int32)
    //     + FCMOV register / FUCOMPP
    // ═══════════════════════════════════════════════════════
    private void execDA(int mod, int reg, int rm, int ea, Memory mem) {
        if (mod != 3) {
            double val = readInt32(ea, mem);
            switch (reg) {
                case 0: setST(0, getST(0) + val); break;
                case 1: setST(0, getST(0) * val); break;
                case 2: fcom(val, false); break;
                case 3: fcom(val, true); break;
                case 4: setST(0, getST(0) - val); break;
                case 5: setST(0, val - getST(0)); break;
                case 6: fdiv(0, getST(0), val); break;
                case 7: fdiv(0, val, getST(0)); break;
            }
        } else {
            if (reg == 5 && rm == 1) { // DA E9 = FUCOMPP
                fcom(getST(1), false);
                pop(); pop();
            }
            // FCMOV (P6+) — ignored for DOS games
        }
    }

    // ═══════════════════════════════════════════════════════
    // DB: FILD/FIST/FISTP (int32) + FINIT/FCLEX + FLD/FSTP (80-bit)
    // ═══════════════════════════════════════════════════════
    private void execDB(int mod, int reg, int rm, int ea, Memory mem) {
        if (mod != 3) {
            switch (reg) {
                case 0: push(readInt32(ea, mem)); break;    // FILD dword
                case 1: { // FISTTP dword (SSE3)
                    writeInt32(ea, mem, (int) getST(0)); pop(); break;
                }
                case 2: writeInt32(ea, mem, (int) Math.rint(getST(0))); break; // FIST
                case 3: writeInt32(ea, mem, (int) Math.rint(getST(0))); pop(); break; // FISTP
                case 5: push(readFloat80(ea, mem)); break;  // FLD tbyte
                case 7: writeFloat80(ea, mem, getST(0)); pop(); break; // FSTP tbyte
            }
        } else {
            int op = (reg << 3) | rm;
            switch (op) {
                case 0x22: clearExceptions(); break;  // DB E2 = FNCLEX
                case 0x23: init(); break;              // DB E3 = FNINIT
                case 0x24: break;                      // DB E4 = FNSETPM (stub)
                // FCOMI etc (P6+) — ignored
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // DC: FADD/FMUL/FSUB/FSUBR/FDIV/FDIVR (float64 mem / ST(i) reg)
    // ═══════════════════════════════════════════════════════
    private void execDC(int mod, int reg, int rm, int ea, Memory mem) {
        if (mod != 3) {
            double val = readFloat64(ea, mem);
            switch (reg) {
                case 0: setST(0, getST(0) + val); break;
                case 1: setST(0, getST(0) * val); break;
                case 2: fcom(val, false); break;
                case 3: fcom(val, true); break;
                case 4: setST(0, getST(0) - val); break;
                case 5: setST(0, val - getST(0)); break;
                case 6: fdiv(0, getST(0), val); break;
                case 7: fdiv(0, val, getST(0)); break;
            }
        } else {
            // Register form: destination = ST(i)
            switch (reg) {
                case 0: setST(rm, getST(rm) + getST(0)); break;  // FADD ST(i),ST
                case 1: setST(rm, getST(rm) * getST(0)); break;  // FMUL ST(i),ST
                case 4: setST(rm, getST(0) - getST(rm)); break;  // FSUBR ST(i),ST
                case 5: setST(rm, getST(rm) - getST(0)); break;  // FSUB  ST(i),ST
                case 6: fdiv(rm, getST(0), getST(rm)); break;    // FDIVR ST(i),ST
                case 7: fdiv(rm, getST(rm), getST(0)); break;    // FDIV  ST(i),ST
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // DD: FLD/FST/FSTP (float64) + FFREE/FUCOM/FUCOMP + FRSTOR/FSAVE/FSTSW
    // ═══════════════════════════════════════════════════════
    private void execDD(int mod, int reg, int rm, int ea, Memory mem) {
        if (mod != 3) {
            switch (reg) {
                case 0: push(readFloat64(ea, mem)); break;        // FLD float64
                case 1: writeInt64(ea, mem, (long) getST(0)); pop(); break; // FISTTP (SSE3)
                case 2: writeFloat64(ea, mem, getST(0)); break;   // FST float64
                case 3: writeFloat64(ea, mem, getST(0)); pop(); break; // FSTP float64
                case 4: break; // FRSTOR (stub)
                case 6: break; // FNSAVE (stub)
                case 7: mem.writeWord(ea, sw); break;             // FNSTSW m16
            }
        } else {
            switch (reg) {
                case 0: setTag((top + rm) & 7, 3); break;         // FFREE ST(i)
                case 2: setST(rm, getST(0)); break;               // FST ST(i)
                case 3: setST(rm, getST(0)); pop(); break;        // FSTP ST(i)
                case 4: fcom(getST(rm), false); break;            // FUCOM ST(i)
                case 5: fcom(getST(rm), true); break;             // FUCOMP ST(i)
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // DE: FIADD/FIMUL/FICOM/FICOMP/FISUB/FISUBR/FIDIV/FIDIVR (int16)
    //     + register form with pop (FADDP etc.) + FCOMPP
    // ═══════════════════════════════════════════════════════
    private void execDE(int mod, int reg, int rm, int ea, Memory mem) {
        if (mod != 3) {
            double val = (short) mem.readWord(ea);
            switch (reg) {
                case 0: setST(0, getST(0) + val); break;
                case 1: setST(0, getST(0) * val); break;
                case 2: fcom(val, false); break;
                case 3: fcom(val, true); break;
                case 4: setST(0, getST(0) - val); break;
                case 5: setST(0, val - getST(0)); break;
                case 6: fdiv(0, getST(0), val); break;
                case 7: fdiv(0, val, getST(0)); break;
            }
        } else {
            if (reg == 3 && rm == 1) { // DE D9 = FCOMPP
                fcom(getST(1), false);
                pop(); pop();
                return;
            }
            // Register form with pop: op ST(i), ST; pop
            switch (reg) {
                case 0: setST(rm, getST(rm) + getST(0)); break;  // FADDP
                case 1: setST(rm, getST(rm) * getST(0)); break;  // FMULP
                case 4: setST(rm, getST(0) - getST(rm)); break;  // FSUBRP
                case 5: setST(rm, getST(rm) - getST(0)); break;  // FSUBP
                case 6: fdiv(rm, getST(0), getST(rm)); break;    // FDIVRP
                case 7: fdiv(rm, getST(rm), getST(0)); break;    // FDIVP
            }
            pop();
        }
    }

    // ═══════════════════════════════════════════════════════
    // DF: FILD/FIST/FISTP (int16, int64) + FBLD/FBSTP + FNSTSW AX
    // ═══════════════════════════════════════════════════════
    private void execDF(int mod, int reg, int rm, int ea, Memory mem) {
        if (mod != 3) {
            switch (reg) {
                case 0: push((short) mem.readWord(ea)); break;    // FILD int16
                case 1: { // FISTTP int16 (SSE3)
                    mem.writeWord(ea, ((int) getST(0)) & 0xFFFF); pop(); break;
                }
                case 2: mem.writeWord(ea, ((int) Math.rint(getST(0))) & 0xFFFF); break; // FIST
                case 3: mem.writeWord(ea, ((int) Math.rint(getST(0))) & 0xFFFF); pop(); break; // FISTP
                case 4: push(0); break; // FBLD (BCD stub)
                case 5: push((double) readInt64(ea, mem)); break; // FILD int64
                case 6: pop(); break; // FBSTP (BCD stub)
                case 7: writeInt64(ea, mem, (long) Math.rint(getST(0))); pop(); break; // FISTP int64
            }
        } else {
            // DF E0 = FNSTSW AX — handled by CPU (reads getStatusWord())
            // FUCOMI/FCOMIP (P6+) — ignored
        }
    }

    // ── Compare helpers ─────────────────────────────────────

    private void fcom(double val, boolean popAfter) {
        double st0 = getST(0);
        if (Double.isNaN(st0) || Double.isNaN(val)) {
            setCC(true, true, true); // unordered
        } else if (st0 > val) {
            setCC(false, false, false);
        } else if (st0 < val) {
            setCC(false, false, true);
        } else {
            setCC(true, false, false); // equal
        }
        if (popAfter) pop();
    }

    private void ftst() {
        double st0 = getST(0);
        if (Double.isNaN(st0)) {
            setCC(true, true, true);
        } else if (st0 > 0) {
            setCC(false, false, false);
        } else if (st0 < 0) {
            setCC(false, false, true);
        } else {
            setCC(true, false, false);
        }
    }

    private void fxam() {
        double st0 = getST(0);
        boolean sign = (Double.doubleToRawLongBits(st0) & 0x8000000000000000L) != 0;
        sw &= ~(SW_C0 | SW_C1 | SW_C2 | SW_C3);
        if (sign) sw |= SW_C1;
        if (Double.isNaN(st0))            sw |= SW_C0;
        else if (Double.isInfinite(st0))  sw |= SW_C0 | SW_C2;
        else if (st0 == 0.0)              sw |= SW_C3;
        else                              sw |= SW_C2; // normal
    }

    private void fdiv(int dstIdx, double a, double b) {
        setST(dstIdx, (b != 0) ? a / b : (a == 0 ? Double.NaN : (a > 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY)));
    }

    // ── Memory helpers ──────────────────────────────────────

    private double readFloat32(int addr, Memory mem) {
        int bits = mem.readByte(addr)
                 | (mem.readByte(addr + 1) << 8)
                 | (mem.readByte(addr + 2) << 16)
                 | (mem.readByte(addr + 3) << 24);
        return Float.intBitsToFloat(bits);
    }

    private void writeFloat32(int addr, Memory mem, double val) {
        int bits = Float.floatToRawIntBits((float) val);
        mem.writeByte(addr, bits & 0xFF);
        mem.writeByte(addr + 1, (bits >> 8) & 0xFF);
        mem.writeByte(addr + 2, (bits >> 16) & 0xFF);
        mem.writeByte(addr + 3, (bits >> 24) & 0xFF);
    }

    private double readFloat64(int addr, Memory mem) {
        long bits = 0;
        for (int i = 0; i < 8; i++)
            bits |= ((long) mem.readByte(addr + i)) << (i * 8);
        return Double.longBitsToDouble(bits);
    }

    private void writeFloat64(int addr, Memory mem, double val) {
        long bits = Double.doubleToRawLongBits(val);
        for (int i = 0; i < 8; i++)
            mem.writeByte(addr + i, (int) ((bits >> (i * 8)) & 0xFF));
    }

    private int readInt32(int addr, Memory mem) {
        return mem.readByte(addr)
             | (mem.readByte(addr + 1) << 8)
             | (mem.readByte(addr + 2) << 16)
             | (mem.readByte(addr + 3) << 24);
    }

    private void writeInt32(int addr, Memory mem, int val) {
        mem.writeByte(addr, val & 0xFF);
        mem.writeByte(addr + 1, (val >> 8) & 0xFF);
        mem.writeByte(addr + 2, (val >> 16) & 0xFF);
        mem.writeByte(addr + 3, (val >> 24) & 0xFF);
    }

    private long readInt64(int addr, Memory mem) {
        long val = 0;
        for (int i = 0; i < 8; i++)
            val |= ((long) mem.readByte(addr + i)) << (i * 8);
        return val;
    }

    private void writeInt64(int addr, Memory mem, long val) {
        for (int i = 0; i < 8; i++)
            mem.writeByte(addr + i, (int) ((val >> (i * 8)) & 0xFF));
    }

    private double readFloat80(int addr, Memory mem) {
        long mantissa = 0;
        for (int i = 0; i < 8; i++)
            mantissa |= ((long) mem.readByte(addr + i)) << (i * 8);
        int expSign = mem.readByte(addr + 8) | (mem.readByte(addr + 9) << 8);
        int exponent = expSign & 0x7FFF;
        boolean sign = (expSign & 0x8000) != 0;

        if (exponent == 0 && mantissa == 0) return sign ? -0.0 : 0.0;
        if (exponent == 0x7FFF) {
            if ((mantissa & 0x7FFFFFFFFFFFFFFFL) == 0)
                return sign ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            return Double.NaN;
        }
        double val = (double) mantissa / (1L << 63) * Math.pow(2, exponent - 16383);
        return sign ? -val : val;
    }

    private void writeFloat80(int addr, Memory mem, double val) {
        long bits = Double.doubleToRawLongBits(val);
        boolean sign = (bits & 0x8000000000000000L) != 0;
        int dblExp = (int) ((bits >> 52) & 0x7FF);
        long dblMant = bits & 0x000FFFFFFFFFFFFFL;

        if (dblExp == 0 && dblMant == 0) {
            for (int i = 0; i < 10; i++) mem.writeByte(addr + i, 0);
            if (sign) mem.writeByte(addr + 9, 0x80);
            return;
        }
        if (dblExp == 0x7FF) {
            for (int i = 0; i < 8; i++) mem.writeByte(addr + i, dblMant != 0 ? 0xFF : 0);
            mem.writeByte(addr + 7, 0x80 | (dblMant != 0 ? 0x40 : 0));
            mem.writeByte(addr + 8, 0xFF);
            mem.writeByte(addr + 9, sign ? 0xFF : 0x7F);
            return;
        }
        int ext80Exp = dblExp - 1023 + 16383;
        long ext80Mant = (1L << 63) | (dblMant << 11);
        for (int i = 0; i < 8; i++)
            mem.writeByte(addr + i, (int) ((ext80Mant >> (i * 8)) & 0xFF));
        mem.writeByte(addr + 8, ext80Exp & 0xFF);
        mem.writeByte(addr + 9, ((ext80Exp >> 8) & 0x7F) | (sign ? 0x80 : 0));
    }
}

