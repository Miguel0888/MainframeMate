package de.bund.zrb.dosbox.shell;

import de.bund.zrb.dosbox.core.DOSBox;
import de.bund.zrb.dosbox.dos.DosKernel;
import de.bund.zrb.dosbox.dos.ProgramLoader;
import de.bund.zrb.dosbox.ints.Int10Handler;
import de.bund.zrb.dosbox.ints.Int16Handler;
import de.bund.zrb.dosbox.hardware.memory.Memory;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * DOS command shell — interprets internal commands like DIR, CD, CLS, VER, etc.
 * Executes .EXE/.COM/.BAT files via the built-in Java CPU emulator.
 *
 * Ported from: src/shell/shell.cpp, src/shell/shell_cmds.cpp
 */
public class DosShell {

    private final DosKernel dos;
    private final Int10Handler video;
    private final Int16Handler keyboard;
    private final DOSBox dosbox;

    private boolean running = true;

    /** Drive mount table: drive letter (0-25) -> host directory path */
    private final Map<Integer, String> mountTable = new HashMap<>();

    public DosShell(DosKernel dos, Int10Handler video, Int16Handler keyboard, DOSBox dosbox) {
        this.dos = dos;
        this.video = video;
        this.keyboard = keyboard;
        this.dosbox = dosbox;
    }

