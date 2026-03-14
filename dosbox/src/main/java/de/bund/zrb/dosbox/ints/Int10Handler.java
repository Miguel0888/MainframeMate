package de.bund.zrb.dosbox.ints;

import de.bund.zrb.dosbox.cpu.CPU;
import de.bund.zrb.dosbox.hardware.memory.Memory;

/**
 * INT 10h handler — BIOS Video Services (text mode only for MVP).
 *
 * Ported from: src/ints/int10.cpp, src/ints/int10_char.cpp
 */
public class Int10Handler implements CPU.IntHandler {

    private final Memory memory;
    private int cursorRow, cursorCol;
    private int activePage;
    private int currentMode = 3; // 80x25 color text

    public static final int COLS = 80;
    public static final int ROWS = 25;
    public static final int TEXT_BASE = Memory.TEXT_VIDEO_START; // 0xB8000

    // ── VGA Mode 13h (320x200x256) ──────────────────────────
    public static final int VGA_WIDTH = 320;
    public static final int VGA_HEIGHT = 200;
    public static final int VGA_BASE = Memory.VIDEO_RAM_START; // 0xA0000

    // Standard VGA 256-color palette (DAC)
    private final int[] vgaPalette = new int[256];

    public Int10Handler(Memory memory) {
        this.memory = memory;
        initDefaultPalette();
    }

    /** Initialize the default VGA 256-color palette. */
    private void initDefaultPalette() {
        // Standard 16 CGA colors
        int[] cga16 = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
            0xAA0000, 0xAA00AA, 0xAA5500, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
            0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
        };
        System.arraycopy(cga16, 0, vgaPalette, 0, 16);

