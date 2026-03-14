package de.bund.zrb.dosbox.dos;

import de.bund.zrb.dosbox.cpu.CPU;
import de.bund.zrb.dosbox.hardware.memory.Memory;

/**
 * DPMI (DOS Protected Mode Interface) Manager.
 * Manages LDT descriptors, extended memory, and the mode switch.
 *
 * Provides the runtime environment that DOS/4GW and other
 * DOS extenders expect when running in protected mode.
 *
 * Now also supports GDT descriptor resolution: when a selector has TI=0
 * (GDT), the descriptor is read from memory at gdtr_base + index*8.
 */
public class DPMIManager {

    // ── LDT Descriptor Entry ─────────────────────────────────
    public static class LDTEntry {
        public int base;        // 32-bit base address
        public int limit;       // 20-bit limit (page granular if G bit set)
        public int accessRights;// access byte + extended byte
        public boolean present;
        public boolean is32Bit; // D/B bit
        public boolean pageGranular; // G bit

        public long getEffectiveLimit() {
            if (pageGranular) return ((long)(limit & 0xFFFFF) << 12) | 0xFFF;
            return limit & 0xFFFFF;
        }
    }

    // ── Memory Block ─────────────────────────────────────────
    private static class MemBlock {
        int linearAddress;
        int size;
        int handle;
    }

    // ── Constants ────────────────────────────────────────────
    private static final int MAX_LDT_ENTRIES = 8192;
    private static final int SELECTOR_INCREMENT = 8;
    // Extended memory starts at 1MB + 64KB
    private static final int EXT_MEM_START = 0x110000;
    // Max extended memory = 15MB (16MB total - 1MB conventional)
    private static final int EXT_MEM_SIZE = 15 * 1024 * 1024;

    // ── State ───────────────────────────────────────────────
    private final LDTEntry[] ldt = new LDTEntry[MAX_LDT_ENTRIES];
    private final MemBlock[] memBlocks = new MemBlock[256];
    private int nextHandle = 1;
    private int extMemNext = EXT_MEM_START; // next free extended memory address

    private boolean dpmiActive;
    private boolean realModeSimulation; // temporarily use real-mode addressing during simulated RM calls
    private final Memory memory;
    private CPU cpu; // set after construction to avoid circular dependency

    // ── GDT tracking (synced from CPU when LGDT is executed) ──
    private int gdtrBase;
    private int gdtrLimit;

    // ── Real mode interrupt vectors (saved for RM simulation) ──
    private final int[] rmIntVecOfs = new int[256];
    private final int[] rmIntVecSeg = new int[256];

    // ── Protected mode interrupt vectors ─────────────────────
    private final int[] pmIntVecOfs = new int[256];
    private final int[] pmIntVecSel = new int[256];

    // ── Processor exception handler vectors (0-31) ──────────
    private final int[] excHandlerOfs = new int[32];
    private final int[] excHandlerSel = new int[32];

    // DPMI entry point address (in BIOS ROM area)
    public static final int DPMI_ENTRY_SEG = 0xF000;
    public static final int DPMI_ENTRY_OFS = 0x8000;

    // Callback interrupt number (used internally)
    public static final int DPMI_CALLBACK_INT = 0xFE;

    // Stub addresses for INT 31h/0305 (State Save/Restore)
    public static final int STATE_SAVE_RM_SEG = 0xF000;
    public static final int STATE_SAVE_RM_OFS = 0x8200;
    public static final int STATE_SAVE_PM_SEL = 0;  // filled in after PM entry
    public static final int STATE_SAVE_PM_OFS = 0x8204;
    public static final int STATE_SAVE_SIZE   = 0;   // no state to save

    // Stub addresses for INT 31h/0306 (Raw Mode Switch)
    public static final int RAW_SWITCH_RM2PM_SEG = 0xF000;
    public static final int RAW_SWITCH_RM2PM_OFS = 0x8210;
    public static final int RAW_SWITCH_PM2RM_SEG = 0xF000;
    public static final int RAW_SWITCH_PM2RM_OFS = 0x8214;

    // GDT location in BIOS ROM area (must not collide with other stubs)
    private static final int GDT_LINEAR_ADDR = 0xF8300; // F000:8300
    private static final int GDT_NUM_ENTRIES = 5;

    public DPMIManager(Memory memory) {
        this.memory = memory;
        for (int i = 0; i < MAX_LDT_ENTRIES; i++) {
            ldt[i] = new LDTEntry();
        }
    }

