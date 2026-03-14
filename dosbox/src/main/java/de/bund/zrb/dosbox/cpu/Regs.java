package de.bund.zrb.dosbox.cpu;

/**
 * x86 CPU register file.
 * Supports 8-bit, 16-bit, and 32-bit access patterns.
 *
 * Ported from: include/regs.h
 */
public class Regs {

    // ── General purpose registers (stored as 32-bit) ────────
    private int eax, ebx, ecx, edx;
    private int esi, edi, ebp, esp;

    // ── Segment registers ───────────────────────────────────
    public int cs, ds, es, ss, fs, gs;

    // ── Instruction pointer ─────────────────────────────────
    public int eip;

    // ── Flags ───────────────────────────────────────────────
    public final Flags flags = new Flags();

    // ── 32-bit access ───────────────────────────────────────

    public int getEAX() { return eax; }
    public int getEBX() { return ebx; }
    public int getECX() { return ecx; }
    public int getEDX() { return edx; }
    public int getESI() { return esi; }
    public int getEDI() { return edi; }
    public int getEBP() { return ebp; }
    public int getESP() { return esp; }

    public void setEAX(int v) { eax = v; }
    public void setEBX(int v) { ebx = v; }
    public void setECX(int v) { ecx = v; }
    public void setEDX(int v) { edx = v; }
    public void setESI(int v) { esi = v; }
    public void setEDI(int v) { edi = v; }
    public void setEBP(int v) { ebp = v; }
    public void setESP(int v) { esp = v; }

    // ── 16-bit access ───────────────────────────────────────

    public int getAX() { return eax & 0xFFFF; }
    public int getBX() { return ebx & 0xFFFF; }
    public int getCX() { return ecx & 0xFFFF; }
    public int getDX() { return edx & 0xFFFF; }
    public int getSI() { return esi & 0xFFFF; }
    public int getDI() { return edi & 0xFFFF; }
    public int getBP() { return ebp & 0xFFFF; }
    public int getSP() { return esp & 0xFFFF; }

    public void setAX(int v) { eax = (eax & 0xFFFF0000) | (v & 0xFFFF); }
    public void setBX(int v) { ebx = (ebx & 0xFFFF0000) | (v & 0xFFFF); }
    public void setCX(int v) { ecx = (ecx & 0xFFFF0000) | (v & 0xFFFF); }
    public void setDX(int v) { edx = (edx & 0xFFFF0000) | (v & 0xFFFF); }
    public void setSI(int v) { esi = (esi & 0xFFFF0000) | (v & 0xFFFF); }
    public void setDI(int v) { edi = (edi & 0xFFFF0000) | (v & 0xFFFF); }
    public void setBP(int v) { ebp = (ebp & 0xFFFF0000) | (v & 0xFFFF); }
    public void setSP(int v) { esp = (esp & 0xFFFF0000) | (v & 0xFFFF); }

    // ── 8-bit access ────────────────────────────────────────

    public int getAL() { return eax & 0xFF; }
    public int getAH() { return (eax >> 8) & 0xFF; }
    public int getBL() { return ebx & 0xFF; }
    public int getBH() { return (ebx >> 8) & 0xFF; }
    public int getCL() { return ecx & 0xFF; }
    public int getCH() { return (ecx >> 8) & 0xFF; }
    public int getDL() { return edx & 0xFF; }
    public int getDH() { return (edx >> 8) & 0xFF; }

    public void setAL(int v) { eax = (eax & 0xFFFFFF00) | (v & 0xFF); }
    public void setAH(int v) { eax = (eax & 0xFFFF00FF) | ((v & 0xFF) << 8); }
    public void setBL(int v) { ebx = (ebx & 0xFFFFFF00) | (v & 0xFF); }
    public void setBH(int v) { ebx = (ebx & 0xFFFF00FF) | ((v & 0xFF) << 8); }
    public void setCL(int v) { ecx = (ecx & 0xFFFFFF00) | (v & 0xFF); }
    public void setCH(int v) { ecx = (ecx & 0xFFFF00FF) | ((v & 0xFF) << 8); }
    public void setDL(int v) { edx = (edx & 0xFFFFFF00) | (v & 0xFF); }
    public void setDH(int v) { edx = (edx & 0xFFFF00FF) | ((v & 0xFF) << 8); }

    // ── IP access ───────────────────────────────────────────

    /** Get EIP (full 32-bit instruction pointer). */
    public int getEIP() { return eip; }
    /** Set EIP (full 32-bit instruction pointer). */
    public void setEIP(int v) { eip = v; }

