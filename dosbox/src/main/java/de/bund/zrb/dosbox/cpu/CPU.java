package de.bund.zrb.dosbox.cpu;

import de.bund.zrb.dosbox.core.Module;
import de.bund.zrb.dosbox.hardware.memory.IoPortHandler;
import de.bund.zrb.dosbox.hardware.memory.Memory;
import de.bund.zrb.dosbox.hardware.pic.PIC;

/**
 * x86 CPU emulation core (real mode, 16-bit).
 * Implements fetch-decode-execute cycle with interrupt dispatch.
 *
 * Ported from: src/cpu/cpu.cpp, src/cpu/core_normal.cpp
 */
public class CPU implements Module {

    public final Regs regs = new Regs();
    private final Memory memory;
    private final IoPortHandler io;
    private final PIC pic;

    /** Interrupt callback table (Java-side handlers for INT n). */
    @FunctionalInterface
    public interface IntHandler {
        void handle(CPU cpu);
    }

    private final IntHandler[] intHandlers = new IntHandler[256];

    private boolean halted;
    private boolean running;
    private int segOverride = -1; // -1 = none
    private boolean repPrefix;
    private boolean repNE;        // REPNE vs REPE

    // ── Cycle counting ──────────────────────────────────────
    private long totalCycles;
    private static final int CYCLES_PER_TICK = 1000; // simplified

    public CPU(Memory memory, IoPortHandler io, PIC pic) {
        this.memory = memory;
        this.io = io;
        this.pic = pic;
    }

    @Override
    public void reset() {
        regs.reset();
        halted = false;
        running = false;
        segOverride = -1;
        totalCycles = 0;
    }

    // ── Interrupt handler registration ──────────────────────

    public void setIntHandler(int vector, IntHandler handler) {
        intHandlers[vector & 0xFF] = handler;
    }

    // ── Memory access helpers using current segments ────────

    /** Get the effective data segment (or override). */
    private int getDS() {
        if (segOverride >= 0) return segOverride;
        return regs.ds;
    }

    private int getSS() {
        if (segOverride >= 0) return segOverride;
        return regs.ss;
    }

    /** Fetch a byte at CS:IP and advance IP. */
    private int fetchByte() {
        int addr = Memory.segOfs(regs.cs, regs.getIP());
        regs.setIP(regs.getIP() + 1);
        return memory.readByte(addr);
    }

    /** Fetch a word at CS:IP and advance IP. */
    private int fetchWord() {
        int lo = fetchByte();
        int hi = fetchByte();
        return lo | (hi << 8);
    }

    /** Fetch a signed byte at CS:IP. */
    private int fetchSignedByte() {
        int v = fetchByte();
        return (v < 128) ? v : v - 256;
    }

    /** Push a word onto the stack. */
    public void push(int value) {
        regs.setSP(regs.getSP() - 2);
        memory.writeWord(Memory.segOfs(regs.ss, regs.getSP()), value);
    }

    /** Pop a word from the stack. */
    public int pop() {
        int val = memory.readWord(Memory.segOfs(regs.ss, regs.getSP()));
        regs.setSP(regs.getSP() + 2);
        return val;
    }

    // ── INT instruction ─────────────────────────────────────

    /** Software interrupt. Tries Java handler first, then IVT. */
    public void softwareInt(int vector) {
        IntHandler handler = intHandlers[vector & 0xFF];
        if (handler != null) {
            handler.handle(this);
            return;
        }
        // Fall back to IVT (Interrupt Vector Table at 0000:0000)
        push(regs.flags.getWord());
        push(regs.cs);
        push(regs.getIP());
        regs.flags.setIF(false);
        regs.flags.setTF(false);
        int ivtAddr = vector * 4;
        regs.setIP(memory.readWord(ivtAddr));
        regs.cs = memory.readWord(ivtAddr + 2);
    }

    // ── ModR/M decoding ─────────────────────────────────────

    /** Decode ModR/M byte and return effective address. For reg, returns -1. */
    private int decodeModRM16(int modrm) {
        int mod = (modrm >> 6) & 3;
        int rm = modrm & 7;

        if (mod == 3) return -1; // register mode

        int addr;
        switch (rm) {
            case 0: addr = (regs.getBX() + regs.getSI()) & 0xFFFF; break;
            case 1: addr = (regs.getBX() + regs.getDI()) & 0xFFFF; break;
            case 2: addr = (regs.getBP() + regs.getSI()) & 0xFFFF; break;
            case 3: addr = (regs.getBP() + regs.getDI()) & 0xFFFF; break;
            case 4: addr = regs.getSI(); break;
            case 5: addr = regs.getDI(); break;
            case 6:
                if (mod == 0) {
                    addr = fetchWord();
                    return Memory.segOfs(getDS(), addr);
                }
                addr = regs.getBP();
                break;
            case 7: addr = regs.getBX(); break;
            default: addr = 0;
        }

        // Use SS for BP-based addressing
        int seg = (rm == 2 || rm == 3 || rm == 6) ? getSS() : getDS();

        if (mod == 1) {
            addr = (addr + fetchSignedByte()) & 0xFFFF;
        } else if (mod == 2) {
            addr = (addr + fetchWord()) & 0xFFFF;
        }

        return Memory.segOfs(seg, addr);
    }

    // ── ALU operations ──────────────────────────────────────

    private int alu8(int op, int a, int b) {
        int result;
        switch (op) {
            case 0: // ADD
                result = a + b;
                regs.flags.setCF((result & 0x100) != 0);
                regs.flags.setOF(((a ^ result) & (b ^ result) & 0x80) != 0);
                regs.flags.setAF(((a ^ b ^ result) & 0x10) != 0);
                result &= 0xFF;
                regs.flags.setSZP8(result);
                return result;
            case 1: // OR
                result = (a | b) & 0xFF;
                regs.flags.setCF(false);
                regs.flags.setOF(false);
                regs.flags.setSZP8(result);
                return result;
            case 2: // ADC
                int carry = regs.flags.getCF() ? 1 : 0;
                result = a + b + carry;
                regs.flags.setCF((result & 0x100) != 0);
                regs.flags.setOF(((a ^ result) & (b ^ result) & 0x80) != 0);
                regs.flags.setAF(((a ^ b ^ result) & 0x10) != 0);
                result &= 0xFF;
                regs.flags.setSZP8(result);
                return result;
            case 3: // SBB
                carry = regs.flags.getCF() ? 1 : 0;
                result = a - b - carry;
                regs.flags.setCF((result & 0x100) != 0);
                regs.flags.setOF(((a ^ b) & (a ^ result) & 0x80) != 0);
                regs.flags.setAF(((a ^ b ^ result) & 0x10) != 0);
                result &= 0xFF;
                regs.flags.setSZP8(result);
                return result;
            case 4: // AND
                result = (a & b) & 0xFF;
                regs.flags.setCF(false);
                regs.flags.setOF(false);
                regs.flags.setSZP8(result);
                return result;
            case 5: // SUB
                result = a - b;
                regs.flags.setCF((result & 0x100) != 0);
                regs.flags.setOF(((a ^ b) & (a ^ result) & 0x80) != 0);
                regs.flags.setAF(((a ^ b ^ result) & 0x10) != 0);
                result &= 0xFF;
                regs.flags.setSZP8(result);
                return result;
            case 6: // XOR
                result = (a ^ b) & 0xFF;
                regs.flags.setCF(false);
                regs.flags.setOF(false);
                regs.flags.setSZP8(result);
                return result;
            case 7: // CMP (same as SUB but don't store)
                result = a - b;
                regs.flags.setCF((result & 0x100) != 0);
                regs.flags.setOF(((a ^ b) & (a ^ result) & 0x80) != 0);
                regs.flags.setAF(((a ^ b ^ result) & 0x10) != 0);
                regs.flags.setSZP8(result & 0xFF);
                return a; // CMP doesn't modify dst
            default: return a;
        }
    }