    public boolean isDpmiActive() { return dpmiActive && !realModeSimulation; }

    /** Temporarily switch to real-mode address resolution (for INT 31h/0300,0301). */
    public void setRealModeSimulation(boolean active) { this.realModeSimulation = active; }
    public boolean isRealModeSimulation() { return realModeSimulation; }

    /** Check if DPMI has been entered (regardless of simulation state). */
    public boolean isDpmiEntered() { return dpmiActive; }

    /** Set CPU reference (called after construction to avoid circular dependency). */
    public void setCPU(CPU cpu) { this.cpu = cpu; }

    // ── GDTR synchronisation from CPU ───────────────────────

    /** Called by CPU after LGDT instruction to keep DPMI in sync. */
    public void setGDTR(int base, int limit) {
        this.gdtrBase = base;
        this.gdtrLimit = limit;
    }

    /**
     * Sync DPMI's GDTR back to the CPU's internal GDTR registers.
     * Must be called after setupGDT() and whenever DPMI changes the GDT.
     */
    private void syncGDTRtoCPU() {
        if (cpu != null) {
            cpu.setGDTR(gdtrBase, gdtrLimit);
        }
    }

    public int getGdtrBase()  { return gdtrBase; }
    public int getGdtrLimit() { return gdtrLimit; }

    // ── Selector ↔ LDT index conversion ─────────────────────

    /** Check if a selector references the LDT (TI bit = 1). */
    public boolean isLDTSelector(int sel) {
        return (sel & 0x04) != 0;  // bit 2 = TI
    }

    /** Convert selector to table index. Selector format: index*8 + flags */
    public int selectorToIndex(int sel) {
        return (sel >> 3) & (MAX_LDT_ENTRIES - 1);
    }

    /** Convert LDT index to selector value */
    public int indexToSelector(int idx) {
        return (idx << 3) | 7; // LDT bit + ring 3
    }

    // ── GDT descriptor reading from memory ──────────────────

    /**
     * Read a GDT descriptor from memory at gdtr_base + index*8.
     * Returns a temporary LDTEntry with the parsed descriptor fields,
     * or null if the index is out of range.
     */
    private LDTEntry readGDTEntry(int index) {
        if (gdtrBase == 0 && gdtrLimit == 0) return null;
        int offset = index * 8;
        if (offset + 7 > gdtrLimit) return null;

        int addr = gdtrBase + offset;
        int limitLo = memory.readByte(addr) | (memory.readByte(addr + 1) << 8);
        int baseLo  = memory.readByte(addr + 2) | (memory.readByte(addr + 3) << 8);
        int baseMid = memory.readByte(addr + 4);
        int access  = memory.readByte(addr + 5);
        int limHiFlags = memory.readByte(addr + 6);
        int baseHi  = memory.readByte(addr + 7);

        LDTEntry e = new LDTEntry();
        e.base = baseLo | (baseMid << 16) | (baseHi << 24);
        e.limit = limitLo | ((limHiFlags & 0x0F) << 16);
        e.accessRights = access | ((limHiFlags & 0xF0) << 8);
        e.present = (access & 0x80) != 0;
        e.is32Bit = (limHiFlags & 0x40) != 0;
        e.pageGranular = (limHiFlags & 0x80) != 0;
        return e;
    }

    // ── DPMI Mode Switch ────────────────────────────────────

