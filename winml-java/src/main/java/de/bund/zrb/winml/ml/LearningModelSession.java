package de.bund.zrb.winml.ml;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.ptr.PointerByReference;
import de.bund.zrb.winml.rt.ComVTable;
import de.bund.zrb.winml.rt.HString;
import de.bund.zrb.winml.rt.WinRtAsync;
import de.bund.zrb.winml.rt.WinRtCollections;
import de.bund.zrb.winml.rt.WinRtException;
import de.bund.zrb.winml.rt.WinRtRuntime;

import java.io.Closeable;
import java.util.logging.Logger;

/**
 * Java wrapper for {@code Windows.AI.MachineLearning.LearningModelSession}.
 * <p>
 * Represents an inference session bound to a specific model and device.
 * Supports synchronous evaluation via blocking on the async WinRT API.
 *
 * <pre>{@code
 * try (LearningModel model = LearningModel.loadFromFilePath("model.onnx");
 *      LearningModelDevice device = LearningModelDevice.create(Kind.DIRECTX_HIGH_PERFORMANCE);
 *      LearningModelSession session = LearningModelSession.create(model, device)) {
 *     LearningModelBinding binding = LearningModelBinding.create(session);
 *     binding.bind("input_ids", inputTensor);
 *     EvaluationResult result = session.evaluate(binding, "step-0");
 *     Pointer logits = result.getOutput("logits");
 * }
 * }</pre>
 */
public final class LearningModelSession implements Closeable {

    private static final Logger LOG = Logger.getLogger(LearningModelSession.class.getName());

    private static final String RUNTIME_CLASS =
            "Windows.AI.MachineLearning.LearningModelSession";

    /**
     * IID for ILearningModelSessionFactory (discovered via GetIids on Win11 23H2).
     * {0F6B881D-1C9B-47B6-BFE0-F1CF62A67579}
     */
    private static final Guid.GUID IID_ILEARNINGMODELSESSION_FACTORY = new Guid.GUID(
            "{0F6B881D-1C9B-47B6-BFE0-F1CF62A67579}");

    // ILearningModelSessionFactory VTable:
    // [0..5] IInspectable
    // [6] CreateFromModel(ILearningModel, out ILearningModelSession)
    // [7] CreateFromModelOnDevice(ILearningModel, ILearningModelDevice, out ILearningModelSession)
    private static final int VT_CREATE_FROM_MODEL = 6;
    private static final int VT_CREATE_FROM_MODEL_ON_DEVICE = 7;

    // ILearningModelSession instance VTable:
    // [0..5] IInspectable
    // [6] get_Model(out ILearningModel)
    // [7] get_Device(out ILearningModelDevice)
    // [8] get_EvaluationProperties(out IPropertySet)
    // [9] EvaluateAsync(ILearningModelBinding, HSTRING correlationId,
    //                    out IAsyncOperation<ILearningModelEvaluationResult>)
    // [10] EvaluateFeaturesAsync(...)
    private static final int VT_EVALUATE_ASYNC = 9;

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

    /**
     * Evaluates the model with the given binding (blocks until complete).
     *
     * @param binding       the bound inputs
     * @param correlationId optional identifier for logging/tracing (may be empty)
     * @return the evaluation result containing output tensors
     * @throws WinRtException if evaluation fails
     */
    public EvaluationResult evaluate(LearningModelBinding binding, String correlationId) {
        try (HString corrId = HString.create(correlationId != null ? correlationId : "")) {
            PointerByReference asyncOpRef = new PointerByReference();

            int hr = ComVTable.call(ptr, VT_EVALUATE_ASYNC,
                    binding.getPointer(), corrId.getHandle(), asyncOpRef);

            if (hr < 0) {
                throw new WinRtException("LearningModelSession.EvaluateAsync", hr);
            }

            Pointer asyncOp = asyncOpRef.getValue();
            try {
                Pointer resultPtr = WinRtAsync.awaitResult(asyncOp, "EvaluateAsync");
                return new EvaluationResult(resultPtr);
            } finally {
                ComVTable.release(asyncOp);
            }
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
