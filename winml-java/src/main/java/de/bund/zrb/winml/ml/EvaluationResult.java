package de.bund.zrb.winml.ml;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import de.bund.zrb.winml.rt.ComVTable;
import de.bund.zrb.winml.rt.WinRtCollections;
import de.bund.zrb.winml.rt.WinRtException;
import de.bund.zrb.winml.tensor.TensorFactory;

import java.io.Closeable;
import java.util.logging.Logger;

/**
 * Wrapper for {@code Windows.AI.MachineLearning.LearningModelEvaluationResult}.
 * <p>
 * Provides access to the evaluation outputs as named tensors.
 *
 * <pre>
 * ILearningModelEvaluationResult VTable:
 *   [0..5] IInspectable
 *   [6] get_CorrelationId(out HSTRING)
 *   [7] get_ErrorStatus(out INT32)
 *   [8] get_Succeeded(out BOOLEAN)
 *   [9] get_Outputs(out IMapView&lt;HSTRING, IInspectable&gt;)
 * </pre>
 */
public final class EvaluationResult implements Closeable {

    private static final Logger LOG = Logger.getLogger(EvaluationResult.class.getName());

    private static final int VT_GET_ERROR_STATUS = 7;
    private static final int VT_GET_SUCCEEDED = 8;
    private static final int VT_GET_OUTPUTS = 9;

    private Pointer ptr;
    private Pointer outputsMap; // cached IMapView

    EvaluationResult(Pointer ptr) {
        this.ptr = ptr;
    }

    /**
     * Returns whether the evaluation succeeded.
     */
    public boolean succeeded() {
        IntByReference ref = new IntByReference();
        int hr = ComVTable.call(ptr, VT_GET_SUCCEEDED, ref);
        if (hr < 0) {
            throw new WinRtException("EvaluationResult.get_Succeeded", hr);
        }
        return ref.getValue() != 0;
    }

    /**
     * Returns the error status code (0 if success).
     */
    public int getErrorStatus() {
        IntByReference ref = new IntByReference();
        int hr = ComVTable.call(ptr, VT_GET_ERROR_STATUS, ref);
        if (hr < 0) {
            throw new WinRtException("EvaluationResult.get_ErrorStatus", hr);
        }
        return ref.getValue();
    }

    /**
     * Gets a raw output by name as an IInspectable COM pointer.
     * The caller must determine the tensor type and read accordingly.
     *
     * @param name the output feature name (e.g. "logits")
     * @return COM pointer to the output value (do NOT release — owned by the result)
     */
    public Pointer getOutput(String name) {
        Pointer map = getOutputsMap();
        return WinRtCollections.mapLookup(map, name);
    }

    /**
     * Reads a float output tensor by name.
     *
     * @param name the output feature name
     * @return float array with all values
     */
    public float[] getOutputAsFloat(String name) {
        Pointer outputPtr = getOutput(name);
        try {
            return TensorFactory.readTensorFloat(outputPtr);
        } finally {
            ComVTable.release(outputPtr);
        }
    }

    /**
     * Reads an int64 output tensor by name.
     *
     * @param name the output feature name
     * @return long array with all values
     */
    public long[] getOutputAsInt64(String name) {
        Pointer outputPtr = getOutput(name);
        try {
            return TensorFactory.readTensorInt64(outputPtr);
        } finally {
            ComVTable.release(outputPtr);
        }
    }

    /**
     * Reads the shape of an output tensor by name.
     */
    public long[] getOutputShape(String name) {
        Pointer outputPtr = getOutput(name);
        try {
            return TensorFactory.readTensorShape(outputPtr);
        } finally {
            ComVTable.release(outputPtr);
        }
    }

    /**
     * Checks if an output with the given name exists.
     */
    public boolean hasOutput(String name) {
        Pointer map = getOutputsMap();
        return WinRtCollections.mapHasKey(map, name);
    }

    /**
     * Returns the number of outputs.
     */
    public int outputCount() {
        Pointer map = getOutputsMap();
        return WinRtCollections.mapSize(map);
    }

    private Pointer getOutputsMap() {
        if (outputsMap == null) {
            PointerByReference mapRef = new PointerByReference();
            int hr = ComVTable.call(ptr, VT_GET_OUTPUTS, mapRef);
            if (hr < 0) {
                throw new WinRtException("EvaluationResult.get_Outputs", hr);
            }
            outputsMap = mapRef.getValue();
        }
        return outputsMap;
    }

    @Override
    public void close() {
        if (outputsMap != null) {
            ComVTable.release(outputsMap);
            outputsMap = null;
        }
        if (ptr != null) {
            ComVTable.release(ptr);
            ptr = null;
        }
    }
}

