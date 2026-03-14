package de.bund.zrb.dosbox.dos;

import de.bund.zrb.dosbox.hardware.memory.Memory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MZExeLoaderTest {

    private Memory memory;

    @BeforeEach
    void setUp() {
        memory = new Memory();
    }

    /**
     * Build a minimal MZ EXE file in memory.
     */
    private byte[] buildMinimalMZExe(byte[] code, int initCS, int initIP, int initSS, int initSP) {
        int headerParas = 2; // 32 bytes header (minimum)
        int headerSize = headerParas * 16;
        int totalSize = headerSize + code.length;
        int pages = (totalSize + 511) / 512;
        int lastPageBytes = totalSize % 512;

        byte[] exe = new byte[totalSize];

        // MZ signature
        exe[0] = 0x4D; // 'M'
        exe[1] = 0x5A; // 'Z'

        // Bytes on last page
        writeWord(exe, 0x02, lastPageBytes);
        // Pages in file
        writeWord(exe, 0x04, pages);
        // Number of relocations
        writeWord(exe, 0x06, 0);
        // Header size in paragraphs
        writeWord(exe, 0x08, headerParas);
        // Min extra paragraphs
        writeWord(exe, 0x0A, 0);
        // Max extra paragraphs
        writeWord(exe, 0x0C, 0xFFFF);
        // Initial SS
        writeWord(exe, 0x0E, initSS);
        // Initial SP
        writeWord(exe, 0x10, initSP);
        // Checksum
        writeWord(exe, 0x12, 0);
        // Initial IP
        writeWord(exe, 0x14, initIP);
        // Initial CS
        writeWord(exe, 0x16, initCS);
        // Relocation table offset
        writeWord(exe, 0x18, 0x1C);
        // Overlay number
        writeWord(exe, 0x1A, 0);

        // Copy code after header
        System.arraycopy(code, 0, exe, headerSize, code.length);

        return exe;
    }

    @Test
    void testIsMZExe() {
        byte[] mz = { 0x4D, 0x5A, 0x00, 0x00 };
        assertTrue(MZExeLoader.isMZExe(mz));

        byte[] notMz = { 0x00, 0x00, 0x00, 0x00 };
        assertFalse(MZExeLoader.isMZExe(notMz));

        byte[] com = { (byte) 0xCD, 0x21 };
        assertFalse(MZExeLoader.isMZExe(com));
    }

    @Test
    void testLoadMinimalExe() {
        // Simple code: MOV AX, 0x1234; INT 21h (terminate)
        byte[] code = { (byte) 0xB8, 0x34, 0x12, (byte) 0xCD, 0x21 };
        byte[] exe = buildMinimalMZExe(code, 0, 0, 0x10, 0x100);

        int loadSeg = 0x0810; // after PSP
        MZExeLoader.LoadResult result = MZExeLoader.load(exe, memory, loadSeg);

        assertTrue(result.success, "Load should succeed");
        assertEquals(loadSeg, result.loadSegment);
        assertEquals(loadSeg, result.initialCS); // initCS = 0 + loadSeg
        assertEquals(0, result.initialIP);
        assertEquals(loadSeg + 0x10, result.initialSS); // initSS = 0x10 + loadSeg
        assertEquals(0x100, result.initialSP);

        // Verify code is in memory at loadSeg:0000
        int codeAddr = Memory.segOfs(loadSeg, 0);
        assertEquals(0xB8, memory.readByte(codeAddr));     // MOV AX, imm16
        assertEquals(0x34, memory.readByte(codeAddr + 1)); // low byte
        assertEquals(0x12, memory.readByte(codeAddr + 2)); // high byte
    }

    @Test
    void testLoadWithRelocations() {
        // Code: MOV AX, [seg:0000] where seg needs relocation
        byte[] code = { (byte) 0xB8, 0x00, 0x00 }; // MOV AX, 0x0000

        int headerParas = 2;
        int headerSize = headerParas * 16;
        int totalSize = headerSize + code.length + 4; // +4 for relocation entry space
        int pages = (totalSize + 511) / 512;
        int lastPageBytes = totalSize % 512;

        byte[] exe = new byte[headerSize + code.length];

        // MZ header
        exe[0] = 0x4D; exe[1] = 0x5A;
        writeWord(exe, 0x02, lastPageBytes);
        writeWord(exe, 0x04, pages);
        writeWord(exe, 0x06, 1); // 1 relocation
        writeWord(exe, 0x08, headerParas);
        writeWord(exe, 0x0A, 0);
        writeWord(exe, 0x0C, 0xFFFF);
        writeWord(exe, 0x0E, 0); // SS
        writeWord(exe, 0x10, 0x100); // SP
        writeWord(exe, 0x14, 0); // IP
        writeWord(exe, 0x16, 0); // CS
        writeWord(exe, 0x18, 0x1C); // reloc table at offset 0x1C

        // Relocation entry: offset 1, segment 0 (points to the imm16 in MOV AX)
        writeWord(exe, 0x1C, 0x0001); // offset within code
        writeWord(exe, 0x1E, 0x0000); // segment

        // Code
        System.arraycopy(code, 0, exe, headerSize, code.length);

        int loadSeg = 0x0810;
        MZExeLoader.LoadResult result = MZExeLoader.load(exe, memory, loadSeg);

        assertTrue(result.success);

        // The word at code offset 1 should be 0x0000 + loadSeg
        int relocatedAddr = Memory.segOfs(loadSeg, 1);
        int relocatedValue = memory.readWord(relocatedAddr);
        assertEquals(loadSeg, relocatedValue, "Relocation should add loadSeg to the original value");
    }

    @Test
    void testPSPSetup() {
        byte[] code = { (byte) 0xCD, 0x20 }; // INT 20h (terminate)
        byte[] exe = buildMinimalMZExe(code, 0, 0, 0, 0x100);

        int loadSeg = 0x0810;
        MZExeLoader.LoadResult result = MZExeLoader.load(exe, memory, loadSeg);

        assertTrue(result.success);

        // PSP should be 16 paragraphs before load segment
        int pspAddr = Memory.segOfs(result.pspSegment, 0);

        // PSP starts with INT 20h (CD 20)
        assertEquals(0xCD, memory.readByte(pspAddr));
        assertEquals(0x20, memory.readByte(pspAddr + 1));

        // Far call to DOS at PSP:0005
        assertEquals(0xCD, memory.readByte(pspAddr + 5));
        assertEquals(0x21, memory.readByte(pspAddr + 6));
        assertEquals(0xCB, memory.readByte(pspAddr + 7)); // RETF
    }

    @Test
    void testCommandTail() {
        byte[] code = { (byte) 0xCD, 0x20 };
        byte[] exe = buildMinimalMZExe(code, 0, 0, 0, 0x100);

        int loadSeg = 0x0810;
        MZExeLoader.LoadResult result = MZExeLoader.load(exe, memory, loadSeg);
        assertTrue(result.success);

        int pspAddr = Memory.segOfs(result.pspSegment, 0);
        MZExeLoader.setCommandTail(memory, pspAddr, "/NOLOGO /MAP");

        // Command tail length at PSP:0x80
        int tailLen = memory.readByte(pspAddr + 0x80);
        assertEquals(13, tailLen); // " /NOLOGO /MAP" = 13 chars

        // Read command tail
        String tail = memory.readString(pspAddr + 0x81, tailLen);
        assertEquals(" /NOLOGO /MAP", tail);
    }

    @Test
    void testRejectInvalidFile() {
        byte[] garbage = { 0x00, 0x00, 0x00, 0x00 };
        MZExeLoader.LoadResult result = MZExeLoader.load(garbage, memory, 0x0800);
        assertFalse(result.success);
        assertNotNull(result.errorMessage);
    }

    @Test
    void testRejectTooSmall() {
        byte[] tiny = { 0x4D }; // just 'M', no 'Z'
        MZExeLoader.LoadResult result = MZExeLoader.load(tiny, memory, 0x0800);
        assertFalse(result.success);
    }

    // ── Helpers ─────────────────────────────────────────────

    private static void writeWord(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }
}
