package de.bund.zrb.dosbox.hardware.pic;

import de.bund.zrb.dosbox.core.Module;
import de.bund.zrb.dosbox.hardware.memory.IoPortHandler;

/**
 * Dual 8259A PIC (Programmable Interrupt Controller) emulation.
 * Handles IRQ routing, masking, and the timed-event queue.
 *
 * Ported from: src/hardware/pic.cpp, include/pic.h
 */
public class PIC implements Module {

    /** Ticks per millisecond (base cycle rate) */
    public static final double TICK_RATE = 14318180.0 / 12.0; // ~1.193 MHz PIT frequency reference

    /** Timed event callback */
    @FunctionalInterface
    public interface EventHandler {
        void handle(int val);
    }

    /** A single PIC chip (master or slave) */
    public static class PicChip {
        int irr;       // Interrupt Request Register
        int isr;       // In-Service Register
        int imr;       // Interrupt Mask Register (IMR)
        int icwState;  // ICW state machine
        int vectorBase; // Base interrupt vector
        boolean autoEoi;
        boolean readIsr; // Read ISR instead of IRR

        void reset() {
            irr = 0;
            isr = 0;
            imr = 0xFF; // all masked
            icwState = 0;
            vectorBase = 0;
            autoEoi = false;
            readIsr = false;
        }
    }

    /** Timed event entry */
    private static class PicEvent {
        double triggerTime;
        EventHandler handler;
        int value;
        PicEvent next;
    }

    private final PicChip master = new PicChip();
    private final PicChip slave = new PicChip();
    private PicEvent eventHead;
    private double currentTime;

    // Pending interrupt state
    private boolean irqPending;
    private int pendingIrq;

    public PIC() {
        reset();
    }

    @Override
    public void reset() {
        master.reset();
        slave.reset();
        master.vectorBase = 0x08; // IRQ 0-7 → INT 08h-0Fh
        slave.vectorBase = 0x70;  // IRQ 8-15 → INT 70h-77h
        master.imr = 0;
        slave.imr = 0;
        eventHead = null;
        currentTime = 0;
        irqPending = false;
    }

    /** Register I/O ports with the port handler. */
    public void registerPorts(IoPortHandler io) {
        // Master PIC: ports 0x20-0x21
        io.registerWrite(0x20, (port, val, w) -> writeMasterCommand(val));
        io.registerWrite(0x21, (port, val, w) -> writeMasterData(val));
        io.registerRead(0x20, (port, w) -> readMasterCommand());
        io.registerRead(0x21, (port, w) -> master.imr);

        // Slave PIC: ports 0xA0-0xA1
        io.registerWrite(0xA0, (port, val, w) -> writeSlaveCommand(val));
        io.registerWrite(0xA1, (port, val, w) -> writeSlaveData(val));
        io.registerRead(0xA0, (port, w) -> readSlaveCommand());
        io.registerRead(0xA1, (port, w) -> slave.imr);
    }

    // ── IRQ management ──────────────────────────────────────

    /** Raise an IRQ line. */
    public void raiseIRQ(int irq) {
        if (irq < 8) {
            master.irr |= (1 << irq);
        } else {
            slave.irr |= (1 << (irq - 8));
            master.irr |= (1 << 2); // cascade on IRQ2
        }
        checkPending();
    }

    /** Lower an IRQ line. */
    public void lowerIRQ(int irq) {
        if (irq < 8) {
            master.irr &= ~(1 << irq);
        } else {
            slave.irr &= ~(1 << (irq - 8));
        }
    }

    /** Check if an interrupt is pending and return the vector, or -1. */
    public int checkInterrupt() {
        if (!irqPending) return -1;
        irqPending = false;
        int irq = pendingIrq;
        // Set in-service, clear request
        if (irq < 8) {
            master.isr |= (1 << irq);
            master.irr &= ~(1 << irq);
            if (master.autoEoi) master.isr &= ~(1 << irq);
            return master.vectorBase + irq;
        } else {
            int sIrq = irq - 8;
            slave.isr |= (1 << sIrq);
            slave.irr &= ~(1 << sIrq);
            master.isr |= (1 << 2);
            if (slave.autoEoi) slave.isr &= ~(1 << sIrq);
            if (master.autoEoi) master.isr &= ~(1 << 2);
            return slave.vectorBase + sIrq;
        }
    }

    public boolean hasInterrupt() {
        checkPending();
        return irqPending;
    }

