package de.bund.zrb.dosbox.core;

import de.bund.zrb.dosbox.cpu.CPU;
import de.bund.zrb.dosbox.dos.DosKernel;
import de.bund.zrb.dosbox.gui.SwingDisplay;
import de.bund.zrb.dosbox.hardware.memory.IoPortHandler;
import de.bund.zrb.dosbox.hardware.memory.Memory;
import de.bund.zrb.dosbox.hardware.pic.PIC;
import de.bund.zrb.dosbox.ints.Int10Handler;
import de.bund.zrb.dosbox.ints.Int16Handler;
import de.bund.zrb.dosbox.ints.Int21Handler;
import de.bund.zrb.dosbox.shell.DosShell;

import javax.swing.*;

/**
 * DOSBox main machine — wires up all components and runs the emulation.
 * This is the Java equivalent of src/dosbox.cpp + src/gui/sdlmain.cpp.
 *
 * Entry point for the Java DOSBox emulator.
 */
public class DOSBox {

    // ── Hardware components ──────────────────────────────────
    private final Memory memory;
    private final IoPortHandler io;
    private final PIC pic;
    private final CPU cpu;

    // ── BIOS / DOS ──────────────────────────────────────────
    private final Int10Handler int10;
    private final Int16Handler int16;
    private final Int21Handler int21;
    private final DosKernel dos;
    private final DosShell shell;

    // ── GUI ─────────────────────────────────────────────────
    private SwingDisplay display;

    public DOSBox() {
        // Initialize hardware
        memory = new Memory();
        io = new IoPortHandler();
        pic = new PIC();
        cpu = new CPU(memory, io, pic);

        // Register PIC I/O ports
        pic.registerPorts(io);

        // Initialize BIOS handlers
        int10 = new Int10Handler(memory);
        int16 = new Int16Handler();

        // Initialize DOS kernel
        dos = new DosKernel(memory);

        // Initialize INT 21h handler
        int21 = new Int21Handler(memory, dos, int10, int16);

        // Initialize shell
        shell = new DosShell(dos, int10, int16);

        // Register interrupt handlers with CPU
        cpu.setIntHandler(0x10, int10);
        cpu.setIntHandler(0x16, int16);
        cpu.setIntHandler(0x21, int21);

        // Set up initial video mode (80x25 text)
        initVideoMemory();
    }

    /** Initialize video RAM with blank screen. */
    private void initVideoMemory() {
        for (int i = 0; i < Int10Handler.ROWS * Int10Handler.COLS; i++) {
            memory.writeByte(Memory.TEXT_VIDEO_START + i * 2, 0x20);      // space
            memory.writeByte(Memory.TEXT_VIDEO_START + i * 2 + 1, 0x07);  // light gray on black
        }
    }

    /**
     * Start the DOSBox emulator in interactive shell mode.
     * Opens a Swing window and runs the DOS command shell.
     */
    public void startInteractive() {
        SwingUtilities.invokeLater(() -> {
            display = new SwingDisplay(memory, int16);
            display.createWindow();
            display.startRendering();

            // Run the shell on a background thread
            Thread shellThread = new Thread(() -> {
                shell.showBanner();
                while (shell.isRunning()) {
                    shell.showPrompt();
                    // Update cursor position in display
                    if (display != null) {
                        display.setCursorPosition(int10.getCursorRow(), int10.getCursorCol());
                    }
                    String line = shell.readLine();
                    if (line != null) {
                        shell.executeCommand(line);
                    }
                }
                System.exit(0);
            }, "DOSBox-Shell");
            shellThread.setDaemon(true);
            shellThread.start();
        });
    }

    /**
     * Start the DOSBox emulator in CPU emulation mode.
     * Loads and executes x86 code from memory.
     */
    public void startEmulation() {
        SwingUtilities.invokeLater(() -> {
            display = new SwingDisplay(memory, int16);
            display.createWindow();
            display.startRendering();

            // CPU execution thread
            Thread cpuThread = new Thread(() -> {
                cpu.setRunning(true);
                while (cpu.isRunning()) {
                    cpu.executeBlock(10000);
                    pic.advanceTime(1.0);

                    // Update cursor
                    if (display != null) {
                        display.setCursorPosition(int10.getCursorRow(), int10.getCursorCol());
                    }

                    // Small sleep to prevent 100% CPU usage
                    try { Thread.sleep(1); } catch (InterruptedException e) { break; }
                }
            }, "DOSBox-CPU");
            cpuThread.setDaemon(true);
            cpuThread.start();
        });
    }

    /**
     * Load a .COM file into memory and prepare for execution.
     */
    public void loadCOM(byte[] data) {
        // COM files are loaded at CS:0100h
        int loadSeg = 0x0700; // typical COM load segment
        int loadAddr = Memory.segOfs(loadSeg, 0x0100);

        memory.writeBlock(loadAddr, data, 0, data.length);

        // Set up PSP at segment:0000
        memory.writeWord(Memory.segOfs(loadSeg, 0x0000), 0xCD20); // INT 20h at PSP start

        // Set up CPU registers for COM execution
        cpu.regs.cs = loadSeg;
        cpu.regs.ds = loadSeg;
        cpu.regs.es = loadSeg;
        cpu.regs.ss = loadSeg;
        cpu.regs.setIP(0x0100);
        cpu.regs.setSP(0xFFFE);
    }

    // ── Accessors ───────────────────────────────────────────

    public Memory getMemory() { return memory; }
    public CPU getCPU() { return cpu; }
    public IoPortHandler getIo() { return io; }
    public PIC getPic() { return pic; }
    public DosKernel getDos() { return dos; }
    public DosShell getShell() { return shell; }
    public Int10Handler getInt10() { return int10; }
    public Int16Handler getInt16() { return int16; }
    public SwingDisplay getDisplay() { return display; }

    // ── Main entry point ────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("JDOSBox - Java DOSBox Port (em-dosbox-svn-sdl2)");
        System.out.println("================================================");

        DOSBox dosbox = new DOSBox();

        if (args.length > 0 && args[0].toLowerCase().endsWith(".com")) {
            // Load and run a .COM file
            try {
                byte[] data = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0]));
                dosbox.loadCOM(data);
                dosbox.startEmulation();
            } catch (java.io.IOException e) {
                System.err.println("Error loading file: " + e.getMessage());
                System.exit(1);
            }
        } else {
            // Interactive shell mode
            dosbox.startInteractive();
        }
    }
}

