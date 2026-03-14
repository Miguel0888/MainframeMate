package de.bund.zrb.dosbox.dos;

import de.bund.zrb.dosbox.cpu.CPU;
import de.bund.zrb.dosbox.hardware.memory.IoPortHandler;
import de.bund.zrb.dosbox.hardware.memory.Memory;
import de.bund.zrb.dosbox.hardware.pic.PIC;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProgramLoaderTest {

    private Memory memory;
    private CPU cpu;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        memory = new Memory();
        IoPortHandler io = new IoPortHandler();
        PIC pic = new PIC();
        cpu = new CPU(memory, io, pic);
        cpu.reset();
    }

    @Test
    void testLoadCOMFile() throws IOException {
        // Create a minimal COM file: MOV AX, 0x4C00; INT 21h (terminate)
        byte[] comCode = { (byte) 0xB8, 0x00, 0x4C, (byte) 0xCD, 0x21 };
        File comFile = tempDir.resolve("TEST.COM").toFile();
        Files.write(comFile.toPath(), comCode);

        ProgramLoader.LoadResult result = ProgramLoader.loadProgram(comFile, "", memory, cpu);

        assertTrue(result.success, "COM load should succeed");
        assertEquals("COM", result.programType);
        assertEquals("TEST.COM", result.programName);

        // CPU registers should be set for COM execution
        assertEquals(0x0100, cpu.regs.getIP(), "IP should be 0x0100 for COM files");
        assertEquals(cpu.regs.cs, cpu.regs.ds, "CS == DS for COM files");
        assertEquals(cpu.regs.cs, cpu.regs.es, "CS == ES for COM files");
        assertEquals(cpu.regs.cs, cpu.regs.ss, "CS == SS for COM files");
        assertEquals(0xFFFE, cpu.regs.getSP(), "SP should be 0xFFFE");

        // Verify code is in memory at CS:0100
        int codeAddr = Memory.segOfs(cpu.regs.cs, 0x0100);
        assertEquals(0xB8, memory.readByte(codeAddr));
        assertEquals(0x00, memory.readByte(codeAddr + 1));
        assertEquals(0x4C, memory.readByte(codeAddr + 2));

        // Verify PSP at CS:0000
        int pspAddr = Memory.segOfs(cpu.regs.cs, 0);
        assertEquals(0xCD, memory.readByte(pspAddr));     // INT
        assertEquals(0x20, memory.readByte(pspAddr + 1)); // 20h

        // Verify return address on stack
        int stackAddr = Memory.segOfs(cpu.regs.ss, cpu.regs.getSP());
        assertEquals(0x0000, memory.readWord(stackAddr), "Return address should be 0x0000 (PSP:0000 = INT 20h)");
    }

    @Test
    void testLoadMZExeFile() throws IOException {
        // Build a minimal MZ EXE
        byte[] code = { (byte) 0xB8, 0x00, 0x4C, (byte) 0xCD, 0x21 };
        byte[] exe = buildMinimalMZExe(code);
        File exeFile = tempDir.resolve("TEST.EXE").toFile();
        Files.write(exeFile.toPath(), exe);

        ProgramLoader.LoadResult result = ProgramLoader.loadProgram(exeFile, "", memory, cpu);

        assertTrue(result.success, "EXE load should succeed: " + result.errorMessage);
        assertEquals("EXE", result.programType);
        assertEquals("TEST.EXE", result.programName);
    }

    @Test
    void testLoadWithArguments() throws IOException {
        byte[] comCode = { (byte) 0xCD, 0x20 };
        File comFile = tempDir.resolve("TEST.COM").toFile();
        Files.write(comFile.toPath(), comCode);

        ProgramLoader.LoadResult result = ProgramLoader.loadProgram(comFile, "/V /MAP", memory, cpu);

        assertTrue(result.success);

        // Check command tail in PSP
        int pspAddr = Memory.segOfs(cpu.regs.cs, 0);
        int tailLen = memory.readByte(pspAddr + 0x80);
        assertTrue(tailLen > 0, "Command tail should have content");
        String tail = memory.readString(pspAddr + 0x81, tailLen);
        assertTrue(tail.contains("/V"), "Command tail should contain arguments");
    }

    @Test
    void testLoadNonExistentFile() {
        File noFile = tempDir.resolve("DOESNOTEXIST.COM").toFile();
        ProgramLoader.LoadResult result = ProgramLoader.loadProgram(noFile, "", memory, cpu);
        assertFalse(result.success);
        assertNotNull(result.errorMessage);
    }

    @Test
    void testLoadEmptyFile() throws IOException {
        File emptyFile = tempDir.resolve("EMPTY.COM").toFile();
        Files.write(emptyFile.toPath(), new byte[0]);

        ProgramLoader.LoadResult result = ProgramLoader.loadProgram(emptyFile, "", memory, cpu);
        assertFalse(result.success);
    }

    @Test
    void testCOMExecutionTerminatesViaInt20h() throws IOException {
        // COM file: push return addr (0) is already on stack
        // Code: INT 20h (program terminates, CPU stops)
        byte[] comCode = { (byte) 0xCD, 0x20 };
        File comFile = tempDir.resolve("QUIT.COM").toFile();
        Files.write(comFile.toPath(), comCode);

        // Set up INT 20h handler
        final boolean[] terminated = { false };
        cpu.setIntHandler(0x20, c -> {
            terminated[0] = true;
            c.setRunning(false);
        });

        ProgramLoader.loadProgram(comFile, "", memory, cpu);

        cpu.setRunning(true);
        cpu.executeBlock(10);

        assertTrue(terminated[0], "Program should have terminated via INT 20h");
    }

    // ── Helpers ─────────────────────────────────────────────

    private byte[] buildMinimalMZExe(byte[] code) {
        int headerParas = 2;
        int headerSize = headerParas * 16;
        int totalSize = headerSize + code.length;
        int pages = (totalSize + 511) / 512;
        int lastPageBytes = totalSize % 512;

        byte[] exe = new byte[totalSize];
        exe[0] = 0x4D; exe[1] = 0x5A;
        writeWord(exe, 0x02, lastPageBytes);
        writeWord(exe, 0x04, pages);
        writeWord(exe, 0x06, 0);
        writeWord(exe, 0x08, headerParas);
        writeWord(exe, 0x0A, 0);
        writeWord(exe, 0x0C, 0xFFFF);
        writeWord(exe, 0x0E, 0);
        writeWord(exe, 0x10, 0x100);
        writeWord(exe, 0x14, 0);
        writeWord(exe, 0x16, 0);
        writeWord(exe, 0x18, 0x1C);
        writeWord(exe, 0x1A, 0);
        System.arraycopy(code, 0, exe, headerSize, code.length);
        return exe;
    }

    private static void writeWord(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }
}
