package de.bund.zrb.dosbox.shell;

import de.bund.zrb.dosbox.dos.DosKernel;
import de.bund.zrb.dosbox.ints.Int10Handler;
import de.bund.zrb.dosbox.ints.Int16Handler;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * DOS command shell — interprets internal commands like DIR, CD, CLS, VER, etc.
 * This is the Java equivalent of DOSBox's src/shell/ directory.
 *
 * Ported from: src/shell/shell.cpp, src/shell/shell_cmds.cpp
 */
public class DosShell {

    private final DosKernel dos;
    private final Int10Handler video;
    private final Int16Handler keyboard;

    private boolean running = true;

    public DosShell(DosKernel dos, Int10Handler video, Int16Handler keyboard) {
        this.dos = dos;
        this.video = video;
        this.keyboard = keyboard;
    }

    /** Print startup banner. */
    public void showBanner() {
        printLine("JDOSBox - Java DOSBox Port (em-dosbox-svn-sdl2)");
        printLine("Based on DOSBox (C) The DOSBox Team");
        printLine("Ported to Java for MainframeMate");
        printLine("");
        printLine("Type HELP for a list of commands.");
        printLine("");
    }

    /** Print the command prompt. */
    public void showPrompt() {
        char driveLetter = (char) ('A' + dos.getCurrentDrive());
        String dir = dos.getCurrentDir();
        String prompt = driveLetter + ":\\" + dir + ">";
        printString(prompt);
    }

    /**
     * Read a line from keyboard input. Blocks until Enter is pressed.
     * Returns the entered line, or null if shell should exit.
     */
    public String readLine() {
        StringBuilder sb = new StringBuilder();
        while (running) {
            Integer key = waitForKey();
            if (key == null) return null;
            int ascii = key & 0xFF;
            int scancode = (key >> 8) & 0xFF;

            if (ascii == 0x0D) { // Enter
                printString("\r\n");
                return sb.toString();
            } else if (ascii == 0x08) { // Backspace
                if (sb.length() > 0) {
                    sb.deleteCharAt(sb.length() - 1);
                    video.ttyOutput(0x08, 0x07);
                    video.ttyOutput(' ', 0x07);
                    video.ttyOutput(0x08, 0x07);
                }
            } else if (ascii == 0x1B) { // Escape - clear line
                while (sb.length() > 0) {
                    sb.deleteCharAt(sb.length() - 1);
                    video.ttyOutput(0x08, 0x07);
                    video.ttyOutput(' ', 0x07);
                    video.ttyOutput(0x08, 0x07);
                }
            } else if (ascii >= 0x20) { // Printable
                sb.append((char) ascii);
                video.ttyOutput(ascii, 0x07);
            }
        }
        return null;
    }

    /** Wait for a key from the keyboard buffer. */
    private Integer waitForKey() {
        while (running) {
            Integer key = keyboard.pollKey();
            if (key != null) {
                return key;
            }
            try { Thread.sleep(10); } catch (InterruptedException e) { return null; }
        }
        return null;
    }


