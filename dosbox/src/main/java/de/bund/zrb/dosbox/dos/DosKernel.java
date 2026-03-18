package de.bund.zrb.dosbox.dos;

import de.bund.zrb.dosbox.hardware.memory.Memory;

import java.io.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private int dtaAddress = 0x80; // will be fixed to PSP:0080 when PSP is set
    private int currentPSP = 0x100;

    // File handle table
    private final Map<Integer, DosFileHandle> openFiles = new HashMap<>();
    private int nextHandle = 5; // 0-4 reserved for STDIN, STDOUT, STDERR, STDAUX, STDPRN

    // ── Memory management (tracked block allocator) ─────────
    // Tracks allocated blocks: segment -> size in paragraphs
    private final LinkedHashMap<Integer, Integer> memBlocks = new LinkedHashMap<>();
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

    // ── PSP & DTA ───────────────────────────────────────────

    public int getDTA() { return dtaAddress; }
    public void setDTA(int addr) { dtaAddress = addr; }
    public int getCurrentPSP() { return currentPSP; }

    /**
     * Set the current PSP segment. Also updates the default DTA
     * to PSP:0080 (linear address) unless DTA has been explicitly set.
     */
    public void setCurrentPSP(int pspSegment) {
        this.currentPSP = pspSegment;
        // Default DTA is always at PSP:0080
        this.dtaAddress = Memory.segOfs(pspSegment, 0x0080);
    }

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

    /** Create a new file. */
    public int createFile(String dosPath, int attrib) {
        String hostPath = dosPathToHost(dosPath);
        try {
            File f = new File(hostPath);
            RandomAccessFile raf = new RandomAccessFile(f, "rw");
            raf.setLength(0); // truncate
            int handle = nextHandle++;
            openFiles.put(handle, new DosFileHandle(raf, f.getName()));
            return handle;
        } catch (IOException e) {
            return -1;
        }
    }

    /** Seek file (lseek). Returns new position or -1 on error. */
    public long seekFile(int handle, long offset, int whence) {
        DosFileHandle fh = openFiles.get(handle);
        if (fh == null) return -1;
        try {
            long newPos;
            switch (whence) {
                case 0: // SEEK_SET
                    fh.file.seek(offset);
                    newPos = fh.file.getFilePointer();
                    break;
                case 1: // SEEK_CUR
                    fh.file.seek(fh.file.getFilePointer() + offset);
                    newPos = fh.file.getFilePointer();
                    break;
                case 2: // SEEK_END
                    fh.file.seek(fh.file.length() + offset);
                    newPos = fh.file.getFilePointer();
                    break;
                default:
                    return -1;
            }
            return newPos;
        } catch (IOException e) {
            return -1;
        }
    }

    /** Get file size (via seek to end and back). */
    public long getFileSize(int handle) {
        DosFileHandle fh = openFiles.get(handle);
        if (fh == null) return -1;
        try {
            return fh.file.length();
        } catch (IOException e) {
            return -1;
        }
    }

    /** Duplicate file handle. */
    public int dupHandle(int handle) {
        DosFileHandle fh = openFiles.get(handle);
        if (fh == null) return -1;
        int newHandle = nextHandle++;
        openFiles.put(newHandle, fh); // share the same underlying file
        return newHandle;
    }

    /** Force duplicate to specific handle. */
    public boolean forceDup(int srcHandle, int dstHandle) {
        DosFileHandle fh = openFiles.get(srcHandle);
        if (fh == null) return false;
        openFiles.put(dstHandle, fh);
        return true;
    }

    // ── Memory management (tracked block allocator) ─────────

    /**
     * Register a memory block that was allocated externally (e.g. by ProgramLoader).
     * This is needed so that resize/free can find the block later.
     */
    public void registerBlock(int segment, int paragraphs) {
        memBlocks.put(segment, paragraphs);
        int end = segment + paragraphs;
        if (end > nextFreeSeg) {
            nextFreeSeg = end;
        }
        System.out.printf("[DOS-MEM] Registered block: seg=%04X size=%04X paras (end=%04X)%n",
                segment, paragraphs, end);
    }

    public int allocMemory(int paragraphs) {
        // First try to allocate from the top of free space
        if (nextFreeSeg + paragraphs > memoryTop) return -1;
        int seg = nextFreeSeg;
        nextFreeSeg += paragraphs;
        memBlocks.put(seg, paragraphs);
        System.out.printf("[DOS-MEM] Alloc: seg=%04X size=%04X paras (nextFree=%04X)%n",
                seg, paragraphs, nextFreeSeg);
        return seg;
    }

    public boolean freeMemory(int segment) {
        Integer size = memBlocks.remove(segment);
        if (size == null) {
            System.out.printf("[DOS-MEM] Free FAILED: seg=%04X not found%n", segment);
            return false;
        }
        System.out.printf("[DOS-MEM] Free: seg=%04X size=%04X paras%n", segment, size);
        // If this was the topmost block, reclaim space
        if (segment + size == nextFreeSeg) {
            recalcNextFree();
        }
        return true;
    }

    /**
     * Resize a memory block. ES=segment, BX=new size in paragraphs.
     * Returns true on success. On failure, caller should set CF and report largest free.
     */
    public boolean resizeMemory(int segment, int newParagraphs) {
        Integer oldSize = memBlocks.get(segment);
        if (oldSize == null) {
            // Block not tracked — try to handle gracefully
            // This can happen for the initial program block if it wasn't registered
            System.out.printf("[DOS-MEM] Resize: seg=%04X NOT FOUND, registering as new block of %04X paras%n",
                    segment, newParagraphs);
            // Register it and succeed (best-effort)
            if (segment + newParagraphs > memoryTop) return false;
            memBlocks.put(segment, newParagraphs);
            int end = segment + newParagraphs;
            if (end > nextFreeSeg) nextFreeSeg = end;
            return true;
        }

        int oldEnd = segment + oldSize;
        int newEnd = segment + newParagraphs;

        if (newParagraphs <= oldSize) {
            // Shrink: always OK
            memBlocks.put(segment, newParagraphs);
            if (oldEnd == nextFreeSeg) {
                nextFreeSeg = newEnd;
            }
            System.out.printf("[DOS-MEM] Resize SHRINK: seg=%04X %04X->%04X paras (nextFree=%04X)%n",
                    segment, oldSize, newParagraphs, nextFreeSeg);
            return true;
        }

        // Grow: check if this is the last block and there's space
        if (oldEnd == nextFreeSeg) {
            if (newEnd > memoryTop) return false;
            memBlocks.put(segment, newParagraphs);
            nextFreeSeg = newEnd;
            System.out.printf("[DOS-MEM] Resize GROW: seg=%04X %04X->%04X paras (nextFree=%04X)%n",
                    segment, oldSize, newParagraphs, nextFreeSeg);
            return true;
        }

        // Can't grow non-last block
        System.out.printf("[DOS-MEM] Resize FAILED: seg=%04X can't grow %04X->%04X (not last block)%n",
                segment, oldSize, newParagraphs);
        return false;
    }

    public int getLargestFreeBlock() {
        return memoryTop - nextFreeSeg;
    }

    private void recalcNextFree() {
        int maxEnd = 0x1000; // base allocation start
        for (Map.Entry<Integer, Integer> entry : memBlocks.entrySet()) {
            int end = entry.getKey() + entry.getValue();
            if (end > maxEnd) maxEnd = end;
        }
        nextFreeSeg = maxEnd;
        System.out.printf("[DOS-MEM] Recalc nextFree=%04X%n", nextFreeSeg);
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

