package de.bund.zrb.dosbox.cpu;

/**
 * x86 CPU flags register.
 * Manages CF, PF, AF, ZF, SF, TF, IF, DF, OF and IOPL.
 *
 * Ported from: src/cpu/flags.cpp, src/cpu/lazyflags.h
 */
public class Flags {

    // ── Flag bit positions ──────────────────────────────────
    public static final int FLAG_CF = 0x0001;  // Carry
    public static final int FLAG_PF = 0x0004;  // Parity
    public static final int FLAG_AF = 0x0010;  // Auxiliary carry
    public static final int FLAG_ZF = 0x0040;  // Zero
    public static final int FLAG_SF = 0x0080;  // Sign
    public static final int FLAG_TF = 0x0100;  // Trap
    public static final int FLAG_IF = 0x0200;  // Interrupt enable
    public static final int FLAG_DF = 0x0400;  // Direction
    public static final int FLAG_OF = 0x0800;  // Overflow
    public static final int FLAG_IOPL = 0x3000; // I/O privilege level
    public static final int FLAG_NT = 0x4000;  // Nested task

    /** The actual flags word */
    private int word;

    // ── Parity lookup table ─────────────────────────────────
    private static final boolean[] PARITY = new boolean[256];
    static {
        for (int i = 0; i < 256; i++) {
            PARITY[i] = (Integer.bitCount(i) & 1) == 0; // even parity → PF=1
        }
    }

    public Flags() {
        word = FLAG_IF | 0x0002; // bit 1 is always 1
    }

    // ── Bulk access ─────────────────────────────────────────

    public int getWord() { return word | 0x0002; }
    public void setWord(int v) { word = (v & 0xFFFF) | 0x0002; }

    // ── Individual flag access ──────────────────────────────

    public boolean getCF() { return (word & FLAG_CF) != 0; }
    public boolean getPF() { return (word & FLAG_PF) != 0; }
    public boolean getAF() { return (word & FLAG_AF) != 0; }
    public boolean getZF() { return (word & FLAG_ZF) != 0; }
    public boolean getSF() { return (word & FLAG_SF) != 0; }
    public boolean getTF() { return (word & FLAG_TF) != 0; }
    public boolean getIF() { return (word & FLAG_IF) != 0; }
    public boolean getDF() { return (word & FLAG_DF) != 0; }
    public boolean getOF() { return (word & FLAG_OF) != 0; }

    public void setCF(boolean v) { if (v) word |= FLAG_CF; else word &= ~FLAG_CF; }
    public void setPF(boolean v) { if (v) word |= FLAG_PF; else word &= ~FLAG_PF; }
    public void setAF(boolean v) { if (v) word |= FLAG_AF; else word &= ~FLAG_AF; }
    public void setZF(boolean v) { if (v) word |= FLAG_ZF; else word &= ~FLAG_ZF; }
    public void setSF(boolean v) { if (v) word |= FLAG_SF; else word &= ~FLAG_SF; }
    public void setTF(boolean v) { if (v) word |= FLAG_TF; else word &= ~FLAG_TF; }
    public void setIF(boolean v) { if (v) word |= FLAG_IF; else word &= ~FLAG_IF; }
    public void setDF(boolean v) { if (v) word |= FLAG_DF; else word &= ~FLAG_DF; }
    public void setOF(boolean v) { if (v) word |= FLAG_OF; else word &= ~FLAG_OF; }

    // ── Utility: set flags based on result ──────────────────

    /** Set SF, ZF, PF based on an 8-bit result. */
    public void setSZP8(int result) {
        result &= 0xFF;
        setSF((result & 0x80) != 0);
        setZF(result == 0);
        setPF(PARITY[result]);
    }

    /** Set SF, ZF, PF based on a 16-bit result. */
    public void setSZP16(int result) {
        result &= 0xFFFF;
        setSF((result & 0x8000) != 0);
        setZF(result == 0);
        setPF(PARITY[result & 0xFF]);
    }

    /** Set SF, ZF, PF based on a 32-bit result. */
    public void setSZP32(int result) {
        setSF((result & 0x80000000) != 0);
        setZF(result == 0);
        setPF(PARITY[result & 0xFF]);
    }

    /** Static parity check. */
    public static boolean parity(int val) {
        return PARITY[val & 0xFF];
    }

    public void reset() {
        word = FLAG_IF | 0x0002;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getOF() ? 'O' : 'o');
        sb.append(getDF() ? 'D' : 'd');
        sb.append(getIF() ? 'I' : 'i');
        sb.append(getTF() ? 'T' : 't');
        sb.append(getSF() ? 'S' : 's');
        sb.append(getZF() ? 'Z' : 'z');
        sb.append(getAF() ? 'A' : 'a');
        sb.append(getPF() ? 'P' : 'p');
        sb.append(getCF() ? 'C' : 'c');
        return sb.toString();
    }
}

