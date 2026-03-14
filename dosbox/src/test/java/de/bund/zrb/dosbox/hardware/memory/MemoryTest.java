package de.bund.zrb.dosbox.hardware.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemoryTest {

    private Memory memory;

    @BeforeEach
    void setUp() {
        memory = new Memory();
    }

    @Test
    void testByteReadWrite() {
        memory.writeByte(0x1000, 0xAB);
        assertEquals(0xAB, memory.readByte(0x1000));
    }

    @Test
    void testWordReadWrite() {
        memory.writeWord(0x2000, 0x1234);
        assertEquals(0x1234, memory.readWord(0x2000));
        // Little-endian check
        assertEquals(0x34, memory.readByte(0x2000));
        assertEquals(0x12, memory.readByte(0x2001));
    }

    @Test
    void testDWordReadWrite() {
        memory.writeDWord(0x3000, 0x12345678L);
        assertEquals(0x12345678L, memory.readDWord(0x3000));
        assertEquals(0x5678, memory.readWord(0x3000));
        assertEquals(0x1234, memory.readWord(0x3002));
    }

    @Test
    void testSegOfs() {
        // Segment 0x1000, Offset 0x0100 = physical 0x10100
        assertEquals(0x10100, Memory.segOfs(0x1000, 0x0100));
        // Segment 0xB800, Offset 0x0000 = physical 0xB8000
        assertEquals(0xB8000, Memory.segOfs(0xB800, 0x0000));
    }

    @Test
    void testStringReadWrite() {
        memory.writeString(0x5000, "Hello");
        assertEquals("Hello", memory.readString(0x5000, 10));
    }

    @Test
    void testFill() {
        memory.fill(0x6000, 10, 0xFF);
        for (int i = 0; i < 10; i++) {
            assertEquals(0xFF, memory.readByte(0x6000 + i));
        }
    }

    @Test
    void testBlockReadWrite() {
        byte[] src = {0x01, 0x02, 0x03, 0x04};
        memory.writeBlock(0x7000, src, 0, 4);
        byte[] dst = new byte[4];
        memory.readBlock(0x7000, dst, 0, 4);
        assertArrayEquals(src, dst);
    }

    @Test
    void testBoundsProtection() {
        // Writing beyond memory should not throw
        memory.writeByte(Memory.MEMORY_SIZE + 100, 0x42);
        assertEquals(0xFF, memory.readByte(Memory.MEMORY_SIZE + 100)); // should read 0xFF (out of bounds)
    }

    @Test
    void testReset() {
        memory.writeByte(0x1000, 0xAB);
        memory.reset();
        assertEquals(0, memory.readByte(0x1000));
    }
}