    private int alu16(int op, int a, int b) {
        int result;
        switch (op) {
            case 0: // ADD
                result = a + b;
                regs.flags.setCF((result & 0x10000) != 0);
                regs.flags.setOF(((a ^ result) & (b ^ result) & 0x8000) != 0);
                regs.flags.setAF(((a ^ b ^ result) & 0x10) != 0);
                result &= 0xFFFF;
                regs.flags.setSZP16(result);
                return result;
            case 1: // OR
                result = (a | b) & 0xFFFF;
                regs.flags.setCF(false); regs.flags.setOF(false);
                regs.flags.setSZP16(result);
                return result;
            case 2: // ADC
                int carry = regs.flags.getCF() ? 1 : 0;
                result = a + b + carry;
                regs.flags.setCF((result & 0x10000) != 0);
                regs.flags.setOF(((a ^ result) & (b ^ result) & 0x8000) != 0);
                regs.flags.setAF(((a ^ b ^ result) & 0x10) != 0);
                result &= 0xFFFF;
                regs.flags.setSZP16(result);
                return result;
            case 3: // SBB
                carry = regs.flags.getCF() ? 1 : 0;
                result = a - b - carry;
                regs.flags.setCF((result & 0x10000) != 0);
                regs.flags.setOF(((a ^ b) & (a ^ result) & 0x8000) != 0);
                regs.flags.setAF(((a ^ b ^ result) & 0x10) != 0);
                result &= 0xFFFF;
                regs.flags.setSZP16(result);
                return result;
            case 4: // AND
                result = (a & b) & 0xFFFF;
                regs.flags.setCF(false); regs.flags.setOF(false);
                regs.flags.setSZP16(result);
                return result;
            case 5: // SUB
                result = a - b;
                regs.flags.setCF((result & 0x10000) != 0);
                regs.flags.setOF(((a ^ b) & (a ^ result) & 0x8000) != 0);
                regs.flags.setAF(((a ^ b ^ result) & 0x10) != 0);
                result &= 0xFFFF;
                regs.flags.setSZP16(result);
                return result;
            case 6: // XOR
                result = (a ^ b) & 0xFFFF;
                regs.flags.setCF(false); regs.flags.setOF(false);
                regs.flags.setSZP16(result);
                return result;
            case 7: // CMP
                result = a - b;
                regs.flags.setCF((result & 0x10000) != 0);
                regs.flags.setOF(((a ^ b) & (a ^ result) & 0x8000) != 0);
                regs.flags.setAF(((a ^ b ^ result) & 0x10) != 0);
                regs.flags.setSZP16(result & 0xFFFF);
                return a;
            default: return a;
        }
    }

    // ── Condition code evaluation ───────────────────────────

    private boolean evalCondition(int cc) {
        switch (cc & 0xF) {
            case 0x0: return regs.flags.getOF();                    // O
            case 0x1: return !regs.flags.getOF();                   // NO
            case 0x2: return regs.flags.getCF();                    // B/C
            case 0x3: return !regs.flags.getCF();                   // NB/NC
            case 0x4: return regs.flags.getZF();                    // Z/E
            case 0x5: return !regs.flags.getZF();                   // NZ/NE
            case 0x6: return regs.flags.getCF() || regs.flags.getZF(); // BE
            case 0x7: return !regs.flags.getCF() && !regs.flags.getZF(); // A
            case 0x8: return regs.flags.getSF();                    // S
            case 0x9: return !regs.flags.getSF();                   // NS
            case 0xA: return regs.flags.getPF();                    // P
            case 0xB: return !regs.flags.getPF();                   // NP
            case 0xC: return regs.flags.getSF() != regs.flags.getOF(); // L
            case 0xD: return regs.flags.getSF() == regs.flags.getOF(); // GE
            case 0xE: return regs.flags.getZF() || (regs.flags.getSF() != regs.flags.getOF()); // LE
            case 0xF: return !regs.flags.getZF() && (regs.flags.getSF() == regs.flags.getOF()); // G
            default: return false;
        }
    }

    // ── Main execution loop ─────────────────────────────────

    public void setRunning(boolean running) { this.running = running; }
    public boolean isRunning() { return running; }
    public long getTotalCycles() { return totalCycles; }

    /**
     * Execute a block of instructions.
     * @param maxCycles maximum cycles to execute (approximate)
     */
    public void executeBlock(int maxCycles) {
        int cycles = 0;
        while (running && cycles < maxCycles) {
            // Check for hardware interrupts
            if (regs.flags.getIF() && pic.hasInterrupt()) {
                int vector = pic.checkInterrupt();
                if (vector >= 0) {
                    halted = false;
                    softwareInt(vector);
                }
            }

            if (halted) {
                cycles += maxCycles; // sleep until interrupt
                break;
            }

            segOverride = -1;
            repPrefix = false;
            executeOne();
            cycles++;
            totalCycles++;
        }
    }

    /** Execute a single instruction. */
    private void executeOne() {
        int opcode = fetchByte();

        // Handle prefixes
        while (true) {
            switch (opcode) {
                case 0x26: segOverride = regs.es; opcode = fetchByte(); continue;
                case 0x2E: segOverride = regs.cs; opcode = fetchByte(); continue;
                case 0x36: segOverride = regs.ss; opcode = fetchByte(); continue;
                case 0x3E: segOverride = regs.ds; opcode = fetchByte(); continue;
                case 0x64: segOverride = regs.fs; opcode = fetchByte(); continue; // FS override
                case 0x65: segOverride = regs.gs; opcode = fetchByte(); continue; // GS override
                case 0xF2: repPrefix = true; repNE = true; opcode = fetchByte(); continue;
                case 0xF3: repPrefix = true; repNE = false; opcode = fetchByte(); continue;
            }
            break;
        }

        switch (opcode) {
            // ── ALU r/m8, reg8 (00-05, 08-0D, ... 38-3D) ───
            case 0x00: case 0x08: case 0x10: case 0x18:
            case 0x20: case 0x28: case 0x30: case 0x38: {
                int aluOp = (opcode >> 3) & 7;
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int a, b = regs.getReg8(reg);
                if (ea == -1) { a = regs.getReg8(rm); } else { a = memory.readByte(ea); }
                int result = alu8(aluOp, a, b);
                if (aluOp != 7) { // not CMP
                    if (ea == -1) { regs.setReg8(rm, result); } else { memory.writeByte(ea, result); }
                }
                break;
            }

            // ── ALU reg8, r/m8 (02, 0A, 12, 1A, 22, 2A, 32, 3A) ──
            case 0x02: case 0x0A: case 0x12: case 0x1A:
            case 0x22: case 0x2A: case 0x32: case 0x3A: {
                int aluOp = (opcode >> 3) & 7;
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int a = regs.getReg8(reg);
                int b;
                if (ea == -1) { b = regs.getReg8(rm); } else { b = memory.readByte(ea); }
                int result = alu8(aluOp, a, b);
                if (aluOp != 7) regs.setReg8(reg, result);
                break;
            }

            // ── ALU r/m16, reg16 (01,09,11,19,21,29,31,39) ──
            case 0x01: case 0x09: case 0x11: case 0x19:
            case 0x21: case 0x29: case 0x31: case 0x39: {
                int aluOp = (opcode >> 3) & 7;
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int a, b = regs.getReg16(reg);
                if (ea == -1) { a = regs.getReg16(rm); } else { a = memory.readWord(ea); }
                int result = alu16(aluOp, a, b);
                if (aluOp != 7) {
                    if (ea == -1) { regs.setReg16(rm, result); } else { memory.writeWord(ea, result); }
                }
                break;
            }

            // ── ALU reg16, r/m16 (03,0B,13,1B,23,2B,33,3B) ──
            case 0x03: case 0x0B: case 0x13: case 0x1B:
            case 0x23: case 0x2B: case 0x33: case 0x3B: {
                int aluOp = (opcode >> 3) & 7;
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int a = regs.getReg16(reg);
                int b;
                if (ea == -1) { b = regs.getReg16(rm); } else { b = memory.readWord(ea); }
                int result = alu16(aluOp, a, b);
                if (aluOp != 7) regs.setReg16(reg, result);
                break;
            }

            // ── ALU AL, imm8 (04,0C,14,1C,24,2C,34,3C) ─────
            case 0x04: case 0x0C: case 0x14: case 0x1C:
            case 0x24: case 0x2C: case 0x34: case 0x3C: {
                int aluOp = (opcode >> 3) & 7;
                int imm = fetchByte();
                int result = alu8(aluOp, regs.getAL(), imm);
                if (aluOp != 7) regs.setAL(result);
                break;
            }

            // ── ALU AX, imm16 (05,0D,15,1D,25,2D,35,3D) ────
            case 0x05: case 0x0D: case 0x15: case 0x1D:
            case 0x25: case 0x2D: case 0x35: case 0x3D: {
                int aluOp = (opcode >> 3) & 7;
                int imm = fetchWord();
                int result = alu16(aluOp, regs.getAX(), imm);
                if (aluOp != 7) regs.setAX(result);
                break;
            }

            // ── PUSH segment ────────────────────────────────
            case 0x06: push(regs.es); break;
            case 0x0E: push(regs.cs); break;
            case 0x16: push(regs.ss); break;
            case 0x1E: push(regs.ds); break;

            // ── POP segment ─────────────────────────────────
            case 0x07: regs.es = pop() & 0xFFFF; break;
            case 0x17: regs.ss = pop() & 0xFFFF; break;
            case 0x1F: regs.ds = pop() & 0xFFFF; break;

            // ── Two-byte opcode escape (0F) ─────────────────
            case 0x0F: executeTwoByteOpcode(); break;

            // ── INC reg16 (40-47) ───────────────────────────
            case 0x40: case 0x41: case 0x42: case 0x43:
            case 0x44: case 0x45: case 0x46: case 0x47: {
                int idx = opcode - 0x40;
                int v = regs.getReg16(idx);
                boolean cf = regs.flags.getCF();
                int result = alu16(0, v, 1);
                regs.flags.setCF(cf);
                regs.setReg16(idx, result);
                break;
            }

            // ── DEC reg16 (48-4F) ───────────────────────────
            case 0x48: case 0x49: case 0x4A: case 0x4B:
            case 0x4C: case 0x4D: case 0x4E: case 0x4F: {
                int idx = opcode - 0x48;
                int v = regs.getReg16(idx);
                boolean cf = regs.flags.getCF();
                int result = alu16(5, v, 1);
                regs.flags.setCF(cf);
                regs.setReg16(idx, result);
                break;
            }

            // ── PUSH reg16 (50-57) ──────────────────────────
            case 0x50: case 0x51: case 0x52: case 0x53:
            case 0x54: case 0x55: case 0x56: case 0x57:
                push(regs.getReg16(opcode - 0x50));
                break;

            // ── POP reg16 (58-5F) ───────────────────────────
            case 0x58: case 0x59: case 0x5A: case 0x5B:
            case 0x5C: case 0x5D: case 0x5E: case 0x5F:
                regs.setReg16(opcode - 0x58, pop());
                break;

            // ── PUSHA (60) ──────────────────────────────────
            case 0x60: {
                int tmp = regs.getSP();
                push(regs.getAX());
                push(regs.getCX());
                push(regs.getDX());
                push(regs.getBX());
                push(tmp);
                push(regs.getBP());
                push(regs.getSI());
                push(regs.getDI());
                break;
            }

            // ── POPA (61) ───────────────────────────────────
            case 0x61: {
                regs.setDI(pop());
                regs.setSI(pop());
                regs.setBP(pop());
                pop(); // skip SP
                regs.setBX(pop());
                regs.setDX(pop());
                regs.setCX(pop());
                regs.setAX(pop());
                break;
            }

            // ── BOUND (62) — stub ───────────────────────────
            case 0x62: {
                int modrm = fetchByte();
                decodeModRM16(modrm); // consume but ignore
                break;
            }

            // ── PUSH imm16 (68) ─────────────────────────────
            case 0x68:
                push(fetchWord());
                break;

            // ── IMUL reg16, r/m16, imm16 (69) ──────────────
            case 0x69: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg16(rm); } else { val = memory.readWord(ea); }
                int imm = fetchWord();
                if (imm >= 0x8000) imm -= 0x10000;
                if (val >= 0x8000) val -= 0x10000;
                int result = val * imm;
                regs.setReg16(reg, result & 0xFFFF);
                boolean overflow = (result < -32768 || result > 32767);
                regs.flags.setCF(overflow);
                regs.flags.setOF(overflow);
                break;
            }

