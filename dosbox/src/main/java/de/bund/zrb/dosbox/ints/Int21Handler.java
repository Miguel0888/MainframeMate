package de.bund.zrb.dosbox.ints;

import de.bund.zrb.dosbox.cpu.CPU;
import de.bund.zrb.dosbox.dos.DosKernel;
import de.bund.zrb.dosbox.hardware.memory.Memory;

/**
 * INT 21h handler — DOS API Services.
 * Handles character I/O, file operations, memory management, and program control.
 *
 * Ported from: src/dos/dos.cpp
 */
public class Int21Handler implements CPU.IntHandler {

    private final Memory memory;
    private final DosKernel dos;
    private final Int10Handler video;
    private final Int16Handler keyboard;

    private boolean terminated;

    public Int21Handler(Memory memory, DosKernel dos, Int10Handler video, Int16Handler keyboard) {
        this.memory = memory;
        this.dos = dos;
        this.video = video;
        this.keyboard = keyboard;
    }

    @Override
    public void handle(CPU cpu) {
        int ah = cpu.regs.getAH();
        switch (ah) {
            case 0x00: // Terminate program
                terminated = true;
                cpu.setRunning(false);
                break;

            case 0x01: { // Character input with echo
                // Wait for key via INT 16h
                cpu.softwareInt(0x16);
                int ch = cpu.regs.getAL();
                video.ttyOutput(ch, 0x07);
                break;
            }

            case 0x02: // Character output
                video.ttyOutput(cpu.regs.getDL(), 0x07);
                break;

            case 0x06: { // Direct console I/O
                int dl = cpu.regs.getDL();
                if (dl == 0xFF) {
                    // Input
                    if (keyboard.hasKey()) {
                        cpu.softwareInt(0x16);
                        cpu.regs.flags.setZF(false);
                    } else {
                        cpu.regs.setAL(0);
                        cpu.regs.flags.setZF(true);
                    }
                } else {
                    video.ttyOutput(dl, 0x07);
                }
                break;
            }

            case 0x07: // Direct character input without echo
            case 0x08: // Character input without echo
                cpu.softwareInt(0x16);
                break;

            case 0x09: { // Print string (terminated by '$')
                int addr = cpu.resolveSegOfs(cpu.regs.ds, cpu.regs.getDX());
                for (int i = 0; i < 10000; i++) {
                    int ch = memory.readByte(addr + i);
                    if (ch == '$') break;
                    video.ttyOutput(ch, 0x07);
                }
                break;
            }

            case 0x0A: { // Buffered input
                int bufAddr = cpu.resolveSegOfs(cpu.regs.ds, cpu.regs.getDX());
                int maxLen = memory.readByte(bufAddr);
                int count = 0;
                for (int i = 0; i < maxLen; i++) {
                    cpu.softwareInt(0x16);
                    int ch = cpu.regs.getAL();
                    if (ch == 0x0D) break; // Enter
                    memory.writeByte(bufAddr + 2 + count, ch);
                    video.ttyOutput(ch, 0x07);
                    count++;
                }
                memory.writeByte(bufAddr + 1, count);
                memory.writeByte(bufAddr + 2 + count, 0x0D);
                video.ttyOutput(0x0D, 0x07);
                video.ttyOutput(0x0A, 0x07);
                break;
            }

            case 0x0B: // Check standard input status
                cpu.regs.setAL(keyboard.hasKey() ? 0xFF : 0x00);
                break;

            case 0x19: // Get current default drive
                cpu.regs.setAL(dos.getCurrentDrive());
                break;

            case 0x0E: // Select default drive
                dos.setCurrentDrive(cpu.regs.getDL());
                cpu.regs.setAL(26); // 26 logical drives available
                break;

            case 0x1A: // Set DTA (Disk Transfer Address)
                dos.setDTA(cpu.resolveSegOfs(cpu.regs.ds, cpu.regs.getDX()));
                break;

            case 0x25: // Set interrupt vector
                memory.writeWord(cpu.regs.getAL() * 4, cpu.regs.getDX());
                memory.writeWord(cpu.regs.getAL() * 4 + 2, cpu.regs.ds);
                break;

            case 0x2A: { // Get system date
                java.time.LocalDate now = java.time.LocalDate.now();
                cpu.regs.setCX(now.getYear());
                cpu.regs.setDH(now.getMonthValue());
                cpu.regs.setDL(now.getDayOfMonth());
                cpu.regs.setAL(now.getDayOfWeek().getValue() % 7);
                break;
            }

            case 0x2C: { // Get system time
                java.time.LocalTime now = java.time.LocalTime.now();
                cpu.regs.setCH(now.getHour());
                cpu.regs.setCL(now.getMinute());
                cpu.regs.setDH(now.getSecond());
                cpu.regs.setDL(now.getNano() / 10_000_000); // hundredths
                break;
            }

            case 0x30: // Get DOS version
                cpu.regs.setAL(5);  // DOS 5.0
                cpu.regs.setAH(0);
                cpu.regs.setBX(0);
                cpu.regs.setCX(0);
                break;

            case 0x33: // Get/Set break flag
                if (cpu.regs.getAL() == 0) cpu.regs.setDL(0);
                break;

            case 0x35: { // Get interrupt vector
                int vec = cpu.regs.getAL();
                cpu.regs.setBX(memory.readWord(vec * 4));
                cpu.regs.es = memory.readWord(vec * 4 + 2);
                break;
            }

            case 0x3C: { // Create file
                String path = memory.readString(cpu.resolveSegOfs(cpu.regs.ds, cpu.regs.getDX()), 256);
                int handle = dos.createFile(path, cpu.regs.getCX());
                if (handle >= 0) {
                    cpu.regs.setAX(handle);
                    cpu.regs.flags.setCF(false);
                } else {
                    cpu.regs.setAX(3); // path not found
                    cpu.regs.flags.setCF(true);
                }
                break;
            }

            case 0x3D: { // Open file
                String path = memory.readString(cpu.resolveSegOfs(cpu.regs.ds, cpu.regs.getDX()), 256);
                int handle = dos.openFile(path, cpu.regs.getAL());
                if (handle >= 0) {
                    cpu.regs.setAX(handle);
                    cpu.regs.flags.setCF(false);
                } else {
                    cpu.regs.setAX(2); // file not found
                    cpu.regs.flags.setCF(true);
                }
                break;
            }

            case 0x3E: { // Close file
                if (dos.closeFile(cpu.regs.getBX())) {
                    cpu.regs.flags.setCF(false);
                } else {
                    cpu.regs.setAX(6); // invalid handle
                    cpu.regs.flags.setCF(true);
                }
                break;
            }

            case 0x3F: { // Read file
                int handle = cpu.regs.getBX();
                int count = cpu.regs.getCX();
                int bufAddr = cpu.resolveSegOfs(cpu.regs.ds, cpu.regs.getDX());
                int read = dos.readFile(handle, memory, bufAddr, count);
                if (read >= 0) {
                    cpu.regs.setAX(read);
                    cpu.regs.flags.setCF(false);
                } else {
                    cpu.regs.setAX(5); // access denied
                    cpu.regs.flags.setCF(true);
                }
                break;
            }

            case 0x40: { // Write file
                int handle = cpu.regs.getBX();
                int count = cpu.regs.getCX();
                int bufAddr = cpu.resolveSegOfs(cpu.regs.ds, cpu.regs.getDX());
                int written = dos.writeFile(handle, memory, bufAddr, count);
                if (written >= 0) {
                    cpu.regs.setAX(written);
                    cpu.regs.flags.setCF(false);
                } else {
                    cpu.regs.setAX(5);
                    cpu.regs.flags.setCF(true);
                }
                break;
            }

            case 0x42: { // Seek file (LSEEK)
                int handle = cpu.regs.getBX();
                long offset = ((long)(cpu.regs.getCX() & 0xFFFF) << 16) | (cpu.regs.getDX() & 0xFFFF);
                int whence = cpu.regs.getAL();
                long result = dos.seekFile(handle, offset, whence);
                if (result >= 0) {
                    cpu.regs.setDX((int)((result >> 16) & 0xFFFF));
                    cpu.regs.setAX((int)(result & 0xFFFF));
                    cpu.regs.flags.setCF(false);
                } else {
                    cpu.regs.setAX(6); // invalid handle
                    cpu.regs.flags.setCF(true);
                }
                break;
            }

            case 0x43: { // Get/Set file attributes
                if (cpu.regs.getAL() == 0) {
                    // Get attributes - return normal file
                    cpu.regs.setCX(0x0020); // archive bit
                    cpu.regs.flags.setCF(false);
                } else {
                    // Set attributes - stub
                    cpu.regs.flags.setCF(false);
                }
                break;
            }

            case 0x44: { // IOCTL
                int subFunc = cpu.regs.getAL();
                int handle = cpu.regs.getBX();
                switch (subFunc) {
                    case 0x00: // Get device information
                        if (handle <= 4) {
                            // Character device (STDIN/STDOUT/STDERR/STDAUX/STDPRN)
                            cpu.regs.setDX(0x80D3); // character device
                        } else {
                            cpu.regs.setDX(0x0000); // file (not device)
                        }
                        cpu.regs.flags.setCF(false);
                        break;
                    case 0x01: // Set device information
                        cpu.regs.flags.setCF(false);
                        break;
                    case 0x08: // Check if block device is removable
                        cpu.regs.setAX(1); // not removable
                        cpu.regs.flags.setCF(false);
                        break;
                    default:
                        cpu.regs.flags.setCF(true);
                        cpu.regs.setAX(1);
                        break;
                }
                break;
            }

            case 0x45: { // Duplicate file handle
                int newHandle = dos.dupHandle(cpu.regs.getBX());
                if (newHandle >= 0) {
                    cpu.regs.setAX(newHandle);
                    cpu.regs.flags.setCF(false);
                } else {
                    cpu.regs.setAX(6);
                    cpu.regs.flags.setCF(true);
                }
                break;
            }

            case 0x46: { // Force duplicate file handle
                if (dos.forceDup(cpu.regs.getBX(), cpu.regs.getCX())) {
                    cpu.regs.flags.setCF(false);
                } else {
                    cpu.regs.setAX(6);
                    cpu.regs.flags.setCF(true);
                }
                break;
            }

            case 0x47: { // Get current directory
                String dir = dos.getCurrentDir();
                int bufAddr = cpu.resolveSegOfs(cpu.regs.ds, cpu.regs.getSI());
                memory.writeString(bufAddr, dir);
                cpu.regs.flags.setCF(false);
                break;
            }

            case 0x48: { // Allocate memory (paragraphs)
                int paras = cpu.regs.getBX();
                int seg = dos.allocMemory(paras);
                if (seg >= 0) {
                    cpu.regs.setAX(seg);
                    cpu.regs.flags.setCF(false);
                } else {
                    cpu.regs.setAX(8); // insufficient memory
                    cpu.regs.setBX(dos.getLargestFreeBlock());
                    cpu.regs.flags.setCF(true);
                }
                break;
            }

            case 0x49: // Free memory
                if (dos.freeMemory(cpu.regs.es)) {
                    cpu.regs.flags.setCF(false);
                } else {
                    cpu.regs.setAX(9);
                    cpu.regs.flags.setCF(true);
                }
                break;

            case 0x4A: { // Resize memory block
                int segment = cpu.regs.es;
                int newParas = cpu.regs.getBX();
                if (dos.resizeMemory(segment, newParas)) {
                    cpu.regs.flags.setCF(false);
                } else {
                    cpu.regs.setAX(8); // insufficient memory
                    cpu.regs.setBX(dos.getLargestFreeBlock());
                    cpu.regs.flags.setCF(true);
                }
                break;
            }

            case 0x4C: // Terminate with return code
                terminated = true;
                cpu.setRunning(false);
                break;

            case 0x4E: { // FindFirst
                String pattern = memory.readString(cpu.resolveSegOfs(cpu.regs.ds, cpu.regs.getDX()), 256);
                if (dos.findFirst(pattern, cpu.regs.getCX())) {
                    cpu.regs.flags.setCF(false);
                } else {
                    cpu.regs.setAX(18); // no more files
                    cpu.regs.flags.setCF(true);
                }
                break;
            }

            case 0x4F: // FindNext
                if (dos.findNext()) {
                    cpu.regs.flags.setCF(false);
                } else {
                    cpu.regs.setAX(18);
                    cpu.regs.flags.setCF(true);
                }
                break;

            case 0x50: // Set current PSP
                dos.setCurrentPSP(cpu.regs.getBX());
                break;

            case 0x51: // Get current PSP (same as 62h)
                cpu.regs.setBX(dos.getCurrentPSP());
                break;

            case 0x58: // Get/Set memory allocation strategy
                if (cpu.regs.getAL() == 0) {
                    cpu.regs.setAX(0); // first fit
                    cpu.regs.flags.setCF(false);
                } else {
                    cpu.regs.flags.setCF(false); // accept any strategy
                }
                break;

            case 0x62: // Get PSP
                cpu.regs.setBX(dos.getCurrentPSP());
                break;

            default:
                // Unhandled - set carry and error
                cpu.regs.flags.setCF(true);
                cpu.regs.setAX(1); // invalid function
                break;
        }
    }

    public boolean isTerminated() { return terminated; }
    public void resetTerminated() { terminated = false; }
}

