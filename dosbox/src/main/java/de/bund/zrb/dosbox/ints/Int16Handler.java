package de.bund.zrb.dosbox.ints;

import de.bund.zrb.dosbox.cpu.CPU;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * INT 16h handler — BIOS Keyboard Services.
 *
 * Ported from: src/ints/bios_keyboard.cpp
 */
public class Int16Handler implements CPU.IntHandler {

    /** Keystroke: scancode (high byte) + ASCII (low byte) */
    private final BlockingQueue<Integer> keyBuffer = new ArrayBlockingQueue<>(32);

    @Override
    public void handle(CPU cpu) {
        int ah = cpu.regs.getAH();
        switch (ah) {
            case 0x00: // Wait for keystroke
            case 0x10: {
                Integer key = keyBuffer.poll();
                if (key != null) {
                    cpu.regs.setAH((key >> 8) & 0xFF); // scancode
                    cpu.regs.setAL(key & 0xFF);         // ASCII
                } else {
                    // No key available: back up IP to re-execute INT 16h
                    cpu.regs.setIP(cpu.regs.getIP() - 2);
                }
                break;
            }
            case 0x01: // Check for keystroke
            case 0x11: {
                Integer key = keyBuffer.peek();
                if (key != null) {
                    cpu.regs.setAH((key >> 8) & 0xFF);
                    cpu.regs.setAL(key & 0xFF);
                    cpu.regs.flags.setZF(false);
                } else {
                    cpu.regs.flags.setZF(true);
                }
                break;
            }
            case 0x02: // Get shift flags
            case 0x12:
                cpu.regs.setAL(0); // no shift keys pressed
                break;
            case 0x05: // Store keystroke in buffer
                if (keyBuffer.offer((cpu.regs.getCH() << 8) | cpu.regs.getCL())) {
                    cpu.regs.setAL(0);
                } else {
                    cpu.regs.setAL(1); // buffer full
                }
                break;
        }
    }

    /** Enqueue a keystroke (called by keyboard hardware emulation). */
    public void enqueueKey(int scancode, int ascii) {
        keyBuffer.offer(((scancode & 0xFF) << 8) | (ascii & 0xFF));
    }

    /** Dequeue a keystroke. Returns null if empty. */
    public Integer pollKey() {
        return keyBuffer.poll();
    }

    /** Check if buffer has keys. */
    public boolean hasKey() {
        return !keyBuffer.isEmpty();
    }

    /** Peek at the next key without removing it. Returns null if empty. */
    public Integer peekKey() {
        return keyBuffer.peek();
    }
}

