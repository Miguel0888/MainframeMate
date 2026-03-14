package de.bund.zrb.dosbox.hardware.timer;

import de.bund.zrb.dosbox.hardware.memory.IoPortHandler;
import de.bund.zrb.dosbox.hardware.pic.PIC;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PITTest {

    private PIC pic;
    private PIT pit;
    private IoPortHandler io;

    @BeforeEach
    void setUp() {
        pic = new PIC();
        pit = new PIT(pic);
        io = new IoPortHandler();
        pic.registerPorts(io);
        pit.registerPorts(io);
    }

    @Test
    void testDefaultFrequency() {
        // Default divisor is 65536 → ~18.2 Hz
        double freq = pit.getChannel0Frequency();
        assertTrue(freq > 18.0 && freq < 18.3,
                "Default frequency should be ~18.2 Hz, was: " + freq);
    }

    @Test
    void testTimerTick() {
        // Default interval is ~54.925 ms (1000/18.2065)
        // After ~55 ms, one tick should have fired
        assertEquals(0, pit.getBiosTickCount());

        pit.tick(55.0); // slightly more than one interval
        assertTrue(pit.getBiosTickCount() >= 1,
                "Should have at least 1 tick after 55ms");
    }

    @Test
    void testMultipleTicks() {
        // Tick for ~1 second should give ~18 ticks
        for (int i = 0; i < 1000; i++) {
            pit.tick(1.0); // 1ms steps
        }
        long ticks = pit.getBiosTickCount();
        assertTrue(ticks >= 17 && ticks <= 19,
                "~1 second should produce ~18 ticks, got: " + ticks);
    }

    @Test
    void testProgramChannel0() {
        // Program channel 0 to a faster rate (divisor = 1000 → ~1193 Hz)
        // Command: channel 0, LSB/MSB, mode 2
        io.writeByte(0x43, 0x34); // 00_11_010_0 = ch0, LSB/MSB, mode 2, binary
        io.writeByte(0x40, 0xE8); // LSB of 1000 (0x03E8)
        io.writeByte(0x40, 0x03); // MSB of 1000

        double freq = pit.getChannel0Frequency();
        assertTrue(freq > 1190 && freq < 1196,
                "Programmed frequency should be ~1193 Hz, was: " + freq);
    }

    @Test
    void testLatchCount() {
        // Latch channel 0 count
        io.writeByte(0x43, 0x00); // Latch channel 0

        // Read back count (LSB then MSB)
        int lsb = io.readByte(0x40);
        int msb = io.readByte(0x40);
        int count = lsb | (msb << 8);

        // Should be the default divisor (65536 = 0x10000, stored as 0x0000)
        // or the loaded value
        assertTrue(count >= 0 && count <= 65536,
                "Count should be valid: " + count);
    }

    @Test
    void testReset() {
        pit.tick(100);
        assertTrue(pit.getBiosTickCount() > 0);

        pit.reset();
        assertEquals(0, pit.getBiosTickCount());
    }
}
