package de.bund.zrb.winml.ml;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.ptr.PointerByReference;
import de.bund.zrb.winml.rt.ComVTable;
import de.bund.zrb.winml.rt.WinRtException;
import de.bund.zrb.winml.rt.WinRtRuntime;

import java.io.Closeable;
import java.util.logging.Logger;

/**
 * Java wrapper for {@code Windows.AI.MachineLearning.LearningModelSession}.
 * <p>
 * Represents an inference session bound to a specific model and device.
 *
 * <pre>{@code
 * try (LearningModel model = LearningModel.loadFromFilePath("model.onnx");
 *      LearningModelDevice device = LearningModelDevice.create(Kind.DIRECTX_HIGH_PERFORMANCE);
 *      LearningModelSession session = LearningModelSession.create(model, device)) {
 *     // bind inputs, evaluate
 * }
 * }</pre>
 */
public final class LearningModelSession implements Closeable {

    private static final Logger LOG = Logger.getLogger(LearningModelSession.class.getName());

    private static final String RUNTIME_CLASS =
            "Windows.AI.MachineLearning.LearningModelSession";

    /**
     * IID for ILearningModelSessionFactory.
     * {0F6B881D-1C9B-47B2-BF08-25628D7D16AD}
     */
    private static final Guid.GUID IID_ILEARNINGMODELSESSION_FACTORY = new Guid.GUID(
            "{0F6B881D-1C9B-47B2-BF08-25628D7D16AD}");

    // ILearningModelSessionFactory VTable:
    // [0..5] IInspectable
    // [6] CreateFromModel(ILearningModel, out ILearningModelSession)
    // [7] CreateFromModelOnDevice(ILearningModel, ILearningModelDevice, out ILearningModelSession)
    private static final int VT_CREATE_FROM_MODEL = 6;
    private static final int VT_CREATE_FROM_MODEL_ON_DEVICE = 7;

    private Pointer ptr;

    private LearningModelSession(Pointer ptr) {
        this.ptr = ptr;
    }

    /**
     * Creates a session with the default device.
     */
    public static LearningModelSession create(LearningModel model) {
        WinRtRuntime.initialize();

        Pointer factory = WinRtRuntime.getActivationFactory(
                RUNTIME_CLASS, IID_ILEARNINGMODELSESSION_FACTORY);

        try {
            PointerByReference sessionRef = new PointerByReference();
            int hr = ComVTable.call(factory, VT_CREATE_FROM_MODEL,
                    model.getPointer(), sessionRef);

            if (hr < 0) {
                throw new WinRtException("LearningModelSession.CreateFromModel", hr);
            }

            LOG.info("WinML session created (default device)");
            return new LearningModelSession(sessionRef.getValue());
        } finally {
            ComVTable.release(factory);
        }
    }

    /**
     * Creates a session on a specific device (e.g. DirectX GPU).
     */
    public static LearningModelSession create(LearningModel model, LearningModelDevice device) {
        WinRtRuntime.initialize();

        Pointer factory = WinRtRuntime.getActivationFactory(
                RUNTIME_CLASS, IID_ILEARNINGMODELSESSION_FACTORY);

        try {
            PointerByReference sessionRef = new PointerByReference();
            int hr = ComVTable.call(factory, VT_CREATE_FROM_MODEL_ON_DEVICE,
                    model.getPointer(), device.getPointer(), sessionRef);

            if (hr < 0) {
                throw new WinRtException("LearningModelSession.CreateFromModelOnDevice", hr);
            }

            LOG.info("WinML session created (explicit device)");
            return new LearningModelSession(sessionRef.getValue());
        } finally {
            ComVTable.release(factory);
        }
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

