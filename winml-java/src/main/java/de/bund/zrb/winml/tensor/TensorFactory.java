package de.bund.zrb.winml.tensor;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.ptr.PointerByReference;
import de.bund.zrb.winml.rt.ComVTable;
import de.bund.zrb.winml.rt.WinRtCollections;
import de.bund.zrb.winml.rt.WinRtException;
import de.bund.zrb.winml.rt.WinRtRuntime;

import java.util.logging.Logger;

/**
 * Factory for creating and reading WinML tensor objects.
 * <p>
 * Uses {@code ITensorFloatStatics2} and {@code ITensorInt64BitStatics2} which
 * accept raw arrays (available since Windows 10 1903 / Build 18362).
 * This avoids the complexity of creating {@code IIterable<Int64>} from Java.
 * <p>
 * Supported tensor types:
 * <ul>
 *   <li>{@code TensorFloat}  — 32-bit floating point</li>
 *   <li>{@code TensorInt64Bit} — 64-bit integer</li>
 * </ul>
 */
public final class TensorFactory {

    private static final Logger LOG = Logger.getLogger(TensorFactory.class.getName());

    private TensorFactory() {}

    // ── Runtime class names ──────────────────────────────────

    private static final String TENSOR_FLOAT_CLASS =
            "Windows.AI.MachineLearning.TensorFloat";
    private static final String TENSOR_INT64_CLASS =
            "Windows.AI.MachineLearning.TensorInt64Bit";

    // ── IIDs for Statics2 interfaces (raw array methods) ─────

    /**
     * ITensorFloatStatics2:
     * <pre>
     *   [0..5] IInspectable
     *   [6] CreateFromShapeArrayAndDataArray(UINT32 shapeLen, INT64* shape,
     *                                        UINT32 dataLen, FLOAT* data,
     *                                        out ITensorFloat)
     *   [7] CreateFromBuffer(...)
     * </pre>
     */
    private static final Guid.GUID IID_ITENSORFLOAT_STATICS2 = new Guid.GUID(
            "{24610BC1-5E44-5713-B281-8F4AD4D555E8}");

    /**
     * ITensorInt64BitStatics2:
     * <pre>
     *   [6] CreateFromShapeArrayAndDataArray(UINT32 shapeLen, INT64* shape,
     *                                        UINT32 dataLen, INT64* data,
     *                                        out ITensorInt64Bit)
     * </pre>
     */
    private static final Guid.GUID IID_ITENSORINT64_STATICS2 = new Guid.GUID(
            "{6D3D9DCB-FF40-5EC2-89FE-084E2B6BC6DB}");

    // ── Instance IIDs (for QI when reading outputs) ──────────

    /** ITensorFloat instance interface (discovered via GetIids on Win11 23H2). */
    static final Guid.GUID IID_ITENSORFLOAT = new Guid.GUID(
            "{F2282D82-AA02-42C8-A0C8-DF1EFC9676E1}");

    /** ITensorInt64Bit instance interface. */
    static final Guid.GUID IID_ITENSORINT64 = new Guid.GUID(
            "{499665BA-1D2E-4C6B-B517-3576828D23B0}");

    /** ITensor (base interface for shape access). */
    // [6] get_TensorKind(out TensorKind)
    // [7] get_Shape(out IVectorView<Int64>)
    static final Guid.GUID IID_ITENSOR = new Guid.GUID(
            "{05489593-A305-4A25-AD09-440119B4B7F6}");

    // VTable indices
    private static final int VT_STATICS2_CREATE_FROM_ARRAYS = 6;
    private static final int VT_TENSOR_GET_AS_VECTOR_VIEW = 6;
    private static final int VT_TENSOR_GET_SHAPE = 7;

    // ══════════════════════════════════════════════════════════
    //  CREATE — TensorFloat
    // ══════════════════════════════════════════════════════════