    /**
     * Perform the DPMI mode switch.
     * Called when the program calls the DPMI entry point.
     * Sets up initial selectors and activates protected mode.
     *
     * @return array of [csSel, dsSel, ssSel, esSel] initial selectors
     */
    public int[] enterProtectedMode(int clientBits, int callerCS, int callerDS, int callerSS, int callerES) {
        dpmiActive = true;

        // Save real mode IVT
        for (int i = 0; i < 256; i++) {
            rmIntVecOfs[i] = memory.readWord(i * 4);
            rmIntVecSeg[i] = memory.readWord(i * 4 + 2);
        }

        // Create initial selectors for the caller's real mode segments
        // CS selector — must map the caller's actual CS, NOT the PSP!
        int csSel = segmentToDescriptor(callerCS);
        int csIdx = selectorToIndex(csSel);
        ldt[csIdx].accessRights = 0x009A; // code, read, present
        // The initial CS maps the caller's real-mode segment — always 16-bit.
        // clientBits=1 means the client WANTS 32-bit DPMI features later,
        // NOT that the initial CS segment itself is 32-bit.
        ldt[csIdx].is32Bit = false;
        ldt[csIdx].limit = 0xFFFF;

        // DS selector — map caller's DS
        int dsSel = segmentToDescriptor(callerDS);

        // SS selector — map caller's SS
        int ssSel = segmentToDescriptor(callerSS);

        // ES selector — map caller's ES (typically the PSP)
        int esSel = segmentToDescriptor(callerES);

        // Also create a selector for the environment segment (PSP+0x2C)
        // and selectors for conventional memory ranges
        // Create a flat selector for all of real mode memory (0-1MB)
        int flatSel = allocateDescriptors(1);
        if (flatSel >= 0) {
            int flatIdx = selectorToIndex(flatSel);
            ldt[flatIdx].base = 0;
            ldt[flatIdx].limit = 0xFFFFF; // 1MB
            ldt[flatIdx].pageGranular = false;
            ldt[flatIdx].is32Bit = true;
            ldt[flatIdx].accessRights = 0x0092; // data, RW, present
        }

        // ── Set up a proper GDT so that GDT selectors used by DOS/4GW work ──
        // DOS/4GW and other extenders expect certain GDT entries to be valid,
        // especially flat code/data selectors.
        setupGDT();

        System.out.printf("[DPMI] Protected mode entered. CS=%04X DS=%04X SS=%04X ES=%04X%n",
                csSel, dsSel, ssSel, esSel);

        return new int[] { csSel, dsSel, ssSel, esSel };
    }

    // ── INT 31h/0000: Allocate LDT Descriptors ──────────────

    public int allocateDescriptors(int count) {
        // Find 'count' consecutive free entries
        int firstFree = -1;
        int found = 0;
        for (int i = 1; i < MAX_LDT_ENTRIES; i++) {
            if (!ldt[i].present) {
                if (firstFree == -1) firstFree = i;
                found++;
                if (found >= count) {
                    // Mark as allocated
                    for (int j = firstFree; j < firstFree + count; j++) {
                        ldt[j].present = true;
                        ldt[j].base = 0;
                        ldt[j].limit = 0;
                        ldt[j].accessRights = 0x0092; // data, RW, present
                        ldt[j].is32Bit = false;
                        ldt[j].pageGranular = false;
                    }
                    return indexToSelector(firstFree);
                }
            } else {
                firstFree = -1;
                found = 0;
            }
        }
        return -1; // no free descriptors
    }

    // ── INT 31h/0001: Free LDT Descriptor ───────────────────

    public boolean freeDescriptor(int selector) {
        int idx = selectorToIndex(selector);
        if (idx > 0 && idx < MAX_LDT_ENTRIES && ldt[idx].present) {
            ldt[idx].present = false;
            ldt[idx].base = 0;
            ldt[idx].limit = 0;
            return true;
        }
        return false;
    }

    // ── INT 31h/0002: Segment to Descriptor ─────────────────

    public int segmentToDescriptor(int realModeSeg) {
        int sel = allocateDescriptors(1);
        if (sel < 0) return -1;
        int idx = selectorToIndex(sel);
        ldt[idx].base = (realModeSeg & 0xFFFF) << 4;
        ldt[idx].limit = 0xFFFF;
        ldt[idx].accessRights = 0x0092; // data, RW, present
        ldt[idx].is32Bit = false;
        return sel;
    }

    // ── INT 31h/0003: Get Selector Increment Value ──────────

    public int getSelectorIncrement() {
        return SELECTOR_INCREMENT;
    }

    // ── Auto-map real-mode segment as PM code selector ──────

    /**
     * When a RETF/JMP FAR loads a CS value that is not a valid PM selector,
     * the raw value is likely a real-mode segment address pushed before PM entry.
     * This method creates a code segment descriptor mapping that real-mode segment,
     * allowing execution to continue at the correct linear address.
     *
     * @param realModeSeg the raw 16-bit value popped as CS (interpreted as real-mode segment)
     * @return a valid PM selector for the new code segment, or -1 on failure
     */
    public int autoMapRealModeCS(int realModeSeg) {
        int sel = allocateDescriptors(1);
        if (sel < 0) return -1;
        int idx = selectorToIndex(sel);
        ldt[idx].base = (realModeSeg & 0xFFFF) << 4;
        ldt[idx].limit = 0xFFFF;
        ldt[idx].accessRights = 0x009A; // code, read, present (16-bit)
        ldt[idx].is32Bit = false;
        ldt[idx].present = true;
        System.out.printf("[DPMI] Auto-mapped real-mode segment %04X → selector %04X (LDT[%d], base=%08X)%n",
                realModeSeg, sel, idx, ldt[idx].base);
        return sel;
    }

