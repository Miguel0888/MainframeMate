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

    public Int10Handler(Memory memory) {
        this.memory = memory;
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
            case 0x10: // Color/palette (stub)
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
        cursorRow = 0;
        cursorCol = 0;
        // Clear screen
        for (int i = 0; i < COLS * ROWS; i++) {
            memory.writeByte(TEXT_BASE + i * 2, 0x20); // space
            memory.writeByte(TEXT_BASE + i * 2 + 1, 0x07); // light gray on black
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
}

