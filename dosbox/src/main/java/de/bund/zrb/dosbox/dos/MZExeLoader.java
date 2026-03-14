package de.bund.zrb.dosbox.dos;

import de.bund.zrb.dosbox.hardware.memory.Memory;

/**
 * MZ (Mark Zbikowski) EXE file loader for DOS executables.
 * Parses the MZ header, applies relocations, and loads code into emulated memory.
 *
 * MZ EXE format:
 *   Offset  Size  Description
 *   0x00    2     Signature 'MZ' (0x5A4D)
 *   0x02    2     Bytes on last page
 *   0x04    2     Pages in file (512 bytes each)
 *   0x06    2     Number of relocations
 *   0x08    2     Header size in paragraphs (16 bytes each)
 *   0x0A    2     Minimum extra paragraphs needed (BSS)
 *   0x0C    2     Maximum extra paragraphs requested
 *   0x0E    2     Initial SS (relative to load segment)
 *   0x10    2     Initial SP
 *   0x12    2     Checksum
 *   0x14    2     Initial IP
 *   0x16    2     Initial CS (relative to load segment)
 *   0x18    2     Offset of relocation table
 *   0x1A    2     Overlay number
 */
public class MZExeLoader {

    /** Result of loading an EXE file. */
    public static class LoadResult {
        public boolean success;
        public String errorMessage;

        /** Segment where code was loaded */
        public int loadSegment;
        /** Initial CS value */
        public int initialCS;
        /** Initial IP value */
        public int initialIP;
        /** Initial SS value */
        public int initialSS;
        /** Initial SP value */
        public int initialSP;
        /** PSP segment */
        public int pspSegment;
        /** Total paragraphs used (for memory management) */
        public int totalParagraphs;

        static LoadResult error(String msg) {
            LoadResult r = new LoadResult();
            r.success = false;
            r.errorMessage = msg;
            return r;
        }

        static LoadResult ok() {
            LoadResult r = new LoadResult();
            r.success = true;
            return r;
        }
    }

    /**
     * Check if file data starts with MZ signature.
     */
    public static boolean isMZExe(byte[] data) {
        return data.length >= 2
                && (data[0] & 0xFF) == 0x4D   // 'M'
                && (data[1] & 0xFF) == 0x5A;   // 'Z'
    }

    /**
     * Load an MZ EXE file into emulated memory.
     *
     * @param data    Raw file bytes
     * @param memory  Emulated memory
     * @param loadSeg Segment where to start loading (after PSP)
     * @return LoadResult with register setup or error
     */
    public static LoadResult load(byte[] data, Memory memory, int loadSeg) {
        if (data.length < 0x1C) {
            return LoadResult.error("Datei zu klein fuer MZ-Header");
        }

        if (!isMZExe(data)) {
            return LoadResult.error("Keine gueltige MZ-EXE-Datei (Signatur fehlt)");
        }

        // Parse MZ header
        int bytesLastPage   = readWord(data, 0x02);
        int pagesInFile     = readWord(data, 0x04);
        int numRelocations  = readWord(data, 0x06);
        int headerParas     = readWord(data, 0x08);
        int minExtraParas   = readWord(data, 0x0A);
        int maxExtraParas   = readWord(data, 0x0C);
        int initSS          = readWord(data, 0x0E);
        int initSP          = readWord(data, 0x10);
        // int checksum     = readWord(data, 0x12); // ignored
        int initIP          = readWord(data, 0x14);
        int initCS          = readWord(data, 0x16);
        int relocTableOfs   = readWord(data, 0x18);
        // int overlayNum   = readWord(data, 0x1A); // ignored

        // Calculate code size
        int headerSize = headerParas * 16;
        int fileImageSize;
        if (pagesInFile == 0) {
            fileImageSize = data.length;
        } else {
            fileImageSize = (pagesInFile - 1) * 512 + bytesLastPage;
            if (bytesLastPage == 0) fileImageSize = pagesInFile * 512;
        }

        int codeSize = fileImageSize - headerSize;
        if (codeSize <= 0 || headerSize > data.length) {
            return LoadResult.error("Ungueltiger MZ-Header: Code-Groesse = " + codeSize);
        }
        if (headerSize + codeSize > data.length) {
            codeSize = data.length - headerSize; // truncate gracefully
        }

        // PSP is at loadSeg - 0x10 (256 bytes = 16 paragraphs before load segment)
        int pspSeg = loadSeg - 0x10;
        int pspAddr = Memory.segOfs(pspSeg, 0);

        // Set up PSP (Program Segment Prefix)
        setupPSP(memory, pspAddr, pspSeg, codeSize / 16 + minExtraParas + 0x10);

        // Load code into memory at loadSeg:0000
        int loadAddr = Memory.segOfs(loadSeg, 0);
        for (int i = 0; i < codeSize; i++) {
            int byteVal = data[headerSize + i] & 0xFF;
            memory.writeByte(loadAddr + i, byteVal);
        }

        // Apply relocations
        if (numRelocations > 0 && relocTableOfs + numRelocations * 4 <= data.length) {
            for (int i = 0; i < numRelocations; i++) {
                int relocOfs = relocTableOfs + i * 4;
                int ofs = readWord(data, relocOfs);
                int seg = readWord(data, relocOfs + 2);

                // Physical address of the relocation target
                int relocAddr = Memory.segOfs(loadSeg + seg, ofs);
                int currentVal = memory.readWord(relocAddr);
                memory.writeWord(relocAddr, (currentVal + loadSeg) & 0xFFFF);
            }
        }

        // Zero-fill BSS (minimum extra paragraphs after loaded code)
        int bssStart = loadAddr + codeSize;
        int bssSize = minExtraParas * 16;
        for (int i = 0; i < bssSize; i++) {
            memory.writeByte(bssStart + i, 0);
        }

        // Build result
        LoadResult result = LoadResult.ok();
        result.loadSegment = loadSeg;
        result.pspSegment = pspSeg;
        result.initialCS = loadSeg + initCS;
        result.initialIP = initIP;
        result.initialSS = loadSeg + initSS;
        result.initialSP = initSP;
        result.totalParagraphs = (codeSize + 15) / 16 + minExtraParas + 0x10;
        return result;
    }