    // ── INT 31h/0006: Get Segment Base Address ──────────────

    public int getSegmentBase(int selector) {
        // Check GDT first if it's a GDT selector
        if (!isLDTSelector(selector)) {
            LDTEntry gdt = readGDTEntry(selectorToIndex(selector));
            if (gdt != null && gdt.present) return gdt.base;
        }
        int idx = selectorToIndex(selector);
        if (idx < MAX_LDT_ENTRIES) return ldt[idx].base;
        return 0;
    }

    // ── INT 31h/0007: Set Segment Base Address ──────────────

    public boolean setSegmentBase(int selector, int base) {
        int idx = selectorToIndex(selector);
        if (idx < MAX_LDT_ENTRIES) {
            ldt[idx].base = base;
            return true;
        }
        return false;
    }

    // ── INT 31h/0008: Set Segment Limit ─────────────────────

    public boolean setSegmentLimit(int selector, int limit) {
        int idx = selectorToIndex(selector);
        if (idx < MAX_LDT_ENTRIES && ldt[idx].present) {
            // Use unsigned comparison — Java int is signed, so limit=0xFFFFFFFF would be negative
            if (Integer.compareUnsigned(limit, 0xFFFFF) > 0) {
                // Need page granularity
                ldt[idx].limit = (limit >>> 12) & 0xFFFFF;
                ldt[idx].pageGranular = true;
            } else {
                ldt[idx].limit = limit & 0xFFFFF;
                ldt[idx].pageGranular = false;
            }
            return true;
        }
        return false;
    }

    // ── INT 31h/0009: Set Descriptor Access Rights ──────────

    public boolean setAccessRights(int selector, int rights) {
        int idx = selectorToIndex(selector);
        if (idx < MAX_LDT_ENTRIES) {
            ldt[idx].accessRights = rights;
            ldt[idx].present = (rights & 0x0080) != 0;   // bit 7 = Present bit
            ldt[idx].is32Bit = (rights & 0x4000) != 0;   // bit 14 = D/B bit
            ldt[idx].pageGranular = (rights & 0x8000) != 0; // bit 15 = G bit
            return true;
        }
        return false;
    }

    // ── INT 31h/000B: Get Descriptor ────────────────────────

    public void getDescriptor(int selector, int addr) {
        int idx = selectorToIndex(selector);
        LDTEntry e;
        if (!isLDTSelector(selector)) {
            // GDT selector: read from memory
            e = readGDTEntry(idx);
            if (e == null) e = ldt[idx]; // fallback
        } else {
            e = ldt[idx];
        }
        // Write 8-byte x86 descriptor format
        int limitLo = e.limit & 0xFFFF;
        int baseLo = e.base & 0xFFFF;
        int baseMid = (e.base >> 16) & 0xFF;
        int access = e.accessRights & 0xFF;
        int limitHi = (e.limit >> 16) & 0x0F;
        int flags = (e.accessRights >> 8) & 0xF0;
        int baseHi = (e.base >> 24) & 0xFF;

        memory.writeWord(addr, limitLo);
        memory.writeWord(addr + 2, baseLo);
        memory.writeByte(addr + 4, baseMid);
        memory.writeByte(addr + 5, access | 0x80); // present bit
        memory.writeByte(addr + 6, limitHi | flags);
        memory.writeByte(addr + 7, baseHi);
    }

    // ── INT 31h/000C: Set Descriptor ────────────────────────

    public boolean setDescriptor(int selector, int addr) {
        int idx = selectorToIndex(selector);
        if (idx >= MAX_LDT_ENTRIES) return false;
        LDTEntry e = ldt[idx];

        int limitLo = memory.readWord(addr);
        int baseLo = memory.readWord(addr + 2);
        int baseMid = memory.readByte(addr + 4);
        int access = memory.readByte(addr + 5);
        int limHiFlags = memory.readByte(addr + 6);
        int baseHi = memory.readByte(addr + 7);

        e.base = baseLo | (baseMid << 16) | (baseHi << 24);
        e.limit = limitLo | ((limHiFlags & 0x0F) << 16);
        e.accessRights = access | ((limHiFlags & 0xF0) << 8);
        e.present = (access & 0x80) != 0;
        e.is32Bit = (limHiFlags & 0x40) != 0;
        e.pageGranular = (limHiFlags & 0x80) != 0;
        return true;
    }

