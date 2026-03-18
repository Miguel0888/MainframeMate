package de.bund.zrb.winml.rt;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

/**
 * JNA bindings for the WinRT base APIs needed to activate runtime classes.
 * <p>
 * Maps {@code combase.dll} functions:
 * <ul>
 *   <li>{@code RoInitialize} – initialize WinRT apartment</li>
 *   <li>{@code RoUninitialize} – tear down</li>
 *   <li>{@code RoGetActivationFactory} – get IActivationFactory for a runtime class</li>
 *   <li>{@code WindowsCreateString} / {@code WindowsDeleteString} – HSTRING management</li>
 * </ul>
 */
public interface ComBase extends StdCallLibrary {

    ComBase INSTANCE = Native.load("combase", ComBase.class);

    // ── RoInitialize ─────────────────────────────────────────

    /** Single-threaded apartment (STA). */
    int RO_INIT_SINGLETHREADED = 0;
    /** Multi-threaded apartment (MTA). */
    int RO_INIT_MULTITHREADED = 1;

    /**
     * Initializes the Windows Runtime on the current thread.
     *
     * @param initType {@link #RO_INIT_SINGLETHREADED} or {@link #RO_INIT_MULTITHREADED}
     * @return HRESULT (0 = S_OK, 1 = S_FALSE if already initialized)
     */
    WinNT.HRESULT RoInitialize(int initType);

    /** Closes the Windows Runtime on the current thread. */
    void RoUninitialize();

    // ── HSTRING ──────────────────────────────────────────────

    /**
     * Creates an HSTRING from a UTF-16 source string.
     *
     * @param sourceString  UTF-16 char pointer
     * @param length        number of chars (not bytes)
     * @param string        receives the HSTRING handle
     * @return HRESULT
     */
    WinNT.HRESULT WindowsCreateString(char[] sourceString, int length, PointerByReference string);

    /**
     * Deletes an HSTRING previously created with {@link #WindowsCreateString}.
     *
     * @param string HSTRING handle (may be {@code null})
     * @return HRESULT
     */
    WinNT.HRESULT WindowsDeleteString(Pointer string);

    /**
     * Gets the backing buffer of an HSTRING.
     *
     * @param string HSTRING handle
     * @param length receives the string length
     * @return pointer to the UTF-16 character data
     */
    Pointer WindowsGetStringRawBuffer(Pointer string, com.sun.jna.ptr.IntByReference length);

    // ── Activation ───────────────────────────────────────────

    /**
     * Gets the activation factory for a Windows Runtime class.
     *
     * @param activatableClassId HSTRING with the fully-qualified class name
     * @param iid                IID of the requested interface (typically IActivationFactory)
     * @param factory            receives the factory pointer
     * @return HRESULT
     */
    WinNT.HRESULT RoGetActivationFactory(Pointer activatableClassId,
                                          Guid.GUID iid,
                                          PointerByReference factory);
}

