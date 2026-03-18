package de.bund.zrb.winml.rt;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.PointerByReference;

import java.util.logging.Logger;

/**
 * Manages WinRT initialization and activation factory resolution.
 * <p>
 * Typical usage:
 * <pre>{@code
 * WinRtRuntime.initialize();
 * try {
 *     Pointer factory = WinRtRuntime.getActivationFactory(
 *             "Windows.AI.MachineLearning.LearningModel",
 *             IID_ILearningModelStatics);
 *     // ... use factory via COM vtable calls
 * } finally {
 *     WinRtRuntime.uninitialize();
 * }
 * }</pre>
 */
public final class WinRtRuntime {

    private static final Logger LOG = Logger.getLogger(WinRtRuntime.class.getName());

    private static volatile boolean initialized = false;

    private WinRtRuntime() {}

    /**
     * Initializes the Windows Runtime (MTA).
     * Safe to call multiple times — tracks initialization state.
     * <p>
     * Also ensures JNA loads its native library from a fixed directory
     * ({@code ~/.mainframemate/native/}) instead of {@code %TEMP%}, which
     * may be blocked on hardened Windows 11 systems.
     *
     * @throws WinRtException if RoInitialize fails
     */
    public static synchronized void initialize() {
        if (initialized) return;

        // MUST run before any JNA class (ComBase) is loaded —
        // configures jna.boot.library.path to avoid temp extraction
        JnaBootstrap.configure();

        WinNT.HRESULT hr = ComBase.INSTANCE.RoInitialize(ComBase.RO_INIT_MULTITHREADED);
        int code = hr.intValue();

        // S_OK (0) or S_FALSE (1 = already initialized) are both fine
        if (code != 0 && code != 1) {
            throw new WinRtException("RoInitialize", code);
        }

        initialized = true;
        LOG.info("WinRT initialized (MTA)");
    }

    /**
     * Uninitializes the Windows Runtime.
     */
    public static synchronized void uninitialize() {
        if (!initialized) return;
        ComBase.INSTANCE.RoUninitialize();
        initialized = false;
        LOG.info("WinRT uninitialized");
    }

    /**
     * Gets a WinRT activation factory for a runtime class.
     *
     * @param className fully-qualified WinRT class name
     *                  (e.g. {@code "Windows.AI.MachineLearning.LearningModel"})
     * @param iid       the IID of the interface to request
     * @return raw COM pointer to the factory (caller must Release when done)
     * @throws WinRtException if RoGetActivationFactory fails
     */
    public static Pointer getActivationFactory(String className, Guid.GUID iid) {
        if (!initialized) {
            initialize();
        }

        try (HString hs = HString.create(className)) {
            PointerByReference ref = new PointerByReference();
            WinNT.HRESULT hr = ComBase.INSTANCE.RoGetActivationFactory(
                    hs.getHandle(), iid, ref);

            if (hr.intValue() != 0) {
                throw new WinRtException("RoGetActivationFactory", hr.intValue(),
                        "class=" + className);
            }

            return ref.getValue();
        }
    }

    /**
     * Checks whether the current OS is Windows 10+ (required for WinML).
     */
    public static boolean isWinMlAvailable() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("windows")) return false;

        // WinML requires Windows 10 1809+ (build 17763+)
        String ver = System.getProperty("os.version", "0");
        try {
            String[] parts = ver.split("\\.");
            int major = Integer.parseInt(parts[0]);
            return major >= 10;
        } catch (Exception e) {
            return false;
        }
    }
}