    // ── INT 31h/000A: Create Code Alias Descriptor ──────────

    public int createCodeAlias(int selector) {
        int srcIdx = selectorToIndex(selector);
        LDTEntry src;
        if (!isLDTSelector(selector)) {
            src = readGDTEntry(srcIdx);
            if (src == null) src = ldt[srcIdx];
        } else {
            src = ldt[srcIdx];
        }
        int newSel = allocateDescriptors(1);
        if (newSel < 0) return -1;
        int dstIdx = selectorToIndex(newSel);
        ldt[dstIdx].base = src.base;
        ldt[dstIdx].limit = src.limit;
        ldt[dstIdx].is32Bit = src.is32Bit;
        ldt[dstIdx].pageGranular = src.pageGranular;
        // Make it a code segment (executable, readable)
        ldt[dstIdx].accessRights = (src.accessRights & 0xFF00) | 0x009A;
        return newSel;
    }

    // ── INT 31h/0100: Allocate DOS Memory ───────────────────

    public int[] allocateDOSMemory(int paragraphs) {
        // Allocate from conventional memory area
        // Returns [rmSegment, pmSelector]
        // Simple bump allocator from 0x2000
        int seg = 0x2000 + (nextHandle * 0x100); // crude allocation
        int sel = segmentToDescriptor(seg);
        if (sel < 0) return null;
        int idx = selectorToIndex(sel);
        ldt[idx].limit = paragraphs * 16 - 1;
        nextHandle++;
        return new int[] { seg, sel };
    }

    // ── INT 31h/0101: Free DOS Memory ───────────────────────

    public boolean freeDOSMemory(int selector) {
        return freeDescriptor(selector);
    }

    // ── Interrupt Vector Management ─────────────────────────

    public int getRMIntVector_Ofs(int intNum) { return rmIntVecOfs[intNum & 0xFF]; }
    public int getRMIntVector_Seg(int intNum) { return rmIntVecSeg[intNum & 0xFF]; }

    public void setRMIntVector(int intNum, int seg, int ofs) {
        rmIntVecOfs[intNum & 0xFF] = ofs;
        rmIntVecSeg[intNum & 0xFF] = seg;
        // Also update the actual IVT in memory
        memory.writeWord((intNum & 0xFF) * 4, ofs);
        memory.writeWord((intNum & 0xFF) * 4 + 2, seg);
    }

    public int getPMIntVector_Sel(int intNum) { return pmIntVecSel[intNum & 0xFF]; }
    public int getPMIntVector_Ofs(int intNum) { return pmIntVecOfs[intNum & 0xFF]; }

    public void setPMIntVector(int intNum, int sel, int ofs) {
        pmIntVecSel[intNum & 0xFF] = sel;
        pmIntVecOfs[intNum & 0xFF] = ofs;
    }

    // ── Exception handler management (INT 31h/0202-0203) ────

    public int getExceptionHandler_Sel(int excNum) {
        return (excNum < 32) ? excHandlerSel[excNum] : 0;
    }

    public int getExceptionHandler_Ofs(int excNum) {
        return (excNum < 32) ? excHandlerOfs[excNum] : 0;
    }

    public void setExceptionHandler(int excNum, int sel, int ofs) {
        if (excNum < 32) {
            excHandlerSel[excNum] = sel;
            excHandlerOfs[excNum] = ofs;
        }
    }

    // ── INT 31h/0400: Get DPMI Version ──────────────────────

    public int getVersionMajor() { return 0; }
    public int getVersionMinor() { return 90; }

    // ── INT 31h/0501: Allocate Memory Block ─────────────────