    private void checkPending() {
        // Check slave first
        int slaveUnmasked = slave.irr & ~slave.imr & ~slave.isr;
        if (slaveUnmasked != 0) {
            master.irr |= (1 << 2);
        }
        int masterUnmasked = master.irr & ~master.imr & ~master.isr;
        if (masterUnmasked != 0) {
            // Find highest priority (lowest numbered)
            for (int i = 0; i < 8; i++) {
                if ((masterUnmasked & (1 << i)) != 0) {
                    if (i == 2 && slaveUnmasked != 0) {
                        // Cascade: find slave IRQ
                        for (int j = 0; j < 8; j++) {
                            if ((slaveUnmasked & (1 << j)) != 0) {
                                pendingIrq = 8 + j;
                                irqPending = true;
                                return;
                            }
                        }
                    }
                    pendingIrq = i;
                    irqPending = true;
                    return;
                }
            }
        }
        irqPending = false;
    }

    // ── Timed events ────────────────────────────────────────

    /** Schedule an event to fire after delayMs milliseconds. */
    public void addEvent(EventHandler handler, double delayMs, int value) {
        PicEvent ev = new PicEvent();
        ev.handler = handler;
        ev.value = value;
        ev.triggerTime = currentTime + delayMs;

        // Insert sorted
        if (eventHead == null || ev.triggerTime < eventHead.triggerTime) {
            ev.next = eventHead;
            eventHead = ev;
        } else {
            PicEvent cur = eventHead;
            while (cur.next != null && cur.next.triggerTime <= ev.triggerTime) {
                cur = cur.next;
            }
            ev.next = cur.next;
            cur.next = ev;
        }
    }

    /** Remove all pending events for a given handler. */
    public void removeEvent(EventHandler handler) {
        while (eventHead != null && eventHead.handler == handler) {
            eventHead = eventHead.next;
        }
        if (eventHead != null) {
            PicEvent cur = eventHead;
            while (cur.next != null) {
                if (cur.next.handler == handler) {
                    cur.next = cur.next.next;
                } else {
                    cur = cur.next;
                }
            }
        }
    }

    /** Advance time and fire due events. */
    public void advanceTime(double deltaMs) {
        currentTime += deltaMs;
        while (eventHead != null && eventHead.triggerTime <= currentTime) {
            PicEvent ev = eventHead;
            eventHead = ev.next;
            ev.handler.handle(ev.value);
        }
    }

    public double getCurrentTime() { return currentTime; }

    // ── I/O port handlers ───────────────────────────────────

    private void writeMasterCommand(int val) {
        if ((val & 0x10) != 0) {
            // ICW1
            master.icwState = 1;
            master.imr = 0;
            master.isr = 0;
            master.irr = 0;
        } else if ((val & 0x08) == 0) {
            // OCW2
            if ((val & 0x20) != 0) {
                // Non-specific EOI
                for (int i = 0; i < 8; i++) {
                    if ((master.isr & (1 << i)) != 0) {
                        master.isr &= ~(1 << i);
                        break;
                    }
                }
            } else if ((val & 0x60) == 0x60) {
                // Specific EOI
                master.isr &= ~(1 << (val & 7));
            }
        } else {
            // OCW3
            if ((val & 0x02) != 0) {
                master.readIsr = (val & 0x01) != 0;
            }
        }
    }

    private void writeMasterData(int val) {
        switch (master.icwState) {
            case 0: master.imr = val & 0xFF; break;
            case 1: master.vectorBase = val & 0xF8; master.icwState = 2; break;
            case 2: master.icwState = 3; break; // ICW3
            case 3:
                master.autoEoi = (val & 0x02) != 0;
                master.icwState = 0;
                break;
        }
    }

    private int readMasterCommand() {
        return master.readIsr ? master.isr : master.irr;
    }

    private void writeSlaveCommand(int val) {
        if ((val & 0x10) != 0) {
            slave.icwState = 1;
            slave.imr = 0;
            slave.isr = 0;
            slave.irr = 0;
        } else if ((val & 0x08) == 0) {
            if ((val & 0x20) != 0) {
                for (int i = 0; i < 8; i++) {
                    if ((slave.isr & (1 << i)) != 0) {
                        slave.isr &= ~(1 << i);
                        break;
                    }
                }
            } else if ((val & 0x60) == 0x60) {
                slave.isr &= ~(1 << (val & 7));
            }
        } else {
            if ((val & 0x02) != 0) {
                slave.readIsr = (val & 0x01) != 0;
            }
        }
    }

    private void writeSlaveData(int val) {
        switch (slave.icwState) {
            case 0: slave.imr = val & 0xFF; break;
            case 1: slave.vectorBase = val & 0xF8; slave.icwState = 2; break;
            case 2: slave.icwState = 3; break;
            case 3:
                slave.autoEoi = (val & 0x02) != 0;
                slave.icwState = 0;
                break;
        }
    }

    private int readSlaveCommand() {
        return slave.readIsr ? slave.isr : slave.irr;
    }
}

