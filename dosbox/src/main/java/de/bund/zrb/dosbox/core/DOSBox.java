package de.bund.zrb.dosbox.core;

import de.bund.zrb.dosbox.cpu.CPU;
import de.bund.zrb.dosbox.dos.DosKernel;
import de.bund.zrb.dosbox.gui.SwingDisplay;
import de.bund.zrb.dosbox.hardware.memory.IoPortHandler;
import de.bund.zrb.dosbox.hardware.memory.Memory;
import de.bund.zrb.dosbox.hardware.pic.PIC;
import de.bund.zrb.dosbox.hardware.timer.PIT;
import de.bund.zrb.dosbox.dos.DPMIManager;
import de.bund.zrb.dosbox.ints.Int10Handler;
import de.bund.zrb.dosbox.ints.Int16Handler;
import de.bund.zrb.dosbox.ints.Int20Handler;
import de.bund.zrb.dosbox.ints.Int21Handler;
import de.bund.zrb.dosbox.ints.Int2FHandler;
import de.bund.zrb.dosbox.ints.Int31Handler;
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
    private final PIT pit;
    private final CPU cpu;

    // ── BIOS / DOS ──────────────────────────────────────────
    private final Int10Handler int10;
    private final Int16Handler int16;
    private final Int20Handler int20;
    private final Int21Handler int21;
    private final Int2FHandler int2f;
    private final Int31Handler int31;
    private final DPMIManager dpmi;
    private final DosKernel dos;
    private final DosShell shell;

    // ── GUI ─────────────────────────────────────────────────
    private SwingDisplay display;

    public DOSBox() {
        // Initialize hardware
        memory = new Memory();
        io = new IoPortHandler();
        pic = new PIC();
        pit = new PIT(pic);
        cpu = new CPU(memory, io, pic);

        // Register PIC and PIT I/O ports
        pic.registerPorts(io);
        pit.registerPorts(io);

        // Initialize BIOS handlers
        int10 = new Int10Handler(memory);
        int16 = new Int16Handler();
        int20 = new Int20Handler();

        // Initialize DOS kernel
        dos = new DosKernel(memory);

        // Initialize DPMI manager
        dpmi = new DPMIManager(memory);

        // Wire DPMI into CPU for protected mode address resolution
        cpu.setDPMI(dpmi);

        // Initialize INT 21h handler
        int21 = new Int21Handler(memory, dos, int10, int16);

        // Initialize INT 2Fh (multiplex) and INT 31h (DPMI services)
        int2f = new Int2FHandler(dpmi);
        int31 = new Int31Handler(dpmi, memory);

        // Initialize shell (no longer needs NativeDosLauncher)
        shell = new DosShell(dos, int10, int16, this);

        // Register interrupt handlers with CPU
        cpu.setIntHandler(0x10, int10);
        cpu.setIntHandler(0x16, int16);
        cpu.setIntHandler(0x20, int20);
        cpu.setIntHandler(0x21, int21);
        cpu.setIntHandler(0x2F, int2f);
        cpu.setIntHandler(0x31, int31);

        // INT 33h - Mouse driver (stub)
        cpu.setIntHandler(0x33, c -> {
            int ax = c.regs.getAX();
            switch (ax) {
                case 0x0000: // Reset/detect
                    c.regs.setAX(0xFFFF); // mouse installed
                    c.regs.setBX(2);       // 2 buttons
                    break;
                case 0x0003: // Get position and button status
                    c.regs.setBX(0);       // no buttons pressed
                    c.regs.setCX(160);     // X = center
                    c.regs.setDX(100);     // Y = center
                    break;
                case 0x0007: // Set horizontal range
                case 0x0008: // Set vertical range
                case 0x000C: // Set handler
                case 0x001A: // Set sensitivity
                    break; // stubs
                default:
                    break;
            }
        });

        // INT 67h - EMS (not installed)
        cpu.setIntHandler(0x67, c -> {
            c.regs.setAH(0x84); // function not supported
        });

        // Set up DPMI entry point callback at F000:8000
        // When the DOS extender calls this, we intercept via INT FEh
        setupDPMIEntryPoint();

        // Register VGA I/O ports
        setupVGAPorts();

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
     * Set up the DPMI entry point at F000:8000.
     * When a DOS extender (like DOS/4GW) detects DPMI via INT 2Fh/1687h,
     * it will call this address to switch to protected mode.
     * We install a callback (INT FEh) that the CPU intercepts.
     */
    private void setupDPMIEntryPoint() {
        int addr = Memory.segOfs(DPMIManager.DPMI_ENTRY_SEG, DPMIManager.DPMI_ENTRY_OFS);

        // Install: INT FEh (DPMI callback) + RETF
        memory.writeByte(addr, 0xCD);   // INT
        memory.writeByte(addr + 1, DPMIManager.DPMI_CALLBACK_INT); // FEh
        memory.writeByte(addr + 2, 0xCB); // RETF

        // Register INT FEh handler for DPMI entry callback
        cpu.setIntHandler(DPMIManager.DPMI_CALLBACK_INT, c -> {
            // AX = 0 for 16-bit client, 1 for 32-bit client
            int clientBits = c.regs.getAX();
            int pspSeg = c.regs.es; // ES = PSP selector/segment

            System.out.println("[DPMI] Mode switch requested, client bits=" + clientBits);
            System.out.printf("[DPMI] Current CS=%04X IP=%04X SS=%04X SP=%04X DS=%04X ES=%04X%n",
                    c.regs.cs, c.regs.getIP(), c.regs.ss, c.regs.getSP(), c.regs.ds, c.regs.es);

            // The stack currently has the FAR CALL return address:
            //   [SS:SP] = return_IP (word)
            //   [SS:SP+2] = caller_CS (real-mode segment) (word)
            int stackAddr = Memory.segOfs(c.regs.ss, c.regs.getSP());
            int returnIP = memory.readWord(stackAddr);
            int callerCS = memory.readWord(stackAddr + 2);
            int callerSS = c.regs.ss;

            System.out.printf("[DPMI] FAR CALL return: CS=%04X IP=%04X, caller SS=%04X%n",
                    callerCS, returnIP, callerSS);

            int[] selectors = dpmi.enterProtectedMode(clientBits, pspSeg);

            // Create a code selector for the caller's CS segment
            int callerCSSel = dpmi.segmentToDescriptor(callerCS);
            int csIdx = dpmi.selectorToIndex(callerCSSel);
            dpmi.setAccessRights(callerCSSel,
                    clientBits != 0 ? 0x409A : 0x009A); // code, read, present (32-bit if requested)

            // Create a data selector for the caller's SS segment
            int callerSSSel = dpmi.segmentToDescriptor(callerSS);

            // Patch the FAR CALL return address on the stack:
            // Replace real-mode CS with the new PM code selector
            memory.writeWord(stackAddr + 2, callerCSSel);

            // Set DS, ES to PM selectors (from enterProtectedMode)
            c.regs.ds = selectors[1]; // DS selector
            c.regs.es = selectors[3]; // ES selector
            // Set SS to the new SS selector
            c.regs.ss = callerSSSel;

            // DON'T change CS here! CS is still F000 (BIOS ROM area).
            // The RETF instruction at F000:8002 will pop CS from the patched stack,
            // giving the caller the proper PM code selector.

            // Set AX=0 to indicate success
            c.regs.setAX(0);

            // Set PE bit in CR0 to indicate protected mode
            c.setCR0(c.getCR0() | 1);

            System.out.printf("[DPMI] Patched return: CS_sel=%04X DS=%04X SS=%04X ES=%04X%n",
                    callerCSSel, c.regs.ds, c.regs.ss, c.regs.es);
        });

        // Also install a callback at F000:8100 for real mode callbacks
        int cbAddr = Memory.segOfs(0xF000, 0x8100);
        memory.writeByte(cbAddr, 0xCB); // RETF (stub)

        // Set up IVT entries for BIOS interrupts so real mode reflection works
        setupIVT();
    }

    /**
     * Set up the real mode Interrupt Vector Table (IVT) at 0000:0000.
     * Each vector points to a callback that triggers the Java handler.
     */
    private void setupIVT() {
        // For each interrupt we handle, set up IVT to point to
        // a small trampoline in the BIOS ROM area
        int trampolineBase = Memory.segOfs(0xF000, 0x9000);

        int[] handledInts = { 0x10, 0x16, 0x20, 0x21, 0x2F, 0x31 };
        for (int intNum : handledInts) {
            int tramAddr = trampolineBase + intNum * 4;
            // Install IRET at each trampoline location
            memory.writeByte(tramAddr, 0xCF); // IRET

            // Set IVT entry to point to trampoline
            memory.writeWord(intNum * 4, 0x9000 + intNum * 4); // offset
            memory.writeWord(intNum * 4 + 2, 0xF000);           // segment
        }

        // INT 67h (EMS) - not installed
        memory.writeWord(0x67 * 4, 0);
        memory.writeWord(0x67 * 4 + 2, 0);
    }

    /**
     * Set up VGA I/O ports for Mode 13h palette and status.
     */
    private void setupVGAPorts() {
        // ── Keyboard controller ports ───────────────────────
        // Port 60h: Keyboard data port
        io.registerRead(0x60, (port, w) -> {
            // Return last scancode from keyboard buffer
            Integer key = int16.peekKey();
            return key != null ? ((key >> 8) & 0xFF) : 0;
        });
        io.registerWrite(0x60, (port, val, w) -> {}); // keyboard command (stub)

        // Port 61h: System control port (speaker, etc.)
        final int[] port61 = { 0 };
        io.registerRead(0x61, (port, w) -> port61[0]);
        io.registerWrite(0x61, (port, val, w) -> port61[0] = val);

        // Port 64h: Keyboard status port
        io.registerRead(0x64, (port, w) -> 0x14); // buffer empty, self-test passed
        io.registerWrite(0x64, (port, val, w) -> {}); // controller command (stub)

        // ── Sound Blaster ports (stubs) ─────────────────────
        // Port 220h-22Fh: Sound Blaster base I/O
        for (int p = 0x220; p <= 0x22F; p++) {
            final int pp = p;
            io.registerRead(pp, (port, w) -> {
                if (port == 0x22A) return 0xAA; // DSP read data ready
                if (port == 0x22E) return 0x00; // DSP busy (ready for write)
                if (port == 0x22C) return 0x00; // DSP write status (ready)
                return 0xFF;
            });
            io.registerWrite(pp, (port, val, w) -> {
                // Accept and ignore sound commands
            });
        }

        // ── AdLib / OPL ports (stubs) ───────────────────────
        // Port 388h/389h: AdLib
        final int[] adlibStatus = { 0x06 }; // timer flags
        io.registerRead(0x388, (port, w) -> adlibStatus[0]);
        io.registerWrite(0x388, (port, val, w) -> {
            // Register select — OPL detection writes 0x04 (timer control)
            if (val == 0x04) adlibStatus[0] = 0x00; // clear timers
        });
        io.registerWrite(0x389, (port, val, w) -> {
            // Data write — stub
        });
        io.registerRead(0x389, (port, w) -> 0);

        // ── VGA DAC palette state ───────────────────────────
        final int[] dacState = new int[4]; // [0]=readIndex, [1]=writeIndex, [2]=component(0-2), [3]=readMode

        // Port 3C7: DAC Address Read Mode Register
        io.registerWrite(0x3C7, (port, val, w) -> {
            dacState[0] = val & 0xFF; // read index
            dacState[2] = 0;          // reset component counter
            dacState[3] = 1;          // read mode
        });

        // Port 3C8: DAC Address Write Mode Register
        io.registerWrite(0x3C8, (port, val, w) -> {
            dacState[1] = val & 0xFF; // write index
            dacState[2] = 0;          // reset component counter
            dacState[3] = 0;          // write mode
        });

        // Port 3C9: DAC Data Register (read/write)
        io.registerWrite(0x3C9, (port, val, w) -> {
            int idx = dacState[1];
            int comp = dacState[2];
            int[] palette = int10.getVgaPalette();
            if (idx >= 0 && idx < 256) {
                int color = palette[idx];
                int scaled = (val & 0x3F) * 255 / 63;
                switch (comp) {
                    case 0: color = (color & 0x00FFFF) | (scaled << 16); break; // R
                    case 1: color = (color & 0xFF00FF) | (scaled << 8); break;  // G
                    case 2: color = (color & 0xFFFF00) | scaled; break;          // B
                }
                palette[idx] = color;
            }
            dacState[2]++;
            if (dacState[2] >= 3) {
                dacState[2] = 0;
                dacState[1] = (dacState[1] + 1) & 0xFF;
            }
        });

        io.registerRead(0x3C9, (port, w) -> {
            int idx = dacState[0];
            int comp = dacState[2];
            int[] palette = int10.getVgaPalette();
            int val = 0;
            if (idx >= 0 && idx < 256) {
                int color = palette[idx];
                switch (comp) {
                    case 0: val = ((color >> 16) & 0xFF) * 63 / 255; break; // R
                    case 1: val = ((color >> 8) & 0xFF) * 63 / 255; break;  // G
                    case 2: val = (color & 0xFF) * 63 / 255; break;          // B
                }
            }
            dacState[2]++;
            if (dacState[2] >= 3) {
                dacState[2] = 0;
                dacState[0] = (dacState[0] + 1) & 0xFF;
            }
            return val;
        });

        // Port 3DA: Input Status Register 1 (bit 0 = retrace, bit 3 = vblank)
        // Toggle to simulate VGA timing
        final int[] vgaStatus = { 0 };
        io.registerRead(0x3DA, (port, w) -> {
            // Toggle retrace bits to prevent programs from hanging
            vgaStatus[0] ^= 0x09; // toggle bits 0 and 3
            return vgaStatus[0];
        });

        // Port 3C0: Attribute Controller (write: index/data alternating)
        final int[] attrState = { 0, 0 }; // [0]=index, [1]=flipflop
        io.registerWrite(0x3C0, (port, val, w) -> {
            if (attrState[1] == 0) {
                attrState[0] = val & 0x3F;
            }
            attrState[1] ^= 1;
        });
        io.registerRead(0x3C0, (port, w) -> attrState[0]);

        // Port 3C1: Attribute Controller Data read
        io.registerRead(0x3C1, (port, w) -> 0);

        // Port 3C2: Miscellaneous Output Register (write)
        io.registerWrite(0x3C2, (port, val, w) -> {});
        // Port 3CC: Miscellaneous Output Register (read)
        io.registerRead(0x3CC, (port, w) -> 0x63); // color, enable RAM, clock

        // Port 3C4/3C5: Sequencer
        final int[] seqIndex = { 0 };
        final int[] seqData = new int[256];
        io.registerWrite(0x3C4, (port, val, w) -> seqIndex[0] = val & 0xFF);
        io.registerRead(0x3C4, (port, w) -> seqIndex[0]);
        io.registerWrite(0x3C5, (port, val, w) -> seqData[seqIndex[0] & 0xFF] = val);
        io.registerRead(0x3C5, (port, w) -> seqData[seqIndex[0] & 0xFF]);

        // Port 3CE/3CF: Graphics Controller
        final int[] gcIndex = { 0 };
        final int[] gcData = new int[256];
        io.registerWrite(0x3CE, (port, val, w) -> gcIndex[0] = val & 0xFF);
        io.registerRead(0x3CE, (port, w) -> gcIndex[0]);
        io.registerWrite(0x3CF, (port, val, w) -> gcData[gcIndex[0] & 0xFF] = val);
        io.registerRead(0x3CF, (port, w) -> gcData[gcIndex[0] & 0xFF]);

        // Port 3D4/3D5: CRTC
        final int[] crtcIndex = { 0 };
        final int[] crtcData = new int[256];
        io.registerWrite(0x3D4, (port, val, w) -> crtcIndex[0] = val & 0xFF);
        io.registerRead(0x3D4, (port, w) -> crtcIndex[0]);
        io.registerWrite(0x3D5, (port, val, w) -> crtcData[crtcIndex[0] & 0xFF] = val);
        io.registerRead(0x3D5, (port, w) -> crtcData[crtcIndex[0] & 0xFF]);
    }

    /**
     * Start the DOSBox emulator in interactive shell mode.
     * Opens a Swing window and runs the DOS command shell.
     */
    public void startInteractive() {
        SwingUtilities.invokeLater(() -> {
            display = new SwingDisplay(memory, int16, int10);
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
            display = new SwingDisplay(memory, int16, int10);
            display.createWindow();
            display.startRendering();

            // CPU execution thread
            Thread cpuThread = new Thread(() -> {
                cpu.setRunning(true);
                while (cpu.isRunning()) {
                    cpu.executeBlock(10000);
                    pic.advanceTime(1.0);
                    pit.tick(1.0);

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
     * Execute a program using the Java CPU emulator.
     * Called from DosShell when running EXE/COM files.
     * Runs in the calling thread, blocks until program terminates.
     *
     * @param cyclesPerBlock cycles to execute per iteration
     */
    public void executeProgramEmulated(int cyclesPerBlock) {
        int20.reset();
        int21.resetTerminated();
        cpu.setRunning(true);

        System.out.printf("[DOSBox] Starting emulation: CS=%04X IP=%04X SS=%04X SP=%04X DS=%04X ES=%04X%n",
                cpu.regs.cs, cpu.regs.getIP(), cpu.regs.ss, cpu.regs.getSP(), cpu.regs.ds, cpu.regs.es);

        long startTime = System.currentTimeMillis();
        long blockCount = 0;

        while (cpu.isRunning() && !int20.isTerminated() && !int21.isTerminated()) {
            cpu.executeBlock(cyclesPerBlock);
            pic.advanceTime(0.5);
            pit.tick(0.5);
            blockCount++;

            // Update cursor in display
            if (display != null) {
                display.setCursorPosition(int10.getCursorRow(), int10.getCursorCol());
            }

            // Periodic status update
            if (blockCount % 10000 == 0) {
                System.out.printf("[DOSBox] Cycles=%d, CS=%04X IP=%04X, PM=%s%n",
                        cpu.getTotalCycles(), cpu.regs.cs, cpu.regs.getIP(),
                        cpu.isProtectedMode() ? "yes" : "no");
            }

            // Small yield to prevent 100% host CPU usage
            try { Thread.sleep(1); } catch (InterruptedException e) {
                cpu.setRunning(false);
                break;
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("[DOSBox] Emulation ended after %d ms, %d blocks, total cycles=%d, PM=%s%n",
                elapsed, blockCount, cpu.getTotalCycles(), cpu.isProtectedMode() ? "yes" : "no");
        System.out.printf("[DOSBox] Final: CS=%04X IP=%04X SS=%04X SP=%04X DS=%04X ES=%04X%n",
                cpu.regs.cs, cpu.regs.getIP(), cpu.regs.ss, cpu.regs.getSP(), cpu.regs.ds, cpu.regs.es);

        cpu.setRunning(false);
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
    public PIT getPit() { return pit; }
    public DosKernel getDos() { return dos; }
    public DPMIManager getDPMI() { return dpmi; }
    public DosShell getShell() { return shell; }
    public Int10Handler getInt10() { return int10; }
    public Int16Handler getInt16() { return int16; }
    public Int20Handler getInt20() { return int20; }
    public Int21Handler getInt21() { return int21; }
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

