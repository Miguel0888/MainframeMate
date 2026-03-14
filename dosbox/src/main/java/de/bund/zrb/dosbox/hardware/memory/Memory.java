package de.bund.zrb.dosbox.hardware.memory;

/**
 * Flat memory model for the emulated PC.
 * Provides 1 MB conventional + 64 KB HMA = 1,114,112 bytes.
 * All reads/writes use physical (linear) addresses.
 *
 * Ported from: src/hardware/memory.cpp, include/mem.h
 */
public class Memory implements de.bund.zrb.dosbox.core.Module {

    /** Total memory size: 1 MB + 64 KB HMA */
    public static final int MEMORY_SIZE = 1024 * 1024 + 65536; // 0x110000

    /** Conventional memory limit */
    public static final int CONVENTIONAL_SIZE = 640 * 1024;     // 0xA0000

    /** Video RAM start (text mode at B8000, graphics at A0000) */
    public static final int VIDEO_RAM_START = 0xA0000;

    /** Text mode video RAM start */
    public static final int TEXT_VIDEO_START = 0xB8000;

    /** ROM area start */
    public static final int ROM_START = 0xC0000;

    /** BIOS ROM start */
    public static final int BIOS_START = 0xF0000;

    private final byte[] ram;

    public Memory() {
        ram = new byte[MEMORY_SIZE];
    }

    // ── Physical address helpers ─────────────────────────────

    /** Convert segment:offset to physical address (real mode). */
    public static int segOfs(int segment, int offset) {
        return ((segment & 0xFFFF) << 4) + (offset & 0xFFFF);
    }

    // ── Byte access ─────────────────────────────────────────

    public int readByte(int addr) {
        addr &= 0x1FFFFF; // mask to 21 bits (A20 gate)
        if (addr >= MEMORY_SIZE) return 0xFF;
        return ram[addr] & 0xFF;
    }

    public void writeByte(int addr, int value) {
        addr &= 0x1FFFFF;
        if (addr >= MEMORY_SIZE) return;
        ram[addr] = (byte) (value & 0xFF);
    }

    // ── Word access (little-endian) ─────────────────────────

    public int readWord(int addr) {
        return readByte(addr) | (readByte(addr + 1) << 8);
    }

    public void writeWord(int addr, int value) {
        writeByte(addr, value & 0xFF);
        writeByte(addr + 1, (value >> 8) & 0xFF);
    }

    // ── DWord access (little-endian) ────────────────────────

    public long readDWord(int addr) {
        return (readByte(addr))
             | (readByte(addr + 1) << 8)
             | (readByte(addr + 2) << 16)
             | ((long) readByte(addr + 3) << 24);
    }

    public void writeDWord(int addr, long value) {
        writeByte(addr, (int) (value & 0xFF));
        writeByte(addr + 1, (int) ((value >> 8) & 0xFF));
        writeByte(addr + 2, (int) ((value >> 16) & 0xFF));
        writeByte(addr + 3, (int) ((value >> 24) & 0xFF));
    }

    // ── Block operations ────────────────────────────────────

    /** Copy bytes from host array into emulated memory. */
    public void writeBlock(int addr, byte[] src, int srcOfs, int len) {
        for (int i = 0; i < len; i++) {
            writeByte(addr + i, src[srcOfs + i] & 0xFF);
        }
    }

    /** Copy bytes from emulated memory to host array. */
    public void readBlock(int addr, byte[] dst, int dstOfs, int len) {
        for (int i = 0; i < len; i++) {
            dst[dstOfs + i] = (byte) readByte(addr + i);
        }
    }

    /** Fill memory range with a value. */
    public void fill(int addr, int len, int value) {
        for (int i = 0; i < len; i++) {
            writeByte(addr + i, value);
        }
    }

    /** Direct access to backing array (use with care). */
    public byte[] getRawArray() {
        return ram;
    }

    // ── String helpers ──────────────────────────────────────

    /** Read a null-terminated ASCII string from memory. */
    public String readString(int addr, int maxLen) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLen; i++) {
            int ch = readByte(addr + i);
            if (ch == 0) break;
            sb.append((char) ch);
        }
        return sb.toString();
    }

    /** Write an ASCII string followed by a null terminator. */
    public void writeString(int addr, String s) {
        for (int i = 0; i < s.length(); i++) {
            writeByte(addr + i, s.charAt(i) & 0xFF);
        }
        writeByte(addr + s.length(), 0);
    }

    @Override
    public void reset() {
        java.util.Arrays.fill(ram, (byte) 0);
    }
}

