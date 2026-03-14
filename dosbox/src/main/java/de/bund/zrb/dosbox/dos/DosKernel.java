package de.bund.zrb.dosbox.dos;

import de.bund.zrb.dosbox.hardware.memory.Memory;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal DOS kernel — memory management, file handles, drive mapping.
 *
 * Ported from: src/dos/dos.cpp, src/dos/dos_memory.cpp, src/dos/dos_files.cpp
 */
public class DosKernel {

    private final Memory memory;

    private int currentDrive = 2; // C:
    private String currentDir = "";
    private int dtaAddress = 0x80;
    private int currentPSP = 0x100;

    // File handle table
    private final Map<Integer, DosFileHandle> openFiles = new HashMap<>();
    private int nextHandle = 5; // 0-4 reserved for STDIN, STDOUT, STDERR, STDAUX, STDPRN

    // Simple memory allocation (paragraph-based)
    private int nextFreeSeg = 0x1000; // Start allocating at segment 0x1000
    private int memoryTop = 0x9FFF;   // Top of conventional memory

    // Host directory mapped as drive C:
    private String hostRootDir = System.getProperty("user.dir");

    // FindFirst/FindNext state
    private File[] findFiles;
    private int findIndex;
    private String findPattern;

    public DosKernel(Memory memory) {
        this.memory = memory;
    }

    // ── Drive management ────────────────────────────────────

    public int getCurrentDrive() { return currentDrive; }
    public void setCurrentDrive(int drive) { this.currentDrive = drive & 0xFF; }
    public String getCurrentDir() { return currentDir; }
    public void setCurrentDir(String dir) { this.currentDir = dir; }
    public void setHostRootDir(String dir) { this.hostRootDir = dir; }
    public String getHostRootDir() { return hostRootDir; }

    // ── DTA ─────────────────────────────────────────────────

    public int getDTA() { return dtaAddress; }
    public void setDTA(int addr) { dtaAddress = addr; }
    public int getCurrentPSP() { return currentPSP; }

    // ── File operations ─────────────────────────────────────

    public int openFile(String dosPath, int mode) {
        String hostPath = dosPathToHost(dosPath);
        try {
            File f = new File(hostPath);
            if (!f.exists()) return -1;
            RandomAccessFile raf = new RandomAccessFile(f, (mode & 1) != 0 ? "rw" : "r");
            int handle = nextHandle++;
            openFiles.put(handle, new DosFileHandle(raf, f.getName()));
            return handle;
        } catch (IOException e) {
            return -1;
        }
    }

    public boolean closeFile(int handle) {
        DosFileHandle fh = openFiles.remove(handle);
        if (fh == null) return false;
        try { fh.file.close(); } catch (IOException ignored) {}
        return true;
    }

    public int readFile(int handle, Memory mem, int bufAddr, int count) {
        // Handle STDIN (0)
        if (handle == 0) {
            return 0; // STDIN not yet connected
        }
        DosFileHandle fh = openFiles.get(handle);
        if (fh == null) return -1;
        try {
            byte[] buf = new byte[count];
            int read = fh.file.read(buf);
            if (read <= 0) return 0;
            mem.writeBlock(bufAddr, buf, 0, read);
            return read;
        } catch (IOException e) {
            return -1;
        }
    }

    public int writeFile(int handle, Memory mem, int bufAddr, int count) {
        // Handle STDOUT (1) and STDERR (2)
        if (handle == 1 || handle == 2) {
            // Just consume - actual output goes through INT 10h
            return count;
        }
        DosFileHandle fh = openFiles.get(handle);
        if (fh == null) return -1;
        try {
            byte[] buf = new byte[count];
            mem.readBlock(bufAddr, buf, 0, count);
            fh.file.write(buf);
            return count;
        } catch (IOException e) {
            return -1;
        }
    }

    // ── Memory management (simple bump allocator) ───────────

    public int allocMemory(int paragraphs) {
        if (nextFreeSeg + paragraphs > memoryTop) return -1;
        int seg = nextFreeSeg;
        nextFreeSeg += paragraphs;
        return seg;
    }

    public boolean freeMemory(int segment) {
        // Simple implementation: we don't actually free
        return true;
    }

    public int getLargestFreeBlock() {
        return memoryTop - nextFreeSeg;
    }

    // ── FindFirst / FindNext ────────────────────────────────

    public boolean findFirst(String pattern, int attrib) {
        String hostPath = dosPathToHost(pattern);
        File dir = new File(hostPath).getParentFile();
        if (dir == null) dir = new File(hostRootDir);
        String filePattern = new File(hostPath).getName()
                .replace("*", ".*").replace("?", ".");

        File[] files = dir.listFiles();
        if (files == null) return false;

        // Filter by pattern
        java.util.List<File> matched = new java.util.ArrayList<>();
        for (File f : files) {
            if (f.getName().toUpperCase().matches(filePattern.toUpperCase())) {
                matched.add(f);
            }
        }

        if (matched.isEmpty()) return false;

        findFiles = matched.toArray(new File[0]);
        findIndex = 0;
        findPattern = filePattern;

        return writeFindResult(findFiles[findIndex++]);
    }

    public boolean findNext() {
        if (findFiles == null || findIndex >= findFiles.length) return false;
        return writeFindResult(findFiles[findIndex++]);
    }

    private boolean writeFindResult(File file) {
        // Write find result to DTA
        int dta = dtaAddress;
        // Attribute at offset 21
        int attr = 0;
        if (file.isDirectory()) attr |= 0x10;
        if (!file.canWrite()) attr |= 0x01;
        memory.writeByte(dta + 21, attr);

        // File size at offset 26 (dword)
        long size = file.length();
        memory.writeDWord(dta + 26, size);

        // File name at offset 30 (13 bytes, null-terminated)
        String name = file.getName().toUpperCase();
        if (name.length() > 12) name = name.substring(0, 12);
        memory.writeString(dta + 30, name);

        return true;
    }

    // ── Path conversion ─────────────────────────────────────

    private String dosPathToHost(String dosPath) {
        // Remove drive letter if present
        if (dosPath.length() >= 2 && dosPath.charAt(1) == ':') {
            dosPath = dosPath.substring(2);
        }
        // Convert backslashes
        dosPath = dosPath.replace('\\', File.separatorChar);
        if (dosPath.startsWith(File.separator)) {
            return hostRootDir + dosPath;
        }
        return hostRootDir + File.separator + currentDir + File.separator + dosPath;
    }

    // ── File handle wrapper ─────────────────────────────────

    private static class DosFileHandle {
        final RandomAccessFile file;
        final String name;

        DosFileHandle(RandomAccessFile file, String name) {
            this.file = file;
            this.name = name;
        }
    }
}

