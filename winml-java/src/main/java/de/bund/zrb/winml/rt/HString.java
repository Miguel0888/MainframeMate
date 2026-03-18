package de.bund.zrb.winml.rt;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.PointerByReference;

import java.util.logging.Logger;

/**
 * Managed wrapper around WinRT HSTRING handles.
 * <p>
 * An HSTRING is an immutable reference-counted UTF-16 string used by all WinRT APIs.
 * This class implements {@link AutoCloseable} so it can be used in try-with-resources.
 *
 * <pre>{@code
 * try (HString hs = HString.create("Windows.AI.MachineLearning.LearningModel")) {
 *     Pointer raw = hs.getHandle();
 *     // ... pass to WinRT API
 * }
 * }</pre>
 */
public final class HString implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(HString.class.getName());

    private Pointer handle;

    private HString(Pointer handle) {
        this.handle = handle;
    }

    /**
     * Creates a new HSTRING from a Java string.
     *
     * @param text the string content
     * @return a new HString (must be closed)
     * @throws WinRtException if WindowsCreateString fails
     */
    public static HString create(String text) {
        if (text == null || text.isEmpty()) {
            return new HString(Pointer.NULL);
        }

        char[] chars = text.toCharArray();
        PointerByReference ref = new PointerByReference();
        WinNT.HRESULT hr = ComBase.INSTANCE.WindowsCreateString(chars, chars.length, ref);

        if (hr.intValue() != 0) {
            throw new WinRtException("WindowsCreateString", hr.intValue());
        }

        return new HString(ref.getValue());
    }

    /** Returns the native HSTRING handle for passing to WinRT APIs. */
    public Pointer getHandle() {
        return handle;
    }

    /** Reads the HSTRING content back into a Java String. */
    public String toJavaString() {
        if (handle == null || handle == Pointer.NULL) {
            return "";
        }
        com.sun.jna.ptr.IntByReference lenRef = new com.sun.jna.ptr.IntByReference();
        Pointer buf = ComBase.INSTANCE.WindowsGetStringRawBuffer(handle, lenRef);
        if (buf == null) return "";
        return new String(buf.getCharArray(0, lenRef.getValue()));
    }

    @Override
    public void close() {
        if (handle != null && handle != Pointer.NULL) {
            WinNT.HRESULT hr = ComBase.INSTANCE.WindowsDeleteString(handle);
            if (hr.intValue() != 0) {
                LOG.warning("WindowsDeleteString failed: 0x" + Integer.toHexString(hr.intValue()));
            }
            handle = null;
        }
    }

    @Override
    public String toString() {
        return toJavaString();
    }
}

