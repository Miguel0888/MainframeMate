package de.bund.zrb.winml;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.PointerByReference;
import de.bund.zrb.winml.rt.ComBase;
import de.bund.zrb.winml.rt.ComVTable;
import de.bund.zrb.winml.rt.HString;
import de.bund.zrb.winml.rt.WinRtRuntime;
import de.bund.zrb.winml.tensor.TensorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Discovers the actual interface IIDs exposed by WinML runtime classes
 * on the current Windows installation. Results are used to correct
 * hardcoded IIDs in the production code.
 */
@EnabledOnOs(OS.WINDOWS)
class IidDiscoveryTest {

    private static final Guid.GUID IID_IACTIVATION_FACTORY =
            new Guid.GUID("{00000035-0000-0000-C000-000000000046}");

    @Test
    void discoverTensorFloatIids() {
        WinRtRuntime.initialize();
        System.out.println("=== TensorFloat factory IIDs ===");
        dumpIids("Windows.AI.MachineLearning.TensorFloat");
    }

    @Test
    void discoverTensorInt64Iids() {
        WinRtRuntime.initialize();
        System.out.println("=== TensorInt64Bit factory IIDs ===");
        dumpIids("Windows.AI.MachineLearning.TensorInt64Bit");
    }

    @Test
    void discoverTensorFloatInstanceIids() {
        WinRtRuntime.initialize();
        System.out.println("=== TensorFloat INSTANCE IIDs ===");

        // Create a tensor via the working Statics2 factory
        try {
            Pointer tensor = TensorFactory.createTensorFloat(new long[]{1, 3}, new float[]{1f, 2f, 3f});
            dumpObjectIids(tensor, "TensorFloat instance");

            // Try calling VTable[6] directly (GetAsVectorView if ITensorFloat)
            System.out.println("\nAttempting direct VTable[6] call (GetAsVectorView)...");
            PointerByReference ref = new PointerByReference();
            int hr = ComVTable.call(tensor, 6, ref);
            System.out.println("  VTable[6] → HRESULT 0x" + Integer.toHexString(hr));
            if (hr >= 0 && ref.getValue() != null) {
                System.out.println("  SUCCESS! VTable[6] returned a pointer.");
                // Try to get size from it (if it's IVectorView, VTable[7] = get_Size)
                com.sun.jna.ptr.IntByReference sizeRef = new com.sun.jna.ptr.IntByReference();
                int hr2 = ComVTable.call(ref.getValue(), 7, sizeRef);
                System.out.println("  VTable[7] on result → HRESULT 0x" + Integer.toHexString(hr2)
                        + ", size=" + sizeRef.getValue());
                ComVTable.release(ref.getValue());
            }

            ComVTable.release(tensor);
        } catch (Exception e) {
            System.out.println("  Failed to create tensor: " + e.getMessage());
        }
    }

    @Test
    void discoverTensorInt64FactoryIids() {
        WinRtRuntime.initialize();
        System.out.println("=== TensorInt64Bit via generic IActivationFactory ===");

        // Get generic IActivationFactory
        try (HString hs = HString.create("Windows.AI.MachineLearning.TensorInt64Bit")) {
            PointerByReference ref = new PointerByReference();
            WinNT.HRESULT hr = ComBase.INSTANCE.RoGetActivationFactory(
                    hs.getHandle(), IID_IACTIVATION_FACTORY, ref);
            System.out.println("RoGetActivationFactory → HRESULT 0x" + Integer.toHexString(hr.intValue()));

            if (hr.intValue() == 0 && ref.getValue() != null) {
                dumpObjectIids(ref.getValue(), "TensorInt64Bit factory");
                ComVTable.release(ref.getValue());
            }
        }
    }

    @Test
    void discoverLearningModelSessionIids() {
        WinRtRuntime.initialize();
        System.out.println("=== LearningModelSession factory IIDs ===");
        dumpIids("Windows.AI.MachineLearning.LearningModelSession");
    }

    @Test
    void discoverEvaluationResultIids() {
        WinRtRuntime.initialize();
        System.out.println("=== LearningModelEvaluationResult factory IIDs ===");
        dumpIids("Windows.AI.MachineLearning.LearningModelEvaluationResult");
    }

    // ── Helpers ──────────────────────────────────────────────

    private void dumpIids(String className) {
        try (HString hs = HString.create(className)) {
            PointerByReference ref = new PointerByReference();
            WinNT.HRESULT hr = ComBase.INSTANCE.RoGetActivationFactory(
                    hs.getHandle(), IID_IACTIVATION_FACTORY, ref);

            if (hr.intValue() != 0) {
                System.out.println("RoGetActivationFactory FAILED: 0x" + Integer.toHexString(hr.intValue()));
                return;
            }

            dumpObjectIids(ref.getValue(), className + " factory");
            ComVTable.release(ref.getValue());
        }
    }

    private void dumpObjectIids(Pointer comObj, String label) {
        Pointer vtable = comObj.getPointer(0);

        // GetIids is VTable[3]: GetIids(out ULONG iidCount, out IID** iids)
        PointerByReference countRef = new PointerByReference();
        PointerByReference iidsRef = new PointerByReference();

        Function getIids = Function.getFunction(
                vtable.getPointer(3L * Native.POINTER_SIZE), Function.ALT_CONVENTION);
        int hr = getIids.invokeInt(new Object[]{comObj, countRef, iidsRef});

        if (hr != 0) {
            System.out.println(label + " GetIids → HRESULT 0x" + Integer.toHexString(hr));
            return;
        }

        long count = Pointer.nativeValue(countRef.getValue());
        System.out.println(label + " implements " + count + " interfaces:");

        if (count > 0 && count < 100 && iidsRef.getValue() != null) {
            Pointer arr = iidsRef.getValue();
            for (long i = 0; i < count; i++) {
                long offset = i * 16;
                int d1 = arr.getInt(offset);
                short d2 = arr.getShort(offset + 4);
                short d3 = arr.getShort(offset + 6);
                byte[] d4 = arr.getByteArray(offset + 8, 8);
                String iid = String.format("{%08X-%04X-%04X-%02X%02X-%02X%02X%02X%02X%02X%02X}",
                        d1, d2 & 0xFFFF, d3 & 0xFFFF,
                        d4[0], d4[1], d4[2], d4[3],
                        d4[4], d4[5], d4[6], d4[7]);
                System.out.println("  [" + i + "] " + iid);
            }

            // CoTaskMemFree
            try {
                com.sun.jna.platform.win32.Ole32.INSTANCE.CoTaskMemFree(arr);
            } catch (Exception ignore) {}
        }
    }
}