    /** Execute a command line. */
    public void executeCommand(String line) {
        line = line.trim();
        if (line.isEmpty()) return;

        // Parse command and arguments
        String cmd, args;
        int spaceIdx = line.indexOf(' ');
        if (spaceIdx >= 0) {
            cmd = line.substring(0, spaceIdx).toUpperCase();
            args = line.substring(spaceIdx + 1).trim();
        } else {
            cmd = line.toUpperCase();
            args = "";
        }

        switch (cmd) {
            case "DIR": cmdDir(args); break;
            case "CD":
            case "CHDIR": cmdCd(args); break;
            case "CLS": cmdCls(); break;
            case "VER": cmdVer(); break;
            case "HELP":
            case "?": cmdHelp(); break;
            case "TYPE": cmdType(args); break;
            case "SET": cmdSet(args); break;
            case "DATE": cmdDate(); break;
            case "TIME": cmdTime(); break;
            case "EXIT": running = false; break;
            case "ECHO": printLine(args); break;
            case "VOL": printLine(" Volume in drive C has no label"); break;
            case "PATH": printLine("PATH=" + System.getenv("PATH")); break;
            case "PROMPT": break; // stub
            case "COPY": printLine("  1 file(s) copied. (stub)"); break;
            case "DEL":
            case "ERASE": printLine("File(s) deleted. (stub)"); break;
            case "REN":
            case "RENAME": printLine("File renamed. (stub)"); break;
            case "MD":
            case "MKDIR": cmdMkdir(args); break;
            case "RD":
            case "RMDIR": printLine("Directory removed. (stub)"); break;
            case "MEM": cmdMem(); break;
            default:
                // Check if it's a drive letter change (e.g., "C:")
                if (cmd.length() == 2 && cmd.charAt(1) == ':') {
                    int drive = cmd.charAt(0) - 'A';
                    if (drive >= 0 && drive < 26) {
                        dos.setCurrentDrive(drive);
                    } else {
                        printLine("Invalid drive specification");
                    }
                } else {
                    printLine("Bad command or file name");
                }
                break;
        }
    }

    // ── Internal commands ────────────────────────────────────

    private void cmdDir(String args) {
        String path = args.isEmpty() ? "*.*" : args;
        File dir;
        if (path.equals("*.*") || path.equals("*")) {
            dir = new File(dos.getHostRootDir() +
                    (dos.getCurrentDir().isEmpty() ? "" : File.separator + dos.getCurrentDir()));
        } else {
            String hostPath = dos.getHostRootDir() + File.separator + path.replace('\\', File.separatorChar);
            dir = new File(hostPath);
            if (!dir.isDirectory()) {
                dir = dir.getParentFile();
            }
        }

        printLine(" Volume in drive " + (char)('A' + dos.getCurrentDrive()) + " has no label");
        printLine(" Directory of " + (char)('A' + dos.getCurrentDrive()) + ":\\" + dos.getCurrentDir());
        printLine("");

        if (dir == null || !dir.exists()) {
            printLine("File Not Found");
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            printLine("File Not Found");
            return;
        }

        int fileCount = 0;
        long totalSize = 0;

        for (File f : files) {
            String name = f.getName().toUpperCase();
            if (name.startsWith(".")) continue;

            // Format: 8.3 name, date, size
            String display;
            if (f.isDirectory()) {
                display = String.format("%-12s   <DIR>", formatDosName(name));
            } else {
                display = String.format("%-12s   %,10d", formatDosName(name), f.length());
                totalSize += f.length();
            }
            fileCount++;
            printLine(display);
        }

        printLine(String.format("     %d file(s)    %,d bytes", fileCount, totalSize));
        printLine(String.format("     %,d bytes free", Runtime.getRuntime().freeMemory()));
    }

