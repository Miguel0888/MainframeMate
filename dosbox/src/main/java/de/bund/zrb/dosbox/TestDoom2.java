package de.bund.zrb.dosbox;

import de.bund.zrb.dosbox.core.DOSBox;
import de.bund.zrb.dosbox.shell.DosShell;

/**
 * Quick test to run DOOM2 in the emulator without GUI.
 * Exits immediately after emulation completes.
 */
public class TestDoom2 {
    public static void main(String[] args) {
        System.out.println("[Test] Starting DOOM2 emulation test...");

        DOSBox dosbox = new DOSBox();
        DosShell shell = dosbox.getShell();

        // Mount doom2 directory as D:
        java.io.File doom2Dir = new java.io.File("doom2");
        if (!doom2Dir.isDirectory()) {
            System.err.println("[Test] doom2/ directory not found in: " + new java.io.File(".").getAbsolutePath());
            System.exit(1);
        }
        shell.mountDrive(3, doom2Dir.getAbsolutePath()); // D:

        // Enable trace
        dosbox.getCPU().getTrace().setEnabled(true);
        dosbox.getCPU().getTrace().clear();

        // Switch to D: and run DOOM2
        shell.executeCommand("D:");
        shell.executeCommand("DOOM2");

        System.out.println("[Test] Done.");
    }
}

