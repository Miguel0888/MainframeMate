package de.bund.zrb.winml.rt;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * Helpers for reading WinRT collection interfaces via VTable calls.
 * <p>
 * Supports:
 * <ul>
 *   <li>{@code IMapView<HSTRING, IInspectable>} — for reading evaluation outputs</li>
 *   <li>{@code IVectorView<Single>} — for reading float tensor data</li>
 *   <li>{@code IVectorView<Int64>} — for reading tensor shapes</li>
 * </ul>
 */
public final class WinRtCollections {

    private WinRtCollections() {}

    // ── IMapView<HSTRING, IInspectable> ──────────────────────
    // VTable:
    //   [0..5] IInspectable
    //   [6] Lookup(HSTRING key, out IInspectable value)
    //   [7] get_Size(out UINT32)
    //   [8] HasKey(HSTRING key, out BOOLEAN)
    //   [9] Split(out IMapView first, out IMapView second)

    /**
     * Looks up a value in an {@code IMapView<HSTRING, IInspectable>} by key.
     *
     * @param mapView COM pointer to the IMapView
     * @param key     the string key to look up
     * @return COM pointer to the value (IInspectable), or null if not found
     * @throws WinRtException if the lookup fails
     */
    public static Pointer mapLookup(Pointer mapView, String key) {
        try (HString keyStr = HString.create(key)) {
            PointerByReference valueRef = new PointerByReference();
            int hr = ComVTable.call(mapView, 6, keyStr.getHandle(), valueRef);
            if (hr < 0) {
                throw new WinRtException("IMapView.Lookup", hr, "key=" + key);
            }
            return valueRef.getValue();
        }
    }

    /**
     * Gets the size of an {@code IMapView}.
     */
    public static int mapSize(Pointer mapView) {
        IntByReference sizeRef = new IntByReference();
        int hr = ComVTable.call(mapView, 7, sizeRef);
        if (hr < 0) {
            throw new WinRtException("IMapView.get_Size", hr);
        }
        return sizeRef.getValue();
    }

    /**
     * Checks if an {@code IMapView<HSTRING, IInspectable>} contains a key.
     */
    public static boolean mapHasKey(Pointer mapView, String key) {
        try (HString keyStr = HString.create(key)) {
            IntByReference foundRef = new IntByReference();
            int hr = ComVTable.call(mapView, 8, keyStr.getHandle(), foundRef);
            if (hr < 0) return false;
            return foundRef.getValue() != 0;
        }
    }

    // ── IVectorView<Single/Float> ────────────────────────────
    // VTable:
    //   [0..5] IInspectable
    //   [6] GetAt(UINT32 index, out T)
    //   [7] get_Size(out UINT32)
    //   [8] IndexOf(T value, out UINT32 index, out BOOLEAN found)
    //   [9] GetMany(UINT32 startIndex, UINT32 capacity, T* items, out UINT32 actual)

    /**
     * Gets the size of an {@code IVectorView}.
     */
    public static int vectorSize(Pointer vectorView) {
        IntByReference sizeRef = new IntByReference();
        int hr = ComVTable.call(vectorView, 7, sizeRef);
        if (hr < 0) {
            throw new WinRtException("IVectorView.get_Size", hr);
        }
        return sizeRef.getValue();
    }

    /**
     * Reads all float values from an {@code IVectorView<Single>} using GetMany.
     *
     * @param vectorView COM pointer to IVectorView<Single>
     * @return float array with all values
     */
    public static float[] vectorGetManyFloat(Pointer vectorView) {
        int size = vectorSize(vectorView);
        if (size == 0) return new float[0];

        // Allocate native memory for the output array
        Memory buffer = new Memory((long) size * 4);
        IntByReference actualRef = new IntByReference();

        // GetMany(startIndex=0, capacity=size, items=buffer, out actual)
        int hr = ComVTable.call(vectorView, 9, 0, size, buffer, actualRef);
        if (hr < 0) {
            throw new WinRtException("IVectorView<Single>.GetMany", hr);
        }

        int actual = actualRef.getValue();
        float[] result = new float[actual];
        for (int i = 0; i < actual; i++) {
            result[i] = buffer.getFloat((long) i * 4);
        }
        return result;
    }

    /**
     * Reads all int64 values from an {@code IVectorView<Int64>} using GetMany.
     *
     * @param vectorView COM pointer to IVectorView<Int64>
     * @return long array with all values
     */
    public static long[] vectorGetManyInt64(Pointer vectorView) {
        int size = vectorSize(vectorView);
        if (size == 0) return new long[0];

        Memory buffer = new Memory((long) size * 8);
        IntByReference actualRef = new IntByReference();

        int hr = ComVTable.call(vectorView, 9, 0, size, buffer, actualRef);
        if (hr < 0) {
            throw new WinRtException("IVectorView<Int64>.GetMany", hr);
        }

        int actual = actualRef.getValue();
        long[] result = new long[actual];
        for (int i = 0; i < actual; i++) {
            result[i] = buffer.getLong((long) i * 8);
        }
        return result;
    }

    /**
     * Gets a single float from {@code IVectorView<Single>} at the given index.
     */
    public static float vectorGetAtFloat(Pointer vectorView, int index) {
        // For float, GetAt stores into a 4-byte out parameter
        Memory outVal = new Memory(4);
        int hr = ComVTable.call(vectorView, 6, index, outVal);
        if (hr < 0) {
            throw new WinRtException("IVectorView<Single>.GetAt", hr, "index=" + index);
        }
        return outVal.getFloat(0);
    }

    /**
     * Gets a single long from {@code IVectorView<Int64>} at the given index.
     */
    public static long vectorGetAtInt64(Pointer vectorView, int index) {
        Memory outVal = new Memory(8);
        int hr = ComVTable.call(vectorView, 6, index, outVal);
        if (hr < 0) {
            throw new WinRtException("IVectorView<Int64>.GetAt", hr, "index=" + index);
        }
        return outVal.getLong(0);
    }
}

