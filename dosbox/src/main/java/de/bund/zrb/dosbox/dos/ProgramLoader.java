package de.bund.zrb.dosbox.dos;

import de.bund.zrb.dosbox.cpu.CPU;
import de.bund.zrb.dosbox.hardware.memory.Memory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Unified program loader for DOS executables.
 * Loads .COM and .EXE (MZ) files into emulated memory
 * and prepares the CPU registers for execution.
 *
 * This replaces the need for a native DOSBox — everything
 * runs inside the Java CPU emulator.
 */
public class ProgramLoader {

    /** Base segment for loading programs. */
    private static final int DEFAULT_LOAD_SEG = 0x0800;

    /** Result of a program load operation. */
    public static class LoadResult {
        public boolean success;
        public String errorMessage;
        public String programName;
        public String programType; // "COM" or "EXE"
        public int pspSegment;
        public int codeSegment;
        public int codeSize;

        static LoadResult error(String msg) {
            LoadResult r = new LoadResult();
            r.success = false;
            r.errorMessage = msg;
            return r;
        }
    }

    /**
     * Load and prepare a DOS program for execution.
     *
     * @param exeFile  The file to load (.COM or .EXE)
     * @param args     Command-line arguments
     * @param memory   Emulated memory
     * @param cpu      CPU to configure registers
     * @return LoadResult with status information
     */
    public static LoadResult loadProgram(File exeFile, String args, Memory memory, CPU cpu) {
        if (!exeFile.exists() || !exeFile.isFile()) {
            return LoadResult.error("Datei nicht gefunden: " + exeFile.getAbsolutePath());
        }

        byte[] data;
        try {
            data = Files.readAllBytes(exeFile.toPath());
        } catch (IOException e) {
            return LoadResult.error("Fehler beim Lesen: " + e.getMessage());
        }

        if (data.length == 0) {
            return LoadResult.error("Datei ist leer: " + exeFile.getName());
        }

        String name = exeFile.getName().toUpperCase();
        LoadResult result = new LoadResult();
        result.programName = name;

        if (MZExeLoader.isMZExe(data)) {
            // ── MZ EXE loading ──────────────────────────────
            result.programType = "EXE";
            return loadMZExe(data, args, memory, cpu, result);
        } else {
            // ── COM loading ─────────────────────────────────
            result.programType = "COM";
            return loadCOM(data, args, memory, cpu, result);
        }
    }

    /**
     * Load an MZ EXE file.
     */
    private static LoadResult loadMZExe(byte[] data, String args,
                                         Memory memory, CPU cpu, LoadResult result) {
        int loadSeg = DEFAULT_LOAD_SEG + 0x10; // leave room for PSP

        MZExeLoader.LoadResult mzResult = MZExeLoader.load(data, memory, loadSeg);
        if (!mzResult.success) {
            result.success = false;
            result.errorMessage = mzResult.errorMessage;
            return result;
        }

        // Set command-line arguments in PSP
        int pspAddr = Memory.segOfs(mzResult.pspSegment, 0);
        MZExeLoader.setCommandTail(memory, pspAddr, args);

        // Configure CPU registers
        cpu.regs.cs = mzResult.initialCS;
        cpu.regs.setIP(mzResult.initialIP);
        cpu.regs.ss = mzResult.initialSS;
        cpu.regs.setSP(mzResult.initialSP);
        cpu.regs.ds = mzResult.pspSegment;
        cpu.regs.es = mzResult.pspSegment;
        cpu.regs.setAX(0);
        cpu.regs.setBX(0);
        cpu.regs.setCX(0);
        cpu.regs.setDX(0);
        cpu.regs.setSI(0);
        cpu.regs.setDI(0);
        cpu.regs.setBP(0);

        result.success = true;
        result.pspSegment = mzResult.pspSegment;
        result.codeSegment = mzResult.loadSegment;
        result.codeSize = data.length;
        return result;
    }

    /**
     * Load a COM file (raw binary at offset 0x100).
     */
    private static LoadResult loadCOM(byte[] data, String args,
                                       Memory memory, CPU cpu, LoadResult result) {
        if (data.length > 65280) { // 0xFF00 max for COM
            result.success = false;
            result.errorMessage = "COM-Datei zu gross (max 65280 Bytes)";
            return result;
        }

        int loadSeg = DEFAULT_LOAD_SEG;
        int pspAddr = Memory.segOfs(loadSeg, 0);
        int codeAddr = Memory.segOfs(loadSeg, 0x0100);

        // Set up PSP
        setupCOMPSP(memory, pspAddr, loadSeg);

        // Set command tail
        MZExeLoader.setCommandTail(memory, pspAddr, args);

        // Load COM code at seg:0100
        memory.writeBlock(codeAddr, data, 0, data.length);

        // Push return address (0x0000) onto stack — INT 20h at PSP:0000 will terminate
        int stackAddr = Memory.segOfs(loadSeg, 0xFFFE);
        memory.writeWord(stackAddr, 0x0000);

        // Configure CPU registers (all segments point to PSP segment for COM files)
        cpu.regs.cs = loadSeg;
        cpu.regs.ds = loadSeg;
        cpu.regs.es = loadSeg;
        cpu.regs.ss = loadSeg;
        cpu.regs.setIP(0x0100);
        cpu.regs.setSP(0xFFFE);
        cpu.regs.setAX(0);
        cpu.regs.setBX(0);
        cpu.regs.setCX(0);
        cpu.regs.setDX(0);
        cpu.regs.setSI(0);
        cpu.regs.setDI(0);
        cpu.regs.setBP(0);

        result.success = true;
        result.pspSegment = loadSeg;
        result.codeSegment = loadSeg;
        result.codeSize = data.length;
        return result;
    }

    /**
     * Set up a minimal PSP for COM file execution.
     */
    private static void setupCOMPSP(Memory memory, int pspAddr, int pspSeg) {
        // Clear PSP
        for (int i = 0; i < 256; i++) {
            memory.writeByte(pspAddr + i, 0);
        }

        // INT 20h at PSP:0000
        memory.writeByte(pspAddr + 0x00, 0xCD);
        memory.writeByte(pspAddr + 0x01, 0x20);

        // Memory top
        memory.writeWord(pspAddr + 0x02, 0x9FFF);

        // Far call to DOS (INT 21h, RETF)
        memory.writeByte(pspAddr + 0x05, 0xCD);
        memory.writeByte(pspAddr + 0x06, 0x21);
        memory.writeByte(pspAddr + 0x07, 0xCB);

        // Default DTA at PSP:0080
        // (DTA offset is set by default in DOS)

        // Command tail
        memory.writeByte(pspAddr + 0x80, 0);
        memory.writeByte(pspAddr + 0x81, 0x0D);
    }
}
