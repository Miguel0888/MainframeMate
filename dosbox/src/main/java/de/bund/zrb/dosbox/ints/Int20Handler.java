package de.bund.zrb.dosbox.ints;

import de.bund.zrb.dosbox.cpu.CPU;

/**
 * INT 20h handler — Terminate Program.
 * Called when a DOS program executes INT 20h or returns to PSP:0000.
 *
 * Ported from: src/dos/dos.cpp (DOS_Terminate)
 */
public class Int20Handler implements CPU.IntHandler {

    /** Listener that gets notified when a program terminates. */
    public interface TerminateListener {
        void onProgramTerminated(int returnCode);
    }

    private TerminateListener listener;
    private boolean terminated;
    private int returnCode;

    @Override
    public void handle(CPU cpu) {
        terminated = true;
        returnCode = 0;
        cpu.setRunning(false);
        if (listener != null) {
            listener.onProgramTerminated(returnCode);
        }
    }

    public void setListener(TerminateListener listener) {
        this.listener = listener;
    }

    public boolean isTerminated() {
        return terminated;
    }

    public int getReturnCode() {
        return returnCode;
    }

    public void reset() {
        terminated = false;
        returnCode = 0;
    }
}
