package de.bund.zrb.dosbox.cpu;

import de.bund.zrb.dosbox.core.Module;
import de.bund.zrb.dosbox.dos.DPMIManager;
import de.bund.zrb.dosbox.hardware.memory.IoPortHandler;
import de.bund.zrb.dosbox.hardware.memory.Memory;
import de.bund.zrb.dosbox.hardware.pic.PIC;

/**
 * x86 CPU emulation core (real mode + 386 extensions).
 * Implements fetch-decode-execute cycle with interrupt dispatch.
 * Supports 8/16/32-bit operations via operand-size prefix (0x66).
 *
 * Ported from: src/cpu/cpu.cpp, src/cpu/core_normal.cpp
 */
public class CPU implements Module {

    public final Regs regs = new Regs();
    public final FPU fpu = new FPU();
    private final Memory memory;
    private final IoPortHandler io;
    private final PIC pic;
    private DPMIManager dpmi; // set after construction

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

    // ── 386 prefix state ─────────────────────────────────────
    private boolean prefix66;     // operand size override (16↔32)
    private boolean prefix67;     // address size override (16↔32)
    private boolean csIs32;       // CS descriptor D-bit (true = 32-bit default)

    // ── Protected mode / system registers ────────────────────
    private int cr0;              // Control register 0 (bit 0 = PE)
    private int cr2;              // Page fault linear address
    private int cr3;              // Page directory base
    private int cr4;              // Various extensions
    private int gdtr_base;        // GDT base address
    private int gdtr_limit;       // GDT limit
    private int idtr_base;        // IDT base address
    private int idtr_limit;       // IDT limit (default 0x3FF for real mode)

    // ── Cycle counting ──────────────────────────────────────
    private long totalCycles;
    private static final int CYCLES_PER_TICK = 1000; // simplified
    private int csChangeLogCount = 0; // limit CS change logging

