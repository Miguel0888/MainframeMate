package de.bund.zrb.winml;

import com.sun.jna.Pointer;
import de.bund.zrb.winml.ml.*;
import de.bund.zrb.winml.rt.ComVTable;
import de.bund.zrb.winml.rt.WinRtRuntime;
import de.bund.zrb.winml.tensor.TensorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for the WinML Java bindings.
 * <p>
 * These tests only run on Windows and require Windows 10 1809+.
 */
@EnabledOnOs(OS.WINDOWS)
class WinMlSmokeTest {

    @Test
    void winRtInitializeSucceeds() {
        assertDoesNotThrow(() -> {
            WinRtRuntime.initialize();
            assertTrue(WinRtRuntime.isWinMlAvailable(), "WinML should be available on Win10+");
        });
    }

    @Test
    void winRtInitializeIdempotent() {
        WinRtRuntime.initialize();
        assertDoesNotThrow(() -> WinRtRuntime.initialize());
    }

    @Test
    void hstringRoundTrip() {
        WinRtRuntime.initialize();
        try (de.bund.zrb.winml.rt.HString hs = de.bund.zrb.winml.rt.HString.create("Hello WinRT!")) {
            assertEquals("Hello WinRT!", hs.toJavaString());
        }
    }

    @Test
    void learningModelDeviceCreation() {
        WinRtRuntime.initialize();
        try (LearningModelDevice device = LearningModelDevice.create(
                LearningModelDevice.Kind.DIRECTX_HIGH_PERFORMANCE)) {
            assertNotNull(device.getPointer(), "Device pointer should not be null");
        }
    }

    @Test
    void createTensorFloat() {
        WinRtRuntime.initialize();
        float[] data = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f};
        Pointer tensor = TensorFactory.createTensorFloat(new long[]{2, 3}, data);
        assertNotNull(tensor, "TensorFloat should not be null");

        // Read back the data
        float[] readBack = TensorFactory.readTensorFloat(tensor);
        assertArrayEquals(data, readBack, 0.001f, "TensorFloat round-trip");

        // Read shape
        long[] shape = TensorFactory.readTensorShape(tensor);
        assertArrayEquals(new long[]{2, 3}, shape, "TensorFloat shape");

        ComVTable.release(tensor);
    }

    @Test
    void createTensorInt64() {
        WinRtRuntime.initialize();
        long[] data = {10L, 20L, 30L, 40L};
        Pointer tensor = TensorFactory.createTensorInt64(new long[]{1, 4}, data);
        assertNotNull(tensor, "TensorInt64 should not be null");

        // Read back
        long[] readBack = TensorFactory.readTensorInt64(tensor);
        assertArrayEquals(data, readBack, "TensorInt64 round-trip");

        long[] shape = TensorFactory.readTensorShape(tensor);
        assertArrayEquals(new long[]{1, 4}, shape, "TensorInt64 shape");

        ComVTable.release(tensor);
    }

    @Test
    void createEmptyTensorFloat() {
        WinRtRuntime.initialize();
        Pointer tensor = TensorFactory.createTensorFloat(new long[]{1, 10});
        assertNotNull(tensor, "Empty TensorFloat should not be null");

        float[] data = TensorFactory.readTensorFloat(tensor);
        assertEquals(10, data.length, "Empty tensor should have 10 elements");

        ComVTable.release(tensor);
    }

    /**
     * Loads an ONNX model if available and attempts a session on various devices.
     * Set project property {@code winml.test.model} to the .onnx file path.
     */
    @Test
    void loadModelAndEvaluateIfAvailable() {
        String modelPath = System.getProperty("winml.test.model");
        if (modelPath == null || modelPath.isEmpty()) {
            System.out.println("Skipping model eval test (set -Pwinml.test.model=path/to/model.onnx)");
            return;
        }

        WinRtRuntime.initialize();
        LearningModel model = LearningModel.loadFromFilePath(modelPath);
        assertNotNull(model.getPointer(), "Model should load");
        System.out.println("Model loaded: " + modelPath);

        // Try various device kinds — quantized models may not work on all
        LearningModelDevice.Kind[] kinds = {
                LearningModelDevice.Kind.DEFAULT,
                LearningModelDevice.Kind.CPU,
                LearningModelDevice.Kind.DIRECTX,
                LearningModelDevice.Kind.DIRECTX_HIGH_PERFORMANCE
        };

        LearningModelSession workingSession = null;
        LearningModelDevice workingDevice = null;

        for (LearningModelDevice.Kind kind : kinds) {
            LearningModelDevice dev = null;
            try {
                dev = LearningModelDevice.create(kind);
                LearningModelSession sess = LearningModelSession.create(model, dev);
                System.out.println("Session created with device: " + kind);
                workingSession = sess;
                workingDevice = dev;
                break;
            } catch (Exception e) {
                System.out.println("Device " + kind + " failed: " + e.getMessage());
                if (dev != null) dev.close();
            }
        }

        if (workingSession == null) {
            model.close();
            System.out.println("No device kind supported this model. "
                    + "Quantized (int4-awq) models may need full ONNX Runtime.");
            return;
        }

        try {
            long[] inputIds = {1, 2, 3};
            Pointer inputTensor = TensorFactory.createTensorInt64(new long[]{1, 3}, inputIds);
            long[] attMask = {1, 1, 1};
            Pointer attTensor = TensorFactory.createTensorInt64(new long[]{1, 3}, attMask);

            try {
                LearningModelBinding binding = LearningModelBinding.create(workingSession);
                binding.bind("input_ids", inputTensor);
                binding.bind("attention_mask", attTensor);

                long t0 = System.currentTimeMillis();
                EvaluationResult result = workingSession.evaluate(binding, "smoke-test");
                long dt = System.currentTimeMillis() - t0;

                System.out.println("Evaluation took " + dt + " ms, succeeded=" + result.succeeded());
                if (result.succeeded() && result.hasOutput("logits")) {
                    long[] shape = result.getOutputShape("logits");
                    System.out.println("Logits shape: " + Arrays.toString(shape));
                }

                result.close();
                binding.close();
            } finally {
                ComVTable.release(inputTensor);
                ComVTable.release(attTensor);
            }
        } finally {
            workingSession.close();
            workingDevice.close();
            model.close();
        }
    }
}