        // Colors 16-231: 6x6x6 color cube
        int idx = 16;
        for (int r = 0; r < 6; r++) {
            for (int g = 0; g < 6; g++) {
                for (int b = 0; b < 6; b++) {
                    vgaPalette[idx++] = ((r * 51) << 16) | ((g * 51) << 8) | (b * 51);
                }
            }
        }
        // Colors 232-255: grayscale
        for (int i = 0; i < 24; i++) {
            int v = 8 + i * 10;
            vgaPalette[232 + i] = (v << 16) | (v << 8) | v;
        }
    }

    /** Get the VGA palette (for display rendering). */
    public int[] getVgaPalette() { return vgaPalette; }

    /** Get current video mode. */
    public int getCurrentMode() { return currentMode; }

    /** Is the current mode a graphics mode? */
    public boolean isGraphicsMode() {
        return currentMode == 0x13 || currentMode == 0x12 || currentMode == 0x0D || currentMode == 0x0E;
    }

    @Override
    public void handle(CPU cpu) {
        int ah = cpu.regs.getAH();
        switch (ah) {
            case 0x00: // Set video mode
                setMode(cpu.regs.getAL());
                break;
            case 0x01: // Set cursor type (stub)
                break;
            case 0x02: // Set cursor position
                setCursorPos(cpu.regs.getDH(), cpu.regs.getDL());
                break;
            case 0x03: // Get cursor position
                cpu.regs.setDH(cursorRow);
                cpu.regs.setDL(cursorCol);
                cpu.regs.setCX(0x0607); // cursor shape
                break;
            case 0x05: // Set active display page
                activePage = cpu.regs.getAL();
                break;
            case 0x06: // Scroll up
                scrollUp(cpu.regs.getAL(), cpu.regs.getBH(),
                        cpu.regs.getCH(), cpu.regs.getCL(),
                        cpu.regs.getDH(), cpu.regs.getDL());
                break;
            case 0x07: // Scroll down
                scrollDown(cpu.regs.getAL(), cpu.regs.getBH(),
                        cpu.regs.getCH(), cpu.regs.getCL(),
                        cpu.regs.getDH(), cpu.regs.getDL());
                break;
            case 0x08: { // Read character and attribute at cursor
                int addr = TEXT_BASE + (cursorRow * COLS + cursorCol) * 2;
                cpu.regs.setAL(memory.readByte(addr));
                cpu.regs.setAH(memory.readByte(addr + 1));
                break;
            }
            case 0x09: { // Write character and attribute at cursor
                int ch = cpu.regs.getAL();
                int attr = cpu.regs.getBL();
                int count = cpu.regs.getCX();
                for (int i = 0; i < count; i++) {
                    int pos = cursorRow * COLS + cursorCol + i;
                    if (pos >= COLS * ROWS) break;
                    int addr = TEXT_BASE + pos * 2;
                    memory.writeByte(addr, ch);
                    memory.writeByte(addr + 1, attr);
                }
                break;
            }
            case 0x0A: { // Write character only at cursor
                int ch = cpu.regs.getAL();
                int count = cpu.regs.getCX();
                for (int i = 0; i < count; i++) {
                    int pos = cursorRow * COLS + cursorCol + i;
                    if (pos >= COLS * ROWS) break;
                    memory.writeByte(TEXT_BASE + pos * 2, ch);
                }
                break;
            }
            case 0x0E: // Teletype output
                ttyOutput(cpu.regs.getAL(), cpu.regs.getBL());
                break;
            case 0x0F: // Get current video mode
                cpu.regs.setAL(currentMode);
                cpu.regs.setAH(COLS);
                cpu.regs.setBH(activePage);
                break;
            case 0x10: // Color/palette functions
                handlePalette(cpu);
                break;
            case 0x11: // Character generator (stub)
                break;
            case 0x12: // Alternate select (stub)
                cpu.regs.setAL(0x12); // indicate function supported
                break;
            case 0x1A: // Display combination code
                cpu.regs.setAL(0x1A);
                cpu.regs.setBL(0x08); // VGA with color analog
                break;
            default:
                break;
        }
    }

    private void setMode(int mode) {
        currentMode = mode & 0x7F;
        boolean noClear = (mode & 0x80) != 0;
        cursorRow = 0;
        cursorCol = 0;

        if (currentMode == 0x13) {
            // VGA Mode 13h: 320x200, 256 colors, linear at A0000
            if (!noClear) {
                for (int i = 0; i < VGA_WIDTH * VGA_HEIGHT; i++) {
                    memory.writeByte(VGA_BASE + i, 0);
                }
            }
            System.out.println("[VGA] Switched to Mode 13h (320x200x256)");
        } else {
            // Text mode: clear screen
            if (!noClear) {
                for (int i = 0; i < COLS * ROWS; i++) {
                    memory.writeByte(TEXT_BASE + i * 2, 0x20);
                    memory.writeByte(TEXT_BASE + i * 2 + 1, 0x07);
                }
            }
        }
    }

    // Note: public setCursorPos and setCursorPos_internal are defined at bottom of class

    /** Teletype-style character output with auto-scroll. */
    public void ttyOutput(int ch, int attr) {
        switch (ch) {
            case 0x07: // BEL
                break;
            case 0x08: // BS
                if (cursorCol > 0) cursorCol--;
                break;
            case 0x09: // TAB
                cursorCol = (cursorCol + 8) & ~7;
                if (cursorCol >= COLS) { cursorCol = 0; advanceLine(); }
                break;
            case 0x0A: // LF
                advanceLine();
                break;
            case 0x0D: // CR
                cursorCol = 0;
                break;
            default:
                int addr = TEXT_BASE + (cursorRow * COLS + cursorCol) * 2;
                memory.writeByte(addr, ch);
                memory.writeByte(addr + 1, 0x07);
                cursorCol++;
                if (cursorCol >= COLS) {
                    cursorCol = 0;
                    advanceLine();
                }
                break;
        }
    }

    private void advanceLine() {
        cursorRow++;
        if (cursorRow >= ROWS) {
            cursorRow = ROWS - 1;
            scrollUp(1, 0x07, 0, 0, ROWS - 1, COLS - 1);
        }
    }

    private void scrollUp(int lines, int attr, int topRow, int leftCol, int botRow, int rightCol) {
        if (lines == 0) {
            // Clear window
            for (int r = topRow; r <= botRow; r++) {
                for (int c = leftCol; c <= rightCol; c++) {
                    int addr = TEXT_BASE + (r * COLS + c) * 2;
                    memory.writeByte(addr, 0x20);
                    memory.writeByte(addr + 1, attr);
                }
            }
            return;
        }
        for (int r = topRow; r <= botRow - lines; r++) {
            for (int c = leftCol; c <= rightCol; c++) {
                int srcAddr = TEXT_BASE + ((r + lines) * COLS + c) * 2;
                int dstAddr = TEXT_BASE + (r * COLS + c) * 2;
                memory.writeByte(dstAddr, memory.readByte(srcAddr));
                memory.writeByte(dstAddr + 1, memory.readByte(srcAddr + 1));
            }
        }
        for (int r = botRow - lines + 1; r <= botRow; r++) {
            for (int c = leftCol; c <= rightCol; c++) {
                int addr = TEXT_BASE + (r * COLS + c) * 2;
                memory.writeByte(addr, 0x20);
                memory.writeByte(addr + 1, attr);
            }
        }
    }

    private void scrollDown(int lines, int attr, int topRow, int leftCol, int botRow, int rightCol) {
        if (lines == 0) { scrollUp(0, attr, topRow, leftCol, botRow, rightCol); return; }
        for (int r = botRow; r >= topRow + lines; r--) {
            for (int c = leftCol; c <= rightCol; c++) {
                int srcAddr = TEXT_BASE + ((r - lines) * COLS + c) * 2;
                int dstAddr = TEXT_BASE + (r * COLS + c) * 2;
                memory.writeByte(dstAddr, memory.readByte(srcAddr));
                memory.writeByte(dstAddr + 1, memory.readByte(srcAddr + 1));
            }
        }
        for (int r = topRow; r < topRow + lines; r++) {
            for (int c = leftCol; c <= rightCol; c++) {
                int addr = TEXT_BASE + (r * COLS + c) * 2;
                memory.writeByte(addr, 0x20);
                memory.writeByte(addr + 1, attr);
            }
        }
    }

    public int getCursorRow() { return cursorRow; }
    public int getCursorCol() { return cursorCol; }

    /** Direct access to the memory instance (for CLS etc.). */
    public Memory getMemory() { return memory; }

    /** Set cursor position directly (public for shell CLS). */
    public void setCursorPos(int row, int col) {
        setCursorPos_internal(row, col);
    }

    private void setCursorPos_internal(int row, int col) {
        cursorRow = Math.min(row, ROWS - 1);
        cursorCol = Math.min(col, COLS - 1);
    }

    // ── VGA Palette (INT 10h, AH=10h) ──────────────────────

    private void handlePalette(CPU cpu) {
        int al = cpu.regs.getAL();
        switch (al) {
            case 0x10: { // Set individual DAC register
                int regNum = cpu.regs.getBX();
                // VGA DAC uses 6-bit values (0-63), scale to 8-bit
                int r = (cpu.regs.getDH() & 0x3F) * 255 / 63;
                int g = (cpu.regs.getCH() & 0x3F) * 255 / 63;
                int b = (cpu.regs.getCL() & 0x3F) * 255 / 63;
                if (regNum >= 0 && regNum < 256) {
                    vgaPalette[regNum] = (r << 16) | (g << 8) | b;
                }
                break;
            }
            case 0x12: { // Set block of DAC registers
                int start = cpu.regs.getBX();
                int count = cpu.regs.getCX();
                int addr = cpu.resolveSegOfs(cpu.regs.es, cpu.regs.getDX());
                for (int i = 0; i < count && (start + i) < 256; i++) {
                    int r = (memory.readByte(addr + i * 3) & 0x3F) * 255 / 63;
                    int g = (memory.readByte(addr + i * 3 + 1) & 0x3F) * 255 / 63;
                    int b = (memory.readByte(addr + i * 3 + 2) & 0x3F) * 255 / 63;
                    vgaPalette[start + i] = (r << 16) | (g << 8) | b;
                }
                break;
            }
            case 0x15: { // Read individual DAC register
                int regNum = cpu.regs.getBX();
                if (regNum >= 0 && regNum < 256) {
                    int color = vgaPalette[regNum];
                    cpu.regs.setDH(((color >> 16) & 0xFF) * 63 / 255);
                    cpu.regs.setCH(((color >> 8) & 0xFF) * 63 / 255);
                    cpu.regs.setCL((color & 0xFF) * 63 / 255);
                }
                break;
            }
            case 0x17: { // Read block of DAC registers
                int start = cpu.regs.getBX();
                int count = cpu.regs.getCX();
                int addr = cpu.resolveSegOfs(cpu.regs.es, cpu.regs.getDX());
                for (int i = 0; i < count && (start + i) < 256; i++) {
                    int color = vgaPalette[start + i];
                    memory.writeByte(addr + i * 3, ((color >> 16) & 0xFF) * 63 / 255);
                    memory.writeByte(addr + i * 3 + 1, ((color >> 8) & 0xFF) * 63 / 255);
                    memory.writeByte(addr + i * 3 + 2, (color & 0xFF) * 63 / 255);
                }
                break;
            }
            default:
                // Other palette functions — stub
                break;
        }
    }
}

