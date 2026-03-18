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
 * Java wrapper for {@code Windows.AI.MachineLearning.LearningModel}.
 * <p>
 * Loads an ONNX model via the WinML API, which uses the system's built-in
 * DirectML / DirectX 12 stack — no separate native DLLs required.
 *
 * <pre>{@code
 * try (LearningModel model = LearningModel.loadFromFilePath("C:/models/model.onnx")) {
 *     // create session, bind, evaluate ...
 * }
 * }</pre>
 */
public final class LearningModel implements Closeable {

    private static final Logger LOG = Logger.getLogger(LearningModel.class.getName());

    /**
     * IID for {@code ILearningModelStatics}.
     * {E3B977E8-6952-4E47-8EF4-1F7F07897C6D}
     */
    private static final Guid.GUID IID_ILEARNINGMODEL_STATICS = new Guid.GUID(
            "{E3B977E8-6952-4E47-8EF4-1F7F07897C6D}");

    private static final String RUNTIME_CLASS =
            "Windows.AI.MachineLearning.LearningModel";

    // VTable indices for ILearningModelStatics (inherits IInspectable [0..5])
    // [6] LoadFromStorageFileAsync
    // [7] LoadFromStreamAsync
    // [8] LoadFromFilePath(HSTRING, out ILearningModel)
    private static final int VT_LOAD_FROM_FILE_PATH = 8;

    private Pointer ptr;

    private LearningModel(Pointer ptr) {
        this.ptr = ptr;
    }

    /**
     * Loads an ONNX model from a file path using WinML.
     *
     * @param modelPath absolute path to the .onnx file
     * @return a loaded LearningModel (must be closed when done)
     * @throws WinRtException if loading fails
     */
    public static LearningModel loadFromFilePath(String modelPath) {
        WinRtRuntime.initialize();

        LOG.info("Loading WinML model: " + modelPath);
        long t0 = System.currentTimeMillis();

        // Get ILearningModelStatics activation factory
        Pointer statics = WinRtRuntime.getActivationFactory(RUNTIME_CLASS, IID_ILEARNINGMODEL_STATICS);

        try (HString pathStr = HString.create(modelPath)) {
            PointerByReference modelRef = new PointerByReference();

            // ILearningModelStatics::LoadFromFilePath(HSTRING modelPath, out ILearningModel model)
            int hr = ComVTable.call(statics, VT_LOAD_FROM_FILE_PATH,
                    pathStr.getHandle(), modelRef);

            if (hr < 0) {
                throw new WinRtException("LearningModel.LoadFromFilePath", hr,
                        "path=" + modelPath);
            }

            long dt = System.currentTimeMillis() - t0;
            LOG.info("WinML model loaded in " + dt + " ms");

            return new LearningModel(modelRef.getValue());
        } finally {
            ComVTable.release(statics);
        }
    }

    /** Returns the raw COM pointer (for passing to LearningModelSession etc.). */
    public Pointer getPointer() {
        return ptr;
    }

    @Override
    public void close() {
        if (ptr != null) {
            ComVTable.release(ptr);
            ptr = null;
        }
    }
}

