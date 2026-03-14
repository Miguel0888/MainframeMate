package de.bund.zrb.dosbox.cpu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegsTest {

    private Regs regs;

    @BeforeEach
    void setUp() {
        regs = new Regs();
        regs.reset();
    }

    @Test
    void test16BitAccess() {
        regs.setAX(0x1234);
        assertEquals(0x1234, regs.getAX());
        assertEquals(0x34, regs.getAL());
        assertEquals(0x12, regs.getAH());
    }

    @Test
    void test8BitAccessPreservesOther() {
        regs.setAX(0x1234);
        regs.setAL(0xAB);
        assertEquals(0x12AB, regs.getAX());
        assertEquals(0xAB, regs.getAL());
        assertEquals(0x12, regs.getAH());
    }

    @Test
    void test8BitHighAccess() {
        regs.setAX(0x1234);
        regs.setAH(0xCD);
        assertEquals(0xCD34, regs.getAX());
    }

    @Test
    void test32BitAccess() {
        regs.setEAX(0xDEADBEEF);
        assertEquals(0xDEADBEEF, regs.getEAX());
        assertEquals(0xBEEF, regs.getAX());
        assertEquals(0xEF, regs.getAL());
        assertEquals(0xBE, regs.getAH());
    }

    @Test
    void test16BitPreserves32BitHigh() {
        regs.setEAX(0xDEADBEEF);
        regs.setAX(0x1234);
        assertEquals(0xDEAD1234, regs.getEAX());
    }

    @Test
    void testRegByIndex() {
        regs.setAX(0x1111);
        regs.setCX(0x2222);
        regs.setDX(0x3333);
        regs.setBX(0x4444);

        assertEquals(0x1111, regs.getReg16(0)); // AX
        assertEquals(0x2222, regs.getReg16(1)); // CX
        assertEquals(0x3333, regs.getReg16(2)); // DX
        assertEquals(0x4444, regs.getReg16(3)); // BX
    }

    @Test
    void testReg8ByIndex() {
        regs.setAX(0x1234);
        assertEquals(0x34, regs.getReg8(0)); // AL
        assertEquals(0x12, regs.getReg8(4)); // AH
    }

    @Test
    void testSegmentRegisters() {
        regs.setSeg(0, 0x1000); // ES
        regs.setSeg(1, 0x2000); // CS
        regs.setSeg(2, 0x3000); // SS
        regs.setSeg(3, 0x4000); // DS

        assertEquals(0x1000, regs.es);
        assertEquals(0x2000, regs.cs);
        assertEquals(0x3000, regs.ss);
        assertEquals(0x4000, regs.ds);
    }

    @Test
    void testReset() {
        regs.setEAX(0x12345678);
        regs.cs = 0x1234;
        regs.reset();

        assertEquals(0, regs.getEAX());
        assertEquals(0xF000, regs.cs);
        assertEquals(0xFFF0, regs.getIP());
    }
}

