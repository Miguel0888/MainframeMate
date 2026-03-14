package de.bund.zrb.dosbox.hardware.timer;

import de.bund.zrb.dosbox.core.Module;
import de.bund.zrb.dosbox.hardware.memory.IoPortHandler;
import de.bund.zrb.dosbox.hardware.pic.PIC;

/**
 * Intel 8253/8254 Programmable Interval Timer (PIT) emulation.
 * Provides three timer channels:
 *   Channel 0: System timer (IRQ 0) — used by DOS for clock ticks
 *   Channel 1: DRAM refresh (usually not used by software)
 *   Channel 2: Speaker tone generation
 *
 * I/O Ports:
 *   0x40 - Channel 0 data
 *   0x41 - Channel 1 data
 *   0x42 - Channel 2 data
 *   0x43 - Mode/Command register
 *
 * Ported from: src/hardware/timer.cpp
 */
public class PIT implements Module {

    /** PIT input clock frequency: 1,193,182 Hz */
    public static final double PIT_CLOCK = 1193182.0;

    /** Default divisor (65536 → ~18.2 Hz) */
    public static final int DEFAULT_DIVISOR = 65536;

    private final PIC pic;

    /** Per-channel state */
    private final Channel[] channels = new Channel[3];

    /** Accumulated time in milliseconds for IRQ 0 firing */
    private double accumulatedMs;

    /** Current interval in milliseconds for channel 0 */
    private double channel0IntervalMs;

    /** BIOS tick counter at 0040:006C (incremented by IRQ 0 handler) */
    private long biosTickCount;

    public PIT(PIC pic) {
        this.pic = pic;
        for (int i = 0; i < 3; i++) {
            channels[i] = new Channel();
        }
        reset();
    }

    @Override
    public void reset() {
        for (Channel ch : channels) {
            ch.reset();
        }
        accumulatedMs = 0;
        biosTickCount = 0;
        recalcInterval();
    }

    /** Register I/O ports with the port handler. */
    public void registerPorts(IoPortHandler io) {
        // Data ports for channels 0-2
        io.registerWrite(0x40, (port, val, w) -> writeData(0, val));
        io.registerWrite(0x41, (port, val, w) -> writeData(1, val));
        io.registerWrite(0x42, (port, val, w) -> writeData(2, val));

        io.registerRead(0x40, (port, w) -> readData(0));
        io.registerRead(0x41, (port, w) -> readData(1));
        io.registerRead(0x42, (port, w) -> readData(2));

        // Mode/Command register
        io.registerWrite(0x43, (port, val, w) -> writeCommand(val));
    }

    /**
     * Advance the timer by deltaMs milliseconds.
     * Should be called from the main emulation loop.
     * Fires IRQ 0 when channel 0 expires.
     */
    public void tick(double deltaMs) {
        accumulatedMs += deltaMs;

        while (accumulatedMs >= channel0IntervalMs) {
            accumulatedMs -= channel0IntervalMs;
            biosTickCount++;

            // Fire IRQ 0
            pic.raiseIRQ(0);
        }
    }

    /** Get the BIOS tick count (18.2 Hz ticks since startup). */
    public long getBiosTickCount() {
        return biosTickCount;
    }

    /** Get channel 0 frequency in Hz. */
    public double getChannel0Frequency() {
        int divisor = channels[0].reloadValue;
        if (divisor == 0) divisor = 65536;
        return PIT_CLOCK / divisor;
    }

    // ── I/O port handlers ───────────────────────────────────

    private void writeCommand(int val) {
        int channelIdx = (val >> 6) & 3;
        if (channelIdx == 3) {
            // Read-back command (8254 only) — stub
            return;
        }

        Channel ch = channels[channelIdx];
        int accessMode = (val >> 4) & 3;
        int opMode = (val >> 1) & 7;
        boolean bcd = (val & 1) != 0;

        if (accessMode == 0) {
            // Latch count value
            ch.latched = true;
            ch.latchedValue = ch.currentCount;
            return;
        }

        ch.accessMode = accessMode;
        ch.operatingMode = opMode;
        ch.bcd = bcd;
        ch.writeLSB = true; // next write is LSB
        ch.readLSB = true;
    }

    private void writeData(int channelIdx, int val) {
        Channel ch = channels[channelIdx];

        switch (ch.accessMode) {
            case 1: // LSB only
                ch.reloadValue = val & 0xFF;
                ch.loaded = true;
                break;
            case 2: // MSB only
                ch.reloadValue = (val & 0xFF) << 8;
                ch.loaded = true;
                break;
            case 3: // LSB then MSB
                if (ch.writeLSB) {
                    ch.reloadValue = (ch.reloadValue & 0xFF00) | (val & 0xFF);
                    ch.writeLSB = false;
                } else {
                    ch.reloadValue = (ch.reloadValue & 0x00FF) | ((val & 0xFF) << 8);
                    ch.writeLSB = true;
                    ch.loaded = true;
                }
                break;
        }

        if (ch.loaded) {
            ch.currentCount = ch.reloadValue;
            if (channelIdx == 0) {
                recalcInterval();
            }
        }
    }

    private int readData(int channelIdx) {
        Channel ch = channels[channelIdx];

        int value;
        if (ch.latched) {
            value = ch.latchedValue;
        } else {
            value = ch.currentCount;
        }

        switch (ch.accessMode) {
            case 1: // LSB only
                ch.latched = false;
                return value & 0xFF;
            case 2: // MSB only
                ch.latched = false;
                return (value >> 8) & 0xFF;
            case 3: // LSB then MSB
                if (ch.readLSB) {
                    ch.readLSB = false;
                    return value & 0xFF;
                } else {
                    ch.readLSB = true;
                    ch.latched = false;
                    return (value >> 8) & 0xFF;
                }
            default:
                return 0;
        }
    }

    private void recalcInterval() {
        int divisor = channels[0].reloadValue;
        if (divisor == 0) divisor = 65536; // divisor 0 means 65536
        channel0IntervalMs = (divisor / PIT_CLOCK) * 1000.0;
    }

    // ── Channel state ───────────────────────────────────────

    private static class Channel {
        int reloadValue = DEFAULT_DIVISOR;
        int currentCount = DEFAULT_DIVISOR;
        int accessMode = 3;     // LSB/MSB
        int operatingMode = 3;  // Square wave
        boolean bcd;
        boolean writeLSB = true;
        boolean readLSB = true;
        boolean latched;
        int latchedValue;
        boolean loaded;

        void reset() {
            reloadValue = DEFAULT_DIVISOR;
            currentCount = DEFAULT_DIVISOR;
            accessMode = 3;
            operatingMode = 3;
            bcd = false;
            writeLSB = true;
            readLSB = true;
            latched = false;
            latchedValue = 0;
            loaded = false;
        }
    }
}