    public int[] allocateMemoryBlock(int sizeBytes) {
        if (sizeBytes <= 0) return null;
        // Align to 4KB
        int aligned = (sizeBytes + 0xFFF) & ~0xFFF;
        if (extMemNext + aligned > EXT_MEM_START + EXT_MEM_SIZE) return null;

        int linearAddr = extMemNext;
        extMemNext += aligned;

        int handle = nextHandle++;
        // Store block info
        for (int i = 0; i < memBlocks.length; i++) {
            if (memBlocks[i] == null) {
                memBlocks[i] = new MemBlock();
                memBlocks[i].linearAddress = linearAddr;
                memBlocks[i].size = aligned;
                memBlocks[i].handle = handle;
                break;
            }
        }

        // Returns [linearAddrHi, linearAddrLo, handleHi, handleLo]
        return new int[] {
            (linearAddr >> 16) & 0xFFFF, linearAddr & 0xFFFF,
            (handle >> 16) & 0xFFFF, handle & 0xFFFF
        };
    }

    // ── INT 31h/0502: Free Memory Block ─────────────────────

    public boolean freeMemoryBlock(int handle) {
        for (int i = 0; i < memBlocks.length; i++) {
            if (memBlocks[i] != null && memBlocks[i].handle == handle) {
                memBlocks[i] = null;
                return true;
            }
        }
        return true; // always succeed
    }

    // ── INT 31h/0500: Get Free Memory Info ──────────────────

    public void getFreeMemoryInfo(int addr) {
        int freeBytes = EXT_MEM_SIZE - (extMemNext - EXT_MEM_START);
        int freePages = freeBytes / 4096;
        // Write 48-byte structure
        memory.writeByte(addr, freeBytes & 0xFF);
        memory.writeByte(addr + 1, (freeBytes >> 8) & 0xFF);
        memory.writeByte(addr + 2, (freeBytes >> 16) & 0xFF);
        memory.writeByte(addr + 3, (freeBytes >> 24) & 0xFF);
        // Free unlocked pages
        memory.writeByte(addr + 4, freePages & 0xFF);
        memory.writeByte(addr + 5, (freePages >> 8) & 0xFF);
        memory.writeByte(addr + 6, (freePages >> 16) & 0xFF);
        memory.writeByte(addr + 7, (freePages >> 24) & 0xFF);
        // Remaining fields: -1 (unknown)
        for (int i = 8; i < 48; i += 4) {
            memory.writeByte(addr + i, 0xFF);
            memory.writeByte(addr + i + 1, 0xFF);
            memory.writeByte(addr + i + 2, 0xFF);
            memory.writeByte(addr + i + 3, 0xFF);
        }
    }

    // ── Resolve linear address from selector:offset ─────────

    /**
     * Convert a selector:offset pair to a linear address.
     * In real mode: seg*16 + offset.
     * In protected mode:
     *   - LDT selector (TI=1): use our LDT array
     *   - GDT selector (TI=0): read descriptor from memory at gdtr_base + index*8
     */
    private int resolveAddressErrCount = 0;

    public int resolveAddress(int selector, int offset) {
        if (!isLDTSelector(selector)) {
            // GDT selector — read descriptor from memory
            int idx = selectorToIndex(selector);
            if (idx == 0) {
                // Null selector — fall back to linear addressing
                return offset;
            }
            LDTEntry gdt = readGDTEntry(idx);
            if (gdt != null && gdt.present) {
                return gdt.base + offset;
            }
            // GDT entry invalid or out of bounds — fall back to real-mode style addressing
            if (resolveAddressErrCount < 10) {
                resolveAddressErrCount++;
                System.err.printf("[DPMI] resolveAddress: invalid GDT selector %04X (idx=%d, gdtBase=%08X, gdtLimit=%04X)%n",
                        selector, idx, gdtrBase, gdtrLimit);
            }
            return ((selector & 0xFFFF) << 4) + (offset & 0xFFFF);
        }

        // LDT selector
        int idx = selectorToIndex(selector);
        if (idx > 0 && idx < MAX_LDT_ENTRIES && ldt[idx].present) {
            return ldt[idx].base + offset;
        }
        // Fall back to real-mode style (for unmapped selectors / BIOS ROM area)
        return ((selector & 0xFFFF) << 4) + (offset & 0xFFFF);
    }

    /**
     * Check if a selector has 32-bit default operand size (D/B bit).
     */
    public boolean is32BitSelector(int selector) {
        if (!isLDTSelector(selector)) {
            // GDT selector — read from memory
            int idx = selectorToIndex(selector);
            LDTEntry gdt = readGDTEntry(idx);
            if (gdt != null && gdt.present) {
                return gdt.is32Bit;
            }
            return false;
        }
        // LDT selector
        int idx = selectorToIndex(selector);
        if (idx < MAX_LDT_ENTRIES && ldt[idx].present) {
            return ldt[idx].is32Bit;
        }
        return false;
    }

