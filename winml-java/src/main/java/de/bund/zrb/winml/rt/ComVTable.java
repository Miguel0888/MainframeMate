package de.bund.zrb.winml.rt;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinNT;

/**
 * Helper for calling COM interface methods via VTable pointers.
 * <p>
 * Every COM object starts with a pointer to its VTable (array of function pointers).
 * The VTable layout for {@code IUnknown} is:
 * <pre>
 *   [0] QueryInterface
 *   [1] AddRef
 *   [2] Release
 * </pre>
 * {@code IInspectable} adds:
 * <pre>
 *   [3] GetIids
 *   [4] GetRuntimeClassName
 *   [5] GetTrustLevel
 * </pre>
 * WinML interfaces add their methods starting at index 6.
 */
public final class ComVTable {

    private ComVTable() {}

    /**
     * Calls a COM method by VTable index.
     *
     * @param comPtr    pointer to the COM object
     * @param vtIndex   0-based index in the VTable
     * @param args      arguments (first arg is always {@code comPtr} = "this")
     * @return HRESULT as int
     */
    public static int call(Pointer comPtr, int vtIndex, Object... args) {
        // comPtr → VTable pointer
        Pointer vtable = comPtr.getPointer(0);
        // vtable[vtIndex] → function pointer
        long fnAddr = Pointer.nativeValue(vtable) + (long) vtIndex * Native.POINTER_SIZE;
        Pointer fnPtr = new Pointer(Pointer.nativeValue(new Pointer(fnAddr).getPointer(0)));

        Function fn = Function.getFunction(fnPtr, Function.ALT_CONVENTION);

        // Prepend "this" pointer
        Object[] fullArgs = new Object[args.length + 1];
        fullArgs[0] = comPtr;
        System.arraycopy(args, 0, fullArgs, 1, args.length);

        return fn.invokeInt(fullArgs);
    }

    /**
     * Calls IUnknown::AddRef (VTable index 1).
     */
    public static int addRef(Pointer comPtr) {
        return call(comPtr, 1);
    }

    /**
     * Calls IUnknown::Release (VTable index 2).
     */
    public static int release(Pointer comPtr) {
        return call(comPtr, 2);
    }

    /**
     * Calls a COM method and throws if the HRESULT indicates failure.
     *
     * @param methodName human-readable name for error messages
     * @param comPtr     COM object pointer
     * @param vtIndex    VTable index
     * @param args       arguments (excluding "this")
     * @throws WinRtException if HRESULT < 0
     */
    public static void callOrThrow(String methodName, Pointer comPtr, int vtIndex, Object... args) {
        int hr = call(comPtr, vtIndex, args);
        if (hr < 0) {
            throw new WinRtException(methodName, hr);
        }
    }
}

