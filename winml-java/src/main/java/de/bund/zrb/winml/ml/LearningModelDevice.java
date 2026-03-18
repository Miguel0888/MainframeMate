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
 * Java wrapper for {@code Windows.AI.MachineLearning.LearningModelDevice}.
 * <p>
 * Uses WinRT activation via generic {@code IActivationFactory} since the
 * specific {@code ILearningModelDeviceFactory} IID varies across Windows versions.
 * <p>
 * The approach: get IActivationFactory, QI for ILearningModelDeviceFactory
 * by known patterns, or construct via the activatable overload.
 */
public final class LearningModelDevice implements Closeable {

    private static final Logger LOG = Logger.getLogger(LearningModelDevice.class.getName());

    private static final String RUNTIME_CLASS =
            "Windows.AI.MachineLearning.LearningModelDevice";

    /** Generic IActivationFactory IID — every WinRT class must support this. */
    private static final Guid.GUID IID_IACTIVATION_FACTORY =
            new Guid.GUID("{00000035-0000-0000-C000-000000000046}");

    /**
     * IID for ILearningModelDeviceFactory — discovered via IInspectable.GetIids()
     * on Windows 11 23H2 (Build 22631).
     * {9CFFD74D-B1E5-4F20-80AD-0A56690DB06B}
     */
    private static final Guid.GUID IID_ILEARNINGMODELDEVICE_FACTORY =
            new Guid.GUID("{9CFFD74D-B1E5-4F20-80AD-0A56690DB06B}");

    // ILearningModelDeviceFactory VTable (inherits IInspectable [0..5]):
    // [6] Create(LearningModelDeviceKind kind, out ILearningModelDevice device)
    private static final int VT_FACTORY_CREATE = 6;

    /** Maps to {@code Windows.AI.MachineLearning.LearningModelDeviceKind}. */
    public enum Kind {
        DEFAULT(0),
        CPU(1),
        DIRECTX(2),
        DIRECTX_HIGH_PERFORMANCE(3),
        DIRECTX_MIN_POWER(4);

        final int value;
        Kind(int value) { this.value = value; }
    }

    private Pointer ptr;

    private LearningModelDevice(Pointer ptr) {
        this.ptr = ptr;
    }

    /**
     * Creates a LearningModelDevice for the specified hardware.
     * <p>
     * For {@code Kind.DEFAULT}, uses the generic IActivationFactory.ActivateInstance().
     * For specific device kinds, uses the factory's Create method if available.
     */
    public static LearningModelDevice create(Kind kind) {
        WinRtRuntime.initialize();
        LOG.info("Creating WinML device: " + kind);

        // Get the specific factory interface
        Pointer factory = WinRtRuntime.getActivationFactory(
                RUNTIME_CLASS, IID_ILEARNINGMODELDEVICE_FACTORY);

        try {
            PointerByReference deviceRef = new PointerByReference();
            int hr = ComVTable.call(factory, VT_FACTORY_CREATE, kind.value, deviceRef);
            if (hr < 0) {
                throw new WinRtException("LearningModelDevice.Create", hr, "kind=" + kind);
            }
            LOG.info("WinML device created: " + kind);
            return new LearningModelDevice(deviceRef.getValue());
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
