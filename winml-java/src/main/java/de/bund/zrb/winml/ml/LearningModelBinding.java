package de.bund.zrb.winml.ml;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.ptr.PointerByReference;
import de.bund.zrb.winml.rt.ComVTable;
import de.bund.zrb.winml.rt.HString;
import de.bund.zrb.winml.rt.WinRtException;
import de.bund.zrb.winml.rt.WinRtRuntime;

import java.io.Closeable;
import java.util.logging.Logger;

/**
 * Java wrapper for {@code Windows.AI.MachineLearning.LearningModelBinding}.
 * <p>
 * Binds named inputs and outputs to a session before evaluation.
 */
public final class LearningModelBinding implements Closeable {

    private static final Logger LOG = Logger.getLogger(LearningModelBinding.class.getName());

    private static final String RUNTIME_CLASS =
            "Windows.AI.MachineLearning.LearningModelBinding";

    /**
     * IID for ILearningModelBindingFactory.
     * {C95F7A7A-E788-475E-8917-23AA381FAF0B}
     */
    private static final Guid.GUID IID_ILEARNINGMODELBINDING_FACTORY = new Guid.GUID(
            "{C95F7A7A-E788-475E-8917-23AA381FAF0B}");

    // ILearningModelBindingFactory:
    // [0..5] IInspectable
    // [6] CreateFromSession(ILearningModelSession, out ILearningModelBinding)
    private static final int VT_CREATE_FROM_SESSION = 6;

    /**
     * IID for ILearningModelBinding (instance interface).
     * {EA312F20-168F-4F8C-94FE-2E7AC31B4AA8}
     */
    private static final Guid.GUID IID_ILEARNINGMODELBINDING = new Guid.GUID(
            "{EA312F20-168F-4F8C-94FE-2E7AC31B4AA8}");

    // ILearningModelBinding instance:
    // [0..5] IInspectable
    // [6] Bind(HSTRING name, IInspectable value)
    // [7] Bind(HSTRING name, IInspectable value, IPropertySet props)
    // [8] Clear()
    private static final int VT_BIND = 6;
    private static final int VT_CLEAR = 8;

    private Pointer ptr;

    private LearningModelBinding(Pointer ptr) {
        this.ptr = ptr;
    }

    /**
     * Creates a new binding for the given session.
     */
    public static LearningModelBinding create(LearningModelSession session) {
        WinRtRuntime.initialize();

        Pointer factory = WinRtRuntime.getActivationFactory(
                RUNTIME_CLASS, IID_ILEARNINGMODELBINDING_FACTORY);

        try {
            PointerByReference bindingRef = new PointerByReference();
            int hr = ComVTable.call(factory, VT_CREATE_FROM_SESSION,
                    session.getPointer(), bindingRef);

            if (hr < 0) {
                throw new WinRtException("LearningModelBinding.CreateFromSession", hr);
            }

            return new LearningModelBinding(bindingRef.getValue());
        } finally {
            ComVTable.release(factory);
        }
    }

    /**
     * Binds a value (e.g. a TensorFloat) to a named input/output.
     *
     * @param name      the feature name (as defined in the model)
     * @param valuePtr  COM pointer to the IInspectable value (e.g. a TensorFloat)
     */
    public void bind(String name, Pointer valuePtr) {
        try (HString nameStr = HString.create(name)) {
            int hr = ComVTable.call(ptr, VT_BIND,
                    nameStr.getHandle(), valuePtr);
            if (hr < 0) {
                throw new WinRtException("LearningModelBinding.Bind", hr,
                        "name=" + name);
            }
        }
    }

    /** Clears all bindings. */
    public void clear() {
        ComVTable.callOrThrow("LearningModelBinding.Clear", ptr, VT_CLEAR);
    }

    public Pointer getPointer() { return ptr; }

    @Override
    public void close() {
        if (ptr != null) {
            ComVTable.release(ptr);
            ptr = null;
        }
    }
}

