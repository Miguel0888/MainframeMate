package de.bund.zrb.dosbox.cpu;

import de.bund.zrb.dosbox.hardware.memory.Memory;

/**
 * CPU instruction trace recorder.
 * Stores the last N executed instructions in a ring buffer
 * for post-mortem debugging of DOS programs.
 *
 * Each trace entry captures the full CPU state before instruction execution.
 */
public class CpuTrace {

    /** Single trace entry capturing pre-execution CPU state. */
    public static class Entry {
        public long cycle;
        public int cs, ip;
        public int ss, sp;
        public int ds, es, fs, gs;
        public int ax, bx, cx, dx;
        public int si, di, bp;
        public int flags;
        public int opcode;       // first opcode byte
        public int opcode2;      // second byte (for 0F xx, or modrm)
        public int linearAddr;   // resolved CS:IP linear address
        public boolean pm;       // protected mode?
        public String disasm;    // optional disassembly text

        @Override
        public String toString() {
            String opcStr;
            if (opcode == 0x0F && opcode2 != 0) {
                opcStr = String.format("0F %02X", opcode2 & 0xFF);
            } else {
                opcStr = String.format("%02X", opcode & 0xFF);
            }
            return String.format("%08d %s %04X:%08X [%08X] %-5s %-6s | EAX=%08X EBX=%08X ECX=%08X EDX=%08X ESI=%08X EDI=%08X ESP=%08X EBP=%08X DS=%04X ES=%04X FL=%08X",
                    cycle,
                    pm ? "PM" : "RM",
                    cs & 0xFFFF, ip,
                    linearAddr,
                    opcStr,
                    disasm != null ? disasm : "",
                    ax, bx, cx, dx,
                    si, di, sp, bp,
                    ds & 0xFFFF, es & 0xFFFF, flags);
        }

        /** Tab-separated line for export. */
        public String toTSV() {
            return String.format("%d\t%s\t%04X\t%04X\t%08X\t%02X\t%s\tAX=%04X\tBX=%04X\tCX=%04X\tDX=%04X\tSI=%04X\tDI=%04X\tSP=%04X\tDS=%04X\tES=%04X\tSS=%04X\tFL=%04X",
                    cycle, pm ? "PM" : "RM",
                    cs & 0xFFFF, ip & 0xFFFF, linearAddr, opcode & 0xFF,
                    disasm != null ? disasm : "",
                    ax & 0xFFFF, bx & 0xFFFF, cx & 0xFFFF, dx & 0xFFFF,
                    si & 0xFFFF, di & 0xFFFF, sp & 0xFFFF,
                    ds & 0xFFFF, es & 0xFFFF, ss & 0xFFFF, flags & 0xFFFF);
        }
    }

    private final Entry[] buffer;
    private final int capacity;
    private int writePos;
    private int count;
    private boolean enabled;