    // ── Instruction trace ────────────────────────────────────
    private final CpuTrace trace = new CpuTrace(100_000); // last 100K instructions

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
        prefix66 = false;
        prefix67 = false;
        cr0 = 0;
        cr2 = 0;
        cr3 = 0;
        cr4 = 0;
        gdtr_base = 0;
        gdtr_limit = 0;
        idtr_base = 0;
        idtr_limit = 0x3FF; // real mode IVT
        totalCycles = 0;
    }

    // ── DPMI manager (set after construction) ─────────────

    public void setDPMI(DPMIManager dpmi) { this.dpmi = dpmi; }
    public DPMIManager getDPMI() { return dpmi; }

    // ── Interrupt handler registration ──────────────────────

    public void setIntHandler(int vector, IntHandler handler) {
        intHandlers[vector & 0xFF] = handler;
    }

    // ── Protected mode check ────────────────────────────────

    /** Check if CPU is in protected mode (CR0 PE bit). */
    public boolean isProtectedMode() {
        return (cr0 & 1) != 0;
    }

    /**
     * Resolve segment:offset to a linear (physical) address.
     * In real mode: seg*16 + offset.
     * In protected mode:
     *   - If DPMI active: use DPMI descriptor resolution (handles both GDT and LDT)
     *   - If PE set but no DPMI: read GDT descriptors directly from memory
     *
     * IMPORTANT: When DPMI is active, we ALWAYS use DPMI resolution, even if PE
     * has been temporarily cleared (e.g., by DOS/4GW doing MOV CR0 to switch to
     * real mode). This simulates the x86 segment descriptor cache behavior:
     * after clearing PE, descriptors remain cached until segment registers are
     * explicitly reloaded. The DPMI fallback paths handle real-mode segment
     * values gracefully by falling back to seg*16+offset for unknown selectors.
     */
    public int resolveSegOfs(int seg, int offset) {
        // When DPMI is active, always use PM resolution (descriptor cache simulation)
        if (dpmi != null && dpmi.isDpmiActive()) {
            return dpmi.resolveAddress(seg, offset);
        }
        if (isProtectedMode()) {
            // PE set without DPMI (DOS extender set PE directly)
            // Try to resolve via GDT if we have one (gdtr_limit > 0 means GDT was loaded)
            if (dpmi != null && gdtr_limit > 0) {
                // Sync GDTR and use dpmi's GDT reading capability
                dpmi.setGDTR(gdtr_base, gdtr_limit);
                return dpmi.resolveAddress(seg, offset);
            }
        }
        return Memory.segOfs(seg, offset);
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

    /** Get the effective instruction pointer (16 or 32 bit based on CS D-bit). */
    private int getEffIP() {
        return csIs32 ? regs.getEIP() : regs.getIP();
    }

    /** Set the effective instruction pointer (16 or 32 bit based on CS D-bit). */
    private void setEffIP(int v) {
        if (csIs32) regs.setEIP(v);
        else regs.setIP(v);
    }

    /** Fetch a byte at CS:IP and advance IP. */
    private int fetchByte() {
        int ip = getEffIP();
        int addr = resolveSegOfs(regs.cs, ip);
        setEffIP(ip + 1);
        return memory.readByte(addr);
    }

    /** Fetch a word at CS:IP and advance IP. */
    private int fetchWord() {
        int lo = fetchByte();
        int hi = fetchByte();
        return lo | (hi << 8);
    }

    /** Fetch a dword at CS:IP and advance IP. */
    private int fetchDWord() {
        int b0 = fetchByte();
        int b1 = fetchByte();
        int b2 = fetchByte();
        int b3 = fetchByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    /** Fetch a signed byte at CS:IP. */
    private int fetchSignedByte() {
        int v = fetchByte();
        return (v < 128) ? v : v - 256;
    }

    /** Fetch signed word at CS:IP. */
    private int fetchSignedWord() {
        int v = fetchWord();
        return (v < 0x8000) ? v : v - 0x10000;
    }

    // ── Operand-size-aware fetch/access helpers ──────────────

    /** Fetch immediate: 32-bit if prefix66, else 16-bit. */
    private int fetchImm() {
        return prefix66 ? fetchDWord() : fetchWord();
    }

    /** Fetch signed immediate: 32-bit if prefix66, else 16-bit. */
    private int fetchSignedImm() {
        if (prefix66) {
            return fetchDWord(); // already 32-bit signed int
        }
        return fetchSignedWord();
    }

    /** Get register value (16 or 32 bit based on prefix66). */
    private int getRegOp(int idx) {
        return prefix66 ? regs.getReg32(idx) : regs.getReg16(idx);
    }

    /** Set register value (16 or 32 bit based on prefix66). */
    private void setRegOp(int idx, int v) {
        if (prefix66) regs.setReg32(idx, v);
        else regs.setReg16(idx, v);
    }

    /** Read memory word/dword based on prefix66. */
    private int readMemOp(int addr) {
        if (prefix66) {
            return memory.readByte(addr)
                 | (memory.readByte(addr + 1) << 8)
                 | (memory.readByte(addr + 2) << 16)
                 | (memory.readByte(addr + 3) << 24);
        }
        return memory.readWord(addr);
    }

    /** Write memory word/dword based on prefix66. */
    private void writeMemOp(int addr, int val) {
        if (prefix66) {
            memory.writeByte(addr, val & 0xFF);
            memory.writeByte(addr + 1, (val >> 8) & 0xFF);
            memory.writeByte(addr + 2, (val >> 16) & 0xFF);
            memory.writeByte(addr + 3, (val >> 24) & 0xFF);
        } else {
            memory.writeWord(addr, val);
        }
    }

    /** Get AX or EAX based on prefix66. */
    private int getAccOp() {
        return prefix66 ? regs.getEAX() : regs.getAX();
    }

    /** Set AX or EAX based on prefix66. */
    private void setAccOp(int v) {
        if (prefix66) regs.setEAX(v);
        else regs.setAX(v);
    }

    /** ALU operation dispatched by operand size. */
    private int aluOp(int op, int a, int b) {
        return prefix66 ? alu32(op, a, b) : alu16(op, a, b);
    }

    /** Set SZP flags based on operand size. */
    private void setSZPOp(int result) {
        if (prefix66) regs.flags.setSZP32(result);
        else regs.flags.setSZP16(result);
    }

    // ── Push/Pop ─────────────────────────────────────────────

    /** Push a word onto the stack (16-bit). */
    public void push(int value) {
        if (csIs32) {
            regs.setESP(regs.getESP() - 2);
            memory.writeWord(resolveSegOfs(regs.ss, regs.getESP()), value);
        } else {
            regs.setSP(regs.getSP() - 2);
            memory.writeWord(resolveSegOfs(regs.ss, regs.getSP()), value);
        }
    }

    /** Pop a word from the stack (16-bit). */
    public int pop() {
        if (csIs32) {
            int val = memory.readWord(resolveSegOfs(regs.ss, regs.getESP()));
            regs.setESP(regs.getESP() + 2);
            return val;
        } else {
            int val = memory.readWord(resolveSegOfs(regs.ss, regs.getSP()));
            regs.setSP(regs.getSP() + 2);
            return val;
        }
    }

    /** Push a dword onto the stack (32-bit). */
    private void push32(int value) {
        if (csIs32) {
            regs.setESP(regs.getESP() - 4);
            int addr = resolveSegOfs(regs.ss, regs.getESP());
            memory.writeByte(addr, value & 0xFF);
            memory.writeByte(addr + 1, (value >> 8) & 0xFF);
            memory.writeByte(addr + 2, (value >> 16) & 0xFF);
            memory.writeByte(addr + 3, (value >> 24) & 0xFF);
        } else {
            regs.setSP(regs.getSP() - 4);
            int addr = resolveSegOfs(regs.ss, regs.getSP());
            memory.writeByte(addr, value & 0xFF);
            memory.writeByte(addr + 1, (value >> 8) & 0xFF);
            memory.writeByte(addr + 2, (value >> 16) & 0xFF);
            memory.writeByte(addr + 3, (value >> 24) & 0xFF);
        }
    }

    /** Pop a dword from the stack (32-bit). */
    private int pop32() {
        int addr;
        if (csIs32) {
            addr = resolveSegOfs(regs.ss, regs.getESP());
            int val = memory.readByte(addr)
                    | (memory.readByte(addr + 1) << 8)
                    | (memory.readByte(addr + 2) << 16)
                    | (memory.readByte(addr + 3) << 24);
            regs.setESP(regs.getESP() + 4);
            return val;
        } else {
            addr = resolveSegOfs(regs.ss, regs.getSP());
            int val = memory.readByte(addr)
                    | (memory.readByte(addr + 1) << 8)
                    | (memory.readByte(addr + 2) << 16)
                    | (memory.readByte(addr + 3) << 24);
            regs.setSP(regs.getSP() + 4);
            return val;
        }
    }

    /** Push word or dword based on prefix66. */
    private void pushOp(int value) {
        if (prefix66) push32(value); else push(value);
    }

    /** Pop word or dword based on prefix66. */
    private int popOp() {
        return prefix66 ? pop32() : pop();
    }

    // ── INT instruction ─────────────────────────────────────

    /** Software interrupt. Tries Java handler first, then IVT/DPMI vectors. */
    public void softwareInt(int vector) {
        IntHandler handler = intHandlers[vector & 0xFF];
        if (handler != null) {
            handler.handle(this);
            return;
        }

        if (dpmi != null && dpmi.isDpmiActive()) {
            // DPMI active: check if DPMI has a PM vector registered
            int pmSel = dpmi.getPMIntVector_Sel(vector);
            int pmOfs = dpmi.getPMIntVector_Ofs(vector);
            if (pmSel != 0) {
                // Jump to protected mode handler
                if (csIs32) {
                    pushOp(regs.flags.getDWord());
                    pushOp(regs.cs);
                    pushOp(regs.getEIP());
                } else {
                    pushOp(regs.flags.getWord());
                    pushOp(regs.cs);
                    pushOp(regs.getIP());
                }
                regs.flags.setIF(false);
                regs.flags.setTF(false);
                regs.cs = pmSel;
                setEffIP(pmOfs);
                return;
            }
            // No PM vector — reflect to real mode via saved IVT
            // Hardware interrupts (and unhandled software INTs) must be
            // reflected to the real-mode handler that was active before PM entry.
            // For hardware IRQs, always provide a default handler (timer tick, EOI)
            // even if the saved RM vector is 0:0, to prevent crashes.
            boolean isHardwareIRQ = (vector >= 0x08 && vector <= 0x0F) || (vector >= 0x70 && vector <= 0x77);

            if (isHardwareIRQ) {
                // Handle hardware IRQs inline (timer tick + EOI)
                if (vector == 0x08) {
                    // Timer tick: increment BIOS tick count at 0040:006C
                    int tickAddr = Memory.segOfs(0x0040, 0x006C);
                    long ticks = memory.readDWord(tickAddr) & 0xFFFFFFFFL;
                    ticks++;
                    memory.writeDWord(tickAddr, (int) ticks);
                }
                // Send EOI for all hardware IRQs
                if (pic != null) {
                    if (vector >= 0x08 && vector <= 0x0F) {
                        pic.lowerIRQ(vector - 0x08);
                    } else {
                        pic.lowerIRQ(vector - 0x70 + 8);
                    }
                }
                return;
            }

            int rmSeg = dpmi.getRMIntVector_Seg(vector);
            int rmOfs = dpmi.getRMIntVector_Ofs(vector);
            if (rmSeg != 0 || rmOfs != 0) {
                // Non-hardware interrupt with an RM vector: silently handle
                // (We cannot truly switch to RM and execute, so just return)
                return;
            }
            System.err.printf("[DPMI] Unhandled PM INT %02X at %04X:%04X (no RM vector either)%n",
                    vector, regs.cs, regs.getIP());
            return;
        }

        // Real mode: Fall back to IVT (Interrupt Vector Table at 0000:0000)
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

    /** Decode ModR/M based on current address size. */
    private int decodeModRM(int modrm) {
        return prefix67 ? decodeModRM32(modrm) : decodeModRM16(modrm);
    }

    /** Decode ModR/M byte and return effective address (16-bit addressing). */
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
                    return resolveSegOfs(getDS(), addr);
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

        return resolveSegOfs(seg, addr);
    }

    /** Decode ModR/M byte with 32-bit addressing (SIB support). */
    private int decodeModRM32(int modrm) {
        int mod = (modrm >> 6) & 3;
        int rm = modrm & 7;

        if (mod == 3) return -1; // register mode

        int addr;
        boolean useSS = false;

        if (rm == 4) {
            // SIB byte follows
            int sib = fetchByte();
            int scale = (sib >> 6) & 3;
            int index = (sib >> 3) & 7;
            int base = sib & 7;

            // Base
            if (base == 5 && mod == 0) {
                addr = fetchDWord();
            } else {
                addr = regs.getReg32(base);
                if (base == 4 || base == 5) useSS = true;
            }

            // Index (index=4 means no index, ESP can't be index)
            if (index != 4) {
                addr += regs.getReg32(index) << scale;
            }
        } else if (rm == 5 && mod == 0) {
            // disp32
            addr = fetchDWord();
        } else {
            addr = regs.getReg32(rm);
            if (rm == 4 || rm == 5) useSS = true;
        }

        // Displacement
        if (mod == 1) {
            addr += fetchSignedByte();
        } else if (mod == 2) {
            addr += fetchDWord();
        }

        // Segment
        int seg = (segOverride >= 0) ? segOverride : (useSS ? regs.ss : regs.ds);

        // Use resolveSegOfs for consistent PM and RM address resolution
        return resolveSegOfs(seg, addr);
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
            case 7: // CMP
                result = a - b;
                regs.flags.setCF((result & 0x100) != 0);
                regs.flags.setOF(((a ^ b) & (a ^ result) & 0x80) != 0);
                regs.flags.setAF(((a ^ b ^ result) & 0x10) != 0);
                regs.flags.setSZP8(result & 0xFF);
                return a;
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

    private int alu32(int op, int a, int b) {
        long la = a & 0xFFFFFFFFL;
        long lb = b & 0xFFFFFFFFL;
        long result;
        switch (op) {
            case 0: // ADD
                result = la + lb;
                regs.flags.setCF((result & 0x100000000L) != 0);
                regs.flags.setOF(((a ^ (int)result) & (b ^ (int)result) & 0x80000000) != 0);
                regs.flags.setAF(((a ^ b ^ (int)result) & 0x10) != 0);
                regs.flags.setSZP32((int) result);
                return (int) result;
            case 1: // OR
                result = la | lb;
                regs.flags.setCF(false); regs.flags.setOF(false);
                regs.flags.setSZP32((int) result);
                return (int) result;
            case 2: { // ADC
                int carry = regs.flags.getCF() ? 1 : 0;
                result = la + lb + carry;
                regs.flags.setCF((result & 0x100000000L) != 0);
                regs.flags.setOF(((a ^ (int)result) & (b ^ (int)result) & 0x80000000) != 0);
                regs.flags.setAF(((a ^ b ^ (int)result) & 0x10) != 0);
                regs.flags.setSZP32((int) result);
                return (int) result;
            }
            case 3: { // SBB
                int carry = regs.flags.getCF() ? 1 : 0;
                result = la - lb - carry;
                regs.flags.setCF((result & 0x100000000L) != 0);
                regs.flags.setOF(((a ^ b) & (a ^ (int)result) & 0x80000000) != 0);
                regs.flags.setAF(((a ^ b ^ (int)result) & 0x10) != 0);
                regs.flags.setSZP32((int) result);
                return (int) result;
            }
            case 4: // AND
                result = la & lb;
                regs.flags.setCF(false); regs.flags.setOF(false);
                regs.flags.setSZP32((int) result);
                return (int) result;
            case 5: // SUB
                result = la - lb;
                regs.flags.setCF((result & 0x100000000L) != 0);
                regs.flags.setOF(((a ^ b) & (a ^ (int)result) & 0x80000000) != 0);
                regs.flags.setAF(((a ^ b ^ (int)result) & 0x10) != 0);
                regs.flags.setSZP32((int) result);
                return (int) result;
            case 6: // XOR
                result = la ^ lb;
                regs.flags.setCF(false); regs.flags.setOF(false);
                regs.flags.setSZP32((int) result);
                return (int) result;
            case 7: // CMP
                result = la - lb;
                regs.flags.setCF((result & 0x100000000L) != 0);
                regs.flags.setOF(((a ^ b) & (a ^ (int)result) & 0x80000000) != 0);
                regs.flags.setAF(((a ^ b ^ (int)result) & 0x10) != 0);
                regs.flags.setSZP32((int)(result));
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
    public CpuTrace getTrace() { return trace; }

    private int consecutiveNullOps = 0;
    private static final int NULL_OP_ABORT_THRESHOLD = 200;
    private int nullRegionOps = 0;
    private static final int NULL_REGION_ABORT_THRESHOLD = 500;

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
            // In protected mode (or DPMI active), check CS descriptor's D-bit for default operand/address size.
            // When DPMI is active, always check even if PE is temporarily cleared (descriptor cache simulation).
            csIs32 = false;
            if (dpmi != null && (dpmi.isDpmiActive() || isProtectedMode())) {
                csIs32 = dpmi.is32BitSelector(regs.cs);
            }
            prefix66 = csIs32;
            prefix67 = csIs32;

            // Null-byte detection: detect execution in zeroed memory regions
            int linearAddr = resolveSegOfs(regs.cs, getEffIP());
            int peekOp = memory.readByte(linearAddr);
            if (peekOp == 0x00) {
                nullRegionOps++;
            }
            // Every 600 instructions, check if most were null ops
            if (totalCycles % 600 == 599) {
                if (nullRegionOps >= NULL_REGION_ABORT_THRESHOLD) {
                    System.err.printf("[CPU] ABORT: %d of last 600 instructions were null bytes (ADD [EAX],AL) at %04X:%08X [linear %08X]%n",
                            nullRegionOps, regs.cs, getEffIP(), linearAddr);
                    System.err.printf("[CPU] Cycle %d, Registers: EAX=%08X EBX=%08X ECX=%08X EDX=%08X ESI=%08X EDI=%08X%n",
                            totalCycles, regs.getEAX(), regs.getEBX(), regs.getECX(), regs.getEDX(), regs.getESI(), regs.getEDI());
                    System.err.printf("[CPU] ESP=%08X EBP=%08X CS=%04X DS=%04X ES=%04X SS=%04X FS=%04X GS=%04X%n",
                            regs.getESP(), regs.getEBP(), regs.cs, regs.ds, regs.es, regs.ss, regs.fs, regs.gs);
                    System.err.printf("[CPU] CR0=%08X GDTR base=%08X limit=%04X csIs32=%b%n",
                            cr0, gdtr_base, gdtr_limit, csIs32);
                    if (dpmi != null) {
                        System.err.printf("[CPU] DPMI active=%b, DPMI GDTR base=%08X limit=%04X%n",
                                dpmi.isDpmiActive(), dpmi.getGdtrBase(), dpmi.getGdtrLimit());
                        boolean isLdt = dpmi.isLDTSelector(regs.cs);
                        int csIdx = dpmi.selectorToIndex(regs.cs);
                        System.err.printf("[CPU] CS=%04X is %s index=%d%n", regs.cs, isLdt ? "LDT" : "GDT", csIdx);
                        if (isLdt) {
                            DPMIManager.LDTEntry entry = dpmi.getEntry(regs.cs);
                            if (entry != null) {
                                System.err.printf("[CPU] LDT[%d]: base=%08X limit=%05X present=%b is32=%b pageGranular=%b access=%04X%n",
                                        csIdx, entry.base, entry.limit, entry.present, entry.is32Bit, entry.pageGranular, entry.accessRights);
                            }
                        }
                    }
                    System.err.printf("[CPU] Memory at linear %08X:", linearAddr & ~0xF);
                    for (int d = 0; d < 48; d++) {
                        if (d % 16 == 0) System.err.printf("%n  %08X:", (linearAddr & ~0xF) + d);
                        System.err.printf(" %02X", memory.readByte((linearAddr & ~0xF) + d));
                    }
                    System.err.println();
                    running = false;
                    break;
                }
                nullRegionOps = 0;
            }

            int prevCS = regs.cs;
            executeOne();

            // ── CS change detection ──
            if (regs.cs != prevCS) {
                csChangeLogCount++;
                if (csChangeLogCount <= 50) {
                    System.out.printf("[CPU] CS changed: %04X → %04X at cycle %d (EIP=%08X)%n",
                            prevCS, regs.cs, totalCycles, regs.getEIP());
                }
                // Validate new CS in protected mode
                if (isProtectedMode() && dpmi != null) {
                    int sel = regs.cs;
                    if (!dpmi.isLDTSelector(sel)) {
                        // GDT selector — check bounds
                        int idx = dpmi.selectorToIndex(sel);
                        int maxIdx = dpmi.getGdtrLimit() / 8;
                        if (idx > 0 && idx > maxIdx) {
                            System.out.printf("[CPU] *** INVALID CS: GDT selector %04X index=%d exceeds GDT limit %04X (max index=%d) ***%n",
                                    sel, idx, dpmi.getGdtrLimit(), maxIdx);
                            System.out.printf("[CPU] Previous CS=%04X, GDTR base=%08X limit=%04X%n",
                                    prevCS, dpmi.getGdtrBase(), dpmi.getGdtrLimit());
                            // Dump trace and stop
                            running = false;
                        }
                    }
                }
            }

            cycles++;
            totalCycles++;
        }
    }

    /**
     * Execute a single instruction with proper prefix initialization.
     * Public for use by real-mode simulation (INT 31h/0301).
     */
    public void executeOnePublic() {
        segOverride = -1;
        repPrefix = false;
        csIs32 = false;
        if (dpmi != null && (dpmi.isDpmiActive() || isProtectedMode())) {
            csIs32 = dpmi.is32BitSelector(regs.cs);
        }
        prefix66 = csIs32;
        prefix67 = csIs32;
        executeOne();
    }

    /** Execute a single instruction. */
    private void executeOne() {
        // Save pre-fetch IP for trace
        int preIP = getEffIP();
        int preCS = regs.cs;

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
                case 0x66: prefix66 = !prefix66; opcode = fetchByte(); continue;       // operand size toggle
                case 0x67: prefix67 = !prefix67; opcode = fetchByte(); continue;       // address size toggle
                case 0xF0: opcode = fetchByte(); continue;                        // LOCK (ignored)
                case 0xF2: repPrefix = true; repNE = true; opcode = fetchByte(); continue;
                case 0xF3: repPrefix = true; repNE = false; opcode = fetchByte(); continue;
            }
            break;
        }

        // Record trace entry (with actual opcode after prefix stripping)
        if (trace.isEnabled()) {
            int savedIP = getEffIP();
            int savedCS = regs.cs;
            // For 0F xx opcodes, peek at the op2 byte (next byte after 0F)
            int op2ForTrace = 0;
            if (opcode == 0x0F) {
                op2ForTrace = memory.readByte(resolveSegOfs(savedCS, savedIP));
            }
            // Temporarily set IP back to pre-fetch position for trace
            regs.cs = preCS;
            setEffIP(preIP);
            trace.record(this, opcode, op2ForTrace, totalCycles);
            regs.cs = savedCS;
            setEffIP(savedIP);
        }

        switch (opcode) {
            // ── ALU r/m8, reg8 (00,08,10,18,20,28,30,38) ───
            case 0x00: case 0x08: case 0x10: case 0x18:
            case 0x20: case 0x28: case 0x30: case 0x38: {
                int aluOp = (opcode >> 3) & 7;
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int a, b = regs.getReg8(reg);
                if (ea == -1) { a = regs.getReg8(rm); } else { a = memory.readByte(ea); }
                int result = alu8(aluOp, a, b);
                if (aluOp != 7) {
                    if (ea == -1) { regs.setReg8(rm, result); } else { memory.writeByte(ea, result); }
                }
                break;
            }

            // ── ALU reg8, r/m8 (02,0A,12,1A,22,2A,32,3A) ──
            case 0x02: case 0x0A: case 0x12: case 0x1A:
            case 0x22: case 0x2A: case 0x32: case 0x3A: {
                int aluOp = (opcode >> 3) & 7;
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int a = regs.getReg8(reg);
                int b;
                if (ea == -1) { b = regs.getReg8(rm); } else { b = memory.readByte(ea); }
                int result = alu8(aluOp, a, b);
                if (aluOp != 7) regs.setReg8(reg, result);
                break;
            }

            // ── ALU r/m16(32), reg16(32) (01,09,11,19,21,29,31,39) ──
            case 0x01: case 0x09: case 0x11: case 0x19:
            case 0x21: case 0x29: case 0x31: case 0x39: {
                int aluOp = (opcode >> 3) & 7;
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int a, b = getRegOp(reg);
                if (ea == -1) { a = getRegOp(rm); } else { a = readMemOp(ea); }
                int result = aluOp(aluOp, a, b);
                if (aluOp != 7) {
                    if (ea == -1) { setRegOp(rm, result); } else { writeMemOp(ea, result); }
                }
                break;
            }

            // ── ALU reg16(32), r/m16(32) (03,0B,13,1B,23,2B,33,3B) ──
            case 0x03: case 0x0B: case 0x13: case 0x1B:
            case 0x23: case 0x2B: case 0x33: case 0x3B: {
                int aluOp = (opcode >> 3) & 7;
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int a = getRegOp(reg);
                int b;
                if (ea == -1) { b = getRegOp(rm); } else { b = readMemOp(ea); }
                int result = aluOp(aluOp, a, b);
                if (aluOp != 7) setRegOp(reg, result);
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

            // ── ALU AX(EAX), imm16(32) (05,0D,15,1D,25,2D,35,3D) ────
            case 0x05: case 0x0D: case 0x15: case 0x1D:
            case 0x25: case 0x2D: case 0x35: case 0x3D: {
                int aluOp = (opcode >> 3) & 7;
                int imm = fetchImm();
                int result = aluOp(aluOp, getAccOp(), imm);
                if (aluOp != 7) setAccOp(result);
                break;
            }

            // ── PUSH segment ────────────────────────────────
            case 0x06: pushOp(regs.es); break;
            case 0x0E: pushOp(regs.cs); break;
            case 0x16: pushOp(regs.ss); break;
            case 0x1E: pushOp(regs.ds); break;

            // ── POP segment ─────────────────────────────────
            case 0x07: regs.es = popOp() & 0xFFFF; break;
            case 0x17: regs.ss = popOp() & 0xFFFF; break;
            case 0x1F: regs.ds = popOp() & 0xFFFF; break;

            // ── DAA (27) ────────────────────────────────────
            case 0x27: {
                int al = regs.getAL();
                boolean oldCF = regs.flags.getCF();
                regs.flags.setCF(false);
                if ((al & 0x0F) > 9 || regs.flags.getAF()) {
                    regs.setAL((al + 6) & 0xFF);
                    regs.flags.setCF(oldCF || ((al + 6) > 0xFF));
                    regs.flags.setAF(true);
                } else {
                    regs.flags.setAF(false);
                }
                al = regs.getAL();
                if (al > 0x99 || oldCF) {
                    regs.setAL((al + 0x60) & 0xFF);
                    regs.flags.setCF(true);
                }
                regs.flags.setSZP8(regs.getAL());
                break;
            }

            // ── DAS (2F) ────────────────────────────────────
            case 0x2F: {
                int al = regs.getAL();
                boolean oldCF = regs.flags.getCF();
                regs.flags.setCF(false);
                if ((al & 0x0F) > 9 || regs.flags.getAF()) {
                    regs.setAL((al - 6) & 0xFF);
                    regs.flags.setCF(oldCF || (al < 6));
                    regs.flags.setAF(true);
                } else {
                    regs.flags.setAF(false);
                }
                al = regs.getAL();
                if (al > 0x99 || oldCF) {
                    regs.setAL((al - 0x60) & 0xFF);
                    regs.flags.setCF(true);
                }
                regs.flags.setSZP8(regs.getAL());
                break;
            }

            // ── AAA (37) ────────────────────────────────────
            case 0x37: {
                if ((regs.getAL() & 0x0F) > 9 || regs.flags.getAF()) {
                    regs.setAX((regs.getAX() + 0x106) & 0xFFFF);
                    regs.flags.setAF(true);
                    regs.flags.setCF(true);
                } else {
                    regs.flags.setAF(false);
                    regs.flags.setCF(false);
                }
                regs.setAL(regs.getAL() & 0x0F);
                break;
            }

            // ── AAS (3F) ────────────────────────────────────
            case 0x3F: {
                if ((regs.getAL() & 0x0F) > 9 || regs.flags.getAF()) {
                    regs.setAX((regs.getAX() - 6) & 0xFFFF);
                    regs.setAH((regs.getAH() - 1) & 0xFF);
                    regs.flags.setAF(true);
                    regs.flags.setCF(true);
                } else {
                    regs.flags.setAF(false);
                    regs.flags.setCF(false);
                }
                regs.setAL(regs.getAL() & 0x0F);
                break;
            }

            // ── Two-byte opcode escape (0F) ─────────────────
            case 0x0F: executeTwoByteOpcode(); break;

            // ── INC reg16(32) (40-47) ───────────────────────
            case 0x40: case 0x41: case 0x42: case 0x43:
            case 0x44: case 0x45: case 0x46: case 0x47: {
                int idx = opcode - 0x40;
                int v = getRegOp(idx);
                boolean cf = regs.flags.getCF();
                int result = aluOp(0, v, 1);
                regs.flags.setCF(cf);
                setRegOp(idx, result);
                break;
            }

            // ── DEC reg16(32) (48-4F) ───────────────────────
            case 0x48: case 0x49: case 0x4A: case 0x4B:
            case 0x4C: case 0x4D: case 0x4E: case 0x4F: {
                int idx = opcode - 0x48;
                int v = getRegOp(idx);
                boolean cf = regs.flags.getCF();
                int result = aluOp(5, v, 1);
                regs.flags.setCF(cf);
                setRegOp(idx, result);
                break;
            }

            // ── PUSH reg16(32) (50-57) ──────────────────────
            case 0x50: case 0x51: case 0x52: case 0x53:
            case 0x54: case 0x55: case 0x56: case 0x57:
                pushOp(getRegOp(opcode - 0x50));
                break;

            // ── POP reg16(32) (58-5F) ───────────────────────
            case 0x58: case 0x59: case 0x5A: case 0x5B:
            case 0x5C: case 0x5D: case 0x5E: case 0x5F:
                setRegOp(opcode - 0x58, popOp());
                break;

            // ── PUSHA(D) (60) ────────────────────────────────
            case 0x60: {
                int tmp = getRegOp(4); // SP/ESP
                pushOp(getRegOp(0)); // AX/EAX
                pushOp(getRegOp(1)); // CX/ECX
                pushOp(getRegOp(2)); // DX/EDX
                pushOp(getRegOp(3)); // BX/EBX
                pushOp(tmp);
                pushOp(getRegOp(5)); // BP/EBP
                pushOp(getRegOp(6)); // SI/ESI
                pushOp(getRegOp(7)); // DI/EDI
                break;
            }

            // ── POPA(D) (61) ────────────────────────────────
            case 0x61: {
                setRegOp(7, popOp()); // DI/EDI
                setRegOp(6, popOp()); // SI/ESI
                setRegOp(5, popOp()); // BP/EBP
                popOp(); // skip SP
                setRegOp(3, popOp()); // BX/EBX
                setRegOp(2, popOp()); // DX/EDX
                setRegOp(1, popOp()); // CX/ECX
                setRegOp(0, popOp()); // AX/EAX
                break;
            }

            // ── BOUND (62) — stub ───────────────────────────
            case 0x62: {
                int modrm = fetchByte();
                decodeModRM(modrm);
                break;
            }

            // ── ARPL (63) — Adjust RPL Field of Selector ────
            case 0x63: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int srcRPL = regs.getReg16(reg) & 3;
                int destVal;
                if (ea == -1) {
                    destVal = regs.getReg16(rm);
                } else {
                    destVal = memory.readWord(ea);
                }
                int destRPL = destVal & 3;
                if (destRPL < srcRPL) {
                    destVal = (destVal & 0xFFFC) | srcRPL;
                    if (ea == -1) {
                        regs.setReg16(rm, destVal);
                    } else {
                        memory.writeWord(ea, destVal);
                    }
                    regs.flags.setZF(true);
                } else {
                    regs.flags.setZF(false);
                }
                break;
            }

            // ── PUSH imm16(32) (68) ─────────────────────────
            case 0x68:
                pushOp(fetchImm());
                break;

            // ── IMUL reg, r/m, imm16(32) (69) ──────────────
            case 0x69: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = getRegOp(rm); } else { val = readMemOp(ea); }
                int imm = fetchImm();
                if (prefix66) {
                    long res = (long) val * (long) imm;
                    setRegOp(reg, (int) res);
                    boolean overflow = (res < Integer.MIN_VALUE || res > Integer.MAX_VALUE);
                    regs.flags.setCF(overflow);
                    regs.flags.setOF(overflow);
                } else {
                    if (imm >= 0x8000) imm -= 0x10000;
                    if (val >= 0x8000) val -= 0x10000;
                    int result = val * imm;
                    setRegOp(reg, result & 0xFFFF);
                    boolean overflow = (result < -32768 || result > 32767);
                    regs.flags.setCF(overflow);
                    regs.flags.setOF(overflow);
                }
                break;
            }

            // ── PUSH sign-ext imm8 (6A) ─────────────────────
            case 0x6A:
                pushOp(fetchSignedByte());
                break;

            // ── IMUL reg, r/m, sign-ext imm8 (6B) ──────────
            case 0x6B: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = getRegOp(rm); } else { val = readMemOp(ea); }
                int imm = fetchSignedByte();
                if (prefix66) {
                    long res = (long) val * (long) imm;
                    setRegOp(reg, (int) res);
                    boolean overflow = (res < Integer.MIN_VALUE || res > Integer.MAX_VALUE);
                    regs.flags.setCF(overflow);
                    regs.flags.setOF(overflow);
                } else {
                    if (val >= 0x8000) val -= 0x10000;
                    int result = val * imm;
                    setRegOp(reg, result & 0xFFFF);
                    boolean overflow = (result < -32768 || result > 32767);
                    regs.flags.setCF(overflow);
                    regs.flags.setOF(overflow);
                }
                break;
            }

            // ── INSB (6C) / INSW (6D) — stub ────────────────
            case 0x6C: case 0x6D: break;

            // ── OUTSB (6E) / OUTSW (6F) — stub ──────────────
            case 0x6E: case 0x6F: break;

            // ── Jcc short (70-7F) ───────────────────────────
            case 0x70: case 0x71: case 0x72: case 0x73:
            case 0x74: case 0x75: case 0x76: case 0x77:
            case 0x78: case 0x79: case 0x7A: case 0x7B:
            case 0x7C: case 0x7D: case 0x7E: case 0x7F: {
                int disp = fetchSignedByte();
                if (evalCondition(opcode - 0x70)) {
                    setEffIP(getEffIP() + disp);
                }
                break;
            }

            // ── ALU r/m8, imm8 (80) ────────────────────────
            case 0x80: case 0x82: {
                int modrm = fetchByte();
                int aluOp2 = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int a;
                if (ea == -1) { a = regs.getReg8(rm); } else { a = memory.readByte(ea); }
                int imm = fetchByte();
                int result = alu8(aluOp2, a, imm);
                if (aluOp2 != 7) {
                    if (ea == -1) { regs.setReg8(rm, result); } else { memory.writeByte(ea, result); }
                }
                break;
            }

            // ── ALU r/m16(32), imm16(32) (81) ──────────────
            case 0x81: {
                int modrm = fetchByte();
                int aluOp2 = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int a;
                if (ea == -1) { a = getRegOp(rm); } else { a = readMemOp(ea); }
                int imm = fetchImm();
                int result = aluOp(aluOp2, a, imm);
                if (aluOp2 != 7) {
                    if (ea == -1) { setRegOp(rm, result); } else { writeMemOp(ea, result); }
                }
                break;
            }

            // ── ALU r/m16(32), sign-ext imm8 (83) ──────────
            case 0x83: {
                int modrm = fetchByte();
                int aluOp2 = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int a;
                if (ea == -1) { a = getRegOp(rm); } else { a = readMemOp(ea); }
                int imm = fetchSignedByte();
                if (!prefix66) imm &= 0xFFFF;
                int result = aluOp(aluOp2, a, imm);
                if (aluOp2 != 7) {
                    if (ea == -1) { setRegOp(rm, result); } else { writeMemOp(ea, result); }
                }
                break;
            }

            // ── TEST r/m8, reg8 (84) ────────────────────────
            case 0x84: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int a;
                if (ea == -1) { a = regs.getReg8(rm); } else { a = memory.readByte(ea); }
                alu8(4, a, regs.getReg8(reg));
                break;
            }

            // ── TEST r/m16(32), reg16(32) (85) ──────────────
            case 0x85: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int a;
                if (ea == -1) { a = getRegOp(rm); } else { a = readMemOp(ea); }
                aluOp(4, a, getRegOp(reg));
                break;
            }

            // ── XCHG r/m8, reg8 (86) ───────────────────────
            case 0x86: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int a, b = regs.getReg8(reg);
                if (ea == -1) { a = regs.getReg8(rm); regs.setReg8(rm, b); }
                else { a = memory.readByte(ea); memory.writeByte(ea, b); }
                regs.setReg8(reg, a);
                break;
            }

            // ── XCHG r/m16(32), reg16(32) (87) ─────────────
            case 0x87: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int a, b = getRegOp(reg);
                if (ea == -1) { a = getRegOp(rm); setRegOp(rm, b); }
                else { a = readMemOp(ea); writeMemOp(ea, b); }
                setRegOp(reg, a);
                break;
            }

            // ── MOV r/m8, reg8 (88) ────────────────────────
            case 0x88: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val = regs.getReg8(reg);
                if (ea == -1) { regs.setReg8(rm, val); } else { memory.writeByte(ea, val); }
                break;
            }

            // ── MOV r/m16(32), reg16(32) (89) ──────────────
            case 0x89: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val = getRegOp(reg);
                if (ea == -1) { setRegOp(rm, val); } else { writeMemOp(ea, val); }
                break;
            }

            // ── MOV reg8, r/m8 (8A) ────────────────────────
            case 0x8A: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg8(rm); } else { val = memory.readByte(ea); }
                regs.setReg8(reg, val);
                break;
            }

            // ── MOV reg16(32), r/m16(32) (8B) ──────────────
            case 0x8B: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = getRegOp(rm); } else { val = readMemOp(ea); }
                setRegOp(reg, val);
                break;
            }

            // ── MOV r/m16, Sreg (8C) ───────────────────────
            case 0x8C: {
                int modrm = fetchByte();
                int seg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val = regs.getSeg(seg);
                if (ea == -1) { regs.setReg16(rm, val); } else { memory.writeWord(ea, val); }
                break;
            }

            // ── LEA reg16(32), mem (8D) ─────────────────────
            case 0x8D: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                if (prefix66) {
                    regs.setReg32(reg, ea);
                } else {
                    regs.setReg16(reg, ea & 0xFFFF);
                }
                break;
            }

            // ── MOV Sreg, r/m16 (8E) ───────────────────────
            case 0x8E: {
                int modrm = fetchByte();
                int seg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg16(rm); } else { val = memory.readWord(ea); }

                // MOV CS is undefined on 386+ — ignore (CS is loaded via far JMP/CALL/RET only)
                if (seg == 1) {
                    System.err.printf("[CPU] WARNING: MOV CS,%04X ignored at %04X:%08X cycle %d%n",
                            val, regs.cs, getEffIP(), totalCycles);
                    break;
                }

                // Protected mode: validate selector for data segments (DS=3, ES=0, FS=4, GS=5)
                if (dpmi != null && dpmi.isDpmiActive()) {
                    if ((val & 0xFFFC) == 0) {
                        // Null selector is allowed for data segments — clears the register
                        regs.setSeg(seg, 0);
                    } else {
                        // Check if the selector points to a valid descriptor
                        boolean valid = false;
                        if (dpmi.isLDTSelector(val)) {
                            int idx = dpmi.selectorToIndex(val);
                            DPMIManager.LDTEntry e = dpmi.getEntry(val);
                            if (e != null && e.present) valid = true;
                        } else {
                            // GDT selector — check if GDT has this entry
                            int idx = dpmi.selectorToIndex(val);
                            DPMIManager.LDTEntry e = dpmi.getEntry(val);
                            if (e != null && e.present) valid = true;
                        }
                        if (valid) {
                            regs.setSeg(seg, val);
                        } else {
                            // Invalid selector — likely a real-mode segment value used in PM.
                            // DOS/4GW's #GP handler patches these by auto-mapping the real-mode
                            // segment to a PM data selector. We do this directly to avoid the
                            // complexity of a proper DPMI exception frame.
                            // Treat the value as a real-mode segment and create a mapping.
                            int mappedSel = dpmi.autoMapRealModeDS(val);
                            if (mappedSel >= 0) {
                                regs.setSeg(seg, mappedSel);
                            } else {
                                // Could not allocate — load null selector
                                regs.setSeg(seg, 0);
                            }
                        }
                    }
                } else {
                    regs.setSeg(seg, val);
                }
                break;
            }

            // ── POP r/m16(32) (8F) ──────────────────────────
            case 0x8F: {
                int modrm = fetchByte();
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val = popOp();
                if (ea == -1) { setRegOp(rm, val); } else { writeMemOp(ea, val); }
                break;
            }

            // ── NOP / XCHG AX(EAX),reg (90-97) ─────────────
            case 0x90: break; // NOP
            case 0x91: case 0x92: case 0x93:
            case 0x94: case 0x95: case 0x96: case 0x97: {
                int idx = opcode - 0x90;
                int tmp = getAccOp();
                setAccOp(getRegOp(idx));
                setRegOp(idx, tmp);
                break;
            }

            // ── CBW/CWDE (98) ───────────────────────────────
            case 0x98:
                if (prefix66) {
                    int ax = regs.getAX();
                    regs.setEAX((ax & 0x8000) != 0 ? ax | 0xFFFF0000 : ax);
                } else {
                    regs.setAX((regs.getAL() & 0x80) != 0 ? regs.getAL() | 0xFF00 : regs.getAL());
                }
                break;

            // ── CWD/CDQ (99) ────────────────────────────────
            case 0x99:
                if (prefix66) {
                    regs.setEDX((regs.getEAX() & 0x80000000) != 0 ? 0xFFFFFFFF : 0);
                } else {
                    regs.setDX((regs.getAX() & 0x8000) != 0 ? 0xFFFF : 0);
                }
                break;

            // ── CALL far (9A) ──────────────────────────────
            case 0x9A: {
                int newIP = prefix66 ? fetchDWord() : fetchWord();
                int newCS = fetchWord();
                pushOp(regs.cs);
                pushOp(getEffIP());
                regs.cs = newCS;
                setEffIP(newIP);
                if (dpmi != null && dpmi.isDpmiActive()) {
                    fixupCSChange("CALL FAR", regs.cs, getEffIP());
                }
                break;
            }

            // ── WAIT/FWAIT (9B) ──────────────────────────────
            case 0x9B: break; // NOP — FPU is synchronous in our emulation

            // ── PUSHF(D) (9C) ───────────────────────────────
            case 0x9C:
                if (prefix66) push32(regs.flags.getDWord());
                else push(regs.flags.getWord());
                break;

            // ── POPF(D) (9D) ────────────────────────────────
            case 0x9D:
                if (prefix66) regs.flags.setDWord(pop32());
                else regs.flags.setWord(pop());
                break;

            // ── SAHF (9E) / LAHF (9F) ──────────────────────
            case 0x9E: regs.flags.setWord((regs.flags.getWord() & 0xFF00) | regs.getAH()); break;
            case 0x9F: regs.setAH(regs.flags.getWord() & 0xFF); break;

            // ── MOV AL/AX(EAX), moffs (A0-A1) ──────────────
            case 0xA0: {
                int addr = prefix67 ? fetchDWord() : fetchWord();
                regs.setAL(memory.readByte(resolveSegOfs(getDS(), addr)));
                break;
            }
            case 0xA1: {
                int addr = prefix67 ? fetchDWord() : fetchWord();
                setAccOp(readMemOp(resolveSegOfs(getDS(), addr)));
                break;
            }

            // ── MOV moffs, AL/AX(EAX) (A2-A3) ──────────────
            case 0xA2: {
                int addr = prefix67 ? fetchDWord() : fetchWord();
                memory.writeByte(resolveSegOfs(getDS(), addr), regs.getAL());
                break;
            }
            case 0xA3: {
                int addr = prefix67 ? fetchDWord() : fetchWord();
                writeMemOp(resolveSegOfs(getDS(), addr), getAccOp());
                break;
            }

            // ── MOVSB (A4) ─────────────────────────────────
            case 0xA4: execMovsb(); break;

            // ── MOVSW/D (A5) ────────────────────────────────
            case 0xA5:
                if (prefix66) execMovsd(); else execMovsw();
                break;

            // ── CMPSB (A6) ─────────────────────────────────
            case 0xA6: execCmpsb(); break;

            // ── CMPSW/D (A7) ────────────────────────────────
            case 0xA7:
                if (prefix66) execCmpsd(); else execCmpsw();
                break;

            // ── TEST AL, imm8 (A8) ─────────────────────────
            case 0xA8: alu8(4, regs.getAL(), fetchByte()); break;

            // ── TEST AX(EAX), imm16(32) (A9) ───────────────
            case 0xA9: aluOp(4, getAccOp(), fetchImm()); break;

            // ── STOSB (AA) ─────────────────────────────────
            case 0xAA: execStosb(); break;

            // ── STOSW/D (AB) ────────────────────────────────
            case 0xAB:
                if (prefix66) execStosd(); else execStosw();
                break;

            // ── LODSB (AC) ─────────────────────────────────
            case 0xAC: execLodsb(); break;

            // ── LODSW/D (AD) ────────────────────────────────
            case 0xAD:
                if (prefix66) execLodsd(); else execLodsw();
                break;

            // ── SCASB (AE) ─────────────────────────────────
            case 0xAE: execScasb(); break;

            // ── SCASW/D (AF) ────────────────────────────────
            case 0xAF:
                if (prefix66) execScasd(); else execScasw();
                break;

            // ── MOV reg8, imm8 (B0-B7) ─────────────────────
            case 0xB0: case 0xB1: case 0xB2: case 0xB3:
            case 0xB4: case 0xB5: case 0xB6: case 0xB7:
                regs.setReg8(opcode - 0xB0, fetchByte());
                break;

            // ── MOV reg16(32), imm16(32) (B8-BF) ───────────
            case 0xB8: case 0xB9: case 0xBA: case 0xBB:
            case 0xBC: case 0xBD: case 0xBE: case 0xBF:
                setRegOp(opcode - 0xB8, fetchImm());
                break;

            // ── Shift/Rotate r/m8, imm8 (C0) ───────────────
            case 0xC0: {
                int modrm = fetchByte();
                int op = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
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

            // ── Shift/Rotate r/m16(32), imm8 (C1) ──────────
            case 0xC1: {
                int modrm = fetchByte();
                int op = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = getRegOp(rm); } else { val = readMemOp(ea); }
                int cnt = fetchByte() & 0x1F;
                if (cnt != 0) {
                    val = prefix66 ? doShift32(op, val, cnt) : doShift16(op, val, cnt);
                    if (ea == -1) { setRegOp(rm, val); } else { writeMemOp(ea, val); }
                }
                break;
            }

            // ── RET near imm16 (C2) ────────────────────────
            case 0xC2: {
                int n = fetchWord();
                setEffIP(popOp());
                if (csIs32) regs.setESP(regs.getESP() + n);
                else regs.setSP(regs.getSP() + n);
                break;
            }

            // ── RET near (C3) ──────────────────────────────
            case 0xC3: setEffIP(popOp()); break;

            // ── LES (C4) / LDS (C5) ────────────────────────
            case 0xC4: case 0xC5: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                if (prefix66) {
                    setRegOp(reg, readMemOp(ea));
                    if (opcode == 0xC4) regs.es = memory.readWord(ea + 4);
                    else regs.ds = memory.readWord(ea + 4);
                } else {
                    setRegOp(reg, memory.readWord(ea));
                    if (opcode == 0xC4) regs.es = memory.readWord(ea + 2);
                    else regs.ds = memory.readWord(ea + 2);
                }
                break;
            }

            // ── MOV r/m8, imm8 (C6) ────────────────────────
            case 0xC6: {
                int modrm = fetchByte();
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int imm = fetchByte();
                if (ea == -1) { regs.setReg8(rm, imm); } else { memory.writeByte(ea, imm); }
                break;
            }

            // ── MOV r/m16(32), imm16(32) (C7) ──────────────
            case 0xC7: {
                int modrm = fetchByte();
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int imm = fetchImm();
                if (ea == -1) { setRegOp(rm, imm); } else { writeMemOp(ea, imm); }
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
                        push(memory.readWord(resolveSegOfs(regs.ss, regs.getBP())));
                    }
                    push(framePtr);
                }
                regs.setBP(framePtr);
                regs.setSP(regs.getSP() - allocSize);
                break;
            }

            // ── LEAVE (C9) ─────────────────────────────────
            case 0xC9:
                if (csIs32) {
                    regs.setESP(regs.getEBP());
                    regs.setEBP(pop32());
                } else {
                    regs.setSP(regs.getBP());
                    regs.setBP(pop());
                }
                break;

            // ── RETF imm16 (CA) ────────────────────────────
            case 0xCA: {
                int n = fetchWord();
                int retIP = popOp();
                int retCS = popOp() & 0xFFFF;
                setEffIP(retIP);
                regs.cs = retCS;
                if (dpmi != null && dpmi.isDpmiActive()) {
                    fixupCSChange("RETF", retCS, retIP);
                }
                if (csIs32) regs.setESP(regs.getESP() + n);
                else regs.setSP(regs.getSP() + n);
                break;
            }

            // ── RETF (CB) ──────────────────────────────────
            case 0xCB: {
                int retIP = popOp();
                int retCS = popOp() & 0xFFFF;
                setEffIP(retIP);
                regs.cs = retCS;
                if (isProtectedMode() && dpmi != null && dpmi.isDpmiActive()) {
                    fixupCSChange("RETF", retCS, retIP);
                }
                break;
            }

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
                setEffIP(popOp());
                regs.cs = popOp() & 0xFFFF;
                if (prefix66) regs.flags.setDWord(popOp());
                else regs.flags.setWord(popOp());
                break;

            // ── Shift/Rotate r/m8, 1 (D0) ──────────────────
            case 0xD0: execShift8(1); break;

            // ── Shift/Rotate r/m16(32), 1 (D1) ─────────────
            case 0xD1: execShiftOp(1); break;

            // ── Shift/Rotate r/m8, CL (D2) ─────────────────
            case 0xD2: execShift8(regs.getCL()); break;

            // ── Shift/Rotate r/m16(32), CL (D3) ────────────
            case 0xD3: execShiftOp(regs.getCL()); break;

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

            // ── SALC (D6) — undocumented: Set AL from Carry ──
            case 0xD6:
                regs.setAL(regs.flags.getCF() ? 0xFF : 0x00);
                break;

            // ── XLAT (D7) ──────────────────────────────────
            case 0xD7:
                regs.setAL(memory.readByte(resolveSegOfs(getDS(), (regs.getBX() + regs.getAL()) & 0xFFFF)));
                break;

            // ── FPU escape opcodes (D8-DF) ──────────────────
            case 0xD8: case 0xD9: case 0xDA: case 0xDB:
            case 0xDC: case 0xDD: case 0xDE: case 0xDF: {
                int modrm = fetchByte();
                int ea = decodeModRM(modrm);
                // Special case: DF E0 = FNSTSW AX
                if (opcode == 0xDF && (modrm >> 6) == 3 && ((modrm >> 3) & 7) == 4 && (modrm & 7) == 0) {
                    regs.setAX(fpu.getStatusWord());
                } else {
                    fpu.execute(opcode, modrm, ea, memory);
                }
                break;
            }

            // ── LOOPNZ (E0) ────────────────────────────────
            case 0xE0: {
                int disp = fetchSignedByte();
                int cnt;
                if (prefix67) {
                    regs.setECX(regs.getECX() - 1);
                    cnt = regs.getECX();
                } else {
                    regs.setCX(regs.getCX() - 1);
                    cnt = regs.getCX();
                }
                if (cnt != 0 && !regs.flags.getZF()) setEffIP(getEffIP() + disp);
                break;
            }

            // ── LOOPZ (E1) ─────────────────────────────────
            case 0xE1: {
                int disp = fetchSignedByte();
                int cnt;
                if (prefix67) {
                    regs.setECX(regs.getECX() - 1);
                    cnt = regs.getECX();
                } else {
                    regs.setCX(regs.getCX() - 1);
                    cnt = regs.getCX();
                }
                if (cnt != 0 && regs.flags.getZF()) setEffIP(getEffIP() + disp);
                break;
            }

            // ── LOOP (E2) ──────────────────────────────────
            case 0xE2: {
                int disp = fetchSignedByte();
                int cnt;
                if (prefix67) {
                    regs.setECX(regs.getECX() - 1);
                    cnt = regs.getECX();
                } else {
                    regs.setCX(regs.getCX() - 1);
                    cnt = regs.getCX();
                }
                if (cnt != 0) setEffIP(getEffIP() + disp);
                break;
            }

            // ── JCXZ/JECXZ (E3) ────────────────────────────
            case 0xE3: {
                int disp = fetchSignedByte();
                int counter = prefix67 ? regs.getECX() : regs.getCX();
                if (counter == 0) setEffIP(getEffIP() + disp);
                break;
            }

            // ── IN AL, imm8 (E4) ───────────────────────────
            case 0xE4: regs.setAL(io.readByte(fetchByte())); break;

            // ── IN AX(EAX), imm8 (E5) ──────────────────────
            case 0xE5: {
                int port = fetchByte();
                if (prefix66) regs.setEAX(io.readDWord(port));
                else regs.setAX(io.readWord(port));
                break;
            }

            // ── OUT imm8, AL (E6) ──────────────────────────
            case 0xE6: io.writeByte(fetchByte(), regs.getAL()); break;

            // ── OUT imm8, AX(EAX) (E7) ─────────────────────
            case 0xE7: {
                int port = fetchByte();
                if (prefix66) io.writeDWord(port, regs.getEAX());
                else io.writeWord(port, regs.getAX());
                break;
            }

            // ── CALL near (E8) ─────────────────────────────
            case 0xE8: {
                int disp;
                if (prefix66) {
                    disp = fetchDWord();
                } else {
                    disp = fetchWord();
                    if (disp >= 0x8000) disp -= 0x10000;
                }
                pushOp(getEffIP());
                setEffIP(getEffIP() + disp);
                break;
            }

            // ── JMP near (E9) ──────────────────────────────
            case 0xE9: {
                int disp;
                if (prefix66) {
                    disp = fetchDWord();
                } else {
                    disp = fetchWord();
                    if (disp >= 0x8000) disp -= 0x10000;
                }
                setEffIP(getEffIP() + disp);
                break;
            }

            // ── JMP far (EA) ───────────────────────────────
            case 0xEA: {
                int newIP = prefix66 ? fetchDWord() : fetchWord();
                int newCS = fetchWord();
                setEffIP(newIP);
                regs.cs = newCS;
                if (dpmi != null && dpmi.isDpmiActive()) {
                    fixupCSChange("JMP FAR", regs.cs, getEffIP());
                }
                break;
            }

            // ── JMP short (EB) ─────────────────────────────
            case 0xEB: {
                int disp = fetchSignedByte();
                setEffIP(getEffIP() + disp);
                break;
            }

            // ── IN AL, DX (EC) ─────────────────────────────
            case 0xEC: regs.setAL(io.readByte(regs.getDX())); break;

            // ── IN AX(EAX), DX (ED) ────────────────────────
            case 0xED:
                if (prefix66) regs.setEAX(io.readDWord(regs.getDX()));
                else regs.setAX(io.readWord(regs.getDX()));
                break;

            // ── OUT DX, AL (EE) ────────────────────────────
            case 0xEE: io.writeByte(regs.getDX(), regs.getAL()); break;

            // ── OUT DX, AX(EAX) (EF) ───────────────────────
            case 0xEF:
                if (prefix66) io.writeDWord(regs.getDX(), regs.getEAX());
                else io.writeWord(regs.getDX(), regs.getAX());
                break;

            // ── HLT (F4) ───────────────────────────────────
            case 0xF4: halted = true; break;

            // ── CMC (F5) ───────────────────────────────────
            case 0xF5: regs.flags.setCF(!regs.flags.getCF()); break;

            // ── GRP3 r/m8 (F6) ─────────────────────────────
            case 0xF6: execGrp3_8(); break;

            // ── GRP3 r/m16(32) (F7) ────────────────────────
            case 0xF7: execGrp3_Op(); break;

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

            // ── GRP5 r/m16(32) (FF) ────────────────────────
            case 0xFF: execGrp5(); break;

            default:
                System.err.printf("Unimplemented opcode: %02X at %04X:%04X (linear=%08X, PM=%s)%n",
                        opcode, regs.cs, regs.getIP() - 1,
                        resolveSegOfs(regs.cs, regs.getIP() - 1),
                        isProtectedMode() ? "yes" : "no");
                running = false; // stop to prevent garbage execution
                break;
        }
    }

    // ── Two-byte opcode handler (0x0F prefix) ───────────────

    private void executeTwoByteOpcode() {
        int op2 = fetchByte();
        switch (op2) {

            // ── GRP6 (0F 00): SLDT/STR/LLDT/LTR/VERR/VERW ──
            case 0x00: {
                int modrm = fetchByte();
                int subOp = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                switch (subOp) {
                    case 0: // SLDT — Store LDT register (stub: return 0)
                        if (ea == -1) regs.setReg16(rm, 0);
                        else memory.writeWord(ea, 0);
                        break;
                    case 1: // STR — Store Task Register (stub: return 0)
                        if (ea == -1) regs.setReg16(rm, 0);
                        else memory.writeWord(ea, 0);
                        break;
                    case 2: // LLDT — Load LDT (stub, ignored in real mode)
                        break;
                    case 3: // LTR — Load Task Register (stub)
                        break;
                    case 4: // VERR — Verify segment readable
                        regs.flags.setZF(true); // stub: always readable
                        break;
                    case 5: // VERW — Verify segment writable
                        regs.flags.setZF(true); // stub: always writable
                        break;
                    default:
                        System.err.printf("Unimplemented 0F 00 /%d at %04X:%04X%n", subOp, regs.cs, regs.getIP());
                        break;
                }
                break;
            }

            // ── System instructions (0F 01) ──────────────────
            case 0x01: {
                int modrm = fetchByte();
                int subOp = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                switch (subOp) {
                    case 0: // SGDT
                        if (ea != -1) {
                            memory.writeWord(ea, gdtr_limit);
                            memory.writeByte(ea + 2, gdtr_base & 0xFF);
                            memory.writeByte(ea + 3, (gdtr_base >> 8) & 0xFF);
                            memory.writeByte(ea + 4, (gdtr_base >> 16) & 0xFF);
                            memory.writeByte(ea + 5, (gdtr_base >> 24) & 0xFF);
                        }
                        break;
                    case 1: // SIDT
                        if (ea != -1) {
                            memory.writeWord(ea, idtr_limit);
                            memory.writeByte(ea + 2, idtr_base & 0xFF);
                            memory.writeByte(ea + 3, (idtr_base >> 8) & 0xFF);
                            memory.writeByte(ea + 4, (idtr_base >> 16) & 0xFF);
                            memory.writeByte(ea + 5, (idtr_base >> 24) & 0xFF);
                        }
                        break;
                    case 2: // LGDT
                        if (ea != -1) {
                            int newLimit = memory.readWord(ea);
                            int newBase = memory.readByte(ea + 2)
                                      | (memory.readByte(ea + 3) << 8)
                                      | (memory.readByte(ea + 4) << 16)
                                      | (memory.readByte(ea + 5) << 24);

                            // Virtualize LGDT when DPMI is active:
                            // The DPMI host owns the GDT. We merge the client's GDT entries
                            // into the DPMI-managed GDT instead of letting the client overwrite it.
                            // This must also work when PE is temporarily cleared (e.g., DOS/4GW
                            // doing real-mode transitions via MOV CR0).
                            if (dpmi != null && dpmi.isDpmiActive()) {
                                int dpmiGdtBase = dpmi.getGdtrBase();
                                int dpmiGdtLimit = dpmi.getGdtrLimit();
                                System.out.printf("[CPU] LGDT virtualized in PM: client wants base=%08X limit=%04X, DPMI GDT at %08X limit=%04X%n",
                                        newBase, newLimit, dpmiGdtBase, dpmiGdtLimit);

                                // Copy client's GDT entries into the DPMI-managed GDT
                                // Skip entry 0 (null descriptor). Copy as many entries as fit.
                                int clientEntries = (newLimit + 1) / 8;
                                int dpmiMaxEntries = (dpmiGdtLimit + 1) / 8;

                                // Expand DPMI GDT if needed
                                int neededEntries = Math.max(clientEntries, dpmiMaxEntries);
                                int neededLimit = neededEntries * 8 - 1;
                                if (neededLimit > dpmiGdtLimit) {
                                    dpmi.setGDTR(dpmiGdtBase, neededLimit);
                                    dpmiGdtLimit = neededLimit;
                                }

                                // Copy client entries (skip entry 0 = null descriptor)
                                for (int g = 1; g < clientEntries; g++) {
                                    int srcAddr = newBase + g * 8;
                                    int dstAddr = dpmiGdtBase + g * 8;
                                    for (int b = 0; b < 8; b++) {
                                        memory.writeByte(dstAddr + b, memory.readByte(srcAddr + b));
                                    }
                                }

                                // Update CPU GDTR to point to (possibly expanded) DPMI GDT
                                gdtr_base = dpmiGdtBase;
                                gdtr_limit = dpmiGdtLimit;
                                dpmi.setGDTR(dpmiGdtBase, dpmiGdtLimit);

                                System.out.printf("[CPU] LGDT virtualized: merged %d client entries into DPMI GDT at %08X%n",
                                        clientEntries - 1, dpmiGdtBase);

                                // Dump merged GDT
                                int maxDump = Math.min(10, (dpmiGdtLimit + 1) / 8);
                                for (int g = 0; g < maxDump; g++) {
                                    int gaddr = dpmiGdtBase + g * 8;
                                    int lo = memory.readByte(gaddr) | (memory.readByte(gaddr+1) << 8)
                                           | (memory.readByte(gaddr+2) << 16) | (memory.readByte(gaddr+3) << 24);
                                    int hi = memory.readByte(gaddr+4) | (memory.readByte(gaddr+5) << 8)
                                           | (memory.readByte(gaddr+6) << 16) | (memory.readByte(gaddr+7) << 24);
                                    int baseLo2 = (lo >> 16) & 0xFFFF;
                                    int baseMid2 = (hi) & 0xFF;
                                    int baseHi2 = (hi >> 24) & 0xFF;
                                    int base2 = baseLo2 | (baseMid2 << 16) | (baseHi2 << 24);
                                    int limitLo2 = lo & 0xFFFF;
                                    int limitHi4 = (hi >> 16) & 0x0F;
                                    int limit2 = limitLo2 | (limitHi4 << 16);
                                    int access2 = (hi >> 8) & 0xFF;
                                    int flags2 = (hi >> 20) & 0x0F;
                                    boolean present2 = (access2 & 0x80) != 0;
                                    boolean is32g = (flags2 & 0x04) != 0;
                                    boolean gran2 = (flags2 & 0x08) != 0;
                                    System.out.printf("[CPU]   GDT[%d] sel=%04X: base=%08X limit=%05X%s acc=%02X P=%s D=%s %s%n",
                                            g, g * 8, base2, limit2, gran2 ? "*4K" : "",
                                            access2, present2 ? "Y" : "N", is32g ? "32" : "16",
                                            (access2 & 0x08) != 0 ? "CODE" : "DATA");
                                }
                            } else {
                                // Real mode or no DPMI: accept LGDT directly
                                gdtr_limit = newLimit;
                                gdtr_base = newBase;
                                // Sync with DPMI manager so it can resolve GDT selectors
                                if (dpmi != null) {
                                    dpmi.setGDTR(gdtr_base, gdtr_limit);
                                    System.out.printf("[CPU] LGDT: base=%08X limit=%04X (read from ea=%08X)%n", gdtr_base, gdtr_limit, ea);
                                }
                            }
                        }
                        break;
                    case 3: // LIDT
                        if (ea != -1) {
                            idtr_limit = memory.readWord(ea);
                            idtr_base = memory.readByte(ea + 2)
                                      | (memory.readByte(ea + 3) << 8)
                                      | (memory.readByte(ea + 4) << 16)
                                      | (memory.readByte(ea + 5) << 24);
                        }
                        break;
                    case 4: { // SMSW
                        int rm = modrm & 7;
                        int val = cr0 & 0xFFFF;
                        if (ea == -1) { regs.setReg16(rm, val); }
                        else { memory.writeWord(ea, val); }
                        break;
                    }
                    case 6: { // LMSW
                        int rm = modrm & 7;
                        int val;
                        if (ea == -1) { val = regs.getReg16(rm); }
                        else { val = memory.readWord(ea); }
                        // LMSW can set PE but not clear it
                        int newCr0 = (cr0 & 0xFFFF0000) | (val & 0xFFFF);
                        // PE bit can only be set, not cleared by LMSW
                        if ((cr0 & 1) != 0) newCr0 |= 1;
                        if ((newCr0 & 1) != 0 && (cr0 & 1) == 0) {
                            System.out.printf("[CPU] LMSW: Entering protected mode (CR0=%08X)%n", newCr0);
                        }
                        cr0 = newCr0;
                        break;
                    }
                    case 7: // INVLPG (stub)
                        break;
                    default:
                        System.err.printf("Unimplemented 0F 01 /%d at %04X:%04X%n", subOp, regs.cs, regs.getIP());
                        break;
                }
                break;
            }

            // ── LAR (0F 02) ─────────────────────────────────
            case 0x02: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int sel;
                if (ea == -1) { sel = getRegOp(rm); } else { sel = readMemOp(ea); }
                if (dpmi != null) {
                    DPMIManager.LDTEntry entry = dpmi.getEntry(sel);
                    if (entry != null && entry.present) {
                        // Return access rights in upper bytes
                        setRegOp(reg, (entry.accessRights & 0xFF) << 8);
                        regs.flags.setZF(true);
                    } else {
                        regs.flags.setZF(false);
                    }
                } else {
                    regs.flags.setZF(false);
                }
                break;
            }

            // ── LSL (0F 03) ─────────────────────────────────
            case 0x03: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int sel;
                if (ea == -1) { sel = getRegOp(rm); } else { sel = readMemOp(ea); }
                if (dpmi != null) {
                    DPMIManager.LDTEntry entry = dpmi.getEntry(sel);
                    if (entry != null && entry.present) {
                        long effectiveLimit = entry.getEffectiveLimit();
                        setRegOp(reg, (int) effectiveLimit);
                        regs.flags.setZF(true);
                    } else {
                        regs.flags.setZF(false);
                    }
                } else {
                    regs.flags.setZF(false);
                }
                break;
            }

            // ── CLTS (0F 06) ────────────────────────────────
            case 0x06:
                cr0 &= ~0x08;
                break;

            // ── WBINVD (0F 09) ──────────────────────────────
            case 0x09: break;

            // ── MOV reg32, CRn (0F 20) ──────────────────────
            case 0x20: {
                int modrm = fetchByte();
                int crn = (modrm >> 3) & 7;
                int rm = modrm & 7;
                int val;
                switch (crn) {
                    case 0: val = cr0; break;
                    case 2: val = cr2; break;
                    case 3: val = cr3; break;
                    case 4: val = cr4; break;
                    default: val = 0; break;
                }
                regs.setReg32(rm, val);
                break;
            }

            // ── MOV reg32, DRn (0F 21) — stub ───────────────
            case 0x21: {
                int modrm = fetchByte();
                regs.setReg32(modrm & 7, 0);
                break;
            }

            // ── MOV CRn, reg32 (0F 22) ──────────────────────
            case 0x22: {
                int modrm = fetchByte();
                int crn = (modrm >> 3) & 7;
                int rm = modrm & 7;
                int val = regs.getReg32(rm);
                switch (crn) {
                    case 0:
                        if ((val & 1) != (cr0 & 1)) {
                            System.out.printf("[CPU] MOV CR0: PE %s (CR0=%08X → %08X)%n",
                                    (val & 1) != 0 ? "set" : "cleared", cr0, val);
                        }
                        cr0 = val;
                        break;
                    case 2: cr2 = val; break;
                    case 3: cr3 = val; break;
                    case 4: cr4 = val; break;
                }
                break;
            }

            // ── MOV DRn, reg32 (0F 23) — stub ───────────────
            case 0x23: {
                fetchByte();
                break;
            }

            // ── Jcc near (0F 80 - 0F 8F) ───────────────────
            case 0x80: case 0x81: case 0x82: case 0x83:
            case 0x84: case 0x85: case 0x86: case 0x87:
            case 0x88: case 0x89: case 0x8A: case 0x8B:
            case 0x8C: case 0x8D: case 0x8E: case 0x8F: {
                int disp;
                if (prefix66) {
                    disp = fetchDWord();
                } else {
                    disp = fetchWord();
                    if (disp >= 0x8000) disp -= 0x10000;
                }
                if (evalCondition(op2 - 0x80)) {
                    setEffIP(getEffIP() + disp);
                }
                break;
            }

            // ── SETcc r/m8 (0F 90 - 0F 9F) ─────────────────
            case 0x90: case 0x91: case 0x92: case 0x93:
            case 0x94: case 0x95: case 0x96: case 0x97:
            case 0x98: case 0x99: case 0x9A: case 0x9B:
            case 0x9C: case 0x9D: case 0x9E: case 0x9F: {
                int modrm = fetchByte();
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val = evalCondition(op2 - 0x90) ? 1 : 0;
                if (ea == -1) { regs.setReg8(rm, val); } else { memory.writeByte(ea, val); }
                break;
            }

            // ── PUSH FS (0F A0) / POP FS (0F A1) ───────────
            case 0xA0: pushOp(regs.fs); break;
            case 0xA1: regs.fs = popOp() & 0xFFFF; break;

            // ── CPUID (0F A2) ───────────────────────────────
            case 0xA2: {
                int func = regs.getEAX();
                switch (func) {
                    case 0:
                        regs.setEAX(1);
                        regs.setEBX(0x756E6547); // "Genu"
                        regs.setEDX(0x49656E69); // "ineI"
                        regs.setECX(0x6C65746E); // "ntel"
                        break;
                    case 1:
                        regs.setEAX(0x00000480); // 486DX
                        regs.setEBX(0);
                        regs.setECX(0);
                        regs.setEDX(0x00000001); // FPU
                        break;
                    default:
                        regs.setEAX(0);
                        regs.setEBX(0);
                        regs.setECX(0);
                        regs.setEDX(0);
                        break;
                }
                break;
            }

            // ── BT r/m, reg (0F A3) ────────────────────────
            case 0xA3: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = getRegOp(rm); } else { val = readMemOp(ea); }
                int bitSize = prefix66 ? 32 : 16;
                int bit = getRegOp(reg) & (bitSize - 1);
                regs.flags.setCF(((val >> bit) & 1) != 0);
                break;
            }

            // ── SHLD r/m, reg, imm8 (0F A4) ────────────────
            case 0xA4: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int dst;
                if (ea == -1) { dst = getRegOp(rm); } else { dst = readMemOp(ea); }
                int src = getRegOp(reg);
                int cnt = fetchByte() & 0x1F;
                if (cnt != 0) {
                    int result;
                    if (prefix66) {
                        long ldst = dst & 0xFFFFFFFFL;
                        long lsrc = src & 0xFFFFFFFFL;
                        result = (int)((ldst << cnt) | (lsrc >>> (32 - cnt)));
                        regs.flags.setCF(((dst >> (32 - cnt)) & 1) != 0);
                        regs.flags.setSZP32(result);
                    } else {
                        result = ((dst << cnt) | (src >>> (16 - cnt))) & 0xFFFF;
                        regs.flags.setCF(((dst >> (16 - cnt)) & 1) != 0);
                        regs.flags.setSZP16(result);
                    }
                    if (ea == -1) { setRegOp(rm, result); } else { writeMemOp(ea, result); }
                }
                break;
            }

            // ── SHLD r/m, reg, CL (0F A5) ──────────────────
            case 0xA5: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int dst;
                if (ea == -1) { dst = getRegOp(rm); } else { dst = readMemOp(ea); }
                int src = getRegOp(reg);
                int cnt = regs.getCL() & 0x1F;
                if (cnt != 0) {
                    int result;
                    if (prefix66) {
                        long ldst = dst & 0xFFFFFFFFL;
                        long lsrc = src & 0xFFFFFFFFL;
                        result = (int)((ldst << cnt) | (lsrc >>> (32 - cnt)));
                        regs.flags.setCF(((dst >> (32 - cnt)) & 1) != 0);
                        regs.flags.setSZP32(result);
                    } else {
                        result = ((dst << cnt) | (src >>> (16 - cnt))) & 0xFFFF;
                        regs.flags.setCF(((dst >> (16 - cnt)) & 1) != 0);
                        regs.flags.setSZP16(result);
                    }
                    if (ea == -1) { setRegOp(rm, result); } else { writeMemOp(ea, result); }
                }
                break;
            }

            // ── PUSH GS (0F A8) / POP GS (0F A9) ───────────
            case 0xA8: pushOp(regs.gs); break;
            case 0xA9: regs.gs = popOp() & 0xFFFF; break;

            // ── BTS r/m, reg (0F AB) ────────────────────────
            case 0xAB: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = getRegOp(rm); } else { val = readMemOp(ea); }
                int bitSize = prefix66 ? 32 : 16;
                int bit = getRegOp(reg) & (bitSize - 1);
                regs.flags.setCF(((val >> bit) & 1) != 0);
                val |= (1 << bit);
                if (ea == -1) { setRegOp(rm, val); } else { writeMemOp(ea, val); }
                break;
            }

            // ── SHRD r/m, reg, imm8 (0F AC) ────────────────
            case 0xAC: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int dst;
                if (ea == -1) { dst = getRegOp(rm); } else { dst = readMemOp(ea); }
                int src = getRegOp(reg);
                int cnt = fetchByte() & 0x1F;
                if (cnt != 0) {
                    int result;
                    if (prefix66) {
                        long ldst = dst & 0xFFFFFFFFL;
                        long lsrc = src & 0xFFFFFFFFL;
                        result = (int)((ldst >>> cnt) | (lsrc << (32 - cnt)));
                        regs.flags.setCF(((dst >> (cnt - 1)) & 1) != 0);
                        regs.flags.setSZP32(result);
                    } else {
                        result = ((dst >>> cnt) | (src << (16 - cnt))) & 0xFFFF;
                        regs.flags.setCF(((dst >> (cnt - 1)) & 1) != 0);
                        regs.flags.setSZP16(result);
                    }
                    if (ea == -1) { setRegOp(rm, result); } else { writeMemOp(ea, result); }
                }
                break;
            }

            // ── SHRD r/m, reg, CL (0F AD) ──────────────────
            case 0xAD: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int dst;
                if (ea == -1) { dst = getRegOp(rm); } else { dst = readMemOp(ea); }
                int src = getRegOp(reg);
                int cnt = regs.getCL() & 0x1F;
                if (cnt != 0) {
                    int result;
                    if (prefix66) {
                        long ldst = dst & 0xFFFFFFFFL;
                        long lsrc = src & 0xFFFFFFFFL;
                        result = (int)((ldst >>> cnt) | (lsrc << (32 - cnt)));
                        regs.flags.setCF(((dst >> (cnt - 1)) & 1) != 0);
                        regs.flags.setSZP32(result);
                    } else {
                        result = ((dst >>> cnt) | (src << (16 - cnt))) & 0xFFFF;
                        regs.flags.setCF(((dst >> (cnt - 1)) & 1) != 0);
                        regs.flags.setSZP16(result);
                    }
                    if (ea == -1) { setRegOp(rm, result); } else { writeMemOp(ea, result); }
                }
                break;
            }

            // ── IMUL reg, r/m (0F AF) ──────────────────────
            case 0xAF: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int a = getRegOp(reg);
                int b;
                if (ea == -1) { b = getRegOp(rm); } else { b = readMemOp(ea); }
                if (prefix66) {
                    long res = (long) a * (long) b;
                    setRegOp(reg, (int) res);
                    boolean overflow = (res < Integer.MIN_VALUE || res > Integer.MAX_VALUE);
                    regs.flags.setCF(overflow);
                    regs.flags.setOF(overflow);
                } else {
                    if (a >= 0x8000) a -= 0x10000;
                    if (b >= 0x8000) b -= 0x10000;
                    int result = a * b;
                    setRegOp(reg, result & 0xFFFF);
                    boolean overflow = (result < -32768 || result > 32767);
                    regs.flags.setCF(overflow);
                    regs.flags.setOF(overflow);
                }
                break;
            }

            // ── CMPXCHG r/m8, reg8 (0F B0) ──────────────────
            case 0xB0: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg8(rm); } else { val = memory.readByte(ea); }
                alu8(7, regs.getAL(), val);
                if (regs.flags.getZF()) {
                    if (ea == -1) { regs.setReg8(rm, regs.getReg8(reg)); }
                    else { memory.writeByte(ea, regs.getReg8(reg)); }
                } else {
                    regs.setAL(val);
                }
                break;
            }

            // ── CMPXCHG r/m16(32), reg16(32) (0F B1) ────────
            case 0xB1: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = getRegOp(rm); } else { val = readMemOp(ea); }
                aluOp(7, getAccOp(), val);
                if (regs.flags.getZF()) {
                    if (ea == -1) { setRegOp(rm, getRegOp(reg)); }
                    else { writeMemOp(ea, getRegOp(reg)); }
                } else {
                    setAccOp(val);
                }
                break;
            }

            // ── BTR r/m, reg (0F B3) ────────────────────────
            case 0xB3: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = getRegOp(rm); } else { val = readMemOp(ea); }
                int bitSize = prefix66 ? 32 : 16;
                int bit = getRegOp(reg) & (bitSize - 1);
                regs.flags.setCF(((val >> bit) & 1) != 0);
                val &= ~(1 << bit);
                if (ea == -1) { setRegOp(rm, val); } else { writeMemOp(ea, val); }
                break;
            }

            // ── MOVZX reg, r/m8 (0F B6) ────────────────────
            case 0xB6: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg8(rm); } else { val = memory.readByte(ea); }
                setRegOp(reg, val & 0xFF);
                break;
            }

            // ── MOVZX reg, r/m16 (0F B7) ───────────────────
            case 0xB7: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg16(rm); } else { val = memory.readWord(ea); }
                setRegOp(reg, val & 0xFFFF);
                break;
            }

            // ── BT/BTS/BTR/BTC r/m, imm8 (0F BA) ──────────
            case 0xBA: {
                int modrm = fetchByte();
                int subOp = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = getRegOp(rm); } else { val = readMemOp(ea); }
                int bitSize = prefix66 ? 32 : 16;
                int bit = fetchByte() & (bitSize - 1);
                regs.flags.setCF(((val >> bit) & 1) != 0);
                switch (subOp) {
                    case 4: break;
                    case 5: val |= (1 << bit); break;
                    case 6: val &= ~(1 << bit); break;
                    case 7: val ^= (1 << bit); break;
                }
                if (subOp >= 5) {
                    if (ea == -1) { setRegOp(rm, val); } else { writeMemOp(ea, val); }
                }
                break;
            }

            // ── BTC r/m, reg (0F BB) ────────────────────────
            case 0xBB: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = getRegOp(rm); } else { val = readMemOp(ea); }
                int bitSize = prefix66 ? 32 : 16;
                int bit = getRegOp(reg) & (bitSize - 1);
                regs.flags.setCF(((val >> bit) & 1) != 0);
                val ^= (1 << bit);
                if (ea == -1) { setRegOp(rm, val); } else { writeMemOp(ea, val); }
                break;
            }

            // ── BSF reg, r/m (0F BC) ────────────────────────
            case 0xBC: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = getRegOp(rm); } else { val = readMemOp(ea); }
                if (val == 0) {
                    regs.flags.setZF(true);
                } else {
                    regs.flags.setZF(false);
                    int bit = 0;
                    while (((val >> bit) & 1) == 0) bit++;
                    setRegOp(reg, bit);
                }
                break;
            }

            // ── BSR reg, r/m (0F BD) ────────────────────────
            case 0xBD: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = getRegOp(rm); } else { val = readMemOp(ea); }
                if (val == 0) {
                    regs.flags.setZF(true);
                } else {
                    regs.flags.setZF(false);
                    int maxBit = prefix66 ? 31 : 15;
                    int bit = maxBit;
                    while (bit > 0 && ((val >> bit) & 1) == 0) bit--;
                    setRegOp(reg, bit);
                }
                break;
            }

            // ── MOVSX reg, r/m8 (0F BE) ────────────────────
            case 0xBE: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg8(rm); } else { val = memory.readByte(ea); }
                if ((val & 0x80) != 0) {
                    if (prefix66) val |= 0xFFFFFF00;
                    else val |= 0xFF00;
                }
                setRegOp(reg, val);
                break;
            }

            // ── MOVSX reg, r/m16 (0F BF) ───────────────────
            case 0xBF: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int val;
                if (ea == -1) { val = regs.getReg16(rm); } else { val = memory.readWord(ea); }
                if (prefix66 && (val & 0x8000) != 0) val |= 0xFFFF0000;
                setRegOp(reg, val);
                break;
            }

            // ── XADD r/m8, reg8 (0F C0) ────────────────────
            case 0xC0: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int dst, src = regs.getReg8(reg);
                if (ea == -1) { dst = regs.getReg8(rm); } else { dst = memory.readByte(ea); }
                int sum = alu8(0, dst, src);
                regs.setReg8(reg, dst);
                if (ea == -1) { regs.setReg8(rm, sum); } else { memory.writeByte(ea, sum); }
                break;
            }

            // ── XADD r/m16(32), reg16(32) (0F C1) ──────────
            case 0xC1: {
                int modrm = fetchByte();
                int reg = (modrm >> 3) & 7;
                int ea = decodeModRM(modrm);
                int rm = modrm & 7;
                int dst, src = getRegOp(reg);
                if (ea == -1) { dst = getRegOp(rm); } else { dst = readMemOp(ea); }
                int sum = aluOp(0, dst, src);
                setRegOp(reg, dst);
                if (ea == -1) { setRegOp(rm, sum); } else { writeMemOp(ea, sum); }
                break;
            }

            // ── BSWAP reg32 (0F C8 - 0F CF) ────────────────
            case 0xC8: case 0xC9: case 0xCA: case 0xCB:
            case 0xCC: case 0xCD: case 0xCE: case 0xCF: {
                int idx = op2 - 0xC8;
                int val = regs.getReg32(idx);
                val = ((val & 0xFF) << 24) | ((val & 0xFF00) << 8)
                    | ((val >>> 8) & 0xFF00) | ((val >>> 24) & 0xFF);
                regs.setReg32(idx, val);
                break;
            }

            default:
                System.err.printf("Unimplemented 0F opcode: 0F %02X at %04X:%04X (linear=%08X, PM=%s)%n",
                        op2, regs.cs, regs.getIP() - 2,
                        resolveSegOfs(regs.cs, regs.getIP() - 2),
                        isProtectedMode() ? "yes" : "no");
                running = false; // stop to prevent garbage execution
                break;
        }
    }

    // ── String operations ───────────────────────────────────

    private void execMovsb() {
        int dir = regs.flags.getDF() ? -1 : 1;
        if (repPrefix) {
            while (regs.getCX() != 0) {
                memory.writeByte(resolveSegOfs(regs.es, regs.getDI()),
                        memory.readByte(resolveSegOfs(getDS(), regs.getSI())));
                regs.setSI(regs.getSI() + dir);
                regs.setDI(regs.getDI() + dir);
                regs.setCX(regs.getCX() - 1);
            }
        } else {
            memory.writeByte(resolveSegOfs(regs.es, regs.getDI()),
                    memory.readByte(resolveSegOfs(getDS(), regs.getSI())));
            regs.setSI(regs.getSI() + dir);
            regs.setDI(regs.getDI() + dir);
        }
    }

    private void execMovsw() {
        int dir = regs.flags.getDF() ? -2 : 2;
        if (repPrefix) {
            while (regs.getCX() != 0) {
                memory.writeWord(resolveSegOfs(regs.es, regs.getDI()),
                        memory.readWord(resolveSegOfs(getDS(), regs.getSI())));
                regs.setSI(regs.getSI() + dir);
                regs.setDI(regs.getDI() + dir);
                regs.setCX(regs.getCX() - 1);
            }
        } else {
            memory.writeWord(resolveSegOfs(regs.es, regs.getDI()),
                    memory.readWord(resolveSegOfs(getDS(), regs.getSI())));
            regs.setSI(regs.getSI() + dir);
            regs.setDI(regs.getDI() + dir);
        }
    }

    private void execMovsd() {
        int dir = regs.flags.getDF() ? -4 : 4;
        if (repPrefix) {
            while (regs.getCX() != 0) {
                int sa = resolveSegOfs(getDS(), regs.getSI());
                int da = resolveSegOfs(regs.es, regs.getDI());
                int v = memory.readByte(sa) | (memory.readByte(sa+1)<<8)
                      | (memory.readByte(sa+2)<<16) | (memory.readByte(sa+3)<<24);
                memory.writeByte(da, v & 0xFF);
                memory.writeByte(da+1, (v>>8)&0xFF);
                memory.writeByte(da+2, (v>>16)&0xFF);
                memory.writeByte(da+3, (v>>24)&0xFF);
                regs.setSI(regs.getSI() + dir);
                regs.setDI(regs.getDI() + dir);
                regs.setCX(regs.getCX() - 1);
            }
        } else {
            int sa = resolveSegOfs(getDS(), regs.getSI());
            int da = resolveSegOfs(regs.es, regs.getDI());
            int v = memory.readByte(sa) | (memory.readByte(sa+1)<<8)
                  | (memory.readByte(sa+2)<<16) | (memory.readByte(sa+3)<<24);
            memory.writeByte(da, v & 0xFF);
            memory.writeByte(da+1, (v>>8)&0xFF);
            memory.writeByte(da+2, (v>>16)&0xFF);
            memory.writeByte(da+3, (v>>24)&0xFF);
            regs.setSI(regs.getSI() + dir);
            regs.setDI(regs.getDI() + dir);
        }
    }

    private void execCmpsb() {
        int dir = regs.flags.getDF() ? -1 : 1;
        if (repPrefix) {
            while (regs.getCX() != 0) {
                int a = memory.readByte(resolveSegOfs(getDS(), regs.getSI()));
                int b = memory.readByte(resolveSegOfs(regs.es, regs.getDI()));
                alu8(7, a, b);
                regs.setSI(regs.getSI() + dir);
                regs.setDI(regs.getDI() + dir);
                regs.setCX(regs.getCX() - 1);
                if (repNE && regs.flags.getZF()) break;
                if (!repNE && !regs.flags.getZF()) break;
            }
        } else {
            int a = memory.readByte(resolveSegOfs(getDS(), regs.getSI()));
            int b = memory.readByte(resolveSegOfs(regs.es, regs.getDI()));
            alu8(7, a, b);
            regs.setSI(regs.getSI() + dir);
            regs.setDI(regs.getDI() + dir);
        }
    }

    private void execCmpsw() {
        int dir = regs.flags.getDF() ? -2 : 2;
        if (repPrefix) {
            while (regs.getCX() != 0) {
                int a = memory.readWord(resolveSegOfs(getDS(), regs.getSI()));
                int b = memory.readWord(resolveSegOfs(regs.es, regs.getDI()));
                alu16(7, a, b);
                regs.setSI(regs.getSI() + dir);
                regs.setDI(regs.getDI() + dir);
                regs.setCX(regs.getCX() - 1);
                if (repNE && regs.flags.getZF()) break;
                if (!repNE && !regs.flags.getZF()) break;
            }
        } else {
            int a = memory.readWord(resolveSegOfs(getDS(), regs.getSI()));
            int b = memory.readWord(resolveSegOfs(regs.es, regs.getDI()));
            alu16(7, a, b);
            regs.setSI(regs.getSI() + dir);
            regs.setDI(regs.getDI() + dir);
        }
    }

    private void execCmpsd() {
        int dir = regs.flags.getDF() ? -4 : 4;
        if (repPrefix) {
            while (regs.getCX() != 0) {
                int a = readMemOp(resolveSegOfs(getDS(), regs.getSI()));
                int b = readMemOp(resolveSegOfs(regs.es, regs.getDI()));
                alu32(7, a, b);
                regs.setSI(regs.getSI() + dir);
                regs.setDI(regs.getDI() + dir);
                regs.setCX(regs.getCX() - 1);
                if (repNE && regs.flags.getZF()) break;
                if (!repNE && !regs.flags.getZF()) break;
            }
        } else {
            int a = readMemOp(resolveSegOfs(getDS(), regs.getSI()));
            int b = readMemOp(resolveSegOfs(regs.es, regs.getDI()));
            alu32(7, a, b);
            regs.setSI(regs.getSI() + dir);
            regs.setDI(regs.getDI() + dir);
        }
    }

    private void execStosb() {
        int dir = regs.flags.getDF() ? -1 : 1;
        if (repPrefix) {
            while (regs.getCX() != 0) {
                memory.writeByte(resolveSegOfs(regs.es, regs.getDI()), regs.getAL());
                regs.setDI(regs.getDI() + dir);
                regs.setCX(regs.getCX() - 1);
            }
        } else {
            memory.writeByte(resolveSegOfs(regs.es, regs.getDI()), regs.getAL());
            regs.setDI(regs.getDI() + dir);
        }
    }

    private void execStosw() {
        int dir = regs.flags.getDF() ? -2 : 2;
        if (repPrefix) {
            while (regs.getCX() != 0) {
                memory.writeWord(resolveSegOfs(regs.es, regs.getDI()), regs.getAX());
                regs.setDI(regs.getDI() + dir);
                regs.setCX(regs.getCX() - 1);
            }
        } else {
            memory.writeWord(resolveSegOfs(regs.es, regs.getDI()), regs.getAX());
            regs.setDI(regs.getDI() + dir);
        }
    }

    private void execStosd() {
        int dir = regs.flags.getDF() ? -4 : 4;
        if (repPrefix) {
            while (regs.getCX() != 0) {
                writeMemOp(resolveSegOfs(regs.es, regs.getDI()), regs.getEAX());
                regs.setDI(regs.getDI() + dir);
                regs.setCX(regs.getCX() - 1);
            }
        } else {
            writeMemOp(resolveSegOfs(regs.es, regs.getDI()), regs.getEAX());
            regs.setDI(regs.getDI() + dir);
        }
    }

    private void execLodsb() {
        int dir = regs.flags.getDF() ? -1 : 1;
        regs.setAL(memory.readByte(resolveSegOfs(getDS(), regs.getSI())));
        regs.setSI(regs.getSI() + dir);
    }

    private void execLodsw() {
        int dir = regs.flags.getDF() ? -2 : 2;
        regs.setAX(memory.readWord(resolveSegOfs(getDS(), regs.getSI())));
        regs.setSI(regs.getSI() + dir);
    }

    private void execLodsd() {
        int dir = regs.flags.getDF() ? -4 : 4;
        regs.setEAX(readMemOp(resolveSegOfs(getDS(), regs.getSI())));
        regs.setSI(regs.getSI() + dir);
    }

    private void execScasb() {
        int dir = regs.flags.getDF() ? -1 : 1;
        if (repPrefix) {
            while (regs.getCX() != 0) {
                int b = memory.readByte(resolveSegOfs(regs.es, regs.getDI()));
                alu8(7, regs.getAL(), b);
                regs.setDI(regs.getDI() + dir);
                regs.setCX(regs.getCX() - 1);
                if (repNE && regs.flags.getZF()) break;
                if (!repNE && !regs.flags.getZF()) break;
            }
        } else {
            int b = memory.readByte(resolveSegOfs(regs.es, regs.getDI()));
            alu8(7, regs.getAL(), b);
            regs.setDI(regs.getDI() + dir);
        }
    }

    private void execScasw() {
        int dir = regs.flags.getDF() ? -2 : 2;
        if (repPrefix) {
            while (regs.getCX() != 0) {
                int b = memory.readWord(resolveSegOfs(regs.es, regs.getDI()));
                alu16(7, regs.getAX(), b);
                regs.setDI(regs.getDI() + dir);
                regs.setCX(regs.getCX() - 1);
                if (repNE && regs.flags.getZF()) break;
                if (!repNE && !regs.flags.getZF()) break;
            }
        } else {
            int b = memory.readWord(resolveSegOfs(regs.es, regs.getDI()));
            alu16(7, regs.getAX(), b);
            regs.setDI(regs.getDI() + dir);
        }
    }

    private void execScasd() {
        int dir = regs.flags.getDF() ? -4 : 4;
        if (repPrefix) {
            while (regs.getCX() != 0) {
                int b = readMemOp(resolveSegOfs(regs.es, regs.getDI()));
                alu32(7, regs.getEAX(), b);
                regs.setDI(regs.getDI() + dir);
                regs.setCX(regs.getCX() - 1);
                if (repNE && regs.flags.getZF()) break;
                if (!repNE && !regs.flags.getZF()) break;
            }
        } else {
            int b = readMemOp(resolveSegOfs(regs.es, regs.getDI()));
            alu32(7, regs.getEAX(), b);
            regs.setDI(regs.getDI() + dir);
        }
    }

    // ── Shift/Rotate ────────────────────────────────────────

    private void execShift8(int count) {
        int modrm = fetchByte();
        int op = (modrm >> 3) & 7;
        int ea = decodeModRM(modrm);
        int rm = modrm & 7;
        int val;
        if (ea == -1) { val = regs.getReg8(rm); } else { val = memory.readByte(ea); }
        count &= 0x1F;
        if (count == 0) return;
        val = doShift8(op, val, count);
        if (ea == -1) { regs.setReg8(rm, val); } else { memory.writeByte(ea, val); }
    }

    private void execShiftOp(int count) {
        int modrm = fetchByte();
        int op = (modrm >> 3) & 7;
        int ea = decodeModRM(modrm);
        int rm = modrm & 7;
        int val;
        if (ea == -1) { val = getRegOp(rm); } else { val = readMemOp(ea); }
        count &= 0x1F;
        if (count == 0) return;
        val = prefix66 ? doShift32(op, val, count) : doShift16(op, val, count);
        if (ea == -1) { setRegOp(rm, val); } else { writeMemOp(ea, val); }
    }

    private int doShift8(int op, int val, int count) {
        int result;
        switch (op) {
            case 0:
                for (int i = 0; i < count; i++) {
                    int msb = (val >> 7) & 1;
                    val = ((val << 1) | msb) & 0xFF;
                }
                regs.flags.setCF((val & 1) != 0);
                if (count == 1) regs.flags.setOF(((val >> 7) ^ (val & 1)) != 0);
                return val;
            case 1:
                for (int i = 0; i < count; i++) {
                    int lsb = val & 1;
                    val = ((val >> 1) | (lsb << 7)) & 0xFF;
                }
                regs.flags.setCF((val & 0x80) != 0);
                if (count == 1) regs.flags.setOF((((val >> 7) ^ (val >> 6)) & 1) != 0);
                return val;
            case 2: {
                for (int i = 0; i < count; i++) {
                    int oldCF = regs.flags.getCF() ? 1 : 0;
                    regs.flags.setCF((val & 0x80) != 0);
                    val = ((val << 1) | oldCF) & 0xFF;
                }
                if (count == 1) regs.flags.setOF(((val >> 7) ^ (regs.flags.getCF() ? 1 : 0)) != 0);
                return val;
            }
            case 3: {
                for (int i = 0; i < count; i++) {
                    int oldCF = regs.flags.getCF() ? 1 : 0;
                    regs.flags.setCF((val & 1) != 0);
                    val = ((val >> 1) | (oldCF << 7)) & 0xFF;
                }
                if (count == 1) regs.flags.setOF((((val >> 7) ^ (val >> 6)) & 1) != 0);
                return val;
            }
            case 4: case 6:
                result = val << count;
                regs.flags.setCF((result & 0x100) != 0);
                result &= 0xFF;
                regs.flags.setSZP8(result);
                if (count == 1) regs.flags.setOF(((result >> 7) ^ (regs.flags.getCF() ? 1 : 0)) != 0);
                return result;
            case 5:
                regs.flags.setCF(((val >> (count - 1)) & 1) != 0);
                if (count == 1) regs.flags.setOF((val & 0x80) != 0);
                result = (val >> count) & 0xFF;
                regs.flags.setSZP8(result);
                return result;
            case 7:
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
            case 0:
                for (int i = 0; i < count; i++) {
                    int msb = (val >> 15) & 1;
                    val = ((val << 1) | msb) & 0xFFFF;
                }
                regs.flags.setCF((val & 1) != 0);
                return val;
            case 1:
                for (int i = 0; i < count; i++) {
                    int lsb = val & 1;
                    val = ((val >> 1) | (lsb << 15)) & 0xFFFF;
                }
                regs.flags.setCF((val & 0x8000) != 0);
                return val;
            case 2: {
                for (int i = 0; i < count; i++) {
                    int oldCF = regs.flags.getCF() ? 1 : 0;
                    regs.flags.setCF((val & 0x8000) != 0);
                    val = ((val << 1) | oldCF) & 0xFFFF;
                }
                return val;
            }
            case 3: {
                for (int i = 0; i < count; i++) {
                    int oldCF = regs.flags.getCF() ? 1 : 0;
                    regs.flags.setCF((val & 1) != 0);
                    val = ((val >> 1) | (oldCF << 15)) & 0xFFFF;
                }
                return val;
            }
            case 4: case 6:
                result = val << count;
                regs.flags.setCF((result & 0x10000) != 0);
                result &= 0xFFFF;
                regs.flags.setSZP16(result);
                return result;
            case 5:
                regs.flags.setCF(((val >> (count - 1)) & 1) != 0);
                result = (val >> count) & 0xFFFF;
                regs.flags.setSZP16(result);
                return result;
            case 7:
                regs.flags.setCF(((val >> (count - 1)) & 1) != 0);
                int sign = (val & 0x8000) != 0 ? 0xFFFF : 0;
                result = val;
                for (int i = 0; i < count; i++) result = ((result >> 1) | (sign & 0x8000)) & 0xFFFF;
                regs.flags.setSZP16(result);
                return result;
            default: return val;
        }
    }

    private int doShift32(int op, int val, int count) {
        switch (op) {
            case 0:
                for (int i = 0; i < count; i++) {
                    int msb = (val >>> 31) & 1;
                    val = (val << 1) | msb;
                }
                regs.flags.setCF((val & 1) != 0);
                return val;
            case 1:
                for (int i = 0; i < count; i++) {
                    int lsb = val & 1;
                    val = (val >>> 1) | (lsb << 31);
                }
                regs.flags.setCF((val & 0x80000000) != 0);
                return val;
            case 2: {
                for (int i = 0; i < count; i++) {
                    int oldCF = regs.flags.getCF() ? 1 : 0;
                    regs.flags.setCF((val & 0x80000000) != 0);
                    val = (val << 1) | oldCF;
                }
                return val;
            }
            case 3: {
                for (int i = 0; i < count; i++) {
                    int oldCF = regs.flags.getCF() ? 1 : 0;
                    regs.flags.setCF((val & 1) != 0);
                    val = (val >>> 1) | (oldCF << 31);
                }
                return val;
            }
            case 4: case 6: {
                long lval = val & 0xFFFFFFFFL;
                long result = lval << count;
                regs.flags.setCF((result & 0x100000000L) != 0);
                int r = (int) result;
                regs.flags.setSZP32(r);
                return r;
            }
            case 5: {
                regs.flags.setCF(((val >>> (count - 1)) & 1) != 0);
                int r = val >>> count;
                regs.flags.setSZP32(r);
                return r;
            }
            case 7: {
                regs.flags.setCF(((val >> (count - 1)) & 1) != 0);
                int r = val >> count;
                regs.flags.setSZP32(r);
                return r;
            }
            default: return val;
        }
    }

    // ── GRP3 (TEST, NOT, NEG, MUL, DIV, ...) ───────────────

    private void execGrp3_8() {
        int modrm = fetchByte();
        int op = (modrm >> 3) & 7;
        int ea = decodeModRM(modrm);
        int rm = modrm & 7;
        int val;
        if (ea == -1) { val = regs.getReg8(rm); } else { val = memory.readByte(ea); }

        switch (op) {
            case 0: case 1:
                alu8(4, val, fetchByte());
                break;
            case 2:
                val = (~val) & 0xFF;
                if (ea == -1) regs.setReg8(rm, val); else memory.writeByte(ea, val);
                break;
            case 3: {
                int result = alu8(5, 0, val);
                if (ea == -1) regs.setReg8(rm, result); else memory.writeByte(ea, result);
                regs.flags.setCF(val != 0);
                break;
            }
            case 4: {
                int res = regs.getAL() * val;
                regs.setAX(res & 0xFFFF);
                boolean hi = (res & 0xFF00) != 0;
                regs.flags.setCF(hi); regs.flags.setOF(hi);
                break;
            }
            case 5: {
                int res = ((byte) regs.getAL()) * ((byte) val);
                regs.setAX(res & 0xFFFF);
                boolean ext = (regs.getAH() != 0 && regs.getAH() != 0xFF);
                regs.flags.setCF(ext); regs.flags.setOF(ext);
                break;
            }
            case 6: {
                if (val == 0) { softwareInt(0); return; }
                int dividend = regs.getAX();
                int quot = (dividend & 0xFFFF) / val;
                int rem = (dividend & 0xFFFF) % val;
                if (quot > 0xFF) { softwareInt(0); return; }
                regs.setAL(quot); regs.setAH(rem);
                break;
            }
            case 7: {
                if (val == 0) { softwareInt(0); return; }
                short dividend = (short) regs.getAX();
                byte divisor = (byte) val;
                int quot = dividend / divisor;
                int rem = dividend % divisor;
                if (quot > 127 || quot < -128) { softwareInt(0); return; }
                regs.setAL(quot & 0xFF); regs.setAH(rem & 0xFF);
                break;
            }
        }
    }

    private void execGrp3_Op() {
        int modrm = fetchByte();
        int op = (modrm >> 3) & 7;
        int ea = decodeModRM(modrm);
        int rm = modrm & 7;
        int val;
        if (ea == -1) { val = getRegOp(rm); } else { val = readMemOp(ea); }

        if (prefix66) execGrp3_32(op, ea, rm, val);
        else execGrp3_16(op, ea, rm, val);
    }

    private void execGrp3_16(int op, int ea, int rm, int val) {
        switch (op) {
            case 0: case 1: alu16(4, val, fetchWord()); break;
            case 2:
                val = (~val) & 0xFFFF;
                if (ea == -1) regs.setReg16(rm, val); else memory.writeWord(ea, val);
                break;
            case 3: {
                int result = alu16(5, 0, val);
                if (ea == -1) regs.setReg16(rm, result); else memory.writeWord(ea, result);
                regs.flags.setCF(val != 0);
                break;
            }
            case 4: {
                long res = (long)(regs.getAX() & 0xFFFF) * (val & 0xFFFF);
                regs.setAX((int)(res & 0xFFFF));
                regs.setDX((int)((res >> 16) & 0xFFFF));
                boolean hi = regs.getDX() != 0;
                regs.flags.setCF(hi); regs.flags.setOF(hi);
                break;
            }
            case 5: {
                long res = (long)((short)regs.getAX()) * ((short)val);
                regs.setAX((int)(res & 0xFFFF));
                regs.setDX((int)((res >> 16) & 0xFFFF));
                boolean ext = (regs.getDX() != 0 && regs.getDX() != 0xFFFF);
                regs.flags.setCF(ext); regs.flags.setOF(ext);
                break;
            }
            case 6: {
                if (val == 0) { softwareInt(0); return; }
                long dividend = ((long)(regs.getDX() & 0xFFFF) << 16) | (regs.getAX() & 0xFFFF);
                long quot = Long.divideUnsigned(dividend, val & 0xFFFF);
                long rem = Long.remainderUnsigned(dividend, val & 0xFFFF);
                if (quot > 0xFFFF) { softwareInt(0); return; }
                regs.setAX((int)(quot & 0xFFFF));
                regs.setDX((int)(rem & 0xFFFF));
                break;
            }
            case 7: {
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

    private void execGrp3_32(int op, int ea, int rm, int val) {
        switch (op) {
            case 0: case 1: alu32(4, val, fetchDWord()); break;
            case 2:
                val = ~val;
                if (ea == -1) regs.setReg32(rm, val); else writeMemOp(ea, val);
                break;
            case 3: {
                int result = alu32(5, 0, val);
                if (ea == -1) regs.setReg32(rm, result); else writeMemOp(ea, result);
                regs.flags.setCF(val != 0);
                break;
            }
            case 4: {
                long la = regs.getEAX() & 0xFFFFFFFFL;
                long lb = val & 0xFFFFFFFFL;
                long res = la * lb;
                regs.setEAX((int) res);
                regs.setEDX((int)(res >> 32));
                boolean hi = regs.getEDX() != 0;
                regs.flags.setCF(hi); regs.flags.setOF(hi);
                break;
            }
            case 5: {
                long res = (long) regs.getEAX() * (long) val;
                regs.setEAX((int) res);
                regs.setEDX((int)(res >> 32));
                boolean ext = (regs.getEDX() != 0 && regs.getEDX() != -1);
                regs.flags.setCF(ext); regs.flags.setOF(ext);
                break;
            }
            case 6: {
                if (val == 0) { softwareInt(0); return; }
                long dividend = ((long)(regs.getEDX() & 0xFFFFFFFFL) << 32) | (regs.getEAX() & 0xFFFFFFFFL);
                long divisor = val & 0xFFFFFFFFL;
                long quot = Long.divideUnsigned(dividend, divisor);
                long rem = Long.remainderUnsigned(dividend, divisor);
                if ((quot & 0xFFFFFFFF00000000L) != 0) { softwareInt(0); return; }
                regs.setEAX((int) quot);
                regs.setEDX((int) rem);
                break;
            }
            case 7: {
                if (val == 0) { softwareInt(0); return; }
                long dividend = ((long) regs.getEDX() << 32) | (regs.getEAX() & 0xFFFFFFFFL);
                long quot = dividend / val;
                long rem = dividend % val;
                if (quot > Integer.MAX_VALUE || quot < Integer.MIN_VALUE) { softwareInt(0); return; }
                regs.setEAX((int) quot);
                regs.setEDX((int) rem);
                break;
            }
        }
    }

    // ── GRP4 (INC/DEC r/m8) ────────────────────────────────

    private void execGrp4() {
        int modrm = fetchByte();
        int op = (modrm >> 3) & 7;
        int ea = decodeModRM(modrm);
        int rm = modrm & 7;
        int val;
        if (ea == -1) { val = regs.getReg8(rm); } else { val = memory.readByte(ea); }

        boolean cf = regs.flags.getCF();
        switch (op) {
            case 0: val = alu8(0, val, 1); break;
            case 1: val = alu8(5, val, 1); break;
            default: return;
        }
        regs.flags.setCF(cf);
        if (ea == -1) regs.setReg8(rm, val); else memory.writeByte(ea, val);
    }

    // ── GRP5 (INC/DEC/CALL/JMP/PUSH r/m16(32)) ─────────────

    private void execGrp5() {
        int modrm = fetchByte();
        int op = (modrm >> 3) & 7;
        int ea = decodeModRM(modrm);
        int rm = modrm & 7;
        int val;
        if (ea == -1) { val = getRegOp(rm); } else { val = readMemOp(ea); }

        switch (op) {
            case 0: {
                boolean cf = regs.flags.getCF();
                int result = aluOp(0, val, 1);
                regs.flags.setCF(cf);
                if (ea == -1) setRegOp(rm, result); else writeMemOp(ea, result);
                break;
            }
            case 1: {
                boolean cf = regs.flags.getCF();
                int result = aluOp(5, val, 1);
                regs.flags.setCF(cf);
                if (ea == -1) setRegOp(rm, result); else writeMemOp(ea, result);
                break;
            }
            case 2: // CALL near r/m
                pushOp(getEffIP());
                setEffIP(val);
                break;
            case 3: // CALL far m16:16(32)
                pushOp(regs.cs);
                pushOp(getEffIP());
                if (prefix66) {
                    setEffIP(readMemOp(ea));
                    regs.cs = memory.readWord(ea + 4);
                } else {
                    setEffIP(memory.readWord(ea));
                    regs.cs = memory.readWord(ea + 2);
                }
                if (dpmi != null && dpmi.isDpmiActive()) {
                    fixupCSChange("CALL FAR", regs.cs, getEffIP());
                }
                break;
            case 4: // JMP near r/m
                setEffIP(val);
                break;
            case 5: // JMP far m16:16(32)
                if (prefix66) {
                    setEffIP(readMemOp(ea));
                    regs.cs = memory.readWord(ea + 4);
                } else {
                    setEffIP(memory.readWord(ea));
                    regs.cs = memory.readWord(ea + 2);
                }
                if (dpmi != null && dpmi.isDpmiActive()) {
                    fixupCSChange("JMP FAR", regs.cs, getEffIP());
                }
                break;
            case 6:
                pushOp(val);
                break;
        }
    }

    public Memory getMemory() { return memory; }
    public IoPortHandler getIo() { return io; }
    public PIC getPic() { return pic; }

    /** Validate a CS change in protected mode and auto-map invalid selectors. */
    private void fixupCSChange(String source, int newCS, int newIP) {
        if (dpmi == null) return;
        int idx = dpmi.selectorToIndex(newCS);
        boolean isLdt = dpmi.isLDTSelector(newCS);
        DPMIManager.LDTEntry entry = dpmi.getEntry(newCS);

        if ((newCS & 0xFFFC) == 0) {
            System.err.printf("[CPU] WARNING: %s to null selector CS=%04X IP=%08X at cycle %d%n",
                    source, newCS, newIP, totalCycles);
            return;
        }

        boolean needsAutoMap = false;

        if (entry == null || !entry.present) {
            needsAutoMap = true;
        } else {
            boolean isCode = (entry.accessRights & 0x08) != 0;
            if (!isCode) {
                needsAutoMap = true;
            }
        }

        if (needsAutoMap) {
            // The popped CS is not a valid PM code selector.
            // Treat it as a real-mode segment and auto-create a mapping.
            System.out.printf("[CPU] %s: auto-mapping invalid CS=%04X as real-mode segment at cycle %d%n",
                    source, newCS, totalCycles);
            int mappedSel = dpmi.autoMapRealModeCS(newCS);
            if (mappedSel >= 0) {
                regs.cs = mappedSel;
                // Also leave PM if this is a return to real-mode code
                // (the segment points to conventional memory below 1MB)
            }
        } else {
            int linear = entry.base + newIP;
            System.out.printf("[CPU] %s → CS=%04X (%s[%d]) IP=%08X linear=%08X is32=%b base=%08X limit=%05X access=%04X%n",
                    source, newCS, isLdt ? "LDT" : "GDT", idx, newIP,
                    linear, entry.is32Bit, entry.base, entry.limit, entry.accessRights);
        }
    }
    public int getCR0() { return cr0; }
    public void setCR0(int v) { cr0 = v; }
    public int getGdtrBase() { return gdtr_base; }
    public int getGdtrLimit() { return gdtr_limit; }
    public void setGDTR(int base, int limit) {
        this.gdtr_base = base;
        this.gdtr_limit = limit;
    }
}