    /**
     * Creates a {@code TensorFloat} with data.
     *
     * @param shape tensor dimensions (e.g. {1, 32000})
     * @param data  float data values
     * @return COM pointer to ITensorFloat (caller must release)
     */
    public static Pointer createTensorFloat(long[] shape, float[] data) {
        WinRtRuntime.initialize();

        Pointer statics = WinRtRuntime.getActivationFactory(
                TENSOR_FLOAT_CLASS, IID_ITENSORFLOAT_STATICS2);
        try {
            Memory shapeMem = longArrayToMemory(shape);
            Memory dataMem = floatArrayToMemory(data);

            PointerByReference tensorRef = new PointerByReference();
            int hr = ComVTable.call(statics, VT_STATICS2_CREATE_FROM_ARRAYS,
                    shape.length, shapeMem,
                    data.length, dataMem,
                    tensorRef);

            if (hr < 0) {
                throw new WinRtException("TensorFloat.CreateFromShapeArrayAndDataArray", hr);
            }
            return tensorRef.getValue();
        } finally {
            ComVTable.release(statics);
        }
    }

    /**
     * Creates an empty {@code TensorFloat} (e.g. for output bindings).
     */
    public static Pointer createTensorFloat(long[] shape) {
        return createTensorFloat(shape, new float[totalElements(shape)]);
    }

    // ══════════════════════════════════════════════════════════
    //  CREATE — TensorInt64Bit
    // ══════════════════════════════════════════════════════════

    /**
     * Creates a {@code TensorInt64Bit} with data.
     */
    public static Pointer createTensorInt64(long[] shape, long[] data) {
        WinRtRuntime.initialize();

        Pointer statics = WinRtRuntime.getActivationFactory(
                TENSOR_INT64_CLASS, IID_ITENSORINT64_STATICS2);
        try {
            Memory shapeMem = longArrayToMemory(shape);
            Memory dataMem = longArrayToMemory(data);

            PointerByReference tensorRef = new PointerByReference();
            int hr = ComVTable.call(statics, VT_STATICS2_CREATE_FROM_ARRAYS,
                    shape.length, shapeMem,
                    data.length, dataMem,
                    tensorRef);

            if (hr < 0) {
                throw new WinRtException("TensorInt64Bit.CreateFromShapeArrayAndDataArray", hr);
            }
            return tensorRef.getValue();
        } finally {
            ComVTable.release(statics);
        }
    }

    /**
     * Creates an empty {@code TensorInt64Bit} (e.g. for output bindings).
     */
    public static Pointer createTensorInt64(long[] shape) {
        return createTensorInt64(shape, new long[totalElements(shape)]);
    }

    // ══════════════════════════════════════════════════════════
    //  READ — Tensor data from output pointers
    // ══════════════════════════════════════════════════════════

    /**
     * Reads float data from a tensor pointer.
     * First tries QI for ITensorFloat; if that fails, tries direct VTable call
     * (factory-returned objects are often already in the primary interface).
     *
     * @param tensorInspectable COM pointer to an IInspectable that implements ITensorFloat
     * @return float array with all tensor values
     */
    public static float[] readTensorFloat(Pointer tensorInspectable) {
        // Try QI first (needed for output map values)
        PointerByReference qiRef = new PointerByReference();
        int hr = ComVTable.call(tensorInspectable, 0, IID_ITENSORFLOAT, qiRef);

        if (hr >= 0 && qiRef.getValue() != null) {
            Pointer tensorFloat = qiRef.getValue();
            try {
                return readVectorViewFloat(tensorFloat);
            } finally {
                ComVTable.release(tensorFloat);
            }
        }

        // Fallback: object might already be in ITensorFloat interface (direct VTable call)
        LOG.fine("QI(ITensorFloat) failed (0x" + Integer.toHexString(hr)
                + "), trying direct VTable call");
        return readVectorViewFloat(tensorInspectable);
    }

    /**
     * Reads int64 data from a tensor pointer.
     */
    public static long[] readTensorInt64(Pointer tensorInspectable) {
        PointerByReference qiRef = new PointerByReference();
        int hr = ComVTable.call(tensorInspectable, 0, IID_ITENSORINT64, qiRef);

        if (hr >= 0 && qiRef.getValue() != null) {
            Pointer tensorInt64 = qiRef.getValue();
            try {
                return readVectorViewInt64(tensorInt64);
            } finally {
                ComVTable.release(tensorInt64);
            }
        }

        // Fallback: direct VTable call
        LOG.fine("QI(ITensorInt64Bit) failed, trying direct VTable call");
        return readVectorViewInt64(tensorInspectable);
    }