    /** Create a trace buffer with the given capacity (number of entries). */
    public CpuTrace(int capacity) {
        this.capacity = capacity;
        this.buffer = new Entry[capacity];
        for (int i = 0; i < capacity; i++) {
            buffer[i] = new Entry();
        }
        this.writePos = 0;
        this.count = 0;
        this.enabled = false;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** Record the CPU state before executing an instruction. */
    public void record(CPU cpu, int opcode, long cycle) {
        if (!enabled) return;

        Entry e = buffer[writePos];
        e.cycle = cycle;
        e.cs = cpu.regs.cs;
        e.ip = cpu.regs.getEIP();
        e.ss = cpu.regs.ss;
        e.sp = cpu.regs.getESP();
        e.ds = cpu.regs.ds;
        e.es = cpu.regs.es;
        e.fs = cpu.regs.fs;
        e.gs = cpu.regs.gs;
        e.ax = cpu.regs.getEAX();
        e.bx = cpu.regs.getEBX();
        e.cx = cpu.regs.getECX();
        e.dx = cpu.regs.getEDX();
        e.si = cpu.regs.getESI();
        e.di = cpu.regs.getEDI();
        e.bp = cpu.regs.getEBP();
        e.flags = cpu.regs.flags.getDWord();
        e.opcode = opcode;
        e.pm = cpu.isProtectedMode();
        e.linearAddr = cpu.resolveSegOfs(cpu.regs.cs, cpu.regs.getEIP());
        e.disasm = disassembleSimple(opcode);

        writePos = (writePos + 1) % capacity;
        if (count < capacity) count++;
    }

    /** Get the number of entries currently stored. */
    public int getCount() { return count; }

    /** Clear all trace entries. */
    public void clear() {
        writePos = 0;
        count = 0;
    }

    /**
     * Get all trace entries in chronological order (oldest first).
     * Returns a new array.
     */
    public Entry[] getEntries() {
        Entry[] result = new Entry[count];
        if (count < capacity) {
            // Buffer not yet wrapped
            System.arraycopy(buffer, 0, result, 0, count);
        } else {
            // Buffer has wrapped — oldest entry is at writePos
            int first = writePos; // oldest
            int tail = capacity - first;
            System.arraycopy(buffer, first, result, 0, tail);
            System.arraycopy(buffer, 0, result, tail, first);
        }
        return result;
    }

    /**
     * Get the last N entries (most recent).
     */
    public Entry[] getLastEntries(int n) {
        Entry[] all = getEntries();
        if (n >= all.length) return all;
        Entry[] result = new Entry[n];
        System.arraycopy(all, all.length - n, result, 0, n);
        return result;
    }

    /**
     * Export all entries as a multi-line string.
     */
    public String exportAsText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Cycle    Mode CS   IP   Linear   Op Disasm  | Registers\n");
        sb.append("-------- ---- ---- ---- -------- -- ------- | --------------------------------------------------\n");
        for (Entry e : getEntries()) {
            sb.append(e.toString()).append('\n');
        }
        return sb.toString();
    }

    // ── Simple disassembler for common opcodes ──────────────

