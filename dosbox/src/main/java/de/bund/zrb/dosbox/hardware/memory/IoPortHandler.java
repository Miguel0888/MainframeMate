package de.bund.zrb.dosbox.hardware.memory;

/**
 * I/O port handler for x86 IN/OUT instructions.
 * 65536 ports, each can have read and write handlers.
 *
 * Ported from: src/hardware/iohandler.cpp, include/inout.h
 */
public class IoPortHandler implements de.bund.zrb.dosbox.core.Module {

    @FunctionalInterface
    public interface IoReadHandler {
        int read(int port, int ioWidth);
    }

    @FunctionalInterface
    public interface IoWriteHandler {
        void write(int port, int value, int ioWidth);
    }

    /** I/O width constants */
    public static final int IO_MB = 1; // byte
    public static final int IO_MW = 2; // word
    public static final int IO_MD = 4; // dword

    private static final int PORT_COUNT = 65536;

    private final IoReadHandler[] readHandlers = new IoReadHandler[PORT_COUNT];
    private final IoWriteHandler[] writeHandlers = new IoWriteHandler[PORT_COUNT];

    public IoPortHandler() {
        // Default: reads return 0xFF, writes are ignored
        IoReadHandler defaultRead = (port, w) -> 0xFF;
        IoWriteHandler defaultWrite = (port, v, w) -> {};
        java.util.Arrays.fill(readHandlers, defaultRead);
        java.util.Arrays.fill(writeHandlers, defaultWrite);
    }

    // ── Registration ────────────────────────────────────────

    public void registerRead(int port, IoReadHandler handler) {
        readHandlers[port & 0xFFFF] = handler;
    }

    public void registerWrite(int port, IoWriteHandler handler) {
        writeHandlers[port & 0xFFFF] = handler;
    }

    public void registerReadRange(int basePort, int count, IoReadHandler handler) {
        for (int i = 0; i < count; i++) {
            readHandlers[(basePort + i) & 0xFFFF] = handler;
        }
    }

    public void registerWriteRange(int basePort, int count, IoWriteHandler handler) {
        for (int i = 0; i < count; i++) {
            writeHandlers[(basePort + i) & 0xFFFF] = handler;
        }
    }

    // ── Access ──────────────────────────────────────────────

    public int readByte(int port) {
        return readHandlers[port & 0xFFFF].read(port & 0xFFFF, IO_MB) & 0xFF;
    }

    public int readWord(int port) {
        return readHandlers[port & 0xFFFF].read(port & 0xFFFF, IO_MW) & 0xFFFF;
    }

    public void writeByte(int port, int value) {
        writeHandlers[port & 0xFFFF].write(port & 0xFFFF, value & 0xFF, IO_MB);
    }

    public void writeWord(int port, int value) {
        writeHandlers[port & 0xFFFF].write(port & 0xFFFF, value & 0xFFFF, IO_MW);
    }
}

