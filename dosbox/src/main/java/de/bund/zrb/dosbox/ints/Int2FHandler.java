package de.bund.zrb.dosbox.ints;

import de.bund.zrb.dosbox.cpu.CPU;
import de.bund.zrb.dosbox.dos.DPMIManager;

/**
 * INT 2Fh handler — DOS Multiplex Interrupt.
 * Handles DPMI detection (AX=1687h) and other multiplex functions.
 */
public class Int2FHandler implements CPU.IntHandler {

    private final DPMIManager dpmi;

    public Int2FHandler(DPMIManager dpmi) {
        this.dpmi = dpmi;
    }

    @Override
    public void handle(CPU cpu) {
        int ax = cpu.regs.getAX();

        switch (ax) {
            case 0x1687: // DPMI — Detect DPMI Host
                cpu.regs.setAX(0);      // DPMI is available
                cpu.regs.setBX(0x005A); // DPMI version 0.90
                cpu.regs.setCL(0x03);   // 32-bit and 16-bit clients supported
                cpu.regs.setDX(0x005A); // DPMI version 0.90
                cpu.regs.setSI(0);      // Paragraphs needed for private data (0 = none)
                // ES:DI = DPMI entry point
                cpu.regs.es = DPMIManager.DPMI_ENTRY_SEG;
                cpu.regs.setDI(DPMIManager.DPMI_ENTRY_OFS);
                break;

            case 0x1686: // DPMI — Get CPU Mode
                // AX=0 means protected mode, AX=1 means real mode
                cpu.regs.setAX(dpmi.isDpmiActive() ? 0 : 1);
                break;

            case 0x4300: // XMS — Detect XMS Driver
                cpu.regs.setAL(0); // Not installed
                break;

            case 0x4310: // XMS — Get Driver Address
                cpu.regs.flags.setCF(true); // Not available
                break;

            default:
                // Most multiplex functions: return unchanged (not installed)
                break;
        }
    }
}

