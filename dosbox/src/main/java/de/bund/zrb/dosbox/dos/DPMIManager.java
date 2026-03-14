package de.bund.zrb.dosbox.dos;

import de.bund.zrb.dosbox.hardware.memory.Memory;

/**
 * DPMI (DOS Protected Mode Interface) Manager.
 * Manages LDT descriptors, extended memory, and the mode switch.
 *
 * Provides the runtime environment that DOS/4GW and other
 * DOS extenders expect when running in protected mode.
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
    private final Memory memory;

    // ── Real mode interrupt vectors (saved for RM simulation) ──
    private final int[] rmIntVecOfs = new int[256];
    private final int[] rmIntVecSeg = new int[256];

    // ── Protected mode interrupt vectors ─────────────────────
    private final int[] pmIntVecOfs = new int[256];
    private final int[] pmIntVecSel = new int[256];

    // DPMI entry point address (in BIOS ROM area)
    public static final int DPMI_ENTRY_SEG = 0xF000;
    public static final int DPMI_ENTRY_OFS = 0x8000;

    // Callback interrupt number (used internally)
    public static final int DPMI_CALLBACK_INT = 0xFE;

    public DPMIManager(Memory memory) {
        this.memory = memory;
        for (int i = 0; i < MAX_LDT_ENTRIES; i++) {
            ldt[i] = new LDTEntry();
        }
    }

    public boolean isDpmiActive() { return dpmiActive; }

    // ── Selector ↔ LDT index conversion ─────────────────────

    /** Convert selector to LDT index. Selector format: index*8 + 7 (LDT, ring 3) */
    public int selectorToIndex(int sel) {
        return (sel >> 3) & (MAX_LDT_ENTRIES - 1);
    }

    /** Convert LDT index to selector value */
    public int indexToSelector(int idx) {
        return (idx << 3) | 7; // LDT bit + ring 3
    }

    // ── DPMI Mode Switch ────────────────────────────────────

    /**
     * Perform the DPMI mode switch.
     * Called when the program calls the DPMI entry point.
     * Sets up initial selectors and activates protected mode.
     */
    public void enterProtectedMode(int clientBits, int pspSegment) {
        dpmiActive = true;

        // Save real mode IVT
        for (int i = 0; i < 256; i++) {
            rmIntVecOfs[i] = memory.readWord(i * 4);
            rmIntVecSeg[i] = memory.readWord(i * 4 + 2);
        }
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

    // ── INT 31h/0006: Get Segment Base Address ──────────────

    public int getSegmentBase(int selector) {
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
        if (idx < MAX_LDT_ENTRIES) {
            if (limit > 0xFFFFF) {
                // Need page granularity
                ldt[idx].limit = limit >>> 12;
                ldt[idx].pageGranular = true;
            } else {
                ldt[idx].limit = limit;
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
            ldt[idx].is32Bit = (rights & 0x4000) != 0; // bit 14 = D/B bit
            ldt[idx].pageGranular = (rights & 0x8000) != 0; // bit 15 = G bit
            return true;
        }
        return false;
    }

    // ── INT 31h/000B: Get Descriptor ────────────────────────

    public void getDescriptor(int selector, int addr) {
        int idx = selectorToIndex(selector);
        LDTEntry e = ldt[idx];
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
        int newSel = allocateDescriptors(1);
        if (newSel < 0) return -1;
        int dstIdx = selectorToIndex(newSel);
        ldt[dstIdx].base = ldt[srcIdx].base;
        ldt[dstIdx].limit = ldt[srcIdx].limit;
        ldt[dstIdx].is32Bit = ldt[srcIdx].is32Bit;
        ldt[dstIdx].pageGranular = ldt[srcIdx].pageGranular;
        // Make it a code segment (executable, readable)
        ldt[dstIdx].accessRights = (ldt[srcIdx].accessRights & 0xFF00) | 0x009A;
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
     * Used by the CPU when in protected mode.
     */
    public int resolveAddress(int selector, int offset) {
        int idx = selectorToIndex(selector);
        if (idx < MAX_LDT_ENTRIES && ldt[idx].present) {
            return ldt[idx].base + offset;
        }
        // Fall back to real-mode style
        return ((selector & 0xFFFF) << 4) + (offset & 0xFFFF);
    }

    /**
     * Check if a selector has 32-bit default operand size (D/B bit).
     */
    public boolean is32BitSelector(int selector) {
        int idx = selectorToIndex(selector);
        if (idx < MAX_LDT_ENTRIES && ldt[idx].present) {
            return ldt[idx].is32Bit;
        }
        return false;
    }

    public LDTEntry getEntry(int selector) {
        return ldt[selectorToIndex(selector)];
    }
}