    private String formatDosName(String name) {
        // Convert to 8.3 format
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return name.length() > 8 ? name.substring(0, 8) : name;
        }
        String base = name.substring(0, dot);
        String ext = name.substring(dot + 1);
        if (base.length() > 8) base = base.substring(0, 8);
        if (ext.length() > 3) ext = ext.substring(0, 3);
        return base + "." + ext;
    }

    private void cmdCd(String args) {
        if (args.isEmpty()) {
            printLine((char)('A' + dos.getCurrentDrive()) + ":\\" + dos.getCurrentDir());
            return;
        }
        if (args.equals("\\") || args.equals("/")) {
            dos.setCurrentDir("");
            return;
        }
        if (args.equals("..")) {
            String cur = dos.getCurrentDir();
            int lastSep = cur.lastIndexOf(File.separatorChar);
            if (lastSep >= 0) {
                dos.setCurrentDir(cur.substring(0, lastSep));
            } else {
                dos.setCurrentDir("");
            }
            return;
        }
        // Try relative path
        String newDir = dos.getCurrentDir().isEmpty() ? args : dos.getCurrentDir() + File.separator + args;
        File f = new File(dos.getHostRootDir() + File.separator + newDir);
        if (f.isDirectory()) {
            dos.setCurrentDir(newDir);
        } else {
            printLine("Invalid directory");
        }
    }

    private void cmdCls() {
        // Clear screen via INT 10h AH=06
        for (int i = 0; i < Int10Handler.ROWS * Int10Handler.COLS; i++) {
            int addr = Int10Handler.TEXT_BASE + i * 2;
            video.ttyOutput(' ', 0x07); // not ideal, let's just clear VRAM
        }
        // Better: direct VRAM clear
        for (int i = 0; i < Int10Handler.ROWS * Int10Handler.COLS; i++) {
            int addr = Int10Handler.TEXT_BASE + i * 2;
            // We need memory access. Use the field via video handler
        }
        // Simplest approach: scroll everything up
        printString("\033[2J\033[H"); // won't work in our terminal, so:
        // TODO: proper CLS via INT 10h
    }

    private void cmdVer() {
        printLine("JDOSBox version 0.74-SDL2 (Java port)");
        printLine("DOSBox reports: DOS 5.0");
    }

    private void cmdHelp() {
        printLine("Internal commands available:");
        printLine("  DIR     - List directory contents");
        printLine("  CD      - Change directory");
        printLine("  CLS     - Clear screen");
        printLine("  TYPE    - Display contents of a text file");
        printLine("  VER     - Show version");
        printLine("  DATE    - Show current date");
        printLine("  TIME    - Show current time");
        printLine("  MEM     - Show memory usage");
        printLine("  SET     - Show environment variables");
        printLine("  ECHO    - Display a message");
        printLine("  COPY    - Copy files");
        printLine("  DEL     - Delete files");
        printLine("  MD      - Create directory");
        printLine("  RD      - Remove directory");
        printLine("  EXIT    - Exit DOSBox");
    }

    private void cmdType(String args) {
        if (args.isEmpty()) {
            printLine("Required parameter missing");
            return;
        }
        String hostPath = dos.getHostRootDir() + File.separator +
                (dos.getCurrentDir().isEmpty() ? "" : dos.getCurrentDir() + File.separator) +
                args.replace('\\', File.separatorChar);
        File f = new File(hostPath);
        if (!f.exists() || f.isDirectory()) {
            printLine("File not found - " + args.toUpperCase());
            return;
        }
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(f))) {
            String line;
            while ((line = reader.readLine()) != null) {
                printLine(line);
            }
        } catch (java.io.IOException e) {
            printLine("Error reading file: " + e.getMessage());
        }
    }

    private void cmdSet(String args) {
        if (args.isEmpty()) {
            printLine("COMSPEC=C:\\COMMAND.COM");
            printLine("PATH=C:\\");
            printLine("PROMPT=$P$G");
        }
    }

    private void cmdDate() {
        printLine("Current date is " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
    }

    private void cmdTime() {
        printLine("Current time is " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    private void cmdMkdir(String args) {
        if (args.isEmpty()) {
            printLine("Required parameter missing");
            return;
        }
        String hostPath = dos.getHostRootDir() + File.separator + args.replace('\\', File.separatorChar);
        new File(hostPath).mkdirs();
    }

    private void cmdMem() {
        printLine("");
        printLine("Memory Type        Total     Used      Free");
        printLine("----------------  --------  --------  --------");
        printLine(String.format("Conventional       %,6dK   %,6dK   %,6dK", 640, 40, 600));
        printLine(String.format("Upper              %,6dK   %,6dK   %,6dK", 384, 100, 284));
        printLine("");
        printLine("Total memory:     1,024K");
    }

    // ── Output helpers ──────────────────────────────────────

    public void printString(String s) {
        for (int i = 0; i < s.length(); i++) {
            video.ttyOutput(s.charAt(i), 0x07);
        }
    }

    public void printLine(String s) {
        printString(s);
        video.ttyOutput(0x0D, 0x07);
        video.ttyOutput(0x0A, 0x07);
    }

    public boolean isRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }
}