    private static float[] readVectorViewFloat(Pointer tensor) {
        PointerByReference vectorViewRef = new PointerByReference();
        int hr = ComVTable.call(tensor, VT_TENSOR_GET_AS_VECTOR_VIEW, vectorViewRef);
        if (hr < 0) {
            throw new WinRtException("ITensorFloat.GetAsVectorView", hr);
        }

        Pointer vectorView = vectorViewRef.getValue();
        try {
            return WinRtCollections.vectorGetManyFloat(vectorView);
        } finally {
            ComVTable.release(vectorView);
        }
    }

    private static long[] readVectorViewInt64(Pointer tensor) {
        PointerByReference vectorViewRef = new PointerByReference();
        int hr = ComVTable.call(tensor, VT_TENSOR_GET_AS_VECTOR_VIEW, vectorViewRef);
        if (hr < 0) {
            throw new WinRtException("ITensorInt64Bit.GetAsVectorView", hr);
        }

        Pointer vectorView = vectorViewRef.getValue();
        try {
            return WinRtCollections.vectorGetManyInt64(vectorView);
        } finally {
            ComVTable.release(vectorView);
        }
    }

    /**
     * Reads the shape of any tensor via ITensor.get_Shape().
     */
    public static long[] readTensorShape(Pointer tensorInspectable) {
        PointerByReference qiRef = new PointerByReference();
        int hr = ComVTable.call(tensorInspectable, 0, IID_ITENSOR, qiRef);

        Pointer tensor;
        boolean release;
        if (hr >= 0 && qiRef.getValue() != null) {
            tensor = qiRef.getValue();
            release = true;
        } else {
            // Fallback: try IInspectable → ITensor might be at different offset
            // For factory-returned objects, ITensor is typically accessible via QI
            tensor = tensorInspectable;
            release = false;
            LOG.fine("QI(ITensor) failed (0x" + Integer.toHexString(hr)
                    + "), will try GetRuntimeClassName to find shape");
        }

        try {
            PointerByReference shapeRef = new PointerByReference();
            // ITensor VTable: [6] get_TensorKind, [7] get_Shape
            int hr2 = ComVTable.call(tensor, VT_TENSOR_GET_SHAPE, shapeRef);
            if (hr2 < 0) {
                throw new WinRtException("ITensor.get_Shape", hr2);
            }

            Pointer shapeView = shapeRef.getValue();
            try {
                return WinRtCollections.vectorGetManyInt64(shapeView);
            } finally {
                ComVTable.release(shapeView);
            }
        } finally {
            if (release) ComVTable.release(tensor);
        }
    }

    /**
     * Checks if the given IInspectable implements ITensorFloat.
     */
    public static boolean isTensorFloat(Pointer inspectable) {
        return tryQi(inspectable, IID_ITENSORFLOAT);
    }

    /**
     * Checks if the given IInspectable implements ITensorInt64Bit.
     */
    public static boolean isTensorInt64(Pointer inspectable) {
        return tryQi(inspectable, IID_ITENSORINT64);
    }

    // ── Helpers ──────────────────────────────────────────────

    private static boolean tryQi(Pointer ptr, Guid.GUID iid) {
        PointerByReference ref = new PointerByReference();
        int hr = ComVTable.call(ptr, 0, iid, ref);
        if (hr == 0 && ref.getValue() != null) {
            ComVTable.release(ref.getValue());
            return true;
        }
        return false;
    }

    private static Memory longArrayToMemory(long[] values) {
        Memory mem = new Memory((long) values.length * 8);
        for (int i = 0; i < values.length; i++) {
            mem.setLong((long) i * 8, values[i]);
        }
        return mem;
    }

    private static Memory floatArrayToMemory(float[] values) {
        Memory mem = new Memory((long) values.length * 4);
        for (int i = 0; i < values.length; i++) {
            mem.setFloat((long) i * 4, values[i]);
        }
        return mem;
    }

    private static int totalElements(long[] shape) {
        int total = 1;
        for (long dim : shape) {
            total *= (int) dim;
        }
        return total;
    }
}