    /** Print startup banner. */
    public void showBanner() {
        printLine("JDOSBox - Java DOSBox Emulator");
        printLine("Reine Java-Emulation — kein natives DOSBox noetig!");
        printLine("CPU: x86 Real Mode (16-bit) | Video: VGA Text 80x25");
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
            case "MOUNT": cmdMount(args); break;
            default:
                // Check if it's a drive letter change (e.g., "C:")
                if (cmd.length() == 2 && cmd.charAt(1) == ':') {
                    int drive = cmd.charAt(0) - 'A';
                    if (drive >= 0 && drive < 26) {
                        // If drive has a mount, update hostRootDir
                        String mountDir = mountTable.get(drive);
                        if (mountDir != null) {
                            dos.setHostRootDir(mountDir);
                        }
                        dos.setCurrentDrive(drive);
                        dos.setCurrentDir("");
                    } else {
                        printLine("Invalid drive specification");
                    }
                } else {
                    // Try to run as EXE/COM/BAT file
                    if (!tryRunExecutable(cmd, args)) {
                        printLine("Bad command or file name");
                    }
                }
                break;
        }
    }

    // ── EXE/COM/BAT execution (Java CPU emulator) ─────────────

    /**
     * Try to find and run an executable file.
     * Checks current directory for .EXE, .COM, .BAT files.
     * Uses the built-in Java CPU emulator for EXE/COM execution.
     */
    private boolean tryRunExecutable(String cmd, String args) {
        // Build search path in current directory
        String hostDir = dos.getHostRootDir() +
                (dos.getCurrentDir().isEmpty() ? "" : File.separator + dos.getCurrentDir());

        String[] extensions = { "", ".EXE", ".COM", ".BAT", ".exe", ".com", ".bat" };

        File exeFile = null;
        for (String ext : extensions) {
            File candidate = new File(hostDir, cmd + ext);
            if (candidate.exists() && candidate.isFile()) {
                exeFile = candidate;
                break;
            }
            // Also try the original case
            candidate = new File(hostDir, cmd.toLowerCase() + ext);
            if (candidate.exists() && candidate.isFile()) {
                exeFile = candidate;
                break;
            }
        }

        if (exeFile == null) return false;

        String name = exeFile.getName().toUpperCase();

        if (name.endsWith(".BAT")) {
            // Execute batch file line by line
            executeBatchFile(exeFile);
            return true;
        }

        if (name.endsWith(".EXE") || name.endsWith(".COM")) {
            executeInEmulator(exeFile, args);
            return true;
        }

        return false;
    }

    /**
     * Execute a DOS program (.EXE or .COM) using the Java CPU emulator.
     * No native DOSBox needed — everything runs in Java!
     */
    private void executeInEmulator(File exeFile, String args) {
        printLine("");
        printLine("Lade " + exeFile.getName().toUpperCase() + " in Java-Emulator...");

        // Set the host root directory to the program's directory
        // so file operations work relative to the program
        String previousHostDir = dos.getHostRootDir();
        String previousDir = dos.getCurrentDir();
        dos.setHostRootDir(exeFile.getParentFile().getAbsolutePath());

        // Load program into memory
        ProgramLoader.LoadResult loadResult = ProgramLoader.loadProgram(
                exeFile, args, dosbox.getMemory(), dosbox.getCPU());

        if (!loadResult.success) {
            printLine("FEHLER: " + loadResult.errorMessage);
            printLine("");
            dos.setHostRootDir(previousHostDir);
            return;
        }

        printLine("Typ: " + loadResult.programType +
                " | Groesse: " + loadResult.codeSize + " Bytes" +
                " | Segment: " + String.format("0x%04X", loadResult.codeSegment));
        printLine("Ausfuehrung gestartet...");
        printLine("");

        // Run the program in the CPU emulator
        try {
            dosbox.executeProgramEmulated(5000);
        } catch (Exception e) {
            printLine("");
            printLine("EMULATOR-FEHLER: " + e.getMessage());
        }

        // Restore shell state
        dos.setHostRootDir(previousHostDir);
        dos.setCurrentDir(previousDir);

        printLine("");
        printLine("Programm beendet.");
        printLine("");
    }

    /**
     * Execute a simple batch file.
     */
    private void executeBatchFile(File batFile) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(batFile))) {
            String line;
            while ((line = reader.readLine()) != null && running) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("REM") || line.startsWith("rem")) continue;
                if (line.startsWith("@")) line = line.substring(1).trim();
                if (line.startsWith("ECHO OFF") || line.startsWith("echo off")) continue;
                printLine(line);
                executeCommand(line);
            }
        } catch (java.io.IOException e) {
            printLine("Error reading batch file: " + e.getMessage());
        }
    }

    /**
     * Mount a host directory as a DOS drive letter.
     * Syntax: MOUNT D C:\Games\DOOM2
     */
    public void mountDrive(int drive, String hostDir) {
        mountTable.put(drive, hostDir);
        // If mounting current drive, update hostRootDir
        if (drive == dos.getCurrentDrive()) {
            dos.setHostRootDir(hostDir);
        }
    }

    // ── Internal commands ────────────────────────────────────

    private void cmdMount(String args) {
        if (args.isEmpty()) {
            // Show current mounts
            printLine("Aktuelle Laufwerkszuordnungen:");
            if (mountTable.isEmpty()) {
                printLine("  C: -> " + dos.getHostRootDir() + " (Standard)");
            } else {
                for (Map.Entry<Integer, String> entry : mountTable.entrySet()) {
                    char letter = (char) ('A' + entry.getKey());
                    printLine("  " + letter + ": -> " + entry.getValue());
                }
                if (!mountTable.containsKey(2)) {
                    printLine("  C: -> " + dos.getHostRootDir() + " (Standard)");
                }
            }
            return;
        }

        // Parse: MOUNT D C:\path\to\dir
        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2 || parts[0].length() != 1) {
            printLine("Syntax: MOUNT <Laufwerksbuchstabe> <Verzeichnis>");
            printLine("Beispiel: MOUNT D C:\\Games\\DOOM2");
            return;
        }

        char letter = parts[0].toUpperCase().charAt(0);
        int drive = letter - 'A';
        if (drive < 0 || drive > 25) {
            printLine("Ungueltiger Laufwerksbuchstabe: " + letter);
            return;
        }

        String hostPath = parts[1].trim();
        // Remove surrounding quotes if present
        if (hostPath.startsWith("\"") && hostPath.endsWith("\"")) {
            hostPath = hostPath.substring(1, hostPath.length() - 1);
        }

        File dir = new File(hostPath);
        if (!dir.isDirectory()) {
            printLine("Verzeichnis nicht gefunden: " + hostPath);
            return;
        }

        mountTable.put(drive, dir.getAbsolutePath());
        printLine("Laufwerk " + letter + ": gemountet auf " + dir.getAbsolutePath());

        // If mounting current drive, update immediately
        if (drive == dos.getCurrentDrive()) {
            dos.setHostRootDir(dir.getAbsolutePath());
            dos.setCurrentDir("");
        }
    }

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
        // Direct VRAM clear - proper CLS implementation
        for (int i = 0; i < Int10Handler.ROWS * Int10Handler.COLS; i++) {
            int addr = Int10Handler.TEXT_BASE + i * 2;
            video.getMemory().writeByte(addr, 0x20); // space
            video.getMemory().writeByte(addr + 1, 0x07); // light gray on black
        }
        // Reset cursor to top-left
        video.setCursorPos(0, 0);
    }

    private void cmdVer() {
        printLine("JDOSBox version 0.74 (Pure Java Emulator)");
        printLine("CPU: x86 Real Mode (16-bit) | DOS 5.0 kompatibel");
        printLine("Keine nativen Abhaengigkeiten — laeuft ueberall mit Java!");
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
        printLine("  MOUNT   - Mount host directory as DOS drive");
        printLine("  EXIT    - Exit DOSBox");
        printLine("");
        printLine("EXE/COM-Dateien werden im Java-Emulator ausgefuehrt.");
        printLine("Kein natives DOSBox erforderlich!");
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