            // ── PUSH sign-ext imm8 (6A) ─────────────────────
            case 0x6A:
                push(fetchSignedByte() & 0xFFFF);
                break;

            // ── IMUL reg16, r/m16, sign-ext imm8 (6B) ──────
            case 0x6B: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg16(rm); } else { val = memory.readWord(ea); }
                int imm = fetchSignedByte();
                if (val >= 0x8000) val -= 0x10000;
                int result = val * imm;
                regs.setReg16(reg, result & 0xFFFF);
                boolean overflow = (result < -32768 || result > 32767);
                regs.flags.setCF(overflow);
                regs.flags.setOF(overflow);
                break;
            }

            // ── Jcc short (70-7F) ───────────────────────────
            case 0x70: case 0x71: case 0x72: case 0x73:
            case 0x74: case 0x75: case 0x76: case 0x77:
            case 0x78: case 0x79: case 0x7A: case 0x7B:
            case 0x7C: case 0x7D: case 0x7E: case 0x7F: {
                int disp = fetchSignedByte();
                if (evalCondition(opcode - 0x70)) {
                    regs.setIP(regs.getIP() + disp);
                }
                break;
            }

            // ── ALU r/m8, imm8 (80) ────────────────────────
            case 0x80: case 0x82: {
                int modrm = fetchByte();
                int aluOp = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int a;
                if (ea == -1) { a = regs.getReg8(rm); } else { a = memory.readByte(ea); }
                int imm = fetchByte();
                int result = alu8(aluOp, a, imm);
                if (aluOp != 7) {
                    if (ea == -1) { regs.setReg8(rm, result); } else { memory.writeByte(ea, result); }
                }
                break;
            }

            // ── ALU r/m16, imm16 (81) ──────────────────────
            case 0x81: {
                int modrm = fetchByte();
                int aluOp = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int a;
                if (ea == -1) { a = regs.getReg16(rm); } else { a = memory.readWord(ea); }
                int imm = fetchWord();
                int result = alu16(aluOp, a, imm);
                if (aluOp != 7) {
                    if (ea == -1) { regs.setReg16(rm, result); } else { memory.writeWord(ea, result); }
                }
                break;
            }

            // ── ALU r/m16, sign-ext imm8 (83) ──────────────
            case 0x83: {
                int modrm = fetchByte();
                int aluOp = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int a;
                if (ea == -1) { a = regs.getReg16(rm); } else { a = memory.readWord(ea); }
                int imm = fetchSignedByte() & 0xFFFF;
                int result = alu16(aluOp, a, imm);
                if (aluOp != 7) {
                    if (ea == -1) { regs.setReg16(rm, result); } else { memory.writeWord(ea, result); }
                }
                break;
            }

            // ── TEST r/m8, reg8 (84) ────────────────────────
            case 0x84: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int a;
                if (ea == -1) { a = regs.getReg8(rm); } else { a = memory.readByte(ea); }
                alu8(4, a, regs.getReg8(reg));
                break;
            }

            // ── TEST r/m16, reg16 (85) ──────────────────────
            case 0x85: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int a;
                if (ea == -1) { a = regs.getReg16(rm); } else { a = memory.readWord(ea); }
                alu16(4, a, regs.getReg16(reg));
                break;
            }

            // ── XCHG r/m8, reg8 (86) ───────────────────────
            case 0x86: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int a, b = regs.getReg8(reg);
                if (ea == -1) { a = regs.getReg8(rm); regs.setReg8(rm, b); }
                else { a = memory.readByte(ea); memory.writeByte(ea, b); }
                regs.setReg8(reg, a);
                break;
            }

            // ── XCHG r/m16, reg16 (87) ─────────────────────
            case 0x87: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int a, b = regs.getReg16(reg);
                if (ea == -1) { a = regs.getReg16(rm); regs.setReg16(rm, b); }
                else { a = memory.readWord(ea); memory.writeWord(ea, b); }
                regs.setReg16(reg, a);
                break;
            }

            // ── MOV r/m8, reg8 (88) ────────────────────────
            case 0x88: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val = regs.getReg8(reg);
                if (ea == -1) { regs.setReg8(rm, val); } else { memory.writeByte(ea, val); }
                break;
            }

            // ── MOV r/m16, reg16 (89) ──────────────────────
            case 0x89: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val = regs.getReg16(reg);
                if (ea == -1) { regs.setReg16(rm, val); } else { memory.writeWord(ea, val); }
                break;
            }

            // ── MOV reg8, r/m8 (8A) ────────────────────────
            case 0x8A: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg8(rm); } else { val = memory.readByte(ea); }
                regs.setReg8(reg, val);
                break;
            }

            // ── MOV reg16, r/m16 (8B) ──────────────────────
            case 0x8B: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg16(rm); } else { val = memory.readWord(ea); }
                regs.setReg16(reg, val);
                break;
            }

            // ── MOV r/m16, Sreg (8C) ───────────────────────
            case 0x8C: {
                int modrm = fetchByte();
                int seg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val = regs.getSeg(seg);
                if (ea == -1) { regs.setReg16(rm, val); } else { memory.writeWord(ea, val); }
                break;
            }

            // ── LEA reg16, mem (8D) ─────────────────────────
            case 0x8D: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                regs.setReg16(reg, ea & 0xFFFF);
                break;
            }

            // ── MOV Sreg, r/m16 (8E) ───────────────────────
            case 0x8E: {
                int modrm = fetchByte();
                int seg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg16(rm); } else { val = memory.readWord(ea); }
                regs.setSeg(seg, val);
                break;
            }

            // ── POP r/m16 (8F) ──────────────────────────────
            case 0x8F: {
                int modrm = fetchByte();
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val = pop();
                if (ea == -1) { regs.setReg16(rm, val); } else { memory.writeWord(ea, val); }
                break;
            }

            // ── NOP / XCHG AX,reg (90-97) ──────────────────
            case 0x90: break; // NOP
            case 0x91: case 0x92: case 0x93:
            case 0x94: case 0x95: case 0x96: case 0x97: {
                int idx = opcode - 0x90;
                int tmp = regs.getAX();
                regs.setAX(regs.getReg16(idx));
                regs.setReg16(idx, tmp);
                break;
            }

            // ── CBW (98) ───────────────────────────────────
            case 0x98:
                regs.setAX((regs.getAL() & 0x80) != 0 ? regs.getAL() | 0xFF00 : regs.getAL());
                break;

            // ── CWD (99) ───────────────────────────────────
            case 0x99:
                regs.setDX((regs.getAX() & 0x8000) != 0 ? 0xFFFF : 0);
                break;

            // ── CALL far (9A) ──────────────────────────────
            case 0x9A: {
                int newIP = fetchWord();
                int newCS = fetchWord();
                push(regs.cs);
                push(regs.getIP());
                regs.cs = newCS;
                regs.setIP(newIP);
                break;
            }

            // ── PUSHF (9C) ─────────────────────────────────
            case 0x9C: push(regs.flags.getWord()); break;

            // ── POPF (9D) ──────────────────────────────────
            case 0x9D: regs.flags.setWord(pop()); break;

            // ── SAHF (9E) / LAHF (9F) ──────────────────────
            case 0x9E: regs.flags.setWord((regs.flags.getWord() & 0xFF00) | regs.getAH()); break;
            case 0x9F: regs.setAH(regs.flags.getWord() & 0xFF); break;

            // ── MOV AL/AX, moffs (A0-A1) ───────────────────
            case 0xA0: regs.setAL(memory.readByte(Memory.segOfs(getDS(), fetchWord()))); break;
            case 0xA1: regs.setAX(memory.readWord(Memory.segOfs(getDS(), fetchWord()))); break;

            // ── MOV moffs, AL/AX (A2-A3) ───────────────────
            case 0xA2: memory.writeByte(Memory.segOfs(getDS(), fetchWord()), regs.getAL()); break;
            case 0xA3: memory.writeWord(Memory.segOfs(getDS(), fetchWord()), regs.getAX()); break;

            // ── MOVSB (A4) ─────────────────────────────────
            case 0xA4: execMovsb(); break;

            // ── MOVSW (A5) ─────────────────────────────────
            case 0xA5: execMovsw(); break;

            // ── CMPSB (A6) ─────────────────────────────────
            case 0xA6: execCmpsb(); break;

            // ── CMPSW (A7) ─────────────────────────────────
            case 0xA7: execCmpsw(); break;

            // ── TEST AL, imm8 (A8) ─────────────────────────
            case 0xA8: alu8(4, regs.getAL(), fetchByte()); break;

            // ── TEST AX, imm16 (A9) ────────────────────────
            case 0xA9: alu16(4, regs.getAX(), fetchWord()); break;

            // ── STOSB (AA) ─────────────────────────────────
            case 0xAA: execStosb(); break;

            // ── STOSW (AB) ─────────────────────────────────
            case 0xAB: execStosw(); break;

            // ── LODSB (AC) ─────────────────────────────────
            case 0xAC: execLodsb(); break;

            // ── LODSW (AD) ─────────────────────────────────
            case 0xAD: execLodsw(); break;

            // ── SCASB (AE) ─────────────────────────────────
            case 0xAE: execScasb(); break;

            // ── SCASW (AF) ─────────────────────────────────
            case 0xAF: execScasw(); break;

            // ── MOV reg8, imm8 (B0-B7) ─────────────────────
            case 0xB0: case 0xB1: case 0xB2: case 0xB3:
            case 0xB4: case 0xB5: case 0xB6: case 0xB7:
                regs.setReg8(opcode - 0xB0, fetchByte());
                break;

            // ── MOV reg16, imm16 (B8-BF) ───────────────────
            case 0xB8: case 0xB9: case 0xBA: case 0xBB:
            case 0xBC: case 0xBD: case 0xBE: case 0xBF:
                regs.setReg16(opcode - 0xB8, fetchWord());
                break;

            // ── Shift/Rotate r/m8, imm8 (C0) ───────────────
            case 0xC0: {
                int modrm = fetchByte();
                int op = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg8(rm); } else { val = memory.readByte(ea); }
                int cnt = fetchByte() & 0x1F;
                if (cnt != 0) {
                    val = doShift8(op, val, cnt);
                    if (ea == -1) { regs.setReg8(rm, val); } else { memory.writeByte(ea, val); }
                }
                break;
            }

            // ── Shift/Rotate r/m16, imm8 (C1) ──────────────
            case 0xC1: {
                int modrm = fetchByte();
                int op = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg16(rm); } else { val = memory.readWord(ea); }
                int cnt = fetchByte() & 0x1F;
                if (cnt != 0) {
                    val = doShift16(op, val, cnt);
                    if (ea == -1) { regs.setReg16(rm, val); } else { memory.writeWord(ea, val); }
                }
                break;
            }

            // ── RET near imm16 (C2) ────────────────────────
            case 0xC2: {
                int n = fetchWord();
                regs.setIP(pop());
                regs.setSP(regs.getSP() + n);
                break;
            }

            // ── RET near (C3) ──────────────────────────────
            case 0xC3: regs.setIP(pop()); break;

            // ── LES (C4) / LDS (C5) ────────────────────────
            case 0xC4: case 0xC5: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                regs.setReg16(reg, memory.readWord(ea));
                if (opcode == 0xC4) regs.es = memory.readWord(ea + 2);
                else regs.ds = memory.readWord(ea + 2);
                break;
            }

            // ── MOV r/m8, imm8 (C6) ────────────────────────
            case 0xC6: {
                int modrm = fetchByte();
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int imm = fetchByte();
                if (ea == -1) { regs.setReg8(rm, imm); } else { memory.writeByte(ea, imm); }
                break;
            }

            // ── MOV r/m16, imm16 (C7) ──────────────────────
            case 0xC7: {
                int modrm = fetchByte();
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int imm = fetchWord();
                if (ea == -1) { regs.setReg16(rm, imm); } else { memory.writeWord(ea, imm); }
                break;
            }

            // ── ENTER (C8) ─────────────────────────────────
            case 0xC8: {
                int allocSize = fetchWord();
                int nestLevel = fetchByte() & 0x1F;
                push(regs.getBP());
                int framePtr = regs.getSP();
                if (nestLevel > 0) {
                    for (int i = 1; i < nestLevel; i++) {
                        regs.setBP(regs.getBP() - 2);
                        push(memory.readWord(Memory.segOfs(regs.ss, regs.getBP())));
                    }
                    push(framePtr);
                }
                regs.setBP(framePtr);
                regs.setSP(regs.getSP() - allocSize);
                break;
            }

            // ── LEAVE (C9) ─────────────────────────────────
            case 0xC9:
                regs.setSP(regs.getBP());
                regs.setBP(pop());
                break;

            // ── RETF imm16 (CA) ────────────────────────────
            case 0xCA: {
                int n = fetchWord();
                regs.setIP(pop());
                regs.cs = pop() & 0xFFFF;
                regs.setSP(regs.getSP() + n);
                break;
            }

            // ── RETF (CB) ──────────────────────────────────
            case 0xCB:
                regs.setIP(pop());
                regs.cs = pop() & 0xFFFF;
                break;

            // ── INT 3 (CC) ─────────────────────────────────
            case 0xCC: softwareInt(3); break;

            // ── INT imm8 (CD) ──────────────────────────────
            case 0xCD: softwareInt(fetchByte()); break;

            // ── INTO (CE) ──────────────────────────────────
            case 0xCE:
                if (regs.flags.getOF()) softwareInt(4);
                break;

            // ── IRET (CF) ──────────────────────────────────
            case 0xCF:
                regs.setIP(pop());
                regs.cs = pop() & 0xFFFF;
                regs.flags.setWord(pop());
                break;

            // ── Shift/Rotate r/m8, 1 (D0) ──────────────────
            case 0xD0: execShift8(1); break;

            // ── Shift/Rotate r/m16, 1 (D1) ─────────────────
            case 0xD1: execShift16(1); break;

            // ── Shift/Rotate r/m8, CL (D2) ─────────────────
            case 0xD2: execShift8(regs.getCL()); break;

            // ── Shift/Rotate r/m16, CL (D3) ────────────────
            case 0xD3: execShift16(regs.getCL()); break;

            // ── AAM (D4) ───────────────────────────────────
            case 0xD4: {
                int base = fetchByte();
                if (base == 0) { softwareInt(0); break; }
                regs.setAH(regs.getAL() / base);
                regs.setAL(regs.getAL() % base);
                regs.flags.setSZP8(regs.getAL());
                break;
            }

            // ── AAD (D5) ───────────────────────────────────
            case 0xD5: {
                int base = fetchByte();
                regs.setAL((regs.getAH() * base + regs.getAL()) & 0xFF);
                regs.setAH(0);
                regs.flags.setSZP8(regs.getAL());
                break;
            }

            // ── XLAT (D7) ──────────────────────────────────
            case 0xD7:
                regs.setAL(memory.readByte(Memory.segOfs(getDS(), (regs.getBX() + regs.getAL()) & 0xFFFF)));
                break;

            // ── LOOPNZ (E0) ────────────────────────────────
            case 0xE0: {
                int disp = fetchSignedByte();
                regs.setCX(regs.getCX() - 1);
                if (regs.getCX() != 0 && !regs.flags.getZF()) regs.setIP(regs.getIP() + disp);
                break;
            }

            // ── LOOPZ (E1) ─────────────────────────────────
            case 0xE1: {
                int disp = fetchSignedByte();
                regs.setCX(regs.getCX() - 1);
                if (regs.getCX() != 0 && regs.flags.getZF()) regs.setIP(regs.getIP() + disp);
                break;
            }

            // ── LOOP (E2) ──────────────────────────────────
            case 0xE2: {
                int disp = fetchSignedByte();
                regs.setCX(regs.getCX() - 1);
                if (regs.getCX() != 0) regs.setIP(regs.getIP() + disp);
                break;
            }

            // ── JCXZ (E3) ──────────────────────────────────
            case 0xE3: {
                int disp = fetchSignedByte();
                if (regs.getCX() == 0) regs.setIP(regs.getIP() + disp);
                break;
            }

            // ── IN AL, imm8 (E4) ───────────────────────────
            case 0xE4: regs.setAL(io.readByte(fetchByte())); break;

            // ── IN AX, imm16 (E5) ──────────────────────────
            case 0xE5: regs.setAX(io.readWord(fetchByte())); break;

            // ── OUT imm8, AL (E6) ──────────────────────────
            case 0xE6: io.writeByte(fetchByte(), regs.getAL()); break;

            // ── OUT imm8, AX (E7) ──────────────────────────
            case 0xE7: io.writeWord(fetchByte(), regs.getAX()); break;

            // ── CALL near (E8) ─────────────────────────────
            case 0xE8: {
                int disp = fetchWord();
                if (disp >= 0x8000) disp -= 0x10000;
                push(regs.getIP());
                regs.setIP(regs.getIP() + disp);
                break;
            }

            // ── JMP near (E9) ──────────────────────────────
            case 0xE9: {
                int disp = fetchWord();
                if (disp >= 0x8000) disp -= 0x10000;
                regs.setIP(regs.getIP() + disp);
                break;
            }

            // ── JMP far (EA) ───────────────────────────────
            case 0xEA: {
                int newIP = fetchWord();
                int newCS = fetchWord();
                regs.setIP(newIP);
                regs.cs = newCS;
                break;
            }

            // ── JMP short (EB) ─────────────────────────────
            case 0xEB: {
                int disp = fetchSignedByte();
                regs.setIP(regs.getIP() + disp);
                break;
            }

            // ── IN AL, DX (EC) ─────────────────────────────
            case 0xEC: regs.setAL(io.readByte(regs.getDX())); break;

            // ── IN AX, DX (ED) ─────────────────────────────
            case 0xED: regs.setAX(io.readWord(regs.getDX())); break;

            // ── OUT DX, AL (EE) ────────────────────────────
            case 0xEE: io.writeByte(regs.getDX(), regs.getAL()); break;

            // ── OUT DX, AX (EF) ────────────────────────────
            case 0xEF: io.writeWord(regs.getDX(), regs.getAX()); break;

            // ── HLT (F4) ───────────────────────────────────
            case 0xF4: halted = true; break;

            // ── CMC (F5) ───────────────────────────────────
            case 0xF5: regs.flags.setCF(!regs.flags.getCF()); break;

            // ── GRP3 r/m8 (F6) ─────────────────────────────
            case 0xF6: execGrp3_8(); break;

            // ── GRP3 r/m16 (F7) ────────────────────────────
            case 0xF7: execGrp3_16(); break;

            // ── CLC (F8) ───────────────────────────────────
            case 0xF8: regs.flags.setCF(false); break;

            // ── STC (F9) ───────────────────────────────────
            case 0xF9: regs.flags.setCF(true); break;

            // ── CLI (FA) ───────────────────────────────────
            case 0xFA: regs.flags.setIF(false); break;

            // ── STI (FB) ───────────────────────────────────
            case 0xFB: regs.flags.setIF(true); break;

            // ── CLD (FC) ───────────────────────────────────
            case 0xFC: regs.flags.setDF(false); break;

            // ── STD (FD) ───────────────────────────────────
            case 0xFD: regs.flags.setDF(true); break;

            // ── GRP4 r/m8 (FE) ─────────────────────────────
            case 0xFE: execGrp4(); break;

            // ── GRP5 r/m16 (FF) ────────────────────────────
            case 0xFF: execGrp5(); break;

            default:
                System.err.printf("Unimplemented opcode: %02X at %04X:%04X%n", opcode, regs.cs, regs.getIP() - 1);
                // Don't halt, just skip
                break;
        }
    }

    // ── Two-byte opcode handler (0x0F prefix) ───────────────

    private void executeTwoByteOpcode() {
        int op2 = fetchByte();
        switch (op2) {
            // ── Jcc near (0F 80 - 0F 8F) ───────────────────
            case 0x80: case 0x81: case 0x82: case 0x83:
            case 0x84: case 0x85: case 0x86: case 0x87:
            case 0x88: case 0x89: case 0x8A: case 0x8B:
            case 0x8C: case 0x8D: case 0x8E: case 0x8F: {
                int disp = fetchWord();
                if (disp >= 0x8000) disp -= 0x10000;
                if (evalCondition(op2 - 0x80)) {
                    regs.setIP(regs.getIP() + disp);
                }
                break;
            }

            // ── SETcc r/m8 (0F 90 - 0F 9F) ─────────────────
            case 0x90: case 0x91: case 0x92: case 0x93:
            case 0x94: case 0x95: case 0x96: case 0x97:
            case 0x98: case 0x99: case 0x9A: case 0x9B:
            case 0x9C: case 0x9D: case 0x9E: case 0x9F: {
                int modrm = fetchByte();
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val = evalCondition(op2 - 0x90) ? 1 : 0;
                if (ea == -1) { regs.setReg8(rm, val); } else { memory.writeByte(ea, val); }
                break;
            }

            // ── PUSH FS (0F A0) / POP FS (0F A1) ───────────
            case 0xA0: push(regs.fs); break;
            case 0xA1: regs.fs = pop() & 0xFFFF; break;

            // ── BT r/m16, reg16 (0F A3) ────────────────────
            case 0xA3: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg16(rm); } else { val = memory.readWord(ea); }
                int bit = regs.getReg16(reg) & 15;
                regs.flags.setCF(((val >> bit) & 1) != 0);
                break;
            }

            // ── SHLD r/m16, reg16, imm8 (0F A4) ────────────
            case 0xA4: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int dst;
                if (ea == -1) { dst = regs.getReg16(rm); } else { dst = memory.readWord(ea); }
                int src = regs.getReg16(reg);
                int cnt = fetchByte() & 0x1F;
                if (cnt != 0) {
                    int result = ((dst << cnt) | (src >>> (16 - cnt))) & 0xFFFF;
                    regs.flags.setCF(((dst >> (16 - cnt)) & 1) != 0);
                    regs.flags.setSZP16(result);
                    if (ea == -1) { regs.setReg16(rm, result); } else { memory.writeWord(ea, result); }
                }
                break;
            }

            // ── SHLD r/m16, reg16, CL (0F A5) ──────────────
            case 0xA5: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int dst;
                if (ea == -1) { dst = regs.getReg16(rm); } else { dst = memory.readWord(ea); }
                int src = regs.getReg16(reg);
                int cnt = regs.getCL() & 0x1F;
                if (cnt != 0) {
                    int result = ((dst << cnt) | (src >>> (16 - cnt))) & 0xFFFF;
                    regs.flags.setCF(((dst >> (16 - cnt)) & 1) != 0);
                    regs.flags.setSZP16(result);
                    if (ea == -1) { regs.setReg16(rm, result); } else { memory.writeWord(ea, result); }
                }
                break;
            }

            // ── PUSH GS (0F A8) / POP GS (0F A9) ───────────
            case 0xA8: push(regs.gs); break;
            case 0xA9: regs.gs = pop() & 0xFFFF; break;

            // ── BTS r/m16, reg16 (0F AB) ────────────────────
            case 0xAB: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg16(rm); } else { val = memory.readWord(ea); }
                int bit = regs.getReg16(reg) & 15;
                regs.flags.setCF(((val >> bit) & 1) != 0);
                val |= (1 << bit);
                if (ea == -1) { regs.setReg16(rm, val); } else { memory.writeWord(ea, val); }
                break;
            }

            // ── SHRD r/m16, reg16, imm8 (0F AC) ────────────
            case 0xAC: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int dst;
                if (ea == -1) { dst = regs.getReg16(rm); } else { dst = memory.readWord(ea); }
                int src = regs.getReg16(reg);
                int cnt = fetchByte() & 0x1F;
                if (cnt != 0) {
                    int result = ((dst >>> cnt) | (src << (16 - cnt))) & 0xFFFF;
                    regs.flags.setCF(((dst >> (cnt - 1)) & 1) != 0);
                    regs.flags.setSZP16(result);
                    if (ea == -1) { regs.setReg16(rm, result); } else { memory.writeWord(ea, result); }
                }
                break;
            }

            // ── SHRD r/m16, reg16, CL (0F AD) ──────────────
            case 0xAD: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int dst;
                if (ea == -1) { dst = regs.getReg16(rm); } else { dst = memory.readWord(ea); }
                int src = regs.getReg16(reg);
                int cnt = regs.getCL() & 0x1F;
                if (cnt != 0) {
                    int result = ((dst >>> cnt) | (src << (16 - cnt))) & 0xFFFF;
                    regs.flags.setCF(((dst >> (cnt - 1)) & 1) != 0);
                    regs.flags.setSZP16(result);
                    if (ea == -1) { regs.setReg16(rm, result); } else { memory.writeWord(ea, result); }
                }
                break;
            }

            // ── IMUL reg16, r/m16 (0F AF) ──────────────────
            case 0xAF: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int a = regs.getReg16(reg);
                int b;
                if (ea == -1) { b = regs.getReg16(rm); } else { b = memory.readWord(ea); }
                if (a >= 0x8000) a -= 0x10000;
                if (b >= 0x8000) b -= 0x10000;
                int result = a * b;
                regs.setReg16(reg, result & 0xFFFF);
                boolean overflow = (result < -32768 || result > 32767);
                regs.flags.setCF(overflow);
                regs.flags.setOF(overflow);
                break;
            }

            // ── BTR r/m16, reg16 (0F B3) ────────────────────
            case 0xB3: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg16(rm); } else { val = memory.readWord(ea); }
                int bit = regs.getReg16(reg) & 15;
                regs.flags.setCF(((val >> bit) & 1) != 0);
                val &= ~(1 << bit);
                if (ea == -1) { regs.setReg16(rm, val); } else { memory.writeWord(ea, val); }
                break;
            }

            // ── MOVZX reg16, r/m8 (0F B6) ──────────────────
            case 0xB6: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg8(rm); } else { val = memory.readByte(ea); }
                regs.setReg16(reg, val & 0xFF);
                break;
            }

            // ── MOVZX reg16, r/m16 (0F B7) — in 16-bit mode: just MOV
            case 0xB7: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg16(rm); } else { val = memory.readWord(ea); }
                regs.setReg16(reg, val & 0xFFFF);
                break;
            }

            // ── BT/BTS/BTR/BTC r/m16, imm8 (0F BA) ────────
            case 0xBA: {
                int modrm = fetchByte();
                int subOp = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg16(rm); } else { val = memory.readWord(ea); }
                int bit = fetchByte() & 15;
                regs.flags.setCF(((val >> bit) & 1) != 0);
                switch (subOp) {
                    case 4: break; // BT (test only)
                    case 5: val |= (1 << bit); break;  // BTS
                    case 6: val &= ~(1 << bit); break;  // BTR
                    case 7: val ^= (1 << bit); break;   // BTC
                }
                if (subOp >= 5) {
                    if (ea == -1) { regs.setReg16(rm, val); } else { memory.writeWord(ea, val); }
                }
                break;
            }

            // ── BTC r/m16, reg16 (0F BB) ────────────────────
            case 0xBB: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg16(rm); } else { val = memory.readWord(ea); }
                int bit = regs.getReg16(reg) & 15;
                regs.flags.setCF(((val >> bit) & 1) != 0);
                val ^= (1 << bit);
                if (ea == -1) { regs.setReg16(rm, val); } else { memory.writeWord(ea, val); }
                break;
            }

            // ── BSF reg16, r/m16 (0F BC) ────────────────────
            case 0xBC: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg16(rm); } else { val = memory.readWord(ea); }
                if (val == 0) {
                    regs.flags.setZF(true);
                } else {
                    regs.flags.setZF(false);
                    int bit = 0;
                    while (((val >> bit) & 1) == 0) bit++;
                    regs.setReg16(reg, bit);
                }
                break;
            }

            // ── BSR reg16, r/m16 (0F BD) ────────────────────
            case 0xBD: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg16(rm); } else { val = memory.readWord(ea); }
                if (val == 0) {
                    regs.flags.setZF(true);
                } else {
                    regs.flags.setZF(false);
                    int bit = 15;
                    while (bit > 0 && ((val >> bit) & 1) == 0) bit--;
                    regs.setReg16(reg, bit);
                }
                break;
            }

            // ── MOVSX reg16, r/m8 (0F BE) ──────────────────
            case 0xBE: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg8(rm); } else { val = memory.readByte(ea); }
                // Sign-extend byte to word
                if ((val & 0x80) != 0) val |= 0xFF00;
                regs.setReg16(reg, val & 0xFFFF);
                break;
            }

            // ── MOVSX reg16, r/m16 (0F BF) — in 16-bit mode: just MOV
            case 0xBF: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM16(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg16(rm); } else { val = memory.readWord(ea); }
                regs.setReg16(reg, val);
                break;
            }

            default:
                System.err.printf("Unimplemented 0F opcode: 0F %02X at %04X:%04X%n", op2, regs.cs, regs.getIP() - 2);
                break;
        }
    }

    // ── String operations ───────────────────────────────────

    private void execMovsb() {
        int dir = regs.flags.getDF() ? -1 : 1;
        if (repPrefix) {
            while (regs.getCX() != 0) {
                memory.writeByte(Memory.segOfs(regs.es, regs.getDI()),
                        memory.readByte(Memory.segOfs(getDS(), regs.getSI())));
                regs.setSI(regs.getSI() + dir);
                regs.setDI(regs.getDI() + dir);
                regs.setCX(regs.getCX() - 1);
            }
        } else {
            memory.writeByte(Memory.segOfs(regs.es, regs.getDI()),
                    memory.readByte(Memory.segOfs(getDS(), regs.getSI())));
            regs.setSI(regs.getSI() + dir);
            regs.setDI(regs.getDI() + dir);
        }
    }

    private void execMovsw() {
        int dir = regs.flags.getDF() ? -2 : 2;
        if (repPrefix) {
            while (regs.getCX() != 0) {
                memory.writeWord(Memory.segOfs(regs.es, regs.getDI()),
                        memory.readWord(Memory.segOfs(getDS(), regs.getSI())));
                regs.setSI(regs.getSI() + dir);
                regs.setDI(regs.getDI() + dir);
                regs.setCX(regs.getCX() - 1);
            }
        } else {
            memory.writeWord(Memory.segOfs(regs.es, regs.getDI()),
                    memory.readWord(Memory.segOfs(getDS(), regs.getSI())));
            regs.setSI(regs.getSI() + dir);
            regs.setDI(regs.getDI() + dir);
        }
    }

    private void execCmpsb() {
        int dir = regs.flags.getDF() ? -1 : 1;
        if (repPrefix) {
            while (regs.getCX() != 0) {
                int a = memory.readByte(Memory.segOfs(getDS(), regs.getSI()));
                int b = memory.readByte(Memory.segOfs(regs.es, regs.getDI()));
                alu8(7, a, b);
                regs.setSI(regs.getSI() + dir);
                regs.setDI(regs.getDI() + dir);
                regs.setCX(regs.getCX() - 1);
                if (repNE && regs.flags.getZF()) break;
                if (!repNE && !regs.flags.getZF()) break;
            }
        } else {
            int a = memory.readByte(Memory.segOfs(getDS(), regs.getSI()));
            int b = memory.readByte(Memory.segOfs(regs.es, regs.getDI()));
            alu8(7, a, b);
            regs.setSI(regs.getSI() + dir);
            regs.setDI(regs.getDI() + dir);
        }
    }

    private void execCmpsw() {
        int dir = regs.flags.getDF() ? -2 : 2;
        if (repPrefix) {
            while (regs.getCX() != 0) {
                int a = memory.readWord(Memory.segOfs(getDS(), regs.getSI()));
                int b = memory.readWord(Memory.segOfs(regs.es, regs.getDI()));
                alu16(7, a, b); // CMP
                regs.setSI(regs.getSI() + dir);
                regs.setDI(regs.getDI() + dir);
                regs.setCX(regs.getCX() - 1);
                if (repNE && regs.flags.getZF()) break;   // REPE: stop when not equal
                if (!repNE && !regs.flags.getZF()) break;  // REPNE: stop when equal
            }
        } else {
            int a = memory.readWord(Memory.segOfs(getDS(), regs.getSI()));
            int b = memory.readWord(Memory.segOfs(regs.es, regs.getDI()));
            alu16(7, a, b);
            regs.setSI(regs.getSI() + dir);
            regs.setDI(regs.getDI() + dir);
        }
    }

    private void execStosb() {
        int dir = regs.flags.getDF() ? -1 : 1;
        if (repPrefix) {
            while (regs.getCX() != 0) {
                memory.writeByte(Memory.segOfs(regs.es, regs.getDI()), regs.getAL());
                regs.setDI(regs.getDI() + dir);
                regs.setCX(regs.getCX() - 1);
            }
        } else {
            memory.writeByte(Memory.segOfs(regs.es, regs.getDI()), regs.getAL());
            regs.setDI(regs.getDI() + dir);
        }
    }

    private void execStosw() {
        int dir = regs.flags.getDF() ? -2 : 2;
        if (repPrefix) {
            while (regs.getCX() != 0) {
                memory.writeWord(Memory.segOfs(regs.es, regs.getDI()), regs.getAX());
                regs.setDI(regs.getDI() + dir);
                regs.setCX(regs.getCX() - 1);
            }
        } else {
            memory.writeWord(Memory.segOfs(regs.es, regs.getDI()), regs.getAX());
            regs.setDI(regs.getDI() + dir);
        }
    }

    private void execLodsb() {
        int dir = regs.flags.getDF() ? -1 : 1;
        regs.setAL(memory.readByte(Memory.segOfs(getDS(), regs.getSI())));
        regs.setSI(regs.getSI() + dir);
    }

    private void execLodsw() {
        int dir = regs.flags.getDF() ? -2 : 2;
        regs.setAX(memory.readWord(Memory.segOfs(getDS(), regs.getSI())));
        regs.setSI(regs.getSI() + dir);
    }

    private void execScasb() {
        int dir = regs.flags.getDF() ? -1 : 1;
        if (repPrefix) {
            while (regs.getCX() != 0) {
                int b = memory.readByte(Memory.segOfs(regs.es, regs.getDI()));
                alu8(7, regs.getAL(), b);
                regs.setDI(regs.getDI() + dir);
                regs.setCX(regs.getCX() - 1);
                if (repNE && regs.flags.getZF()) break;
                if (!repNE && !regs.flags.getZF()) break;
            }
        } else {
            int b = memory.readByte(Memory.segOfs(regs.es, regs.getDI()));
            alu8(7, regs.getAL(), b);
            regs.setDI(regs.getDI() + dir);
        }
    }

    private void execScasw() {
        int dir = regs.flags.getDF() ? -2 : 2;
        if (repPrefix) {
            while (regs.getCX() != 0) {
                int b = memory.readWord(Memory.segOfs(regs.es, regs.getDI()));
                alu16(7, regs.getAX(), b); // CMP AX with ES:DI word
                regs.setDI(regs.getDI() + dir);
                regs.setCX(regs.getCX() - 1);
                if (repNE && regs.flags.getZF()) break;
                if (!repNE && !regs.flags.getZF()) break;
            }
        } else {
            int b = memory.readWord(Memory.segOfs(regs.es, regs.getDI()));
            alu16(7, regs.getAX(), b);
            regs.setDI(regs.getDI() + dir);
        }
    }

    // ── Shift/Rotate ────────────────────────────────────────

    private void execShift8(int count) {
        int modrm = fetchByte();
        int op = (modrm >> 3) & 7;
        int ea = decodeModRM16(modrm);
        int rm = modrm & 7;
        int val;
        if (ea == -1) { val = regs.getReg8(rm); } else { val = memory.readByte(ea); }
        count &= 0x1F;
        if (count == 0) return;
        val = doShift8(op, val, count);
        if (ea == -1) { regs.setReg8(rm, val); } else { memory.writeByte(ea, val); }
    }

    private void execShift16(int count) {
        int modrm = fetchByte();
        int op = (modrm >> 3) & 7;
        int ea = decodeModRM16(modrm);
        int rm = modrm & 7;
        int val;
        if (ea == -1) { val = regs.getReg16(rm); } else { val = memory.readWord(ea); }
        count &= 0x1F;
        if (count == 0) return;
        val = doShift16(op, val, count);
        if (ea == -1) { regs.setReg16(rm, val); } else { memory.writeWord(ea, val); }
    }

    private int doShift8(int op, int val, int count) {
        int result;
        switch (op) {
            case 0: // ROL
                for (int i = 0; i < count; i++) {
                    int msb = (val >> 7) & 1;
                    val = ((val << 1) | msb) & 0xFF;
                }
                regs.flags.setCF((val & 1) != 0);
                if (count == 1) regs.flags.setOF(((val >> 7) ^ (val & 1)) != 0);
                return val;
            case 1: // ROR
                for (int i = 0; i < count; i++) {
                    int lsb = val & 1;
                    val = ((val >> 1) | (lsb << 7)) & 0xFF;
                }
                regs.flags.setCF((val & 0x80) != 0);
                if (count == 1) regs.flags.setOF((((val >> 7) ^ (val >> 6)) & 1) != 0);
                return val;
            case 2: { // RCL (rotate through carry left)
                for (int i = 0; i < count; i++) {
                    int oldCF = regs.flags.getCF() ? 1 : 0;
                    regs.flags.setCF((val & 0x80) != 0);
                    val = ((val << 1) | oldCF) & 0xFF;
                }
                if (count == 1) regs.flags.setOF(((val >> 7) ^ (regs.flags.getCF() ? 1 : 0)) != 0);
                return val;
            }
            case 3: { // RCR (rotate through carry right)
                for (int i = 0; i < count; i++) {
                    int oldCF = regs.flags.getCF() ? 1 : 0;
                    regs.flags.setCF((val & 1) != 0);
                    val = ((val >> 1) | (oldCF << 7)) & 0xFF;
                }
                if (count == 1) regs.flags.setOF((((val >> 7) ^ (val >> 6)) & 1) != 0);
                return val;
            }
            case 4: case 6: // SHL (6 is undocumented alias)
                result = val << count;
                regs.flags.setCF((result & 0x100) != 0);
                result &= 0xFF;
                regs.flags.setSZP8(result);
                if (count == 1) regs.flags.setOF(((result >> 7) ^ (regs.flags.getCF() ? 1 : 0)) != 0);
                return result;
            case 5: // SHR
                regs.flags.setCF(((val >> (count - 1)) & 1) != 0);
                if (count == 1) regs.flags.setOF((val & 0x80) != 0);
                result = (val >> count) & 0xFF;
                regs.flags.setSZP8(result);
                return result;
            case 7: // SAR
                regs.flags.setCF(((val >> (count - 1)) & 1) != 0);
                if (count == 1) regs.flags.setOF(false);
                int sign = (val & 0x80) != 0 ? 0xFF : 0;
                result = val;
                for (int i = 0; i < count; i++) result = ((result >> 1) | (sign & 0x80)) & 0xFF;
                regs.flags.setSZP8(result);
                return result;
            default: return val;
        }
    }

    private int doShift16(int op, int val, int count) {
        int result;
        switch (op) {
            case 0: // ROL
                for (int i = 0; i < count; i++) {
                    int msb = (val >> 15) & 1;
                    val = ((val << 1) | msb) & 0xFFFF;
                }
                regs.flags.setCF((val & 1) != 0);
                return val;
            case 1: // ROR
                for (int i = 0; i < count; i++) {
                    int lsb = val & 1;
                    val = ((val >> 1) | (lsb << 15)) & 0xFFFF;
                }
                regs.flags.setCF((val & 0x8000) != 0);
                return val;
            case 2: { // RCL
                for (int i = 0; i < count; i++) {
                    int oldCF = regs.flags.getCF() ? 1 : 0;
                    regs.flags.setCF((val & 0x8000) != 0);
                    val = ((val << 1) | oldCF) & 0xFFFF;
                }
                return val;
            }
            case 3: { // RCR
                for (int i = 0; i < count; i++) {
                    int oldCF = regs.flags.getCF() ? 1 : 0;
                    regs.flags.setCF((val & 1) != 0);
                    val = ((val >> 1) | (oldCF << 15)) & 0xFFFF;
                }
                return val;
            }
            case 4: case 6: // SHL
                result = val << count;
                regs.flags.setCF((result & 0x10000) != 0);
                result &= 0xFFFF;
                regs.flags.setSZP16(result);
                return result;
            case 5: // SHR
                regs.flags.setCF(((val >> (count - 1)) & 1) != 0);
                result = (val >> count) & 0xFFFF;
                regs.flags.setSZP16(result);
                return result;
            case 7: // SAR
                regs.flags.setCF(((val >> (count - 1)) & 1) != 0);
                int sign = (val & 0x8000) != 0 ? 0xFFFF : 0;
                result = val;
                for (int i = 0; i < count; i++) result = ((result >> 1) | (sign & 0x8000)) & 0xFFFF;
                regs.flags.setSZP16(result);
                return result;
            default: return val;
        }
    }

    // ── GRP3 (TEST, NOT, NEG, MUL, DIV, ...) ───────────────

    private void execGrp3_8() {
        int modrm = fetchByte();
        int op = (modrm >> 3) & 7;
        int ea = decodeModRM16(modrm);
        int rm = modrm & 7;
        int val;
        if (ea == -1) { val = regs.getReg8(rm); } else { val = memory.readByte(ea); }

        switch (op) {
            case 0: // TEST r/m8, imm8
                alu8(4, val, fetchByte());
                break;
            case 2: // NOT
                val = (~val) & 0xFF;
                if (ea == -1) regs.setReg8(rm, val); else memory.writeByte(ea, val);
                break;
            case 3: // NEG
                int result = alu8(5, 0, val); // 0 - val
                if (ea == -1) regs.setReg8(rm, result); else memory.writeByte(ea, result);
                regs.flags.setCF(val != 0);
                break;
            case 4: { // MUL
                int res = regs.getAL() * val;
                regs.setAX(res & 0xFFFF);
                boolean hi = (res & 0xFF00) != 0;
                regs.flags.setCF(hi);
                regs.flags.setOF(hi);
                break;
            }
            case 5: { // IMUL
                int res = ((byte) regs.getAL()) * ((byte) val);
                regs.setAX(res & 0xFFFF);
                boolean ext = (regs.getAH() != 0 && regs.getAH() != 0xFF);
                regs.flags.setCF(ext);
                regs.flags.setOF(ext);
                break;
            }
            case 6: { // DIV
                if (val == 0) { softwareInt(0); return; }
                int dividend = regs.getAX();
                int quot = (dividend & 0xFFFF) / val;
                int rem = (dividend & 0xFFFF) % val;
                if (quot > 0xFF) { softwareInt(0); return; }
                regs.setAL(quot);
                regs.setAH(rem);
                break;
            }
            case 7: { // IDIV
                if (val == 0) { softwareInt(0); return; }
                short dividend = (short) regs.getAX();
                byte divisor = (byte) val;
                int quot = dividend / divisor;
                int rem = dividend % divisor;
                if (quot > 127 || quot < -128) { softwareInt(0); return; }
                regs.setAL(quot & 0xFF);
                regs.setAH(rem & 0xFF);
                break;
            }
        }
    }

    private void execGrp3_16() {
        int modrm = fetchByte();
        int op = (modrm >> 3) & 7;
        int ea = decodeModRM16(modrm);
        int rm = modrm & 7;
        int val;
        if (ea == -1) { val = regs.getReg16(rm); } else { val = memory.readWord(ea); }

        switch (op) {
            case 0: // TEST
                alu16(4, val, fetchWord());
                break;
            case 2: // NOT
                val = (~val) & 0xFFFF;
                if (ea == -1) regs.setReg16(rm, val); else memory.writeWord(ea, val);
                break;
            case 3: { // NEG
                int result = alu16(5, 0, val);
                if (ea == -1) regs.setReg16(rm, result); else memory.writeWord(ea, result);
                regs.flags.setCF(val != 0);
                break;
            }
            case 4: { // MUL
                long res = (long)(regs.getAX() & 0xFFFF) * (val & 0xFFFF);
                regs.setAX((int)(res & 0xFFFF));
                regs.setDX((int)((res >> 16) & 0xFFFF));
                boolean hi = regs.getDX() != 0;
                regs.flags.setCF(hi);
                regs.flags.setOF(hi);
                break;
            }
            case 5: { // IMUL
                long res = (long)((short)regs.getAX()) * ((short)val);
                regs.setAX((int)(res & 0xFFFF));
                regs.setDX((int)((res >> 16) & 0xFFFF));
                boolean ext = (regs.getDX() != 0 && regs.getDX() != 0xFFFF);
                regs.flags.setCF(ext);
                regs.flags.setOF(ext);
                break;
            }
            case 6: { // DIV
                if (val == 0) { softwareInt(0); return; }
                long dividend = ((long)(regs.getDX() & 0xFFFF) << 16) | (regs.getAX() & 0xFFFF);
                long quot = Long.divideUnsigned(dividend, val & 0xFFFF);
                long rem = Long.remainderUnsigned(dividend, val & 0xFFFF);
                if (quot > 0xFFFF) { softwareInt(0); return; }
                regs.setAX((int)(quot & 0xFFFF));
                regs.setDX((int)(rem & 0xFFFF));
                break;
            }
            case 7: { // IDIV
                if (val == 0) { softwareInt(0); return; }
                int dividend = (regs.getDX() << 16) | (regs.getAX() & 0xFFFF);
                short divisor = (short) val;
                int quot = dividend / divisor;
                int rem = dividend % divisor;
                if (quot > 32767 || quot < -32768) { softwareInt(0); return; }
                regs.setAX(quot & 0xFFFF);
                regs.setDX(rem & 0xFFFF);
                break;
            }
        }
    }

    // ── GRP4 (INC/DEC r/m8) ────────────────────────────────

    private void execGrp4() {
        int modrm = fetchByte();
        int op = (modrm >> 3) & 7;
        int ea = decodeModRM16(modrm);
        int rm = modrm & 7;
        int val;
        if (ea == -1) { val = regs.getReg8(rm); } else { val = memory.readByte(ea); }

        boolean cf = regs.flags.getCF();
        switch (op) {
            case 0: val = alu8(0, val, 1); break; // INC
            case 1: val = alu8(5, val, 1); break; // DEC
            default: return;
        }
        regs.flags.setCF(cf); // INC/DEC don't affect CF
        if (ea == -1) regs.setReg8(rm, val); else memory.writeByte(ea, val);
    }

    // ── GRP5 (INC/DEC/CALL/JMP/PUSH r/m16) ─────────────────

    private void execGrp5() {
        int modrm = fetchByte();
        int op = (modrm >> 3) & 7;
        int ea = decodeModRM16(modrm);
        int rm = modrm & 7;
        int val;
        if (ea == -1) { val = regs.getReg16(rm); } else { val = memory.readWord(ea); }

        switch (op) {
            case 0: { // INC
                boolean cf = regs.flags.getCF();
                int result = alu16(0, val, 1);
                regs.flags.setCF(cf);
                if (ea == -1) regs.setReg16(rm, result); else memory.writeWord(ea, result);
                break;
            }
            case 1: { // DEC
                boolean cf = regs.flags.getCF();
                int result = alu16(5, val, 1);
                regs.flags.setCF(cf);
                if (ea == -1) regs.setReg16(rm, result); else memory.writeWord(ea, result);
                break;
            }
            case 2: // CALL near indirect
                push(regs.getIP());
                regs.setIP(val);
                break;
            case 3: // CALL far indirect
                push(regs.cs);
                push(regs.getIP());
                regs.setIP(memory.readWord(ea));
                regs.cs = memory.readWord(ea + 2);
                break;
            case 4: // JMP near indirect
                regs.setIP(val);
                break;
            case 5: // JMP far indirect
                regs.setIP(memory.readWord(ea));
                regs.cs = memory.readWord(ea + 2);
                break;
            case 6: // PUSH
                push(val);
                break;
        }
    }

    public Memory getMemory() { return memory; }
    public IoPortHandler getIo() { return io; }
    public PIC getPic() { return pic; }
}