    /** Get IP (16-bit, for real mode / 16-bit code). */
    public int getIP() { return eip & 0xFFFF; }
    /** Set IP (16-bit, preserves upper 16 bits). */
    public void setIP(int v) { eip = (eip & 0xFFFF0000) | (v & 0xFFFF); }

    // ── Utility ─────────────────────────────────────────────

    /** Get register by index (0=AX,1=CX,2=DX,3=BX,4=SP,5=BP,6=SI,7=DI) */
    public int getReg16(int idx) {
        switch (idx & 7) {
            case 0: return getAX();
            case 1: return getCX();
            case 2: return getDX();
            case 3: return getBX();
            case 4: return getSP();
            case 5: return getBP();
            case 6: return getSI();
            case 7: return getDI();
            default: return 0;
        }
    }

    /** Set register by index */
    public void setReg16(int idx, int v) {
        switch (idx & 7) {
            case 0: setAX(v); break;
            case 1: setCX(v); break;
            case 2: setDX(v); break;
            case 3: setBX(v); break;
            case 4: setSP(v); break;
            case 5: setBP(v); break;
            case 6: setSI(v); break;
            case 7: setDI(v); break;
        }
    }

    /** Get 32-bit register by index (0=EAX,1=ECX,2=EDX,3=EBX,4=ESP,5=EBP,6=ESI,7=EDI) */
    public int getReg32(int idx) {
        switch (idx & 7) {
            case 0: return eax;
            case 1: return ecx;
            case 2: return edx;
            case 3: return ebx;
            case 4: return esp;
            case 5: return ebp;
            case 6: return esi;
            case 7: return edi;
            default: return 0;
        }
    }

    /** Set 32-bit register by index */
    public void setReg32(int idx, int v) {
        switch (idx & 7) {
            case 0: eax = v; break;
            case 1: ecx = v; break;
            case 2: edx = v; break;
            case 3: ebx = v; break;
            case 4: esp = v; break;
            case 5: ebp = v; break;
            case 6: esi = v; break;
            case 7: edi = v; break;
        }
    }

    /** Get 8-bit register by index (0=AL,1=CL,2=DL,3=BL,4=AH,5=CH,6=DH,7=BH) */
    public int getReg8(int idx) {
        switch (idx & 7) {
            case 0: return getAL();
            case 1: return getCL();
            case 2: return getDL();
            case 3: return getBL();
            case 4: return getAH();
            case 5: return getCH();
            case 6: return getDH();
            case 7: return getBH();
            default: return 0;
        }
    }

    /** Set 8-bit register by index */
    public void setReg8(int idx, int v) {
        switch (idx & 7) {
            case 0: setAL(v); break;
            case 1: setCL(v); break;
            case 2: setDL(v); break;
            case 3: setBL(v); break;
            case 4: setAH(v); break;
            case 5: setCH(v); break;
            case 6: setDH(v); break;
            case 7: setBH(v); break;
        }
    }

    /** Get segment register by index (0=ES,1=CS,2=SS,3=DS,4=FS,5=GS) */
    public int getSeg(int idx) {
        switch (idx & 7) {
            case 0: return es;
            case 1: return cs;
            case 2: return ss;
            case 3: return ds;
            case 4: return fs;
            case 5: return gs;
            default: return 0;
        }
    }

    /** Set segment register by index */
    public void setSeg(int idx, int v) {
        v &= 0xFFFF;
        switch (idx & 7) {
            case 0: es = v; break;
            case 1: cs = v; break;
            case 2: ss = v; break;
            case 3: ds = v; break;
            case 4: fs = v; break;
            case 5: gs = v; break;
        }
    }

    public void reset() {
        eax = ebx = ecx = edx = 0;
        esi = edi = ebp = 0;
        esp = 0;
        cs = 0xF000;
        eip = 0xFFF0;
        ds = es = ss = fs = gs = 0;
        flags.reset();
    }

    @Override
    public String toString() {
        return String.format(
            "AX=%04X BX=%04X CX=%04X DX=%04X SI=%04X DI=%04X BP=%04X SP=%04X\n" +
            "CS=%04X DS=%04X ES=%04X SS=%04X FS=%04X GS=%04X IP=%04X FL=%04X",
            getAX(), getBX(), getCX(), getDX(), getSI(), getDI(), getBP(), getSP(),
            cs, ds, es, ss, fs, gs, getIP(), flags.getWord()
        );
    }
}