    /**
     * Set up the Program Segment Prefix (PSP) at the given address.
     */
    private static void setupPSP(Memory memory, int pspAddr, int pspSeg, int memoryTopParas) {
        // Clear PSP (256 bytes)
        for (int i = 0; i < 256; i++) {
            memory.writeByte(pspAddr + i, 0);
        }

        // 0x00: INT 20h instruction (CD 20)
        memory.writeByte(pspAddr + 0x00, 0xCD);
        memory.writeByte(pspAddr + 0x01, 0x20);

        // 0x02: Memory size in paragraphs (top of memory for this program)
        memory.writeWord(pspAddr + 0x02, (pspSeg + memoryTopParas) & 0xFFFF);

        // 0x05: Far call to DOS dispatcher (INT 21h / RETF stub)
        memory.writeByte(pspAddr + 0x05, 0xCD);
        memory.writeByte(pspAddr + 0x06, 0x21);
        memory.writeByte(pspAddr + 0x07, 0xCB); // RETF

        // 0x2C: Environment segment (point to a minimal environment block)
        // We'll store a minimal environment right after the PSP
        int envSeg = pspSeg + 0x1C; // arbitrary, after PSP area
        memory.writeWord(pspAddr + 0x2C, envSeg);

        // Write minimal environment block at envSeg:0
        int envAddr = Memory.segOfs(envSeg, 0);
        // PATH=C:\
        String env = "PATH=C:\\\0COMSPEC=C:\\COMMAND.COM\0";
        for (int i = 0; i < env.length(); i++) {
            memory.writeByte(envAddr + i, env.charAt(i) & 0xFF);
        }
        // Double null terminates environment
        memory.writeByte(envAddr + env.length(), 0);
        // After environment: word with count of strings following (usually 1)
        memory.writeWord(envAddr + env.length() + 1, 1);
        // Program name (ASCIIZ)
        String progName = "C:\\PROGRAM.EXE\0";
        for (int i = 0; i < progName.length(); i++) {
            memory.writeByte(envAddr + env.length() + 3 + i, progName.charAt(i) & 0xFF);
        }

        // 0x80: Command tail length (0 = no arguments)
        memory.writeByte(pspAddr + 0x80, 0);
        // 0x81: Command tail (terminated by CR)
        memory.writeByte(pspAddr + 0x81, 0x0D);
    }

    /**
     * Write command-line arguments into the PSP command tail.
     */
    public static void setCommandTail(Memory memory, int pspAddr, String args) {
        if (args == null) args = "";
        if (args.length() > 126) args = args.substring(0, 126);

        // Command tail: space + args + CR
        String tail = " " + args;
        memory.writeByte(pspAddr + 0x80, tail.length() & 0xFF);
        for (int i = 0; i < tail.length(); i++) {
            memory.writeByte(pspAddr + 0x81 + i, tail.charAt(i) & 0xFF);
        }
        memory.writeByte(pspAddr + 0x81 + tail.length(), 0x0D);
    }

    // ── Little-endian word read from byte array ─────────────

    private static int readWord(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }
}