    private static String disassembleSimple(int opcode) {
        switch (opcode) {
            case 0x00: case 0x01: case 0x02: case 0x03: case 0x04: case 0x05: return "ADD";
            case 0x06: return "PUSH ES";
            case 0x07: return "POP ES";
            case 0x08: case 0x09: case 0x0A: case 0x0B: case 0x0C: case 0x0D: return "OR";
            case 0x0E: return "PUSH CS";
            case 0x0F: return "0F...";
            case 0x10: case 0x11: case 0x12: case 0x13: case 0x14: case 0x15: return "ADC";
            case 0x16: return "PUSH SS";
            case 0x17: return "POP SS";
            case 0x18: case 0x19: case 0x1A: case 0x1B: case 0x1C: case 0x1D: return "SBB";
            case 0x1E: return "PUSH DS";
            case 0x1F: return "POP DS";
            case 0x20: case 0x21: case 0x22: case 0x23: case 0x24: case 0x25: return "AND";
            case 0x27: return "DAA";
            case 0x28: case 0x29: case 0x2A: case 0x2B: case 0x2C: case 0x2D: return "SUB";
            case 0x2F: return "DAS";
            case 0x30: case 0x31: case 0x32: case 0x33: case 0x34: case 0x35: return "XOR";
            case 0x37: return "AAA";
            case 0x38: case 0x39: case 0x3A: case 0x3B: case 0x3C: case 0x3D: return "CMP";
            case 0x3F: return "AAS";
            case 0x40: case 0x41: case 0x42: case 0x43:
            case 0x44: case 0x45: case 0x46: case 0x47: return "INC";
            case 0x48: case 0x49: case 0x4A: case 0x4B:
            case 0x4C: case 0x4D: case 0x4E: case 0x4F: return "DEC";
            case 0x50: case 0x51: case 0x52: case 0x53:
            case 0x54: case 0x55: case 0x56: case 0x57: return "PUSH";
            case 0x58: case 0x59: case 0x5A: case 0x5B:
            case 0x5C: case 0x5D: case 0x5E: case 0x5F: return "POP";
            case 0x60: return "PUSHA";
            case 0x61: return "POPA";
            case 0x68: return "PUSH i";
            case 0x6A: return "PUSH i8";
            case 0x70: case 0x71: case 0x72: case 0x73:
            case 0x74: case 0x75: case 0x76: case 0x77:
            case 0x78: case 0x79: case 0x7A: case 0x7B:
            case 0x7C: case 0x7D: case 0x7E: case 0x7F: return "Jcc";
            case 0x80: case 0x81: case 0x82: case 0x83: return "GRP1";
            case 0x84: case 0x85: return "TEST";
            case 0x86: case 0x87: return "XCHG";
            case 0x88: case 0x89: case 0x8A: case 0x8B: return "MOV";
            case 0x8C: return "MOV sr";
            case 0x8D: return "LEA";
            case 0x8E: return "MOV sr";
            case 0x8F: return "POP m";
            case 0x90: return "NOP";
            case 0x91: case 0x92: case 0x93: case 0x94:
            case 0x95: case 0x96: case 0x97: return "XCHG";
            case 0x98: return "CBW";
            case 0x99: return "CWD";
            case 0x9A: return "CALL F";
            case 0x9C: return "PUSHF";
            case 0x9D: return "POPF";
            case 0x9E: return "SAHF";
            case 0x9F: return "LAHF";
            case 0xA0: case 0xA1: return "MOV m";
            case 0xA2: case 0xA3: return "MOV m";
            case 0xA4: return "MOVSB";
            case 0xA5: return "MOVSW";
            case 0xA6: return "CMPSB";
            case 0xA7: return "CMPSW";
            case 0xA8: case 0xA9: return "TEST";
            case 0xAA: return "STOSB";
            case 0xAB: return "STOSW";
            case 0xAC: return "LODSB";
            case 0xAD: return "LODSW";
            case 0xAE: return "SCASB";
            case 0xAF: return "SCASW";
            case 0xB0: case 0xB1: case 0xB2: case 0xB3:
            case 0xB4: case 0xB5: case 0xB6: case 0xB7: return "MOV r8";
            case 0xB8: case 0xB9: case 0xBA: case 0xBB:
            case 0xBC: case 0xBD: case 0xBE: case 0xBF: return "MOV r";
            case 0xC0: case 0xC1: return "SHIFT";
            case 0xC2: return "RET n";
            case 0xC3: return "RET";
            case 0xC4: return "LES";
            case 0xC5: return "LDS";
            case 0xC6: case 0xC7: return "MOV mi";
            case 0xC8: return "ENTER";
            case 0xC9: return "LEAVE";
            case 0xCA: return "RETF n";
            case 0xCB: return "RETF";
            case 0xCC: return "INT 3";
            case 0xCD: return "INT";
            case 0xCE: return "INTO";
            case 0xCF: return "IRET";
            case 0xD0: case 0xD1: case 0xD2: case 0xD3: return "SHIFT";
            case 0xD6: return "SALC";
            case 0xD7: return "XLAT";
            case 0xD8: case 0xD9: case 0xDA: case 0xDB:
            case 0xDC: case 0xDD: case 0xDE: case 0xDF: return "FPU";
            case 0xE0: return "LOOPNE";
            case 0xE1: return "LOOPE";
            case 0xE2: return "LOOP";
            case 0xE3: return "JCXZ";
            case 0xE4: case 0xE5: return "IN";
            case 0xE6: case 0xE7: return "OUT";
            case 0xE8: return "CALL";
            case 0xE9: return "JMP";
            case 0xEA: return "JMP F";
            case 0xEB: return "JMP s";
            case 0xEC: case 0xED: return "IN DX";
            case 0xEE: case 0xEF: return "OUT DX";
            case 0xF4: return "HLT";
            case 0xF5: return "CMC";
            case 0xF6: case 0xF7: return "GRP3";
            case 0xF8: return "CLC";
            case 0xF9: return "STC";
            case 0xFA: return "CLI";
            case 0xFB: return "STI";
            case 0xFC: return "CLD";
            case 0xFD: return "STD";
            case 0xFE: return "GRP4";
            case 0xFF: return "GRP5";
            default: return String.format("%02X", opcode);
        }
    }
}

