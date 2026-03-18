package de.bund.zrb.winml.rt;

/**
 * Thrown when a WinRT / COM call returns a failing HRESULT.
 */
public class WinRtException extends RuntimeException {

    private final int hresult;

    public WinRtException(String function, int hresult) {
        super(function + " failed: HRESULT 0x" + Integer.toHexString(hresult));
        this.hresult = hresult;
    }

    public WinRtException(String function, int hresult, String detail) {
        super(function + " failed: HRESULT 0x" + Integer.toHexString(hresult) + " — " + detail);
        this.hresult = hresult;
    }

    /** Returns the raw HRESULT value. */
    public int getHresult() {
        return hresult;
    }
}

