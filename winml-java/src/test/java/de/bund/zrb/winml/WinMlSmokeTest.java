package de.bund.zrb.winml;

import de.bund.zrb.winml.ml.LearningModel;
import de.bund.zrb.winml.ml.LearningModelDevice;
import de.bund.zrb.winml.ml.LearningModelSession;
import de.bund.zrb.winml.rt.WinRtRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

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
        // Calling initialize twice should not throw
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

    /**
     * Loads the Phi-3 model if available.
     * Set system property {@code winml.test.model} to the .onnx file path.
     */
    @Test
    void loadModelIfAvailable() {
        String modelPath = System.getProperty("winml.test.model");
        if (modelPath == null || modelPath.isEmpty()) {
            System.out.println("Skipping model load test (set -Dwinml.test.model=path/to/model.onnx)");
            return;
        }

        WinRtRuntime.initialize();
        try (LearningModel model = LearningModel.loadFromFilePath(modelPath)) {
            assertNotNull(model.getPointer());

            try (LearningModelDevice device = LearningModelDevice.create(
                    LearningModelDevice.Kind.DIRECTX_HIGH_PERFORMANCE);
                 LearningModelSession session = LearningModelSession.create(model, device)) {
                assertNotNull(session.getPointer());
            }
        }
    }
}