    /**
     * Get the descriptor entry for a selector.
     * For GDT selectors, reads from the GDT in memory.
     * For LDT selectors, returns the LDT array entry.
     * Returns a non-present entry if the GDT selector cannot be resolved
     * (never falls through to LDT to avoid false-positive validation).
     */
    public LDTEntry getEntry(int selector) {
        if (!isLDTSelector(selector)) {
            LDTEntry gdt = readGDTEntry(selectorToIndex(selector));
            if (gdt != null) return gdt;
            // GDT entry unresolvable — return a non-present dummy entry.
            // Do NOT fall through to LDT, as that would cause false-positive
            // validation (GDT index N would match LDT index N by accident).
            LDTEntry dummy = new LDTEntry();
            dummy.present = false;
            return dummy;
        }
        return ldt[selectorToIndex(selector)];
    }

    // ── GDT Setup ───────────────────────────────────────────

    /**
     * Set up a minimal GDT in the BIOS ROM area so that DOS extenders
     * (like DOS/4GW) can use standard GDT selectors.
     *
     * Layout:
     *   GDT[0] = 0x0000  null descriptor
     *   GDT[1] = 0x0008  flat 32-bit code (base=0, limit=4G, code+read)
     *   GDT[2] = 0x0010  flat 32-bit data (base=0, limit=4G, data+RW)
     *   GDT[3] = 0x0018  16-bit code (base=0, limit=1M, code+read)
     *   GDT[4] = 0x0020  flat 32-bit data (alias, same as GDT[2])
     */
    private void setupGDT() {
        int gdtAddr = GDT_LINEAR_ADDR;
        int gdtSize = GDT_NUM_ENTRIES * 8;

        // GDT[0]: null descriptor (all zeros)
        for (int i = 0; i < 8; i++) memory.writeByte(gdtAddr + i, 0);

        // GDT[1]: flat 32-bit code segment (sel=0x0008)
        writeGDTDescriptor(gdtAddr + 8, 0x00000000, 0xFFFFF, 0x9A, true, true);

        // GDT[2]: flat 32-bit data segment (sel=0x0010)
        writeGDTDescriptor(gdtAddr + 16, 0x00000000, 0xFFFFF, 0x92, true, true);

        // GDT[3]: 16-bit code segment (sel=0x0018)
        writeGDTDescriptor(gdtAddr + 24, 0x00000000, 0xFFFFF, 0x9A, false, false);

        // GDT[4]: flat 32-bit data segment (sel=0x0020, alias of GDT[2])
        writeGDTDescriptor(gdtAddr + 32, 0x00000000, 0xFFFFF, 0x92, true, true);

        // Set GDTR
        this.gdtrBase = gdtAddr;
        this.gdtrLimit = gdtSize - 1;
        syncGDTRtoCPU();

        System.out.printf("[DPMI] GDT set up at %08X, limit=%04X (%d entries)%n",
                gdtrBase, gdtrLimit, GDT_NUM_ENTRIES);
    }

    /**
     * Write a single 8-byte x86 descriptor at the given memory address.
     */
    private void writeGDTDescriptor(int addr, int base, int limit20, int access, boolean is32, boolean granularity) {
        int limitLo = limit20 & 0xFFFF;
        int limitHi = (limit20 >> 16) & 0x0F;
        int baseLo = base & 0xFFFF;
        int baseMid = (base >> 16) & 0xFF;
        int baseHi = (base >> 24) & 0xFF;

        int flags = 0;
        if (granularity) flags |= 0x80; // G bit
        if (is32) flags |= 0x40;        // D/B bit

        memory.writeByte(addr + 0, limitLo & 0xFF);
        memory.writeByte(addr + 1, (limitLo >> 8) & 0xFF);
        memory.writeByte(addr + 2, baseLo & 0xFF);
        memory.writeByte(addr + 3, (baseLo >> 8) & 0xFF);
        memory.writeByte(addr + 4, baseMid);
        memory.writeByte(addr + 5, access | 0x80); // present bit
        memory.writeByte(addr + 6, limitHi | flags);
        memory.writeByte(addr + 7, baseHi);
    }
}

