package de.bund.zrb.dosbox.cpu;

import de.bund.zrb.dosbox.hardware.memory.IoPortHandler;
import de.bund.zrb.dosbox.hardware.memory.Memory;
import de.bund.zrb.dosbox.hardware.pic.PIC;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CPUTest {

    private Memory memory;
    private IoPortHandler io;
    private PIC pic;
    private CPU cpu;

    @BeforeEach
    void setUp() {
        memory = new Memory();
        io = new IoPortHandler();
        pic = new PIC();
        cpu = new CPU(memory, io, pic);
        cpu.reset();
        // Set up a working code segment
        cpu.regs.cs = 0x0000;
        cpu.regs.ds = 0x0000;
        cpu.regs.es = 0x0000;
        cpu.regs.ss = 0x1000;
        cpu.regs.setSP(0xFFFE);
        cpu.regs.setIP(0x0000);
        cpu.regs.flags.setIF(false); // disable interrupts for tests
    }

    /** Write instructions into memory at CS:IP. */
    private void writeCode(int... bytes) {
        for (int i = 0; i < bytes.length; i++) {
            memory.writeByte(i, bytes[i]);
        }
    }

    @Test
    void testMovImm16() {
        // MOV AX, 0x1234
        writeCode(0xB8, 0x34, 0x12);
        cpu.setRunning(true);
        cpu.executeBlock(1);
        assertEquals(0x1234, cpu.regs.getAX());
    }

    @Test
    void testMovImm8() {
        // MOV AL, 0x42
        writeCode(0xB0, 0x42);
        cpu.setRunning(true);
        cpu.executeBlock(1);
        assertEquals(0x42, cpu.regs.getAL());
    }

    @Test
    void testAddReg16() {
        cpu.regs.setAX(0x1000);
        cpu.regs.setBX(0x0234);
        // ADD AX, BX (01 D8)
        writeCode(0x01, 0xD8);
        cpu.setRunning(true);
        cpu.executeBlock(1);
        assertEquals(0x1234, cpu.regs.getAX());
    }

    @Test
    void testSubImm16() {
        cpu.regs.setAX(0x1234);
        // SUB AX, 0x0034 (2D 34 00)
        writeCode(0x2D, 0x34, 0x00);
        cpu.setRunning(true);
        cpu.executeBlock(1);
        assertEquals(0x1200, cpu.regs.getAX());
        assertFalse(cpu.regs.flags.getZF());
        assertFalse(cpu.regs.flags.getCF());
    }

    @Test
    void testCmpAndJump() {
        cpu.regs.setAX(0x0005);
        // CMP AX, 5 (3D 05 00) → JZ +2 (74 02) → INC AX (40) → INC AX (40)
        // If equal: skip 2 bytes (both INC), IP should end at offset 7
        writeCode(0x3D, 0x05, 0x00,  // CMP AX, 5
                  0x74, 0x02,          // JZ +2
                  0x40,                // INC AX (skipped)
                  0x40,                // INC AX (skipped)
                  0x90);               // NOP (lands here)
        cpu.setRunning(true);
        cpu.executeBlock(3); // CMP, JZ, NOP
        assertEquals(0x0005, cpu.regs.getAX()); // AX unchanged (INC was skipped)
    }

    @Test
    void testPushPop() {
        cpu.regs.setAX(0xABCD);
        // PUSH AX (50), MOV AX, 0 (B8 00 00), POP BX (5B)
        writeCode(0x50,              // PUSH AX
                  0xB8, 0x00, 0x00,  // MOV AX, 0
                  0x5B);             // POP BX
        cpu.setRunning(true);
        cpu.executeBlock(3);
        assertEquals(0x0000, cpu.regs.getAX());
        assertEquals(0xABCD, cpu.regs.getBX());
    }

    @Test
    void testCallRet() {
        // CALL +3 (E8 03 00) → NOP NOP NOP → MOV AX, 0x42 (B8 42 00) → RET (C3)
        writeCode(0xE8, 0x03, 0x00,  // CALL +3 (to offset 6)
                  0x90, 0x90, 0x90,  // NOP NOP NOP (return target)
                  0xB8, 0x42, 0x00,  // MOV AX, 0x42
                  0xC3);             // RET
        cpu.setRunning(true);
        cpu.executeBlock(3); // CALL, MOV, RET
        assertEquals(0x0042, cpu.regs.getAX());
        assertEquals(3, cpu.regs.getIP()); // returned to after CALL
    }

    @Test
    void testLoop() {
        cpu.regs.setCX(3);
        cpu.regs.setAX(0);
        // Loop body: INC AX (40), LOOP -3 (E2 FD), HLT (F4)
        // LOOP displacement is relative to IP after LOOP instruction (offset 3)
        // -3 = 0xFD → target = 3 + (-3) = 0 (back to INC AX)
        writeCode(0x40,       // INC AX       (offset 0)
                  0xE2, 0xFD, // LOOP -3      (offset 1-2, target = offset 0)
                  0xF4);      // HLT          (offset 3)
        cpu.setRunning(true);
        cpu.executeBlock(20);
        assertEquals(3, cpu.regs.getAX());
        assertEquals(0, cpu.regs.getCX());
    }

    @Test
    void testSoftwareInt() {
        // Set up a Java handler for INT 42h
        cpu.setIntHandler(0x42, c -> c.regs.setAX(0xBEEF));
        // INT 42h (CD 42)
        writeCode(0xCD, 0x42);
        cpu.setRunning(true);
        cpu.executeBlock(1);
        assertEquals(0xBEEF, cpu.regs.getAX());
    }

    @Test
    void testMovsb() {
        // Set up source and destination
        cpu.regs.ds = 0x0000;
        cpu.regs.es = 0x0000;
        cpu.regs.setSI(0x1000);
        cpu.regs.setDI(0x2000);
        cpu.regs.setCX(5);
        memory.writeString(0x1000, "Hello");

        // REP MOVSB (F3 A4)
        writeCode(0xF3, 0xA4);
        cpu.setRunning(true);
        cpu.executeBlock(1);

        assertEquals("Hello", memory.readString(0x2000, 5));
        assertEquals(0, cpu.regs.getCX());
    }

    @Test
    void testStosb() {
        cpu.regs.es = 0x0000;
        cpu.regs.setDI(0x3000);
        cpu.regs.setCX(4);
        cpu.regs.setAL(0x41); // 'A'

        // REP STOSB (F3 AA)
        writeCode(0xF3, 0xAA);
        cpu.setRunning(true);
        cpu.executeBlock(1);

        assertEquals(0x41, memory.readByte(0x3000));
        assertEquals(0x41, memory.readByte(0x3001));
        assertEquals(0x41, memory.readByte(0x3002));
        assertEquals(0x41, memory.readByte(0x3003));
    }

    @Test
    void testFlagOperations() {
        // STC (F9), then CMC (F5)
        writeCode(0xF9, 0xF5);
        cpu.setRunning(true);
        cpu.executeBlock(2);
        assertFalse(cpu.regs.flags.getCF()); // was set, then complemented
    }

    @Test
    void testShiftLeft() {
        cpu.regs.setAX(0x0001);
        // SHL AX, 1 (D1 E0)
        writeCode(0xD1, 0xE0);
        cpu.setRunning(true);
        cpu.executeBlock(1);
        assertEquals(0x0002, cpu.regs.getAX());
    }

    @Test
    void testMultiplyByte() {
        cpu.regs.setAL(10);
        cpu.regs.setCL(20);
        // MUL CL (F6 E1)
        writeCode(0xF6, 0xE1);
        cpu.setRunning(true);
        cpu.executeBlock(1);
        assertEquals(200, cpu.regs.getAX());
    }
}

