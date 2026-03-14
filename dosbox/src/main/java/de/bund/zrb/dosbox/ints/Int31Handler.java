package de.bund.zrb.dosbox.ints;

import de.bund.zrb.dosbox.cpu.CPU;
import de.bund.zrb.dosbox.dos.DPMIManager;
import de.bund.zrb.dosbox.hardware.memory.Memory;

/**
 * INT 31h handler â€” DPMI (DOS Protected Mode Interface) services.
 * Provides descriptor management, memory allocation, interrupt
 * vector management, and other DPMI functions required by DOS extenders
 * like DOS/4GW (used by DOOM, DOOM2, Duke Nukem 3D, etc.).
 *
 * Reference: DPMI Specification 0.9 / 1.0
 */
public class Int31Handler implements CPU.IntHandler {

    private final DPMIManager dpmi;
    private final Memory memory;

    public Int31Handler(DPMIManager dpmi, Memory memory) {
        this.dpmi = dpmi;
        this.memory = memory;
    }

    @Override
    public void handle(CPU cpu) {
        int ax = cpu.regs.getAX();
        int func = ax;

        // Log all INT 31h calls
        System.out.printf("[INT31] AX=%04X BX=%04X CX=%04X DX=%04X SI=%04X DI=%04X ES=%04X%n",
                ax, cpu.regs.getBX(), cpu.regs.getCX(), cpu.regs.getDX(),
                cpu.regs.getSI(), cpu.regs.getDI(), cpu.regs.es);

        // Clear carry flag by default (success)
        cpu.regs.flags.setCF(false);

        switch (func) {
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0000h: Allocate LDT Descriptors
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0000: {
                int count = cpu.regs.getCX();
                if (count == 0) count = 1;
                int sel = dpmi.allocateDescriptors(count);
                if (sel >= 0) {
                    cpu.regs.setAX(sel);
                    System.out.printf("[INT31] 0000: Allocated %d descriptor(s), first sel=%04X (LDT[%d])%n",
                            count, sel, dpmi.selectorToIndex(sel));
                } else {
                    cpu.regs.flags.setCF(true);
                    cpu.regs.setAX(0x8011); // descriptor unavailable
                }
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0001h: Free LDT Descriptor
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0001: {
                if (!dpmi.freeDescriptor(cpu.regs.getBX())) {
                    cpu.regs.flags.setCF(true);
                    cpu.regs.setAX(0x8022); // invalid selector
                }
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0002h: Segment to Descriptor
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0002: {
                int sel = dpmi.segmentToDescriptor(cpu.regs.getBX());
                if (sel >= 0) {
                    cpu.regs.setAX(sel);
                } else {
                    cpu.regs.flags.setCF(true);
                    cpu.regs.setAX(0x8011);
                }
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0003h: Get Selector Increment Value
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0003:
                cpu.regs.setAX(dpmi.getSelectorIncrement());
                break;

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0006h: Get Segment Base Address
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0006: {
                int base = dpmi.getSegmentBase(cpu.regs.getBX());
                cpu.regs.setCX((base >> 16) & 0xFFFF);
                cpu.regs.setDX(base & 0xFFFF);
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0007h: Set Segment Base Address
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0007: {
                int base = (cpu.regs.getCX() << 16) | (cpu.regs.getDX() & 0xFFFF);
                int sel = cpu.regs.getBX();
                System.out.printf("[INT31] 0007: SetBase sel=%04X (LDT[%d]) base=%08X%n",
                        sel, dpmi.selectorToIndex(sel), base);
                if (!dpmi.setSegmentBase(sel, base)) {
                    cpu.regs.flags.setCF(true);
                    cpu.regs.setAX(0x8022);
                }
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0008h: Set Segment Limit
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0008: {
                int limit = (cpu.regs.getCX() << 16) | (cpu.regs.getDX() & 0xFFFF);
                if (!dpmi.setSegmentLimit(cpu.regs.getBX(), limit)) {
                    cpu.regs.flags.setCF(true);
                    cpu.regs.setAX(0x8022);
                }
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0009h: Set Descriptor Access Rights
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0009: {
                int sel = cpu.regs.getBX();
                int rights = cpu.regs.getCX();
                System.out.printf("[INT31] 0009: SetAccessRights sel=%04X (LDT[%d]) rights=%04X (is32=%b, present=%b, code=%b)%n",
                        sel, dpmi.selectorToIndex(sel), rights,
                        (rights & 0x4000) != 0, (rights & 0x0080) != 0, (rights & 0x0008) != 0);
                if (!dpmi.setAccessRights(sel, rights)) {
                    cpu.regs.flags.setCF(true);
                    cpu.regs.setAX(0x8022);
                }
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 000Ah: Create Alias Descriptor
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x000A: {
                int newSel = dpmi.createCodeAlias(cpu.regs.getBX());
                if (newSel >= 0) {
                    cpu.regs.setAX(newSel);
                } else {
                    cpu.regs.flags.setCF(true);
                    cpu.regs.setAX(0x8011);
                }
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 000Bh: Get Descriptor
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x000B: {
                int addr = cpu.resolveSegOfs(cpu.regs.es, cpu.regs.getDI());
                dpmi.getDescriptor(cpu.regs.getBX(), addr);
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 000Ch: Set Descriptor
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x000C: {
                int sel = cpu.regs.getBX();
                int addr = cpu.resolveSegOfs(cpu.regs.es, cpu.regs.getDI());
                // Log raw descriptor bytes
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 8; i++) sb.append(String.format("%02X ", memory.readByte(addr + i)));
                System.out.printf("[INT31] 000C: SetDescriptor sel=%04X (LDT[%d]) addr=%08X bytes=[%s]%n",
                        sel, dpmi.selectorToIndex(sel), addr, sb.toString().trim());
                if (!dpmi.setDescriptor(sel, addr)) {
                    cpu.regs.flags.setCF(true);
                    cpu.regs.setAX(0x8022);
                } else {
                    DPMIManager.LDTEntry e = dpmi.getEntry(sel);
                    System.out.printf("[INT31] 000C: Result: base=%08X limit=%05X is32=%b pageGran=%b access=%04X present=%b%n",
                            e.base, e.limit, e.is32Bit, e.pageGranular, e.accessRights, e.present);
                }
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0100h: Allocate DOS Memory Block
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0100: {
                int[] result = dpmi.allocateDOSMemory(cpu.regs.getBX());
                if (result != null) {
                    cpu.regs.setAX(result[0]); // real mode segment
                    cpu.regs.setDX(result[1]); // selector
                } else {
                    cpu.regs.flags.setCF(true);
                    cpu.regs.setAX(0x8012);
                    cpu.regs.setBX(0); // no memory available
                }
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0101h: Free DOS Memory Block
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0101:
                dpmi.freeDOSMemory(cpu.regs.getDX());
                break;

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0200h: Get Real Mode Interrupt Vector
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0200: {
                int intNum = cpu.regs.getBL();
                cpu.regs.setCX(dpmi.getRMIntVector_Seg(intNum));
                cpu.regs.setDX(dpmi.getRMIntVector_Ofs(intNum));
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0201h: Set Real Mode Interrupt Vector
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0201: {
                int intNum = cpu.regs.getBL();
                dpmi.setRMIntVector(intNum, cpu.regs.getCX(), cpu.regs.getDX());
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0204h: Get Protected Mode Interrupt Vector
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0204: {
                int intNum = cpu.regs.getBL();
                cpu.regs.setCX(dpmi.getPMIntVector_Sel(intNum));
                cpu.regs.setDX(dpmi.getPMIntVector_Ofs(intNum));
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0205h: Set Protected Mode Interrupt Vector
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0205: {
                int intNum = cpu.regs.getBL();
                dpmi.setPMIntVector(intNum, cpu.regs.getCX(), cpu.regs.getDX());
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0300h: Simulate Real Mode Interrupt
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0300: {
                int intNum = cpu.regs.getBL();
                simulateRealModeInterrupt(cpu, intNum);
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0301h: Call Real Mode Procedure (FAR CALL)
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0301: {
                simulateRealModeFarCall(cpu);
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0303h: Allocate Real Mode Callback Address
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0303: {
                // Stub: return a dummy callback address
                cpu.regs.setCX(0xF000);
                cpu.regs.setDX(0x8100);
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0304h: Free Real Mode Callback Address
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0304:
                break; // stub

            // ═══════════════════════════════════════════
            // 0202h: Get Processor Exception Handler Vector
            // ═══════════════════════════════════════════
            case 0x0202: {
                int excNum = cpu.regs.getBL();
                cpu.regs.setCX(dpmi.getExceptionHandler_Sel(excNum));
                cpu.regs.setDX(dpmi.getExceptionHandler_Ofs(excNum));
                break;
            }

            // ═══════════════════════════════════════════
            // 0203h: Set Processor Exception Handler Vector
            // ═══════════════════════════════════════════
            case 0x0203: {
                int excNum = cpu.regs.getBL();
                dpmi.setExceptionHandler(excNum, cpu.regs.getCX(), cpu.regs.getDX());
                break;
            }

            // ═══════════════════════════════════════════
            // 0305h: Get State Save/Restore Addresses
            // ═══════════════════════════════════════════
            case 0x0305: {
                // Returns addresses of procedures to save/restore DPMI host state.
                // AX = size of save state buffer (0 = no state to save)
                // BX:CX = real mode save/restore procedure (segment:offset)
                // SI:DI = protected mode save/restore procedure (selector:offset)
                cpu.regs.setAX(DPMIManager.STATE_SAVE_SIZE);
                cpu.regs.setBX(DPMIManager.STATE_SAVE_RM_SEG);
                cpu.regs.setCX(DPMIManager.STATE_SAVE_RM_OFS);
                cpu.regs.setSI(cpu.regs.cs);  // use current CS for PM stub
                cpu.regs.setDI(DPMIManager.STATE_SAVE_PM_OFS);
                break;
            }

            // ═══════════════════════════════════════════
            // 0306h: Get Raw Mode Switch Addresses
            // ═══════════════════════════════════════════
            case 0x0306: {
                // Returns addresses for raw real/protected mode switching.
                // BX:CX = real-to-protected mode switch address (segment:offset)
                // SI:DI = protected-to-real mode switch address (selector:offset)
                cpu.regs.setBX(DPMIManager.RAW_SWITCH_RM2PM_SEG);
                cpu.regs.setCX(DPMIManager.RAW_SWITCH_RM2PM_OFS);
                cpu.regs.setSI(cpu.regs.cs);  // use current CS for PM stub
                cpu.regs.setDI(DPMIManager.RAW_SWITCH_PM2RM_OFS);
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0400h: Get DPMI Version
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0400:
                cpu.regs.setAH(dpmi.getVersionMajor());
                cpu.regs.setAL(dpmi.getVersionMinor());
                cpu.regs.setBX(0x0005);  // flags: 32-bit, virtual memory
                cpu.regs.setCL(0x04);    // processor type: 486
                cpu.regs.setDX(0x0000);  // PIC base: master=08, slave=70
                cpu.regs.setDH(0x08);
                cpu.regs.setDL(0x70);
                break;

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0500h: Get Free Memory Information
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0500: {
                int addr = cpu.resolveSegOfs(cpu.regs.es, cpu.regs.getDI());
                dpmi.getFreeMemoryInfo(addr);
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0501h: Allocate Memory Block
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0501: {
                int size = (cpu.regs.getBX() << 16) | (cpu.regs.getCX() & 0xFFFF);
                int[] result = dpmi.allocateMemoryBlock(size);
                if (result != null) {
                    cpu.regs.setBX(result[0]); // linear addr high
                    cpu.regs.setCX(result[1]); // linear addr low
                    cpu.regs.setSI(result[2]); // handle high
                    cpu.regs.setDI(result[3]); // handle low
                } else {
                    cpu.regs.flags.setCF(true);
                    cpu.regs.setAX(0x8012);
                }
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0502h: Free Memory Block
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0502: {
                int handle = (cpu.regs.getSI() << 16) | (cpu.regs.getDI() & 0xFFFF);
                dpmi.freeMemoryBlock(handle);
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0503h: Resize Memory Block
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0503: {
                // Stub: just allocate new and return
                int newSize = (cpu.regs.getBX() << 16) | (cpu.regs.getCX() & 0xFFFF);
                int[] result = dpmi.allocateMemoryBlock(newSize);
                if (result != null) {
                    cpu.regs.setBX(result[0]);
                    cpu.regs.setCX(result[1]);
                    cpu.regs.setSI(result[2]);
                    cpu.regs.setDI(result[3]);
                } else {
                    cpu.regs.flags.setCF(true);
                    cpu.regs.setAX(0x8012);
                }
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0600h: Lock Linear Region (stub)
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0600:
                break; // always succeed

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0601h: Unlock Linear Region (stub)
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0601:
                break; // always succeed

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0604h: Get Page Size
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0604:
                cpu.regs.setBX(0);
                cpu.regs.setCX(0x1000); // 4096 bytes
                break;

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0800h: Physical Address Mapping
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0800: {
                // Map physical to linear 1:1
                cpu.regs.setBX(cpu.regs.getBX()); // same address
                cpu.regs.setCX(cpu.regs.getCX());
                break;
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0900h: Get and Disable Virtual Interrupt State
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0900:
                cpu.regs.setAL(cpu.regs.flags.getIF() ? 1 : 0);
                cpu.regs.flags.setIF(false);
                break;

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0901h: Get and Enable Virtual Interrupt State
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0901:
                cpu.regs.setAL(cpu.regs.flags.getIF() ? 1 : 0);
                cpu.regs.flags.setIF(true);
                break;

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // 0902h: Get Virtual Interrupt State
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            case 0x0902:
                cpu.regs.setAL(cpu.regs.flags.getIF() ? 1 : 0);
                break;

            // ═══════════════════════════════════════════
            // 0A00h: Get Vendor-Specific API Entry Point
            // ═══════════════════════════════════════════
            case 0x0A00:
                // Not supported — set carry flag (this is normal)
                cpu.regs.flags.setCF(true);
                break;

            default:
                System.err.printf("Unhandled DPMI INT 31h function: AX=%04X%n", ax);
                cpu.regs.flags.setCF(true);
                cpu.regs.setAX(0x8001); // unsupported function
                break;
        }
    }

    // â”€â”€ Real Mode Interrupt Simulation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Simulate a real mode interrupt from protected mode.
     * Uses the register structure at ES:EDI.
     */
    private void simulateRealModeInterrupt(CPU cpu, int intNum) {
        int structAddr = cpu.resolveSegOfs(cpu.regs.es, cpu.regs.getDI());

        // Read register structure (DPMI real mode call structure)
        int rdi = memory.readWord(structAddr + 0x00);
        int rsi = memory.readWord(structAddr + 0x04);
        int rbp = memory.readWord(structAddr + 0x08);
        // 0x0C reserved
        int rbx = memory.readWord(structAddr + 0x10);
        int rdx = memory.readWord(structAddr + 0x14);
        int rcx = memory.readWord(structAddr + 0x18);
        int rax = memory.readWord(structAddr + 0x1C);
        int rflags = memory.readWord(structAddr + 0x20);
        int res = memory.readWord(structAddr + 0x22);
        int rds = memory.readWord(structAddr + 0x24);
        int rfs = memory.readWord(structAddr + 0x26);
        int rgs = memory.readWord(structAddr + 0x28);
        // 0x2A = IP, 0x2C = CS, 0x2E = SP, 0x30 = SS

        // Save current registers
        int savedAX = cpu.regs.getAX(), savedBX = cpu.regs.getBX();
        int savedCX = cpu.regs.getCX(), savedDX = cpu.regs.getDX();
        int savedSI = cpu.regs.getSI(), savedDI = cpu.regs.getDI();
        int savedBP = cpu.regs.getBP();
        int savedDS = cpu.regs.ds, savedES = cpu.regs.es;
        int savedFS = cpu.regs.fs, savedGS = cpu.regs.gs;
        int savedFlags = cpu.regs.flags.getWord();

        // Load real mode registers
        cpu.regs.setAX(rax);
        cpu.regs.setBX(rbx);
        cpu.regs.setCX(rcx);
        cpu.regs.setDX(rdx);
        cpu.regs.setSI(rsi);
        cpu.regs.setDI(rdi);
        cpu.regs.setBP(rbp);
        cpu.regs.es = res;
        cpu.regs.ds = rds;
        cpu.regs.fs = rfs;
        cpu.regs.gs = rgs;

        // Execute the interrupt via Java handler
        cpu.softwareInt(intNum);

        // Write results back to structure
        memory.writeWord(structAddr + 0x00, cpu.regs.getDI());
        memory.writeWord(structAddr + 0x04, cpu.regs.getSI());
        memory.writeWord(structAddr + 0x08, cpu.regs.getBP());
        memory.writeWord(structAddr + 0x10, cpu.regs.getBX());
        memory.writeWord(structAddr + 0x14, cpu.regs.getDX());
        memory.writeWord(structAddr + 0x18, cpu.regs.getCX());
        memory.writeWord(structAddr + 0x1C, cpu.regs.getAX());
        memory.writeWord(structAddr + 0x20, cpu.regs.flags.getWord());
        memory.writeWord(structAddr + 0x22, cpu.regs.es);
        memory.writeWord(structAddr + 0x24, cpu.regs.ds);
        memory.writeWord(structAddr + 0x26, cpu.regs.fs);
        memory.writeWord(structAddr + 0x28, cpu.regs.gs);

        // Restore protected mode registers
        cpu.regs.setAX(savedAX);
        cpu.regs.setBX(savedBX);
        cpu.regs.setCX(savedCX);
        cpu.regs.setDX(savedDX);
        cpu.regs.setSI(savedSI);
        cpu.regs.setDI(savedDI);
        cpu.regs.setBP(savedBP);
        cpu.regs.ds = savedDS;
        cpu.regs.es = savedES;
        cpu.regs.fs = savedFS;
        cpu.regs.gs = savedGS;
        cpu.regs.flags.setWord(savedFlags);
    }

    /**
     * Simulate a real mode far call from protected mode.
     */
    private void simulateRealModeFarCall(CPU cpu) {
        int structAddr = cpu.resolveSegOfs(cpu.regs.es, cpu.regs.getDI());
        // Read CS:IP from structure
        int callIP = memory.readWord(structAddr + 0x2A);
        int callCS = memory.readWord(structAddr + 0x2C);

        // For now, just simulate as a NOP (return immediately)
        // Real implementation would switch to real mode and execute
        // TODO: Full real mode callback support
    }
}

